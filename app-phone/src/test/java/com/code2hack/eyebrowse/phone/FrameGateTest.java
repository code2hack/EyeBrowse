package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Deterministic admission regressions for the frame delivery gate (F1) plus its expiry authority
 * (R4): delayed callbacks from a superseded lease, replacement within one hosting generation,
 * Stop/restart across generations, gate closure, and — independently of any later watchdog or
 * cleanup tick — rejection of delivery and renewal after the authoritative 5 s deadline, with
 * late renewal unable to revive an expired lease.
 */
public class FrameGateTest {

    private static final long TTL = HostingPolicy.LEASE_TTL_MS;

    @Test
    public void delayedCallbackFromReleasedLeaseIsRejectedAfterReplacement() {
        FrameGate gate = new FrameGate();
        Object leaseA = new Object();
        Object leaseB = new Object();
        gate.open(leaseA, 5, 1_000 + TTL);
        assertTrue(gate.admit(leaseA, 5, 1_000));

        // A released, B acquired in the SAME hosting generation; A's callback arrives late.
        gate.close();
        gate.open(leaseB, 5, 2_000 + TTL);
        assertFalse("A's delayed frame must not reach B's consumer", gate.admit(leaseA, 5, 2_000));
        assertTrue(gate.admit(leaseB, 5, 2_000));
    }

    @Test
    public void stopClosesTheGateAndRestartReopensForTheNewGeneration() {
        FrameGate gate = new FrameGate();
        Object leaseA = new Object();
        gate.open(leaseA, 3, 1_000 + TTL);
        gate.close(); // Stop: no further admissions.
        assertFalse(gate.admit(leaseA, 3, 1_100));
        assertFalse("closed gate admits nothing, even the current token", gate.isOpen());

        Object leaseB = new Object();
        gate.open(leaseB, 4, 3_000 + TTL); // Restart: new hosting generation.
        assertFalse("old generation's frame is fenced after restart", gate.admit(leaseA, 3, 3_100));
        assertFalse(gate.admit(leaseB, 3, 3_100)); // Right token, stale frame generation.
        assertTrue(gate.admit(leaseB, 4, 3_100));
    }

    @Test
    public void revokedGateBlocksNewAdmissionsButAlreadyAdmittedFramesAreTheConsumersOwn() {
        FrameGate gate = new FrameGate();
        Object lease = new Object();
        gate.open(lease, 1, 500 + TTL);
        assertTrue(gate.admit(lease, 1, 600)); // Admission decided; delivery may complete in flight.
        gate.close(); // Revocation prevents NEW admissions only.
        assertFalse(gate.admit(lease, 1, 700));
    }

    @Test
    public void reopeningForTheSameTokenAfterReplacementFencesOldFrames() {
        FrameGate gate = new FrameGate();
        Object first = new Object();
        gate.open(first, 2, 1_000 + TTL);
        gate.close();
        gate.open(first, 2, 2_000 + TTL); // Same identity re-acquired as a new lease is a NEW token.
        Object second = new Object();
        gate.open(second, 2, 2_000 + TTL);
        assertFalse(gate.admit(first, 2, 2_100));
        assertTrue(gate.admit(second, 2, 2_100));
    }

    // ------------------------------------------------- expiry authority (R4)

    @Test
    public void authorityExpiresAtTheDeadlineInstantIndependentlyOfAnyWatchdogTick() {
        FrameGate gate = new FrameGate();
        Object lease = new Object();
        long openAt = 10_000;
        gate.open(lease, 1, openAt + TTL);
        assertTrue(gate.admit(lease, 1, openAt + TTL - 1)); // Last instant of authority.
        assertFalse("authority expires AT the 5 s deadline instant", gate.admit(lease, 1, openAt + TTL));
        assertFalse(gate.admit(lease, 1, openAt + TTL + 400)); // Well before a 500 ms watchdog.
    }

    @Test
    public void renewalBeforeTheDeadlineIsAcceptedAndMovesTheDeadline() {
        FrameGate gate = new FrameGate();
        Object lease = new Object();
        long openAt = 10_000;
        gate.open(lease, 1, openAt + TTL);
        long renewalAt = openAt + 3_000; // Successful main-thread renewal inside the TTL.
        assertTrue(gate.renew(renewalAt, renewalAt + TTL));
        assertEquals(renewalAt + TTL, gate.acceptUntilElapsedMs());
        assertTrue(gate.admit(lease, 1, renewalAt + TTL - 1)); // Last instant of the new authority.
        assertFalse("the renewed deadline also expires AT its instant",
                gate.admit(lease, 1, renewalAt + TTL));
    }

    @Test
    public void renewalAfterExpiryIsRejectedAndCannotReviveTheLease() {
        FrameGate gate = new FrameGate();
        Object lease = new Object();
        long openAt = 10_000;
        gate.open(lease, 1, openAt + TTL);
        long lateRenewalAt = openAt + TTL; // AT the deadline, before any watchdog tick.
        assertFalse("renewal at the exact expiry instant is already rejected",
                gate.renew(lateRenewalAt, lateRenewalAt + TTL));
        lateRenewalAt = openAt + TTL + 50; // Inside the TTL-to-watchdog interval.
        assertFalse("an expired lease cannot resurrect its delivery authority",
                gate.renew(lateRenewalAt, lateRenewalAt + TTL));
        assertEquals("a rejected renewal moves no deadline", openAt + TTL,
                gate.acceptUntilElapsedMs());
        assertTrue("the gate object stays open; expiry is enforced at admission/renewal",
                gate.isOpen());
        assertFalse(gate.admit(lease, 1, lateRenewalAt)); // Past-deadline admission rejected.
    }

    @Test
    public void renewalOnAClosedGateIsRejected() {
        FrameGate gate = new FrameGate();
        Object lease = new Object();
        gate.open(lease, 1, 1_000 + TTL);
        gate.close();
        assertFalse(gate.renew(1_500, 1_500 + TTL));
        assertFalse(gate.isOpen());
    }
}
