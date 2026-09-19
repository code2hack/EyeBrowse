package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests of the hosting timing and size bounds, with an explicit fake clock where useful. */
public class HostingPolicyTest {

    private static final HostingPolicy.Clock CLOCK_AT = () -> 123_456L;

    @Test
    public void leaseExpiresOnlyAfterTheFiveSecondTtl() {
        long now = 100_000;
        assertEquals(false, HostingPolicy.leaseExpired(now, now - HostingPolicy.LEASE_TTL_MS));
        assertEquals(true, HostingPolicy.leaseExpired(now + 1,
                now - HostingPolicy.LEASE_TTL_MS));
        // Fake-clock shape: the decision reads the caller-supplied time, not a global.
        assertEquals(true, HostingPolicy.leaseExpired(CLOCK_AT.now(),
                CLOCK_AT.now() - HostingPolicy.LEASE_TTL_MS - 1));
    }

    @Test
    public void idleReleaseAppliesOnlyAfterThirtySecondsWithoutDemand() {
        long now = 500_000;
        assertEquals(false, HostingPolicy.idleExceeded(now, now - HostingPolicy.IDLE_RELEASE_MS));
        assertEquals(true, HostingPolicy.idleExceeded(now + 1, now - HostingPolicy.IDLE_RELEASE_MS));
    }

    // ------------------------------------------- anchored idle deadline (R1)

    @Test
    public void idleDeadlineIsAnchoredToLastDemandNotToSchedulingMoment() {
        long lastDemand = 100_000;
        assertEquals(HostingPolicy.IDLE_RELEASE_MS, HostingPolicy.idleReleaseDeadlineMs(lastDemand)
                - lastDemand);
        // A release/expiry observed 25 s after the last demand must schedule the callback for
        // lastDemand+30s — NOT now+30s. This is the release-anchoring defect of R1.
        long observedAt = lastDemand + 25_000;
        assertEquals(5_000, HostingPolicy.idleReleaseDelayMs(observedAt, lastDemand));
        assertEquals(lastDemand + 30_000,
                observedAt + HostingPolicy.idleReleaseDelayMs(observedAt, lastDemand));
        // Anchor at/after the deadline: fire immediately, never negative.
        assertEquals(0, HostingPolicy.idleReleaseDelayMs(lastDemand + 30_000, lastDemand));
        assertEquals(0, HostingPolicy.idleReleaseDelayMs(lastDemand + 45_000, lastDemand));
        assertTrue(HostingPolicy.idleDeadlineReached(lastDemand + 30_000, lastDemand));
        assertFalse(HostingPolicy.idleDeadlineReached(lastDemand + 29_999, lastDemand));
    }

    @Test
    public void renewedDemandMovesTheAnchoredDeadlineForwardExactlyOncePerSuccess() {
        long firstDemand = 200_000;
        long renewalAt = firstDemand + 4_000; // Successful main-thread renewal.
        assertEquals(renewalAt + HostingPolicy.IDLE_RELEASE_MS,
                HostingPolicy.idleReleaseDeadlineMs(renewalAt));
        // The old anchor's deadline no longer governs: only the newest successful demand does.
        assertTrue(HostingPolicy.idleReleaseDeadlineMs(renewalAt)
                > HostingPolicy.idleReleaseDeadlineMs(firstDemand));
    }

    @Test
    public void frameThrottleEnforcesTheFiveFpsCap() {
        long now = 1_000_000;
        assertEquals(true, HostingPolicy.frameThrottled(now, now - HostingPolicy.MIN_FRAME_INTERVAL_MS + 1));
        assertEquals(false, HostingPolicy.frameThrottled(now, now - HostingPolicy.MIN_FRAME_INTERVAL_MS));
    }

    @Test
    public void viewportBoundsAcceptMeasuredSizesWithinTheAllocationCap() {
        assertNull(HostingPolicy.viewportError(1, 1));
        assertNull(HostingPolicy.viewportError(1856, 1980));
        assertNull(HostingPolicy.viewportError(4096, 1024)); // exactly 4,194,304 pixels
        assertNull(HostingPolicy.viewportError(2048, 2048)); // exactly 4,194,304 pixels
    }

    @Test
    public void viewportBoundsReportUnsupportedSizesWithReasons() {
        assertEquals("viewport not measured", HostingPolicy.viewportError(0, 100));
        assertEquals("viewport not measured", HostingPolicy.viewportError(-5, 100));
        assertEquals("viewport dimension 4097 exceeds 4096", HostingPolicy.viewportError(4097, 1));
        assertEquals("viewport dimension 5000 exceeds 4096", HostingPolicy.viewportError(100, 5000));
        assertEquals("viewport allocation 4198400 exceeds 4194304",
                HostingPolicy.viewportError(4096, 1025));
        assertEquals("viewport allocation 9437184 exceeds 4194304",
                HostingPolicy.viewportError(2304, 4096));
    }
}
