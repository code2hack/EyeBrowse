package com.code2hack.eyebrowse.phone

import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
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

/** Run prepare, externally force-stop ONLY the Phone app, then verify with the same missionId.
 * The receipt belongs to the test APK and contains fixture/identity metadata, never form drafts.
 */
@RunWith(AndroidJUnit4::class)
class ColdRecoveryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val mission = java.util.UUID.fromString(checkNotNull(args.getString("missionId"))).toString()
    private val base = checkNotNull(args.getString("fixtureBaseUrl")).trimEnd('/')
    private val receipt = instrumentation.context.getSharedPreferences("i11-$mission", Context.MODE_PRIVATE)
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

    @Test fun prepareRealProcessLossAfterFixturePost() {
        val browser = PhoneBrowserSession.get(app)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        try {
            scenario.onActivity { browser.openAddress("$base/form.html") }
            StopRecoveryAssertions.await("fixture ready", 10_000) { browser.isLive() && !browser.isLoading() && browser.lastCommittedUrl() == "$base/form.html" }
            val before = posts()
            // Setup through the real WebView HTTP stack; this is not a keyboard/input-path claim.
            scenario.onActivity { browser.view()!!.postUrl("$base/submit", "test_id=fixture-post-1&message=harmless".toByteArray()) }
            StopRecoveryAssertions.await("POST response committed", 10_000) { !browser.isLoading() && browser.lastCommittedUrl() == "$base/submit" }
            assertEquals(before + 1, posts())
            assertEquals("true", js(browser, "localStorage.setItem('$storageKey','persisted');document.cookie='$cookieName=present; Max-Age=86400; Path=/';window.i11Transient='unsaved';true"))
            scenario.onActivity { browser.flushCookies() }
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
            scenario.onActivity { it.findViewById<Button>(R.id.button_open).performClick() }
            StopRecoveryAssertions.await("explicit GET recovery", 10_000) { browser.isLive() && !browser.isLoading() && browser.lastCommittedUrl() == "$base/submit" }
            assertEquals("explicit Open must not resubmit POST", expectedPosts, posts())
            assertEquals("\"persisted\"", js(browser, "localStorage.getItem('$storageKey')"))
            assertEquals("true", js(browser, "document.cookie.split(';').some(v=>v.trim()==='$cookieName=present')"))
            assertEquals("\"undefined\"", js(browser, "typeof window.i11Transient"))
            assertEquals("\"\"", js(browser, "document.getElementById('text-field').value"))
            assertEquals("true", js(browser, "localStorage.removeItem('$storageKey');document.cookie='$cookieName=; Max-Age=0; Path=/';true"))
            scenario.onActivity { browser.flushCookies() }
            assertTrue(receipt.edit().clear().commit())
            android.util.Log.i("EyeBrowseRecovery", "COLD_PASS mission=$mission pid=${Process.myPid()} posts=$expectedPosts noAutoLoad=true noPostReplay=true siteStorage=true")
        } finally { scenario.close() }
    }
}
