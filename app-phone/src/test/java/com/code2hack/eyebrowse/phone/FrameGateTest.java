package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Deterministic admission regressions for the frame delivery gate (F1): delayed callbacks from a
 * superseded lease, replacement within one hosting generation, Stop/restart across generations,
 * and gate closure must all fence exactly as the ownership model requires.
 */
public class FrameGateTest {

    @Test
    public void delayedCallbackFromReleasedLeaseIsRejectedAfterReplacement() {
        FrameGate gate = new FrameGate();
        Object leaseA = new Object();
        Object leaseB = new Object();
        gate.open(leaseA, 5);
        assertTrue(gate.admit(leaseA, 5));

        // A released, B acquired in the SAME hosting generation; A's callback arrives late.
        gate.close();
        gate.open(leaseB, 5);
        assertFalse("A's delayed frame must not reach B's consumer", gate.admit(leaseA, 5));
        assertTrue(gate.admit(leaseB, 5));
    }

    @Test
    public void stopClosesTheGateAndRestartReopensForTheNewGeneration() {
        FrameGate gate = new FrameGate();
        Object leaseA = new Object();
        gate.open(leaseA, 3);
        gate.close(); // Stop: no further admissions.
        assertFalse(gate.admit(leaseA, 3));
        assertFalse("closed gate admits nothing, even the current token", gate.isOpen());

        Object leaseB = new Object();
        gate.open(leaseB, 4); // Restart: new hosting generation.
        assertFalse("old generation's frame is fenced after restart", gate.admit(leaseA, 3));
        assertFalse(gate.admit(leaseB, 3)); // Right token, stale frame generation.
        assertTrue(gate.admit(leaseB, 4));
    }

    @Test
    public void revokedGateBlocksNewAdmissionsButAlreadyAdmittedFramesAreTheConsumersOwn() {
        FrameGate gate = new FrameGate();
        Object lease = new Object();
        gate.open(lease, 1);
        assertTrue(gate.admit(lease, 1)); // Admission decided; delivery may complete in flight.
        gate.close(); // Revocation prevents NEW admissions only.
        assertFalse(gate.admit(lease, 1));
    }

    @Test
    public void reopeningForTheSameTokenAfterReplacementFencesOldFrames() {
        FrameGate gate = new FrameGate();
        Object first = new Object();
        gate.open(first, 2);
        gate.close();
        gate.open(first, 2); // Same identity re-acquired as a new lease instance is a NEW token.
        Object second = new Object();
        gate.open(second, 2);
        assertFalse(gate.admit(first, 2));
        assertTrue(gate.admit(second, 2));
    }
}
