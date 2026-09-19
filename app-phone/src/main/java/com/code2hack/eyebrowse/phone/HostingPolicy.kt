package com.code2hack.eyebrowse.phone

import kotlin.math.max

/**
 * Pure hosting timing and size policy, testable on the JVM with a fake clock.
 *
 * <p>Initial measurable operating bounds from the ticket plan: one service, one browser, at most
 * one private display/presentation, an ImageReader with {@code maxImages=2}, latest-only output
 * with no frame FIFO, capture at most 5 fps only for a live in-process consumer, a 5-second lease,
 * frame production and wake-lock release within 6 seconds of lost liveness, a 10-second wake-lock
 * acquisition timeout, and capture-resource release after 30 seconds without demand. Capture
 * allocation is capped at 4,194,304 pixels with each dimension at most 4096.
 *
 * <p>Migration note: visibility widened package-private -> public for the separately compiled
 * androidTest consumer; companion constants compile to static fields and `@JvmStatic` preserves
 * the Java static-method call shape `HostingPolicy.leaseExpired(...)` (disclosed in the ledger).
 */
class HostingPolicy private constructor() {

    interface Clock {
        fun now(): Long
    }

    companion object {
        /** Lease must be renewed within this interval or liveness is lost. */
        const val LEASE_TTL_MS: Long = 5_000L

        /** After this long without lease demand, capture reader/surface/thread are released. */
        const val IDLE_RELEASE_MS: Long = 30_000L

        /**
         * Teardown initiation lead before the anchored idle deadline: asynchronous completion needs
         * time, so cleanup STARTS before the deadline in order to complete by it — the plan deadline
         * itself is never extended (R1).
         */
        const val IDLE_RELEASE_LEAD_MS: Long = 500L

        /** Minimum interval between delivered frames: the 5 fps capture cap. */
        const val MIN_FRAME_INTERVAL_MS: Long = 200L

        /** Platform timeout carried by every wake-lock acquisition; renewed while liveness holds. */
        const val WAKE_LOCK_TIMEOUT_MS: Long = 10_000L

        /** Maximum capture allocation in pixels (4 Mi pixels). */
        const val MAX_ALLOCATION_PIXELS: Long = 4_194_304L

        /** Maximum capture dimension in pixels. */
        const val MAX_DIMENSION: Int = 4096

        @JvmStatic
        fun leaseExpired(nowMs: Long, lastRenewMs: Long): Boolean {
            return nowMs - lastRenewMs > LEASE_TTL_MS
        }

        @JvmStatic
        fun idleExceeded(nowMs: Long, lastDemandMs: Long): Boolean {
            return nowMs - lastDemandMs > IDLE_RELEASE_MS
        }

        /**
         * The immutable idle-release deadline: exactly {@link #IDLE_RELEASE_MS} after the last
         * successful demand (acquire/renewal) or resource readiness. Release, expiry ticks and other
         * lifecycle events never move this anchor (R1); only a new successful demand/readiness does.
         */
        @JvmStatic
        fun idleReleaseDeadlineMs(lastDemandMs: Long): Long {
            return lastDemandMs + IDLE_RELEASE_MS
        }

        /**
         * Delay until the idle deadline, anchored to {@code lastDemandMs} — NOT to {@code nowMs} — so
         * a late release/expiry can never extend the deadline. Never negative.
         */
        @JvmStatic
        fun idleReleaseDelayMs(nowMs: Long, lastDemandMs: Long): Long {
            return max(0L, idleReleaseDeadlineMs(lastDemandMs) - nowMs)
        }

        /** True when {@code nowMs} has reached the anchored idle deadline. */
        @JvmStatic
        fun idleDeadlineReached(nowMs: Long, lastDemandMs: Long): Boolean {
            return nowMs >= idleReleaseDeadlineMs(lastDemandMs)
        }

        /**
         * True when idle-release teardown should INITIATE at {@code nowMs}: at/after the deadline
         * minus the completion lead, so teardown completes by the exact plan deadline (R1).
         */
        @JvmStatic
        fun idleReleaseDue(nowMs: Long, lastDemandMs: Long): Boolean {
            return nowMs >= idleReleaseDeadlineMs(lastDemandMs) - IDLE_RELEASE_LEAD_MS
        }

        /**
         * Delay until teardown INITIATION (deadline minus lead), anchored to {@code lastDemandMs};
         * never negative.
         */
        @JvmStatic
        fun idleReleaseInitiationDelayMs(nowMs: Long, lastDemandMs: Long): Long {
            return max(0L, idleReleaseDeadlineMs(lastDemandMs) - IDLE_RELEASE_LEAD_MS - nowMs)
        }

        @JvmStatic
        fun frameThrottled(nowMs: Long, lastDeliveryMs: Long): Boolean {
            return nowMs - lastDeliveryMs < MIN_FRAME_INTERVAL_MS
        }

        /**
         * Returns {@code null} when the measured viewport may be used as the private-display capture
         * geometry, otherwise a short reason. Unsupported sizes are reported, never silently altered.
         */
        @JvmStatic
        fun viewportError(width: Int, height: Int): String? {
            if (width <= 0 || height <= 0) {
                return "viewport not measured"
            }
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                return "viewport dimension " + maxOf(width, height) + " exceeds " + MAX_DIMENSION
            }
            if (width.toLong() * height > MAX_ALLOCATION_PIXELS) {
                return "viewport allocation " + (width.toLong() * height) + " exceeds " +
                        MAX_ALLOCATION_PIXELS
            }
            return null
        }
    }
}
