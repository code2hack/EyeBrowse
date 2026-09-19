package com.code2hack.eyebrowse.phone.link

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkProperties
import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.transport.Locator
import com.code2hack.eyebrowse.core.link.transport.LocatorPolicy
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Phone-side explicit locator enumeration for the QR invitation (ticket plan §5).
 *
 * Rules implemented here:
 * - NEVER consult SSID/BSSID and NEVER require a "same subnet" check;
 * - prefer addresses of currently connected non-VPN networks exposed by the system;
 * - fall back to interface enumeration (covers hotspot/tether interfaces);
 * - reject loopback/unspecified/multicast and IPv6 link-local (no scope id on the wire);
 * - always the fixed application port [LinkProtocol.LOCAL_PORT].
 */
object PhoneLocatorEnumerator {

    fun enumerate(context: Context): List<Locator> {
        val candidates = linkedSetOf<InetAddress>()
        runCatching { collectFromNetworks(context, candidates) }
        if (candidates.isEmpty()) {
            runCatching { collectFromInterfaces(candidates) }
        }
        val locators = candidates.map { Locator(it, LinkProtocol.LOCAL_PORT) }
            .filter { LocatorPolicy.isEmittable(it) }
        return LocatorPolicy.orderForAttempts(locators)
    }

    @Suppress("DEPRECATION") // allNetworks is the correct non-VPN-aware enumeration for this use
    @SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is a normal-level permission
    private fun collectFromNetworks(context: Context, sink: LinkedHashSet<InetAddress>) {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        for (network in connectivity.allNetworks) {
            val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val linkProperties: LinkProperties = connectivity.getLinkProperties(network) ?: continue
            for (linkAddress in linkProperties.linkAddresses) {
                sink.add(linkAddress.address)
            }
        }
    }

    private fun collectFromInterfaces(sink: LinkedHashSet<InetAddress>) {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return
        for (ni in interfaces.asSequence()) {
            // Bound the fallback: skip obvious virtual/tunnel containers by name heuristics.
            val name = ni.name?.lowercase() ?: continue
            if (name.startsWith("tun") || name.startsWith("tap")) continue
            for (address in ni.inetAddresses.asSequence()) {
                if (address is Inet4Address || isGlobalIpv6(address)) sink.add(address)
            }
        }
    }

    private fun isGlobalIpv6(address: InetAddress): Boolean {
        if (address.address.size != 16) return false
        return !address.isLoopbackAddress && !address.isLinkLocalAddress &&
            !address.isAnyLocalAddress && !address.isMulticastAddress
    }
}
