package com.code2hack.eyebrowse.core.link.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T03 listener recovery: a wedged half-open session (no inbound for the full liveness window)
 * must be evictable so an explicit Retry can reconnect; a live link (heartbeat refreshes inbound
 * at least every heartbeat interval) is never evicted.
 */
class StaleSessionEvictionTest {

    private val livenessMs = 30_000L
    private val now = 1_000_000_000_000L

    @Test
    fun `session with no inbound beyond the liveness window is evictable`() {
        val stale = now - (livenessMs + 1) * 1_000_000
        assertTrue(LinkServerEngine.shouldEvictStaleSession(stale, now, livenessMs))
    }

    @Test
    fun `session with recent inbound is never evicted`() {
        val fresh = now - 5_000_000_000L // 5 s ago: well inside the heartbeat cadence
        assertFalse(LinkServerEngine.shouldEvictStaleSession(fresh, now, livenessMs))
    }

    @Test
    fun `session exactly at the liveness boundary is not evicted`() {
        val boundary = now - livenessMs * 1_000_000
        assertFalse(LinkServerEngine.shouldEvictStaleSession(boundary, now, livenessMs))
    }

    @Test
    fun `no tracked session is never evictable`() {
        assertFalse(LinkServerEngine.shouldEvictStaleSession(0L, now, livenessMs))
    }

    /**
     * Review round 1 deterministic integration regression: A is evicted, B becomes active, A
     * finishes afterward — and B REMAINS LINK_UP with status delivery intact. The evicted A's
     * cleanup is owner-fenced: it must NOT clobber B's phase/liveness or emit a stale link-down.
     * The wedge is simulated deterministically by freezing the session's liveness timestamp while
     * its session thread is still alive and serving.
     */
    @Test
    fun `evicted stale session is owner-fenced and the replacement stays link-up`() {
        val timings = com.code2hack.eyebrowse.core.link.LinkTimings(
            connectTimeoutMs = 1_000,
            authTimeoutMs = 2_000,
            heartbeatIntervalMs = 100,
            livenessTimeoutMs = 10_000,
            operationBudgetMs = 8_000,
        )
        val server = Harness.ServerHarness(timings = timings)
        val port = server.start()
        var a: Harness.RawClient? = null
        val aKeepAlive = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            val occupant = com.code2hack.eyebrowse.core.link.testfix.TestCrypto.ecKeyPair()
            val occupantSpki = occupant.public.encoded
            server.storedPeerSpki.set(occupantSpki)
            val pin = server.identity.spkiSha256Hex()

            // A: raw TLS client that authenticates via reconnect and stays LINK_UP.
            a = Harness.RawClient(port, pin, occupant)
            val hello = com.code2hack.eyebrowse.core.link.messages.HelloMessage(
                com.code2hack.eyebrowse.core.link.LinkProtocol.MAJOR,
                com.code2hack.eyebrowse.core.link.LinkProtocol.MINOR,
                com.code2hack.eyebrowse.core.link.LinkProtocol.REQUIRED_CAPABILITIES,
            )
            a.send(hello)
            assertTrue(a.readMessage() is com.code2hack.eyebrowse.core.link.messages.HelloMessage)
            val challenge =
                a.readMessage() as com.code2hack.eyebrowse.core.link.messages.ChallengeMessage
            val nonce = com.code2hack.eyebrowse.core.link.invitation.B64URL.decode(challenge.nonce)!!
            val transcript = com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.build(
                purpose = com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.Purpose.RECONNECT,
                protocolMajor = com.code2hack.eyebrowse.core.link.LinkProtocol.MAJOR,
                protocolMinor = com.code2hack.eyebrowse.core.link.LinkProtocol.MINOR,
                phoneSpki = server.identity.spki(),
                rgSpki = occupantSpki,
                nonce = nonce,
            )
            val signature = com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.sign(
                transcript,
                occupant.private,
            )
            a.send(
                com.code2hack.eyebrowse.core.link.messages.ReconnectAuthMessage(
                    rgSpki = com.code2hack.eyebrowse.core.link.invitation.B64URL.encode(occupantSpki),
                    nonce = challenge.nonce,
                    sig = com.code2hack.eyebrowse.core.link.invitation.B64URL.encode(signature),
                    hello = hello,
                ),
            )
            assertTrue(a.readMessage() is com.code2hack.eyebrowse.core.link.messages.AuthOkMessage)
            assertTrue("A must reach LINK_UP before liveness is frozen", Harness.await(server.linkUp))
            assertTrue(server.engine.isLinkUp())

            // Keep A's session thread alive and its real inbound fresh (no self-reap) until the
            // freeze below: the wedge is "tracking frozen while the thread still serves".
            aKeepAlive.set(true)
            val pinger = Thread {
                while (aKeepAlive.get()) {
                    try {
                        a.send(com.code2hack.eyebrowse.core.link.messages.PingMessage)
                    } catch (_: Exception) {
                        return@Thread
                    }
                    try {
                        Thread.sleep(200)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            }.apply { isDaemon = true; start() }
            Thread.sleep(600) // several real refreshes

            // Freeze the liveness tracking (the wedged state): the predicate now sees the session
            // as provably dead even though its thread is still serving.
            server.engine.activeSessionLastInboundNanos.set(
                System.nanoTime() - (timings.livenessTimeoutMs + 2_000) * 1_000_000,
            )
            aKeepAlive.set(false)
            pinger.join(2_000)

            val linkLostBeforeB = server.linkLostNotifications.get()
            val listenerDownBeforeB = server.listenerLinkDownNotifications.get()

            // B: normal client engine with the SAME stored identity -> eviction -> LINK_UP.
            val bSigner = object : com.code2hack.eyebrowse.core.link.crypto.RgSigningIdentity {
                override fun spki(): ByteArray = occupantSpki
                override fun sign(transcript: ByteArray): ByteArray =
                    com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.sign(
                        transcript,
                        occupant.private,
                    )
            }
            val client = Harness.ClientHarness()
            client.startEngine()
            client.engine.connect(client.attempt(pin, port, null, bSigner))
            assertTrue("B must authenticate after the eviction", Harness.await(client.connected))

            // Drain the auth-time initial status, then verify live delivery to the replacement.
            assertEquals(
                com.code2hack.eyebrowse.core.link.HostStatusValue.HOST_INACTIVE,
                client.statuses.pollFirst(5, java.util.concurrent.TimeUnit.SECONDS),
            )
            server.engine.pushStatus(com.code2hack.eyebrowse.core.link.HostStatusValue.HOSTING)
            assertEquals(
                com.code2hack.eyebrowse.core.link.HostStatusValue.HOSTING,
                client.statuses.pollFirst(5, java.util.concurrent.TimeUnit.SECONDS),
            )

            // Let A's session thread finish AFTER B is active; its fenced cleanup must leave B up.
            Thread.sleep(1_000)
            assertTrue("B must remain LINK_UP after the evicted session finishes", server.engine.isLinkUp())
            server.engine.pushStatus(com.code2hack.eyebrowse.core.link.HostStatusValue.HOST_INACTIVE)
            assertEquals(
                com.code2hack.eyebrowse.core.link.HostStatusValue.HOST_INACTIVE,
                client.statuses.pollFirst(5, java.util.concurrent.TimeUnit.SECONDS),
            )
            // Exactly ONE link-down notification (A's eviction-time down); A's fenced finish and
            // B's ongoing link add none.
            org.junit.Assert.assertEquals(
                linkLostBeforeB + 1,
                server.linkLostNotifications.get(),
            )
            // E1 regression: listener-side link-down is countable and must also be exactly one.
            org.junit.Assert.assertEquals(
                listenerDownBeforeB + 1,
                server.listenerLinkDownNotifications.get(),
            )
            client.engine.disconnect()
        } finally {
            aKeepAlive.set(false)
            runCatching { a?.socket?.close() }
            server.stop()
        }
    }

    /**
     * E2 deterministic regression: the evictor closes stale A while holding the ownership lock.
     * The test waits until A's session-final cleanup has reached the owner-CAS boundary before
     * allowing the evictor to install B. Without the shared ownership lock, A can clear the slot
     * to null first and the same B Retry is spuriously rejected.
     */
    @Test
    fun `stale cleanup forced to owner boundary before install cannot reject the same replacement`() {
        val timings = com.code2hack.eyebrowse.core.link.LinkTimings(
            connectTimeoutMs = 1_000,
            authTimeoutMs = 2_000,
            heartbeatIntervalMs = 100,
            livenessTimeoutMs = 10_000,
            operationBudgetMs = 8_000,
        )
        val server = Harness.ServerHarness(timings = timings)
        val port = server.start()
        var a: Harness.RawClient? = null
        val cleanupReachedOwnerBoundary = java.util.concurrent.CountDownLatch(1)
        val evictionGapReached = java.util.concurrent.CountDownLatch(1)
        val cleanupHookArmed = java.util.concurrent.atomic.AtomicBoolean(true)
        try {
            val occupant = com.code2hack.eyebrowse.core.link.testfix.TestCrypto.ecKeyPair()
            val occupantSpki = occupant.public.encoded
            server.storedPeerSpki.set(occupantSpki)
            val pin = server.identity.spkiSha256Hex()

            // A authenticates through the real reconnect path and remains the current owner.
            a = Harness.RawClient(port, pin, occupant)
            val hello = com.code2hack.eyebrowse.core.link.messages.HelloMessage(
                com.code2hack.eyebrowse.core.link.LinkProtocol.MAJOR,
                com.code2hack.eyebrowse.core.link.LinkProtocol.MINOR,
                com.code2hack.eyebrowse.core.link.LinkProtocol.REQUIRED_CAPABILITIES,
            )
            a.send(hello)
            assertTrue(a.readMessage() is com.code2hack.eyebrowse.core.link.messages.HelloMessage)
            val challenge =
                a.readMessage() as com.code2hack.eyebrowse.core.link.messages.ChallengeMessage
            val nonce = com.code2hack.eyebrowse.core.link.invitation.B64URL.decode(challenge.nonce)!!
            val transcript = com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.build(
                purpose = com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.Purpose.RECONNECT,
                protocolMajor = com.code2hack.eyebrowse.core.link.LinkProtocol.MAJOR,
                protocolMinor = com.code2hack.eyebrowse.core.link.LinkProtocol.MINOR,
                phoneSpki = server.identity.spki(),
                rgSpki = occupantSpki,
                nonce = nonce,
            )
            a.send(
                com.code2hack.eyebrowse.core.link.messages.ReconnectAuthMessage(
                    rgSpki = com.code2hack.eyebrowse.core.link.invitation.B64URL.encode(occupantSpki),
                    nonce = challenge.nonce,
                    sig = com.code2hack.eyebrowse.core.link.invitation.B64URL.encode(
                        com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.sign(
                            transcript,
                            occupant.private,
                        ),
                    ),
                    hello = hello,
                ),
            )
            assertTrue(a.readMessage() is com.code2hack.eyebrowse.core.link.messages.AuthOkMessage)
            assertTrue("A must reach LINK_UP before it is marked stale", Harness.await(server.linkUp))
            assertTrue(server.engine.isLinkUp())

            // Freeze A as stale, then force the exact close -> cleanup -> install ordering.
            server.engine.activeSessionLastInboundNanos.set(
                System.nanoTime() - (timings.livenessTimeoutMs + 2_000) * 1_000_000,
            )
            server.engine.beforeSessionOwnerCleanupForTest = {
                if (cleanupHookArmed.compareAndSet(true, false)) {
                    cleanupReachedOwnerBoundary.countDown()
                }
            }
            server.engine.staleCloseBeforeInstallForTest = {
                evictionGapReached.countDown()
                assertTrue(
                    "A cleanup must reach the owner boundary before B installation",
                    cleanupReachedOwnerBoundary.await(2, java.util.concurrent.TimeUnit.SECONDS),
                )
            }

            val trustDownBefore = server.linkLostNotifications.get()
            val listenerDownBefore = server.listenerLinkDownNotifications.get()
            val bSigner = object : com.code2hack.eyebrowse.core.link.crypto.RgSigningIdentity {
                override fun spki(): ByteArray = occupantSpki
                override fun sign(transcript: ByteArray): ByteArray =
                    com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.sign(
                        transcript,
                        occupant.private,
                    )
            }
            val b = Harness.ClientHarness()
            b.startEngine()
            b.engine.connect(b.attempt(pin, port, null, bSigner))

            assertTrue("forced close/install race seam must execute", Harness.await(evictionGapReached))
            assertTrue("the SAME B Retry must be admitted", Harness.await(b.connected))
            val bLinkUpDeadline = System.nanoTime() + 2_000_000_000L
            while (!server.engine.isLinkUp() && System.nanoTime() < bLinkUpDeadline) {
                Thread.sleep(10)
            }
            assertTrue("B must own a live authenticated link", server.engine.isLinkUp())
            assertEquals(
                com.code2hack.eyebrowse.core.link.HostStatusValue.HOST_INACTIVE,
                b.statuses.pollFirst(5, java.util.concurrent.TimeUnit.SECONDS),
            )

            // A was authenticated, so exactly one truthful down is published by the evictor.
            assertEquals(trustDownBefore + 1, server.linkLostNotifications.get())
            assertEquals(listenerDownBefore + 1, server.listenerLinkDownNotifications.get())

            server.engine.beforeSessionOwnerCleanupForTest = null
            server.engine.staleCloseBeforeInstallForTest = null
            b.engine.disconnect()
        } finally {
            server.engine.beforeSessionOwnerCleanupForTest = null
            server.engine.staleCloseBeforeInstallForTest = null
            runCatching { a?.socket?.close() }
            server.stop()
        }
    }
    /** Healthy non-eviction (integration): a live, refreshing occupant rejects a second client. */
    @Test
    fun `live healthy session is never evicted by a concurrent client`() {
        val server = Harness.ServerHarness() // FAST_TIMINGS: liveness 500 ms
        val port = server.start()
        try {
            val occupant = com.code2hack.eyebrowse.core.link.testfix.TestCrypto.ecKeyPair()
            server.storedPeerSpki.set(occupant.public.encoded)
            val pin = server.identity.spkiSha256Hex()

            val a = Harness.RawClient(port, pin, occupant)
            val hello = com.code2hack.eyebrowse.core.link.messages.HelloMessage(
                com.code2hack.eyebrowse.core.link.LinkProtocol.MAJOR,
                com.code2hack.eyebrowse.core.link.LinkProtocol.MINOR,
                com.code2hack.eyebrowse.core.link.LinkProtocol.REQUIRED_CAPABILITIES,
            )
            a.send(hello)
            assertTrue(a.readMessage() is com.code2hack.eyebrowse.core.link.messages.HelloMessage)
            val challenge =
                a.readMessage() as com.code2hack.eyebrowse.core.link.messages.ChallengeMessage
            val transcript = com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.build(
                purpose = com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.Purpose.RECONNECT,
                protocolMajor = com.code2hack.eyebrowse.core.link.LinkProtocol.MAJOR,
                protocolMinor = com.code2hack.eyebrowse.core.link.LinkProtocol.MINOR,
                phoneSpki = server.identity.spki(),
                rgSpki = occupant.public.encoded,
                nonce = com.code2hack.eyebrowse.core.link.invitation.B64URL.decode(challenge.nonce)!!,
            )
            a.send(
                com.code2hack.eyebrowse.core.link.messages.ReconnectAuthMessage(
                    rgSpki = com.code2hack.eyebrowse.core.link.invitation.B64URL.encode(occupant.public.encoded),
                    nonce = challenge.nonce,
                    sig = com.code2hack.eyebrowse.core.link.invitation.B64URL.encode(
                        com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.sign(
                            transcript,
                            occupant.private,
                        ),
                    ),
                    hello = hello,
                ),
            )
            assertTrue(a.readMessage() is com.code2hack.eyebrowse.core.link.messages.AuthOkMessage)

            // A keeps its inbound FRESH (no freeze): the occupant is healthy, not stale.
            val keepAlive = java.util.concurrent.atomic.AtomicBoolean(true)
            val pinger = Thread {
                while (keepAlive.get()) {
                    try {
                        a.send(com.code2hack.eyebrowse.core.link.messages.PingMessage)
                    } catch (_: Exception) {
                        return@Thread
                    }
                    try {
                        Thread.sleep(100)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            }.apply { isDaemon = true; start() }

            // B arrives while A is live: second-client policy rejects it pre-auth; A stays up.
            val bSigner = object : com.code2hack.eyebrowse.core.link.crypto.RgSigningIdentity {
                override fun spki(): ByteArray = occupant.public.encoded
                override fun sign(transcript: ByteArray): ByteArray =
                    com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.sign(
                        transcript,
                        occupant.private,
                    )
            }
            val client = Harness.ClientHarness()
            client.startEngine()
            client.engine.connect(client.attempt(pin, port, null, bSigner))
            val failure = client.connectFailed.pollFirst(10, java.util.concurrent.TimeUnit.SECONDS)
            org.junit.Assert.assertNotNull(failure)
            assertTrue("A (live) must remain LINK_UP", server.engine.isLinkUp())
            keepAlive.set(false)
            pinger.join(2_000)
            client.engine.disconnect()
        } finally {
            server.stop()
        }
    }
}
