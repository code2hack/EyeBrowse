package com.code2hack.eyebrowse.core.link.control

import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable enum class ControlOwner { PHONE, RG }
@Serializable enum class PresentationStatus { INACTIVE, READY, STALE }

/** All callbacks and page commands are bound to a particular presentation, not just its size. */
@Serializable
data class ControlContext(
    val lifetimeId: String,
    val controlEpoch: Long,
    val documentId: String,
    val viewportEpoch: Long,
    val hostingGeneration: Long?,
) {
    init {
        require(lifetimeId.isNotBlank() && lifetimeId.length <= 128)
        require(documentId.isNotBlank() && documentId.length <= 256)
        require(controlEpoch >= 0 && viewportEpoch >= 0)
        require(hostingGeneration == null || hostingGeneration >= 0)
    }
}

data class ControlSnapshot(
    val context: ControlContext,
    val owner: ControlOwner = ControlOwner.PHONE,
    val profile: PresentationProfile? = null,
    val hostingActive: Boolean = false,
    val presentationStatus: PresentationStatus = PresentationStatus.INACTIVE,
    val linkAuthenticated: Boolean = false,
    val sessionCompatible: Boolean = false,
) {
    val controlEpoch get() = context.controlEpoch
    val documentId get() = context.documentId
    val viewportEpoch get() = context.viewportEpoch
    val hostingGeneration get() = context.hostingGeneration
}

enum class HandoffTarget { PHONE, RG }
data class HandoffRequest(val target: HandoffTarget, val observedControlEpoch: Long, val profile: PresentationProfile? = null)
enum class HandoffRejection { ALREADY_OWNER, STALE_CONTROL_EPOCH, LINK_UNAVAILABLE, INCOMPATIBLE_SESSION, HOSTING_INACTIVE, INVALID_PROFILE, EPOCH_EXHAUSTED }
sealed class HandoffDecision {
    data class Accepted(val snapshot: ControlSnapshot) : HandoffDecision()
    data class Rejected(val reason: HandoffRejection) : HandoffDecision()
}

@Serializable
sealed class BrowserAction {
    @Serializable data object Back : BrowserAction()
    @Serializable data object Forward : BrowserAction()
    @Serializable data object Reload : BrowserAction()
    @Serializable data class ActivateAt(val x: Float, val y: Float) : BrowserAction() {
        init { require(x.isFinite() && y.isFinite() && x >= 0 && y >= 0) }
    }
    @Serializable data class ScrollBy(val dx: Float, val dy: Float) : BrowserAction() {
        init { require(dx.isFinite() && dy.isFinite() && kotlin.math.abs(dx) <= 4096 && kotlin.math.abs(dy) <= 4096) }
    }
}
data class BrowserActionRequest(val commandId: String, val context: ControlContext, val action: BrowserAction, val commandSequence: Long)
enum class ActionRejection { INVALID_COMMAND_ID, WRONG_OWNER, STALE_CONTEXT, PRESENTATION_NOT_READY, LINK_UNAVAILABLE, INCOMPATIBLE_SESSION, STALE_COMMAND_SEQUENCE, INVALID_COMMAND_SEQUENCE, OUTSIDE_VIEWPORT }
sealed class ActionDecision {
    data class Accepted(val commandId: String) : ActionDecision()
    data class Rejected(val reason: ActionRejection) : ActionDecision()
}

/**
 * Phone arbiter; no transport acknowledgment claims website success.
 * Calls and admission are serialized. The caller derives source from the authenticated endpoint,
 * never from a peer-supplied owner field. UI rendering and WebView dispatch are later todo work.
 */
class BrowserControlCoordinator(
    initialDocumentId: String,
    lifetimeId: String = UUID.randomUUID().toString(),
) {
    init { require(initialDocumentId.isNotBlank() && initialDocumentId.length <= 256) }
    private var state = ControlSnapshot(ControlContext(lifetimeId, 0, initialDocumentId, 0, null))
    // Per-control-epoch ordering, not a cache: old/uncertain sequences never become admissible.
    private var commandHighWater = 0L

    @Synchronized fun snapshot(): ControlSnapshot = state

    /** A Phone layout publication has no authority over an RG-owned presentation profile. */
    @Synchronized fun updatePhoneViewport(profile: PresentationProfile): Boolean {
        if (state.owner != ControlOwner.PHONE) return false
        if (state.profile != profile) state = state.copy(profile = profile,
            context = state.context.copy(viewportEpoch = Math.incrementExact(state.viewportEpoch)))
        return true
    }

    @Synchronized fun setDocumentIdentity(documentId: String): ControlSnapshot {
        require(documentId.isNotBlank() && documentId.length <= 256)
        if (documentId != state.documentId) {
            state = state.copy(context = state.context.copy(documentId = documentId, viewportEpoch = Math.incrementExact(state.viewportEpoch)),
                presentationStatus = if (state.owner == ControlOwner.RG) PresentationStatus.STALE else PresentationStatus.INACTIVE)
        }
        return state
    }

    /** Reconnect and foreground events cannot transfer control or revive an old presentation. */
    @Synchronized fun setAuthenticated(authenticated: Boolean, compatible: Boolean = false) {
        val changed = authenticated != state.linkAuthenticated || compatible != state.sessionCompatible
        state = state.copy(linkAuthenticated = authenticated, sessionCompatible = authenticated && compatible,
            context = if (changed) state.context.copy(viewportEpoch = Math.incrementExact(state.viewportEpoch)) else state.context,
            presentationStatus = if (changed && state.owner == ControlOwner.RG) PresentationStatus.STALE else state.presentationStatus)
    }

    @Synchronized fun setHostingGeneration(generation: Long?, active: Boolean) {
        require(generation == null || generation >= 0)
        val changed = state.hostingGeneration != generation || state.hostingActive != active
        state = state.copy(hostingActive = active && generation != null,
            context = state.context.copy(hostingGeneration = generation,
                viewportEpoch = if (changed) Math.incrementExact(state.viewportEpoch) else state.viewportEpoch),
            presentationStatus = if (changed && state.owner == ControlOwner.RG) PresentationStatus.STALE else state.presentationStatus)
    }

    /** Phone takeover accepts only a fresh local measurement supplied by the Phone adapter. */
    @Synchronized fun requestHandoff(request: HandoffRequest, freshPhoneProfile: (() -> PresentationProfile?)? = null): HandoffDecision {
        fun reject(reason: HandoffRejection) = HandoffDecision.Rejected(reason)
        if (request.observedControlEpoch != state.controlEpoch) return reject(HandoffRejection.STALE_CONTROL_EPOCH)
        val owner = if (request.target == HandoffTarget.RG) ControlOwner.RG else ControlOwner.PHONE
        if (state.owner == owner) return reject(HandoffRejection.ALREADY_OWNER)
        if (owner == ControlOwner.RG) {
            if (!state.linkAuthenticated) return reject(HandoffRejection.LINK_UNAVAILABLE)
            if (!state.sessionCompatible) return reject(HandoffRejection.INCOMPATIBLE_SESSION)
            if (!state.hostingActive) return reject(HandoffRejection.HOSTING_INACTIVE)
        }
        val profile = (if (owner == ControlOwner.RG) request.profile else freshPhoneProfile?.invoke())
            ?: return reject(HandoffRejection.INVALID_PROFILE)
        if (state.controlEpoch == Long.MAX_VALUE || state.viewportEpoch == Long.MAX_VALUE) return reject(HandoffRejection.EPOCH_EXHAUSTED)
        state = state.copy(owner = owner, profile = profile,
            context = state.context.copy(controlEpoch = state.controlEpoch + 1, viewportEpoch = state.viewportEpoch + 1),
            presentationStatus = if (owner == ControlOwner.RG) PresentationStatus.STALE else PresentationStatus.INACTIVE)
        commandHighWater = 0L // Previous commands cannot pass the new control epoch.
        return HandoffDecision.Accepted(state)
    }

    @Synchronized fun markPresentationReady(context: ControlContext): Boolean {
        if (context != state.context || state.owner != ControlOwner.RG || !state.hostingActive ||
            !state.linkAuthenticated || !state.sessionCompatible) return false
        state = state.copy(presentationStatus = PresentationStatus.READY)
        return true
    }

    @Synchronized fun markPresentationStale(context: ControlContext): Boolean {
        if (context != state.context || state.owner != ControlOwner.RG) return false
        state = state.copy(presentationStatus = PresentationStatus.STALE)
        return true
    }

    @Synchronized fun admitAction(source: ControlOwner, request: BrowserActionRequest): ActionDecision {
        fun reject(reason: ActionRejection) = ActionDecision.Rejected(reason)
        if (request.commandId.isBlank() || request.commandId.length > 128) return reject(ActionRejection.INVALID_COMMAND_ID)
        if (source != state.owner) return reject(ActionRejection.WRONG_OWNER)
        if (request.context != state.context) return reject(ActionRejection.STALE_CONTEXT)
        if (source == ControlOwner.RG) {
            if (!state.linkAuthenticated) return reject(ActionRejection.LINK_UNAVAILABLE)
            if (!state.sessionCompatible) return reject(ActionRejection.INCOMPATIBLE_SESSION)
            if (!state.hostingActive || state.presentationStatus != PresentationStatus.READY) return reject(ActionRejection.PRESENTATION_NOT_READY)
        }
        val action = request.action
        if (action is BrowserAction.ActivateAt) {
            val profile = state.profile ?: return reject(ActionRejection.OUTSIDE_VIEWPORT)
            if (action.x >= profile.width || action.y >= profile.height) return reject(ActionRejection.OUTSIDE_VIEWPORT)
        }
        if (request.commandSequence <= 0) return reject(ActionRejection.INVALID_COMMAND_SEQUENCE)
        if (request.commandSequence <= commandHighWater) return reject(ActionRejection.STALE_COMMAND_SEQUENCE)
        commandHighWater = request.commandSequence
        return ActionDecision.Accepted(request.commandId)
    }
}
