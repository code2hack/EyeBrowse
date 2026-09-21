package com.code2hack.eyebrowse.phone

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile

/** Phone-only protocol adapter. T01 acknowledges admission; T02/T03 supply pixels and page effects. */
class PhoneControlCoordinator(initialDocumentId: String, private val measurePhone: () -> PresentationProfile?) {
    val authority = BrowserControlCoordinator(initialDocumentId)

    fun receive(message: BrowserControlMessage): BrowserControlMessage? = when (message) {
        is HandoffRequestMessage -> {
            val result = authority.requestHandoff(HandoffRequest(
                if (message.target == HandoffTargetWire.RG) HandoffTarget.RG else HandoffTarget.PHONE,
                message.observedControlEpoch, message.profile), measurePhone)
            val state = authority.snapshot()
            HandoffResultMessage(result is HandoffDecision.Accepted, state.owner, state.context, state.profile,
                (result as? HandoffDecision.Rejected)?.reason?.name)
        }
        is BrowserActionMessage -> {
            val result = authority.admitAction(ControlOwner.RG,
                BrowserActionRequest(message.commandId,message.context,message.action))
            BrowserActionResultMessage(message.commandId,result is ActionDecision.Accepted,
                effectSucceeded = null, reason = (result as? ActionDecision.Rejected)?.reason?.name)
        }
        else -> null // A peer cannot publish authoritative owner/state or stop a Phone generation.
    }

    fun privateProfile(): HostingPresentationProfile? = authority.snapshot().let { state ->
        state.profile?.takeIf { state.owner == ControlOwner.RG }?.let {
            HostingPresentationProfile(it.width,it.height,it.densityDpi)
        }
    }
}
