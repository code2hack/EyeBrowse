package com.code2hack.eyebrowse.phone;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Focused hosting-lifecycle instrumentation (correction rounds 2–3).
 *
 * <p>Evidence boundaries: activation uses the real Start/Stop controls through single verified
 * system-pipeline taps (bounded direct-callback activation is a separately labeled diagnostic);
 * page reads and fixture field/storage setup use {@link WebView#evaluateJavascript} (fixture
 * setup, not typing evidence); backgrounding uses {@code moveTaskToBack}, which proves the
 * Activity lifecycle path but is not a physical Home press or secure-lock observation. Delivered
 * pixels are sampled per frame in the consumer callback so content correlation uses the actually
 * delivered bitmap, and state/clock/resource milestones are persisted app-scoped while produced
 * (not from a rotating logcat tail).
 */
@RunWith(AndroidJUnit4.class)
public class HostingInstrumentedTest {

    private static final String DEFAULT_FIXTURE_BASE = "http://127.0.0.1:25341";
    private static final String FIXTURE_BASE = trimTrailingSlash(
            InstrumentationRegistry.getArguments().getString("fixtureBaseUrl", DEFAULT_FIXTURE_BASE));
    private static final long START_BOUND_MS = 5_000;
    private static final long STOP_BOUND_MS = 5_000;
    private static final long TIMEOUT_MS = 20_000;

    /** Fixture page background colors, used for delivered-pixel content correlation. */
    private static final int CAPTURE_PAGE_COLOR = Color.parseColor("#f6f3ea");
    private static final int SECOND_PAGE_COLOR = Color.parseColor("#2e5f8a");
    private static final int PIXEL_CHANNEL_TOLERANCE = 8;

    private ActivityScenario<MainActivity> scenario;
    private PhoneBrowserSession session;
    private HostingController hosting;
    private static MilestoneSink milestones;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        session = PhoneBrowserSession.get(context);
        hosting = HostingController.get(context);
        if (milestones == null) {
            milestones = new MilestoneSink(context, System.currentTimeMillis());
        }
        ensureNotificationPermissionSetupForTest();
        // Independent cases: hosting stopped and the session back to a clean, never-loaded state.
        runOnMain(hosting::stop);
        waitUntilMain("hosting stopped",
                () -> hosting.status().state == HostingController.State.NOT_HOSTING);
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> session.resetForTest());
    }

    /**
     * App-scoped permission setup so the POST_NOTIFICATIONS dialog never interrupts the Start
     * control. The pre-existing grant state is recorded once per class (reported as
     * {@code NOTIF_PERM_BEFORE} in the instrumentation stream) so cleanup can restore it; a failed
     * setup fails loudly instead of proceeding as if verified.
     */
    private void ensureNotificationPermissionSetupForTest() {
        boolean granted = notificationPermissionGranted();
        if (!notificationPermissionSetupRecorded) {
            notificationPermissionWasGrantedBeforeSetup = granted;
            notificationPermissionSetupRecorded = true;
            System.out.println("NOTIF_PERM_BEFORE granted=" + granted);
        }
        if (!granted) {
            runShellCommandForTest(
                    "pm grant com.code2hack.eyebrowse.phone android.permission.POST_NOTIFICATIONS");
            if (!notificationPermissionGranted()) {
                fail("POST_NOTIFICATIONS setup did not take effect; not proceeding as verified success");
            }
        }
    }

    /** Recorded before-state for cleanup restoration; true when the permission was pre-granted. */
    private static boolean notificationPermissionWasGrantedBeforeSetup;

    private static boolean notificationPermissionSetupRecorded;

    static boolean notificationPermissionNeedsCleanupRestore() {
        return notificationPermissionSetupRecorded && !notificationPermissionWasGrantedBeforeSetup;
    }

    private boolean notificationPermissionGranted() {
        // In-process check of the target app's runtime permission state: deterministic
        // GRANTED/DENIED, no shell-output parsing that could misread failure as denial.
        return androidx.core.content.ContextCompat.checkSelfPermission(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Shell-launched return to the foreground reusing the existing instance (SINGLE_TOP). */
    private void bringMainActivityToFrontForTest() {
        runShellCommandForTest(
                "am start -f 0x20000000 -n com.code2hack.eyebrowse.phone/.MainActivity");
    }

    private void runShellCommandForTest(String command) {
        try {
            android.os.ParcelFileDescriptor descriptor =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .executeShellCommand(command);
            descriptor.close();
        } catch (RuntimeException | java.io.IOException ignored) {
            // Proceed: callers verify the observable effect instead of the command result.
        }
    }

    private String runShellCommandWithOutputForTest(String command) {
        try {
            android.os.ParcelFileDescriptor descriptor =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .executeShellCommand(command);
            try (java.io.InputStream in = new java.io.FileInputStream(
                    descriptor.getFileDescriptor())) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toString("UTF-8");
            } finally {
                descriptor.close();
            }
        } catch (RuntimeException | java.io.IOException e) {
            return "(command failed: " + e + ")";
        }
    }

    /** Real input injection is only meaningful while this app owns the focused window. */
    private void awaitWindowFocusForTest() {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> focused = new AtomicReference<>(false);
            try {
                scenario.onActivity(activity -> focused.set(activity.hasWindowFocus()));
            } catch (RuntimeException ignored) {
                // Activity briefly unavailable during the transition; keep polling.
            }
            if (Boolean.TRUE.equals(focused.get())) {
                return;
            }
            SystemClock.sleep(200);
        }
        fail("the browser window never regained input focus after the transition");
    }

    /**
     * ONE actual tap on the real hosting control via the codebase's validated instrumentation
     * input route (explicit screen coordinates, {@code sendPointerSync}, touchscreen source),
     * after verified readiness: focused window, laid-out visible button inside the window on the
     * default display, and a bounded post-transition settle. No retry — an unchanged state after
     * the tap is uncertain delivery and fails with diagnosis. A test-only touch observer records
     * whether the window received the injected events at all.
     */
    private void tapHostingToggleOnce(HostingController.State expectedAfter, long boundMs) {
        awaitWindowFocusForTest();
        SystemClock.sleep(800); // Transition settle BEFORE readiness: coordinates as of the tap.
        AtomicReference<int[]> centerRef = new AtomicReference<>();
        AtomicReference<String> diagnosis = new AtomicReference<>();
        scenario.onActivity(activity -> {
            View button = activity.findViewById(R.id.button_hosting_toggle);
            boolean focusedWindow = activity.hasWindowFocus();
            boolean laidOut = button.isShown() && button.getWidth() > 0 && button.getHeight() > 0;
            int[] location = new int[2];
            button.getLocationOnScreen(location);
            View decor = activity.getWindow().getDecorView();
            boolean onScreen = location[0] >= 0 && location[1] >= 0
                    && location[0] + button.getWidth() <= decor.getWidth()
                    && location[1] + button.getHeight() <= decor.getHeight();
            android.view.Display display = activity.getDisplay();
            boolean defaultDisplayOn = display != null
                    && display.getDisplayId() == android.view.Display.DEFAULT_DISPLAY
                    && display.getState() == android.view.Display.STATE_ON;
            if (focusedWindow && laidOut && onScreen && defaultDisplayOn) {
                centerRef.set(new int[]{location[0] + button.getWidth() / 2,
                        location[1] + button.getHeight() / 2});
                button.setOnTouchListener((view, event) -> {
                    android.util.Log.i("EyeBrowseTap", "button touch " + event.getActionMasked());
                    return false; // Observes delivery; normal click handling is preserved.
                });
            } else {
                diagnosis.set("focus=" + focusedWindow + " laidOut=" + laidOut + " onScreen="
                        + onScreen + " at=" + location[0] + "," + location[1]
                        + " display=" + (display == null ? "null"
                                : display.getDisplayId() + "/" + display.getState()));
            }
        });
        if (diagnosis.get() != null) {
            fail("hosting control not ready for a single tap: " + diagnosis.get());
        }
        int[] center = centerRef.get();
        long now = SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
                android.view.MotionEvent.ACTION_DOWN, center[0], center[1], 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 60,
                android.view.MotionEvent.ACTION_UP, center[0], center[1], 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try {
            InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
        } finally {
            down.recycle();
            up.recycle();
        }
        long deadline = SystemClock.uptimeMillis() + boundMs;
        HostingController.Status current = runOnMainSync(hosting::status);
        while (SystemClock.uptimeMillis() < deadline) {
            current = runOnMainSync(hosting::status);
            if (current.state == expectedAfter) {
                return;
            }
            SystemClock.sleep(50);
        }
        fail("injected tap produced no " + expectedAfter + " within " + boundMs + "ms (last="
                + current.state + " reason=" + current.failureReason
                + "); focus, geometry and display state were verified before this single attempt; "
                + "button touch delivery is in the EyeBrowseTap logcat; cause unknown if no touch "
                + "was logged; no retry performed");
    }

    // ------------------------------------------------------------------ tests

    /**
     * Labeled DIAGNOSTIC, not UI-journey evidence: the hosting toggle's own click listener is wired
     * and functional when invoked directly. This isolates product-listener defects from input-
     * injection quirks; it never substitutes the actual Start/Stop tap journey.
     */
    @Test
    public void hostingToggleListenerWiredDirectCallbackDiagnostic() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        long generationBefore = runOnMainSync(() -> (long) hosting.currentGeneration());
        scenario.onActivity(activity -> activity.findViewById(R.id.button_hosting_toggle)
                .performClick());
        awaitHostingState(HostingController.State.HOSTING, START_BOUND_MS);
        assertEquals(generationBefore + 1,
                (long) runOnMainSync(() -> (long) hosting.currentGeneration()));
        runOnMain(hosting::stop);
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
    }

    /**
     * The core same-instance continuity check: Phone → private presentation → Phone keeps the
     * load marker, field value, WebView identity and load count, and Start/Stop stay bounded.
     */
    @Test
    public void hostingStartBackgroundReturnKeepsSamePageStateAndWebViewIdentity() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        String marker = domText("load-marker");
        setFieldValue("continuity-value");
        int loadsBefore = loadCount("/hosting.html");
        int webViewIdentity = webViewIdentityHash();
        long generationBefore = runOnMainSync(() -> (long) hosting.currentGeneration());

        long startBegin = SystemClock.uptimeMillis();
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        HostingController.Status started = runOnMainSync(hosting::status);
        long startElapsed = SystemClock.uptimeMillis() - startBegin;
        assertEquals(generationBefore + 1, (long) started.generation);
        assertEquals(HostingController.Attachment.PHONE_UI, started.attachment);

        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("webview hosted offscreen", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        assertEquals(webViewIdentity, webViewIdentityHash());
        assertEquals("no reload while hosted", loadsBefore, loadCount("/hosting.html"));
        assertEquals(marker, domText("load-marker"));
        assertEquals("continuity-value", readFieldValue());

        bringMainActivityToFrontForTest();
        waitUntil("webview back on phone ui", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PHONE_UI
                    && snapshot.viewAttached;
        });
        assertEquals(webViewIdentity, webViewIdentityHash());
        assertEquals(marker, domText("load-marker"));
        assertEquals("continuity-value", readFieldValue());
        assertEquals("no reload across the private-display round trip", loadsBefore,
                loadCount("/hosting.html"));

        long stopBegin = SystemClock.uptimeMillis();
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
        long stopElapsed = SystemClock.uptimeMillis() - stopBegin;
        assertTrue("stop bound " + stopElapsed + "ms", stopElapsed <= STOP_BOUND_MS);
        assertTrue("stop bound " + startElapsed + "ms", startElapsed <= START_BOUND_MS);
        assertEquals("the page survives Stop", marker, domText("load-marker"));
        assertEquals("the field survives Stop", "continuity-value", readFieldValue());
        assertEquals(webViewIdentity, webViewIdentityHash());
    }

    /** Activity exit during hosting is not Stop: the host and page survive, relaunch reattaches. */
    @Test
    public void activityFinishDuringHostingKeepsHostedPageForRelaunch() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        String marker = domText("load-marker");
        int loadsBefore = loadCount("/hosting.html");
        int webViewIdentity = webViewIdentityHash();

        long generationBefore = runOnMainSync(() -> (long) hosting.currentGeneration());
        onView(withId(R.id.button_hosting_toggle)).perform(click());
        awaitHostingState(HostingController.State.HOSTING, START_BOUND_MS);

        scenario.onActivity(android.app.Activity::finish);
        // Finish is not Stop: the host keeps running and the page stays live. Where the view sits
        // (this Activity's successor may already have taken it) is transition-ordered; the host
        // state, generation and document are the invariants here.
        waitUntilMain("host intact after activity finish",
                () -> hosting.status().state == HostingController.State.HOSTING);
        long generationAfterFinish = runOnMainSync(() -> (long) hosting.currentGeneration());
        assertEquals("hosting outlives the Activity", generationBefore + 1, generationAfterFinish);
        assertEquals("the hosted page is still the same live document", marker,
                domText("load-marker"));

        scenario = ActivityScenario.launch(MainActivity.class);
        waitUntil("relaunch shows the hosted page on phone ui", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.state == HostingController.State.HOSTING
                    && snapshot.status.attachment == HostingController.Attachment.PHONE_UI
                    && snapshot.viewAttached;
        });
        long generationAfterRelaunch = runOnMainSync(() -> (long) hosting.currentGeneration());
        assertEquals("same hosting generation after relaunch", generationBefore + 1,
                generationAfterRelaunch);
        assertEquals("same live WebView instance", webViewIdentity, webViewIdentityHash());
        assertEquals("no reload on relaunch", loadsBefore, loadCount("/hosting.html"));
        assertEquals(marker, domText("load-marker"));
    }

    /**
     * Repeated Start/Stop cycles keep the page, history and WebView identity; equivalent stopped
     * states, deterministic Stop-during-STARTING, stale-callback fencing after bounded teardown.
     */
    @Test
    public void startStopCyclesKeepPageAndRepeatedStopIsIdempotent() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        // Representative history before hosting: navigation the hosting lifecycle must preserve.
        onView(withId(R.id.address_input)).perform(click(),
                replaceText(FIXTURE_BASE + "/hosting-two.html"));
        onView(withId(R.id.button_open)).perform(click());
        waitUntil("second page for history", () ->
                "Second hosting page".equals(domText("page-title")));
        onView(withId(R.id.address_input)).perform(click(),
                replaceText(FIXTURE_BASE + "/hosting.html"));
        onView(withId(R.id.button_open)).perform(click());
        waitUntil("back on the capture page", () ->
                "Hosting capture page".equals(domText("page-title")));
        final boolean historyBefore = runOnMainSync(session::canGoBack);
        assertTrue("representative history exists before hosting", historyBefore);

        String marker = domText("load-marker");
        int loadsBefore = loadCount("/hosting.html");
        int webViewIdentity = webViewIdentityHash();
        long generationBefore = runOnMainSync(() -> (long) hosting.currentGeneration());
        java.util.List<String> stoppedStates = new java.util.ArrayList<>();

        for (int cycle = 1; cycle <= 3; cycle++) {
            onView(withId(R.id.button_hosting_toggle)).perform(click());
            HostingController.Status started = awaitHostingState(HostingController.State.HOSTING,
                    START_BOUND_MS);
            assertEquals("generation advances per cycle", generationBefore + cycle,
                    (long) started.generation);

            runOnMain(hosting::stop); // Direct in-process Stop, not only via the UI control.
            awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
            // Equivalent stopped states across cycles: the same resource signature every time.
            stoppedStates.add(runOnMainSync(() -> hosting.captureResourcesPresent() + "|"
                    + hosting.hasDisplayResources() + "|" + hosting.isWakeLockHeld()));
        }
        assertEquals("all cycles ended in the same stopped resource state", 1,
                new HashSet<>(stoppedStates).size());

        // Deterministic Stop-during-STARTING: start() sets STARTING synchronously on the main
        // thread; the assertion observes that exact state before Stop cancels it.
        // One main-thread turn: start accepted -> observe STARTING -> Stop cancels it. Direct
        // synchronized calls on this turn; no nested dispatch can interleave service readiness.
        runOnMain(() -> {
            assertTrue("stop-during-start start accepted", hosting.start());
            assertEquals("stop-during-start exercises STARTING", HostingController.State.STARTING,
                    hosting.status().state);
            hosting.stop();
        });
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);

        // Idempotent extra Stops.
        runOnMain(hosting::stop);
        runOnMain(hosting::stop);
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);

        // Stale-callback fencing across Stop: a consumer holding a lease when Stop lands receives
        // no further frames once teardown is confirmed (reader closed, capture thread exited).
        onView(withId(R.id.button_hosting_toggle)).perform(click());
        awaitHostingState(HostingController.State.HOSTING, START_BOUND_MS);
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("webview hosted offscreen", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        CollectingConsumer staleConsumer = new CollectingConsumer();
        HostingController.Lease stoppedLease =
                runOnMainSync(() -> hosting.acquireLease(staleConsumer));
        assertNotNull("lease before Stop", stoppedLease);
        waitUntil("frames flow before Stop", () -> staleConsumer.count() > 0);
        runOnMain(hosting::stop);
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
        long teardownDeadline = SystemClock.uptimeMillis() + STOP_BOUND_MS;
        while (SystemClock.uptimeMillis() < teardownDeadline
                && runOnMainSync(hosting::captureResourcesPresent)) {
            SystemClock.sleep(100);
        }
        assertEquals("teardown completed within bound (reader closed, thread exited)", false,
                runOnMainSync(hosting::captureResourcesPresent));
        assertEquals("wake lock released by Stop", false, runOnMainSync(hosting::isWakeLockHeld));
        int staleCount = staleConsumer.count();
        SystemClock.sleep(2_000);
        assertEquals("no stale frames delivered after Stop", staleCount, staleConsumer.count());

        assertEquals("representative history survives the hosting lifecycle",
                historyBefore, runOnMainSync(session::canGoBack));
        assertEquals("page survives every cycle", marker, domText("load-marker"));
        assertEquals("no page reload from hosting cycles", loadsBefore, loadCount("/hosting.html"));
        assertEquals("same live WebView instance", webViewIdentity, webViewIdentityHash());
    }

    /** Injected null and thrown platform allocation failures roll back bounded and idempotently. */
    @Test
    public void partialAllocationFailuresRollBackBounded() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        PrivateDisplayHost.Factory platform = new PrivateDisplayHost.PlatformFactory();

        // Failure 1: virtual display creation returns null; the created reader must be released.
        long generationBefore = runOnMainSync(() -> (long) hosting.currentGeneration());
        hosting.setResourceFactoryForTest(new PrivateDisplayHost.Factory() {
            @Override
            public VirtualDisplay createVirtualDisplay(DisplayManager manager, String name,
                    int width, int height, int densityDpi, Object surface) {
                return null; // Injected allocation failure.
            }

            @Override
            public ImageReader createImageReader(int width, int height) {
                return platform.createImageReader(width, height);
            }

            @Override
            public PrivateDisplayHost.PresentationHost createPresentation(Context context,
                    android.view.Display display) {
                return platform.createPresentation(context, display);
            }
        });
        onView(withId(R.id.button_hosting_toggle)).perform(click());
        awaitStartupOutcome(generationBefore + 1);
        assertEquals("display failure rolled back capture resources", false,
                runOnMainSync(hosting::captureResourcesPresent));
        assertEquals("display failure released display resources", false,
                runOnMainSync(hosting::hasDisplayResources));

        // Failure 2: image reader creation THROWS a recoverable platform exception (F8).
        hosting.setResourceFactoryForTest(new PrivateDisplayHost.Factory() {
            @Override
            public VirtualDisplay createVirtualDisplay(DisplayManager manager, String name,
                    int width, int height, int densityDpi, Object surface) {
                return platform.createVirtualDisplay(manager, name, width, height, densityDpi,
                        surface);
            }

            @Override
            public ImageReader createImageReader(int width, int height) {
                throw new IllegalStateException("injected platform allocation failure");
            }

            @Override
            public PrivateDisplayHost.PresentationHost createPresentation(Context context,
                    android.view.Display display) {
                return platform.createPresentation(context, display);
            }
        });
        onView(withId(R.id.button_hosting_toggle)).perform(click());
        awaitStartupOutcome(generationBefore + 2);
        assertEquals("thrown failure rolled back capture resources", false,
                runOnMainSync(hosting::captureResourcesPresent));
        assertEquals("thrown failure released display resources", false,
                runOnMainSync(hosting::hasDisplayResources));

        // Restored factory: ordinary start/stop still works and the page never was disturbed.
        hosting.setResourceFactoryForTest(platform);
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
        assertEquals(false, runOnMainSync(hosting::captureResourcesPresent));
    }

    /** Waits for a failed start: NOT_HOSTING with the generation consumed and a recorded reason. */
    private void awaitStartupOutcome(long expectedGeneration) {
        long deadline = SystemClock.uptimeMillis() + START_BOUND_MS;
        HostingController.Status status = runOnMainSync(hosting::status);
        while (SystemClock.uptimeMillis() < deadline) {
            status = runOnMainSync(hosting::status);
            if (status.state == HostingController.State.NOT_HOSTING
                    && status.generation == expectedGeneration
                    && status.failureReason != null) {
                return;
            }
            SystemClock.sleep(50);
        }
        fail("failed start did not roll back in " + START_BOUND_MS + "ms (state=" + status.state
                + " gen=" + status.generation + " expected=" + expectedGeneration + " reason="
                + status.failureReason + ")");
    }

    /**
     * Hosting-active recreation, renderer interruption and site persistence: recreation with the
     * host live keeps generation/WebView/document; a renderer loss interrupts hosting and
     * requires the explicit restart; persisted site data survives hosting start/stop.
     */
    @Test
    public void hostingRecreationInterruptionAndPersistenceKeepSessionAndData() throws Exception {
        // Site persistence across a hosting start/stop cycle (fixture storage page).
        openFixture("/storage.html", "localStorage controls");
        String storedValue = "persist-check-" + SystemClock.uptimeMillis();
        evaluateJs("(function(){localStorage.setItem('fixture-key',"
                + JSONObject.quote(storedValue) + ");"
                + "return localStorage.getItem('fixture-key');})()");
        long generationBefore = runOnMainSync(() -> (long) hosting.currentGeneration());

        // Hosting-active recreation: the same live WebView reattaches; hosting generation holds.
        openFixture("/hosting.html", "Hosting capture page");
        String marker = domText("load-marker");
        int webViewIdentity = webViewIdentityHash();
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        scenario.recreate();
        waitUntil("recreated activity reattached the hosted page", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.state == HostingController.State.HOSTING
                    && snapshot.status.attachment == HostingController.Attachment.PHONE_UI
                    && snapshot.viewAttached;
        });
        assertEquals("recreation keeps the hosting generation", generationBefore + 1,
                (long) runOnMainSync(() -> (long) hosting.currentGeneration()));
        assertEquals("same live WebView instance across recreation", webViewIdentity,
                webViewIdentityHash());
        assertEquals("document preserved across hosting-active recreation", marker,
                domText("load-marker"));

        // Renderer interruption while hosting: explicit interruption, no automatic replay.
        runOnMain(session::simulateProcessRestartForTest);
        waitUntil("hosting interrupted after renderer loss", () -> {
            HostingController.Status status = hosting.status();
            return status.state == HostingController.State.NOT_HOSTING
                    && status.failureReason != null;
        });
        assertEquals("interruption reason recorded", HostingController.State.NOT_HOSTING,
                runOnMainSync(hosting::status).state);

        // The persisted site value survives the hosting start/stop cycle on the restored session.
        scenario = ActivityScenario.launch(MainActivity.class);
        openFixture("/storage.html", "localStorage controls");
        assertEquals("site persistence before hosting cycle", storedValue,
                decode(evaluateJs("localStorage.getItem('fixture-key')")));
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
        onView(withId(R.id.address_input)).perform(click(),
                replaceText(FIXTURE_BASE + "/storage.html"));
        onView(withId(R.id.button_open)).perform(click());
        waitUntil("storage page reloaded after hosting cycle",
                () -> "localStorage controls".equals(domText("page-title")));
        assertEquals("site persistence across hosting start/stop", storedValue,
                decode(evaluateJs("localStorage.getItem('fixture-key')")));
    }

    /**
     * A safe app-scoped window change while hosting reconciles the private geometry to the last
     * measured Phone content viewport (frames at the new size, same document, no reload) and the
     * exact page state is restored when the window returns.
     */
    @Test
    public void hostingWindowChangeReconcilesGeometryWithExactRestoration() throws Exception {
        assertFalse("blank white presentation must not qualify as the fixture page",
                nearColor(Color.WHITE, CAPTURE_PAGE_COLOR));
        openFixture("/hosting.html", "Hosting capture page");
        String marker = domText("load-marker");
        setFieldValue("geometry-value");
        int loadsBefore = loadCount("/hosting.html");
        int[] sizeBefore = currentWebViewSize();
        long generationBefore = runOnMainSync(() -> (long) hosting.currentGeneration());

        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        // App-scoped window change (reversible; no global display override): recreation applies it.
        scenario.onActivity(activity -> activity.setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));
        waitUntil("window changed while hosting", () -> {
            int[] size = currentWebViewSize();
            return size[0] != sizeBefore[0] || size[1] != sizeBefore[1];
        });
        int[] sizeAfterChange = currentWebViewSize();

        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("hosted offscreen at the reconciled geometry", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        // The privately re-laid-out view now measures the OLD presentation geometry (no lease
        // existed during the move, so nothing rebuilt): the first demand below must reconcile to
        // the SAVED Phone viewport (sizeAfterChange), not to this stale private layout.
        CollectingConsumer consumer = new CollectingConsumer();
        HostingController.Lease lease = runOnMainSync(() -> hosting.acquireLease(consumer));
        assertNotNull("lease after geometry reconciliation", lease);
        HostViewSnapshot afterAcquisition = hostViewSnapshot();
        assertEquals("first demand remains privately attached",
                HostingController.Attachment.PRIVATE_DISPLAY, afterAcquisition.status.attachment);
        assertTrue("the actual WebView is attached AFTER acquisition/rebuild",
                afterAcquisition.viewAttached);
        waitUntil("frames flow at the reconciled geometry", () -> consumer.count() > 0);
        assertTrue("delivered frames carry the reconciled (saved Phone) viewport "
                        + sizeAfterChange[0] + "x" + sizeAfterChange[1],
                consumer.allFramesMatchSize(sizeAfterChange[0], sizeAfterChange[1]));
        assertTrue("delivered pixels show the same document after reconcile",
                consumer.allFramesNearColor(CAPTURE_PAGE_COLOR));
        assertEquals("no reload from geometry reconciliation", loadsBefore,
                loadCount("/hosting.html"));
        assertEquals(marker, domText("load-marker"));
        runOnMain(lease::release);

        // Exact restoration.
        bringMainActivityToFrontForTest();
        waitUntil("webview back on phone ui", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PHONE_UI
                    && snapshot.viewAttached;
        });
        scenario.onActivity(activity -> activity.setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        waitUntil("window restored", () -> {
            int[] size = currentWebViewSize();
            return size[0] == sizeBefore[0] && size[1] == sizeBefore[1];
        });
        assertEquals(marker, domText("load-marker"));
        assertEquals("geometry-value", readFieldValue());
        assertEquals("no reload across the window round trip", loadsBefore,
                loadCount("/hosting.html"));
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
    }

    /**
     * The bounded background-capture acceptance core (corrected round 2): never-leased idle
     * anchored at readiness, first frame at CONSUMER delivery ≤2s from eligibility, delivered-
     * pixel content correlation, ≥3 changing frames per 10s, 120s active offscreen capture,
     * production stop ≤6s TOTAL after the last renewal, wake-lock release in the same bound,
     * idle release by demand+30s, and final Stop — with device/lock facts and resource milestones
     * persisted app-scoped while produced.
     */
    @Test
    public void backgroundCaptureMeetsLivenessContentIdleAndStopBounds() throws Exception {
        recordLockRecoverabilityAssessment();
        openFixture("/hosting.html", "Hosting capture page");
        String marker = domText("load-marker");
        int loadsHosting = loadCount("/hosting.html");
        setFieldValue("capture-value");
        long generationBefore = runOnMainSync(() -> (long) hosting.currentGeneration());
        memoryMilestone("before-start");

        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        assertEquals(generationBefore + 1,
                (long) runOnMainSync(() -> (long) hosting.currentGeneration()));
        long readyElapsed = SystemClock.elapsedRealtime();
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("webview hosted offscreen", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        recordDeviceState("backgrounded-before-capture");

        // --- Never-leased idle: the authoritative readiness anchor is read from the controller
        // (set inside onServiceReady before it returns); the plan deadline is anchor+30s EXACTLY.
        // The 500ms early-initiation lead is production headroom, not a claimed guarantee:
        // acceptance uses the AUTHORITATIVE completion timestamp recorded on the production
        // completion path and asserts it <= the exact deadline. A longer diagnostic wait may
        // expose a late completion but cannot make it pass.
        long readinessAnchor = runOnMainSync(hosting::lastDemandAnchorElapsedMs);
        long neverLeasedDeadline = readinessAnchor + HostingPolicy.IDLE_RELEASE_MS;
        long completion = 0;
        long diagnosticLimit = neverLeasedDeadline + 10_000;
        while (SystemClock.elapsedRealtime() < diagnosticLimit) {
            completion = runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs);
            if (completion > 0) {
                break;
            }
            SystemClock.sleep(50);
        }
        assertTrue("idle-release completion was observed on the production path", completion > 0);
        assertTrue("never-leased completion " + (completion - readinessAnchor)
                        + "ms after the anchor is within the exact 30s deadline",
                completion <= neverLeasedDeadline);
        assertEquals("capture resources absent after the observed completion", false,
                runOnMainSync(hosting::captureResourcesPresent));
        assertEquals("never-leased hosting session persists", HostingController.State.HOSTING,
                runOnMainSync(hosting::status).state);
        memoryMilestone("after-never-leased-idle completedAt="
                + (completion - readinessAnchor) + "msAfterExactAnchor");

        // --- Lease acquisition (recreates the surface on the surviving display) and first frame
        // measured at CONSUMER delivery, within 2s of eligibility.
        CollectingConsumer consumer = new CollectingConsumer();
        long eligibleUptime = SystemClock.uptimeMillis();
        HostingController.Lease lease = runOnMainSync(() -> hosting.acquireLease(consumer));
        assertNotNull("lease must be acquirable while hosting", lease);
        LeaseRenewal renewal = new LeaseRenewal(lease);
        renewal.start();
        waitUntil("first delivered frame", () -> consumer.count() > 0);
        long firstDeliveryMs = consumer.deliveryUptimeAt(0) - eligibleUptime;
        assertTrue("first current-token frame at consumer delivery within 2s: " + firstDeliveryMs
                + "ms", firstFrameDelayIsValid(firstDeliveryMs));
        int expectedGeneration = (int) (generationBefore + 1);
        int[] expectedSize = currentWebViewSize();
        assertTrue("frame carries the hosting generation",
                consumer.allFramesMatchGeneration(expectedGeneration));
        assertTrue("frame dimensions match the measured viewport",
                consumer.allFramesMatchSize(expectedSize[0], expectedSize[1]));
        assertTrue("capture stamps are monotonic", consumer.monotonicCaptureStamps());
        assertTrue("wake lock is held while the lease is live",
                runOnMainSync(hosting::isWakeLockHeld));

        // --- Content correlation: frozen+blurred page produces no frames; resuming the counter
        // does; delivered pixels match the displayed page's own background color.
        evaluateJs("window.__eyebrowseFreeze(true)");
        SystemClock.sleep(3_000); // Let the render pipeline drain the last invalidations.
        int frozenCount = consumer.count();
        SystemClock.sleep(2_500);
        assertEquals("static page produces no new frames", frozenCount, consumer.count());
        evaluateJs("window.__eyebrowseFreeze(false)");
        waitUntil("frames resume when the counter resumes", () -> consumer.count() > frozenCount);
        int distinctBefore = consumer.distinctHashes();
        long windowStart = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - windowStart < 10_000) {
            SystemClock.sleep(200);
        }
        assertTrue("at least 3 content-changing frames in a 10s window (got "
                + (consumer.distinctHashes() - distinctBefore) + ")",
                consumer.distinctHashes() - distinctBefore >= 3);
        assertTrue("delivered pixels show the counter page",
                consumer.latestFrameNearColor(CAPTURE_PAGE_COLOR));

        // --- In-process browser-action navigation while hosted: delivered pixels must identify
        // the deterministic second-page content, then the counter page again (stale-output
        // negative case: no second-page pixels after the return settles).
        int loadsTwoBefore = loadCount("/hosting-two.html");
        runOnMain(() -> session.openAddress(FIXTURE_BASE + "/hosting-two.html"));
        waitUntil("second page loaded while hosted",
                () -> "Second hosting page".equals(domText("page-title")));
        assertEquals("navigation load recorded once", loadsTwoBefore + 1,
                loadCount("/hosting-two.html"));
        int loadsTwoAfter = loadsTwoBefore + 1;
        waitUntil("delivered pixels show the second page", () ->
                consumer.latestFrameNearColor(SECOND_PAGE_COLOR));
        int loadsHostingBeforeReturn = loadCount("/hosting.html");
        runOnMain(() -> session.openAddress(FIXTURE_BASE + "/hosting.html"));
        waitUntil("counter page restored while hosted",
                () -> "Hosting capture page".equals(domText("page-title")));
        waitUntil("delivered pixels return to the counter page", () ->
                consumer.latestFrameNearColor(CAPTURE_PAGE_COLOR));
        SystemClock.sleep(2_000); // Stale-output negative window.
        assertTrue("no second-page pixels after returning (stale output not replayed)",
                consumer.recentFramesNearColor(CAPTURE_PAGE_COLOR, 2_000));

        // --- Hold the live lease until 120 seconds of active capture have elapsed.
        long firstDeliveryElapsed = consumer.deliveryElapsedAt(0);
        waitUntil("120s of active offscreen capture",
                () -> consumer.latestCaptureElapsed() - firstDeliveryElapsed >= 120_000,
                140_000);
        distinctBefore = consumer.distinctHashes();
        windowStart = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - windowStart < 10_000) {
            SystemClock.sleep(200);
        }
        assertTrue("final 10s window still has >=3 changing frames (got "
                + (consumer.distinctHashes() - distinctBefore) + ")",
                consumer.distinctHashes() - distinctBefore >= 3);
        recordDeviceState("after-120s-active-capture");
        memoryMilestone("after-120s-active-capture");

        // --- Lease loss: production stops within 6s TOTAL of the last successful renewal (5s TTL
        // + <=1s expiry detection), and the wake lock releases in the same bound.
        renewal.stopRenewing(); // Joins the producer; it cannot enqueue a later renewal.
        runOnMain(lease::renew);
        long lastRenewElapsed = runOnMainSync(hosting::lastDemandAnchorElapsedMs);
        long stopDeadline = lastRenewElapsed + 6_000;
        long stoppedObservedAt = 0;
        HostingController.Status endpointStatus;
        do {
            endpointStatus = runOnMainSync(hosting::status);
            long observedAt = SystemClock.elapsedRealtime();
            if (!endpointStatus.captureActive && !endpointStatus.wakeLockHeld
                    && stoppedObservedAt == 0) {
                stoppedObservedAt = observedAt; // Conservative observed-completion bound.
            }
            if (observedAt >= stopDeadline) {
                break;
            }
            SystemClock.sleep(Math.min(50, stopDeadline - observedAt));
        } while (true);
        assertTrue("capture and wake lock stopped by last successful renewal +6s",
                stoppedObservedAt > 0 && stoppedObservedAt <= stopDeadline);
        assertFalse("capture remains inactive at the six-second endpoint", endpointStatus.captureActive);
        assertFalse("wake lock remains released at the endpoint", endpointStatus.wakeLockHeld);
        // Check after observations cover the endpoint, not before a separate wake-lock wait.
        long lastDelivery = consumer.latestDeliveryElapsed();
        assertTrue("no late consumer delivery beyond the six-second endpoint",
                lastDelivery <= stopDeadline);
        milestones.record("liveness anchor=" + lastRenewElapsed + " deadline=" + stopDeadline
                + " stoppedObserved=" + stoppedObservedAt + " lastDelivery=" + lastDelivery);

        // --- Reacquisition while the reader still exists: frames resume without new resources.
        CollectingConsumer reacquired = new CollectingConsumer();
        HostingController.Lease lease2 = runOnMainSync(() -> hosting.acquireLease(reacquired));
        assertNotNull("lease reacquisition must succeed before idle release", lease2);
        LeaseRenewal renewal2 = new LeaseRenewal(lease2);
        renewal2.start();
        waitUntil("frames resume after reacquisition", () -> reacquired.count() > 0);
        assertEquals("still the hosted counter document", "Hosting capture page",
                domText("page-title"));
        assertEquals("no navigation from reacquisition", loadsTwoAfter,
                loadCount("/hosting-two.html"));
        assertEquals("no reload of the counter page on reacquisition", loadsHostingBeforeReturn + 1,
                loadCount("/hosting.html"));

        // --- Idle release anchored at the last successful demand: voluntary release acts
        // immediately (earlier than the bound is always allowed) and never later than
        // demand + 30s. The anchor is the authoritative demand anchor; acceptance below uses the
        // production completion timestamp against the exact deadline (R1/R4).
        renewal2.stopRenewing();
        runOnMain(lease2::renew); // Synchronous on main; the definitive last successful demand.
        long demandAnchor = runOnMainSync(hosting::lastDemandAnchorElapsedMs); // Authoritative.
        runOnMain(lease2::release);
        memoryMilestone("lease-released-idle-window-start");
        long idleDeadline = demandAnchor + HostingPolicy.IDLE_RELEASE_MS; // Exact plan deadline.
        long idleCompletion = 0;
        long idleDiagnosticLimit = idleDeadline + 10_000;
        while (SystemClock.elapsedRealtime() < idleDiagnosticLimit) {
            idleCompletion = runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs);
            if (idleCompletion > 0) {
                break;
            }
            SystemClock.sleep(50);
        }
        assertTrue("idle-release completion was observed on the production path", idleCompletion > 0);
        assertTrue("post-demand completion " + (idleCompletion - demandAnchor)
                        + "ms after the anchor is within the exact 30s deadline",
                idleCompletion <= idleDeadline);
        assertEquals("capture resources absent after the observed completion", false,
                runOnMainSync(hosting::captureResourcesPresent));
        assertEquals("display/presentation attachment survives idle release", true,
                runOnMainSync(hosting::hasDisplayResources));
        assertEquals("hosting session survives idle release", HostingController.State.HOSTING,
                runOnMainSync(hosting::status).state);
        memoryMilestone("after-idle-release");

        // --- Reacquisition after idle release recreates the surface on the same display, without
        // any navigation, and frames flow again.
        CollectingConsumer afterIdle = new CollectingConsumer();
        HostingController.Lease lease3 = runOnMainSync(() -> hosting.acquireLease(afterIdle));
        assertNotNull("lease must reacquire after idle release", lease3);
        waitUntil("frames flow after idle-release reacquisition", () -> afterIdle.count() > 0);
        assertEquals("document untouched by idle release/reacquire", "Hosting capture page",
                domText("page-title"));
        assertEquals("no reload across idle release", loadsHostingBeforeReturn + 1,
                loadCount("/hosting.html"));
        runOnMain(lease3::release);

        // --- Final Stop from the actual UI: everything hosting-owned is released.
        bringMainActivityToFrontForTest();
        waitUntil("webview back on phone ui", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PHONE_UI
                    && snapshot.viewAttached;
        });
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
        assertEquals("capture resources gone after Stop", false,
                runOnMainSync(hosting::captureResourcesPresent));
        assertEquals("display resources gone after Stop", false,
                runOnMainSync(hosting::hasDisplayResources));
        assertEquals("wake lock released by Stop", false, runOnMainSync(hosting::isWakeLockHeld));
        assertEquals("hosting session stopped", HostingController.State.NOT_HOSTING,
                runOnMainSync(hosting::status).state);
        recordDeviceState("after-final-stop");
        memoryMilestone("after-final-stop");
        milestones.record("capture acceptance sequence complete");
        milestones.flushToStream("capture acceptance session");
    }

    // ------------------------------------------------- correction3 controlled cases (R1–R7)

    // Controlled ownership/deadline/attachment regressions. Execution results belong in the
    // source- and APK-bound run records, not in a static source-code status claim.

    /** R3: Stop while backgrounded, then return: the surviving live page reattaches, no reload. */
    @Test
    public void backgroundStopThenReturnReattachesLivePageWithoutReload() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        int loadsBefore = loadCount("/hosting.html");
        String markerBefore = domText("load-marker");
        int identityBefore = webViewIdentityHash();
        setFieldValue("bgstop-value");
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("webview hosted offscreen", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        // Background Stop through the same controller entry the notification Stop action drives.
        runOnMain(() -> hosting.stop());
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
        // Return to the existing Activity without any new navigation.
        bringMainActivityToFrontForTest();
        waitUntil("live page reattached on return after background Stop",
                () -> hostViewSnapshot().viewAttached);
        // The baseline was sampled AFTER opening; Stop/return must add zero page loads.
        assertEquals("no reload across background Stop and return", loadsBefore,
                loadCount("/hosting.html"));
        assertEquals("same document across background Stop and return", markerBefore,
                domText("load-marker"));
        assertEquals("same WebView across background Stop and return", identityBefore,
                webViewIdentityHash());
        assertEquals("field value survived background Stop and return", "bgstop-value",
                readFieldValue());
        assertEquals("hosting remains stopped after return", HostingController.State.NOT_HOSTING,
                runOnMainSync(hosting::status).state);
    }

    /**
     * B: an intentionally ALL-WHITE document delivers after output readiness without any color
     * dependence. The epoch facts (ready=true before admission) prove the delivered frame's
     * origin as current rendered output; white pixels here are the EXPECTED document content, so
     * this case also guards against white-heuristic shortcuts in either direction.
     */
    @Test
    public void allWhiteDocumentDeliversAfterReadinessWithoutColorDependence() throws Exception {
        openFixture("/hosting-white.html", "White capture page");
        String marker = domText("load-marker");
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("webview hosted offscreen", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        CollectingConsumer consumer = new CollectingConsumer();
        long eligibleUptime = SystemClock.uptimeMillis();
        HostingController.Lease lease = runOnMainSync(() -> hosting.acquireLease(consumer));
        assertNotNull("white-document lease", lease);
        waitUntil("first frame of the all-white document", () -> consumer.count() > 0);
        long firstDeliveryMs = consumer.deliveryUptimeAt(0) - eligibleUptime;
        assertTrue("first delivered frame within 2s of eligibility: " + firstDeliveryMs + "ms",
                firstFrameDelayIsValid(firstDeliveryMs));
        String epochFacts = runOnMainSync(hosting::outputEpochFacts);
        milestones.record("all-white epoch facts: " + epochFacts
                + " firstDeliveryMs=" + firstDeliveryMs);
        assertTrue("admission followed completed output readiness (epoch facts: " + epochFacts + ")",
                epochFacts.contains("ready=true"));
        assertTrue("delivered frames are the live white document",
                consumer.allFramesNearColor(Color.WHITE));
        assertEquals("same document marker (no reload/substitution)", marker,
                domText("load-marker"));
        assertEquals("no reload of the white page", 1, loadCount("/hosting-white.html"));
        runOnMain(lease::release);
        bringMainActivityToFrontForTest();
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
    }

    /** R6: a live lease survives a geometry rebuild; delivery rearms at the rebuilt viewport. */
    @Test
    public void liveLeaseSurvivesGeometryRebuildWithRearmedDelivery() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        int[] sizeBefore = currentWebViewSize();
        long generationBefore = runOnMainSync(() -> (long) hosting.currentGeneration());
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        CollectingConsumer consumer = new CollectingConsumer();
        HostingController.Lease lease = runOnMainSync(() -> hosting.acquireLease(consumer));
        assertNotNull("live lease before geometry change", lease);
        waitUntil("frames flow before geometry change", () -> consumer.count() > 0);
        // App-scoped reversible window change while the lease is live, then the offscreen move
        // reconciles (rebuilds) the capture surface to the new measured geometry.
        scenario.onActivity(activity -> activity.setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("rebuild completed offscreen with live lease", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        int[] sizeAfter = currentWebViewSize();
        assertTrue("the window change actually changed the geometry",
                sizeAfter[0] != sizeBefore[0] || sizeAfter[1] != sizeBefore[1]);
        int framesAtRebuild = consumer.count();
        waitUntil("delivery rearmed after the rebuild", () -> consumer.count() > framesAtRebuild);
        assertTrue("post-rebuild delivery carries the rebuilt viewport "
                        + sizeAfter[0] + "x" + sizeAfter[1],
                consumer.tailFramesMatchSize(framesAtRebuild, sizeAfter[0], sizeAfter[1]));
        assertTrue("rearmed delivery keeps the same hosting generation",
                consumer.allFramesMatchGeneration((int) (generationBefore + 1)));
        assertTrue("rearmed delivery shows the same document",
                consumer.tailFramesNearColor(framesAtRebuild, CAPTURE_PAGE_COLOR));
        scenario.onActivity(activity -> activity.setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT));
        bringMainActivityToFrontForTest();
        waitUntil("webview restored to phone ui", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PHONE_UI
                    && snapshot.viewAttached;
        });
        runOnMain(lease::release);
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
    }

    /** Real owned renderer loss, explicit recovery in the SAME Activity, then hosting ownership. */
    @Test
    public void rendererLossRecoveryInSameActivityRegistersNewUiOwner() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        AtomicReference<MainActivity> originalActivity = new AtomicReference<>();
        scenario.onActivity(originalActivity::set);
        WebView originalView = runOnMainSync(session::view);
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        boolean terminated = runOnMainSync(() -> {
            android.webkit.WebViewRenderProcess process = originalView.getWebViewRenderProcess();
            assertNotNull("the owned WebView has a renderer process", process);
            return process.terminate();
        });
        assertTrue("owned renderer termination request accepted", terminated);
        waitUntil("production renderer-loss callback cleared the old attachment",
                () -> runOnMainSync(() -> session.view() == null && !session.isLive()));
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
        milestones.record("actual owned renderer loss observed; explicit same-Activity recovery follows");

        openFixture("/hosting.html", "Hosting capture page"); // Actual native recovery/Open path.
        scenario.onActivity(activity -> assertTrue("recovery did not replace the Activity",
                activity == originalActivity.get()));
        assertTrue("recovery creates a new WebView after real renderer loss",
                runOnMainSync(session::view) != originalView);
        String recoveredMarker = domText("load-marker");
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("new UI token permits offscreen transfer after renderer recovery", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        assertEquals("recovered document survives transfer", recoveredMarker, domText("load-marker"));
        scenario.onActivity(android.app.Activity::finish);
        waitUntil("destroyed recovered Activity is no longer retained as UI owner",
                () -> !runOnMainSync(hosting::hasPhoneUiOwner));
        assertEquals("Activity finish is not hosting Stop", HostingController.State.HOSTING,
                runOnMainSync(hosting::status).state);
        scenario = ActivityScenario.launch(MainActivity.class);
        waitUntil("live recovered page reattaches to the successor Phone UI",
                () -> hostViewSnapshot().status.attachment == HostingController.Attachment.PHONE_UI);
        assertEquals(recoveredMarker, domText("load-marker"));
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
    }

    /** R7: a thrown reader recreation after idle rolls back and surfaces in actual status. */
    @Test
    public void thrownReaderRecreationSurfacesFailureAndRollsBack() throws Exception {
        SettableFactory factory = new SettableFactory();
        runOnMain(() -> hosting.setResourceFactoryForTest(factory));
        openFixture("/hosting.html", "Hosting capture page");
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("webview hosted offscreen", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        // Wait out the never-leased idle release against the authoritative anchor/exact deadline
        // so the next acquisition must RECREATE the reader.
        long readinessAnchor = runOnMainSync(hosting::lastDemandAnchorElapsedMs);
        long idleDeadline = readinessAnchor + HostingPolicy.IDLE_RELEASE_MS;
        long r7Completion = 0;
        while (SystemClock.elapsedRealtime() < idleDeadline + 10_000) {
            r7Completion = runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs);
            if (r7Completion > 0) {
                break;
            }
            SystemClock.sleep(50);
        }
        assertTrue("idle release completed before the injection", r7Completion > 0);
        assertTrue("idle release within the exact deadline before the injection",
                r7Completion <= idleDeadline);
        assertEquals("capture resources absent before the injection", false,
                runOnMainSync(hosting::captureResourcesPresent));
        // Inject the recoverable allocation failure on the recreation path (R7).
        factory.throwOnNextReader = true;
        CollectingConsumer consumer = new CollectingConsumer();
        HostingController.Lease lease = runOnMainSync(() -> hosting.acquireLease(consumer));
        assertNull("acquisition fails explicitly when reader recreation throws", lease);
        HostingController.Status status = runOnMainSync(hosting::status);
        assertEquals("hosting session survives the recoverable failure",
                HostingController.State.HOSTING, status.state);
        assertNotNull("failure surfaced in actual status", status.failureReason);
        assertEquals("allocation rolled back: no live capture resources", false,
                runOnMainSync(hosting::captureResourcesPresent));
        // Recovery: clear the injection; the next acquisition recreates and delivers.
        factory.throwOnNextReader = false;
        lease = runOnMainSync(() -> hosting.acquireLease(consumer));
        assertNotNull("recovery after failed recreation", lease);
        waitUntil("frames flow after recovery", () -> consumer.count() > 0);
        assertNull("successful capture clears its resolved failure",
                runOnMainSync(hosting::status).failureReason);
        runOnMain(lease::release);
        bringMainActivityToFrontForTest();
        scenario.onActivity(activity -> {
            String label = ((android.widget.TextView) activity.findViewById(R.id.hosting_status))
                    .getText().toString();
            assertTrue("recovered native UI reports active hosting: " + label,
                    label.startsWith("Hosting ·"));
        });
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
    }

    /** R4: after the 5s deadline, a late renewal must not revive delivery or the wake lock. */
    @Test
    public void lateRenewalAfterExpiryCannotReviveDelivery() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("page attached offscreen before lease", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        CollectingConsumer consumer = new CollectingConsumer();
        HostingController.Lease lease = runOnMainSync(() -> hosting.acquireLease(consumer));
        assertNotNull(lease);
        waitUntil("first frame before expiry", () -> consumer.count() > 0);
        // No renewal: the authoritative deadline passes; the gate rejects delivery and renewal
        // from the deadline instant itself (strict boundary covered with controlled clocks in
        // FrameGateTest, including inside the TTL-to-watchdog interval), and the watchdog
        // revokes within its tick. This device case proves the observable no-revival endpoint.
        SystemClock.sleep(HostingPolicy.LEASE_TTL_MS + 1_500);
        int framesAtExpiry = consumer.count();
        // This device case renews after expiry; it must move nothing. The exact pre-watchdog
        // deadline interval is exercised with controlled time by FrameGateTest, not this sleep.
        runOnMain(lease::renew);
        SystemClock.sleep(2_000); // A revived pipeline would deliver within this window.
        assertEquals("no delivery revived by the late renewal", framesAtExpiry, consumer.count());
        assertEquals("wake lock released after expiry despite the late renewal", false,
                runOnMainSync(hosting::isWakeLockHeld));
        bringMainActivityToFrontForTest();
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
    }

    /** R2: a delayed in-flight consumer and a reacquiring consumer never share a borrowed frame. */
    @Test
    public void delayedConsumerReleaseReacquireIsolatesBorrowedFrames() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        // Offscreen precondition: private capture must have the hosted page (Phone-UI attachment
        // has no hosted offscreen page to deliver).
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("webview hosted offscreen", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        DelayedConsumer first = new DelayedConsumer();
        HostingController.Lease lease1 = runOnMainSync(() -> hosting.acquireLease(first));
        assertNotNull(lease1);
        CollectingConsumer second = new CollectingConsumer();
        HostingController.Lease prematureLease = null;
        try {
            // Entry precedes collection. The test, not a sleep, owns the callback's release.
            assertTrue("consumer entered its callback", first.awaitEntered(5_000));
            milestones.record("delayed-consumer callback held before revocation");
            runOnMain(lease1::release);
            assertTrue("borrowed callback keeps the retiring owner observable",
                    runOnMainSync(hosting::captureResourcesPresent));
            prematureLease = runOnMainSync(() -> hosting.acquireLease(second));
            assertNull("no replacement lease while the old callback is held", prematureLease);
            assertEquals("no anonymous delivery after rejected acquisition", 0, second.count());
            milestones.record("replacement rejected while old callback held");
        } finally {
            first.releaseHold();
            // If the invariant failed, release only the unexpected lease this test acquired.
            if (prematureLease != null) {
                runOnMain(prematureLease::release);
            }
            runOnMain(lease1::release); // Idempotent test cleanup, not a replayed UI action.
        }
        waitUntil("old capture owner actually quiescent",
                () -> !runOnMainSync(hosting::captureResourcesPresent), STOP_BOUND_MS);
        assertFalse("test-controlled callback hold expired or was interrupted", first.holdFailed());
        assertTrue("held callback completed its own borrowed use", first.collector().count() > 0);
        assertEquals("rejected acquisition created no hidden lease or delivery", 0, second.count());
        milestones.record("old owner quiescent before explicit replacement acquisition");

        // One explicit acquisition after observed quiescence; never wait for an abandoned lease
        // to expire and never acquire a third lease while a successful second lease is held.
        HostingController.Lease replacement = runOnMainSync(() -> hosting.acquireLease(second));
        assertNotNull("explicit acquisition succeeds after quiescence", replacement);
        try {
            waitUntil("replacement consumer receiving", () -> second.count() > 0);
            assertTrue("the retiring consumer's borrowed frame retained the original document",
                    first.collector().allFramesNearColor(CAPTURE_PAGE_COLOR));
            assertTrue("replacement frames show the same document",
                    second.allFramesNearColor(CAPTURE_PAGE_COLOR));
            milestones.record("explicit replacement lease delivered");
        } finally {
            runOnMain(replacement::release);
        }
        assertEquals("test milestone writes succeeded", 0, milestones.failureCount());
        bringMainActivityToFrontForTest();
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
    }

    /** R1: after a never-leased idle release, a private move allocates nothing without demand. */
    @Test
    public void homeAfterNeverLeasedIdleDoesNotReallocateWithoutDemand() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("webview hosted offscreen", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        // Wait out the never-leased idle release against the authoritative anchor (exact deadline).
        long readinessAnchor = runOnMainSync(hosting::lastDemandAnchorElapsedMs);
        long neverLeasedDeadline = readinessAnchor + HostingPolicy.IDLE_RELEASE_MS;
        while (SystemClock.elapsedRealtime() < neverLeasedDeadline
                && runOnMainSync(hosting::captureResourcesPresent)) {
            SystemClock.sleep(50);
        }
        assertEquals("never-leased idle release completed by the exact deadline", false,
                runOnMainSync(hosting::captureResourcesPresent));
        // Return to the Phone UI and background again: the private move must preserve the same
        // live attachment WITHOUT recreating capture resources (no demand, deadline passed) and
        // without introducing any new idle window (R1 corrected wiring).
        bringMainActivityToFrontForTest();
        waitUntil("webview back on phone ui", () -> hostViewSnapshot().viewAttached);
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("hosted offscreen again without capture allocation", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        // Bounded no-reallocation observation: nothing recreates the reader absent demand.
        SystemClock.sleep(5_000);
        assertEquals("no capture resources recreated by the demand-free private move", false,
                runOnMainSync(hosting::captureResourcesPresent));
        assertEquals("hosting session persists through the demand-free moves",
                HostingController.State.HOSTING, runOnMainSync(hosting::status).state);
        // Regression (completion epoch): cycle 1's completion event must not satisfy cycle 2.
        // The new demand invalidates the recorded completion immediately...
        long staleCompletion = runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs);
        assertTrue("cycle 1 completion was observed", staleCompletion > 0);
        CollectingConsumer consumer = new CollectingConsumer();
        HostingController.Lease lease = runOnMainSync(() -> hosting.acquireLease(consumer));
        assertNotNull("genuine demand recreates the capture surface", lease);
        assertEquals("new demand invalidates the prior cycle's completion evidence", 0L,
                runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs).longValue());
        waitUntil("frames flow after genuine demand", () -> consumer.count() > 0);
        // ...and cycle 2's own release must produce a completion bound to THIS cycle's anchor
        // (>= anchor2 rejects the stale event by construction: anchor2 > staleCompletion).
        long anchor2 = runOnMainSync(hosting::lastDemandAnchorElapsedMs);
        assertTrue("cycle 2 anchor postdates the stale completion", anchor2 > staleCompletion);
        runOnMain(lease::release);
        long cycle2Deadline = anchor2 + HostingPolicy.IDLE_RELEASE_MS;
        long cycle2Completion = 0;
        while (SystemClock.elapsedRealtime() < cycle2Deadline + 10_000) {
            cycle2Completion = runOnMainSync(hosting::lastIdleReleaseCompletedElapsedMs);
            if (cycle2Completion >= anchor2) {
                break;
            }
            SystemClock.sleep(50);
        }
        assertTrue("cycle 2 completion observed for the current release only",
                cycle2Completion >= anchor2);
        assertTrue("cycle 2 completion within the exact deadline",
                cycle2Completion <= cycle2Deadline);
        assertEquals("capture resources absent after cycle 2 completion", false,
                runOnMainSync(hosting::captureResourcesPresent));
        runOnMain(lease::release);
        bringMainActivityToFrontForTest();
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
    }

    /** First-frame delivery bound: 2s from eligibility (uptime-based, consumer-side). */
    private boolean firstFrameDelayIsValid(long deliveryDelayMs) {
        return deliveryDelayMs >= 0 && deliveryDelayMs <= 2_000;
    }

    /**
     * Records the contemporaneous interactive/keyguard/device-secure/recovery facts that ground
     * the display-off/lock coverage decision, app-scoped, while produced.
     */
    private void recordLockRecoverabilityAssessment() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(
                Context.KEYGUARD_SERVICE);
        String assessment = "LOCK_ASSESSMENT interactive=" + power.isInteractive()
                + " deviceLocked=" + keyguard.isDeviceLocked()
                + " keyguardRestricted=" + keyguard.inKeyguardRestrictedInputMode()
                + " deviceSecure=" + keyguard.isDeviceSecure()
                + " keyguardSecure=" + keyguard.isKeyguardSecure()
                + " => displayOffLockExercise="
                + (keyguard.isDeviceSecure()
                        ? "NOT safely recoverable unattended (returning requires the Owner's "
                                + "unlock credential, which is never requested or recorded)"
                        : "safely recoverable");
        System.out.println(assessment);
        milestones.record(assessment);
    }

    @AfterClass
    public static void flushMilestoneSink() {
        if (milestones != null) {
            milestones.flushToStream("hosting correction round 3 execution");
        }
    }

    // -------------------------------------------------------------- utilities

    private int[] currentWebViewSize() {
        return runOnMainSync(() -> {
            WebView view = session.view();
            return view == null ? new int[]{0, 0} : new int[]{view.getWidth(), view.getHeight()};
        });
    }

    private void openFixture(String path, String expectedTitle) throws Exception {
        String url = FIXTURE_BASE + path;
        onView(withId(R.id.address_input)).perform(click(), replaceText(url));
        onView(withId(R.id.button_open)).perform(click());
        androidx.test.espresso.Espresso.closeSoftKeyboard();
        try {
            waitUntil("fixture page " + path, () -> expectedTitle.equals(domText("page-title")));
        } catch (AssertionError failure) {
            HostingController.Status status = runOnMainSync(hosting::status);
            fail(failure.getMessage() + " (displayUrl=" + runOnMainSync(session::displayUrl)
                    + " loading=" + runOnMainSync(session::isLoading) + " error="
                    + runOnMainSync(session::errorMessage) + " title=" + domTextOrNull("page-title")
                    + " state=" + status.state + " attachment=" + status.attachment + ")");
        }
    }

    private String domTextOrNull(String elementId) {
        try {
            return domText(elementId);
        } catch (RuntimeException | AssertionError e) {
            return "(unreadable)";
        }
    }

    private HostingController.Status awaitHostingState(HostingController.State state, long boundMs) {
        long deadline = SystemClock.uptimeMillis() + boundMs;
        AtomicReference<HostingController.Status> reached = new AtomicReference<>();
        while (SystemClock.uptimeMillis() < deadline) {
            HostingController.Status status = runOnMainSync(hosting::status);
            if (status.state == state) {
                reached.set(status);
                return status;
            }
            SystemClock.sleep(50);
        }
        HostingController.Status last = runOnMainSync(hosting::status);
        fail("hosting never reached " + state + " within " + boundMs + "ms (last: " + last.state
                + ", reason: " + last.failureReason + ")");
        return reached.get();
    }

    private void waitUntilMain(String description, BooleanSupplier condition) {
        waitUntil(description, () -> runOnMainSync(condition::getAsBoolean));
    }

    /** One coherent main-thread snapshot; never call this from inside a main-sync block. */
    private HostViewSnapshot hostViewSnapshot() {
        return runOnMainSync(() -> {
            WebView view = session.view();
            return new HostViewSnapshot(hosting.status(),
                    view != null && view.getParent() != null);
        });
    }

    private static final class HostViewSnapshot {

        final HostingController.Status status;
        final boolean viewAttached;

        HostViewSnapshot(HostingController.Status status, boolean viewAttached) {
            this.status = status;
            this.viewAttached = viewAttached;
        }
    }

    private void waitUntil(String description, BooleanSupplier condition) {
        waitUntil(description, condition, TIMEOUT_MS);
    }

    private void waitUntil(String description, BooleanSupplier condition, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (RuntimeException | AssertionError ignored) {
                // Retry until the deadline; the final failure carries the description.
            }
            SystemClock.sleep(100);
        }
        HostingController.Status status = runOnMainSync(hosting::status);
        fail("timed out waiting for " + description + " (state=" + status.state + " attachment="
                + status.attachment + " browserLive=" + status.browserLive + " gen="
                + status.generation + " reason=" + status.failureReason + " parentless="
                + webViewParentless() + ")");
    }

    private void runOnMain(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }

    private <T> T runOnMainSync(java.util.function.Supplier<T> supplier) {
        AtomicReference<T> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> result.set(supplier.get()));
        return result.get();
    }

    private int webViewIdentityHash() {
        Integer identity = runOnMainSync(() -> {
            WebView view = session.view();
            return view == null ? 0 : System.identityHashCode(view);
        });
        assertNotNull(identity);
        assertTrue("no live WebView to track", identity != 0);
        return identity;
    }

    private boolean webViewParentless() {
        Boolean parentless = runOnMainSync(() -> {
            WebView view = session.view();
            return view == null || view.getParent() == null;
        });
        return Boolean.TRUE.equals(parentless);
    }

    private String domText(String elementId) {
        String result = evaluateJs("(function(){var el=document.getElementById('" + elementId
                + "');return el ? el.textContent : null;})()");
        return decode(result);
    }

    private String readFieldValue() {
        return decode(evaluateJs("document.getElementById('hosting-field').value"));
    }

    private void setFieldValue(String value) {
        // Fixture setup through page JavaScript: not typing or IME evidence.
        evaluateJs("(function(){var el=document.getElementById('hosting-field');"
                + "el.value=" + JSONObject.quote(value) + ";"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));return el.value;})()");
    }

    private String evaluateJs(String expression) {
        WebView view = runOnMainSync(session::view);
        if (view == null) {
            throw new IllegalStateException("no live WebView to evaluate against");
        }
        AtomicReference<String> raw = new AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                view.evaluateJavascript(expression, value -> {
                    raw.set(value);
                    latch.countDown();
                }));
        try {
            assertTrue("javascript evaluation timed out", latch.await(10_000,
                    java.util.concurrent.TimeUnit.MILLISECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while evaluating javascript", interrupted);
        }
        return raw.get();
    }

    private static String decode(String raw) {
        if (raw == null || "null".equals(raw)) {
            return null;
        }
        try {
            Object value = new JSONObject("{\"v\":" + raw + "}").get("v");
            return value == JSONObject.NULL ? null : String.valueOf(value);
        } catch (org.json.JSONException e) {
            return raw;
        }
    }

    private int loadCount(String path) throws Exception {
        java.net.URL url = new java.net.URL(FIXTURE_BASE + "/api/observations");
        try (java.io.InputStream in = url.openStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            JSONObject observations = new JSONObject(out.toString("UTF-8"));
            JSONObject loads = observations.optJSONObject("loads");
            return loads == null ? 0 : loads.optInt(path, 0);
        }
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * Collects per-frame delivery metadata and one sampled delivered pixel (never bitmaps) from
     * the capture thread. The pixel sample is read from the delivered borrowed bitmap during the
     * callback, tying the oracle to actual delivered content.
     */
    private static final class CollectingConsumer implements HostingController.FrameConsumer {

        private final java.util.List<long[]> frames = new java.util.ArrayList<>();
        private final List<Long> deliveryUptime = new ArrayList<>();

        @Override
        public synchronized void onFrame(HostingFrame frame) {
            deliveryUptime.add(SystemClock.uptimeMillis());
            frames.add(new long[]{frame.sequence, frame.contentHash, frame.captureElapsedMs,
                    frame.generation, frame.width, frame.height, frame.bitmap.getPixel(10, 10),
                    SystemClock.elapsedRealtime()});
        }

        synchronized int count() {
            return frames.size();
        }

        synchronized long deliveryUptimeAt(int index) {
            return deliveryUptime.get(index);
        }

        synchronized long deliveryElapsedAt(int index) {
            return frames.get(index)[7];
        }

        synchronized long latestCaptureElapsed() {
            return frames.isEmpty() ? Long.MIN_VALUE : frames.get(frames.size() - 1)[2];
        }

        synchronized long latestDeliveryElapsed() {
            return frames.isEmpty() ? Long.MIN_VALUE : frames.get(frames.size() - 1)[7];
        }

        synchronized int distinctHashes() {
            return (int) frames.stream().mapToLong(f -> f[1]).distinct().count();
        }

        synchronized boolean allFramesMatchGeneration(int generation) {
            return frames.stream().allMatch(f -> f[3] == generation);
        }

        synchronized boolean allFramesMatchSize(int width, int height) {
            return frames.stream().allMatch(f -> f[4] == width && f[5] == height);
        }

        /** True when frames from {@code fromIndex} on all match the given viewport. */
        synchronized boolean tailFramesMatchSize(int fromIndex, int width, int height) {
            for (int i = Math.max(0, fromIndex); i < frames.size(); i++) {
                long[] frame = frames.get(i);
                if (frame[4] != width || frame[5] != height) {
                    return false;
                }
            }
            return true;
        }

        synchronized boolean monotonicCaptureStamps() {
            for (int i = 1; i < frames.size(); i++) {
                if (frames.get(i)[2] < frames.get(i - 1)[2]) {
                    return false;
                }
            }
            return true;
        }

        /** True when frames from {@code fromIndex} on all match the expected sampled pixel. */
        synchronized boolean tailFramesNearColor(int fromIndex, int expectedColor) {
            boolean any = false;
            for (int i = Math.max(0, fromIndex); i < frames.size(); i++) {
                any = true;
                if (!nearColor((int) frames.get(i)[6], expectedColor)) {
                    return false;
                }
            }
            return any;
        }

        /** True when the latest delivered frame's sampled pixel matches the expected color. */
        synchronized boolean latestFrameNearColor(int expectedColor) {
            if (frames.isEmpty()) {
                return false;
            }
            return nearColor((int) frames.get(frames.size() - 1)[6], expectedColor);
        }

        /** True when every frame delivered in the last {@code windowMs} matches the color. */
        synchronized boolean recentFramesNearColor(int expectedColor, long windowMs) {
            long cutoff = SystemClock.elapsedRealtime() - windowMs;
            boolean any = false;
            for (long[] frame : frames) {
                if (frame[7] >= cutoff) {
                    any = true;
                    if (!nearColor((int) frame[6], expectedColor)) {
                        return false;
                    }
                }
            }
            return any;
        }

        synchronized boolean allFramesNearColor(int expectedColor) {
            return frames.stream().allMatch(f -> nearColor((int) f[6], expectedColor));
        }

        synchronized java.util.Set<Long> distinctHashSet() {
            java.util.Set<Long> hashes = new HashSet<>();
            for (long[] frame : frames) {
                hashes.add(frame[1]);
            }
            return hashes;
        }
    }

    /** Channel-wise comparison with tolerance; robust to renderer color-management drift. */
    private static boolean nearColor(int actual, int expected) {
        return Math.abs(Color.red(actual) - Color.red(expected)) <= PIXEL_CHANNEL_TOLERANCE
                && Math.abs(Color.green(actual) - Color.green(expected)) <= PIXEL_CHANNEL_TOLERANCE
                && Math.abs(Color.blue(actual) - Color.blue(expected)) <= PIXEL_CHANNEL_TOLERANCE;
    }

    /**
     * Wraps a {@link CollectingConsumer} with an in-callback hold, occupying the borrowed bitmap
     * across release/reacquire so the isolation property is exercised, not assumed (R2). The
     * entered latch is a controlled barrier: the test observes that the consumer actually entered
     * its callback before driving the ownership transition.
     */
    private static final class DelayedConsumer implements HostingController.FrameConsumer {

        private final CollectingConsumer collector = new CollectingConsumer();
        private final java.util.concurrent.CountDownLatch entered =
                new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch released =
                new java.util.concurrent.CountDownLatch(1);
        private volatile boolean holdFailed;

        @Override
        public void onFrame(HostingFrame frame) {
            entered.countDown();
            try {
                // Below the lease TTL; a broken test must not strand the capture thread.
                if (!released.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    holdFailed = true;
                    return;
                }
            } catch (InterruptedException interrupted) {
                holdFailed = true;
                Thread.currentThread().interrupt();
                return;
            }
            collector.onFrame(frame);
        }

        CollectingConsumer collector() {
            return collector;
        }

        boolean awaitEntered(long timeoutMs) throws InterruptedException {
            return entered.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        void releaseHold() {
            released.countDown();
        }

        boolean holdFailed() {
            return holdFailed;
        }
    }

    /**
     * Platform factory with an injectable, recoverable reader-recreation failure for the R7
     * rollback/failure-surfacing case; otherwise fully delegating.
     */
    private static final class SettableFactory implements PrivateDisplayHost.Factory {

        final PrivateDisplayHost.PlatformFactory platform = new PrivateDisplayHost.PlatformFactory();
        volatile boolean throwOnNextReader;

        @Override
        public VirtualDisplay createVirtualDisplay(DisplayManager manager, String name, int width,
                int height, int densityDpi, Object surface) {
            return platform.createVirtualDisplay(manager, name, width, height, densityDpi, surface);
        }

        @Override
        public ImageReader createImageReader(int width, int height) {
            if (throwOnNextReader) {
                throw new IllegalStateException("injected reader recreation failure");
            }
            return platform.createImageReader(width, height);
        }

        @Override
        public PrivateDisplayHost.PresentationHost createPresentation(Context context,
                android.view.Display display) {
            return platform.createPresentation(context, display);
        }
    }

    /** Renews the lease every second from a test thread; {@code renew()} posts to the main thread. */
    private static final class LeaseRenewal extends Thread {

        private final HostingController.Lease lease;
        private volatile boolean running = true;

        LeaseRenewal(HostingController.Lease lease) {
            this.lease = lease;
            setDaemon(true);
        }

        @Override
        public void run() {
            while (running) {
                lease.renew();
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        void stopRenewing() throws InterruptedException {
            running = false;
            interrupt();
            join(2_000); // Called by the test thread, never under the controller monitor.
            assertFalse("renewal producer terminated before the final anchor", isAlive());
        }
    }

    /** Records the actual interactive/lock state; screen-off is never claimed as secure lock. */
    private void recordDeviceState(String label) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(
                Context.KEYGUARD_SERVICE);
        String line = "DEVICE_STATE " + label + " interactive=" + power.isInteractive()
                + " deviceLocked=" + keyguard.isDeviceLocked()
                + " keyguardRestricted=" + keyguard.inKeyguardRestrictedInputMode();
        System.out.println(line);
        milestones.record(line);
    }

    /** Same-process memory milestone; no process reset occurs between milestones. */
    private void memoryMilestone(String label) {
        long nativeHeap = android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576;
        Runtime runtime = Runtime.getRuntime();
        long javaUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576;
        String line = "MEMORY_MILESTONE " + label + " nativeHeapMB=" + nativeHeap
                + " javaUsedMB=" + javaUsed;
        System.out.println(line);
        milestones.record(line);
    }
}
