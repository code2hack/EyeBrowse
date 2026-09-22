package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.widget.Button
import android.widget.ImageView

/** Local pad gestures only. All webpage effects still enter the existing authenticated controller. */
internal class RgInputRouter(
    private val activity: Activity,
    private val pointer: PointerOverlay,
    private val presentation: RgPresentationController,
    private val feedback: (Boolean)->Unit,
) {
    internal sealed interface Target {
        data class Native(val view: Button, val action: LocalInputAction, val label: String): Target
        data class Page(val geometry: PointerImageGeometry, val point: InputPoint): Target
    }
    private data class Tap(val point: InputPoint, val target: Target, val geometryVersion: Long,
        val input: RgInputSnapshot, val capturedAtUptime: Long)
    data class DispatchTrace(val kind: String, val accepted: Boolean, val confirmedAt: Long, val finishedAt: Long, val recognitionWaitMs: Long, val capturedAt: Long?)
    internal var lastDispatch: DispatchTrace?=null
        private set
    internal var nativeInvocations=0L
        private set
    /** #10 attaches the Reading transition. #8 emits the intent without implementing that mode. */
    var onModeToggleIntent: ()->Unit = {}
    private val root=activity.findViewById<ViewGroup>(android.R.id.content)
    private val browserRoot=activity.findViewById<View>(R.id.rg_root)
    private val image=activity.findViewById<ImageView>(R.id.rg_page)
    private val main=Handler(Looper.getMainLooper())
    private var gestureEventAt=0L
    private var active=false
    private var focused=false
    private var geometryVersion=0L
    private var geometrySignature: List<Any> = emptyList()
    private var gestureContext: RgInputSnapshot?=null
    private val gestures=PadGestureRecognizer(
        ViewConfiguration.getDoubleTapTimeout().toLong().coerceIn(100,600),
        ViewConfiguration.getLongPressTimeout().toLong().coerceIn(200,2_000),
        ::captureTap,::confirmedTap,{
            if (usable()) { onModeToggleIntent();feedback(true) }
        },::scroll)
    val doubleTapMs get() = gestures.doubleTapMs
    private val confirmation=Runnable { if(active) { refresh();gestures.confirm(SystemClock.uptimeMillis());schedule() } }
    private val preDraw=ViewTreeObserver.OnPreDrawListener { if(gestures.hasWork) refresh();true }

    fun resume() {
        if(active) return
        active=true;focused=activity.hasWindowFocus();refresh()
        root.viewTreeObserver.addOnPreDrawListener(preDraw)
    }
    fun pause() {
        active=false;cancel()
        if(root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(preDraw)
    }
    fun focus(value: Boolean) { focused=value;if(!value) cancel() }
    fun cancel() { gestures.cancel();gestureContext=null;main.removeCallbacks(confirmation) }
    fun surfaceChanged() { if(active) refresh() }

    fun key(event: KeyEvent): Boolean {
        // OEM numeric codes are scoped to the observed physical pad, never a generic keyboard.
        if(event.device?.name!="ROKID,PSOC-TP-R" || !event.isFromSource(InputDevice.SOURCE_KEYBOARD)) return false
        val key=when(event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> PadGestureRecognizer.Key.TAP
            291 -> PadGestureRecognizer.Key.DOUBLE
            292 -> PadGestureRecognizer.Key.FORWARD
            293 -> PadGestureRecognizer.Key.BACKWARD
            else -> return false
        }
        if(!usable() || event.metaState!=0) { cancel();feedback(false);return true }
        refresh()
        if(!gestures.hasWork) gestureContext=presentation.inputSnapshot()
        val phase=when {
            event.isCanceled || event.isLongPress -> PadGestureRecognizer.Phase.CANCEL
            event.action==KeyEvent.ACTION_DOWN -> PadGestureRecognizer.Phase.DOWN
            event.action==KeyEvent.ACTION_UP -> PadGestureRecognizer.Phase.UP
            else -> PadGestureRecognizer.Phase.CANCEL
        }
        gestureEventAt=event.eventTime
        gestures.accept(PadGestureRecognizer.Event(key,phase,event.eventTime,event.downTime,event.repeatCount),SystemClock.uptimeMillis())
        schedule()
        return true // Do not also let the focused native Button process the same pad sequence.
    }
    private fun schedule() {
        main.removeCallbacks(confirmation)
        if(active) gestures.deadline?.let { main.postAtTime(confirmation,it) }
        if(!gestures.hasWork) gestureContext=null
    }
    private fun usable() = active && focused && activity.hasWindowFocus() && browserRoot.isShown &&
        pointer.inputPosition().available && !occluded()

    private fun captureTap(): Tap? {
        if(!usable()) return null
        val point=pointer.inputPosition().let { InputPoint(it.x,it.y) }
        val input=presentation.inputSnapshot()
        if(input!=gestureContext) return null // Refill between refresh and UP cannot promote an unavailable DOWN.
        val target=targetAt(point,input) ?: run { feedback(false);return null }
        return Tap(point,target,geometryVersion,input,SystemClock.uptimeMillis())
    }
    private fun confirmedTap(tap: Tap?) {
        val confirmed=SystemClock.uptimeMillis()
        val accepted=tap!=null && usable() && tap.geometryVersion==geometryVersion &&
            targetAt(tap.point,presentation.inputSnapshot())==tap.target && presentation.dispatchIfCurrent(tap.input) {
                when(val target=tap.target) {
                    is Target.Native -> tap.input.allows(target.action) && target.view.isEnabled && target.view.isShown &&
                        target.view.performClick().also { if(it) nativeInvocations++ }
                    is Target.Page -> tap.input.pageReady && presentation.activateAt(target.point.x,target.point.y)!=null
                }
            }
        lastDispatch=DispatchTrace("tap",accepted,confirmed,SystemClock.uptimeMillis(),tap?.let { confirmed-it.capturedAtUptime } ?: 0,tap?.capturedAtUptime)
        feedback(accepted)
    }
    private fun scroll(delta: Int) {
        val confirmed=SystemClock.uptimeMillis()
        val input=gestureContext
        // Swipe keeps its original availability/context even if a background refill finishes at UP.
        val accepted=usable() && input?.pageReady==true && presentation.dispatchIfCurrent(input) {
            presentation.scrollBy(0f,delta.toFloat())!=null
        }
        lastDispatch=DispatchTrace("scroll",accepted,confirmed,SystemClock.uptimeMillis(),0,gestureEventAt)
        feedback(accepted)
    }

    private fun refresh() {
        // ponytail: fingerprint this small view tree while input is pending; cache transforms if later surfaces grow.
        val signature=mutableListOf<Any>()
        fun visit(view: View) {
            if(view===pointer) return
            signature.add(view);signature.add(view.visibility);signature.add(view.alpha);signature.add(view.z)
            signature.add(view.isEnabled);signature.add(bounds(view) ?: "not visible")
            if(view is Button) signature.add(view.text.toString())
            if(view is ViewGroup) children(view).forEach(::visit)
        }
        visit(root);signature.add(imageGeometry(presentation.inputSnapshot()) ?: "no image")
        if(signature!=geometrySignature) { geometrySignature=signature;geometryVersion++;cancel() }
        if(gestureContext?.let { it!=presentation.inputSnapshot() }==true) cancel()
        if(!focused || !activity.hasWindowFocus() || occluded()) cancel()
    }
    private fun children(group: ViewGroup) = (0 until group.childCount).map(group::getChildAt).sortedBy { it.z }
    private fun globalToRoot(): Matrix? {
        val global=Matrix();pointer.transformMatrixToGlobal(global)
        return Matrix().takeIf { global.invert(it) }
    }
    private fun localToRoot(view: View): Matrix? {
        val inverse=globalToRoot() ?: return null
        val global=Matrix();view.transformMatrixToGlobal(global)
        return Matrix().apply { setConcat(inverse,global) }
    }
    private fun bounds(view: View): PointerBounds? {
        if(!view.isShown || view.alpha<=0) return null
        val rect=Rect();if(!view.getGlobalVisibleRect(rect)) return null
        val mapped=RectF(rect);(globalToRoot() ?: return null).mapRect(mapped)
        return PointerBounds(mapped.left,mapped.top,mapped.right,mapped.bottom)
    }
    private fun hit(view: View, point: InputPoint): View? {
        if(view===pointer || bounds(view)?.contains(point)!=true) return null
        if(view is ViewGroup) {
            for(child in children(view).asReversed()) hit(child,point)?.let { return it }
            return view.takeIf { it.isClickable || it.background!=null }
        }
        return view
    }
    /** An extra visible layer above this surface owns input; never scroll a hidden page. */
    private fun occluded(): Boolean {
        var child: View=browserRoot
        while(child!==root) {
            val parent=child.parent as? ViewGroup ?: return true
            val ordered=children(parent);val index=ordered.indexOf(child)
            if(ordered.drop(index+1).any { it!==pointer && it.isShown && it.alpha>0 && it.width>0 && it.height>0 }) return true
            child=parent
        }
        return false
    }
    internal fun targetAt(point: InputPoint, input: RgInputSnapshot): Target? {
        val view=hit(root,point) ?: return null
        if(view is Button) {
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
            return if(view.isEnabled && input.allows(action)) Target.Native(view,action,view.text.toString()) else null
        }
        if(view!==image || !input.pageReady) return null
        val geometry=imageGeometry(input) ?: return null
        return geometry.pagePoint(point)?.let { Target.Page(geometry,it) }
    }
    internal fun imageGeometry(input: RgInputSnapshot): PointerImageGeometry? {
        val profile=input.profile ?: return null
        val drawable=image.drawable as? BitmapDrawable ?: return null
        if(drawable.bitmap.width!=profile.width || drawable.bitmap.height!=profile.height) return null
        val imageToRoot=localToRoot(image) ?: return null
        val padding=Matrix().apply { setTranslate(image.paddingLeft.toFloat(),image.paddingTop.toFloat()) }
        val paddedImage=Matrix().apply { setConcat(padding,image.imageMatrix) }
        val forward=Matrix().apply { setConcat(imageToRoot,paddedImage) }
        val inverse=Matrix();if(!forward.invert(inverse)) return null
        val values=FloatArray(9);inverse.getValues(values)
        val content=RectF(image.paddingLeft.toFloat(),image.paddingTop.toFloat(),
            (image.width-image.paddingRight).toFloat(),(image.height-image.paddingBottom).toFloat())
        imageToRoot.mapRect(content)
        val visible=bounds(image) ?: return null
        if(!content.intersect(visible.left,visible.top,visible.right,visible.bottom)) return null
        return PointerImageGeometry(values.toList(),drawable.intrinsicWidth,drawable.intrinsicHeight,
            PointerBounds(content.left,content.top,content.right,content.bottom),profile)
    }
}
