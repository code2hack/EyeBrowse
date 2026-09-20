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

        /**
         * Digests the platform TLS stack needs from the server identity key (S20+ KeyMint
         * evidence: Conscrypt signs the TLS 1.3 CertificateVerify through a raw NONEwithECDSA
         * upcall; absent digest authorizations are treated as "none authorized", so DIGEST_NONE
         * must be granted explicitly alongside the per-suite SHA-256/384/512 digests, with
         * SIGN-only purpose — VERIFY in the purpose set breaks the raw upcall).
         */
        val TLS_DIGESTS = arrayOf(
            KeyProperties.DIGEST_NONE,
            KeyProperties.DIGEST_SHA256,
            KeyProperties.DIGEST_SHA384,
            KeyProperties.DIGEST_SHA512,
        )
    }

    /**
     * TLS usability check, verified EMPIRICALLY: Conscrypt's native TLS upcall signs through
     * raw NONEwithECDSA, so the identity key must be able to produce a raw ECDSA signature.
     * (KeyInfo digest metadata proved unreliable on the device: a key generated with
     * DIGEST_NONE authorized may not report it, yet still sign raw — the signature attempt is
     * the ground truth the TLS stack depends on.) An alias that cannot sign raw is inadequate
     * and is upgraded only through the pre-pairing regeneration path.
     */
    private fun supportsRawEcdsa(): Boolean = try {
        val key = keyStore.getKey(ALIAS, null) as? java.security.PrivateKey ?: return false
        val signature = java.security.Signature.getInstance("NONEwithECDSA")
        signature.initSign(key)
        signature.update(ByteArray(32))
        signature.sign()
        true
    } catch (e: Exception) {
        false
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Idempotently ensures the identity key exists (survives process death, not uninstall).
     * Explicitly requests secp256r1 (review R4/B5) and validates any pre-existing alias against
     * that curve and the digest policy platform TLS actually needs — an inadequate identity
     * fails closed instead of being silently reused (device evidence: Conscrypt TLS upcalls
     * require unrestricted/NONEwithECDSA digests; a digest-restricted alias dies mid-handshake).
     */
    @Synchronized
    fun ensureKey() = ensureKeyOrUpgrade(regenerateIfInadequate = false)

    /**
     * As [ensureKey], but an EXISTING alias whose digest specification is inadequate for TLS may
     * be regenerated only when `regenerateIfInadequate` is set — which the server grants only
     * when pairing trust is definitively ABSENT. VALID and CORRUPT both fail closed; CORRUPT must
     * first recover through explicit Forget, which clears trust before rotation is permitted.
     */
    @Synchronized
    fun ensureKeyOrUpgrade(regenerateIfInadequate: Boolean) {
        if (keyStore.containsAlias(ALIAS)) {
            val existing = keyStore.getCertificate(ALIAS)?.publicKey
                ?: throw IllegalStateException("Phone link identity alias exists without certificate")
            check(EcKeys.isP256(existing)) {
                "existing Phone link identity is not EC P-256; explicit identity reset required"
            }
            if (!supportsRawEcdsa()) {
                check(regenerateIfInadequate) {
                    "existing Phone link identity cannot sign raw ECDSA (TLS upcall); explicit identity reset required"
                }
                keyStore.deleteEntry(ALIAS)
            } else {
                return
            }
        }
        val generator = java.security.KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            // SIGN only: device evidence (KeyMint spec matrix) shows VERIFY in the purpose set
            // breaks the raw NONEwithECDSA upcall Conscrypt needs for the TLS 1.3 CertificateVerify.
            KeyProperties.PURPOSE_SIGN,
        )
            .setDigests(*TLS_DIGESTS)
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
