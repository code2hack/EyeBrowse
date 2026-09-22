package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.ControlContext
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile

internal enum class LocalInputAction { RECENTER, RETRY, PAIR, USE_GLASSES, USE_PHONE, BACK, FORWARD, RELOAD }

/** A local delayed-intent fence. It is never sent on the wire or persisted as command authority. */
internal data class RgInputSnapshot(
    val context: ControlContext?, val owner: ControlOwner?, val profile: PresentationProfile?,
    val revision: Long, val pageReady: Boolean, val handoffReady: Boolean,
    val canGoBack: Boolean, val canGoForward: Boolean,
    val reservationRevision: Long = 0,
) {
    fun allows(action: LocalInputAction): Boolean = when(action) {
        LocalInputAction.RECENTER,LocalInputAction.RETRY,LocalInputAction.PAIR -> true
        LocalInputAction.USE_GLASSES -> handoffReady && owner==ControlOwner.PHONE
        LocalInputAction.USE_PHONE -> handoffReady && owner==ControlOwner.RG
        LocalInputAction.BACK -> pageReady && owner==ControlOwner.RG && canGoBack
        LocalInputAction.FORWARD -> pageReady && owner==ControlOwner.RG && canGoForward
        LocalInputAction.RELOAD -> pageReady && owner==ControlOwner.RG
    }
}
