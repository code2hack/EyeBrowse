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
        val adapter=PhoneControlCoordinator({ "doc" }) { local }
        val initial=adapter.authority.snapshot()
        val denied=adapter.receive(BrowserActionMessage(BrowserCommandId.create(initial.context,1),initial.context,BrowserAction.Back,1)) as BrowserActionResultMessage
        assertFalse(denied.accepted)
        assertEquals("WRONG_OWNER",denied.reason)
        adapter.authority.setHostingGeneration(1,true);adapter.authority.setAuthenticated(true,true)
        val handoff=adapter.receive(HandoffRequestMessage(HandoffTargetWire.RG,0,remote)) as HandoffResultMessage
        assertTrue(handoff.accepted)
        assertEquals(HostingPresentationProfile(440,570,160),adapter.privateProfile())
        assertFalse(adapter.authority.updatePhoneViewport(local))
        assertTrue(adapter.authority.markPresentationReady(handoff.context))
        val admitted=adapter.receive(BrowserActionMessage(BrowserCommandId.create(handoff.context,2),handoff.context,BrowserAction.Back,2)) as BrowserActionResultMessage
        assertTrue(admitted.accepted);assertNull(admitted.effectSucceeded)
        val takeover=adapter.receive(HandoffRequestMessage(HandoffTargetWire.PHONE,handoff.context.controlEpoch,remote)) as HandoffResultMessage
        assertEquals(local,takeover.profile);assertNull(adapter.privateProfile())
        assertFalse(adapter.authority.markPresentationReady(handoff.context))
    }

    @Test fun missedBrowserListenerIsReconciledByLinkLifecycleAndFirstAdmission() {
        // Mutable browser source advances without firing its detached listener. These are the
        // production adapter lifecycle hooks used by PhoneLinkServer, not manual authority setters.
        var browserDocument="A"
        var pendingGrant=true
        var invalidations=0
        val adapter=PhoneControlCoordinator(
            readDocumentIdentity={ browserDocument },
            invalidatePresentation={ pendingGrant=false;invalidations++ },
            measurePhone={ PresentationProfile(1000,1600,450) },
        )
        adapter.authority.setHostingGeneration(1,true)
        adapter.onLinkStarting()
        adapter.onAuthenticatedSession(true)
        val oldA=adapter.authority.snapshot().context
        adapter.onLinkStopped() // Listener detached; no callbacks from subsequent browser changes.
        browserDocument="B"
        pendingGrant=true
        val count=invalidations
        adapter.onLinkStarting()
        assertEquals("B",adapter.authority.snapshot().documentId)
        assertFalse(pendingGrant)
        assertEquals(count+1,invalidations)
        adapter.onAuthenticatedSession(true)
        val first=adapter.receive(HandoffRequestMessage(HandoffTargetWire.RG,0,PresentationProfile(440,570,160))) as HandoffResultMessage
        assertTrue(first.accepted)
        assertEquals("B",first.context.documentId)
        assertTrue(adapter.authority.markPresentationReady(first.context))
        val rejected=adapter.receive(BrowserActionMessage(BrowserCommandId.create(oldA,1),oldA,BrowserAction.Back,1)) as BrowserActionResultMessage
        assertFalse(rejected.accepted)
        assertEquals("STALE_CONTEXT",rejected.reason)
        assertNull(rejected.effectSucceeded)
        val wrongDocument=adapter.receive(BrowserActionMessage(BrowserCommandId.create(first.context,1),
            first.context.copy(documentId="A"),BrowserAction.Back,1)) as BrowserActionResultMessage
        assertFalse(wrongDocument.accepted)
        assertEquals("STALE_CONTEXT",wrongDocument.reason)
        val current=adapter.receive(BrowserActionMessage(BrowserCommandId.create(first.context,1),first.context,BrowserAction.Back,1)) as BrowserActionResultMessage
        assertTrue(current.accepted) // Rejected A actions never reached the admission/effect boundary.

        // Cover a change after start but before authenticated-session publication.
        browserDocument="C"; pendingGrant=true
        adapter.onAuthenticatedSession(true)
        assertEquals("C",adapter.authority.snapshot().documentId)
        assertFalse(pendingGrant)

        // And a missed notification immediately before a page action, without a reconnect.
        val prior=adapter.authority.snapshot().context
        adapter.authority.markPresentationReady(prior)
        browserDocument="D"; pendingGrant=true
        val stale=adapter.receive(BrowserActionMessage(BrowserCommandId.create(prior,2),prior,BrowserAction.ScrollBy(0f,1f),2)) as BrowserActionResultMessage
        assertEquals("STALE_CONTEXT",stale.reason)
        assertFalse(stale.accepted)
        assertEquals("D",adapter.authority.snapshot().documentId)
        assertFalse(pendingGrant)
    }

    @Test fun delayedResultsStayCorrelatedAcrossHandoffAndBrowserLifetime() {
        val profile=PresentationProfile(440,570,160)
        fun readyAdapter()=PhoneControlCoordinator({ "doc" }) { profile }.apply {
            authority.setHostingGeneration(1,true)
            onLinkStarting()
            onAuthenticatedSession(true)
            val handoff=receive(HandoffRequestMessage(HandoffTargetWire.RG,0,profile)) as HandoffResultMessage
            assertTrue(handoff.accepted)
            assertTrue(authority.markPresentationReady(handoff.context))
        }
        val requests=mutableListOf<BrowserActionMessage>()
        val results=mutableListOf<BrowserActionResultMessage>()
        fun admit(adapter: PhoneControlCoordinator,sequence: Long,action: BrowserAction) {
            val context=adapter.authority.snapshot().context
            val message=BrowserActionMessage(BrowserCommandId.create(context,sequence),context,action,sequence)
            val result=adapter.receive(message) as BrowserActionResultMessage
            assertTrue(result.accepted)
            assertNull(result.effectSucceeded) // T01 admission is not evidence of a page effect.
            requests.add(message)
            results.add(result)
        }
        val adapter=readyAdapter()
        admit(adapter,100,BrowserAction.Back)
        admit(adapter,101,BrowserAction.Forward)
        val old=adapter.authority.snapshot().context
        val phone=adapter.receive(HandoffRequestMessage(HandoffTargetWire.PHONE,old.controlEpoch)) as HandoffResultMessage
        assertTrue(phone.accepted)
        val glasses=adapter.receive(HandoffRequestMessage(HandoffTargetWire.RG,phone.context.controlEpoch,profile)) as HandoffResultMessage
        assertTrue(glasses.accepted)
        assertTrue(adapter.authority.markPresentationReady(glasses.context))
        admit(adapter,100,BrowserAction.Reload) // Same ordinal, distinct control epoch.
        val replacement=readyAdapter()
        assertNotEquals(old.lifetimeId,replacement.authority.snapshot().context.lifetimeId)
        admit(replacement,100,BrowserAction.ScrollBy(0f,1f)) // Same epoch/ordinal, distinct lifetime.

        val pending=requests.associateBy { it.commandId }.toMutableMap()
        assertEquals(4,pending.size)
        // Deliver later actions first, including results from the previous epoch/lifetime.
        for ((index,result) in results.reversed().withIndex()) {
            val encoded=LinkMessageCodec.encode(result)
            assertFalse(encoded.toString(Charsets.UTF_8).contains("commandSequence"))
            val decoded=(LinkMessageCodec.decode(encoded).getOrThrow() as LinkMessageCodec.Incoming.Known).message as BrowserActionResultMessage
            val request=pending.remove(decoded.commandId)
            assertNotNull(request)
            assertEquals(requests[requests.lastIndex-index],request)
        }
        assertTrue(pending.isEmpty())
    }

    @Test fun peerStatePublicationCannotGrantOwnership() {
        val c=PhoneControlCoordinator({ "doc" }) { null }
        val state=c.authority.snapshot()
        assertNull(c.receive(HandoffResultMessage(true,ControlOwner.RG,state.context,PresentationProfile(480,640,160))))
        assertEquals(ControlOwner.PHONE,c.authority.snapshot().owner)
    }
}
