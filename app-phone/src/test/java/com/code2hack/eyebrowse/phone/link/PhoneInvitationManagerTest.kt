package com.code2hack.eyebrowse.phone.link

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.invitation.B64URL
import com.code2hack.eyebrowse.core.link.invitation.InvitationCodec
import com.code2hack.eyebrowse.core.link.invitation.InvitationLifecycle
import com.code2hack.eyebrowse.core.link.transport.Locator
import com.code2hack.eyebrowse.core.link.testfix.FakeClock
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phone invitation manager wiring over the pure lifecycle with a fake monotonic clock and
 * deterministic random (plan §5 invitation rules; Keystore-independent).
 */
class PhoneInvitationManagerTest {

    private val fp = "ab".repeat(32)
    private val locators = listOf(Locator(InetAddress.getByName("192.168.1.20"), LinkProtocol.LOCAL_PORT))

    private class FixedRandom(seed: Long = 7) : (Int) -> ByteArray {
        private var state = seed
        override fun invoke(size: Int): ByteArray = ByteArray(size) {
            state = state * 6364136223846793005L + 1442695040888963407L
            (state ushr 33).toByte()
        }
    }

    private fun manager(clock: FakeClock) = PairingInvitationManager(
        lifecycle = InvitationLifecycle(clock::now),
        phoneSpkiSha256Hex = { fp },
        random = FixedRandom(),
    )

    @Test
    fun `fingerprint is lazy and later invitation uses current Phone identity (B2 wiring)`() {
        val clock = FakeClock()
        var currentFp = "11".repeat(32)
        var reads = 0
        val manager = PairingInvitationManager(
            lifecycle = InvitationLifecycle(clock::now),
            phoneSpkiSha256Hex = {
                reads += 1
                currentFp
            },
            random = FixedRandom(),
        )

        assertEquals("manager construction must not touch Phone identity", 0, reads)
        val first = InvitationCodec.parse(manager.generate(locators).payload).getOrThrow()
        assertEquals("11".repeat(32), first.phoneSpkiSha256Hex)

        currentFp = "22".repeat(32)
        val second = InvitationCodec.parse(manager.generate(locators).payload).getOrThrow()
        assertEquals("22".repeat(32), second.phoneSpkiSha256Hex)
        assertEquals(2, reads)
    }

    @Test
    fun `fingerprint failure occurs before invitation lifecycle mutation (B1 wiring)`() {
        val clock = FakeClock()
        val manager = PairingInvitationManager(
            lifecycle = InvitationLifecycle(clock::now),
            phoneSpkiSha256Hex = { throw IllegalStateException("inadequate alias") },
            random = FixedRandom(),
        )

        var thrown = false
        try {
            manager.generate(locators)
        } catch (_: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown)
        assertNull(manager.activeInvitation())
    }

    @Test
    fun `generated invitation payload parses and carries plan-required fields`() {
        val clock = FakeClock()
        val manager = manager(clock)
        val active = manager.generate(locators)

        val parsed = InvitationCodec.parse(active.payload).getOrThrow()
        assertEquals(1, parsed.protocolMajor)
        assertEquals(0, parsed.protocolMinor)
        assertEquals(fp, parsed.phoneSpkiSha256Hex)
        assertEquals(LinkProtocol.INVITATION_TTL_SECONDS, parsed.ttlSeconds)
        assertEquals(LinkProtocol.INVITATION_SECRET_BYTES, parsed.invitationSecret.size)
        assertEquals(LinkProtocol.INVITATION_ID_BYTES, B64URL.decode(parsed.invitationId)!!.size)
        assertTrue(parsed.hasRequiredCapabilities())
        assertEquals(1, parsed.locators.size)
        assertEquals(active.id, parsed.invitationId)
        assertEquals(clock.nowMillis + LinkProtocol.INVITATION_TTL_SECONDS * 1000L, active.expiresAtElapsedMillis)
    }

    @Test
    fun `regeneration replaces the single active invitation`() {
        val manager = manager(FakeClock())
        val first = manager.generate(locators)
        val second = manager.generate(locators)
        assertEquals(second.id, manager.activeInvitation()!!.id)
        assertFalse(first.payload == second.payload)
    }

    @Test
    fun `consuming a stale invitation never hides a newer active one (B2)`() {
        val manager = manager(FakeClock())
        val stale = manager.generate(locators)
        val fresh = manager.generate(locators)
        val staleSecret = InvitationCodec.parse(stale.payload).getOrThrow().invitationSecret
        // The stale invitation was REPLACED, so its consume outcome is CANCELLED (B1 semantics);
        // regardless of outcome class, the manager surface must keep the newer valid invitation.
        val outcome = manager.consumeForServer(stale.id, B64URL.encode(staleSecret))
        assertTrue(outcome is InvitationLifecycle.ConsumeOutcome.Cancelled)
        assertEquals(
            "manager surface must keep the newly generated valid invitation (review R1/B2)",
            fresh.id,
            manager.activeInvitation()!!.id,
        )
    }

    @Test
    fun `generate and consume race keeps the manager surface consistent (B2)`() {
        val manager = manager(FakeClock())
        repeat(50) {
            val previous = manager.generate(locators)
            val previousSecret = B64URL.encode(InvitationCodec.parse(previous.payload).getOrThrow().invitationSecret)
            val consumer = Thread { manager.consumeForServer(previous.id, previousSecret) }
            consumer.start()
            val next = manager.generate(locators)
            consumer.join()
            val surface = manager.activeInvitation()
            if (surface != null) {
                assertEquals(next.id, surface.id)
            }
        }
    }

    @Test
    fun `cancel invalidates the active invitation`() {
        val manager = manager(FakeClock())
        manager.generate(locators)
        assertTrue(manager.cancel())
        assertNull(manager.activeInvitation())
        assertFalse(manager.cancel())
    }

    @Test
    fun `server consumption is one-time and clears the active invitation`() {
        val clock = FakeClock()
        val manager = manager(clock)
        val active = manager.generate(locators)
        val parsed = InvitationCodec.parse(active.payload).getOrThrow()

        val outcome = manager.consumeForServer(parsed.invitationId, B64URL.encode(parsed.invitationSecret))
        assertTrue(outcome is InvitationLifecycle.ConsumeOutcome.Consumed)
        assertNull(manager.activeInvitation())
        assertTrue(
            manager.consumeForServer(parsed.invitationId, B64URL.encode(parsed.invitationSecret))
                is InvitationLifecycle.ConsumeOutcome.Reused,
        )
    }

    @Test
    fun `expired invitation is distinguishable at consume time`() {
        val clock = FakeClock()
        val manager = manager(clock)
        val active = manager.generate(locators)
        clock.advance(LinkProtocol.INVITATION_TTL_SECONDS * 1000L + 1)
        val outcome = manager.consumeForServer(
            active.id,
            B64URL.encode(InvitationCodec.parse(active.payload).getOrThrow().invitationSecret),
        )
        assertTrue(outcome is InvitationLifecycle.ConsumeOutcome.Expired)
    }

    @Test
    fun `wrong secret is invalid and invitation secrets are never exposed on the manager`() {
        val manager = manager(FakeClock())
        val active = manager.generate(locators)
        // Only the QR payload (which legitimately carries the secret to the scanner) and the
        // manager surface exist; there is no getter returning raw secret bytes.
        val methods = PairingInvitationManager::class.java.declaredMethods.map { it.name }
        assertFalse(methods.any { it.lowercase().contains("secret") && it.lowercase().contains("get") })
        val outcome = manager.consumeForServer(
            active.id,
            com.code2hack.eyebrowse.core.link.invitation.B64URL.encode(
                ByteArray(LinkProtocol.INVITATION_SECRET_BYTES),
            ),
        )
        assertTrue(outcome is InvitationLifecycle.ConsumeOutcome.Invalid)
        assertNotNull(InvitationCodec.parse(active.payload).getOrNull())
    }
}
