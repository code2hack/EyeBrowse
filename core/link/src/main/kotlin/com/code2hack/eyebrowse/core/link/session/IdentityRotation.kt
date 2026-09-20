package com.code2hack.eyebrowse.core.link.session

/**
 * When the platform identity alias is inadequate, automatic regeneration (delete + recreate) is
 * legal ONLY for definitively ABSENT trust (T02 review round 1, blocker B1):
 *
 *  - ABSENT: no pairing exists, so rotating the identity cannot orphan a peer — regenerate.
 *  - VALID: a paired peer's pin refers to the existing identity — regeneration is forbidden
 *    (ledger D12 pre-pairing-only rule).
 *  - CORRUPT: trust state is unreadable, so "no pairing" is NOT established; rotating would
 *    destroy the one artifact an explicit recovery may still need. CORRUPT recovers exclusively
 *    through the explicit Forget/reset path, after which trust is ABSENT and this rule allows
 *    regeneration.
 */
object IdentityRotation {
    fun regenerateAllowed(trust: PeerTrustRead): Boolean = trust is PeerTrustRead.Absent
}
