package com.code2hack.eyebrowse.core.link.crypto

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

/**
 * Versioned challenge transcript (ticket plan §4.2). Canonical, length-prefixed byte sequence
 * signed with standard `SHA256withECDSA`:
 *
 * `"EyeBrowse-Pairing-Challenge-v1" | u8 purpose | u16 protoMajor | u16 protoMinor |
 *  len||phoneSpki | len||rgSpki | len||nonce | len||invitationIdUtf8`
 *
 * The fresh server nonce binds every signature to the current challenge, so replaying an old
 * signature always fails. No home-grown cipher/KDF: only canonical serialization + JCA signatures.
 */
object ChallengeTranscript {

    const val MAGIC: String = "EyeBrowse-Pairing-Challenge-v1"

    enum class Purpose(val code: Int) { INITIAL_PAIRING(1), RECONNECT(2) }

    fun build(
        purpose: Purpose,
        protocolMajor: Int,
        protocolMinor: Int,
        phoneSpki: ByteArray,
        rgSpki: ByteArray,
        nonce: ByteArray,
        invitationId: String = "",
    ): ByteArray {
        val out = ByteArrayOutputStream(128)
        out.write(MAGIC.toByteArray(StandardCharsets.UTF_8))
        out.write(purpose.code)
        writeU16(out, protocolMajor)
        writeU16(out, protocolMinor)
        writeBlob(out, phoneSpki)
        writeBlob(out, rgSpki)
        writeBlob(out, nonce)
        writeBlob(out, invitationId.toByteArray(StandardCharsets.UTF_8))
        return out.toByteArray()
    }

    fun sign(transcript: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(transcript)
        return signature.sign()
    }

    /** True when [signature] verifies over [transcript] with the standard algorithm. */
    fun verify(transcript: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(transcript)
        return try {
            verifier.verify(signature)
        } catch (e: java.security.SignatureException) {
            false
        }
    }

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        require(value in 0..0xFFFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeBlob(out: ByteArrayOutputStream, blob: ByteArray) {
        require(blob.size <= 0xFFFF)
        writeU16(out, blob.size)
        out.write(blob)
    }
}
