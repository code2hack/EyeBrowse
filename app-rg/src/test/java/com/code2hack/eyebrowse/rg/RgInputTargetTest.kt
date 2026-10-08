package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test

class RgInputTargetTest {
    private data class LocalState(
        val lifetime: Int = 1, val tab: String = "A", val selection: Long = 1,
        val document: Long = 1, val layout: Long = 1, val editor: Long = 1, val revision: Long = 1,
        override val pageReady: Boolean = true,
    ) : RgInputState

    private data class LocalGeometry(val bounds: PointerBounds) : RgInputGeometry {
        override fun pagePoint(root: InputPoint) = root.takeIf(bounds::contains)
    }
    private data class LocalIntent(val point: InputPoint, val editor: Long) : RgInputIntent

    /** Native viewport/tab identity only: no controller, frame, ControlContext, ordinal or image. */
    private class LocalTarget : RgInputTarget {
        var state = LocalState()
        var viewport = LocalGeometry(PointerBounds(0f, 0f, 480f, 640f))
        var beforeEffect: (() -> Unit)? = null
        val activations = mutableListOf<Pair<String, InputPoint>>()
        val scrolls = mutableListOf<Pair<String, Int>>()
        override fun snapshot() = state
        override fun geometry(input: RgInputState) = viewport
        override fun targetAt(point: InputPoint, input: RgInputState): RgInputIntent? =
            (input as? LocalState)?.takeIf { it.pageReady }?.let {
                viewport.pagePoint(point)?.let { position -> LocalIntent(position, it.editor) }
            }
        override fun dispatch(expected: RgInputState, intent: RgInputIntent): Boolean {
            beforeEffect?.invoke()
            val hit = intent as? LocalIntent ?: return false
            if (state != expected || !state.pageReady || state.editor != hit.editor) return false
            activations += state.tab to hit.point
            state = state.copy(editor = state.editor + 1)
            return true
        }
        override fun scroll(expected: RgInputState, delta: Int): Boolean {
            if (state != expected || !state.pageReady) return false
            scrolls += state.tab to delta
            return true
        }
        fun capture() = RgInputCapture(InputPoint(200f, 500f),
            checkNotNull(targetAt(InputPoint(200f, 500f), state)), 1, state, 10)
    }

    @Test fun localPortraitPageDispatchAndScrollNeedNoRemoteSessionOrImage() {
        val page = LocalTarget()
        assertNotNull(page.geometry(page.state).pagePoint(InputPoint(479f, 639f)))
        assertNull(page.geometry(page.state).pagePoint(InputPoint(480f, 500f)))
        assertTrue(page.capture().dispatch(page, 1))
        assertEquals(listOf("A" to InputPoint(200f, 500f)), page.activations)
        assertTrue(page.scroll(page.snapshot(), 160))
        assertEquals(listOf("A" to 160), page.scrolls)
    }

    @Test fun lifetimeTabDocumentLayoutEditorAndReadinessChangesRejectOriginalIntent() {
        for (changed in listOf(
            LocalState(lifetime = 2), LocalState(tab = "B", selection = 2),
            LocalState(document = 2), LocalState(layout = 2), LocalState(editor = 2), LocalState(revision = 2),
            LocalState(pageReady = false),
        )) {
            val page = LocalTarget(); val tap = page.capture()
            page.state = changed
            assertFalse(tap.dispatch(page, 1))
            assertFalse(page.scroll(tap.input, 160))
            assertTrue(page.activations.isEmpty()); assertTrue(page.scrolls.isEmpty())
        }
    }

    @Test fun returningToTheSameTabDoesNotReviveAPendingOperation() {
        val page = LocalTarget(); val tap = page.capture()
        page.state = page.state.copy(tab = "B", selection = 2)
        page.state = page.state.copy(tab = "A", selection = 3)
        assertFalse(tap.dispatch(page, 1)); assertFalse(page.scroll(tap.input, -160))
        assertTrue(page.capture().dispatch(page, 1))
        assertEquals(1, page.activations.size)
    }

    @Test fun readinessRecoveryDoesNotReviveAnOldCapture() {
        val page = LocalTarget(); val tap = page.capture()
        page.state = page.state.copy(pageReady = false, revision = 2)
        page.state = page.state.copy(pageReady = true, revision = 3)
        assertFalse(tap.dispatch(page, 1)); assertFalse(page.scroll(tap.input, 160))
        assertTrue(page.capture().dispatch(page, 1))
    }

    @Test fun oneRecognizerDispatchesOnceAndSuppressesBothDoubleTapConstituents() {
        val page = LocalTarget()
        val recognizer = PadGestureRecognizer(300, 500, page::capture,
            { it?.dispatch(page, 1) }, {}, {})
        fun tap(down: Long) {
            recognizer.accept(PadGestureRecognizer.Event(PadGestureRecognizer.Key.TAP,
                PadGestureRecognizer.Phase.DOWN, down, down), down)
            recognizer.accept(PadGestureRecognizer.Event(PadGestureRecognizer.Key.TAP,
                PadGestureRecognizer.Phase.UP, down + 10, down), down + 10)
        }
        tap(200); recognizer.confirm(510); recognizer.confirm(600)
        assertEquals(1, page.activations.size)
        tap(1000); tap(1100); recognizer.confirm(1500)
        assertEquals(1, page.activations.size)
    }

    @Test fun changedGeometryOrHitMeaningCannotRetargetATap() {
        val page = LocalTarget(); val tap = page.capture()
        assertFalse(tap.dispatch(page, 2))
        page.viewport = LocalGeometry(PointerBounds(0f, 0f, 100f, 100f))
        assertFalse(tap.dispatch(page, 1)); assertTrue(page.activations.isEmpty())
    }

    @Test fun executionOwnerRechecksAfterReentrantTargetChangeAndConsumesOnlyOnce() {
        val page = LocalTarget(); val tap = page.capture()
        page.beforeEffect = { page.state = page.state.copy(document = 2) }
        assertFalse(tap.dispatch(page, 1)); assertTrue(page.activations.isEmpty())
        page.beforeEffect = null
        val fresh = page.capture()
        assertTrue(fresh.dispatch(page, 1)); assertFalse(fresh.dispatch(page, 1))
        assertEquals(1, page.activations.size)
    }
}
