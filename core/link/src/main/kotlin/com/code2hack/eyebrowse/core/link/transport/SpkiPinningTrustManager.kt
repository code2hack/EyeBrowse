package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.crypto.SpkiFingerprint
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Exact SHA-256 SPKI pinning trust manager (ticket plan §4.1). The pinned Phone key is the trust
 * anchor for an IP-literal/local locator: every certificate whose leaf SPKI fingerprint is not
 * the pin is rejected. DNS-name validation is intentionally omitted for this local protocol and
 * this trust manager never accepts an unverified certificate — there is no accept-all path.
 */
class SpkiPinningTrustManager(private val pinnedSpkiSha256Hex: String) : X509ExtendedTrustManager() {

    init {
        require(SpkiFingerprint.isValidHexFingerprint(pinnedSpkiSha256Hex))
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = validate(chain)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        validate(chain)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        validate(chain)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        // No client certificates in this protocol; a client-trust check is a protocol error.
        throw CertificateException("client certificates are not used by the EyeBrowse link")
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

    private fun validate(chain: Array<X509Certificate>) {
        if (chain.isEmpty()) throw CertificateException("empty certificate chain")
        val leafDigest = SpkiFingerprint.sha256(chain[0].publicKey.encoded)
        val pinBytes = SpkiFingerprint.fromHex(pinnedSpkiSha256Hex)
            ?: throw CertificateException("malformed pin")
        if (!SpkiFingerprint.equalsConstantTime(leafDigest, pinBytes)) {
            throw CertificateException("server SPKI does not match the pinned Phone identity")
        }
    }
}
