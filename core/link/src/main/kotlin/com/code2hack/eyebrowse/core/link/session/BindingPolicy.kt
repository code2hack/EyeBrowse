package com.code2hack.eyebrowse.core.link.session

import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.invitation.MessageDigestEquals
import com.code2hack.eyebrowse.core.link.invitation.InvitationLifecycle
import com.code2hack.eyebrowse.core.link.invitation.toLinkErrorOrNull

/**
 * Pure peer-binding/replacement rules (ticket plan §8). Deterministic, JVM-tested; the engines
 * consult these before committing any trust or sending protected status.
 */
object BindingPolicy {

    /**
     * Phone side, initial PairAuth from a presenting RG key.
     * [pairedPeerSpki] is the currently stored RG SPKI (null when unpaired).
     */
    fun phoneAcceptInitial(
        pairedPeerSpki: ByteArray?,
        candidateRgSpki: ByteArray,
    ): LinkError? {
        if (pairedPeerSpki == null) return null
        val samePeer = MessageDigestEquals.equal(pairedPeerSpki, candidateRgSpki)
        return if (samePeer) {
            // Same remembered RG presenting a fresh valid invitation: idempotent re-pair.
            null
        } else {
            // Different RG while a pairing exists: explicit Forget first.
            LinkError.PeerReplacementRequired
        }
    }

    /**
     * Phone side, ReconnectAuth. The stored peer must exist and match the presented key.
     */
    fun phoneAcceptReconnect(
        pairedPeerSpki: ByteArray?,
        candidateRgSpki: ByteArray,
    ): LinkError? = when {
        pairedPeerSpki == null -> LinkError.WrongRgIdentity
        MessageDigestEquals.equal(pairedPeerSpki, candidateRgSpki) -> null
        else -> LinkError.WrongRgIdentity
    }

    /**
     * RG side, QR invitation scanned while possibly already paired.
     * A different Phone identity in the QR requires explicit Forget before connecting.
     */
    fun rgAcceptInvitation(
        pairedPhoneSpkiSha256Hex: String?,
        invitationPhoneSpkiSha256Hex: String,
    ): LinkError? {
        if (pairedPhoneSpkiSha256Hex == null) return null
        return if (pairedPhoneSpkiSha256Hex == invitationPhoneSpkiSha256Hex) {
            // Same pinned Phone: proceeding may refresh locators after TLS proof (plan §8).
            null
        } else {
            LinkError.PeerReplacementRequired
        }
    }

    /** Invitation consume outcome → pre-auth rejection error (distinct failures, plan §5/§7). */
    fun invitationOutcomeToError(outcome: InvitationLifecycle.ConsumeOutcome): LinkError =
        outcome.toLinkErrorOrNull() ?: LinkError.InvitationInvalid
}
