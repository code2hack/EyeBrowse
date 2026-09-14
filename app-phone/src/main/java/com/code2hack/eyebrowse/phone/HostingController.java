package com.code2hack.eyebrowse.phone;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

/**
 * The one hosting state machine: explicit native Start/Stop of the same live browser, the single
 * private display host, the bounded frame lease and its wake lock.
 *
 * <p>All methods run on the main thread. Hosting is a generation-fenced, explicitly started
 * session: it never starts by itself, never grants any remote control (that remains a later
 * slice), survives Activity backgrounding/exit while the service lives, and requires an explicit
 * Phone restart after renderer or service loss (no automatic page replay). Stop revokes the lease,
 * releases hosting-only resources and leaves normal browsing and persisted site data intact.
 *
 * <p>Retained while hosting: the live session WebView attached to the private presentation (or the
 * Phone UI), the foreground service, and — only while a lease is live or within the idle window —
 * the capture reader/surface/thread and wake lock. Bounded absent-client behavior: after 30 s
 * without lease demand the capture resources are released; the display attachment and service
 * remain until Stop.
 */
final class HostingController {

    /** Non-sensitive state-transition diagnostics (SPEC §13: transitions, not page content). */
    private static final String TAG = "EyeBrowseHosting";

    enum State {
        NOT_HOSTING,
        STARTING,
        HOSTING,
        STOPPING
    }

    enum Attachment {
        NONE,
        PHONE_UI,
        PRIVATE_DISPLAY
    }

    /** Distinguishes browser lifetime, hosting generation, attachment and capture activity. */
    static final class Status {

        final State state;
        final int generation;
        final Attachment attachment;
        final boolean browserLive;
        final boolean captureActive;
        final boolean wakeLockHeld;
        @Nullable
        final String failureReason;

        Status(State state, int generation, Attachment attachment, boolean browserLive,
                boolean captureActive, boolean wakeLockHeld, @Nullable String failureReason) {
            this.state = state;
            this.generation = generation;
            this.attachment = attachment;
            this.browserLive = browserLive;
            this.captureActive = captureActive;
            this.wakeLockHeld = wakeLockHeld;
            this.failureReason = failureReason;
        }
    }

    /** The single live in-process frame consumer lease; renewal keeps liveness. */
    final class Lease {

        private boolean revoked;

        /** Must be called within {@link HostingPolicy#LEASE_TTL_MS} of the last renewal. */
        public synchronized void renew() {
            runOnMain(() -> {
                if (revoked || lease != this) {
                    return;
                }
                lastLeaseRenewElapsedMs = android.os.SystemClock.elapsedRealtime();
                lastDemandElapsedMs = lastLeaseRenewElapsedMs;
                ensureWakeLock();
            });
        }

        public synchronized void release() {
            runOnMain(() -> {
                if (revoked || lease != this) {
                    return;
                }
                revokeLease();
            });
        }

        synchronized void markRevoked() {
            revoked = true;
        }
    }

    /** Receives frames on the capture thread while a lease is live. */
    interface FrameConsumer {
        void onFrame(HostingFrame frame);
    }

    private static final String WAKE_LOCK_TAG = "EyeBrowse:HostingCapture";
    private static final long WATCHDOG_INTERVAL_MS = 1_000;

    /** Plan bound: Start reaches active or explicit failure within 5 seconds of the request. */
    private static final long START_COMPLETION_TIMEOUT_MS = 5_000;

    private static HostingController instance;

    static synchronized HostingController get(Context context) {
        if (instance == null) {
            instance = new HostingController(context.getApplicationContext());
        }
        return instance;
    }

    private final Context appContext;
    private final PhoneBrowserSession session;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PrivateDisplayHost displayHost;
    private HostingService hostingService;
    private PowerManager.WakeLock wakeLock;

    private State state = State.NOT_HOSTING;
    private Attachment attachment = Attachment.NONE;
    private int generation;
    private @Nullable String failureReason;

    private Lease lease;
    private Lease deliveryLease;
    private HostingController.FrameConsumer frameConsumer;
    private long lastLeaseRenewElapsedMs;
    private long lastDemandElapsedMs;
    private boolean stopRequestedDuringStart;
    private final Runnable startTimeout = this::onStartTimeout;

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            watchdogTick();
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    private HostingController(Context appContext) {
        this.appContext = appContext;
        this.session = PhoneBrowserSession.get(appContext);
        this.session.addListener(this::onSessionChanged);
    }

    // ---------------------------------------------------------------- status

    synchronized Status status() {
        boolean captureActive = displayHost != null && displayHost.isCapturing();
        boolean wakeLockHeld = wakeLock != null && wakeLock.isHeld();
        return new Status(state, generation, attachment, session.isLive(), captureActive,
                wakeLockHeld, failureReason);
    }

    /** True when any capture resource (reader or thread) still exists. */
    synchronized boolean captureResourcesPresent() {
        return displayHost != null && (displayHost.hasReader() || displayHost.hasCaptureThread());
    }

    synchronized boolean hasDisplayResources() {
        return displayHost != null && (displayHost.hasDisplay() || displayHost.hasPresentation());
    }

    synchronized boolean isWakeLockHeld() {
        return wakeLock != null && wakeLock.isHeld();
    }

    synchronized int currentGeneration() {
        return generation;
    }

    // ---------------------------------------------------------------- start

    /**
     * Requests hosting of the current live browser. Returns {@code false} with a recorded failure
     * reason when the request is invalid; asynchronous platform failures roll back and surface in
     * {@link Status#failureReason}. Completion (either way) happens within the plan's 5-second
     * bound and is observable through {@link #status()}.
     */
    synchronized boolean start() {
        if (state == State.STARTING || state == State.HOSTING || state == State.STOPPING) {
            return false;
        }
        if (!session.isLive()) {
            failureReason = appContext.getString(R.string.hosting_failure_no_page);
            return false;
        }
        failureReason = null;
        stopRequestedDuringStart = false;
        state = State.STARTING;
        generation++;
        Intent intent = new Intent(appContext, HostingService.class);
        intent.setAction(HostingService.ACTION_START);
        try {
            appContext.startService(intent);
        } catch (RuntimeException error) {
            failStart("service start: " + error.getMessage());
            return true;
        }
        mainHandler.postDelayed(startTimeout, START_COMPLETION_TIMEOUT_MS);
        return true;
    }

    /** Called by the service once it entered the foreground. */
    synchronized void onServiceReady(HostingService service) {
        if (state != State.STARTING || hostingService != null) {
            return;
        }
        hostingService = service;
        if (stopRequestedDuringStart) {
            completeStop();
            return;
        }
        WebViewMetric metric = WebViewMetric.measure(session);
        String sizeError = HostingPolicy.viewportError(metric.width, metric.height);
        if (sizeError != null) {
            failStart(sizeError);
            return;
        }
        displayHost = new PrivateDisplayHost(resourceFactory);
        try {
            displayHost.create(service, metric.width, metric.height, metric.densityDpi);
        } catch (HostingException error) {
            rollbackDisplayHost();
            failStart(error.getMessage());
            return;
        }
        // The visible Phone UI keeps its interactive WebView; the presentation receives the view
        // only when the UI backgrounds (or the Activity is destroyed) while hosting continues.
        attachment = session.view() != null && session.view().getParent() == null
                ? Attachment.PRIVATE_DISPLAY
                : Attachment.PHONE_UI;
        state = State.HOSTING;
        Log.i(TAG, "hosting active gen=" + generation + " viewport=" + metric.width + "x"
                + metric.height + "@" + metric.densityDpi + " attachment=" + attachment);
        lastDemandElapsedMs = android.os.SystemClock.elapsedRealtime();
        mainHandler.post(watchdog);
    }

    private void onStartTimeout() {
        synchronized (this) {
            if (state == State.STARTING) {
                failStart("hosting start did not complete in time");
            }
        }
    }

    private void failStart(String reason) {
        Log.i(TAG, "start failed gen=" + generation + " reason=" + reason);
        mainHandler.removeCallbacks(startTimeout);
        rollbackDisplayHost();
        releaseWakeLock();
        if (hostingService != null) {
            hostingService.stopSelf();
            hostingService = null;
        }
        state = State.NOT_HOSTING;
        attachment = Attachment.NONE;
        failureReason = reason;
        mainHandler.removeCallbacks(watchdog);
    }

    // ---------------------------------------------------------------- stop

    /** Explicit Stop; idempotent, bounded, and safe during startup or when not hosting. */
    synchronized void stop() {
        switch (state) {
            case NOT_HOSTING:
                return;
            case STARTING:
                stopRequestedDuringStart = true;
                revokeLease();
                return;
            case STOPPING:
                return;
            case HOSTING:
                state = State.STOPPING;
                revokeLease();
                completeStop();
                return;
        }
    }

    private void completeStop() {
        Log.i(TAG, "stop complete gen=" + generation);
        mainHandler.removeCallbacks(watchdog);
        revokeLease();
        if (displayHost != null) {
            displayHost.release(session);
            displayHost = null;
        }
        releaseWakeLock();
        if (hostingService != null) {
            hostingService.stopSelf();
            hostingService = null;
        }
        state = State.NOT_HOSTING;
        attachment = Attachment.NONE;
    }

    // ------------------------------------------------------ attachment moves

    /**
     * Moves the live WebView from the Phone UI into the private presentation. The caller's
     * attachment token must still own the session: an old Activity being destroyed must never
     * steal the view from a newly attached Activity (system transition ordering can interleave
     * their {@code onStop}/{@code onStart} either way).
     */
    synchronized void moveWebViewToPrivateDisplay(PhoneBrowserSession.Attachment token) {
        Log.i(TAG, "moveToPrivateDisplay state=" + state + " attachment=" + attachment);
        if (state != State.HOSTING || displayHost == null
                || attachment != Attachment.PHONE_UI) {
            return;
        }
        if (!session.isCurrentAttachment(token)) {
            Log.i(TAG, "moveToPrivateDisplay skipped: stale token" + identity(token));
            return;
        }
        displayHost.attachSessionView(session);
        attachment = Attachment.PRIVATE_DISPLAY;
        Log.i(TAG, "moveToPrivateDisplay done" + identity(token));
    }

    private String identity(PhoneBrowserSession.Attachment token) {
        return " token=" + System.identityHashCode(token);
    }

    /**
     * Reattaches the live WebView to returning Phone UI without reload, returning the fresh
     * attachment token the owning Activity must keep for its own {@code onDestroy}.
     */
    synchronized @Nullable PhoneBrowserSession.Attachment moveWebViewToPhoneUi(
            android.app.Activity activity, ViewGroup container) {
        Log.i(TAG, "moveToPhoneUi state=" + state + " attachment=" + attachment);
        if (state != State.HOSTING || attachment == Attachment.PHONE_UI) {
            return null;
        }
        PhoneBrowserSession.Attachment token = session.attach(activity, container);
        attachment = Attachment.PHONE_UI;
        Log.i(TAG, "moveToPhoneUi done token=" + System.identityHashCode(token));
        return token;
    }

    // ---------------------------------------------------------------- lease

    /** Acquires the single frame lease; {@code null} when hosting is inactive or already leased. */
    synchronized @Nullable Lease acquireLease(FrameConsumer consumer) {
        if (state != State.HOSTING || lease != null || displayHost == null) {
            return null;
        }
        if (!displayHost.hasReader()) {
            // Recreate the capture surface on the surviving display without navigation.
            WebViewMetric metric = WebViewMetric.measure(session);
            if (HostingPolicy.viewportError(metric.width, metric.height) != null) {
                return null;
            }
            displayHost.recreateCaptureSurface(metric.width, metric.height);
            if (!displayHost.hasReader()) {
                return null;
            }
        }
        lease = new Lease();
        deliveryLease = lease;
        frameConsumer = consumer;
        lastLeaseRenewElapsedMs = android.os.SystemClock.elapsedRealtime();
        lastDemandElapsedMs = lastLeaseRenewElapsedMs;
        ensureWakeLock();
        displayHost.startCapture(generation, this::deliverFrameIfLive);
        return lease;
    }

    private void deliverFrameIfLive(HostingFrame frame) {
        HostingController.FrameConsumer consumer;
        synchronized (this) {
            // Fenced on lease IDENTITY (an in-flight frame from a superseded lease must not reach
            // the replacement's consumer), hosting generation, and state.
            if (lease == null || lease != deliveryLease || frame.generation != generation
                    || state != State.HOSTING) {
                return;
            }
            consumer = frameConsumer;
        }
        if (consumer != null) {
            consumer.onFrame(frame);
        }
    }

    private void revokeLease() {
        if (lease != null) {
            lease.markRevoked();
            lease = null;
        }
        deliveryLease = null;
        frameConsumer = null;
        if (displayHost != null) {
            displayHost.stopCapture();
        }
        releaseWakeLock();
    }

    // ------------------------------------------------------------- watchdog

    private void watchdogTick() {
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (this) {
            if (state != State.HOSTING) {
                return;
            }
            if (lease != null && HostingPolicy.leaseExpired(now, lastLeaseRenewElapsedMs)) {
                // Liveness lost: revoke (stops production) and release the wake lock immediately,
                // well inside the 6-second bound; the idle window starts now.
                Log.i(TAG, "lease expired gen=" + generation);
                revokeLease();
            }
            if (lease == null && HostingPolicy.idleExceeded(now, lastDemandElapsedMs)) {
                Log.i(TAG, "idle release gen=" + generation);
                displayHost.releaseCaptureResources();
            }
        }
    }

    private void ensureWakeLock() {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) appContext.getSystemService(
                    Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
            wakeLock.setReferenceCounted(false);
        }
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(HostingPolicy.WAKE_LOCK_TIMEOUT_MS);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    // ---------------------------------------------------- lifecycle events

    private void onSessionChanged(PhoneBrowserSession changed) {
        synchronized (this) {
            if (state == State.HOSTING && !changed.isLive()) {
                // The renderer is gone: hosting is interrupted and needs an explicit restart.
                Log.i(TAG, "browser lost while hosting gen=" + generation);
                state = State.STOPPING;
                revokeLease();
                completeStop();
                failureReason = appContext.getString(R.string.hosting_failure_browser_lost);
            }
        }
    }

    /** Called by the service when the system destroys it while hosting was expected to continue. */
    synchronized void onServiceDestroyed(HostingService service) {
        if (hostingService != service) {
            return;
        }
        hostingService = null;
        if (state == State.STARTING || state == State.HOSTING) {
            Log.i(TAG, "service destroyed while hosting gen=" + generation);
            state = State.STOPPING;
            revokeLease();
            completeStop();
            failureReason = appContext.getString(R.string.hosting_failure_service_lost);
        }
    }

    // -------------------------------------------------------------- helpers

    private void rollbackDisplayHost() {
        if (displayHost != null) {
            displayHost.release(session);
            displayHost = null;
        }
        attachment = Attachment.NONE;
    }

    /** Test-only seam: substitute the platform resource factory for failure injection. */
    synchronized void setResourceFactoryForTest(PrivateDisplayHost.Factory factory) {
        this.resourceFactory = factory;
    }

    private PrivateDisplayHost.Factory resourceFactory = new PrivateDisplayHost.PlatformFactory();

    /** The measured WebView content viewport used as the private-display geometry. */
    private static final class WebViewMetric {

        final int width;
        final int height;
        final int densityDpi;

        WebViewMetric(int width, int height, int densityDpi) {
            this.width = width;
            this.height = height;
            this.densityDpi = densityDpi;
        }

        static WebViewMetric measure(PhoneBrowserSession session) {
            android.webkit.WebView view = session.view();
            if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
                return new WebViewMetric(0, 0, 0);
            }
            return new WebViewMetric(view.getWidth(), view.getHeight(),
                    view.getResources().getConfiguration().densityDpi);
        }
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
