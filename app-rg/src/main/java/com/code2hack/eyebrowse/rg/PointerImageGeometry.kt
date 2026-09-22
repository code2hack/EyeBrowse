package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile

internal data class InputPoint(val x: Float, val y: Float)
internal fun PointerBounds.contains(p: InputPoint) =
    p.x.isFinite() && p.y.isFinite() && p.x>=left && p.x<right && p.y>=top && p.y<bottom

/** Android supplies this measured inverse matrix, including ancestors, view padding and ImageView matrix. */
internal data class PointerImageGeometry(
    val rootToDrawable: List<Float>, val drawableWidth: Int, val drawableHeight: Int,
    val visibleContent: PointerBounds, val profile: PresentationProfile,
) {
    init { require(rootToDrawable.size==9 && rootToDrawable.all(Float::isFinite)) }
    fun pagePoint(root: InputPoint): InputPoint? {
        if(!visibleContent.contains(root) || drawableWidth<=0 || drawableHeight<=0) return null
        val m=rootToDrawable
        val w=m[6]*root.x+m[7]*root.y+m[8]
        if(!w.isFinite() || w<=0f) return null
        val x=(m[0]*root.x+m[1]*root.y+m[2])/w
        val y=(m[3]*root.x+m[4]*root.y+m[5])/w
        if(!x.isFinite() || !y.isFinite() || x<0 || y<0 || x>=drawableWidth || y>=drawableHeight) return null
        // BitmapDrawable's intrinsic density-scaled size need not equal native presentation pixels.
        return InputPoint(x/drawableWidth*profile.width,y/drawableHeight*profile.height)
    }
}
