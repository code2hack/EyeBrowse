package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test

class LocalInputSettingsTest {
    @Test fun approvedDefaultsSelectionsAndRecreatedStoreRemainBounded() {
        val store=mutableMapOf<String,String>()
        fun load()=LocalInputSettings(store::get) { k,v -> store[k]=v;true }
        var s=load()
        assertEquals(1.0,s.sensitivity.gain,0.0);assertEquals(240,s.speed.pixelsPerSecond)
        for(value in LocalInputSettings.Sensitivity.entries) {assertTrue(s.select(value));s=load();assertEquals(value,s.sensitivity)}
        for(value in LocalInputSettings.Speed.entries) {assertTrue(s.select(value));s=load();assertEquals(value,s.speed)}
        store["pointer.sensitivity"]="other";store["edge.speed"]="10000"
        s=load();assertEquals(LocalInputSettings.Sensitivity.STANDARD,s.sensitivity);assertEquals(240,s.speed.pixelsPerSecond)
    }
    @Test fun failedPersistenceDoesNotShowOrApplyAnUnsavedSelection() {
        val s=LocalInputSettings({null}) { _,_ -> false }
        assertFalse(s.select(LocalInputSettings.Sensitivity.HIGH));assertFalse(s.select(LocalInputSettings.Speed.FAST))
        assertEquals(1.0,s.sensitivity.gain,0.0);assertEquals(240,s.speed.pixelsPerSecond);assertNotNull(s.error)
    }
}
