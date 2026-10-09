package com.code2hack.eyebrowse.rg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/** RG-local app view. Never captures touches, owns a browser, or sends a page action. */
class PointerOverlay(context: Context, attrs: AttributeSet?=null) : View(context,attrs) {
    private val model=HeadPointerModel()
    private var source: HeadPoseSource=SensorHeadPoseSource(context)
    private var running=false
    private var generation=0L
    private var pendingFrame=false
    private var publishedAvailable=false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var radius=8*resources.displayMetrics.density
    private var hudAppearance=false
    val position get() = model.position
    val sourceRegistered get() = source.registered
    val sourceDescription get() = source.description
    internal var onSample: ((RotationSample,Long,Int)->Unit)? = null
    private var displayRotation=0
    var onAvailabilityChanged: ((Boolean)->Unit)?=null
    internal var acceptedSamples=0L
        private set
    internal var lastSampleReceiptNs=0L
        private set
    internal var lastDrawElapsedNs=0L
        private set
    internal var lastDrawSampleReceiptNs=0L
        private set
    internal var drawCount=0L
        private set

    init { isClickable=false;isFocusable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }

    /** H1.2's 16 rendered-pixel footprint and light palette, independent of Android density. */
    internal fun useLocalHudAppearance() { hudAppearance=true;radius=6f;invalidate() }

    fun bounds(left: Float, top: Float, right: Float, bottom: Float, rotation: Int) {
        // Keep the entire ring inside the measured usable root, including its actual insets.
        val inset=minOf(if(hudAppearance) 8f else radius,
            (right-left).coerceAtLeast(0f)/2,(bottom-top).coerceAtLeast(0f)/2)
        displayRotation=rotation
        model.resize(PointerBounds(left+inset,top+inset,right-inset,bottom-inset),rotation)
        publishAvailability();requestFrame()
    }

    fun start() {
        check(Looper.myLooper()==Looper.getMainLooper())
        if (running) return
        running=true;model.start()
        val registration=++generation
        source.start onSample@{ sample ->
            check(Looper.myLooper()==Looper.getMainLooper())
            if (!running || registration!=generation) return@onSample
            val receipt=SystemClock.elapsedRealtimeNanos()
            onSample?.invoke(sample,receipt,displayRotation)
            val before=position
            if (model.sample(sample,receipt)) {
                acceptedSamples++;lastSampleReceiptNs=receipt
                if (position!=before || model.settling) requestFrame()
                scheduleExpiry()
            }
        }
        publishedAvailable=!position.available;publishAvailability();requestFrame()
    }

    fun stop() {
        check(Looper.myLooper()==Looper.getMainLooper())
        running=false;generation++
        source.stop();removeCallbacks(frame);removeCallbacks(expire);pendingFrame=false
        model.stop();publishAvailability();invalidate()
    }

    fun recenter() {
        if (model.recenter(SystemClock.elapsedRealtimeNanos())) requestFrame()
        publishAvailability()
    }

    /** Recheck age at admission even if the stale Handler callback has been delayed. */
    internal fun inputPosition(): PointerPosition {
        model.advance(SystemClock.elapsedRealtimeNanos());publishAvailability()
        return position
    }

    internal fun replaceSourceForTest(replacement: HeadPoseSource) {
        check(!running) { "Stop real acquisition before injecting raw replay" }
        source.stop();source=replacement
    }

    private fun publishAvailability() {
        val available=position.available
        if (publishedAvailable!=available) { publishedAvailable=available;onAvailabilityChanged?.invoke(available) }
    }
    private fun requestFrame() {
        if (running && !pendingFrame) { pendingFrame=true;postOnAnimation(frame) }
    }
    private val frame=Runnable {
        pendingFrame=false
        if (running) {
            model.advance(SystemClock.elapsedRealtimeNanos());publishAvailability();invalidate()
            if (model.settling) requestFrame()
        }
    }
    private val expire=Runnable {
        if (running) {
            model.advance(SystemClock.elapsedRealtimeNanos());publishAvailability();invalidate()
            if (position.available) scheduleExpiry()
        }
    }
    private fun scheduleExpiry() {
        removeCallbacks(expire)
        val remaining=model.expiresAtNs-SystemClock.elapsedRealtimeNanos()
        postDelayed(expire,((remaining+999_999)/1_000_000).coerceAtLeast(1))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val point=position
        paint.style=Paint.Style.STROKE;paint.strokeWidth=if(hudAppearance) 3f else 4f;paint.color=Color.BLACK
        canvas.drawCircle(point.x,point.y,radius,paint)
        paint.strokeWidth=2f;paint.color=if(hudAppearance) {
            if(point.available) 0xfff2f2f2.toInt() else 0xff8a8a8a.toInt()
        } else if(point.available) Color.CYAN else Color.GRAY
        canvas.drawCircle(point.x,point.y,radius,paint)
        paint.style=Paint.Style.FILL
        canvas.drawCircle(point.x,point.y,2f,paint)
        drawCount++;lastDrawElapsedNs=SystemClock.elapsedRealtimeNanos();lastDrawSampleReceiptNs=lastSampleReceiptNs
    }
    override fun onDetachedFromWindow() { stop();super.onDetachedFromWindow() }
}
