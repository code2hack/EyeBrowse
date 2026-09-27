package com.code2hack.eyebrowse.core.link.messages

import com.code2hack.eyebrowse.core.link.control.ControlContext
import kotlinx.serialization.Serializable

object ContinuousScrollLimits {
    const val CREDIT_MS = 300L
    const val TICK_MS = 50L
    const val MAX_SPEED = 1200.0
    fun validId(id: String) = id.isNotBlank() && id.length <= 128
}

/** State stream, distinct from effectful v2 commands. Authority is still the full browser context. */
@Serializable data class ScrollStartMessage(val requestId: String, val context: ControlContext) : BrowserControlMessage {
    init { require(ContinuousScrollLimits.validId(requestId)) }
}
@Serializable data class ScrollCreditMessage(val requestId: String, val context: ControlContext,
    val leaseId: String, val credit: String) : BrowserControlMessage {
    init { require(listOf(requestId,leaseId,credit).all(ContinuousScrollLimits::validId)) }
}
@Serializable data class ScrollVelocityMessage(val context: ControlContext, val leaseId: String,
    val credit: String, val pixelsPerSecond: Double) : BrowserControlMessage {
    init {
        require(listOf(leaseId,credit).all(ContinuousScrollLimits::validId))
        require(pixelsPerSecond.isFinite() && kotlin.math.abs(pixelsPerSecond) <= ContinuousScrollLimits.MAX_SPEED)
    }
}
@Serializable data class ScrollStopMessage(val context: ControlContext, val leaseId: String) : BrowserControlMessage {
    init { require(ContinuousScrollLimits.validId(leaseId)) }
}
