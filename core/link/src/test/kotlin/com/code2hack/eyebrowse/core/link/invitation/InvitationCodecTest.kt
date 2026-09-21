package com.code2hack.eyebrowse.core.link.invitation

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.transport.Locator
import java.net.InetAddress
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan §10-B: QR/invitation codec validation and bounds. */
class InvitationCodecTest {

    private fun bytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }
    private val fp = "ab".repeat(32)
    private fun anId(): String = B64URL.encode(bytes(LinkProtocol.INVITATION_ID_BYTES))
    private val locs = listOf(Locator(InetAddress.getByName("192.168.1.20"), LinkProtocol.LOCAL_PORT))

    @Test
    fun `encode and parse roundtrip preserves every field`() {
        val secret = bytes(32)
        val id = anId()
        val payload = InvitationCodec.encode(
            invitationId = id,
            invitationSecret = secret,
            phoneSpkiSha256Hex = fp,
            locators = locs,
            ttlSeconds = 180,
        )
        val parsed = InvitationCodec.parse(payload).getOrThrow()
        assertEquals(1, parsed.protocolMajor)
        assertEquals(0, parsed.protocolMinor)
        assertEquals(id, parsed.invitationId)
        assertTrue(parsed.invitationSecret.contentEquals(secret))
        assertEquals(fp, parsed.phoneSpkiSha256Hex)
        assertEquals(1, parsed.locators.size)
        assertEquals("192.168.1.20", parsed.locators[0].address.hostAddress)
        assertEquals(39818, parsed.locators[0].port)
        assertEquals(180, parsed.ttlSeconds)
        assertTrue(parsed.hasRequiredCapabilities())
    }

    @Test
    fun `payload carries no wifi or credential fields`() {
        val payload = InvitationCodec.encode(
            invitationId = anId(), invitationSecret = bytes(32), phoneSpkiSha256Hex = fp, locators = locs,
        )
        assertFalse(payload.contains("ssid"))
        assertFalse(payload.contains("pass"))
        assertTrue(payload.startsWith("eyebrowse-pair:v1?"))
        assertTrue(payload.length <= LinkProtocol.QR_PAYLOAD_MAX_CHARS)
    }

    @Test
    fun `unknown query keys are rejected by the strict v1 grammar`() {
        val payload = InvitationCodec.encode(
            invitationId = anId(), invitationSecret = bytes(32), phoneSpkiSha256Hex = fp, locators = locs,
        ) + "&rogue=1"
        assertTrue(InvitationCodec.parse(payload).isFailure)
    }

    @Test
    fun `unknown capability bits are permitted and ignored`() {
        val payload = InvitationCodec.encode(
            capabilityBits = 0b100011, // known bits + unknown bits
            invitationId = anId(), invitationSecret = bytes(32), phoneSpkiSha256Hex = fp, locators = locs,
        )
        val parsed = InvitationCodec.parse(payload).getOrThrow()
        assertTrue(parsed.hasRequiredCapabilities())
        assertEquals(0b100011, parsed.capabilityBits)
    }

    @Test
    fun `missing required capability bit fails the requirement check`() {
        val payload = InvitationCodec.encode(
            capabilityBits = InvitationCodec.CAP_BIT_PAIRING_V1,
            invitationId = anId(), invitationSecret = bytes(32), phoneSpkiSha256Hex = fp, locators = locs,
        )
        assertFalse(InvitationCodec.parse(payload).getOrThrow().hasRequiredCapabilities())
    }

    @Test
    fun `malformed inputs produce MalformedQr failures`() {
        val good = InvitationCodec.encode(
            invitationId = anId(), invitationSecret = bytes(32), phoneSpkiSha256Hex = fp, locators = locs,
        )
        // Wrong scheme / version
        assertTrue(InvitationCodec.parse("eyebrowse-pair:v2?" + good.substringAfter('?')).isFailure)
        assertTrue(InvitationCodec.parse("https://example.com").isFailure)
        // Wrong secret length (crafted wire payload with a 16-byte secret)
        val shortSec = B64URL.encode(bytes(16))
        val shortPayload = good.replace(Regex("sec=[^&]+"), "sec=$shortSec")
        assertTrue(InvitationCodec.parse(shortPayload).isFailure)
        // Broken base64url
        val brokenSec = good.replace(Regex("sec=[^&]+"), "sec=!!!not-base64!!!")
        assertTrue(InvitationCodec.parse(brokenSec).isFailure)
        // Broken fingerprint
        val brokenFp = good.replace(Regex("fp=[^&]+"), "fp=XYZ")
        assertTrue(InvitationCodec.parse(brokenFp).isFailure)
        // Oversize payload
        val longLoc = (1..LinkProtocol.LOCATOR_MAX_CANDIDATES).joinToString("|") {
            "192.168.${it}.1:39818"
        }
        val big = good.replace(Regex("loc=[^&]+"), "loc=" + "9".repeat(1100))
        assertTrue(InvitationCodec.parse(big).isFailure)
        // Duplicate key
        assertTrue(InvitationCodec.parse("$good&ttl=180").isFailure)
        // Empty query
        assertTrue(InvitationCodec.parse("eyebrowse-pair:v1?").isFailure)
    }

    @Test
    fun `loopback unspecified multicast and scoped link-local locators are rejected`() {
        val loopback = listOf(Locator(InetAddress.getLoopbackAddress(), 39818))
        val unspecified = listOf(Locator(InetAddress.getByName("0.0.0.0"), 39818))
        val multicast = listOf(Locator(InetAddress.getByName("224.0.0.1"), 39818))
        val linkLocalV6 = listOf(Locator(InetAddress.getByName("fe80::1"), 39818))
        for (bad in listOf(loopback, unspecified, multicast, linkLocalV6)) {
            assertTrue(
                LocatorPolicyGuard.describe(bad),
                runCatching {
                    InvitationCodec.encode(
                        invitationId = anId(), invitationSecret = bytes(32), phoneSpkiSha256Hex = fp, locators = bad,
                    )
                }.isFailure,
            )
        }
    }

    @Test
    fun `ipv6 locator roundtrips through bracket form`() {
        val v6 = Locator(InetAddress.getByName("2001:db8::1"), 39818)
        val payload = InvitationCodec.encode(
            invitationId = anId(), invitationSecret = bytes(32), phoneSpkiSha256Hex = fp, locators = listOf(v6),
        )
        val parsed = InvitationCodec.parse(payload).getOrThrow()
        val wire = parsed.locators[0].toWire()
        assertTrue("bracket form with port: $wire", wire.startsWith("[") && wire.endsWith("]:39818"))
        assertEquals(parsed.locators[0], Locator.parse(wire))
    }

    @Test
    fun `port bounds enforced`() {
        assertTrue(Locator.parse("192.168.1.1:0") == null)
        assertTrue(Locator.parse("192.168.1.1:65536") == null)
        assertNotNull(Locator.parse("192.168.1.1:65535"))
        assertNull(Locator.parse("192.168.1.1"))
        assertNull(Locator.parse("host.example:39818")) // never a DNS name
        assertNull(Locator.parse("300.1.1.1:39818"))
    }

    @Test
    fun `b64url codec is strict and unpadded`() {
        val data = bytes(32)
        val encoded = B64URL.encode(data)
        assertFalse(encoded.contains('='))
        assertTrue(encoded.none { it == '+' || it == '/' })
        assertTrue(B64URL.decode(encoded)!!.contentEquals(data))
        assertNull(B64URL.decode("!!!"))
    }

    private object LocatorPolicyGuard {
        fun describe(locators: List<Locator>) = "expected rejection for ${locators[0]}"
    }
}
