package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Deterministic regressions for the output-epoch identity/readiness model (B): readiness never
 * completes implicitly, is stamped once, is bound to the exact identity tuple, and the original
 * eligibility anchor is immutable across readiness and identity checks.
 */
public class OutputEpochTest {

    private static OutputEpoch epoch(long eligible) {
        return new OutputEpoch(new Object(), new Object(), new Object(),
                1000, 2000, 420, 7, eligible, eligible + 5);
    }

    @Test
    public void readinessDoesNotCompleteImplicitlyAndStampsOnce() {
        OutputEpoch e = epoch(10_000);
        assertFalse("a fresh epoch is not ready (initialization output is not deliverable)",
                e.isReady());
        e.markReady(12_345);
        assertTrue(e.isReady());
        assertEquals(12_345, e.readyElapsedMs());
        e.markReady(99_999); // Second completion attempt must not move the first stamp.
        assertEquals("first readiness completion wins", 12_345, e.readyElapsedMs());
    }

    @Test
    public void identityBindingRejectsAnyChangedDimension() {
        OutputEpoch e = epoch(1_000);
        Object view = e.viewRef();
        Object presentation = e.presentationRef();
        Object reader = e.readerRef();
        assertTrue(e.matches(view, presentation, reader, 1000, 2000, 420, 7));
        assertFalse("view replacement invalidates", e.matches(new Object(), presentation, reader,
                1000, 2000, 420, 7));
        assertFalse("presentation replacement invalidates", e.matches(view, new Object(), reader,
                1000, 2000, 420, 7));
        assertFalse("reader replacement invalidates", e.matches(view, presentation, new Object(),
                1000, 2000, 420, 7));
        assertFalse("geometry change invalidates", e.matches(view, presentation, reader,
                1001, 2000, 420, 7));
        assertFalse("density change invalidates", e.matches(view, presentation, reader,
                1000, 2000, 421, 7));
        assertFalse("generation change invalidates", e.matches(view, presentation, reader,
                1000, 2000, 420, 8));
    }

    @Test
    public void eligibilityAnchorIsImmutable() {
        OutputEpoch e = epoch(50_000);
        assertEquals(50_000, e.eligibleElapsedMs());
        e.markReady(80_000); // Readiness happening later never moves the eligibility anchor.
        assertEquals("B: first-delivery bound stays anchored to the ORIGINAL eligibility", 50_000,
                e.eligibleElapsedMs());
        assertTrue(e.readyElapsedMs() > e.eligibleElapsedMs());
    }
}
