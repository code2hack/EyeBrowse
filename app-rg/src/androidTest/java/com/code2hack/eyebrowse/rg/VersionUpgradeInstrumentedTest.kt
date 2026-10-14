package com.code2hack.eyebrowse.rg

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.ProfileStore
import com.code2hack.eyebrowse.rg.proxy.RgRefresh
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Before/after install-r checks retain only opaque private-state digests and owned website markers. */
@RunWith(AndroidJUnit4::class)
class VersionUpgradeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val origin = "http://100.92.81.33:39030"
    private val baseline = File(context.filesDir, "issue46-upgrade-before.json")
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun privateState(): JSONObject {
        val result = JSONObject()
        for (name in listOf("LocalBrowserActivity", "local-input-settings", "proxy-profiles", "proxy-refresh")) {
            val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toSortedMap()
            result.put(name, digest(JSONObject(values).toString().toByteArray()))
        }
        for (path in listOf("local-browser/bookmarks.properties", "pairing/peer_trust.json")) {
            val file = File(context.filesDir, path)
            result.put(path, if (file.isFile) digest(file.readBytes()) else "ABSENT")
        }
        val profiles = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
        val store = ProfileStore({ profiles.getString("profiles", null) }) { false }
        assertTrue(store.readable)
        result.put("profileCount", store.profiles.size).put("nodeCount", store.profiles.sumOf { it.nodes.size })
        return result
    }
    private fun packageState(): JSONObject {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        return JSONObject().put("applicationId", context.packageName).put("versionName", info.versionName)
            .put("versionCode", info.longVersionCode).put("firstInstallTime", info.firstInstallTime)
            .put("signer", digest(info.signingInfo!!.apkContentsSigners.single().toByteArray()))
    }
    private fun assertEmptyWebsiteState() {
        val done = CountDownLatch(1)
        var cookies = true; var origins = -1
        instrumentation.runOnMainSync {
            cookies = CookieManager.getInstance().hasCookies()
            WebStorage.getInstance().getOrigins { origins = it.size; done.countDown() }
        }
        assertTrue("Website storage observed", done.await(5, TimeUnit.SECONDS))
        assertFalse("Fresh baseline has no cookies", cookies)
        assertEquals("Fresh baseline has no website origins", 0, origins)
    }
    private fun js(scene: ActivityScenario<NativeInputTestActivity>, script: String): String {
        val done = CountDownLatch(1); var value = ""
        scene.onActivity { it.page.evaluateJavascript(script) { result -> value = result; done.countDown() } }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return value
    }
    private fun ownedWebsite(write: Boolean) {
        ActivityScenario.launch(NativeInputTestActivity::class.java).use { scene ->
            scene.onActivity {
                it.pageReady = false
                it.page.settings.domStorageEnabled = true
                it.page.loadUrl("$origin/history.html")
            }
            val end = SystemClock.elapsedRealtime() + 15_000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < end) {
                scene.onActivity { ready = it.pageReady && it.page.url == "$origin/history.html" }
                SystemClock.sleep(20)
            }
            assertTrue("Owned website reached", ready)
            if (write) {
                assertEquals("true", js(scene, "localStorage.setItem('eyebrowse_i46_upgrade','owned-marker');true"))
                instrumentation.runOnMainSync {
                    CookieManager.getInstance().setCookie(origin, "eyebrowse_i46_upgrade=owned-marker; Max-Age=86400; SameSite=Lax")
                    CookieManager.getInstance().flush()
                }
            }
            assertEquals("true", js(scene, "localStorage.getItem('eyebrowse_i46_upgrade')==='owned-marker'"))
            assertTrue("Owned persistent cookie retained", CookieManager.getInstance().getCookie(origin)
                ?.split(';')?.any { it.trim() == "eyebrowse_i46_upgrade=owned-marker" } == true)
        }
    }
    @Test fun recordInstalledBaselineBeforeDataPreservingUpgrade() {
        assertFalse("Do not overwrite an unreviewed upgrade baseline", baseline.exists())
        val state = privateState()
        assertEquals(1, state.getInt("profileCount")); assertEquals(6, state.getInt("nodeCount"))
        val pkg = packageState()
        assertEquals(1L, pkg.getLong("versionCode"))
        ownedWebsite(true)
        assertEquals(state.toString(), privateState().toString())
        baseline.writeText(JSONObject().put("package", pkg).put("privateState", state)
            .put("ownedCookieAndLocalStorage", true).toString(2))
    }
    @Test fun upgradeRetainsIdentityPrivateDataAndOwnedWebsiteState() {
        val before = JSONObject(baseline.readText())
        val oldPackage = before.getJSONObject("package")
        val actual = packageState()
        assertEquals("com.code2hack.eyebrowse.rg", actual.getString("applicationId"))
        assertEquals("0.0.3", actual.getString("versionName"))
        assertTrue(actual.getLong("versionCode") > oldPackage.getLong("versionCode"))
        for (key in listOf("applicationId", "signer", "firstInstallTime"))
            assertEquals(oldPackage.get(key), actual.get(key))
        val state = privateState()
        assertEquals(before.getJSONObject("privateState").toString(), state.toString())
        ownedWebsite(false)
        assertEquals(state.toString(), privateState().toString())
        File(context.filesDir, "issue46-upgrade-after.json").writeText(JSONObject().put("package", actual)
            .put("privateState", state).put("privateValuesLogged", false).put("ownedCookieAndLocalStorage", true)
            .put("physicalFreshInstall", "NOT_RUN_OWNER_DATA_PRESERVED").toString(2))
    }
    @Test fun isolatedFreshInitializationUsesDefaultsAndKeepsSubsequentChoices() {
        val prefs = context.getSharedPreferences("issue46-fresh-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        assertTrue(prefs.all.isEmpty())
        fun load() = LocalInputSettings({ prefs.getString(it, null) }) { k, v -> prefs.edit().putString(k, v).commit() }
        var settings = load()
        assertEquals(20, settings.pointerTenths); assertEquals(240, settings.scrollSpeed)
        assertTrue(settings.stepPointer(1)); assertTrue(settings.stepScroll(-1))
        repeat(3) { settings = load(); assertEquals(21, settings.pointerTenths); assertEquals(230, settings.scrollSpeed) }
        File(context.filesDir, "issue46-fresh-initialization.json").writeText(JSONObject()
            .put("isolatedProductionStoreInitialization", true).put("physicalFreshInstall", false)
            .put("defaultTenths", 20).put("defaultScroll", 240).put("repeatedChoiceTenths", 21)
            .put("repeatedChoiceScroll", 230).toString(2))
    }

    /** Run first after the externally verified uninstall/absence/install, before owned fixtures. */
    @Test fun newlyInstalledProductionStateUsesDefaultsAndKeepsRepeatedChoices() {
        val freshBaseline = File(context.filesDir, "issue46-physical-fresh-baseline.json")
        assertFalse("Do not overwrite the observed fresh baseline", freshBaseline.exists())
        assertFalse("Former upgrade marker was discarded", baseline.exists())
        for (name in listOf("LocalBrowserActivity", "local-input-settings", "proxy-profiles", "proxy-refresh"))
            assertTrue("Fresh production preferences: $name", context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty())
        assertFalse(File(context.filesDir, "local-browser/bookmarks.properties").exists())
        assertFalse(File(context.filesDir, "pairing/peer_trust.json").exists())
        val before = privateState()
        assertEquals(0, before.getInt("profileCount")); assertEquals(0, before.getInt("nodeCount"))
        val pkg = packageState()
        assertEquals("com.code2hack.eyebrowse.rg", pkg.getString("applicationId"))
        assertEquals("0.0.3", pkg.getString("versionName")); assertEquals(3L, pkg.getLong("versionCode"))
        assertEmptyWebsiteState()
        ProxyRefreshInstrumentedTest().preserving("issue46-physical-fresh") {
            instrumentation.runOnMainSync { RgRefresh.get(context).testConnectivity = { false } }
            ActivityScenario.launch(LocalBrowserActivity::class.java).use { scene ->
                val deadline = SystemClock.elapsedRealtime() + 5_000
                var laidOut = false
                while (!laidOut && SystemClock.elapsedRealtime() < deadline) {
                    scene.onActivity { laidOut = it.root.width == 480 && it.root.height == 640 }
                    SystemClock.sleep(20)
                }
                assertTrue("Native fresh screen laid out", laidOut)
                scene.onActivity {
                    assertEquals(480, it.root.width); assertEquals(640, it.root.height)
                    assertEquals(1, it.tabs.count); assertEquals("", it.tabs.current.committedUrl)
                    assertEquals("", it.tabs.current.recoveryUrl); assertTrue(it.bookmarks.entries.isEmpty())
                    assertTrue(it.profiles.profiles.isEmpty())
                    assertEquals(20, it.inputSettings.pointerTenths); assertEquals(240, it.inputSettings.scrollSpeed)
                    it.showUtility(LocalBrowserActivity.Utility.SETTINGS)
                    assertEquals("2.0×", it.root.findViewWithTag<TextView>("settings.pointer.value").text.toString())
                    assertEquals("240 px/s", it.root.findViewWithTag<TextView>("settings.scroll.value").text.toString())
                }
                instrumentation.waitForIdleSync()
                val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    assertEquals(480, image.width); assertEquals(640, image.height)
                    File(context.filesDir, "issue46-physical-fresh-settings.png").outputStream().use {
                        assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { image.recycle() }
                scene.onActivity { assertTrue(it.inputSettings.stepPointer(1)); assertTrue(it.inputSettings.stepScroll(-1)) }
                repeat(3) {
                    scene.recreate()
                    scene.onActivity { assertEquals(21, it.inputSettings.pointerTenths); assertEquals(230, it.inputSettings.scrollSpeed) }
                }
            }
        }
        assertEquals("New fresh baseline restored after all closures", before.toString(), privateState().toString())
        assertEmptyWebsiteState()
        freshBaseline.writeText(JSONObject().put("package", pkg).put("privateState", before)
            .put("productionDefaultTenths", 20).put("productionDefaultScroll", 240)
            .put("repeatedChoiceTenths", 21).put("repeatedChoiceScroll", 230).put("activityRecreations", 3)
            .put("cookieCount", 0).put("websiteOriginCount", 0)
            .put("startingFreshStateRestored", true).put("formerRealProfileRestored", false).toString(2))
    }

    @Test fun controlledChecksKeepTheObservedNewFreshBaseline() {
        val before = JSONObject(File(context.filesDir, "issue46-physical-fresh-baseline.json").readText())
        assertEquals(before.getJSONObject("package").toString(), packageState().toString())
        assertEquals(before.getJSONObject("privateState").toString(), privateState().toString())
        assertEmptyWebsiteState()
        File(context.filesDir, "issue46-physical-fresh-integrity.json").writeText(JSONObject()
            .put("newFreshBaselineUnchanged", true).put("profiles", 0).put("nodes", 0)
            .put("formerRealProfileRestored", false).toString(2))
    }

    private fun websiteState(): JSONObject {
        val done = CountDownLatch(1)
        val result = JSONObject()
        instrumentation.runOnMainSync {
            result.put("hasCookies", CookieManager.getInstance().hasCookies())
                .put("ownedOriginCookieSHA256", CookieManager.getInstance().getCookie(origin)
                    ?.let { digest(it.toByteArray()) } ?: "ABSENT")
            WebStorage.getInstance().getOrigins { origins ->
                result.put("originCount", origins.size)
                    .put("originSetSHA256", digest(origins.keys.sorted().joinToString("\n").toByteArray()))
                done.countDown()
            }
        }
        assertTrue("Opaque website state observed", done.await(5, TimeUnit.SECONDS))
        return result
    }

    @Test fun recordCurrentOwnerStateForControlledChecks() {
        val file = File(context.filesDir, "issue46-current-owner-baseline.json")
        assertFalse("Do not overwrite the current Owner baseline", file.exists())
        val fresh = JSONObject(File(context.filesDir, "issue46-physical-fresh-baseline.json").readText())
        val pkg = packageState()
        assertEquals("Same installation after Owner use", fresh.getJSONObject("package").toString(), pkg.toString())
        file.writeText(JSONObject().put("package", pkg).put("privateState", privateState())
            .put("websiteState", websiteState()).put("privateValuesLogged", false).toString(2))
    }

    @Test fun controlledChecksKeepCurrentOwnerState() {
        val before = JSONObject(File(context.filesDir, "issue46-current-owner-baseline.json").readText())
        assertEquals(before.getJSONObject("package").toString(), packageState().toString())
        assertEquals(before.getJSONObject("privateState").toString(), privateState().toString())
        assertEquals(before.getJSONObject("websiteState").toString(), websiteState().toString())
        File(context.filesDir, "issue46-current-owner-integrity.json").writeText(JSONObject()
            .put("currentOwnerStateUnchanged", true).put("privateValuesLogged", false).toString(2))
    }
}
