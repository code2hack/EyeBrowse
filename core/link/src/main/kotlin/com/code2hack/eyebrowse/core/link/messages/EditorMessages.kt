package com.code2hack.eyebrowse.core.link.messages

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import kotlinx.serialization.Serializable

/** Metadata only: no value, selection text, HTML or keyboard payload in a state/result. */
@Serializable data class EditorStateMessage(
    val context: ControlContext,
    val target: EditorTarget?,
    val kind: EditorKind?,
    val enter: EditorEnter?,
    val ready: Boolean,
) : BrowserControlMessage {
    init { require(!ready || (target != null && kind != null && enter != null)) }
}

@Serializable data class EditorCloseMessage(val requestId: String, val context: ControlContext, val target: EditorTarget) : BrowserControlMessage {
    init { require(requestId.isNotBlank() && requestId.length <= 128) }
}

@Serializable data class EditorCloseResultMessage(val requestId: String, val context: ControlContext, val closed: Boolean) : BrowserControlMessage {
    init { require(requestId.isNotBlank() && requestId.length <= 128) }
}

/** Transition ids are monotonically increasing within the authenticated connection. */
@Serializable data class ViewportUpdateMessage(val transitionId: Long, val context: ControlContext,
                                             val profile: PresentationProfile) : BrowserControlMessage {
    init { require(transitionId > 0) }
}

@Serializable data class ViewportUpdateResultMessage(val transitionId: Long, val accepted: Boolean,
                                                   val context: ControlContext, val profile: PresentationProfile?) : BrowserControlMessage {
    init { require(transitionId > 0) }
}
