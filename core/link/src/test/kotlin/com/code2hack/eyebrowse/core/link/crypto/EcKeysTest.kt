package com.code2hack.eyebrowse.core.link.crypto

import com.code2hack.eyebrowse.core.link.testfix.TestCrypto
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review R4/B5: link identities are EC P-256 (secp256r1) explicitly, never provider-default. */
class EcKeysTest {

    @Test
    fun `secp256r1 keys validate`() {
        assertTrue(EcKeys.isP256(TestCrypto.ecKeyPair().public))
    }

    @Test
    fun `non-P256 curves and non-EC keys are rejected`() {
        val p384 = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp384r1"))
        }.generateKeyPair().public
        assertFalse(EcKeys.isP256(p384))

        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
        assertFalse(EcKeys.isP256(rsa))
    }
}
