package com.code2hack.eyebrowse.core.link

/**
 * Fixed v0.0.1 link/protocol constants (ticket plan §4–§5). Values are product-safety bounds,
 * not agent work-time budgets.
 */
object LinkProtocol {
    /** Minor 2 adds bounded editor/address and same-owner viewport messages; trust is unchanged. */
    const val MAJOR: Int = 1
    const val MINOR: Int = 2

    const val CAP_PAIRING_V1: String = "PAIRING_V1"
    const val CAP_STATUS_V1: String = "STATUS_V1"
    const val CAP_PRESENTATION_V1: String = "PRESENTATION_V1"
    const val CAP_HANDOFF_V1: String = "HANDOFF_V1"
    const val CAP_BROWSER_ACTIONS_V1: String = "BROWSER_ACTIONS_V1"
    const val CAP_TEXT_INPUT_V1: String = "TEXT_INPUT_V1"
    const val CAP_RG_VIEWPORT_UPDATE_V1: String = "RG_VIEWPORT_UPDATE_V1"

    /** Capability strings every v1 endpoint must advertise and require for the base link. */
    val REQUIRED_CAPABILITIES: List<String> = listOf(
        CAP_PAIRING_V1,
        CAP_STATUS_V1,
    )

    /** #7 capabilities negotiated after authentication; absent means update-required for #7. */
    val PRESENTATION_CAPABILITIES: List<String> = listOf(
        CAP_PRESENTATION_V1,
        CAP_HANDOFF_V1,
        CAP_BROWSER_ACTIONS_V1,
    )

    val KEYBOARD_CAPABILITIES: List<String> = listOf(CAP_TEXT_INPUT_V1, CAP_RG_VIEWPORT_UPDATE_V1)
    val ALL_CAPABILITIES: List<String> = REQUIRED_CAPABILITIES + PRESENTATION_CAPABILITIES + KEYBOARD_CAPABILITIES

    /** The one documented fixed application port for v0.0.1. */
    const val LOCAL_PORT: Int = 39818

    /** QR invitation TTL hint, seconds, measured on the Phone's monotonic clock. */
    const val INVITATION_TTL_SECONDS: Int = 180

    /** QR payload structural version (independent of the protocol version). */
    const val QR_PAYLOAD_VERSION: Int = 1

    /** Invitation id: 128-bit random value. */
    const val INVITATION_ID_BYTES: Int = 16

    /** Invitation secret: 256-bit random value, base64url on the wire, never persisted. */
    const val INVITATION_SECRET_BYTES: Int = 32

    /** Authentication nonce: 256-bit, fresh per challenge, single-use. */
    const val NONCE_BYTES: Int = 32

    /** Maximum decoded application frame (bytes). */
    const val FRAME_MAX_BYTES: Int = 32 * 1024

    /** Maximum post-auth encoded presentation record, including its metadata header. */
    const val PRESENTATION_RECORD_MAX_BYTES: Int = 1 * 1024 * 1024

    /** Maximum serialized metadata prefix carried before encoded presentation pixels. */
    const val PRESENTATION_METADATA_MAX_BYTES: Int = 8 * 1024

    /** Bounded outbound queue depth. */
    const val OUTBOUND_QUEUE_MAX: Int = 16

    /** Maximum QR payload length accepted by the v1 codec. */
    const val QR_PAYLOAD_MAX_CHARS: Int = 1024

    /** Maximum locator candidates carried/validated. */
    const val LOCATOR_MAX_CANDIDATES: Int = 8
}

/** Operation/heartbeat timings; tests may shrink them through [LinkTimings]. */
data class LinkTimings(
    val connectTimeoutMs: Long = 3_000,
    val authTimeoutMs: Long = 5_000,
    val heartbeatIntervalMs: Long = 10_000,
    val livenessTimeoutMs: Long = 30_000,
    /** Total budget for one user-triggered connect/retry operation across locator attempts. */
    val operationBudgetMs: Long = 10_000,
) {
    companion object {
        val PRODUCT: LinkTimings = LinkTimings()
    }
}
