package com.code2hack.eyebrowse.phone

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class PhoneControlCoordinatorTest {
    @Test fun peerCannotClaimPhoneSourceAndTakeoverUsesOnlyLocalMeasurement() {
        val local=PresentationProfile(1000,1600,450)
        val remote=PresentationProfile(440,570,160)
        val adapter=PhoneControlCoordinator("doc") { local }
        val initial=adapter.authority.snapshot()
        val denied=adapter.receive(BrowserActionMessage("1",initial.context,BrowserAction.Back)) as BrowserActionResultMessage
        assertFalse(denied.accepted)
        assertEquals("WRONG_OWNER",denied.reason)
        adapter.authority.setHostingGeneration(1,true);adapter.authority.setAuthenticated(true,true)
        val handoff=adapter.receive(HandoffRequestMessage(HandoffTargetWire.RG,0,remote)) as HandoffResultMessage
        assertTrue(handoff.accepted)
        assertEquals(HostingPresentationProfile(440,570,160),adapter.privateProfile())
        assertFalse(adapter.authority.updatePhoneViewport(local))
        assertTrue(adapter.authority.markPresentationReady(handoff.context))
        val admitted=adapter.receive(BrowserActionMessage("2",handoff.context,BrowserAction.Back)) as BrowserActionResultMessage
        assertTrue(admitted.accepted);assertNull(admitted.effectSucceeded)
        val takeover=adapter.receive(HandoffRequestMessage(HandoffTargetWire.PHONE,handoff.context.controlEpoch,remote)) as HandoffResultMessage
        assertEquals(local,takeover.profile);assertNull(adapter.privateProfile())
        assertFalse(adapter.authority.markPresentationReady(handoff.context))
    }

    @Test fun peerStatePublicationCannotGrantOwnership() {
        val c=PhoneControlCoordinator("doc") { null }
        val state=c.authority.snapshot()
        assertNull(c.receive(HandoffResultMessage(true,ControlOwner.RG,state.context,PresentationProfile(480,640,160))))
        assertEquals(ControlOwner.PHONE,c.authority.snapshot().owner)
    }
}
