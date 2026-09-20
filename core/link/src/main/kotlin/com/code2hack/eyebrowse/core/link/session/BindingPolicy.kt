package com.code2hack.eyebrowse.core.link.session

import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.invitation.InvitationLifecycle
import com.code2hack.eyebrowse.core.link.invitation.B64URL
import com.code2hack.eyebrowse.core.link.invitation.MessageDigestEquals
import com.code2hack.eyebrowse.core.link.invitation.toLinkErrorOrNull

/**
 * Pure peer-binding/replacement rules (ticket plan §8). Deterministic, JVM-tested; the engines
 * consult these before committing any trust or sending protected status.
 *
 * CORRUPT trust is fail-closed everywhere (review R5/B6): the replacement guard cannot be
 * evaluated, so any new pairing/reconnect is refused with the explicit-Forget semantics until
 * the store is cleared.
 */
object BindingPolicy {

    /**
     * Phone side, initial PairAuth from a presenting RG key.
     * [paired] is the currently stored peer state (Absent when unpaired, Corrupt = fail closed).
     */
    fun phoneAcceptInitial(
        paired: PeerTrustRead,
        candidateRgSpki: ByteArray,
    ): LinkError? = when (paired) {
        is PeerTrustRead.Absent -> null
        is PeerTrustRead.Corrupt -> LinkError.PeerReplacementRequired
        is PeerTrustRead.Valid -> {
            val storedSpki = B64URL.decode(paired.record.peerSpkiB64)
            if (storedSpki != null && MessageDigestEquals.equal(storedSpki, candidateRgSpki)) {
                // Same remembered RG presenting a fresh valid invitation: idempotent re-pair.
                null
            } else {
                // Different/unverifiable RG while a pairing exists: explicit Forget first.
                LinkError.PeerReplacementRequired
            }
        }
    }

    /**
     * Phone side, ReconnectAuth. The stored peer must exist, match the presented key, and be
     * readable; corrupt/unverifiable state fails closed (review R5/B6).
     */
    fun phoneAcceptReconnect(
        paired: PeerTrustRead,
        candidateRgSpki: ByteArray,
    ): LinkError? = when (paired) {
        is PeerTrustRead.Valid -> {
            val storedSpki = B64URL.decode(paired.record.peerSpkiB64)
            if (storedSpki != null && MessageDigestEquals.equal(storedSpki, candidateRgSpki)) null
            else LinkError.WrongRgIdentity
        }
        is PeerTrustRead.Absent -> LinkError.WrongRgIdentity
        is PeerTrustRead.Corrupt -> LinkError.WrongRgIdentity
    }

    /**
     * RG side, QR invitation scanned while possibly already paired.
     * A different Phone identity in the QR requires explicit Forget before connecting.
     */
    fun rgAcceptInvitation(
        paired: PeerTrustRead,
        invitationPhoneSpkiSha256Hex: String,
    ): LinkError? = when (paired) {
        is PeerTrustRead.Absent -> null
        is PeerTrustRead.Corrupt -> LinkError.PeerReplacementRequired
        is PeerTrustRead.Valid ->
            if (paired.record.peerSpkiSha256Hex == invitationPhoneSpkiSha256Hex) {
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
