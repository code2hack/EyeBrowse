package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Deterministic admission-decision regressions for the epoch-gated capture path (B): stale
 * callbacks, superseded epochs, unready initialization output, and throttling are each decided
 * without any pixel-color input (the identical-white negative requirement), and an ADMIT
 * decision requires the full bound-identity + staged-readiness state.
 */
public class EpochAdmissionTest {

    private OutputEpoch readyEpoch() {
        OutputEpoch e = new OutputEpoch(new Object(), new Object(), new Object(),
                1000, 2000, 420, 7, 41, 10_000, 10_001);
        e.markDrawCompleted(11_000);
        e.markReady(11_100);
        return e;
    }

    @Test
    public void readyCurrentEpochAdmits() {
        OutputEpoch e = readyEpoch();
        assertEquals(EpochAdmission.Decision.ADMIT, EpochAdmission.evaluate(
                true, false, e.readerRef(), e, e, e.readerRef(), false, 0, 12_000));
    }

    @Test
    public void supersededEpochIsDiscardedEvenWhenItsOwnStateLooksReady() {
        OutputEpoch e = readyEpoch();
        OutputEpoch successor = readyEpoch();
        assertEquals("an old epoch bound at registration never delivers after replacement",
                EpochAdmission.Decision.DISCARD_STALE, EpochAdmission.evaluate(
                        true, false, e.readerRef(), successor, e, e.readerRef(), false, 0, 12_000));
        assertEquals("a null live epoch (retired owner) discards", EpochAdmission.Decision.DISCARD_STALE,
                EpochAdmission.evaluate(true, false, e.readerRef(), null, e, e.readerRef(),
                        false, 0, 12_000));
    }

    @Test
    public void staleReaderCallbackIsDiscarded() {
        OutputEpoch e = readyEpoch();
        Object replacedReader = new Object();
        assertEquals("a callback from a replaced/superseded reader is stale",
                EpochAdmission.Decision.DISCARD_STALE, EpochAdmission.evaluate(
                        true, false, replacedReader, e, e, e.readerRef(), false, 0, 12_000));
        assertEquals("callback reader must equal the bound epoch's reader",
                EpochAdmission.Decision.DISCARD_STALE, EpochAdmission.evaluate(
                        true, false, e.readerRef(), e, e, replacedReader, false, 0, 12_000));
    }

    @Test
    public void unreadyInitializationOutputIsDiscardedWithoutThrottleCost() {
        OutputEpoch e = new OutputEpoch(new Object(), new Object(), new Object(),
                1000, 2000, 420, 7, 41, 10_000, 10_001);
        e.markDrawCompleted(11_000); // Draw done, frame-rendered not yet observed: NOT ready.
        assertEquals("initialization/preparation output never delivers",
                EpochAdmission.Decision.DISCARD_UNREADY, EpochAdmission.evaluate(
                        true, false, e.readerRef(), e, e, e.readerRef(), true, 10_500, 11_200));
        // And the identical check with a completely white/any-color content is the same decision:
        // no decision input is pixel color.
        assertEquals(EpochAdmission.Decision.DISCARD_UNREADY, EpochAdmission.evaluate(
                true, false, e.readerRef(), e, e, e.readerRef(), false, 0, 11_050));
    }

    @Test
    public void releasedOrInactiveCaptureDiscards() {
        OutputEpoch e = readyEpoch();
        assertEquals(EpochAdmission.Decision.DISCARD_STALE, EpochAdmission.evaluate(
                false, false, e.readerRef(), e, e, e.readerRef(), false, 0, 12_000));
        assertEquals(EpochAdmission.Decision.DISCARD_STALE, EpochAdmission.evaluate(
                true, true, e.readerRef(), e, e, e.readerRef(), false, 0, 12_000));
    }

    @Test
    public void throttleWindowSuppressesWithoutColor() {
        OutputEpoch e = readyEpoch();
        assertEquals("inside the 5 fps window: latest-only keeps, no delivery",
                EpochAdmission.Decision.THROTTLED, EpochAdmission.evaluate(
                        true, false, e.readerRef(), e, e, e.readerRef(), true, 12_000, 12_100));
        assertEquals("outside the window: admits", EpochAdmission.Decision.ADMIT,
                EpochAdmission.evaluate(true, false, e.readerRef(), e, e, e.readerRef(),
                        true, 12_000, 12_250));
    }
}
