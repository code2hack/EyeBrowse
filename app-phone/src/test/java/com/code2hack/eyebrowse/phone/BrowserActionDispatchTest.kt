package com.code2hack.eyebrowse.phone

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class BrowserActionDispatchTest {
    private val profile=PresentationProfile(480,527,204)
    private fun ready(document:()->String={"doc"}, effect:(BrowserAction)->Unit):PhoneControlCoordinator {
        val c=PhoneControlCoordinator(document,executeAction=effect,measurePhone={profile})
        c.authority.setHostingGeneration(1,true);c.onAuthenticatedSession(true)
        c.receive(HandoffRequestMessage(HandoffTargetWire.RG,0,profile))
        assertTrue(c.authority.markPresentationReady(c.authority.snapshot().context))
        return c
    }
    private fun request(c:PhoneControlCoordinator,seq:Long,action:BrowserAction=BrowserAction.Reload):BrowserActionMessage {
        val ctx=c.authority.snapshot().context
        return BrowserActionMessage(BrowserCommandId.create(ctx,seq),ctx,action,seq)
    }
    @Test fun admittedActionsDispatchOnceAndNeverClaimPageSuccess() {
        val calls=mutableListOf<BrowserAction>();val c=ready(effect={calls.add(it)})
        val actions=listOf(BrowserAction.Back,BrowserAction.Forward,BrowserAction.Reload,BrowserAction.ActivateAt(12f,20f),BrowserAction.ScrollBy(0f,200f))
        actions.forEachIndexed { i,a ->
            val req=request(c,i+1L,a);val result=c.receive(req) as BrowserActionResultMessage
            assertTrue(result.accepted);assertNull(result.effectSucceeded)
            assertFalse((c.receive(req) as BrowserActionResultMessage).accepted)
        }
        assertEquals(actions,calls)
    }
    @Test fun missedDocumentAndOldOwnerAreRejectedBeforeAnyEffect() {
        var doc="a";val calls=mutableListOf<BrowserAction>();val c=ready({doc}) {calls.add(it)}
        val old=request(c,100);doc="b"
        assertEquals("STALE_CONTEXT",(c.receive(old) as BrowserActionResultMessage).reason)
        c.receive(HandoffRequestMessage(HandoffTargetWire.PHONE,c.authority.snapshot().controlEpoch))
        assertFalse((c.receive(request(c,101)) as BrowserActionResultMessage).accepted)
        assertTrue(calls.isEmpty())
    }
    @Test fun uncertainDispatchIsNotReplayedWithResolvedOrdinal() {
        var calls=0;val c=ready { calls++;throw IllegalStateException() }
        val req=request(c,1);val result=c.receive(req) as BrowserActionResultMessage
        assertTrue(result.accepted);assertNull(result.effectSucceeded);assertEquals("DISPATCH_UNCERTAIN",result.reason)
        assertEquals("STALE_COMMAND_SEQUENCE",(c.receive(req) as BrowserActionResultMessage).reason)
        assertEquals(1,calls)
    }
    @Test fun readinessRejectionCannotBecomeAnEffectAfterRecovery() {
        var calls=0;val c=ready { calls++ };val ctx=c.authority.snapshot().context;val req=request(c,1)
        c.authority.markPresentationStale(ctx)
        assertFalse((c.receive(req) as BrowserActionResultMessage).accepted)
        c.authority.markPresentationReady(ctx)
        assertFalse((c.receive(req) as BrowserActionResultMessage).accepted)
        assertEquals(0,calls)
        assertTrue((c.receive(request(c,2)) as BrowserActionResultMessage).accepted)
        assertEquals(1,calls)
    }
    @Test fun explicitStopRevokesRgAuthorityWithoutBorrowingPrivateGeometry() {
        var calls=0;val c=ready { calls++ };val old=request(c,1)
        c.authority.setHostingGeneration(1,false)
        c.authority.returnToPhoneAfterStop(null)
        assertEquals(ControlOwner.PHONE,c.authority.snapshot().owner)
        assertNull(c.authority.snapshot().profile)
        assertFalse((c.receive(old) as BrowserActionResultMessage).accepted)
        assertEquals(0,calls)
        val epoch=c.authority.snapshot().controlEpoch
        c.authority.returnToPhoneAfterStop(null)
        assertEquals(epoch,c.authority.snapshot().controlEpoch)
        assertTrue(c.authority.updatePhoneViewport(profile))
        assertEquals(profile,c.authority.snapshot().profile)
    }
}
