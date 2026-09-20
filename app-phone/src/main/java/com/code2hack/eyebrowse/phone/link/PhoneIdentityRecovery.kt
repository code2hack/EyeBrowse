package com.code2hack.eyebrowse.phone.link

import com.code2hack.eyebrowse.core.link.session.IdentityRotation
import com.code2hack.eyebrowse.core.link.session.PeerTrustRead

/**
 * Couples Phone identity usability to the authoritative tri-state trust read.
 *
 * Construction is intentionally side-effect free: a VALID/CORRUPT store plus an inadequate
 * existing alias must not rotate or prevent the pairing surface from opening. Identity usability
 * is checked only when the listener is started. Explicit Forget clears trust first; only then can
 * this coordinator legally repair/rotate an inadequate alias because the trust read is ABSENT.
 */
internal class PhoneIdentityRecovery(
    private val readTrust: () -> PeerTrustRead,
    private val ensureIdentity: (regenerateIfInadequate: Boolean) -> Unit,
    private val readCurrentFingerprint: () -> String,
) {
    /** Throws on an inadequate VALID/CORRUPT alias; rotates only when trust is definitively ABSENT. */
    fun ensureUsableIdentity() {
        ensureIdentity(IdentityRotation.regenerateAllowed(readTrust()))
    }

    /**
     * Best-effort repair after the caller has explicitly cleared trust. If the clear did not
     * produce ABSENT, IdentityRotation still refuses regeneration and this remains fail-closed.
     */
    fun repairAfterForget(): Result<Unit> = runCatching { ensureUsableIdentity() }

    /** Read lazily by invitation generation so post-Forget rotation cannot leave a stale QR pin. */
    fun currentFingerprint(): String = readCurrentFingerprint()
}
