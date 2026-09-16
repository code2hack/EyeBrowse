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
        return new InputSafety.State(ACTIVITY, TARGET, true, true, true, true, false,
                0, 10, 100, 980, 800, 0, 0,
                new InputSafety.Bounds(0, 0, 1000, 1000),
                new InputSafety.Bounds(10, 100, 990, 900),
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
                DispatchReadiness.actionOnceIfReady(nativeState(), nativeState(), point(),
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
                DispatchReadiness.tapOnceIfReady(nativeState(), nativeState(), point(),
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
        DispatchReadiness.tapOnceIfReady(nativeState(), nativeState(), point(), prepared, current,
                dispatch, down::incrementAndGet, up::incrementAndGet, () -> 1L);
        assertEquals("returned", dispatch.stage);
        assertEquals(1, down.get());
        assertEquals(1, up.get());
    }
}
