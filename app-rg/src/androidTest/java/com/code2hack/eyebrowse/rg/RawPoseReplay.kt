package com.code2hack.eyebrowse.rg

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
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
    private val handler=Handler(Looper.getMainLooper())
    private val pump=object: Runnable {
        override fun run() {
            if(!registered) return
            if(flowing) consumer?.invoke(sample.copy(timestampNs=SystemClock.elapsedRealtimeNanos()))
            handler.postDelayed(this,20)
        }
    }
    override fun start(consumer: (RotationSample)->Unit): Boolean { this.consumer=consumer;registered=true;handler.post(pump);return true }
    override fun stop() { registered=false;handler.removeCallbacks(pump);consumer=null }
    fun aim(activity: MainActivity, point: InputPoint) {
        val root=activity.findViewById<View>(R.id.rg_root)
        val radius=8*activity.resources.displayMetrics.density
        val left=root.paddingLeft+radius;val right=root.width-root.paddingRight-radius
        val top=root.paddingTop+radius;val bottom=root.height-root.paddingBottom-radius
        fun angle(delta: Double,halfRange: Double)=Math.toRadians(delta*2*halfRange+sign(delta)*.3)
        val yaw=angle((point.x-left)/(right-left)-.5,30.0)
        val pitch=angle((point.y-top)/(bottom-top)-.5,22.0)
        val sy=sin(-yaw/2);val cy=cos(-yaw/2);val sx=sin(-pitch/2);val cx=cos(-pitch/2)
        val x=(cy*sx).toFloat();val y=(sy*cx).toFloat();val z=(-sy*sx).toFloat();val w=(cy*cx).toFloat()
        val a=reference
        sample=RotationSample(0,a[3]*x+a[0]*w+a[1]*z-a[2]*y,
            a[3]*y-a[0]*z+a[1]*w+a[2]*x,a[3]*z+a[0]*y-a[1]*x+a[2]*w,
            a[3]*w-a[0]*x-a[1]*y-a[2]*z)
    }
}
