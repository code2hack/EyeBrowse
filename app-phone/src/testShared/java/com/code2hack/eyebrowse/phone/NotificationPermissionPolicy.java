package com.code2hack.eyebrowse.phone;

/**
 * Test-only policy for the POST_NOTIFICATIONS runtime-permission setup used by the hosting
 * instrumentation. POST_NOTIFICATIONS is a runtime permission only from API 33; below that it is
 * explicitly NOT_APPLICABLE — no check, no grant, no false GRANTED claim and no restoration
 * obligation. Pure JVM logic shared by the JVM regressions and the instrumented test (the same
 * helper the test uses, not a parallel classifier). Never part of the app APK.
 */
final class NotificationPermissionPolicy {

    private NotificationPermissionPolicy() {}

    /** First API level where POST_NOTIFICATIONS is a runtime permission. */
    static final int RUNTIME_PERMISSION_API = 33;

    /** True when the runtime permission mechanism exists on this API level. */
    static boolean applicable(int sdkInt) {
        return sdkInt >= RUNTIME_PERMISSION_API;
    }

    /** True when setup must attempt the verified grant (applicable and not already granted). */
    static boolean needsGrant(int sdkInt, boolean granted) {
        return applicable(sdkInt) && !granted;
    }

    /**
     * Restoration is required only when the permission is applicable, setup recorded the
     * before-state, and setup changed a not-granted state. Below API 33 (or before setup ran)
     * there is nothing to restore; a default false grant flag must not create an obligation.
     */
    static boolean needsRestore(int sdkInt, boolean setupRecorded, boolean grantedBefore) {
        return applicable(sdkInt) && setupRecorded && !grantedBefore;
    }

    /**
     * True when an attempted setup did not verifiably take effect: that must be reported, never
     * silently treated as denied or success. Below API 33 no grant is attempted.
     */
    static boolean setupFailed(int sdkInt, boolean grantAttempted, boolean grantedAfter) {
        return applicable(sdkInt) && grantAttempted && !grantedAfter;
    }
}
