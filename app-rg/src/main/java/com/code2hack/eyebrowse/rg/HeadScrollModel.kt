package com.code2hack.eyebrowse.rg

import kotlin.math.*

/** Neutral acquisition survives interruptions without rebasing a held tilt into a scroll command. */
internal class HeadScrollModel(val settings: Settings = Settings()) {
    data class Settings(val deadZone: Double=2.0,val gain: Double=60.0,val maximumSpeed: Double=600.0,
        val acquireNs: Long=300_000_000,val staleNs: Long=250_000_000,val smoothingNs: Long=80_000_000,
        val driftDegreesPerSecond: Double=.05,val maximumDrift: Double=3.0) {
        init {
            require(deadZone in .5..10.0 && gain in 1.0..200.0 && maximumSpeed in 1.0..1200.0)
            require(acquireNs in 100_000_000..2_000_000_000 && staleNs in 50_000_000..1_000_000_000)
            require(smoothingNs in 1_000_000..500_000_000 && driftDegreesPerSecond in 0.0..0.2 && maximumDrift in 0.0..5.0)
        }
    }
    private var active=false
    private var reference: HeadOrientation?=null
    private var rotation=0
    private var sampleAt=0L
    private var neutralSince=0L
    private var drift=0.0
    private var filtered=0.0
    private var neutral=true
    var armed=false
        private set
    fun start() { stop();active=true }
    fun stop() { active=false;reference=null;sampleAt=0;drift=0.0;suspend() }
    fun suspend() { armed=false;neutralSince=0;filtered=0.0;neutral=true }
    fun sample(sample: RotationSample, now: Long, displayRotation: Int): Boolean {
        if(!active)return false
        if(displayRotation !in 0..3 || sample.timestampNs<=sampleAt || sample.timestampNs<=0 || sample.timestampNs>now || now-sample.timestampNs>=settings.staleNs) {
            suspend();return false
        }
        val pose=HeadOrientation.from(sample) ?: run { suspend();return false }
        if(reference!=null && displayRotation!=rotation) { reference=null;drift=0.0;suspend() }
        if(sampleAt!=0L && sample.timestampNs-sampleAt>=settings.staleNs)suspend()
        val dt=if(sampleAt==0L)0L else (sample.timestampNs-sampleAt).coerceAtMost(100_000_000)
        sampleAt=sample.timestampNs;rotation=displayRotation
        if(reference==null)reference=pose
        val pitch=(checkNotNull(reference).inverse()*pose).angles(rotation).second
        val relative=pitch-drift
        neutral=abs(relative)<=settings.deadZone
        if(!armed) {
            if(neutral) {
                if(neutralSince==0L)neutralSince=sampleAt
                if(sampleAt-neutralSince>=settings.acquireNs)armed=true
            } else neutralSince=0
            filtered=0.0
        }
        if(neutral) {
            val step=settings.driftDegreesPerSecond*dt/1e9
            drift=(drift+relative.coerceIn(-step,step)).coerceIn(-settings.maximumDrift,settings.maximumDrift)
            filtered=0.0
        } else if(armed) {
            val target=(sign(relative)*(abs(relative)-settings.deadZone)*settings.gain).coerceIn(-settings.maximumSpeed,settings.maximumSpeed)
            filtered+=(target-filtered)*(1-exp(-dt.toDouble()/settings.smoothingNs))
        }
        return true
    }
    fun available(now: Long) = active && sampleAt>0 && now>=sampleAt && now-sampleAt<settings.staleNs
    fun speed(now: Long): Double {
        if(!available(now)) { suspend();return 0.0 }
        return if(armed && !neutral)filtered else 0.0
    }
}
