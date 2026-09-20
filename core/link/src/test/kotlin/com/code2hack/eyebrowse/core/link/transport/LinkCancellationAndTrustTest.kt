package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.LinkTimings
import com.code2hack.eyebrowse.core.link.PairingState
import com.code2hack.eyebrowse.core.link.invitation.InvitationLifecycle
import com.code2hack.eyebrowse.core.link.messages.AuthOkMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Review regressions:
 * - B3: cancellation owns the raw socket before TCP connect and is atomic with final trust commit;
 * - B4: one absolute operation deadline bounds TCP + TLS + all application-auth blocking reads;
 * - B6: CORRUPT phone trust fails closed (replacement-required) and never burns invitations;
 * - B1 follow-through: invitation terminal outcomes remain distinguishable on the wire.
 */
class LinkCancellationAndTrustTest {

    private lateinit var server: Harness.ServerHarness
    private var port: Int = 0

    @Before
    fun setUp() {
        server = Harness.ServerHarness()
        port = server.start()
    }

    @After
    fun tearDown() {
        server.helloGate?.countDown()
        server.stop()
    }

    private fun awaitState(
        client: Harness.ClientHarness,
        state: PairingState,
        timeoutMs: Long = 5_000,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (client.stateChanges.contains(state)) return true
            Thread.sleep(10)
        }
        return false
    }

    private fun awaitQuiesce(client: Harness.ClientHarness, timeoutMs: Long = 3_000): Long {
        val start = System.currentTimeMillis()
        while (client.engine.isBusy && System.currentTimeMillis() - start < timeoutMs) {
            Thread.sleep(10)
        }
        return System.currentTimeMillis() - start
    }

    @Test
    fun `cancel during blocked TCP connect owns raw socket and quiesces within bound (B3)`() {
        val client = Harness.ClientHarness()
        client.startEngine()
        val connectEntered = CountDownLatch(1)
        val closeObserved = CountDownLatch(1)
        client.engine.tcpConnectForTest = { socket, _, _ ->
            connectEntered.countDown()
            while (!socket.isClosed) {
                Thread.sleep(5)
            }
            closeObserved.countDown()
            throw SocketException("test connector released by socket close")
        }
        val invitation = server.generateInvitation()

        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue("test connector did not enter blocked TCP connect", connectEntered.await(2, TimeUnit.SECONDS))

        client.engine.disconnect()
        val quiesceMs = awaitQuiesce(client)

        assertTrue("blocked TCP cancellation exceeded 2 s", quiesceMs < 2_000)
        assertTrue("blocked TCP connector did not observe owned socket close", closeObserved.await(500, TimeUnit.MILLISECONDS))
        assertTrue("cancelled TCP connect must not commit trust", client.authenticated.isEmpty())
        assertFalse(client.stateChanges.contains(PairingState.CONNECTED))
        assertTrue("deliberate TCP-connect cancel must not report connect failure", client.connectFailed.isEmpty())
    }

    @Test
    fun `cancel at authenticated precommit boundary cannot persist trust or CONNECTED (B3)`() {
        val client = Harness.ClientHarness()
        client.startEngine()
        val precommitReached = CountDownLatch(1)
        val releasePrecommit = CountDownLatch(1)
        client.engine.beforeFinalCommitForTest = {
            precommitReached.countDown()
            releasePrecommit.await(3, TimeUnit.SECONDS)
        }
        val invitation = server.generateInvitation()

        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue("client never reached deterministic precommit boundary", precommitReached.await(3, TimeUnit.SECONDS))

        client.engine.disconnect()
        releasePrecommit.countDown()
        val quiesceMs = awaitQuiesce(client)

        assertTrue("precommit cancellation exceeded 2 s", quiesceMs < 2_000)
        assertTrue("cancelled precommit must never invoke onAuthenticated", client.authenticated.isEmpty())
        assertFalse(client.stateChanges.contains(PairingState.CONNECTED))
        assertTrue("deliberate precommit cancel must not report connect failure", client.connectFailed.isEmpty())
    }

    @Test
    fun `cancel during stalling auth quiesces bounded and never completes pairing (B3)`() {
        server.helloGate = CountDownLatch(1)
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()

        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue(awaitState(client, PairingState.AUTHENTICATING))
        Thread.sleep(150)

        client.engine.disconnect()
        val quiesceMs = awaitQuiesce(client)
        assertTrue("authentication cancellation exceeded 2 s", quiesceMs < 2_000)
        assertTrue("cancelled auth must never commit trust", client.authenticated.isEmpty())
        assertFalse(client.stateChanges.contains(PairingState.CONNECTED))
        assertTrue("no connect-failure may be reported for a deliberate cancel", client.connectFailed.isEmpty())
        server.helloGate!!.countDown()
    }

    @Test
    fun `cancel during the authenticated session still quiesces and keeps trust decisions consistent (B3)`() {
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()
        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue(Harness.await(client.connected))
        assertTrue(client.authenticated.size >= 1)

        client.engine.disconnect()
        val quiesceMs = awaitQuiesce(client)

        assertTrue("session cancellation exceeded 2 s", quiesceMs < 2_000)
        assertFalse(client.stateChanges.contains(PairingState.PAIRED_DISCONNECTED))
        server.helloGate?.countDown()
    }

    @Test
    fun `slow TCP then stalled auth respects actual aggregate operation deadline (B4)`() {
        server.helloGate = CountDownLatch(1)
        val tight = LinkTimings(
            connectTimeoutMs = 2_000,
            authTimeoutMs = 8_000,
            heartbeatIntervalMs = 100,
            livenessTimeoutMs = 500,
            operationBudgetMs = 2_500,
        )
        val client = Harness.ClientHarness(timings = tight)
        client.startEngine()
        val tcpElapsedMs = AtomicLong(0)
        client.engine.tcpConnectForTest = { socket, address, timeoutMs ->
            val tcpStarted = System.nanoTime()
            Thread.sleep(1_000)
            socket.connect(address, timeoutMs)
            tcpElapsedMs.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tcpStarted))
        }
        val invitation = server.generateInvitation()

        val started = System.nanoTime()
        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        val failure = client.connectFailed.pollFirst(5, TimeUnit.SECONDS)
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertEquals(LinkError.AuthenticationFailed, failure)
        assertTrue("test did not consume a material TCP phase", tcpElapsedMs.get() >= 900)
        assertTrue(
            "operation exceeded configured budget plus 500 ms scheduling slack",
            elapsedMs <= tight.operationBudgetMs + 500,
        )
        assertTrue(client.authenticated.isEmpty())
        assertFalse(client.stateChanges.contains(PairingState.CONNECTED))
        server.helloGate!!.countDown()
        client.engine.disconnect()
    }

    @Test
    fun `TLS and multiple auth reads share one aggregate auth deadline (B4)`() {
        val tight = LinkTimings(
            connectTimeoutMs = 1_000,
            authTimeoutMs = 1_000,
            heartbeatIntervalMs = 100,
            livenessTimeoutMs = 500,
            operationBudgetMs = 4_000,
        )
        server.serverHelloDelayMs = 600
        server.consumeInvitationDelayMs = 800
        val client = Harness.ClientHarness(timings = tight)
        client.startEngine()
        val invitation = server.generateInvitation()

        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue("client never entered AUTHENTICATING", awaitState(client, PairingState.AUTHENTICATING, 2_000))
        val authStartedAt = client.stateTimesNanos[PairingState.AUTHENTICATING]
            ?: throw AssertionError("AUTHENTICATING timestamp missing")
        val failure = client.connectFailed.pollFirst(3, TimeUnit.SECONDS)
        val authElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - authStartedAt)

        assertEquals(
            "reusing a full socket timeout per read would incorrectly allow this authentication",
            LinkError.AuthenticationFailed,
            failure,
        )
        assertTrue(
            "aggregate auth exceeded configured auth cap plus 500 ms scheduling slack",
            authElapsedMs <= tight.authTimeoutMs + 500,
        )
        assertTrue(client.authenticated.isEmpty())
        assertFalse(client.stateChanges.contains(PairingState.CONNECTED))
        client.engine.disconnect()
    }

    @Test
    fun `corrupt phone trust fails closed, refuses pairing and never burns the invitation (B6)`() {
        server.corruptTrust = true
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()

        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        val failure = client.connectFailed.pollFirst(10, TimeUnit.SECONDS)
        assertEquals(LinkError.PeerReplacementRequired, failure)
        assertEquals(
            "the refused attempt must not consume the valid invitation",
            InvitationLifecycle.State.ACTIVE,
            server.lifecycle.stateOf(invitation.first),
        )
        assertNull(client.statuses.peek())
        client.engine.disconnect()

        server.corruptTrust = false
        server.storedPeerSpki.set(null)
        val recovery = Harness.ClientHarness(keyPair = client.keyPair)
        recovery.startEngine()
        val freshInvitation = server.generateInvitation()
        recovery.engine.connect(recovery.attempt(server.identity.spkiSha256Hex(), port, freshInvitation))
        assertTrue(Harness.await(recovery.connected))
        recovery.engine.disconnect()
    }

    @Test
    fun `expired invitation is still distinguishable at the engine after replacement (B1 wire)`() {
        val first = server.generateInvitation()
        server.lifecycle.generate(
            com.code2hack.eyebrowse.core.link.invitation.B64URL.encode(
                randomBytes(com.code2hack.eyebrowse.core.link.LinkProtocol.INVITATION_ID_BYTES),
            ),
            randomBytes(com.code2hack.eyebrowse.core.link.LinkProtocol.INVITATION_SECRET_BYTES),
        )
        val raw = Harness.RawClient(port, server.identity.spkiSha256Hex())
        raw.sendHello()
        raw.expectChallenge()
        raw.sendPairAuth(first.first, first.second)
        assertEquals(
            com.code2hack.eyebrowse.core.link.messages.AuthErrMessage("INVITATION_CANCELLED"),
            raw.readMessage(),
        )
        raw.close()
    }

    @Test
    fun `forget notice never appears before authentication completes (B7)`() {
        val invitation = server.generateInvitation()
        val raw = Harness.RawClient(port, server.identity.spkiSha256Hex())
        raw.sendHello()
        raw.expectChallenge()

        server.engine.sendForgetNotice()
        raw.sendPairAuth(invitation.first, invitation.second)
        val next = raw.readMessage()
        assertTrue(
            "expected the auth result next; pre-auth Forget would expose a control frame",
            next is AuthOkMessage,
        )
        raw.close()
    }
}
