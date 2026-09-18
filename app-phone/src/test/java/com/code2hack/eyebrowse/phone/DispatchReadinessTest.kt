package com.code2hack.eyebrowse.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.LongSupplier

/** Regressions for the exact final-admission helper used by Android tap/swipe dispatch. */
class DispatchReadinessTest {
    private fun nativeState(): InputSafety.State =
        nativeState(
            ACTIVITY,
            TARGET,
            0,
            10,
            980,
            InputSafety.Bounds(10, 100, 990, 900),
        )

    private fun nativeState(
        activity: Any,
        target: Any,
        display: Int,
        x: Int,
        width: Int,
        visible: InputSafety.Bounds,
    ): InputSafety.State =
        InputSafety.State(
            activity,
            target,
            true,
            true,
            true,
            true,
            false,
            display,
            x,
            100,
            width,
            800,
            0,
            0,
            InputSafety.Bounds(0, 0, 1100, 1000),
            visible,
            InputSafety.ImeState.HIDDEN,
            0,
            "bars=0,0,0,10",
        )

    private fun point(): InputSafety.Path = InputSafety.Path(500f, 500f, 500f, 500f)

    private fun dom(marker: String, geometry: String): DispatchReadiness.DomState =
        DispatchReadiness.DomState(marker, URL, "click-button", geometry)

    @Test
    fun documentOnlyChangeOnUsedActionPathDispatchesZeroInput() {
        val dispatch = HarnessProtocol.Dispatch()
        val calls = AtomicInteger()
        val failure = assertThrows(IllegalStateException::class.java) {
            DispatchReadiness.actionOnceIfRecomputedReady(
                nativeState(),
                nativeState(),
                point(),
                dom("L1", "x=100;y=200;w=800;h=600"),
                dom("L2", "x=100;y=200;w=800;h=600"),
                dispatch,
                Runnable { calls.incrementAndGet() },
                LongSupplier { 1L },
            )
        }
        assertEquals("document changed after preparation", failure.message)
        assertEquals("not-attempted", dispatch.stage)
        assertEquals(0, calls.get())
    }

    @Test
    fun targetGeometryOnlyChangeOnUsedTapPathDispatchesZeroInput() {
        val dispatch = HarnessProtocol.Dispatch()
        val down = AtomicInteger()
        val up = AtomicInteger()
        val failure = assertThrows(IllegalStateException::class.java) {
            DispatchReadiness.tapOnceIfRecomputedReady(
                nativeState(),
                nativeState(),
                point(),
                dom("L1", "x=100;y=200;w=800;h=600"),
                dom("L1", "x=101;y=200;w=800;h=600"),
                dispatch,
                Runnable { down.incrementAndGet() },
                Runnable { up.incrementAndGet() },
                LongSupplier { 1L },
            )
        }
        assertEquals("DOM target/viewport changed after preparation", failure.message)
        assertEquals("not-attempted", dispatch.stage)
        assertEquals(0, down.get())
        assertEquals(0, up.get())
    }

    @Test
    fun unchangedFinalDomAndNativeStatePermitsOneSingleShotTap() {
        val dispatch = HarnessProtocol.Dispatch()
        val down = AtomicInteger()
        val up = AtomicInteger()
        val prepared = dom("L1", "x=100;y=200;w=800;h=600")
        val current = dom("L1", "x=100;y=200;w=800;h=600")
        DispatchReadiness.tapOnceIfRecomputedReady(
            nativeState(),
            nativeState(),
            point(),
            prepared,
            current,
            dispatch,
            Runnable { down.incrementAndGet() },
            Runnable { up.incrementAndGet() },
            LongSupplier { 1L },
        )
        assertEquals("returned", dispatch.stage)
        assertEquals(1, down.get())
        assertEquals(1, up.get())
    }

    private class CheckedInjectionFailure : Exception()

    @Test
    fun checkedCallbackPrimaryCrossesHandoffByIdentityWithStageAndCleanup() {
        val dispatch = HarnessProtocol.Dispatch()
        val handoff = DispatchReadiness.CallbackHandoff()
        val down = AtomicInteger()
        val up = AtomicInteger()
        val dispatchCleanup = AtomicInteger()
        val callbackCleanup = AtomicInteger()
        val primary = CheckedInjectionFailure()
        val evidenceFailure = IllegalStateException("evidence failed")

        val callbackSucceeded: Boolean
        try {
            callbackSucceeded = handoff.capture {
                try {
                    DispatchReadiness.tapOnceIfRecomputedReady(
                        nativeState(),
                        nativeState(),
                        point(),
                        dom("L1", "x=100;y=200;w=800;h=600"),
                        dom("L1", "x=100;y=200;w=800;h=600"),
                        dispatch,
                        Runnable {
                            down.incrementAndGet()
                            throw primary
                        },
                        Runnable { up.incrementAndGet() },
                        LongSupplier { 1L },
                    )
                } finally {
                    dispatchCleanup.incrementAndGet()
                }
            }
        } finally {
            callbackCleanup.incrementAndGet()
        }

        assertFalse(callbackSucceeded)

        val actual = assertThrows(Exception::class.java) {
            handoff.rethrowIfPresent()
        }
        assertSame(primary, actual)
        HarnessProtocol.preserveFailure(actual) {
            throw evidenceFailure
        }

        assertSame("supplementary evidence must not replace the primary", primary, actual)
        assertEquals("down-attempted", dispatch.stage)
        assertEquals(1, down.get())
        assertEquals(0, up.get())
        assertEquals(1, dispatchCleanup.get())
        assertEquals(1, callbackCleanup.get())
        assertEquals(1, actual.suppressed.size)
        assertSame(evidenceFailure, actual.suppressed[0])
    }

    @Test
    fun finalMappingDriftPermitsFreshlyRecomputedPath() {
        val preparedNative = nativeState()
        val currentNative = nativeState(
            ACTIVITY,
            TARGET,
            0,
            20,
            970,
            InputSafety.Bounds(20, 100, 990, 900),
        )
        val currentPoint = InputSafety.Path(510f, 500f, 510f, 500f)
        val dispatch = HarnessProtocol.Dispatch()
        val down = AtomicInteger()
        val up = AtomicInteger()
        DispatchReadiness.tapOnceIfRecomputedReady(
            preparedNative,
            currentNative,
            currentPoint,
            dom("L1", "x=100;y=200;w=800;h=600"),
            dom("L1", "x=100;y=200;w=800;h=600"),
            dispatch,
            Runnable { down.incrementAndGet() },
            Runnable { up.incrementAndGet() },
            LongSupplier { 1L },
        )
        assertEquals("returned", dispatch.stage)
        assertEquals(1, down.get())
        assertEquals(1, up.get())
    }

    @Test
    fun ownerChangeAfterRecomputeStillDispatchesZeroInput() {
        val dispatch = HarnessProtocol.Dispatch()
        val calls = AtomicInteger()
        val current = nativeState(
            Any(),
            TARGET,
            0,
            10,
            980,
            InputSafety.Bounds(10, 100, 990, 900),
        )
        val failure = assertThrows(IllegalStateException::class.java) {
            DispatchReadiness.actionOnceIfRecomputedReady(
                nativeState(),
                current,
                point(),
                dom("L1", "x=100;y=200;w=800;h=600"),
                dom("L1", "x=100;y=200;w=800;h=600"),
                dispatch,
                Runnable { calls.incrementAndGet() },
                LongSupplier { 1L },
            )
        }
        assertEquals("input owner changed after preparation", failure.message)
        assertEquals("not-attempted", dispatch.stage)
        assertEquals(0, calls.get())
    }

    @Test
    fun displayChangeAfterRecomputeStillDispatchesZeroInput() {
        val dispatch = HarnessProtocol.Dispatch()
        val calls = AtomicInteger()
        val current = nativeState(
            ACTIVITY,
            TARGET,
            1,
            10,
            980,
            InputSafety.Bounds(10, 100, 990, 900),
        )
        val failure = assertThrows(IllegalStateException::class.java) {
            DispatchReadiness.actionOnceIfRecomputedReady(
                nativeState(),
                current,
                point(),
                dom("L1", "x=100;y=200;w=800;h=600"),
                dom("L1", "x=100;y=200;w=800;h=600"),
                dispatch,
                Runnable { calls.incrementAndGet() },
                LongSupplier { 1L },
            )
        }
        assertEquals("input display changed after preparation", failure.message)
        assertEquals("not-attempted", dispatch.stage)
        assertEquals(0, calls.get())
    }

    companion object {
        private val ACTIVITY = Any()
        private val TARGET = Any()
        private const val URL = "http://127.0.0.1:25341/basic.html"
    }
}
