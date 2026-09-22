package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.*
import org.junit.Assert.*
import org.junit.Test

class CommandSequenceTest {
    private val ctx=ControlContext("life",1,"doc",2,3)
    @Test fun recreationAndReconnectRetainOrdinalAcrossFullContextChanges() {
        var saved:CommandSequence.Cursor?=null
        fun factory()=CommandSequence({saved}) { saved=it;true }
        assertEquals(1L,factory().next(ctx,BrowserAction.Reload)!!.commandSequence)
        val next=factory().next(ctx.copy(documentId="new",viewportEpoch=9),BrowserAction.Back)!!
        assertEquals(2L,next.commandSequence)
        assertEquals(BrowserCommandId.create(next.context,2),next.commandId)
        assertEquals(1L,factory().next(ctx.copy(controlEpoch=2),BrowserAction.Forward)!!.commandSequence)
    }
    @Test fun failedPersistenceAndExhaustedSequenceNeverProduceSendableCommand() {
        assertNull(CommandSequence({null}) { false }.next(ctx,BrowserAction.Reload))
        assertNull(CommandSequence({CommandSequence.Cursor("life",1,Long.MAX_VALUE)}) { true }.next(ctx,BrowserAction.Reload))
    }
    @Test fun stableEpochHasNoSmallActionCeilingAndEachIdIsUnique() {
        var saved:CommandSequence.Cursor?=null
        val source=CommandSequence({saved}) { saved=it;true }
        for(i in 1L..10_001L) assertEquals(i,source.next(ctx,BrowserAction.ScrollBy(0f,1f))!!.commandSequence)
    }
}
