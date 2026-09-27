package com.code2hack.eyebrowse.rg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.code2hack.eyebrowse.core.link.*
import com.code2hack.eyebrowse.core.link.control.*
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
        fun keyboardChanged() {}
        fun frame(bitmap: Bitmap)
    }
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val commands = commandSequence(context.applicationContext)
    private val reservationOwner = commands.claim { reservationChanged() }
    private var validatedInput: RgInputSnapshot? = null
    private var constructedActions=0L
    private var queuedActions=0L
    internal data class ActionAccounting(val consumed: Long, val constructed: Long, val queued: Long)
    internal fun actionAccounting() = synchronized(lock) {
        ActionAccounting(commands.consumedCount(),constructedActions,queuedActions)
    }
    internal fun reservationState() = commands.snapshot(reservationOwner)
    private var guardAt: Long?=null
    private val preparationGuard=Runnable { guardAt=null;reservationChanged() }
    private val reservationUpdate=Runnable {
        synchronized(lock) {
            if(!closed) {
                val preparation=commands.snapshot(reservationOwner)
                val deadline=preparation.deadline.takeIf { preparation.phase==CommandSequence.Phase.PREPARING }
                if(deadline!=guardAt) {
                    main.removeCallbacks(preparationGuard);guardAt=deadline
                    if(deadline!=null) main.postAtTime(preparationGuard,deadline)
                }
                status(statusText)
            }
        }
    }
    private fun reservationChanged() {
        // Never enter the controller monitor from the allocator's worker/publication callback.
        main.removeCallbacks(reservationUpdate);main.post(reservationUpdate)
    }
    private fun prepareControls() {
        val current=state
        if(compatible && !closed && current?.owner==ControlOwner.RG && (!current.stale || keyboardCompatible) && current.profile==measuredProfile)
            commands.demand(reservationOwner,CommandSequence.Namespace.of(current.context))
        else commands.cancel(reservationOwner)
    }
    internal val keyboard = RgKeyboard()
    internal var reading = false
        private set
    private val headScroll = HeadScrollModel()
    private var scrollCompatible = false
    private var presentationActive = true
    internal var inputSurfaceAvailable: () -> Boolean = { false }
    private var scrollRequest: String? = null
    private var scrollCredit: ScrollCreditMessage? = null
    private var usedCredit: String? = null
    private var creditWaitAt = 0L
    private var creditPending = false
    private var sentNeutral = false
    private var readingUiState: Triple<Boolean,Boolean,Boolean>? = null
    private var reconcileLayoutOnConnect = false
    private val readingTick = object : Runnable {
        override fun run() = synchronized(lock) {
            if (closed || !reading) return@synchronized
            val now = SystemClock.elapsedRealtime()
            val speed = headScroll.speed(SystemClock.elapsedRealtimeNanos())
            val eligible = presentationActive && inputSurfaceAvailable() && scrollCompatible && baseActionEligibleLocked()
            if (!eligible) stopHeadScroll()
            else if (!headScroll.armed) stopHeadScroll(reacquire=false)
            else if (creditPending && now-creditWaitAt >= ContinuousScrollLimits.CREDIT_MS) {
                stopHeadScroll();status("Reading paused — return to neutral")
            } else if (scrollRequest == null) {
                val request = java.util.UUID.randomUUID().toString()
                scrollRequest=request;creditPending=true;creditWaitAt=now
                if (!client.sendControl(ScrollStartMessage(request,checkNotNull(state).context))) stopHeadScroll()
            } else if (!creditPending) {
                val credit=scrollCredit
                if (credit != null && credit.credit != usedCredit) {
                    val velocity=if(sentNeutral)speed else 0.0
                    usedCredit=credit.credit;creditPending=true;creditWaitAt=now
                    if (client.sendControl(ScrollVelocityMessage(credit.context,credit.leaseId,credit.credit,velocity))) sentNeutral=true
                    else stopHeadScroll()
                }
            }
            val next=Triple(eligible,headScroll.armed,headScroll.available(SystemClock.elapsedRealtimeNanos()))
            if (readingUiState != next) { readingUiState=next;status(statusText) }
            main.postDelayed(this,ContinuousScrollLimits.TICK_MS)
        }
    }
    internal fun headSample(sample: RotationSample, now: Long, rotation: Int) = synchronized(lock) {
        if (reading) headScroll.sample(sample,now,rotation)
    }
    internal fun inputPresentationActive(active: Boolean) = synchronized(lock) {
        presentationActive=active
        if(!active)stopHeadScroll()
    }
    internal fun readingNotice(): String? = synchronized(lock) {
        when {
            !reading -> null
            !presentationActive || !inputSurfaceAvailable() || !scrollCompatible || !baseActionEligibleLocked() -> "Reading paused — double tap to exit"
            !headScroll.available(SystemClock.elapsedRealtimeNanos()) -> "Motion input unavailable — double tap to exit"
            !headScroll.armed -> "Hold neutral to scroll · Double tap to exit"
            else -> null
        }
    }
    internal fun toggleReading(): Boolean = synchronized(lock) {
        if (reading) {
            stopHeadScroll();headScroll.stop();reading=false;main.removeCallbacks(readingTick)
            beginLayout();status("Normal Mode");return@synchronized true
        }
        if (!scrollCompatible || !compatible || state?.owner != ControlOwner.RG) {
            status("Reading unavailable — connect or update Phone");return@synchronized false
        }
        dismissKeyboard();pendingActivation=null
        reading=true;headScroll.start();beginLayout();status("Reading Mode")
        main.removeCallbacks(readingTick);main.post(readingTick)
        true
    }
    internal fun suspendReadingScroll() = synchronized(lock) { if(reading)stopHeadScroll() }
    private fun stopHeadScroll(reacquire: Boolean = true) {
        scrollCredit?.let { client.sendControl(ScrollStopMessage(it.context,it.leaseId)) }
        scrollRequest=null;scrollCredit=null;usedCredit=null;creditPending=false;sentNeutral=false
        if (reacquire)headScroll.suspend()
    }

    private var keyboardCompatible = false
    private var hostActive = false
    private var editorState: EditorStateMessage? = null
    private var pendingActivation: Pair<String, ControlContext>? = null
    private var pendingAddress: Pair<String, Long>? = null
    private var editorClose: EditorCloseMessage? = null
    private var editorCloseTimeout: Runnable? = null
    private var layoutMeasured = false
    private var layoutExpected = false
    private var layoutStartedAt = 0L
    private var transitionSequence = 0L
    private data class ViewportChange(val request: ViewportUpdateMessage, val deadline: Long,
                                      var acceptedContext: ControlContext? = null)
    private var viewportChange: ViewportChange? = null
    private val viewportTimeout = Runnable {
        synchronized(lock) { failViewport("Viewport change timed out — Retry") }
    }

    internal fun canOpenAddress(): Boolean = synchronized(lock) {
        !reading && keyboardCompatible && compatible && hostActive && !closed && state?.owner == ControlOwner.RG && !handoff.busy
    }
    private fun addressEligible(): Boolean = canOpenAddress() && pendingCommand == null &&
        viewportChange == null && !layoutExpected && editorClose == null &&
        state?.profile == measuredProfile
    internal fun canSubmitAddress(): Boolean = synchronized(lock) {
        val current = state ?: return@synchronized false
        val slot = commands.snapshot(reservationOwner)
        addressEligible() && slot.phase == CommandSequence.Phase.READY &&
            slot.namespace == CommandSequence.Namespace.of(current.context)
    }
    internal fun canKey(key: RgKeyboard.Key): Boolean = synchronized(lock) {
        if (!keyboard.visible) return@synchronized false
        when (key) {
            RgKeyboard.Key.Command.DONE, RgKeyboard.Key.Command.SHIFT, RgKeyboard.Key.Command.SYMBOLS -> true
            RgKeyboard.Key.Command.LEFT, RgKeyboard.Key.Command.RIGHT -> keyboard.destination == RgKeyboard.Destination.ADDRESS
            else -> if (keyboard.destination == RgKeyboard.Destination.ADDRESS)
                key != RgKeyboard.Key.Command.ENTER || canSubmitAddress()
            else keyboardCompatible && canAct() && editorState?.let { it.ready && it.context == state?.context && it.target == keyboard.target } == true
        }
    }
    internal fun openAddressKeyboard(): Boolean = synchronized(lock) {
        if (!canOpenAddress()) return@synchronized false
        if (keyboard.destination == RgKeyboard.Destination.ADDRESS) return@synchronized true
        if (!closeEditor()) return@synchronized false
        pendingActivation = null
        val address = state?.url.orEmpty()
        // The existing status URL is a 2048-character preview, not an assured full address at its limit.
        keyboard.openAddress(address.takeIf { it.length < 2048 }.orEmpty()); beginLayout()
        status(if (address.length >= 2048) "Address too long to prefill — enter an address" else "Edit address")
        true
    }
    internal fun dismissKeyboard() = synchronized(lock) {
        pendingActivation = null; pendingAddress = null
        if (keyboard.visible) {
            val cancellationStarted = closeEditor()
            keyboard.close()
            if (cancellationStarted) { beginLayout(); status("Text entry ended") }
        }
    }
    internal fun key(intent: RgKeyboard.Intent): Boolean = synchronized(lock) {
        if (!keyboard.current(intent) || !canKey(intent.key)) return@synchronized false
        when (val key = intent.key) {
            RgKeyboard.Key.Command.DONE -> { dismissKeyboard(); true }
            RgKeyboard.Key.Command.SHIFT, RgKeyboard.Key.Command.SYMBOLS -> keyboard.local(intent).also { status(statusText) }
            else -> if (keyboard.destination == RgKeyboard.Destination.ADDRESS) {
                if (key == RgKeyboard.Key.Command.ENTER) {
                    val id = action(BrowserAction.OpenAddress(keyboard.draft)) ?: return@synchronized false
                    pendingAddress = id to keyboard.generation; true
                } else keyboard.local(intent).also { ok -> status(if (ok) "Edit address" else "Address is too long") }
            } else {
                val target = keyboard.target ?: return@synchronized false
                val operation = when (key) {
                    is RgKeyboard.Key.Character -> EditorOperation.Insert(key.text)
                    RgKeyboard.Key.Command.SPACE -> EditorOperation.Insert(" ")
                    RgKeyboard.Key.Command.BACKSPACE -> EditorOperation.Backspace
                    RgKeyboard.Key.Command.ENTER -> EditorOperation.Enter
                    else -> return@synchronized false
                }
                action(BrowserAction.Edit(target, operation)) != null
            }
        }
    }
    private fun closeEditor(): Boolean {
        val editor = editorState
        editorState = null; inputRevision++
        // An in-progress resize owns the old grant. The coalesced hide profile explicitly
        // declines retention, so a late rebind cannot restore editing after Done.
        if (viewportChange != null) return true
        val target = editor?.target ?: return true
        if (editorClose != null) return true
        if (!compatible) return false
        val request = EditorCloseMessage(java.util.UUID.randomUUID().toString(), editor.context, target)
        editorClose = request
        if (!client.sendControl(request)) { failViewport("Text cancellation unavailable — Retry"); return false }
        val timeout = Runnable { synchronized(lock) {
            if (editorClose == request) failViewport("Text cancellation unconfirmed — Retry")
        } }
        editorCloseTimeout = timeout; main.postDelayed(timeout, 1_000)
        return true
    }
    internal fun needsLayoutMeasurement(): Boolean = synchronized(lock) { layoutExpected && !layoutMeasured }
    private fun beginLayout() {
        stopHeadScroll()
        layoutExpected = true; layoutMeasured = false; layoutStartedAt = SystemClock.uptimeMillis(); inputRevision++
        main.removeCallbacks(viewportTimeout)
        main.postAtTime(viewportTimeout, viewportChange?.deadline ?: (layoutStartedAt + 2_000))
    }
    private fun sendViewportIfNeeded(deadline: Long = layoutStartedAt + 2_000) {
        if (!layoutExpected || !layoutMeasured || viewportChange != null || editorClose != null) return
        val current = state ?: return
        val profile = measuredProfile ?: return
        if (!keyboardCompatible || !compatible || current.owner != ControlOwner.RG) return
        if (profile == current.profile) {
            layoutExpected = false; main.removeCallbacks(viewportTimeout); prepareControls(); return
        }
        if (SystemClock.uptimeMillis() >= deadline || transitionSequence == Long.MAX_VALUE) {
            failViewport("Viewport unavailable — Retry"); return
        }
        val request = ViewportUpdateMessage(++transitionSequence, current.context, profile,
            retainEditor = keyboard.destination == RgKeyboard.Destination.FIELD)
        viewportChange = ViewportChange(request, deadline)
        if (!client.sendControl(request)) { failViewport("Viewport request not sent — Retry"); return }
        inbox.grant(null, null); inputRevision++
        status("Updating page layout")
    }
    private fun settleViewport() {
        val change = viewportChange ?: return
        val context = change.acceptedContext ?: return
        val current = state ?: return
        if (current.context != context || current.stale || current.profile != change.request.profile ||
            lastFrameHeader?.context != context) return
        if (SystemClock.uptimeMillis() >= change.deadline) { failViewport("Viewport change timed out — Retry"); return }
        viewportChange = null; main.removeCallbacks(viewportTimeout)
        if (measuredProfile == current.profile) layoutExpected = false
        else {
            main.postAtTime(viewportTimeout, layoutStartedAt + 2_000)
            sendViewportIfNeeded()
        }
        prepareControls(); inputRevision++
    }
    private fun failViewport(reason: String) = invalidate(reason)
    private fun retireKeyboard() {
        stopHeadScroll()
        keyboard.close(); editorState = null; pendingActivation = null; pendingAddress = null
        viewportChange = null; layoutExpected = false; editorClose = null
        main.removeCallbacks(viewportTimeout); editorCloseTimeout?.let(main::removeCallbacks); editorCloseTimeout = null
    }
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

    private fun baseActionEligibleLocked(): Boolean {
        val current=state
        return compatible && !closed && pendingCommand==null && !handoff.busy &&
            viewportChange==null && !layoutExpected && editorClose==null &&
            current?.owner==ControlOwner.RG && !current.stale && !current.loading &&
            current.profile==measuredProfile && lastFrameHeader?.context==current.context
    }
    internal fun baseActionEligible(): Boolean = synchronized(lock) { baseActionEligibleLocked() }
    fun canAct(): Boolean = synchronized(lock) {
        val current=state
        val reservation=commands.snapshot(reservationOwner)
        baseActionEligibleLocked() && current!=null && reservation.phase==CommandSequence.Phase.READY &&
            reservation.namespace==CommandSequence.Namespace.of(current.context)
    }

    fun canHandoff(): Boolean = synchronized(lock) { compatible && !closed && state != null && !handoff.busy }

    internal fun inputSnapshot(): RgInputSnapshot = synchronized(lock) {
        RgInputSnapshot(state?.context,state?.owner,measuredProfile,inputRevision,canAct(),canHandoff(),
            state?.canGoBack==true,state?.canGoForward==true,commands.snapshot(reservationOwner).revision,
            canOpenAddress(),canSubmitAddress())
    }
    /** Remote actions/handoffs validate and send atomically. Retry is local recovery, not a page action. */
    internal fun dispatchIfCurrent(expected: RgInputSnapshot, nativeAction: LocalInputAction? = null,
        dispatch: ()->Boolean): Boolean {
        if(nativeAction==LocalInputAction.RETRY) {
            val accepted=synchronized(lock) { !closed && inputSnapshot()==expected }
            // The router validates the original native target on Main. Invoke its listener now,
            // synchronously but outside this monitor: reconnect loads trust. Never defer a gesture.
            return accepted && dispatch()
        }
        return synchronized(lock) {
            if (closed || inputSnapshot()!=expected) false else {
                val previous=validatedInput;validatedInput=expected
                try { dispatch() } finally { validatedInput=previous }
            }
        }
    }

    fun back(): String? = action(BrowserAction.Back)
    fun forward(): String? = action(BrowserAction.Forward)
    fun reload(): String? = action(BrowserAction.Reload)
    fun activateAt(x: Float, y: Float): String? = synchronized(lock) {
        val id = action(BrowserAction.ActivateAt(x,y)) ?: return@synchronized null
        pendingActivation = id to checkNotNull(state).context
        main.postDelayed({ synchronized(lock) {
            if (pendingActivation?.first == id) {
                pendingActivation = null
                if (keyboard.destination == RgKeyboard.Destination.FIELD && keyboard.target == null) dismissKeyboard()
            }
        } }, 1_000)
        id
    }
    fun scrollBy(dx: Float, dy: Float): String? = synchronized(lock) {
        if (reading) stopHeadScroll()
        action(BrowserAction.ScrollBy(dx,dy))
    }

    private fun action(action: BrowserAction): String? = synchronized(lock) {
        if (if (action is BrowserAction.OpenAddress) !canSubmitAddress() else !canAct()) return@synchronized null
        val current = checkNotNull(state)
        val reservation=commands.snapshot(reservationOwner)
        val ordinal=commands.consume(reservationOwner,CommandSequence.Namespace.of(current.context),
            validatedInput?.reservationRevision ?: reservation.revision) ?: return@synchronized null
        // Consume only after original-intent validation; no IO or preparation wait in this path.
        val request=ordinal.message(current.context,action)
        constructedActions++
        prepareControls() // Coalesced background refill, never an accepted-action queue.

        pendingCommand = request.commandId
        inputRevision++
        if (!client.sendControl(request)) {
            pendingCommand = null
            status("Action not sent")
            return@synchronized null
        }
        queuedActions++
        Log.i("EyeBrowseOrdinal","QUEUE_ACCEPTED namespace="+ordinal.namespace+" ordinal="+ordinal.sequence+
            " at="+SystemClock.uptimeMillis()+" commandId="+request.commandId)
        status("Waiting for page")
        val timeout = Runnable { synchronized(lock) {
            if (pendingCommand == request.commandId) {
                pendingCommand = null
                pendingActivation = null; pendingAddress = null
                if (action is BrowserAction.Edit) dismissKeyboard()
                status("Could not confirm action — not retried")
            }
        } }
        actionTimeout = timeout
        main.postDelayed(timeout,5_000)
        request.commandId
    }

    fun requestPhone(): Boolean {
        synchronized(lock) { pendingActivation = null; retireKeyboard(); status(statusText) }
        return requestHandoff(HandoffTargetWire.PHONE)
    }

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

    private val inbox = PresentationInbox()
    private val decoder = Executors.newSingleThreadExecutor { r -> Thread(r,"eyebrowse-frame-decoder") }
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
    private var lastReadiness: Pair<Boolean,CommandSequence.Snapshot>?=null
    private val updateSurface = Runnable {
        synchronized(lock) {
            if (!closed) {
                state?.let(surface::browserState)
                surface.keyboardChanged()
                val reservation=commands.snapshot(reservationOwner)
                val hasAuthority=compatible && state?.owner==ControlOwner.RG && state?.stale==false
                surface.status(if(hasAuthority && reservation.phase==CommandSequence.Phase.FAILED)
                    "Page controls unavailable — Retry"
                else if(hasAuthority && pendingCommand==null && reservation.phase==CommandSequence.Phase.PREPARING)
                    "Preparing page controls"
                else statusText)
            }
        }
    }
    private fun status(text: String) = synchronized(lock) {
        statusText = text
        val preparation=commands.snapshot(reservationOwner)
        val observed=baseActionEligibleLocked() to preparation
        if(observed!=lastReadiness) {
            lastReadiness=observed
            Log.i("EyeBrowseOrdinal","READINESS at="+SystemClock.uptimeMillis()+" base="+observed.first+
                " namespace="+preparation.namespace+" phase="+preparation.phase+" revision="+preparation.revision+
                " ordinal="+preparation.ordinal+" demandAt="+preparation.demandAt+" readyAt="+preparation.readyAt)
        }
        main.removeCallbacks(updateSurface)
        if (!closed) main.post(updateSurface)
        Unit
    }

    fun measure(width: Int, height: Int, densityDpi: Int): Unit = synchronized(lock) {
        val next = PresentationProfile.fromMeasured(width,height,densityDpi)
        if (next != measuredProfile) inputRevision++
        measuredProfile = next
        val current = state
        if (current?.owner == ControlOwner.RG) {
            if (layoutExpected) sendViewportIfNeeded()
            else if (current.profile != measuredProfile) {
                invalidate("Viewport changed — presentation stale")
                client.disconnect()
            }
        }
    }
    /** Also called after a requested layout whose content rectangle did not change. */
    internal fun layoutMeasured() = synchronized(lock) {
        layoutMeasured = true; sendViewportIfNeeded()
    }
    fun profile(): PresentationProfile? = measuredProfile
    fun browserState(): BrowserStateMessage? = state
    fun reconnect() {
        val open=synchronized(lock) {
            if(closed) false else {
                reconcileLayoutOnConnect=true
                if (compatible && state?.owner==ControlOwner.RG && state?.profile!=measuredProfile) {
                    beginLayout();layoutMeasured=true;sendViewportIfNeeded();reconcileLayoutOnConnect=false
                }
                commands.cancel(reservationOwner)
                prepareControls() // Storage recovery also works while the authenticated link is already live.
                true
            }
        }
        // Link setup may load trust; keep it outside the controller/allocator monitors.
        if(open) client.reconnect()
    }

    /** T02 production API; explicit handoff UI and bidirectional journey arrive in T03. */
    fun requestPresentation(): Boolean = requestHandoff(HandoffTargetWire.RG)
    // Engine lifecycle callbacks can hold its operation lock. Do not take our monitor from that
    // lock while main-thread dispatch holds our monitor and calls sendControl in the other direction.
    private fun onMain(block: ()->Unit) {
        if(Looper.myLooper()==Looper.getMainLooper()) block() else main.post { if(!closed) block() }
    }
    override fun onStateChange(state: PairingState) = onMain { status(state.name) }
    override fun onStatus(status: HostStatusValue) = onMain { synchronized(lock) {
        val active = status == HostStatusValue.HOSTING
        if (hostActive != active) inputRevision++
        hostActive = active
        if (!hostActive && state?.owner == ControlOwner.RG) invalidate("Host: $status")
        else if (state?.owner != ControlOwner.RG) status("Host: $status")
        else status(statusText)
    } }
    override fun onPresentationCompatibility(result: CapabilityNegotiation) = onMain { synchronized(lock) {
        val next=result == CapabilityNegotiation.Accepted
        if(next!=compatible) inputRevision++
        compatible = next
        if (!compatible) invalidate("Update apps to use presentation") else prepareControls()
    } }
    override fun onKeyboardCompatibility(compatible: Boolean) = onMain { synchronized(lock) {
        keyboardCompatible = compatible; inputRevision++
        if (compatible && layoutExpected) sendViewportIfNeeded()
        if (!compatible) retireKeyboard()
        status(statusText)
    } }
    override fun onContinuousScrollCompatibility(compatible: Boolean) = onMain { synchronized(lock) {
        scrollCompatible=compatible
        if (!compatible) stopHeadScroll()
    } }
    override fun onLinkLost() = onMain { synchronized(lock) { compatible = false; invalidate("Disconnected — page stale; Retry") } }
    override fun onConnectFailed(error: LinkError) = onMain { invalidate("Connection failed: ${error.javaClass.simpleName}") }
    override fun onControl(message: BrowserControlMessage): Unit = receiveControl(message)
    private fun receiveControl(message: BrowserControlMessage): Unit = synchronized(lock) {
        if(closed) return@synchronized
        when (message) {
            is BrowserStateMessage -> {
                val old=state
                var continueLayoutUntil: Long? = null
                if(old==null || old.context!=message.context || old.owner!=message.owner || old.profile!=message.profile ||
                    old.stale!=message.stale || old.loading!=message.loading) inputRevision++
                if (old != null && (old.context.lifetimeId != message.context.lifetimeId ||
                    old.context.controlEpoch != message.context.controlEpoch || old.owner != message.owner ||
                    old.context.hostingGeneration != message.context.hostingGeneration)) retireKeyboard()
                else if (old != null && old.context.documentId != message.context.documentId) {
                    if (layoutExpected && keyboard.destination != RgKeyboard.Destination.FIELD)
                        continueLayoutUntil = viewportChange?.deadline ?: (layoutStartedAt + 2_000)
                    pendingActivation = null; editorState = null
                    viewportChange = null; editorClose = null
                    editorCloseTimeout?.let(main::removeCallbacks); editorCloseTimeout = null
                    main.removeCallbacks(viewportTimeout)
                    if (keyboard.destination == RgKeyboard.Destination.FIELD) {
                        keyboard.close(); beginLayout()
                    }
                }
                state = message
                if (old?.context != message.context || old?.owner != message.owner || message.stale) stopHeadScroll()
                if (reconcileLayoutOnConnect) {
                    reconcileLayoutOnConnect=false
                    if (message.owner==ControlOwner.RG && message.profile!=measuredProfile) {
                        beginLayout();layoutMeasured=true;sendViewportIfNeeded()
                    }
                }
                continueLayoutUntil?.let { deadline ->
                    // The old document retired the request, not the measured local layout intent.
                    // Continue only that state update, under its original guard and fresh context.
                    main.postAtTime(viewportTimeout, deadline)
                    sendViewportIfNeeded(deadline)
                }
                if (state !== message) return@synchronized // An expired continuation stays invalid.
                val bufferedEditor = editorState
                if (bufferedEditor?.context == message.context) receiveControl(bufferedEditor)
                prepareControls()
                handoff.observed(message.owner,message.context)
                if(!handoff.busy) { handoffTimeout?.let(main::removeCallbacks);handoffTimeout=null }
                val live = message.owner == ControlOwner.RG && !message.stale && message.profile == measuredProfile
                val receiving = live || viewportChange?.let { it.request.profile == message.profile &&
                    message.owner == ControlOwner.RG && !message.stale && it.acceptedContext == message.context } == true
                inbox.grant(message.context.takeIf { receiving }, message.profile.takeIf { receiving })
                settleViewport()
                status(if (!live) {
                    if (message.owner == ControlOwner.PHONE) "Browsing on Phone" else "Presentation stale"
                } else if (lastFrameHeader?.context == message.context) statusText else "Waiting for current frame")
            }
            is ScrollCreditMessage -> {
                if (!reading || !presentationActive || message.requestId!=scrollRequest || message.context!=state?.context ||
                    message.credit==usedCredit || scrollCredit?.let { it.leaseId!=message.leaseId }==true) return@synchronized
                scrollCredit=message;creditPending=false
            }
            is EditorStateMessage -> {
                val current = state ?: return@synchronized
                if (current.owner != ControlOwner.RG || !keyboardCompatible) return@synchronized
                if (message.context != current.context) {
                    val old = viewportChange?.request?.context
                    if (old != null && message.context.copy(viewportEpoch = old.viewportEpoch) == old &&
                        message.context.viewportEpoch > old.viewportEpoch) editorState = message
                    return@synchronized
                }
                if (editorState != message) inputRevision++
                editorState = message
                val activation = pendingActivation
                if (message.ready && message.target != null && activation != null &&
                    activation.first == message.activationCommandId && activation.second == message.context &&
                    keyboard.destination != RgKeyboard.Destination.ADDRESS) {
                    val openingLayout = !keyboard.visible
                    pendingActivation = null; keyboard.openField(checkNotNull(message.target),message.activationCommandId)
                    if (openingLayout) beginLayout()
                } else if (keyboard.destination == RgKeyboard.Destination.FIELD) {
                    if (message.target != null && message.activationCommandId == keyboard.activationCommandId &&
                        (message.target == keyboard.target || viewportChange != null))
                        keyboard.rebind(message.target)
                    else if (message.target == null && !layoutExpected && viewportChange == null) {
                        if (pendingActivation != null) keyboard.rebind(null)
                        else { keyboard.close(); beginLayout() }
                    }
                } else if (message.target != null && pendingActivation == null && viewportChange == null) {
                    closeEditor() // Orphaned/delayed grant after dismissal is never a reopen instruction.
                }
                status(statusText)
            }
            is EditorCloseResultMessage -> {
                val request = editorClose ?: return@synchronized
                if (message.requestId != request.requestId) return@synchronized
                if (!message.closed || message.context != request.context) {
                    failViewport("Text cancellation unconfirmed — Retry"); return@synchronized
                }
                editorClose = null; editorCloseTimeout?.let(main::removeCallbacks); editorCloseTimeout = null
                sendViewportIfNeeded(); status(statusText)
            }
            is ViewportUpdateResultMessage -> {
                val change = viewportChange ?: return@synchronized
                if (message.transitionId != change.request.transitionId) return@synchronized
                if (!message.accepted || message.profile != change.request.profile ||
                    message.context.lifetimeId != change.request.context.lifetimeId ||
                    message.context.controlEpoch != change.request.context.controlEpoch ||
                    message.context.documentId != change.request.context.documentId ||
                    message.context.hostingGeneration != change.request.context.hostingGeneration ||
                    message.context.viewportEpoch <= change.request.context.viewportEpoch) {
                    failViewport("Viewport rejected — Retry"); return@synchronized
                }
                change.acceptedContext = message.context
                val current = state
                if (current?.context == message.context && !current.stale)
                    inbox.grant(current.context, current.profile)
                settleViewport(); status(statusText)
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
                    val address = pendingAddress
                    if (address?.first == message.commandId) {
                        pendingAddress = null
                        if (keyboard.generation == address.second && message.accepted && message.reason == "ADDRESS_OPENED") dismissKeyboard()
                    }
                    if (!message.accepted || message.reason == "EDITOR_UNCERTAIN") {
                        pendingActivation = null
                        if (keyboard.destination == RgKeyboard.Destination.FIELD) dismissKeyboard()
                    }
                    status(if (!message.accepted) "Action unavailable — page or control changed"
                        else if (message.reason == "ADDRESS_REJECTED") "Invalid address — edit and try again"
                        else if (message.reason in setOf(null,"EDITOR_APPLIED","ADDRESS_OPENED","SUBMISSION_REQUESTED")) "Connected to Phone"
                        else "Could not confirm action — not retried")
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
            settleViewport()
            status("Live page · authenticated")
            Log.i("EyeBrowsePresentation", "display seq=${next.first.header.frameSeq} capture=${next.first.header.captureTsMs} local=${SystemClock.elapsedRealtime()} profile=${next.second.width}x${next.second.height} coalesced=${inbox.dropped}")
        }
    }
    private fun invalidate(reason: String) {
        synchronized(lock) {
            retireKeyboard()
            inputRevision++;commands.cancel(reservationOwner);handoff.clear();handoffTimeout?.let(main::removeCallbacks);handoffTimeout=null
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
                val store=CommandSequenceStore(context.applicationContext)
                val worker=Executors.newSingleThreadExecutor { r ->
                    Thread(r,"eyebrowse-command-reservation").apply { isDaemon=true }
                }
                CommandSequence(store::read,store::persist,{ job -> worker.execute(job) },
                    SystemClock::uptimeMillis) { event -> Log.i("EyeBrowseOrdinal",event) }
                    .also { sequence=it }
            }
        }
    }

    fun close() {
        synchronized(lock) { retireKeyboard();closed = true;inputRevision++;handoff.clear(); inbox.grant(null,null); pendingDisplay?.second?.recycle(); pendingDisplay = null }
        commands.release(reservationOwner)
        main.removeCallbacks(preparationGuard);main.removeCallbacks(reservationUpdate)
        handoffTimeout?.let(main::removeCallbacks);handoffTimeout=null
        actionTimeout?.let(main::removeCallbacks)
        headScroll.stop();main.removeCallbacks(readingTick)
        client.disconnect(); decoder.shutdownNow(); main.removeCallbacks(display); main.removeCallbacks(updateSurface)
        // ImageView may still render the last bitmap until its Activity detaches. Let GC own it.
        shown = null
    }
}
