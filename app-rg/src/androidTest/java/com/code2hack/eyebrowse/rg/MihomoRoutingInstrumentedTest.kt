package com.code2hack.eyebrowse.rg

import android.os.Build
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import com.code2hack.eyebrowse.rg.proxy.CoreControl
import com.code2hack.eyebrowse.rg.proxy.CnIpProbe
import com.code2hack.eyebrowse.rg.proxy.ProxyPolicy
import com.code2hack.eyebrowse.rg.proxy.ProxySource
import com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime
import com.code2hack.eyebrowse.rg.proxy.RouteFixture
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture

@RunWith(AndroidJUnit4::class)
class MihomoRoutingInstrumentedTest {
    @Test(timeout = 150000) fun actualProviderCoreAndNativeRoutesFallbackAndRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val runtime = RgProxyRuntime.get(context)
        val results = JSONObject().put("model", Build.MODEL).put("androidApi", Build.VERSION.SDK_INT)
            .put("webView", WebView.getCurrentWebViewPackage()?.versionName)
        val cases = JSONArray()
        val health = JSONArray()
        RouteFixture(instrumentation.context.assets.open("proxy-fixture.p12")).use { fixture ->
            runtime.useTestEndpoint("http://health.test:${fixture.http}/health")
            assertTrue(runtime.replaceSources(emptyList()).get(10, TimeUnit.SECONDS))
            try {
                ActivityScenario.launch(LocalBrowserActivity::class.java).use { scenario ->
                    assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))
                    await("core and completed provider configuration") {
                        runtime.status == "Running" && runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort
                    }
                    val api = CoreControl(File(context.filesDir, "mihomo/control.sock").absolutePath)
                    assertEquals("v1.19.32", api.request("GET", "/version").getString("version"))
                    fun recordHealth(label: String) {
                        health.put(JSONObject().put("case", label)
                            .put("provider", api.request("GET", "/providers/proxies/ENABLED"))
                            .put("egress", api.request("GET", "/proxies/EGRESS")))
                    }
                    fun configure(sources: List<ProxySource>) {
                        assertTrue(runtime.replaceSources(sources).get(20, TimeUnit.SECONDS))
                        val nodes = ProxyPolicy.nodes(sources)
                        val config = ProxyPolicy.configuration(nodes, checkNotNull(runtime.proxyPort),
                            File(context.filesDir, "mihomo/control.sock").absolutePath, listOf("system"), runtime.testUrl)
                            .put("hosts", JSONObject().put("route.test", "127.0.0.1").put("health.test", "127.0.0.1")
                                .put("baidu.com", "127.0.0.1").put("fixture.ts.net", "127.0.0.1"))
                        if (nodes.isNotEmpty()) {
                            val rules = JSONArray().put("DOMAIN,route.test,EGRESS")
                            val original = config.getJSONArray("rules")
                            for (i in 0 until original.length()) rules.put(original.get(i))
                            config.put("rules", rules)
                        }
                        // Test-only loopback destination mapping. Production's local DIRECT policy remains
                        // intact; only route.test explicitly exercises the public-destination EGRESS path.
                        val file = File(context.filesDir, "mihomo/instrumentation.json").apply { writeText(config.toString()) }
                        api.request("PUT", "/configs", JSONObject().put("path", file.absolutePath))
                        assertTrue(runtime.checkHealthNow().get(20, TimeUnit.SECONDS))
                    }
                    fun native(label: String, expected: String, scheme: String = "http", host: String = "route.test") {
                        val port = if (scheme == "https") fixture.https else fixture.http
                        val connection = runtime.openConnection(URL("$scheme://$host:$port/observe-$label"))
                        val peer = try { connection.inputStream.bufferedReader().use { it.readText() } }
                            finally { connection.disconnect() }
                        assertEquals(label, expected, peer)
                        cases.put(JSONObject().put("case", label).put("scheme", scheme).put("destinationPeer", peer))
                    }
                    for ((label, sources) in listOf("no-sources" to emptyList(),
                        "all-off" to fixture.sources().map { ProxySource(it.id, false, it.nodes) },
                        "empty" to listOf(ProxySource("empty", true, emptyList())))) {
                        configure(sources); native(label, "127.0.0.1")
                    }
                    configure(fixture.sources())
                    recordHealth("both-healthy")
                    native("second-source-fastest", "127.0.0.3")
                    native("native-https", "127.0.0.3", "https")
                    val receiptsBefore = fixture.proxyRequests.size
                    native("cn-ip-control-before", "127.0.0.3")
                    val probe = JSONObject().also { results.put("cnIp", it) }
                    CnIpProbe.observe(checkNotNull(runtime.proxyPort), probe) {
                        api.request("GET", "/connections").optJSONArray("connections") ?: JSONArray()
                    }
                    assertEquals("AUTO", api.request("GET", "/proxies/EGRESS").getString("now"))
                    native("cn-ip-control-after", "127.0.0.3")
                    val receipts = fixture.proxyRequests.drop(receiptsBefore)
                    assertEquals(2, receipts.count { it.first == "127.0.0.3" && it.second == "127.0.0.1:${fixture.http}" })
                    assertFalse(receipts.any { it.second == "${CnIpProbe.IP}:443" })
                    results.getJSONObject("cnIp").put("healthyControlPeerBeforeAndAfter", "127.0.0.3")
                        .put("dummyNodeReceipts", fixture.proxyReceipts(receiptsBefore))
                    recordHealth("after-cn-ip-with-healthy-auto")
                    native("private-ip-direct", "127.0.0.1", host = "127.0.0.1")
                    for ((host, rule, payload) in listOf(Triple("baidu.com", "RuleSet", "CN-DOMAIN"),
                        Triple("fixture.ts.net", "DomainSuffix", "ts.net"))) {
                        val connection = runtime.openConnection(URL("http://$host:${fixture.http}/hold-$payload"))
                        val response = CompletableFuture.supplyAsync {
                            try { connection.inputStream.bufferedReader().use { it.readText() } }
                            finally { connection.disconnect() }
                        }
                        var matched: JSONObject? = null
                        await("live matched rule for $host") {
                            val active = api.request("GET", "/connections").optJSONArray("connections") ?: JSONArray()
                            matched = (0 until active.length()).map { active.getJSONObject(it) }
                                .firstOrNull { it.getJSONObject("metadata").optString("host") == host }
                            matched != null
                        }
                        assertEquals(rule, checkNotNull(matched).getString("rule"))
                        assertEquals(payload, matched!!.getString("rulePayload"))
                        assertEquals("127.0.0.1", response.get(5, TimeUnit.SECONDS))
                        cases.put(JSONObject().put("case", "$payload-direct").put("destinationPeer", "127.0.0.1")
                            .put("matchedRule", rule).put("rulePayload", payload))
                    }
                    val ipv6 = runtime.openConnection(URL("http://[::1]:${fixture.ipv6}/observe-ipv6"))
                    try {
                        val peer = ipv6.inputStream.bufferedReader().use { it.readText() }
                        assertEquals(java.net.InetAddress.getByName("::1"), java.net.InetAddress.getByName(peer))
                        cases.put(JSONObject().put("case", "ipv6-loopback-direct").put("destinationPeer", peer))
                    } finally { ipv6.disconnect() }
                    for (scheme in listOf("http", "https")) {
                        val port = if (scheme == "https") fixture.https else fixture.http
                        scenario.onActivity { it.tabs.current.session.open("$scheme://route.test:$port/page") }
                        await("WebView $scheme and ${if (scheme == "https") "wss" else "ws"} route") {
                            js(scenario, "document.body.dataset.route") == "\"127.0.0.3\"" &&
                                js(scenario, "document.body.dataset.ws") == "\"127.0.0.3\""
                        }
                        cases.put(JSONObject().put("case", "webview-$scheme-and-websocket").put("destinationPeer", "127.0.0.3"))
                    }
                    assertEquals("true", js(scenario, "document.querySelector('#note').value='retained-form';true"))
                    fixture.slowHealthy = false; fixture.fastHealthy = false
                    Thread.sleep(1100) // Genuine core health endpoint's one-second dedup window.
                    assertTrue(runtime.checkHealthNow().get(20, TimeUnit.SECONDS))
                    assertEquals("DIRECT", api.request("GET", "/proxies/EGRESS").getString("now"))
                    recordHealth("all-unhealthy")
                    native("all-unhealthy", "127.0.0.1")
                    js(scenario, "fetch('/observe-down').then(r=>r.text()).then(t=>document.body.dataset.down=t);true")
                    await("new WebView request uses all-down DIRECT") { js(scenario, "document.body.dataset.down") == "\"127.0.0.1\"" }
                    assertEquals("\"retained-form\"", js(scenario, "document.querySelector('#note').value"))
                    fixture.slowHealthy = true
                    Thread.sleep(1100)
                    assertTrue(runtime.checkHealthNow().get(20, TimeUnit.SECONDS))
                    recordHealth("recovered-first-source")
                    Thread.sleep(10_100) // Native AUTO's ten-second cache, not an app latency engine.
                    native("recovered-first-source", "127.0.0.2")
                    js(scenario, "fetch('/observe-recovered').then(r=>r.text()).then(t=>document.body.dataset.recovered=t);true")
                    await("new WebView request uses recovered AUTO") { js(scenario, "document.body.dataset.recovered") == "\"127.0.0.2\"" }
                    assertEquals("\"retained-form\"", js(scenario, "document.querySelector('#note').value"))
                    cases.put(JSONObject().put("case", "selection-keeps-live-form").put("result", "retained"))
                    configure(emptyList())
                    scenario.recreate()
                    await("recreated core/provider") { runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort && runtime.status == "Running" }
                    assertEquals("v1.19.32", api.request("GET", "/version").getString("version"))
                    cases.put(JSONObject().put("case", "recreation").put("result", "core/provider ready"))
                }
                await("foreground runtime stopped and WebView override cleared") { runtime.proxyPort == null && runtime.webViewPort == null }
                results.put("result", "PASS_EXERCISED_CASES")
            } finally {
                runtime.replaceSources(emptyList()).get(20, TimeUnit.SECONDS)
                runtime.useTestEndpoint(ProxyPolicy.TEST_URL)
                results.put("cases", cases).put("coreHealth", health).put("destinationObservations", JSONArray(fixture.observations.map {
                    JSONObject().put("path", it.first).put("peer", it.second)
                }))
                results.put("dummyNodeReceipts", fixture.proxyReceipts())
                results.put("limits", "Owned loopback fixture with explicit test route and dummy .test-only TLS trust. Public China-IP probe uses system TLS, live core rule and dummy-node receipts, without owned public server logs. Real external tailnet proof remains separate. No Phone checks.")
                File(context.filesDir, "mihomo-test-results.json").writeText(results.toString(2))
            }
        }
    }
    private fun js(scenario: ActivityScenario<LocalBrowserActivity>, expression: String): String? {
        val value = AtomicReference<String>()
        val ready = CountDownLatch(1)
        scenario.onActivity { it.tabs.current.session.page?.evaluateJavascript(expression) { result -> value.set(result); ready.countDown() } }
        assertTrue("JavaScript callback", ready.await(3, TimeUnit.SECONDS))
        return value.get()
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
        while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(50) }
        fail(label)
    }
}
