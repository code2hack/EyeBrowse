package com.code2hack.eyebrowse.core.link.invitation

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.testfix.FakeClock
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan §10-B: one-active, cancel, monotonic expiry, one-time consumption, race protection. */
class InvitationLifecycleTest {

    private fun randomBytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    @Test
    fun `generated invitation is active with correct ttl window`() {
        val clock = FakeClock()
        val lifecycle = InvitationLifecycle(clock::now)
        val invitation = lifecycle.generate("id-1", randomBytes(32))
        assertEquals(InvitationLifecycle.State.ACTIVE, lifecycle.stateOf("id-1"))
        assertEquals(clock.nowMillis, invitation.createdAtMillis)
        assertEquals(LinkProtocol.INVITATION_TTL_SECONDS, invitation.ttlSeconds)
        assertEquals(invitation, lifecycle.activeInvitation())
    }

    @Test
    fun `monotonic expiry is distinguishable and exact at boundary`() {
        val clock = FakeClock()
        val lifecycle = InvitationLifecycle(clock::now)
        val invitation = lifecycle.generate("id-1", randomBytes(32))
        clock.advance(invitation.ttlSeconds * 1000L - 1)
        assertEquals(InvitationLifecycle.State.ACTIVE, lifecycle.stateOf("id-1"))
        clock.advance(1)
        assertEquals(InvitationLifecycle.State.EXPIRED, lifecycle.stateOf("id-1"))
        assertTrue(lifecycle.consume("id-1", invitation.secret) is InvitationLifecycle.ConsumeOutcome.Expired)
    }

    @Test
    fun `replacement cancels the previous invitation`() {
        val lifecycle = InvitationLifecycle(FakeClock()::now)
        val first = lifecycle.generate("id-1", randomBytes(32))
        lifecycle.generate("id-2", randomBytes(32))
        assertEquals(InvitationLifecycle.State.CANCELLED, lifecycle.stateOf("id-1"))
        assertTrue(lifecycle.consume("id-1", first.secret) is InvitationLifecycle.ConsumeOutcome.Cancelled)
        assertEquals(InvitationLifecycle.State.ACTIVE, lifecycle.stateOf("id-2"))
    }

    @Test
    fun `explicit cancel invalidates immediately`() {
        val lifecycle = InvitationLifecycle(FakeClock()::now)
        val invitation = lifecycle.generate("id-1", randomBytes(32))
        assertTrue(lifecycle.cancel())
        assertEquals(InvitationLifecycle.State.CANCELLED, lifecycle.stateOf("id-1"))
        assertTrue(lifecycle.consume("id-1", invitation.secret) is InvitationLifecycle.ConsumeOutcome.Cancelled)
        assertFalse(lifecycle.cancel()) // nothing active anymore
    }

    @Test
    fun `consumption is one-time and outcome distinguishable`() {
        val lifecycle = InvitationLifecycle(FakeClock()::now)
        val invitation = lifecycle.generate("id-1", randomBytes(32))
        val first = lifecycle.consume("id-1", invitation.secret)
        assertTrue(first is InvitationLifecycle.ConsumeOutcome.Consumed)
        val second = lifecycle.consume("id-1", invitation.secret)
        assertTrue(second is InvitationLifecycle.ConsumeOutcome.Reused)
    }

    @Test
    fun `wrong secret and unknown id are invalid`() {
        val lifecycle = InvitationLifecycle(FakeClock()::now)
        val invitation = lifecycle.generate("id-1", randomBytes(32))
        assertTrue(lifecycle.consume("id-1", randomBytes(32)) is InvitationLifecycle.ConsumeOutcome.Invalid)
        assertTrue(lifecycle.consume("nope", invitation.secret) is InvitationLifecycle.ConsumeOutcome.Invalid)
    }

    @Test
    fun `expired invitation keeps EXPIRED terminal state after replacement (B1)`() {
        val clock = FakeClock()
        val lifecycle = InvitationLifecycle(clock::now)
        val first = lifecycle.generate("id-1", randomBytes(32))
        clock.advance(first.ttlSeconds * 1000L + 1)
        assertEquals(InvitationLifecycle.State.EXPIRED, lifecycle.stateOf("id-1"))
        lifecycle.generate("id-2", randomBytes(32))
        assertEquals(
            "replacement must not move EXPIRED to CANCELLED (review R1/B1)",
            InvitationLifecycle.State.EXPIRED,
            lifecycle.stateOf("id-1"),
        )
        assertTrue(lifecycle.consume("id-1", first.secret) is InvitationLifecycle.ConsumeOutcome.Expired)
    }

    @Test
    fun `consumed invitation keeps CONSUMED terminal state after replacement (B1)`() {
        val lifecycle = InvitationLifecycle(FakeClock()::now)
        val first = lifecycle.generate("id-1", randomBytes(32))
        assertTrue(lifecycle.consume("id-1", first.secret) is InvitationLifecycle.ConsumeOutcome.Consumed)
        lifecycle.generate("id-2", randomBytes(32))
        assertEquals(InvitationLifecycle.State.CONSUMED, lifecycle.stateOf("id-1"))
        assertTrue(lifecycle.consume("id-1", first.secret) is InvitationLifecycle.ConsumeOutcome.Reused)
    }

    @Test
    fun `secret comparison is content based and secret bytes are not exposed`() {
        val lifecycle = InvitationLifecycle(FakeClock()::now)
        val secret = randomBytes(32)
        val invitation = lifecycle.generate("id-1", secret)
        assertEquals(LinkProtocol.INVITATION_SECRET_BYTES, invitation.secret.size)
        // Mutating the caller's copy must not change the stored secret.
        secret[0] = (secret[0] + 1).toByte()
        val fresh = randomBytes(32)
        val copy = lifecycle.generate("id-2", fresh)
        fresh[0] = 0
        assertNotNull(lifecycle.consume("id-2", copy.secret))
    }

    @Test
    fun `concurrent consume race admits exactly one winner`() {
        val lifecycle = InvitationLifecycle(FakeClock()::now)
        val invitation = lifecycle.generate("id-1", randomBytes(32))
        val threads = 8
        val winners = java.util.concurrent.atomic.AtomicInteger(0)
        val failures = java.util.concurrent.atomic.AtomicInteger(0)
        val barrier = java.util.concurrent.CountDownLatch(threads)
        val start = java.util.concurrent.CountDownLatch(1)
        repeat(threads) {
            Thread {
                try {
                    start.await()
                    val outcome = lifecycle.consume("id-1", invitation.secret)
                    if (outcome is InvitationLifecycle.ConsumeOutcome.Consumed) {
                        winners.incrementAndGet()
                    } else {
                        failures.incrementAndGet()
                    }
                } catch (e: Exception) {
                    failures.incrementAndGet()
                } finally {
                    barrier.countDown()
                }
            }.apply { start() }
        }
        start.countDown()
        assertTrue(barrier.await(10, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(1, winners.get())
        assertEquals(threads - 1, failures.get())
    }

    @Test
    fun `error mapping covers every distinguishable outcome`() {
        val clock = FakeClock()
        val lifecycle = InvitationLifecycle(clock::now)
        val invitation = lifecycle.generate("id-1", randomBytes(32))
        assertNull(lifecycle.consume("id-1", invitation.secret).toLinkErrorOrNull())

        val cancelled = lifecycle.generate("id-2", randomBytes(32))
        lifecycle.cancel()
        assertEquals(
            com.code2hack.eyebrowse.core.link.LinkError.InvitationCancelled,
            lifecycle.consume("id-2", cancelled.secret).toLinkErrorOrNull(),
        )

        val expired = lifecycle.generate("id-3", randomBytes(32))
        clock.advance(expired.ttlSeconds * 1000L + 1)
        assertEquals(
            com.code2hack.eyebrowse.core.link.LinkError.InvitationExpired,
            lifecycle.consume("id-3", expired.secret).toLinkErrorOrNull(),
        )

        assertEquals(
            com.code2hack.eyebrowse.core.link.LinkError.InvitationReused,
            lifecycle.consume("id-1", invitation.secret).toLinkErrorOrNull(),
        )
    }
}
