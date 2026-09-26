package com.code2hack.eyebrowse.phone

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile

/** Phone-only protocol adapter. T01 acknowledges admission; T02/T03 supply pixels and page effects. */
class PhoneControlCoordinator(
    private val readDocumentIdentity: () -> String,
    private val invalidatePresentation: () -> Unit = {},
    private val executeAction: ((BrowserAction) -> Unit)? = null,
    private val measurePhone: () -> PresentationProfile?,
) {
    val authority = BrowserControlCoordinator(readDocumentIdentity())

    /** Listener callbacks are an optimization; lifecycle/admission always read actual browser state. */
    fun reconcileDocument() = synchronized(authority) {
        val before = authority.snapshot().context
        val after = authority.setDocumentIdentity(readDocumentIdentity()).context
        if (before != after) invalidatePresentation()
    }

    fun onLinkStarting() = reconcileDocument()

    fun onAuthenticatedSession(compatible: Boolean, keyboardCompatible: Boolean = false) = synchronized(authority) {
        reconcileDocument()
        authority.setAuthenticated(true, compatible, keyboardCompatible)
    }

    fun onLinkStopped() = synchronized(authority) {
        invalidatePresentation()
        authority.setAuthenticated(false)
    }

    fun receive(message: BrowserControlMessage): BrowserControlMessage? = synchronized(authority) {
        reconcileDocument()
        when (message) {
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
                    BrowserActionRequest(message.commandId,message.context,message.action,message.commandSequence))
                if (result is ActionDecision.Accepted) dispatchBrowserAction(message) { executeAction?.invoke(it) }
                else BrowserActionResultMessage(message.commandId, false,
                    reason = (result as ActionDecision.Rejected).reason.name)
            }
            else -> null // A peer cannot publish authoritative owner/state or stop a Phone generation.
        }
    }

    fun privateProfile(): HostingPresentationProfile? = authority.snapshot().let { state ->
        state.profile?.takeIf { state.owner == ControlOwner.RG }?.let {
            HostingPresentationProfile(it.width,it.height,it.densityDpi)
        }
    }
}

/** No user input in exception text or diagnostics. */
internal class RemoteAddressRejected : IllegalArgumentException()

/** Both quiescent and editor-retirement dispatch paths report the same policy outcome. */
internal fun dispatchBrowserAction(message: BrowserActionMessage, execute: (BrowserAction) -> Unit): BrowserActionResultMessage {
    val reason = try {
        execute(message.action)
        if (message.action is BrowserAction.OpenAddress) "ADDRESS_OPENED" else null
    } catch (_: RemoteAddressRejected) { "ADDRESS_REJECTED" }
      catch (_: RuntimeException) { "DISPATCH_UNCERTAIN" }
    // OPENED means a navigation was dispatched, not that a website transaction succeeded.
    return BrowserActionResultMessage(message.commandId, true,
        effectSucceeded = false.takeIf { reason == "ADDRESS_REJECTED" }, reason = reason)
}
