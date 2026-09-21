package com.code2hack.eyebrowse.core.link.messages

import kotlinx.serialization.Serializable
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile

/**
 * Versioned v1 application messages (ticket plan §4.4/§5), carried as bounded JSON frames.
 * Field names are wire contract; unknown JSON fields are ignored by the v1 decoder
 * (kotlinx ignoreUnknownKeys) and unknown message types are explicitly ignorable notifications.
 */
@Serializable
data class HelloMessage(val pmj: Int, val pmm: Int, val caps: List<String>) {
    fun hasRequiredCapabilities(): Boolean =
        com.code2hack.eyebrowse.core.link.LinkProtocol.REQUIRED_CAPABILITIES.all { caps.contains(it) }

    fun hasPresentationCapabilities(): Boolean =
        com.code2hack.eyebrowse.core.link.LinkProtocol.PRESENTATION_CAPABILITIES.all { caps.contains(it) }
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

@Serializable
enum class HandoffTargetWire { PHONE, RG }

/** #7 messages are admitted only on an authenticated, negotiated presentation session. */
sealed interface BrowserControlMessage

@Serializable
data class HandoffRequestMessage(
    val target: HandoffTargetWire,
    val observedControlEpoch: Long,
    val profile: PresentationProfile? = null,
) : BrowserControlMessage

@Serializable
data class HandoffResultMessage(
    val accepted: Boolean,
    val owner: com.code2hack.eyebrowse.core.link.control.ControlOwner,
    val context: com.code2hack.eyebrowse.core.link.control.ControlContext,
    val profile: PresentationProfile? = null,
    val reason: String? = null,
) : BrowserControlMessage

@Serializable
data class BrowserStateMessage(
    val owner: com.code2hack.eyebrowse.core.link.control.ControlOwner,
    val context: com.code2hack.eyebrowse.core.link.control.ControlContext,
    val profile: PresentationProfile? = null,
    val url: String? = null,
    val title: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loading: Boolean = false,
    val stale: Boolean = true,
    val error: String? = null,
) : BrowserControlMessage {
    init {
        require(url == null || url.length <= 2048)
        require(title == null || title.length <= 512)
        require(error == null || error.length <= 512)
    }
}

@Serializable
data class BrowserActionMessage(
    val commandId: String,
    val context: com.code2hack.eyebrowse.core.link.control.ControlContext,
    val action: com.code2hack.eyebrowse.core.link.control.BrowserAction,
) : BrowserControlMessage {
    init { require(commandId.isNotBlank() && commandId.length <= 128) }
}

/** Accepted is admission only. Unknown page effect stays null and is never blindly replayed. */
@Serializable
data class BrowserActionResultMessage(
    val commandId: String,
    val accepted: Boolean,
    val effectSucceeded: Boolean? = null,
    val reason: String? = null,
) : BrowserControlMessage {
    init {
        require(commandId.isNotBlank() && commandId.length <= 128)
        require(reason == null || reason.length <= 128)
    }
}

@Serializable
data class PresentationStopMessage(
    val context: com.code2hack.eyebrowse.core.link.control.ControlContext,
    val reason: String,
) : BrowserControlMessage { init { require(reason.length <= 128) } }

@Serializable
data class PresentationStaleMessage(
    val context: com.code2hack.eyebrowse.core.link.control.ControlContext,
    val reason: String,
) : BrowserControlMessage { init { require(reason.length <= 128) } }
