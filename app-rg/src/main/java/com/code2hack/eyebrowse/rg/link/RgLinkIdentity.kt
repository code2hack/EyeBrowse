package com.code2hack.eyebrowse.rg.link

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript
import com.code2hack.eyebrowse.core.link.crypto.RgSigningIdentity
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature

/**
 * RG link identity: a persistent non-exportable EC P-256 signing key in AndroidKeyStore used for
 * the invitation/reconnect proof-of-possession (ticket plan §4.2). No key material leaves the
 * Keystore; signatures are produced inside the platform provider.
 */
class RgLinkIdentity() : RgSigningIdentity {

    companion object {
        const val ALIAS: String = "eyebrowse_rg_link_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Idempotently ensures the signing identity exists. */
    @Synchronized
    fun ensureKey() {
        if (keyStore.containsAlias(ALIAS)) return
        val generator = java.security.KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=EyeBrowse RG Link"))
            .build()
        generator.initialize(spec, SecureRandom())
        generator.generateKeyPair()
    }

    override fun spki(): ByteArray {
        ensureKey()
        return keyStore.getCertificate(ALIAS)?.publicKey?.encoded
            ?: throw IllegalStateException("RG link identity certificate missing")
    }

    override fun sign(transcript: ByteArray): ByteArray {
        ensureKey()
        val privateKey = keyStore.getKey(ALIAS, null) as java.security.PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(transcript)
        return signature.sign()
    }
}
