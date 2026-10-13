package com.code2hack.eyebrowse.rg

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
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
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture

@RunWith(AndroidJUnit4::class)
class MihomoRoutingInstrumentedTest {
    @Test(timeout = 150000) fun actualProviderCoreAndNativeRoutesFallbackAndRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("LocalBrowserActivity", Context.MODE_PRIVATE)
        val previousRecovery = preferences.getString("local.tabs", null)
        val tailnetOrigin = URL(checkNotNull(InstrumentationRegistry.getArguments().getString("tailnetOrigin")) {
            "Supply the exclusively reserved tailnet fixture origin"
        })
        require(tailnetOrigin.protocol == "http" && tailnetOrigin.userInfo == null && tailnetOrigin.port > 0)
        val runtime = RgProxyRuntime.get(context)
        val results = JSONObject().put("model", Build.MODEL).put("androidApi", Build.VERSION.SDK_INT)
            .put("webView", WebView.getCurrentWebViewPackage()?.versionName)
        val cases = JSONArray()
        val health = JSONArray()
        val tailnet = JSONArray().also { results.put("externalTailnet", it) }
        val documents = JSONArray().also { results.put("browserDocuments", it) }
        // Kotlin use always attempts restoration after the scenario and other resources close;
        // a restore failure is suppressed onto an existing body/teardown/report failure.
        restoreRecoveryOnClose(preferences, previousRecovery).use {
            RouteFixture(instrumentation.context.assets.open("proxy-fixture.p12")).use { fixture ->
                Closeable {
                    results.put("cases", cases).put("coreHealth", health).put("destinationObservations", JSONArray(fixture.observations.map {
                        JSONObject().put("path", it.first).put("peer", it.second)
                    }))
                    results.put("dummyNodeReceipts", fixture.proxyReceipts())
                    results.put("limits", "Owned loopback fixture with explicit test route and dummy .test-only TLS trust. Public China-IP probe uses system TLS, live core rule and dummy-node receipts, without owned public server logs. External tailnet has nonce-correlated owned receipts, live core rules and dummy controls; source IP alone cannot distinguish RG-local dummy egress. No Phone checks.")
                    File(context.filesDir, "mihomo-test-results.json").writeText(results.toString(2))
                }.use {
                    Closeable {
                        Closeable { runtime.useTestEndpoint(ProxyPolicy.TEST_URL) }.use {
                            runtime.replaceSources(emptyList()).get(20, TimeUnit.SECONDS)
                        }
                    }.use {
                        runtime.useTestEndpoint("http://health.test:${fixture.http}/health")
                        assertTrue(runtime.replaceSources(emptyList()).get(10, TimeUnit.SECONDS))
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
                                val file = File(context.filesDir, "mihomo/instrumentation.json").apply {
                                    writeText(ProxyPolicy.configurationText(config.toString()))
                                }
                                api.request("PUT", "/configs", JSONObject().put("path", file.absolutePath))
                                assertTrue(runtime.checkHealthNow().get(20, TimeUnit.SECONDS))
                            }
                            fun native(label: String, expected: String, scheme: String = "http", host: String = "route.test") {
                                val port = if (scheme == "https") fixture.https else fixture.http
                                val observation = JSONObject().put("case", label).put("scheme", scheme)
                                cases.put(observation)
                                val connection = runtime.openConnection(URL("$scheme://$host:$port/observe-$label"))
                                val peer = try {
                                    val code = connection.responseCode
                                    observation.put("httpStatus", code)
                                    if (code != 200) {
                                        observation.put("errorBody", connection.errorStream?.bufferedReader()?.use { it.readText().take(1024) })
                                            .put("fixturePort", port).put("nativePort", runtime.proxyPort)
                                            .put("webViewPort", runtime.webViewPort).put("runtimeStatus", runtime.status)
                                        // This is the owned origin, not a personal page or production subscription.
                                        val direct = URL("http://127.0.0.1:${fixture.http}/observe-failure-control")
                                            .openConnection(java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
                                        try {
                                            direct.connectTimeout = 3000; direct.readTimeout = 3000
                                            observation.put("directControlStatus", direct.responseCode)
                                            observation.put("directControlPeer", direct.inputStream.bufferedReader().use { it.readText() })
                                        } catch (diagnostic: Exception) {
                                            observation.put("directControlErrorType", diagnostic.javaClass.name)
                                        } finally { direct.disconnect() }
                                        observation.put("coreVersion", runCatching { api.request("GET", "/version") }.getOrNull())
                                        // Keep the failed assertion. A second owned request is diagnostic only;
                                        // retain only log lines naming this loopback fixture destination.
                                        try {
                                            api.request("PATCH", "/configs", JSONObject().put("log-level", "debug"))
                                            android.net.LocalSocket().use { logs ->
                                                logs.connect(android.net.LocalSocketAddress(File(context.filesDir, "mihomo/control.sock").absolutePath,
                                                    android.net.LocalSocketAddress.Namespace.FILESYSTEM))
                                                logs.soTimeout = 1000
                                                logs.outputStream.write("GET /logs?level=debug HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray())
                                                logs.outputStream.flush()
                                                Thread.sleep(50)
                                                val probe = runtime.openConnection(URL("http://127.0.0.1:${fixture.http}/observe-failure-core-control"))
                                                try { observation.put("secondCoreControlStatus", probe.responseCode) }
                                                finally { probe.disconnect() }
                                                val recorded = java.io.ByteArrayOutputStream()
                                                val buffer = ByteArray(4096)
                                                try {
                                                    while (recorded.size() < 65536) {
                                                        val size = logs.inputStream.read(buffer)
                                                        if (size < 0) break
                                                        recorded.write(buffer, 0, size)
                                                    }
                                                } catch (_: java.io.IOException) { /* Bounded stream observation. */ }
                                                observation.put("ownedDestinationCoreLogs", JSONArray(recorded.toString("UTF-8").lineSequence()
                                                    .filter { it.contains("127.0.0.1:${fixture.http}") }.map { it.take(2048) }.toList()))
                                            }
                                        } catch (diagnostic: Exception) {
                                            observation.put("coreLogDiagnosticErrorType", diagnostic.javaClass.name)
                                        } finally { runCatching { api.request("PATCH", "/configs", JSONObject().put("log-level", "silent")) } }
                                    }
                                    assertEquals(label, 200, code)
                                    connection.inputStream.bufferedReader().use { it.readText() }
                                }
                                    finally { connection.disconnect() }
                                observation.put("destinationPeer", peer)
                                assertEquals(label, expected, peer)
                            }
                            fun browser(url: String, expected: String) {
                                val observation = JSONObject().put("url", url).put("expectedPeer", expected)
                                documents.put(observation)
                                scenario.onActivity { observation.put("accepted", it.tabs.current.session.open(url).accepted()) }
                                try {
                                    await("fresh WebView document and WebSocket route") {
                                        val page = runCatching { JSONObject(checkNotNull(js(scenario,
                                            "({url:location.href,ready:document.readyState,route:document.body?.dataset.route,ws:document.body?.dataset.ws})"))) }.getOrNull()
                                        observation.put("observed", page)
                                        page?.optString("url") == url && page.optString("ready") == "complete" &&
                                            page.optString("route") == expected && page.optString("ws") == expected
                                    }
                                } catch (failure: AssertionError) {
                                    observation.put("nativePort", runtime.proxyPort).put("webViewPort", runtime.webViewPort)
                                        .put("runtimeStatus", runtime.status)
                                    scenario.onActivity {
                                        observation.put("phase", it.tabs.current.session.state.phase)
                                            .put("pageError", it.tabs.current.session.state.error)
                                    }
                                    observation.put("core", runCatching { api.request("GET", "/version") }.getOrNull())
                                    throw failure
                                }
                            }
                            native("startup-no-source-direct", "127.0.0.1", host = "127.0.0.1")
                            browser("http://127.0.0.1:${fixture.http}/page?nonce=${UUID.randomUUID()}", "127.0.0.1")
                            cases.put(JSONObject().put("case", "startup-webview-direct").put("destinationPeer", "127.0.0.1"))
                            for ((label, sources) in listOf("no-sources" to emptyList(),
                                "all-off" to fixture.sources().map { ProxySource(it.id, false, it.nodes) },
                                "empty" to listOf(ProxySource("empty", true, emptyList())))) {
                                configure(sources); native(label, "127.0.0.1")
                                browser("http://route.test:${fixture.http}/page?nonce=${UUID.randomUUID()}", "127.0.0.1")
                                cases.put(JSONObject().put("case", "webview-$label").put("destinationPeer", "127.0.0.1"))
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
                            for (mode in listOf("native", "webview")) {
                                val nonce = UUID.randomUUID().toString()
                                val url = URL(tailnetOrigin, "/route-probe/$nonce")
                                val observation = JSONObject().put("client", mode).put("nonce", nonce).put("url", url.toString())
                                tailnet.put(observation)
                                val receiptsBeforeTailnet = fixture.proxyRequests.size
                                native("tailnet-$mode-control-before", "127.0.0.3")
                                val connection = if (mode == "native") runtime.openConnection(url) else null
                                val response = connection?.let {
                                    CompletableFuture.supplyAsync {
                                        try { it.inputStream.bufferedReader().use { reader -> reader.readText() } }
                                        finally { it.disconnect() }
                                    }
                                }
                                if (mode == "webview") scenario.onActivity { it.tabs.current.session.open(url.toString()) }
                                var matched: JSONObject? = null
                                await("live external tailnet $mode DIRECT rule") {
                                    val active = api.request("GET", "/connections").optJSONArray("connections") ?: JSONArray()
                                    matched = (0 until active.length()).map { active.getJSONObject(it) }.firstOrNull {
                                        val metadata = it.getJSONObject("metadata")
                                        metadata.optString("destinationIP") == url.host && metadata.optString("destinationPort") == url.port.toString()
                                    }
                                    matched != null
                                }
                                val route = checkNotNull(matched).also { observation.put("matchedConnection", it) }
                                assertEquals("IPCIDR", route.getString("rule"))
                                assertEquals("100.64.0.0/10", route.getString("rulePayload"))
                                assertEquals("[\"DIRECT\"]", route.getJSONArray("chains").toString())
                                var receipt: JSONObject? = response?.get(5, TimeUnit.SECONDS)?.let(::JSONObject)
                                if (mode == "webview") await("external tailnet browser destination receipt") {
                                    receipt = runCatching { JSONObject(JSONArray("[${js(scenario, "document.body.innerText")}]").getString(0)) }.getOrNull()
                                    receipt?.optString("nonce") == nonce
                                }
                                observation.put("destinationReceipt", checkNotNull(receipt))
                                assertEquals(nonce, receipt!!.getString("nonce"))
                                assertEquals("GET", receipt!!.getString("method"))
                                assertEquals(url.path, receipt!!.getString("path"))
                                native("tailnet-$mode-control-after", "127.0.0.3")
                                val receipts = fixture.proxyRequests.drop(receiptsBeforeTailnet)
                                assertEquals(2, receipts.count { it.first == "127.0.0.3" && it.second == "127.0.0.1:${fixture.http}" })
                                assertFalse(receipts.any { it.second == "${url.host}:${url.port}" })
                                assertEquals("AUTO", api.request("GET", "/proxies/EGRESS").getString("now"))
                                observation.put("dummyNodeReceipts", fixture.proxyReceipts(receiptsBeforeTailnet))
                                    .put("limits", "Direct and dummy-proxied traffic on this same RG can share a tailnet source IP; peer IP alone is not the route oracle.")
                            }
                            var formPageUrl = ""
                            for (scheme in listOf("http", "https")) {
                                val port = if (scheme == "https") fixture.https else fixture.http
                                val url = "$scheme://route.test:$port/page?nonce=${UUID.randomUUID()}"
                                browser(url, "127.0.0.3")
                                formPageUrl = url
                                cases.put(JSONObject().put("case", "webview-$scheme-and-websocket").put("url", url)
                                    .put("destinationPeer", "127.0.0.3"))
                            }
                            assertEquals("true", js(scenario, "document.querySelector('#note').value='retained-form';true"))
                            results.put("formBeforeSelection", JSONObject(checkNotNull(js(scenario,
                                "({url:location.href,note:document.querySelector('#note').value,ready:document.readyState})"))))
                            assertEquals("\"retained-form\"", js(scenario, "document.querySelector('#note').value"))
                            fixture.slowHealthy = false; fixture.fastHealthy = false
                            Thread.sleep(1100) // Genuine core health endpoint's one-second dedup window.
                            assertTrue(runtime.checkHealthNow().get(20, TimeUnit.SECONDS))
                            assertEquals("DIRECT", api.request("GET", "/proxies/EGRESS").getString("now"))
                            recordHealth("all-unhealthy")
                            native("all-unhealthy", "127.0.0.1")
                            js(scenario, "fetch('/observe-down').then(r=>r.text()).then(t=>document.body.dataset.down=t);true")
                            await("new WebView request uses all-down DIRECT") { js(scenario, "document.body.dataset.down") == "\"127.0.0.1\"" }
                            results.put("formAfterAllDown", JSONObject(checkNotNull(js(scenario,
                                "({url:location.href,note:document.querySelector('#note').value,ready:document.readyState})"))))
                            assertEquals("\"$formPageUrl\"", js(scenario, "location.href"))
                            assertEquals("\"retained-form\"", js(scenario, "document.querySelector('#note').value"))
                            fixture.slowHealthy = true
                            Thread.sleep(1100)
                            assertTrue(runtime.checkHealthNow().get(20, TimeUnit.SECONDS))
                            recordHealth("recovered-first-source")
                            Thread.sleep(10_100) // Native AUTO's ten-second cache, not an app latency engine.
                            native("recovered-first-source", "127.0.0.2")
                            js(scenario, "fetch('/observe-recovered').then(r=>r.text()).then(t=>document.body.dataset.recovered=t);true")
                            await("new WebView request uses recovered AUTO") { js(scenario, "document.body.dataset.recovered") == "\"127.0.0.2\"" }
                            results.put("formAfterRecovery", JSONObject(checkNotNull(js(scenario,
                                "({url:location.href,note:document.querySelector('#note').value,ready:document.readyState})"))))
                            assertEquals("\"$formPageUrl\"", js(scenario, "location.href"))
                            assertEquals("\"retained-form\"", js(scenario, "document.querySelector('#note').value"))
                            cases.put(JSONObject().put("case", "selection-keeps-live-form").put("result", "retained"))
                            configure(emptyList())
                            scenario.recreate()
                            await("recreated core/provider") { runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort && runtime.status == "Running" }
                            assertEquals("v1.19.32", api.request("GET", "/version").getString("version"))
                            cases.put(JSONObject().put("case", "recreation").put("result", "core/provider ready"))
                            val directOrigin = URL("http://127.0.0.1:${fixture.http}/observe-recreated-os-direct")
                                .openConnection(java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
                            try {
                                directOrigin.connectTimeout = 3000; directOrigin.readTimeout = 3000
                                assertEquals("127.0.0.1", directOrigin.inputStream.bufferedReader().use { it.readText() })
                            } finally { directOrigin.disconnect() }
                            native("recreated-no-source-direct", "127.0.0.1", host = "127.0.0.1")
                            browser("http://127.0.0.1:${fixture.http}/page?nonce=${UUID.randomUUID()}", "127.0.0.1")
                            // Inject an actual child exit without adding a product kill/control interface.
                            val child = RgProxyRuntime::class.java.getDeclaredField("process").apply { isAccessible = true }
                                .get(runtime) as Process
                            assertTrue(child.isAlive)
                            child.destroyForcibly()
                            await("dead child clears native and provider proxy routing") {
                                !child.isAlive && runtime.proxyPort == null && runtime.webViewPort == null
                            }
                            native("core-exit-native-direct", "127.0.0.1", host = "127.0.0.1")
                            browser("http://127.0.0.1:${fixture.http}/page?nonce=${UUID.randomUUID()}", "127.0.0.1")
                            cases.put(JSONObject().put("case", "core-exit-webview-direct").put("destinationPeer", "127.0.0.1"))
                            scenario.recreate()
                            await("normal foreground restart after child exit") {
                                runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort && runtime.status == "Running"
                            }
                            assertEquals("v1.19.32", api.request("GET", "/version").getString("version"))
                            browser("http://127.0.0.1:${fixture.http}/page?nonce=${UUID.randomUUID()}", "127.0.0.1")
                            cases.put(JSONObject().put("case", "restart-after-core-exit").put("result", "core/provider ready"))
                        }
                        await("foreground runtime stopped and WebView override cleared") { runtime.proxyPort == null && runtime.webViewPort == null }
                        results.put("result", "PASS_EXERCISED_CASES")
                    }
                }
            }
        }
    }

    @Test fun teardownAndReportFailuresCannotSkipRecoveryRestoration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("LocalBrowserActivity", Context.MODE_PRIVATE)
        val snapshot = preferences.getString("local.tabs", null)
        val runtime = RgProxyRuntime.get(context)
        val observations = JSONArray()
        for (primary in listOf(null, AssertionError("Injected body failure"))) {
            val teardown = IOException("Injected teardown failure")
            val report = IOException("Injected report failure")
            val marker = "https://recovery-preservation.invalid/${UUID.randomUUID()}"
            lateinit var scenario: ActivityScenario<LocalBrowserActivity>
            val failure = runCatching {
                restoreRecoveryOnClose(preferences, snapshot).use {
                    Closeable {
                        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
                        val saved = JSONObject(checkNotNull(preferences.getString("local.tabs", null)))
                        assertTrue("Scenario close saved the controlled test location",
                            saved.getJSONArray("locations").getString(saved.getInt("selected")) == marker)
                        throw report
                    }.use {
                        Closeable { throw teardown }.use {
                            scenario = ActivityScenario.launch(LocalBrowserActivity::class.java)
                            scenario.use {
                                it.onActivity { activity -> activity.tabs.current.recoveryUrl = marker }
                                if (primary != null) throw primary
                            }
                        }
                    }
                }
            }.exceptionOrNull()
            assertSame(primary ?: teardown, failure)
            assertEquals(if (primary == null) listOf(report) else listOf(teardown, report),
                checkNotNull(failure).suppressed.toList())
            assertTrue("Starting recovery metadata restored without recording its value",
                preferences.getString("local.tabs", null) == snapshot)
            await("exceptional scenario stopped its runtime") { runtime.proxyPort == null && runtime.webViewPort == null }
            observations.put(JSONObject().put("primaryBodyFailure", primary != null)
                .put("scenarioClosed", true).put("recoverySnapshotRestored", true)
                .put("suppressedFailures", failure.suppressed.size))
        }
        File(context.filesDir, "mihomo-preservation-results.json").writeText(JSONObject()
            .put("result", "PASS_EXCEPTIONAL_RESTORATION").put("cases", observations).toString(2))
    }

    private fun restoreRecoveryOnClose(preferences: SharedPreferences, snapshot: String?) = Closeable {
        assertTrue("Recovery snapshot commit", preferences.edit().apply {
            if (snapshot == null) remove("local.tabs") else putString("local.tabs", snapshot)
        }.commit()) // In-memory starting value only; no personal locations in diagnostics.
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
