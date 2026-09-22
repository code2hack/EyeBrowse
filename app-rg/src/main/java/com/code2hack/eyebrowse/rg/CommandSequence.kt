package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.BrowserActionMessage

/** Reserve before send. One durable cursor, not an ID cache; uncertain effects are never retried. */
internal class CommandSequence(
    private val read: () -> Cursor?,
    private val persist: (Cursor) -> Boolean,
) {
    data class Cursor(val lifetime: String, val epoch: Long, val sequence: Long)
    @Synchronized fun next(context: ControlContext, action: BrowserAction): BrowserActionMessage? {
        val old=read()
        val last=if (old?.lifetime==context.lifetimeId && old.epoch==context.controlEpoch) old.sequence else 0
        if (last < 0 || last==Long.MAX_VALUE) return null
        val sequence=last+1
        if (!persist(Cursor(context.lifetimeId,context.controlEpoch,sequence))) return null
        return BrowserActionMessage(BrowserCommandId.create(context,sequence),context,action,sequence)
    }
}
