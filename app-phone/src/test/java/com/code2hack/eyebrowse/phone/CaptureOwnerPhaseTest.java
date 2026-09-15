package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Deterministic regressions for the capture-owner phase machine (R2): ACTIVE → RETIRING →
 * QUIESCENT is real and observable; no replacement capture may begin while a previous owner is
 * still retiring; and the transitions reject out-of-order lifecycle steps.
 */
public class CaptureOwnerPhaseTest {

    @Test
    public void freshOwnerStartsActiveFromIdle() {
        CaptureOwnerPhase phase = new CaptureOwnerPhase();
        assertTrue(phase.isQuiescent());
        assertTrue(phase.beginActive());
        assertTrue(phase.isActive());
        assertFalse(phase.isQuiescent());
    }

    @Test
    public void retirementIsObservableAndBlocksReplacementCaptureUntilQuiescent() {
        CaptureOwnerPhase phase = new CaptureOwnerPhase();
        phase.beginActive();
        assertTrue(phase.beginRetiring());
        assertTrue(phase.isRetiring());
        assertFalse("no replacement capture may start while the previous owner is retiring",
                phase.beginActive());
        assertTrue(phase.completeRetirement());
        assertTrue(phase.isQuiescent());
        assertTrue("replacement is safe after quiescence", phase.beginActive());
    }

    @Test
    public void outOfOrderTransitionsAreRejected() {
        CaptureOwnerPhase phase = new CaptureOwnerPhase();
        assertFalse("retirement requires an active owner", phase.beginRetiring());
        assertFalse("completion requires a retiring owner", phase.completeRetirement());
        phase.beginActive();
        assertFalse("double start is rejected", phase.beginActive());
        assertTrue(phase.beginRetiring());
        assertFalse("double retirement request is rejected", phase.beginRetiring());
        assertTrue(phase.completeRetirement());
        assertFalse("double completion is rejected", phase.completeRetirement());
    }

    @Test
    public void retirementFromIdleIsRejectedAndQuiescenceIsPreserved() {
        CaptureOwnerPhase phase = new CaptureOwnerPhase();
        assertFalse(phase.beginRetiring());
        assertTrue(phase.isQuiescent());
    }
}
