package com.code2hack.eyebrowse.rg

import kotlin.math.*

/** Copied raw Android rotation-vector components, not Euler yaw/pitch or screen coordinates. */
data class RotationSample(val timestampNs: Long, val x: Float, val y: Float, val z: Float, val w: Float)
data class PointerPosition(val x: Float, val y: Float, val available: Boolean)
data class PointerBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init { require(listOf(left,top,right,bottom).all(Float::isFinite) && right >= left && bottom >= top) }
    val width get() = right-left
    val height get() = bottom-top
}

/** Position aiming only. No input dispatch, browser state, transport or Android dependency. */
class HeadPointerModel(val settings: Settings = Settings()) {
    data class Settings(
        val horizontalHalfRangeDegrees: Double = 30.0,
        val verticalHalfRangeDegrees: Double = 22.0,
        val deadbandDegrees: Double = 0.3,
        val smoothingNs: Long = 60_000_000L,
        val maxSpanPerSecond: Double = 3.0,
        val staleNs: Long = 250_000_000L,
    ) {
        init {
            require(horizontalHalfRangeDegrees in 5.0..90.0 && verticalHalfRangeDegrees in 5.0..90.0)
            require(deadbandDegrees in 0.0..2.0 && smoothingNs in 5_000_000L..500_000_000L)
            require(maxSpanPerSecond in 0.1..10.0 && staleNs in 50_000_000L..1_000_000_000L)
        }
    }
    private data class Quaternion(val x: Double, val y: Double, val z: Double, val w: Double) {
        fun inverse() = Quaternion(-x,-y,-z,w)
        operator fun times(b: Quaternion) = Quaternion(
            w*b.x+x*b.w+y*b.z-z*b.y, w*b.y-x*b.z+y*b.w+z*b.x,
            w*b.z+x*b.y-y*b.x+z*b.w, w*b.w-x*b.x-y*b.y-z*b.z)
    }
    private var bounds = PointerBounds(0f,0f,0f,0f)
    private var rotation = 0
    private var running = false
    private var reference: Quaternion? = null
    private var latest: Quaternion? = null
    private var sourceTimeNs = 0L
    private var frameTimeNs = 0L
    private var x = .5
    private var y = .5
    private var targetX = .5
    private var targetY = .5
    private var fresh = false
    val position get() = PointerPosition((bounds.left+x*bounds.width).toFloat(),
        (bounds.top+y*bounds.height).toFloat(),running && fresh && bounds.width>0 && bounds.height>0)
    val settling get() = position.available && (abs(targetX-x)*bounds.width > .05 || abs(targetY-y)*bounds.height > .05)
    val expiresAtNs get() = sourceTimeNs + settings.staleNs

    fun start() { stop();running=true }
    fun stop() {
        running=false;fresh=false;reference=null;latest=null;sourceTimeNs=0;frameTimeNs=0
        x=.5;y=.5;targetX=.5;targetY=.5
    }
    fun resize(value: PointerBounds, displayRotation: Int) {
        require(displayRotation in 0..3)
        if (displayRotation!=rotation) { reference=null;fresh=false;x=.5;y=.5;targetX=.5;targetY=.5 }
        rotation=displayRotation;bounds=value
    }

    /** nowNs and sample.timestampNs both use elapsed realtime, including sleep. */
    fun sample(sample: RotationSample, nowNs: Long): Boolean {
        if (!running || nowNs<=0 || sample.timestampNs<=sourceTimeNs || sample.timestampNs>nowNs ||
            nowNs-sample.timestampNs>=settings.staleNs) return false
        val values=doubleArrayOf(sample.x.toDouble(),sample.y.toDouble(),sample.z.toDouble(),sample.w.toDouble())
        if (!values.all(Double::isFinite)) return false
        val norm=sqrt(values.sumOf { it*it })
        if (norm<1e-6) return false
        val current=Quaternion(values[0]/norm,values[1]/norm,values[2]/norm,values[3]/norm)
        val rebase=reference==null || !fresh || sample.timestampNs-sourceTimeNs>=settings.staleNs
        latest=current;sourceTimeNs=sample.timestampNs;fresh=true
        if (rebase) {
            reference=current;x=.5;y=.5;targetX=.5;targetY=.5;frameTimeNs=nowNs
        } else aim(checkNotNull(reference).inverse()*current)
        return true
    }

    private fun aim(q: Quaternion) {
        // Rotate the neutral forward ray (0,0,-1) into neutral device coordinates.
        // Natural display axes: +X right, +Y up, +Z toward viewer. No Euler field assumptions.
        val deviceX=-2*(q.x*q.z+q.w*q.y)
        val deviceY=2*(q.w*q.x-q.y*q.z)
        val forwardZ=2*(q.x*q.x+q.y*q.y)-1
        val (right,up)=when(rotation) {
            1 -> deviceY to -deviceX
            2 -> -deviceX to -deviceY
            3 -> -deviceY to deviceX
            else -> deviceX to deviceY
        }
        fun deadband(radians: Double): Double {
            val degrees=Math.toDegrees(radians)
            return sign(degrees)*max(0.0,abs(degrees)-settings.deadbandDegrees)
        }
        targetX=(.5+deadband(atan2(right,-forwardZ))/(2*settings.horizontalHalfRangeDegrees)).coerceIn(0.0,1.0)
        targetY=(.5+deadband(-atan2(up,hypot(right,forwardZ)))/(2*settings.verticalHalfRangeDegrees)).coerceIn(0.0,1.0)
    }

    /** Advance on display frames, independent of the <=5fps remote page stream. */
    fun advance(nowNs: Long): PointerPosition {
        if (!running || sourceTimeNs==0L || nowNs-sourceTimeNs>=settings.staleNs) {
            fresh=false;return position // Freeze; the next good sample establishes a new reference.
        }
        if (nowNs<=frameTimeNs) return position
        val dt=nowNs-frameTimeNs;frameTimeNs=nowNs
        val alpha=1-exp(-dt.toDouble()/settings.smoothingNs)
        var dx=(targetX-x)*alpha;var dy=(targetY-y)*alpha
        val distance=hypot(dx,dy);val limit=settings.maxSpanPerSecond*dt/1e9
        if (distance>limit) { dx*=limit/distance;dy*=limit/distance }
        x=(x+dx).coerceIn(0.0,1.0);y=(y+dy).coerceIn(0.0,1.0)
        if (!settling) { x=targetX;y=targetY }
        return position
    }

    fun recenter(nowNs: Long): Boolean {
        advance(nowNs)
        if (!position.available) return false
        reference=latest;x=.5;y=.5;targetX=.5;targetY=.5;frameTimeNs=nowNs
        return true
    }
}
