package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.LinkProtocol
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan §10-B: locator validation with no SSID/subnet concept; bounded deterministic ordering. */
class LocatorPolicyTest {

    private fun loc(ip: String, port: Int = LinkProtocol.LOCAL_PORT) = Locator(InetAddress.getByName(ip), port)

    @Test
    fun `emittable validation rejects loopback unspecified multicast and scoped link-local`() {
        assertFalse(LocatorPolicy.isEmittable(loc("127.0.0.1")))
        assertFalse(LocatorPolicy.isEmittable(loc("0.0.0.0")))
        assertFalse(LocatorPolicy.isEmittable(loc("224.0.0.9")))
        assertFalse(LocatorPolicy.isEmittable(loc("::1")))
        assertFalse(LocatorPolicy.isEmittable(loc("ff02::1")))
        assertFalse(LocatorPolicy.isEmittable(loc("fe80::a1"))) // needs scope id: not emitted
        assertTrue(LocatorPolicy.isEmittable(loc("192.168.1.20")))
        assertTrue(LocatorPolicy.isEmittable(loc("10.0.0.5")))
        assertTrue(LocatorPolicy.isEmittable(loc("2001:db8::7")))
        assertTrue(LocatorPolicy.isEmittable(loc("169.254.3.4"))) // v4 link-local: parseable, deprioritized
    }

    @Test
    fun `ordering is deterministic deduplicated and bounded`() {
        val candidates = listOf(
            loc("fe80::5"),
            loc("192.168.1.20"),
            loc("192.168.1.20"), // duplicate
            loc("2001:db8::1"),
            loc("169.254.9.9"),
            loc("192.168.1.21"),
        )
        val ordered = LocatorPolicy.orderForAttempts(candidates)
        // Java canonicalizes IPv6 to its uncompressed form; rank order is what matters here.
        assertEquals(
            listOf(
                "192.168.1.20", "192.168.1.21",
                InetAddress.getByName("2001:db8::1").hostAddress,
                "169.254.9.9",
                InetAddress.getByName("fe80::5").hostAddress,
            ),
            ordered.map { it.address.hostAddress },
        )
    }

    @Test
    fun `candidate count is capped`() {
        val many = (1..40).map { loc("10.1.$it.2") }
        assertEquals(LinkProtocol.LOCATOR_MAX_CANDIDATES, LocatorPolicy.orderForAttempts(many).size)
    }

    @Test
    fun `wire forms roundtrip`() {
        assertEquals("192.168.1.20:39818", Locator.parse("192.168.1.20:39818")!!.toWire())
        val v6 = Locator.parse("[2001:db8::1]:39818")!!
        assertTrue(v6.toWire().startsWith("[") && v6.toWire().endsWith("]:39818"))
        assertEquals(v6, Locator.parse(v6.toWire()))
        assertNull(Locator.parse("[2001:db8::1]")) // missing port
        assertNull(Locator.parse("2001:db8::1:39818")) // ambiguous without brackets
        assertNull(Locator.parse("[fe80::1%25wlan0]:39818")) // scope ids never carried
        assertNull(Locator.parse("::1:39818".let { "::1" + ":39818" })) // v6 requires brackets
    }

    @Test
    fun `no subnet or ssid concept exists in the locator policy surface`() {
        // The public surface of LocatorPolicy/Locator must not expose SSID/BSSID/subnet helpers.
        val methods = LocatorPolicy::class.java.declaredMethods.map { it.name } +
            Locator::class.java.declaredMethods.map { it.name }
        assertFalse(methods.any { it.lowercase().contains("ssid") })
        assertFalse(methods.any { it.lowercase().contains("subnet") })
        assertFalse(methods.any { it.lowercase().contains("bssid") })
    }
}
