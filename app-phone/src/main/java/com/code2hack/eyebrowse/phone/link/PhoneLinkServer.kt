package com.code2hack.eyebrowse.phone.link

import android.content.Context
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
 * active RG link (ticket plan §6 PhoneLinkServer). Hosting remains an independent, explicitly
 * started session; this class only READS its status — never start()/stop()/leases.
 *
 * Process-scoped lifecycle: starting from Phone UI is sufficient for inactive-host pairing and
 * retry; while the existing HostingService keeps the process alive the singleton stays alive.
 */
class PhoneLinkServer(
    private val identity: PhoneLinkIdentity,
    private val invitations: PairingInvitationManager,
    private val store: PhonePairingStore,
    private val hostingController: HostingController?,
    private val locators: () -> List<Locator>,
) {

    @Volatile
    private var engine: LinkServerEngine? = null

    private val hostingListener = HostingController.Listener {
        // Hosting state transition (main thread): push the latest-state observation if linked.
        engine?.pushStatus(currentHostStatus())
    }

    private val linkObservers = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    private val engineListener = object : LinkServerEngine.Listener {
        override fun onLinkUp() = notifyLinkObservers()
        override fun onLinkDown() = notifyLinkObservers()
        override fun onAuthFailed(error: LinkError) = notifyLinkObservers()
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
            HelloMessage(LinkProtocol.MAJOR, LinkProtocol.MINOR, LinkProtocol.REQUIRED_CAPABILITIES)

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
        if (engine != null) return
        hostingController?.addListener(hostingListener)
        val newEngine = LinkServerEngine(identity, trustController, LinkTimings.PRODUCT, engineListener)
        newEngine.start(LinkProtocol.LOCAL_PORT)
        engine = newEngine
    }

    @Synchronized
    fun stop(sendForgetNotice: Boolean = false) {
        hostingController?.removeListener(hostingListener)
        engine?.let {
            if (sendForgetNotice) it.sendForgetNotice()
            it.stop()
        }
        engine = null
    }

    fun isLinkUp(): Boolean = engine?.isLinkUp() ?: false

    /** Usable pairing exists (VALID trust; CORRUPT intentionally reports false and fails closed). */
    fun isPaired(): Boolean = store.read() is PeerTrustRead.Valid

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
            // Identity regeneration is legal only before a VALID pairing exists (ledger D12).
            identity.ensureKeyOrUpgrade(regenerateIfInadequate = store.read() !is PeerTrustRead.Valid)
            val invitations = PairingInvitationManager(
                lifecycle = InvitationLifecycle { android.os.SystemClock.elapsedRealtime() },
                phoneSpkiSha256Hex = identity.spkiSha256Hex(),
            )
            val server = PhoneLinkServer(
                identity = identity,
                invitations = invitations,
                store = store,
                hostingController = runCatching { HostingController.get(appContext) }.getOrNull(),
                locators = { PhoneLocatorEnumerator.enumerate(appContext) },
            )
            return server
        }
    }
}
