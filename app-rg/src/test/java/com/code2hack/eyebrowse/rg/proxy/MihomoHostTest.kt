package com.code2hack.eyebrowse.rg.proxy

import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Native Linux qualification complements, and never substitutes for, the required RG/provider test. */
class MihomoHostTest {
    @Test(timeout = 60000) fun coreAcceptsNetworkDnsIncludingScopedIpv6() {
        val binary = System.getenv("EYEBROWSE_MIHOMO_HOST_BINARY")?.let(::File)
        assumeTrue("Pinned host binary must be supplied for native qualification", binary?.canExecute() == true)
        val home = Files.createTempDirectory("mihomo-dns-").toFile()
        try {
            for (name in listOf("cn-domain.mrs", "cn-ip.mrs"))
                File("src/main/assets/mihomo/$name").copyTo(File(home, name))
            for (servers in listOf(listOf("fe80::53%wlan0", "192.0.2.53"), listOf("fe80::53%7"),
                listOf("2001:db8::53"), listOf("192.0.2.53"), emptyList())) {
                val config = ProxyPolicy.configuration(emptyList(), 18789,
                    File(home, "control.sock").absolutePath, servers, regionalDirectory = home.absolutePath)
                val file = File(home, "config.json").apply {
                    writeText(ProxyPolicy.configurationText(config.toString()))
                }
                val log = File(home, "validation.log")
                val process = ProcessBuilder(binary!!.absolutePath, "-t", "-d", home.absolutePath,
                    "-f", file.absolutePath).redirectErrorStream(true).redirectOutput(log).start()
                try {
                    assertTrue("Native DNS configuration validation settled", process.waitFor(10, TimeUnit.SECONDS))
                    assertEquals(log.readText(), 0, process.exitValue())
                } finally { process.destroyForcibly(); process.waitFor(2, TimeUnit.SECONDS) }
            }
        } finally { home.deleteRecursively() }
    }

    @Test(timeout = 90000) fun realCoreRoutesAndSelectsAcrossSourcesWithAllDownRecovery() {
        val binary = System.getenv("EYEBROWSE_MIHOMO_HOST_BINARY")?.let(::File)
        assumeTrue("Pinned host binary must be supplied for native qualification", binary?.canExecute() == true)
        val evidence = System.getenv("EYEBROWSE_MIHOMO_HOST_EVIDENCE")?.let(::File)
        val home = Files.createTempDirectory("mihomo-host-").toFile()
        val journal = JSONArray()
        val healthJournal = JSONArray()
        val cnIp = JSONObject()
        RouteFixture(checkNotNull(javaClass.getResourceAsStream("/proxy-fixture.p12"))).use { fixture ->
            try {
                for (name in listOf("cn-domain.mrs", "cn-ip.mrs")) File("src/main/assets/mihomo/$name").copyTo(File(home, name))
                val port = java.net.ServerSocket(0).use { it.localPort }
                val socket = File(home, "control.sock")
                val healthUrl = "http://health.test:${fixture.http}/health"
                fun config(sources: List<ProxySource>): JSONObject {
                    val nodes = ProxyPolicy.nodes(sources)
                    val config = ProxyPolicy.configuration(nodes, port, socket.absolutePath, listOf("system"), healthUrl)
                        .put("hosts", JSONObject().put("route.test", "127.0.0.1").put("health.test", "127.0.0.1")
                            .put("baidu.com", "127.0.0.1").put("fixture.ts.net", "127.0.0.1"))
                    // Owned test destinations are loopback. This explicit test-only rule exercises the
                    // public-destination path; production's private-address DIRECT rule is tested separately.
                    if (nodes.isNotEmpty()) {
                        val rules = JSONArray().put("DOMAIN,route.test,EGRESS")
                        val original = config.getJSONArray("rules")
                        for (i in 0 until original.length()) rules.put(original.get(i))
                        config.put("rules", rules)
                    }
                    return config
                }
                File(home, "config.json").writeText(ProxyPolicy.configurationText(config(emptyList()).toString()))
                val process = ProcessBuilder(binary!!.absolutePath, "-d", home.absolutePath, "-f", File(home, "config.json").absolutePath)
                    .redirectErrorStream(true).redirectOutput(File(home, "core.log")).start()
                try {
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (!socket.exists() && process.isAlive && System.nanoTime() < deadline) Thread.sleep(25)
                    assertTrue(File(home, "core.log").readText(), process.isAlive && socket.exists())
                    fun api(method: String, path: String, body: JSONObject?, timeout: Int): JSONObject {
                        val args = mutableListOf("curl", "-sS", "--noproxy", "*", "--raw", "--max-time", (timeout / 1000 + 1).toString(),
                            "--unix-socket", socket.absolutePath, "-i", "-X", method)
                        if (body != null) args.addAll(listOf("-H", "Content-Type: application/json", "--data-binary", body.toString()))
                        args += "http://localhost$path"
                        val call = ProcessBuilder(args).start()
                        val bytes = call.inputStream.readBytes()
                        check(call.waitFor(timeout.toLong() + 2000, TimeUnit.MILLISECONDS) && call.exitValue() == 0)
                        return CoreControl.decode(bytes)
                    }
                    assertEquals("v1.19.32", api("GET", "/version", null, 3000).getString("version"))
                    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
                    fun observe(label: String, scheme: String = "http", host: String = "route.test"): String {
                        val destination = if (scheme == "https") fixture.https else fixture.http
                        val connection = URL("$scheme://$host:$destination/observe-$label").openConnection(proxy) as HttpURLConnection
                        if (connection is HttpsURLConnection) connection.sslSocketFactory = fixture.tls.socketFactory
                        connection.connectTimeout = 4000; connection.readTimeout = 4000
                        return try {
                            connection.inputStream.bufferedReader().use { it.readText() }.also {
                                journal.put(JSONObject().put("case", label).put("scheme", scheme).put("destinationPeer", it))
                            }
                        } finally { connection.disconnect() }
                    }
                    fun apply(sources: List<ProxySource>): String {
                        val next = File(home, "next.json").apply { writeText(ProxyPolicy.configurationText(config(sources).toString())) }
                        api("PUT", "/configs", JSONObject().put("path", next.absolutePath), 4000)
                        val count = ProxyPolicy.nodes(sources).size
                        return if (count == 0) "DIRECT" else ProxyPolicy.applyHealth(count, healthUrl, ::api)
                    }
                    fun recordHealth(label: String) {
                        healthJournal.put(JSONObject().put("case", label)
                            .put("provider", api("GET", "/providers/proxies/ENABLED", null, 3000))
                            .put("egress", api("GET", "/proxies/EGRESS", null, 3000)))
                    }
                    fun minimumAndNewRoute(label: String, expectedPeer: String) {
                        assertTrue(fixture.slowHealthy && fixture.fastHealthy)
                        assertTrue(fixture.sources().all { it.enabled })
                        val provider = api("GET", "/providers/proxies/ENABLED", null, 3000).getJSONArray("proxies")
                        val delays = (0 until provider.length()).associate { index ->
                            val node = provider.getJSONObject(index)
                            val health = node.getJSONObject("extra").getJSONObject(healthUrl)
                            assertTrue(health.getBoolean("alive"))
                            val history = health.getJSONArray("history")
                            node.getString("name") to history.getJSONObject(history.length() - 1).getInt("delay")
                        }
                        assertEquals(ProxyPolicy.nodes(fixture.sources()).map { it.getString("name") }.toSet(), delays.keys)
                        assertEquals(2, delays.size)
                        assertTrue(delays.values.all { it > 0 })
                        val selected = api("GET", "/proxies/AUTO", null, 3000).getString("now")
                        assertEquals(delays.values.minOrNull(), delays[selected])
                        assertEquals("AUTO", api("GET", "/proxies/EGRESS", null, 3000).getString("now"))
                        val start = fixture.proxyRequests.size
                        assertEquals(expectedPeer, observe(label))
                        assertTrue("New connection through the measured winner", fixture.proxyRequests.drop(start).any {
                            it.first == expectedPeer && it.second == "127.0.0.1:${fixture.http}"
                        })
                        healthJournal.put(JSONObject().put("case", label).put("provider", provider)
                            .put("bothSourcesEnabledAndHealthy", true).put("selected", selected)
                            .put("minimumMeasuredDelayMs", delays.values.minOrNull()).put("selectedDelayMs", delays[selected])
                            .put("firstConfiguredDelayMs", fixture.slowDelayMs).put("secondConfiguredDelayMs", fixture.fastDelayMs)
                            .put("ordinaryAutoCacheWaitMs", 10_100).put("destinationPeer", expectedPeer)
                            .put("newReceipts", fixture.proxyReceipts(start)))
                    }
                    for ((label, sources) in listOf("no-sources" to emptyList(),
                        "all-off" to fixture.sources().map { ProxySource(it.id, false, it.nodes) },
                        "empty" to listOf(ProxySource("empty", true, emptyList())))) {
                        assertEquals("DIRECT", apply(sources)); assertEquals("127.0.0.1", observe(label))
                    }
                    assertEquals("AUTO", apply(fixture.sources()))
                    recordHealth("both-healthy")
                    Thread.sleep(10_100) // Observe the genuine AUTO cache interval in both delay orderings.
                    minimumAndNewRoute("second-source-fastest", "127.0.0.3")
                    assertEquals("127.0.0.3", observe("https", "https"))
                    val receiptsBefore = fixture.proxyRequests.size
                    assertEquals("127.0.0.3", observe("cn-ip-control-before"))
                    val probe = JSONObject().also { cnIp.put("probe", it) }
                    CnIpProbe.observe(port, probe) { api("GET", "/connections", null, 3000).optJSONArray("connections") ?: JSONArray() }
                    assertEquals("AUTO", api("GET", "/proxies/EGRESS", null, 3000).getString("now"))
                    assertEquals("127.0.0.3", observe("cn-ip-control-after"))
                    val receipts = fixture.proxyRequests.drop(receiptsBefore)
                    assertEquals(2, receipts.count { it.first == "127.0.0.3" && it.second == "127.0.0.1:${fixture.http}" })
                    assertFalse(receipts.any { it.second == "${CnIpProbe.IP}:443" })
                    cnIp.put("healthyControlPeerBeforeAndAfter", "127.0.0.3").put("dummyNodeReceipts", fixture.proxyReceipts(receiptsBefore))
                    recordHealth("after-cn-ip-with-healthy-auto")
                    assertEquals("127.0.0.1", observe("private-direct", host = "127.0.0.1"))
                    for ((host, rule, payload) in listOf(Triple("baidu.com", "RuleSet", "CN-DOMAIN"),
                        Triple("fixture.ts.net", "DomainSuffix", "ts.net"))) {
                        val connection = URL("http://$host:${fixture.http}/hold-$payload").openConnection(proxy) as HttpURLConnection
                        connection.connectTimeout = 4000; connection.readTimeout = 4000
                        val response = CompletableFuture.supplyAsync {
                            try { connection.inputStream.bufferedReader().use { it.readText() } }
                            finally { connection.disconnect() }
                        }
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                        var matched: JSONObject? = null
                        while (matched == null && System.nanoTime() < deadline) {
                            val active = api("GET", "/connections", null, 3000).optJSONArray("connections") ?: JSONArray()
                            matched = (0 until active.length()).map { active.getJSONObject(it) }
                                .firstOrNull { it.getJSONObject("metadata").optString("host") == host }
                            if (matched == null) Thread.sleep(25)
                        }
                        assertEquals(rule, checkNotNull(matched).getString("rule"))
                        assertEquals(payload, matched.getString("rulePayload"))
                        assertEquals("127.0.0.1", response.get(5, TimeUnit.SECONDS))
                        journal.put(JSONObject().put("case", "$payload-direct").put("destinationPeer", "127.0.0.1")
                            .put("matchedRule", rule).put("rulePayload", payload))
                    }
                    val ipv6 = URL("http://[::1]:${fixture.ipv6}/observe-ipv6").openConnection(proxy) as HttpURLConnection
                    ipv6.connectTimeout = 4000; ipv6.readTimeout = 4000
                    try {
                        val peer = ipv6.inputStream.bufferedReader().use { it.readText() }
                        assertEquals(java.net.InetAddress.getByName("::1"), java.net.InetAddress.getByName(peer))
                        journal.put(JSONObject().put("case", "ipv6-loopback-direct").put("destinationPeer", peer))
                    } finally { ipv6.disconnect() }
                    Socket("127.0.0.1", port).use { webSocket ->
                        webSocket.soTimeout = 4000
                        webSocket.outputStream.write("CONNECT route.test:${fixture.http} HTTP/1.1\r\nHost: route.test\r\n\r\n".toByteArray())
                        assertTrue(RouteFixture.header(webSocket.inputStream).contains("200"))
                        webSocket.outputStream.write(("GET /ws-host HTTP/1.1\r\nHost: route.test\r\nUpgrade: websocket\r\n" +
                            "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
                        assertTrue(RouteFixture.header(webSocket.inputStream).contains("101"))
                        assertEquals(0x81, webSocket.inputStream.read())
                        val frame = ByteArray(webSocket.inputStream.read()); java.io.DataInputStream(webSocket.inputStream).readFully(frame)
                        assertEquals("127.0.0.3", frame.toString(Charsets.UTF_8))
                        journal.put(JSONObject().put("case", "websocket").put("destinationPeer", frame.toString(Charsets.UTF_8)))
                    }
                    fixture.slowDelayMs = 15; fixture.fastDelayMs = 180
                    Thread.sleep(1100) // Native health calls deduplicate within one second.
                    assertEquals("AUTO", ProxyPolicy.applyHealth(2, healthUrl, ::api))
                    Thread.sleep(10_100)
                    minimumAndNewRoute("first-source-fastest-after-delay-swap", "127.0.0.2")
                    fixture.slowDelayMs = 180; fixture.fastDelayMs = 15
                    var transactionApplication = 0
                    fixture.checkProfileTransaction(home, { sources ->
                        try {
                            apply(sources)
                            val provider = api("GET", "/providers/proxies/ENABLED", null, 3000).getJSONArray("proxies")
                            assertEquals(ProxyPolicy.nodes(sources).map { it.getString("name") }.toSet(),
                                (0 until provider.length()).map { provider.getJSONObject(it).getString("name") }.toSet())
                            recordHealth("production-transaction-apply-${++transactionApplication}")
                            true
                        } catch (_: Exception) { false }
                    }, ::observe)
                    recordHealth("production-transaction-refusal-kept-prior")
                    fixture.slowHealthy = false; fixture.fastHealthy = false
                    Thread.sleep(1100) // Native health calls deduplicate within one second.
                    assertEquals("DIRECT", ProxyPolicy.applyHealth(2, healthUrl, ::api))
                    recordHealth("all-unhealthy")
                    assertEquals("127.0.0.1", observe("all-unhealthy"))
                    fixture.slowHealthy = true
                    Thread.sleep(1100)
                    assertEquals("AUTO", ProxyPolicy.applyHealth(2, healthUrl, ::api))
                    recordHealth("recovered-first-source")
                    // Native URLTest has a ten-second selection cache; do not replace its latency engine.
                    Thread.sleep(10_100)
                    assertEquals("127.0.0.2", observe("recovered-first-source"))
                } finally {
                    process.destroy(); if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
                    process.waitFor(3, TimeUnit.SECONDS)
                    evidence?.mkdirs()
                    evidence?.let { File(home, "core.log").copyTo(File(it, "core-host.log"), overwrite = true) }
                    evidence?.let { destination ->
                        for (name in listOf("transaction-observations.json", "transaction-profiles.json", "next.json", "config.json"))
                            File(home, name).takeIf { it.exists() }?.copyTo(File(destination, name), overwrite = true)
                    }
                }
            } finally {
                evidence?.let { File(it, "destination-observations.json").writeText(journal.toString(2)) }
                evidence?.let { File(it, "core-health-observations.json").writeText(healthJournal.toString(2)) }
                evidence?.let { File(it, "cn-ip-observations.json").writeText(cnIp.toString(2)) }
                evidence?.let { File(it, "dummy-node-receipts.json").writeText(fixture.proxyReceipts().toString(2)) }
                home.deleteRecursively()
            }
        }
    }
}
