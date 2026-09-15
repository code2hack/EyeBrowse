package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.code2hack.eyebrowse.phone.NotificationPermissionPolicy.SetupOutcome;

/**
 * JVM regressions for the test-only POST_NOTIFICATIONS setup policy, driving the same
 * {@link NotificationPermissionPolicy} the instrumented setup/cleanup uses: API<33 explicit
 * NOT_APPLICABLE with no permission calls or restoration obligation; API33+ pre-granted;
 * known-denied with a failed/unchanged grant (no verified mutation, uncertainty reported, not
 * silently assumed unchanged); a true verified mutation; and not-yet-run setup.
 */
public class NotificationPermissionPolicyTest {

    @Test
    public void belowApi33IsNotApplicableAndCreatesNoObligationOrCalls() {
        assertFalse(NotificationPermissionPolicy.applicable(31));
        assertFalse("no runtime grant is attempted below 33",
                NotificationPermissionPolicy.needsGrant(31, false));
        assertFalse("no attempted grant below 33 can report a failed setup",
                NotificationPermissionPolicy.setupFailed(31, true, false));
        assertFalse("no outcome is restorable below 33",
                NotificationPermissionPolicy.needsRestore(31, SetupOutcome.GRANT_VERIFIED));
        assertFalse(NotificationPermissionPolicy.needsRestore(31,
                SetupOutcome.GRANT_FAILED_OR_UNCERTAIN));
    }

    @Test
    public void api33PreGrantedRestoresNothing() {
        assertTrue(NotificationPermissionPolicy.applicable(33));
        assertFalse(NotificationPermissionPolicy.needsGrant(33, true));
        assertFalse("a pre-granted permission is not this test's mutation to restore",
                NotificationPermissionPolicy.needsRestore(33, SetupOutcome.PRE_GRANTED));
        assertFalse(NotificationPermissionPolicy.isUncertain(SetupOutcome.PRE_GRANTED));
    }

    @Test
    public void api33KnownDeniedFailedOrUnchangedGrantIsNotAVerifiedMutation() {
        assertTrue(NotificationPermissionPolicy.needsGrant(33, false));
        assertTrue("an attempted grant that did not take effect is a failed setup",
                NotificationPermissionPolicy.setupFailed(33, true, false));
        assertFalse("without a verified change there is nothing this test may restore",
                NotificationPermissionPolicy.needsRestore(33,
                        SetupOutcome.GRANT_FAILED_OR_UNCERTAIN));
        assertTrue("the uncertain case must be reported, never assumed unchanged",
                NotificationPermissionPolicy.isUncertain(SetupOutcome.GRANT_FAILED_OR_UNCERTAIN));
        assertFalse("unknown state is not treated as a verified change",
                NotificationPermissionPolicy.needsRestore(33, SetupOutcome.NOT_RUN));
    }

    @Test
    public void api33VerifiedMutationIsTheOnlyRestorableOutcome() {
        assertFalse(NotificationPermissionPolicy.setupFailed(33, true, true));
        assertTrue(NotificationPermissionPolicy.needsRestore(33, SetupOutcome.GRANT_VERIFIED));
        assertFalse(NotificationPermissionPolicy.isUncertain(SetupOutcome.GRANT_VERIFIED));
    }
}
