package com.code2hack.eyebrowse.core.link.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T02 review round 1, blocker B1: automatic identity regeneration is legal ONLY for definitively
 * ABSENT trust. CORRUPT never authorizes delete/regenerate of the TLS identity alias — it keeps
 * the replacement-required semantics and recovers only through the explicit Forget path (after
 * which trust is ABSENT and regeneration is allowed). VALID never rotates (ledger D12).
 */
class IdentityRotationTest {

    @Test
    fun `regeneration is allowed only for absent trust`() {
        assertTrue(IdentityRotation.regenerateAllowed(PeerTrustRead.Absent))
    }

    @Test
    fun `corrupt trust does not authorize identity rotation`() {
        assertFalse(IdentityRotation.regenerateAllowed(PeerTrustRead.Corrupt))
    }

    @Test
    fun `valid trust does not authorize identity rotation`() {
        assertFalse(
            IdentityRotation.regenerateAllowed(
                PeerTrustRead.Valid(
                    PeerTrustRecord(
                        peerSpkiSha256Hex = "aa",
                        peerSpkiB64 = "bb",
                        lastLocators = listOf(),
                        protocolMajor = 1,
                        protocolMinor = 0,
                        peerCapabilities = listOf("PAIRING_V1", "STATUS_V1"),
                    ),
                ),
            ),
        )
    }
}
