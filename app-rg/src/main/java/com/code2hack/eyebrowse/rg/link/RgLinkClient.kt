package com.code2hack.eyebrowse.rg.link

import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.LinkTimings
import com.code2hack.eyebrowse.core.link.PairingState
import com.code2hack.eyebrowse.core.link.crypto.RgSigningIdentity
import com.code2hack.eyebrowse.core.link.invitation.InvitationCodec
import com.code2hack.eyebrowse.core.link.messages.HelloMessage
import com.code2hack.eyebrowse.core.link.session.BindingPolicy
import com.code2hack.eyebrowse.core.link.session.PeerTrustRead
import com.code2hack.eyebrowse.core.link.session.PeerTrustRecord
import com.code2hack.eyebrowse.core.link.transport.LinkClientEngine
import com.code2hack.eyebrowse.core.link.transport.Locator
import com.code2hack.eyebrowse.core.link.transport.LocatorPolicy

/**
 * The RG-side link client (ticket plan §6 RgLinkClient): TLS with exact SPKI pinning from the
 * scanned invitation (initial) or the stored record (reconnect), invitation/reconnect proof,
 * authenticated status consumption and bounded retry semantics. Trust is committed only after a
 * proven authentication; a locator is never an identity.
 */
class RgLinkClient(
    private val signer: RgSigningIdentity,
    private val store: RgPairingStore,
    private val listener: Listener,
    private val timings: LinkTimings = LinkTimings.PRODUCT,
) {

    interface Listener {
        fun onStateChange(state: PairingState)
        fun onStatus(status: HostStatusValue)
        fun onLinkLost()
        fun onConnectFailed(error: LinkError)
    }

    @Volatile
    private var engine: LinkClientEngine? = null

    private val engineListener = object : LinkClientEngine.Listener {
        override fun onStateChange(state: PairingState) = listener.onStateChange(state)

        override fun onStatus(status: HostStatusValue) = listener.onStatus(status)

        override fun onLinkLost() = listener.onLinkLost()

        override fun onConnectFailed(error: LinkError) = listener.onConnectFailed(error)

        override fun onAuthenticated(phoneSpki: ByteArray, usedLocator: Locator) {
            // Trust commit happens strictly after the pinned TLS peer proved possession of the
            // invitation or its remembered identity (plan §4.2/§8). Locator refresh is allowed
            // for the SAME pinned identity (plan §8 "Changed locator").
            store.save(
                PeerTrustRecord(
                    peerSpkiSha256Hex = com.code2hack.eyebrowse.core.link.crypto.SpkiFingerprint.sha256Hex(phoneSpki),
                    peerSpkiB64 = com.code2hack.eyebrowse.core.link.invitation.B64URL.encode(phoneSpki),
                    lastLocators = listOf(usedLocator.toWire()),
                    protocolMajor = LinkProtocol.MAJOR,
                    protocolMinor = LinkProtocol.MINOR,
                    peerCapabilities = LinkProtocol.REQUIRED_CAPABILITIES,
                ),
            )
        }
    }

    /** Begins pairing from a scanned QR payload (T02 scanner feeds this). */
    fun pairFromQr(payload: String) {
        if (engine?.isBusy == true) return
        val parsed = InvitationCodec.parse(payload).getOrElse {
            listener.onConnectFailed(LinkError.MalformedQr)
            return
        }
        // Peer replacement rules (plan §8): a different Phone identity in the QR while paired
        // requires explicit Forget before any connection attempt. CORRUPT stored trust fails
        // closed as replacement-required (review R5/B6).
        val replacement = BindingPolicy.rgAcceptInvitation(
            store.read(),
            parsed.phoneSpkiSha256Hex,
        )
        if (replacement != null) {
            listener.onConnectFailed(replacement)
            return
        }
        startEngine().connect(
            LinkClientEngine.Attempt(
                phoneSpkiSha256Hex = parsed.phoneSpkiSha256Hex,
                locators = LocatorPolicy.orderForAttempts(parsed.locators),
                signer = signer,
                clientHello = hello(),
                invitation = parsed.invitationId to parsed.invitationSecret,
            ),
        )
    }

    /** Explicit Retry: reconnect with the stored peer; no QR and no invitation secret (§8). */
    fun reconnect() {
        if (engine?.isBusy == true) return
        val record = when (val read = store.read()) {
            is PeerTrustRead.Valid -> read.record
            is PeerTrustRead.Corrupt ->
                // Fail closed: explicit Forget required before any reconnect (review R5/B6).
                return listener.onConnectFailed(LinkError.PeerReplacementRequired)
            is PeerTrustRead.Absent ->
                return listener.onConnectFailed(LinkError.InvitationInvalid)
        }
        val locators = record.lastLocators.mapNotNull { Locator.parse(it) }
        if (locators.isEmpty()) {
            listener.onConnectFailed(LinkError.NetworkUnreachable)
            return
        }
        startEngine().connect(
            LinkClientEngine.Attempt(
                phoneSpkiSha256Hex = record.peerSpkiSha256Hex,
                locators = LocatorPolicy.orderForAttempts(locators),
                signer = signer,
                clientHello = hello(),
                invitation = null,
            ),
        )
    }

    /** Bounded disconnect of any owned connection work. */
    fun disconnect() {
        engine?.disconnect()
    }

    /** Locally authoritative Forget: close the link and remove peer trust (§8). */
    fun forget() {
        disconnect()
        store.clear()
    }

    fun isPaired(): Boolean = store.isPaired()

    private fun startEngine(): LinkClientEngine =
        engine ?: LinkClientEngine(timings, engineListener).also { engine = it }

    private fun hello(): HelloMessage =
        HelloMessage(LinkProtocol.MAJOR, LinkProtocol.MINOR, LinkProtocol.REQUIRED_CAPABILITIES)
}
