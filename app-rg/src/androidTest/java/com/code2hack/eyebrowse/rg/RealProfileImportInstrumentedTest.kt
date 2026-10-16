package com.code2hack.eyebrowse.rg

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.CoreControl
import com.code2hack.eyebrowse.rg.proxy.ProfileImport
import com.code2hack.eyebrowse.rg.proxy.ProfileStore
import com.code2hack.eyebrowse.rg.proxy.ProxyPolicy
import com.code2hack.eyebrowse.rg.proxy.RgProfiles
import com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime
import com.code2hack.eyebrowse.rg.qr.QrDecoder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import org.json.JSONTokener
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt-in real-source checks. Reports contain only fixed categories, counts and hashes. */
@RunWith(AndroidJUnit4::class)
class RealProfileImportInstrumentedTest {
    @Test fun savedProfilesSurviveFreshProcessWithoutChangingThem() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("readSavedProfiles") == "true")
        val invocation = UUID.fromString(checkNotNull(arguments.getString("invocationId"))).toString()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
        val before = checkNotNull(preferences.getString("profiles", null))
        val tabs = localBrowserTestPreferences(context)
        val previousTabs = tabs.getString("local.tabs", null)
        val disk = ProfileStore({ preferences.getString("profiles", null) }) { error("Read-only check must not write") }
        val profiles = RgProfiles.get(context).profiles
        assertTrue(disk.readable)
        assertEquals(arguments.getString("expectedProfiles")?.toInt(), disk.profiles.size)
        assertEquals(arguments.getString("expectedEnabledNodes")?.toInt(), disk.profiles.filter { it.enabled }.sumOf { it.nodes.size })
        assertTrue(disk.profiles.all { ProfileImport.source(it.source) == it.source && it.name.matches(Regex("Profile [1-9][0-9]*")) })
        assertTrue(disk.profiles.zip(profiles).all { (a, b) ->
            a.source == b.source && a.name == b.name && a.enabled == b.enabled && a.nodes == b.nodes
        } && disk.profiles.size == profiles.size)
        assertTrue("Private profile cache unchanged", before == preferences.getString("profiles", null))
        assertTrue("Starting tab metadata unchanged", previousTabs == tabs.getString("local.tabs", null))
        assertNull(RgProxyRuntime.get(context).proxyPort)
        File(context.filesDir, "issue44-real-persistence.json").writeText(JSONObject()
            .put("invocation", invocation).put("process", android.os.Process.myPid())
            .put("profiles", profiles.size).put("enabledNodes", profiles.filter { it.enabled }.sumOf { it.nodes.size })
            .put("canonicalIdentityNameToggleNodesReloaded", true).put("privateCacheUnchanged", true)
            .put("tabMetadataUnchanged", true).put("coreStarted", false).toString(2))
    }

    @Test fun retainedToolbarStarTabsBookmarksAndKeyboardKeepRealProfile() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("retainedProfileInput") == "true")
        val invocation = UUID.fromString(checkNotNull(args.getString("invocationId"))).toString()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val tabs = localBrowserTestPreferences(context)
        val previousTabs = tabs.getString("local.tabs", null)
        val profiles = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
        val previousProfiles = checkNotNull(profiles.getString("profiles", null))
        val page = "http://100.92.81.33:39030/local-keyboard.html?issue44=$invocation"
        val history = "http://100.92.81.33:39030/history.html?issue44=$invocation"
        val bookmarks = File(context.filesDir, "local-browser/bookmarks.properties")
        val store = LocalBookmarks(bookmarks)
        assertNull(store.error); assertFalse(store.contains(page))
        fun await(label: String, check: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 45_000
            while (SystemClock.elapsedRealtime() < end) { if (check()) return; SystemClock.sleep(25) }
            fail(label)
        }
        Closeable {
            assertTrue("Starting tabs restored", tabs.edit().apply {
                if (previousTabs == null) remove("local.tabs") else putString("local.tabs", previousTabs)
            }.commit())
            assertTrue("Starting tabs match", previousTabs == tabs.getString("local.tabs", null))
            assertTrue("Real private profile unchanged", previousProfiles == profiles.getString("profiles", null))
        }.use {
            // Remove only this initially absent owned entry after every fallible scenario closure.
            Closeable { removeOwnedBookmark(store, bookmarks, page) }.use {
                ActivityScenario.launch(LocalBrowserActivity::class.java).use { scene ->
                    fun click(find: (LocalBrowserActivity) -> View) {
                        await("Current native input target") {
                            var ready = false; scene.onActivity { val v = find(it)
                                ready = v.isShown && v.isEnabled && v.width > 0 && !it.root.isLayoutRequested }; ready
                        }
                        scene.onActivity { val v = find(it); val p = IntArray(2); v.getLocationOnScreen(p)
                            assertTrue(it.input.activate(InputPoint(p[0] + v.width / 2f, p[1] + v.height / 2f))) }
                        instrumentation.waitForIdleSync()
                    }
                    fun tag(name: String) = click { checkNotNull(it.root.findViewWithTag<View>(name)) }
                    fun js(code: String): String {
                        var value = ""; val latch = CountDownLatch(1)
                        scene.onActivity { checkNotNull(it.tabs.current.session.page).evaluateJavascript(code) { value = it; latch.countDown() } }
                        assertTrue(latch.await(3, TimeUnit.SECONDS)); return JSONTokener(value).nextValue().toString()
                    }
                    val runtime = RgProxyRuntime.get(context)
                    await("Foreground proxy ready") { runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort }
                    scene.onActivity { assertEquals(8, it.controls.size); assertTrue(it.tabs.current.session.open(page).accepted()) }
                    await("Owned fresh page") { js("location.href") == page && js("document.readyState") == "complete" }
                    tag("hud.address"); scene.onActivity { it.address.selectAll() }
                    click { it.keyButtons.getValue(RgKeyboardKeys.Key.Character("a")) }
                    scene.onActivity { assertEquals("a", it.address.text.toString()) }
                    tag("hud.bookmark")
                    scene.onActivity { assertTrue(it.bookmarks.contains(page)); assertEquals("★", it.controls.getValue("hud.bookmark").text.toString()) }
                    tag("hud.bookmark")
                    scene.onActivity { confirmOwnedBookmarkRemoved(it.bookmarks, bookmarks, page) }
                    click { it.keyButtons.getValue(RgKeyboardKeys.Key.Command.DONE) }
                    tag("hud.more"); tag("menu.1")
                    scene.onActivity { assertEquals(LocalBrowserActivity.Utility.BOOKMARKS, it.utility) }
                    tag("utility.done")
                    scene.onActivity { assertTrue(it.tabs.current.session.open(history).accepted()) }
                    await("Owned history page") { js("location.href") == history && js("document.readyState") == "complete" }
                    tag("hud.back"); await("Native Back") { js("location.href") == page }
                    tag("hud.forward"); await("Native Forward") { js("location.href") == history }
                    val origin = js("String(performance.timeOrigin)")
                    tag("hud.refresh"); await("Native Refresh") { js("String(performance.timeOrigin)") != origin && js("document.readyState") == "complete" }
                    var count = 0; scene.onActivity { count = it.tabs.count }
                    tag("hud.tab_counter"); scene.onActivity { assertEquals(LocalBrowserActivity.Utility.TABS, it.utility) }
                    tag("hud.more"); tag("menu.0"); scene.onActivity { assertEquals(count + 1, it.tabs.count) }
                    tag("hud.close_tab"); scene.onActivity { assertEquals(count, it.tabs.count) }
                }
            }
        }
        await("Owned foreground core released") { RgProxyRuntime.get(context).proxyPort == null && RgProxyRuntime.get(context).webViewPort == null }
        File(context.filesDir, "issue44-retained-input.json").writeText(JSONObject()
            .put("invocation", invocation).put("nativeKeyboardStarBookmarksTabsCloseBackForwardRefresh", true)
            .put("ownedBookmarkRemoved", true).put("startingTabsRestored", true)
            .put("realProfileUnchanged", true).put("foregroundRuntimeReleased", true).toString(2))
    }

    /** Classification stays in-process; only static categories and counts leave this function. */
    private fun inspect(status: Int, contentType: String?, document: String): JSONObject {
        val knownTypes = setOf("http", "socks5", "ss", "ssr", "vmess", "vless", "trojan", "snell",
            "hysteria", "hysteria2", "tuic", "anytls", "wireguard", "ssh")
        val knownMime = setOf("application/yaml", "text/yaml", "application/x-yaml", "text/plain",
            "application/json", "text/html", "application/octet-stream")
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase()
        val output = JSONObject().put("httpStatus", status).put("contentTypeCategory",
            if (mime == null) "ABSENT" else if (mime in knownMime) mime else "OTHER")
            .put("bodyRead", status == 200).put("utf8Bytes", document.toByteArray(Charsets.UTF_8).size)
        if (status != 200) return output.put("stage", "HTTP_RESPONSE")
        val root = runCatching { Yaml(SafeConstructor(LoaderOptions().apply {
            isAllowDuplicateKeys = false; maxAliasesForCollections = 0
            nestingDepthLimit = 20; codePointLimit = ProfileImport.MAX_BYTES
        })).load<Any?>(document) }.getOrNull()
        val text = document.trim()
        val decoded = if (root is String && text.matches(Regex("[A-Za-z0-9+/=\\r\\n]+")))
            runCatching { String(java.util.Base64.getMimeDecoder().decode(text), Charsets.UTF_8) }.getOrNull() else null
        val representation = when {
            text.startsWith('<') -> "HTML_OR_XML"
            root is Map<*, *> && root["proxies"] is List<*> -> "YAML_PROXIES"
            root is Map<*, *> && root.containsKey("proxy-providers") -> "YAML_PROVIDER_ONLY"
            root is Map<*, *> -> "YAML_OTHER_MAPPING"
            decoded != null && decoded.lineSequence().any { line -> knownTypes.any { line.startsWith("$it://") } } -> "BASE64_PROXY_LINKS"
            decoded != null -> "BASE64_TEXT"
            root is String && knownTypes.any { text.startsWith("$it://") } -> "PROXY_LINK_TEXT"
            else -> "TEXT_OR_INVALID_YAML"
        }
        output.put("representation", representation)
        ((root as? Map<*, *>)?.get("proxies") as? List<*>)?.let { nodes ->
            output.put("nodeCount", nodes.size)
            val protocols = nodes.groupingBy { node ->
                val protocol = (node as? Map<*, *>)?.get("type")
                if (protocol in knownTypes) protocol as String else "OTHER"
            }.eachCount()
            output.put("protocolCategories", JSONObject(protocols))
        }
        try { output.put("usableNodeCount", ProfileImport.nodes(document).size).put("stage", "PARSER_ACCEPTED") }
        catch (failure: ProfileImport.Rejected) {
            output.put("stage", "PARSER_REJECTED").put("failureCategory", failure.category)
                .put("fieldCategory", failure.field ?: "NONE").put("protocolCategory", failure.protocol ?: "NONE")
        } catch (_: Exception) { output.put("stage", "PARSER_REJECTED").put("failureCategory", "OTHER") }
        return output
    }

    @Test fun selectedPrivateSourceUsesNativeAddAndProductionImport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("privateSourceImport") == "true")
        val invocation = UUID.fromString(checkNotNull(arguments.getString("invocationId"))).toString()
        val context = instrumentation.targetContext
        val preferences = localBrowserTestPreferences(context)
        val previousTabs = preferences.getString("local.tabs", null)
        val cache = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
        val previousCache = cache.getString("profiles", null)
        val profiles = RgProfiles.get(context)
        val download = AtomicReference<JSONObject>()
        val report = JSONObject().put("invocation", invocation)
            .put("delivery", "CONTROLLED_RASTER_NOT_OPTICAL").put("phase", "PRIVATE_INPUT")
        var ownsInspection = false
        fun writeReport() {
            download.get()?.let { report.put("download", it) }
            context.openFileOutput("issue59-import-$invocation.json", Context.MODE_PRIVATE).use {
                it.write(report.toString(2).toByteArray(Charsets.UTF_8))
            }
        }
        fun await(label: String, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 45_000
            while (SystemClock.elapsedRealtime() < end) {
                if (condition()) return
                SystemClock.sleep(25)
            }
            fail(label)
        }
        try {
            val input = File(context.filesDir, "issue59-input-$invocation.txt")
            assertTrue("Private input size", input.length() in 1L..16_384L)
            val bytes = input.readBytes()
            assertTrue("Private input bound", bytes.size in 1..16_384)
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            assertTrue("Selected private input binding", fingerprint == arguments.getString("sourceSHA256"))
            val payload = bytes.toString(Charsets.UTF_8)
            report.put("sourceSHA256", fingerprint)
            check(profiles.inspectDownload == null)
            profiles.inspectDownload = { status, type, document -> download.set(inspect(status, type, document)) }
            ownsInspection = true
            Closeable {
                profiles.inspectDownload = null
                writeReport()
            }.use {
                Closeable {
                    assertTrue("Starting tabs restored", preferences.edit().apply {
                        if (previousTabs == null) remove("local.tabs") else putString("local.tabs", previousTabs)
                    }.commit())
                    report.put("startingTabsRestored", preferences.getString("local.tabs", null) == previousTabs)
                }.use {
                    ActivityScenario.launch(LocalBrowserActivity::class.java).use { scene ->
                        fun click(tag: String) {
                            await("Current native import control") {
                                var ready = false
                                scene.onActivity {
                                    val view = it.root.findViewWithTag<View>(tag)
                                    ready = view != null && view.isShown && view.isEnabled &&
                                        view.width > 0 && !it.root.isLayoutRequested
                                }
                                ready
                            }
                            scene.onActivity {
                                val view = checkNotNull(it.root.findViewWithTag<View>(tag))
                                val point = IntArray(2); view.getLocationOnScreen(point)
                                assertTrue(it.input.activate(InputPoint(point[0] + view.width / 2f,
                                    point[1] + view.height / 2f)))
                            }
                            instrumentation.waitForIdleSync()
                        }
                        report.put("phase", "NATIVE_SETTINGS_PROXY_ADD")
                        click("hud.more"); click("menu.3"); click("settings.proxy"); click("profile.add")
                        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
                        val plane = ByteArray(matrix.width * matrix.height) {
                            if (matrix[it % matrix.width, it / matrix.width]) 0 else 0xff.toByte()
                        }
                        val decoded = QrDecoder.decodeYPlane(plane, matrix.width, matrix.width, matrix.height)
                        assertTrue("Private source controlled decoder round trip", decoded == payload)
                        scene.onActivity {
                            assertTrue(it.utility == LocalBrowserActivity.Utility.QR)
                            assertTrue(it.root.findViewWithTag<TextView>("qr.title").text == "Add a new profile")
                            report.put("cameraHadFrameAtDelivery", it.cameraHasFrame)
                            it.previewQr(checkNotNull(decoded))
                            assertFalse(it.cameraActive)
                            assertTrue(it.utility == LocalBrowserActivity.Utility.PROXY)
                        }
                        report.put("phase", "PRODUCTION_IMPORT")
                        // Production IO/core operations retain their own bounds; do not abandon an applied pool.
                        while (profiles.busy) SystemClock.sleep(100)
                        instrumentation.waitForIdleSync()
                        val saved = profiles.message == "Profile saved."
                        val feedback = when (profiles.message) {
                            "Profile saved." -> "SAVED"
                            "Unsupported profile URL. Use HTTP(S) or a Clash install link." -> "URL_REJECTED"
                            "Profile download failed. Existing data kept." -> "DOWNLOAD_FAILED"
                            "Unsupported or invalid profile. A usable YAML proxies list is required." -> "PARSER_REJECTED"
                            "Profile nodes were rejected. Existing data kept." -> "CORE_REJECTED"
                            "Profile change could not be applied. Existing data kept." -> "APPLY_FAILED"
                            "Profile change could not be saved. Existing data kept." -> "SAVE_FAILED"
                            else -> "OTHER"
                        }
                        report.put("feedbackCategory", feedback).put("importSaved", saved)
                        if (!saved) report.put("priorCacheUnchanged", previousCache == cache.getString("profiles", null))
                        writeReport()
                        assertTrue("Selected private source import saved; see fixed-category report", saved)
                        val fresh = ProfileStore({ cache.getString("profiles", null) }) { false }
                        val source = ProfileImport.source(payload)
                        val persisted = fresh.readable && fresh.profiles.any { it.source == source } &&
                            fresh.profiles.size == profiles.profiles.size && fresh.profiles.zip(profiles.profiles).all {
                                (a, b) -> a.source == b.source && a.name == b.name &&
                                    a.enabled == b.enabled && a.nodes == b.nodes
                            }
                        report.put("phase", "FRESH_READER").put("freshReaderExact", persisted)
                            .put("profileCount", fresh.profiles.size)
                            .put("enabledNodeCount", fresh.profiles.filter { it.enabled }.sumOf { it.nodes.size })
                        assertTrue("Real source/name/toggle/nodes persisted", persisted)
                        report.put("result", "IMPORT_AND_READER_PASSED")
                    }
                }
            }
        } catch (_: AssertionError) {
            report.put("result", "NOT_PASSED").put("failureCategory", "ASSERTION")
            writeReport()
            throw AssertionError("Private source import failed; see fixed-category report")
        } catch (_: Exception) {
            report.put("result", "NOT_PASSED").put("failureCategory", "OPERATION")
            writeReport()
            throw AssertionError("Private source import operation failed; see fixed-category report")
        } finally {
            if (ownsInspection) profiles.inspectDownload = null
        }
    }

    @Test fun ownerPresentedSubscriptionIsImportedThroughNormalCameraFlow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("realSubscriptionOptical") == "true")
        val invocation = UUID.fromString(checkNotNull(arguments.getString("invocationId"))).toString()
        val context = instrumentation.targetContext
        val runtime = RgProxyRuntime.get(context)
        val profiles = RgProfiles.get(context)
        val preferences = localBrowserTestPreferences(context)
        val previousTabs = preferences.getString("local.tabs", null)
        val profilePreferences = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
        val report = JSONObject().put("invocation", invocation).put("mode", "OWNER_REAL_QR_OPTICAL")
        val download = AtomicReference<JSONObject>()
        fun writeReport() {
            download.get()?.let { report.put("download", it) }
            File(context.filesDir, "issue44-real-results.json").writeText(report.toString(2))
        }
        fun await(label: String, seconds: Long = 45, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + seconds * 1000
            while (SystemClock.elapsedRealtime() < end) {
                if (condition()) return
                SystemClock.sleep(50)
            }
            fail(label)
        }
        check(profiles.inspectDownload == null)
        profiles.inspectDownload = { status, type, document -> download.set(inspect(status, type, document)) }
        Closeable {
            profiles.inspectDownload = null
            // The deliberate real profile stays saved. Only this check's tab metadata is restored.
            assertTrue("Starting tabs restored", preferences.edit().apply {
                if (previousTabs == null) remove("local.tabs") else putString("local.tabs", previousTabs)
            }.commit())
            assertTrue("Starting tabs match", preferences.getString("local.tabs", null) == previousTabs)
            report.put("startingTabsRestored", true)
            writeReport()
        }.use {
            Closeable { writeReport() }.use {
                ActivityScenario.launch(LocalBrowserActivity::class.java).use { scene ->
                    fun click(tag: String) {
                        instrumentation.waitForIdleSync()
                        scene.onActivity {
                            val target = checkNotNull(it.root.findViewWithTag<View>(tag))
                            assertTrue(target.isShown && target.isEnabled)
                            val point = IntArray(2); target.getLocationOnScreen(point)
                            assertTrue(it.input.activate(InputPoint(point[0] + target.width / 2f, point[1] + target.height / 2f)))
                        }
                        instrumentation.waitForIdleSync()
                    }
                    fun js(script: String): String {
                        var value = ""; val latch = CountDownLatch(1)
                        scene.onActivity { checkNotNull(it.tabs.current.session.page).evaluateJavascript(script) { result -> value = result; latch.countDown() } }
                        assertTrue(latch.await(3, TimeUnit.SECONDS)); return JSONTokener(value).nextValue() as? String ?: value
                    }
                    await("Foreground native/WebView proxy ready") { runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort }
                    val page = "http://100.92.81.33:39030/local-keyboard.html?issue44=$invocation"
                    scene.onActivity { assertTrue(it.tabs.current.session.open(page).accepted()) }
                    await("Owned fresh page loaded") { js("document.readyState") == "complete" && js("location.href") == page }
                    js("document.querySelector('#text').value='owned-live-retained-form';true")
                    scene.onActivity { it.showUtility(LocalBrowserActivity.Utility.SETTINGS) }
                    click("settings.proxy"); click("profile.add")
                    await("PROFILE camera has an actual frame") {
                        var ready = false
                        scene.onActivity { ready = it.cameraActive && it.cameraHasFrame &&
                            it.root.findViewWithTag<TextView>("qr.title")?.text == "Add a new profile" }
                        ready
                    }
                    File(context.filesDir, "issue44-real-ready.json").writeText(JSONObject()
                        .put("invocation", invocation).put("cameraActive", true).put("cameraHasFrame", true)
                        .put("flow", "Settings → Proxy → Add").put("title", "Add a new profile").toString(2))
                    report.put("cameraReadyObserved", true)
                    // This bounds the human presentation operation, not the mission or import work.
                    await("Owner camera presentation has not produced a result", 900) {
                        var returned = false; scene.onActivity { returned = it.utility != LocalBrowserActivity.Utility.QR }; returned
                    }
                    var decoded = false
                    scene.onActivity { decoded = it.utility == LocalBrowserActivity.Utility.PROXY && !it.cameraActive }
                    report.put("opticalResultReturnedToProxy", decoded)
                        .put("delivery", if (decoded) "ACTUAL_CAMERA_OWNER_PRESENTED" else "NO_RESULT")
                    assertTrue("PROFILE camera returned an optical result", decoded)
                    // Production core operations have their own bounds, proportional to pool size.
                    while (profiles.busy) SystemClock.sleep(100)
                    instrumentation.waitForIdleSync()
                    val imported = profiles.message == "Profile saved." && profiles.profiles.isNotEmpty()
                    report.put("importSaved", imported).put("feedback", profiles.message)
                    assertTrue("Real subscription import was not saved; see fixed feedback", imported)
                    val disk = ProfileStore({ profilePreferences.getString("profiles", null) }) { false }
                    val cached = disk.readable && disk.profiles.size == profiles.profiles.size &&
                        disk.profiles.zip(profiles.profiles).all { (a, b) ->
                            a.source == b.source && a.name == b.name && a.enabled == b.enabled && a.nodes == b.nodes
                        }
                    report.put("privateCacheRoundTrip", cached).put("profileCount", disk.profiles.size)
                        .put("enabledNodeCount", disk.profiles.filter { it.enabled }.sumOf { it.nodes.size })
                    assertTrue("Real source identity, name, toggle and nodes persisted", cached)
                    assertTrue("Import retained loaded page", js("location.href") == page)
                    assertTrue("Import retained form", js("document.querySelector('#text').value") == "owned-live-retained-form")
                    report.put("pageAndFormRetainedDuringImport", true)
                    val api = CoreControl(File(context.filesDir, "mihomo/control.sock").absolutePath)
                    fun pooledConnectionObserved(): Boolean = runCatching {
                        val connections = api.request("GET", "/connections").getJSONArray("connections")
                        (0 until connections.length()).any { index ->
                            val connection = connections.getJSONObject(index)
                            val chains = connection.getJSONArray("chains")
                            connection.getJSONObject("metadata").optString("host") == "example.com" &&
                                (0 until chains.length()).any { chains.getString(it).startsWith("n_") }
                        }
                    }.getOrDefault(false)
                    report.put("healthyCandidate", runCatching {
                        ProxyPolicy.hasHealthyCandidate(api.request("GET", "/providers/proxies/ENABLED"), ProxyPolicy.TEST_URL)
                    }.getOrDefault(false))
                    report.put("egressSelection", runCatching { api.request("GET", "/proxies/EGRESS").getString("now") }.getOrDefault("UNAVAILABLE"))
                    val connection = runtime.openConnection(URL("https://example.com/?issue44=$invocation"))
                    try {
                        report.put("nativeHttpStatus", connection.responseCode)
                        report.put("nativePooledConnectionObserved", pooledConnectionObserved())
                        connection.inputStream.use { it.readBytes() }
                    } catch (_: Exception) { report.put("nativeRequest", "FAILED") }
                    finally { connection.disconnect() }
                    scene.onActivity { it.dismissUtility(); assertTrue(it.tabs.current.session.open("https://example.com/?issue44=$invocation-browser").accepted()) }
                    val loaded = runCatching {
                        await("Fresh public WebView response", 90) { js("document.title") == "Example Domain" }; true
                    }.getOrDefault(false)
                    report.put("freshPublicWebViewLoaded", loaded).put("browserPooledConnectionObserved", pooledConnectionObserved())
                        .put("realProfileRetained", profilePreferences.getString("profiles", null) != null)
                    assertEquals("Actual native public response", 200, report.optInt("nativeHttpStatus", -1))
                    assertTrue("Actual fresh public WebView response", loaded)
                    assertTrue("Real node health", report.getBoolean("healthyCandidate"))
                    assertTrue("Actual pooled public connection", report.getBoolean("nativePooledConnectionObserved") ||
                        report.getBoolean("browserPooledConnectionObserved"))
                }
                await("Camera scenario released foreground runtime") { runtime.proxyPort == null && runtime.webViewPort == null }
                report.put("foregroundRuntimeReleased", true)
            }
        }
    }
}
