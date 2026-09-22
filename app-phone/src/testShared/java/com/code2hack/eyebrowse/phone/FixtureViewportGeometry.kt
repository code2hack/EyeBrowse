package com.code2hack.eyebrowse.phone

import kotlin.math.roundToInt

/** Test-only conversion from the live WebView's measured CSS-to-viewport scale. */
internal class FixtureViewportGeometry(val scale: Double, val width: Int, val height: Int) {
    init { require(scale.isFinite() && scale>0 && width>0 && height>0) }
    fun cssDelta(viewportPixels: Int): Int = (viewportPixels/scale).roundToInt()
    fun center(left: Double, top: Double, w: Double, h: Double): Pair<Double,Double> {
        require(listOf(left,top,w,h).all { it.isFinite() } && w>0 && h>0)
        val x=(left+w/2)*scale;val y=(top+h/2)*scale
        require(x>=0 && y>=0 && x<width && y<height) { "fixture target outside current viewport" }
        return x to y
    }
}
