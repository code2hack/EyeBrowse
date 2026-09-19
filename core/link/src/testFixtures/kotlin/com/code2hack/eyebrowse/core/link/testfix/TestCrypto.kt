package com.code2hack.eyebrowse.core.link.testfix

import com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript
import com.code2hack.eyebrowse.core.link.crypto.RgSigningIdentity
import com.code2hack.eyebrowse.core.link.crypto.SpkiFingerprint
import com.code2hack.eyebrowse.core.link.crypto.TlsServerIdentity
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * JVM test-only crypto material: software EC P-256 keys and BouncyCastle self-signed certificates
 * standing in for the AndroidKeyStore identities (plan §15: host tests bind abstract identity
 * interfaces; device tests bind the real Keystore). Never used by production code.
 */
object TestCrypto {

    fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        }.generateKeyPair()

    fun selfSignedCertificate(keyPair: KeyPair, commonName: String): java.security.cert.X509Certificate {
        val issuer = X500Name("CN=$commonName")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(0xEB0000L).add(BigInteger.valueOf(now)),
            Date(now - 60_000),
            Date(now + 365L * 24 * 3600 * 1000),
            issuer,
            keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    fun softwareTlsIdentity(keyPair: KeyPair, certificate: java.security.cert.X509Certificate): TlsServerIdentity {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry("test-identity", keyPair.private, null, arrayOf(certificate))
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, null)
        }.keyManagers
        val context = SSLContext.getInstance("TLS")
        context.init(keyManagers, null, null)
        return object : TlsServerIdentity {
            override fun tlsContext(): SSLContext = context
            override fun spki(): ByteArray = certificate.publicKey.encoded
            override fun spkiSha256Hex(): String = SpkiFingerprint.sha256Hex(certificate.publicKey.encoded)
        }
    }

    fun softwareSigningIdentity(keyPair: KeyPair): RgSigningIdentity = object : RgSigningIdentity {
        override fun spki(): ByteArray = keyPair.public.encoded
        override fun sign(transcript: ByteArray): ByteArray =
            ChallengeTranscript.sign(transcript, keyPair.private)
    }
}

/** Controllable monotonic-ish clock for fake-time tests. */
class FakeClock(var nowMillis: Long = 1_000_000) {
    fun now(): Long = nowMillis
    fun advance(deltaMillis: Long) {
        nowMillis += deltaMillis
    }
}
