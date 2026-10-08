package com.code2hack.eyebrowse.rg

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup

/** The pointer view defines the root coordinate space for both native and page hit testing. */
internal class RgViewGeometry(private val pointer: View) {
    fun children(group: ViewGroup) = (0 until group.childCount).map(group::getChildAt).sortedBy { it.z }
    private fun globalToRoot(): Matrix? {
        val global=Matrix();pointer.transformMatrixToGlobal(global)
        return Matrix().takeIf { global.invert(it) }
    }
    fun localToRoot(view: View): Matrix? {
        val inverse=globalToRoot() ?: return null
        val global=Matrix();view.transformMatrixToGlobal(global)
        return Matrix().apply { setConcat(inverse,global) }
    }
    fun bounds(view: View): PointerBounds? {
        if(!view.isShown || view.alpha<=0) return null
        val rect=Rect();if(!view.getGlobalVisibleRect(rect)) return null
        val mapped=RectF(rect);(globalToRoot() ?: return null).mapRect(mapped)
        return PointerBounds(mapped.left,mapped.top,mapped.right,mapped.bottom)
    }
    fun hit(view: View, point: InputPoint): View? {
        if(view===pointer || bounds(view)?.contains(point)!=true) return null
        if(view is ViewGroup) {
            for(child in children(view).asReversed()) hit(child,point)?.let { return it }
            return view.takeIf { it.isClickable || it.background!=null }
        }
        return view
    }
}
