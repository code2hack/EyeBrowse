package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.ControlContext
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.core.link.messages.*

/** Used under the presentation-controller lock; one request, no retry or ownership prediction. */
internal class PendingHandoff {
    private var source: ControlContext?=null
    private var target: HandoffTargetWire?=null
    val busy get() = source!=null
    fun request(context: ControlContext, request: HandoffRequestMessage, send: (HandoffRequestMessage)->Boolean): Boolean {
        if (busy) return false
        source=context;target=request.target
        if (send(request)) return true
        clear();return false
    }
    fun result(result: HandoffResultMessage) {
        val pending=source ?: return
        if(result.context.lifetimeId!=pending.lifetimeId || result.context.controlEpoch<pending.controlEpoch) return
        if(!result.accepted || matches(result.owner,result.context)) clear()
    }
    fun observed(owner: ControlOwner, context: ControlContext) { if(matches(owner,context)) clear() }
    private fun matches(owner: ControlOwner, context: ControlContext): Boolean {
        val pending=source ?: return false
        return context.lifetimeId==pending.lifetimeId && context.controlEpoch>pending.controlEpoch &&
            ((target==HandoffTargetWire.RG && owner==ControlOwner.RG) || (target==HandoffTargetWire.PHONE && owner==ControlOwner.PHONE))
    }
    fun clear() { source=null;target=null }
}
