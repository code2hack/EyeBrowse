package com.code2hack.eyebrowse.phone.link

import android.content.Context
import com.code2hack.eyebrowse.phone.PhonePresentationPublisher
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.phone.PhoneControlCoordinator
import com.code2hack.eyebrowse.phone.PhoneBrowserSession
import com.code2hack.eyebrowse.phone.PhoneEditorController
import com.code2hack.eyebrowse.core.link.messages.BrowserControlMessage
import com.code2hack.eyebrowse.core.link.transport.AuthenticatedControlSession
import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.LinkTimings
import com.code2hack.eyebrowse.core.link.crypto.SpkiFingerprint
import com.code2hack.eyebrowse.core.link.invitation.B64URL
import com.code2hack.eyebrowse.core.link.invitation.InvitationLifecycle
import com.code2hack.eyebrowse.core.link.messages.HelloMessage
import com.code2hack.eyebrowse.core.link.session.PeerTrustRead
import com.code2hack.eyebrowse.core.link.session.PeerTrustRecord
import com.code2hack.eyebrowse.core.link.transport.LinkServerEngine
import com.code2hack.eyebrowse.core.link.transport.Locator
import com.code2hack.eyebrowse.phone.HostingController

/**
 * The Phone-side link: bounded TLS 1.3 server, invitation/reconnect authentication and the one
 * active RG link. Hosting starts explicitly on Phone. T02 owns a frame lease only after the
 * authenticated arbiter grants RG presentation; pairing/status alone never acquires a lease.
 *
 * Process-scoped lifecycle: starting from Phone UI is sufficient for inactive-host pairing and
 * retry; while the existing HostingService keeps the process alive the singleton stays alive.
 */
class PhoneLinkServer(
    private val identity: PhoneLinkIdentity,
    private val invitations: PairingInvitationManager,
    private val identityRecovery: PhoneIdentityRecovery,
    private val store: PhonePairingStore,
    private val hostingController: HostingController?,
    private val locators: () -> List<Locator>,
    private val browserSession: PhoneBrowserSession? = null,
    private val appContext: Context? = null,
) {

    @Volatile
    private var engine: LinkServerEngine? = null

    private val pendingControls = java.util.concurrent.Semaphore(LinkProtocol.OUTBOUND_QUEUE_MAX)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var authenticatedSession: AuthenticatedControlSession? = null
    private val fallbackDocumentId = java.util.UUID.randomUUID().toString()
    val controlCoordinator = PhoneControlCoordinator(
        readDocumentIdentity = { browserSession?.documentIdentity() ?: fallbackDocumentId },
        invalidatePresentation = { authenticatedSession?.setPresentation(null, null) },
        executeAction = { browserSession?.executeRemoteAction(it) ?: error("browser unavailable") },
        measurePhone = { hostingController?.measurePhoneControlProfile() },
    )
    @Volatile private var connectionGeneration = 0L
    val editorController: PhoneEditorController? = if (appContext != null && browserSession != null)
        PhoneEditorController(appContext, browserSession, controlCoordinator.authority::snapshot,
            { connectionGeneration }) { state ->
                authenticatedSession?.takeIf { it.keyboardCompatible }?.sendControl(state)
            }.also { browserSession.remoteEditor = it } else null
    private var transitionPending = false
    private var viewportHighWater = 0L
    private var lastViewportResult: Pair<ViewportUpdateMessage, ViewportUpdateResultMessage>? = null
    private var lastCloseResult: Pair<EditorCloseMessage, EditorCloseResultMessage>? = null
    @Volatile private var profileEditor: Pair<ControlContext, EditorTarget>? = null
    private val publisher by lazy {
        hostingController?.let { PhonePresentationPublisher(it, ::publishPresentationReady, ::sendPresentation,
            { browserSession?.requestFreshCaptureFrame() }, beforeCapture = { state, start ->
                val retained = profileEditor?.takeIf { it.first == state.context }
                if (retained != null && editorController != null) {
                    profileEditor = null
                    editorController.resumeAfterProfile(retained.second) { start() }
                } else start()
            }) { context, reason ->
            synchronized(controlCoordinator.authority) {
                if (controlCoordinator.authority.markPresentationStale(context)) {
                    authenticatedSession?.setPresentation(null, null)
                    authenticatedSession?.sendControl(PresentationStaleMessage(context, reason))
                }
            }
        } }
    }
    private val reconcilePresentation = Runnable {
        val state = synchronized(controlCoordinator.authority) {
            controlCoordinator.reconcileDocument()
            controlCoordinator.authority.snapshot()
        }
        // Native focus reads and renderer scheduling never run under the link authority monitor.
        editorController?.reconcile()
        publisher?.reconcile(state)
        publishBrowserState()
    }
    private fun schedulePresentation() {
        main.removeCallbacks(reconcilePresentation)
        main.post(reconcilePresentation)
    }
    private val browserListener = PhoneBrowserSession.Listener {
        controlCoordinator.reconcileDocument()
        schedulePresentation()
    }

    private fun publishBrowserState() {
        val state = controlCoordinator.authority.snapshot()
        val session = authenticatedSession ?: return
        if (!session.sendControl(BrowserStateMessage(state.owner, state.context, state.profile,
            url=browserSession?.displayUrl()?.take(2048), title=browserSession?.pageTitle()?.take(512),
            canGoBack=browserSession?.canGoBack() ?: false, canGoForward=browserSession?.canGoForward() ?: false,
            loading=browserSession?.isLoading() ?: false, stale=state.presentationStatus != PresentationStatus.READY,
            error=browserSession?.errorMessage()?.take(512)))) session.close()
    }
    /** Pull actual Hosting state at lifecycle/admission boundaries, including missed Stop events. */
    private fun reconcileHostingAuthority() {
        hostingController?.status()?.let {
            controlCoordinator.authority.setHostingGeneration(it.generation.toLong(), it.state == HostingController.State.HOSTING)
            if (it.state == HostingController.State.NOT_HOSTING) {
                controlCoordinator.authority.returnToPhoneAfterStop(hostingController.measurePhoneControlProfile())
            } else if (!it.captureActive) {
                val current = controlCoordinator.authority.snapshot()
                if (current.owner == ControlOwner.RG && current.presentationStatus == PresentationStatus.READY) {
                    controlCoordinator.authority.markPresentationStale(current.context)
                    authenticatedSession?.setPresentation(null,null)
                }
            }
        }
    }

    private val hostingListener = HostingController.Listener {
        val before = controlCoordinator.authority.snapshot().context
        reconcileHostingAuthority()
        if (before != controlCoordinator.authority.snapshot().context) {
            authenticatedSession?.setPresentation(null,null)
            notifyLinkObservers()
        }
        // Hosting state transition (main thread): push the latest-state observation if linked.
        engine?.pushStatus(currentHostStatus())
        schedulePresentation()
    }

    private val linkObservers = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    private val engineListener = object : LinkServerEngine.Listener {
        override fun onLinkUp() = notifyLinkObservers()
        override fun onLinkDown() {
            synchronized(controlCoordinator.authority) {
                controlCoordinator.onLinkStopped()
                authenticatedSession = null
            }
            main.post { editorController?.close { } }
            schedulePresentation()
            notifyLinkObservers()
        }
        override fun onAuthenticatedSession(session: AuthenticatedControlSession, peer: HelloMessage) {
            synchronized(controlCoordinator.authority) {
                connectionGeneration = Math.incrementExact(connectionGeneration)
                controlCoordinator.onAuthenticatedSession(session.presentationCompatible, session.keyboardCompatible)
                authenticatedSession = session
            }
            main.post {
                if (authenticatedSession !== session) return@post
                viewportHighWater = 0; lastViewportResult = null; lastCloseResult = null
                editorController?.close { }
            }
            schedulePresentation()
        }
        override fun onControl(session: AuthenticatedControlSession, message: BrowserControlMessage) {
            if (!pendingControls.tryAcquire()) { session.close(); return }
            val posted = main.post {
                var completed = false
                fun complete(response: BrowserControlMessage?) {
                    if (completed) return
                    completed = true
                    try {
                        if (response != null && authenticatedSession === session && !session.sendControl(response)) session.close()
                    } finally { pendingControls.release() }
                }
                try {
                    if (authenticatedSession !== session) { complete(null); return@post }
                    reconcileHostingAuthority()
                    processEditorAwareControl(session, message, ::complete)
                } catch (_: RuntimeException) { complete(null); session.close() }
            }
            if (!posted) { pendingControls.release(); session.close() }
        }
        override fun onAuthFailed(error: LinkError) = notifyLinkObservers()
    }

    /** One effect/transition at a time; no accepted-effect backlog behind editor work. */
    private fun processEditorAwareControl(session: AuthenticatedControlSession, message: BrowserControlMessage,
                                          answer: (BrowserControlMessage?) -> Unit) {
        controlCoordinator.reconcileDocument()
        val editor = editorController
        if (message is BrowserActionMessage && (message.action is BrowserAction.Edit || transitionPending)) {
            val admission = controlCoordinator.authority.admitAction(ControlOwner.RG,
                BrowserActionRequest(message.commandId, message.context, message.action, message.commandSequence))
            if (admission is ActionDecision.Rejected) {
                answer(BrowserActionResultMessage(message.commandId, false, reason = admission.reason.name)); return
            }
            if (transitionPending || editor == null) {
                answer(BrowserActionResultMessage(message.commandId, false, reason = "EDITOR_NOT_READY")); return
            }
            editor.execute(message, answer); return
        }
        when (message) {
            is EditorCloseMessage -> {
                lastCloseResult?.takeIf { it.first == message }?.let { answer(it.second); return }
                val current = controlCoordinator.authority.snapshot()
                if (!session.keyboardCompatible || message.context != current.context || current.owner != ControlOwner.RG ||
                    editor?.authority?.grant?.target != message.target) {
                    answer(EditorCloseResultMessage(message.requestId, current.context, false)); return
                }
                editor.close { verified ->
                    val result = EditorCloseResultMessage(message.requestId, message.context, verified)
                    lastCloseResult = message to result; answer(result)
                }
            }
            is ViewportUpdateMessage -> {
                lastViewportResult?.takeIf { it.first == message }?.let { answer(it.second); return }
                val current = controlCoordinator.authority.snapshot()
                fun rejected() = ViewportUpdateResultMessage(message.transitionId, false,
                    controlCoordinator.authority.snapshot().context, controlCoordinator.authority.snapshot().profile)
                if (!session.keyboardCompatible || transitionPending || message.context != current.context ||
                    message.transitionId <= viewportHighWater || current.owner != ControlOwner.RG || !current.hostingActive) {
                    answer(rejected()); return
                }
                viewportHighWater = message.transitionId
                transitionPending = true
                val previousTarget = editor?.authority?.grant?.target
                retireEditor(retainForProfile = previousTarget != null) { verified ->
                    transitionPending = false
                    val updated = if (verified && authenticatedSession === session)
                        controlCoordinator.authority.updateRgViewport(message.context, message.profile) else null
                    val result = if (updated == null) rejected() else {
                        publisher?.stop(); authenticatedSession?.setPresentation(null, null)
                        controlCoordinator.authority.markPresentationStale(updated.context)
                        profileEditor = previousTarget?.let { updated.context to it }
                        schedulePresentation()
                        ViewportUpdateResultMessage(message.transitionId, true, updated.context, updated.profile)
                    }
                    lastViewportResult = message to result; answer(result)
                }
            }
            is HandoffRequestMessage -> {
                if (transitionPending) {
                    val state = controlCoordinator.authority.snapshot()
                    answer(HandoffResultMessage(false, state.owner, state.context, state.profile, "EDITOR_TRANSITION")); return
                }
                transitionPending = true
                retireEditor { verified ->
                    transitionPending = false
                    if (verified && authenticatedSession === session) answer(processControl(message)) else {
                        val state = controlCoordinator.authority.snapshot()
                        answer(HandoffResultMessage(false, state.owner, state.context, state.profile, "EDITOR_UNCERTAIN"))
                    }
                }
            }
            is BrowserActionMessage -> {
                // A page navigation/activation must not overtake an earlier renderer edit.
                if (editor?.isQuiescent() == false) {
                    val admission = controlCoordinator.authority.admitAction(ControlOwner.RG,
                        BrowserActionRequest(message.commandId, message.context, message.action, message.commandSequence))
                    if (admission is ActionDecision.Rejected) answer(BrowserActionResultMessage(message.commandId, false, reason = admission.reason.name))
                    else if (editor.authority.phase != com.code2hack.eyebrowse.phone.PhoneEditorAuthority.Phase.READY) {
                        answer(BrowserActionResultMessage(message.commandId, false, reason = "EDITOR_NOT_READY"))
                    } else {
                        transitionPending = true
                        editor.close { verified ->
                            transitionPending = false
                            val current = controlCoordinator.authority.snapshot()
                            if (!verified || authenticatedSession !== session || current.context != message.context || current.owner != ControlOwner.RG ||
                                !current.linkAuthenticated || !current.sessionCompatible || !current.hostingActive ||
                                (message.action !is BrowserAction.OpenAddress && current.presentationStatus != PresentationStatus.READY)) {
                                answer(BrowserActionResultMessage(message.commandId, true, false, "EDITOR_TRANSITION"))
                            } else {
                                var dispatchFailure: String? = null
                                try { browserSession?.executeRemoteAction(message.action) ?: error("browser unavailable") }
                                catch (_: RuntimeException) { dispatchFailure = "DISPATCH_UNCERTAIN" }
                                answer(BrowserActionResultMessage(message.commandId, true, null, dispatchFailure))
                                schedulePresentation()
                                if (dispatchFailure == null) requestEditorForActivation(session, message)
                            }
                        }
                    }
                } else {
                    val response = processControl(message)
                    answer(response)
                    if ((response as? BrowserActionResultMessage)?.accepted == true) requestEditorForActivation(session, message)
                }
            }
            else -> answer(processControl(message))
        }
    }

    private fun requestEditorForActivation(session: AuthenticatedControlSession, message: BrowserActionMessage) {
        val activation = message.action as? BrowserAction.ActivateAt ?: return
        if (!session.keyboardCompatible) return
        main.post {
            if (authenticatedSession !== session) return@post
            controlCoordinator.reconcileDocument()
            // A successful old-page tap cannot open an autofocus editor on its successor page.
            if (controlCoordinator.authority.snapshot().context == message.context)
                editorController?.openAfterActivation(activation)
        }
    }

    private fun retireEditor(retainForProfile: Boolean = false, completed: (Boolean) -> Unit) {
        editorController?.close(retainForProfile, completed) ?: completed(true)
    }

    /** Shared main-thread boundary for remote handoff/actions and explicit local recovery. */
    private fun processControl(message: BrowserControlMessage): BrowserControlMessage? {
        val before = controlCoordinator.authority.snapshot().context
        val response = controlCoordinator.receive(message)
        val state = controlCoordinator.authority.snapshot()
        if (before != state.context) authenticatedSession?.setPresentation(null,null)
        if (response is HandoffResultMessage && response.accepted) {
            profileEditor = null
            publisher?.stop()
            if (state.owner == ControlOwner.PHONE) check(hostingController?.presentOnPhone() == true)
            else publisher?.reconcile(state)
            notifyLinkObservers()
        }
        schedulePresentation()
        return response
    }

    /** Phone-local explicit takeover works even after the RG link is lost. */
    fun useOnPhone() {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        retireEditor { verified ->
            if (verified) {
                reconcileHostingAuthority()
                val epoch = controlCoordinator.authority.snapshot().controlEpoch
                val result = processControl(HandoffRequestMessage(HandoffTargetWire.PHONE, epoch)) as HandoffResultMessage
                authenticatedSession?.sendControl(result)
            }
        }
    }

    fun phoneOwnsInput(): Boolean = controlCoordinator.authority.snapshot().owner == ControlOwner.PHONE

    fun publishPhoneViewport() {
        hostingController?.measurePhoneControlProfile()?.let { controlCoordinator.authority.updatePhoneViewport(it) }
        schedulePresentation()
    }

    /** T02's real producer reports readiness only for its exact immutable presentation context. */
    fun publishPresentationReady(context: com.code2hack.eyebrowse.core.link.control.ControlContext): Boolean =
        synchronized(controlCoordinator.authority) {
            val session = authenticatedSession ?: return false
            controlCoordinator.reconcileDocument()
            if (controlCoordinator.authority.snapshot().presentationStatus == PresentationStatus.READY &&
                controlCoordinator.authority.snapshot().context == context) return true
            if (!controlCoordinator.authority.markPresentationReady(context)) return false
            val state = controlCoordinator.authority.snapshot()
            session.setPresentation(state.context, state.profile)
            main.post { if (authenticatedSession === session) editorController?.reconcile() }
            schedulePresentation()
            session.sendControl(com.code2hack.eyebrowse.core.link.messages.BrowserStateMessage(
                state.owner, state.context, state.profile, stale=false)).also { if (!it) session.close() }
        }

    fun sendPresentation(frame: com.code2hack.eyebrowse.core.link.framing.PresentationFrame): Boolean =
        synchronized(controlCoordinator.authority) {
            controlCoordinator.reconcileDocument()
            val state = controlCoordinator.authority.snapshot()
            if (state.context != frame.header.context || state.owner != com.code2hack.eyebrowse.core.link.control.ControlOwner.RG ||
                state.presentationStatus != com.code2hack.eyebrowse.core.link.control.PresentationStatus.READY || !state.linkAuthenticated) return false
            authenticatedSession?.sendPresentation(frame) ?: false
        }

    /** UI surfaces register for link-state changes instead of polling (uiautomator-friendly). */
    fun addLinkObserver(observer: () -> Unit) {
        linkObservers.add(observer)
    }

    fun removeLinkObserver(observer: () -> Unit) {
        linkObservers.remove(observer)
    }

    private fun notifyLinkObservers() {
        linkObservers.forEach { it() }
    }

    private val trustController = object : LinkServerEngine.TrustController {
        override fun serverHello(): HelloMessage =
            HelloMessage(LinkProtocol.MAJOR, LinkProtocol.MINOR, LinkProtocol.ALL_CAPABILITIES)

        override fun consumeInvitation(id: String, secretB64: String): InvitationLifecycle.ConsumeOutcome =
            invitations.consumeForServer(id, secretB64)

        // Tri-state read: CORRUPT fails closed inside the binding policy (review R5/B6) — it is
        // never reported as "unpaired", which would erase the replacement guard.
        override fun pairedPeer(): PeerTrustRead = store.read()

        override fun commitPairedPeer(spki: ByteArray, clientHello: HelloMessage) {
            store.save(
                PeerTrustRecord(
                    peerSpkiSha256Hex = SpkiFingerprint.sha256Hex(spki),
                    peerSpkiB64 = B64URL.encode(spki),
                    // The Phone advertises locators; it stores no peer locators (plan §4.3).
                    lastLocators = listOf(),
                    protocolMajor = clientHello.pmj,
                    protocolMinor = clientHello.pmm,
                    peerCapabilities = clientHello.caps,
                ),
            )
        }

        override fun currentHostStatus(): HostStatusValue = this@PhoneLinkServer.currentHostStatus()

        override fun onLinkLost() = Unit // state only; trust retained
    }

    /** Latest observed hosting state, read-only (never a control grant). */
    private fun currentHostStatus(): HostStatusValue =
        hostingController?.status()?.let { PhoneStatusProvider.toHostStatus(it.state) }
            ?: HostStatusValue.HOST_INACTIVE

    /** Starts the TLS listener on the fixed application port. Idempotent while running. */
    @Synchronized
    fun start() {
        controlCoordinator.onLinkStarting()
        reconcileHostingAuthority()
        notifyLinkObservers()
        if (engine != null) return
        // Validate/repair only at use time. Construction must survive an inadequate VALID/CORRUPT
        // alias so PairingActivity can still expose the explicit Forget recovery control.
        identityRecovery.ensureUsableIdentity()
        hostingController?.addListener(hostingListener)
        browserSession?.addListener(browserListener)
        val newEngine = LinkServerEngine(identity, trustController, LinkTimings.PRODUCT, engineListener)
        newEngine.start(LinkProtocol.LOCAL_PORT)
        engine = newEngine
    }

    @Synchronized
    fun stop(sendForgetNotice: Boolean = false) {
        hostingController?.removeListener(hostingListener)
        browserSession?.removeListener(browserListener)
        synchronized(controlCoordinator.authority) {
            controlCoordinator.onLinkStopped()
            authenticatedSession = null
        }
        main.post { publisher?.stop() }
        main.post { editorController?.close { } }
        engine?.let {
            if (sendForgetNotice) it.sendForgetNotice()
            it.stop()
        }
        engine = null
    }

    fun isLinkUp(): Boolean = engine?.isLinkUp() ?: false

    /** Usable pairing exists (VALID trust; CORRUPT intentionally reports false and fails closed). */
    fun isPaired(): Boolean = store.read() is PeerTrustRead.Valid

    /** Trust store is CORRUPT (unreadable): distinct from ABSENT; recovery is explicit Forget (B2). */
    fun trustIsCorrupt(): Boolean = store.read() is PeerTrustRead.Corrupt

    /** Generates a fresh invitation (cancels any previous one) with current explicit locators. */
    fun generateInvitation(): PairingInvitationManager.ActiveInvitation =
        invitations.generate(locators())

    fun cancelInvitation(): Boolean = invitations.cancel()

    fun activeInvitation(): PairingInvitationManager.ActiveInvitation? = invitations.activeInvitation()

    /**
     * Locally authoritative Forget: close the link, clear peer trust, cancel the invitation
     * (ticket plan §8). The device identity key is intentionally retained for re-pairing.
     */
    @Synchronized
    fun forget() {
        stop(sendForgetNotice = true)
        store.clear()
        invitations.cancel()
        // Trust has been explicitly cleared. Only an ABSENT read can authorize repair/rotation;
        // VALID/CORRUPT never silently rotate. Failure remains recoverable/fail-closed.
        identityRecovery.repairAfterForget()
    }

    companion object {
        /**
         * Process-scoped singleton access (ticket plan §6: app-process scoped listener).
         * UI (T02) starts/controls through this holder.
         */
        @Volatile
        private var instance: PhoneLinkServer? = null

        fun obtain(context: Context): PhoneLinkServer =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(appContext: Context): PhoneLinkServer {
            val identity = PhoneLinkIdentity()
            val store = PhonePairingStore(appContext)
            // Construction is side-effect free with respect to identity usability. VALID/CORRUPT
            // trust with an inadequate alias must still reach PairingActivity and explicit Forget.
            val identityRecovery = PhoneIdentityRecovery(
                readTrust = store::read,
                ensureIdentity = identity::ensureKeyOrUpgrade,
                readCurrentFingerprint = identity::spkiSha256Hex,
            )
            val invitations = PairingInvitationManager(
                lifecycle = InvitationLifecycle { android.os.SystemClock.elapsedRealtime() },
                // Resolve on each generation: legal post-Forget rotation cannot leave an old pin.
                phoneSpkiSha256Hex = identityRecovery::currentFingerprint,
            )
            val server = PhoneLinkServer(
                identity = identity,
                invitations = invitations,
                identityRecovery = identityRecovery,
                store = store,
                hostingController = runCatching { HostingController.get(appContext) }.getOrNull(),
                locators = { PhoneLocatorEnumerator.enumerate(appContext) },
                browserSession = PhoneBrowserSession.get(appContext),
                appContext = appContext,
            )
            return server
        }
    }
}
