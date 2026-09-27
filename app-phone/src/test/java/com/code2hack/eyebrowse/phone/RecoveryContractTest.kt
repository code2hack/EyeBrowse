package com.code2hack.eyebrowse.phone

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

/** Phone adapter recovery checks; callback counts are dispatch evidence, not website receipts. */
class RecoveryContractTest {
    private val profile = PresentationProfile(480, 600, 160)
    private fun ready(effect: (BrowserAction) -> Unit = {}) = PhoneControlCoordinator(
        readDocumentIdentity = { "live-document" }, executeAction = effect, measurePhone = { profile },
    ).apply {
        authority.setHostingGeneration(1, true)
        onAuthenticatedSession(true, true)
        val result = receive(HandoffRequestMessage(HandoffTargetWire.RG, 0, profile)) as HandoffResultMessage
        assertTrue(result.accepted)
        assertTrue(authority.markPresentationReady(result.context))
    }
    private fun request(context: ControlContext, ordinal: Long) = BrowserActionMessage(
        BrowserCommandId.create(context, ordinal), context, BrowserAction.ActivateAt(10f, 10f), ordinal,
    )

    @Test fun lostAcknowledgementDoesNotMakeADuplicateOrReconnectedOrdinalExecutable() {
        var effects = 0
        val phone = ready { effects++ }
        val before = phone.authority.snapshot()
        val original = request(before.context, 1)
        val lostResult = phone.receive(original) as BrowserActionResultMessage
        assertTrue(lostResult.accepted)
        assertNull(lostResult.effectSucceeded)
        assertEquals("STALE_COMMAND_SEQUENCE", (phone.receive(original) as BrowserActionResultMessage).reason)
        assertEquals(1, effects)

        phone.onLinkStopped()
        assertEquals(ControlOwner.RG, phone.authority.snapshot().owner)
        assertEquals(PresentationStatus.STALE, phone.authority.snapshot().presentationStatus)
        phone.onAuthenticatedSession(true, true)
        val recovered = phone.authority.snapshot()
        assertEquals(before.context.lifetimeId, recovered.context.lifetimeId)
        assertEquals(before.context.documentId, recovered.context.documentId)
        assertEquals(before.context.controlEpoch, recovered.context.controlEpoch)
        assertTrue(recovered.context.viewportEpoch > before.context.viewportEpoch)
        assertFalse(phone.authority.markPresentationReady(before.context))
        assertEquals("STALE_CONTEXT", (phone.receive(original) as BrowserActionResultMessage).reason)
        assertTrue(phone.authority.markPresentationReady(recovered.context))
        assertEquals("STALE_COMMAND_SEQUENCE", (phone.receive(request(recovered.context, 1)) as BrowserActionResultMessage).reason)
        assertEquals(1, effects)
        assertTrue((phone.receive(request(recovered.context, 2)) as BrowserActionResultMessage).accepted)
        assertEquals(2, effects)
    }

    @Test fun disconnectedPhoneTakeoverSurvivesReconnectAndRequiresNewExplicitRgConsent() {
        val phone = ready()
        val old = phone.authority.snapshot().context
        phone.onLinkStopped()
        val takeover = phone.receive(HandoffRequestMessage(HandoffTargetWire.PHONE, old.controlEpoch)) as HandoffResultMessage
        assertTrue(takeover.accepted)
        val unavailable = phone.receive(HandoffRequestMessage(HandoffTargetWire.RG, takeover.context.controlEpoch, profile)) as HandoffResultMessage
        assertEquals("LINK_UNAVAILABLE", unavailable.reason)
        phone.onAuthenticatedSession(true, true)
        assertEquals(ControlOwner.PHONE, phone.authority.snapshot().owner)
        assertFalse((phone.receive(request(old, 1)) as BrowserActionResultMessage).accepted)
        val regain = phone.receive(HandoffRequestMessage(HandoffTargetWire.RG, takeover.context.controlEpoch, profile)) as HandoffResultMessage
        assertTrue(regain.accepted)
        assertEquals(PresentationStatus.STALE, phone.authority.snapshot().presentationStatus)
        assertFalse(phone.authority.markPresentationReady(old))
        assertTrue(phone.authority.markPresentationReady(regain.context))
    }

    @Test fun replacementHostCannotAcceptPriorLifetimeEvenWithSameDocumentAndOrdinals() {
        val lostHost = ready()
        val old = lostHost.authority.snapshot().context
        var effects = 0
        val replacement = ready { effects++ }
        val fresh = replacement.authority.snapshot().context
        assertEquals(old.documentId, fresh.documentId)
        assertNotEquals(old.lifetimeId, fresh.lifetimeId)
        assertEquals("STALE_CONTEXT", (replacement.receive(request(old, 1)) as BrowserActionResultMessage).reason)
        assertEquals(0, effects)
        assertTrue((replacement.receive(request(fresh, 1)) as BrowserActionResultMessage).accepted)
        assertEquals(1, effects)
    }
    @Test fun duplicateHandoffDoesNotAdvanceOwnershipTwiceAndStopRejectsUncertainAction() {
        var effects=0
        val phone=ready { effects++ }
        val rg=phone.authority.snapshot().context
        val uncertain=request(rg,1)
        assertTrue((phone.receive(uncertain) as BrowserActionResultMessage).accepted)
        val handoff=HandoffRequestMessage(HandoffTargetWire.PHONE,rg.controlEpoch)
        assertTrue((phone.receive(handoff) as HandoffResultMessage).accepted)
        val returned=phone.authority.snapshot().context
        assertEquals(rg.controlEpoch+1,returned.controlEpoch)
        assertFalse((phone.receive(handoff) as HandoffResultMessage).accepted)
        assertEquals(returned,phone.authority.snapshot().context)
        phone.authority.setHostingGeneration(2,false)
        phone.onLinkStopped()
        assertFalse((phone.receive(uncertain) as BrowserActionResultMessage).accepted)
        assertEquals(1,effects)
    }
}
