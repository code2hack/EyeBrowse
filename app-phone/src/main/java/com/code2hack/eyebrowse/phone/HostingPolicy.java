package com.code2hack.eyebrowse.phone;

/**
 * Pure hosting timing and size policy, testable on the JVM with a fake clock.
 *
 * <p>Initial measurable operating bounds from the ticket plan: one service, one browser, at most
 * one private display/presentation, an ImageReader with {@code maxImages=2}, latest-only output
 * with no frame FIFO, capture at most 5 fps only for a live in-process consumer, a 5-second lease,
 * frame production and wake-lock release within 6 seconds of lost liveness, a 10-second wake-lock
 * acquisition timeout, and capture-resource release after 30 seconds without demand. Capture
 * allocation is capped at 4,194,304 pixels with each dimension at most 4096.
 */
final class HostingPolicy {

    /** Lease must be renewed within this interval or liveness is lost. */
    static final long LEASE_TTL_MS = 5_000;

    /** After this long without lease demand, capture reader/surface/thread are released. */
    static final long IDLE_RELEASE_MS = 30_000;

    /** Minimum interval between delivered frames: the 5 fps capture cap. */
    static final long MIN_FRAME_INTERVAL_MS = 200;

    /** Platform timeout carried by every wake-lock acquisition; renewed while liveness holds. */
    static final long WAKE_LOCK_TIMEOUT_MS = 10_000;

    /** Maximum capture allocation in pixels (4 Mi pixels). */
    static final long MAX_ALLOCATION_PIXELS = 4_194_304L;

    /** Maximum capture dimension in pixels. */
    static final int MAX_DIMENSION = 4096;

    private HostingPolicy() {
    }

    interface Clock {
        long now();
    }

    static boolean leaseExpired(long nowMs, long lastRenewMs) {
        return nowMs - lastRenewMs > LEASE_TTL_MS;
    }

    static boolean idleExceeded(long nowMs, long lastDemandMs) {
        return nowMs - lastDemandMs > IDLE_RELEASE_MS;
    }

    /**
     * The immutable idle-release deadline: exactly {@link #IDLE_RELEASE_MS} after the last
     * successful demand (acquire/renewal) or resource readiness. Release, expiry ticks and other
     * lifecycle events never move this anchor (R1); only a new successful demand/readiness does.
     */
    static long idleReleaseDeadlineMs(long lastDemandMs) {
        return lastDemandMs + IDLE_RELEASE_MS;
    }

    /**
     * Delay until the idle deadline, anchored to {@code lastDemandMs} — NOT to {@code nowMs} — so
     * a late release/expiry can never extend the deadline. Never negative.
     */
    static long idleReleaseDelayMs(long nowMs, long lastDemandMs) {
        return Math.max(0L, idleReleaseDeadlineMs(lastDemandMs) - nowMs);
    }

    /** True when {@code nowMs} has reached the anchored idle deadline. */
    static boolean idleDeadlineReached(long nowMs, long lastDemandMs) {
        return nowMs >= idleReleaseDeadlineMs(lastDemandMs);
    }

    static boolean frameThrottled(long nowMs, long lastDeliveryMs) {
        return nowMs - lastDeliveryMs < MIN_FRAME_INTERVAL_MS;
    }

    /**
     * Returns {@code null} when the measured viewport may be used as the private-display capture
     * geometry, otherwise a short reason. Unsupported sizes are reported, never silently altered.
     */
    static String viewportError(int width, int height) {
        if (width <= 0 || height <= 0) {
            return "viewport not measured";
        }
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            return "viewport dimension " + Math.max(width, height) + " exceeds " + MAX_DIMENSION;
        }
        if ((long) width * height > MAX_ALLOCATION_PIXELS) {
            return "viewport allocation " + (long) width * height + " exceeds "
                    + MAX_ALLOCATION_PIXELS;
        }
        return null;
    }
}
