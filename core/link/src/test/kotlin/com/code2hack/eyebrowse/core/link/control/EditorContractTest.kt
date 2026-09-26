package com.code2hack.eyebrowse.core.link.control

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class EditorContractTest {
    private fun active(keyboard: Boolean = true) = BrowserControlCoordinator("document", "lifetime").also {
        it.setAuthenticated(true, true, keyboard)
        it.setHostingGeneration(1, true)
        it.requestHandoff(HandoffRequest(HandoffTarget.RG, 0, PresentationProfile(480, 344, 204)))
        it.markPresentationReady(it.snapshot().context)
    }
    private fun request(c: ControlContext, n: Long, action: BrowserAction) =
        BrowserActionRequest(BrowserCommandId.create(c, n), c, action, n)
    private val edit = BrowserAction.Edit(EditorTarget("opaque", 1), EditorOperation.Insert("dummy"))

    @Test fun legacyTrustKeepsBaseBrowsingButCannotAdmitText() {
        val hello = HelloMessage(1, 1, LinkProtocol.REQUIRED_CAPABILITIES + LinkProtocol.PRESENTATION_CAPABILITIES)
        assertTrue(hello.hasRequiredCapabilities()); assertTrue(hello.hasPresentationCapabilities()); assertFalse(hello.hasKeyboardCapabilities())
        val c = active(false); val context = c.snapshot().context
        assertEquals(ActionDecision.Rejected(ActionRejection.KEYBOARD_UNAVAILABLE), c.admitAction(ControlOwner.RG, request(context, 1, edit)))
        assertTrue(c.admitAction(ControlOwner.RG, request(context, 2, BrowserAction.Reload)) is ActionDecision.Accepted)
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE), c.admitAction(ControlOwner.RG, request(context, 1, edit)))
    }
    @Test fun textBoundsRejectUtf8OversizeAndMalformedSurrogates() {
        assertTrue(EditorLimits.printable("a".repeat(256)))
        for (invalid in listOf("a".repeat(257), "😀".repeat(65), "\uD800", "\uDC00", "\n", "", "\u0000")) {
            assertFalse(EditorLimits.printable(invalid))
            assertThrows(IllegalArgumentException::class.java) { EditorOperation.Insert(invalid) }
        }
    }
    @Test fun escapedDataAndLongIdentitiesRoundTripWithoutCoercion() {
        val c = ControlContext("life:😀", Long.MAX_VALUE, "doc:😀", Long.MAX_VALUE - 1, Long.MAX_VALUE - 2)
        val action = BrowserAction.Edit(EditorTarget("opaque", Long.MAX_VALUE), EditorOperation.Insert("\"\\\u2028\u2029😀"))
        val message = BrowserActionMessage(BrowserCommandId.create(c, Long.MAX_VALUE), c, action, Long.MAX_VALUE)
        val decoded = LinkMessageCodec.decode(LinkMessageCodec.encode(message)).getOrThrow() as LinkMessageCodec.Incoming.Known
        assertEquals(message, decoded.message)
    }
    @Test fun routineFormattingAndDecodeErrorsDoNotEchoText() {
        val secret = "DUMMY-PRIVATE-MARKER"
        assertFalse(BrowserAction.OpenAddress(secret).toString().contains(secret))
        val text = BrowserAction.Edit(EditorTarget("opaque", 1), EditorOperation.Insert(secret))
        assertFalse(text.toString().contains(secret))
        val malformed = "{\"t\":\"browser_action\",\"action\":\"$secret\"}".toByteArray()
        val error = LinkMessageCodec.decode(malformed).exceptionOrNull()!!
        assertFalse(error.toString().contains(secret)); assertNull(error.cause)
    }
    @Test fun sameOwnerProfileUpdatePreservesOrdinalFloorAndRejectsOldContext() {
        val c = active(); val old = c.snapshot().context
        assertTrue(c.admitAction(ControlOwner.RG, request(old, 90, edit)) is ActionDecision.Accepted)
        val updated = c.updateRgViewport(old, PresentationProfile(480, 200, 204))!!
        assertEquals(old.controlEpoch, updated.context.controlEpoch)
        assertEquals(old.viewportEpoch + 1, updated.context.viewportEpoch)
        c.markPresentationReady(updated.context)
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_CONTEXT), c.admitAction(ControlOwner.RG, request(old, 999, edit)))
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE), c.admitAction(ControlOwner.RG, request(updated.context, 90, edit)))
        assertTrue(c.admitAction(ControlOwner.RG, request(updated.context, 91, edit)) is ActionDecision.Accepted)
    }
    @Test fun unexpectedOwnerLinkAndMissingCapabilityCannotResize() {
        val legacy = active(false)
        assertNull(legacy.updateRgViewport(legacy.snapshot().context, PresentationProfile(480, 200, 204)))
        val c = active(); c.setAuthenticated(false)
        assertNull(c.updateRgViewport(c.snapshot().context, PresentationProfile(480, 200, 204)))
        val phone = BrowserControlCoordinator("doc")
        assertNull(phone.updateRgViewport(phone.snapshot().context, PresentationProfile(480, 200, 204)))
    }
    @Test fun addressRecoveryDoesNotRequireAPageEditorOrCurrentPixels() {
        val c = active(); val ctx = c.snapshot().context; c.markPresentationStale(ctx)
        assertTrue(c.admitAction(ControlOwner.RG, request(ctx, 1, BrowserAction.OpenAddress("https://example.test/"))) is ActionDecision.Accepted)
        assertEquals(ActionDecision.Rejected(ActionRejection.PRESENTATION_NOT_READY), c.admitAction(ControlOwner.RG, request(ctx, 2, edit)))
    }
    @Test fun effectAndStateMessagesStayInsideTheExistingBoundedEnvelope() {
        val ctx = active().snapshot().context
        val messages = listOf<BrowserControlMessage>(
            EditorStateMessage(ctx, edit.target, EditorKind.TEXT, EditorEnter.IMPLICIT_SUBMIT, true),
            EditorCloseMessage("close", ctx, edit.target), EditorCloseResultMessage("close", ctx, true),
            ViewportUpdateMessage(Long.MAX_VALUE, ctx, PresentationProfile(480, 200, 204)),
            ViewportUpdateResultMessage(Long.MAX_VALUE, true, ctx, PresentationProfile(480, 200, 204)))
        messages.forEach {
            val bytes = LinkMessageCodec.encode(it); assertTrue(bytes.size < LinkProtocol.FRAME_MAX_BYTES)
            assertEquals(it, (LinkMessageCodec.decode(bytes).getOrThrow() as LinkMessageCodec.Incoming.Known).message)
        }
    }
}
