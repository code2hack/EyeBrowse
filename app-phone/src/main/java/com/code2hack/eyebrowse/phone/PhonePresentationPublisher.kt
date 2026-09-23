package com.code2hack.eyebrowse.phone

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.control.ControlContext
import com.code2hack.eyebrowse.core.link.control.ControlSnapshot
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.core.link.framing.PresentationFrame
import com.code2hack.eyebrowse.core.link.framing.PresentationFrameHeader

/** One lease, no bitmap queue: encode the borrowed capture buffer on its existing capture thread. */
class PhonePresentationPublisher(
    private val hosting: HostingController,
    private val ready: (ControlContext) -> Boolean,
    private val send: (PresentationFrame) -> Boolean,
    private val requestFreshFrame: () -> Unit,
    private val beforeCapture: ((ControlSnapshot, () -> Unit) -> Unit)? = null,
    private val degraded: (ControlContext, String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var context: ControlContext? = null
    private var lease: HostingController.Lease? = null
    private var lastEncodedMs = Long.MIN_VALUE
    private var trailingRequest: Runnable? = null
    private var profileTimeout: Runnable? = null
    private val renew = object : Runnable {
        override fun run() {
            if (context == null) return
            lease?.renew()
            main.postDelayed(this, 1_000)
        }
    }

    /** Main-thread reconciliation. A retired callback can never publish into a replacement grant. */
    fun reconcile(state: ControlSnapshot, requestedAtMs: Long = android.os.SystemClock.elapsedRealtime()) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (state.owner != ControlOwner.RG || !state.linkAuthenticated || !state.sessionCompatible ||
            !state.hostingActive || state.profile == null) { stop(); return }
        if (context == state.context) return
        val prior = context
        val profile = checkNotNull(state.profile)
        val compatible = prior != null && prior.lifetimeId == state.context.lifetimeId &&
            prior.controlEpoch == state.context.controlEpoch && prior.documentId == state.context.documentId &&
            prior.hostingGeneration == state.context.hostingGeneration &&
            hosting.isRgPresentationOwned() && hosting.presentationProfile().densityDpi == profile.densityDpi
        if (compatible) {
            context = null // Close old encoded-frame admission BEFORE platform work.
            main.removeCallbacks(renew)
            trailingRequest?.let(main::removeCallbacks); trailingRequest = null
            profileTimeout?.let(main::removeCallbacks); profileTimeout = null
            lease = null // Hosting reconfigure revokes/transfers; DO NOT call normal release().
        } else {
            stop()
            if (!hosting.presentOnRg(HostingPresentationProfile(profile.width, profile.height, profile.densityDpi))) {
                degraded(state.context, "Presentation unavailable")
                return
            }
        }
        val grant = state.context
        context = grant
        val deadline = requestedAtMs + 2000
        val firstFrame = java.util.concurrent.atomic.AtomicBoolean()
        fun failProfile(reason: String) {
            if (context != grant) return
            stop(); hosting.stop(); degraded(grant, reason)
        }
        if (compatible) {
            val timeout = Runnable { if (!firstFrame.get()) failProfile("Profile frame deadline exceeded") }
            profileTimeout = timeout
            main.postDelayed(timeout, (deadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
        }
        fun startCapture() {
            if (context != grant) return // A late editor/profile callback cannot revive a retired lease.
            val consumer = HostingController.FrameConsumer { frame ->
                if (context != grant || frame.generation.toLong() != grant.hostingGeneration ||
                    frame.width != profile.width || frame.height != profile.height) return@FrameConsumer
                // Capture already throttles. Retain the bound across lease/context replacements too.
                if (lastEncodedMs != Long.MIN_VALUE && frame.captureElapsedMs - lastEncodedMs < HostingPolicy.MIN_FRAME_INTERVAL_MS) {
                    // A static document may have no later producer event. Request one fresh render
                    // after the remaining interval; never retain the borrowed bitmap or build a FIFO.
                    val delay = HostingPolicy.MIN_FRAME_INTERVAL_MS - (frame.captureElapsedMs - lastEncodedMs)
                    main.post {
                        if (context == grant && trailingRequest == null) {
                            val task = Runnable {
                                trailingRequest = null
                                if (context == grant) requestFreshFrame()
                            }
                            trailingRequest = task
                            main.postDelayed(task, delay)
                        }
                    }
                    return@FrameConsumer
                }
                lastEncodedMs = frame.captureElapsedMs
                try {
                    val output = BoundedFrameOutput(LinkProtocol.PRESENTATION_RECORD_MAX_BYTES - LinkProtocol.PRESENTATION_METADATA_MAX_BYTES - 4)
                    check(frame.bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, output))
                    val pixels = output.toByteArray()
                    val encoded = PresentationFrame(PresentationFrameHeader(grant, frame.sequence,
                        frame.captureElapsedMs, frame.width, frame.height), pixels)
                    if (compatible && !firstFrame.get() && android.os.SystemClock.elapsedRealtime() > deadline) {
                        main.post { failProfile("Profile frame deadline exceeded") }
                        return@FrameConsumer
                    }
                    if (context == grant && ready(grant) && send(encoded)) {
                        firstFrame.set(true)
                        Log.i("EyeBrowsePresentation", "encoded seq=${frame.sequence} capture=${frame.captureElapsedMs} bytes=${pixels.size} profile=${frame.width}x${frame.height}")
                    }
                } catch (_: Exception) {
                    main.post { if (context == grant) degraded(grant, "Frame encoding failed or exceeded limit") }
                }
            }
            lease = if (compatible) hosting.acquireProfileLease(
                HostingPresentationProfile(profile.width, profile.height, profile.densityDpi), consumer)
                else hosting.acquireLease(consumer)
            if (lease == null) {
                if (compatible) failProfile("Capture unavailable")
                else { context = null; degraded(grant, "Capture unavailable") }
            } else main.postDelayed(renew, 1_000)
        }
        // #9 revalidates/reveals the retained editor after attachment, before the first new frame.
        fun prepare() {
            if (context != grant) return
            beforeCapture?.invoke(state, ::startCapture) ?: startCapture()
        }
        if (compatible) hosting.reconfigureRgProfile(
            HostingPresentationProfile(profile.width, profile.height, profile.densityDpi), deadline) { settled ->
            if (context != grant) return@reconfigureRgProfile
            if (settled) prepare() else failProfile("In-place profile resize unavailable")
        } else prepare()
    }

    fun stop() {
        context = null
        main.removeCallbacks(renew)
        profileTimeout?.let(main::removeCallbacks); profileTimeout = null
        trailingRequest?.let(main::removeCallbacks)
        trailingRequest = null
        lease?.release()
        lease = null
    }
}
