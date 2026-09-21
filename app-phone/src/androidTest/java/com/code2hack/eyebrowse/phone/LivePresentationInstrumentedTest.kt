package com.code2hack.eyebrowse.phone

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Paired physical-device fixture companion. No trust/data mutation or production test receiver. */
@RunWith(AndroidJUnit4::class)
class LivePresentationInstrumentedTest {
    @Test fun measuredRgProfileSurvivesPhoneConfigurationAndStopsCleanly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val browser = PhoneBrowserSession.get(app)
        val host = HostingController.get(app)
        val server = PhoneLinkServer.obtain(app)
        var originalView: android.webkit.WebView? = null
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        try {
            scenario.onActivity {
                originalView = browser.view()
                browser.openAddress(InstrumentationRegistry.getArguments().getString("fixtureBaseUrl", "http://127.0.0.1:26341") + "/hosting.html")
                server.start()
            }
            await("fixture ready", 10_000) { !browser.isLoading() && browser.pageTitle() == "Hosting capture page" }
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("hosting active", 5_000) { host.status().state == HostingController.State.HOSTING }
            Log.i("EyeBrowseT02", "PHONE_READY")
            await("explicit RG request and capture", 30_000) { host.isRgPresentationOwned() && host.status().captureActive }
            val profile = host.presentationProfile()
            val context = server.controlCoordinator.authority.snapshot().context
            assertEquals(profile.width,server.controlCoordinator.authority.snapshot().profile!!.width)
            assertEquals(profile.height,server.controlCoordinator.authority.snapshot().profile!!.height)
            Log.i("EyeBrowseT02", "PHONE_PROFILE ${profile.width}x${profile.height}@${profile.densityDpi}")
            SystemClock.sleep(5_000)
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            SystemClock.sleep(2_000)
            assertEquals(profile,host.presentationProfile())
            assertEquals(context,server.controlCoordinator.authority.snapshot().context)
            assertSame(originalView,browser.view())
            val snapshot = host.privateDisplaySnapshot()!!
            assertEquals(profile.width,snapshot.actualWidth)
            assertEquals(profile.height,snapshot.actualHeight)
            assertEquals(android.view.Display.STATE_ON,snapshot.state)
            Log.i("EyeBrowseT02", "CONFIGURATION_PRESERVED $snapshot")
            SystemClock.sleep(10_000)
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("Stop cleanup", 5_000) { !host.hasDisplayResources() && !host.captureResourcesPresent() && !host.isWakeLockHeld() }
            assertSame(originalView,browser.view())
            Log.i("EyeBrowseT02", "STOP_CLEAN")
        } finally {
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; host.stop(); server.stop() }
            scenario.close()
        }
    }
    private fun await(label: String, bound: Long, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime()+bound
        while (SystemClock.elapsedRealtime() < end) {
            var pass = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync { pass = predicate() }
            if (pass) return
            SystemClock.sleep(50)
        }
        fail(label)
    }
}
