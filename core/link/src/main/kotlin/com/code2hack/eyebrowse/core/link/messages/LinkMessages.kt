package com.code2hack.eyebrowse.core.link.messages

import kotlinx.serialization.Serializable

/**
 * Versioned v1 application messages (ticket plan §4.4/§5), carried as bounded JSON frames.
 * Field names are wire contract; unknown JSON fields are ignored by the v1 decoder
 * (kotlinx ignoreUnknownKeys) and unknown message types are explicitly ignorable notifications.
 */
@Serializable
data class HelloMessage(val pmj: Int, val pmm: Int, val caps: List<String>) {
    fun hasRequiredCapabilities(): Boolean =
        com.code2hack.eyebrowse.core.link.LinkProtocol.REQUIRED_CAPABILITIES.all { caps.contains(it) }
}

/** RG → Phone, initial pairing proof (plan §4.2 steps 2–4). Byte fields are base64url. */
@Serializable
data class PairAuthMessage(
    val iid: String,
    val sec: String,
    val rgSpki: String,
    val nonce: String,
    val sig: String,
    val hello: HelloMessage,
)

/** RG → Phone, reconnect proof (no invitation secret; plan §4.2 "Reconnect"). */
@Serializable
data class ReconnectAuthMessage(
    val rgSpki: String,
    val nonce: String,
    val sig: String,
    val hello: HelloMessage,
)

/** Phone → RG, fresh single-use authentication nonce. */
@Serializable
data class ChallengeMessage(val nonce: String)

@Serializable
object AuthOkMessage

@Serializable
data class AuthErrMessage(val code: String)

/** Authenticated host-status observation (never a control grant). */
@Serializable
data class StatusMessage(val state: String) {
    companion object {
        fun of(value: com.code2hack.eyebrowse.core.link.HostStatusValue): StatusMessage =
            StatusMessage(value.name)
    }
}

@Serializable
object PingMessage

@Serializable
object PongMessage

/** Best-effort pre-close Forget notice; security never depends on delivery (plan §8). */
@Serializable
object ForgetNoticeMessage
