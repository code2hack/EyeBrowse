package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.LinkTimings
import com.code2hack.eyebrowse.core.link.PairingState
import com.code2hack.eyebrowse.core.link.invitation.B64URL
import com.code2hack.eyebrowse.core.link.invitation.InvitationLifecycle
import com.code2hack.eyebrowse.core.link.messages.AuthErrMessage
import com.code2hack.eyebrowse.core.link.messages.AuthOkMessage
import com.code2hack.eyebrowse.core.link.messages.ChallengeMessage
import com.code2hack.eyebrowse.core.link.messages.HelloMessage
import com.code2hack.eyebrowse.core.link.messages.StatusMessage
import com.code2hack.eyebrowse.core.link.testfix.FakeClock
import com.code2hack.eyebrowse.core.link.testfix.TestCrypto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end TLS engine tests over localhost with software EC identities (plan §10-B host tests;
 * AndroidKeyStore binds on device in T02+).
 */
class LinkEnginePairingTest {

    private lateinit var server: Harness.ServerHarness
    private var port: Int = 0

    @Before
    fun setUp() {
        server = Harness.ServerHarness()
        port = server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `initial pairing completes and consumes the invitation exactly once`() {
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()

        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))

        assertTrue(Harness.await(server.committed))
        assertTrue(server.committedPeer.get()!!.contentEquals(client.keyPair.public.encoded))
        assertEquals(InvitationLifecycle.State.CONSUMED, server.lifecycle.stateOf(invitation.first))
        assertTrue(Harness.await(client.connected))
        assertTrue(Harness.await(server.linkUp))
        // First protected status arrives only after authentication.
        assertEquals(HostStatusValue.HOST_INACTIVE, client.statuses.pollFirst(5, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(client.stateChanges.contains(PairingState.AUTHENTICATING))
        client.engine.disconnect()
    }

    @Test
    fun `reconnect authenticates with stored peer and no invitation secret`() {
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()

        // Establish the pairing first (also stores the peer server-side).
        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue(Harness.await(server.committed))
        client.engine.disconnect()
        // Deliberate client disconnect: no PAIRED_DISCONNECTED event; the server observes the drop.
        assertTrue(Harness.await(server.linkDown))
        assertTrue(server.linkLostNotifications.get() >= 1)

        // Fresh engine, reconnect without any invitation.
        val reconnecting = Harness.ClientHarness(keyPair = client.keyPair)
        reconnecting.startEngine()
        reconnecting.engine.connect(reconnecting.attempt(server.identity.spkiSha256Hex(), port, null))
        assertTrue(Harness.await(reconnecting.connected))
        assertEquals(
            HostStatusValue.HOST_INACTIVE,
            reconnecting.statuses.pollFirst(5, java.util.concurrent.TimeUnit.SECONDS),
        )
        reconnecting.engine.disconnect()
    }

    @Test
    fun `reconnect with an unknown peer key is refused as wrong rg identity`() {
        server.storedPeerSpki.set(TestCrypto.ecKeyPair().public.encoded) // paired to someone else
        val client = Harness.ClientHarness()
        client.startEngine()
        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, null))
        val failure = client.connectFailed.pollFirst(10, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(LinkError.WrongRgIdentity, failure)
        assertNull(client.statuses.peek())
        client.engine.disconnect()
    }

    @Test
    fun `wrong key signature fails authentication and never yields status`() {
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()
        val imposter = TestCrypto.ecKeyPair()

        // Presented SPKI belongs to the imposter, signature is made by a different key.
        val lyingSigner = object : com.code2hack.eyebrowse.core.link.crypto.RgSigningIdentity {
            override fun spki(): ByteArray = imposter.public.encoded
            override fun sign(transcript: ByteArray): ByteArray =
                com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.sign(transcript, client.keyPair.private)
        }
        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation, lyingSigner))

        val failure = client.connectFailed.pollFirst(10, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(LinkError.AuthenticationFailed, failure)
        assertNull("no protected status may arrive on failed auth", client.statuses.peek())
        assertFalse(server.committed.await(0, java.util.concurrent.TimeUnit.MILLISECONDS))
        // A failed initial pairing must not burn the invitation (refused pre-consume).
        assertEquals(InvitationLifecycle.State.ACTIVE, server.lifecycle.stateOf(invitation.first))
        client.engine.disconnect()
    }

    @Test
    fun `expired cancelled reused and invalid invitations are distinguishable`() {
        // Expired: fake clock past TTL.
        val clock = FakeClock()
        val expiredServer = Harness.ServerHarness(lifecycle = InvitationLifecycle(clock::now))
        val expiredPort = expiredServer.start()
        val secret = randomBytes(LinkProtocol.INVITATION_SECRET_BYTES)
        expiredServer.lifecycle.generate("iid-expired", secret)
        clock.advance((LinkProtocol.INVITATION_TTL_SECONDS + 1) * 1000L)
        val rawExpired = Harness.RawClient(expiredPort, expiredServer.identity.spkiSha256Hex())
        rawExpired.sendHello()
        rawExpired.expectChallenge()
        rawExpired.sendPairAuth("iid-expired", secret)
        assertEquals(AuthErrMessage("INVITATION_EXPIRED"), rawExpired.readMessage())
        rawExpired.close()
        expiredServer.stop()

        // Cancelled before use.
        val cancelledServer = Harness.ServerHarness()
        val cancelledPort = cancelledServer.start()
        val cancelledSecret = randomBytes(LinkProtocol.INVITATION_SECRET_BYTES)
        cancelledServer.lifecycle.generate("iid-cancelled", cancelledSecret)
        cancelledServer.lifecycle.cancel()
        val rawCancelled = Harness.RawClient(cancelledPort, cancelledServer.identity.spkiSha256Hex())
        rawCancelled.sendHello()
        rawCancelled.expectChallenge()
        rawCancelled.sendPairAuth("iid-cancelled", cancelledSecret)
        assertEquals(AuthErrMessage("INVITATION_CANCELLED"), rawCancelled.readMessage())
        rawCancelled.close()
        cancelledServer.stop()

        // Reused: engine pairs once, then the same invitation is replayed raw.
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()
        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue(Harness.await(server.committed))
        client.engine.disconnect()
        assertTrue(Harness.await(server.linkDown))
        val rawReused = Harness.RawClient(port, server.identity.spkiSha256Hex(), keyPair = client.keyPair)
        rawReused.sendHello()
        rawReused.expectChallenge()
        rawReused.sendPairAuth(invitation.first, invitation.second)
        assertEquals(AuthErrMessage("INVITATION_REUSED"), rawReused.readMessage())
        rawReused.close()

        // Invalid: unknown invitation id.
        val rawInvalid = Harness.RawClient(port, server.identity.spkiSha256Hex(), keyPair = client.keyPair)
        rawInvalid.sendHello()
        rawInvalid.expectChallenge()
        rawInvalid.sendPairAuth("iid-unknown", randomBytes(LinkProtocol.INVITATION_SECRET_BYTES))
        assertEquals(AuthErrMessage("INVITATION_INVALID"), rawInvalid.readMessage())
        rawInvalid.close()
    }

    @Test
    fun `different rg while paired is refused before burning the invitation`() {
        val first = Harness.ClientHarness()
        first.startEngine()
        val firstInvitation = server.generateInvitation()
        first.engine.connect(first.attempt(server.identity.spkiSha256Hex(), port, firstInvitation))
        assertTrue(Harness.await(server.committed))
        first.engine.disconnect()

        val second = Harness.ClientHarness() // different key pair
        second.startEngine()
        val freshInvitation = server.generateInvitation()
        second.engine.connect(second.attempt(server.identity.spkiSha256Hex(), port, freshInvitation))
        val failure = second.connectFailed.pollFirst(10, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(LinkError.PeerReplacementRequired, failure)
        assertEquals(
            "refusal must not consume the fresh invitation",
            InvitationLifecycle.State.ACTIVE,
            server.lifecycle.stateOf(freshInvitation.first),
        )
        assertNull(second.statuses.peek())
        second.engine.disconnect()
    }

    @Test
    fun `second concurrent client is closed pre-auth and receives no status`() {
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()
        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue(Harness.await(client.connected))

        // Server must reject the concurrent client pre-auth: either the TLS handshake is
        // terminated outright, or the connection closes before any application frame.
        val rejectedCleanly = try {
            val intruder = Harness.RawClient(port, server.identity.spkiSha256Hex())
            assertNull(intruder.readRawFrame())
            intruder.close()
            true
        } catch (e: javax.net.ssl.SSLHandshakeException) {
            true // closed during handshake: rejected before any status could be sent
        }
        assertTrue(rejectedCleanly)

        // The established link is unaffected and keeps receiving latest-state statuses.
        server.engine.pushStatus(HostStatusValue.HOSTING)
        var hostingSeen = false
        val deadline = System.currentTimeMillis() + 5_000
        while (!hostingSeen && System.currentTimeMillis() < deadline) {
            client.statuses.pollFirst(200, java.util.concurrent.TimeUnit.MILLISECONDS)?.let {
                hostingSeen = hostingSeen || it == HostStatusValue.HOSTING
            }
        }
        assertTrue(hostingSeen)
        client.engine.disconnect()
    }

    @Test
    fun `wrong pin client fails handshake as wrong phone identity`() {
        val client = Harness.ClientHarness()
        client.startEngine()
        val otherPhone = TestCrypto.ecKeyPair()
        val otherPin = com.code2hack.eyebrowse.core.link.crypto.SpkiFingerprint.sha256Hex(otherPhone.public.encoded)
        client.engine.connect(client.attempt(otherPin, port, null))
        val failure = client.connectFailed.pollFirst(10, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(LinkError.WrongPhoneIdentity, failure)
        client.engine.disconnect()
    }

    @Test
    fun `forget notice reaches the peer and the link ends in paired-disconnected`() {
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()
        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue(Harness.await(client.connected))

        server.engine.sendForgetNotice()
        assertTrue(Harness.await(client.disconnected))
        assertTrue(client.stateChanges.contains(PairingState.PAIRED_DISCONNECTED))
        client.engine.disconnect()
    }

    @Test
    fun `host status push keeps latest state flowing to the authenticated peer`() {
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()
        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue(Harness.await(client.connected))

        server.engine.pushStatus(HostStatusValue.HOST_STARTING)
        server.engine.pushStatus(HostStatusValue.HOSTING)
        var hostingSeen = false
        var startingSeen = false
        val deadline = System.currentTimeMillis() + 5_000
        while (!hostingSeen && System.currentTimeMillis() < deadline) {
            client.statuses.pollFirst(200, java.util.concurrent.TimeUnit.MILLISECONDS)?.let {
                if (it == HostStatusValue.HOSTING) hostingSeen = true
                if (it == HostStatusValue.HOST_STARTING) startingSeen = true
            }
        }
        assertTrue("latest status must reach the peer", hostingSeen)
        assertTrue("intermediate state should also flow: $startingSeen", startingSeen)
        client.engine.disconnect()
    }

    @Test
    fun `plan operation bounds are the product defaults`() {
        val t = LinkTimings.PRODUCT
        assertEquals(3_000L, t.connectTimeoutMs)
        assertEquals(5_000L, t.authTimeoutMs)
        assertEquals(10_000L, t.heartbeatIntervalMs)
        assertEquals(30_000L, t.livenessTimeoutMs)
        assertEquals(10_000L, t.operationBudgetMs)
        assertEquals(39818, LinkProtocol.LOCAL_PORT)
        assertEquals(180, LinkProtocol.INVITATION_TTL_SECONDS)
    }
}
