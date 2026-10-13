package com.code2hack.eyebrowse.rg

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.*

/** Instrumentation-only raw quaternion generator; no cursor-position or network injection. */
internal class RawPoseReplay : HeadPoseSource {
    override val description="instrumentation raw quaternion stream"
    override var registered=false
        private set
    var flowing=true
    private var sample=RotationSample(0,0f,0f,0f,1f)
    private var reference=floatArrayOf(0f,0f,0f,1f)
    fun adoptCurrentReference() { reference=floatArrayOf(sample.x,sample.y,sample.z,sample.w) }
    private var consumer: ((RotationSample)->Unit)?=null
    private var adjustAim: (() -> Unit)?=null
    private var nextAimNs=0L
    private val handler=Handler(Looper.getMainLooper())
    private val pump=object: Runnable {
        override fun run() {
            if(!registered) return
            if(flowing) {
                val now=SystemClock.elapsedRealtimeNanos()
                if(now>=nextAimNs) {adjustAim?.invoke();nextAimNs=now+200_000_000}
                consumer?.invoke(sample.copy(timestampNs=now))
            }
            handler.postDelayed(this,20)
        }
    }
    override fun start(consumer: (RotationSample)->Unit): Boolean { this.consumer=consumer;registered=true;handler.post(pump);return true }
    override fun stop() { registered=false;handler.removeCallbacks(pump);consumer=null;adjustAim=null }
    fun orientationDegrees(yaw: Double, pitch: Double) {
        adjustAim=null
        val sy=sin(Math.toRadians(-yaw)/2);val cy=cos(Math.toRadians(-yaw)/2)
        val sx=sin(Math.toRadians(-pitch)/2);val cx=cos(Math.toRadians(-pitch)/2)
        sample=RotationSample(0,(cy*sx).toFloat(),(sy*cx).toFloat(),(-sy*sx).toFloat(),(cy*cx).toFloat())
    }
    fun pitchDegrees(degrees: Double) {
        adjustAim=null
        val half=Math.toRadians(-degrees)/2
        sample=RotationSample(0,sin(half).toFloat(),0f,0f,cos(half).toFloat())
    }
    fun aimRelative(activity: LocalBrowserActivity, point: InputPoint) = aim(activity.pointer,point)
    fun aim(activity: NativeInputTestActivity, point: InputPoint) = aim(activity.pointer,point)
    private fun aim(pointer: PointerOverlay, point: InputPoint) {
        // Correct through raw poses after smoothing; a fixed deadband offset over-aims after reversal.
        adjustAim={
            val current=pointer.inputPosition()
            if(current.available && (abs(current.x-point.x)>2 || abs(current.y-point.y)>2)) {
                val a=reference
                sample=rawPoseToward(RotationSample(0,a[0],a[1],a[2],a[3]),sample,
                    current,pointer.gainBounds,point,pointer.gain)
            }
        }
        adjustAim?.invoke();nextAimNs=SystemClock.elapsedRealtimeNanos()+200_000_000
    }
}
