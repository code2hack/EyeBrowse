package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Deterministic admission/consumption regressions for the epoch-gated capture path (B, Worker2):
 * stale callbacks are never touched; live-epoch images are CONSUMED (acquire+close) while the
 * consumer gate is closed (step5 unresolved — an explicitly non-delivering state); with the gate
 * open the 5 fps window records a single bounded candidate (eventual last-update delivery) or
 * delivers. No decision input is pixel color (identical-white negative requirement).
 */
public class EpochAdmissionTest {

    private OutputEpoch chainedEpoch() {
        OutputEpoch e = new OutputEpoch(new Object(), new Object(), new Object(),
                1000, 2000, 420, 7, 41, 10_000, 10_001);
        e.markVisualStateCompleted(11_000);
        e.markWindowSubmitted(11_100);
        return e;
    }

    @Test
    public void liveEpochWithClosedConsumerGateConsumesWithoutDelivering() {
        OutputEpoch e = chainedEpoch(); // visualState + WINDOW_SUBMITTED recorded.
        assertEquals("step5 unresolved: live output is consumed, never delivered",
                EpochAdmission.Decision.CONSUME_UNREADY, EpochAdmission.evaluate(
                        true, false, e.readerRef(), e, e, e.readerRef(), false, false, 0, 12_000));
        // Even a fully chained epoch stays non-delivering while the gate is closed.
        assertEquals(EpochAdmission.Decision.CONSUME_UNREADY, EpochAdmission.evaluate(
                true, false, e.readerRef(), e, e, e.readerRef(), false, true, 11_150, 12_000));
    }

    @Test
    public void staleOrphanedCallbacksAreDiscardedWithoutTouchingTheReader() {
        OutputEpoch e = chainedEpoch();
        OutputEpoch successor = chainedEpoch();
        assertEquals("an old epoch bound at registration never acts after replacement",
                EpochAdmission.Decision.DISCARD_STALE, EpochAdmission.evaluate(
                        true, false, e.readerRef(), successor, e, e.readerRef(), true, false, 0,
                        12_000));
        assertEquals("retired owner (null live epoch) discards", EpochAdmission.Decision.DISCARD_STALE,
                EpochAdmission.evaluate(true, false, e.readerRef(), null, e, e.readerRef(), true,
                        false, 0, 12_000));
        assertEquals("callback reader must equal the live reader", EpochAdmission.Decision.DISCARD_STALE,
                EpochAdmission.evaluate(true, false, new Object(), e, e, e.readerRef(), true,
                        false, 0, 12_000));
        assertEquals("callback reader must equal the bound epoch's reader",
                EpochAdmission.Decision.DISCARD_STALE, EpochAdmission.evaluate(
                        true, false, e.readerRef(), e, e, new Object(), true, false, 0, 12_000));
        assertEquals(EpochAdmission.Decision.DISCARD_STALE, EpochAdmission.evaluate(
                false, false, e.readerRef(), e, e, e.readerRef(), true, false, 0, 12_000));
        assertEquals(EpochAdmission.Decision.DISCARD_STALE, EpochAdmission.evaluate(
                true, true, e.readerRef(), e, e, e.readerRef(), true, false, 0, 12_000));
    }

    @Test
    public void openGateOutsideThrottleWindowDelivers() {
        OutputEpoch e = chainedEpoch();
        assertEquals(EpochAdmission.Decision.DELIVER, EpochAdmission.evaluate(
                true, false, e.readerRef(), e, e, e.readerRef(), true, false, 0, 12_000));
        assertEquals(EpochAdmission.Decision.DELIVER, EpochAdmission.evaluate(
                true, false, e.readerRef(), e, e, e.readerRef(), true, true, 12_000, 12_250));
    }

    @Test
    public void openGateInsideThrottleWindowRecordsTheBoundedCandidate() {
        OutputEpoch e = chainedEpoch();
        assertEquals("latest-only inside the 5 fps window: single candidate + continuation (S5)",
                EpochAdmission.Decision.RECORD_CANDIDATE, EpochAdmission.evaluate(
                        true, false, e.readerRef(), e, e, e.readerRef(), true, true, 12_000,
                        12_100));
    }

    @Test
    public void chainedStagesAreRequiredBeforeDeliverySemanticsEvenMatter() {
        OutputEpoch e = new OutputEpoch(new Object(), new Object(), new Object(),
                1000, 2000, 420, 7, 41, 10_000, 10_001);
        e.markVisualStateCompleted(11_000); // Stage 1 only: window not submitted.
        // Stage progression is orthogonal to the gate: a closed gate consumes regardless of
        // stage state; the DECISION table never delivers on an incomplete chain.
        assertEquals(EpochAdmission.Decision.CONSUME_UNREADY, EpochAdmission.evaluate(
                true, false, e.readerRef(), e, e, e.readerRef(), false, false, 0, 11_050));
    }
}
