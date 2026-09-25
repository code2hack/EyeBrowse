package com.code2hack.eyebrowse.phone

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.ArrayList
import java.util.HashSet
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused hosting-lifecycle instrumentation (correction rounds 2–3).
 *
 * <p>Evidence boundaries: activation uses the real Start/Stop controls through single verified
 * system-pipeline taps (bounded direct-callback activation is a separately labeled diagnostic);
 * page reads and fixture field/storage setup use {@link WebView#evaluateJavascript} (fixture setup,
 * not typing evidence); backgrounding uses {@code moveTaskToBack}, which proves the Activity
 * lifecycle path but is not a physical Home press or secure-lock observation. Delivered pixels are
 * sampled per frame in the consumer callback so content correlation uses the actually delivered
 * bitmap, and state/clock/resource milestones are persisted app-scoped while produced (not from a
 * rotating logcat tail).
 */
@RunWith(AndroidJUnit4::class)
class HostingInstrumentedTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val ownedScenarios = mutableListOf<ActivityScenario<MainActivity>>()
    private val ownedRenewals = mutableListOf<LeaseRenewal>()

    private fun launchScenario(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { ownedScenarios.add(it) }

    @After
    fun releaseTestOwnership() {
        // Preserve the original JUnit failure. Cleanup failures are additional failures, not a
        // substitute for it. No process/app-data/trust reset or hidden foreground rescue.
        val failures = mutableListOf<Throwable>()
        fun attempt(action: () -> Unit) {
            try { action() } catch (failure: Throwable) { failures.add(failure) }
        }
        if (::hosting.isInitialized) {
            println("HYBRID_CLEANUP_BEFORE " + runOnMainSync(hosting::captureDiagnostics))
        }
        for (renewal in ownedRenewals) attempt { renewal.stopRenewing() }
        if (::hosting.isInitialized) attempt { runOnMain(hosting::stop) }
        for (owned in ownedScenarios.asReversed()) attempt { owned.close() }
        ownedScenarios.clear()
        if (::hosting.isInitialized) {
            attempt {
                waitUntilMain("test-owned resources retired", {
                    !hosting.captureResourcesPresent() && !hosting.hasDisplayResources() &&
                        !hosting.isWakeLockHeld() && !hosting.hasPhoneUiOwner()
                })
            }
            attempt { runOnMain { hosting.setResourceFactoryForTest(PrivateDisplayHost.PlatformFactory()) } }
        }
        org.junit.runners.model.MultipleFailureException.assertEmpty(failures)
    }

    private lateinit var session: PhoneBrowserSession

    private lateinit var hosting: HostingController

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        session = PhoneBrowserSession.get(context)
        hosting = HostingController.get(context)
        if (milestones == null) {
            milestones = MilestoneSink(context, System.currentTimeMillis())
        }
        ensureNotificationPermissionSetupForTest()
        runOnMain(hosting::stop)
        waitUntilMain(
            "hosting stopped",
            { (hosting.status().state == HostingController.State.NOT_HOSTING) },
        )
        scenario = launchScenario()
        scenario.onActivity({ activity -> session.resetForTest() })
    }

    /**
     * App-scoped permission setup so the POST_NOTIFICATIONS dialog never interrupts the Start
     * control. Below API 33 that runtime permission is explicitly NOT_APPLICABLE: no check, no
     * grant/revoke, no GRANTED claim and no restoration obligation. On API 33+ the known original
     * state is recorded once per class (reported as {@code NOTIF_PERM_BEFORE} in the
     * instrumentation stream) and a not-granted state is changed only through a verified grant; an
     * attempted but unverified change is reported as uncertain, never silently treated as unchanged
     * or as success, and fails the setup.
     */
    private fun ensureNotificationPermissionSetupForTest() {
        val sdkInt: Int = android.os.Build.VERSION.SDK_INT
        if (!NotificationPermissionPolicy.applicable(sdkInt)) {
            notificationPermissionOutcome = NotificationPermissionPolicy.SetupOutcome.NOT_APPLICABLE
            if (!notificationPermissionNotApplicableReported) {
                notificationPermissionNotApplicableReported = true
                println(
                    ("NOTIF_PERM_N/A sdk=" + sdkInt).toString() +
                        " reason=no-runtime-permission-below-33"
                )
            }
            return
        }
        if (
            (notificationPermissionOutcome ==
                NotificationPermissionPolicy.SetupOutcome.PRE_GRANTED) ||
                (notificationPermissionOutcome ==
                    NotificationPermissionPolicy.SetupOutcome.GRANT_VERIFIED)
        ) {
            return
        }
        var granted: Boolean = notificationPermissionGranted()
        if (notificationPermissionOutcome == NotificationPermissionPolicy.SetupOutcome.NOT_RUN) {
            println("NOTIF_PERM_BEFORE granted=" + granted)
            if (granted) {
                notificationPermissionOutcome =
                    NotificationPermissionPolicy.SetupOutcome.PRE_GRANTED
                return
            }
        }
        var grantAttempted: Boolean = false
        if (NotificationPermissionPolicy.needsGrant(sdkInt, granted)) {
            grantAttempted = true
            runShellCommandForTest(
                "pm grant com.code2hack.eyebrowse.phone android.permission.POST_NOTIFICATIONS"
            )
            granted = notificationPermissionGranted()
        }
        if (NotificationPermissionPolicy.setupFailed(sdkInt, grantAttempted, granted)) {
            notificationPermissionOutcome =
                NotificationPermissionPolicy.SetupOutcome.GRANT_FAILED_OR_UNCERTAIN
            println(
                "NOTIF_PERM_CHANGED verified=false uncertain=true" +
                    " (no verified mutation; cleanup must not claim restoration)"
            )
            fail("POST_NOTIFICATIONS setup did not take effect; not proceeding as verified success")
        }
        if (granted) {
            notificationPermissionOutcome = NotificationPermissionPolicy.SetupOutcome.GRANT_VERIFIED
            println("NOTIF_PERM_CHANGED verified=true (restoration owed)")
        }
    }

    private fun notificationPermissionGranted(): Boolean {
        return (androidx.core.content.ContextCompat.checkSelfPermission(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    /** Shell-launched return to the foreground reusing the existing instance (SINGLE_TOP). */
    private fun bringMainActivityToFrontForTest() {
        val result = runShellCommandWithOutputForTest(
            "am start -W --display 0 -f 0x20000000 -n com.code2hack.eyebrowse.phone/.MainActivity"
        )
        println("PHONE_RETURN_RESULT " + result)
        assertTrue("single foreground request must succeed: " + result,
            result.contains("Status: ok") && !result.contains("Error:"))
        // One real launch, followed by the original attachment/focus checks and single tap.
    }

    private fun runShellCommandForTest(command: String) {
        try {
            val descriptor: android.os.ParcelFileDescriptor =
                InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .executeShellCommand(command)
            descriptor.close()
        } catch (ignored: RuntimeException) {} catch (ignored: java.io.IOException) {}
    }

    private fun runShellCommandWithOutputForTest(command: String): String {
        try {
            val descriptor: android.os.ParcelFileDescriptor =
                InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .executeShellCommand(command)
            try {
                java.io.FileInputStream(descriptor.getFileDescriptor()).use { `in` ->
                    val out: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream()
                    val buffer: ByteArray = ByteArray(8192)
                    var read: Int
                    while (true) {
                        read = `in`.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                    }
                    return out.toString("UTF-8")
                }
            } finally {
                descriptor.close()
            }
        } catch (e: RuntimeException) {
            return (("(command failed: " + e).toString() + ")")
        } catch (e: java.io.IOException) {
            return (("(command failed: " + e).toString() + ")")
        }
    }

    /** Real input injection is only meaningful while this app owns the focused window. */
    private fun awaitWindowFocusForTest() {
        val deadline: Long = (SystemClock.uptimeMillis() + 15_000)
        while (SystemClock.uptimeMillis() < deadline) {
            val focused: AtomicReference<Boolean> = AtomicReference(false)
            try {
                scenario.onActivity({ activity -> focused.set(activity.hasWindowFocus()) })
            } catch (ignored: RuntimeException) {}

            if (true.equals(focused.get())) {
                return
            }
            SystemClock.sleep(200)
        }
        fail("the browser window never regained input focus after the transition")
    }

    /**
     * ONE actual tap on the real hosting control via the codebase's validated instrumentation input
     * route (explicit screen coordinates, {@code sendPointerSync}, touchscreen source), after
     * verified readiness: focused window, laid-out visible button inside the window on the default
     * display, and a bounded post-transition settle. No retry — an unchanged state after the tap is
     * uncertain delivery and fails with diagnosis. A test-only touch observer records whether the
     * window received the injected events at all.
     */
    private fun tapHostingToggleOnce(expectedAfter: HostingController.State, boundMs: Long) {
        awaitWindowFocusForTest()
        SystemClock.sleep(800)
        val centerRef: AtomicReference<IntArray> = AtomicReference()
        val diagnosis: AtomicReference<String> = AtomicReference()
        scenario.onActivity({ activity ->
            val button: View = activity.findViewById<View>(R.id.button_hosting_toggle)
            val focusedWindow: Boolean = activity.hasWindowFocus()
            val laidOut: Boolean =
                ((button.isShown() && (button.getWidth() > 0)) && (button.getHeight() > 0))
            val location: IntArray = IntArray(2)
            button.getLocationOnScreen(location)
            val decor: View = activity.getWindow().getDecorView()
            val onScreen: Boolean =
                ((((location[0] >= 0) && (location[1] >= 0)) &&
                    ((location[0] + button.getWidth()) <= decor.getWidth())) &&
                    ((location[1] + button.getHeight()) <= decor.getHeight()))
            val display: android.view.Display? = activity.getDisplay()
            val defaultDisplayOn: Boolean =
                (((display != null) &&
                    (display.getDisplayId() == android.view.Display.DEFAULT_DISPLAY)) &&
                    (display.getState() == android.view.Display.STATE_ON))
            if (((focusedWindow && laidOut) && onScreen) && defaultDisplayOn) {
                centerRef.set(
                    intArrayOf(
                        (location[0] + (button.getWidth() / 2)),
                        (location[1] + (button.getHeight() / 2)),
                    )
                )
                button.setOnTouchListener(
                    callback5@{ view, event ->
                        android.util.Log.i(
                            "EyeBrowseTap",
                            ("button touch " + event.getActionMasked()),
                        )
                        return@callback5 false
                    }
                )
            } else {
                diagnosis.set(
                    (((((((((("focus=" + focusedWindow).toString() + " laidOut=") + laidOut)
                                    .toString() + " onScreen=") + onScreen)
                                .toString() + " at=") + location[0])
                            .toString() + ",") + location[1])
                        .toString() + " display=") +
                        (if (display == null) "null"
                        else (((display.getDisplayId()).toString() + "/") + display.getState()))
                )
            }
        })
        if (diagnosis.get() != null) {
            fail("hosting control not ready for a single tap: " + diagnosis.get())
        }
        val center: IntArray = centerRef.get()
        val now: Long = SystemClock.uptimeMillis()
        val down: android.view.MotionEvent =
            android.view.MotionEvent.obtain(
                now,
                now,
                android.view.MotionEvent.ACTION_DOWN,
                center[0].toFloat(),
                center[1].toFloat(),
                0,
            )
        val up: android.view.MotionEvent =
            android.view.MotionEvent.obtain(
                now,
                (now + 60),
                android.view.MotionEvent.ACTION_UP,
                center[0].toFloat(),
                center[1].toFloat(),
                0,
            )
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)
        try {
            InstrumentationRegistry.getInstrumentation().sendPointerSync(down)
            InstrumentationRegistry.getInstrumentation().sendPointerSync(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        val deadline: Long = (SystemClock.uptimeMillis() + boundMs)
        var current: HostingController.Status = runOnMainSync(hosting::status)
        while (SystemClock.uptimeMillis() < deadline) {
            current = runOnMainSync(hosting::status)
            if (current.state == expectedAfter) {
                return
            }
            SystemClock.sleep(50)
        }
        fail(
            ((((((((("injected tap produced no " + expectedAfter).toString() + " within ") +
                                    boundMs)
                                .toString() + "ms (last=") + current.state)
                            .toString() + " reason=") + current.failureReason)
                        .toString() +
                        "); focus, geometry and display state were verified before this single attempt; ")
                    .toString() +
                    "button touch delivery is in the EyeBrowseTap logcat; cause unknown if no touch ")
                .toString() + "was logged; no retry performed"
        )
    }

    /**
     * Labeled DIAGNOSTIC, not UI-journey evidence: the hosting toggle's own click listener is wired
     * and functional when invoked directly. This isolates product-listener defects from input-
     * injection quirks; it never substitutes the actual Start/Stop tap journey.
     */
    @Test
    fun hostingToggleListenerWiredDirectCallbackDiagnostic() {
        openFixture("/hosting.html", "Hosting capture page")
        val generationBefore: Long = runOnMainSync({ (hosting.currentGeneration()).toLong() })
        scenario.onActivity({ activity ->
            activity.findViewById<View>(R.id.button_hosting_toggle).performClick()
        })
        awaitHostingState(HostingController.State.HOSTING, START_BOUND_MS)
        assertEquals(
            (generationBefore + 1).toLong(),
            ((runOnMainSync({ (hosting.currentGeneration()).toLong() })).toLong()).toLong(),
        )
        runOnMain(hosting::stop)
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

    /**
     * The core same-instance continuity check: Phone → private presentation → Phone keeps the load
     * marker, field value, WebView identity and load count, and Start/Stop stay bounded.
     */
    @Test
    fun hostingStartBackgroundReturnKeepsSamePageStateAndWebViewIdentity() {
        openFixture("/hosting.html", "Hosting capture page")
        val marker: String? = domText("load-marker")
        setFieldValue("continuity-value")
        val loadsBefore: Int = loadCount("/hosting.html")
        val webViewIdentity: Int = webViewIdentityHash()
        val generationBefore: Long = runOnMainSync({ (hosting.currentGeneration()).toLong() })
        val startBegin: Long = SystemClock.uptimeMillis()
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val started: HostingController.Status = runOnMainSync(hosting::status)
        val startElapsed: Long = (SystemClock.uptimeMillis() - startBegin)
        assertEquals((generationBefore + 1).toLong(), ((started.generation).toLong()).toLong())
        assertEquals(HostingController.Attachment.PHONE_UI, started.attachment)
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "webview hosted offscreen",
            callback11@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback11 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        assertEquals((webViewIdentity).toLong(), (webViewIdentityHash()).toLong())
        assertEquals(
            "no reload while hosted",
            (loadsBefore).toLong(),
            (loadCount("/hosting.html")).toLong(),
        )
        assertEquals(marker, domText("load-marker"))
        assertEquals("continuity-value", readFieldValue())
        bringMainActivityToFrontForTest()
        waitUntil(
            "webview back on phone ui",
            callback12@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback12 ((snapshot.status.attachment ==
                    HostingController.Attachment.PHONE_UI) && snapshot.viewAttached)
            },
        )
        assertEquals((webViewIdentity).toLong(), (webViewIdentityHash()).toLong())
        assertEquals(marker, domText("load-marker"))
        assertEquals("continuity-value", readFieldValue())
        assertEquals(
            "no reload across the private-display round trip",
            (loadsBefore).toLong(),
            (loadCount("/hosting.html")).toLong(),
        )
        val stopBegin: Long = SystemClock.uptimeMillis()
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        val stopElapsed: Long = (SystemClock.uptimeMillis() - stopBegin)
        assertTrue(
            (("stop bound " + stopElapsed).toString() + "ms"),
            (stopElapsed <= STOP_BOUND_MS),
        )
        assertTrue(
            (("stop bound " + startElapsed).toString() + "ms"),
            (startElapsed <= START_BOUND_MS),
        )
        assertEquals("the page survives Stop", marker, domText("load-marker"))
        assertEquals("the field survives Stop", "continuity-value", readFieldValue())
        assertEquals((webViewIdentity).toLong(), (webViewIdentityHash()).toLong())
    }

    /** Activity exit during hosting is not Stop: the host and page survive, relaunch reattaches. */
    @Test
    fun activityFinishDuringHostingKeepsHostedPageForRelaunch() {
        openFixture("/hosting.html", "Hosting capture page")
        val marker: String? = domText("load-marker")
        val loadsBefore: Int = loadCount("/hosting.html")
        val webViewIdentity: Int = webViewIdentityHash()
        val generationBefore: Long = runOnMainSync({ (hosting.currentGeneration()).toLong() })
        onView(withId(R.id.button_hosting_toggle)).perform(click())
        awaitHostingState(HostingController.State.HOSTING, START_BOUND_MS)
        scenario.onActivity(android.app.Activity::finish)
        waitUntilMain(
            "host intact after activity finish",
            { (hosting.status().state == HostingController.State.HOSTING) },
        )
        val generationAfterFinish: Long = runOnMainSync({ (hosting.currentGeneration()).toLong() })
        assertEquals(
            "hosting outlives the Activity",
            (generationBefore + 1).toLong(),
            (generationAfterFinish).toLong(),
        )
        assertEquals(
            "the hosted page is still the same live document",
            marker,
            domText("load-marker"),
        )
        scenario = launchScenario()
        waitUntil(
            "relaunch shows the hosted page on phone ui",
            callback16@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback16 (((snapshot.status.state == HostingController.State.HOSTING) &&
                    (snapshot.status.attachment == HostingController.Attachment.PHONE_UI)) &&
                    snapshot.viewAttached)
            },
        )
        val generationAfterRelaunch: Long =
            runOnMainSync({ (hosting.currentGeneration()).toLong() })
        assertEquals(
            "same hosting generation after relaunch",
            (generationBefore + 1).toLong(),
            (generationAfterRelaunch).toLong(),
        )
        assertEquals(
            "same live WebView instance",
            (webViewIdentity).toLong(),
            (webViewIdentityHash()).toLong(),
        )
        assertEquals(
            "no reload on relaunch",
            (loadsBefore).toLong(),
            (loadCount("/hosting.html")).toLong(),
        )
        assertEquals(marker, domText("load-marker"))
    }

    /**
     * Repeated Start/Stop cycles keep the page, history and WebView identity; equivalent stopped
     * states, deterministic Stop-during-STARTING, stale-callback fencing after bounded teardown.
     */
    @Test
    fun startStopCyclesKeepPageAndRepeatedStopIsIdempotent() {
        openFixture("/hosting.html", "Hosting capture page")
        onView(withId(R.id.address_input))
            .perform(click(), replaceText((FIXTURE_BASE).toString() + "/hosting-two.html"))
        onView(withId(R.id.button_open)).perform(click())
        waitUntil(
            "second page for history",
            { "Second hosting page".equals(domText("page-title")) },
        )
        onView(withId(R.id.address_input))
            .perform(click(), replaceText((FIXTURE_BASE).toString() + "/hosting.html"))
        onView(withId(R.id.button_open)).perform(click())
        waitUntil(
            "back on the capture page",
            { "Hosting capture page".equals(domText("page-title")) },
        )
        val historyBefore: Boolean = runOnMainSync(session::canGoBack)
        assertTrue("representative history exists before hosting", historyBefore)
        val marker: String? = domText("load-marker")
        val loadsBefore: Int = loadCount("/hosting.html")
        val webViewIdentity: Int = webViewIdentityHash()
        val generationBefore: Long = runOnMainSync({ (hosting.currentGeneration()).toLong() })
        val stoppedStates: MutableList<String> = java.util.ArrayList()
        var cycle: Int = 1
        while (cycle <= 3) {
            onView(withId(R.id.button_hosting_toggle)).perform(click())
            val started: HostingController.Status =
                awaitHostingState(HostingController.State.HOSTING, START_BOUND_MS)
            assertEquals(
                "generation advances per cycle",
                (generationBefore + cycle).toLong(),
                ((started.generation).toLong()).toLong(),
            )
            runOnMain(hosting::stop)
            awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
            stoppedStates.add(
                runOnMainSync({
                    (((((hosting.captureResourcesPresent()).toString() + "|") +
                            hosting.hasDisplayResources())
                        .toString() + "|") + hosting.isWakeLockHeld())
                })
            )
            cycle++
        }
        assertEquals(
            "all cycles ended in the same stopped resource state",
            (1).toLong(),
            (HashSet(stoppedStates).size).toLong(),
        )
        runOnMain({
            assertTrue("stop-during-start start accepted", hosting.start())
            assertEquals(
                "stop-during-start exercises STARTING",
                HostingController.State.STARTING,
                hosting.status().state,
            )
            hosting.stop()
        })
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        runOnMain(hosting::stop)
        runOnMain(hosting::stop)
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        onView(withId(R.id.button_hosting_toggle)).perform(click())
        awaitHostingState(HostingController.State.HOSTING, START_BOUND_MS)
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "webview hosted offscreen",
            callback24@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback24 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        val staleConsumer: CollectingConsumer = CollectingConsumer()
        val stoppedLease: HostingController.Lease? =
            runOnMainSync({ hosting.acquireLease(staleConsumer) })
        assertNotNull("lease before Stop", stoppedLease)
        waitUntil("frames flow before Stop", { (staleConsumer.count() > 0) })
        runOnMain(hosting::stop)
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        val teardownDeadline: Long = (SystemClock.uptimeMillis() + STOP_BOUND_MS)
        while (
            (SystemClock.uptimeMillis() < teardownDeadline) &&
                runOnMainSync(hosting::captureResourcesPresent)
        ) {
            SystemClock.sleep(100)
        }
        assertEquals(
            "teardown completed within bound (reader closed, thread exited)",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        assertEquals("wake lock released by Stop", false, runOnMainSync(hosting::isWakeLockHeld))
        val staleCount: Int = staleConsumer.count()
        SystemClock.sleep(2_000)
        assertEquals(
            "no stale frames delivered after Stop",
            (staleCount).toLong(),
            (staleConsumer.count()).toLong(),
        )
        assertEquals(
            "representative history survives the hosting lifecycle",
            historyBefore,
            runOnMainSync(session::canGoBack),
        )
        assertEquals("page survives every cycle", marker, domText("load-marker"))
        assertEquals(
            "no page reload from hosting cycles",
            (loadsBefore).toLong(),
            (loadCount("/hosting.html")).toLong(),
        )
        assertEquals(
            "same live WebView instance",
            (webViewIdentity).toLong(),
            (webViewIdentityHash()).toLong(),
        )
    }

    /** Injected null and thrown platform allocation failures roll back bounded and idempotently. */
    @Test
    fun partialAllocationFailuresRollBackBounded() {
        openFixture("/hosting.html", "Hosting capture page")
        val platform: PrivateDisplayHost.Factory = PrivateDisplayHost.PlatformFactory()
        val generationBefore: Long = runOnMainSync({ (hosting.currentGeneration()).toLong() })
        hosting.setResourceFactoryForTest(
            object : PrivateDisplayHost.Factory {
                override fun createVirtualDisplay(
                    manager: DisplayManager,
                    name: String,
                    width: Int,
                    height: Int,
                    densityDpi: Int,
                    surface: Any?,
                ): VirtualDisplay? {
                    return null
                }

                override fun createImageReader(width: Int, height: Int): ImageReader? {
                    return platform.createImageReader(width, height)
                }

                override fun createPresentation(
                    context: Context,
                    display: android.view.Display,
                ): PrivateDisplayHost.PresentationHost {
                    return platform.createPresentation(context, display)
                }
            }
        )
        onView(withId(R.id.button_hosting_toggle)).perform(click())
        awaitStartupOutcome(generationBefore + 1)
        assertEquals(
            "display failure rolled back capture resources",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        assertEquals(
            "display failure released display resources",
            false,
            runOnMainSync(hosting::hasDisplayResources),
        )
        hosting.setResourceFactoryForTest(
            object : PrivateDisplayHost.Factory {
                override fun createVirtualDisplay(
                    manager: DisplayManager,
                    name: String,
                    width: Int,
                    height: Int,
                    densityDpi: Int,
                    surface: Any?,
                ): VirtualDisplay? {
                    return platform.createVirtualDisplay(
                        manager,
                        name,
                        width,
                        height,
                        densityDpi,
                        surface,
                    )
                }

                override fun createImageReader(width: Int, height: Int): ImageReader? {
                    throw IllegalStateException("injected platform allocation failure")
                }

                override fun createPresentation(
                    context: Context,
                    display: android.view.Display,
                ): PrivateDisplayHost.PresentationHost {
                    return platform.createPresentation(context, display)
                }
            }
        )
        onView(withId(R.id.button_hosting_toggle)).perform(click())
        awaitStartupOutcome(generationBefore + 2)
        assertEquals(
            "thrown failure rolled back capture resources",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        assertEquals(
            "thrown failure released display resources",
            false,
            runOnMainSync(hosting::hasDisplayResources),
        )
        hosting.setResourceFactoryForTest(platform)
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        assertEquals(false, runOnMainSync(hosting::captureResourcesPresent))
    }

    /** Waits for a failed start: NOT_HOSTING with the generation consumed and a recorded reason. */
    private fun awaitStartupOutcome(expectedGeneration: Long) {
        val deadline: Long = (SystemClock.uptimeMillis() + START_BOUND_MS)
        var status: HostingController.Status = runOnMainSync(hosting::status)
        while (SystemClock.uptimeMillis() < deadline) {
            status = runOnMainSync(hosting::status)
            if (
                ((status.state == HostingController.State.NOT_HOSTING) &&
                    (status.generation.toLong() == expectedGeneration)) &&
                    (status.failureReason != null)
            ) {
                return
            }
            SystemClock.sleep(50)
        }
        fail(
            ((((((((("failed start did not roll back in " + START_BOUND_MS).toString() +
                                "ms (state=") + status.state)
                            .toString() + " gen=") + status.generation)
                        .toString() + " expected=") + expectedGeneration)
                    .toString() + " reason=") + status.failureReason)
                .toString() + ")"
        )
    }

    /**
     * Hosting-active recreation, renderer interruption and site persistence: recreation with the
     * host live keeps generation/WebView/document; a renderer loss interrupts hosting and requires
     * the explicit restart; persisted site data survives hosting start/stop.
     */
    @Test
    fun hostingRecreationInterruptionAndPersistenceKeepSessionAndData() {
        openFixture("/storage.html", "localStorage controls")
        val storedValue: String = ("persist-check-" + SystemClock.uptimeMillis())
        evaluateJs(
            (("(function(){localStorage.setItem('fixture-key'," + JSONObject.quote(storedValue))
                    .toString() + ");")
                .toString() + "return localStorage.getItem('fixture-key');})()"
        )
        val generationBefore: Long = runOnMainSync({ (hosting.currentGeneration()).toLong() })
        openFixture("/hosting.html", "Hosting capture page")
        val marker: String? = domText("load-marker")
        val webViewIdentity: Int = webViewIdentityHash()
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        scenario.recreate()
        waitUntil(
            "recreated activity reattached the hosted page",
            callback29@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback29 (((snapshot.status.state == HostingController.State.HOSTING) &&
                    (snapshot.status.attachment == HostingController.Attachment.PHONE_UI)) &&
                    snapshot.viewAttached)
            },
        )
        assertEquals(
            "recreation keeps the hosting generation",
            (generationBefore + 1).toLong(),
            ((runOnMainSync({ (hosting.currentGeneration()).toLong() })).toLong()).toLong(),
        )
        assertEquals(
            "same live WebView instance across recreation",
            (webViewIdentity).toLong(),
            (webViewIdentityHash()).toLong(),
        )
        assertEquals(
            "document preserved across hosting-active recreation",
            marker,
            domText("load-marker"),
        )
        runOnMain(session::simulateProcessRestartForTest)
        waitUntil(
            "hosting interrupted after renderer loss",
            callback31@{
                var status: HostingController.Status = hosting.status()
                return@callback31 ((status.state == HostingController.State.NOT_HOSTING) &&
                    (status.failureReason != null))
            },
        )
        assertEquals(
            "interruption reason recorded",
            HostingController.State.NOT_HOSTING,
            runOnMainSync(hosting::status).state,
        )
        scenario = launchScenario()
        openFixture("/storage.html", "localStorage controls")
        assertEquals(
            "site persistence before hosting cycle",
            storedValue,
            decode(evaluateJs("localStorage.getItem('fixture-key')")),
        )
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        onView(withId(R.id.address_input))
            .perform(click(), replaceText((FIXTURE_BASE).toString() + "/storage.html"))
        onView(withId(R.id.button_open)).perform(click())
        waitUntil(
            "storage page reloaded after hosting cycle",
            { "localStorage controls".equals(domText("page-title")) },
        )
        assertEquals(
            "site persistence across hosting start/stop",
            storedValue,
            decode(evaluateJs("localStorage.getItem('fixture-key')")),
        )
    }

    /**
     * Hybrid contract: Phone rotation cannot mutate the private epoch profile. Return uses fresh
     * Phone content bounds; exact document/form/history continuity is preserved across reflow.
     */
    @Test
    fun hostingWindowChangeReconcilesGeometryWithExactRestoration() {
        openFixture("/hosting.html", "Hosting capture page")
        val marker = domText("load-marker")
        setFieldValue("geometry-value")
        val loadsBefore = loadCount("/hosting.html")
        val viewIdentity = webViewIdentityHash()
        val phoneBefore = currentWebViewSize()
        val originalPortrait = phoneBefore[1] >= phoneBefore[0]
        val opposite = if (originalPortrait) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val restore = if (originalPortrait) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val profile = runOnMainSync(hosting::presentationProfile)
        val generation = runOnMainSync(hosting::currentGeneration)
        scenario.onActivity { it.moveTaskToBack(true) }
        waitUntil("private owner before Phone rotation", {
            hostViewSnapshot().status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
        })
        val consumer = CollectingConsumer()
        consumer.expectQualification(profile.width, profile.height, CAPTURE_PAGE_COLOR)
        val eligible = SystemClock.uptimeMillis()
        val lease = runOnMainSync { hosting.acquireLease(consumer) }
        assertNotNull(lease)
        val renewal = LeaseRenewal(lease!!); renewal.start()
        try {
            val before = awaitPrivateProfile()
            waitUntil("first valid RG-profile frame", { consumer.qualifyingCountFrom(0) > 0 })
            val first = consumer.earliestQualifyingIndexFrom(0)
            assertTrue(OutputQualification.validWithinBound(consumer.earliestQualifyingDelayMsFrom(0, eligible)))
            scenario.onActivity { it.requestedOrientation = opposite }
            SystemClock.sleep(1_000)
            val during = awaitPrivateProfile()
            assertEquals(before.displayId, during.displayId)
            assertEquals(before.serial, during.serial)
            assertEquals(profile, runOnMainSync(hosting::presentationProfile))
            assertEquals(generation, runOnMainSync(hosting::currentGeneration))
            assertTrue(consumer.tailFramesMatchSize(first, profile.width, profile.height))
            assertTrue(consumer.tailFramesNearColor(first, CAPTURE_PAGE_COLOR))
            assertEquals(viewIdentity, webViewIdentityHash())
            milestones!!.record("hybrid Phone rotation preserved private profile " + during)
        } finally { renewal.stopRenewing(); runOnMain(lease::release) }
        bringMainActivityToFrontForTest()
        awaitPhoneContent()
        scenario.onActivity { it.requestedOrientation = restore }
        waitUntil("Phone returned to original orientation class", {
            val size = currentWebViewSize()
            (size[1] >= size[0]) == originalPortrait
        })
        awaitPhoneContent()
        val restored = currentWebViewSize()
        assertEquals(originalPortrait, restored[1] >= restored[0])
        assertEquals(viewIdentity, webViewIdentityHash())
        assertEquals(marker, domText("load-marker"))
        assertEquals("geometry-value", readFieldValue())
        assertEquals("no reload across owner handoff", loadsBefore, loadCount("/hosting.html"))
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

    /**
     * The bounded background-capture acceptance core (corrected round 2): never-leased idle
     * anchored at readiness, first frame at CONSUMER delivery ≤2s from eligibility, delivered-
     * pixel content correlation, ≥3 changing frames per 10s, 120s active offscreen capture,
     * production stop ≤6s TOTAL after the last renewal, wake-lock release in the same bound, idle
     * release by demand+30s, and final Stop — with device/lock facts and resource milestones
     * persisted app-scoped while produced.
     */
    @Test
    fun backgroundCaptureMeetsLivenessContentIdleAndStopBounds() {
        recordLockRecoverabilityAssessment()
        openFixture("/hosting.html", "Hosting capture page")
        val marker: String? = domText("load-marker")
        val loadsHosting: Int = loadCount("/hosting.html")
        setFieldValue("capture-value")
        val generationBefore: Long = runOnMainSync({ (hosting.currentGeneration()).toLong() })
        memoryMilestone("before-start")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        assertEquals(
            (generationBefore + 1).toLong(),
            ((runOnMainSync({ (hosting.currentGeneration()).toLong() })).toLong()).toLong(),
        )
        val readyElapsed: Long = SystemClock.elapsedRealtime()
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "webview hosted offscreen",
            callback46@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback46 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        recordDeviceState("backgrounded-before-capture")
        val readinessAnchor: Long = runOnMainSync(hosting::lastDemandAnchorElapsedMs)
        val neverLeasedDeadline: Long = (readinessAnchor + HostingPolicy.IDLE_RELEASE_MS)
        var completion: Long = 0
        val diagnosticLimit: Long = (neverLeasedDeadline + 10_000)
        while (SystemClock.elapsedRealtime() < diagnosticLimit) {
            completion = runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs)
            if (completion > 0) {
                break
            }
            SystemClock.sleep(50)
        }
        assertTrue("idle-release completion was observed on the production path", (completion > 0))
        assertTrue(
            (("never-leased completion " + (completion - readinessAnchor)).toString() +
                "ms after the anchor is within the exact 30s deadline"),
            (completion <= neverLeasedDeadline),
        )
        assertEquals(
            "capture resources absent after the observed completion",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        assertEquals(
            "never-leased hosting session persists",
            HostingController.State.HOSTING,
            runOnMainSync(hosting::status).state,
        )
        memoryMilestone(
            ("after-never-leased-idle completedAt=" + (completion - readinessAnchor)).toString() +
                "msAfterExactAnchor"
        )
        val expectedGeneration: Int = (generationBefore + 1).toInt()
        val expectedSize: IntArray = expectedPrivateSize()
        val consumer: CollectingConsumer = CollectingConsumer()
        consumer.expectQualification(expectedSize[0], expectedSize[1], CAPTURE_PAGE_COLOR)
        val eligibleUptime: Long = SystemClock.uptimeMillis()
        var lease: HostingController.Lease? = runOnMainSync({ hosting.acquireLease(consumer) })
        assertNotNull("lease must be acquirable while hosting", lease)
        val renewal: LeaseRenewal = LeaseRenewal(lease!!)
        renewal.start()
        try {
            waitUntil(
                "first VALID current-document/current-geometry frame",
                { (consumer.qualifyingCountFrom(0) > 0) },
            )
        } catch (failure: AssertionError) {
            fail(
                (((failure.message).toString() + " qualification={") +
                        consumer.qualificationSummary(0, eligibleUptime))
                    .toString() + "}"
            )
        }
        val firstValid: Int = consumer.earliestQualifyingIndexFrom(0)
        val firstValidDelayMs: Long = consumer.earliestQualifyingDelayMsFrom(0, eligibleUptime)
        milestones!!.record(
            "background first-raw/first-valid: " + consumer.qualificationSummary(0, eligibleUptime)
        )
        assertTrue(
            (((("first VALID current-token frame at consumer delivery within 2s of ORIGINAL ")
                    .toString() + "eligibility: ") + firstValidDelayMs)
                .toString() + "ms"),
            OutputQualification.validWithinBound(firstValidDelayMs),
        )
        assertTrue(
            "frame carries the hosting generation",
            consumer.allFramesMatchGeneration(expectedGeneration),
        )
        assertTrue(
            "frames from the first VALID frame match the measured viewport",
            consumer.tailFramesMatchSize(firstValid, expectedSize[0], expectedSize[1]),
        )
        assertTrue("capture stamps are monotonic", consumer.monotonicCaptureStamps())
        assertTrue(
            "wake lock is held while the lease is live",
            runOnMainSync(hosting::isWakeLockHeld),
        )
        evaluateJs("window.__eyebrowseFreeze(true)")
        SystemClock.sleep(3_000)
        val frozenCount: Int = consumer.count()
        SystemClock.sleep(2_500)
        assertEquals(
            "static page produces no new frames",
            (frozenCount).toLong(),
            (consumer.count()).toLong(),
        )
        evaluateJs("window.__eyebrowseFreeze(false)")
        waitUntil("frames resume when the counter resumes", { (consumer.count() > frozenCount) })
        var distinctBefore: Int = consumer.distinctHashes()
        var windowStart: Long = SystemClock.elapsedRealtime()
        while ((SystemClock.elapsedRealtime() - windowStart) < 10_000) {
            SystemClock.sleep(200)
        }
        assertTrue(
            (("at least 3 content-changing frames in a 10s window (got " +
                    (consumer.distinctHashes() - distinctBefore))
                .toString() + ")"),
            ((consumer.distinctHashes() - distinctBefore) >= 3),
        )
        assertTrue(
            "delivered pixels show the counter page",
            consumer.latestFrameNearColor(CAPTURE_PAGE_COLOR),
        )
        val loadsTwoBefore: Int = loadCount("/hosting-two.html")
        val secondPageFrom: Int = consumer.count()
        consumer.expectQualification(expectedSize[0], expectedSize[1], SECOND_PAGE_COLOR)
        val secondPageNavigationUptime: Long = SystemClock.uptimeMillis()
        runOnMain({ session.openAddress((FIXTURE_BASE).toString() + "/hosting-two.html") })
        waitUntil(
            "second page loaded while hosted",
            { "Second hosting page".equals(domText("page-title")) },
        )
        assertEquals(
            "navigation load recorded once",
            (loadsTwoBefore + 1).toLong(),
            (loadCount("/hosting-two.html")).toLong(),
        )
        val loadsTwoAfter: Int = (loadsTwoBefore + 1)
        try {
            waitUntil(
                "delivered pixels show the second page",
                { consumer.latestFrameNearColor(SECOND_PAGE_COLOR) },
            )
        } catch (failure: AssertionError) {
            fail(
                (((failure.message).toString() + " secondPageQualification={") +
                        consumer.qualificationSummary(secondPageFrom, secondPageNavigationUptime))
                    .toString() + "}"
            )
        }
        milestones!!.record(
            "second-page delivery: " +
                consumer.qualificationSummary(secondPageFrom, secondPageNavigationUptime)
        )
        val loadsHostingBeforeReturn: Int = loadCount("/hosting.html")
        val returnPageFrom: Int = consumer.count()
        consumer.expectQualification(expectedSize[0], expectedSize[1], CAPTURE_PAGE_COLOR)
        val returnNavigationUptime: Long = SystemClock.uptimeMillis()
        runOnMain({ session.openAddress((FIXTURE_BASE).toString() + "/hosting.html") })
        waitUntil(
            "counter page restored while hosted",
            { "Hosting capture page".equals(domText("page-title")) },
        )
        try {
            waitUntil(
                "delivered pixels return to the counter page",
                { consumer.latestFrameNearColor(CAPTURE_PAGE_COLOR) },
            )
        } catch (failure: AssertionError) {
            fail(
                (((failure.message).toString() + " returnQualification={") +
                        consumer.qualificationSummary(returnPageFrom, returnNavigationUptime))
                    .toString() + "}"
            )
        }
        SystemClock.sleep(2_000)
        assertTrue(
            "no second-page pixels after returning (stale output not replayed)",
            consumer.recentFramesNearColor(CAPTURE_PAGE_COLOR, 2_000),
        )
        val firstDeliveryElapsed: Long = consumer.deliveryElapsedAt(0)
        waitUntil(
            "120s of active offscreen capture",
            { ((consumer.latestCaptureElapsed() - firstDeliveryElapsed) >= 120_000) },
            140_000,
        )
        distinctBefore = consumer.distinctHashes()
        windowStart = SystemClock.elapsedRealtime()
        while ((SystemClock.elapsedRealtime() - windowStart) < 10_000) {
            SystemClock.sleep(200)
        }
        assertTrue(
            (("final 10s window still has >=3 changing frames (got " +
                    (consumer.distinctHashes() - distinctBefore))
                .toString() + ")"),
            ((consumer.distinctHashes() - distinctBefore) >= 3),
        )
        recordDeviceState("after-120s-active-capture")
        memoryMilestone("after-120s-active-capture")
        renewal.stopRenewing()
        runOnMain(lease!!::renew)
        val lastRenewElapsed: Long = runOnMainSync(hosting::lastDemandAnchorElapsedMs)
        val stopDeadline: Long = (lastRenewElapsed + 6_000)
        var stoppedObservedAt: Long = 0
        var endpointStatus: HostingController.Status
        do {
            endpointStatus = runOnMainSync(hosting::status)
            val observedAt: Long = SystemClock.elapsedRealtime()
            if (
                (!endpointStatus.captureActive && !endpointStatus.wakeLockHeld) &&
                    (stoppedObservedAt == 0L)
            ) {
                stoppedObservedAt = observedAt
            }
            if (observedAt >= stopDeadline) {
                break
            }
            SystemClock.sleep(Math.min(50, (stopDeadline - observedAt)))
        } while (true)
        assertTrue(
            "capture and wake lock stopped by last successful renewal +6s",
            ((stoppedObservedAt > 0) && (stoppedObservedAt <= stopDeadline)),
        )
        assertFalse(
            "capture remains inactive at the six-second endpoint",
            endpointStatus.captureActive,
        )
        assertFalse("wake lock remains released at the endpoint", endpointStatus.wakeLockHeld)
        val lastDelivery: Long = consumer.latestDeliveryElapsed()
        assertTrue(
            "no late consumer delivery beyond the six-second endpoint",
            (lastDelivery <= stopDeadline),
        )
        milestones!!.record(
            (((((("liveness anchor=" + lastRenewElapsed).toString() + " deadline=") + stopDeadline)
                    .toString() + " stoppedObserved=") + stoppedObservedAt)
                .toString() + " lastDelivery=") + lastDelivery
        )
        val reacquired: CollectingConsumer = CollectingConsumer()
        val lease2: HostingController.Lease? = runOnMainSync({ hosting.acquireLease(reacquired) })
        assertNotNull("lease reacquisition must succeed before idle release", lease2)
        val renewal2: LeaseRenewal = LeaseRenewal(lease2!!)
        renewal2.start()
        waitUntil("frames resume after reacquisition", { (reacquired.count() > 0) })
        assertEquals(
            "still the hosted counter document",
            "Hosting capture page",
            domText("page-title"),
        )
        assertEquals(
            "no navigation from reacquisition",
            (loadsTwoAfter).toLong(),
            (loadCount("/hosting-two.html")).toLong(),
        )
        assertEquals(
            "no reload of the counter page on reacquisition",
            (loadsHostingBeforeReturn + 1).toLong(),
            (loadCount("/hosting.html")).toLong(),
        )
        renewal2.stopRenewing()
        runOnMain(lease2!!::renew)
        val demandAnchor: Long = runOnMainSync(hosting::lastDemandAnchorElapsedMs)
        runOnMain(lease2!!::release)
        memoryMilestone("lease-released-idle-window-start")
        val idleDeadline: Long = (demandAnchor + HostingPolicy.IDLE_RELEASE_MS)
        var idleCompletion: Long = 0
        val idleDiagnosticLimit: Long = (idleDeadline + 10_000)
        while (SystemClock.elapsedRealtime() < idleDiagnosticLimit) {
            idleCompletion = runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs)
            if (idleCompletion > 0) {
                break
            }
            SystemClock.sleep(50)
        }
        assertTrue(
            "idle-release completion was observed on the production path",
            (idleCompletion > 0),
        )
        assertTrue(
            (("post-demand completion " + (idleCompletion - demandAnchor)).toString() +
                "ms after the anchor is within the exact 30s deadline"),
            (idleCompletion <= idleDeadline),
        )
        assertEquals(
            "capture resources absent after the observed completion",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        assertEquals(
            "display/presentation attachment survives idle release",
            true,
            runOnMainSync(hosting::hasDisplayResources),
        )
        assertEquals(
            "hosting session survives idle release",
            HostingController.State.HOSTING,
            runOnMainSync(hosting::status).state,
        )
        memoryMilestone("after-idle-release")
        val afterIdle: CollectingConsumer = CollectingConsumer()
        val lease3: HostingController.Lease? = runOnMainSync({ hosting.acquireLease(afterIdle) })
        assertNotNull("lease must reacquire after idle release", lease3)
        waitUntil("frames flow after idle-release reacquisition", { (afterIdle.count() > 0) })
        assertEquals(
            "document untouched by idle release/reacquire",
            "Hosting capture page",
            domText("page-title"),
        )
        assertEquals(
            "no reload across idle release",
            (loadsHostingBeforeReturn + 1).toLong(),
            (loadCount("/hosting.html")).toLong(),
        )
        runOnMain(lease3!!::release)
        bringMainActivityToFrontForTest()
        waitUntil(
            "webview back on phone ui",
            callback61@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback61 ((snapshot.status.attachment ==
                    HostingController.Attachment.PHONE_UI) && snapshot.viewAttached)
            },
        )
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        assertEquals(
            "capture resources gone after Stop",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        assertEquals(
            "display resources gone after Stop",
            false,
            runOnMainSync(hosting::hasDisplayResources),
        )
        assertEquals("wake lock released by Stop", false, runOnMainSync(hosting::isWakeLockHeld))
        assertEquals(
            "hosting session stopped",
            HostingController.State.NOT_HOSTING,
            runOnMainSync(hosting::status).state,
        )
        recordDeviceState("after-final-stop")
        memoryMilestone("after-final-stop")
        milestones!!.record("capture acceptance sequence complete")
        milestones!!.flushToStream("capture acceptance session")
    }

    /** R3: Stop while backgrounded, then return: the surviving live page reattaches, no reload. */
    @Test
    fun backgroundStopThenReturnReattachesLivePageWithoutReload() {
        openFixture("/hosting.html", "Hosting capture page")
        val loadsBefore: Int = loadCount("/hosting.html")
        val markerBefore: String? = domText("load-marker")
        val identityBefore: Int = webViewIdentityHash()
        setFieldValue("bgstop-value")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "webview hosted offscreen",
            callback63@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback63 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        runOnMain({ hosting.stop() })
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        bringMainActivityToFrontForTest()
        waitUntil(
            "live page reattached on return after background Stop",
            { hostViewSnapshot().viewAttached },
        )
        assertEquals(
            "no reload across background Stop and return",
            (loadsBefore).toLong(),
            (loadCount("/hosting.html")).toLong(),
        )
        assertEquals(
            "same document across background Stop and return",
            markerBefore,
            domText("load-marker"),
        )
        assertEquals(
            "same WebView across background Stop and return",
            (identityBefore).toLong(),
            (webViewIdentityHash()).toLong(),
        )
        assertEquals(
            "field value survived background Stop and return",
            "bgstop-value",
            readFieldValue(),
        )
        assertEquals(
            "hosting remains stopped after return",
            HostingController.State.NOT_HOSTING,
            runOnMainSync(hosting::status).state,
        )
    }

    /**
     * A: an intentionally ALL-WHITE document delivers as valid current content within the original
     * 2s eligibility bound. The expectation is white, so no color heuristic may reject a legitimate
     * white page; the live document marker persists and there is no reload. Raw initialization
     * callbacks are retained by the consumer, and validity is decided by the callback-time sample
     * and geometry rather than by a readiness fact.
     */
    @Test
    fun allWhiteDocumentDeliversWithinEligibilityBoundWithoutColorDependence() {
        openFixture("/hosting-white.html", "White capture page")
        assertTrue(
            "authoritative WebView must preraster while attached offscreen",
            runOnMainSync({ session.view()!!.getSettings().getOffscreenPreRaster() }),
        )
        val marker: String? = domText("load-marker")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "webview hosted offscreen",
            callback68@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback68 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        val expectedSize: IntArray = expectedPrivateSize()
        val consumer: CollectingConsumer = CollectingConsumer()
        consumer.expectQualification(expectedSize[0], expectedSize[1], Color.WHITE)
        val eligibleUptime: Long = SystemClock.uptimeMillis()
        var lease: HostingController.Lease? = runOnMainSync({ hosting.acquireLease(consumer) })
        assertNotNull("white-document lease", lease)
        try {
            waitUntil(
                "first VALID frame of the all-white document",
                { (consumer.qualifyingCountFrom(0) > 0) },
            )
        } catch (failure: AssertionError) {
            fail(
                (((failure.message).toString() + " qualification={") +
                        consumer.qualificationSummary(0, eligibleUptime))
                    .toString() + "}"
            )
        }
        val firstValid: Int = consumer.earliestQualifyingIndexFrom(0)
        val firstValidDelayMs: Long = consumer.earliestQualifyingDelayMsFrom(0, eligibleUptime)
        milestones!!.record(
            "all-white first-raw/first-valid: " + consumer.qualificationSummary(0, eligibleUptime)
        )
        assertTrue(
            (("first VALID white-document frame within 2s of ORIGINAL eligibility: " +
                    firstValidDelayMs)
                .toString() + "ms"),
            OutputQualification.validWithinBound(firstValidDelayMs),
        )
        assertTrue(
            "white frames from the first VALID callback are the live document",
            consumer.tailFramesNearColor(firstValid, Color.WHITE),
        )
        assertTrue(
            "white frames from the first VALID callback match the viewport",
            consumer.tailFramesMatchSize(firstValid, expectedSize[0], expectedSize[1]),
        )
        assertEquals(
            "same document marker (no reload/substitution)",
            marker,
            domText("load-marker"),
        )
        assertEquals(
            "no reload of the white page",
            (1).toLong(),
            (loadCount("/hosting-white.html")).toLong(),
        )
        runOnMain(lease!!::release)
        bringMainActivityToFrontForTest()
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

    /** Hybrid R6: a live lease survives owner handoff without Phone-driven private resizing. */
    @Test
    fun liveLeaseSurvivesGeometryRebuildWithRearmedDelivery() {
        openFixture("/hosting.html", "Hosting capture page")
        val originalSize = currentWebViewSize()
        val marker = domText("load-marker")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val profile = runOnMainSync(hosting::presentationProfile)
        val generation = runOnMainSync(hosting::currentGeneration)
        scenario.onActivity { it.moveTaskToBack(true) }
        waitUntil("private owner for live lease", {
            hostViewSnapshot().status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
        })
        val consumer = CollectingConsumer()
        consumer.expectQualification(profile.width, profile.height, CAPTURE_PAGE_COLOR)
        val lease = runOnMainSync { hosting.acquireLease(consumer) }
        assertNotNull(lease)
        val renewal = LeaseRenewal(lease!!); renewal.start()
        try {
            val before = awaitPrivateProfile()
            waitUntil("real current-document frames before handoff", { consumer.qualifyingCountFrom(0) > 0 })
            bringMainActivityToFrontForTest()
            awaitPhoneContent()
            val opposite = if (originalSize[1] >= originalSize[0])
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            scenario.onActivity { it.requestedOrientation = opposite }
            waitUntil("actual Phone layout changed", {
                val size = currentWebViewSize()
                size[0] != originalSize[0] || size[1] != originalSize[1]
            })
            awaitPhoneContent()
            assertEquals(profile, runOnMainSync(hosting::presentationProfile))
            val from = consumer.count()
            scenario.onActivity { it.moveTaskToBack(true) }
            val after = awaitPrivateProfile()
            assertEquals(before.displayId, after.displayId)
            assertEquals(before.serial, after.serial)
            waitUntil("same live lease resumes current page on immutable RG profile", {
                consumer.qualifyingCountFrom(from) > 0
            })
            val first = consumer.earliestQualifyingIndexFrom(from)
            assertTrue(consumer.tailFramesMatchSize(first, profile.width, profile.height))
            assertTrue(consumer.tailFramesNearColor(first, CAPTURE_PAGE_COLOR))
            assertTrue(consumer.allFramesMatchGeneration(generation))
            assertEquals(marker, domText("load-marker"))
        } finally { renewal.stopRenewing(); runOnMain(lease::release) }
        bringMainActivityToFrontForTest()
        awaitPhoneContent()
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

    /** Real owned renderer loss, explicit recovery in the SAME Activity, then hosting ownership. */
    @Test
    fun rendererLossRecoveryInSameActivityRegistersNewUiOwner() {
        openFixture("/hosting.html", "Hosting capture page")
        val originalActivity: AtomicReference<MainActivity> = AtomicReference()
        scenario.onActivity(originalActivity::set)
        val originalView: WebView? = runOnMainSync(session::view)
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val terminated: Boolean =
            runOnMainSync(
                callback80@{
                    val process: android.webkit.WebViewRenderProcess? =
                        originalView!!.getWebViewRenderProcess()
                    assertNotNull("the owned WebView has a renderer process", process)
                    return@callback80 process!!.terminate()
                }
            )
        assertTrue("owned renderer termination request accepted", terminated)
        waitUntil(
            "production renderer-loss callback cleared the old attachment",
            { runOnMainSync({ ((session.view() == null) && !session.isLive()) }) },
        )
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        milestones!!.record(
            "actual owned renderer loss observed; explicit same-Activity recovery follows"
        )
        openFixture("/hosting.html", "Hosting capture page")
        scenario.onActivity({ activity ->
            assertTrue(
                "recovery did not replace the Activity",
                (activity === originalActivity.get()),
            )
        })
        assertTrue(
            "recovery creates a new WebView after real renderer loss",
            (runOnMainSync(session::view) !== originalView),
        )
        val recoveredMarker: String? = domText("load-marker")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "new UI token permits offscreen transfer after renderer recovery",
            callback85@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback85 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        assertEquals(
            "recovered document survives transfer",
            recoveredMarker,
            domText("load-marker"),
        )
        scenario.onActivity(android.app.Activity::finish)
        waitUntil(
            "destroyed recovered Activity is no longer retained as UI owner",
            { !runOnMainSync(hosting::hasPhoneUiOwner) },
        )
        assertEquals(
            "Activity finish is not hosting Stop",
            HostingController.State.HOSTING,
            runOnMainSync(hosting::status).state,
        )
        scenario = launchScenario()
        waitUntil(
            "live recovered page reattaches to the successor Phone UI",
            { (hostViewSnapshot().status.attachment == HostingController.Attachment.PHONE_UI) },
        )
        assertEquals(recoveredMarker, domText("load-marker"))
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

    /** R7: a thrown reader recreation after idle rolls back and surfaces in actual status. */
    @Test
    fun thrownReaderRecreationSurfacesFailureAndRollsBack() {
        val factory: SettableFactory = SettableFactory()
        runOnMain({ hosting.setResourceFactoryForTest(factory) })
        openFixture("/hosting.html", "Hosting capture page")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "webview hosted offscreen",
            callback90@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback90 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        val readinessAnchor: Long = runOnMainSync(hosting::lastDemandAnchorElapsedMs)
        val idleDeadline: Long = (readinessAnchor + HostingPolicy.IDLE_RELEASE_MS)
        var r7Completion: Long = 0
        while (SystemClock.elapsedRealtime() < (idleDeadline + 10_000)) {
            r7Completion = runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs)
            if (r7Completion > 0) {
                break
            }
            SystemClock.sleep(50)
        }
        assertTrue("idle release completed before the injection", (r7Completion > 0))
        assertTrue(
            "idle release within the exact deadline before the injection",
            (r7Completion <= idleDeadline),
        )
        assertEquals(
            "capture resources absent before the injection",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        factory.throwOnNextReader = true
        val consumer: CollectingConsumer = CollectingConsumer()
        var lease: HostingController.Lease? = runOnMainSync({ hosting.acquireLease(consumer) })
        assertNull("acquisition fails explicitly when reader recreation throws", lease)
        var status: HostingController.Status = runOnMainSync(hosting::status)
        assertEquals(
            "hosting session survives the recoverable failure",
            HostingController.State.HOSTING,
            status.state,
        )
        assertNotNull("failure surfaced in actual status", status.failureReason)
        assertEquals(
            "allocation rolled back: no live capture resources",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        factory.throwOnNextReader = false
        lease = runOnMainSync({ hosting.acquireLease(consumer) })
        assertNotNull("recovery after failed recreation", lease)
        waitUntil("frames flow after recovery", { (consumer.count() > 0) })
        assertNull(
            "successful capture clears its resolved failure",
            runOnMainSync(hosting::status).failureReason,
        )
        runOnMain(lease!!::release)
        bringMainActivityToFrontForTest()
        scenario.onActivity({ activity ->
            val label: String =
                (activity.findViewById(R.id.hosting_status) as android.widget.TextView)
                    .getText()
                    .toString()
            assertTrue(
                ("recovered native UI reports active hosting: " + label),
                label.startsWith("Hosting ·"),
            )
        })
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

    /** R4: after the 5s deadline, a late renewal must not revive delivery or the wake lock. */
    @Test
    fun lateRenewalAfterExpiryCannotReviveDelivery() {
        openFixture("/hosting.html", "Hosting capture page")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "page attached offscreen before lease",
            callback96@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback96 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        val consumer: CollectingConsumer = CollectingConsumer()
        var lease: HostingController.Lease? = runOnMainSync({ hosting.acquireLease(consumer) })
        assertNotNull(lease)
        waitUntil("first frame before expiry", { (consumer.count() > 0) })
        SystemClock.sleep(HostingPolicy.LEASE_TTL_MS + 1_500)
        val framesAtExpiry: Int = consumer.count()
        runOnMain(lease!!::renew)
        SystemClock.sleep(2_000)
        assertEquals(
            "no delivery revived by the late renewal",
            (framesAtExpiry).toLong(),
            (consumer.count()).toLong(),
        )
        assertEquals(
            "wake lock released after expiry despite the late renewal",
            false,
            runOnMainSync(hosting::isWakeLockHeld),
        )
        bringMainActivityToFrontForTest()
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

    /** R2: a delayed in-flight consumer and a reacquiring consumer never share a borrowed frame. */
    @Test
    fun delayedConsumerReleaseReacquireIsolatesBorrowedFrames() {
        openFixture("/hosting.html", "Hosting capture page")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "webview hosted offscreen",
            callback100@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback100 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        val first: DelayedConsumer = DelayedConsumer()
        val lease1: HostingController.Lease? = runOnMainSync({ hosting.acquireLease(first) })
        assertNotNull(lease1)
        val second: CollectingConsumer = CollectingConsumer()
        var prematureLease: Array<HostingController.Lease?> = arrayOf(null)
        var quiescence: Array<String> = arrayOf("not-reached")
        HarnessProtocol.withFinalEvidence(
            {
                HarnessProtocol.withFinalEvidence(
                    {
                        assertTrue("consumer entered its callback", first.awaitEntered(5_000))
                        milestones!!.record("delayed-consumer callback held before revocation")
                        runOnMain(lease1!!::release)
                        assertTrue(
                            "borrowed callback keeps the retiring owner observable",
                            runOnMainSync(hosting::captureResourcesPresent),
                        )
                        prematureLease[0] = runOnMainSync({ hosting.acquireLease(second) })
                        assertNull(
                            "no replacement lease while the old callback is held",
                            prematureLease[0],
                        )
                        assertEquals(
                            "no anonymous delivery after rejected acquisition",
                            (0).toLong(),
                            (second.count()).toLong(),
                        )
                        milestones!!.record("replacement rejected while old callback held")
                    },
                    {
                        first.releaseHold()
                        if (prematureLease[0] != null) {
                            runOnMain(prematureLease[0]!!::release)
                        }
                        runOnMain(lease1!!::release)
                    },
                )
                quiescence[0] = "waiting/not-yet-observed"
                waitUntil(
                    "old capture owner actually quiescent",
                    { !runOnMainSync(hosting::captureResourcesPresent) },
                    STOP_BOUND_MS,
                )
                quiescence[0] = "observed"
                var entry: CallbackIntegrity.Snapshot? = first.entrySnapshot()
                var postHold: CallbackIntegrity.Snapshot? = first.postHoldSnapshot()
                assertNull(
                    ("held callback integrity failure: " + first.integrityFailure()),
                    first.integrityFailure(),
                )
                assertFalse(
                    "test-controlled callback hold expired or was interrupted",
                    first.holdFailed(),
                )
                assertNotNull("entry integrity snapshot captured before the hold", entry)
                assertNotNull(
                    "post-hold integrity snapshot captured in the same callback",
                    postHold,
                )
                assertTrue(
                    "borrowed contents stable across hold (whole-bitmap fingerprint)",
                    entry!!.sameContent(postHold!!),
                )
                assertTrue(
                    "held callback completed its own borrowed use",
                    (first.collector().count() > 0),
                )
                assertEquals(
                    "rejected acquisition created no hidden lease or delivery",
                    (0).toLong(),
                    (second.count()).toLong(),
                )
            },
            {
                var entry: CallbackIntegrity.Snapshot? = first.entrySnapshot()
                var postHold: CallbackIntegrity.Snapshot? = first.postHoldSnapshot()
                milestones!!.record(
                    (((((((((("delayed-consumer outcome quiescence=" + quiescence[0]).toString() +
                                        " holdFailed=") + first.holdFailed())
                                    .toString() + " integrityFailure=") + first.integrityFailure())
                                .toString() + " collected=") + first.collector().count())
                            .toString() + " entry=") +
                            (if (entry == null) "missing" else entry.summary()))
                        .toString() + " postHold=") +
                        (if (postHold == null) "missing" else postHold.summary())
                )
            },
        )
        milestones!!.record("old owner quiescent before explicit replacement acquisition")
        val replacementSize: IntArray = expectedPrivateSize()
        second.expectQualification(replacementSize[0], replacementSize[1], CAPTURE_PAGE_COLOR)
        val replacementEligibleUptime: Long = SystemClock.uptimeMillis()
        val replacement: HostingController.Lease? = runOnMainSync({ hosting.acquireLease(second) })
        assertNotNull("explicit acquisition succeeds after quiescence", replacement)
        try {
            waitUntil(
                "replacement consumer first VALID frame",
                { (second.qualifyingCountFrom(0) > 0) },
            )
            val replacementFirstValid: Int = second.earliestQualifyingIndexFrom(0)
            val replacementDelayMs: Long =
                second.earliestQualifyingDelayMsFrom(0, replacementEligibleUptime)
            milestones!!.record(
                "replacement first-raw/first-valid: " +
                    second.qualificationSummary(0, replacementEligibleUptime)
            )
            assertTrue(
                (("replacement earliest VALID frame within 2s of original acquisition: " +
                        replacementDelayMs)
                    .toString() + "ms"),
                OutputQualification.validWithinBound(replacementDelayMs),
            )
            assertTrue(
                ((("replacement frames from the first VALID callback match the viewport " +
                        replacementSize[0])
                    .toString() + "x") + replacementSize[1]),
                second.tailFramesMatchSize(
                    replacementFirstValid,
                    replacementSize[0],
                    replacementSize[1],
                ),
            )
            assertTrue(
                "replacement frames from the first VALID callback show the same document",
                second.tailFramesNearColor(replacementFirstValid, CAPTURE_PAGE_COLOR),
            )
            milestones!!.record("explicit replacement lease delivered")
        } finally {
            runOnMain(replacement!!::release)
        }
        assertEquals(
            "test milestone writes succeeded",
            (0).toLong(),
            (milestones!!.failureCount()).toLong(),
        )
        bringMainActivityToFrontForTest()
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

    /** R1: after a never-leased idle release, a private move allocates nothing without demand. */
    @Test
    fun homeAfterNeverLeasedIdleDoesNotReallocateWithoutDemand() {
        openFixture("/hosting.html", "Hosting capture page")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "webview hosted offscreen",
            callback111@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback111 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        val readinessAnchor: Long = runOnMainSync(hosting::lastDemandAnchorElapsedMs)
        val neverLeasedDeadline: Long = (readinessAnchor + HostingPolicy.IDLE_RELEASE_MS)
        while (
            (SystemClock.elapsedRealtime() < neverLeasedDeadline) &&
                runOnMainSync(hosting::captureResourcesPresent)
        ) {
            SystemClock.sleep(50)
        }
        assertEquals(
            "never-leased idle release completed by the exact deadline",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        bringMainActivityToFrontForTest()
        waitUntil("webview back on phone ui", { hostViewSnapshot().viewAttached })
        scenario.onActivity({ activity -> activity.moveTaskToBack(true) })
        waitUntil(
            "hosted offscreen again without capture allocation",
            callback114@{
                val snapshot: HostViewSnapshot = hostViewSnapshot()
                return@callback114 ((snapshot.status.attachment ==
                    HostingController.Attachment.PRIVATE_DISPLAY) && snapshot.viewAttached)
            },
        )
        SystemClock.sleep(5_000)
        assertEquals(
            "no capture resources recreated by the demand-free private move",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        assertEquals(
            "hosting session persists through the demand-free moves",
            HostingController.State.HOSTING,
            runOnMainSync(hosting::status).state,
        )
        val staleCompletion: Long = runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs)
        assertTrue("cycle 1 completion was observed", (staleCompletion > 0))
        val consumer: CollectingConsumer = CollectingConsumer()
        var lease: HostingController.Lease? = runOnMainSync({ hosting.acquireLease(consumer) })
        assertNotNull("genuine demand recreates the capture surface", lease)
        assertEquals(
            "new demand invalidates the prior cycle's completion evidence",
            (0L).toLong(),
            (runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs).toLong()).toLong(),
        )
        waitUntil("frames flow after genuine demand", { (consumer.count() > 0) })
        val anchor2: Long = runOnMainSync(hosting::lastDemandAnchorElapsedMs)
        assertTrue("cycle 2 anchor postdates the stale completion", (anchor2 > staleCompletion))
        runOnMain(lease!!::release)
        val cycle2Deadline: Long = (anchor2 + HostingPolicy.IDLE_RELEASE_MS)
        var cycle2Completion: Long = 0
        while (SystemClock.elapsedRealtime() < (cycle2Deadline + 10_000)) {
            cycle2Completion = runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs)
            if (cycle2Completion >= anchor2) {
                break
            }
            SystemClock.sleep(50)
        }
        assertTrue(
            "cycle 2 completion observed for the current release only",
            (cycle2Completion >= anchor2),
        )
        assertTrue(
            "cycle 2 completion within the exact deadline",
            (cycle2Completion <= cycle2Deadline),
        )
        assertEquals(
            "capture resources absent after cycle 2 completion",
            false,
            runOnMainSync(hosting::captureResourcesPresent),
        )
        runOnMain(lease!!::release)
        bringMainActivityToFrontForTest()
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

    /**
     * Records the contemporaneous interactive/keyguard/device-secure/recovery facts that ground the
     * display-off/lock coverage decision, app-scoped, while produced.
     */
    private fun recordLockRecoverabilityAssessment() {
        val context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val power: PowerManager = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        val keyguard: KeyguardManager =
            (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
        val assessment: String =
            (((((((((((((("LOCK_ASSESSMENT interactive=" + power.isInteractive()).toString() +
                                                " deviceLocked=") + keyguard.isDeviceLocked())
                                            .toString() + " keyguardRestricted=") +
                                            keyguard.inKeyguardRestrictedInputMode())
                                        .toString() + " deviceSecure=") + keyguard.isDeviceSecure())
                                    .toString() + " keyguardSecure=") + keyguard.isKeyguardSecure())
                                .toString() +
                                " externalGuardedHelperRecovery=not-attested-in-process; see current phase record")
                            .toString() +
                            " displayOffSecureLockExercise=NOT EXERCISED — unattended profile: ")
                        .toString() + "no qualified mid-invocation guard recovery handshake; ")
                    .toString() +
                    "deviceSecure is a lock fact, not a recovery-availability decision. ")
                .toString() + "Guarded entry/final relock alone is not locked-capture evidence.")
        println(assessment)
        milestones!!.record(assessment)
    }

    @Test
    fun stalePresentationCallbackCannotStopFreshGeneration() {
        val callbacks = mutableListOf<Runnable>()
        val platform = PrivateDisplayHost.PlatformFactory()
        runOnMain { hosting.setResourceFactoryForTest(object : PrivateDisplayHost.Factory by platform {
            override fun createPresentation(context: Context, display: android.view.Display): PrivateDisplayHost.PresentationHost {
                val delegate = platform.createPresentation(context, display)
                return object : PrivateDisplayHost.PresentationHost by delegate {
                    override fun setUnavailableListener(listener: Runnable?) {
                        if (listener != null) callbacks.add(listener)
                        delegate.setUnavailableListener(listener)
                    }
                }
            }
        }) }
        openFixture("/hosting.html", "Hosting capture page")
        val marker = domText("load-marker")
        val viewId = webViewIdentityHash()
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val old = runOnMainSync { hosting.privateDisplaySnapshot()!! }
        val staleCallback = runOnMainSync { callbacks.first() }
        val oldActivity = AtomicReference<MainActivity>()
        val oldContainer = AtomicReference<android.view.ViewGroup>()
        val oldToken = AtomicReference<PhoneBrowserSession.Attachment>()
        scenario.onActivity { activity ->
            oldActivity.set(activity)
            oldContainer.set(activity.findViewById(R.id.web_container))
            // Read the real token without minting test-only product ownership.
            val field = MainActivity::class.java.getDeclaredField("attachment")
            field.isAccessible = true
            oldToken.set(field.get(activity) as PhoneBrowserSession.Attachment)
        }
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        waitUntilMain("prior display and capture released", {
            !hosting.hasDisplayResources() && !hosting.captureResourcesPresent()
        })
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val gen = runOnMainSync(hosting::currentGeneration)
        scenario.recreate()
        awaitPhoneContent()
        val successorParent = runOnMainSync { session.view()!!.parent }
        runOnMain {
            val token = oldToken.get()
            hosting.onPhoneUiHidden(token)
            hosting.moveWebViewToPrivateDisplay(token)
            hosting.onPhoneUiDestroyed(token)
            hosting.ensurePhoneUiAttachment(oldActivity.get(), oldContainer.get(), token)
            assertNull("stale Activity cannot reclaim Phone presentation",
                hosting.moveWebViewToPhoneUi(oldActivity.get(), oldContainer.get()))
            session.detach(token)
            assertTrue("old Activity cleanup cannot detach successor", session.view()!!.parent === successorParent)
            assertTrue("successor UI registration survives old callback", hosting.hasPhoneUiOwner())
            assertEquals(gen, hosting.currentGeneration())
        }
        scenario.onActivity { it.moveTaskToBack(true) }
        waitUntil("successor privately attached", {
            hostViewSnapshot().status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
        })
        val profile = runOnMainSync(hosting::presentationProfile)
        val consumer = CollectingConsumer()
        consumer.expectQualification(profile.width, profile.height, CAPTURE_PAGE_COLOR)
        val eligible = SystemClock.uptimeMillis()
        val lease = runOnMainSync { hosting.acquireLease(consumer) }
        assertNotNull(lease)
        val renewal = LeaseRenewal(lease!!); renewal.start()
        try {
            val fresh = awaitPrivateProfile()
            assertNotEquals("next generation cannot inherit old/OFF display", old.displayId, fresh.displayId)
            runOnMain { staleCallback.run() }
            runOnMain { }
            waitUntil("successor still produces qualified frames", { consumer.qualifyingCountFrom(0) > 0 })
            assertTrue(OutputQualification.validWithinBound(consumer.earliestQualifyingDelayMsFrom(0, eligible)))
            assertEquals(HostingController.State.HOSTING, runOnMainSync(hosting::status).state)
            assertEquals(gen, runOnMainSync(hosting::currentGeneration))
            assertEquals(fresh.displayId, awaitPrivateProfile().displayId)
            assertEquals(viewId, webViewIdentityHash())
            assertEquals(marker, domText("load-marker"))
            milestones!!.record("fresh private generation after stale callback " + fresh)
        } finally { renewal.stopRenewing(); runOnMain(lease::release) }
        bringMainActivityToFrontForTest()
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

    /**
     * I9-T01 FW1/FW2 focused physical regression: use the recorded 480x344 -> 480x240 ->
     * 480x344 stimulus at density 204, while production still consumes measured profiles.
     *
     * The fixture is frozen before RG ownership so the final profile frame cannot depend on a
     * second natural animation. Each transition keeps the same VirtualDisplay/Presentation/window,
     * WebView and document, requires a fresh profile lease inside the ORIGINAL two-second request
     * deadline, and verifies copied content/geometry. Separate consumers make a same-size return
     * unable to masquerade as the original 344 epoch.
     */
    @Test
    fun committedWindowCopySurvivesShrinkAndSameSizeReturn() {
        openFixture("/hosting.html", "Hosting capture page")
        evaluateJs("window.__eyebrowseFreeze(true)")
        val marker = domText("load-marker")
        val viewId = webViewIdentityHash()
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)

        val normal = HostingPresentationProfile(480, 344, 204)
        val keyboard = HostingPresentationProfile(480, 240, 204)
        assertTrue("recorded 344 profile accepted",
            runOnMainSync { hosting.presentOnRg(normal) })
        waitUntilMain("local private focus for recorded profile", {
            hosting.localEditorFocusReady()
        })
        awaitPrivateProfile()
        val physical = runOnMainSync { hosting.profileGeometry()!! }
        assertEquals(viewId, physical.viewId)
        assertTrue(physical.localFocus)

        fun assertSamePhysicalSource(now: PrivateDisplayHost.ProfileGeometry) {
            assertEquals("VirtualDisplay survives resize", physical.display.displayId, now.display.displayId)
            assertEquals("Presentation survives resize", physical.presentationId, now.presentationId)
            assertEquals("private Window survives resize", physical.windowId, now.windowId)
            assertEquals("decor survives resize", physical.decorId, now.decorId)
            assertEquals("WebView parent survives resize", physical.parentId, now.parentId)
            assertEquals("same live WebView survives resize", physical.viewId, now.viewId)
            assertEquals("no local-focus departure", physical.focusLossSerial, now.focusLossSerial)
            assertTrue("local focus stays ready", now.localFocus)
        }

        fun acquireCurrent(
            profile: HostingPresentationProfile,
            consumer: CollectingConsumer,
            originalEligibleUptimeMs: Long,
            originalDeadlineElapsedMs: Long,
        ): HostingController.Lease {
            consumer.expectQualification(profile.width, profile.height, CAPTURE_PAGE_COLOR)
            val lease = runOnMainSync {
                hosting.acquireProfileLease(profile, originalDeadlineElapsedMs, consumer)
            }
            assertNotNull("profile lease " + profile, lease)
            waitUntil(
                "qualified committed-Window frame " + profile,
                { consumer.qualifyingCountFrom(0) > 0 },
                2_500,
            )
            val delay = consumer.earliestQualifyingDelayMsFrom(0, originalEligibleUptimeMs)
            assertTrue(
                "profile publication exceeded original 2s request deadline: " + delay + "ms",
                OutputQualification.validWithinBound(delay),
            )
            val first = consumer.earliestQualifyingIndexFrom(0)
            assertTrue("copied bitmap geometry matches profile",
                consumer.tailFramesMatchSize(first, profile.width, profile.height))
            assertTrue("copied pixels are the current static document",
                consumer.tailFramesNearColor(first, CAPTURE_PAGE_COLOR))
            assertTrue("Window PixelCopy SUCCESS recorded: " + runOnMainSync(hosting::captureDiagnostics),
                runOnMainSync(hosting::captureDiagnostics).contains("copyResult=" + android.view.PixelCopy.SUCCESS))
            return lease!!
        }

        // Establish a real committed baseline at 344. This page has no autonomous draw after freeze.
        val baseEligible = SystemClock.uptimeMillis()
        val baseDeadline = SystemClock.elapsedRealtime() + 2_000
        val baseConsumer = CollectingConsumer()
        val baseLease = acquireCurrent(normal, baseConsumer, baseEligible, baseDeadline)
        val baseCount = baseConsumer.count()

        fun transition(
            profile: HostingPresentationProfile,
            consumer: CollectingConsumer,
        ): Pair<HostingController.Lease, PrivateDisplayHost.ProfileGeometry> {
            val eligibleUptime = SystemClock.uptimeMillis()
            val deadlineElapsed = SystemClock.elapsedRealtime() + 2_000
            val settled = java.util.concurrent.CountDownLatch(1)
            val ok = java.util.concurrent.atomic.AtomicBoolean(false)
            runOnMain {
                hosting.reconfigureRgProfile(profile, deadlineElapsed) { result ->
                    ok.set(result)
                    settled.countDown()
                }
            }
            assertTrue("profile resize callback within original deadline",
                settled.await(2_200, java.util.concurrent.TimeUnit.MILLISECONDS))
            assertTrue("profile resize settled " + profile, ok.get())
            val geometry = runOnMainSync { hosting.profileGeometry()!! }
            assertEquals(profile.width, geometry.display.actualWidth)
            assertEquals(profile.height, geometry.display.actualHeight)
            assertEquals(profile.width, geometry.display.readerWidth)
            assertEquals(profile.height, geometry.display.readerHeight)
            assertEquals(profile.width, geometry.decorWidth)
            assertEquals(profile.height, geometry.decorHeight)
            assertEquals(profile.width, geometry.containerWidth)
            assertEquals(profile.height, geometry.containerHeight)
            assertEquals(profile.width, geometry.viewWidth)
            assertEquals(profile.height, geometry.viewHeight)
            assertEquals(1, geometry.readerOverlap)
            assertSamePhysicalSource(geometry)
            val lease = acquireCurrent(profile, consumer, eligibleUptime, deadlineElapsed)
            return lease to geometry
        }

        val shrinkConsumer = CollectingConsumer()
        val (shrinkLease, shrinkGeometry) = transition(keyboard, shrinkConsumer)
        assertEquals("old 344 sink stays fenced after shrink", baseCount, baseConsumer.count())
        assertTrue("shrink creates a fresh geometry epoch", shrinkGeometry.profileSerial > physical.profileSerial)

        val shrinkCount = shrinkConsumer.count()
        val growConsumer = CollectingConsumer()
        val (growLease, growGeometry) = transition(normal, growConsumer)
        assertEquals("old 240 sink stays fenced after grow", shrinkCount, shrinkConsumer.count())
        assertEquals("same-size return cannot revive original 344 sink", baseCount, baseConsumer.count())
        assertTrue("344 return is a later geometry epoch",
            growGeometry.profileSerial > shrinkGeometry.profileSerial)
        assertEquals(marker, domText("load-marker"))
        assertEquals(viewId, webViewIdentityHash())

        // Reconfigure revoked the older lease objects; releasing them is intentionally a no-op.
        runOnMain(baseLease::release)
        runOnMain(shrinkLease::release)
        runOnMain(growLease::release)
        runOnMain(hosting::stop)
    }


    /**
     * R4a: hold the actual post-visual hardware traversal inside the Presentation after the
     * product draw observer ran but before traversal returns. Queue the 240 profile transition at
     * the front of Main, then release the traversal. The old frame-commit callback is therefore
     * delivered only after its capture/profile authority was superseded and must publish nothing.
     */
    @Test
    fun delayedCommitFromSupersededProfileCannotPublish() {
        val factory = R4ControlledFactory()
        val normal = prepareR4RecordedProfile(factory)
        val keyboard = HostingPresentationProfile(480, 240, 204)
        val oldConsumer = CollectingConsumer()
        oldConsumer.expectQualification(normal.width, normal.height, CAPTURE_PAGE_COLOR)
        val drawGate = factory.armNextDraw()
        val deadline = SystemClock.elapsedRealtime() + 2_000
        val oldLease = runOnMainSync {
            hosting.acquireProfileLease(normal, deadline, oldConsumer)
        }
        assertNotNull("old profile lease", oldLease)
        assertTrue("real hardware draw reached delayed-commit barrier",
            drawGate.awaitEntered(1_000))

        val transitionDone = CountDownLatch(1)
        val transitionOk = AtomicBoolean(false)
        Handler(Looper.getMainLooper()).postAtFrontOfQueue {
            hosting.reconfigureRgProfile(keyboard, deadline) { ok ->
                transitionOk.set(ok)
                transitionDone.countDown()
            }
        }
        drawGate.release()
        assertTrue("draw barrier released without timeout", !drawGate.timedOut)
        assertTrue("superseding profile settled inside original deadline",
            transitionDone.await(2_000, TimeUnit.MILLISECONDS))
        assertTrue("superseding profile accepted", transitionOk.get())
        assertEquals("delayed old commit cannot publish", 0, oldConsumer.count())

        val fresh = CollectingConsumer()
        fresh.expectQualification(keyboard.width, keyboard.height, CAPTURE_PAGE_COLOR)
        val freshLease = runOnMainSync {
            hosting.acquireProfileLease(keyboard, deadline, fresh)
        }
        assertNotNull("successor profile lease", freshLease)
        waitUntil("successor publishes after delayed old commit rejects", {
            fresh.qualifyingCountFrom(0) > 0
        }, 2_000)
        assertEquals("old consumer remains fenced after successor readiness", 0, oldConsumer.count())
        runOnMain(oldLease!!::release)
        runOnMain(freshLease!!::release)
    }

    /**
     * R4b: keep the capture HandlerThread between the swap-post and old-reader retirement. That
     * leaves the prior ImageReader retained while the new 240 reader is current. Replay the old
     * callback through the production callback seam and explicitly run the settlement seam while
     * overlap=2: neither may complete readiness or deliver an old frame.
     */
    @Test
    fun retainedOldReaderCallbackDuringDelayedSettlementCannotUnblockReadiness() {
        val factory = R4ControlledFactory()
        val normal = prepareR4RecordedProfile(factory)
        val keyboard = HostingPresentationProfile(480, 240, 204)
        val baseline = CollectingConsumer()
        baseline.expectQualification(normal.width, normal.height, CAPTURE_PAGE_COLOR)
        val baselineDeadline = SystemClock.elapsedRealtime() + 2_000
        val baselineLease = runOnMainSync {
            hosting.acquireProfileLease(normal, baselineDeadline, baseline)
        }
        assertNotNull("baseline profile lease", baselineLease)
        waitUntil("baseline committed-Window frame", {
            baseline.qualifyingCountFrom(0) > 0
        }, 2_000)
        val baselineCount = baseline.count()
        val host = currentPrivateHostForR4()
        val oldReader = factory.latestReader()
        val staleBefore = hosting.staleProfileImageCallbacksForTest()

        val captureGate = R4Gate()
        val transitionDone = CountDownLatch(1)
        val transitionOk = AtomicBoolean(false)
        val deadline = SystemClock.elapsedRealtime() + 2_000
        runOnMain {
            hosting.reconfigureRgProfile(keyboard, deadline) { ok ->
                transitionOk.set(ok)
                transitionDone.countDown()
            }
            // resizeProfile already queued swap-post on this handler; this barrier is next.
            assertTrue("capture barrier queued",
                handlerFieldForR4(host, "captureHandler").post(captureGate.asRunnable()))
        }
        assertTrue("capture thread reached retirement-delay barrier",
            captureGate.awaitEntered(1_000))
        waitUntilMain("new reader installed while old reader retained", {
            val g = hosting.profileGeometry()
            g != null && g.display.readerWidth == keyboard.width &&
                g.display.readerHeight == keyboard.height && g.readerOverlap == 2
        })

        val settlement = runOnMainSync { hosting.profileSettlementForTest() }
        assertNotNull("profile settlement seam wired", settlement)
        runOnMain(settlement!!)
        assertEquals("settlement cannot complete while old reader is retained",
            1L, transitionDone.count)

        hosting.replayProfileImageCallbackForTest(oldReader)
        assertEquals("retained old reader callback classified stale",
            staleBefore + 1, hosting.staleProfileImageCallbacksForTest())
        assertEquals("stale reader callback cannot publish/unblock old sink",
            baselineCount, baseline.count())

        captureGate.release()
        assertTrue("capture barrier released without timeout", !captureGate.timedOut)
        assertTrue("profile settles after old reader retirement",
            transitionDone.await(2_000, TimeUnit.MILLISECONDS))
        assertTrue("profile resize succeeds after delayed retirement", transitionOk.get())
        assertEquals("old sink stays fenced after settlement", baselineCount, baseline.count())

        val fresh = CollectingConsumer()
        fresh.expectQualification(keyboard.width, keyboard.height, CAPTURE_PAGE_COLOR)
        val freshLease = runOnMainSync {
            hosting.acquireProfileLease(keyboard, deadline, fresh)
        }
        assertNotNull("successor lease after stale reader replay", freshLease)
        waitUntil("successor frame after stale reader replay", {
            fresh.qualifyingCountFrom(0) > 0
        }, 2_000)
        runOnMain(baselineLease!!::release)
        runOnMain(freshLease!!::release)
    }

    /**
     * R4c: block the dedicated readback thread before the old request invokes PixelCopy. After
     * the committed request is in-flight, block Main, queue the 240 profile supersession, release
     * readback and wait for the synchronous Window PixelCopy invocation to return. Its SUCCESS
     * listener is now queued behind the already-enqueued supersession. Releasing Main proves a
     * late successful old bitmap cannot be reheadered or publish into the successor epoch.
     */
    @Test
    fun delayedPixelCopyCompletionAfterEpochSupersessionCannotPublishOldBitmap() {
        val factory = R4ControlledFactory()
        val normal = prepareR4RecordedProfile(factory)
        val keyboard = HostingPresentationProfile(480, 240, 204)
        val oldConsumer = CollectingConsumer()
        oldConsumer.expectQualification(normal.width, normal.height, CAPTURE_PAGE_COLOR)

        // Resolve the Main-thread-owned host before the draw barrier can hold Main. The
        // readback Handler is lazy, so reflect it only after acquireProfileLease starts capture.
        val host = currentPrivateHostForR4()
        val drawGate = factory.armNextDraw()
        val deadline = SystemClock.elapsedRealtime() + 2_000
        val oldLease = runOnMainSync {
            hosting.acquireProfileLease(normal, deadline, oldConsumer)
        }
        assertNotNull("old profile lease", oldLease)
        assertTrue("hardware draw reached R4c barrier", drawGate.awaitEntered(1_000))

        // Pure reflection: no Main hop while the traversal gate is holding Main.
        val readback = handlerFieldForR4(host, "readbackHandler")
        val readbackGate = R4Gate()
        assertTrue("readback barrier queued", readback.post(readbackGate.asRunnable()))
        assertTrue("readback thread blocked before PixelCopy", readbackGate.awaitEntered(1_000))

        drawGate.release()
        assertTrue("draw barrier released without timeout", !drawGate.timedOut)
        waitUntil("committed old Window copy request becomes in-flight", {
            inFlightWindowCopyForR4(host)
        }, 1_000)
        // Flush the commit callback: the invoke runnable is now definitely queued behind readbackGate.
        runOnMain { }

        val mainGate = R4Gate()
        Handler(Looper.getMainLooper()).postAtFrontOfQueue(mainGate.asRunnable())
        assertTrue("Main blocked before PixelCopy completion delivery", mainGate.awaitEntered(1_000))

        val transitionDone = CountDownLatch(1)
        val transitionOk = AtomicBoolean(false)
        Handler(Looper.getMainLooper()).post {
            hosting.reconfigureRgProfile(keyboard, deadline) { ok ->
                transitionOk.set(ok)
                transitionDone.countDown()
            }
        }

        // This marker is queued after the already-posted invoke runnable. Android 12's Window
        // PixelCopy call has returned (and its Main listener is queued) before this marker runs.
        val readbackReturned = CountDownLatch(1)
        assertTrue("readback return marker queued",
            readback.post { readbackReturned.countDown() })
        readbackGate.release()
        assertTrue("readback barrier released without timeout", !readbackGate.timedOut)
        assertTrue("Window PixelCopy invocation returned while Main callback remained delayed",
            readbackReturned.await(1_500, TimeUnit.MILLISECONDS))

        mainGate.release()
        assertTrue("Main barrier released without timeout", !mainGate.timedOut)
        assertTrue("superseding profile completes before deadline",
            transitionDone.await(2_000, TimeUnit.MILLISECONDS))
        assertTrue("superseding profile accepted", transitionOk.get())
        waitUntilMain("late PixelCopy completion retired", {
            val d = hosting.captureDiagnostics()
            d.contains("nativeCopy=0") && d.contains("copyResult=" + android.view.PixelCopy.SUCCESS)
        })
        assertEquals("late SUCCESS bitmap cannot publish into superseded epoch",
            0, oldConsumer.count())

        val fresh = CollectingConsumer()
        fresh.expectQualification(keyboard.width, keyboard.height, CAPTURE_PAGE_COLOR)
        val freshLease = runOnMainSync {
            hosting.acquireProfileLease(keyboard, deadline, fresh)
        }
        assertNotNull("successor lease after delayed PixelCopy completion", freshLease)
        waitUntil("successor publishes only its own committed Window copy", {
            fresh.qualifyingCountFrom(0) > 0
        }, 2_000)
        assertEquals("old bitmap remains retired after successor readiness", 0, oldConsumer.count())
        runOnMain(oldLease!!::release)
        runOnMain(freshLease!!::release)
    }

    /**
     * R-04 stale-SUCCESS variant: predecessor native copy has completed, but its SUCCESS delivery
     * is held without blocking Main. A real compatible resize + successor rearm must preserve B's
     * admitted demand until A releases the single product copy slot.
     */
    @Test
    fun successorDemandSurvivesHeldStaleSuccessCompletion() {
        runSuccessorDemandAfterHeldPredecessorCompletion(null, "stale-success")
    }

    /** R-04 stale-error variant: identical successor-liveness proof for an old NO_DATA completion. */
    @Test
    fun successorDemandSurvivesHeldStaleErrorCompletion() {
        runSuccessorDemandAfterHeldPredecessorCompletion(
            android.view.PixelCopy.ERROR_SOURCE_NO_DATA,
            "stale-error",
        )
    }

    private fun runSuccessorDemandAfterHeldPredecessorCompletion(
        scriptedPredecessorResult: Int?,
        label: String,
    ) {
        val factory = R4ControlledFactory()
        if (scriptedPredecessorResult != null) {
            factory.scriptCopyResults(scriptedPredecessorResult)
        }
        val normal = prepareR4RecordedProfile(factory)
        val keyboard = HostingPresentationProfile(480, 240, 204)
        val host = currentPrivateHostForR4()
        val predecessor = CollectingConsumer()
        predecessor.expectQualification(normal.width, normal.height, CAPTURE_PAGE_COLOR)
        val held = factory.holdNextCopyCompletion()
        val predecessorDeadline = SystemClock.elapsedRealtime() + 2_000
        val predecessorLease = runOnMainSync {
            hosting.acquireProfileLease(normal, predecessorDeadline, predecessor)
        }
        assertNotNull("$label predecessor lease", predecessorLease)
        assertTrue("$label predecessor completion captured without blocking Main",
            held.awaitCaptured(1_200))
        assertEquals("$label only predecessor backend copy requested", 1, factory.copyInvocationCount())
        val predecessorState = captureAuthorityForR4(host)
        assertTrue("$label predecessor product copy slot remains occupied",
            predecessorState.copyInFlight)
        assertTrue("$label predecessor transaction established",
            predecessorState.transactionId > 0)
        assertEquals("$label predecessor unpublished while completion held", 0, predecessor.count())
        val predecessorBitmap = checkNotNull(held.destinationBitmap()) {
            "$label predecessor destination not captured"
        }
        assertFalse("$label predecessor destination retained until completion delivery",
            predecessorBitmap.isRecycled)

        // Real compatible resize; no test draw/commit callback is synthesized.
        val successorDeadline = SystemClock.elapsedRealtime() + 2_000
        val settled = CountDownLatch(1)
        val settledOk = AtomicBoolean(false)
        runOnMain {
            hosting.reconfigureRgProfile(keyboard, successorDeadline) { ok ->
                settledOk.set(ok)
                settled.countDown()
            }
        }
        assertTrue("$label compatible resize callback", settled.await(1_200, TimeUnit.MILLISECONDS))
        assertTrue("$label compatible resize settled", settledOk.get())
        waitUntilMain("$label successor local focus ready", { hosting.localEditorFocusReady() })

        val successor = CollectingConsumer()
        successor.expectQualification(keyboard.width, keyboard.height, CAPTURE_PAGE_COLOR)
        val successorLease = runOnMainSync {
            hosting.acquireProfileLease(keyboard, successorDeadline, successor)
        }
        assertNotNull("$label successor lease", successorLease)

        // B is admitted while A still occupies the resource slot. No extra stimulus after this:
        // the only path to B's first frame is A completion -> scheduler re-evaluation.
        waitUntil("$label successor demand is recorded behind predecessor slot", {
            val a = captureAuthorityForR4(host)
            a.successorDemandAuthority == a.authoritySerial &&
                a.copyInFlight && a.transactionId == 0L
        }, 500)
        val admitted = captureAuthorityForR4(host)
        assertNotEquals("$label successor binding differs from predecessor",
            predecessorState.bindingIdentity, admitted.bindingIdentity)
        assertNotEquals("$label successor authority differs from predecessor",
            predecessorState.authoritySerial, admitted.authoritySerial)
        assertEquals("$label successor keeps original readiness deadline",
            successorDeadline, admitted.bindingDeadline)
        assertTrue("$label successor readiness pending", admitted.readinessPending)
        assertFalse("$label successor is not terminal", admitted.terminal)
        assertEquals("$label old copy remains the only backend request before release",
            1, factory.copyInvocationCount())
        assertEquals("$label old consumer still unpublished", 0, predecessor.count())
        assertEquals("$label successor still unpublished before slot release", 0, successor.count())
        assertTrue("$label completion release stays inside successor budget",
            successorDeadline - SystemClock.elapsedRealtime() > 200)

        held.release()
        waitUntil("$label predecessor bitmap retired after stale completion", {
            predecessorBitmap.isRecycled
        }, 500)
        assertEquals("$label stale predecessor completion never publishes old pixels",
            0, predecessor.count())

        // No invalidate/navigation/draw stimulus here. B's already-admitted demand must launch its
        // own normal visual-state -> hardware draw -> commit -> Window-copy fence.
        waitUntil("$label successor autonomously publishes newly fenced frame", {
            successor.qualifyingCountFrom(0) > 0
        }, (successorDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(1))
        assertTrue("$label successor frames use keyboard geometry",
            successor.allFramesMatchSize(keyboard.width, keyboard.height))
        assertTrue("$label successor frame stayed inside original readiness deadline",
            successor.latestDeliveryElapsed() <= successorDeadline)
        assertEquals("$label stale predecessor remains unpublished after successor readiness",
            0, predecessor.count())
        assertTrue("$label successor uses a later backend copy",
            factory.copyInvocationCount() >= 2)
        assertEquals("$label backend copy invocation calls never overlap",
            1, factory.maxConcurrentCopyCalls())

        val ready = captureAuthorityForR4(host)
        assertEquals("$label successor demand consumed", 0L, ready.successorDemandAuthority)
        assertFalse("$label successor readiness retired after success", ready.readinessPending)
        assertFalse("$label successor remains non-terminal", ready.terminal)

        // Resize already revoked the predecessor lease; explicit releases remain safe/no-op.
        runOnMain(predecessorLease!!::release)
        runOnMain(successorLease!!::release)
    }

    /**
     * T-A / R-01: the two-second profile-readiness deadline is one-time. After the first
     * committed Window frame succeeds, the SAME renewed lease must keep publishing autonomous
     * page updates after t0+2000 through independent steady-state transactions.
     */
    @Test
    fun sameProfileLeaseKeepsPublishingPastInitialReadinessDeadline() {
        val factory = R4ControlledFactory()
        val normal = prepareR4RecordedProfile(factory)
        val consumer = CollectingConsumer()
        consumer.expectQualification(normal.width, normal.height, CAPTURE_PAGE_COLOR)
        val deadline = SystemClock.elapsedRealtime() + 2_000
        val lease = runOnMainSync { hosting.acquireProfileLease(normal, deadline, consumer) }
        assertNotNull("finite-deadline profile lease", lease)
        val renewal = LeaseRenewal(lease!!)
        renewal.start()
        try {
            waitUntil("first readiness frame", { consumer.qualifyingCountFrom(0) > 0 }, 2_000)
            val firstCount = consumer.count()
            assertTrue("readiness retired after first successful frame: " +
                runOnMainSync(hosting::captureDiagnostics),
                runOnMainSync(hosting::captureDiagnostics).contains("readinessPending=false"))

            waitUntil("original readiness deadline has elapsed", {
                SystemClock.elapsedRealtime() > deadline + 100
            }, 2_500)
            val countAfterDeadline = consumer.count()
            val document = runOnMainSync(session::documentIdentity)
            val host = currentPrivateHostForR4()
            val drawBefore = runOnMainSync { host.drawObservationForTest().serial }

            // Fixture diagnosis: background timer unfreeze did not produce a traversal on S20+.
            // Mutate layout synchronously in the SAME document, then request a normal WebView
            // traversal. This is test stimulus only; production admission still waits on the real
            // hardware draw/commit/Window-copy chain.
            evaluateJs(
                "(function(){document.body.style.paddingBottom='96px';" +
                    "return document.body.getBoundingClientRect().height;})()"
            )
            runOnMain {
                session.view()!!.requestLayout()
                session.view()!!.invalidate()
                session.view()!!.postInvalidateOnAnimation()
            }
            waitUntilMain("same-document post-deadline hardware draw", {
                host.drawObservationForTest().serial > drawBefore
            })
            assertEquals("T-A stimulus preserves browser document", document,
                runOnMainSync(session::documentIdentity))
            waitUntil("same lease publishes a steady-state frame after t0+2000", {
                consumer.count() > countAfterDeadline &&
                    consumer.latestDeliveryElapsed() > deadline
            }, 2_500)
            assertTrue("same lease delivered beyond initial deadline", consumer.count() > firstCount)
            assertTrue("steady-state capture authority remains live: " +
                runOnMainSync(hosting::captureDiagnostics),
                !runOnMainSync(hosting::captureDiagnostics).contains("terminal=true"))
        } finally {
            renewal.stopRenewing()
            runOnMain(lease::release)
        }
    }

    /**
     * T-B / R-02 negative, premise-ruling version: initial NO_DATA, one permitted recovery
     * TIMEOUT, with scheduler DEMAND coalesced while copy #1 is unresolved. The demand enters
     * through the real requestCaptureDemand() scheduler; this test never sets trailingCaptureDemand
     * and never synthesizes a draw/commit callback.
     */
    @Test
    fun transientRecoveryBudgetCannotResetFromTrailingDemand() {
        val factory = R4ControlledFactory().apply {
            scriptCopyResults(
                android.view.PixelCopy.ERROR_SOURCE_NO_DATA,
                android.view.PixelCopy.ERROR_TIMEOUT,
            )
        }
        val normal = prepareR4RecordedProfile(factory)
        val host = currentPrivateHostForR4()
        val consumer = CollectingConsumer()
        consumer.expectQualification(normal.width, normal.height, CAPTURE_PAGE_COLOR)

        // The gate is released deliberately after scheduler coalescing is observed. Its timeout
        // is only a deadlock guard and remains shorter than the immutable 2s readiness deadline.
        val firstCopyGate = factory.armNextCopy(1_200)
        val started = SystemClock.elapsedRealtime()
        val deadline = started + 2_000
        val generation = runOnMainSync(hosting::currentGeneration)
        val lease = runOnMainSync { hosting.acquireProfileLease(normal, deadline, consumer) }
        assertNotNull("scripted transient profile lease", lease)
        assertTrue("initial copy invocation reached test barrier",
            factory.awaitCopyInvocation(700) != null)
        assertTrue("initial copy held in flight", firstCopyGate.awaitEntered(700))

        // P1: immutable authority/transaction receipt while copy #1 is outstanding.
        val initial = captureAuthorityForR4(host)
        assertTrue("readiness transaction is pending", initial.readinessPending)
        assertTrue("copy #1 is in flight", initial.copyInFlight)
        assertTrue("transaction identity established", initial.transactionId > 0)
        assertEquals("transaction uses original readiness deadline", deadline, initial.transactionDeadline)
        assertEquals("binding keeps original readiness deadline", deadline, initial.bindingDeadline)
        assertTrue("lease/capture authority live before fault injection",
            runOnMainSync(hosting::status).captureActive)
        assertEquals("no frame published before injected results", 0, consumer.count())
        assertEquals("only initial backend copy before coalescing", 1, factory.copyInvocationCount())
        val runnerCandidate = InstrumentationRegistry.getArguments()
            .getString("candidateSha", "runner-not-specified")
        println("T_B_NEG_AUTHORITY candidate=" + runnerCandidate + " gen=" + generation +
            " binding=" + initial.bindingIdentity + " authority=" + initial.authoritySerial +
            " transaction=" + initial.transactionId + " deadline=" + initial.transactionDeadline)

        // P2: submit demand through the ACTUAL production scheduler. The product itself observes
        // the unresolved transaction/copy and coalesces one bounded trailing demand.
        invokeProductionCaptureDemandForR4(host)
        val coalesced = captureAuthorityForR4(host)
        assertEquals("scheduler demand stays on same binding",
            initial.bindingIdentity, coalesced.bindingIdentity)
        assertEquals("scheduler demand stays on same authority serial",
            initial.authoritySerial, coalesced.authoritySerial)
        assertEquals("scheduler demand stays on unresolved transaction",
            initial.transactionId, coalesced.transactionId)
        assertEquals("scheduler demand cannot reset transaction deadline",
            initial.transactionDeadline, coalesced.transactionDeadline)
        assertTrue("production scheduler records bounded trailing demand", coalesced.trailingDemand)
        assertTrue("copy #1 remains outstanding after coalescing", coalesced.copyInFlight)
        assertEquals("coalesced demand cannot start another copy", 1, factory.copyInvocationCount())
        assertEquals("coalescing cannot publish", 0, consumer.count())
        val firstFenceDiagnostics = runOnMainSync(hosting::captureDiagnostics)
        val firstVisual = diagnosticLong(firstFenceDiagnostics, "visual")
        val firstCommittedDraw = diagnosticLong(firstFenceDiagnostics, "committedDraw")
        assertTrue("initial production fence was recorded: $firstFenceDiagnostics",
            firstVisual > 0 && firstCommittedDraw > 0)

        // P3: release NO_DATA; one recovery must run under the SAME transaction/deadline and its
        // fresh production visual/draw/commit fence, then scripted TIMEOUT closes it.
        assertTrue("budget remains before releasing first result",
            deadline - SystemClock.elapsedRealtime() > 300)
        val recoveryGate = factory.armNextCopy(700)
        firstCopyGate.release()
        assertFalse("first-copy gate released deliberately, not by timeout", firstCopyGate.timedOut)
        assertNotNull("single recovery backend copy invoked",
            factory.awaitCopyInvocation(700))
        assertTrue("recovery copy held for transaction observation",
            recoveryGate.awaitEntered(500))
        val recovery = captureAuthorityForR4(host)
        assertEquals("recovery remains same binding", initial.bindingIdentity, recovery.bindingIdentity)
        assertEquals("recovery remains same authority", initial.authoritySerial, recovery.authoritySerial)
        assertEquals("recovery remains same transaction", initial.transactionId, recovery.transactionId)
        assertEquals("recovery keeps original deadline", initial.transactionDeadline,
            recovery.transactionDeadline)
        assertTrue("transaction recovery budget is spent exactly once", recovery.recoveryUsed)
        assertTrue("exactly one Window copy is in flight at recovery observation",
            recovery.copyInFlight)
        assertEquals("exactly initial plus recovery at P3", 2, factory.copyInvocationCount())
        val recoveryFenceDiagnostics = runOnMainSync(hosting::captureDiagnostics)
        val recoveryVisual = diagnosticLong(recoveryFenceDiagnostics, "visual")
        val recoveryCommittedDraw = diagnosticLong(recoveryFenceDiagnostics, "committedDraw")
        assertTrue("recovery used a fresh visual request: $recoveryFenceDiagnostics",
            recoveryVisual > firstVisual)
        assertTrue("recovery used a fresh committed hardware draw: $recoveryFenceDiagnostics",
            recoveryCommittedDraw > firstCommittedDraw)
        assertTrue("recovery still has original readiness budget",
            deadline - SystemClock.elapsedRealtime() > 100)
        recoveryGate.release()
        assertFalse("recovery gate released deliberately, not by timeout", recoveryGate.timedOut)

        waitUntil("TIMEOUT closes failed readiness transaction before cleanup", {
            val a = captureAuthorityForR4(host)
            a.transactionId == 0L && a.terminal && !a.copyInFlight
        }, 700)
        val exhausted = captureAuthorityForR4(host)
        val diagnostics = runOnMainSync(hosting::captureDiagnostics)
        val copyDone = diagnosticLong(diagnostics, "copyDone")
        assertTrue("recovery TIMEOUT completed within original 2s readiness deadline: " +
            "copyDone=$copyDone deadline=$deadline diagnostics={$diagnostics}",
            copyDone in 1L..deadline)
        assertEquals("binding survives failed readiness without rearm",
            initial.bindingIdentity, exhausted.bindingIdentity)
        assertEquals("authority serial survives failed readiness",
            initial.authoritySerial, exhausted.authoritySerial)
        assertEquals("binding deadline was never reset", initial.bindingDeadline,
            exhausted.bindingDeadline)
        assertTrue("readiness remains unsuccessful", exhausted.readinessPending)
        assertTrue("failed readiness authority is terminal", exhausted.terminal)
        assertFalse("no backend copy remains in flight", exhausted.copyInFlight)
        assertFalse("failed transaction drops its old coalesced demand", exhausted.trailingDemand)
        assertTrue("lease remains live while exhaustion is observed",
            runOnMainSync(hosting::status).captureActive)
        assertEquals("hosting generation unchanged through failed transaction",
            generation, runOnMainSync(hosting::currentGeneration))
        assertFalse("surface remains attached before lease release/cleanup",
            runOnMainSync(hosting::privateDisplaySnapshot)!!.surfaceDetached)
        assertEquals("failed transaction published no frame", 0, consumer.count())
        assertEquals("exactly initial + one recovery copy", 2, factory.copyInvocationCount())
        assertEquals("backend copy requests were never concurrent", 1, factory.maxConcurrentCopyCalls())

        // P4/P5: challenge closure under the SAME live binding. The real scheduler sees terminal
        // authority and must stay inert: no attempt-zero transaction, no third copy, no frame.
        invokeProductionCaptureDemandForR4(host)
        val challenged = captureAuthorityForR4(host)
        assertEquals("closure challenge keeps same binding",
            initial.bindingIdentity, challenged.bindingIdentity)
        assertEquals("closure challenge keeps same authority",
            initial.authoritySerial, challenged.authoritySerial)
        assertEquals("terminal binding cannot mint new transaction", 0L, challenged.transactionId)
        assertTrue("terminal readiness remains unsuccessful", challenged.readinessPending)
        assertTrue("terminal authority remains terminal", challenged.terminal)
        assertFalse("closure challenge creates no in-flight copy", challenged.copyInFlight)
        assertFalse("terminal scheduler does not queue new trailing demand", challenged.trailingDemand)
        assertFalse("no third backend copy after terminal challenge",
            factory.awaitCopyInvocation(300) != null)
        assertEquals("total backend copies remain exactly two", 2, factory.copyInvocationCount())
        assertEquals("terminal challenge publishes no frame", 0, consumer.count())
        assertTrue("lease still live until explicit test release",
            runOnMainSync(hosting::status).captureActive)
        assertEquals("closure challenge cannot change hosting generation",
            generation, runOnMainSync(hosting::currentGeneration))

        runOnMain(lease!!::release)
    }

    /** T-B positive: one transient NO_DATA may recover exactly once to a real Window SUCCESS. */
    @Test
    fun singleTransientRecoveryCanSucceedWithinOriginalTransaction() {
        val factory = R4ControlledFactory().apply {
            scriptCopyResults(
                android.view.PixelCopy.ERROR_SOURCE_NO_DATA,
                R4ControlledFactory.REAL_COPY,
            )
        }
        val normal = prepareR4RecordedProfile(factory)
        val consumer = CollectingConsumer()
        consumer.expectQualification(normal.width, normal.height, CAPTURE_PAGE_COLOR)
        val deadline = SystemClock.elapsedRealtime() + 2_000
        val lease = runOnMainSync { hosting.acquireProfileLease(normal, deadline, consumer) }
        assertNotNull("recoverable profile lease", lease)
        waitUntil("recovery SUCCESS publishes current Window pixels", {
            consumer.qualifyingCountFrom(0) > 0
        }, 2_000)
        assertEquals("initial transient + one real recovery", 2, factory.copyInvocationCount())
        val diagnostics = runOnMainSync(hosting::captureDiagnostics)
        assertTrue("one recovery recorded: $diagnostics", diagnostics.contains("recoveries=1"))
        assertTrue("successful recovery retires readiness: $diagnostics",
            diagnostics.contains("readinessPending=false"))
        assertTrue("successful recovery does not terminally fail capture: $diagnostics",
            !diagnostics.contains("terminal=true"))
        runOnMain(lease!!::release)
    }

    /**
     * T-C / R-03: delay delivery of the REAL frame-commit callback while allowing another actual
     * draw in the same epoch. The later draw may become PixelCopy's newest same-context pixels,
     * but it must not advance the recorded commit association or HostingFrame freshness anchor.
     */
    @Test
    fun delayedSameEpochCommitKeepsFirstQualifyingDrawAnchor() {
        val factory = R4ControlledFactory()
        val normal = prepareR4RecordedProfile(factory)
        val host = currentPrivateHostForR4()
        val consumer = CollectingConsumer()
        consumer.expectQualification(normal.width, normal.height, CAPTURE_PAGE_COLOR)
        val delayedCommit = AtomicReference<Runnable>()
        val commitCaptured = CountDownLatch(1)
        runOnMain {
            session.captureCommitDispatcherForTest = { callback ->
                if (delayedCommit.compareAndSet(null, callback)) {
                    commitCaptured.countDown()
                } else {
                    callback.run()
                }
            }
        }
        val started = SystemClock.elapsedRealtime()
        val deadline = started + 2_000
        val lease = runOnMainSync { hosting.acquireProfileLease(normal, deadline, consumer) }
        assertNotNull("delayed-commit profile lease", lease)
        var leaseRetired = false
        try {
            assertTrue("real frame-commit callback captured",
                commitCaptured.await(700, TimeUnit.MILLISECONDS))
            val associated = runOnMainSync { host.drawObservationForTest() }
            assertTrue("qualifying draw observed", associated.serial > 0 && associated.elapsedMs > 0)

            // Hold only commit DELIVERY, not Main or the lease. Force one real same-document
            // traversal and observe it non-blockingly while substantial original-deadline budget
            // remains. This replaces the earlier over-deadline T-C execution retained in evidence.
            val interveningDraw = factory.observeNextDraw()
            evaluateJs(
                "(function(){document.body.style.paddingTop='17px';" +
                    "return document.body.getBoundingClientRect().height;})()"
            )
            runOnMain {
                session.view()!!.requestLayout()
                session.view()!!.invalidate()
                session.view()!!.postInvalidateOnAnimation()
            }
            assertTrue("intervening same-epoch hardware draw inside readiness transaction",
                interveningDraw.await(600, TimeUnit.MILLISECONDS))
            val later = runOnMainSync { host.drawObservationForTest() }
            assertTrue("test actually advanced the Presentation draw", later.serial > associated.serial)
            assertTrue("later draw has a later time", later.elapsedMs >= associated.elapsedMs)
            val remainingBeforeCommit = deadline - SystemClock.elapsedRealtime()
            assertTrue("held commit must be released with original readiness budget remaining: " +
                remainingBeforeCommit + "ms", remainingBeforeCommit > 200)

            runOnMain(checkNotNull(delayedCommit.get()))
            val remainingForPublication = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1)
            waitUntil("delayed commit publishes within ORIGINAL 2s readiness transaction", {
                consumer.qualifyingCountFrom(0) > 0
            }, remainingForPublication)
            assertTrue("publication stayed inside original deadline",
                consumer.latestDeliveryElapsed() <= deadline)
            assertEquals("freshness lower-bound remains the qualifying draw",
                associated.elapsedMs, consumer.latestCaptureElapsed())
            val diagnostics = runOnMainSync(hosting::captureDiagnostics)
            assertTrue("commit association remains first qualifying serial: $diagnostics",
                diagnostics.contains("committedDraw=" + associated.serial))
            assertTrue("latest observed draw advanced independently: $diagnostics",
                diagnostics.contains("draw=" + later.serial + "@"))

            // Retirement remains authoritative. Replaying the exact already-delivered callback
            // after lease/capture retirement cannot publish again or revive capture authority.
            val deliveredBeforeRetire = consumer.count()
            runOnMain(lease!!::release)
            leaseRetired = true
            waitUntilMain("T-C lease capture resources retire", {
                !hosting.captureResourcesPresent() && !hosting.status().captureActive
            })
            runOnMain(checkNotNull(delayedCommit.get()))
            runOnMain { } // Flush any callback work that could have been posted to Main.
            assertEquals("post-retirement commit replay is inert",
                deliveredBeforeRetire, consumer.count())
            assertFalse("post-retirement replay cannot revive capture",
                runOnMainSync(hosting::status).captureActive)
        } finally {
            runOnMain { session.captureCommitDispatcherForTest = null }
            if (!leaseRetired) runOnMain(lease!!::release)
        }
    }

    /** Static recorded regression stimulus; production profiles remain RG-measured. */
    private fun prepareR4RecordedProfile(factory: R4ControlledFactory): HostingPresentationProfile {
        runOnMain { hosting.setResourceFactoryForTest(factory) }
        openFixture("/hosting.html", "Hosting capture page")
        evaluateJs("window.__eyebrowseFreeze(true)")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val normal = HostingPresentationProfile(480, 344, 204)
        assertTrue("recorded R4 profile accepted",
            runOnMainSync { hosting.presentOnRg(normal) })
        waitUntilMain("R4 private local focus ready", { hosting.localEditorFocusReady() })
        awaitPrivateProfile()
        return normal
    }

    private data class R4CaptureAuthority(
        val bindingIdentity: Int,
        val authoritySerial: Long,
        val bindingDeadline: Long,
        val transactionId: Long,
        val transactionDeadline: Long,
        val recoveryUsed: Boolean,
        val readinessPending: Boolean,
        val trailingDemand: Boolean,
        val successorDemandAuthority: Long,
        val terminal: Boolean,
        val copyInFlight: Boolean,
    )

    /** Read-only androidTest reflection over EyeBrowse-owned transaction state. */
    private fun captureAuthorityForR4(host: PrivateDisplayHost): R4CaptureAuthority {
        val type = PrivateDisplayHost::class.java
        val lockField = type.getDeclaredField("nativeLock").apply { isAccessible = true }
        val bindingField = type.getDeclaredField("captureBinding").apply { isAccessible = true }
        val transactionField = type.getDeclaredField("activeCaptureTransaction").apply { isAccessible = true }
        val readinessField = type.getDeclaredField("captureReadinessPending").apply { isAccessible = true }
        val trailingField = type.getDeclaredField("trailingCaptureDemand").apply { isAccessible = true }
        val successorField = type.getDeclaredField("successorCaptureDemand").apply { isAccessible = true }
        val terminalField = type.getDeclaredField("captureTerminalFailure").apply { isAccessible = true }
        val copyField = type.getDeclaredField("inFlightWindowCopy").apply { isAccessible = true }
        val lock = checkNotNull(lockField.get(host))
        return synchronized(lock) {
            val binding = checkNotNull(bindingField.get(host)) { "capture binding unavailable" }
            val bindingType = binding.javaClass
            val authoritySerial = bindingType.getDeclaredField("authoritySerial").apply {
                isAccessible = true
            }.getLong(binding)
            val bindingDeadline = bindingType.getDeclaredField("readinessDeadlineElapsedMs").apply {
                isAccessible = true
            }.getLong(binding)
            val transaction = transactionField.get(host)
            val transactionId: Long
            val transactionDeadline: Long
            val recoveryUsed: Boolean
            if (transaction == null) {
                transactionId = 0L
                transactionDeadline = 0L
                recoveryUsed = false
            } else {
                val transactionType = transaction.javaClass
                transactionId = transactionType.getDeclaredField("id").apply {
                    isAccessible = true
                }.getLong(transaction)
                transactionDeadline = transactionType.getDeclaredField("deadlineElapsedMs").apply {
                    isAccessible = true
                }.getLong(transaction)
                recoveryUsed = transactionType.getDeclaredField("recoveryUsed").apply {
                    isAccessible = true
                }.getBoolean(transaction)
            }
            val successorBinding = successorField.get(host)
            val successorAuthority = if (successorBinding == null) {
                0L
            } else {
                successorBinding.javaClass.getDeclaredField("authoritySerial").apply {
                    isAccessible = true
                }.getLong(successorBinding)
            }
            R4CaptureAuthority(
                System.identityHashCode(binding),
                authoritySerial,
                bindingDeadline,
                transactionId,
                transactionDeadline,
                recoveryUsed,
                readinessField.getBoolean(host),
                trailingField.getBoolean(host),
                successorAuthority,
                terminalField.getBoolean(host),
                copyField.get(host) != null,
            )
        }
    }

    /** Fault-injection/scheduler evidence: invoke the actual production coalescing entry point. */
    private fun invokeProductionCaptureDemandForR4(host: PrivateDisplayHost) {
        val method = PrivateDisplayHost::class.java.getDeclaredMethod(
            "requestCaptureDemand",
            java.lang.Long.TYPE,
        )
        method.isAccessible = true
        method.invoke(host, SystemClock.elapsedRealtime())
    }

    private fun diagnosticLong(diagnostics: String, key: String): Long {
        val marker = "$key="
        val start = diagnostics.indexOf(marker)
        require(start >= 0) { "missing diagnostic $key in {$diagnostics}" }
        val valueStart = start + marker.length
        var end = valueStart
        while (end < diagnostics.length &&
            (diagnostics[end] == '-' || diagnostics[end].isDigit())) {
            end++
        }
        return diagnostics.substring(valueStart, end).toLong()
    }

    private fun currentPrivateHostForR4(): PrivateDisplayHost = runOnMainSync {
        val field = HostingController::class.java.getDeclaredField("displayHost")
        field.isAccessible = true
        checkNotNull(field.get(hosting) as? PrivateDisplayHost)
    }

    private fun handlerFieldForR4(host: PrivateDisplayHost, name: String): Handler {
        val field = PrivateDisplayHost::class.java.getDeclaredField(name)
        field.isAccessible = true
        return checkNotNull(field.get(host) as? Handler) { "$name unavailable" }
    }

    private fun inFlightWindowCopyForR4(host: PrivateDisplayHost): Boolean {
        val lockField = PrivateDisplayHost::class.java.getDeclaredField("nativeLock")
        lockField.isAccessible = true
        val copyField = PrivateDisplayHost::class.java.getDeclaredField("inFlightWindowCopy")
        copyField.isAccessible = true
        val lock = checkNotNull(lockField.get(host))
        return synchronized(lock) { copyField.get(host) != null }
    }

    private fun expectedPrivateSize(): IntArray = runOnMainSync {
        val profile = hosting.presentationProfile()
        intArrayOf(profile.width, profile.height)
    }

    private fun awaitPrivateProfile(): PrivateDisplayHost.DisplaySnapshot {
        val profile = runOnMainSync(hosting::presentationProfile)
        waitUntil("private display, reader and WebView match epoch profile", {
            runOnMainSync {
                val snapshot = hosting.privateDisplaySnapshot()
                val view = session.view()
                hosting.status().attachment == HostingController.Attachment.PRIVATE_DISPLAY &&
                    snapshot != null && snapshot.valid && snapshot.state == android.view.Display.STATE_ON &&
                    snapshot.width == profile.width && snapshot.height == profile.height &&
                    snapshot.actualWidth == profile.width && snapshot.actualHeight == profile.height &&
                    snapshot.readerWidth == profile.width && snapshot.readerHeight == profile.height &&
                    snapshot.presentationContextDisplayId == snapshot.displayId &&
                    view != null && view.width == profile.width && view.height == profile.height &&
                    view.display?.displayId == snapshot.displayId
            }
        })
        return runOnMainSync { hosting.privateDisplaySnapshot()!! }
    }

    private fun awaitPhoneContent() {
        waitUntil("Phone owner uses fresh Phone content bounds", {
            runOnMainSync {
                val view = session.view()
                val container = view?.parent as? android.view.ViewGroup
                view != null && container != null && view.display?.displayId == 0 &&
                    hosting.status().attachment == HostingController.Attachment.PHONE_UI &&
                    !view.isLayoutRequested && !container.isLayoutRequested &&
                    view.width == container.width - container.paddingLeft - container.paddingRight &&
                    view.height == container.height - container.paddingTop - container.paddingBottom
            }
        })
    }

    private fun currentWebViewSize(): IntArray {
        return runOnMainSync(
            callback117@{
                val view: WebView? = session.view()
                return@callback117 (if (view == null) intArrayOf(0, 0)
                else intArrayOf(view.getWidth(), view.getHeight()))
            }
        )
    }

    private fun openFixture(path: String, expectedTitle: String) {
        val url: String = (FIXTURE_BASE + path)
        onView(withId(R.id.address_input)).perform(click(), replaceText(url))
        onView(withId(R.id.button_open)).perform(click())
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        try {
            waitUntil(("fixture page " + path), { expectedTitle.equals(domText("page-title")) })
        } catch (failure: AssertionError) {
            var status: HostingController.Status = runOnMainSync(hosting::status)
            fail(
                (((((((((((((failure.message).toString() + " (displayUrl=") +
                                            runOnMainSync(session::displayUrl))
                                        .toString() + " loading=") +
                                        runOnMainSync(session::isLoading))
                                    .toString() + " error=") + runOnMainSync(session::errorMessage))
                                .toString() + " title=") + domTextOrNull("page-title"))
                            .toString() + " state=") + status.state)
                        .toString() + " attachment=") + status.attachment)
                    .toString() + ")"
            )
        }
    }

    private fun domTextOrNull(elementId: String): String? {
        try {
            return domText(elementId)
        } catch (e: RuntimeException) {
            return "(unreadable)"
        } catch (e: AssertionError) {
            return "(unreadable)"
        }
    }

    private fun awaitHostingState(
        state: HostingController.State,
        boundMs: Long,
    ): HostingController.Status {
        val deadline: Long = (SystemClock.uptimeMillis() + boundMs)
        val reached: AtomicReference<HostingController.Status> = AtomicReference()
        while (SystemClock.uptimeMillis() < deadline) {
            var status: HostingController.Status = runOnMainSync(hosting::status)
            if (status.state == state) {
                reached.set(status)
                return status
            }
            SystemClock.sleep(50)
        }
        val last: HostingController.Status = runOnMainSync(hosting::status)
        fail(
            ((((((("hosting never reached " + state).toString() + " within ") + boundMs)
                        .toString() + "ms (last: ") + last.state)
                    .toString() + ", reason: ") + last.failureReason)
                .toString() + ")"
        )
        return reached.get()
    }

    private fun waitUntilMain(description: String, condition: BooleanSupplier) {
        waitUntil(description, { runOnMainSync(condition::getAsBoolean) })
    }

    /** One coherent main-thread snapshot; never call this from inside a main-sync block. */
    private fun hostViewSnapshot(): HostViewSnapshot {
        return runOnMainSync(
            callback120@{
                val view: WebView? = session.view()
                return@callback120 HostViewSnapshot(
                    hosting.status(),
                    ((view != null) && (view.getParent() != null)),
                )
            }
        )
    }

    private class HostViewSnapshot {
        val status: HostingController.Status

        val viewAttached: Boolean

        constructor(status: HostingController.Status, viewAttached: Boolean) {
            this.status = status
            this.viewAttached = viewAttached
        }
    }

    private fun waitUntil(description: String, condition: BooleanSupplier) {
        waitUntil(description, condition, TIMEOUT_MS)
    }

    private fun waitUntil(description: String, condition: BooleanSupplier, timeoutMs: Long) {
        val deadline: Long = (SystemClock.uptimeMillis() + timeoutMs)
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return
                }
            } catch (ignored: RuntimeException) {} catch (ignored: AssertionError) {}

            SystemClock.sleep(100)
        }
        var status: HostingController.Status = runOnMainSync(hosting::status)
        val webSize: IntArray = currentWebViewSize()
        val capture: String = runOnMainSync(hosting::captureDiagnostics)
        val windowFacts: String =
            runOnMainSync(
                callback121@{
                    val view: WebView? = session.view()
                    if (view == null) {
                        return@callback121 "view=none"
                    }
                    val orientation: Int = view.getResources().getConfiguration().orientation
                    val rotation: Int =
                        (if (view.getDisplay() == null) -1 else view.getDisplay().getRotation())
                    return@callback121 ((("configOrientation=" + orientation).toString() +
                        " displayRotation=") + rotation)
                }
            )
        fail(
            ((((((((((((((((((((("timed out waiting for " + description).toString() + " (state=") +
                                                        status.state)
                                                    .toString() + " attachment=") +
                                                    status.attachment)
                                                .toString() + " browserLive=") + status.browserLive)
                                            .toString() + " gen=") + status.generation)
                                        .toString() + " reason=") + status.failureReason)
                                    .toString() + " parentless=") + webViewParentless())
                                .toString() + " webSize=") + webSize[0])
                            .toString() + "x") + webSize[1])
                        .toString() + " window={") + windowFacts)
                    .toString() + "} capture={") + capture)
                .toString() + "})"
        )
    }

    private fun runOnMain(runnable: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable)
    }

    private fun <T> runOnMainSync(supplier: java.util.function.Supplier<T>): T {
        val result: AtomicReference<T> = AtomicReference()
        InstrumentationRegistry.getInstrumentation().runOnMainSync({ result.set(supplier.get()) })
        return result.get()
    }

    private fun webViewIdentityHash(): Int {
        val identity: Int? =
            runOnMainSync(
                callback123@{
                    val view: WebView? = session.view()
                    return@callback123 (if (view == null) 0 else System.identityHashCode(view))
                }
            )
        assertNotNull(identity)
        assertTrue("no live WebView to track", (identity != 0))
        return identity!!
    }

    private fun webViewParentless(): Boolean {
        val parentless: Boolean =
            runOnMainSync(
                callback124@{
                    val view: WebView? = session.view()
                    return@callback124 ((view == null) || (view.getParent() == null))
                }
            )
        return true.equals(parentless)
    }

    private fun domText(elementId: String): String? {
        val result: String? =
            evaluateJs(
                ("(function(){var el=document.getElementById('" + elementId).toString() +
                    "');return el ? el.textContent : null;})()"
            )
        return decode(result)
    }

    private fun readFieldValue(): String? {
        return decode(evaluateJs("document.getElementById('hosting-field').value"))
    }

    private fun setFieldValue(value: String) {
        evaluateJs(
            ((("(function(){var el=document.getElementById('hosting-field');" + "el.value=") +
                        JSONObject.quote(value))
                    .toString() + ";")
                .toString() +
                "el.dispatchEvent(new Event('input',{bubbles:true}));return el.value;})()"
        )
    }

    private fun evaluateJs(expression: String): String? {
        val view: WebView? = runOnMainSync(session::view)
        if (view == null) {
            throw IllegalStateException("no live WebView to evaluate against")
        }
        val raw: AtomicReference<String> = AtomicReference()
        val latch: java.util.concurrent.CountDownLatch = java.util.concurrent.CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation()
            .runOnMainSync({
                view.evaluateJavascript(
                    expression,
                    { value ->
                        raw.set(value)
                        latch.countDown()
                    },
                )
            })
        try {
            assertTrue(
                "javascript evaluation timed out",
                latch.await(10_000, java.util.concurrent.TimeUnit.MILLISECONDS),
            )
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("interrupted while evaluating javascript", interrupted)
        }
        return raw.get()
    }

    private fun loadCount(path: String): Int {
        val url: java.net.URL = java.net.URL((FIXTURE_BASE).toString() + "/api/observations")
        url.openStream().use { `in` ->
            val out: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream()
            val buffer: ByteArray = ByteArray(4096)
            var read: Int
            while (true) {
                read = `in`.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
            }
            val observations: JSONObject = JSONObject(out.toString("UTF-8"))
            val loads: JSONObject? = observations.optJSONObject("loads")
            return (if (loads == null) 0 else loads.optInt(path, 0))
        }
    }

    /**
     * Collects per-frame delivery metadata and one sampled delivered pixel (never bitmaps) from the
     * capture thread. The pixel sample is read from the delivered borrowed bitmap during the
     * callback, tying the oracle to actual delivered content, and each callback is classified
     * through {@link OutputQualification} against the expected current document/geometry: raw
     * initialization callbacks may be non-qualifying, and the earliest VALID callback is selected
     * from the retained observations.
     */
    private class CollectingConsumer : HostingController.FrameConsumer {
        private val frames: MutableList<LongArray> = java.util.ArrayList()

        private val observations: MutableList<OutputQualification.Observation> = ArrayList()

        private var expectedWidth: Int = 0

        private var expectedHeight: Int = 0

        private var expectedColor: Int = 0

        /**
         * Sets the expected current document/geometry used to qualify each callback as it arrives;
         * call before the callbacks that should be classified.
         */
        @Synchronized
        fun expectQualification(width: Int, height: Int, color: Int) {
            expectedWidth = width
            expectedHeight = height
            expectedColor = color
        }

        override fun onFrame(frame: HostingFrame) {
            onFrameAt(frame, SystemClock.uptimeMillis(), SystemClock.elapsedRealtime())
        }

        /**
         * Records one delivered callback with an explicit delivery time. The delayed consumer
         * passes the actual callback ENTRY time so a test-controlled hold never restamps delivery
         * after the fact.
         */
        @Synchronized
        fun onFrameAt(frame: HostingFrame, deliveryUptimeMs: Long, deliveryElapsedMs: Long) {
            val sampledColor: Int = frame.bitmap.getPixel(10, 10)
            frames.add(
                longArrayOf(
                    (frame.sequence).toLong(),
                    (frame.contentHash).toLong(),
                    (frame.captureElapsedMs).toLong(),
                    (frame.generation).toLong(),
                    (frame.width).toLong(),
                    (frame.height).toLong(),
                    (sampledColor).toLong(),
                    (deliveryElapsedMs).toLong(),
                )
            )
            observations.add(
                OutputQualification.observe(
                    deliveryUptimeMs,
                    deliveryElapsedMs,
                    frame.width,
                    frame.height,
                    frame.generation,
                    frame.sequence,
                    frame.contentHash,
                    sampledColor,
                    expectedWidth,
                    expectedHeight,
                    expectedColor,
                )
            )
        }

        @Synchronized
        fun count(): Int {
            return frames.size
        }

        @Synchronized
        fun qualifyingCountFrom(fromIndex: Int): Int {
            var count: Int = 0
            var i: Int = Math.max(0, fromIndex)
            while (i < observations.size) {
                if (observations.get(i).qualified) {
                    count++
                }
                i++
            }
            return count
        }

        @Synchronized
        fun earliestQualifyingIndexFrom(fromIndex: Int): Int {
            return OutputQualification.earliestQualifyingIndex(observations, fromIndex)
        }

        @Synchronized
        fun earliestQualifyingDelayMsFrom(fromIndex: Int, eligibleUptimeMs: Long): Long {
            return OutputQualification.earliestQualifyingDelayMs(
                observations,
                fromIndex,
                eligibleUptimeMs,
            )
        }

        @Synchronized
        fun qualificationSummary(fromIndex: Int, eligibleUptimeMs: Long): String {
            return OutputQualification.summary(observations, fromIndex, eligibleUptimeMs)
        }

        @Synchronized
        fun deliveryElapsedAt(index: Int): Long {
            return frames.get(index)[7]
        }

        @Synchronized
        fun latestCaptureElapsed(): Long {
            return (if (frames.isEmpty()) Long.MIN_VALUE else frames.get(frames.size - 1)[2])
        }

        @Synchronized
        fun latestDeliveryElapsed(): Long {
            return (if (frames.isEmpty()) Long.MIN_VALUE else frames.get(frames.size - 1)[7])
        }

        @Synchronized
        fun distinctHashes(): Int {
            return (frames.stream().mapToLong({ f -> f[1] }).distinct().count()).toInt()
        }

        @Synchronized
        fun allFramesMatchGeneration(generation: Int): Boolean {
            return frames.stream().allMatch({ f -> (f[3] == generation.toLong()) })
        }

        @Synchronized
        fun allFramesMatchSize(width: Int, height: Int): Boolean {
            return frames
                .stream()
                .allMatch({ f -> ((f[4] == width.toLong()) && (f[5] == height.toLong())) })
        }

        /** True when frames from {@code fromIndex} on all match the given viewport. */
        @Synchronized
        fun tailFramesMatchSize(fromIndex: Int, width: Int, height: Int): Boolean {
            var i: Int = Math.max(0, fromIndex)
            while (i < frames.size) {
                val frame: LongArray = frames.get(i)
                if ((frame[4] != width.toLong()) || (frame[5] != height.toLong())) {
                    return false
                }
                i++
            }
            return true
        }

        @Synchronized
        fun monotonicCaptureStamps(): Boolean {
            var i: Int = 1
            while (i < frames.size) {
                if (frames.get(i)[2] < frames.get(i - 1)[2]) {
                    return false
                }
                i++
            }
            return true
        }

        /** True when frames from {@code fromIndex} on all match the expected sampled pixel. */
        @Synchronized
        fun tailFramesNearColor(fromIndex: Int, expectedColor: Int): Boolean {
            var any: Boolean = false
            var i: Int = Math.max(0, fromIndex)
            while (i < frames.size) {
                any = true
                if (!nearColor((frames.get(i)[6]).toInt(), expectedColor)) {
                    return false
                }
                i++
            }
            return any
        }

        /** True when the latest delivered frame's sampled pixel matches the expected color. */
        @Synchronized
        fun latestFrameNearColor(expectedColor: Int): Boolean {
            if (frames.isEmpty()) {
                return false
            }
            return nearColor((frames.get(frames.size - 1)[6]).toInt(), expectedColor)
        }

        /** True when every frame delivered in the last {@code windowMs} matches the color. */
        @Synchronized
        fun recentFramesNearColor(expectedColor: Int, windowMs: Long): Boolean {
            val cutoff: Long = (SystemClock.elapsedRealtime() - windowMs)
            var any: Boolean = false
            for (frame in frames) {
                if (frame[7] >= cutoff) {
                    any = true
                    if (!nearColor((frame[6]).toInt(), expectedColor)) {
                        return false
                    }
                }
            }
            return any
        }

        @Synchronized
        fun allFramesNearColor(expectedColor: Int): Boolean {
            return frames.stream().allMatch({ f -> nearColor((f[6]).toInt(), expectedColor) })
        }

        @Synchronized
        fun distinctHashSet(): MutableSet<Long> {
            val hashes: MutableSet<Long> = HashSet()
            for (frame in frames) {
                hashes.add(frame[1])
            }
            return hashes
        }
    }

    /**
     * Wraps a {@link CollectingConsumer} with an in-callback hold, occupying the borrowed bitmap
     * across release/reacquire so the isolation property is exercised, not assumed (R2). The
     * entered latch is a controlled barrier: the test observes that the consumer actually entered
     * its callback before driving the ownership transition.
     */
    private class DelayedConsumer : HostingController.FrameConsumer {
        private val collector: CollectingConsumer = CollectingConsumer()

        private val entered: java.util.concurrent.CountDownLatch =
            java.util.concurrent.CountDownLatch(1)

        private val released: java.util.concurrent.CountDownLatch =
            java.util.concurrent.CountDownLatch(1)

        private val entrySnapshot:
            java.util.concurrent.atomic.AtomicReference<CallbackIntegrity.Snapshot> =
            java.util.concurrent.atomic.AtomicReference()

        private val postHoldSnapshot:
            java.util.concurrent.atomic.AtomicReference<CallbackIntegrity.Snapshot> =
            java.util.concurrent.atomic.AtomicReference()

        private @Volatile var holdFailed: Boolean = false

        private @Volatile var integrityFailure: String? = null

        override fun onFrame(frame: HostingFrame) {
            val entryUptimeMs: Long = SystemClock.uptimeMillis()
            val entryElapsedMs: Long = SystemClock.elapsedRealtime()
            var entry: CallbackIntegrity.Snapshot?
            try {
                entry = sample(frame, entryUptimeMs, entryElapsedMs)
            } catch (sampleError: RuntimeException) {
                integrityFailure = ("entry sample failed: " + sampleError)
                holdFailed = true
                entered.countDown()
                released.countDown()
                return
            }
            if (entry == null) {
                integrityFailure = "entry sample unavailable (missing/zero-size/recycled bitmap)"
                holdFailed = true
                entered.countDown()
                released.countDown()
                return
            }
            entrySnapshot.set(entry)
            entered.countDown()
            try {
                if (!released.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    holdFailed = true
                    integrityFailure = "hold timeout after 2000ms (post-hold sample not taken)"
                    return
                }
            } catch (interrupted: InterruptedException) {
                holdFailed = true
                integrityFailure = "hold interrupted before the post-hold sample"
                Thread.currentThread().interrupt()
                return
            }
            var postHold: CallbackIntegrity.Snapshot?
            try {
                postHold = sample(frame, entryUptimeMs, entryElapsedMs)
            } catch (sampleError: RuntimeException) {
                integrityFailure = ("post-hold sample failed: " + sampleError)
                holdFailed = true
                return
            }
            if (postHold == null) {
                integrityFailure = "post-hold sample unavailable"
                holdFailed = true
                return
            }
            postHoldSnapshot.set(postHold)
            if (!entry!!.sameContent(postHold!!)) {
                integrityFailure =
                    (((("borrowed bitmap changed while held: entry [" + entry.summary())
                            .toString() + "] vs post-hold [") + postHold.summary())
                        .toString() + "]")
                holdFailed = true
                return
            }
            collector.onFrameAt(frame, entryUptimeMs, entryElapsedMs)
        }

        fun collector(): CollectingConsumer {
            return collector
        }

        fun entrySnapshot(): CallbackIntegrity.Snapshot? {
            return entrySnapshot.get()
        }

        fun postHoldSnapshot(): CallbackIntegrity.Snapshot? {
            return postHoldSnapshot.get()
        }

        fun integrityFailure(): String? {
            return integrityFailure
        }

        fun awaitEntered(timeoutMs: Long): Boolean {
            return entered.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }

        fun releaseHold() {
            released.countDown()
        }

        fun holdFailed(): Boolean {
            return holdFailed
        }

        companion object {
            /**
             * Copies whole-valid-bitmap facts from the actual borrowed bitmap; never retains it.
             */
            @JvmStatic
            private fun sample(
                frame: HostingFrame,
                entryUptimeMs: Long,
                entryElapsedMs: Long,
            ): CallbackIntegrity.Snapshot? {
                val bitmap: android.graphics.Bitmap? = frame.bitmap
                if (
                    (((bitmap == null) || bitmap.isRecycled()) || (bitmap.getWidth() <= 0)) ||
                        (bitmap.getHeight() <= 0)
                ) {
                    return null
                }
                val width: Int = bitmap.getWidth()
                val height: Int = bitmap.getHeight()
                val pixels: IntArray = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                return CallbackIntegrity.of(
                    entryUptimeMs,
                    entryElapsedMs,
                    frame.generation,
                    frame.sequence,
                    frame.width,
                    frame.height,
                    width,
                    height,
                    java.lang.String.valueOf(bitmap.getConfig()),
                    pixels,
                    pixels.size,
                )
            }
        }
    }

    /**
     * Holds only product callback DELIVERY. The platform/scripted request completes and Main
     * returns normally; release() later posts the original listener back to its original handler.
     */
    private class R4CompletionHold {
        private val captured = CountDownLatch(1)
        private val pending = AtomicReference<Runnable?>()
        private val released = AtomicBoolean(false)
        @Volatile private var destination: android.graphics.Bitmap? = null
        @Volatile var result: Int = Int.MIN_VALUE
            private set

        fun bindDestination(bitmap: android.graphics.Bitmap) {
            destination = bitmap
        }

        fun wrap(
            delegate: android.view.PixelCopy.OnPixelCopyFinishedListener,
            handler: Handler,
        ): android.view.PixelCopy.OnPixelCopyFinishedListener =
            android.view.PixelCopy.OnPixelCopyFinishedListener { value ->
                result = value
                val delivery = Runnable {
                    handler.post { delegate.onPixelCopyFinished(value) }
                }
                pending.set(delivery)
                captured.countDown()
                if (released.get()) pending.getAndSet(null)?.run()
            }

        fun awaitCaptured(timeoutMs: Long): Boolean =
            captured.await(timeoutMs, TimeUnit.MILLISECONDS)

        fun destinationBitmap(): android.graphics.Bitmap? = destination

        fun release() {
            released.set(true)
            pending.getAndSet(null)?.run()
        }
    }

    /** Test-only deterministic barriers for I9-T01 FW2; never used by production admission. */
    private class R4Gate(private val holdTimeoutMs: Long = 1_500) {
        val entered = CountDownLatch(1)
        private val releaseLatch = CountDownLatch(1)
        private val claimed = AtomicBoolean(false)
        @Volatile var timedOut: Boolean = false
            private set

        fun asRunnable(): Runnable = Runnable { blockOnce() }

        fun blockOnce() {
            if (!claimed.compareAndSet(false, true)) return
            entered.countDown()
            try {
                if (!releaseLatch.await(holdTimeoutMs, TimeUnit.MILLISECONDS)) timedOut = true
            } catch (interrupted: InterruptedException) {
                timedOut = true
                Thread.currentThread().interrupt()
            }
        }

        fun awaitEntered(timeoutMs: Long): Boolean =
            entered.await(timeoutMs, TimeUnit.MILLISECONDS)

        fun release() {
            releaseLatch.countDown()
        }
    }

    /**
     * Real platform factory with test-only observation/control: retain reader object identities
     * and delay one actual Presentation draw after the product listener has observed it.
     */
    private class R4ControlledFactory : PrivateDisplayHost.Factory {
        private val platform = PrivateDisplayHost.PlatformFactory()
        private val readers = CopyOnWriteArrayList<ImageReader>()
        private val scriptedCopyResults = ConcurrentLinkedQueue<Int>()
        private val copyEvents = LinkedBlockingQueue<Int>()
        private val copyCount = AtomicInteger(0)
        private val activeCopyCalls = AtomicInteger(0)
        private val maxConcurrentCopyCalls = AtomicInteger(0)
        @Volatile private var nextDrawGate: R4Gate? = null
        @Volatile private var nextDrawObserved: CountDownLatch? = null
        @Volatile private var nextCopyGate: R4Gate? = null
        @Volatile private var nextCompletionHold: R4CompletionHold? = null

        fun armNextDraw(): R4Gate = R4Gate().also { nextDrawGate = it }

        /** Observe one real Presentation draw without blocking Main. */
        fun observeNextDraw(): CountDownLatch =
            CountDownLatch(1).also { nextDrawObserved = it }

        fun armNextCopy(holdTimeoutMs: Long = 1_500): R4Gate =
            R4Gate(holdTimeoutMs).also { nextCopyGate = it }

        fun holdNextCopyCompletion(): R4CompletionHold =
            R4CompletionHold().also { nextCompletionHold = it }

        fun scriptCopyResults(vararg results: Int) {
            results.forEach(scriptedCopyResults::add)
        }

        fun awaitCopyInvocation(timeoutMs: Long): Int? =
            copyEvents.poll(timeoutMs, TimeUnit.MILLISECONDS)

        fun copyInvocationCount(): Int = copyCount.get()

        fun maxConcurrentCopyCalls(): Int = maxConcurrentCopyCalls.get()

        fun latestReader(): ImageReader = checkNotNull(readers.lastOrNull())

        override fun createVirtualDisplay(
            manager: DisplayManager,
            name: String,
            width: Int,
            height: Int,
            densityDpi: Int,
            surface: Any?,
        ): VirtualDisplay? =
            platform.createVirtualDisplay(manager, name, width, height, densityDpi, surface)

        override fun createImageReader(width: Int, height: Int): ImageReader? =
            platform.createImageReader(width, height)?.also(readers::add)

        override fun requestWindowCopy(
            window: android.view.Window,
            sourceRect: android.graphics.Rect,
            destination: android.graphics.Bitmap,
            listener: android.view.PixelCopy.OnPixelCopyFinishedListener,
            handler: Handler,
        ) {
            val active = activeCopyCalls.incrementAndGet()
            maxConcurrentCopyCalls.updateAndGet { previous -> maxOf(previous, active) }
            try {
                val invocation = copyCount.incrementAndGet()
                copyEvents.offer(invocation)
                val gate = nextCopyGate
                if (gate != null && nextCopyGate === gate) {
                    nextCopyGate = null
                    gate.blockOnce()
                }
                val hold = nextCompletionHold
                val completionListener =
                    if (hold != null && nextCompletionHold === hold) {
                        nextCompletionHold = null
                        hold.bindDestination(destination)
                        hold.wrap(listener, handler)
                    } else listener
                val scripted = scriptedCopyResults.poll()
                if (scripted == null || scripted == REAL_COPY) {
                    platform.requestWindowCopy(
                        window, sourceRect, destination, completionListener, handler,
                    )
                } else {
                    handler.post { completionListener.onPixelCopyFinished(scripted) }
                }
            } finally {
                activeCopyCalls.decrementAndGet()
            }
        }

        override fun createPresentation(
            context: Context,
            display: android.view.Display,
        ): PrivateDisplayHost.PresentationHost {
            val delegate = platform.createPresentation(context, display)
            return object : PrivateDisplayHost.PresentationHost by delegate {
                override fun setDrawListener(listener: PrivateDisplayHost.DrawListener?) {
                    if (listener == null) {
                        delegate.setDrawListener(null)
                        return
                    }
                    delegate.setDrawListener(PrivateDisplayHost.DrawListener { serial, elapsedMs ->
                        listener.onDraw(serial, elapsedMs)
                        val observed = nextDrawObserved
                        if (observed != null && nextDrawObserved === observed) {
                            nextDrawObserved = null
                            observed.countDown()
                        }
                        val gate = nextDrawGate
                        if (gate != null && nextDrawGate === gate) {
                            nextDrawGate = null
                            gate.blockOnce()
                        }
                    })
                }
            }
        }

        companion object {
            /** Sentinel means delegate to the real public Window PixelCopy path. */
            const val REAL_COPY: Int = Int.MIN_VALUE
        }
    }

    /**
     * Platform factory with an injectable, recoverable reader-recreation failure for the R7
     * rollback/failure-surfacing case; otherwise fully delegating.
     */
    private class SettableFactory : PrivateDisplayHost.Factory {
        val platform: PrivateDisplayHost.PlatformFactory = PrivateDisplayHost.PlatformFactory()

        @Volatile var throwOnNextReader: Boolean = false

        override fun createVirtualDisplay(
            manager: DisplayManager,
            name: String,
            width: Int,
            height: Int,
            densityDpi: Int,
            surface: Any?,
        ): VirtualDisplay? {
            return platform.createVirtualDisplay(manager, name, width, height, densityDpi, surface)
        }

        override fun createImageReader(width: Int, height: Int): ImageReader? {
            if (throwOnNextReader) {
                throw IllegalStateException("injected reader recreation failure")
            }
            return platform.createImageReader(width, height)
        }

        override fun createPresentation(
            context: Context,
            display: android.view.Display,
        ): PrivateDisplayHost.PresentationHost {
            return platform.createPresentation(context, display)
        }
    }

    /**
     * Renews the lease every second from a test thread; {@code renew()} posts to the main thread.
     */
    private inner class LeaseRenewal : Thread {
        private val lease: HostingController.Lease

        private @Volatile var running: Boolean = true

        constructor(lease: HostingController.Lease) : super() {
            this.lease = lease
            ownedRenewals.add(this)
            setDaemon(true)
        }

        override fun run() {
            while (running) {
                lease.renew()
                try {
                    Thread.sleep(1_000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        fun stopRenewing() {
            running = false
            interrupt()
            join(2_000)
            assertFalse("renewal producer terminated before the final anchor", isAlive())
        }
    }

    /** Records the actual interactive/lock state; screen-off is never claimed as secure lock. */
    private fun recordDeviceState(label: String) {
        val context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val power: PowerManager = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        val keyguard: KeyguardManager =
            (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
        val line: String =
            ((((((("DEVICE_STATE " + label).toString() + " interactive=") + power.isInteractive())
                    .toString() + " deviceLocked=") + keyguard.isDeviceLocked())
                .toString() + " keyguardRestricted=") + keyguard.inKeyguardRestrictedInputMode())
        println(line)
        milestones!!.record(line)
    }

    /** Same-process memory milestone; no process reset occurs between milestones!!. */
    private fun memoryMilestone(label: String) {
        val nativeHeap: Long = (android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576)
        val runtime: Runtime = Runtime.getRuntime()
        val javaUsed: Long = ((runtime.totalMemory() - runtime.freeMemory()) / 1_048_576)
        val line: String =
            ((((("MEMORY_MILESTONE " + label).toString() + " nativeHeapMB=") + nativeHeap)
                .toString() + " javaUsedMB=") + javaUsed)
        println(line)
        milestones!!.record(line)
    }

    companion object {
        private val DEFAULT_FIXTURE_BASE: String = "http://127.0.0.1:25341"

        private val FIXTURE_BASE: String =
            trimTrailingSlash(
                InstrumentationRegistry.getArguments()
                    .getString("fixtureBaseUrl", DEFAULT_FIXTURE_BASE)
            )

        private val START_BOUND_MS: Long = 5_000

        private val STOP_BOUND_MS: Long = 5_000

        private val TIMEOUT_MS: Long = 20_000

        /** Fixture page background colors, used for delivered-pixel content correlation. */
        private val CAPTURE_PAGE_COLOR: Int = Color.parseColor("#f6f3ea")

        private val SECOND_PAGE_COLOR: Int = Color.parseColor("#2e5f8a")

        private var milestones: MilestoneSink? = null

        /** Explicit NOT_APPLICABLE report guard for API<33 (once per class). */
        private var notificationPermissionNotApplicableReported: Boolean = false

        /**
         * Recorded setup outcome for this class: applicability/original-state/changed/uncertain.
         */
        private var notificationPermissionOutcome: NotificationPermissionPolicy.SetupOutcome =
            NotificationPermissionPolicy.SetupOutcome.NOT_RUN

        /**
         * Cleanup applicability: only a verified grant mutation made by this class is restored.
         * Below API 33 there is no runtime permission to restore; a failed/uncertain change is
         * reported through {@link #notificationPermissionSetupUncertain()} but is never claimed as
         * a verified mutation.
         */
        @JvmStatic
        fun notificationPermissionNeedsCleanupRestore(): Boolean {
            return NotificationPermissionPolicy.needsRestore(
                android.os.Build.VERSION.SDK_INT,
                notificationPermissionOutcome,
            )
        }

        /** True when a setup attempt could not be verified; callers must report, never assume. */
        @JvmStatic
        fun notificationPermissionSetupUncertain(): Boolean {
            return NotificationPermissionPolicy.isUncertain(notificationPermissionOutcome)
        }

        @AfterClass
        @JvmStatic
        fun flushMilestoneSink() {
            if (milestones != null) {
                milestones!!.flushToStream("hosting correction round 3 execution")
            }
        }

        @JvmStatic
        private fun decode(raw: String?): String? {
            if ((raw == null) || "null".equals(raw)) {
                return null
            }
            try {
                val value: Any = JSONObject(("{\"v\":" + raw).toString() + "}").get("v")
                return (if (value === JSONObject.NULL) null else java.lang.String.valueOf(value))
            } catch (e: org.json.JSONException) {
                return raw
            }
        }

        @JvmStatic
        private fun trimTrailingSlash(value: String): String {
            return (if (value.endsWith("/")) value.substring(0, (value.length - 1)) else value)
        }

        /** Channel-wise comparison with tolerance (same helper used by callback qualification). */
        @JvmStatic
        private fun nearColor(actual: Int, expected: Int): Boolean {
            return OutputQualification.nearColor(actual, expected)
        }
    }
}
