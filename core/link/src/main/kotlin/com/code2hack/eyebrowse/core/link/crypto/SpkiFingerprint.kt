package com.code2hack.eyebrowse.core.link.crypto

import com.code2hack.eyebrowse.core.link.invitation.MessageDigestEquals
import java.security.MessageDigest
import java.security.PublicKey

/**
 * SHA-256 SPKI fingerprint helpers (ticket plan §4.1/§4.3). The fingerprint over the
 * SubjectPublicKeyInfo DER encoding is the trust anchor identity on both endpoints.
 */
object SpkiFingerprint {

    fun sha256(spkiDer: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(spkiDer)

    fun sha256OfKey(key: PublicKey): ByteArray = sha256(key.encoded)

    /** Lowercase hex, 64 chars for SHA-256. */
    fun hex(digest: ByteArray): String = buildString(digest.size * 2) {
        for (b in digest) {
            val v = b.toInt() and 0xFF
            append(HEX[v ushr 4])
            append(HEX[v and 0x0F])
        }
    }

    fun sha256Hex(spkiDer: ByteArray): String = hex(sha256(spkiDer))

    fun isValidHexFingerprint(text: String): Boolean =
        text.length == 64 && text.all { it in "0123456789abcdef" }

    fun fromHex(hex: String): ByteArray? {
        if (!isValidHexFingerprint(hex)) return null
        return ByteArray(32) { i ->
            ((hexValue(hex[2 * i]) shl 4) or hexValue(hex[2 * i + 1])).toByte()
        }
    }

    /** Constant-time fingerprint/secret comparison (plan §4.1). */
    fun equalsConstantTime(a: ByteArray, b: ByteArray): Boolean = MessageDigestEquals.equal(a, b)

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        else -> throw IllegalArgumentException("bad hex")
    }

    private const val HEX = "0123456789abcdef"
}
