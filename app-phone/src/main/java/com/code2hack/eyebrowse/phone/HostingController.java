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
                long now = android.os.SystemClock.elapsedRealtime();
                synchronized (HostingController.this) {
                    if (revoked || lease != this) {
                        return;
                    }
                    // R4: renewal is accepted only before the authoritative deadline; an expired
                    // lease cannot revive its delivery authority, whatever a later watchdog or
                    // cleanup tick would do.
                    if (!frameGate.renew(now, now + HostingPolicy.LEASE_TTL_MS)) {
                        Log.i(TAG, "renewal rejected: lease expired gen=" + generation);
                        revokeLease();
                        releaseIdleCaptureResourcesLocked(); // Bounded immediate release (R1).
                        notifyHostingChanged();
                        return;
                    }
                    // Successful main-thread renewal: the ack and the idle anchor are THIS event;
                    // release/expiry/lifecycle events never move the anchor (R1/R4).
                    lastLeaseRenewElapsedMs = now;
                    lastDemandElapsedMs = now;
                    idleReleaseCompletedElapsedMs = 0; // Continued demand: evidence re-scoped.
                    wakeLockKeeper.refresh(); // Refresh the bounded platform timeout while live (F9).
                    scheduleIdleReleaseLocked();
                }
            });
        }

        public void release() {
            runOnMain(() -> {
                synchronized (HostingController.this) {
                    if (revoked || lease != this) {
                        return;
                    }
                    revokeLease();
                    // Demand ended: capture resources release now (earlier than the anchored
                    // deadline is always allowed); the deadline anchor itself is NOT moved (R1).
                    releaseIdleCaptureResourcesLocked();
                    notifyHostingChanged();
                }
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
            if (!frameGate.admit(leaseToken, frame.generation,
                    android.os.SystemClock.elapsedRealtime())) {
                return; // Fenced: superseded lease, replacement token, closed gate, or expiry (R4).
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

    /** Re-check interval for retiring-owner completion (thread exit is observed, not assumed). */
    private static final long RETIREMENT_RECHECK_MS = 50;

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
    private final java.util.List<PrivateDisplayHost> retiringHosts = new java.util.ArrayList<>();
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
    private long lastDemandElapsedMs; // Last successful demand/readiness; anchors the idle deadline.
    private long idleReleaseCompletedElapsedMs; // Idle-release completion, scoped to its cycle.
    private PrivateDisplayHost idleReleasePendingOwner; // The owner whose release is outstanding.
    private boolean stopRequestedDuringStart;
    private int pendingStartGeneration = -1;

    // Phone UI availability, tracked through STARTING so delayed readiness reconciles (F3), with
    // owner identity so a stale old-Activity callback cannot flip a successor's state (R5).
    private boolean phoneUiAvailable;
    private @Nullable android.app.Activity phoneUiActivity;
    private @Nullable ViewGroup phoneUiContainer;
    private @Nullable PhoneBrowserSession.Attachment uiOwnerToken;

    // Last measured Phone content viewport (F6): the geometry private output reconciles to.
    private WebViewMetric lastViewport = new WebViewMetric(0, 0, 0);

    private final Runnable startTimeout = this::onStartTimeout;
    private final Runnable idleRelease = this::runIdleRelease;
    private final Runnable retirementRecheck = this::evaluateRetirements;

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
        if (displayHost != null && displayHost.hasLiveCaptureResources()) {
            return true;
        }
        for (PrivateDisplayHost host : retiringHosts) {
            if (host.hasLiveCaptureResources()) {
                return true; // A retiring owner's resources are retained until quiescent (R2).
            }
        }
        return false;
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
        if ((displayHost != null && !displayHost.isQuiescent()) || !retiringHostsQuiescent()) {
            // R2: one ownership transition at a time — a previous capture owner is still
            // retiring. Explicit refusal with a bounded visible failure state; the retirement
            // recheck notifies when replacement is safe, and the caller retries.
            failureReason = appContext.getString(R.string.hosting_failure_previous_shutdown);
            notifyHostingChanged();
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
        if (displayHost != null && !displayHost.isQuiescent()) {
            // A previous owner's teardown is still outstanding (R2): retain it — it closes only
            // its own snapshots — and replace it only when its thread has actually exited.
            retiringHosts.add(displayHost);
        }
        displayHost = new PrivateDisplayHost(resourceFactory, this::onRetirementSignal);
        pruneQuiescedRetiringHostsLocked();
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
        // Never-leased readiness is a real resource-readiness event: it anchors the idle deadline
        // exactly like a successful demand (R1).
        lastDemandElapsedMs = android.os.SystemClock.elapsedRealtime();
        idleReleaseCompletedElapsedMs = 0; // Fresh readiness: prior completion evidence is stale.
        scheduleIdleReleaseLocked();
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
     * Records the explicit failure, rolls back already allocated resources (including any display
     * owner and the service context reference, R7) and publishes the settled final state BEFORE
     * notifying listeners (F7).
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
        hostingContext = null;
        if (displayHost != null) {
            displayHost.release(session); // Roll back the allocated display owner (R7).
            if (!displayHost.isQuiescent()) {
                retiringHosts.add(displayHost); // Its snapshot teardown finishes on its own.
            }
            displayHost = null;
        }
        state = State.NOT_HOSTING;
        attachment = Attachment.NONE;
        failureReason = reason;
        mainHandler.removeCallbacks(watchdog);
        mainHandler.removeCallbacks(idleRelease);
        mainHandler.removeCallbacks(retirementRecheck);
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
        mainHandler.removeCallbacks(retirementRecheck); // Stop ends the hosting recheck scope.
        pendingStartGeneration = -1;
        revokeLease();
        releaseWakeLock();
        if (displayHost != null) {
            displayHost.release(session);
            // The host object stays reachable for teardown introspection; a non-quiescent owner
            // is additionally retained until its own snapshot teardown completes (R2).
            if (!displayHost.isQuiescent()) {
                retiringHosts.add(displayHost);
            }
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
     *
     * <p>R3: a surviving live parentless view is reattached to the returning Phone UI
     * independently of hosting state — including the return after a background Stop — without
     * reload and with a valid returned ownership token.
     */
    synchronized @Nullable PhoneBrowserSession.Attachment onPhoneUiAvailable(
            android.app.Activity activity, ViewGroup container,
            @Nullable PhoneBrowserSession.Attachment currentToken) {
        phoneUiAvailable = true;
        phoneUiActivity = activity;
        phoneUiContainer = container;
        if (state == State.HOSTING && displayHost != null
                && attachment == Attachment.PRIVATE_DISPLAY) {
            PhoneBrowserSession.Attachment token = moveWebViewToPhoneUi(activity, container);
            if (token != null) {
                uiOwnerToken = token;
                return token;
            }
            return currentToken;
        }
        if (session.isLive() && session.view() != null
                && session.view().getParent() == null) {
            // Parentless live view (background Stop return, or a STARTING race residue):
            // reattach without reload; hosting state itself is unchanged.
            PhoneBrowserSession.Attachment token = session.attach(activity, container);
            uiOwnerToken = token;
            return token;
        }
        uiOwnerToken = currentToken;
        return currentToken;
    }

    /**
     * Records hidden Phone UI. During HOSTING the live view moves offscreen (geometry
     * reconciled); during STARTING the transition is deferred and readiness reconciles (F3).
     *
     * <p>R5: identity-checked — a stale old-Activity callback (its token is not the registered UI
     * owner's, even if that token was consumed by a private attachment) never flips availability
     * for a visible successor.
     */
    synchronized @Nullable PhoneBrowserSession.Attachment onPhoneUiHidden(
            @Nullable PhoneBrowserSession.Attachment token) {
        if (token == null || token != uiOwnerToken) {
            return token; // Not the registered UI owner; a successor (or nobody) owns the slot.
        }
        phoneUiAvailable = false;
        if (state == State.HOSTING && attachment == Attachment.PHONE_UI
                && session.isCurrentAttachment(token)) {
            hostOffscreenWithReconciledGeometry();
        }
        return token; // The owner keeps its token for its destroy path.
    }

    /**
     * Records a destroyed Phone UI: releases the controller-held Activity/container references
     * for the matching owner and clears availability (R5). The hosting offscreen move for a
     * destroyed owner happens through the caller's explicit
     * {@link #moveWebViewToPrivateDisplay(PhoneBrowserSession.Attachment)}.
     */
    synchronized @Nullable PhoneBrowserSession.Attachment onPhoneUiDestroyed(
            @Nullable PhoneBrowserSession.Attachment token) {
        if (token != null && token == uiOwnerToken) {
            phoneUiAvailable = false;
            phoneUiActivity = null;
            phoneUiContainer = null;
            uiOwnerToken = null;
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
        boolean demandLive = lease != null && frameConsumer != null;
        if (demandLive) {
            // Only live demand justifies capture-surface allocation/reconciliation here (R1):
            // rebuild for width/height/density and REARM the same active owner on the new reader.
            try {
                displayHost.ensureCaptureSurface(hostingContext, metric.width, metric.height,
                        metric.densityDpi, session);
            } catch (HostingException error) {
                failureReason = error.getMessage();
                displayHost.stopCapture(); // No surviving reader: report not-capturing honestly.
                notifyHostingChanged();
                return;
            }
            if (!displayHost.rearmCapture(generation,
                    new BoundSink(lease, generation, frameConsumer))) {
                // A checked rearm failure must not leave a healthy capturing label (R6).
                failureReason = "capture rearm failed after geometry rebuild";
                displayHost.stopCapture();
                notifyHostingChanged();
                return;
            }
        }
        // Without live demand: the private move attaches the surviving browser WITHOUT capture
        // allocation or deadline changes — post-idle moves recreate nothing (R1 corrected).
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
        if (displayHost.isRetiring()) {
            // R2: the current capture owner is still retiring. Null means NO lease and NO hidden
            // side effect; the caller retries explicitly once retirement is quiescent (the
            // retirement recheck notifies listeners at that point).
            return null;
        }
        // Viewport authority: while the view is attached to the private presentation, its laid-out
        // dimensions are the OLD private geometry (a layout pass can overwrite them before the
        // first demand) — the last measured PHONE viewport/density is authoritative for this
        // transition. On the Phone UI the live measurement is the current geometry.
        WebViewMetric metric;
        if (attachment == Attachment.PRIVATE_DISPLAY) {
            metric = lastViewport;
        } else {
            metric = WebViewMetric.measure(session);
            if (metric.width <= 0 || metric.height <= 0) {
                metric = lastViewport; // A hosted/parentless view falls back to the last measurement.
            }
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
        long now = android.os.SystemClock.elapsedRealtime();
        Lease newLease = new Lease();
        frameGate.open(newLease, generation, now + HostingPolicy.LEASE_TTL_MS);
        // Same-owner rearm vs new-owner start: revocation retains the capture owner (thread and
        // reader stay for reacquisition), so a new lease after expiry/release rearms the SAME
        // active owner with the new bound sink; only a quiescent host starts a fresh owner (R2).
        boolean started = displayHost.isOwnerActive()
                ? displayHost.rearmCapture(generation,
                        new BoundSink(newLease, generation, consumer))
                : displayHost.startCapture(generation,
                        new BoundSink(newLease, generation, consumer));
        if (!started) {
            frameGate.close(); // Nothing retained; the caller retries after quiescence (R2).
            return null;
        }
        lease = newLease;
        frameConsumer = consumer;
        lastLeaseRenewElapsedMs = now;
        lastDemandElapsedMs = now; // Successful demand anchors the idle deadline (R1).
        idleReleaseCompletedElapsedMs = 0; // New demand cycle: prior completion evidence is stale.
        wakeLockKeeper.refresh();
        scheduleIdleReleaseLocked();
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
                    // Liveness lost: revoke and release capture resources immediately. The gate
                    // already rejected delivery/renewal at the 5 s deadline (R4); the watchdog is
                    // detection, not authority. The idle anchor is NOT moved here (R1).
                    Log.i(TAG, "lease expired gen=" + generation);
                    revokeLease();
                    releaseIdleCaptureResourcesLocked();
                    notifyHostingChanged();
                } else {
                    wakeLockKeeper.refresh(); // Keep the bounded timeout forward while live (F9).
                }
            }
        }
    }

    /**
     * Schedules the idle-release callback against the anchored deadline: teardown INITIATES at
     * deadline minus the completion lead so it completes by exactly
     * {@link HostingPolicy#IDLE_RELEASE_MS} after the last successful demand/readiness — never
     * re-anchored to the scheduling moment (R1).
     */
    private void scheduleIdleReleaseLocked() {
        mainHandler.removeCallbacks(idleRelease);
        mainHandler.postDelayed(idleRelease,
                HostingPolicy.idleReleaseInitiationDelayMs(android.os.SystemClock.elapsedRealtime(),
                        lastDemandElapsedMs));
    }

    /**
     * Immediate bounded release of idle capture resources (voluntary release/expiry paths). The
     * completion evidence is bound to THIS owner/cycle: any prior completion is invalidated here,
     * and only the bound owner's completion is recorded (an older retiring owner never overwrites
     * it).
     */
    private void releaseIdleCaptureResourcesLocked() {
        if (displayHost != null && displayHost.hasLiveCaptureResources()) {
            Log.i(TAG, "idle release gen=" + generation);
            idleReleasePendingOwner = displayHost;
            idleReleaseCompletedElapsedMs = 0; // Invalidate any earlier cycle's completion.
            displayHost.releaseCaptureResources();
            if (displayHost.isQuiescent()) {
                // Inline completion (no capture thread) of the bound owner: record it.
                idleReleaseCompletedElapsedMs = android.os.SystemClock.elapsedRealtime();
                idleReleasePendingOwner = null;
            }
        }
        notifyHostingChanged();
    }

    /**
     * Authoritative idle-release completion observation (diagnostic/test seam), scoped to the
     * current release cycle: 0 until the bound owner completes, invalidated by new demand or
     * readiness. Consumers must require completion >= their cycle's anchor.
     */
    synchronized long lastIdleReleaseCompletedElapsedMs() {
        return idleReleaseCompletedElapsedMs;
    }

    /** The anchored deadline callback: initiates teardown early enough to complete by it. */
    private void runIdleRelease() {
        synchronized (this) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (state != State.HOSTING || lease != null) {
                return; // Demand resumed or hosting stopped; nothing to release.
            }
            if (!HostingPolicy.idleReleaseDue(now, lastDemandElapsedMs)) {
                scheduleIdleReleaseLocked(); // Anchor moved since posting; re-post, never drift (R1).
                return;
            }
            releaseIdleCaptureResourcesLocked(); // Initiates here; completes by the deadline (R1).
        }
    }

    /**
     * Teardown-task completion signal from a retiring capture owner (may fire off-main): hop to
     * main and evaluate actual completion. Replacement is never auto-started here — callers
     * retry explicitly once quiescence is observed.
     */
    private void onRetirementSignal() {
        mainHandler.post(this::evaluateRetirements);
    }

    /** Main-thread retirement evaluation: quiescence requires the thread to have exited (R2). */
    private void evaluateRetirements() {
        synchronized (this) {
            boolean changed = false;
            if (displayHost != null && displayHost.evaluateRetirementCompletion()) {
                changed = true;
                if (idleReleasePendingOwner == displayHost) {
                    // Record completion only for the owner this release cycle is waiting on.
                    idleReleaseCompletedElapsedMs = android.os.SystemClock.elapsedRealtime();
                    idleReleasePendingOwner = null;
                }
            }
            for (PrivateDisplayHost host : retiringHosts) {
                if (host.evaluateRetirementCompletion()) {
                    changed = true;
                    if (idleReleasePendingOwner == host) {
                        idleReleaseCompletedElapsedMs = android.os.SystemClock.elapsedRealtime();
                        idleReleasePendingOwner = null;
                    }
                }
            }
            int before = retiringHosts.size();
            pruneQuiescedRetiringHostsLocked();
            if (changed || retiringHosts.size() != before) {
                notifyHostingChanged(); // Start/acquisition may be retried explicitly now.
            }
            // Follow-up polling only for actual RETIRING work: a healthy ACTIVE owner is never
            // rechecked, so normal capture schedules nothing (R2/manager follow-up 2).
            boolean retiringWork = displayHost != null && displayHost.isRetiring();
            if (!retiringWork) {
                for (PrivateDisplayHost host : retiringHosts) {
                    if (host.isRetiring()) {
                        retiringWork = true;
                        break;
                    }
                }
            }
            if (retiringWork) {
                mainHandler.postDelayed(retirementRecheck, RETIREMENT_RECHECK_MS);
            }
        }
    }

    private boolean retiringHostsQuiescent() {
        for (PrivateDisplayHost host : retiringHosts) {
            if (!host.isQuiescent()) {
                return false;
            }
        }
        return true;
    }

    private void pruneQuiescedRetiringHostsLocked() {
        retiringHosts.removeIf(PrivateDisplayHost::isQuiescent);
    }

    /** Authoritative idle anchor (diagnostic/test seam): the last successful demand/readiness. */
    synchronized long lastDemandAnchorElapsedMs() {
        return lastDemandElapsedMs;
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
