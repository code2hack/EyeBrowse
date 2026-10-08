package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.widget.Button

/** One local pad recognizer, independent of page execution, remote state and image-backed rendering. */
internal class RgInputRouter(
    private val activity: Activity,
    private val pointer: PointerOverlay,
    private val executor: RgInputTarget,
    private val browserRoot: View,
    private val feedback: (Boolean)->Unit,
) {
    data class DispatchTrace(val kind: String, val accepted: Boolean, val confirmedAt: Long, val finishedAt: Long, val recognitionWaitMs: Long, val capturedAt: Long?)
    internal var lastDispatch: DispatchTrace?=null
        private set
    internal var nativeInvocations=0L
        private set
    /** #10 attaches the Reading transition. #8 emits the intent without implementing that mode. */
    var onModeToggleIntent: ()->Unit = {}
    /** Optional legacy Reading policy; neither hook is part of the local execution contract. */
    var legacyReadingActive: ()->Boolean = { false }
    var onLegacySwipe: ()->Unit = {}
    private val root=activity.findViewById<ViewGroup>(android.R.id.content)
    private val views=RgViewGeometry(pointer)
    private val main=Handler(Looper.getMainLooper())
    private var gestureEventAt=0L
    private var active=false
    private var focused=false
    private var geometryVersion=0L
    private var geometrySignature: List<Any> = emptyList()
    private var gestureContext: RgInputState?=null
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
        if(!gestures.hasWork) gestureContext=executor.snapshot()
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
    internal fun surfaceAvailable() = active && focused && activity.hasWindowFocus() && browserRoot.isShown && !occluded()
    private fun usable() = active && focused && activity.hasWindowFocus() && browserRoot.isShown &&
        (legacyReadingActive() || pointer.inputPosition().available) && !occluded()

    private fun captureTap(): RgInputCapture? {
        if(legacyReadingActive()) return null
        if(!usable()) return null
        val point=pointer.inputPosition().let { InputPoint(it.x,it.y) }
        val input=executor.snapshot()
        if(input!=gestureContext) return null // Refill between refresh and UP cannot promote an unavailable DOWN.
        val target=targetAt(point,input) ?: run { feedback(false);return null }
        return RgInputCapture(point,target,geometryVersion,input,SystemClock.uptimeMillis())
    }
    private fun confirmedTap(tap: RgInputCapture?) {
        if(legacyReadingActive()) return
        val confirmed=SystemClock.uptimeMillis()
        val accepted=tap!=null && usable() && tap.dispatch(executor,geometryVersion)
        if(accepted && tap?.target?.nativeControl==true) nativeInvocations++
        lastDispatch=DispatchTrace("tap",accepted,confirmed,SystemClock.uptimeMillis(),tap?.let { confirmed-it.capturedAtUptime } ?: 0,tap?.capturedAtUptime)
        feedback(accepted)
    }
    private fun scroll(delta: Int) {
        val confirmed=SystemClock.uptimeMillis()
        val input=gestureContext
        onLegacySwipe()
        // Swipe keeps its original availability/context even if a background refill finishes at UP.
        val accepted=usable() && input?.pageReady==true && executor.scroll(input,delta)
        lastDispatch=DispatchTrace("scroll",accepted,confirmed,SystemClock.uptimeMillis(),0,gestureEventAt)
        feedback(accepted)
    }

    private fun refresh() {
        // ponytail: fingerprint this small view tree while input is pending; cache transforms if later surfaces grow.
        val signature=mutableListOf<Any>()
        fun visit(view: View) {
            if(view===pointer) return
            signature.add(view);signature.add(view.visibility);signature.add(view.alpha);signature.add(view.z)
            signature.add(view.isEnabled);signature.add(views.bounds(view) ?: "not visible")
            if(view is Button) signature.add(view.text.toString())
            if(view is ViewGroup) views.children(view).forEach(::visit)
        }
        visit(root);signature.add(executor.geometry(executor.snapshot()) ?: "no page geometry")
        if(signature!=geometrySignature) { geometrySignature=signature;geometryVersion++;cancel() }
        if(gestureContext?.let { it!=executor.snapshot() }==true) cancel()
        if(!focused || !activity.hasWindowFocus() || occluded()) cancel()
    }
    /** An extra visible layer above this surface owns input; never scroll a hidden page. */
    private fun occluded(): Boolean {
        var child: View=browserRoot
        while(child!==root) {
            val parent=child.parent as? ViewGroup ?: return true
            val ordered=views.children(parent);val index=ordered.indexOf(child)
            if(ordered.drop(index+1).any { it!==pointer && it.id!=R.id.rg_reading_notice && it.isShown && it.alpha>0 && it.width>0 && it.height>0 }) return true
            child=parent
        }
        return false
    }
    internal fun targetAt(point: InputPoint, input: RgInputState) = executor.targetAt(point,input)
}
