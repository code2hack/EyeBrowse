package com.code2hack.eyebrowse.phone

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class PhoneEditorAuthorityTest {
    private val context = ControlContext("life", 1, "doc", 2, 3)
    private val state = ControlSnapshot(context, ControlOwner.RG, PresentationProfile(480, 344, 204),
        true, PresentationStatus.READY, true, true)
    private fun open(a: PhoneEditorAuthority): PhoneEditorAuthority.Grant {
        val request = a.beginOpen(state, 4)!!
        val grant = PhoneEditorAuthority.Grant(request, EditorTarget(request.id, 1), "renderer", 0, EditorKind.TEXT, EditorEnter.IMPLICIT_SUBMIT)
        assertTrue(a.opened(request, grant, state, 4)); return grant
    }
    @Test fun onePendingEffectAndNoSuccessorCallbackClear() {
        val a = PhoneEditorAuthority(); val first = open(a)
        val p = a.beginEdit("command-1", first.target, state, 4)!!
        assertNull(a.beginEdit("command-2", first.target, state, 4))
        assertTrue(a.completed(p, 1, true))
        val next = a.beginEdit("command-2", first.target, state, 4)!!
        assertFalse(a.completed(p, 2, true)); assertEquals(PhoneEditorAuthority.Phase.PENDING, a.phase)
        assertTrue(a.completed(next, 2, true))
    }
    @Test fun timeoutDoesNotEnableReopenOrRetryUntilRevocationVerified() {
        val a = PhoneEditorAuthority(); val g = open(a); val p = a.beginEdit("command", g.target, state, 4)!!
        a.timedOut(p)
        assertNull(a.beginEdit("again", g.target, state, 4)); assertNull(a.beginOpen(state, 4))
        assertFalse(a.completed(p, 1, true))
        val barrier = a.revoke(); assertFalse(a.revoked(barrier, false)); assertNull(a.beginOpen(state, 4))
        assertTrue(a.revoked(barrier, true)); assertNotNull(a.beginOpen(state, 4))
    }
    @Test fun oldOpenAndOldCloseCannotReopenOrRetireANewGrant() {
        val a = PhoneEditorAuthority(); val opening = a.beginOpen(state, 4)!!
        val barrier = a.revoke(); assertTrue(a.revoked(barrier, true)); val fresh = open(a)
        assertFalse(a.opened(opening, null, state, 4)); assertFalse(a.revoked(barrier, true)); assertSame(fresh, a.grant)
    }
    @Test fun ownershipContextAndConnectionAreCheckedAgainBeforeScheduling() {
        val unready = PhoneEditorAuthority()
        assertNull(unready.beginOpen(state.copy(presentationStatus = PresentationStatus.STALE), 4))
        assertNull(unready.beginOpen(state.copy(presentationStatus = PresentationStatus.INACTIVE), 4, profileTransition = true))
        assertNotNull(unready.beginOpen(state.copy(presentationStatus = PresentationStatus.STALE), 4, profileTransition = true))
        val a = PhoneEditorAuthority(); val g = open(a)
        for (invalid in listOf(state.copy(owner = ControlOwner.PHONE), state.copy(linkAuthenticated = false),
            state.copy(context = context.copy(documentId = "new")), state.copy(context = context.copy(viewportEpoch = 9))))
            assertNull(a.beginEdit("command", g.target, invalid, 4))
        assertNull(a.beginEdit("command", g.target, state, 5))
        assertNull(a.beginEdit("command", EditorTarget("other", 1), state, 4))
    }
    @Test fun staleGrantResponseCannotPublishReadiness() {
        val a = PhoneEditorAuthority(); val request = a.beginOpen(state, 4)!!
        val g = PhoneEditorAuthority.Grant(request, EditorTarget(request.id, 1), "renderer", 0, EditorKind.TEXT, EditorEnter.IMPLICIT_SUBMIT)
        assertFalse(a.opened(request, g, state.copy(context = context.copy(documentId = "new")), 4))
        assertEquals(PhoneEditorAuthority.Phase.UNCERTAIN, a.phase); assertNull(a.grant)
    }
    @Test fun rejectedRendererResultCannotKeepStaleReadiness() {
        val a = PhoneEditorAuthority(); val g = open(a); val p = a.beginEdit("command", g.target, state, 4)!!
        assertTrue(a.completed(p, null, false)); assertNull(a.grant)
        assertEquals(PhoneEditorAuthority.Phase.UNCERTAIN, a.phase)
    }
    @Test fun localFocusIsRequiredBeforeOpeningPublishingAndDispatching() {
        val a = PhoneEditorAuthority()
        assertNull(a.beginOpen(state, 4, localFocusReady = false))
        assertEquals(PhoneEditorAuthority.Phase.EMPTY, a.phase)
        val opening = a.beginOpen(state, 4)!!
        val g = PhoneEditorAuthority.Grant(opening, EditorTarget(opening.id, 1), "renderer", 0,
            EditorKind.TEXT, EditorEnter.IMPLICIT_SUBMIT)
        assertFalse(a.opened(opening, g, state, 4, localFocusReady = false))
        assertEquals(PhoneEditorAuthority.Phase.UNCERTAIN, a.phase)
        val barrier = a.revoke(); assertTrue(a.revoked(barrier, true))
        val fresh = open(a)
        assertNull(a.beginEdit("old-focus", fresh.target, state, 4, localFocusReady = false))
        assertEquals(PhoneEditorAuthority.Phase.READY, a.phase)
    }

    @Test fun focusLossFencesPendingEffectUntilRendererRetirementAndNeverReopens() {
        val a = PhoneEditorAuthority(); val g = open(a)
        val pending = a.beginEdit("effect-before-loss", g.target, state, 4)!!
        val barrier = a.revoke() // Native focus callback closes admission synchronously.
        assertFalse(a.completed(pending, 1, true))
        assertNull(a.beginOpen(state, 4, localFocusReady = true))
        assertNull(a.beginEdit("after-regain", g.target, state, 4))
        assertFalse(a.revoked(barrier, false))
        assertEquals(PhoneEditorAuthority.Phase.UNCERTAIN, a.phase)
        assertTrue(a.revoked(barrier, true))
        assertEquals(PhoneEditorAuthority.Phase.EMPTY, a.phase)
        assertNull(a.grant) // Regaining local focus is availability, not explicit activation.
    }
}
