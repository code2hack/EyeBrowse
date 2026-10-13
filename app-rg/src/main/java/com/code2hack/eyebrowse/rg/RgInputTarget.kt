package com.code2hack.eyebrowse.rg

internal data class InputPoint(val x: Float, val y: Float)
internal fun PointerBounds.contains(p: InputPoint) =
    p.x.isFinite() && p.y.isFinite() && p.x>=left && p.x<right && p.y>=top && p.y<bottom

/** Immediate local operations, using current View geometry and Android focus. Call on the UI thread. */
internal interface RgInputTarget {
    fun activate(point: InputPoint): Boolean
    fun scroll(delta: Int): Boolean
    fun key(key: RgKeyboardKeys.Key): Boolean
}
