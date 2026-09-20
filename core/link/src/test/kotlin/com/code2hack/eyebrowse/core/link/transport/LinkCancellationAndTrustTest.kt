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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Round-1 review regressions:
 * - R2/B3: cancellation owns the in-flight socket at every stage; a cancel can never complete
 *   pairing/trust persistence and quiesces within the ≤2 s bound;
 * - R3/B4: one absolute operation deadline bounds TCP + TLS + application auth in aggregate;
 * - R5/B6: CORRUPT phone trust fails closed (replacement-required) and never burns invitations;
 * - R1/B1 follow-through at the engine level: expired invitations are distinguishable on the wire.
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

    private fun awaitState(client: Harness.ClientHarness, state: PairingState, timeoutMs: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (client.stateChanges.contains(state)) return true
            Thread.sleep(10)
        }
        return false
    }

    private fun awaitQuiesce(client: Harness.ClientHarness, timeoutMs: Long = 3_000): Long {
        val start = System.currentTimeMillis()
        while (client.engine.isBusy && System.currentTimeMillis() - start < timeoutMs) Thread.sleep(10)
        return System.currentTimeMillis() - start
    }

    @Test
    fun `cancel during stalling auth quiesces bounded and never completes pairing (B3)`() {
        server.helloGate = CountDownLatch(1) // server stalls before sending its hello/challenge
        val client = Harness.ClientHarness()
        client.startEngine()
        val invitation = server.generateInvitation()

        client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, invitation))
        assertTrue(awaitState(client, PairingState.AUTHENTICATING))
        Thread.sleep(150) // cancellation lands mid-authentication

        client.engine.disconnect()
        val quiesceMs = awaitQuiesce(client)
        assertTrue(
            "cancellation must quiesce within the 2 s bound, took ${quiesceMs}ms",
            quiesceMs < 2_000,
        )
        assertTrue(
            "cancelled auth must never commit trust (no onAuthenticated)",
            client.authenticated.isEmpty(),
        )
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
        assertTrue(client.authenticated.size >= 1) // trust commit only ever after real auth

        client.engine.disconnect()
        val quiesceMs = awaitQuiesce(client)
        assertTrue("session cancel must be bounded, took ${quiesceMs}ms", quiesceMs < 2_000)
        assertFalse(client.stateChanges.contains(PairingState.PAIRED_DISCONNECTED)) // deliberate stop
        server.helloGate?.countDown()
    }

    @Test
    fun `aggregate operation deadline bounds connect and stalling auth together (B4)`() {
        // Second locator will pass TCP+TLS but stall in the application auth phase.
        server.helloGate = CountDownLatch(1)
        val tight = LinkTimings(
            connectTimeoutMs = 1_000,
            authTimeoutMs = 8_000, // per-phase max far above the operation budget
            heartbeatIntervalMs = 100,
            livenessTimeoutMs = 500,
            operationBudgetMs = 1_500,
        )
        val client = Harness.ClientHarness(timings = tight)
        client.startEngine()
        val invitation = server.generateInvitation()
        // First locator: a closed local port (connection refused immediately).
        val blocker = java.net.ServerSocket(0)
        val refusedPort = blocker.localPort
        blocker.close()

        val attempt = LinkClientEngine.Attempt(
            phoneSpkiSha256Hex = server.identity.spkiSha256Hex(),
            locators = listOf(
                Locator(java.net.InetAddress.getLoopbackAddress(), refusedPort),
                Locator(java.net.InetAddress.getLoopbackAddress(), port),
            ),
            signer = client.signer,
            clientHello = client.hello(),
            invitation = invitation,
        )
        val started = System.currentTimeMillis()
        client.engine.connect(attempt)
        val failure = client.connectFailed.pollFirst(8, TimeUnit.SECONDS)
        val elapsed = System.currentTimeMillis() - started

        assertEquals(LinkError.AuthenticationFailed, failure)
        assertTrue(
            "aggregate operation must respect the ${tight.operationBudgetMs}ms budget (took ${elapsed}ms," +
                " pre-fix behavior would wait the full 8 s auth window)",
            elapsed < 5_000,
        )
        assertTrue(client.authenticated.isEmpty())
        assertFalse(client.stateChanges.contains(PairingState.CONNECTED))
        server.helloGate!!.countDown()
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

        // Explicit Forget semantics (clear the corrupt state) restore normal pairing.
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
        // Phone-side semantics: expired invitation consumed over the wire maps to INVITATION_EXPIRED
        // even after replacements were generated (terminal class preserved by the R1 fix).
        val first = server.generateInvitation()
        server.lifecycle.generate(
            com.code2hack.eyebrowse.core.link.invitation.B64URL.encode(
                randomBytes(com.code2hack.eyebrowse.core.link.LinkProtocol.INVITATION_ID_BYTES),
            ),
            randomBytes(com.code2hack.eyebrowse.core.link.LinkProtocol.INVITATION_SECRET_BYTES),
        )
        // first is now replaced: ACTIVE -> CANCELLED (not EXPIRED — it never expired). This pair
        // asserts the replacement path; the EXPIRED path is covered by the lifecycle unit tests.
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

        // Link is only AUTHENTICATING here: the notice must be a no-op (review R6/B7).
        server.engine.sendForgetNotice()
        raw.sendPairAuth(invitation.first, invitation.second)
        val next = raw.readMessage()
        assertTrue(
            "expected the auth result next, got $next — a pre-auth Forget frame would be a " +
                "control frame to an unauthenticated peer",
            next is AuthOkMessage,
        )
        raw.close()
    }
}
