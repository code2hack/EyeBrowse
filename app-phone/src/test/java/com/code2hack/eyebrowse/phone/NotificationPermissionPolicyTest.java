package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JVM regressions for the test-only POST_NOTIFICATIONS setup policy, driving the same
 * {@link NotificationPermissionPolicy} the instrumented setup/cleanup uses: API<33 explicit
 * NOT_APPLICABLE with no permission calls or restoration obligation, API33+ pre-granted,
 * changed-grant and failed-setup semantics.
 */
public class NotificationPermissionPolicyTest {

    @Test
    public void belowApi33IsNotApplicableAndCreatesNoObligation() {
        assertFalse(NotificationPermissionPolicy.applicable(31));
        assertFalse("no runtime grant is attempted below 33",
                NotificationPermissionPolicy.needsGrant(31, false));
        assertFalse("a default false grant must not create a restoration obligation",
                NotificationPermissionPolicy.needsRestore(31, true, false));
        assertFalse("no attempted grant below 33 can report a failed setup",
                NotificationPermissionPolicy.setupFailed(31, true, false));
    }

    @Test
    public void api33PreGrantedNeedsNoGrantAndNoRestore() {
        assertTrue(NotificationPermissionPolicy.applicable(33));
        assertFalse(NotificationPermissionPolicy.needsGrant(33, true));
        assertFalse("a pre-granted permission is not this test's mutation to restore",
                NotificationPermissionPolicy.needsRestore(33, true, true));
        assertFalse(NotificationPermissionPolicy.setupFailed(33, false, true));
    }

    @Test
    public void api33ChangedGrantNeedsVerifiedGrantAndRestore() {
        assertTrue(NotificationPermissionPolicy.needsGrant(33, false));
        assertTrue(NotificationPermissionPolicy.needsRestore(33, true, false));
        assertFalse("a verified grant after an attempted change is never a failed setup",
                NotificationPermissionPolicy.setupFailed(33, true, true));
    }

    @Test
    public void api33FailedSetupIsReportedNotTreatedAsDeniedOrSuccess() {
        assertTrue(NotificationPermissionPolicy.setupFailed(33, true, false));
        assertFalse("no restore without recorded setup",
                NotificationPermissionPolicy.needsRestore(33, false, false));
    }
}
