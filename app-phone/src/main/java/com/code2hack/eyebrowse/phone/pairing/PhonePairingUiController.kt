package com.code2hack.eyebrowse.phone.pairing

/**
 * Pure UI-state logic for the Phone "Pair RG" surface (ticket plan Phase C: Generate QR, Cancel,
 * status, Forget; exactly ONE visible active invitation).
 *
 * The controller is Android-free so the invitation-discipline regressions run on the JVM: the
 * Activity implements [PairingSurface] with the real process-scoped [PhoneLinkServer] and maps
 * [UiState] to views. The controller mirrors the pairing-store discipline at the UI layer: a
 * cancellation clears the displayed QR only when the displayed invitation is the one actually
 * cancelled (review R1/B2 lineage); Forget is the explicit, locally authoritative replacement.
 */
class PhonePairingUiController(
    private val surface: PairingSurface,
    private val nowMs: () -> Long,
) {

    /** What the Activity renders. Immutable snapshot. */
    data class UiState(
        val invitationId: String? = null,
        val invitationPayload: String? = null,
        val expiresAtMs: Long? = null,
        val linkUp: Boolean = false,
        val paired: Boolean = false,
        val note: String = "",
    )

    /** The Activity-side bridge to the real link server (never a second server instance). */
    interface PairingSurface {
        fun generateInvitation(): GeneratedInvitation?
        fun cancelInvitation(): Boolean
        fun forget()
        fun activeInvitationId(): String?
        fun isLinkUp(): Boolean
        fun isPaired(): Boolean

        data class GeneratedInvitation(val id: String, val payload: String, val expiresAtMs: Long)
    }

    private var state = UiState()

    fun current(): UiState = state

    private fun update(transform: (UiState) -> UiState): UiState {
        state = transform(state)
        return state
    }

    fun onScreenShown(): UiState = update {
        UiState(
            linkUp = surface.isLinkUp(),
            paired = surface.isPaired(),
            note = if (surface.isPaired()) PAIRED_NOTE else IDLE_NOTE,
        )
    }

    fun onGenerateClicked(): UiState {
        val generated = surface.generateInvitation() ?: return update {
            it.copy(invitationId = null, invitationPayload = null, expiresAtMs = null, note = GENERATE_FAILED_NOTE)
        }
        return update {
            UiState(
                invitationId = generated.id,
                invitationPayload = generated.payload,
                expiresAtMs = generated.expiresAtMs,
                linkUp = surface.isLinkUp(),
                paired = surface.isPaired(),
                note = INVITATION_ACTIVE_NOTE,
            )
        }
    }

    fun onCancelClicked(): UiState {
        val displayedId = state.invitationId ?: return update { it.copy(note = NOTHING_TO_CANCEL_NOTE) }
        val cancelled = surface.cancelInvitation()
        val activeAfter = surface.activeInvitationId()
        val cleared = cancelled && activeAfter != displayedId
        return update {
            if (cleared) {
                // The displayed invitation is gone: clear exactly its QR (a newer active one, if
                // any raced in, keeps its display through a subsequent refresh — B2 lineage).
                it.copy(
                    invitationId = null,
                    invitationPayload = null,
                    expiresAtMs = null,
                    note = CANCELLED_NOTE,
                )
            } else {
                it.copy(note = CANCEL_FAILED_NOTE)
            }
        }
    }

    fun onForgetClicked(): UiState {
        surface.forget()
        return update {
            UiState(linkUp = false, paired = false, note = FORGOTTEN_NOTE)
        }
    }

    fun onLinkStateChanged(): UiState = update {
        it.copy(linkUp = surface.isLinkUp(), paired = surface.isPaired())
    }

    fun onTick(): UiState = update { s ->
        val expiry = s.expiresAtMs
        if (expiry != null && nowMs() > expiry && s.invitationPayload != null) {
            s.copy(note = INVITATION_EXPIRED_NOTE)
        } else {
            s
        }
    }

    companion object {
        const val IDLE_NOTE = "Not paired. Generate a QR code on this Phone, then scan it with the RG."
        const val PAIRED_NOTE = "Paired. Generating a new QR re-pairs; use Forget to unpair."
        const val INVITATION_ACTIVE_NOTE = "Invitation active — scan it with the RG."
        const val GENERATE_FAILED_NOTE = "Could not start pairing. Check the Phone network and retry."
        const val CANCELLED_NOTE = "Invitation cancelled."
        const val CANCEL_FAILED_NOTE = "Cancel did not change the invitation."
        const val NOTHING_TO_CANCEL_NOTE = "No invitation is displayed."
        const val FORGOTTEN_NOTE = "Pairing forgotten on this Phone. Re-pair with a new QR."
        const val INVITATION_EXPIRED_NOTE = "Invitation expired — generate a fresh QR."
        const val LINKED_NOTE = "RG linked (authenticated)."
    }
}
