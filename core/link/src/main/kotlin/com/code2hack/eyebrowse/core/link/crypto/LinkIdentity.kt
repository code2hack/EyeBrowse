package com.code2hack.eyebrowse.core.link.crypto

import javax.net.ssl.SSLContext

/**
 * Identity seams (ticket plan §4/§6, §15 "host tests use abstract identity interfaces and device
 * tests bind real Keystore"). Production implementations hold AndroidKeyStore keys; JVM tests use
 * software EC keys. No implementation may export private key material.
 */
interface LinkSigningIdentity {
    /** SubjectPublicKeyInfo DER of this endpoint's EC P-256 identity key. */
    fun spki(): ByteArray

    /** Standard `SHA256withECDSA` signature over the exact transcript bytes. */
    fun sign(transcript: ByteArray): ByteArray
}

/** TLS server-side identity (the Phone). The SSLContext is the platform JSSE context. */
interface TlsServerIdentity {
    fun tlsContext(): SSLContext

    /** SubjectPublicKeyInfo DER of the certificate presented to TLS clients. */
    fun spki(): ByteArray

    /** SHA-256 SPKI fingerprint hex of that certificate. */
    fun spkiSha256Hex(): String
}

/** Signing identity of the RG used for invitation/reconnect proof-of-possession. */
interface RgSigningIdentity : LinkSigningIdentity
