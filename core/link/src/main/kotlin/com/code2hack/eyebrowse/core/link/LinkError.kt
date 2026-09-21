package com.code2hack.eyebrowse.core.link

/**
 * Link error taxonomy — exactly the recoverable error classes required by ticket plan §7.
 * Camera/scan members are defined here once and consumed by the T02 scanner slice.
 */
sealed class LinkError(val wireCode: String) {

    /** Locator(s) could not be reached within the operation budget. */
    object NetworkUnreachable : LinkError("NETWORK_UNREACHABLE")

    /** Invitation id unknown, secret mismatch, or structurally unusable at consume time. */
    object InvitationInvalid : LinkError("INVITATION_INVALID")

    /** Invitation was valid but its monotonic TTL elapsed. */
    object InvitationExpired : LinkError("INVITATION_EXPIRED")

    /** Invitation was explicitly cancelled (or replaced) before use. */
    object InvitationCancelled : LinkError("INVITATION_CANCELLED")

    /** Invitation was already consumed by a successful pairing. */
    object InvitationReused : LinkError("INVITATION_REUSED")

    /** RG proof-of-possession / invitation authentication failed. */
    object AuthenticationFailed : LinkError("AUTHENTICATION_FAILED")

    /** Presented/stored peer key is not the expected Phone identity. */
    object WrongPhoneIdentity : LinkError("WRONG_PHONE_IDENTITY")

    /** Presented/stored peer key is not the expected RG identity. */
    object WrongRgIdentity : LinkError("WRONG_RG_IDENTITY")

    /** A different peer is presented; explicit Forget is required first (both directions). */
    object PeerReplacementRequired : LinkError("PEER_REPLACEMENT_REQUIRED")

    /** Protocol major mismatch or missing required capability. */
    object IncompatibleProtocol : LinkError("INCOMPATIBLE_PROTOCOL")

    /** RG camera permission not granted (T02 scanner). */
    object CameraPermissionDenied : LinkError("CAMERA_PERMISSION_DENIED")

    /** RG camera could not be opened/produced frames (T02 scanner). */
    object CameraUnavailable : LinkError("CAMERA_UNAVAILABLE")

    /** User cancelled the scanner (T02 scanner). */
    object ScanCancelled : LinkError("SCAN_CANCELLED")

    /** QR payload failed v1 structural validation. */
    object MalformedQr : LinkError("MALFORMED_QR")

    companion object {
        fun fromWireCode(code: String): LinkError? = when (code) {
            NetworkUnreachable.wireCode -> NetworkUnreachable
            InvitationInvalid.wireCode -> InvitationInvalid
            InvitationExpired.wireCode -> InvitationExpired
            InvitationCancelled.wireCode -> InvitationCancelled
            InvitationReused.wireCode -> InvitationReused
            AuthenticationFailed.wireCode -> AuthenticationFailed
            WrongPhoneIdentity.wireCode -> WrongPhoneIdentity
            WrongRgIdentity.wireCode -> WrongRgIdentity
            PeerReplacementRequired.wireCode -> PeerReplacementRequired
            IncompatibleProtocol.wireCode -> IncompatibleProtocol
            CameraPermissionDenied.wireCode -> CameraPermissionDenied
            CameraUnavailable.wireCode -> CameraUnavailable
            ScanCancelled.wireCode -> ScanCancelled
            MalformedQr.wireCode -> MalformedQr
            else -> null
        }
    }
}
