package com.code2hack.eyebrowse.rg.proxy

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import org.json.JSONArray
import org.json.JSONObject

/** Public, non-secret DNS query. Rule/TLS/node receipts form a combined oracle, without server logs. */
internal object CnIpProbe {
    const val IP = "223.5.5.5"
    private const val QUERY = "AAABAAABAAAAAAAAB2V4YW1wbGUDY29tAAABAAE"

    fun observe(proxyPort: Int, result: JSONObject, connections: () -> JSONArray): JSONObject {
        result.put("case", "public-cn-ip-direct").put("destination", "https://$IP/dns-query")
            .put("limits", "Public service, no owned destination/server logs; combine with live DIRECT rule and healthy dummy-node receipts.")
        Socket().use { tunnel ->
            tunnel.connect(InetSocketAddress("127.0.0.1", proxyPort), 5000)
            tunnel.soTimeout = 5000
            result.put("clientSourcePort", tunnel.localPort)
            tunnel.outputStream.write("CONNECT $IP:443 HTTP/1.1\r\nHost: $IP:443\r\n\r\n".toByteArray())
            check(RouteFixture.header(tunnel.inputStream).substringBefore("\r\n").split(' ')[1] == "200")
            // Keep this exact request live before TLS/HTTP so a fast response cannot evade rule capture.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            var matched: JSONObject? = null
            while (matched == null && System.nanoTime() < deadline) {
                val active = connections()
                matched = (0 until active.length()).map { active.getJSONObject(it) }
                    .firstOrNull { it.getJSONObject("metadata").optString("destinationIP") == IP &&
                        it.getJSONObject("metadata").optString("destinationPort") == "443" &&
                        it.getJSONObject("metadata").optString("sourcePort") == tunnel.localPort.toString() }
                if (matched == null) Thread.sleep(25)
            }
            val route = checkNotNull(matched) { "No live China-IP connection" }
            result.put("matchedConnection", route)
            check(route.getString("rule") == "RuleSet" && route.getString("rulePayload") == "CN-IP") { route.toString() }
            val chains = route.getJSONArray("chains")
            check(chains.length() == 1 && chains.getString(0) == "DIRECT") { route.toString() }
            ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(tunnel, IP, 443, true) as SSLSocket).use { tls ->
                tls.soTimeout = 5000
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.startHandshake() // System trust and literal-IP certificate verification; no dummy CA override.
                result.put("tlsProtocol", tls.session.protocol).put("tlsCipher", tls.session.cipherSuite)
                    .put("peerCertificateSha256", sha256(tls.session.peerCertificates.first().encoded))
                    .put("tlsVerification", "system trust plus HTTPS literal-IP endpoint identification")
                tls.outputStream.write(("GET /dns-query?dns=$QUERY HTTP/1.0\r\nHost: $IP\r\n" +
                    "Accept: application/dns-message\r\nConnection: close\r\n\r\n").toByteArray())
                tls.outputStream.flush()
                val header = RouteFixture.header(tls.inputStream)
                result.put("httpStatus", header.substringBefore("\r\n").split(' ')[1].toInt())
                check(header.substringBefore("\r\n").split(' ')[1] == "200") { header }
                check(header.lineSequence().any { it.equals("Content-Type: application/dns-message", true) }) { header }
                check(header.lineSequence().none { it.startsWith("Transfer-Encoding:", true) }) { "Unexpected HTTP/1.0 framing: $header" }
                val response = ByteArrayOutputStream()
                while (true) {
                    val next = tls.inputStream.read()
                    if (next < 0) break
                    response.write(next)
                    check(response.size() <= 4096) { "DNS response exceeded 4096 bytes" }
                }
                val body = response.toByteArray()
                result.put("responseBytes", body.size).put("dnsResponseBase64", Base64.getEncoder().encodeToString(body))
                    .put("responseSha256", sha256(body))
                check(body.size >= 12) { "DNS response size ${body.size}" }
                val query = Base64.getUrlDecoder().decode(QUERY)
                fun word(offset: Int) = ((body[offset].toInt() and 255) shl 8) or (body[offset + 1].toInt() and 255)
                check(body.take(2) == query.take(2) && (word(2) and 0xf80f) == 0x8000 && word(4) == 1 && word(6) > 0)
                check(body.size > query.size && body.copyOfRange(12, query.size).contentEquals(query.copyOfRange(12, query.size)))
                return result.put("dnsAnswerCount", word(6))
            }
        }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
