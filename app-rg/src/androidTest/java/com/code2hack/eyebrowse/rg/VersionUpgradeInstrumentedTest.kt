package com.code2hack.eyebrowse.rg

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.webkit.CookieManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.ProfileStore
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
}
