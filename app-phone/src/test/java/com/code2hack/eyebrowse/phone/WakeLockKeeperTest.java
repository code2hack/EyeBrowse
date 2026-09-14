package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic wake-lock regressions (F9): every live renewal refreshes the bounded platform
 * timeout immediately, so a renewing lease never enters an unheld gap, and release clears state.
 */
public class WakeLockKeeperTest {

    private static final class FakeHandle implements WakeLockKeeper.Handle {
        final List<Long> acquireTimestamps = new ArrayList<>();
        boolean held;
        long deadline;

        @Override
        public void acquire(long timeoutMs) {
            acquireTimestamps.add(currentNow);
            held = true;
            deadline = currentNow + timeoutMs;
        }

        @Override
        public boolean isHeld() {
            return held && currentNow < deadline;
        }

        @Override
        public void release() {
            held = false;
            deadline = 0;
        }

        long currentNow;
    }

    @Test
    public void everyRenewalRefreshesTheBoundedTimeoutWithNoUnheldGap() {
        FakeHandle handle = new FakeHandle();
        long[] now = {100_000};
        WakeLockKeeper keeper = new WakeLockKeeper(handle, () -> now[0]);

        keeper.refresh(); // Lease acquisition.
        long previousAcquire = 100_000;
        // Renew every 1s for 25s across three 10s platform-timeout windows.
        for (int second = 1; second <= 25; second++) {
            now[0] = 100_000 + second * 1_000;
            handle.currentNow = now[0];
            keeper.refresh();
            assertEquals("a renewal must always re-execute the bounded acquisition",
                    now[0], (long) handle.acquireTimestamps.get(handle.acquireTimestamps.size() - 1));
            assertTrue("the lock must be held at every renewal while liveness is valid",
                    handle.isHeld());
            assertTrue("no unheld gap may exceed the platform timeout",
                    now[0] - previousAcquire <= 10_000);
            previousAcquire = now[0];
        }
        assertEquals(26, handle.acquireTimestamps.size());
    }

    @Test
    public void heldStateExpiresAtThePlatformDeadlineWithoutRefresh() {
        FakeHandle handle = new FakeHandle();
        long[] now = {50_000};
        WakeLockKeeper keeper = new WakeLockKeeper(handle, () -> now[0]);
        handle.currentNow = 50_000; // The fake stamps acquisitions with its own clock.
        keeper.refresh();
        assertTrue(handle.isHeld());
        now[0] = 50_000 + 10_000; // Platform timeout elapsed with no refresh.
        handle.currentNow = now[0];
        assertFalse("without refresh the bounded acquisition expires", handle.isHeld());
        keeper.refresh(); // A later renewal reacquires.
        now[0] = 50_000 + 10_500;
        handle.currentNow = now[0];
        assertTrue(handle.isHeld());
    }

    @Test
    public void releaseClearsHeldStateAndRefreshBookkeeping() {
        FakeHandle handle = new FakeHandle();
        long[] now = {7_000};
        WakeLockKeeper keeper = new WakeLockKeeper(handle, () -> now[0]);
        handle.currentNow = 7_000;
        keeper.refresh();
        assertEquals(7_000, keeper.lastRefreshMs());
        keeper.release();
        assertFalse(handle.isHeld());
        assertEquals(Long.MIN_VALUE, keeper.lastRefreshMs());
    }
}
