package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * JVM regressions for the test-only A callback qualification. They exercise the same
 * {@link OutputQualification} helper the instrumented consumer uses (no parallel classifier) and
 * cover the retained negatives: early non-qualifying then valid; perpetual initialization;
 * valid-only-after-deadline; wrong geometry/document; immutable callback samples across borrowed
 * buffer reuse; and an intentionally white document that is valid only when white is expected.
 */
public class OutputQualificationTest {

    private static final int CREAM = 0xfff6f3ea;
    private static final int WHITE = 0xffffffff;
    private static final int WIDTH = 100;
    private static final int HEIGHT = 50;

    private static OutputQualification.Observation observation(long uptimeMs, int width,
            int height, int sampledColor) {
        return OutputQualification.observe(uptimeMs, uptimeMs, width, height, 1, uptimeMs, 0L,
                sampledColor, WIDTH, HEIGHT, CREAM);
    }

    @Test
    public void earlyNonQualifyingCallbackThenValidBeforeDeadlineQualifies() {
        List<OutputQualification.Observation> observations = new ArrayList<>();
        observations.add(observation(100, WIDTH, HEIGHT, WHITE));    // initialization output
        observations.add(observation(1_500, WIDTH, HEIGHT, CREAM));  // document drawn
        assertEquals("earliest qualifying callback is the second one", 1,
                OutputQualification.earliestQualifyingIndex(observations, 0));
        long delay = OutputQualification.earliestQualifyingDelayMs(observations, 0, 100);
        assertEquals(1_400, delay);
        assertTrue("valid callback is inside the unchanged 2s bound",
                OutputQualification.validWithinBound(delay));
        assertEquals("the earlier raw callback is retained, not hidden", 1,
                OutputQualification.nonQualifyingBefore(observations, 0, 1));
        assertTrue(OutputQualification.summary(observations, 0, 100)
                .contains("firstValidDelay=1400ms"));
    }

    @Test
    public void perpetualInitializationNeverQualifies() {
        List<OutputQualification.Observation> observations = new ArrayList<>();
        observations.add(observation(0, WIDTH, HEIGHT, WHITE));
        observations.add(observation(1_000, WIDTH, HEIGHT, WHITE));
        observations.add(observation(1_999, WIDTH, HEIGHT, WHITE));
        assertEquals(-1, OutputQualification.earliestQualifyingIndex(observations, 0));
        assertEquals(-1, OutputQualification.earliestQualifyingDelayMs(observations, 0, 0));
        assertFalse("perpetual initialization cannot satisfy the bound", OutputQualification
                .validWithinBound(OutputQualification.earliestQualifyingDelayMs(observations, 0, 0)));
        assertTrue(OutputQualification.summary(observations, 0, 0)
                .contains("nonQualifyingBefore=none-valid totalNonQualifying=3"));
    }

    @Test
    public void validButLateCallbackIsRetainedAndFailsTheOriginalBound() {
        List<OutputQualification.Observation> observations = new ArrayList<>();
        observations.add(observation(500, WIDTH, HEIGHT, WHITE));
        observations.add(observation(2_500, WIDTH, HEIGHT, CREAM));
        int index = OutputQualification.earliestQualifyingIndex(observations, 0);
        assertEquals("the late valid callback is observed", 1, index);
        long delay = OutputQualification.earliestQualifyingDelayMs(observations, 0, 100);
        assertEquals(2_400, delay);
        assertFalse("valid only after the original deadline must fail",
                OutputQualification.validWithinBound(delay));
    }

    @Test
    public void wrongGeometryOrWrongDocumentDoesNotQualify() {
        List<OutputQualification.Observation> observations = new ArrayList<>();
        // Correct document color at the wrong (old) geometry is a raw callback, not a valid one.
        observations.add(observation(200, WIDTH - 1, HEIGHT, CREAM));
        // Correct geometry but another document's pixels (white vs the cream fixture).
        observations.add(observation(400, WIDTH, HEIGHT, WHITE));
        // The current document at the current geometry qualifies.
        observations.add(observation(900, WIDTH, HEIGHT, CREAM));
        assertEquals(2, OutputQualification.earliestQualifyingIndex(observations, 0));
        assertEquals(800, OutputQualification.earliestQualifyingDelayMs(observations, 0, 100));
        assertTrue(OutputQualification.validWithinBound(
                OutputQualification.earliestQualifyingDelayMs(observations, 0, 100)));
        assertFalse(observations.get(0).geometryMatches);
        assertTrue(observations.get(0).contentMatches);
        assertFalse(observations.get(1).contentMatches);
    }

    @Test
    public void sampledPixelsAreImmutableAcrossBorrowedBufferReuse() {
        int[] borrowedBuffer = {WHITE};
        List<OutputQualification.Observation> observations = new ArrayList<>();
        observations.add(OutputQualification.observe(1_000, 1_000, WIDTH, HEIGHT, 1, 1, 0L,
                borrowedBuffer[0], WIDTH, HEIGHT, CREAM));
        // The pipeline reuses the borrowed buffer for a later frame; the retained observation
        // must keep the sample and verdict captured during the callback.
        borrowedBuffer[0] = CREAM;
        assertEquals(WHITE, observations.get(0).sampledColor);
        assertFalse("a re-used buffer can never retroactively qualify an earlier callback",
                observations.get(0).qualified);
        assertEquals(-1, OutputQualification.earliestQualifyingIndex(observations, 0));
    }

    @Test
    public void intentionallyWhiteDocumentQualifiesOnlyWhenWhiteIsExpected() {
        OutputQualification.Observation whiteDocument = OutputQualification.observe(
                500, 500, WIDTH, HEIGHT, 1, 1, 0L, WHITE, WIDTH, HEIGHT, WHITE);
        assertTrue("an intentionally white document is valid", whiteDocument.qualified);
        // The cream fixture keeps its unchanged content/spatial check: white must not satisfy it.
        assertFalse(OutputQualification.nearColor(WHITE, CREAM));
        OutputQualification.Observation creamAsWhite = OutputQualification.observe(
                500, 500, WIDTH, HEIGHT, 1, 1, 0L, CREAM, WIDTH, HEIGHT, WHITE);
        assertFalse("the expectation decides validity, not a white heuristic",
                creamAsWhite.qualified);
    }
}
