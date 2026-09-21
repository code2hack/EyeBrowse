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
    @Test fun measuredRgProfileSurvivesPhoneConfigurationAndStopsCleanly() = runCompanion(true)

    /** Narrow T2-R1 rerun: real connected RG Stop, without repeating configuration evidence. */
    @Test fun rgConnectedStopRestoresPhoneAttachment() = runCompanion(false)

    private fun runCompanion(verifyConfiguration: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val browser = PhoneBrowserSession.get(app)
        val host = HostingController.get(app)
        val server = PhoneLinkServer.obtain(app)
        var originalView: android.webkit.WebView? = null
        var originalDocument: String? = null
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        var primaryFailure: Throwable? = null
        var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        try {
            scenario.onActivity {
                originalOrientation = it.requestedOrientation
                server.start()
            }
            val fixture = StopRecoveryAssertions.openFixture(scenario, browser)
            originalView = fixture.view
            originalDocument = fixture.documentId
            scenario.onActivity {
                StopRecoveryAssertions.sameDocument("companion_before_start", browser, fixture.view, fixture.documentId)
                assertNotNull("fixture must have a live baseline WebView",originalView)
                Log.i("EyeBrowseT02","LIVE_WEBVIEW_BASELINE=${System.identityHashCode(originalView)}")
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            await("hosting active", 5_000) { host.status().state == HostingController.State.HOSTING }
            Log.i("EyeBrowseT02", "PHONE_READY")
            await("explicit RG request and capture", 30_000) { host.isRgPresentationOwned() && host.status().captureActive }
            val profile = host.presentationProfile()
            val context = server.controlCoordinator.authority.snapshot().context
            assertEquals(profile.width,server.controlCoordinator.authority.snapshot().profile!!.width)
            assertEquals(profile.height,server.controlCoordinator.authority.snapshot().profile!!.height)
            Log.i("EyeBrowseT02", "PHONE_PROFILE ${profile.width}x${profile.height}@${profile.densityDpi}")
            scenario.onActivity {
                StopRecoveryAssertions.sameDocument("companion_rg_attached", browser, fixture.view, fixture.documentId)
                assertFalse("RG ownership excludes Phone input", it.findViewById<Button>(R.id.button_open).isEnabled)
                assertNotSame(it.findViewById<android.view.ViewGroup>(R.id.web_container), browser.view()!!.parent)
            }
            if (verifyConfiguration) {
                SystemClock.sleep(5_000)
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                SystemClock.sleep(2_000)
                scenario.onActivity {
                    StopRecoveryAssertions.sameDocument("companion_after_configuration", browser, fixture.view, fixture.documentId)
                    val snapshot = host.privateDisplaySnapshot()!!
                    Log.i("EyeBrowseT02", "CONFIGURATION_FACTS expected=$profile actual=${host.presentationProfile()} expectedContext=$context actualContext=${server.controlCoordinator.authority.snapshot().context} sameView=${originalView === browser.view()} display=$snapshot")
                    assertEquals("immutable RG profile",profile,host.presentationProfile())
                    assertEquals("control context",context,server.controlCoordinator.authority.snapshot().context)
                    assertSame("same live WebView",originalView,browser.view())
                    assertEquals("private display width",profile.width,snapshot.actualWidth)
                    assertEquals("private display height",profile.height,snapshot.actualHeight)
                    assertEquals("private display ON",android.view.Display.STATE_ON,snapshot.state)
                }
                Log.i("EyeBrowseT02", "CONFIGURATION_PRESERVED ${host.privateDisplaySnapshot()}")
            }
            // Stop while the RG companion is still connected (its observation window is 15 s).
            SystemClock.sleep(5_000)
            scenario.onActivity {
                StopRecoveryAssertions.sameDocument("companion_before_stop", browser, fixture.view, fixture.documentId)
                assertTrue("Stop must exercise an authenticated RG peer", server.isLinkUp())
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
                // Synchronous Stop must clear exclusion before its final listeners reattach/render.
                StopRecoveryAssertions.afterStop(it, browser, host, checkNotNull(originalView), checkNotNull(originalDocument))
                assertTrue("TLS peer still present before explicit link Stop", server.isLinkUp())
                val before = SystemClock.elapsedRealtime()
                server.stop() // Actual Activity-lifecycle call shape, under normal StrictMode.
                assertFalse(server.isLinkUp())
                assertTrue("main-thread stop returns within bound",SystemClock.elapsedRealtime()-before < 1_000)
                Log.i("EyeBrowseT02", "LINK_STOP_MAIN_MS=${SystemClock.elapsedRealtime()-before}")
            }
            await("Stop cleanup", 5_000) {
                StopRecoveryAssertions.sameDocument("companion_stop_cleanup", browser, fixture.view, fixture.documentId, log = false)
                StopRecoveryAssertions.resourcesGone(host)
            }
            scenario.onActivity {
                StopRecoveryAssertions.afterStop(it, browser, host, checkNotNull(originalView), checkNotNull(originalDocument))
            }
            Log.i("EyeBrowseT02", "STOP_CLEAN")
            StopRecoveryAssertions.restartAndStop(scenario, browser, host, checkNotNull(originalView), checkNotNull(originalDocument))
        } catch (failure: Throwable) {
            primaryFailure = failure
            Log.e("EyeBrowseT02", "PRIMARY_FAILURE",failure)
            throw failure
        } finally {
            val cleanup = listOf<() -> Unit>(
                { scenario.onActivity { it.requestedOrientation = originalOrientation } },
                { instrumentation.runOnMainSync { host.stop() } },
                { instrumentation.runOnMainSync { server.stop() } },
                { scenario.close() },
                { StopRecoveryAssertions.await("final cleanup", 5_000) { StopRecoveryAssertions.resourcesGone(host) } },
            ).mapNotNull { step -> runCatching(step).exceptionOrNull() }
            cleanup.forEach { Log.e("EyeBrowseT02","CLEANUP_FAILURE",it) }
            if (primaryFailure != null) cleanup.forEach { primaryFailure.addSuppressed(it) }
            else if (cleanup.isNotEmpty()) {
                cleanup.drop(1).forEach { cleanup.first().addSuppressed(it) }
                throw cleanup.first()
            }
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
