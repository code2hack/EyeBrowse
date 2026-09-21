package com.code2hack.eyebrowse.core.link

/**
 * Pairing/connection state model (ticket plan §7). Hosting state and browser control are
 * separate concerns and never part of this model.
 */
enum class PairingState {
    /** No remembered peer trust on this endpoint. */
    UNPAIRED,

    /** Phone: a cancellable invitation exists. */
    INVITATION_READY,

    /** RG: scanner active (T02). */
    SCANNING,

    /** A connection attempt is in progress against candidate locator(s). */
    CONNECTING,

    /** TLS established; invitation/reconnect authentication in progress. */
    AUTHENTICATING,

    /** Trust remembered, no live link (after loss/restart; explicit Retry reconnects). */
    PAIRED_DISCONNECTED,

    /** Authenticated link established. */
    CONNECTED,

    /** Transient: Forget in progress on this endpoint. */
    FORGETTING,
}

/** Authenticated host-status observations (read-only projection; never a control grant). */
enum class HostStatusValue {
    HOST_INACTIVE,
    HOST_STARTING,
    HOSTING,
    HOST_STOPPING,
}
