package com.code2hack.eyebrowse.core.link.crypto

import com.code2hack.eyebrowse.core.link.testfix.TestCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan §10-B: fingerprint equality, wrong-key rejection, nonce proof semantics. */
class CryptoPrimitiveTest {

    @Test
    fun `spki fingerprint is stable sha256 of encoded key and hex-formatted`() {
        val keyPair = TestCrypto.ecKeyPair()
        val fp = SpkiFingerprint.sha256Hex(keyPair.public.encoded)
        assertEquals(64, fp.length)
        assertEquals(fp, SpkiFingerprint.sha256Hex(keyPair.public.encoded))
        assertEquals(fp, SpkiFingerprint.hex(SpkiFingerprint.sha256(keyPair.public.encoded)))
        assertTrue(fp.all { it in "0123456789abcdef" })
    }

    @Test
    fun `different keys never share a fingerprint`() {
        val a = SpkiFingerprint.sha256Hex(TestCrypto.ecKeyPair().public.encoded)
        val b = SpkiFingerprint.sha256Hex(TestCrypto.ecKeyPair().public.encoded)
        assertNotEquals(a, b)
    }

    @Test
    fun `fingerprint comparison is constant time and content based`() {
        val a = SpkiFingerprint.sha256(TestCrypto.ecKeyPair().public.encoded)
        val b = a.copyOf()
        assertTrue(SpkiFingerprint.equalsConstantTime(a, b))
        b[0] = (b[0] + 1).toByte()
        assertFalse(SpkiFingerprint.equalsConstantTime(a, b))
        assertFalse(SpkiFingerprint.equalsConstantTime(a, ByteArray(32)))
    }

    @Test
    fun `transcript is deterministic and field-sensitive`() {
        val phone = ByteArray(91) { 1 }
        val rg = ByteArray(91) { 2 }
        val nonce = ByteArray(32) { 3 }
        val base = ChallengeTranscript.build(
            ChallengeTranscript.Purpose.INITIAL_PAIRING, 1, 0, phone, rg, nonce, "iid",
        )
        assertTrue(
            base.contentEquals(
                ChallengeTranscript.build(
                    ChallengeTranscript.Purpose.INITIAL_PAIRING, 1, 0, phone, rg, nonce, "iid",
                ),
            ),
        )
        // Magic prefix present
        assertEquals(
            "EyeBrowse-Pairing-Challenge-v1",
            String(base.copyOfRange(0, 30), Charsets.US_ASCII),
        )
        // Purpose change
        assertNotEquals(base, ChallengeTranscript.build(
            ChallengeTranscript.Purpose.RECONNECT, 1, 0, phone, rg, nonce, "iid",
        ))
        // Fresh nonce changes the transcript (old-nonce replay fails against new challenge)
        assertNotEquals(base, ChallengeTranscript.build(
            ChallengeTranscript.Purpose.INITIAL_PAIRING, 1, 0, phone, rg, ByteArray(32) { 4 }, "iid",
        ))
        // Invitation id binding
        assertNotEquals(base, ChallengeTranscript.build(
            ChallengeTranscript.Purpose.INITIAL_PAIRING, 1, 0, phone, rg, nonce, "other",
        ))
        assertNotEquals(base, ChallengeTranscript.build(
            ChallengeTranscript.Purpose.INITIAL_PAIRING, 1, 0, phone, rg, nonce,
        ))
    }

    @Test
    fun `ecdsa proof verifies for the right key and rejects wrong key or modified transcript`() {
        val signer = TestCrypto.ecKeyPair()
        val other = TestCrypto.ecKeyPair()
        val phone = ByteArray(91) { 1 }
        val rg = signer.public.encoded
        val nonce = ByteArray(32) { 7 }
        val transcript = ChallengeTranscript.build(
            ChallengeTranscript.Purpose.INITIAL_PAIRING, 1, 0, phone, rg, nonce, "iid-1",
        )
        val signature = ChallengeTranscript.sign(transcript, signer.private)
        assertTrue(ChallengeTranscript.verify(transcript, signature, signer.public))
        // Wrong key
        assertFalse(ChallengeTranscript.verify(transcript, signature, other.public))
        // Modified transcript
        val modified = transcript.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }
        assertFalse(ChallengeTranscript.verify(modified, signature, signer.public))
        // Garbage signature
        assertFalse(ChallengeTranscript.verify(transcript, ByteArray(16), signer.public))
    }

    @Test
    fun `old-nonce replay is rejected against a fresh challenge`() {
        val signer = TestCrypto.ecKeyPair()
        val phone = ByteArray(91) { 9 }
        val rg = signer.public.encoded
        val oldNonce = ByteArray(32) { 0x0A }
        val oldTranscript = ChallengeTranscript.build(
            ChallengeTranscript.Purpose.RECONNECT, 1, 0, phone, rg, oldNonce,
        )
        val oldSignature = ChallengeTranscript.sign(oldTranscript, signer.private)
        val freshNonce = ByteArray(32) { 0x0B }
        val freshTranscript = ChallengeTranscript.build(
            ChallengeTranscript.Purpose.RECONNECT, 1, 0, phone, rg, freshNonce,
        )
        assertFalse(ChallengeTranscript.verify(freshTranscript, oldSignature, signer.public))
    }
}
