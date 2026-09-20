package com.code2hack.eyebrowse.phone.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T02 predeclared: pairing-surface regressions (pure JVM). The controller is the UI-layer mirror
 * of the pairing discipline: exactly ONE visible active invitation, cancellation clears only its
 * own displayed invitation (review R1/B2 lineage), Forget is the explicit reset.
 */
class PhonePairingUiControllerTest {

    private class FakeSurface : PhonePairingUiController.PairingSurface {
        var generated: PhonePairingUiController.PairingSurface.GeneratedInvitation? = null
        var activeId: String? = null
        var cancelResult = true
        var cancelledIds = mutableListOf<String>()
        var forgotten = false
        var linkUp = false
        var paired = false
        var corrupt = false
        var failGenerate = false

        override fun generateInvitation(): PhonePairingUiController.PairingSurface.GeneratedInvitation? {
            if (failGenerate) return null
            val next = PhonePairingUiController.PairingSurface.GeneratedInvitation(
                id = "id-${generated?.id?.length ?: 0}-${(generated?.id ?: "x").hashCode()}",
                payload = "eyebrowse-pair://v1?...",
                expiresAtMs = 10_000L,
            )
            generated = next
            activeId = next.id
            return next
        }

        override fun cancelInvitation(): Boolean {
            val id = activeId ?: return false
            cancelledIds.add(id)
            if (cancelResult) activeId = null
            return cancelResult
        }

        override fun forget() {
            forgotten = true
            generated = null
            activeId = null
            paired = false
            linkUp = false
        }

        override fun activeInvitationId(): String? = activeId
        override fun isLinkUp(): Boolean = linkUp
        override fun isPaired(): Boolean = paired
        override fun isCorrupt(): Boolean = corrupt
    }

    @Test
    fun `generate shows exactly one visible active invitation - the newest`() {
        val surface = FakeSurface()
        val controller = PhonePairingUiController(surface) { 0 }
        controller.onScreenShown()
        controller.onGenerateClicked()
        val first = controller.current()
        assertEquals(surface.generated!!.id, first.invitationId)

        controller.onGenerateClicked() // replacement: the old QR is gone, one active remains
        val second = controller.current()
        assertEquals(surface.generated!!.id, second.invitationId)
        assertEquals(second.invitationId, surface.activeId)
        assertTrue(second.invitationPayload != null)
    }

    @Test
    fun `cancel clears the displayed invitation only when it is the one cancelled`() {
        val surface = FakeSurface()
        val controller = PhonePairingUiController(surface) { 0 }
        controller.onScreenShown()
        controller.onGenerateClicked()
        val displayed = controller.current().invitationId!!

        controller.onCancelClicked()
        assertTrue(surface.cancelledIds.contains(displayed))
        assertNull(controller.current().invitationId)
        assertNull(controller.current().invitationPayload)

        // A refusal to cancel must NOT clear the displayed QR (B2 lineage at the UI layer).
        controller.onGenerateClicked()
        surface.cancelResult = false
        controller.onCancelClicked()
        assertEquals(surface.generated!!.id, controller.current().invitationId)
    }

    @Test
    fun `forget is the explicit reset of the whole surface`() {
        val surface = FakeSurface()
        val controller = PhonePairingUiController(surface) { 0 }
        controller.onScreenShown()
        surface.paired = true
        controller.onRefresh()
        controller.onGenerateClicked()
        assertTrue(controller.current().invitationPayload != null)

        controller.onForgetClicked()
        assertTrue(surface.forgotten)
        assertNull(controller.current().invitationId)
        assertNull(controller.current().invitationPayload)
        assertFalse(controller.current().paired)
        assertFalse(controller.current().linkUp)
    }

    /**
     * Review B5: exactly ONE visible ACTIVE invitation — an expired invitation is no longer
     * active, so its QR display is cleared, not left on screen.
     */
    @Test
    fun `expiry clears the displayed QR and annotates the note`() {
        var now = 5_000L
        val surface = FakeSurface()
        val controller = PhonePairingUiController(surface) { now }
        controller.onScreenShown()
        controller.onGenerateClicked()
        now = 15_000L // past expiresAtMs (10_000)
        val state = controller.onRefresh()
        assertEquals(PhonePairingUiController.INVITATION_EXPIRED_NOTE, state.note)
        assertNull(state.invitationId)
        assertNull(state.invitationPayload)
        assertNull(state.expiresAtMs)
    }

    /** Review B2: CORRUPT trust is distinct from a clean "not paired"; Forget stays reachable. */
    @Test
    fun `corrupt trust keeps the explicit Forget recovery reachable`() {
        val surface = FakeSurface()
        surface.corrupt = true
        val controller = PhonePairingUiController(surface) { 0 }
        val shown = controller.onScreenShown()
        assertTrue(shown.corruptTrust)
        assertTrue("Forget must be enabled under CORRUPT trust", shown.forgetEnabled)
        assertEquals(PhonePairingUiController.CORRUPT_NOTE, shown.note)

        // The generate path does not clear CORRUPT: upgrade/re-pair only AFTER Forget.
        controller.onGenerateClicked()
        assertTrue(controller.current().corruptTrust)
        assertTrue(controller.current().forgetEnabled)

        controller.onForgetClicked()
        assertFalse(controller.current().corruptTrust)
        assertFalse(controller.current().forgetEnabled)
        assertTrue(surface.forgotten)
    }

    /** Review B2: a generate failure under CORRUPT points at the reset, not a generic failure. */
    @Test
    fun `generate failure under corrupt trust surfaces the corrupt reset note`() {
        val surface = FakeSurface()
        surface.corrupt = true
        surface.failGenerate = true
        val controller = PhonePairingUiController(surface) { 0 }
        val state = controller.onGenerateClicked()
        assertTrue(state.corruptTrust)
        assertTrue(state.forgetEnabled)
        assertEquals(PhonePairingUiController.CORRUPT_NOTE, state.note)
    }

    /** Review B2: paired/link-up states keep Forget reachable as before (no regression). */
    @Test
    fun `forget stays reachable while paired or linked`() {
        val surface = FakeSurface()
        val controller = PhonePairingUiController(surface) { 0 }
        val idle = controller.onScreenShown()
        assertFalse(idle.forgetEnabled)

        surface.paired = true
        assertTrue(controller.onRefresh().forgetEnabled)

        surface.paired = false
        surface.linkUp = true
        assertTrue(controller.onRefresh().forgetEnabled)
    }
}
