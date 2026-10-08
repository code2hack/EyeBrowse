package com.code2hack.eyebrowse.rg

internal data class InputPoint(val x: Float, val y: Float)
internal fun PointerBounds.contains(p: InputPoint) =
    p.x.isFinite() && p.y.isFinite() && p.x>=left && p.x<right && p.y>=top && p.y<bottom

/** Immutable equality must fence lifetime, tab/selection, document, layout, editor and readiness changes. */
internal interface RgInputState {
    val pageReady: Boolean
}

/** Measured root-to-page mapping; a local page does not need a bitmap or presentation profile. */
internal interface RgInputGeometry {
    fun pagePoint(root: InputPoint): InputPoint?
}

/** Equality identifies the original control/page point/key, including its semantic generation. */
internal interface RgInputIntent {
    val nativeControl: Boolean get() = false
}

/** One execution owner; implementations revalidate expected state at the actual effect boundary. */
internal interface RgInputTarget {
    fun snapshot(): RgInputState
    fun geometry(input: RgInputState): RgInputGeometry?
    fun targetAt(point: InputPoint, input: RgInputState): RgInputIntent?
    fun dispatch(expected: RgInputState, intent: RgInputIntent): Boolean
    fun scroll(expected: RgInputState, delta: Int): Boolean
}

internal data class RgInputCapture(
    val point: InputPoint, val target: RgInputIntent, val geometryVersion: Long,
    val input: RgInputState, val capturedAtUptime: Long,
) {
    fun dispatch(executor: RgInputTarget, currentGeometryVersion: Long): Boolean =
        geometryVersion == currentGeometryVersion &&
            executor.targetAt(point, executor.snapshot()) == target &&
            executor.dispatch(input, target)
}
