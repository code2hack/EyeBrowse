package com.code2hack.eyebrowse.phone

import com.code2hack.eyebrowse.core.browser.AddressPolicy
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class RemoteAddressResultTest {
    @Test fun rejectionIsCorrelatedPreservesPageAndConsumesOrdinalWithoutReplaying() {
        var page = "https://existing.example/"
        var dispatches = 0
        val adapter = PhoneControlCoordinator({ "doc" }, executeAction = { action ->
            dispatches++
            val result = AddressPolicy.resolve((action as BrowserAction.OpenAddress).address)
            if (!result.accepted()) throw RemoteAddressRejected()
            page = result.url()
        }, measurePhone = { PresentationProfile(480,640,160) })
        adapter.authority.setHostingGeneration(1,true)
        adapter.onAuthenticatedSession(true,true)
        val handoff = adapter.receive(HandoffRequestMessage(HandoffTargetWire.RG,0,PresentationProfile(480,344,160))) as HandoffResultMessage
        fun request(n: Long, text: String) = BrowserActionMessage(BrowserCommandId.create(handoff.context,n),handoff.context,BrowserAction.OpenAddress(text),n)
        val invalid = request(1,"this is not an address")
        val rejected = adapter.receive(invalid) as BrowserActionResultMessage
        assertTrue(rejected.accepted); assertEquals(false,rejected.effectSucceeded)
        assertEquals("ADDRESS_REJECTED",rejected.reason); assertEquals(invalid.commandId,rejected.commandId)
        assertEquals("https://existing.example/",page)
        assertFalse((adapter.receive(invalid) as BrowserActionResultMessage).accepted)
        assertEquals(1,dispatches)
        val valid = adapter.receive(request(2,"example.com")) as BrowserActionResultMessage
        assertEquals("ADDRESS_OPENED",valid.reason); assertNull(valid.effectSucceeded)
        assertTrue(page.startsWith("https://example.com"))
    }
    @Test fun uncertainDispatchNeverClaimsAnAddressWasOpened() {
        val context = ControlContext("lifetime",1,"doc",1,1)
        val message = BrowserActionMessage(BrowserCommandId.create(context,1),context,BrowserAction.OpenAddress("example.com"),1)
        val result = dispatchBrowserAction(message) { throw IllegalStateException("private diagnostic") }
        assertEquals("DISPATCH_UNCERTAIN",result.reason); assertNull(result.effectSucceeded)
        assertFalse(result.toString().contains("private diagnostic"))
    }
}
