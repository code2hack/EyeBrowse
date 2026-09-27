package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.ControlContext
import com.code2hack.eyebrowse.core.link.framing.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class PresentationInboxTest {
    private val context = ControlContext("life",1,"doc",2,3)
    private val profile = PresentationProfile(480,600,160)
    private fun frame(seq: Long, ctx: ControlContext = context) = PresentationFrame(
        PresentationFrameHeader(ctx,seq,1000,480,600), byteArrayOf(1))
    @Test fun slowDecoderKeepsOnlyLatestPendingFrame() {
        val inbox = PresentationInbox(); inbox.grant(context,profile)
        for (i in 1..1000) assertTrue(inbox.offer(frame(i.toLong())))
        assertEquals(999L,inbox.dropped)
        assertEquals(1000L,inbox.take()!!.header.frameSeq)
        assertNull(inbox.take())
    }
    @Test fun disconnectAndNewGrantFenceDecodeAlreadyInFlight() {
        val inbox = PresentationInbox(); inbox.grant(context,profile)
        inbox.offer(frame(1)); val decoding = inbox.take()!!
        inbox.grant(null,null); assertFalse(inbox.current(decoding))
        inbox.grant(context.copy(viewportEpoch=3),profile)
        assertFalse(inbox.displayed(decoding)); assertFalse(inbox.offer(frame(2)))
    }
    @Test fun reorderedFramesAndWrongGeometryNeverReachDisplay() {
        val inbox = PresentationInbox(); assertFalse(inbox.offer(frame(1)))
        inbox.grant(context,profile); assertTrue(inbox.offer(frame(2)))
        assertFalse(inbox.offer(frame(1))); assertTrue(inbox.displayed(inbox.take()!!))
        assertFalse(inbox.displayed(frame(2)))
        inbox.grant(context,profile.copy(width=479)); assertFalse(inbox.offer(frame(3)))
    }
    @Test fun duplicateStatusGrantDoesNotResetCaptureIdentityOrPermitDoubleDisplay() {
        val inbox=PresentationInbox();inbox.grant(context,profile)
        val original=frame(7)
        assertTrue(inbox.offer(original));assertSame(original,inbox.take())
        assertTrue(inbox.displayed(original))
        repeat(2) { inbox.grant(context,profile) }
        assertFalse(inbox.offer(original));assertFalse(inbox.displayed(original))
        assertNull(inbox.take())
        val fresh=context.copy(viewportEpoch=context.viewportEpoch+1)
        inbox.grant(fresh,profile)
        assertFalse(inbox.offer(frame(8)));assertFalse(inbox.displayed(original))
        val next=frame(1,fresh)
        assertTrue(inbox.offer(next));assertTrue(inbox.displayed(inbox.take()!!))
        assertFalse(inbox.displayed(next))
    }
}
