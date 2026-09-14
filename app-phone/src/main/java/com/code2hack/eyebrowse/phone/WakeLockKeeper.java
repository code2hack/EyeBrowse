package com.code2hack.eyebrowse.phone;

/**
 * Keeps the bounded platform wake lock refreshed while lease liveness is valid (JVM-testable).
 *
 * <p>{@link PowerManager.WakeLock#acquire(long)} bounds each acquisition to the platform timeout;
 * the keeper therefore re-executes the bounded acquisition on EVERY refresh (lease acquisition,
 * every lease renewal, and the watchdog tick while a lease is live) instead of waiting for the
 * timeout to expire before reacquiring. A renewing lease never enters an unheld gap.
 */
final class WakeLockKeeper {

    /** Minimal platform seam so refresh/loss behavior is deterministically testable. */
    interface Handle {
        void acquire(long timeoutMs);

        boolean isHeld();

        void release();
    }

    private final Handle handle;
    private final HostingPolicy.Clock clock;
    private long lastRefreshMs = Long.MIN_VALUE;

    WakeLockKeeper(Handle handle, HostingPolicy.Clock clock) {
        this.handle = handle;
        this.clock = clock;
    }

    /** Executes a fresh bounded acquisition, moving the platform timeout deadline forward. */
    void refresh() {
        handle.acquire(HostingPolicy.WAKE_LOCK_TIMEOUT_MS);
        lastRefreshMs = clock.now();
    }

    void release() {
        handle.release();
        lastRefreshMs = Long.MIN_VALUE;
    }

    boolean isHeld() {
        return handle.isHeld();
    }

    long lastRefreshMs() {
        return lastRefreshMs;
    }
}
