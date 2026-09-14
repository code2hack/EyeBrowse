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
 * <p>Delivery identity: each lease hands the capture pipeline a bound sink holding its own lease
 * token, hosting generation and consumer; {@link FrameGate} admits frames against that token, so
 * an in-flight callback of a superseded lease can never reach a replacement consumer. Revocation
 * prevents new admissions; an admitted frame finishes delivery to its own consumer.
 *
 * <p>UI availability is tracked through {@code STARTING}: if the Phone UI hides or is destroyed
 * before service readiness, readiness attaches the live view to the private presentation instead
 * of reporting hosting with an empty offscreen display.
 *
 * <p>Retained while hosting: the live session WebView attached to the private presentation (or the
 * Phone UI), the foreground service, and — only while a lease is live or within the idle window —
 * the capture reader/surface/thread and wake lock. Bounded absent-client behavior: the capture
 * resources are released by 30 s after the last successful demand (scheduled, not polled past the
 * deadline); the display attachment and service remain until Stop.
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
        public void renew() {
            runOnMain(() -> {
                if (revoked || lease != this) {
                    return;
                }
                lastLeaseRenewElapsedMs = android.os.SystemClock.elapsedRealtime();
                lastDemandElapsedMs = lastLeaseRenewElapsedMs;
                wakeLockKeeper.refresh(); // Refresh the bounded platform timeout while live (F9).
                scheduleIdleRelease();
            });
        }

        public void release() {
            runOnMain(() -> {
                if (revoked || lease != this) {
                    return;
                }
                revokeLease();
                scheduleIdleRelease(); // Demand ended now; the idle window is anchored here.
                notifyHostingChanged();
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

    /** Notified on the main thread after any hosting state transition reached its final state. */
    interface Listener {
        void onHostingChanged();
    }

    private final java.util.List<Listener> listeners = new java.util.ArrayList<>();

    void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyHostingChanged() {
        for (Listener listener : new java.util.ArrayList<>(listeners)) {
            listener.onHostingChanged();
        }
    }

    /**
     * Immutable per-lease delivery sink bound at acquisition (F1): the capture pipeline invokes
     * this object, and admission through the lock-free {@link FrameGate} fences superseded leases,
     * replacement consumers and closed gates without taking any monitor.
     */
    private final class BoundSink implements PrivateDisplayHost.FrameSink {

        private final Lease leaseToken;
        private final int hostingGeneration;
        private final FrameConsumer consumer;

        BoundSink(Lease leaseToken, int hostingGeneration, FrameConsumer consumer) {
            this.leaseToken = leaseToken;
            this.hostingGeneration = hostingGeneration;
            this.consumer = consumer;
        }

        @Override
        public void onFrame(HostingFrame frame) {
            if (!frameGate.admit(leaseToken, frame.generation)) {
                return; // Fenced: superseded lease, replacement token, or closed gate.
            }
            // Admitted delivery completes even if revocation lands mid-call: the consumer receives
            // only its own lease-era borrowed bitmap (documented in-flight borrowed use).
            consumer.onFrame(frame);
        }
    }

    /** Minimal platform seam for the bounded wake lock (F9); refresh behavior is JVM-tested. */
    private static final class PowerManagerHandle implements WakeLockKeeper.Handle {

        private final PowerManager.WakeLock wakeLock;

        PowerManagerHandle(PowerManager.WakeLock wakeLock) {
            this.wakeLock = wakeLock;
            wakeLock.setReferenceCounted(false);
        }

        @Override
        public void acquire(long timeoutMs) {
            wakeLock.acquire(timeoutMs);
        }

        @Override
        public boolean isHeld() {
            return wakeLock.isHeld();
        }

        @Override
        public void release() {
            wakeLock.release();
        }
    }

    private static final String WAKE_LOCK_TAG = "EyeBrowse:HostingCapture";

    /** Expiry is detected within one tick; production stops within 6 s total of the last renewal. */
    private static final long WATCHDOG_INTERVAL_MS = 500;

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
    private final FrameGate frameGate = new FrameGate();

    private PrivateDisplayHost displayHost;
    private HostingService hostingService;
    private Context hostingContext; // The live service context while hosting; used for rebuilds.
    private WakeLockKeeper wakeLockKeeper;

    private State state = State.NOT_HOSTING;
    private Attachment attachment = Attachment.NONE;
    private int generation;
    private @Nullable String failureReason;

    private Lease lease;
    private HostingController.FrameConsumer frameConsumer;
    private long lastLeaseRenewElapsedMs;
    private long lastDemandElapsedMs;
    private boolean stopRequestedDuringStart;
    private int pendingStartGeneration = -1;

    // Phone UI availability, tracked through STARTING so delayed readiness reconciles (F3).
    private boolean phoneUiAvailable;
    private @Nullable android.app.Activity phoneUiActivity;
    private @Nullable ViewGroup phoneUiContainer;

    // Last measured Phone content viewport (F6): the geometry private output reconciles to.
    private WebViewMetric lastViewport = new WebViewMetric(0, 0, 0);

    private final Runnable startTimeout = this::onStartTimeout;
    private final Runnable idleRelease = this::runIdleRelease;

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
        PowerManager powerManager = (PowerManager) appContext.getSystemService(
                Context.POWER_SERVICE);
        this.wakeLockKeeper = new WakeLockKeeper(
                new PowerManagerHandle(powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)),
                android.os.SystemClock::elapsedRealtime);
    }

    // ---------------------------------------------------------------- status

    synchronized Status status() {
        boolean captureActive = displayHost != null && displayHost.isCapturing();
        boolean wakeLockHeld = wakeLockKeeper != null && wakeLockKeeper.isHeld();
        return new Status(state, generation, attachment, session.isLive(), captureActive,
                wakeLockHeld, failureReason);
    }

    /** True while any live capture resource (reader or capture thread) still exists. */
    synchronized boolean captureResourcesPresent() {
        return displayHost != null && displayHost.hasLiveCaptureResources();
    }

    synchronized boolean hasDisplayResources() {
        return displayHost != null && (displayHost.hasDisplay() || displayHost.hasPresentation());
    }

    synchronized boolean isWakeLockHeld() {
        return wakeLockKeeper != null && wakeLockKeeper.isHeld();
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
        pendingStartGeneration = generation;
        notifyHostingChanged();
        Intent intent = new Intent(appContext, HostingService.class);
        intent.setAction(HostingService.ACTION_START);
        try {
            appContext.startService(intent);
        } catch (RuntimeException error) {
            failStart("service start: " + error.getMessage());
            return true;
        }
        mainHandler.removeCallbacks(startTimeout); // Fence any earlier generation's timer (F4).
        mainHandler.postDelayed(startTimeout, START_COMPLETION_TIMEOUT_MS);
        return true;
    }

    /** Called by the service once it entered the foreground. */
    synchronized void onServiceReady(HostingService service) {
        if (state != State.STARTING || hostingService != null
                || generation != pendingStartGeneration) {
            // Stale or duplicate service arrival: stop the already-entered foreground shell
            // instead of leaving it without an owning controller (F4).
            if (service != null && service != hostingService) {
                service.stopSelf();
            }
            return;
        }
        hostingService = service;
        hostingContext = service;
        if (stopRequestedDuringStart) {
            completeStop();
            return;
        }
        WebViewMetric metric = WebViewMetric.measure(session);
        if (metric.width <= 0 || metric.height <= 0) {
            metric = lastViewport; // Fall back to the last measured Phone content viewport (F6).
        }
        String sizeError = HostingPolicy.viewportError(metric.width, metric.height);
        if (sizeError != null) {
            failStart(sizeError);
            return;
        }
        displayHost = new PrivateDisplayHost(resourceFactory);
        try {
            displayHost.create(service, metric.width, metric.height, metric.densityDpi);
        } catch (HostingException error) {
            failStart(error.getMessage());
            return;
        } catch (RuntimeException error) {
            // Recoverable platform failure at the ownership boundary rolls back and reports (F8).
            failStart("display platform failure: " + error.getMessage());
            return;
        }
        lastViewport = metric;
        reconcileAttachmentAfterReadiness();
        state = State.HOSTING;
        notifyHostingChanged();
        Log.i(TAG, "hosting active gen=" + generation + " viewport=" + metric.width + "x"
                + metric.height + "@" + metric.densityDpi + " attachment=" + attachment);
        lastDemandElapsedMs = android.os.SystemClock.elapsedRealtime();
        scheduleIdleRelease();
        mainHandler.removeCallbacks(watchdog);
        mainHandler.post(watchdog);
    }

    /**
     * Reconciles the WebView attachment when readiness arrives (F3): a hidden or destroyed Phone
     * UI hosts the view offscreen immediately; a parentless view returns to the available Phone
     * UI; otherwise the visible Phone UI keeps its interactive WebView.
     */
    private void reconcileAttachmentAfterReadiness() {
        if (!phoneUiAvailable) {
            displayHost.attachSessionView(session);
            attachment = Attachment.PRIVATE_DISPLAY;
        } else if (session.view() != null && session.view().getParent() == null
                && phoneUiActivity != null && phoneUiContainer != null) {
            attachment = Attachment.PHONE_UI;
            session.attach(phoneUiActivity, phoneUiContainer);
        } else {
            attachment = Attachment.PHONE_UI;
        }
    }

    private void onStartTimeout() {
        synchronized (this) {
            if (state == State.STARTING && generation == pendingStartGeneration) {
                failStart("hosting start did not complete in time");
            }
        }
    }

    /**
     * Records the explicit failure, rolls back already allocated resources and publishes the
     * settled final state BEFORE notifying listeners (F7).
     */
    private void failStart(String reason) {
        mainHandler.removeCallbacks(startTimeout);
        pendingStartGeneration = -1;
        revokeLease();
        releaseWakeLock();
        if (hostingService != null) {
            hostingService.stopSelf();
            hostingService = null;
        }
        state = State.NOT_HOSTING;
        attachment = Attachment.NONE;
        failureReason = reason;
        mainHandler.removeCallbacks(watchdog);
        mainHandler.removeCallbacks(idleRelease);
        Log.i(TAG, "start failed gen=" + generation + " reason=" + reason);
        notifyHostingChanged();
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
                notifyHostingChanged();
                revokeLease();
                completeStop();
                return;
        }
    }

    /** Publishes the settled final state BEFORE notifying listeners (F7). */
    private void completeStop() {
        mainHandler.removeCallbacks(watchdog);
        mainHandler.removeCallbacks(idleRelease);
        mainHandler.removeCallbacks(startTimeout);
        pendingStartGeneration = -1;
        revokeLease();
        releaseWakeLock();
        if (displayHost != null) {
            displayHost.release(session);
            // The host object stays reachable for teardown introspection; captureResourcesPresent
            // consults its live-resource/completion state instead of a nulled reference (F2).
        }
        if (hostingService != null) {
            hostingService.stopSelf();
            hostingService = null;
        }
        hostingContext = null;
        state = State.NOT_HOSTING;
        attachment = Attachment.NONE;
        Log.i(TAG, "stop complete gen=" + generation);
        notifyHostingChanged();
    }

    // ------------------------------------------------------ UI availability (F3)

    /**
     * Records returning Phone UI and reattaches the hosted view when applicable. Returns the
     * attachment token the Activity must keep for its own {@code onDestroy}.
     */
    synchronized @Nullable PhoneBrowserSession.Attachment onPhoneUiAvailable(
            android.app.Activity activity, ViewGroup container,
            @Nullable PhoneBrowserSession.Attachment currentToken) {
        phoneUiAvailable = true;
        phoneUiActivity = activity;
        phoneUiContainer = container;
        if (state == State.HOSTING && displayHost != null) {
            if (attachment == Attachment.PRIVATE_DISPLAY) {
                return moveWebViewToPhoneUi(activity, container);
            }
            if (session.view() != null && session.view().getParent() != container) {
                return session.attach(activity, container);
            }
        }
        return currentToken;
    }

    /**
     * Records hidden Phone UI. During HOSTING the live view moves offscreen (geometry
     * reconciled); during STARTING the transition is deferred and readiness reconciles (F3).
     */
    synchronized @Nullable PhoneBrowserSession.Attachment onPhoneUiHidden(
            @Nullable PhoneBrowserSession.Attachment token) {
        phoneUiAvailable = false;
        if (state == State.HOSTING && attachment == Attachment.PHONE_UI
                && session.isCurrentAttachment(token)) {
            hostOffscreenWithReconciledGeometry();
            return attachment == Attachment.PHONE_UI ? token : null; // Token consumed on success.
        }
        return token;
    }

    /**
     * Records a destroyed Phone UI. Same hosting reconciliation as hidden; during STARTING the
     * deferred readiness reconcile applies (F3).
     */
    synchronized @Nullable PhoneBrowserSession.Attachment onPhoneUiDestroyed(
            @Nullable PhoneBrowserSession.Attachment token) {
        phoneUiAvailable = false;
        phoneUiActivity = null;
        phoneUiContainer = null;
        if (state == State.HOSTING && attachment == Attachment.PHONE_UI
                && session.isCurrentAttachment(token)) {
            hostOffscreenWithReconciledGeometry();
            return attachment == Attachment.PHONE_UI ? token : null; // Token consumed on success.
        }
        return token;
    }

    // ------------------------------------------------------ attachment moves

    /**
     * Moves the live WebView from the Phone UI into the private presentation, reconciling the
     * private geometry to the last measured Phone content viewport (F6): a changed window
     * rebuilds the display/presentation/reader at the new measured size without navigation; an
     * unsupported size is reported explicitly instead of silently keeping the old geometry.
     */
    private void hostOffscreenWithReconciledGeometry() {
        WebViewMetric metric = WebViewMetric.measure(session);
        if (metric.width <= 0 || metric.height <= 0) {
            metric = lastViewport;
        }
        String sizeError = HostingPolicy.viewportError(metric.width, metric.height);
        if (sizeError != null) {
            failureReason = sizeError;
            notifyHostingChanged();
            return; // The view stays with the (hidden) Phone UI; the condition is explicit.
        }
        lastViewport = metric;
        try {
            displayHost.ensureCaptureSurface(hostingContext, metric.width, metric.height,
                    metric.densityDpi, session);
        } catch (HostingException error) {
            failureReason = error.getMessage();
            notifyHostingChanged();
            return;
        }
        displayHost.attachSessionView(session);
        attachment = Attachment.PRIVATE_DISPLAY;
    }

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
        hostOffscreenWithReconciledGeometry();
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
        WebViewMetric metric = WebViewMetric.measure(session);
        if (metric.width <= 0 || metric.height <= 0) {
            metric = lastViewport; // A hosted/parentless view falls back to the last measurement.
        }
        String sizeError = HostingPolicy.viewportError(metric.width, metric.height);
        if (sizeError != null) {
            failureReason = sizeError;
            notifyHostingChanged();
            return null; // Unsupported viewport reported explicitly (F6).
        }
        try {
            displayHost.ensureCaptureSurface(hostingContext, metric.width, metric.height,
                    metric.densityDpi, session);
        } catch (HostingException error) {
            failureReason = error.getMessage();
            notifyHostingChanged();
            return null;
        }
        lease = new Lease();
        frameGate.open(lease, generation);
        frameConsumer = consumer;
        lastLeaseRenewElapsedMs = android.os.SystemClock.elapsedRealtime();
        lastDemandElapsedMs = lastLeaseRenewElapsedMs;
        wakeLockKeeper.refresh();
        displayHost.startCapture(generation,
                new BoundSink(lease, generation, consumer)); // Bound at acquisition (F1).
        scheduleIdleRelease();
        return lease;
    }

    private void revokeLease() {
        if (lease != null) {
            lease.markRevoked();
            lease = null;
        }
        frameGate.close(); // No further admissions; an admitted frame finishes its own delivery.
        frameConsumer = null;
        if (displayHost != null) {
            displayHost.stopCapture();
        }
        releaseWakeLock();
    }

    // ------------------------------------------------------------- watchdog

    /** Detects lease expiry within one tick so production stops within 6 s of the last renewal. */
    private void watchdogTick() {
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (this) {
            if (state != State.HOSTING) {
                return;
            }
            if (lease != null) {
                if (HostingPolicy.leaseExpired(now, lastLeaseRenewElapsedMs)) {
                    // Liveness lost: revoke (stops new admissions, releases the wake lock) and
                    // anchor the idle window here.
                    Log.i(TAG, "lease expired gen=" + generation);
                    revokeLease();
                    scheduleIdleRelease();
                    notifyHostingChanged();
                } else {
                    wakeLockKeeper.refresh(); // Keep the bounded timeout forward while live (F9).
                }
            }
        }
    }

    /** Anchored idle release: fires exactly {@link HostingPolicy#IDLE_RELEASE_MS} after demand. */
    private void scheduleIdleRelease() {
        mainHandler.removeCallbacks(idleRelease);
        mainHandler.postDelayed(idleRelease, HostingPolicy.IDLE_RELEASE_MS);
    }

    private void runIdleRelease() {
        synchronized (this) {
            if (state != State.HOSTING || lease != null) {
                return; // Demand resumed or hosting stopped; nothing to release.
            }
            if (displayHost != null && displayHost.hasLiveCaptureResources()) {
                Log.i(TAG, "idle release gen=" + generation);
                displayHost.releaseCaptureResources();
                notifyHostingChanged();
            }
        }
    }

    private void releaseWakeLock() {
        if (wakeLockKeeper != null) {
            wakeLockKeeper.release();
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
                failureReason = appContext.getString(R.string.hosting_failure_browser_lost);
                completeStop();
            }
        }
    }

    /** Called by the service when the system destroys it while hosting was expected to continue. */
    synchronized void onServiceDestroyed(HostingService service) {
        if (hostingService != service) {
            return;
        }
        hostingService = null;
        hostingContext = null;
        if (state == State.STARTING || state == State.HOSTING) {
            Log.i(TAG, "service destroyed while hosting gen=" + generation);
            state = State.STOPPING;
            revokeLease();
            failureReason = appContext.getString(R.string.hosting_failure_service_lost);
            completeStop();
        }
    }

    /** Recoverable foreground-entry failure at its owner rolls the start back explicitly (F8). */
    synchronized void onServiceEntryFailed(RuntimeException error) {
        if (state == State.STARTING && generation == pendingStartGeneration) {
            failStart("foreground entry: " + error.getMessage());
        } else if (hostingService != null) {
            hostingService.stopSelf();
        }
    }

    // -------------------------------------------------------------- helpers

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
