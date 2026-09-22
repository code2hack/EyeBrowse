package com.code2hack.eyebrowse.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.ViewGroup
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
 * <li><b>Native serialization.</b> Acquired Images are acquired, copied, hashed and closed inside
 * one {@code nativeLock}-serialized section on the capture path; consumers receive only the copied
 * borrowed bitmap, never a native image, and the image is closed before delivery. Reader
 * creation, swap and close share the same lock, so no close can invalidate a buffer mid-use, and
 * a superseded reader's stale callback is recognized and dropped. Main-thread rebuilds briefly
 * take {@code nativeLock}; the controller monitor is never held while {@code nativeLock} is
 * held, and delivery runs outside both.</li>
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

    // Java original: plain (non-volatile) field; swapped from main, read on the capture path.
    private var frameSink: FrameSink? = null
    private var width: Int = 0
    private var height: Int = 0
    private var densityDpi: Int = 0

    // Capture-path state.
    private var frameSequence: Long = 0
    private val deliveryThrottle = CaptureDeliveryThrottle(HostingPolicy.MIN_FRAME_INTERVAL_MS)
    private var pendingFrameTask: Runnable? = null // nativeLock-protected; no Image/Bitmap retained
    private var pendingFrameHandler: Handler? = null
    private var frameBitmap: Bitmap? = null // nativeLock-protected
    @Volatile private var captureActive: Boolean = false
    @Volatile private var captureReleased: Boolean = false // release requested; no further admissions/copying
    @Volatile private var teardownComplete: Boolean = true
    @Volatile private var captureGeneration: Int = 0 // hosting generation stamped into produced frames

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
     * Rearms frame production for the SAME live capture owner on the current reader (R6): legal
     * only while this owner is ACTIVE with an open reader and a live capture path. Used after a
     * geometry rebuild replaced the reader underneath an existing lease. Returns {@code false}
     * when the owner is not actively capturable — the caller must surface that, never display a
     * healthy capturing state over an unarmed reader.
     */
    fun rearmCapture(hostingGeneration: Int, boundSink: FrameSink?,
            freshFrameRequest: Runnable?): Boolean {
        if (!ownerPhase.isActive() || imageReader == null || boundSink == null ||
                captureThread == null || captureHandler == null) {
            return false
        }
        synchronized(nativeLock) {
            cancelPendingFrameLocked()
            resetCaptureDiagnostics()
            frameSink = boundSink
            frameSequence = 0
            captureGeneration = hostingGeneration
            captureActive = true
            captureReleased = false
        }
        imageReader?.setOnImageAvailableListener(::onImageAvailable, captureHandler)
        scheduleDrainThenFreshFrame(freshFrameRequest)
        Log.i(TAG, "rearmCapture gen=$hostingGeneration on rebuilt reader")
        return true
    }

    /**
     * Starts (or restarts) frame production for a NEW capture owner through the caller's bound
     * sink; latest-only and throttled. The sink is retained as-is: delivery identity lives in the
     * sink, not in a reassigned callback.
     *
     * <p>Returns {@code false} when no capture surface exists or a previous capture owner is
     * still retiring (R2): reacquisition waits for quiescence instead of racing the outstanding
     * teardown. The caller retries explicitly after quiescence — a null acquisition has no
     * hidden side effects.
     */
    fun startCapture(hostingGeneration: Int, boundSink: FrameSink?,
            freshFrameRequest: Runnable?): Boolean {
        if (imageReader == null || boundSink == null) {
            return false
        }
        if (!ownerPhase.beginActive()) {
            Log.i(TAG, "startCapture deferred: owner phase=" + ownerPhase.phase())
            return false
        }
        synchronized(nativeLock) {
            cancelPendingFrameLocked()
            resetCaptureDiagnostics()
            frameSink = boundSink
            frameSequence = 0
            captureGeneration = hostingGeneration
            captureActive = true
            captureReleased = false
        }
        Log.i(TAG, "startCapture gen=$hostingGeneration reader=${imageReader != null}" +
                " threadAlive=${captureThread != null}")
        if (captureThread != null) {
            // Reacquisition after lease loss: the capture thread survived; rearm the listener.
            imageReader?.setOnImageAvailableListener(::onImageAvailable, captureHandler)
            scheduleDrainThenFreshFrame(freshFrameRequest)
            return true
        }
        val thread = HandlerThread("EyeBrowseHostingCapture")
        thread.start()
        captureThread = thread
        captureHandler = Handler(thread.looper)
        retainedCaptureThread = thread
        imageReader?.setOnImageAvailableListener(::onImageAvailable, captureHandler)
        scheduleDrainThenFreshFrame(freshFrameRequest)
        return true
    }

    /**
     * Drain any pre-arm buffers first, then request a fresh render. The ordering is intentional:
     * a static current document may otherwise have its only rendered frame consumed as "stale"
     * and never produce another callback after the listener is armed.
     */
    private fun scheduleDrainThenFreshFrame(freshFrameRequest: Runnable?) {
        captureHandler?.post {
            CaptureDrainSequence.run(
                drain = { drainPendingImages() },
                requestFresh = { freshFrameRequest?.run() },
            )
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
     * retiring reader/bitmap are snapshotted and the current fields released, and the retirement
     * closes only those snapshots on the capture path. With a live capture thread the native
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
        val retiringBitmap: Bitmap?
        synchronized(nativeLock) {
            retiringReader = imageReader
            retiringBitmap = frameBitmap
            // Current fields release now: introspection sees no live reader and any later
            // allocation creates fresh resources the old teardown will never touch.
            imageReader = null
            frameBitmap = null
        }
        val thread = captureThread
        val handler = captureHandler
        if (thread != null && handler != null) {
            handler.post { finishRetirement(retiringReader, retiringBitmap, thread) }
            thread.quitSafely() // Pending callbacks and the teardown task run first.
            captureThread = null
            captureHandler = null
        } else {
            finishRetirement(retiringReader, retiringBitmap, null) // No capture path; inline.
        }
    }

    /**
     * The owning teardown close: retires only the snapshot references of the retiring owner
     * (never the current mutable fields a replacement may already use) and marks the teardown
     * task complete. Phase QUIESCENT is NOT set here: with a live capture thread this executes
     * on that thread BEFORE it exits, so quiescence is declared only later, on the main thread,
     * once the thread has actually terminated ({@link #evaluateRetirementCompletion()}).
     */
    private fun finishRetirement(retiringReader: ImageReader?, retiringBitmap: Bitmap?,
            retiringThread: HandlerThread?) {
        if (retiringReader != null) {
            try {
                retiringReader.close()
            } catch (ignored: RuntimeException) {
                // Serialized with capture-path use; a platform refusal must not block teardown.
            }
        }
        // The retiring borrowed bitmap is dropped without recycle (a completed delivery may
        // still hold it); reclamation stays with GC, which is safe for borrowed bitmaps.
        teardownComplete = true
        onQuiesced?.run() // Signals the controller to evaluate completion on the main thread.
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
            return false // The retiring owner's thread is still terminating.
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
        stopCapture()
        if (session != null) {
            detachSessionView(session)
        }
        val shown = presentation
        if (shown != null) {
            try {
                shown.setUnavailableListener(null)
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
                " lastCallbackMs=" + lastReaderCallbackElapsedMs +
                " image=" + lastAcquiredWidth + "x" + lastAcquiredHeight +
                " reader=" + hasReader() +
                " detached=" + displaySurfaceDetached +
                " owner=" + ownerPhase.phase() + " display={" + displaySnapshot() + "}"
    }

    private fun onImageAvailable(reader: ImageReader) {
        synchronized(nativeLock) {
            readerCallbackCount += 1
            val now = SystemClock.elapsedRealtime()
            lastReaderCallbackElapsedMs = now
            val sink = frameSink ?: return
            val handler = captureHandler ?: return
            if (!captureActive || captureReleased || reader !== imageReader) return
            val wakeup = deliveryThrottle.request(now)
            if (wakeup == null) {
                coalescedCallbackCount += 1
                return // One existing wakeup will acquire the latest, not a FIFO of callbacks.
            }
            val delay = (wakeup.dueElapsedMs - now).coerceAtLeast(0)
            val deferred = delay > 0
            if (deferred) deferredWakeupCount += 1
            val task = Runnable { deliverLatestAvailable(reader, sink, wakeup, deferred) }
            pendingFrameTask = task
            pendingFrameHandler = handler
            if (!handler.postDelayed(task, delay)) {
                cancelPendingFrameLocked()
                Log.w(TAG, "capture wakeup rejected by retiring handler")
            }
        }
    }

    /**
     * Throttle BEFORE acquiring. A final static-navigation frame inside the 200ms window
     * stays in ImageReader until this one-shot wakeup, even if no more callbacks arrive.
     * No native Image or borrowed Bitmap is held while waiting; maxImages=2 is unchanged.
     */
    private fun deliverLatestAvailable(
        reader: ImageReader,
        sink: FrameSink,
        wakeup: CaptureDeliveryThrottle.Wakeup,
        deferred: Boolean,
    ) {
        var frame: HostingFrame? = null
        synchronized(nativeLock) {
            if (!deliveryThrottle.consume(wakeup)) return
            pendingFrameTask = null
            pendingFrameHandler = null
            if (!captureActive || captureReleased || reader !== imageReader ||
                    frameSink !== sink) return
            var image: Image? = null
            try {
                val latest = reader.acquireLatestImage() ?: return
                image = latest
                acquiredImageCount += 1
                lastAcquiredWidth = latest.width
                lastAcquiredHeight = latest.height
                frame = copyFrame(latest, SystemClock.elapsedRealtime())
                if (frame != null) {
                    deliveryThrottle.delivered(SystemClock.elapsedRealtime())
                    if (deferred) deferredDeliveryCount += 1
                }
            } catch (error: RuntimeException) {
                Log.w(TAG, "hosting frame capture failed", error)
            } finally {
                image?.close() // Always closed before consumer admission/delivery.
            }
        }
        val delivered = frame
        if (delivered != null) {
            deliveredFrameCount += 1
            // Immutable sink remains bound to this lease; FrameGate fences revocation.
            // Consumer invocation remains outside nativeLock and the controller monitor.
            sink.onFrame(delivered)
        }
    }

    /** Copies the acquired image into the reused borrowed bitmap (nativeLock held by caller). */
    private fun copyFrame(image: Image, captureElapsedMs: Long): HostingFrame? {
        val plane = image.planes[0]
        val rowStride = plane.rowStride
        val rowBytes = width * plane.pixelStride
        val packed = if (rowStride == rowBytes) {
            plane.buffer
        } else {
            packedRowCopy(plane, height, rowStride, rowBytes)
        } ?: return null
        var bmp = frameBitmap
        if (bmp == null || bmp.width != width || bmp.height != height) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            frameBitmap = bmp
        }
        packed.rewind()
        bmp.copyPixelsFromBuffer(packed)
        frameSequence += 1
        return HostingFrame(bmp, width, height, captureGeneration, frameSequence,
                captureElapsedMs, contentHash(plane))
    }

    /** Packs a stride-padded image plane into tight rows for {@code copyPixelsFromBuffer}. */
    private fun packedRowCopy(plane: Image.Plane, rows: Int, rowStride: Int, rowBytes: Int): ByteBuffer? {
        val source = plane.buffer.duplicate()
        return packRows(source, source.limit(), rows, rowStride, rowBytes)
    }

    /** CRC32 over every valid pixel byte, excluding row padding, so stale frames are detectable. */
    private fun contentHash(plane: Image.Plane): Long {
        val crc = CRC32()
        val source = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val rowBytes = width * plane.pixelStride
        for (y in 0 until height) {
            val rowStart = y * rowStride
            if (rowStart + rowBytes > source.limit()) {
                break
            }
            val row = source.duplicate()
            row.position(rowStart)
            row.limit(rowStart + rowBytes)
            crc.update(row)
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
        private val content = FrameLayout(this.context).apply { setBackgroundColor(Color.WHITE) }
        private var focusView: android.view.View? = null
        private var localFocusRequested = false
        private var focusListener: Runnable? = null
        private var reportedFocusReady = false
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
