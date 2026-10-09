package com.code2hack.eyebrowse.rg

import kotlin.math.*

/** Test-only raw movement from the observed cursor; never assigns model/overlay position. */
internal fun rawPoseToward(reference: RotationSample, current: RotationSample, position: PointerPosition,
                           bounds: PointerBounds, point: InputPoint, gain: Double): RotationSample {
    require(position.available && bounds.width>0 && bounds.height>0)
    val base=checkNotNull(HeadOrientation.from(reference))
    val (yaw,pitch)=(base.inverse()*checkNotNull(HeadOrientation.from(current))).angles(0)
    fun movement(pixels: Float, span: Float, halfRange: Double) =
        pixels/span*2*halfRange/gain+sign(pixels.toDouble())*.3
    val horizontal=Math.toRadians(yaw+movement(point.x-position.x,bounds.width,30.0))
    val vertical=Math.toRadians(pitch+movement(point.y-position.y,bounds.height,22.0))
    val sy=sin(-horizontal/2);val cy=cos(-horizontal/2);val sx=sin(-vertical/2);val cx=cos(-vertical/2)
    val raw=base*HeadOrientation(cy*sx,sy*cx,-sy*sx,cy*cx)
    return RotationSample(0,raw.x.toFloat(),raw.y.toFloat(),raw.z.toFloat(),raw.w.toFloat())
}
