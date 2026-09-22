package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class PendingHandoffTest {
    private val context=ControlContext("life",3,"doc",2,1)
    private val request=HandoffRequestMessage(HandoffTargetWire.RG,3,PresentationProfile(480,344,204))
    @Test fun duplicatePendingConsentCannotEnqueueAnOppositeHandoff() {
        val gate=PendingHandoff();val sent=mutableListOf<HandoffRequestMessage>()
        assertTrue(gate.request(context,request) { sent.add(it);true })
        assertFalse(gate.request(context,request.copy(target=HandoffTargetWire.PHONE)) { sent.add(it);true })
        assertEquals(listOf(request),sent);assertTrue(gate.busy)
    }
    @Test fun authoritativeResultOrStateReleasesThePendingRequest() {
        val gate=PendingHandoff();gate.request(context,request){true}
        gate.result(HandoffResultMessage(true,ControlOwner.RG,context.copy(controlEpoch=4)))
        assertFalse(gate.busy)
        gate.request(context,request){true};gate.observed(ControlOwner.RG,context.copy(controlEpoch=4));assertFalse(gate.busy)
    }
    @Test fun olderLifetimeOrEpochResultDoesNotUnlockCurrentPendingConsent() {
        val gate=PendingHandoff();gate.request(context,request){true}
        gate.result(HandoffResultMessage(false,ControlOwner.PHONE,context.copy(lifetimeId="old")))
        gate.result(HandoffResultMessage(false,ControlOwner.PHONE,context.copy(controlEpoch=2)))
        gate.observed(ControlOwner.RG,context)
        assertTrue(gate.busy)
    }
    @Test fun failedSendRejectionAndTimeoutDoNotRetry() {
        val gate=PendingHandoff();var sends=0
        assertFalse(gate.request(context,request){sends++;false});assertFalse(gate.busy)
        gate.request(context,request){sends++;true};gate.result(HandoffResultMessage(false,ControlOwner.PHONE,context))
        assertFalse(gate.busy)
        gate.request(context,request){sends++;true};gate.clear();assertFalse(gate.busy);assertEquals(3,sends)
    }
}
