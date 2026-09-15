package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.code2hack.eyebrowse.phone.InputSafety.ImeState;

/**
 * JVM regressions for the input-safety gate used by the instrumented tap/swipe paths: unknown
 * IME/geometry is unsafe, and each unavailable fact has its own distinct stop reason.
 */
public class InputSafetyTest {

    private static String reason(boolean focus, boolean attached, boolean shown, boolean bounds,
            boolean inside, boolean display, ImeState ime, boolean overlaps) {
        return InputSafety.unsafeReason(focus, attached, shown, bounds, inside, display, ime,
                overlaps);
    }

    @Test
    public void safeGeometryWithHiddenImeDispatches() {
        assertNull(reason(true, true, true, true, true, true, ImeState.HIDDEN, false));
    }

    @Test
    public void missingInsetsIsUnknownAndUnsafeNotHidden() {
        assertEquals(ImeState.UNKNOWN, InputSafety.imeState(false, false));
        assertEquals("IME state unknown (window insets unavailable)",
                reason(true, true, true, true, true, true, ImeState.UNKNOWN, false));
    }

    @Test
    public void unavailableOwnershipOrGeometryStopsWithDistinctReasons() {
        assertEquals("no window focus",
                reason(false, true, true, true, true, true, ImeState.HIDDEN, false));
        assertEquals("target WebView not attached",
                reason(true, false, true, true, true, true, ImeState.HIDDEN, false));
        assertEquals("target WebView not shown",
                reason(true, true, false, true, true, true, ImeState.HIDDEN, false));
        assertEquals("target WebView visible bounds empty",
                reason(true, true, true, false, false, true, ImeState.HIDDEN, false));
        assertEquals("intended point outside target visible bounds",
                reason(true, true, true, true, false, true, ImeState.HIDDEN, false));
        assertEquals("display metadata unknown",
                reason(true, true, true, true, true, false, ImeState.HIDDEN, false));
    }

    @Test
    public void visibleImeOverlappingThePointStops() {
        assertEquals(ImeState.VISIBLE, InputSafety.imeState(true, true));
        assertEquals("IME overlaps the intended gesture",
                reason(true, true, true, true, true, true, ImeState.VISIBLE, true));
        assertNull("a visible IME elsewhere does not block the point",
                reason(true, true, true, true, true, true, ImeState.VISIBLE, false));
    }
}
