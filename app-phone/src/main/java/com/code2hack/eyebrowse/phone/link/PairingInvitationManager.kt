package com.code2hack.eyebrowse.phone.link

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.invitation.B64URL
import com.code2hack.eyebrowse.core.link.invitation.InvitationCodec
import com.code2hack.eyebrowse.core.link.invitation.InvitationLifecycle
import com.code2hack.eyebrowse.core.link.transport.Locator
import java.security.SecureRandom

/**
 * One active, expiring, cancellable, single-use pairing invitation (ticket plan §5).
 * Secrets are memory-resident only and die with the process; the payload is the QR content
 * (rendering itself is T02).
 */
class PairingInvitationManager(
    private val lifecycle: InvitationLifecycle,
    private val phoneSpkiSha256Hex: String,
    private val random: (Int) -> ByteArray = { size ->
        ByteArray(size).also { SecureRandom().nextBytes(it) }
    },
) {

    class ActiveInvitation(
        val id: String,
        val payload: String,
        val expiresAtElapsedMillis: Long,
    )

    @Volatile
    private var active: ActiveInvitation? = null

    /** Generates the one active invitation; any previous one is cancelled by replacement. */
    @Synchronized
    fun generate(locators: List<Locator>): ActiveInvitation {
        require(locators.isNotEmpty()) { "at least one emittable locator is required" }
        val id = B64URL.encode(random(LinkProtocol.INVITATION_ID_BYTES))
        val secret = random(LinkProtocol.INVITATION_SECRET_BYTES)
        val invitation = lifecycle.generate(id, secret)
        val payload = InvitationCodec.encode(
            invitationId = id,
            invitationSecret = secret,
            phoneSpkiSha256Hex = phoneSpkiSha256Hex,
            locators = locators,
            ttlSeconds = invitation.ttlSeconds,
        )
        val activeInvitation = ActiveInvitation(
            id = id,
            payload = payload,
            expiresAtElapsedMillis = invitation.expiresAtMillis(),
        )
        active = activeInvitation
        return activeInvitation
    }

    /** Cancel invalidates the active invitation immediately. */
    @Synchronized
    fun cancel(): Boolean {
        active = null
        return lifecycle.cancel()
    }

    fun activeInvitation(): ActiveInvitation? = active?.takeIf {
        lifecycle.activeInvitation()?.id == it.id
    }

    /** Called by the link server on authenticated initial pairing; atomic one-time consume.
     *  Coordinated with [generate] on the manager monitor; the visible active surface is cleared
     *  only when the consumed id is still the displayed one (review R1/B2). */
    @Synchronized
    fun consumeForServer(id: String, secretB64: String): InvitationLifecycle.ConsumeOutcome {
        val secret = B64URL.decode(secretB64)
            ?: return InvitationLifecycle.ConsumeOutcome.Invalid
        val outcome = lifecycle.consume(id, secret)
        if (outcome is InvitationLifecycle.ConsumeOutcome.Consumed && active?.id == id) {
            active = null
        }
        return outcome
    }
}
