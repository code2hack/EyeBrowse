package com.code2hack.eyebrowse.rg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.code2hack.eyebrowse.core.link.*
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.core.link.framing.PresentationFrame
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import com.code2hack.eyebrowse.rg.link.*
import java.util.concurrent.Executors

/** The live page receiver. No WebView, page effects, automatic ownership transfer or retry. */
class RgPresentationController(context: Context, private val surface: Surface) : RgLinkClient.Listener {
    interface Surface {
        fun status(text: String)
        fun browserState(state: BrowserStateMessage)
        /** Bitmap remains owned here; the surface must not recycle or retain replaced frames. */
        fun frame(bitmap: Bitmap)
    }
    private val main = Handler(Looper.getMainLooper())
    private val inbox = PresentationInbox()
    private val decoder = Executors.newSingleThreadExecutor { r -> Thread(r,"eyebrowse-frame-decoder") }
    private val lock = Any()
    private var decoding = false
    private var pendingDisplay: Triple<PresentationFrame,Bitmap,Long>? = null
    private var shown: Bitmap? = null
    @Volatile private var closed = false
    @Volatile private var state: BrowserStateMessage? = null
    @Volatile private var compatible = false
    @Volatile private var measuredProfile: PresentationProfile? = null
    private val client = RgLinkClient(RgLinkIdentity(), RgPairingStore(context), this)
    @Volatile var displayedFrames = 0L
        private set
    @Volatile var lastFrameHeader: com.code2hack.eyebrowse.core.link.framing.PresentationFrameHeader? = null
        private set
    @Volatile var lastReceiveToDisplayMs = 0L
        private set
    private var receiveAt = 0L
    private var statusText = "Not connected"
    private val updateSurface = Runnable {
        synchronized(lock) {
            if (!closed) {
                state?.let(surface::browserState)
                surface.status(statusText)
            }
        }
    }
    private fun status(text: String) = synchronized(lock) {
        statusText = text
        main.removeCallbacks(updateSurface)
        if (!closed) main.post(updateSurface)
        Unit
    }

    fun measure(width: Int, height: Int, densityDpi: Int) {
        measuredProfile = PresentationProfile.fromMeasured(width,height,densityDpi)
        val current = state
        if (current?.owner == ControlOwner.RG && current.profile != measuredProfile) {
            invalidate("Viewport changed — presentation stale")
            client.disconnect() // No implicit ownership/profile renegotiation on a layout callback.
        }
    }
    fun profile(): PresentationProfile? = measuredProfile
    fun browserState(): BrowserStateMessage? = state
    fun reconnect() { if (!closed) client.reconnect() }

    /** T02 production API; explicit handoff UI and bidirectional journey arrive in T03. */
    fun requestPresentation(): Boolean {
        val current = state ?: return false
        val profile = measuredProfile ?: return false
        if (!compatible || closed || current.owner != ControlOwner.PHONE) return false
        return client.sendControl(HandoffRequestMessage(HandoffTargetWire.RG,current.context.controlEpoch,profile))
    }
    override fun onStateChange(state: PairingState) = status(state.name)
    override fun onStatus(status: HostStatusValue) { if (state?.owner != ControlOwner.RG) status("Host: $status") }
    override fun onPresentationCompatibility(result: CapabilityNegotiation) {
        compatible = result == CapabilityNegotiation.Accepted
        if (!compatible) invalidate("Update apps to use presentation")
    }
    override fun onLinkLost() { compatible = false; invalidate("Disconnected — page stale; Retry") }
    override fun onConnectFailed(error: LinkError) { invalidate("Connection failed: ${error.javaClass.simpleName}") }
    override fun onControl(message: BrowserControlMessage): Unit = synchronized(lock) {
        when (message) {
            is BrowserStateMessage -> {
                state = message
                val live = message.owner == ControlOwner.RG && !message.stale && message.profile == measuredProfile
                inbox.grant(message.context.takeIf { live }, message.profile.takeIf { live })
                status(if (!live) {
                    if (message.owner == ControlOwner.PHONE) "Browsing on Phone" else "Presentation stale"
                } else if (lastFrameHeader?.context == message.context) statusText else "Waiting for current frame")
            }
            is HandoffResultMessage -> if (!message.accepted) invalidate("Presentation declined: ${message.reason}")
            is PresentationStaleMessage -> invalidate("Presentation stale: ${message.reason}")
            is PresentationStopMessage -> invalidate("Presentation stopped")
            else -> Unit
        }
    }
    override fun onPresentation(frame: PresentationFrame) {
        synchronized(lock) {
            if (closed || !inbox.offer(frame)) return
            receiveAt = SystemClock.elapsedRealtime()
            if (decoding) return
            decoding = true
            decoder.execute { decodeLoop() }
        }
    }
    private fun decodeLoop() {
        while (true) {
            val next = synchronized(lock) {
                val frame = if (closed) null else inbox.take()
                if (frame == null) { decoding = false; return }
                frame to receiveAt
            }
            decode(next.first,next.second)
        }
    }
    private fun decode(frame: PresentationFrame, received: Long) {
        if (!inbox.current(frame)) return
        var bitmap: Bitmap? = null
        try {
            val pixels = frame.pixels()
            check(pixels.size >= 12 && String(pixels,0,4,Charsets.US_ASCII) == "RIFF" &&
                String(pixels,8,4,Charsets.US_ASCII) == "WEBP")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(pixels,0,pixels.size,options)
            check(options.outWidth == frame.header.width && options.outHeight == frame.header.height)
            options.inJustDecodeBounds = false; options.inScaled = false
            bitmap = checkNotNull(BitmapFactory.decodeByteArray(pixels,0,pixels.size,options))
            check(bitmap.width == frame.header.width && bitmap.height == frame.header.height)
            synchronized(lock) {
                if (closed || !inbox.current(frame)) { bitmap.recycle(); return }
                pendingDisplay?.second?.recycle()
                pendingDisplay = Triple(frame, bitmap, received)
                main.removeCallbacks(display)
                main.post(display)
            }
        } catch (_: Exception) {
            bitmap?.recycle()
            synchronized(lock) { if (!closed && inbox.current(frame)) status("Frame decode failed — page stale") }
        }
    }
    private val display = Runnable {
        synchronized(lock) {
            val next = pendingDisplay ?: return@Runnable
            pendingDisplay = null
            if (closed || !inbox.displayed(next.first)) { next.second.recycle(); return@Runnable }
            surface.frame(next.second)
            shown?.recycle(); shown = next.second
            lastReceiveToDisplayMs = SystemClock.elapsedRealtime() - next.third
            displayedFrames++
            lastFrameHeader = next.first.header
            status("Live page · authenticated")
            Log.i("EyeBrowsePresentation", "display seq=${next.first.header.frameSeq} capture=${next.first.header.captureTsMs} local=${SystemClock.elapsedRealtime()} profile=${next.second.width}x${next.second.height} coalesced=${inbox.dropped}")
        }
    }
    private fun invalidate(reason: String) {
        synchronized(lock) { inbox.grant(null,null) }
        status(reason)
    }
    fun pause() {
        compatible = false
        invalidate("Presentation paused — Retry")
        client.disconnect()
    }
    fun close() {
        synchronized(lock) { closed = true; inbox.grant(null,null); pendingDisplay?.second?.recycle(); pendingDisplay = null }
        client.disconnect(); decoder.shutdownNow(); main.removeCallbacks(display); main.removeCallbacks(updateSurface)
        // ImageView may still render the last bitmap until its Activity detaches. Let GC own it.
        shown = null
    }
}
