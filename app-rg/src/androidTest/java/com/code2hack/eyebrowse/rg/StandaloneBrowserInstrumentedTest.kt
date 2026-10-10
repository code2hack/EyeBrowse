package com.code2hack.eyebrowse.rg

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.webkit.CookieManager
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.code2hack.eyebrowse.rg.qr.CameraQrScanner
import com.code2hack.eyebrowse.rg.qr.QrDecoder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Ordinary reserved-RG tests. Raster decoding is supplemental, never optical camera evidence. */
@RunWith(AndroidJUnit4::class)
class StandaloneBrowserInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val base = checkNotNull(InstrumentationRegistry.getArguments().getString("fixtureBaseUrl")).trimEnd('/')

    private fun await(label: String, ms: Long = 10_000, check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + ms
        while (SystemClock.elapsedRealtime() < end) { if (check()) return; SystemClock.sleep(20) }
        fail(label)
    }
    private fun scene(body: (ActivityScenario<LocalBrowserActivity>) -> Unit) {
        val preferences = context.getSharedPreferences("LocalBrowserActivity", Context.MODE_PRIVATE)
        val previous = preferences.getString("local.tabs", null)
        val scenario = ActivityScenario.launch(LocalBrowserActivity::class.java)
        try {
            await("production window focused") { var yes = false; scenario.onActivity { yes = it.hasWindowFocus() }; yes }
            scenario.onActivity { while (it.tabs.count > 1) it.closeTab(); it.closeTab() }
            body(scenario)
        } finally {
            scenario.close()
            assertTrue(preferences.edit().apply {
                if (previous == null) remove("local.tabs") else putString("local.tabs", previous)
            }.commit())
        }
    }
    private fun tap(s: ActivityScenario<LocalBrowserActivity>, find: (LocalBrowserActivity) -> View) {
        await("current native control ready") {
            var yes = false; s.onActivity { val v = find(it); yes = v.isShown && v.width > 0 && !it.root.isLayoutRequested }; yes
        }
        s.onActivity {
            val v = find(it); val xy = IntArray(2); val origin = IntArray(2)
            v.getLocationOnScreen(xy); it.root.getLocationOnScreen(origin)
            assertTrue(it.input.activate(InputPoint(xy[0] - origin[0] + v.width / 2f, xy[1] - origin[1] + v.height / 2f)))
        }
    }
    private fun tag(s: ActivityScenario<LocalBrowserActivity>, name: String) = tap(s) { checkNotNull(it.root.findViewWithTag(name)) }
    private fun hud(s: ActivityScenario<LocalBrowserActivity>, name: String) = tap(s) { it.controls.getValue("hud.$name") }
    private fun scan(s: ActivityScenario<LocalBrowserActivity>) { hud(s, "more"); tag(s, "menu.2") }
    private fun ready(s: ActivityScenario<LocalBrowserActivity>) = await("local page presented") {
        var yes = false; s.onActivity { yes = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.READY }; yes
    }
    private fun open(s: ActivityScenario<LocalBrowserActivity>, url: String) {
        hud(s, "address"); s.onActivity { it.address.setText(url) }
        tap(s) { it.keyButtons.getValue(RgKeyboardKeys.Key.Command.ENTER) }; ready(s)
    }
    private fun js(s: ActivityScenario<LocalBrowserActivity>, script: String): String {
        val done = CountDownLatch(1); var value = ""
        s.onActivity { checkNotNull(it.tabs.current.session.page).evaluateJavascript(script) { result -> value = result; done.countDown() } }
        assertTrue(done.await(3, TimeUnit.SECONDS)); return value
    }
    private fun capture(s: ActivityScenario<LocalBrowserActivity>, name: String) {
        instrumentation.waitForIdleSync()
        s.onActivity { assertEquals(480, it.root.width); assertEquals(640, it.root.height); assertEquals(48, it.toolbar.height) }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        assertEquals(480, image.width); assertEquals(640, image.height)
        // Predeclared black toolbar interiors; avoid outlines/glyphs and the cursor center.
        for (x in listOf(4, 52, 100, 148, 324, 436)) assertEquals("toolbar interior $x", Color.BLACK, image.getPixel(x, 44))
        context.openFileOutput("standalone-$name.png", 0).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun raster(value: String): String {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 256, 256)
        val bytes = ByteArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) 0 else 0xff.toByte() }
        return checkNotNull(QrDecoder.decodeYPlane(bytes, matrix.width, matrix.width, matrix.height))
    }
    private fun allowCamera() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) return
        val allow = device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_allow_foreground_only_button")), 5_000)
        assertNotNull("real Android runtime permission prompt", allow); checkNotNull(allow).click()
    }

    @Test fun launcherAndAllFourMenuItemsUseTheLocalBrowser() = scene { s ->
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        assertEquals(LocalBrowserActivity::class.java.name, context.packageManager.resolveActivity(launcher, 0)?.activityInfo?.name)
        val activities = checkNotNull(context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities).map { it.name }
        assertFalse(activities.any { it.endsWith("MainActivity") || it.contains("Pairing") || it.contains("Preparation") })
        hud(s, "more")
        s.onActivity { a -> assertEquals(listOf("Add new tab", "Bookmarks", "QR scan", "Settings"), (0..3).map {
            a.root.findViewWithTag<android.widget.TextView>("menu.$it").text.toString()
        }) }
        capture(s, "more")
        tag(s, "menu.0"); s.onActivity { assertEquals(2, it.tabs.count); assertTrue(it.keyboard.isShown) }
        hud(s, "more"); tag(s, "menu.1"); capture(s, "bookmarks")
        tag(s, "utility.done"); scan(s); allowCamera(); tag(s, "qr.cancel")
        hud(s, "more"); tag(s, "menu.3"); capture(s, "settings"); tag(s, "utility.done")
        capture(s, "local-launch")
    }

    @Test fun decodedUrlRequiresDeliberateOpenInSelectedTabAndCancelDoesNotNavigate() = scene { s ->
        open(s, "$base/keyboard.html")
        val first = js(s, "fixtureIdentity")
        hud(s, "more"); tag(s, "menu.0"); open(s, "$base/history.html")
        scan(s); allowCamera()
        s.onActivity { it.previewQr(raster("$base/author-light.html")); assertFalse(it.cameraActive); assertEquals(1, it.tabs.selectedIndex) }
        capture(s, "qr-preview-raster")
        s.onActivity { assertTrue(it.tabs.current.session.state.url.endsWith("history.html")) }
        tag(s, "qr.cancel")
        s.onActivity { assertTrue(it.tabs.current.session.state.url.endsWith("history.html")) }
        scan(s); s.onActivity { it.previewQr(raster("$base/author-light.html")) }
        tag(s, "qr.open"); ready(s)
        s.onActivity { assertEquals(1, it.tabs.selectedIndex); assertTrue(it.tabs.current.session.state.url.endsWith("author-light.html")); it.selectTab(0) }
        assertEquals(first, js(s, "fixtureIdentity"))
        for (invalid in listOf("javascript:alert(1)", "intent://example", "file:///sdcard/private", "not a web address")) {
            scan(s); s.onActivity {
                it.previewQr(raster(invalid)); assertNull(it.root.findViewWithTag<View>("qr.open")); assertFalse(it.cameraActive)
            }
            capture(s, "qr-invalid-raster"); tag(s, "qr.cancel")
            assertEquals(first, js(s, "fixtureIdentity"))
        }
    }

    @Test fun realCameraFramesReleaseOnCancelPauseAndUtilityExit() = scene { s ->
        val manager = context.getSystemService(CameraManager::class.java)
        val available = ConcurrentHashMap<String, Boolean>()
        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(id: String) { available[id] = true }
            override fun onCameraUnavailable(id: String) { available[id] = false }
        }
        manager.registerAvailabilityCallback(callback, Handler(Looper.getMainLooper()))
        try {
            repeat(3) { round ->
                scan(s); allowCamera()
                await("actual RG camera analyzer frame") { var yes = false; s.onActivity { yes = it.cameraHasFrame }; yes }
                await("camera in actual use") { available.values.any { !it } }
                val owned = available.filterValues { !it }.keys.toList()
                capture(s, "real-camera-$round")
                when (round) {
                    0 -> tag(s, "qr.cancel")
                    1 -> {
                        s.moveToState(Lifecycle.State.CREATED)
                        s.onActivity { assertFalse(it.cameraActive); assertFalse(it.pointer.sourceRegistered) }
                        s.moveToState(Lifecycle.State.RESUMED)
                        await("resumed window") { var yes = false; s.onActivity { yes = it.hasWindowFocus() }; yes }
                        capture(s, "qr-paused"); tag(s, "qr.cancel")
                    }
                    else -> { hud(s, "tab_counter"); tag(s, "utility.done") }
                }
                await("owned camera released", 2_000) { owned.all { available[it] == true } }
                s.onActivity { assertFalse(it.cameraActive) }
            }
        } finally { manager.unregisterAvailabilityCallback(callback) }
    }

    @Test fun cameraBindFailureReportsAnErrorAndReleasesResources() = scene { s ->
        val failed = CountDownLatch(1)
        var scanner: CameraQrScanner? = null
        try {
            s.onActivity {
                scanner = CameraQrScanner(it, it, { fail("no camera selected") }, { fail("no camera selected") },
                    { failed.countDown() }, CameraSelector.Builder().addCameraFilter { emptyList() }.build())
                scanner!!.start(PreviewView(it))
            }
            assertTrue("unavailable-camera error callback", failed.await(10, TimeUnit.SECONDS))
        } finally { s.onActivity { scanner?.cancel() } }
    }

    /** Host revokes only this app's CAMERA before this method; real dialog Deny then Allow restores it. */
    @Test fun deniedCameraPermissionOffersRetryAndActualPermissionRecovery() = scene { s ->
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA))
        scan(s)
        val deny = device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_deny_button")), 5_000)
        assertNotNull("actual Android permission dialog", deny); checkNotNull(deny).click()
        await("permission-denied panel") {
            var yes = false; s.onActivity { yes = it.root.findViewWithTag<android.widget.TextView>("qr.message")?.text?.contains("denied") == true }; yes
        }
        capture(s, "qr-permission-denied")
        s.onActivity { assertFalse(it.cameraActive); assertNull(it.root.findViewWithTag<View>("qr.open")) }
        tag(s, "qr.retry"); allowCamera()
        await("actual camera frame after permission recovery") { var yes = false; s.onActivity { yes = it.cameraHasFrame }; yes }
        tag(s, "qr.cancel")
    }

    @Test fun activityRecreationPreservesDurableDataAndNeverReplaysAForm() = scene { s ->
        open(s, "$base/local-keyboard.html")
        val before = js(s, "fixtureIdentity")
        assertEquals("true", js(s, "(()=>{localStorage.setItem('issue33-owned','durable');document.cookie='issue33=durable; path=/; Max-Age=3600';return localStorage.getItem('issue33-owned')==='durable'})()"))
        s.onActivity { CookieManager.getInstance().flush() }
        s.recreate()
        await("recreated native window") { var yes = false; s.onActivity { yes = it.hasWindowFocus() }; yes }
        s.onActivity {
            assertEquals(LocalBrowserSession.Phase.EMPTY, it.tabs.current.session.state.phase)
            assertEquals("$base/local-keyboard.html", it.tabs.current.recoveryUrl)
            assertFalse(it.keyboard.isShown); assertFalse(it.cameraActive)
        }
        capture(s, "interrupted-recreation")
        open(s, "$base/local-keyboard.html")
        assertNotEquals(before, js(s, "fixtureIdentity"))
        assertEquals("\"durable\"", js(s, "localStorage.getItem('issue33-owned')"))
        assertEquals("true", js(s, "document.cookie.includes('issue33=durable')"))
        assertEquals("0", js(s, "fixtureSubmits"))
        js(s, "localStorage.removeItem('issue33-owned');document.cookie='issue33=; path=/; Max-Age=0'")
    }

    /** Host stops/restarts only its reserved fixture on these two explicit status messages. */
    @Test fun networkInterruptionKeepsLiveTabAndLocalUtilitiesRecoverable() = scene { s ->
        open(s, "$base/local-keyboard.html")
        val identity = js(s, "fixtureIdentity")
        hud(s, "more"); tag(s, "menu.0")
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "ISSUE33_STOP_OWN_FIXTURE\n") })
        // The host closes the listener; bounded probes observe that outcome without mutating network settings.
        await("owned fixture stopped", 15_000) {
            try { java.net.URL("$base/api/observations").openConnection().apply { connectTimeout = 200; readTimeout = 200 }.getInputStream().use { it.read() }; false }
            catch (_: java.io.IOException) { true }
        }
        hud(s, "address"); s.onActivity { it.address.setText("$base/history.html") }
        tap(s) { it.keyButtons.getValue(RgKeyboardKeys.Key.Command.ENTER) }
        await("actual network request error") {
            var yes = false; s.onActivity { yes = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.ERROR }; yes
        }
        capture(s, "network-error")
        hud(s, "tab_counter"); tag(s, "tab.0")
        assertEquals(identity, js(s, "fixtureIdentity")); assertEquals("0", js(s, "fixtureSubmits"))
        hud(s, "more"); tag(s, "menu.1"); tag(s, "utility.done")
        hud(s, "more"); tag(s, "menu.3"); tag(s, "utility.done")
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "ISSUE33_RESTART_OWN_FIXTURE\n") })
        await("owned fixture available again", 15_000) {
            try { java.net.URL("$base/api/observations").openConnection().apply { connectTimeout = 200; readTimeout = 200 }.getInputStream().use { it.read() }; true }
            catch (_: java.io.IOException) { false }
        }
        hud(s, "tab_counter"); tag(s, "tab.1"); hud(s, "refresh"); ready(s)
        capture(s, "network-explicit-recovery")
    }

    /** Separate invocations with an ordinary force-stop/install-r between them prove cold durability. */
    @Test fun prepareOwnedColdRecoveryData() {
        val metadata = context.getSharedPreferences("LocalBrowserActivity", Context.MODE_PRIVATE)
        val settings = context.getSharedPreferences("local-input-settings", Context.MODE_PRIVATE)
        val before = JSONObject().put("tabs", metadata.getString("local.tabs", null))
            .put("pointer.sensitivity", settings.getString("pointer.sensitivity", null))
            .put("edge.speed", settings.getString("edge.speed", null))
        val record = java.io.File(context.filesDir, "issue33-owned-cold-before.json")
        check(!record.exists()) { "Resolve existing owned cold-recovery run before writing" }
        record.writeText(before.toString())
        ActivityScenario.launch(LocalBrowserActivity::class.java).use { s ->
            s.onActivity { while (it.tabs.count > 1) it.closeTab(); it.closeTab() }
            open(s, "$base/local-keyboard.html?issue33=upgrade")
            assertEquals("true", js(s, "(()=>{localStorage.setItem('issue33-upgrade','durable');document.cookie='issue33upgrade=durable; path=/; Max-Age=3600';return true})()"))
            hud(s, "bookmark")
            hud(s, "more"); tag(s, "menu.3")
            tag(s, "settings.sensitivity.high"); tag(s, "settings.speed.fast"); tag(s, "utility.done")
            s.onActivity { CookieManager.getInstance().flush(); assertTrue(it.bookmarks.contains("$base/local-keyboard.html?issue33=upgrade")) }
            capture(s, "before-cold-restart")
        }
    }

    @Test fun coldRestartAndDataPreservingInstallKeepDurableDataWithoutLoadingOrSubmitting() {
        val record = java.io.File(context.filesDir, "issue33-owned-cold-before.json")
        val before = JSONObject(record.readText())
        val metadata = context.getSharedPreferences("LocalBrowserActivity", Context.MODE_PRIVATE)
        val settings = context.getSharedPreferences("local-input-settings", Context.MODE_PRIVATE)
        try {
            ActivityScenario.launch(LocalBrowserActivity::class.java).use { s ->
                s.onActivity {
                    assertEquals(1, it.tabs.count)
                    assertEquals(LocalBrowserSession.Phase.EMPTY, it.tabs.current.session.state.phase)
                    assertEquals("$base/local-keyboard.html?issue33=upgrade", it.tabs.current.recoveryUrl)
                    assertTrue(it.bookmarks.contains(it.tabs.current.recoveryUrl))
                    assertEquals(LocalInputSettings.Sensitivity.HIGH, it.inputSettings.sensitivity)
                    assertEquals(LocalInputSettings.Speed.FAST, it.inputSettings.speed)
                }
                capture(s, "cold-recovery-no-replay")
                open(s, "$base/local-keyboard.html?issue33=upgrade")
                assertEquals("\"durable\"", js(s, "localStorage.getItem('issue33-upgrade')"))
                assertEquals("true", js(s, "document.cookie.includes('issue33upgrade=durable')"))
                assertEquals("0", js(s, "fixtureSubmits"))
                capture(s, "durable-restored-explicit-open")
                hud(s, "bookmark")
                js(s, "localStorage.removeItem('issue33-upgrade');document.cookie='issue33upgrade=; path=/; Max-Age=0'")
            }
        } finally {
            assertTrue(metadata.edit().apply {
                if (before.has("tabs")) putString("local.tabs", before.getString("tabs")) else remove("local.tabs")
            }.commit())
            assertTrue(settings.edit().apply {
                for (key in listOf("pointer.sensitivity", "edge.speed")) {
                    if (before.has(key)) putString(key, before.getString(key)) else remove(key)
                }
            }.commit())
            assertTrue(record.delete())
        }
    }
}
