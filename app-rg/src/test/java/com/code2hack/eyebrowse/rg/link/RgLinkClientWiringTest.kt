package com.code2hack.eyebrowse.rg.link

import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.invitation.B64URL
import com.code2hack.eyebrowse.core.link.invitation.InvitationCodec
import com.code2hack.eyebrowse.core.link.session.PeerTrustRecord
import com.code2hack.eyebrowse.core.link.testfix.TestCrypto
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * RG link client wiring: pre-connection binding/replacement decisions and trust-store seam
 * (plan §8/§10-B). Full TLS/auth engine behavior is exercised by the core:link host suites.
 */
class RgLinkClientWiringTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class RecordingListener : RgLinkClient.Listener {
        val failures = mutableListOf<LinkError>()
        val states = mutableListOf<com.code2hack.eyebrowse.core.link.PairingState>()
        val statuses = mutableListOf<com.code2hack.eyebrowse.core.link.HostStatusValue>()
        override fun onStateChange(state: com.code2hack.eyebrowse.core.link.PairingState) { states.add(state) }
        override fun onStatus(status: com.code2hack.eyebrowse.core.link.HostStatusValue) { statuses.add(status) }
        override fun onLinkLost() {}
        override fun onConnectFailed(error: LinkError) { failures.add(error) }
    }

    private fun payloadFor(phoneFp: String): String {
        val locators = listOf(
            com.code2hack.eyebrowse.core.link.transport.Locator(
                java.net.InetAddress.getByName("192.168.1.20"),
                com.code2hack.eyebrowse.core.link.LinkProtocol.LOCAL_PORT,
            ),
        )
        return InvitationCodec.encode(
            invitationId = B64URL.encode(ByteArray(16) { 1 }),
            invitationSecret = ByteArray(32) { 2 },
            phoneSpkiSha256Hex = phoneFp,
            locators = locators,
        )
    }

    private fun newStore(): RgPairingStore = RgPairingStore(File(tmp.newFolder(), "peer.json"))

    private fun awaitState(listener: RecordingListener, state: com.code2hack.eyebrowse.core.link.PairingState): Boolean {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (listener.states.contains(state)) return true
            Thread.sleep(20)
        }
        return false
    }

    private fun clientWith(listener: RecordingListener, store: RgPairingStore = newStore()): RgLinkClient =
        RgLinkClient(TestCrypto.softwareSigningIdentity(TestCrypto.ecKeyPair()), store, listener)

    private fun pairedRecord(phoneFp: String) = PeerTrustRecord(
        peerSpkiSha256Hex = phoneFp,
        peerSpkiB64 = "cGhvbmlk",
        lastLocators = listOf("192.168.1.20:39818"),
        protocolMajor = 1,
        protocolMinor = 0,
        peerCapabilities = listOf("PAIRING_V1", "STATUS_V1"),
    )

    @Test
    fun `retry without remembered trust maps to invitation-invalid without dialing`() {
        val listener = RecordingListener()
        clientWith(listener).reconnect()
        assertEquals(listOf(LinkError.InvitationInvalid), listener.failures)
        assertTrue(listener.states.isEmpty())
    }

    @Test
    fun `reconnect with a locator-less stored record maps to network-unreachable`() {
        val listener = RecordingListener()
        val store = newStore()
        store.save(pairedRecord("ab".repeat(32)).copy(lastLocators = listOf()))
        clientWith(listener, store).reconnect()
        assertEquals(listOf(LinkError.NetworkUnreachable), listener.failures)
        assertTrue(listener.states.isEmpty())
    }

    @Test
    fun `malformed qr is refused locally as malformed`() {
        val listener = RecordingListener()
        clientWith(listener).pairFromQr("https://not-an-eyebrowse-payload")
        assertEquals(listOf(LinkError.MalformedQr), listener.failures)
        assertTrue(listener.states.isEmpty()) // never even enters CONNECTING
    }

    @Test
    fun `scanning a different phone while paired demands explicit forget before dialing`() {
        val listener = RecordingListener()
        val store = newStore()
        store.save(pairedRecord("ab".repeat(32)))
        val client = clientWith(listener, store)
        assertTrue(client.isPaired())

        client.pairFromQr(payloadFor("cd".repeat(32)))
        assertEquals(listOf(LinkError.PeerReplacementRequired), listener.failures)
        assertTrue(listener.states.isEmpty())

        // Forget clears trust; a subsequent pairing decision may then proceed (plan §8).
        client.forget()
        assertFalse(client.isPaired())
    }

    @Test
    fun `same phone qr is accepted past the replacement check and enters connecting`() {
        val listener = RecordingListener()
        val store = newStore()
        store.save(pairedRecord("ab".repeat(32)))
        val client = clientWith(listener, store)

        client.pairFromQr(payloadFor("ab".repeat(32)))
        // Same pinned identity: no replacement refusal; the bounded engine starts dialing.
        assertTrue(awaitState(listener, com.code2hack.eyebrowse.core.link.PairingState.CONNECTING))
        client.disconnect()
    }

    @Test
    fun `reconnect without pairing is refused without dialing`() {
        val listener = RecordingListener()
        clientWith(listener).reconnect()
        assertEquals(listOf(LinkError.InvitationInvalid), listener.failures)
        assertTrue(listener.states.isEmpty())
    }

    @Test
    fun `reconnect with stored locators starts the bounded operation`() {
        val listener = RecordingListener()
        val store = newStore()
        store.save(pairedRecord("ab".repeat(32)))
        val client = clientWith(listener, store)

        client.reconnect()
        assertTrue(awaitState(listener, com.code2hack.eyebrowse.core.link.PairingState.CONNECTING))
        client.disconnect()
    }

    @Test
    fun `corrupt rg trust refuses pairing and reconnect until forget (B6)`() {
        val listener = RecordingListener()
        val dir = tmp.newFolder()
        val trustFile = java.io.File(dir, "pairing/peer.json")
        val store = RgPairingStore(trustFile)
        store.save(pairedRecord("ab".repeat(32)))
        trustFile.writeText("{ corrupt trust blob")
        val client = clientWith(listener, store)

        // Even the SAME pinned phone must not auto-pair over unreadable trust (fail closed).
        client.pairFromQr(payloadFor("ab".repeat(32)))
        assertEquals(listOf(LinkError.PeerReplacementRequired), listener.failures)
        assertTrue(listener.states.isEmpty())

        client.reconnect()
        assertEquals(
            "reconnect over corrupt trust must demand explicit Forget (review R5/B6)",
            listOf(LinkError.PeerReplacementRequired, LinkError.PeerReplacementRequired),
            listener.failures,
        )
        assertTrue(listener.states.isEmpty())

        // Forget clears the corrupt state; pairing may proceed afterwards.
        client.forget()
        assertFalse(client.isPaired())
    }

    @Test
    fun `store seam persists across instances and forget clears`() {
        val dir = tmp.newFolder()
        val store = RgPairingStore(File(dir, "pairing/peer.json"))
        assertFalse(store.isPaired())
        store.save(pairedRecord("ab".repeat(32)))
        assertTrue(RgPairingStore(File(dir, "pairing/peer.json")).isPaired())
        store.clear()
        assertFalse(store.isPaired())
    }
}
