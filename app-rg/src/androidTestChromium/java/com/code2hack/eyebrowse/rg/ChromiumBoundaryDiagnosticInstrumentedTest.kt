package com.code2hack.eyebrowse.rg

import android.net.http.SslError
import android.net.http.X509TrustManagerExtensions
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.CoreControl
import com.code2hack.eyebrowse.rg.proxy.ProxyPolicy
import com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime
import com.code2hack.eyebrowse.rg.proxy.RouteFixture
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Observations on controlled inputs only; this does not replace any original acceptance case. */
@RunWith(AndroidJUnit4::class)
class ChromiumBoundaryDiagnosticInstrumentedTest {
    @Test fun observeProductionNavigationWithHealthyPool() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val base = checkNotNull(InstrumentationRegistry.getArguments().getString("fixtureBaseUrl")).trimEnd('/')
        require(base == "http://100.92.81.33:39030")
        val preferences = localBrowserTestPreferences(context)
        val previous = preferences.getString("local.tabs", null)
        val runtime = RgProxyRuntime.get(context)
        val events = CopyOnWriteArrayList<JSONObject>()
        val observations = JSONArray()
        val report = JSONObject().put("scope", "DIAGNOSTIC_ONLY_ORIGINAL_CRITERIA_UNCHANGED")
        Closeable {
            report.put("events", JSONArray(events)).put("observations", observations)
            File(context.filesDir, "chromium-navigation-diagnostic.json").writeText(report.toString(2))
            assertTrue(preferences.edit().apply {
                if (previous == null) remove("local.tabs") else putString("local.tabs", previous)
            }.commit())
        }.use {
            RouteFixture(instrumentation.context.assets.open("proxy-fixture.p12")).use { fixture ->
                Closeable {
                    runtime.useTestEndpoint(ProxyPolicy.TEST_URL)
                    runtime.replaceSources(emptyList()).get(20, TimeUnit.SECONDS)
                }.use {
                    runtime.useTestEndpoint("http://health.test:${fixture.http}/health")
                    assertTrue(runtime.replaceSources(fixture.sources()).get(20, TimeUnit.SECONDS))
                    ActivityScenario.launch(LocalBrowserActivity::class.java).use { scenario ->
                        val readyBy = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                        while (runtime.webViewPort == null && System.nanoTime() < readyBy) Thread.sleep(25)
                        val port = checkNotNull(runtime.webViewPort)
                        val socket = File(context.filesDir, "mihomo/control.sock").absolutePath
                        val config = ProxyPolicy.configuration(ProxyPolicy.nodes(fixture.sources()), port, socket,
                            listOf("system"), runtime.testUrl)
                            .put("hosts", JSONObject().put("route.test", "127.0.0.1").put("health.test", "127.0.0.1"))
                        val configFile = File(context.filesDir, "mihomo/navigation-diagnostic.json")
                        configFile.writeText(ProxyPolicy.configurationText(config.toString()))
                        CoreControl(socket).request("PUT", "/configs", JSONObject().put("path", configFile.absolutePath))
                        scenario.onActivity { activity ->
                            val session = activity.tabs.current.session
                            val original = session.onStateChanged
                            session.onStateChanged = { state ->
                                events += JSONObject().put("phase", state.phase.name).put("url", state.url)
                                    .put("error", state.error).put("callback", Throwable().stackTrace
                                        .map { it.methodName }.take(7).joinToString("/"))
                                original(state)
                            }
                        }
                        val nonce = java.util.UUID.randomUUID().toString()
                        for (path in listOf("keyboard.html", "link", "route-probe/$nonce", "does-not-exist.html", "keyboard.html")) {
                            scenario.onActivity { activity ->
                                val session = activity.tabs.current.session
                                if (path == "link") session.page!!.evaluateJavascript("document.querySelector('a').click()", null)
                                else assertTrue(session.open("$base/$path").accepted())
                            }
                            Thread.sleep(4000)
                            val done = CountDownLatch(1)
                            scenario.onActivity { activity ->
                                val session = activity.tabs.current.session
                                val sample = JSONObject().put("requested", path).put("phase", session.state.phase.name)
                                    .put("stateUrl", session.state.url).put("viewUrl", session.page!!.url)
                                    .put("visibility", session.page!!.visibility).put("error", session.state.error)
                                session.page!!.evaluateJavascript("({url:location.href,ready:document.readyState,title:document.title,body:document.body?.innerText.slice(0,500)})") { value ->
                                    observations.put(sample.put("document", JSONObject(value)))
                                    done.countDown()
                                }
                            }
                            assertTrue("diagnostic renderer sample", done.await(3, TimeUnit.SECONDS))
                        }
                    }
                }
            }
        }
    }

    @Test fun observeTrustAndRawCallbacksWithoutChangingPolicy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val preferences = localBrowserTestPreferences(context)
        val previous = preferences.getString("local.tabs", null)
        val report = JSONObject().put("scope", "DIAGNOSTIC_ONLY_ORIGINAL_CRITERIA_UNCHANGED")
        val events = CopyOnWriteArrayList<JSONObject>()
        val observations = JSONArray()
        val runtime = RgProxyRuntime.get(context)
        Closeable {
            assertTrue(preferences.edit().apply {
                if (previous == null) remove("local.tabs") else putString("local.tabs", previous)
            }.commit())
            report.put("events", JSONArray(events)).put("observations", observations)
            File(context.filesDir, "chromium-boundary-diagnostic.json").writeText(report.toString(2))
        }.use {
            RouteFixture(instrumentation.context.assets.open("proxy-fixture.p12")).use { fixture ->
                ActivityScenario.launch(LocalBrowserActivity::class.java).use { scenario ->
                    val readyBy = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                    while (runtime.webViewPort == null && System.nanoTime() < readyBy) Thread.sleep(25)
                    val port = checkNotNull(runtime.webViewPort)
                    val socket = File(context.filesDir, "mihomo/control.sock").absolutePath
                    val configuration = ProxyPolicy.configuration(emptyList(), port, socket,
                        listOf("system"), ProxyPolicy.TEST_URL)
                        .put("hosts", JSONObject().put("route.test", "127.0.0.1"))
                    val file = File(context.filesDir, "mihomo/boundary-diagnostic.json")
                    file.writeText(ProxyPolicy.configurationText(configuration.toString()))
                    CoreControl(socket).request("PUT", "/configs", JSONObject().put("path", file.absolutePath))
                    val keys = KeyStore.getInstance("PKCS12").apply {
                        instrumentation.context.assets.open("proxy-fixture.p12").use { load(it, "fixture".toCharArray()) }
                    }
                    val chain = keys.getCertificateChain(keys.aliases().nextElement())
                        .map { it as X509Certificate }.toTypedArray()
                    val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                        init(null as KeyStore?)
                    }.trustManagers.filterIsInstance<X509TrustManager>().single()
                    report.put("platformTrustManager", trust.javaClass.name)
                    report.put("platformFixtureTrust", runCatching {
                        X509TrustManagerExtensions(trust).checkServerTrusted(chain, "RSA", "route.test").size
                    }.fold({ "ACCEPTED_CHAIN_LENGTH_$it" }, { it.toString() }))
                    var probe: WebView? = null
                    scenario.onActivity { activity ->
                        probe = WebView(activity).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = object : WebViewClient() {
                                private fun record(name: String, url: String?, extra: JSONObject = JSONObject()) {
                                    events += extra.put("event", name).put("url", url).put("atNs", System.nanoTime())
                                }
                                override fun onPageStarted(view: WebView, url: String?, icon: android.graphics.Bitmap?) {
                                    record("started", url)
                                }
                                override fun onPageFinished(view: WebView, url: String?) { record("finished", url) }
                                override fun doUpdateVisitedHistory(view: WebView, url: String?, reload: Boolean) {
                                    record("history", url)
                                }
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    record("override", request.url.toString(), JSONObject().put("main", request.isForMainFrame))
                                    return false
                                }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    record("error", request.url.toString(), JSONObject().put("main", request.isForMainFrame)
                                        .put("code", error.errorCode).put("description", error.description))
                                }
                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                    record("httpError", request.url.toString(), JSONObject().put("main", request.isForMainFrame)
                                        .put("status", response.statusCode))
                                }
                                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                    record("sslError", error.url, JSONObject().put("code", error.primaryError))
                                    handler.cancel()
                                }
                            }
                        }
                        activity.addContentView(probe, ViewGroup.LayoutParams(-1, -1))
                    }
                    try {
                        for (url in listOf("https://route.test:${fixture.https}/page",
                            "http://127.0.0.1:${fixture.http}/page")) {
                            scenario.onActivity { probe!!.loadUrl(url) }
                            Thread.sleep(5000) // Diagnostic sampling only; original tests keep their own waits.
                            val done = CountDownLatch(1)
                            scenario.onActivity {
                                probe!!.evaluateJavascript("({url:location.href,ready:document.readyState,route:document.body?.dataset.route,ws:document.body?.dataset.ws})") { value ->
                                    observations.put(JSONObject().put("requested", url).put("document", JSONObject(value)))
                                    done.countDown()
                                }
                            }
                            assertTrue("diagnostic renderer response", done.await(3, TimeUnit.SECONDS))
                        }
                    } finally {
                        scenario.onActivity { (probe!!.parent as ViewGroup).removeView(probe); probe!!.destroy() }
                        report.put("fixtureReceipts", JSONArray(fixture.observations.map {
                            JSONObject().put("path", it.first).put("peer", it.second)
                        }))
                    }
                }
            }
        }
    }
}
