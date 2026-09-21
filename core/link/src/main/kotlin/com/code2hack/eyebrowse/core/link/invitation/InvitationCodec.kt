package com.code2hack.eyebrowse.core.link.invitation

import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.transport.Locator
import com.code2hack.eyebrowse.core.link.transport.LocatorPolicy

/**
 * QR invitation payload v1 (ticket plan §5). Compact versioned pairing URI carrying pairing
 * metadata only — never SSID/password/hotspot configuration, RG credentials, browser/site data,
 * or a persisted shared secret. Rendering is T02; this is the authoritative codec.
 *
 * Wire form:
 * `eyebrowse-pair:v1?pv=1&maj=1&min=0&cap=<bits>&iid=<b64url16>&sec=<b64url32>&fp=<64hex>&loc=<l>[|<l>]&ttl=<s>`
 *
 * Strict v1 grammar: unknown query keys are rejected (payload version gates evolution);
 * unknown capability *bits* are explicitly permitted and ignored by the v1 codec
 * (plan §5: "unknown optional fields/minor capabilities are ignored only where the v1 codec
 * explicitly permits it").
 */
object InvitationCodec {

    const val SCHEME_PREFIX: String = "eyebrowse-pair:v1?"

    /** Capability bit assignments (v1). Unknown bits permitted, ignored. */
    const val CAP_BIT_PAIRING_V1: Int = 1
    const val CAP_BIT_STATUS_V1: Int = 2

    class Payload(
        val protocolMajor: Int,
        val protocolMinor: Int,
        val capabilityBits: Int,
        val invitationId: String,
        val invitationSecret: ByteArray,
        val phoneSpkiSha256Hex: String,
        val locators: List<Locator>,
        val ttlSeconds: Int,
    ) {
        fun hasRequiredCapabilities(): Boolean =
            (capabilityBits and CAP_BIT_PAIRING_V1) != 0 && (capabilityBits and CAP_BIT_STATUS_V1) != 0
    }

    fun encode(
        protocolMajor: Int = LinkProtocol.MAJOR,
        protocolMinor: Int = LinkProtocol.MINOR,
        capabilityBits: Int = CAP_BIT_PAIRING_V1 or CAP_BIT_STATUS_V1,
        invitationId: String,
        invitationSecret: ByteArray,
        phoneSpkiSha256Hex: String,
        locators: List<Locator>,
        ttlSeconds: Int = LinkProtocol.INVITATION_TTL_SECONDS,
    ): String {
        require(invitationSecret.size == LinkProtocol.INVITATION_SECRET_BYTES) {
            "invitation secret must be ${LinkProtocol.INVITATION_SECRET_BYTES} bytes"
        }
        require(phoneSpkiSha256Hex.length == 64 && phoneSpkiSha256Hex.all { it in "0123456789abcdef" }) {
            "phone fingerprint must be 64 lowercase hex chars"
        }
        val emittable = locators.filter { LocatorPolicy.isEmittable(it) }
        require(emittable.isNotEmpty()) { "no emittable locator" }
        val locWire = emittable.joinToString("|") { it.toWire() }
        val payload = buildString {
            append(SCHEME_PREFIX)
            append("pv=").append(LinkProtocol.QR_PAYLOAD_VERSION)
            append("&maj=").append(protocolMajor)
            append("&min=").append(protocolMinor)
            append("&cap=").append(capabilityBits)
            append("&iid=").append(invitationId)
            append("&sec=").append(B64URL.encode(invitationSecret))
            append("&fp=").append(phoneSpkiSha256Hex)
            append("&loc=").append(locWire)
            append("&ttl=").append(ttlSeconds)
        }
        require(payload.length <= LinkProtocol.QR_PAYLOAD_MAX_CHARS) { "QR payload too long" }
        return payload
    }

    /** Strict parse; any violation yields [LinkError.MalformedQr] as a failure result. */
    fun parse(raw: String): Result<Payload> {
        val s = raw.trim()
        if (s.length > LinkProtocol.QR_PAYLOAD_MAX_CHARS) return malformed("too long")
        if (!s.startsWith(SCHEME_PREFIX)) return malformed("scheme/version")
        val query = s.substring(SCHEME_PREFIX.length)
        if (query.isEmpty()) return malformed("empty query")
        val params = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) return malformed("empty pair")
            val eq = pair.indexOf('=')
            if (eq <= 0) return malformed("pair without key/value")
            val key = pair.substring(0, eq)
            val value = pair.substring(eq + 1)
            if (key !in ALLOWED_KEYS) return malformed("unknown key $key")
            if (params.containsKey(key)) return malformed("duplicate key $key")
            params[key] = value
        }
        val pv = params["pv"]?.toIntOrNull()
        if (pv != LinkProtocol.QR_PAYLOAD_VERSION) return malformed("pv")
        val maj = params["maj"]?.toIntOrNull() ?: return malformed("maj")
        if (maj !in 1..9) return malformed("maj range")
        val min = params["min"]?.toIntOrNull() ?: return malformed("min")
        if (min !in 0..99) return malformed("min range")
        val cap = params["cap"]?.toIntOrNull() ?: return malformed("cap")
        if (cap < 0 || cap > 0xFFFF) return malformed("cap range")
        val iid = params["iid"] ?: return malformed("iid")
        val idBytes = B64URL.decode(iid) ?: return malformed("iid b64")
        if (idBytes.size != LinkProtocol.INVITATION_ID_BYTES) return malformed("iid length")
        val sec = params["sec"] ?: return malformed("sec")
        val secretBytes = B64URL.decode(sec) ?: return malformed("sec b64")
        if (secretBytes.size != LinkProtocol.INVITATION_SECRET_BYTES) return malformed("sec length")
        val fp = params["fp"] ?: return malformed("fp")
        if (fp.length != 64 || fp.any { it !in "0123456789abcdef" }) return malformed("fp format")
        val loc = params["loc"] ?: return malformed("loc")
        if (loc.isEmpty()) return malformed("loc empty")
        val locators = loc.split('|').map { Locator.parse(it) ?: return malformed("locator '$it'") }
        if (locators.size > LinkProtocol.LOCATOR_MAX_CANDIDATES) return malformed("too many locators")
        if (locators.any { !LocatorPolicy.isEmittable(it) }) return malformed("non-emittable locator")
        val ttl = params["ttl"]?.toIntOrNull() ?: return malformed("ttl")
        if (ttl !in 1..86400) return malformed("ttl range")
        return Result.success(
            Payload(maj, min, cap, iid, secretBytes, fp, locators, ttl),
        )
    }

    private fun malformed(why: String): Result<Payload> =
        Result.failure(LinkError.MalformedQr.let { IllegalArgumentException("malformed QR: $why") })

    private val ALLOWED_KEYS = setOf("pv", "maj", "min", "cap", "iid", "sec", "fp", "loc", "ttl")
}

/** Strict unpadded base64url codec for invitation id/secret fields. */
object B64URL {
    private val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
    private val decoder = java.util.Base64.getUrlDecoder()

    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun decode(text: String): ByteArray? = try {
        decoder.decode(text)
    } catch (e: IllegalArgumentException) {
        null
    }
}
