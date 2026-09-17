package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/** Regressions for the exact final-admission helper used by Android tap/swipe dispatch. */
public class DispatchReadinessTest {
    private static final Object ACTIVITY = new Object();
    private static final Object TARGET = new Object();
    private static final String URL = "http://127.0.0.1:25341/basic.html";

    private static InputSafety.State nativeState() {
        return nativeState(ACTIVITY, TARGET, 0, 10, 980,
                new InputSafety.Bounds(10, 100, 990, 900));
    }

    private static InputSafety.State nativeState(Object activity, Object target, int display,
            int x, int width, InputSafety.Bounds visible) {
        return new InputSafety.State(activity, target, true, true, true, true, false,
                display, x, 100, width, 800, 0, 0,
                new InputSafety.Bounds(0, 0, 1100, 1000), visible,
                InputSafety.ImeState.HIDDEN, 0, "bars=0,0,0,10");
    }

    private static InputSafety.Path point() {
        return new InputSafety.Path(500, 500, 500, 500);
    }

    private static DispatchReadiness.DomState dom(String marker, String geometry) {
        return new DispatchReadiness.DomState(marker, URL, "click-button", geometry);
    }

    @Test public void documentOnlyChangeOnUsedActionPathDispatchesZeroInput() {
        HarnessProtocol.Dispatch dispatch = new HarnessProtocol.Dispatch();
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                DispatchReadiness.actionOnceIfRecomputedReady(nativeState(), nativeState(), point(),
                        dom("L1", "x=100;y=200;w=800;h=600"),
                        dom("L2", "x=100;y=200;w=800;h=600"),
                        dispatch, calls::incrementAndGet, () -> 1L));
        assertEquals("document changed after preparation", failure.getMessage());
        assertEquals("not-attempted", dispatch.stage);
        assertEquals(0, calls.get());
    }

    @Test public void targetGeometryOnlyChangeOnUsedTapPathDispatchesZeroInput() {
        HarnessProtocol.Dispatch dispatch = new HarnessProtocol.Dispatch();
        AtomicInteger down = new AtomicInteger();
        AtomicInteger up = new AtomicInteger();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                DispatchReadiness.tapOnceIfRecomputedReady(nativeState(), nativeState(), point(),
                        dom("L1", "x=100;y=200;w=800;h=600"),
                        dom("L1", "x=101;y=200;w=800;h=600"),
                        dispatch, down::incrementAndGet, up::incrementAndGet, () -> 1L));
        assertEquals("DOM target/viewport changed after preparation", failure.getMessage());
        assertEquals("not-attempted", dispatch.stage);
        assertEquals(0, down.get());
        assertEquals(0, up.get());
    }

    @Test public void unchangedFinalDomAndNativeStatePermitsOneSingleShotTap() {
        HarnessProtocol.Dispatch dispatch = new HarnessProtocol.Dispatch();
        AtomicInteger down = new AtomicInteger();
        AtomicInteger up = new AtomicInteger();
        DispatchReadiness.DomState prepared = dom("L1", "x=100;y=200;w=800;h=600");
        DispatchReadiness.DomState current = dom("L1", "x=100;y=200;w=800;h=600");
        DispatchReadiness.tapOnceIfRecomputedReady(nativeState(), nativeState(), point(),
                prepared, current, dispatch, down::incrementAndGet, up::incrementAndGet, () -> 1L);
        assertEquals("returned", dispatch.stage);
        assertEquals(1, down.get());
        assertEquals(1, up.get());
    }

    @Test public void finalMappingDriftPermitsFreshlyRecomputedPath() {
        InputSafety.State preparedNative = nativeState();
        InputSafety.State currentNative = nativeState(ACTIVITY, TARGET, 0, 20, 970,
                new InputSafety.Bounds(20, 100, 990, 900));
        InputSafety.Path currentPoint = new InputSafety.Path(510, 500, 510, 500);
        HarnessProtocol.Dispatch dispatch = new HarnessProtocol.Dispatch();
        AtomicInteger down = new AtomicInteger();
        AtomicInteger up = new AtomicInteger();
        DispatchReadiness.tapOnceIfRecomputedReady(preparedNative, currentNative, currentPoint,
                dom("L1", "x=100;y=200;w=800;h=600"),
                dom("L1", "x=100;y=200;w=800;h=600"),
                dispatch, down::incrementAndGet, up::incrementAndGet, () -> 1L);
        assertEquals("returned", dispatch.stage);
        assertEquals(1, down.get());
        assertEquals(1, up.get());
    }

    @Test public void ownerChangeAfterRecomputeStillDispatchesZeroInput() {
        HarnessProtocol.Dispatch dispatch = new HarnessProtocol.Dispatch();
        AtomicInteger calls = new AtomicInteger();
        InputSafety.State current = nativeState(new Object(), TARGET, 0, 10, 980,
                new InputSafety.Bounds(10, 100, 990, 900));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                DispatchReadiness.actionOnceIfRecomputedReady(nativeState(), current, point(),
                        dom("L1", "x=100;y=200;w=800;h=600"),
                        dom("L1", "x=100;y=200;w=800;h=600"),
                        dispatch, calls::incrementAndGet, () -> 1L));
        assertEquals("input owner changed after preparation", failure.getMessage());
        assertEquals("not-attempted", dispatch.stage);
        assertEquals(0, calls.get());
    }

    @Test public void displayChangeAfterRecomputeStillDispatchesZeroInput() {
        HarnessProtocol.Dispatch dispatch = new HarnessProtocol.Dispatch();
        AtomicInteger calls = new AtomicInteger();
        InputSafety.State current = nativeState(ACTIVITY, TARGET, 1, 10, 980,
                new InputSafety.Bounds(10, 100, 990, 900));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                DispatchReadiness.actionOnceIfRecomputedReady(nativeState(), current, point(),
                        dom("L1", "x=100;y=200;w=800;h=600"),
                        dom("L1", "x=100;y=200;w=800;h=600"),
                        dispatch, calls::incrementAndGet, () -> 1L));
        assertEquals("input display changed after preparation", failure.getMessage());
        assertEquals("not-attempted", dispatch.stage);
        assertEquals(0, calls.get());
    }
}
