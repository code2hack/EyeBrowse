package com.code2hack.eyebrowse.core.link.invitation

import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.LinkProtocol

/**
 * Pure, fake-clock-testable invitation lifecycle (ticket plan §5 "Authoritative invitation rules"):
 * one active invitation per Phone; TTL measured on an injected monotonic clock; generating a
 * replacement cancels the previous one; Cancel invalidates immediately; successful pairing
 * consumes it exactly once; expired/cancelled/consumed remain distinguishable outcomes.
 *
 * The 256-bit secret lives here in memory only — it is never persisted (plan §4.3).
 */
class InvitationLifecycle(
    /** Monotonic millisecond clock (Phone: elapsedRealtime; tests: fake). */
    private val nowMillis: () -> Long,
) {
    enum class State { ACTIVE, EXPIRED, CANCELLED, CONSUMED }

    class Invitation(
        val id: String,
        internal val secret: ByteArray,
        val createdAtMillis: Long,
        val ttlSeconds: Int,
    ) {
        fun expiresAtMillis(): Long = createdAtMillis + ttlSeconds * 1000L
    }

    /**
     * Distinguishable consume outcomes; they map 1:1 onto the plan §7 error taxonomy.
     */
    sealed class ConsumeOutcome {
        /** Invitation accepted and atomically consumed for [invitation]. */
        class Consumed(val invitation: Invitation) : ConsumeOutcome()

        /** Unknown id or secret mismatch. */
        object Invalid : ConsumeOutcome()

        object Expired : ConsumeOutcome()
        object Cancelled : ConsumeOutcome()

        /** Already consumed by an earlier successful pairing. */
        object Reused : ConsumeOutcome()
    }

    /** All invitations ever generated in this process, oldest first (memory-resident only). */
    private val generated = LinkedHashMap<String, Invitation>()

    private val cancelledIds = HashSet<String>()
    private val consumedIds = HashSet<String>()

    @Volatile
    private var activeId: String? = null

    /**
     * Creates the one active invitation; any previous active invitation becomes CANCELLED
     * immediately (replacement rule). Caller supplies fresh [SecureRandom]-quality id/secret.
     */
    @Synchronized
    fun generate(id: String, secret: ByteArray, ttlSeconds: Int = LinkProtocol.INVITATION_TTL_SECONDS): Invitation {
        activeId?.let { previous -> cancelledIds.add(previous) }
        val invitation = Invitation(id, secret.copyOf(), nowMillis(), ttlSeconds)
        generated[id] = invitation
        activeId = id
        return invitation
    }

    /** Cancels the active invitation immediately; a consumed/expired invitation stays as-is. */
    @Synchronized
    fun cancel(): Boolean {
        val id = activeId ?: return false
        if (stateOf(id) != State.ACTIVE) return false
        cancelledIds.add(id)
        return true
    }

    fun activeInvitation(): Invitation? = activeId?.let {
        generated[it]?.takeIf { invitation -> stateOf(invitation.id) == State.ACTIVE }
    }

    fun stateOf(id: String): State {
        val invitation = generated[id] ?: return State.CANCELLED // unknown ids: not authorized
        if (consumedIds.contains(id)) return State.CONSUMED
        if (cancelledIds.contains(id)) return State.CANCELLED
        if (nowMillis() >= invitation.expiresAtMillis()) return State.EXPIRED
        return State.ACTIVE
    }

    /**
     * Atomic one-time consume. Constant-time secret comparison. Exactly one caller (even under
     * concurrency) observes [ConsumeOutcome.Consumed] for a given invitation.
     */
    @Synchronized
    fun consume(id: String, secret: ByteArray): ConsumeOutcome {
        val invitation = generated[id] ?: return ConsumeOutcome.Invalid
        return when (stateOf(id)) {
            State.CONSUMED -> ConsumeOutcome.Reused
            State.CANCELLED -> ConsumeOutcome.Cancelled
            State.EXPIRED -> ConsumeOutcome.Expired
            State.ACTIVE ->
                if (MessageDigestEquals.equal(invitation.secret, secret)) {
                    consumedIds.add(id)
                    ConsumeOutcome.Consumed(invitation)
                } else {
                    ConsumeOutcome.Invalid
                }
        }
    }
}

/** Constant-time equality helper over secrets/fingerprints (plan §4.1). */
object MessageDigestEquals {
    fun equal(a: ByteArray, b: ByteArray): Boolean = java.security.MessageDigest.isEqual(a, b)
}

/** Maps a consume outcome to the wire error it produces (plan §7); null when accepted. */
fun InvitationLifecycle.ConsumeOutcome.toLinkErrorOrNull(): LinkError? = when (this) {
    is InvitationLifecycle.ConsumeOutcome.Consumed -> null
    InvitationLifecycle.ConsumeOutcome.Invalid -> LinkError.InvitationInvalid
    InvitationLifecycle.ConsumeOutcome.Expired -> LinkError.InvitationExpired
    InvitationLifecycle.ConsumeOutcome.Cancelled -> LinkError.InvitationCancelled
    InvitationLifecycle.ConsumeOutcome.Reused -> LinkError.InvitationReused
}
