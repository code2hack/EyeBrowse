package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.LinkProtocol
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Explicit local locator candidates (ticket plan §5 "Local listener / locators").
 * A locator is reachability metadata only — identity always comes from the pinned Phone key.
 * There is deliberately NO SSID/BSSID/"same subnet" concept anywhere in this module.
 */
data class Locator(val address: InetAddress, val port: Int) {

    /** Canonical wire form: `ip:port`, IPv6 in brackets. */
    fun toWire(): String {
        val host = address.hostAddress ?: throw IllegalStateException("unresolved address")
        return if (address is Inet4Address) "$host:$port" else "[$host]:$port"
    }

    companion object {
        /**
         * Parses a strict IP-literal `ip:port` / `[ipv6]:port`. Never performs DNS: strings that
         * are not lexical IP literals are rejected before any resolver could be consulted.
         */
        fun parse(wire: String): Locator? {
            val s = wire.trim()
            if (s.isEmpty() || s.length > 64) return null
            if (s.startsWith("[")) {
                val close = s.indexOf(']')
                if (close < 0) return null
                val rest = s.substring(close + 1)
                if (!rest.startsWith(":")) return null
                val port = rest.substring(1).toIntOrNull() ?: return null
                if (port !in 1..65535) return null
                val addr = parseIpv6Literal(s.substring(1, close)) ?: return null
                return Locator(addr, port)
            }
            // IPv4 form: exactly one colon separator.
            val colon = s.lastIndexOf(':')
            if (colon <= 0 || s.indexOf(':') != colon) return null
            val port = s.substring(colon + 1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            val addr = parseIpv4Literal(s.substring(0, colon)) ?: return null
            return Locator(addr, port)
        }

        fun parseOrNull(wire: String): Locator? = runCatching { parse(wire) }.getOrNull()
    }
}

/** Locator validation/ordering primitives (plan §5; no network policy beyond literal sanity). */
object LocatorPolicy {

    /**
     * Validates a candidate for *production QR emission / acceptance*:
     * rejects loopback, unspecified, multicast, and IPv6 link-local (needs an unavailable scope
     * identifier). Port must be 1..65535.
     */
    fun isEmittable(locator: Locator): Boolean {
        val a = locator.address
        if (a.isLoopbackAddress || a.isAnyLocalAddress || a.isMulticastAddress) return false
        if (a is Inet4Address) {
            // IPv4 link-local (169.254/16) needs no scope id; it parses but is deprioritized.
            return true
        }
        return !isIpv6LinkLocal(a)
    }

    /** Deterministic bounded ordering: dedupe, cap at [LinkProtocol.LOCATOR_MAX_CANDIDATES],
     *  IPv4 global first, IPv6 global next, link-local last. */
    fun orderForAttempts(candidates: List<Locator>): List<Locator> =
        candidates.asSequence()
            .filter { it.port in 1..65535 }
            .distinctBy { it.address.hostAddress to it.port }
            .sortedWith(
                compareBy(
                    { rank(it) },
                    { it.address.hostAddress ?: "" },
                    { it.port },
                ),
            )
            .take(LinkProtocol.LOCATOR_MAX_CANDIDATES)
            .toList()

    private fun rank(locator: Locator): Int {
        val a = locator.address
        return when {
            a is Inet4Address && !a.isLinkLocalAddress -> 0
            a is Inet4Address -> 2
            !isIpv6LinkLocal(a) -> 1
            else -> 3
        }
    }

    private fun isIpv6LinkLocal(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != 16) return false
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return (first and 0xFF) == 0xFE && (second and 0xC0) == 0x80 // fe80::/10
    }
}

/** Strict IPv4 dotted-quad parser (no resolver involvement). */
internal fun parseIpv4Literal(s: String): Inet4Address? {
    if (s.isEmpty() || s.length > 15) return null
    val parts = s.split('.')
    if (parts.size != 4) return null
    val bytes = ByteArray(4)
    for (i in 0..3) {
        val p = parts[i]
        if (p.isEmpty() || p.length > 3 || (p.length > 1 && p[0] == '0')) return null
        val v = p.toIntOrNull() ?: return null
        if (v !in 0..255) return null
        bytes[i] = v.toByte()
    }
    return Inet4Address.getByAddress(bytes) as Inet4Address
}

/**
 * Strict IPv6 literal parser for the textual forms Android/iOS emit. Delegates canonical parsing
 * to [InetAddress.getByName] ONLY after a lexical pre-check guarantees the string is an IPv6
 * literal (contains ':' and only hex/':'/'.'), so no DNS lookup can occur.
 */
internal fun parseIpv6Literal(s: String): InetAddress? {
    if (s.isEmpty() || s.length > 45) return null
    if (!s.contains(':')) return null
    val lexical = s.all { it in "0123456789abcdefABCDEF:." }
    if (!lexical) return null
    return try {
        InetAddress.getByName(s)
    } catch (e: UnknownHostException) {
        null
    }
}
