package com.code2hack.eyebrowse.phone.link

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.crypto.SpkiFingerprint
import com.code2hack.eyebrowse.core.link.invitation.InvitationCodec
import com.code2hack.eyebrowse.core.link.invitation.InvitationLifecycle
import com.code2hack.eyebrowse.core.link.session.PeerTrustRead
import com.code2hack.eyebrowse.core.link.session.PeerTrustRecord
import com.code2hack.eyebrowse.core.link.testfix.FakeClock
import com.code2hack.eyebrowse.core.link.transport.Locator
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T02 B1/B2 lifecycle regression over the production-used recovery coordinator:
 * construct under inadequate trust -> fail closed without rotation -> explicit Forget makes
 * trust ABSENT -> legal rotation -> first new QR carries the new TLS identity fingerprint.
 */
class PhoneIdentityRecoveryTest {

    private class FakeInadequateIdentity {
        private val oldSpki = ByteArray(65) { 0x11 }
        private val newSpki = ByteArray(65) { 0x22 }

        var adequate = false
        var rotations = 0
        var ensureCalls = 0
        var fingerprintReads = 0
        var spki: ByteArray = oldSpki

        fun ensure(regenerateIfInadequate: Boolean) {
            ensureCalls += 1
            if (adequate) return
            if (!regenerateIfInadequate) {
                throw IllegalStateException("existing alias is inadequate")
            }
            rotations += 1
            adequate = true
            spki = newSpki
        }

        fun fingerprint(): String {
            fingerprintReads += 1
            if (!adequate) throw IllegalStateException("existing alias is inadequate")
            return SpkiFingerprint.sha256Hex(spki)
        }
    }

    private fun validTrust(): PeerTrustRead.Valid = PeerTrustRead.Valid(
        PeerTrustRecord(
            peerSpkiSha256Hex = "aa",
            peerSpkiB64 = "bb",
            lastLocators = emptyList(),
            protocolMajor = LinkProtocol.MAJOR,
            protocolMinor = LinkProtocol.MINOR,
            peerCapabilities = LinkProtocol.REQUIRED_CAPABILITIES,
        ),
    )

    private fun exerciseRecovery(initialTrust: PeerTrustRead) {
        var trust: PeerTrustRead = initialTrust
        val identity = FakeInadequateIdentity()
        val recovery = PhoneIdentityRecovery(
            readTrust = { trust },
            ensureIdentity = identity::ensure,
            readCurrentFingerprint = identity::fingerprint,
        )
        val manager = PairingInvitationManager(
            lifecycle = InvitationLifecycle(FakeClock()::now),
            phoneSpkiSha256Hex = recovery::currentFingerprint,
            random = { size -> ByteArray(size) { index -> (index + 1).toByte() } },
        )

        // Construction must not probe or rotate the inadequate identity; PairingActivity can
        // therefore render VALID/CORRUPT state and keep explicit Forget reachable.
        assertEquals(0, identity.ensureCalls)
        assertEquals(0, identity.fingerprintReads)
        assertEquals(0, identity.rotations)

        var startFailed = false
        try {
            recovery.ensureUsableIdentity()
        } catch (_: IllegalStateException) {
            startFailed = true
        }
        assertTrue("VALID/CORRUPT inadequate alias must fail closed at use time", startFailed)
        assertEquals("must not silently rotate before Forget", 0, identity.rotations)

        // Explicit Forget clears trust first; only then is repair/rotation legal.
        trust = PeerTrustRead.Absent
        assertTrue(recovery.repairAfterForget().isSuccess)
        assertEquals(1, identity.rotations)
        assertTrue(identity.adequate)

        val locator = Locator(InetAddress.getByName("192.168.1.20"), LinkProtocol.LOCAL_PORT)
        val invitation = manager.generate(listOf(locator))
        val parsed = InvitationCodec.parse(invitation.payload).getOrThrow()
        val currentTlsFingerprint = SpkiFingerprint.sha256Hex(identity.spki)
        assertEquals(
            "first post-Forget QR must pin the newly rotated Phone TLS identity",
            currentTlsFingerprint,
            parsed.phoneSpkiSha256Hex,
        )
        assertEquals(currentTlsFingerprint, recovery.currentFingerprint())
    }

    @Test
    fun `CORRUPT inadequate alias constructs then Forget repairs and refreshes QR pin`() {
        exerciseRecovery(PeerTrustRead.Corrupt)
    }

    @Test
    fun `VALID inadequate alias constructs then Forget repairs and refreshes QR pin`() {
        exerciseRecovery(validTrust())
    }
}
