package com.code2hack.eyebrowse.rg

/** One key sequence, one gesture. All times are Android uptime milliseconds, never sensor time. */
internal class PadGestureRecognizer<T>(
    val doubleTapMs: Long,
    private val longPressMs: Long,
    private val capture: ()->T?,
    private val single: (T?)->Unit,
    private val modeToggle: ()->Unit,
    private val scroll: (Int)->Unit,
) {
    enum class Key { TAP, DOUBLE, FORWARD, BACKWARD }
    enum class Phase { DOWN, UP, CANCEL }
    data class Event(val key: Key, val phase: Phase, val timeMs: Long, val downTimeMs: Long, val repeat: Int=0)
    private data class Press(val key: Key, val downTimeMs: Long, val second: Boolean)
    private data class Pending<T>(val target: T?, val deadline: Long)
    private var press: Press?=null
    private var pending: Pending<T>?=null
    private var lastEventMs=0L
    private var suppressTapsUntil=0L
    private var modeDedupUntil=0L
    val deadline get() = pending?.deadline
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
            Key.TAP -> if(old.second) emitMode(event.timeMs)
                else pending=Pending(capture(),event.timeMs+doubleTapMs)
            Key.DOUBLE -> emitMode(event.timeMs)
            Key.FORWARD,Key.BACKWARD -> {
                pending=null;suppressTapsUntil=event.timeMs+doubleTapMs
                scroll(if(event.key==Key.FORWARD) 160 else -160)
            }
        }
    }
    fun confirm(nowUptimeMs: Long) {
        val old=pending ?: return
        if(nowUptimeMs<old.deadline) return
        pending=null // Remove before calling out, including when the callback changes the surface.
        single(old.target)
    }
    private fun emitMode(timeMs: Long) {
        pending=null;suppressTapsUntil=timeMs+doubleTapMs
        if(timeMs>=modeDedupUntil) { modeDedupUntil=timeMs+doubleTapMs;modeToggle() }
    }
    fun cancel() { press=null;pending=null }
    companion object { const val MAX_EVENT_AGE_MS=250L }
}
