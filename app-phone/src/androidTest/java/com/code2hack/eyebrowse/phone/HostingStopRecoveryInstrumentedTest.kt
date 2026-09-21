package com.code2hack.eyebrowse.phone

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Phone-local lifecycle coverage; the connected companion separately exercises the real RG link. */
@RunWith(AndroidJUnit4::class)
class HostingStopRecoveryInstrumentedTest {
    @Test fun visibleStopRestoresCurrentActivityAndFencesPredecessor() = exerciseStop(false)

    @Test fun hiddenStopDefersAttachmentUntilCurrentActivityReturns() = exerciseStop(true)

    private fun exerciseStop(hidden: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val browser = PhoneBrowserSession.get(app)
        val host = HostingController.get(app)
        val server = PhoneLinkServer.obtain(app)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        var primaryFailure: Throwable? = null
        lateinit var originalView: WebView
        lateinit var originalDocument: String
        lateinit var predecessor: MainActivity
        lateinit var predecessorContainer: ViewGroup
        var predecessorToken: PhoneBrowserSession.Attachment? = null
        var testLease: HostingController.Lease? = null
        try {
            scenario.onActivity {
                // Preserve trust; this local lifecycle case is not the paired-wire companion.
                server.stop()
                assertEquals(HostingController.State.NOT_HOSTING, host.status().state)
                assertTrue(browser.openAddress(InstrumentationRegistry.getArguments()
                    .getString("fixtureBaseUrl", "http://127.0.0.1:26341") + "/hosting.html").accepted())
            }
            StopRecoveryAssertions.await("fixture ready", 10_000) {
                !browser.isLoading() && browser.pageTitle() == "Hosting capture page"
            }
            scenario.onActivity {
                originalView = checkNotNull(browser.view())
                originalDocument = browser.documentIdentity()
                predecessor = it
                predecessorContainer = it.findViewById(R.id.web_container)
                predecessorToken = StopRecoveryAssertions.token(it)
                assertTrue("initial Activity owns its token", browser.isCurrentAttachment(predecessorToken))
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            StopRecoveryAssertions.await("hosting active", 5_000) { host.status().state == HostingController.State.HOSTING }
            scenario.onActivity {
                // Existing in-process production API, not a protocol/paired-state bypass assertion.
                assertTrue(host.presentOnRg(HostingPresentationProfile(480, 527, 204)))
                assertTrue(host.isRgPresentationOwned())
                assertSame(originalView, browser.view())
                assertNotSame(predecessorContainer, originalView.parent)
                assertNotNull("real private Presentation parent", originalView.parent)
                assertFalse(it.findViewById<Button>(R.id.button_open).isEnabled)
                testLease = host.acquireLease { _ -> }
                assertNotNull("real capture lease", testLease)
                assertTrue(host.status().captureActive)
                assertTrue(host.isWakeLockHeld())
            }
            scenario.recreate()
            scenario.onActivity {
                server.stop() // onStart may reconnect a remembered peer; no trust is cleared.
                assertNotSame("successor Activity", predecessor, it)
                assertTrue("recreation must not steal RG ownership", host.isRgPresentationOwned())
                assertSame(originalView, browser.view())
                testLease!!.renew()
            }
            if (hidden) {
                scenario.moveToState(Lifecycle.State.CREATED)
                instrumentation.runOnMainSync {
                    host.stop()
                    assertEquals(HostingController.State.NOT_HOSTING, host.status().state)
                    assertFalse(host.isRgPresentationOwned())
                    assertSame(originalView, browser.view())
                    assertEquals(originalDocument, browser.documentIdentity())
                    assertNull("hidden UI is not a reattachment target", originalView.parent)
                    host.ensurePhoneUiAttachment(predecessor, predecessorContainer, predecessorToken)
                    assertNull("stale Activity cannot claim hidden Stop residue", originalView.parent)
                }
                scenario.moveToState(Lifecycle.State.RESUMED)
            } else {
                scenario.onActivity {
                    it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
                    StopRecoveryAssertions.afterStop(it, browser, host, originalView, originalDocument)
                }
            }
            StopRecoveryAssertions.await("private/capture/wake cleanup", 5_000) { StopRecoveryAssertions.resourcesGone(host) }
            scenario.onActivity {
                server.stop()
                StopRecoveryAssertions.afterStop(it, browser, host, originalView, originalDocument)
                val successorToken = StopRecoveryAssertions.token(it)
                host.ensurePhoneUiAttachment(predecessor, predecessorContainer, predecessorToken)
                host.onPhoneUiHidden(predecessorToken, predecessor)
                host.onPhoneUiDestroyed(predecessorToken, predecessor)
                browser.detach(predecessorToken)
                assertTrue("stale callbacks retain the live UI owner", host.hasPhoneUiOwner())
                assertTrue(browser.isCurrentAttachment(successorToken))
                StopRecoveryAssertions.afterStop(it, browser, host, originalView, originalDocument)
                host.stop() // Repeated Stop does not replace/detach the recovered attachment token.
                assertSame(successorToken, StopRecoveryAssertions.token(it))
                StopRecoveryAssertions.afterStop(it, browser, host, originalView, originalDocument)
            }
            StopRecoveryAssertions.restartAndStop(scenario, browser, host, originalView, originalDocument)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            val cleanup = listOf<() -> Unit>(
                { instrumentation.runOnMainSync { testLease?.release(); host.stop() } },
                { instrumentation.runOnMainSync { server.stop() } },
                { scenario.close() },
                { StopRecoveryAssertions.await("final cleanup", 5_000) { StopRecoveryAssertions.resourcesGone(host) } },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            cleanup.forEach { Log.e("EyeBrowseT2R1", "CLEANUP_FAILURE", it) }
            if (primaryFailure != null) cleanup.forEach { primaryFailure.addSuppressed(it) }
            else if (cleanup.isNotEmpty()) {
                cleanup.drop(1).forEach { cleanup.first().addSuppressed(it) }
                throw cleanup.first()
            }
        }
    }
}

/** Instrumentation-only observations. No product attachment, ownership or input bypass. */
internal object StopRecoveryAssertions {
    fun token(activity: MainActivity): PhoneBrowserSession.Attachment? =
        MainActivity::class.java.getDeclaredField("attachment").let {
            it.isAccessible = true
            it.get(activity) as PhoneBrowserSession.Attachment?
        }

    fun phoneUi(activity: MainActivity, browser: PhoneBrowserSession, host: HostingController,
                originalView: WebView, originalDocument: String) {
        assertFalse("Stop/restart must not retain RG exclusion", host.isRgPresentationOwned())
        assertSame("same WebView, not a replacement", originalView, browser.view())
        assertEquals("reattachment must not reload", originalDocument, browser.documentIdentity())
        val container = activity.findViewById<ViewGroup>(R.id.web_container)
        assertSame("actual current Phone parent, not just object identity", container, originalView.parent)
        assertTrue("Phone container is live", container.isAttachedToWindow)
        assertTrue("Activity retained the new attachment token", browser.isCurrentAttachment(token(activity)))
        for (id in listOf(R.id.address_input, R.id.button_open, R.id.button_reload)) {
            assertTrue("local input enabled: $id", activity.findViewById<View>(id).isEnabled)
        }
        assertEquals(browser.canGoBack(), activity.findViewById<View>(R.id.button_back).isEnabled)
        assertEquals(browser.canGoForward(), activity.findViewById<View>(R.id.button_forward).isEnabled)
        assertNotEquals(activity.getString(R.string.browsing_on_glasses),
            activity.findViewById<TextView>(R.id.hosting_status).text.toString())
    }

    fun afterStop(activity: MainActivity, browser: PhoneBrowserSession, host: HostingController,
                  originalView: WebView, originalDocument: String) {
        assertEquals("settled Stop state", HostingController.State.NOT_HOSTING, host.status().state)
        phoneUi(activity, browser, host, originalView, originalDocument)
        assertFalse("capture authority revoked synchronously", host.status().captureActive)
        assertFalse("wake lock released synchronously", host.isWakeLockHeld())
        assertFalse("private display/presentation released", host.hasDisplayResources())
        Log.i("EyeBrowseT2R1", "PHONE_STOP_ATTACHED input=true rgOwned=false sameDocument=true")
    }

    fun resourcesGone(host: HostingController): Boolean =
        host.status().state == HostingController.State.NOT_HOSTING &&
            !host.hasDisplayResources() && !host.captureResourcesPresent() && !host.isWakeLockHeld()

    fun restartAndStop(scenario: ActivityScenario<MainActivity>, browser: PhoneBrowserSession,
                       host: HostingController, originalView: WebView, originalDocument: String) {
        val previousGeneration = host.currentGeneration()
        // Teardown may have released its reader/thread before its main-thread retirement signal.
        // Retry only this explicit Start request within the existing completion bound.
        await("retirement permits explicit restart", 5_000) { host.start() }
        scenario.onActivity {
            assertTrue(host.currentGeneration() > previousGeneration)
            assertFalse("fresh start has no inherited RG owner", host.isRgPresentationOwned())
            assertEquals("fresh generation, not the retired measured RG profile",
                HostingPresentationProfile.RG_DESIGN_FALLBACK, host.presentationProfile())
        }
        await("restarted hosting active", 5_000) { host.status().state == HostingController.State.HOSTING }
        scenario.onActivity {
            assertEquals(HostingPresentationProfile.RG_DESIGN_FALLBACK, host.presentationProfile())
            phoneUi(it, browser, host, originalView, originalDocument)
            it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            afterStop(it, browser, host, originalView, originalDocument)
        }
        await("restart cleanup", 5_000) { resourcesGone(host) }
        Log.i("EyeBrowseT2R1", "RESTART_FRESH_OWNERSHIP_PROFILE")
    }

    fun await(label: String, bound: Long, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + bound
        while (SystemClock.elapsedRealtime() < end) {
            var pass = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync { pass = predicate() }
            if (pass) return
            SystemClock.sleep(50)
        }
        fail(label)
    }
}
