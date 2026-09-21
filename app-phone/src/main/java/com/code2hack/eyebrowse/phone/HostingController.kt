package com.code2hack.eyebrowse.phone

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.ViewGroup

import androidx.annotation.Nullable

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
 *
 * <p>Migration note: visibility widened package-private -> public for the separately compiled
 * androidTest consumer (get/State/Attachment/Status/Lease and instance methods). `Status` keeps
 * public JVM FIELDS via `@JvmField` (the Java instrumentation reads `status.state`,
 * `status.generation`, `status.failureReason` directly). `Lease` stays a true `inner` class bound
 * to the outer controller with the original monitor semantics (`@Synchronized markRevoked` on the
 * lease instance; `synchronized(this@HostingController)` blocks). Instance-method synchronization
 * uses `@Synchronized` (same monitor as the Java synchronized instance methods); the static `get`
 * keeps the class monitor via an explicit `synchronized(HostingController::class.java)` block.
 * Identity comparisons use `===`/`!==` where the Java original relied on reference equality.
 */
class HostingController private constructor(private val appContext: Context) {

    enum class State {
        NOT_HOSTING,
        STARTING,
        HOSTING,
        STOPPING
    }

    enum class Attachment {
        NONE,
        PHONE_UI,
        PRIVATE_DISPLAY
    }

    /** Distinguishes browser lifetime, hosting generation, attachment and capture activity. */
    class Status(
        @JvmField val state: State,
        @JvmField val generation: Int,
        @JvmField val attachment: Attachment,
        @JvmField val browserLive: Boolean,
        @JvmField val captureActive: Boolean,
        @JvmField val wakeLockHeld: Boolean,
        @JvmField @Nullable val failureReason: String?,
    )

    /** The single live in-process frame consumer lease; renewal keeps liveness. */
    inner class Lease {

        private var revoked = false

        /** Must be called within {@link HostingPolicy#LEASE_TTL_MS} of the last renewal. */
        fun renew() {
            runOnMain {
                val now = android.os.SystemClock.elapsedRealtime()
                synchronized(this@HostingController) {
                    if (revoked || lease !== this@Lease) {
                        return@runOnMain
                    }
                    // R4: renewal is accepted only before the authoritative deadline; an expired
                    // lease cannot revive its delivery authority, whatever a later watchdog or
                    // cleanup tick would do.
                    if (!frameGate.renew(now, now + HostingPolicy.LEASE_TTL_MS)) {
                        Log.i(TAG, "renewal rejected: lease expired gen=$generation")
                        revokeLease()
                        releaseIdleCaptureResourcesLocked() // Bounded immediate release (R1).
                        notifyHostingChanged()
                        return@runOnMain
                    }
                    // Successful main-thread renewal: the ack and the idle anchor are THIS event;
                    // release/expiry/lifecycle events never move the anchor (R1/R4).
                    lastLeaseRenewElapsedMs = now
                    lastDemandElapsedMs = now
                    idleReleaseCompletedElapsedMs = 0 // Continued demand: evidence re-scoped.
                    wakeLockKeeper!!.refresh() // Refresh the bounded platform timeout while live (F9).
                    scheduleIdleReleaseLocked()
                }
            }
        }

        fun release() {
            runOnMain {
                synchronized(this@HostingController) {
                    if (revoked || lease !== this@Lease) {
                        return@runOnMain
                    }
                    revokeLease()
                    // Demand ended: capture resources release now (earlier than the anchored
                    // deadline is always allowed); the deadline anchor itself is NOT moved (R1).
                    releaseIdleCaptureResourcesLocked()
                    notifyHostingChanged()
                }
            }
        }

        @Synchronized
        fun markRevoked() {
            revoked = true
        }
    }

    /** Receives frames on the capture thread while a lease is live. */
    fun interface FrameConsumer {
        fun onFrame(frame: HostingFrame)
    }

    /** Notified on the main thread after any hosting state transition reached its final state. */
    fun interface Listener {
        fun onHostingChanged()
    }

    private val listeners = java.util.ArrayList<Listener>()

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyHostingChanged() {
        for (listener in java.util.ArrayList(listeners)) {
            listener.onHostingChanged()
        }
    }

    private val session: PhoneBrowserSession = PhoneBrowserSession.get(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameGate = FrameGate()

    private var displayHost: PrivateDisplayHost? = null
    private val retiringHosts = java.util.ArrayList<PrivateDisplayHost>()
    private var hostingService: HostingService? = null
    private var hostingContext: Context? = null // The live service context while hosting; used for rebuilds.
    private var wakeLockKeeper: WakeLockKeeper? = null

    private var state: State = State.NOT_HOSTING
    private var attachment: Attachment = Attachment.NONE
    private var generation: Int = 0
    private var failureReason: String? = null

    private var lease: Lease? = null
    private var frameConsumer: FrameConsumer? = null
    private var lastLeaseRenewElapsedMs: Long = 0
    private var lastDemandElapsedMs: Long = 0 // Last successful demand/readiness; anchors the idle deadline.
    private var idleReleaseCompletedElapsedMs: Long = 0 // Idle-release completion, scoped to its cycle.
    private var idleReleasePendingOwner: PrivateDisplayHost? = null // The owner whose release is outstanding.
    private var stopRequestedDuringStart: Boolean = false
    private var pendingStartGeneration: Int = -1

    // Phone UI availability, tracked through STARTING so delayed readiness reconciles (F3), with
    // owner identity so a stale old-Activity callback cannot flip a successor's state (R5).
    private var phoneUiAvailable: Boolean = false
    private var phoneUiActivity: android.app.Activity? = null
    private var phoneUiContainer: ViewGroup? = null
    private var uiOwnerToken: PhoneBrowserSession.Attachment? = null

    // Phone-only observation, never the private display/reader profile.
    // Phone geometry is freshly framework-measured; no pixel-tolerance workaround.
    private var lastViewport = WebViewMetric(0, 0, 0)
    private val presentationEpochs = PresentationEpochs()

    private val startTimeout = Runnable { onStartTimeout() }
    private val idleRelease = Runnable { runIdleRelease() }
    private val retirementRecheck = Runnable { evaluateRetirements() }

    private val watchdog = object : Runnable {
        override fun run() {
            watchdogTick()
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private var resourceFactory: PrivateDisplayHost.Factory = PrivateDisplayHost.PlatformFactory()

    init {
        session.addListener(PhoneBrowserSession.Listener { onSessionChanged(it) })
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLockKeeper = WakeLockKeeper(
            PowerManagerHandle(powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)),
            android.os.SystemClock::elapsedRealtime,
        )
    }

    /**
     * Immutable per-lease delivery sink bound at acquisition (F1): the capture pipeline invokes
     * this object, and admission through the lock-free {@link FrameGate} fences superseded leases,
     * replacement consumers and closed gates without taking any monitor.
     */
    private inner class BoundSink(
        private val leaseToken: Lease,
        private val hostingGeneration: Int,
        private val consumer: FrameConsumer,
    ) : PrivateDisplayHost.FrameSink {

        override fun onFrame(frame: HostingFrame) {
            if (!frameGate.admit(leaseToken, frame.generation,
                    android.os.SystemClock.elapsedRealtime())) {
                return // Fenced: superseded lease, replacement token, closed gate, or expiry (R4).
            }
            // Admitted delivery completes even if revocation lands mid-call: the consumer receives
            // only its own lease-era borrowed bitmap (documented in-flight borrowed use).
            consumer.onFrame(frame)
        }
    }

    /** Minimal platform seam for the bounded wake lock (F9); refresh behavior is JVM-tested. */
    private class PowerManagerHandle(wakeLock: PowerManager.WakeLock) : WakeLockKeeper.Handle {

        private val wakeLock: PowerManager.WakeLock = wakeLock.also { it.setReferenceCounted(false) }

        override fun acquire(timeoutMs: Long) {
            wakeLock.acquire(timeoutMs)
        }

        override fun isHeld(): Boolean {
            return wakeLock.isHeld
        }

        override fun release() {
            wakeLock.release()
        }
    }

    // ---------------------------------------------------------------- status

    @Synchronized
    fun status(): Status {
        val host = displayHost
        val keeper = wakeLockKeeper
        val captureActive = host != null && host.isCapturing()
        val wakeLockHeld = keeper != null && keeper.isHeld()
        return Status(state, generation, attachment, session.isLive(), captureActive,
                wakeLockHeld, failureReason)
    }

    /** True while any live capture resource (reader or capture thread) still exists. */
    @Synchronized
    fun captureResourcesPresent(): Boolean {
        val host = displayHost
        if (host != null && host.hasLiveCaptureResources()) {
            return true
        }
        for (host in retiringHosts) {
            if (host.hasLiveCaptureResources()) {
                return true // A retiring owner's resources are retained until quiescent (R2).
            }
        }
        return false
    }

    /** Sparse no-content frame-pipeline diagnostics for acceptance failure messages. */
    @Synchronized
    fun captureDiagnostics(): String {
        val host = displayHost
        val viewport = " phoneViewport=" + lastViewport.width + "x" + lastViewport.height +
                "@" + lastViewport.densityDpi
        return (if (host == null) "host=none" else host.captureDiagnostics()) + viewport
    }

    @Synchronized
    fun hasDisplayResources(): Boolean {
        val host = displayHost
        return host != null && (host.hasDisplay() || host.hasPresentation())
    }

    @Synchronized
    fun isWakeLockHeld(): Boolean {
        val keeper = wakeLockKeeper
        return keeper != null && keeper.isHeld()
    }

    @Synchronized
    fun currentGeneration(): Int {
        return generation
    }

    // ---------------------------------------------------------------- start

    /**
     * Requests hosting of the current live browser. Returns {@code false} with a recorded failure
     * reason when the request is invalid; asynchronous platform failures roll back and surface in
     * {@link Status#failureReason}. Completion (either way) happens within the plan's 5-second
     * bound and is observable through {@link #status()}.
     */
    @Synchronized
    fun start(): Boolean {
        if (state == State.STARTING || state == State.HOSTING || state == State.STOPPING) {
            return false
        }
        if (!session.isLive()) {
            failureReason = appContext.getString(R.string.hosting_failure_no_page)
            return false
        }
        val currentHost = displayHost
        if ((currentHost != null && !currentHost.isQuiescent()) || !retiringHostsQuiescent()) {
            // R2: one ownership transition at a time — a previous capture owner is still
            // retiring. Explicit refusal with a bounded visible failure state; the retirement
            // recheck notifies when replacement is safe, and the caller retries.
            failureReason = appContext.getString(R.string.hosting_failure_previous_shutdown)
            notifyHostingChanged()
            return false
        }
        failureReason = null
        stopRequestedDuringStart = false
        state = State.STARTING
        generation++
        presentationEpochs.begin(generation, HostingPresentationProfile.RG_DESIGN_FALLBACK.copy())
        pendingStartGeneration = generation
        notifyHostingChanged()
        val intent = Intent(appContext, HostingService::class.java)
        intent.action = HostingService.ACTION_START
        try {
            appContext.startService(intent)
        } catch (error: RuntimeException) {
            failStart("service start: " + error.message)
            return true
        }
        mainHandler.removeCallbacks(startTimeout) // Fence any earlier generation's timer (F4).
        mainHandler.postDelayed(startTimeout, START_COMPLETION_TIMEOUT_MS)
        return true
    }

    /** Called by the service once it entered the foreground. */
    @Synchronized
    fun onServiceReady(service: HostingService?) {
        if (state != State.STARTING || hostingService != null ||
                generation != pendingStartGeneration) {
            // Stale or duplicate service arrival: stop the already-entered foreground shell
            // instead of leaving it without an owning controller (F4).
            if (service != null && service !== hostingService) {
                service.stopSelf()
            }
            return
        }
        hostingService = service
        hostingContext = service
        if (stopRequestedDuringStart) {
            completeStop()
            return
        }
        val epoch = checkNotNull(presentationEpochs.current)
        val metric = epoch.profile
        val previousHost = displayHost
        if (previousHost != null && !previousHost.isQuiescent()) {
            // A previous owner's teardown is still outstanding (R2): retain it — it closes only
            // its own snapshots — and replace it only when its thread has actually exited.
            retiringHosts.add(previousHost)
        }
        val host = PrivateDisplayHost(resourceFactory, Runnable { onRetirementSignal(epoch) })
        displayHost = host
        host.setUnavailableListener(Runnable { onPresentationUnavailable(epoch, host) })
        pruneQuiescedRetiringHostsLocked()
        try {
            host.create(service!!, metric.width, metric.height, metric.densityDpi)
        } catch (error: HostingException) {
            failStart(error.message)
            return
        } catch (error: RuntimeException) {
            // Recoverable platform failure at the ownership boundary rolls back and reports (F8).
            failStart("display platform failure: " + error.message)
            return
        }
        reconcileAttachmentAfterReadiness()
        state = State.HOSTING
        notifyHostingChanged()
        Log.i(TAG, "hosting active gen=$generation viewport=${metric.width}x" +
                "${metric.height}@${metric.densityDpi} attachment=$attachment")
        // Never-leased readiness is a real resource-readiness event: it anchors the idle deadline
        // exactly like a successful demand (R1).
        lastDemandElapsedMs = android.os.SystemClock.elapsedRealtime()
        idleReleaseCompletedElapsedMs = 0 // Fresh readiness: prior completion evidence is stale.
        scheduleIdleReleaseLocked()
        mainHandler.removeCallbacks(watchdog)
        mainHandler.post(watchdog)
    }

    /**
     * Reconciles the WebView attachment when readiness arrives (F3): a hidden or destroyed Phone
     * UI hosts the view offscreen immediately; a parentless view returns to the available Phone
     * UI; otherwise the visible Phone UI keeps its interactive WebView.
     */
    private fun reconcileAttachmentAfterReadiness() {
        if (!phoneUiAvailable) {
            displayHost!!.attachSessionView(session)
            attachment = Attachment.PRIVATE_DISPLAY
        } else {
            // The settled hosting notification asks the started Activity to ensure attachment
            // through onPhoneUiAvailable. Never mint a UI token behind that owner's back.
            attachment = Attachment.PHONE_UI
        }
    }

    private fun onStartTimeout() {
        synchronized(this) {
            if (state == State.STARTING && generation == pendingStartGeneration) {
                failStart("hosting start did not complete in time")
            }
        }
    }

    /**
     * Records the explicit failure, rolls back already allocated resources (including any display
     * owner and the service context reference, R7) and publishes the settled final state BEFORE
     * notifying listeners (F7).
     */
    private fun failStart(reason: String?) {
        presentationEpochs.retire()
        mainHandler.removeCallbacks(startTimeout)
        pendingStartGeneration = -1
        revokeLease()
        releaseWakeLock()
        val stoppingService = hostingService
        if (stoppingService != null) {
            stoppingService.stopSelf()
            hostingService = null
        }
        hostingContext = null
        val failedHost = displayHost
        if (failedHost != null) {
            failedHost.release(session) // Roll back the allocated display owner (R7).
            if (!failedHost.isQuiescent()) {
                retiringHosts.add(failedHost) // Its snapshot teardown finishes on its own.
            }
            displayHost = null
        }
        state = State.NOT_HOSTING
        attachment = Attachment.NONE
        failureReason = reason
        mainHandler.removeCallbacks(watchdog)
        mainHandler.removeCallbacks(idleRelease)
        mainHandler.removeCallbacks(retirementRecheck)
        Log.i(TAG, "start failed gen=$generation reason=$reason")
        notifyHostingChanged()
    }

    // ---------------------------------------------------------------- stop

    /** Explicit Stop; idempotent, bounded, and safe during startup or when not hosting. */
    @Synchronized
    fun stop() {
        when (state) {
            State.NOT_HOSTING -> return
            State.STARTING -> {
                stopRequestedDuringStart = true
                revokeLease()
                return
            }
            State.STOPPING -> return
            State.HOSTING -> {
                state = State.STOPPING
                notifyHostingChanged()
                revokeLease()
                completeStop()
                return
            }
        }
    }

    /** Publishes the settled final state BEFORE notifying listeners (F7). */
    private fun completeStop() {
        presentationEpochs.retire()
        mainHandler.removeCallbacks(watchdog)
        mainHandler.removeCallbacks(idleRelease)
        mainHandler.removeCallbacks(startTimeout)
        mainHandler.removeCallbacks(retirementRecheck) // Stop ends the hosting recheck scope.
        pendingStartGeneration = -1
        revokeLease()
        releaseWakeLock()
        val stoppedHost = displayHost
        if (stoppedHost != null) {
            stoppedHost.release(session)
            // The host object stays reachable for teardown introspection; a non-quiescent owner
            // is additionally retained until its own snapshot teardown completes (R2).
            if (!stoppedHost.isQuiescent()) {
                retiringHosts.add(stoppedHost)
            }
        }
        val stoppingService = hostingService
        if (stoppingService != null) {
            stoppingService.stopSelf()
            hostingService = null
        }
        hostingContext = null
        state = State.NOT_HOSTING
        attachment = Attachment.NONE
        Log.i(TAG, "stop complete gen=$generation")
        notifyHostingChanged()
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
    @Synchronized
    fun onPhoneUiAvailable(activity: android.app.Activity, container: ViewGroup?,
            currentToken: PhoneBrowserSession.Attachment?): PhoneBrowserSession.Attachment? {
        phoneUiAvailable = true
        phoneUiActivity = activity
        phoneUiContainer = container
        return ensurePhoneUiAttachment(activity, container, currentToken)
    }

    /** Reattachment does not itself declare a background/stale Activity to be visible. */
    @Synchronized
    fun ensurePhoneUiAttachment(activity: android.app.Activity, container: ViewGroup?,
            currentToken: PhoneBrowserSession.Attachment?): PhoneBrowserSession.Attachment? {
        if (!phoneUiAvailable || phoneUiActivity !== activity || phoneUiContainer !== container) {
            return currentToken
        }
        if (state == State.HOSTING && displayHost != null) {
            val view = session.view()
            val alreadyOwnedHere =
                session.isCurrentAttachment(currentToken) && view != null && view.parent === container
            if (alreadyOwnedHere) {
                attachment = Attachment.PHONE_UI
                uiOwnerToken = currentToken
                return currentToken
            }

            // A recreated/relaunched Activity may arrive while attachment metadata still says
            // PHONE_UI even though the old Activity (or private presentation) is the actual parent.
            // Claim through this identity-fenced controller path rather than from onCreate.
            val token = moveWebViewToPhoneUi(activity, container)
            if (token != null) {
                uiOwnerToken = token
                return token
            }
            return currentToken
        }
        val parentlessView = session.view()
        if (session.isLive() && parentlessView != null && parentlessView.parent == null) {
            // Parentless live view (background Stop return, or a STARTING race residue):
            // reattach without reload; hosting state itself is unchanged.
            val token = session.attach(activity, container)
            uiOwnerToken = token
            return token
        }
        uiOwnerToken = currentToken
        return currentToken
    }

    /** Fresh local measurement for Phone takeover; never substitute a private display profile. */
    @Synchronized
    fun measurePhoneControlProfile(): com.code2hack.eyebrowse.core.link.presentation.PresentationProfile? {
        val container = phoneUiContainer ?: return null
        if (!phoneUiAvailable || !container.isAttachedToWindow) return null
        val size = PhoneContentViewport.size(container.width, container.height,
            container.paddingLeft, container.paddingTop, container.paddingRight, container.paddingBottom)
        return com.code2hack.eyebrowse.core.link.presentation.PresentationProfile.fromMeasured(
            size.first,size.second,container.resources.displayMetrics.densityDpi)
    }

    /**
     * Records current Phone content bounds only. Owner identity rejects predecessor callbacks.
     * Private geometry always comes from the immutable generation profile, never these metrics.
     */
    @Synchronized
    fun onPhoneViewportChanged(
        activity: android.app.Activity,
        container: ViewGroup?,
        measuredWidth: Int,
        measuredHeight: Int,
        densityDpi: Int,
    ) {
        if (!phoneUiAvailable || phoneUiActivity !== activity || phoneUiContainer !== container ||
                container == null) return
        val size = PhoneContentViewport.size(measuredWidth, measuredHeight,
            container.paddingLeft, container.paddingTop, container.paddingRight, container.paddingBottom)
        if (size.first > 0 && size.second > 0) {
            lastViewport = WebViewMetric(size.first, size.second, densityDpi)
        }
        // Phone callbacks never resize PRIVATE display/reader/WebView or rewrite its epoch profile.
    }

    /**
     * Records hidden Phone UI. During HOSTING the live view moves offscreen (geometry
     * reconciled); during STARTING the transition is deferred and readiness reconciles (F3).
     *
     * <p>R5: identity-checked — a stale old-Activity callback (its token is not the registered UI
     * owner's, even if that token was consumed by a private attachment) never flips availability
     * for a visible successor.
     */
    @Synchronized
    fun onPhoneUiHidden(token: PhoneBrowserSession.Attachment?): PhoneBrowserSession.Attachment? {
        if (token == null || token !== uiOwnerToken) {
            return token // Not the registered UI owner; a successor (or nobody) owns the slot.
        }
        phoneUiAvailable = false
        if (state == State.HOSTING && attachment == Attachment.PHONE_UI &&
                session.isCurrentAttachment(token)) {
            hostOffscreenWithReconciledGeometry()
        }
        return token // The owner keeps its token for its destroy path.
    }

    /**
     * Records a destroyed Phone UI: releases the controller-held Activity/container references
     * for the matching owner and clears availability (R5). The hosting offscreen move for a
     * destroyed owner happens through the caller's explicit
     * {@link #moveWebViewToPrivateDisplay}.
     */
    @Synchronized
    fun onPhoneUiDestroyed(token: PhoneBrowserSession.Attachment?): PhoneBrowserSession.Attachment? {
        if (token != null && token === uiOwnerToken) {
            phoneUiAvailable = false
            phoneUiActivity = null
            phoneUiContainer = null
            uiOwnerToken = null
        }
        return token
    }

    // ------------------------------------------------------ attachment moves

    /** Moves the live view onto the generation's immutable private profile, without navigation. */
    private fun hostOffscreenWithReconciledGeometry() {
        val epoch = presentationEpochs.current ?: return
        val host = displayHost ?: return
        val profile = epoch.profile
        val demand = lease
        val consumer = frameConsumer
        if (demand != null && consumer != null) {
            try {
                host.ensureCaptureSurface(hostingContext!!, profile.width, profile.height,
                    profile.densityDpi, session)
                if (!host.rearmCapture(generation, BoundSink(demand, generation, consumer),
                        freshFrameRequest(epoch, host))) {
                    throw HostingException("capture rearm failed on private handoff")
                }
            } catch (error: HostingException) {
                failureReason = error.message
                host.stopCapture()
                notifyHostingChanged()
                return
            }
        }
        // Demand-free handoff never recreates a reader or changes the idle deadline. The
        // surviving Presentation has the SAME profile as the next capture acquisition.
        host.attachSessionView(session)
        attachment = Attachment.PRIVATE_DISPLAY
        notifyHostingChanged()
    }

    /**
     * Moves the live WebView from the Phone UI into the private presentation. The caller's
     * attachment token must still own the session: an old Activity being destroyed must never
     * steal the view from a newly attached Activity (system transition ordering can interleave
     * their {@code onStop}/{@code onStart} either way).
     */
    @Synchronized
    fun moveWebViewToPrivateDisplay(token: PhoneBrowserSession.Attachment?) {
        Log.i(TAG, "moveToPrivateDisplay state=$state attachment=$attachment")
        val moveHost = displayHost
        if (state != State.HOSTING || moveHost == null ||
                attachment != Attachment.PHONE_UI) {
            return
        }
        if (!session.isCurrentAttachment(token)) {
            Log.i(TAG, "moveToPrivateDisplay skipped: stale token" + identity(token))
            return
        }
        hostOffscreenWithReconciledGeometry()
    }

    private fun identity(token: PhoneBrowserSession.Attachment?): String {
        return " token=" + System.identityHashCode(token)
    }

    /**
     * Reattaches the live WebView to returning Phone UI without reload, returning the fresh
     * attachment token the owning Activity must keep for its own {@code onDestroy}.
     */
    @Synchronized
    fun moveWebViewToPhoneUi(activity: android.app.Activity,
            container: ViewGroup?): PhoneBrowserSession.Attachment? {
        Log.i(TAG, "moveToPhoneUi state=$state attachment=$attachment")
        if (state != State.HOSTING || !phoneUiAvailable || phoneUiActivity !== activity ||
                phoneUiContainer !== container) {
            return null // A stale Activity cannot reclaim a successor's presentation.
        }
        // PHONE_UI metadata is not sufficient ownership after Activity recreation. session.attach
        // performs the ordered old-parent detach and mints the fresh identity token.
        val token = session.attach(activity, container)
        attachment = Attachment.PHONE_UI
        Log.i(TAG, "moveToPhoneUi done token=" + System.identityHashCode(token))
        return token
    }

    // ---------------------------------------------------------------- lease

    /** Acquires the single frame lease; {@code null} when hosting is inactive or already leased. */
    @Synchronized
    fun acquireLease(consumer: FrameConsumer): Lease? {
        if (state != State.HOSTING || lease != null || displayHost == null) {
            return null
        }
        val host = displayHost ?: return null
        if (host.isRetiring()) {
            // The teardown task/thread may already be complete while the main-handler retirement
            // signal is merely queued. Reconcile that finished owner synchronously so an explicit
            // retry is not ordering-dependent; a genuinely live retiring owner still blocks.
            if (!host.evaluateRetirementCompletion()) {
                return null
            }
            if (idleReleasePendingOwner === host && host.isQuiescent()) {
                idleReleaseCompletedElapsedMs = android.os.SystemClock.elapsedRealtime()
                idleReleasePendingOwner = null
            }
        }
        val epoch = presentationEpochs.current ?: return null
        val metric = epoch.profile
        try {
            host.ensureCaptureSurface(hostingContext!!, metric.width, metric.height,
                    metric.densityDpi, session)
        } catch (error: HostingException) {
            failureReason = error.message
            notifyHostingChanged()
            return null
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val newLease = Lease()
        frameGate.open(newLease, generation, now + HostingPolicy.LEASE_TTL_MS)
        // Same-owner rearm vs new-owner start: revocation retains the capture owner (thread and
        // reader stay for reacquisition), so a new lease after expiry/release rearms the SAME
        // active owner with the new bound sink; only a quiescent host starts a fresh owner (R2).
        val freshFrameRequest = freshFrameRequest(epoch, host)
        val started = if (host.isOwnerActive()) {
            host.rearmCapture(
                generation,
                BoundSink(newLease, generation, consumer),
                freshFrameRequest,
            )
        } else {
            host.startCapture(
                generation,
                BoundSink(newLease, generation, consumer),
                freshFrameRequest,
            )
        }
        if (!started) {
            frameGate.close() // Nothing retained; the caller retries after quiescence (R2).
            return null
        }
        lease = newLease
        frameConsumer = consumer
        lastLeaseRenewElapsedMs = now
        lastDemandElapsedMs = now // Successful demand anchors the idle deadline (R1).
        idleReleaseCompletedElapsedMs = 0 // New demand cycle: prior completion evidence is stale.
        wakeLockKeeper!!.refresh()
        scheduleIdleReleaseLocked()
        failureReason = null // Allocation, attachment and capture are now usable again.
        notifyHostingChanged()
        return lease
    }

    private fun revokeLease() {
        val current = lease
        if (current != null) {
            current.markRevoked()
            lease = null
        }
        frameGate.close() // No further admissions; an admitted frame finishes its own delivery.
        frameConsumer = null
        val revokeHost = displayHost
        if (revokeHost != null) {
            revokeHost.stopCapture()
        }
        releaseWakeLock()
    }

    // ------------------------------------------------------------- watchdog

    /** Detects lease expiry within one tick so production stops within 6 s of the last renewal. */
    private fun watchdogTick() {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(this) {
            if (state != State.HOSTING) {
                return
            }
            if (lease != null) {
                if (HostingPolicy.leaseExpired(now, lastLeaseRenewElapsedMs)) {
                    // Liveness lost: revoke and release capture resources immediately. The gate
                    // already rejected delivery/renewal at the 5 s deadline (R4); the watchdog is
                    // detection, not authority. The idle anchor is NOT moved here (R1).
                    Log.i(TAG, "lease expired gen=$generation")
                    revokeLease()
                    releaseIdleCaptureResourcesLocked()
                    notifyHostingChanged()
                } else {
                    wakeLockKeeper!!.refresh() // Keep the bounded timeout forward while live (F9).
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
    private fun scheduleIdleReleaseLocked() {
        mainHandler.removeCallbacks(idleRelease)
        mainHandler.postDelayed(idleRelease,
                HostingPolicy.idleReleaseInitiationDelayMs(
                        android.os.SystemClock.elapsedRealtime(), lastDemandElapsedMs))
    }

    /**
     * Immediate bounded release of idle capture resources (voluntary release/expiry paths). The
     * completion evidence is bound to THIS owner/cycle: any prior completion is invalidated here,
     * and only the bound owner's completion is recorded (an older retiring owner never overwrites
     * it).
     */
    private fun releaseIdleCaptureResourcesLocked() {
        val host = displayHost
        if (host != null && host.hasLiveCaptureResources()) {
            Log.i(TAG, "idle release gen=$generation")
            idleReleasePendingOwner = host
            idleReleaseCompletedElapsedMs = 0 // Invalidate any earlier cycle's completion.
            host.releaseCaptureResources()
            if (host.isQuiescent()) {
                // Inline completion (no capture thread) of the bound owner: record it.
                idleReleaseCompletedElapsedMs = android.os.SystemClock.elapsedRealtime()
                idleReleasePendingOwner = null
            }
        }
        notifyHostingChanged()
    }

    /**
     * Authoritative idle-release completion observation (diagnostic/test seam), scoped to the
     * current release cycle: 0 until the bound owner completes, invalidated by new demand or
     * readiness. Consumers must require completion >= their cycle's anchor.
     */
    @Synchronized
    fun lastIdleReleaseCompletedElapsedMs(): Long {
        return idleReleaseCompletedElapsedMs
    }

    /** The anchored deadline callback: initiates teardown early enough to complete by it. */
    private fun runIdleRelease() {
        synchronized(this) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (state != State.HOSTING || lease != null) {
                return // Demand resumed or hosting stopped; nothing to release.
            }
            if (!HostingPolicy.idleReleaseDue(now, lastDemandElapsedMs)) {
                scheduleIdleReleaseLocked() // Anchor moved since posting; re-post, never drift (R1).
                return
            }
            releaseIdleCaptureResourcesLocked() // Initiates here; completes by the deadline (R1).
        }
    }

    /**
     * Teardown-task completion signal from a retiring capture owner (may fire off-main): hop to
     * main and evaluate actual completion. Replacement is never auto-started here — callers
     * retry explicitly once quiescence is observed.
     */
    private fun onRetirementSignal(epoch: PresentationEpochs.Token) {
        mainHandler.post {
            if (presentationEpochs.owns(epoch)) {
                evaluateRetirements()
            } else {
                // Old callbacks may complete ONLY retired resources, not the current host.
                synchronized(this) {
                    for (host in retiringHosts.toList()) host.evaluateRetirementCompletion()
                    pruneQuiescedRetiringHostsLocked()
                    if (retiringHosts.any { it.isRetiring() }) {
                        mainHandler.postDelayed({ onRetirementSignal(epoch) }, RETIREMENT_RECHECK_MS)
                    }
                }
            }
        }
    }

    /** Main-thread retirement evaluation: quiescence requires the thread to have exited (R2). */
    private fun evaluateRetirements() {
        synchronized(this) {
            var changed = false
            val activeHost = displayHost
            if (activeHost != null && activeHost.evaluateRetirementCompletion()) {
                changed = true
                if (idleReleasePendingOwner === displayHost) {
                    // Record completion only for the owner this release cycle is waiting on.
                    idleReleaseCompletedElapsedMs = android.os.SystemClock.elapsedRealtime()
                    idleReleasePendingOwner = null
                }
            }
            for (host in retiringHosts) {
                if (host.evaluateRetirementCompletion()) {
                    changed = true
                    if (idleReleasePendingOwner === host) {
                        idleReleaseCompletedElapsedMs = android.os.SystemClock.elapsedRealtime()
                        idleReleasePendingOwner = null
                    }
                }
            }
            val before = retiringHosts.size
            pruneQuiescedRetiringHostsLocked()
            if (changed || retiringHosts.size != before) {
                notifyHostingChanged() // Start/acquisition may be retried explicitly now.
            }
            // Follow-up polling only for actual RETIRING work: a healthy ACTIVE owner is never
            // rechecked, so normal capture schedules nothing (R2/manager follow-up 2).
            var retiringWork = activeHost != null && activeHost.isRetiring()
            if (!retiringWork) {
                for (host in retiringHosts) {
                    if (host.isRetiring()) {
                        retiringWork = true
                        break
                    }
                }
            }
            if (retiringWork) {
                mainHandler.postDelayed(retirementRecheck, RETIREMENT_RECHECK_MS)
            }
        }
    }

    private fun retiringHostsQuiescent(): Boolean {
        for (host in retiringHosts) {
            if (!host.isQuiescent()) {
                return false
            }
        }
        return true
    }

    private fun pruneQuiescedRetiringHostsLocked() {
        retiringHosts.removeIf { it.isQuiescent() }
    }

    /** Authoritative idle anchor (diagnostic/test seam): the last successful demand/readiness. */
    @Synchronized
    fun lastDemandAnchorElapsedMs(): Long {
        return lastDemandElapsedMs
    }

    /** Whether a Phone Activity/container/token is retained as the current UI owner. */
    @Synchronized
    fun hasPhoneUiOwner(): Boolean {
        return phoneUiActivity != null || phoneUiContainer != null || uiOwnerToken != null
    }

    private fun releaseWakeLock() {
        wakeLockKeeper?.release()
    }

    private fun freshFrameRequest(epoch: PresentationEpochs.Token, host: PrivateDisplayHost): Runnable =
        Runnable {
            mainHandler.post {
                if (presentationEpochs.owns(epoch) && displayHost === host && state == State.HOSTING) {
                    session.requestFreshCaptureFrame {
                        presentationEpochs.owns(epoch) && displayHost === host &&
                            state == State.HOSTING && attachment == Attachment.PRIVATE_DISPLAY
                    }
                }
            }
        }

    private fun onPresentationUnavailable(epoch: PresentationEpochs.Token, host: PrivateDisplayHost) {
        mainHandler.post {
            synchronized(this) {
                if (!presentationEpochs.owns(epoch) || displayHost !== host) return@post
                if (state == State.STARTING) {
                    failStart("private presentation became unavailable")
                } else if (state == State.HOSTING) {
                    failureReason = "private presentation became unavailable; explicit restart required"
                    state = State.STOPPING
                    completeStop()
                }
            }
        }
    }

    /** Read-only generation contract, shared by display, reader and test qualification. */
    @Synchronized
    fun presentationProfile(): HostingPresentationProfile =
        checkNotNull(presentationEpochs.current) { "no hosting presentation generation" }.profile

    @Synchronized
    fun privateDisplaySnapshot(): PrivateDisplayHost.DisplaySnapshot? = displayHost?.displaySnapshot()

    // ---------------------------------------------------- lifecycle events

    private fun onSessionChanged(changed: PhoneBrowserSession) {
        synchronized(this) {
            if (state == State.HOSTING && !changed.isLive()) {
                // The renderer is gone: hosting is interrupted and needs an explicit restart.
                Log.i(TAG, "browser lost while hosting gen=$generation")
                state = State.STOPPING
                revokeLease()
                failureReason = appContext.getString(R.string.hosting_failure_browser_lost)
                completeStop()
            }
        }
    }

    /** Called by the service when the system destroys it while hosting was expected to continue. */
    @Synchronized
    fun onServiceDestroyed(service: HostingService) {
        if (hostingService !== service) {
            return
        }
        hostingService = null
        hostingContext = null
        if (state == State.STARTING || state == State.HOSTING) {
            Log.i(TAG, "service destroyed while hosting gen=$generation")
            state = State.STOPPING
            revokeLease()
            failureReason = appContext.getString(R.string.hosting_failure_service_lost)
            completeStop()
        }
    }

    /** Recoverable foreground-entry failure at its owner rolls the start back explicitly (F8). */
    @Synchronized
    fun onServiceEntryFailed(error: RuntimeException) {
        if (state == State.STARTING && generation == pendingStartGeneration) {
            failStart("foreground entry: " + error.message)
        } else {
            val leftoverService = hostingService
            if (leftoverService != null) {
                leftoverService.stopSelf()
            }
        }
    }

    // -------------------------------------------------------------- helpers

    /** Test-only seam: substitute the platform resource factory for failure injection. */
    @Synchronized
    fun setResourceFactoryForTest(factory: PrivateDisplayHost.Factory) {
        resourceFactory = factory
    }

    private fun runOnMain(runnable: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    /** Fallback measurement of Phone geometry only. */
    private class WebViewMetric(
        val width: Int,
        val height: Int,
        val densityDpi: Int,
    ) {
        companion object {
            fun measure(session: PhoneBrowserSession): WebViewMetric {
                val view = session.view()
                if (view == null || view.width <= 0 || view.height <= 0) {
                    return WebViewMetric(0, 0, 0)
                }
                return WebViewMetric(view.width, view.height,
                        view.resources.configuration.densityDpi)
            }
        }
    }

    companion object {
        /** Non-sensitive state-transition diagnostics (SPEC §13: transitions, not page content). */
        private const val TAG = "EyeBrowseHosting"

        private const val WAKE_LOCK_TAG = "EyeBrowse:HostingCapture"

        /** Expiry is detected within one tick; production stops within 6 s total of the last renewal. */
        private const val WATCHDOG_INTERVAL_MS = 500L

        /** Re-check interval for retiring-owner completion (thread exit is observed, not assumed). */
        private const val RETIREMENT_RECHECK_MS = 50L

        /** Plan bound: Start reaches active or explicit failure within 5 seconds of the request. */
        private const val START_COMPLETION_TIMEOUT_MS = 5_000L

        @SuppressLint("StaticFieldLeak")
        private var instance: HostingController? = null

        @JvmStatic
        fun get(context: Context?): HostingController {
            // Java original: `static synchronized get(...)` — monitor is HostingController.class.
            synchronized(HostingController::class.java) {
                if (instance == null) {
                    instance = HostingController(context!!.applicationContext)
                }
                return instance!!
            }
        }
    }
}
