package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Deterministic regressions for the output-epoch identity/staged model (B, Worker2): the
 * visual-state -> WINDOW_SUBMITTED chain is ordered and production-only in shape, the full live
 * identity (including the session output-state version) is enforced, stages never complete
 * retroactively after replacement, and the ORIGINAL eligibility anchor is immutable.
 */
public class OutputEpochTest {

    private static final long VERSION = 41;

    private static OutputEpoch epoch(long eligible) {
        return new OutputEpoch(new Object(), new Object(), new Object(),
                1000, 2000, 420, 7, VERSION, eligible, eligible + 5);
    }

    @Test
    public void windowSubmissionRequiresVisualStateCompletionAndStampsOnce() {
        OutputEpoch e = epoch(10_000);
        assertFalse(e.isVisualStateCompleted());
        assertFalse(e.isWindowSubmitted());
        e.markWindowSubmitted(12_000); // Out-of-order production call: stage 1 missing.
        assertFalse("window submission cannot precede the visual-state completion", e.isWindowSubmitted());
        e.markVisualStateCompleted(12_000);
        assertTrue(e.isVisualStateCompleted());
        assertFalse(e.isWindowSubmitted());
        e.markWindowSubmitted(12_500);
        assertTrue(e.isWindowSubmitted());
        assertEquals(12_500, e.windowSubmittedElapsedMs());
        e.markVisualStateCompleted(99_999); // First completion wins.
        assertEquals(12_000, e.visualStateElapsedMs());
        e.markWindowSubmitted(99_999);
        assertEquals(12_500, e.windowSubmittedElapsedMs());
    }

    @Test
    public void fullLiveIdentityRejectsAnyChangedDimensionIncludingSessionOutputVersion() {
        OutputEpoch e = epoch(1_000);
        Object view = e.viewRef();
        Object presentation = e.presentationRef();
        Object reader = e.readerRef();
        assertTrue(e.matchesLive(view, presentation, reader, 1000, 2000, 420, 7, VERSION));
        assertFalse("view replacement invalidates", e.matchesLive(new Object(), presentation,
                reader, 1000, 2000, 420, 7, VERSION));
        assertFalse("presentation replacement invalidates", e.matchesLive(view, new Object(),
                reader, 1000, 2000, 420, 7, VERSION));
        assertFalse("reader replacement invalidates", e.matchesLive(view, presentation,
                new Object(), 1000, 2000, 420, 7, VERSION));
        assertFalse("geometry change invalidates", e.matchesLive(view, presentation, reader,
                1001, 2000, 420, 7, VERSION));
        assertFalse("density change invalidates", e.matchesLive(view, presentation, reader,
                1000, 2000, 421, 7, VERSION));
        assertFalse("generation change invalidates", e.matchesLive(view, presentation, reader,
                1000, 2000, 420, 8, VERSION));
        assertFalse("document/attachment (output-state) change invalidates",
                e.matchesLive(view, presentation, reader, 1000, 2000, 420, 7, VERSION + 1));
    }

    @Test
    public void stagesNeverCompleteRetroactivelyAfterReplacement() {
        OutputEpoch old = epoch(1_000);
        OutputEpoch replacement = epoch(2_000); // The controller replaces, never resets.
        old.markVisualStateCompleted(3_000);
        // The replacement starts UNCHAINED: the old epoch's completion cannot leak into it.
        assertFalse(replacement.isVisualStateCompleted());
        assertFalse(replacement.isWindowSubmitted());
        old.markWindowSubmitted(4_000); // A late old-chain completion touches only the old epoch.
        assertTrue(old.isWindowSubmitted());
        assertFalse(replacement.isWindowSubmitted());
    }

    @Test
    public void eligibilityAnchorIsImmutableAcrossStages() {
        OutputEpoch e = epoch(50_000);
        assertEquals(50_000, e.eligibleElapsedMs());
        e.markVisualStateCompleted(80_000);
        e.markWindowSubmitted(80_100);
        assertEquals("B: the first-delivery bound stays anchored to the ORIGINAL eligibility",
                50_000, e.eligibleElapsedMs());
        assertTrue(e.windowSubmittedElapsedMs() > e.eligibleElapsedMs());
    }
}
