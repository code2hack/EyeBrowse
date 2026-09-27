package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.core.link.framing.PresentationFrameHeader
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile

/** Test-only deadline witness shared by device observation and deterministic negative controls. */
internal object KeyboardDeadlineEvidence {
    data class Request(val message: ViewportUpdateMessage, val deadline: Long)
    enum class Witness { CONTINUED_REQUEST, INHERITED_PROFILE }
    data class Completion(
        val document: String, val observedAt: Long, val state: BrowserStateMessage?,
        val measured: PresentationProfile?, val frame: PresentationFrameHeader?,
        val layoutPending: Boolean, val viewportPending: Boolean, val canAct: Boolean, val hidden: Boolean,
    )
    fun witness(deadline: Long?, requests: Collection<Request>, replies: Map<Long, ViewportUpdateResultMessage>,
                completion: Completion): Witness? {
        if (deadline == null || completion.observedAt >= deadline || requests.isEmpty() || requests.any { it.deadline != deadline }) return null
        val state=completion.state ?: return null
        val profile=completion.measured ?: return null
        val frame=completion.frame ?: return null
        if (state.context.documentId!=completion.document || state.owner!=ControlOwner.RG || state.stale || state.loading ||
            state.profile!=profile || frame.context!=state.context || frame.width!=profile.width || frame.height!=profile.height ||
            completion.layoutPending || completion.viewportPending || !completion.canAct || !completion.hidden) return null
        var inherited=false
        for (record in requests) {
            val request=record.message
            val reply=replies[request.transitionId] ?: continue
            if (reply.transitionId!=request.transitionId || !reply.accepted || request.profile!=profile || reply.profile!=profile ||
                request.context.documentId!=reply.context.documentId ||
                request.context.lifetimeId!=state.context.lifetimeId || request.context.controlEpoch!=state.context.controlEpoch ||
                request.context.hostingGeneration!=state.context.hostingGeneration ||
                reply.context!=request.context.copy(viewportEpoch=reply.context.viewportEpoch) ||
                reply.context.viewportEpoch<=request.context.viewportEpoch) continue
            if (request.context.documentId==completion.document && reply.context==state.context) return Witness.CONTINUED_REQUEST
            if (request.context.documentId!=completion.document && state.context.viewportEpoch>reply.context.viewportEpoch)
                inherited=true
        }
        return if(inherited) Witness.INHERITED_PROFILE else null
    }
}
