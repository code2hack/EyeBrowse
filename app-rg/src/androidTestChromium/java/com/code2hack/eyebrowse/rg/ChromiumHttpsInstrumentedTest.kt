package com.code2hack.eyebrowse.rg

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.CoreControl
import com.code2hack.eyebrowse.rg.proxy.ProxyPolicy
import com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime
import com.code2hack.eyebrowse.rg.proxy.RouteFixture
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Independent TLS/WSS first-origin case, so a prior cross-origin failure cannot hide its result. */
@RunWith(AndroidJUnit4::class)
class ChromiumHttpsInstrumentedTest {
    @Test fun firstOriginHttpsAndWssUseTheExistingProxyDirectPolicy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val preferences = localBrowserTestPreferences(context)
        val previous = preferences.getString("local.tabs", null)
        val runtime = RgProxyRuntime.get(context)
        val report = JSONObject().put("state", "INCOMPLETE")
            .put("candidateHead", InstrumentationRegistry.getArguments().getString("candidateHead"))
        Closeable {
            assertTrue(preferences.edit().apply {
                if (previous == null) remove("local.tabs") else putString("local.tabs", previous)
            }.commit())
        }.use {
            RouteFixture(instrumentation.context.assets.open("proxy-fixture.p12")).use { fixture ->
                try {
                    ActivityScenario.launch(LocalBrowserActivity::class.java).use { scenario ->
                        await { runtime.status == "Running" && runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort }
                        val socket = File(context.filesDir, "mihomo/control.sock").absolutePath
                        val api = CoreControl(socket)
                        report.put("core", api.request("GET", "/version"))
                            .put("nativeProxyPort", runtime.proxyPort).put("webViewProxyPort", runtime.webViewPort)
                        val configuration = ProxyPolicy.configuration(emptyList(), checkNotNull(runtime.proxyPort),
                            socket, listOf("system"), ProxyPolicy.TEST_URL)
                            .put("hosts", JSONObject().put("route.test", "127.0.0.1"))
                        val file = File(context.filesDir, "mihomo/https-first-origin.json")
                        file.writeText(ProxyPolicy.configurationText(configuration.toString()))
                        api.request("PUT", "/configs", JSONObject().put("path", file.absolutePath))
                        val native = CompletableFuture.supplyAsync {
                            val connection = runtime.openConnection(URL("https://route.test:${fixture.https}/hold-native-https"))
                            try {
                                assertEquals(200, connection.responseCode)
                                connection.inputStream.bufferedReader().use { reader -> reader.readText() }
                            } finally { connection.disconnect() }
                        }
                        var matched: JSONObject? = null
                        await {
                            val connections = api.request("GET", "/connections").optJSONArray("connections") ?: JSONArray()
                            matched = (0 until connections.length()).map { index -> connections.getJSONObject(index) }
                                .firstOrNull { it.getJSONObject("metadata").optString("destinationPort") == fixture.https.toString() }
                            matched != null
                        }
                        report.put("nativeMatchedConnection", matched)
                        assertEquals("[\"DIRECT\"]", checkNotNull(matched).getJSONArray("chains").toString())
                        assertEquals("127.0.0.1", native.get(10, TimeUnit.SECONDS))
                        scenario.onActivity { assertTrue(it.tabs.current.session.open("https://route.test:${fixture.https}/page").accepted()) }
                        await {
                            val done = CountDownLatch(1)
                            var observed: JSONObject? = null
                            scenario.onActivity { activity ->
                                activity.tabs.current.session.page!!.evaluateJavascript("""({scheme:location.protocol,
                                    route:document.body?.dataset.route,ws:document.body?.dataset.ws})""") { value ->
                                    observed = JSONObject(value); done.countDown()
                                }
                            }
                            assertTrue("renderer observation", done.await(3, TimeUnit.SECONDS))
                            report.put("webView", observed)
                            observed?.optString("scheme") == "https:" && observed?.optString("route") == "127.0.0.1" &&
                                observed?.optString("ws") == "127.0.0.1"
                        }
                        report.put("state", "PASS_EXERCISED_HTTPS_WSS_DIRECT_FIRST_ORIGIN")
                    }
                } finally {
                    report.put("destinationObservations", JSONArray(fixture.observations.map {
                        JSONObject().put("path", it.first).put("peer", it.second)
                    })).put("limits", "Fresh first-origin controlled TLS/WSS and empty-pool DIRECT only; no full routing, cross-origin or account/session pass.")
                    File(context.filesDir, "chromium-https.json").writeText(report.toString(2))
                }
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < end) { if (condition()) return; Thread.sleep(25) }
        fail("controlled HTTPS/WSS observation")
    }
}
