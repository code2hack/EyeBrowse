package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class RgInputSnapshotTest {
    private val phone=RgInputSnapshot(ControlContext("life",1,"doc",1,1),ControlOwner.PHONE,
        PresentationProfile(480,344,204),0,false,true,true,true)
    @Test fun phoneOwnerAllowsExplicitConsentAndLocalRecoveryOnly() {
        assertTrue(phone.allows(LocalInputAction.USE_GLASSES))
        assertTrue(phone.allows(LocalInputAction.RECENTER));assertTrue(phone.allows(LocalInputAction.RETRY))
        for (action in listOf(LocalInputAction.BACK,LocalInputAction.FORWARD,LocalInputAction.RELOAD,LocalInputAction.USE_PHONE)) assertFalse(phone.allows(action))
    }
    @Test fun rgOwnerNeedsCurrentReadyPresentationForNavigation() {
        val rg=phone.copy(owner=ControlOwner.RG,pageReady=true)
        assertTrue(rg.allows(LocalInputAction.USE_PHONE));assertFalse(rg.allows(LocalInputAction.USE_GLASSES))
        assertTrue(rg.allows(LocalInputAction.BACK));assertFalse(rg.copy(canGoBack=false).allows(LocalInputAction.BACK))
        assertFalse(rg.copy(pageReady=false).allows(LocalInputAction.RELOAD))
        assertFalse(rg.copy(handoffReady=false).allows(LocalInputAction.USE_PHONE))
    }
    @Test fun localRecoverySurvivesAbsentConnectionWithoutPermittingTakeover() {
        val absent=RgInputSnapshot(null,null,null,0,false,false,false,false)
        for(action in listOf(LocalInputAction.RECENTER,LocalInputAction.RETRY,LocalInputAction.PAIR)) assertTrue(absent.allows(action))
        assertFalse(absent.allows(LocalInputAction.USE_GLASSES));assertFalse(absent.allows(LocalInputAction.RELOAD))
    }
    @Test fun unreadyPresentationStillAllowsExplicitReturnButNoPageAction() {
        val waiting=phone.copy(owner=ControlOwner.RG,pageReady=false)
        assertTrue(waiting.allows(LocalInputAction.USE_PHONE))
        assertFalse(waiting.allows(LocalInputAction.BACK));assertFalse(waiting.allows(LocalInputAction.RELOAD))
    }
    @Test fun preparedSlotTurnoverInvalidatesAnOtherwiseIdenticalRemoteIntent() {
        val before=phone.copy(owner=ControlOwner.RG,pageReady=true,reservationRevision=10)
        val unavailable=before.copy(pageReady=false,reservationRevision=11)
        val refill=before.copy(reservationRevision=12)
        assertNotEquals(before,refill);assertNotEquals(unavailable,refill)
        assertFalse(unavailable.allows(LocalInputAction.RELOAD))
        assertTrue(unavailable.allows(LocalInputAction.RECENTER))
        assertTrue(unavailable.allows(LocalInputAction.RETRY))
        assertTrue(unavailable.allows(LocalInputAction.USE_PHONE))
    }
}
