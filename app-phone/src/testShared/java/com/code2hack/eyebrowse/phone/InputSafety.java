package com.code2hack.eyebrowse.phone;

/**
 * Test-only input-safety decision for one synthetic gesture. Unknown IME/geometry is never treated
 * as safe: a missing window-insets object is UNKNOWN, not hidden, and an unavailable target/display
 * fact stops the action before dispatch. Pure JVM logic shared by the JVM regressions and the
 * instrumented tap/swipe paths; never part of the app APK.
 */
final class InputSafety {

    private InputSafety() {}

    /** Tri-state IME visibility: a missing insets object is UNKNOWN, not hidden. */
    enum ImeState {
        VISIBLE,
        HIDDEN,
        UNKNOWN
    }

    static ImeState imeState(boolean insetsAvailable, boolean imeVisible) {
        if (!insetsAvailable) {
            return ImeState.UNKNOWN;
        }
        return imeVisible ? ImeState.VISIBLE : ImeState.HIDDEN;
    }

    /**
     * Null when the dispatch is safe; otherwise the first distinct reason to stop before acting.
     * {@code pointOverlapsIme} is evaluated by the caller for the intended gesture point (swipe
     * callers check the risk-relevant endpoint).
     */
    static String unsafeReason(boolean windowFocus, boolean targetAttached, boolean targetShown,
            boolean visibleBoundsNonEmpty, boolean pointInsideVisibleBounds, boolean displayKnown,
            ImeState imeState, boolean pointOverlapsIme) {
        if (!windowFocus) {
            return "no window focus";
        }
        if (!targetAttached) {
            return "target WebView not attached";
        }
        if (!targetShown) {
            return "target WebView not shown";
        }
        if (!visibleBoundsNonEmpty) {
            return "target WebView visible bounds empty";
        }
        if (!pointInsideVisibleBounds) {
            return "intended point outside target visible bounds";
        }
        if (!displayKnown) {
            return "display metadata unknown";
        }
        if (imeState == ImeState.UNKNOWN) {
            return "IME state unknown (window insets unavailable)";
        }
        if (pointOverlapsIme) {
            return "IME overlaps the intended gesture";
        }
        return null;
    }
}
