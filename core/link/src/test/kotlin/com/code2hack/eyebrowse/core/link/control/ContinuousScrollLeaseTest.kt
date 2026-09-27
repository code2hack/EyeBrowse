package com.code2hack.eyebrowse.core.link.control

import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class ContinuousScrollLeaseTest {
    private val state = ControlSnapshot(ControlContext("life",1,"doc",1,1),ControlOwner.RG,
        PresentationProfile(480,640,160),true,PresentationStatus.READY,true,true)
    private fun lease(): ContinuousScrollLease { var id=0;return ContinuousScrollLease { "id-${++id}" } }
    private fun input(c: ScrollCreditMessage, speed: Double) = ScrollVelocityMessage(c.context,c.leaseId,c.credit,speed)
    @Test fun finalStopLossExpiresWithoutCaptureLeaseOrLinkTimeout() {
        val l=lease();var c=l.start(ScrollStartMessage("start",state.context),state,1,100)!!
        c=l.update(input(c,0.0),state,1,120)!!
        c=l.update(input(c,600.0),state,1,150)!!
        assertEquals(30,l.delta(state,1,200))
        assertEquals(30,l.delta(state,1,449)) // bounded dt, no catch-up burst
        assertEquals(0,l.delta(state,1,450));assertFalse(l.active)
        assertNull(l.update(input(c,600.0),state,1,451))
    }
    @Test fun replayAndDelayedCreditsCannotRenewAuthority() {
        val l=lease();val start=ScrollStartMessage("start",state.context)
        val c=l.start(start,state,1,100)!!
        assertEquals(c,l.start(start,state,1,200));assertEquals(400L,l.deadlineMs)
        val next=l.update(input(c,0.0),state,1,250)!!
        assertNull(l.update(input(c,1200.0),state,1,300));assertEquals(550L,l.deadlineMs)
        assertNull(l.update(input(next,1200.0),state,1,550));assertFalse(l.active)
    }
    @Test fun neutralAndAllAuthorityBoundariesFenceMotion() {
        val l=lease();val c=l.start(ScrollStartMessage("start",state.context),state,1,100)!!
        assertNull(l.update(input(c,10.0),state,1,110));assertFalse(l.active)
        for (changed in listOf(state.copy(owner=ControlOwner.PHONE),state.copy(linkAuthenticated=false),
            state.copy(hostingActive=false),state.copy(presentationStatus=PresentationStatus.STALE),
            state.copy(context=state.context.copy(documentId="next")),state.copy(context=state.context.copy(viewportEpoch=2)))) {
            val grant=l.start(ScrollStartMessage("new",state.context),state,1,200)!!
            l.update(input(grant,0.0),state,1,210)
            assertEquals(0,l.delta(changed,1,220));assertFalse(l.active)
        }
        val grant=l.start(ScrollStartMessage("connection",state.context),state,1,300)!!
        assertNull(l.update(input(grant,0.0),state,2,310));assertFalse(l.active)
    }
    @Test fun oldStopCannotStopSuccessorAndMalformedStateIsRejected() {
        val l=lease();val old=l.start(ScrollStartMessage("old",state.context),state,1,100)!!
        val fresh=l.start(ScrollStartMessage("fresh",state.context),state,1,110)!!
        l.stop(ScrollStopMessage(state.context,old.leaseId),state,1,120);assertTrue(l.active)
        l.stop(ScrollStopMessage(state.context,fresh.leaseId),state,1,120);assertFalse(l.active)
        for (speed in listOf(Double.NaN,Double.POSITIVE_INFINITY,1200.1))
            assertThrows(IllegalArgumentException::class.java) { input(old,speed) }
    }
    @Test fun recoveredConnectionRejectsOldCreditAndNeedsFreshNeutralBeforeMotion() {
        val l=lease();var old=l.start(ScrollStartMessage("old",state.context),state,1,100)!!
        old=l.update(input(old,0.0),state,1,110)!!
        old=l.update(input(old,600.0),state,1,120)!!
        assertEquals(30,l.delta(state,1,170))
        assertEquals(0,l.delta(state.copy(linkAuthenticated=false),1,180))
        val recovered=state.copy(context=state.context.copy(viewportEpoch=2))
        assertNull(l.start(ScrollStartMessage("old",state.context),recovered,2,200))
        val fresh=l.start(ScrollStartMessage("fresh",recovered.context),recovered,2,210)!!
        val deadline=l.deadlineMs
        assertNull(l.update(input(old,600.0),recovered,2,220))
        l.stop(ScrollStopMessage(old.context,old.leaseId),recovered,2,220)
        assertEquals(deadline,l.deadlineMs);assertTrue(l.active)
        assertEquals(0,l.delta(recovered,2,230))
        assertNull(l.update(input(fresh,600.0),recovered,2,240));assertFalse(l.active)
        var neutral=l.start(ScrollStartMessage("neutral",recovered.context),recovered,2,250)!!
        neutral=l.update(input(neutral,0.0),recovered,2,260)!!
        l.update(input(neutral,600.0),recovered,2,270)!!
        assertEquals(30,l.delta(recovered,2,320))
        assertEquals(0,l.delta(recovered,2,570));assertFalse(l.active)
    }
}
