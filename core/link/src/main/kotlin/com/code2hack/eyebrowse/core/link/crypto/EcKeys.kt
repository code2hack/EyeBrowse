package com.code2hack.eyebrowse.core.link.crypto

import java.math.BigInteger
import java.security.PublicKey
import java.security.interfaces.ECPublicKey

/**
 * Explicit curve validation for link identities (review R4/B5: production identities must be
 * EC P-256 / secp256r1, never provider-default). Standard JCA inspection only.
 */
object EcKeys {

    const val SECP256R1_NAME: String = "secp256r1"

    /** NIST P-256 / secp256r1 order. */
    private val P256_ORDER = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)

    private val P256_FIELD = java.security.spec.ECFieldFp(
        BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
    )

    /** True only for an EC public key on exactly secp256r1 (P-256). */
    fun isP256(publicKey: PublicKey): Boolean {
        if (publicKey !is ECPublicKey) return false
        val params = publicKey.params ?: return false
        val field = params.curve.field
        return field is java.security.spec.ECFieldFp &&
            field.p == P256_FIELD.p &&
            params.order == P256_ORDER &&
            params.curve.field.fieldSize == 256
    }
}
