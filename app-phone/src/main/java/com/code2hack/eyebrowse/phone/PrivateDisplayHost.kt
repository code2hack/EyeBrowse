package com.code2hack.eyebrowse.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.PixelCopy
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * The single private hosting display: a {@link VirtualDisplay} fed by an {@link ImageReader}
 * surface, showing the existing live WebView through a {@link android.app.Presentation}.
 *
 * <p>This is own-content-only private output on a virtual display, never physical-screen capture.
 *
 * <p><b>Ownership model (correction round 2):</b>
 * <ul>
 * <li><b>Bound delivery.</b> Each lease hands the capture pipeline an immutable bound sink
 * (lease token + hosting generation + consumer, created by the controller). Delivery is admitted
 * once per frame through the controller's lock-free {@link FrameGate} against that token.
 * Revocation prevents new admissions; an admitted frame may finish delivery to its own consumer
 * (in-flight borrowed use). The capture path never takes the controller monitor.</li>
 * <li><b>Producer-bound readback.</b> ImageReader is only the safely drained virtual-display
 * sink. Published pixels come from a public Window PixelCopy issued only after a unique WebView
 * visual request, a later observed hardware draw of this Presentation, and its matching frame
 * commit. Readback runs on its own bounded HandlerThread; View/window geometry is captured and
 * revalidated on Main, while delivery remains outside nativeLock and controller monitors.</li>
 * <li><b>Observable teardown.</b> Native capture resources are released on the capture path (a
 * posted teardown task, or inline when no capture thread exists) and the host object remains
 * reachable for introspection until that completion marker is set. Nothing joins while holding
 * the controller monitor.</li>
 * </ul>
 *
 * <p>Entry points other than the capture callback run on the main thread.
 *
 * <p>Migration note: visibility widened package-private -> public for the separately compiled
 * androidTest/JVM-test consumers; `Factory`/`PresentationHost`/`FrameSink` keep their Java
 * implementability; `synchronized(nativeLock)` blocks and `@Volatile` flags preserve the original
 * monitor/visibility model; `packRows` stays a static-entry helper via companion `@JvmStatic`;
 * checked `throws HostingException` is preserved with `@Throws` where Java callers catch it.
 */
class PrivateDisplayHost(
    private val factory: Factory,
    private val onQuiesced: Runnable?,
    private val localFocusForRg: Boolean = false,
) {

    /** Test seam: creates the platform resources so failure injection can exercise rollback. */
    interface Factory {
        @Throws(RuntimeException::class)
        fun createVirtualDisplay(
            manager: DisplayManager,
            name: String,
            width: Int,
            height: Int,
            densityDpi: Int,
            surface: Any?,
        ): VirtualDisplay?

        @Throws(RuntimeException::class)
        fun createImageReader(width: Int, height: Int): ImageReader?

        @Throws(RuntimeException::class)
        fun createPresentation(context: Context, display: Display): PresentationHost
    }

    data class DrawObservation(val serial: Long, val elapsedMs: Long)

    fun interface DrawListener {
        fun onDraw(serial: Long, elapsedMs: Long)
    }

    fun interface FreshFrameRequest {
        fun request(onCommitted: (Long, Long) -> Unit)
    }

    /** The shown presentation holding the container the WebView is attached to. */
    interface PresentationHost {
        fun show()

        fun dismiss()

        fun container(): FrameLayout

        /** Optional for fake factories; platform implementation checks its window/display. */
        fun isAvailable(): Boolean = true
        fun setUnavailableListener(listener: Runnable?) {}
        fun focusAttachedView(view: android.view.View?) {}
        fun localFocusReady(view: android.view.View): Boolean = false
        fun setLocalFocusListener(listener: Runnable?) {}
        fun windowIdentity(): Int = 0
        fun focusLossSerial(): Long = 0
        fun captureWindow(): Window? = null
        fun drawObservation(): DrawObservation = DrawObservation(0, 0)
        fun setDrawListener(listener: DrawListener?) {}
    }

    /** Immutable per-lease delivery sink; the capture pipeline invokes exactly this object. */
    fun interface FrameSink {
        fun onFrame(frame: HostingFrame)
    }

    private val nativeLock: Any = Any()

    /** One capture owner at a time: ACTIVE → RETIRING → QUIESCENT is real and observable (R2). */
    private val ownerPhase = CaptureOwnerPhase()

    private var unavailableListener: Runnable? = null
    private var localFocusListener: Runnable? = null
    private var resourceSerial: Long = 0
    private var virtualDisplay: VirtualDisplay? = null // main-thread only

    fun setUnavailableListener(listener: Runnable?) { unavailableListener = listener }
    fun setLocalFocusListener(listener: Runnable?) { localFocusListener = listener }
    fun localFocusReady(session: PhoneBrowserSession): Boolean =
        localFocusForRg && session.view()?.let { presentation?.localFocusReady(it) } == true

    /** UI-thread draw serial of the exact private Presentation source. */
    fun drawSerial(): Long = presentation?.drawObservation()?.serial ?: 0L

    data class DisplaySnapshot(
        val serial: Long, val displayId: Int, val valid: Boolean, val state: Int,
        val width: Int, val height: Int, val actualWidth: Int, val actualHeight: Int,
        val densityDpi: Int, val readerWidth: Int, val readerHeight: Int,
        val presentationContextDisplayId: Int, val surfaceDetached: Boolean,
    )

    fun displaySnapshot(): DisplaySnapshot {
        val display = virtualDisplay?.display
        val size = android.graphics.Point()
        if (display?.isValid == true) display.getRealSize(size)
        val readerSize = synchronized(nativeLock) {
            (imageReader?.width ?: 0) to (imageReader?.height ?: 0)
        }
        val contextDisplay = runCatching {
            presentation?.container()?.context?.display?.displayId ?: -1
        }.getOrDefault(-1)
        return DisplaySnapshot(resourceSerial, display?.displayId ?: -1,
            display?.isValid == true, display?.state ?: Display.STATE_UNKNOWN,
            width, height, size.x, size.y, densityDpi, readerSize.first, readerSize.second,
            contextDisplay, displaySurfaceDetached)
    }
    private var presentation: PresentationHost? = null // main-thread only
    private var imageReader: ImageReader? = null // nativeLock-protected
    private var captureThread: HandlerThread? = null // main-thread lifecycle
    private var captureHandler: Handler? = null // main-thread lifecycle
    private var retainedCaptureThread: Thread? = null // latest capture thread, for isAlive() introspection
    private val main = Handler(android.os.Looper.getMainLooper())
    private var profileResize: Any? = null
    private var cancelProfileResize: (() -> Unit)? = null
    private var stagedProfileReader: ImageReader? = null
    private var retiringProfileReader: ImageReader? = null
    private var retiringProfileCloseScheduled = false
    private var profileSerial = 0L
    private var lastProfileSettlement: Runnable? = null
    @Volatile private var staleReaderCallbacks = 0L

    /** Instrumentation replays the real callbacks; no receiver, fake focus, or alternate path. */
    internal fun profileSettlementForTest(): Runnable? = lastProfileSettlement
    internal fun replayImageCallbackForTest(reader: ImageReader) = onImageAvailable(reader)
    internal fun staleReaderCallbacksForTest(): Long = staleReaderCallbacks

    data class ProfileGeometry(
        val display: DisplaySnapshot, val presentationId: Int, val windowId: Int, val decorId: Int,
        val parentId: Int, val viewId: Int, val density: Int, val decorWidth: Int, val decorHeight: Int,
        val containerWidth: Int, val containerHeight: Int, val measuredWidth: Int, val measuredHeight: Int,
        val viewWidth: Int, val viewHeight: Int, val profileSerial: Long, val focusLossSerial: Long,
        val localFocus: Boolean, val readerOverlap: Int,
    )

    fun profileGeometry(session: PhoneBrowserSession): ProfileGeometry {
        val shown = presentation
        val container = shown?.container()
        val view = session.view()
        val metrics = android.util.DisplayMetrics()
        virtualDisplay?.display?.getRealMetrics(metrics)
        return ProfileGeometry(displaySnapshot(), System.identityHashCode(shown), shown?.windowIdentity() ?: 0,
            System.identityHashCode(container?.rootView), System.identityHashCode(view?.parent),
            System.identityHashCode(view), metrics.densityDpi, container?.rootView?.width ?: 0,
            container?.rootView?.height ?: 0, container?.width ?: 0, container?.height ?: 0,
            view?.measuredWidth ?: 0, view?.measuredHeight ?: 0, view?.width ?: 0, view?.height ?: 0,
            profileSerial, shown?.focusLossSerial() ?: 0, localFocusReady(session),
            (if (imageReader != null) 1 else 0) + (if (stagedProfileReader != null) 1 else 0) +
                (if (retiringProfileReader != null) 1 else 0))
    }

    /** Dedicated same-window transaction. Ordinary release/expiry keeps its destructive meaning. */
    fun resizeProfile(profile: HostingPresentationProfile, session: PhoneBrowserSession,
                      deadlineElapsedMs: Long, completed: (Boolean) -> Unit) {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        val shown = presentation
        val display = virtualDisplay
        val view = session.view()
        val oldReader = imageReader
        if (profileResize != null || stagedProfileReader != null || retiringProfileReader != null ||
            shown == null || display == null || view == null || oldReader == null || displaySurfaceDetached ||
            !localFocusReady(session) || !shown.isAvailable() || !display.display.isValid ||
            display.display.state != Display.STATE_ON || profile.densityDpi != densityDpi ||
            SystemClock.elapsedRealtime() >= deadlineElapsedMs) { completed(false); return }
        val request = Any()
        profileResize = request
        val serial = ++profileSerial
        val focusSerial = shown.focusLossSerial()
        val document = session.documentIdentity()
        var finished = false
        var timeout: Runnable? = null
        fun finish(ok: Boolean) {
            if (finished) return
            finished = true
            timeout?.let(main::removeCallbacks)
            if (profileResize === request) {
                profileResize = null
                cancelProfileResize = null
                lastProfileSettlement = null
            }
            completed(ok)
        }
        cancelProfileResize = {
            if (profileResize === request) { profileResize = null; lastProfileSettlement = null }
            cancelProfileResize = null
            timeout?.let(main::removeCallbacks)
            main.post { finish(false) } // No recursive Stop during resource teardown.
        }
        timeout = Runnable { if (profileResize === request) finish(false) }
        main.postDelayed(timeout!!, (deadlineElapsedMs-SystemClock.elapsedRealtime()).coerceAtLeast(0))
        fun current() = profileResize === request && presentation === shown && virtualDisplay === display &&
            session.view() === view && session.documentIdentity() == document && view.parent === shown.container() &&
            shown.focusLossSerial() == focusSerial && localFocusReady(session) && shown.isAvailable()
        val staged = try { factory.createImageReader(profile.width, profile.height) }
            catch (_: RuntimeException) { null }
        if (staged == null) { finish(false); return }
        stagedProfileReader = staged
        Log.i(TAG, "profile[$serial] staged reader; overlap=2 oldSurfaceValid=${oldReader.surface.isValid}")
        stopCapture() // No close/null surface. The queued barrier waits outside every monitor.
        val capture = captureHandler
        val settle = object : Runnable {
            override fun run() {
                if (profileResize !== request) return
                if (!current() || SystemClock.elapsedRealtime() >= deadlineElapsedMs) { finish(false); return }
                val g = profileGeometry(session)
                if (g.display.valid && g.display.state == Display.STATE_ON && !g.display.surfaceDetached &&
                    g.display.actualWidth == profile.width && g.display.actualHeight == profile.height &&
                    g.density == profile.densityDpi && g.decorWidth == profile.width && g.decorHeight == profile.height &&
                    g.containerWidth == profile.width && g.containerHeight == profile.height &&
                    g.measuredWidth == profile.width && g.measuredHeight == profile.height &&
                    g.viewWidth == profile.width && g.viewHeight == profile.height && retiringProfileReader == null) {
                    Log.i(TAG, "profile[$serial] native layout settled $g")
                    finish(true)
                } else view.postOnAnimation(this)
            }
        }
        lastProfileSettlement = settle
        val swap = Runnable {
            if (!current()) { finish(false); return@Runnable }
            try {
                Log.i(TAG, "profile[$serial] quiesced; resize ${profile.width}x${profile.height}@${profile.densityDpi}")
                display.resize(profile.width, profile.height, profile.densityDpi)
                Log.i(TAG, "profile[$serial] setSurface(non-null) oldSurfaceValid=${oldReader.surface.isValid}")
                display.setSurface(staged.surface)
                synchronized(nativeLock) {
                    imageReader = staged
                    stagedProfileReader = null
                    retiringProfileReader = oldReader
                    width = profile.width; height = profile.height
                    displaySurfaceDetached = false
                }
                val retire = Runnable {
                    val closed = runCatching { oldReader.close() }.isSuccess
                    main.post {
                        retiringProfileCloseScheduled = false
                        if (closed && retiringProfileReader === oldReader) retiringProfileReader = null
                        Log.i(TAG, "profile[$serial] old reader closed=$closed")
                        if (profileResize === request) {
                            if (!closed) finish(false) else {
                                shown.container().requestLayout(); view.requestLayout()
                                view.postOnAnimation(settle)
                            }
                        }
                    }
                }
                retiringProfileCloseScheduled = true
                if (capture == null) retire.run() else if (!capture.post(retire)) {
                    retiringProfileCloseScheduled = false
                    finish(false)
                }
            } catch (error: RuntimeException) {
                Log.e(TAG, "profile[$serial] platform failure ${error.javaClass.simpleName}")
                // Keep both producers owned until caller's explicit Stop releases the display.
                finish(false)
            }
        }
        if (capture == null) main.post(swap)
        else if (!capture.post { main.post(swap) }) finish(false)
    }

    private data class CaptureBinding(
        val sink: FrameSink,
        val session: PhoneBrowserSession,
        val generation: Int,
        val freshFrameRequest: FreshFrameRequest,
        val deadlineElapsedMs: Long,
        val authoritySerial: Long,
        val documentId: String,
        val presentation: PresentationHost,
        val windowIdentity: Int,
        val profileSerial: Long,
        val width: Int,
        val height: Int,
        val densityDpi: Int,
    )

    private data class CaptureCycle(
        val id: Long,
        val binding: CaptureBinding,
        val attempt: Int,
    )

    private data class WindowCopyRequest(
        val cycleId: Long,
        val binding: CaptureBinding,
        val attempt: Int,
        val visualRequestId: Long,
        val drawSerial: Long,
        val drawElapsedMs: Long,
        val window: Window,
        val sourceRect: Rect,
        val bitmap: Bitmap,
    )

    // Capture-path authority. ImageReader remains the non-null display sink only.
    private var frameSink: FrameSink? = null
    private var width: Int = 0
    private var height: Int = 0
    private var densityDpi: Int = 0
    private var frameSequence: Long = 0
    private val deliveryThrottle = CaptureDeliveryThrottle(HostingPolicy.MIN_FRAME_INTERVAL_MS)
    private var pendingFrameTask: Runnable? = null
    private var pendingFrameHandler: Handler? = null
    private var captureBinding: CaptureBinding? = null
    private var captureAuthoritySerial = 0L
    private var captureCycleSerial = 0L
    private var activeCaptureCycle: CaptureCycle? = null
    private var inFlightWindowCopy: WindowCopyRequest? = null
    private var trailingCaptureDemand = false
    private var captureTerminalFailure = false
    private var readbackThread: HandlerThread? = null
    private var readbackHandler: Handler? = null
    private var retainedReadbackThread: Thread? = null
    @Volatile private var captureActive: Boolean = false
    @Volatile private var captureReleased: Boolean = false
    @Volatile private var teardownComplete: Boolean = true
    @Volatile private var captureGeneration: Int = 0

    // Sparse production diagnostics for the T04 acceptance path. Reset per capture arm/rearm;
    // no bitmap/pixel payload is retained.
    @Volatile private var captureArmSerial: Long = 0
    @Volatile private var readerCallbackCount: Long = 0
    @Volatile private var acquiredImageCount: Long = 0
    @Volatile private var deliveredFrameCount: Long = 0
    @Volatile private var lastReaderCallbackElapsedMs: Long = 0
    @Volatile private var lastAcquiredWidth: Int = 0
    @Volatile private var lastAcquiredHeight: Int = 0
    @Volatile private var displaySurfaceDetached: Boolean = false
    @Volatile private var deferredWakeupCount: Long = 0
    @Volatile private var coalescedCallbackCount: Long = 0
    @Volatile private var deferredDeliveryCount: Long = 0
    @Volatile private var lastObservedDrawSerial: Long = 0
    @Volatile private var lastObservedDrawElapsedMs: Long = 0
    @Volatile private var lastVisualRequestId: Long = 0
    @Volatile private var lastCommittedDrawSerial: Long = 0
    @Volatile private var lastCopyInvokedElapsedMs: Long = 0
    @Volatile private var lastCopyCompletedElapsedMs: Long = 0
    @Volatile private var lastCopyResult: Int = Int.MIN_VALUE
    @Volatile private var copyRecoveryCount: Long = 0

    /**
     * Creates the display, presentation and reader for the measured viewport. Recoverable
     * platform failures are converted to {@link HostingException} after rolling back the partial
     * allocations; the caller records the failure. Throws {@link HostingException} otherwise.
     */
    @Throws(HostingException::class)
    fun create(serviceContext: Context, measuredWidth: Int, measuredHeight: Int,
            measuredDensityDpi: Int) {
        val sizeError = HostingPolicy.viewportError(measuredWidth, measuredHeight)
        if (sizeError != null) {
            throw HostingException(sizeError)
        }
        resourceSerial++
        width = measuredWidth
        height = measuredHeight
        densityDpi = measuredDensityDpi
        try {
            val reader = factory.createImageReader(width, height)
            if (reader == null) {
                throw HostingException("image reader creation failed")
            }
            imageReader = reader
            val displayManager = serviceContext.getSystemService(Context.DISPLAY_SERVICE)
                    as DisplayManager
            // Java original: the FIELD is assigned for every non-null created object BEFORE
            // validation, so the rollback path releases a non-null-but-invalid native object.
            virtualDisplay = factory.createVirtualDisplay(displayManager, DISPLAY_NAME, width,
                    height, measuredDensityDpi, reader.surface)
            displaySurfaceDetached = false
            val created = virtualDisplay
            if (created == null || created.display == null) {
                throw HostingException("virtual display creation failed")
            }
            val presentationHost = factory.createPresentation(serviceContext, created.display)
            presentation = presentationHost
            val createdSerial = resourceSerial
            presentationHost.setUnavailableListener(Runnable {
                // Retired Presentation events cannot invalidate a replacement in the same host.
                if (presentation === presentationHost && resourceSerial == createdSerial) {
                    unavailableListener?.run()
                }
            })
            presentationHost.setLocalFocusListener(Runnable {
                if (presentation === presentationHost && resourceSerial == createdSerial) {
                    localFocusListener?.run()
                }
            })
            presentationHost.setDrawListener(DrawListener { serial, elapsedMs ->
                if (presentation === presentationHost && resourceSerial == createdSerial) {
                    onWindowDraw(serial, elapsedMs)
                }
            })
            presentationHost.show()
        } catch (error: HostingException) {
            rollbackDisplayAllocation()
            throw error
        } catch (error: RuntimeException) {
            rollbackDisplayAllocation()
            throw HostingException("platform allocation failed: " + error.message)
        }
    }


    /** Rolls back whatever subset of display resources was already allocated. */
    private fun rollbackDisplayAllocation() {
        synchronized(nativeLock) {
            val reader = imageReader
            if (reader != null) {
                try {
                    reader.close()
                } catch (ignored: RuntimeException) {
                    // Rollback best effort; no capture thread exists yet.
                }
                imageReader = null
            }
        }
        val display = virtualDisplay
        if (display != null) {
            display.release()
            virtualDisplay = null
        }
        val shown = presentation
        if (shown != null) {
            try {
                shown.setUnavailableListener(null)
                shown.setDrawListener(null)
                shown.dismiss()
            } catch (ignored: RuntimeException) {
                // Rollback best effort.
            }
            presentation = null
        }
    }

    /** Attaches the live session WebView into the presentation container. */
    fun attachSessionView(session: PhoneBrowserSession) {
        val shown = presentation
        if (shown == null || session.view() == null) {
            return
        }
        session.attachExternal(shown.container(), shown.container().context)
        if (localFocusForRg) shown.focusAttachedView(session.view())
    }

    /** Moves the session WebView out of the presentation container (stays alive, parentless). */
    fun detachSessionView(session: PhoneBrowserSession) {
        presentation?.let {
            it.focusAttachedView(null)
            session.detachExternal(it.container())
        }
    }

    /**
     * Ensures a capture surface for {@code desiredWidth}×{@code desiredHeight}×
     * {@code desiredDensityDpi}: a matching live reader is kept, a missing reader is created on
     * the surviving display, and any differing geometry (including density) triggers a full
     * display/presentation rebuild without navigation. A previously private view is restored to
     * the new container before return; a Phone-owned view is not stolen. The caller rearms its
     * live lease and explicitly attaches when transitioning from Phone UI. Throws on failure.
     */
    @Throws(HostingException::class)
    fun ensureCaptureSurface(serviceContext: Context, desiredWidth: Int, desiredHeight: Int,
            desiredDensityDpi: Int, session: PhoneBrowserSession) {
        if (profileResize != null) throw HostingException("profile resize pending; recovery forbidden")
        val sizeError = HostingPolicy.viewportError(desiredWidth, desiredHeight)
        if (sizeError != null) {
            throw HostingException(sizeError)
        }
        if (virtualDisplay?.display?.isValid != true || presentation?.isAvailable() != true) {
            rebuildAtSize(serviceContext, desiredWidth, desiredHeight, desiredDensityDpi, session)
            return
        }
        synchronized(nativeLock) {
            if (imageReader != null && width == desiredWidth && height == desiredHeight &&
                    densityDpi == desiredDensityDpi) {
                return
            }
        }
        if (imageReader == null && width == desiredWidth && height == desiredHeight &&
                densityDpi == desiredDensityDpi) {
            // Same geometry, surface recreated after an idle release — no presentation change.
            // Same recoverable allocation path as initial creation (R7).
            val reader: ImageReader?
            try {
                reader = factory.createImageReader(width, height)
            } catch (error: RuntimeException) {
                throw HostingException("reader recreation failed: " + error.message)
            }
            if (reader == null) {
                throw HostingException("image reader creation failed")
            }
            var surfaceReattachFailure: RuntimeException? = null
            synchronized(nativeLock) {
                try {
                    virtualDisplay!!.setSurface(reader.surface)
                    displaySurfaceDetached = false
                    imageReader = reader
                } catch (error: RuntimeException) {
                    reader.close()
                    surfaceReattachFailure = error
                }
            }
            if (surfaceReattachFailure != null) {
                // Some platform/WebView combinations do not reliably accept a fresh reader
                // surface on a surviving VirtualDisplay after the prior reader was closed.
                // Recover by rebuilding the same private display geometry through the existing
                // navigation-preserving path instead of making explicit Retry fail forever.
                Log.w(TAG, "surface reattach failed; rebuilding private display",
                        surfaceReattachFailure)
                rebuildAtSize(
                    serviceContext,
                    desiredWidth,
                    desiredHeight,
                    desiredDensityDpi,
                    session,
                )
            }
            return
        }
        // Differing geometry or density: reconcile by rebuilding the private
        // display/presentation/reader, without navigation (R6).
        rebuildAtSize(serviceContext, desiredWidth, desiredHeight, desiredDensityDpi, session)
    }

    /** Full rebuild of display, presentation and reader at a new measured geometry. */
    @Throws(HostingException::class)
    private fun rebuildAtSize(serviceContext: Context, newWidth: Int, newHeight: Int,
            newDensityDpi: Int, session: PhoneBrowserSession) {
        val view = session.view()
        val shown = presentation
        val restorePrivateAttachment = shown != null && view != null &&
                view.parent === shown.container()
        detachSessionView(session) // The live view leaves the old container; the document stays.
        if (presentation != null) {
            try {
                presentation?.setUnavailableListener(null)
                presentation?.setDrawListener(null)
                presentation?.dismiss()
            } catch (ignored: RuntimeException) {
                // Teardown continues.
            }
            presentation = null
        }
        val display = virtualDisplay
        if (display != null) {
            display.release()
            virtualDisplay = null
        }
        synchronized(nativeLock) {
            val reader = imageReader
            if (reader != null) {
                try {
                    reader.close()
                } catch (ignored: RuntimeException) {
                    // Serialized with any in-flight capture-path use; safe to close here.
                }
                imageReader = null
            }
        }
        create(serviceContext, newWidth, newHeight, newDensityDpi)
        if (restorePrivateAttachment) {
            // First demand may rebuild after a demand-free window change. Capture must see the
            // same live view in the NEW presentation, not merely a correctly sized blank reader.
            attachSessionView(session)
        }
    }

    /**
     * Rearms Window-backed capture for the SAME live owner. The ImageReader remains only the
     * virtual-display sink; publication always comes from a causally committed private Window.
     */
    fun rearmCapture(
        hostingGeneration: Int,
        boundSink: FrameSink?,
        session: PhoneBrowserSession,
        freshFrameRequest: FreshFrameRequest?,
        deadlineElapsedMs: Long = Long.MAX_VALUE,
    ): Boolean {
        val shown = presentation
        if (!ownerPhase.isActive() || imageReader == null || boundSink == null ||
            freshFrameRequest == null || shown == null || captureThread == null ||
            captureHandler == null || session.view() == null) return false
        ensureReadbackThread()
        val binding = CaptureBinding(
            boundSink, session, hostingGeneration, freshFrameRequest, deadlineElapsedMs,
            ++captureAuthoritySerial, session.documentIdentity(), shown, shown.windowIdentity(),
            profileSerial, width, height, densityDpi,
        )
        synchronized(nativeLock) {
            cancelPendingFrameLocked()
            resetCaptureDiagnostics()
            frameSink = boundSink
            captureBinding = binding
            activeCaptureCycle = null
            trailingCaptureDemand = false
            captureTerminalFailure = false
            frameSequence = 0
            captureGeneration = hostingGeneration
            captureActive = true
            captureReleased = false
        }
        imageReader?.setOnImageAvailableListener(::onImageAvailable, captureHandler)
        scheduleDrainThenCapture()
        Log.i(TAG, "rearmCapture gen=$hostingGeneration window=" + binding.windowIdentity)
        return true
    }

    /**
     * Starts Window-backed capture for a NEW owner. Pixel readback is serialized separately from
     * the ImageReader drainer so a blocking GPU fence can never starve the display sink.
     */
    fun startCapture(
        hostingGeneration: Int,
        boundSink: FrameSink?,
        session: PhoneBrowserSession,
        freshFrameRequest: FreshFrameRequest?,
        deadlineElapsedMs: Long = Long.MAX_VALUE,
    ): Boolean {
        val shown = presentation
        if (imageReader == null || boundSink == null || freshFrameRequest == null ||
            shown == null || session.view() == null) return false
        if (!ownerPhase.beginActive()) {
            Log.i(TAG, "startCapture deferred: owner phase=" + ownerPhase.phase())
            return false
        }
        if (captureThread == null) {
            val thread = HandlerThread("EyeBrowseHostingCapture")
            thread.start()
            captureThread = thread
            captureHandler = Handler(thread.looper)
            retainedCaptureThread = thread
        }
        ensureReadbackThread()
        val binding = CaptureBinding(
            boundSink, session, hostingGeneration, freshFrameRequest, deadlineElapsedMs,
            ++captureAuthoritySerial, session.documentIdentity(), shown, shown.windowIdentity(),
            profileSerial, width, height, densityDpi,
        )
        synchronized(nativeLock) {
            cancelPendingFrameLocked()
            resetCaptureDiagnostics()
            frameSink = boundSink
            captureBinding = binding
            activeCaptureCycle = null
            trailingCaptureDemand = false
            captureTerminalFailure = false
            frameSequence = 0
            captureGeneration = hostingGeneration
            captureActive = true
            captureReleased = false
        }
        Log.i(TAG, "startCapture gen=$hostingGeneration reader=" + (imageReader != null) +
            " window=" + binding.windowIdentity)
        imageReader?.setOnImageAvailableListener(::onImageAvailable, captureHandler)
        scheduleDrainThenCapture()
        return true
    }

    private fun ensureReadbackThread() {
        if (readbackThread?.isAlive == true && readbackHandler != null) return
        val thread = HandlerThread("EyeBrowseWindowReadback")
        thread.start()
        readbackThread = thread
        readbackHandler = Handler(thread.looper)
        retainedReadbackThread = thread
    }

    /** Drain only the display sink, then explicitly request one producer-bound Window cycle. */
    private fun scheduleDrainThenCapture() {
        captureHandler?.post {
            drainPendingImages()
            requestCaptureDemand()
        }
    }

    private fun drainPendingImages() {
        val maxDrain = 8 // maxImages=2 plus settling headroom; bounded by construction.
        var drained = 0
        drain@ while (drained < maxDrain) {
            val stale: Image
            synchronized(nativeLock) {
                val reader = imageReader
                if (reader == null) {
                    break@drain
                }
                val acquired = reader.acquireLatestImage()
                if (acquired == null) {
                    break@drain
                }
                stale = acquired
            }
            try {
                drained++
            } finally {
                stale.close()
            }
        }
        if (drained > 0) {
            Log.i(TAG, "drained $drained stale capture image(s)")
        }
    }

    /**
     * Stops frame delivery for revocation; the capture owner (thread/reader) stays retained for
     * same-owner reacquisition via {@link #rearmCapture}. Native resources leave
     * only through the owning retirement path.
     */
    fun stopCapture() {
        synchronized(nativeLock) {
            captureActive = false
            frameSink = null
            captureBinding = null
            activeCaptureCycle = null
            trailingCaptureDemand = false
            captureTerminalFailure = false
            cancelPendingFrameLocked()
        }
    }

    /** Called with nativeLock held; invalidates even an already-dequeued old wakeup. */
    private fun cancelPendingFrameLocked() {
        pendingFrameTask?.let { pendingFrameHandler?.removeCallbacks(it) }
        pendingFrameTask = null
        pendingFrameHandler = null
        deliveryThrottle.reset()
    }

    fun isCapturing(): Boolean {
        return captureActive && hasReader()
    }

    /**
     * Releases the capture reader/surface/thread as one coherent ownership transition (R2): the
     * owner enters {@code RETIRING} synchronously (no replacement capture can start), the
     * retiring reader is snapshotted and the current field released, while an already-issued
     * PixelCopy keeps its request-owned bitmap until actual completion. With a live sink thread the
     * close executes on that thread after all pending callbacks (serialized by nativeLock and the
     * handler queue); without one it executes inline. Nothing joins under a controller monitor;
     * quiescence is observable via {@link #isQuiescent()} and the quiescence callback.
     */
    fun releaseCaptureResources() {
        if (ownerPhase.isRetiring()) {
            return // Retirement already requested and outstanding; do not re-post.
        }
        if (ownerPhase.isActive() && !ownerPhase.beginRetiring()) {
            return // Concurrent transition; treat as already retiring.
        }
        // ACTIVE enters RETIRING above; IDLE/QUIESCENT hosts only residual resources (e.g. the
        // never-leased reader) and releases them inline without a phase transition.
        stopCapture() // Revoke/cancel the metadata-only trailing wakeup before native teardown.
        captureReleased = true
        teardownComplete = false

        // Detach the VirtualDisplay backing Surface BEFORE destroying the ImageReader that owns
        // it. A surviving display is paused cleanly and can later resume on a fresh reader instead
        // of remaining bound to an abandoned buffer queue.
        val display = virtualDisplay
        if (display != null) {
            try {
                display.setSurface(null)
                displaySurfaceDetached = true
            } catch (error: RuntimeException) {
                Log.w(TAG, "could not detach capture surface before reader close", error)
            }
        }

        val retiringReader: ImageReader?
        synchronized(nativeLock) {
            retiringReader = imageReader
            // Current reader releases now; a native PixelCopy destination remains request-owned
            // until its actual completion callback, even after authority was revoked.
            imageReader = null
        }
        val copyThread = readbackThread
        if (copyThread != null) {
            copyThread.quitSafely()
            readbackThread = null
            readbackHandler = null
            retainedReadbackThread = copyThread
        }
        val thread = captureThread
        val handler = captureHandler
        if (thread != null && handler != null) {
            handler.post { finishRetirement(retiringReader) }
            thread.quitSafely() // Pending drainer callbacks and teardown run before exit.
            captureThread = null
            captureHandler = null
        } else {
            finishRetirement(retiringReader) // No display-sink capture path; inline.
        }
    }

    /**
     * The owning teardown close: retires only the snapshot references of the retiring owner
     * (never the current mutable fields a replacement may already use) and marks the teardown
     * task complete. Phase QUIESCENT is NOT set here: with a live capture thread this executes
     * on that thread BEFORE it exits, so quiescence is declared only later, on the main thread,
     * once the thread has actually terminated ({@link #evaluateRetirementCompletion()}).
     */
    private fun finishRetirement(retiringReader: ImageReader?) {
        if (retiringReader != null) {
            try {
                retiringReader.close()
            } catch (ignored: RuntimeException) {
                // Serialized with the sink drainer; platform refusal must not block teardown.
            }
        }
        teardownComplete = true
        onQuiesced?.run()
    }

    /**
     * Main-thread retirement completion check: declares the owner QUIESCENT only when the
     * teardown task has completed AND the retiring capture thread has actually exited — a
     * still-running thread is never quiescent (R2). Fires the quiescence callback once on the
     * transition. Returns true when this call reached quiescence.
     */
    fun evaluateRetirementCompletion(): Boolean {
        if (ownerPhase.phase() != CaptureOwnerPhase.Phase.RETIRING || !teardownComplete) {
            return false
        }
        val thread = retainedCaptureThread
        if (thread != null && thread.isAlive) {
            return false // The retiring owner's sink-drainer thread is still terminating.
        }
        val copyThread = retainedReadbackThread
        if (copyThread != null && copyThread.isAlive) {
            return false // PixelCopy invocation may still be blocked on a GPU fence.
        }
        if (synchronized(nativeLock) { inFlightWindowCopy != null }) {
            return false // Native copy returned, but its completion/bitmap retirement is pending.
        }
        if (ownerPhase.completeRetirement()) {
            Log.i(TAG, "capture retirement quiescent")
            onQuiesced?.run()
            return true
        }
        return false
    }

    /** Full teardown for Stop: detaches the session view, dismisses, releases the display. */
    fun release(session: PhoneBrowserSession?) {
        cancelProfileResize?.invoke()
        stopCapture()
        if (session != null) {
            detachSessionView(session)
        }
        val shown = presentation
        if (shown != null) {
            try {
                shown.setUnavailableListener(null)
                shown.setDrawListener(null)
                shown.dismiss()
            } catch (ignored: RuntimeException) {
                // A dismissed presentation must not block teardown.
            }
            presentation = null
        }
        val display = virtualDisplay
        if (display != null) {
            display.release()
            virtualDisplay = null
        }
        // A failed surface replacement may have attached the staged producer: close only AFTER
        // the exact display is destroyed. No fallback window or old authority is resurrected.
        stagedProfileReader?.let { runCatching { it.close() } }
        stagedProfileReader = null
        if (!retiringProfileCloseScheduled) {
            retiringProfileReader?.let { runCatching { it.close() } }
            retiringProfileReader = null
        }
        releaseCaptureResources()
    }

    // Resource introspection used by tests and cleanup evidence.

    fun hasDisplay(): Boolean {
        return virtualDisplay != null
    }

    fun hasPresentation(): Boolean {
        return presentation != null
    }

    fun hasReader(): Boolean {
        synchronized(nativeLock) {
            return imageReader != null
        }
    }

    /**
     * True while any live capture resource remains: an open reader, a still-running capture
     * thread, or teardown that has been requested but has not yet completed. Never reports a
     * phantom resource for capture that never started (thread liveness is observed, not inferred
     * from flags).
     */
    fun hasLiveCaptureResources(): Boolean {
        if (hasReader()) {
            return true
        }
        val thread = retainedCaptureThread
        if (thread != null && thread.isAlive) {
            return true
        }
        val copyThread = retainedReadbackThread
        if (copyThread != null && copyThread.isAlive) {
            return true
        }
        if (synchronized(nativeLock) { inFlightWindowCopy != null }) {
            return true
        }
        return !teardownComplete
    }

    /** Completion marker for the requested capture teardown. */
    fun isTeardownComplete(): Boolean {
        return teardownComplete
    }

    /** True when a previous capture owner is still retiring (R2). */
    fun isRetiring(): Boolean {
        return ownerPhase.isRetiring()
    }

    /** True when replacement capture ownership is safe (no outstanding teardown). */
    fun isQuiescent(): Boolean {
        return ownerPhase.isQuiescent() && teardownComplete
    }

    /** True while a capture owner is live (frame production possible). */
    fun isOwnerActive(): Boolean {
        return ownerPhase.isActive()
    }

    private fun resetCaptureDiagnostics() {
        captureArmSerial += 1
        readerCallbackCount = 0
        acquiredImageCount = 0
        deliveredFrameCount = 0
        lastReaderCallbackElapsedMs = 0
        lastAcquiredWidth = 0
        lastAcquiredHeight = 0
        deferredWakeupCount = 0
        coalescedCallbackCount = 0
        deferredDeliveryCount = 0
        lastObservedDrawSerial = 0
        lastObservedDrawElapsedMs = 0
        lastVisualRequestId = 0
        lastCommittedDrawSerial = 0
        lastCopyInvokedElapsedMs = 0
        lastCopyCompletedElapsedMs = 0
        lastCopyResult = Int.MIN_VALUE
        copyRecoveryCount = 0
    }

    /** Sparse no-content diagnostics used by acceptance failures and log reconciliation. */
    fun captureDiagnostics(): String {
        return "arm=" + captureArmSerial +
                " callbacks=" + readerCallbackCount +
                " acquired=" + acquiredImageCount +
                " delivered=" + deliveredFrameCount +
                " deferredWakeups=" + deferredWakeupCount +
                " coalesced=" + coalescedCallbackCount +
                " deferredDeliveries=" + deferredDeliveryCount +
                " pendingFrame=" + synchronized(nativeLock) { deliveryThrottle.pending != null } +
                " activeCycle=" + synchronized(nativeLock) { activeCaptureCycle?.id ?: 0 } +
                " nativeCopy=" + synchronized(nativeLock) { inFlightWindowCopy?.cycleId ?: 0 } +
                " terminal=" + synchronized(nativeLock) { captureTerminalFailure } +
                " draw=" + lastObservedDrawSerial + "@" + lastObservedDrawElapsedMs +
                " visual=" + lastVisualRequestId + " committedDraw=" + lastCommittedDrawSerial +
                " copyInvoke=" + lastCopyInvokedElapsedMs + " copyDone=" + lastCopyCompletedElapsedMs +
                " copyResult=" + lastCopyResult + " recoveries=" + copyRecoveryCount +
                " lastCallbackMs=" + lastReaderCallbackElapsedMs +
                " image=" + lastAcquiredWidth + "x" + lastAcquiredHeight +
                " reader=" + hasReader() +
                " detached=" + displaySurfaceDetached +
                " owner=" + ownerPhase.phase() + " display={" + displaySnapshot() + "}"
    }

    /** ImageReader is only the VirtualDisplay sink. Drain latest safely; never publish its pixels. */
    private fun onImageAvailable(reader: ImageReader) {
        synchronized(nativeLock) {
            readerCallbackCount += 1
            lastReaderCallbackElapsedMs = SystemClock.elapsedRealtime()
            if (reader !== imageReader) {
                staleReaderCallbacks += 1
                return
            }
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    acquiredImageCount += 1
                    lastAcquiredWidth = image.width
                    lastAcquiredHeight = image.height
                }
            } catch (error: RuntimeException) {
                Log.w(TAG, "display-sink drain failed", error)
            } finally {
                image?.close()
            }
        }
    }

    /** A normal app-owned container draw is the demand source; the sink queue is never freshness authority. */
    private fun onWindowDraw(serial: Long, elapsedMs: Long) {
        lastObservedDrawSerial = serial
        lastObservedDrawElapsedMs = elapsedMs
        var explicitCycle = 0L
        synchronized(nativeLock) {
            if (!captureActive || captureReleased || captureBinding == null || captureTerminalFailure) return
            activeCaptureCycle?.let {
                explicitCycle = it.id
                Log.i(TAG, "capture[" + it.id + "] hardware-draw serial=" + serial +
                    " elapsed=" + elapsedMs)
                return // The explicit causal draw belongs to this cycle.
            }
            if (inFlightWindowCopy != null) {
                if (!trailingCaptureDemand) coalescedCallbackCount += 1
                trailingCaptureDemand = true
                return
            }
        }
        requestCaptureDemand(elapsedMs)
    }

    /** One scheduled cycle plus one coalesced trailing demand; never a frame FIFO. */
    private fun requestCaptureDemand(nowMs: Long = SystemClock.elapsedRealtime()) {
        var handler: Handler? = null
        var task: Runnable? = null
        var delayMs = 0L
        synchronized(nativeLock) {
            val binding = captureBinding ?: return
            if (!captureActive || captureReleased || captureTerminalFailure ||
                binding.session.documentIdentity() != binding.documentId) return
            if (activeCaptureCycle != null || inFlightWindowCopy != null) {
                if (!trailingCaptureDemand) coalescedCallbackCount += 1
                trailingCaptureDemand = true
                return
            }
            val wakeup = deliveryThrottle.request(nowMs)
            if (wakeup == null) {
                coalescedCallbackCount += 1
                return
            }
            val h = captureHandler
            if (h == null) {
                deliveryThrottle.consume(wakeup)
                return
            }
            delayMs = (wakeup.dueElapsedMs - nowMs).coerceAtLeast(0)
            if (delayMs > 0) deferredWakeupCount += 1
            val t = Runnable { beginCaptureCycle(wakeup) }
            pendingFrameTask = t
            pendingFrameHandler = h
            handler = h
            task = t
        }
        val h = handler ?: return
        val t = task ?: return
        if (!h.postDelayed(t, delayMs)) {
            synchronized(nativeLock) {
                if (pendingFrameTask === t) cancelPendingFrameLocked()
            }
            Log.w(TAG, "capture-cycle wakeup rejected by retiring handler")
        }
    }

    private fun beginCaptureCycle(wakeup: CaptureDeliveryThrottle.Wakeup) {
        var cycle: CaptureCycle? = null
        synchronized(nativeLock) {
            if (!deliveryThrottle.consume(wakeup)) return
            pendingFrameTask = null
            pendingFrameHandler = null
            val binding = captureBinding ?: return
            if (!captureActive || captureReleased || captureTerminalFailure ||
                binding.session.documentIdentity() != binding.documentId) return
            if (activeCaptureCycle != null || inFlightWindowCopy != null) {
                trailingCaptureDemand = true
                return
            }
            if (SystemClock.elapsedRealtime() >= binding.deadlineElapsedMs) {
                captureTerminalFailure = true
                Log.w(TAG, "capture cycle missed original deadline authority=" + binding.authoritySerial)
                return
            }
            cycle = CaptureCycle(++captureCycleSerial, binding, 0)
            activeCaptureCycle = cycle
        }
        Log.i(TAG, "capture[" + checkNotNull(cycle).id + "] visual-request authority=" +
            checkNotNull(cycle).binding.authoritySerial + " profile=" +
            checkNotNull(cycle).binding.width + "x" + checkNotNull(cycle).binding.height +
            " deadline=" + checkNotNull(cycle).binding.deadlineElapsedMs)
        requestFreshForCycle(checkNotNull(cycle))
    }

    private fun beginRecoveryCycle(binding: CaptureBinding, attempt: Int) {
        var cycle: CaptureCycle? = null
        synchronized(nativeLock) {
            if (captureBinding !== binding || !captureActive || captureReleased ||
                captureTerminalFailure || activeCaptureCycle != null || inFlightWindowCopy != null ||
                binding.session.documentIdentity() != binding.documentId ||
                SystemClock.elapsedRealtime() >= binding.deadlineElapsedMs) return
            cycle = CaptureCycle(++captureCycleSerial, binding, attempt)
            activeCaptureCycle = cycle
            copyRecoveryCount += 1
        }
        requestFreshForCycle(checkNotNull(cycle))
    }

    private fun requestFreshForCycle(cycle: CaptureCycle) {
        try {
            cycle.binding.freshFrameRequest.request { visualRequestId, drawSerial ->
                onFrameCommitted(cycle, visualRequestId, drawSerial)
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "fresh Window draw request failed", error)
            abandonCaptureCycle(cycle, terminalIfCurrent = true)
        }
    }

    /** Commit callback for the exact post-visual draw; all View/window traversal stays on Main. */
    private fun onFrameCommitted(cycle: CaptureCycle, visualRequestId: Long, drawSerial: Long) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            main.post { onFrameCommitted(cycle, visualRequestId, drawSerial) }
            return
        }
        val binding = cycle.binding
        synchronized(nativeLock) {
            if (activeCaptureCycle !== cycle || captureBinding !== binding || !captureActive ||
                captureReleased || captureTerminalFailure) return
        }
        if (SystemClock.elapsedRealtime() >= binding.deadlineElapsedMs ||
            !captureGeometryCurrent(binding)) {
            abandonCaptureCycle(cycle, terminalIfCurrent = true)
            return
        }
        val shown = presentation
        val view = binding.session.view()
        val window = shown?.captureWindow()
        val observation = shown?.drawObservation()
        val source = if (view == null) null else sourceRectFor(view)
        if (shown !== binding.presentation || view == null || window == null || observation == null ||
            observation.serial < drawSerial || observation.elapsedMs <= 0 ||
            source == null || source.width() != binding.width || source.height() != binding.height) {
            abandonCaptureCycle(cycle, terminalIfCurrent = true)
            return
        }
        val bitmap = try {
            Bitmap.createBitmap(binding.width, binding.height, Bitmap.Config.ARGB_8888)
        } catch (error: RuntimeException) {
            Log.w(TAG, "PixelCopy destination allocation failed", error)
            abandonCaptureCycle(cycle, terminalIfCurrent = true)
            return
        }
        val request = WindowCopyRequest(
            cycle.id, binding, cycle.attempt, visualRequestId, observation.serial,
            observation.elapsedMs, window, Rect(source), bitmap,
        )
        Log.i(TAG, "capture[" + cycle.id + "] frame-commit visual=" + visualRequestId +
            " draw=" + observation.serial + " window=" + binding.windowIdentity +
            " source=" + source + " dest=" + binding.width + "x" + binding.height)
        var invoke: Handler? = null
        synchronized(nativeLock) {
            if (activeCaptureCycle !== cycle || captureBinding !== binding || !captureActive ||
                captureReleased || captureTerminalFailure) {
                bitmap.recycle()
                return
            }
            activeCaptureCycle = null
            inFlightWindowCopy = request
            lastVisualRequestId = visualRequestId
            lastCommittedDrawSerial = observation.serial
            invoke = readbackHandler
        }
        val readback = invoke
        if (readback == null || !readback.post { invokePixelCopy(request) }) {
            onPixelCopyFinished(request, PixelCopy.ERROR_UNKNOWN)
        }
    }

    /** Public API26 Window overload, invoked off Main and off the sink-drainer thread. */
    private fun invokePixelCopy(request: WindowCopyRequest) {
        lastCopyInvokedElapsedMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "capture[" + request.cycleId + "] pixelcopy-invoke thread=" +
            Thread.currentThread().name + " visual=" + request.visualRequestId +
            " draw=" + request.drawSerial + " source=" + request.sourceRect)
        try {
            PixelCopy.request(
                request.window,
                Rect(request.sourceRect), // API31 Window overload mutates its Rect; never reuse it.
                request.bitmap,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    lastCopyCompletedElapsedMs = SystemClock.elapsedRealtime()
                    onPixelCopyFinished(request, result)
                },
                main,
            )
        } catch (error: RuntimeException) {
            Log.w(TAG, "Window PixelCopy invocation failed", error)
            main.post {
                lastCopyCompletedElapsedMs = SystemClock.elapsedRealtime()
                onPixelCopyFinished(request, PixelCopy.ERROR_UNKNOWN)
            }
        }
    }

    private fun onPixelCopyFinished(request: WindowCopyRequest, result: Int) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            main.post { onPixelCopyFinished(request, result) }
            return
        }
        lastCopyResult = result
        Log.i(TAG, "capture[" + request.cycleId + "] pixelcopy-complete result=" + result +
            " elapsed=" + lastCopyCompletedElapsedMs)
        if (result != PixelCopy.SUCCESS) {
            retireWindowCopy(
                request,
                allowRecovery = result == PixelCopy.ERROR_SOURCE_NO_DATA || result == PixelCopy.ERROR_TIMEOUT,
                terminalIfCurrent = result != PixelCopy.ERROR_SOURCE_NO_DATA && result != PixelCopy.ERROR_TIMEOUT,
            )
            return
        }
        if (!captureCopyStillCurrent(request)) {
            retireWindowCopy(request, allowRecovery = false, terminalIfCurrent = true)
            return
        }
        val handler = synchronized(nativeLock) {
            if (inFlightWindowCopy === request) captureHandler else null
        }
        if (handler == null || !handler.post { deliverWindowCopy(request) }) {
            retireWindowCopy(request, allowRecovery = false, terminalIfCurrent = false)
        }
    }

    /** SUCCESS still must belong to the same document/window/profile/source rectangle at completion. */
    private fun captureCopyStillCurrent(request: WindowCopyRequest): Boolean {
        val binding = request.binding
        if (captureBinding !== binding || !captureActive || captureReleased ||
            captureTerminalFailure || SystemClock.elapsedRealtime() > binding.deadlineElapsedMs ||
            binding.session.documentIdentity() != binding.documentId ||
            presentation !== binding.presentation || profileSerial != binding.profileSerial ||
            binding.presentation.windowIdentity() != binding.windowIdentity ||
            binding.presentation.captureWindow() !== request.window ||
            !captureGeometryCurrent(binding)) return false
        val view = binding.session.view() ?: return false
        return sourceRectFor(view) == request.sourceRect
    }

    /** Exact source geometry: native/display/reader/view remain separately checked from renderer CSS. */
    private fun captureGeometryCurrent(binding: CaptureBinding): Boolean {
        val shown = presentation ?: return false
        val view = binding.session.view() ?: return false
        if (shown !== binding.presentation || binding.session.documentIdentity() != binding.documentId ||
            view.parent !== shown.container() || shown.windowIdentity() != binding.windowIdentity ||
            profileSerial != binding.profileSerial || !shown.isAvailable()) return false
        val g = profileGeometry(binding.session)
        return g.display.valid && g.display.state == Display.STATE_ON && !g.display.surfaceDetached &&
            g.display.actualWidth == binding.width && g.display.actualHeight == binding.height &&
            g.display.readerWidth == binding.width && g.display.readerHeight == binding.height &&
            g.density == binding.densityDpi && g.decorWidth == binding.width && g.decorHeight == binding.height &&
            g.containerWidth == binding.width && g.containerHeight == binding.height &&
            g.measuredWidth == binding.width && g.measuredHeight == binding.height &&
            g.viewWidth == binding.width && g.viewHeight == binding.height &&
            g.profileSerial == binding.profileSerial && g.windowId == binding.windowIdentity &&
            g.readerOverlap == 1 && (!localFocusForRg || g.localFocus)
    }

    private fun sourceRectFor(view: android.view.View): Rect? {
        if (view.width <= 0 || view.height <= 0 || !view.isAttachedToWindow) return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun deliverWindowCopy(request: WindowCopyRequest) {
        val hash = try {
            contentHash(request.bitmap)
        } catch (error: RuntimeException) {
            Log.w(TAG, "copied bitmap hash failed", error)
            retireWindowCopy(request, allowRecovery = false, terminalIfCurrent = true)
            return
        }
        var frame: HostingFrame? = null
        var scheduleTrailing = false
        synchronized(nativeLock) {
            val binding = request.binding
            if (inFlightWindowCopy !== request || captureBinding !== binding || !captureActive ||
                captureReleased || captureTerminalFailure ||
                binding.session.documentIdentity() != binding.documentId) {
                // Authority changed after Main revalidation; old pixels are never reheadered.
            } else {
                frameSequence += 1
                frame = HostingFrame(
                    request.bitmap, binding.width, binding.height, binding.generation, frameSequence,
                    request.drawElapsedMs, hash,
                )
                deliveryThrottle.delivered(SystemClock.elapsedRealtime())
                inFlightWindowCopy = null
                deliveredFrameCount += 1
                if (trailingCaptureDemand) {
                    trailingCaptureDemand = false
                    scheduleTrailing = true
                }
            }
        }
        val delivered = frame
        if (delivered == null) {
            retireWindowCopy(request, allowRecovery = false, terminalIfCurrent = false)
            return
        }
        Log.i(TAG, "capture[" + request.cycleId + "] publish seq=" + delivered.sequence +
            " drawElapsed=" + delivered.captureElapsedMs + " size=" +
            delivered.width + "x" + delivered.height)
        try {
            request.binding.sink.onFrame(delivered)
        } finally {
            if (!request.bitmap.isRecycled) request.bitmap.recycle()
            if (scheduleTrailing) requestCaptureDemand()
            if (ownerPhase.isRetiring()) onQuiesced?.run()
        }
    }

    private fun retireWindowCopy(
        request: WindowCopyRequest,
        allowRecovery: Boolean,
        terminalIfCurrent: Boolean,
    ) {
        var recovery: CaptureBinding? = null
        var scheduleSuccessor = false
        synchronized(nativeLock) {
            if (inFlightWindowCopy !== request) {
                if (!request.bitmap.isRecycled) request.bitmap.recycle()
                return
            }
            inFlightWindowCopy = null
            val sameAuthority = captureBinding === request.binding && captureActive && !captureReleased
            val sameDocument = request.binding.session.documentIdentity() == request.binding.documentId
            if (sameAuthority && sameDocument && allowRecovery && request.attempt == 0 &&
                SystemClock.elapsedRealtime() < request.binding.deadlineElapsedMs) {
                recovery = request.binding
            } else {
                if (sameAuthority && terminalIfCurrent) captureTerminalFailure = true
                if (captureActive && captureBinding != null && !captureTerminalFailure &&
                    trailingCaptureDemand) {
                    trailingCaptureDemand = false
                    scheduleSuccessor = true
                }
            }
        }
        if (!request.bitmap.isRecycled) request.bitmap.recycle()
        val binding = recovery
        if (binding != null) {
            captureHandler?.post { beginRecoveryCycle(binding, request.attempt + 1) }
        } else if (scheduleSuccessor) {
            requestCaptureDemand()
        }
        if (ownerPhase.isRetiring()) onQuiesced?.run()
    }

    private fun abandonCaptureCycle(cycle: CaptureCycle, terminalIfCurrent: Boolean) {
        var scheduleTrailing = false
        synchronized(nativeLock) {
            if (activeCaptureCycle !== cycle) return
            activeCaptureCycle = null
            val sameAuthority = captureBinding === cycle.binding && captureActive && !captureReleased
            if (sameAuthority && terminalIfCurrent) captureTerminalFailure = true
            if (sameAuthority && !captureTerminalFailure && trailingCaptureDemand) {
                trailingCaptureDemand = false
                scheduleTrailing = true
            }
        }
        if (scheduleTrailing) requestCaptureDemand()
    }

    /** CRC32 over copied ARGB pixels; diagnostic only, never a freshness/admission oracle. */
    private fun contentHash(bitmap: Bitmap): Long {
        val crc = CRC32()
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (pixel in row) {
                crc.update((pixel ushr 24) and 0xff)
                crc.update((pixel ushr 16) and 0xff)
                crc.update((pixel ushr 8) and 0xff)
                crc.update(pixel and 0xff)
            }
        }
        return crc.value
    }

    /** Real platform resources; test failure injection substitutes this factory. */
    class PlatformFactory : Factory {

        override fun createVirtualDisplay(manager: DisplayManager, name: String, width: Int,
                height: Int, densityDpi: Int, surface: Any?): VirtualDisplay? {
            return manager.createVirtualDisplay(name, width, height, densityDpi,
                    surface as android.view.Surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                            or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
        }

        override fun createImageReader(width: Int, height: Int): ImageReader? {
            return ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        }

        override fun createPresentation(context: Context, display: Display): PresentationHost {
            return HostingPresentation(context, display)
        }
    }

    private class HostingPresentation(outerContext: Context, display: Display) :
            android.app.Presentation(outerContext, display), PresentationHost {

        // Presentation.getContext(): a display/window context on API31+, NOT the outer service.
        private val content = object : FrameLayout(this@HostingPresentation.context) {
            var captureDrawSerial: Long = 0
            var captureDrawElapsedMs: Long = 0
            var captureDrawListener: DrawListener? = null

            override fun dispatchDraw(canvas: android.graphics.Canvas) {
                super.dispatchDraw(canvas)
                captureDrawSerial += 1
                captureDrawElapsedMs = SystemClock.elapsedRealtime()
                captureDrawListener?.onDraw(captureDrawSerial, captureDrawElapsedMs)
            }
        }.apply { setBackgroundColor(Color.WHITE) }
        private var focusView: android.view.View? = null
        private var localFocusRequested = false
        private var focusListener: Runnable? = null
        private var reportedFocusReady = false
        private var lostFocusSerial = 0L
        private val windowFocus = android.view.ViewTreeObserver.OnWindowFocusChangeListener {
            reportLocalFocus()
        }
        private val viewFocus = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
            reportLocalFocus()
        }

        private fun reportLocalFocus() {
            val ready = focusView?.let(::localFocusReady) == true
            if (ready != reportedFocusReady) {
                reportedFocusReady = ready
                if (!ready) lostFocusSerial++
                focusListener?.run()
            }
        }
        private val acquireLocalFocus = Runnable {
            val view = focusView
            if (view != null && isShowing && content.isAttachedToWindow &&
                view.isAttachedToWindow && view.parent === content && !localFocusRequested) {
                view.requestFocus() // Select the browser within this non-global local window.
                window?.setLocalFocus(true, true)
                localFocusRequested = true
                Log.i(TAG, "local focus requested display=${display.displayId}")
            }
        }
        private val focusAttachment = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) {
                if (view === focusView) content.post(acquireLocalFocus)
            }
            override fun onViewDetachedFromWindow(view: android.view.View) {
                if (view === focusView) focusAttachedView(null)
            }
        }

        override fun focusAttachedView(view: android.view.View?) {
            check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            if (view === focusView) return
            content.removeCallbacks(acquireLocalFocus)
            focusView?.removeOnAttachStateChangeListener(focusAttachment)
            focusView = null // Fence queued callbacks before retirement of this window.
            reportLocalFocus()
            if (localFocusRequested && content.isAttachedToWindow) {
                window?.setLocalFocus(false, true)
                Log.i(TAG, "local focus released display=${display.displayId}")
            }
            localFocusRequested = false
            focusView = view
            view?.addOnAttachStateChangeListener(focusAttachment)
            if (view?.isAttachedToWindow == true) content.post(acquireLocalFocus)
        }

        override fun onStop() {
            focusAttachedView(null)
            content.captureDrawListener = null
            content.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocus)
            content.viewTreeObserver.removeOnGlobalFocusChangeListener(viewFocus)
            focusListener = null
            super.onStop()
        }

        override fun localFocusReady(view: android.view.View): Boolean =
            view === focusView && isShowing && content.isAttachedToWindow &&
                view.isAttachedToWindow && view.parent === content && view.hasWindowFocus() && view.hasFocus()

        override fun setLocalFocusListener(listener: Runnable?) { focusListener = listener }

        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            super.onCreate(savedInstanceState)
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window?.apply {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    android.view.WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE)
                clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.WHITE))
                setDecorFitsSystemWindows(false)
                attributes = attributes.apply { setFitInsetsTypes(0) }
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            setContentView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            content.viewTreeObserver.addOnWindowFocusChangeListener(windowFocus)
            content.viewTreeObserver.addOnGlobalFocusChangeListener(viewFocus)
        }

        override fun container(): FrameLayout = content
        override fun windowIdentity(): Int = System.identityHashCode(window)
        override fun focusLossSerial(): Long = lostFocusSerial
        override fun captureWindow(): Window? = window
        override fun drawObservation(): DrawObservation =
            DrawObservation(content.captureDrawSerial, content.captureDrawElapsedMs)
        override fun setDrawListener(listener: DrawListener?) {
            content.captureDrawListener = listener
        }
        override fun isAvailable(): Boolean = isShowing && display.isValid
        override fun setUnavailableListener(listener: Runnable?) {
            setOnDismissListener(if (listener == null) null else
                android.content.DialogInterface.OnDismissListener { listener.run() })
        }
    }

    companion object {
        private const val TAG = "EyeBrowseHosting"
        private const val DISPLAY_NAME = "EyeBrowseHosting"

        /**
         * Pure row-packing core (JVM-testable): copies {@code rows} tight {@code rowBytes} rows from a
         * stride-padded buffer of declared {@code sourceLimit} into a fresh direct buffer. The limit is
         * captured by the caller because per-row slicing mutates the working buffer's limit.
         */
        @JvmStatic
        fun packRows(source: ByteBuffer, sourceLimit: Int, rows: Int, rowStride: Int,
                rowBytes: Int): ByteBuffer {
            val packed = ByteBuffer.allocateDirect(rowBytes * rows)
            for (y in 0 until rows) {
                val rowStart = y * rowStride
                if (rowStart + rowBytes > sourceLimit) {
                    break // Buffer holds a short tail row; packed output stays smaller than requested.
                }
                source.limit(rowStart + rowBytes).position(rowStart)
                packed.put(source)
            }
            packed.flip()
            return packed
        }
    }
}
