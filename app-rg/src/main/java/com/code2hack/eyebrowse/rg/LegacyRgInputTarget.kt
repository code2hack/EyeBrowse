package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView

/** Remote-only image mapping, native control meanings and authenticated admission stay here. */
internal class LegacyRgInputTarget(
    private val activity: Activity,
    pointer: PointerOverlay,
    private val presentation: RgPresentationController,
) : RgInputTarget, RgKeyboardInput {
    private sealed interface Target : RgInputIntent {
        data class Native(val view: Button, val action: LocalInputAction, val label: String): Target {
            override val nativeControl get() = true
        }
        data class Keyboard(val view: Button, val intent: RgKeyboard.Intent): Target
        data class Address(val view: Button): Target
        data class Page(val geometry: PointerImageGeometry, val point: InputPoint): Target
    }
    private val root=activity.findViewById<ViewGroup>(android.R.id.content)
    private val image=activity.findViewById<ImageView>(R.id.rg_page)
    private val views=RgViewGeometry(pointer)
    override val keyboard get() = presentation.keyboard
    override fun canKey(key: RgKeyboard.Key) = presentation.canKey(key)
    override fun key(intent: RgKeyboard.Intent) = presentation.key(intent)
    override fun snapshot() = presentation.inputSnapshot()
    override fun geometry(input: RgInputState) = (input as? RgInputSnapshot)?.let(::imageGeometry)

    override fun dispatch(expected: RgInputState, intent: RgInputIntent): Boolean {
        val input=expected as? RgInputSnapshot ?: return false
        val target=intent as? Target ?: return false
        return presentation.dispatchIfCurrent(input,(target as? Target.Native)?.action) {
            when(target) {
                is Target.Native -> input.allows(target.action) && target.view.isEnabled && target.view.isShown &&
                    target.view.performClick()
                is Target.Keyboard -> presentation.keyboard.current(target.intent) && presentation.key(target.intent)
                is Target.Address -> input.addressAvailable && presentation.openAddressKeyboard()
                is Target.Page -> input.pageReady && presentation.activateAt(target.point.x,target.point.y)!=null
            }
        }
    }
    override fun scroll(expected: RgInputState, delta: Int): Boolean {
        val input=expected as? RgInputSnapshot ?: return false
        return input.pageReady && presentation.dispatchIfCurrent(input) {
            presentation.scrollBy(0f,delta.toFloat())!=null
        }
    }

    override fun targetAt(point: InputPoint, input: RgInputState): RgInputIntent? {
        val remote=input as? RgInputSnapshot ?: return null
        val view=views.hit(root,point) ?: return null
        if(view is Button) {
            val key = view.tag as? RgKeyboard.Intent
            if (key != null) return if (view.isEnabled && presentation.keyboard.current(key) && presentation.canKey(key.key)) Target.Keyboard(view, key) else null
            if (view.id == R.id.rg_detail) return if (view.isEnabled && remote.addressAvailable) Target.Address(view) else null
            val action=when(view.id) {
                R.id.rg_recenter -> LocalInputAction.RECENTER
                R.id.rg_retry -> LocalInputAction.RETRY
                R.id.button_scan_pair -> LocalInputAction.PAIR
                R.id.rg_back -> LocalInputAction.BACK
                R.id.rg_forward -> LocalInputAction.FORWARD
                R.id.rg_reload -> LocalInputAction.RELOAD
                R.id.rg_handoff -> when(view.text.toString()) {
                    activity.getString(R.string.use_on_glasses) -> LocalInputAction.USE_GLASSES
                    activity.getString(R.string.use_on_phone) -> LocalInputAction.USE_PHONE
                    else -> return null
                }
                else -> return null
            }
            return if(view.isEnabled && remote.allows(action)) Target.Native(view,action,view.text.toString()) else null
        }
        if(view!==image || !remote.pageReady) return null
        val geometry=imageGeometry(remote) ?: return null
        return geometry.pagePoint(point)?.let { Target.Page(geometry,it) }
    }
    internal fun imageGeometry(input: RgInputSnapshot): PointerImageGeometry? {
        val profile=input.profile ?: return null
        val drawable=image.drawable as? BitmapDrawable ?: return null
        if(drawable.bitmap.width!=profile.width || drawable.bitmap.height!=profile.height) return null
        val imageToRoot=views.localToRoot(image) ?: return null
        val padding=Matrix().apply { setTranslate(image.paddingLeft.toFloat(),image.paddingTop.toFloat()) }
        val paddedImage=Matrix().apply { setConcat(padding,image.imageMatrix) }
        val forward=Matrix().apply { setConcat(imageToRoot,paddedImage) }
        val inverse=Matrix();if(!forward.invert(inverse)) return null
        val values=FloatArray(9);inverse.getValues(values)
        val content=RectF(image.paddingLeft.toFloat(),image.paddingTop.toFloat(),
            (image.width-image.paddingRight).toFloat(),(image.height-image.paddingBottom).toFloat())
        imageToRoot.mapRect(content)
        val visible=views.bounds(image) ?: return null
        if(!content.intersect(visible.left,visible.top,visible.right,visible.bottom)) return null
        return PointerImageGeometry(values.toList(),drawable.intrinsicWidth,drawable.intrinsicHeight,
            PointerBounds(content.left,content.top,content.right,content.bottom),profile)
    }
}
