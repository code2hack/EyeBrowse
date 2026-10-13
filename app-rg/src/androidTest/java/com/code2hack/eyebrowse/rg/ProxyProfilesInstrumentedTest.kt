package com.code2hack.eyebrowse.rg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.*
import com.code2hack.eyebrowse.rg.qr.QrDecoder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.net.URL
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real app/core/network checks. Controlled raster delivery is explicitly separate from optical proof. */
@RunWith(AndroidJUnit4::class)
class ProxyProfilesInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val runtime = RgProxyRuntime.get(context)
    private val results = JSONObject().put("model", Build.MODEL).put("api", Build.VERSION.SDK_INT)
        .put("webView", WebView.getCurrentWebViewPackage()?.versionName)
    private val cases = JSONArray().also { results.put("cases", it) }

    private fun await(label: String, check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 45_000
        do { if (check()) return; SystemClock.sleep(20) } while (SystemClock.elapsedRealtime() < end)
        fail(label)
    }
    private fun preserving(name: String, afterReport: () -> Unit = {}, body: () -> Unit) {
        val keys = mapOf("LocalBrowserActivity" to listOf("local.tabs"), "proxy-profiles" to listOf("profiles"),
            "local-input-settings" to listOf("pointer.sensitivity", "edge.speed", "pointer.tenths", "edge.pixels-per-second"))
        val previous = keys.mapValues { (file, names) ->
            val preferences = context.getSharedPreferences(file, Context.MODE_PRIVATE)
            names.associateWith { preferences.getString(it, null) }
        }
        // Outer restoration runs after every scenario, fixture and fallible report/teardown closure.
        Closeable {
            var failure: Throwable? = null
            for ((file, snapshot) in previous) {
                try {
                    val preferences = context.getSharedPreferences(file, Context.MODE_PRIVATE)
                    assertTrue("Starting controlled keys restored", preferences.edit().apply {
                        snapshot.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
                    }.commit())
                    assertTrue("Starting controlled keys match in memory", snapshot.all { (key, value) -> preferences.getString(key, null) == value })
                } catch (caught: Throwable) {
                    if (failure == null) failure = caught else failure.addSuppressed(caught)
                }
            }
            failure?.let { throw it }
            File(context.filesDir, "issue44-$name-restoration.json").writeText("{\"startingControlledKeysRestored\":true}")
        }.use {
            Closeable {
                File(context.filesDir, "issue44-$name-results.json").writeText(results.toString(2))
                afterReport()
            }.use {
                assertTrue(context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE).edit().remove("profiles").commit())
                body()
            }
        }
        results.put("startingControlledKeysRestored", true)
        File(context.filesDir, "issue44-$name-results.json").writeText(results.toString(2))
    }

    @Test fun exceptionalTeardownAndReportKeepStartingControlledState() {
        val preferences = context.getSharedPreferences("LocalBrowserActivity", Context.MODE_PRIVATE)
        val profilePreferences = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
        val inputPreferences = context.getSharedPreferences("local-input-settings", Context.MODE_PRIVATE)
        val previous = preferences.getString("local.tabs", null)
        val profiles = profilePreferences.getString("profiles", null)
        val pointer = inputPreferences.getString("pointer.tenths", null)
        val observations = JSONArray()
        for (bodyFailure in listOf(null, AssertionError("Injected body failure"))) {
            val teardown = java.io.IOException("Injected teardown failure")
            val report = java.io.IOException("Injected report failure")
            val failure = runCatching {
                preserving("exception", afterReport = { throw report }) {
                    Closeable { throw teardown }.use {
                        ActivityScenario.launch(LocalBrowserActivity::class.java).use { scene ->
                            scene.onActivity { it.tabs.current.recoveryUrl = "https://owned-restoration.invalid/${UUID.randomUUID()}" }
                            assertTrue(inputPreferences.edit().putString("pointer.tenths", "31").commit())
                            if (bodyFailure != null) throw bodyFailure
                        }
                    }
                }
            }.exceptionOrNull()
            assertSame(bodyFailure ?: teardown, failure)
            assertEquals(if (bodyFailure == null) listOf(report) else listOf(teardown, report), checkNotNull(failure).suppressed.toList())
            assertTrue(preferences.getString("local.tabs", null) == previous)
            assertTrue(profilePreferences.getString("profiles", null) == profiles)
            assertTrue(inputPreferences.getString("pointer.tenths", null) == pointer)
            observations.put(JSONObject().put("bodyFailure", bodyFailure != null).put("startingKeysRestored", true)
                .put("suppressed", failure.suppressed.size))
            await("Exceptional scenario released runtime") { runtime.proxyPort == null && runtime.webViewPort == null }
        }
        File(context.filesDir, "issue44-preservation-results.json").writeText(JSONObject().put("cases", observations).toString(2))
    }
    private fun settled() {
        instrumentation.waitForIdleSync() // Android posts ordinary touch clicks to its main loop.
        await("profile operation finished") { !RgProfiles.get(context).busy }
        instrumentation.waitForIdleSync()
    }
    private fun tag(scene: ActivityScenario<LocalBrowserActivity>, name: String) {
        await("current native target $name") {
            var ready = false
            scene.onActivity { val view = it.root.findViewWithTag<View>(name)
                ready = view != null && view.isShown && view.isEnabled && view.width > 0 && !it.root.isLayoutRequested }
            ready
        }
        scene.onActivity { activity ->
            val view = checkNotNull(activity.root.findViewWithTag<View>(name))
            val point = IntArray(2); view.getLocationOnScreen(point)
            assertTrue(activity.input.activate(InputPoint(point[0] + view.width / 2f, point[1] + view.height / 2f)))
        }
    }
    private fun proxy(scene: ActivityScenario<LocalBrowserActivity>) {
        scene.onActivity { it.showUtility(LocalBrowserActivity.Utility.SETTINGS) }
        tag(scene, "settings.proxy")
        scene.onActivity { assertEquals(LocalBrowserActivity.Utility.PROXY, it.utility) }
    }
    private fun raster(payload: String): String {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
        val bytes = ByteArray(matrix.width * matrix.height) { if (matrix[it % matrix.width, it / matrix.width]) 0 else 0xff.toByte() }
        return checkNotNull(QrDecoder.decodeYPlane(bytes, matrix.width, matrix.width, matrix.height))
    }
    private fun scan(scene: ActivityScenario<LocalBrowserActivity>, payload: String) {
        tag(scene, "profile.add")
        scene.onActivity {
            assertEquals("Add a new profile", it.root.findViewWithTag<TextView>("qr.title").text)
            it.previewQr(raster(payload))
            assertFalse(it.cameraActive)
            assertEquals(LocalBrowserActivity.Utility.PROXY, it.utility)
        }
        settled()
    }
    private fun js(scene: ActivityScenario<LocalBrowserActivity>, script: String): String {
        val latch = CountDownLatch(1); var value = ""
        scene.onActivity { checkNotNull(it.tabs.current.session.page).evaluateJavascript(script) { result -> value = result; latch.countDown() } }
        assertTrue(latch.await(3, TimeUnit.SECONDS)); return value
    }
    private fun capture(scene: ActivityScenario<LocalBrowserActivity>, name: String, tags: List<String>) {
        instrumentation.waitForIdleSync()
        await("Capture $name targets fully laid out") {
            var ready = false
            scene.onActivity { activity ->
                ready = !activity.root.isLayoutRequested && tags.all { tag ->
                    val view = activity.root.findViewWithTag<View>(tag)
                    val area = Rect()
                    view != null && view.getGlobalVisibleRect(area) && area.width() == view.width && area.height() == view.height
                }
            }
            ready
        }
        val frame = CountDownLatch(1)
        val bounds = JSONObject()
        scene.onActivity {
            assertEquals(480, it.root.width); assertEquals(640, it.root.height); assertEquals(48, it.toolbar.height)
            for (tag in tags) {
                val view = checkNotNull(it.root.findViewWithTag<View>(tag))
                val area = Rect(); assertTrue(view.getGlobalVisibleRect(area))
                bounds.put(tag, JSONArray(listOf(area.left, area.top, area.width(), area.height())))
            }
            it.root.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }; it.root.invalidate()
        }
        assertTrue("Current frame committed", frame.await(3, TimeUnit.SECONDS))
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            assertEquals(480, bitmap.width); assertEquals(640, bitmap.height)
            context.openFileOutput("issue44-$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(context.filesDir, "issue44-$name-bounds.json").writeText(bounds.toString(2))
        } finally { bitmap.recycle() }
    }

    @Test(timeout = 240000) fun actualImportsPoolFallbackLastGoodAndRecreation() = preserving("imports") {
        RouteFixture(instrumentation.context.assets.open("proxy-fixture.p12")).use { fixture ->
            // This owned server chooses YAML only for our advertised compatible client.
            // Its generic-client response is encoded and must never become an empty success.
            fixture.subscriptionAgent = "mihomo/1.19.32 EyeBrowse/0.0.3"
            Closeable {
                results.put("originReceipts", JSONArray(fixture.observations.map { JSONObject().put("path", it.first).put("peer", it.second) }))
                results.put("dummyNodeReceipts", fixture.proxyReceipts())
            }.use {
                Closeable {
                    Closeable { runtime.useTestEndpoint(ProxyPolicy.TEST_URL) }.use {
                        assertTrue(runtime.replaceSources(emptyList()).get(30, TimeUnit.SECONDS))
                    }
                }.use {
                    runtime.useTestEndpoint("http://127.0.0.1:${fixture.http}/health")
                    val first = "http://127.0.0.1:${fixture.http}/source-one?token=owned-dummy-one"
                    val second = "http://127.0.0.1:${fixture.http}/source-two?token=owned-dummy-two"
                    fun subscription(port: Int) = """
                        mode: global
                        allow-lan: true
                        external-controller: 0.0.0.0:9999
                        dns: {listen: '0.0.0.0:53'}
                        rules: ['MATCH,REJECT']
                        script: {code: 'owned-dummy'}
                        proxies: [{name: duplicate-owned-name, type: http, server: 127.0.0.1, port: $port}]
                    """.trimIndent()
                    fixture.subscriptions["/source-one"] = 200 to subscription(fixture.slow)
                    fixture.subscriptions["/source-two"] = 200 to subscription(fixture.fast)
                    ActivityScenario.launch(LocalBrowserActivity::class.java).use { scene ->
                        await("Core/provider active") { runtime.status == "Running" && runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort }
                        val profiles = RgProfiles.get(context)
                        val api = CoreControl(File(context.filesDir, "mihomo/control.sock").absolutePath)
                        proxy(scene)
                        capture(scene, "empty", listOf("profile.add"))
                        fun mappedRoute() {
                            // Modify only test destination mapping in the actual production-generated config.
                            val config = JSONObject(File(context.filesDir, "mihomo/candidate.json").readText())
                            assertEquals("rule", config.getString("mode")); assertFalse(config.getBoolean("allow-lan"))
                            assertEquals("", config.getString("external-controller")); assertFalse(config.has("script"))
                            assertFalse(config.getJSONObject("dns").has("listen"))
                            val nodes = ProxyPolicy.nodes(profiles.profiles.map { it.asSource() })
                            if (nodes.isNotEmpty()) {
                                val actual = api.request("GET", "/providers/proxies/ENABLED").getJSONArray("proxies")
                                assertEquals(nodes.map { it.getString("name") }.toSet(), (0 until actual.length()).map { actual.getJSONObject(it).getString("name") }.toSet())
                            }
                            val old = config.getJSONArray("rules")
                            config.put("hosts", JSONObject().put("route.test", "127.0.0.1"))
                                .put("rules", JSONArray().put("DOMAIN,route.test,${if (nodes.isEmpty()) "DIRECT" else "EGRESS"}").apply {
                                    for (i in 0 until old.length()) put(old.get(i))
                                })
                            val file = File(context.filesDir, "mihomo/issue44-route.json").apply { writeText(ProxyPolicy.configurationText(config.toString())) }
                            api.request("PUT", "/configs", JSONObject().put("path", file.absolutePath))
                            assertTrue(runtime.checkHealthNow().get(30, TimeUnit.SECONDS))
                        }
                        mappedRoute()
                        val page = "http://route.test:${fixture.http}/page?nonce=${UUID.randomUUID()}"
                        scene.onActivity { it.dismissUtility(); assertTrue(it.tabs.current.session.open(page).accepted()) }
                        await("Fresh owned page") { js(scene, "location.href") == "\"$page\"" && js(scene, "document.readyState") == "\"complete\"" }
                        js(scene, "document.querySelector('#note').value='owned-retained-form';true")
                        proxy(scene)
                        fun probe(label: String, peer: String) {
                            mappedRoute()
                            val connection = runtime.openConnection(URL("http://route.test:${fixture.http}/observe-$label?nonce=${UUID.randomUUID()}"))
                            val observed = try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
                            assertEquals(label, peer, observed)
                            js(scene, "delete document.body.dataset.fresh;fetch('/observe-browser-$label?nonce=${UUID.randomUUID()}').then(r=>r.text()).then(t=>document.body.dataset.fresh=t);true")
                            await("Fresh browser request $label") { js(scene, "document.body.dataset.fresh") == JSONObject.quote(peer) }
                            assertEquals("\"$page\"", js(scene, "location.href"))
                            assertEquals("\"owned-retained-form\"", js(scene, "document.querySelector('#note').value"))
                            cases.put(JSONObject().put("case", label).put("nativeAndBrowserPeer", observed).put("pageAndFormRetained", true))
                        }
                        probe("no-profile-direct", "127.0.0.1")
                        scan(scene, first)
                        assertEquals("Profile saved.", profiles.message)
                        cases.put(JSONObject().put("case", "compatible-client-negotiates-yaml"))
                        assertEquals(listOf(true), profiles.profiles.map { it.enabled })
                        probe("first-source", "127.0.0.2")
                        scan(scene, "clash://install-config?url=${java.net.URLEncoder.encode(second, "UTF-8")}&name=ignored")
                        assertEquals(listOf(first, second), profiles.profiles.map { it.source })
                        assertEquals(listOf(true, true), profiles.profiles.map { it.enabled })
                        capture(scene, "both-on", listOf("profile.name.0", "profile.toggle.0", "profile.delete.0", "profile.name.1", "profile.toggle.1", "profile.delete.1", "profile.add"))
                        probe("faster-second-source", "127.0.0.3")
                        tag(scene, "profile.toggle.0"); settled()
                        assertEquals(listOf(false, true), profiles.profiles.map { it.enabled })
                        probe("first-off-second-retained", "127.0.0.3")
                        tag(scene, "profile.toggle.0"); settled()
                        tag(scene, "profile.toggle.1"); settled()
                        assertEquals(listOf(true, false), profiles.profiles.map { it.enabled })
                        probe("second-off-first-retained", "127.0.0.2")
                        val names = profiles.profiles.map { it.name }
                        scan(scene, second)
                        assertEquals(names, profiles.profiles.map { it.name })
                        assertEquals(listOf(true, false), profiles.profiles.map { it.enabled })
                        cases.put(JSONObject().put("case", "same-source-update-preserves-identity-name-off").put("rows", profiles.profiles.size))
                        val preferences = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
                        val lastGood = preferences.getString("profiles", null)
                        val scalarReuse = "shared: &secret '" + "x".repeat(32 * 1024) + "'\nproxies:\n" +
                            "  - {type: http, server: proxy.test, port: 80, password: *secret}\n".repeat(80)
                        val escapeHeavy = "proxies:\n  - {type: http, server: proxy.test, port: 80, password: '" +
                            "\\".repeat(1100 * 1024) + "'}"
                        for ((label, bad) in listOf("http" to (503 to "owned dummy error"), "malformed" to (200 to "proxies: ["),
                            "provider-only" to (200 to "proxy-providers: {owned: {type: http}}"),
                            "scalar-reuse-limit" to (200 to scalarReuse), "cache-escape-limit" to (200 to escapeHeavy),
                            "core-schema" to (200 to "proxies: [{type: ss, server: 127.0.0.1, port: 12345, cipher: invalid-owned-cipher, password: dummy}]"))) {
                            fixture.subscriptions["/source-two"] = bad
                            scan(scene, second)
                            assertNotEquals("Profile saved.", profiles.message)
                            assertEquals("Last-good data kept after $label", lastGood, preferences.getString("profiles", null))
                            probe("last-good-$label", "127.0.0.2")
                        }
                        fixture.subscriptions["/source-two"] = 200 to subscription(fixture.fast)
                        tag(scene, "profile.toggle.0"); settled()
                        probe("all-off-direct", "127.0.0.1")
                        scene.recreate()
                        await("Recreated core/provider") { runtime.status == "Running" && runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort }
                        await("Persisted profile rows") { profiles.profiles.size == 2 && !profiles.busy }
                        assertEquals(listOf(first, second), profiles.profiles.map { it.source })
                        assertEquals(names, profiles.profiles.map { it.name })
                        assertEquals(listOf(false, false), profiles.profiles.map { it.enabled })
                        cases.put(JSONObject().put("case", "recreation-persists-two-independent-off-sources"))
                        // Recreation intentionally preserves locations, not unsaved page/form memory.
                        mappedRoute()
                        scene.onActivity { assertTrue(it.tabs.current.session.open(page).accepted()) }
                        await("Reopened fresh owned page") { js(scene, "location.href") == "\"$page\"" && js(scene, "document.readyState") == "\"complete\"" }
                        js(scene, "document.querySelector('#note').value='owned-retained-form';true")
                        proxy(scene)
                        tag(scene, "profile.toggle.0"); settled(); tag(scene, "profile.toggle.1"); settled()
                        fixture.slowHealthy = false; fixture.fastHealthy = false
                        Thread.sleep(1100)
                        probe("all-unhealthy-direct", "127.0.0.1")
                        fixture.fastHealthy = true
                        Thread.sleep(1100)
                        assertTrue(runtime.checkHealthNow().get(30, TimeUnit.SECONDS))
                        Thread.sleep(10100) // Genuine core AUTO cache, not a second selection engine.
                        probe("second-source-recovered", "127.0.0.3")
                        tag(scene, "profile.delete.0"); settled()
                        assertEquals(listOf(second), profiles.profiles.map { it.source })
                        probe("delete-one-keeps-second", "127.0.0.3")
                        tag(scene, "profile.delete.0"); settled()
                        assertTrue(profiles.profiles.isEmpty())
                        probe("last-delete-direct", "127.0.0.1")
                        scene.onActivity { it.showUtility(LocalBrowserActivity.Utility.QR); it.previewQr(first) }
                        assertTrue(profiles.profiles.isEmpty())
                        scene.onActivity { assertNotNull(it.root.findViewWithTag<View>("qr.open")); it.dismissUtility() }
                        cases.put(JSONObject().put("case", "website-qr-preview-does-not-import"))
                        results.put("decoderDelivery", "CONTROLLED_RASTER_NOT_OPTICAL").put("result", "PASS_EXERCISED_CASES")
                    }
                    await("Foreground core and provider released") { runtime.proxyPort == null && runtime.webViewPort == null }
                }
            }
        }
    }

    @Test(timeout = 90000) fun longListGeometryAndOrdinaryInputRemainUsable() = preserving("layout") {
        val preferences = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
        val store = ProfileStore({ preferences.getString("profiles", null) }) { preferences.edit().putString("profiles", it).commit() }
        val node = mapOf<String, Any?>("type" to "http", "name" to "same", "server" to "127.0.0.1", "port" to 9)
        assertTrue(store.save((1..12).map { SavedProfile("https://owned-dummy.invalid/$it", "Owned long friendly profile $it " + "name ".repeat(12), false, listOf(node)) }))
        ActivityScenario.launch(LocalBrowserActivity::class.java).use { scene ->
            val source = RawPoseReplay()
            scene.onActivity { it.pointer.stop(); it.pointer.replaceSourceForTest(source); source.orientationDegrees(0.0, 0.0); it.pointer.start() }
            await("Raw-pose neutral acquisition") { var ready = false; scene.onActivity { ready = it.pointer.position.available && it.pointer.position.y in 318f..322f }; ready }
            await("Stored long list loaded") { RgProfiles.get(context).profiles.size == 12 }
            proxy(scene)
            capture(scene, "long-list-top", listOf("profile.name.0", "profile.toggle.0", "profile.delete.0"))
            scene.onActivity {
                for ((tag, x, width) in listOf(Triple("profile.name.0", 16, 264), Triple("profile.toggle.0", 288, 64), Triple("profile.delete.0", 360, 104))) {
                    val view = checkNotNull(it.root.findViewWithTag<View>(tag)); val bounds = Rect()
                    assertTrue(view.getGlobalVisibleRect(bounds)); assertEquals(x, bounds.left); assertEquals(112, bounds.top)
                    assertEquals(width, bounds.width()); assertEquals(56, bounds.height())
                }
                val name = it.root.findViewWithTag<TextView>("profile.name.0")
                assertEquals(android.text.TextUtils.TruncateAt.END, name.ellipsize)
                assertEquals(8, it.controls.size)
            }
            fun scroll(activity: LocalBrowserActivity) = activity.root.findViewWithTag<View>("profile.add").parent.parent as ScrollView
            var pageY = 0
            scene.onActivity {
                pageY = it.tabs.current.session.page?.scrollY ?: 0
                assertTrue(scroll(it).canScrollVertically(1))
                source.orientationDegrees(0.0, 50.0)
            }
            await("Proxy owns bottom-strip scrolling") { var moved = false; scene.onActivity {
                moved = it.pointer.position.y == 664f && it.edgeScrollRunning && scroll(it).scrollY > 20
                assertEquals(pageY, it.tabs.current.session.page?.scrollY ?: 0)
            }; moved }
            await("Raw bottom strip reaches Add at the actual bottom") { var bottom = false; scene.onActivity {
                bottom = !scroll(it).canScrollVertically(1)
            }; bottom }
            scene.onActivity { source.orientationDegrees(0.0, 0.0) }
            await("Visible pointer stops proxy scroll") { var stopped = false; scene.onActivity { stopped = !it.edgeScrollRunning }; stopped }
            capture(scene, "long-list-bottom", listOf("profile.add"))
            scene.onActivity { source.orientationDegrees(0.0, -50.0) }
            await("Proxy owns top-strip scrolling") { var moved = false; scene.onActivity {
                moved = it.pointer.position.y == -24f && it.edgeScrollRunning && scroll(it).scrollY < 150
                assertEquals(pageY, it.tabs.current.session.page?.scrollY ?: 0)
            }; moved }
            scene.onActivity { source.orientationDegrees(0.0, 0.0) }
            await("Visible return stops top-strip scroll") { var stopped = false; scene.onActivity { stopped = !it.edgeScrollRunning }; stopped }
            tag(scene, "utility.done")
            scene.onActivity {
                assertNotNull(it.root.findViewWithTag<View>("settings.pointer.value"))
                assertNotNull(it.root.findViewWithTag<View>("settings.scroll.value"))
                it.dismissUtility(); it.controls.getValue("hud.address").performClick()
                it.address.requestFocus()
            }
            await("Native address keyboard shown") { var shown = false; scene.onActivity { shown = it.keyboard.isShown }; shown }
            scene.onActivity {
                it.address.setText("owned"); it.address.setSelection(it.address.length())
                it.keyButtons.getValue(RgKeyboardKeys.Key.Character("a")).performClick()
                assertEquals("owneda", it.address.text.toString())
                it.dismissKeyboard()
                it.controls.getValue("hud.tab_counter").performClick()
                assertEquals(LocalBrowserActivity.Utility.TABS, it.utility)
                it.showUtility(LocalBrowserActivity.Utility.BOOKMARKS)
                assertEquals(LocalBrowserActivity.Utility.BOOKMARKS, it.utility)
            }
            results.put("rows", 12).put("result", "PASS_EXERCISED_LAYOUT_AND_NATIVE_INPUT")
        }
        await("Runtime stopped after layout scenario") { runtime.proxyPort == null && runtime.webViewPort == null }
    }
}
