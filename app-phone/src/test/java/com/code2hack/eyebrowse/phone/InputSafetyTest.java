package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    private static final class Window {
        Object activity = new Object(), target = new Object();
        boolean focus = true, decor = true, attached = true, shown = true, targetFocus = false;
        int display = 0, x = 10, y = 100, width = 980, height = 800, scrollY = 0, imeBottom = 0;
        InputSafety.Bounds root = new InputSafety.Bounds(0, 0, 1000, 1000);
        InputSafety.Bounds visible = new InputSafety.Bounds(10, 100, 990, 900);
        ImeState ime = ImeState.HIDDEN;
        String insets = "bars=0,0,0,10";

        InputSafety.State snapshot() {
            return new InputSafety.State(activity, target, focus, decor, attached, shown, targetFocus,
                    display, x, y, width, height, 0, scrollY, root, visible, ime, imeBottom, insets);
        }
    }

    private static InputSafety.Path swipe() {
        // Espresso 3.6.1's calculated bottom-center - 0.083*height to top-center, fed into
        // the very same full-path guard as instrumentation (no manually supplied inside=true).
        return new InputSafety.Path(500, 900 - 0.083f * 800, 500, 100);
    }

    @Test public void espressoCalculatedPathIsInsideButExclusiveExtentIsNot() {
        Window window = new Window();
        assertNull(window.snapshot().unsafeReason(swipe()));
        InputSafety.Path oldExtent = new InputSafety.Path(500, 900, 500, 100);
        assertFalse(oldExtent.inside(window.visible));
        assertEquals("intended point outside target visible bounds", window.snapshot().unsafeReason(oldExtent));
    }

    @Test public void fullPathRejectsEitherClippedEndpointEmptyBoundsAndNonfinitePoints() {
        Window window = new Window();
        window.visible = new InputSafety.Bounds(10, 101, 990, 900); // end clipped, start still safe
        assertEquals("intended point outside target visible bounds", window.snapshot().unsafeReason(swipe()));
        window.visible = new InputSafety.Bounds(10, 100, 990, 830); // start clipped
        assertEquals("intended point outside target visible bounds", window.snapshot().unsafeReason(swipe()));
        window.visible = new InputSafety.Bounds(10, 100, 10, 900);
        assertEquals("target WebView visible bounds empty", window.snapshot().unsafeReason(swipe()));
        InputSafety.Bounds bounds = new InputSafety.Bounds(0, 0, 10, 10);
        assertFalse(new InputSafety.Path(-0.1f, 1, 2, 2).inside(bounds)); // no int truncation
        assertFalse(new InputSafety.Path(1, 1, 10, 2).inside(bounds));
        assertFalse(new InputSafety.Path(Float.NaN, 1, 2, 2).inside(bounds));
        assertFalse(new InputSafety.Path(1, 1, 2, Float.POSITIVE_INFINITY).inside(bounds));
        assertTrue(new InputSafety.Path(0, 0, 9.9f, 9.9f).inside(bounds));
    }

    @Test public void entireGestureMustAvoidImeAndUnknownFloatingImeGeometry() {
        Window window = new Window();
        window.ime = ImeState.VISIBLE;
        window.imeBottom = 200;
        assertEquals("IME overlaps the intended gesture", window.snapshot().unsafeReason(swipe()));
        assertNull(window.snapshot().unsafeReason(new InputSafety.Path(500, 700, 500, 100)));
        assertEquals("IME overlaps the intended gesture", window.snapshot().unsafeReason(
                new InputSafety.Path(500, 100, 500, 850))); // other endpoint, not only start
        window.imeBottom = 0;
        assertEquals("IME overlaps the intended gesture", window.snapshot().unsafeReason(swipe()));
    }

    @Test public void focusReturningDoesNotRefreshCoordinatesOrOwnership() {
        Window window = new Window();
        InputSafety.State prepared = window.snapshot();
        window.focus = false;
        assertEquals("no window focus", prepared.revalidationReason(window.snapshot(), swipe()));
        window.focus = true;
        window.x += 1; // wait brought focus back, but coordinates moved
        assertEquals("input context changed after preparation", prepared.revalidationReason(window.snapshot(), swipe()));
        window.x -= 1;
        window.target = new Object();
        assertEquals("input context changed after preparation", prepared.revalidationReason(window.snapshot(), swipe()));
    }

    @Test public void everyReadinessMappingChangeAfterSlowObservationStopsDispatch() {
        for (int change = 0; change < 11; change++) {
            Window window = new Window();
            InputSafety.State before = window.snapshot();
            switch (change) {
                case 0: window.activity = new Object(); break;
                case 1: window.width--; break;
                case 2: window.y++; break;
                case 3: window.scrollY++; break;
                case 4: window.display++; break;
                case 5: window.insets = "bars=0,0,0,20"; break;
                case 6: window.targetFocus = true; break;
                case 7: window.visible = new InputSafety.Bounds(11, 100, 990, 900); break;
                case 8: window.attached = false; break;
                case 9: window.shown = false; break;
                case 10: window.ime = ImeState.UNKNOWN; break;
                default: throw new AssertionError();
            }
            assertTrue("changed readiness must reject case " + change,
                    before.revalidationReason(window.snapshot(), swipe()) != null);
        }
        Window stable = new Window();
        assertNull(stable.snapshot().revalidationReason(stable.snapshot(), swipe()));
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
