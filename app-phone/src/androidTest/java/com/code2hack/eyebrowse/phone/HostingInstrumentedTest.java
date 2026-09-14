package com.code2hack.eyebrowse.phone;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Focused hosting-lifecycle instrumentation: the same live System WebView survives explicit
 * Start/Stop and the Phone UI ↔ private presentation attachment moves without reload, losing field
 * state or WebView identity.
 *
 * <p>Evidence boundaries: activation uses the real Start/Stop controls through the ordinary view
 * pipeline; page reads and fixture field setup use {@link WebView#evaluateJavascript} (fixture
 * setup, not typing evidence); backgrounding uses {@code moveTaskToBack}, which proves the
 * Activity lifecycle path but is not a physical Home press or secure-lock observation.
 */
@RunWith(AndroidJUnit4.class)
public class HostingInstrumentedTest {

    private static final String DEFAULT_FIXTURE_BASE = "http://127.0.0.1:25341";
    private static final String FIXTURE_BASE = trimTrailingSlash(
            InstrumentationRegistry.getArguments().getString("fixtureBaseUrl", DEFAULT_FIXTURE_BASE));
    private static final long START_BOUND_MS = 5_000;
    private static final long STOP_BOUND_MS = 5_000;
    private static final long TIMEOUT_MS = 20_000;

    private ActivityScenario<MainActivity> scenario;
    private PhoneBrowserSession session;
    private HostingController hosting;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        session = PhoneBrowserSession.get(context);
        hosting = HostingController.get(context);
        ensureNotificationPermissionSetupForTest();
        // Independent cases: hosting stopped and the session back to a clean, never-loaded state.
        runOnMain(hosting::stop);
        waitUntilMain("hosting stopped", () -> hosting.status().state == HostingController.State.NOT_HOSTING);
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
            runShellCommandForTest("pm grant com.code2hack.eyebrowse.phone android.permission.POST_NOTIFICATIONS");
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
        return ContextCompat.checkSelfPermission(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Shell-launched return to the foreground reusing the existing instance (REORDER_TO_FRONT). */
    private void bringMainActivityToFrontForTest() {
        // 0x20000000 = FLAG_ACTIVITY_REORDER_TO_FRONT: no duplicate Activity is created.
        runShellCommandForTest("am start -f 0x20000000 -n com.code2hack.eyebrowse.phone/.MainActivity");
    }

    private void runShellCommandForTest(String command) {
        try {
            android.os.ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand(command);
            descriptor.close();
        } catch (RuntimeException | java.io.IOException ignored) {
            // Proceed: callers verify the observable effect instead of the command result.
        }
    }

    private String runShellCommandWithOutputForTest(String command) {
        try {
            android.os.ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand(command);
            try (java.io.InputStream in = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
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
     * ONE actual tap on the real hosting control via the codebase's validated instrumentation input
     * route (explicit screen coordinates, {@code sendPointerSync}, touchscreen source), after
     * verified readiness: focused window, laid-out visible button inside the window on the default
     * display, and a bounded post-transition settle. No retry — an unchanged state after the tap is
     * uncertain delivery and fails with diagnosis. A test-only touch observer on the button records
     * whether the window received the injected events at all.
     */
    private void tapHostingToggleOnce(HostingController.State expectedAfter, long boundMs) {
        awaitWindowFocusForTest();
        // Bounded transition settle BEFORE readiness: coordinates and focus must be observed as of
        // the tap moment, not across the settle delay. Nothing has been sent yet; this is not a retry.
        SystemClock.sleep(800);
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
     * injection quirks; it never substitutes the actual Start/Stop tap journey, which lives in the
     * {@code ...UiJourney} tests using single verified taps.
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

        // Return the same Activity instance to the foreground; moveToState(RESUMED) cannot
        // foreground a task that moveTaskToBack sent behind (verified on device).
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
        waitUntilMain("host intact after activity finish", () ->
                hosting.status().state == HostingController.State.HOSTING);
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

    /** Repeated Start/Stop cycles keep the page; repeated and out-of-band Stops are idempotent. */
    @Test
    public void startStopCyclesKeepPageAndRepeatedStopIsIdempotent() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
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
                new java.util.HashSet<>(stoppedStates).size());

        // Idempotent extra Stops, including one during a fresh startup.
        runOnMain(hosting::stop);
        runOnMain(hosting::stop);
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);

        onView(withId(R.id.button_hosting_toggle)).perform(click());
        runOnMain(hosting::stop); // Stop while STARTING must still end bounded and clean.
        awaitHostingState(HostingController.State.NOT_HOSTING, STOP_BOUND_MS);
        assertEquals("no capture resources after stop-during-start", false,
                runOnMainSync(hosting::captureResourcesPresent));

        // Stale-callback fencing across Stop: a consumer holding a lease when Stop lands receives
        // no further frames once teardown is confirmed (reader closed, capture thread exited),
        // and the teardown is bounded. The frame bitmap is deliberately not recycled while an
        // in-flight callback may still borrow it.
        CollectingConsumer staleConsumer = new CollectingConsumer();
        HostingController.Lease stoppedLease = runOnMainSync(() -> hosting.acquireLease(staleConsumer));
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

        assertEquals("page survives every cycle", marker, domText("load-marker"));
        assertEquals("no page reload from hosting cycles", loadsBefore, loadCount("/hosting.html"));
        assertEquals("same live WebView instance", webViewIdentity, webViewIdentityHash());
    }

    /** Injected resource-creation failures roll back partial state bounded and idempotently. */
    @Test
    public void partialAllocationFailuresRollBackBounded() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        PrivateDisplayHost.Factory platform = new PrivateDisplayHost.PlatformFactory();

        // Failure 1: virtual display creation refuses; the already-created reader must be released.
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

        // Failure 2: image reader creation refuses.
        hosting.setResourceFactoryForTest(new PrivateDisplayHost.Factory() {
            @Override
            public VirtualDisplay createVirtualDisplay(DisplayManager manager, String name,
                    int width, int height, int densityDpi, Object surface) {
                return platform.createVirtualDisplay(manager, name, width, height, densityDpi,
                        surface);
            }

            @Override
            public ImageReader createImageReader(int width, int height) {
                return null; // Injected allocation failure.
            }

            @Override
            public PrivateDisplayHost.PresentationHost createPresentation(Context context,
                    android.view.Display display) {
                return platform.createPresentation(context, display);
            }
        });
        onView(withId(R.id.button_hosting_toggle)).perform(click());
        awaitStartupOutcome(generationBefore + 2);
        assertEquals("reader failure rolled back capture resources", false,
                runOnMainSync(hosting::captureResourcesPresent));
        assertEquals("reader failure released display resources", false,
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
     * The bounded background-capture acceptance core: 120 seconds of active offscreen capture on
     * the real Phone with a live in-process lease, content correlation through the autonomous
     * fixture counter, in-process browser-action navigation while hosted, lease loss/reacquisition,
     * the 30-second idle release and the final Stop — with device-state and memory milestones
     * recorded at each boundary. Frame hashes cover every valid pixel (no sampling), so stale or
     * repeated surfaces cannot masquerade as fresh output.
     */
    @Test
    public void backgroundCaptureMeetsLivenessContentIdleAndStopBounds() throws Exception {
        openFixture("/hosting.html", "Hosting capture page");
        String marker = domText("load-marker");
        int loadsHosting = loadCount("/hosting.html");
        setFieldValue("capture-value");
        long generationBefore = runOnMainSync(() -> (long) hosting.currentGeneration());
        memoryMilestone("before-start");

        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS);
        assertEquals(generationBefore + 1,
                (long) runOnMainSync(() -> (long) hosting.currentGeneration()));
        scenario.onActivity(activity -> activity.moveTaskToBack(true));
        waitUntil("webview hosted offscreen", () -> {
            HostViewSnapshot snapshot = hostViewSnapshot();
            return snapshot.status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
                    && snapshot.viewAttached;
        });
        assertEquals("no reload while hosting started", loadsHosting,
                loadCount("/hosting.html"));
        recordDeviceState("backgrounded-before-capture");

        // --- Lease acquisition, first-frame bound and frame metadata.
        CollectingConsumer consumer = new CollectingConsumer();
        long acquireElapsed = SystemClock.elapsedRealtime();
        HostingController.Lease lease = runOnMainSync(() -> hosting.acquireLease(consumer));
        assertNotNull("lease must be acquirable while hosting", lease);
        LeaseRenewal renewal = new LeaseRenewal(lease);
        renewal.start();
        waitUntil("first offscreen frame", () -> consumer.count() > 0);
        long firstFrameDelayMs = consumer.captureElapsedAt(0) - acquireElapsed;
        assertTrue("first frame within 2s under ready page/live consumer: " + firstFrameDelayMs
                + "ms", firstFrameDelayMs <= 2_000);
        int expectedGeneration = (int) (generationBefore + 1);
        int[] expectedSize = runOnMainSync(() -> {
            android.webkit.WebView view = session.view();
            return new int[]{view.getWidth(), view.getHeight()};
        });
        assertTrue("frame carries the hosting generation",
                consumer.allFramesMatchGeneration(expectedGeneration));
        assertTrue("frame dimensions match the measured viewport",
                consumer.allFramesMatchSize(expectedSize[0], expectedSize[1]));
        assertTrue("capture stamps are monotonic", consumer.monotonicCaptureStamps());
        assertTrue("wake lock is held while the lease is live",
                runOnMainSync(hosting::isWakeLockHeld));

        // --- Content correlation: frozen+blurred page produces no frames; resuming the counter does.
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

        // --- In-process browser-action navigation while hosted offscreen: to the static second
        // page (proves the hosted view tracks navigation), then back to the counter page so active
        // capture continues for the 120-second window. The correlation baseline is taken
        // immediately before the navigation: the post-navigation frame must carry content hashes
        // never seen before it, correlating the frame with the second page's new content.
        int loadsTwoBefore = loadCount("/hosting-two.html");
        final java.util.Set<Long> hashesBeforeNavigation = consumer.distinctHashSet();
        final int framesBeforeNavigation = consumer.count();
        runOnMain(() -> session.openAddress(FIXTURE_BASE + "/hosting-two.html"));
        waitUntil("second page loaded while hosted",
                () -> "Second hosting page".equals(domText("page-title")));
        assertEquals("navigation load recorded once", loadsTwoBefore + 1,
                loadCount("/hosting-two.html"));
        int loadsTwoAfter = loadsTwoBefore + 1;
        waitUntil("frame with never-before-seen content after navigation", () ->
                consumer.hasFrameOutside(hashesBeforeNavigation)
                        && consumer.count() > framesBeforeNavigation);
        int loadsHostingBeforeReturn = loadCount("/hosting.html");
        runOnMain(() -> session.openAddress(FIXTURE_BASE + "/hosting.html"));
        waitUntil("counter page restored while hosted",
                () -> "Hosting capture page".equals(domText("page-title")));

        // --- Hold the live lease until 120 seconds of active capture have elapsed.
        long firstFrameElapsed = consumer.captureElapsedAt(0);
        waitUntil("120s of active offscreen capture",
                () -> consumer.latestCaptureElapsed() - firstFrameElapsed >= 120_000,
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

        // --- Lease loss: stop renewing; liveness expires 5s after the last renewal and production
        // must stop within the 6s bound, with the wake lock released.
        long lastRenewElapsed = renewal.stopRenewing();
        waitUntil("capture stopped after lease expiry", () ->
                SystemClock.elapsedRealtime() - consumer.latestCaptureElapsed() > 2_500);
        assertTrue("no frame later than 6s after liveness loss",
                consumer.latestCaptureElapsed()
                        <= lastRenewElapsed + HostingPolicy.LEASE_TTL_MS + 6_000);
        waitUntil("wake lock released after liveness loss",
                () -> !runOnMainSync(hosting::isWakeLockHeld));

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

        // --- Idle release: drop the lease; after 30s without demand the capture resources go while
        // the display/presentation attachment and the hosting session remain.
        renewal2.stopRenewing();
        runOnMain(lease2::release);
        memoryMilestone("lease-released-idle-window-start");
        long idleDeadline = SystemClock.uptimeMillis()
                + HostingPolicy.IDLE_RELEASE_MS + 10_000;
        while (SystemClock.uptimeMillis() < idleDeadline
                && runOnMainSync(hosting::captureResourcesPresent)) {
            SystemClock.sleep(1_000);
        }
        assertEquals("capture resources released after 30s idle", false,
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
    }

    // -------------------------------------------------------------- utilities

    private void openFixture(String path, String expectedTitle) throws Exception {
        String url = FIXTURE_BASE + path;
        onView(withId(R.id.address_input)).perform(click(), replaceText(url));
        onView(withId(R.id.button_open)).perform(click());
        androidx.test.espresso.Espresso.closeSoftKeyboard();
        try {
            waitUntil("fixture page " + path, () -> expectedTitle.equals(domText("page-title")));
        } catch (AssertionError failure) {
            // Rich one-line diagnosis: where the session thinks the browser is.
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
        String result = evaluateJs(
                "document.getElementById('hosting-field').value");
        return decode(result);
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
     * Collects frame metadata (never bitmaps) from the capture thread. Hashes are computed by the
     * producer over every valid pixel; the consumer records sequence, hash, capture stamp,
     * generation and dimensions for bounds assertions.
     */
    private static final class CollectingConsumer implements HostingController.FrameConsumer {

        private final java.util.List<long[]> frames = new java.util.ArrayList<>();

        @Override
        public synchronized void onFrame(HostingFrame frame) {
            frames.add(new long[]{frame.sequence, frame.contentHash, frame.captureElapsedMs,
                    frame.generation, frame.width, frame.height});
        }

        synchronized int count() {
            return frames.size();
        }

        synchronized long captureElapsedAt(int index) {
            return frames.get(index)[2];
        }

        synchronized long latestCaptureElapsed() {
            return frames.isEmpty() ? Long.MIN_VALUE : frames.get(frames.size() - 1)[2];
        }

        synchronized int distinctHashes() {
            return (int) frames.stream().mapToLong(f -> f[1]).distinct().count();
        }

        synchronized java.util.Set<Long> distinctHashSet() {
            java.util.Set<Long> hashes = new java.util.HashSet<>();
            for (long[] frame : frames) {
                hashes.add(frame[1]);
            }
            return hashes;
        }

        synchronized boolean hasFrameOutside(java.util.Set<Long> knownHashes) {
            for (long[] frame : frames) {
                if (!knownHashes.contains(frame[1])) {
                    return true;
                }
            }
            return false;
        }

        synchronized boolean allFramesMatchGeneration(int generation) {
            return frames.stream().allMatch(f -> f[3] == generation);
        }

        synchronized boolean allFramesMatchSize(int width, int height) {
            return frames.stream().allMatch(f -> f[4] == width && f[5] == height);
        }

        synchronized boolean monotonicCaptureStamps() {
            for (int i = 1; i < frames.size(); i++) {
                if (frames.get(i)[2] < frames.get(i - 1)[2]) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Renews the lease every second from a test thread; {@code renew()} posts to the main thread. */
    private static final class LeaseRenewal extends Thread {

        private final HostingController.Lease lease;
        private volatile boolean running = true;
        private volatile long lastRenewElapsed;

        LeaseRenewal(HostingController.Lease lease) {
            this.lease = lease;
            setDaemon(true);
        }

        @Override
        public void run() {
            while (running) {
                lease.renew();
                lastRenewElapsed = android.os.SystemClock.elapsedRealtime();
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        long stopRenewing() {
            running = false;
            interrupt();
            return lastRenewElapsed;
        }
    }

    /** Records the actual interactive/lock state; screen-off is never claimed as secure lock. */
    private void recordDeviceState(String label) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.os.PowerManager power = (android.os.PowerManager) context.getSystemService(
                Context.POWER_SERVICE);
        android.app.KeyguardManager keyguard = (android.app.KeyguardManager) context.getSystemService(
                Context.KEYGUARD_SERVICE);
        System.out.println("DEVICE_STATE " + label + " interactive=" + power.isInteractive()
                + " deviceLocked=" + keyguard.isDeviceLocked()
                + " keyguardRestricted=" + keyguard.inKeyguardRestrictedInputMode());
    }

    /** Same-process memory milestone; no process reset occurs between milestones. */
    private void memoryMilestone(String label) {
        long nativeHeap = android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576;
        Runtime runtime = Runtime.getRuntime();
        long javaUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576;
        System.out.println("MEMORY_MILESTONE " + label + " nativeHeapMB=" + nativeHeap
                + " javaUsedMB=" + javaUsed);
    }
}
