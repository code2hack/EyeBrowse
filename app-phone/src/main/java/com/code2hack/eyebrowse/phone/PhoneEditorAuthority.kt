package com.code2hack.eyebrowse.phone

import com.code2hack.eyebrowse.core.link.control.*
import java.util.UUID

/** Main-thread state only. Renderer calls, timers and notifications occur OUTSIDE this object. */
class PhoneEditorAuthority {
    enum class Phase { EMPTY, OPENING, READY, PENDING, REVOKING, UNCERTAIN }
    data class Opening(val id: String, val context: ControlContext, val connection: Long, val transition: Long,
                       val profileTransition: Boolean = false)
    data class Grant(val opening: Opening, val target: EditorTarget, val instance: String,
                     val revision: Long, val kind: EditorKind, val enter: EditorEnter)
    data class Pending(val id: String, val grant: Grant)
    data class Revocation(val id: String, val retired: Grant?, val token: String?, val transition: Long)

    var phase = Phase.EMPTY; private set
    var grant: Grant? = null; private set
    private var opening: Opening? = null
    private var pending: Pending? = null
    private var revocation: Revocation? = null
    private var issuedToken: String? = null
    private var transition = 0L
    fun isOpening(request: Opening) = phase == Phase.OPENING && opening == request
    fun isRevoking(barrier: Revocation) = revocation === barrier

    fun beginOpen(state: ControlSnapshot, connection: Long, profileTransition: Boolean = false,
                  localFocusReady: Boolean = true): Opening? {
        if (phase != Phase.EMPTY || !localFocusReady || !eligible(state, profileTransition) || transition >= Long.MAX_VALUE - 1) return null
        transition++
        return Opening(UUID.randomUUID().toString(), state.context, connection, transition, profileTransition).also {
            opening = it; issuedToken = it.id; phase = Phase.OPENING
        }
    }

    fun opened(request: Opening, answer: Grant?, state: ControlSnapshot, connection: Long,
               localFocusReady: Boolean = true): Boolean {
        if (phase != Phase.OPENING || opening != request) return false
        opening = null
        if (answer == null || answer.opening != request || answer.target.token != request.id ||
            request.context != state.context || request.connection != connection || !localFocusReady || !eligible(state, request.profileTransition)) {
            phase = Phase.UNCERTAIN // The renderer may have granted; require a revoke barrier.
            return false
        }
        grant = answer; phase = Phase.READY
        return true
    }

    /** Called only AFTER BrowserControlCoordinator consumes the canonical current ordinal. */
    fun beginEdit(id: String, target: EditorTarget, state: ControlSnapshot, connection: Long,
                  localFocusReady: Boolean = true): Pending? {
        val current = grant ?: return null
        if (phase != Phase.READY || !localFocusReady || current.target != target || current.opening.context != state.context ||
            current.opening.connection != connection || !eligible(state)) return null
        return Pending(id, current).also { pending = it; phase = Phase.PENDING }
    }

    fun completed(operation: Pending, revision: Long?, ready: Boolean): Boolean {
        if (phase != Phase.PENDING || pending !== operation) return false
        pending = null
        if (revision == null || revision < operation.grant.revision || !ready) {
            grant = null; phase = Phase.UNCERTAIN
        } else {
            grant = operation.grant.copy(revision = revision); phase = Phase.READY
        }
        return true
    }

    fun timedOut(operation: Pending): Boolean {
        if (pending !== operation || phase != Phase.PENDING) return false
        phase = Phase.UNCERTAIN
        // Keep the identity; a missing result is not cancellation or a retry permission.
        return true
    }

    fun revoke(): Revocation {
        revocation?.let { return it }
        transition = Math.incrementExact(transition)
        return Revocation(UUID.randomUUID().toString(), grant, issuedToken, transition).also {
            revocation = it; opening = null; grant = null; pending = null; phase = Phase.REVOKING
        }
    }

    fun revoked(barrier: Revocation, verified: Boolean): Boolean {
        if (revocation !== barrier) return false
        if (!verified) { phase = Phase.UNCERTAIN; return false }
        revocation = null; issuedToken = null; phase = Phase.EMPTY
        return true
    }

    private fun eligible(state: ControlSnapshot, profileTransition: Boolean = false) = state.owner == ControlOwner.RG && state.hostingActive &&
        state.linkAuthenticated && state.sessionCompatible && (state.presentationStatus == PresentationStatus.READY ||
            (profileTransition && state.presentationStatus == PresentationStatus.STALE))
}
