package com.code2hack.eyebrowse.projector.directrg

// Input primitive adapted from v0.0.4@03de921e2400147842ce6c56101b58d514dde77c.

/** One key sequence, one gesture. All times are Android uptime milliseconds, never sensor time. */
internal class PadGestureRecognizer(
    val doubleTapMs: Long,
    private val longPressMs: Long,
    private val single: ()->Unit,
    private val scroll: (Int)->Unit,
) {
    enum class Key { TAP, DOUBLE, FORWARD, BACKWARD }
    enum class Phase { DOWN, UP, CANCEL }
    data class Event(val key: Key, val phase: Phase, val timeMs: Long, val downTimeMs: Long, val repeat: Int=0)
    private data class Press(val key: Key, val downTimeMs: Long, val second: Boolean)
    private var press: Press?=null
    private var pending: Long?=null
    private var lastEventMs=0L
    private var suppressTapsUntil=0L
    val deadline get() = pending
    val hasWork get() = press!=null || pending!=null

    init { require(doubleTapMs in 100..600 && longPressMs in 200..2_000) }

    fun accept(event: Event, receiptUptimeMs: Long) {
        if (event.timeMs<=0 || event.timeMs<lastEventMs || event.timeMs>receiptUptimeMs ||
            receiptUptimeMs-event.timeMs>MAX_EVENT_AGE_MS || event.downTimeMs<0 || event.downTimeMs>event.timeMs) {
            cancel();return
        }
        lastEventMs=event.timeMs
        if (event.phase==Phase.CANCEL || event.repeat!=0) { cancel();return }
        if (event.key==Key.TAP && event.timeMs<suppressTapsUntil) return
        if (event.phase==Phase.DOWN) {
            if (press?.let { it.key==event.key && it.downTimeMs==event.downTimeMs }==true) return
            confirm(event.timeMs)
            val second=event.key==Key.TAP && pending!=null
            // Swipe/OEM double/second tap supersedes, never flushes an unconfirmed single.
            pending=null
            press=Press(event.key,event.downTimeMs,second)
            return
        }
        val old=press ?: return
        if (old.key!=event.key || old.downTimeMs!=event.downTimeMs) { cancel();return }
        press=null
        if (event.timeMs-old.downTimeMs>=longPressMs) { cancel();return }
        when(event.key) {
            Key.TAP -> if(old.second) suppressDouble(event.timeMs)
                else pending=event.timeMs+doubleTapMs
            Key.DOUBLE -> suppressDouble(event.timeMs)
            Key.FORWARD,Key.BACKWARD -> {
                pending=null;suppressTapsUntil=event.timeMs+doubleTapMs
                scroll(if(event.key==Key.FORWARD) 160 else -160)
            }
        }
    }
    fun confirm(nowUptimeMs: Long) {
        val old=pending ?: return
        if(nowUptimeMs<old) return
        pending=null // Remove before calling out, including when the callback changes the surface.
        single()
    }
    private fun suppressDouble(timeMs: Long) {
        pending=null;suppressTapsUntil=timeMs+doubleTapMs
    }
    fun cancel() {
        // Do not reinterpret the remaining tail of an invalidated physical sequence as a fresh tap
        // after a pause, focus loss, menu opening or geometry change.
        if(hasWork) suppressTapsUntil=maxOf(suppressTapsUntil,pending ?: (lastEventMs+doubleTapMs))
        press=null;pending=null
    }
    companion object { const val MAX_EVENT_AGE_MS=250L }
}
