package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Deterministic regressions for the output-epoch identity/staged-readiness model (B): readiness
 * never completes implicitly or from a draw callback alone, is stamped once, is bound to the
 * full identity tuple including the session output-state version, and the original eligibility
 * anchor is immutable. Pending readiness resets on output-state changes; a ready epoch does not.
 */
public class OutputEpochTest {

    private static final long VERSION = 41;

    private static OutputEpoch epoch(long eligible) {
        return new OutputEpoch(new Object(), new Object(), new Object(),
                1000, 2000, 420, 7, VERSION, eligible, eligible + 5);
    }

    @Test
    public void readinessIsStagedAndNeverCompletesFromADrawCallbackAlone() {
        OutputEpoch e = epoch(10_000);
        assertFalse("a fresh epoch is not ready (initialization output is not deliverable)",
                e.isReady());
        e.markDrawCompleted(12_000);
        assertTrue("stage 1 recorded", e.isDrawCompleted());
        assertFalse("a completed draw ALONE does not admit delivery (Planner step 4)",
                e.isReady());
        e.markReady(12_345); // Production path only: frame rendered into the capture buffer queue
        assertTrue(e.isReady()); // after the completed draw.
        assertEquals(12_345, e.readyElapsedMs());
        e.markReady(99_999); // First completion wins.
        assertEquals(12_345, e.readyElapsedMs());
        e.markDrawCompleted(88_888); // Stage 1 re-fire never moves the recorded draw.
        assertEquals(12_000, e.drawCompletedElapsedMs());
    }

    @Test
    public void markReadyWithoutACompletedDrawCannotCompleteReadiness() {
        OutputEpoch e = epoch(1_000);
        e.markReady(2_000); // Out-of-order production call: no draw completed yet.
        assertFalse("ready requires the completed draw first", e.isReady());
        e.markDrawCompleted(3_000);
        e.markReady(4_000);
        assertTrue(e.isReady());
    }

    @Test
    public void identityBindingRejectsAnyChangedDimensionIncludingSessionOutputVersion() {
        OutputEpoch e = epoch(1_000);
        Object view = e.viewRef();
        Object presentation = e.presentationRef();
        Object reader = e.readerRef();
        assertTrue(e.matches(view, presentation, reader, 1000, 2000, 420, 7, VERSION));
        assertFalse("view replacement invalidates", e.matches(new Object(), presentation, reader,
                1000, 2000, 420, 7, VERSION));
        assertFalse("presentation replacement invalidates", e.matches(view, new Object(), reader,
                1000, 2000, 420, 7, VERSION));
        assertFalse("reader replacement invalidates", e.matches(view, presentation, new Object(),
                1000, 2000, 420, 7, VERSION));
        assertFalse("geometry change invalidates", e.matches(view, presentation, reader,
                1001, 2000, 420, 7, VERSION));
        assertFalse("density change invalidates", e.matches(view, presentation, reader,
                1000, 2000, 421, 7, VERSION));
        assertFalse("generation change invalidates", e.matches(view, presentation, reader,
                1000, 2000, 420, 8, VERSION));
        assertFalse("session output-state (document/attachment) change invalidates",
                e.matches(view, presentation, reader, 1000, 2000, 420, 7, VERSION + 1));
    }

    @Test
    public void pendingReadinessResetsOnOutputStateChangeButReadyEpochDoesNot() {
        OutputEpoch e = epoch(1_000);
        e.markDrawCompleted(2_000);
        e.resetReadiness(); // Document/attachment changed before readiness completed.
        assertFalse("pending readiness invalidated for re-observation", e.isDrawCompleted());
        e.markDrawCompleted(5_000);
        e.markReady(6_000);
        e.resetReadiness(); // A delivering epoch is never reset (B governs the FIRST frame).
        assertTrue("ready epoch survives output-state changes", e.isReady());
        assertEquals(6_000, e.readyElapsedMs());
    }

    @Test
    public void eligibilityAnchorIsImmutable() {
        OutputEpoch e = epoch(50_000);
        assertEquals(50_000, e.eligibleElapsedMs());
        e.markDrawCompleted(80_000);
        e.markReady(80_001); // Readiness happening later never moves the eligibility anchor.
        assertEquals("B: first-delivery bound stays anchored to the ORIGINAL eligibility", 50_000,
                e.eligibleElapsedMs());
    }
}
