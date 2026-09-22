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
import com.code2hack.eyebrowse.core.link.control.BrowserAction
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
    private val commands = commandSequence(context.applicationContext)
    private var pendingCommand: String? = null
    private var actionTimeout: Runnable? = null
    private val handoff=PendingHandoff()
    private var handoffTimeout: Runnable?=null
    private var inputRevision=0L
    @Volatile var lastActionResult: BrowserActionResultMessage? = null
        private set
    @Volatile var lastHandoffResult: HandoffResultMessage? = null
        private set
    @Volatile internal var lastHandoffRequest: HandoffRequestMessage? = null
        private set

    fun canAct(): Boolean = synchronized(lock) {
        val current = state
        compatible && !closed && pendingCommand == null && !handoff.busy && current?.owner == ControlOwner.RG &&
            !current.stale && !current.loading && current.profile == measuredProfile && lastFrameHeader?.context == current.context
    }
    fun canHandoff(): Boolean = synchronized(lock) { compatible && !closed && state != null && !handoff.busy }

    internal fun inputSnapshot(): RgInputSnapshot = synchronized(lock) {
        RgInputSnapshot(state?.context,state?.owner,measuredProfile,inputRevision,canAct(),canHandoff(),
            state?.canGoBack==true,state?.canGoForward==true)
    }
    /** Validation and the existing reservation/send path share this monitor; no old-point/new-context gap. */
    internal fun dispatchIfCurrent(expected: RgInputSnapshot, dispatch: ()->Boolean): Boolean = synchronized(lock) {
        if (closed || inputSnapshot()!=expected) false else dispatch()
    }

    fun back(): String? = action(BrowserAction.Back)
    fun forward(): String? = action(BrowserAction.Forward)
    fun reload(): String? = action(BrowserAction.Reload)
    fun activateAt(x: Float, y: Float): String? = action(BrowserAction.ActivateAt(x,y))
    fun scrollBy(dx: Float, dy: Float): String? = action(BrowserAction.ScrollBy(dx,dy))

    private fun action(action: BrowserAction): String? = synchronized(lock) {
        if (!canAct()) return@synchronized null
        val current = checkNotNull(state)
        val request = commands.next(current.context,action) ?: run {
            status("Cannot reserve action — use Phone recovery")
            return@synchronized null
        }
        pendingCommand = request.commandId
        inputRevision++
        if (!client.sendControl(request)) {
            pendingCommand = null
            status("Action not sent")
            return@synchronized null
        }
        status("Waiting for page")
        val timeout = Runnable { synchronized(lock) {
            if (pendingCommand == request.commandId) {
                pendingCommand = null
                status("Could not confirm action — not retried")
            }
        } }
        actionTimeout = timeout
        main.postDelayed(timeout,5_000)
        request.commandId
    }

    fun requestPhone(): Boolean = requestHandoff(HandoffTargetWire.PHONE)

    private fun requestHandoff(target: HandoffTargetWire): Boolean = synchronized(lock) {
        val current=state ?: return@synchronized false
        if (!canHandoff() || (target==HandoffTargetWire.RG)!=(current.owner==ControlOwner.PHONE)) return@synchronized false
        val profile=if(target==HandoffTargetWire.RG) measuredProfile ?: return@synchronized false else null
        val request=HandoffRequestMessage(target,current.context.controlEpoch,profile)
        lastHandoffRequest=request
        inputRevision++
        if(!handoff.request(current.context,request,client::sendControl)) {
            status("Control request not sent");return@synchronized false
        }
        handoffTimeout?.let(main::removeCallbacks)
        val timeout=Runnable { synchronized(lock) {
            handoff.clear();handoffTimeout=null;inputRevision++
            status("Control change not confirmed — not retried")
        } }
        handoffTimeout=timeout;main.postDelayed(timeout,5_000)
        status("Waiting for control")
        true
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

    fun measure(width: Int, height: Int, densityDpi: Int): Unit = synchronized(lock) {
        val next=PresentationProfile.fromMeasured(width,height,densityDpi)
        if(next!=measuredProfile) inputRevision++
        measuredProfile = next
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
    fun requestPresentation(): Boolean = requestHandoff(HandoffTargetWire.RG)
    // Engine lifecycle callbacks can hold its operation lock. Do not take our monitor from that
    // lock while main-thread dispatch holds our monitor and calls sendControl in the other direction.
    private fun onMain(block: ()->Unit) {
        if(Looper.myLooper()==Looper.getMainLooper()) block() else main.post { if(!closed) block() }
    }
    override fun onStateChange(state: PairingState) = onMain { status(state.name) }
    override fun onStatus(status: HostStatusValue) = onMain { if (state?.owner != ControlOwner.RG) status("Host: $status") }
    override fun onPresentationCompatibility(result: CapabilityNegotiation) = onMain { synchronized(lock) {
        val next=result == CapabilityNegotiation.Accepted
        if(next!=compatible) inputRevision++
        compatible = next
        if (!compatible) invalidate("Update apps to use presentation")
    } }
    override fun onLinkLost() = onMain { synchronized(lock) { compatible = false; invalidate("Disconnected — page stale; Retry") } }
    override fun onConnectFailed(error: LinkError) = onMain { invalidate("Connection failed: ${error.javaClass.simpleName}") }
    override fun onControl(message: BrowserControlMessage): Unit = synchronized(lock) {
        when (message) {
            is BrowserStateMessage -> {
                val old=state
                if(old==null || old.context!=message.context || old.owner!=message.owner || old.profile!=message.profile ||
                    old.stale!=message.stale || old.loading!=message.loading) inputRevision++
                state = message
                handoff.observed(message.owner,message.context)
                if(!handoff.busy) { handoffTimeout?.let(main::removeCallbacks);handoffTimeout=null }
                val live = message.owner == ControlOwner.RG && !message.stale && message.profile == measuredProfile
                inbox.grant(message.context.takeIf { live }, message.profile.takeIf { live })
                status(if (!live) {
                    if (message.owner == ControlOwner.PHONE) "Browsing on Phone" else "Presentation stale"
                } else if (lastFrameHeader?.context == message.context) statusText else "Waiting for current frame")
            }
            is HandoffResultMessage -> {
                handoff.result(message)
                if(!handoff.busy) { handoffTimeout?.let(main::removeCallbacks);handoffTimeout=null }
                lastHandoffResult = message
                if (!message.accepted) status(if (message.reason == "INVALID_PROFILE") "Open Phone to return browsing" else "Control could not be transferred")
            }
            is BrowserActionResultMessage -> {
                lastActionResult = message
                if (pendingCommand == message.commandId) {
                    pendingCommand = null
                    actionTimeout?.let(main::removeCallbacks); actionTimeout = null
                    status(if (!message.accepted) "Action unavailable — page or control changed"
                        else if (message.reason != null) "Could not confirm action — not retried" else "Connected to Phone")
                }
            }
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
        synchronized(lock) {
            inputRevision++;handoff.clear();handoffTimeout?.let(main::removeCallbacks);handoffTimeout=null
            state = state?.copy(stale=true); inbox.grant(null,null); pendingCommand = null; actionTimeout?.let(main::removeCallbacks); actionTimeout = null
        }
        status(reason)
    }
    fun pause() {
        synchronized(lock) { compatible = false;invalidate("Presentation paused — Retry") }
        client.disconnect()
    }
    companion object {
        private var sequence: CommandSequence? = null
        @Synchronized private fun commandSequence(context: Context): CommandSequence {
            return sequence ?: run {
                val prefs=context.getSharedPreferences("browser-command-sequence",Context.MODE_PRIVATE)
                CommandSequence({
                    prefs.getString("lifetime",null)?.let { CommandSequence.Cursor(it,prefs.getLong("epoch",-1),prefs.getLong("sequence",0)) }
                }, { cursor ->
                    prefs.edit().putString("lifetime",cursor.lifetime).putLong("epoch",cursor.epoch)
                        .putLong("sequence",cursor.sequence).commit()
                }).also { sequence=it }
            }
        }
    }

    fun close() {
        synchronized(lock) { closed = true;inputRevision++;handoff.clear(); inbox.grant(null,null); pendingDisplay?.second?.recycle(); pendingDisplay = null }
        handoffTimeout?.let(main::removeCallbacks);handoffTimeout=null
        actionTimeout?.let(main::removeCallbacks)
        client.disconnect(); decoder.shutdownNow(); main.removeCallbacks(display); main.removeCallbacks(updateSurface)
        // ImageView may still render the last bitmap until its Activity detaches. Let GC own it.
        shown = null
    }
}
