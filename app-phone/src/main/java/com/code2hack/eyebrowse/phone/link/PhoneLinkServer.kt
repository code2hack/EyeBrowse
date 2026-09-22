package com.code2hack.eyebrowse.phone.link

import android.content.Context
import com.code2hack.eyebrowse.phone.PhonePresentationPublisher
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.phone.PhoneControlCoordinator
import com.code2hack.eyebrowse.phone.PhoneBrowserSession
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
    private val publisher by lazy {
        hostingController?.let { PhonePresentationPublisher(it, ::publishPresentationReady, ::sendPresentation,
            { browserSession?.requestFreshCaptureFrame() }) { context, reason ->
            synchronized(controlCoordinator.authority) {
                if (controlCoordinator.authority.markPresentationStale(context)) {
                    authenticatedSession?.setPresentation(null, null)
                    authenticatedSession?.sendControl(PresentationStaleMessage(context, reason))
                }
            }
        } }
    }
    private val reconcilePresentation = Runnable {
        synchronized(controlCoordinator.authority) {
            controlCoordinator.reconcileDocument()
            publisher?.reconcile(controlCoordinator.authority.snapshot())
            publishBrowserState()
        }
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
    private val hostingListener = HostingController.Listener {
        val before = controlCoordinator.authority.snapshot().context
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
            schedulePresentation()
            notifyLinkObservers()
        }
        override fun onAuthenticatedSession(session: AuthenticatedControlSession, peer: HelloMessage) {
            synchronized(controlCoordinator.authority) {
                controlCoordinator.onAuthenticatedSession(session.presentationCompatible)
                authenticatedSession = session
            }
            schedulePresentation()
        }
        override fun onControl(session: AuthenticatedControlSession, message: BrowserControlMessage) {
            if (!pendingControls.tryAcquire()) { session.close(); return }
            val posted = main.post {
                try {
                    synchronized(controlCoordinator.authority) {
                        if (authenticatedSession !== session) return@post
                        hostingController?.status()?.let {
                            controlCoordinator.authority.setHostingGeneration(it.generation.toLong(), it.state == HostingController.State.HOSTING)
                        }
                        processControl(message)?.let { response ->
                            if (!session.sendControl(response)) session.close()
                        }
                    }
                } finally { pendingControls.release() }
            }
            if (!posted) { pendingControls.release(); session.close() }
        }
        override fun onAuthFailed(error: LinkError) = notifyLinkObservers()
    }

    /** Shared main-thread boundary for remote handoff/actions and explicit local recovery. */
    private fun processControl(message: BrowserControlMessage): BrowserControlMessage? {
        val before = controlCoordinator.authority.snapshot().context
        val response = controlCoordinator.receive(message)
        val state = controlCoordinator.authority.snapshot()
        if (before != state.context) authenticatedSession?.setPresentation(null,null)
        if (response is HandoffResultMessage && response.accepted) {
            publisher?.stop()
            if (state.owner == ControlOwner.PHONE) check(hostingController?.presentOnPhone() == true)
            else publisher?.reconcile(state)
            notifyLinkObservers()
        }
        schedulePresentation()
        return response
    }

    /** Phone-local explicit takeover works even after the RG link is lost. */
    fun useOnPhone(): HandoffResultMessage = synchronized(controlCoordinator.authority) {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        val epoch = controlCoordinator.authority.snapshot().controlEpoch
        val result = processControl(HandoffRequestMessage(HandoffTargetWire.PHONE,epoch)) as HandoffResultMessage
        authenticatedSession?.sendControl(result)
        result
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
            )
            return server
        }
    }
}
