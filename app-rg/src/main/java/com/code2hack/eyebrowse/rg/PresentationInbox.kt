package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.ControlContext
import com.code2hack.eyebrowse.core.link.framing.PresentationFrame
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile

/** One pending encoded frame; decode and UI delivery recheck this grant after asynchronous work. */
internal class PresentationInbox {
    private var context: ControlContext? = null
    private var profile: PresentationProfile? = null
    private var sequence = -1L
    private var displayed = -1L
    private var pending: PresentationFrame? = null
    var dropped = 0L
        private set
    @Synchronized fun grant(context: ControlContext?, profile: PresentationProfile?) {
        require((context == null) == (profile == null))
        if (this.context != context || this.profile != profile) {
            pending = null; sequence = -1; displayed = -1
        }
        this.context = context; this.profile = profile
    }
    @Synchronized fun offer(frame: PresentationFrame): Boolean {
        val h = frame.header
        if (h.context != context || h.width != profile?.width || h.height != profile?.height || h.frameSeq <= sequence) return false
        if (pending != null) dropped++
        pending = frame; sequence = h.frameSeq
        return true
    }
    @Synchronized fun take(): PresentationFrame? = pending.also { pending = null }
    @Synchronized fun current(frame: PresentationFrame): Boolean = frame.header.let {
        it.context == context && it.width == profile?.width && it.height == profile?.height && it.frameSeq > displayed
    }
    @Synchronized fun displayed(frame: PresentationFrame): Boolean {
        if (!current(frame)) return false
        displayed = frame.header.frameSeq
        return true
    }
}
