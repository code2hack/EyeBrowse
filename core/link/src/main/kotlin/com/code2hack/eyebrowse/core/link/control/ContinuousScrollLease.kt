package com.code2hack.eyebrowse.core.link.control

import com.code2hack.eyebrowse.core.link.messages.*
import java.util.UUID
import kotlin.math.abs

/** Phone-owned monotonic liveness. Call from the Phone's serialized input boundary. */
class ContinuousScrollLease(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private data class Live(val request: ScrollStartMessage, val connection: Long, val id: String,
        var credit: String, var deadline: Long, var tickAt: Long, var neutral: Boolean = false,
        var velocity: Double = 0.0, var fraction: Double = 0.0)
    private var live: Live? = null
    val active get() = live != null
    val deadlineMs get() = live?.deadline
    private fun eligible(state: ControlSnapshot) = state.owner == ControlOwner.RG && state.linkAuthenticated &&
        state.sessionCompatible && state.hostingActive && state.presentationStatus == PresentationStatus.READY
    private fun current(state: ControlSnapshot, connection: Long, now: Long): Live? {
        val value = live ?: return null
        if (!eligible(state) || value.request.context != state.context || value.connection != connection || now >= value.deadline) {
            stop();return null
        }
        return value
    }
    fun start(request: ScrollStartMessage, state: ControlSnapshot, connection: Long, now: Long): ScrollCreditMessage? {
        if (request.context != state.context || !eligible(state)) return null
        // A duplicate start never rotates credits or renews the existing deadline.
        val existing = current(state,connection,now)
        if (existing?.request?.requestId == request.requestId) return credit(existing)
        val next = Live(request,connection,newId(),newId(),now+ContinuousScrollLimits.CREDIT_MS,now)
        live = next
        return credit(next)
    }
    fun update(message: ScrollVelocityMessage, state: ControlSnapshot, connection: Long, now: Long): ScrollCreditMessage? {
        val value = current(state,connection,now) ?: return null
        if (message.context != state.context || message.leaseId != value.id || message.credit != value.credit) return null
        if (!value.neutral && message.pixelsPerSecond != 0.0) { stop();return null }
        value.neutral = true
        value.velocity = message.pixelsPerSecond
        if (value.velocity == 0.0) value.fraction = 0.0
        value.credit = newId();value.deadline = now+ContinuousScrollLimits.CREDIT_MS
        return credit(value)
    }
    fun stop(message: ScrollStopMessage, state: ControlSnapshot, connection: Long, now: Long) {
        val value = current(state,connection,now) ?: return
        if (message.context == state.context && message.leaseId == value.id) stop()
    }
    fun stop() { live = null }
    fun delta(state: ControlSnapshot, connection: Long, now: Long): Int {
        val value = current(state,connection,now) ?: return 0
        if (now < value.tickAt) { stop();return 0 }
        val dt = (now-value.tickAt).coerceAtMost(ContinuousScrollLimits.TICK_MS)
        value.tickAt = now
        value.fraction += value.velocity*dt/1000.0
        val pixels = value.fraction.toInt()
        value.fraction -= pixels
        check(abs(pixels) <= ContinuousScrollLimits.MAX_SPEED*ContinuousScrollLimits.TICK_MS/1000)
        return pixels
    }
    private fun credit(value: Live) = ScrollCreditMessage(value.request.requestId,value.request.context,value.id,value.credit)
}
