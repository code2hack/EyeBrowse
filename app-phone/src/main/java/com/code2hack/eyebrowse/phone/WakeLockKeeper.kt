package com.code2hack.eyebrowse.phone

/**
 * Keeps the bounded platform wake lock refreshed while lease liveness is valid (JVM-testable).
 *
 * <p>{@link PowerManager.WakeLock#acquire(long)} bounds each acquisition to the platform timeout;
 * the keeper therefore re-executes the bounded acquisition on EVERY refresh (lease acquisition,
 * every lease renewal, and the watchdog tick while a lease is live) instead of waiting for the
 * timeout to expire before reacquiring. A renewing lease never enters an unheld gap.
 */
internal class WakeLockKeeper(
    private val handle: Handle,
    private val clock: HostingPolicy.Clock,
) {

    /** Minimal platform seam so refresh/loss behavior is deterministically testable. */
    interface Handle {
        fun acquire(timeoutMs: Long)

        fun isHeld(): Boolean

        fun release()
    }

    // Java original: non-volatile `private long lastRefreshMs` — volatility intentionally NOT added.
    private var lastRefreshMs: Long = Long.MIN_VALUE

    /** Executes a fresh bounded acquisition, moving the platform timeout deadline forward. */
    fun refresh() {
        handle.acquire(HostingPolicy.WAKE_LOCK_TIMEOUT_MS)
        lastRefreshMs = clock.now()
    }

    fun release() {
        handle.release()
        lastRefreshMs = Long.MIN_VALUE
    }

    fun isHeld(): Boolean {
        return handle.isHeld()
    }

    fun lastRefreshMs(): Long {
        return lastRefreshMs
    }
}
