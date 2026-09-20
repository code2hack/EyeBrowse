package com.code2hack.eyebrowse.phone.link

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.code2hack.eyebrowse.core.link.crypto.EcKeys
import com.code2hack.eyebrowse.core.link.crypto.SpkiFingerprint
import com.code2hack.eyebrowse.core.link.crypto.TlsServerIdentity
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Phone link identity: a persistent EC P-256 key in AndroidKeyStore exposed to platform TLS
 * (ticket plan §4.1). Private key material never leaves the Keystore; the platform-generated
 * self-signed certificate carries the public key whose SPKI SHA-256 fingerprint is the QR pin.
 */
class PhoneLinkIdentity() : TlsServerIdentity {

    companion object {
        const val ALIAS: String = "eyebrowse_phone_link_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Idempotently ensures the identity key exists (survives process death, not uninstall).
     * Explicitly requests secp256r1 (review R4/B5) and validates any pre-existing alias against
     * that curve — a non-P-256 identity fails closed instead of being silently reused.
     */
    @Synchronized
    fun ensureKey() {
        if (keyStore.containsAlias(ALIAS)) {
            val existing = keyStore.getCertificate(ALIAS)?.publicKey
                ?: throw IllegalStateException("Phone link identity alias exists without certificate")
            check(EcKeys.isP256(existing)) {
                "existing Phone link identity is not EC P-256; explicit identity reset required"
            }
            return
        }
        val generator = java.security.KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec(EcKeys.SECP256R1_NAME))
            .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=EyeBrowse Phone Link"))
            .build()
        generator.initialize(spec, SecureRandom())
        generator.generateKeyPair()
    }

    private fun certificate(): java.security.cert.Certificate {
        ensureKey()
        return keyStore.getCertificate(ALIAS)
            ?: throw IllegalStateException("Phone link identity certificate missing")
    }

    override fun tlsContext(): SSLContext {
        ensureKey()
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, null)
        }.keyManagers
        val context = SSLContext.getInstance("TLS")
        context.init(keyManagers, null, null)
        return context
    }

    override fun spki(): ByteArray = certificate().publicKey.encoded

    override fun spkiSha256Hex(): String = SpkiFingerprint.sha256Hex(spki())
}
