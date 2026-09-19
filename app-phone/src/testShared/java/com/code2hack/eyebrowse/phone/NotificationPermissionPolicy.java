package com.code2hack.eyebrowse.phone;

/**
 * Test-only policy for the POST_NOTIFICATIONS runtime-permission setup used by the hosting
 * instrumentation. POST_NOTIFICATIONS is a runtime permission only from API 33; below that it is
 * explicitly NOT_APPLICABLE — no check, no grant, no false GRANTED claim and no restoration
 * obligation. On API 33+ setup records the known original state and tracks whether a grant was
 * actually attempted, verified as changed, or left failed/uncertain; an uncertain outcome is
 * reported as such, never silently treated as unchanged. Pure JVM logic shared by the JVM
 * regressions and the instrumented test (the same helper the test uses, not a parallel
 * classifier). Never part of the app APK.
 */
final class NotificationPermissionPolicy {

    private NotificationPermissionPolicy() {}

    /** First API level where POST_NOTIFICATIONS is a runtime permission. */
    static final int RUNTIME_PERMISSION_API = 33;

    /** What this test class's permission setup established, with no silent unknowns. */
    enum SetupOutcome {
        /** Setup has not run yet. */
        NOT_RUN,
        /** API<33: no runtime permission exists; no check or grant was performed. */
        NOT_APPLICABLE,
        /** The permission was already granted before setup; this test changed nothing. */
        PRE_GRANTED,
        /** A not-granted before-state was changed by a grant verified afterwards. */
        GRANT_VERIFIED,
        /** A grant was attempted but not verified: failed or uncertain, never assumed unchanged. */
        GRANT_FAILED_OR_UNCERTAIN
    }

    /** True when the runtime permission mechanism exists on this API level. */
    static boolean applicable(int sdkInt) {
        return sdkInt >= RUNTIME_PERMISSION_API;
    }

    /** True when setup must attempt the verified grant (applicable and not already granted). */
    static boolean needsGrant(int sdkInt, boolean granted) {
        return applicable(sdkInt) && !granted;
    }

    /** True when an attempted setup did not verifiably take effect. */
    static boolean setupFailed(int sdkInt, boolean grantAttempted, boolean grantedAfter) {
        return applicable(sdkInt) && grantAttempted && !grantedAfter;
    }

    /**
     * Restoration is required only for an actual verified mutation made by this test class.
     * Below API 33, before setup ran, a pre-granted permission and a failed/uncertain attempt all
     * restore nothing; the uncertain case is reported separately, never assumed unchanged.
     */
    static boolean needsRestore(int sdkInt, SetupOutcome outcome) {
        return applicable(sdkInt) && outcome == SetupOutcome.GRANT_VERIFIED;
    }

    /** True when the outcome must be reported as uncertain rather than assumed unchanged. */
    static boolean isUncertain(SetupOutcome outcome) {
        return outcome == SetupOutcome.GRANT_FAILED_OR_UNCERTAIN;
    }
}
