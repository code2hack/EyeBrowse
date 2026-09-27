package com.code2hack.eyebrowse.phone

import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import java.io.File
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Run graceful establishment, prepare, app-scoped force-stop, verify, then scoped cleanup.
 * The test-only receipt uses the target process's private storage and contains no form drafts.
 */
@RunWith(AndroidJUnit4::class)
class ColdRecoveryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val mission = java.util.UUID.fromString(checkNotNull(args.getString("missionId"))).toString()
    private val base = checkNotNull(args.getString("fixtureBaseUrl")).trimEnd('/')
    private val receipt = app.getSharedPreferences("i11-$mission", Context.MODE_PRIVATE)
    private val storageKey = "i11-$mission"
    private val cookieName = "i11_" + mission.replace("-", "")
    private fun js(browser: PhoneBrowserSession, script: String) = BrowserControlJourneyTest().js(browser, script)
    private fun posts(): Int {
        val connection = URL("$base/api/observations").openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000; connection.readTimeout = 2_000
        return try {
            val requests = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("requests")
            (0 until requests.length()).count {
                val r = requests.getJSONObject(it)
                r.optString("method") == "POST" && r.optString("path") == "/submit"
            }
        } finally { connection.disconnect() }
    }

    private fun navigate(scenario: ActivityScenario<MainActivity>, browser: PhoneBrowserSession,
                         path: String, title: String, action: (MainActivity) -> Unit) {
        val url = base + path
        val barrier = FixtureNavigationBarrier(browser.documentIdentity(), url, title)
        scenario.onActivity(action)
        StopRecoveryAssertions.await("fresh $path document", 10_000) {
            barrier.isReady(FixtureNavigationBarrier.Observation(browser.documentIdentity(),
                browser.displayUrl(), browser.lastCommittedUrl(), browser.pageTitle(),
                browser.isLoading(), browser.isLive(), browser.errorMessage()))
        }
        assertEquals("true", js(browser, "location.href===${JSONObject.quote(url)}&&document.readyState==='complete'&&window.fixtureInfo.path===${JSONObject.quote(path)}"))
    }

    private fun assertStored(browser: PhoneBrowserSession) {
        assertEquals("\"persisted\"", js(browser, "localStorage.getItem('$storageKey')"))
        assertEquals("true", js(browser, "document.cookie.split(';').some(v=>v.trim()==='$cookieName=present')"))
    }

    @Test fun gracefulBackgroundPersistsStorageBeforeProcessLoss() {
        val browser = PhoneBrowserSession.get(app)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        val barrier = File(app.cacheDir, "i11-$mission.persistence").apply { writeText("starting") }
        try {
            navigate(scenario, browser, "/form.html", "Form") { browser.openAddress("$base/form.html") }
            assertEquals("true", js(browser, "localStorage.setItem('$storageKey','persisted');document.cookie='$cookieName=present; Max-Age=86400; Path=/';true"))
            assertStored(browser)
            val started = SystemClock.elapsedRealtime()
            // Ordinary MainActivity.onStop flushes cookies; localStorage uses WebView's own
            // background persistence. The host ONLY observes the journal; it never writes it.
            scenario.moveToState(Lifecycle.State.CREATED)
            barrier.writeText("backgrounded")
            val deadline = started + 20_000
            while (barrier.readText() != "durable" && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
            assertEquals("host must verify the exact mission value in a complete journal record", "durable", barrier.readText())
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            assertStored(browser)
            assertTrue(receipt.edit().putLong("gracefulProcessStart", Process.getStartElapsedRealtime()).putBoolean("durableEstablished", true).commit())
            android.util.Log.i("EyeBrowseRecovery", "GRACEFUL_PASS mission=$mission boundary=Activity_onStop recreated=true persistenceMs=${SystemClock.elapsedRealtime()-started}")
        } finally { barrier.delete(); scenario.close() }
    }

    @Test fun prepareRealProcessLossAfterFixturePost() {
        assertTrue("graceful durable baseline required", receipt.getBoolean("durableEstablished", false))
        assertNotEquals("graceful restart requires a new process", receipt.getLong("gracefulProcessStart", -1), Process.getStartElapsedRealtime())
        val browser = PhoneBrowserSession.get(app)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        try {
            navigate(scenario, browser, "/form.html", "Form") { browser.openAddress("$base/form.html") }
            assertStored(browser) // Both values survived the actual restart after orderly backgrounding.
            android.util.Log.i("EyeBrowseRecovery", "GRACEFUL_RESTART_PASS mission=$mission cookie=true localStorage=true")
            val before = posts()
            // Setup through the real WebView HTTP stack; this is not a keyboard/input-path claim.
            navigate(scenario, browser, "/submit", "Submitted") {
                browser.view()!!.postUrl("$base/submit", "test_id=fixture-post-1&message=harmless".toByteArray())
            }
            assertEquals(before + 1, posts())
            assertEquals("true", js(browser, "window.i11Transient='unsaved';true"))
            val lifetime = PhoneLinkServer.obtain(app).controlCoordinator.authority.snapshot().context.lifetimeId
            assertTrue(receipt.edit().putLong("processStart", Process.getStartElapsedRealtime())
                .putInt("pid", Process.myPid()).putString("lifetime", lifetime)
                .putString("url", "$base/submit").putInt("posts", before + 1)
                .putBoolean("paired", PhoneLinkServer.obtain(app).isPaired()).commit())
            android.util.Log.i("EyeBrowseRecovery", "COLD_PREPARED mission=$mission pid=${Process.myPid()} lifetime=$lifetime posts=${before + 1}")
        } finally { scenario.close() }
    }

    @Test fun newProcessOffersSavedAddressWithoutReplayingPost() {
        assertTrue("prepare receipt required", receipt.contains("processStart"))
        assertNotEquals("actual new process required", receipt.getLong("processStart", -1), Process.getStartElapsedRealtime())
        val browser = PhoneBrowserSession.get(app)
        val link = PhoneLinkServer.obtain(app)
        val expectedPosts = receipt.getInt("posts", -1)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        try {
            scenario.onActivity {
                assertFalse("no fabricated live document", browser.isLive())
                assertEquals(receipt.getString("url", null), browser.displayUrl())
                assertEquals(it.getString(R.string.status_recovery), it.findViewById<TextView>(R.id.status_text).text.toString())
                assertEquals(HostingController.State.NOT_HOSTING, HostingController.get(app).status().state)
            }
            assertNotEquals(receipt.getString("lifetime", null), link.controlCoordinator.authority.snapshot().context.lifetimeId)
            assertEquals(receipt.getBoolean("paired", false), link.isPaired())
            assertEquals("no automatic POST replay", expectedPosts, posts())
            navigate(scenario, browser, "/submit", "Form") { it.findViewById<Button>(R.id.button_open).performClick() }
            assertEquals("explicit Open must not resubmit POST", expectedPosts, posts())
            assertStored(browser)
            assertEquals("\"undefined\"", js(browser, "typeof window.i11Transient"))
            assertEquals("\"\"", js(browser, "document.getElementById('text-field').value"))
            android.util.Log.i("EyeBrowseRecovery", "COLD_PASS mission=$mission pid=${Process.myPid()} posts=$expectedPosts noAutoLoad=true noPostReplay=true siteStorage=true")
        } finally { scenario.close() }
    }

    /** Independent cleanup row runs even when a preceding verification fails; never a retry. */
    @Test fun cleanupOwnedMissionState() {
        val browser = PhoneBrowserSession.get(app)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        val priorMissions = args.getString("cleanupMissionIds", "").split(',').filter { it.isNotBlank() }
            .map { java.util.UUID.fromString(it).toString() }
        try {
            navigate(scenario, browser, "/form.html", "Form") { browser.openAddress("$base/form.html") }
            for (ownedMission in (listOf(mission) + priorMissions).distinct()) {
                val key = "i11-$ownedMission"
                val cookie = "i11_" + ownedMission.replace("-", "")
                val before = js(browser, "({local:localStorage.getItem('$key')!==null,cookie:document.cookie.split(';').some(v=>v.trim().startsWith('$cookie='))})")
                assertEquals("true", js(browser, "if(localStorage.getItem('$key')!==null)localStorage.removeItem('$key');document.cookie='$cookie=; Max-Age=0; Path=/';localStorage.getItem('$key')===null&&!document.cookie.split(';').some(v=>v.trim().startsWith('$cookie='))"))
                val receiptFile = File(app.applicationInfo.dataDir, "shared_prefs/i11-$ownedMission.xml")
                val receiptExisted = receiptFile.exists()
                assertTrue("remove only the owned test receipt", app.deleteSharedPreferences("i11-$ownedMission"))
                assertFalse(receiptFile.exists())
                android.util.Log.i("EyeBrowseRecovery", "CLEANUP mission=$mission owned=$ownedMission before=$before localAbsent=true cookieAbsent=true receiptExisted=$receiptExisted receiptAbsent=true")
            }
            scenario.onActivity { browser.flushCookies() }
        } finally { scenario.close() }
    }

}
