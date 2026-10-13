package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test

class LocalInputSettingsTest {
    @Test fun numericStepsBoundsAndRecreatedStoreAgreeWithoutFloatingPointDrift() {
        val store=mutableMapOf<String,String>()
        fun load()=LocalInputSettings(store::get) { k,v -> store[k]=v;true }
        var s=load()
        assertEquals(2.0,s.pointerGain,0.0);assertEquals(240,s.scrollSpeed)
        repeat(100) { assertTrue(s.stepPointer(-1));assertTrue(s.stepScroll(-1)) }
        assertEquals(1,s.pointerTenths);assertEquals(10,s.scrollSpeed)
        for(tenths in 2..60) {
            assertTrue(s.stepPointer(1));s=load()
            assertEquals(tenths,s.pointerTenths);assertEquals(tenths/10.0,s.pointerGain,0.0)
        }
        for(speed in 20..1200 step 10) { assertTrue(s.stepScroll(1));s=load();assertEquals(speed,s.scrollSpeed) }
        repeat(10) { assertTrue(s.stepPointer(1));assertTrue(s.stepScroll(1)) }
        s=load();assertEquals(60,s.pointerTenths);assertEquals(1200,s.scrollSpeed)
        assertTrue(s.stepPointer(-1));assertTrue(s.stepScroll(-1))
        s=load();assertEquals(59,s.pointerTenths);assertEquals(1190,s.scrollSpeed)
    }
    @Test fun allLegacyPresetsMigrateOnceWithoutClearingOrRedoubling() {
        for((legacyPointer,tenths) in mapOf("LOW" to 15,"STANDARD" to 20,"HIGH" to 25))
            for((legacyScroll,speed) in mapOf("SLOW" to 120,"STANDARD" to 240,"FAST" to 360)) {
                val store=mutableMapOf("pointer.sensitivity" to legacyPointer,"edge.speed" to legacyScroll,"unrelated" to "keep")
                var writes=0
                fun load()=LocalInputSettings(store::get) { k,v -> store[k]=v;writes++;true }
                var s=load();assertEquals(tenths,s.pointerTenths);assertEquals(speed,s.scrollSpeed);assertEquals(2,writes)
                repeat(3) { s=load();assertEquals(tenths,s.pointerTenths);assertEquals(speed,s.scrollSpeed) }
                assertEquals(2,writes)
                assertTrue(s.stepPointer(1));assertTrue(s.stepScroll(-1))
                store["pointer.sensitivity"]="LOW";store["edge.speed"]="SLOW"
                s=load();assertEquals(tenths+1,s.pointerTenths);assertEquals(speed-10,s.scrollSpeed)
                assertEquals("keep",store["unrelated"])
            }
    }
    @Test fun malformedNumericValuesUseDefaultsRatherThanReapplyingLegacy() {
        for(pointer in listOf("0","61","NaN","2.0","-1")) for(scroll in listOf("0","1201","245","bad")) {
            val store=mutableMapOf("pointer.tenths" to pointer,"edge.pixels-per-second" to scroll,
                "pointer.sensitivity" to "HIGH","edge.speed" to "FAST")
            val s=LocalInputSettings(store::get) { k,v -> store[k]=v;true }
            assertEquals(20,s.pointerTenths);assertEquals(240,s.scrollSpeed)
        }
    }
    @Test fun failedPersistenceKeepsSelectionAndReportsMigrationOrSaveFailure() {
        val s=LocalInputSettings({null}) { _,_ -> false }
        assertNotNull(s.error)
        assertFalse(s.stepPointer(1));assertFalse(s.stepScroll(1))
        assertEquals(2.0,s.pointerGain,0.0);assertEquals(240,s.scrollSpeed);assertNotNull(s.error)
        val mixed=LocalInputSettings({null}) { key,_ -> key!="pointer.tenths" }
        assertNotNull("a successful scroll migration cannot hide a failed pointer migration",mixed.error)
    }
    @Test fun successfulRetryClearsTheErrorOnlyAfterSaving() {
        var writable=false
        val store=mutableMapOf("pointer.tenths" to "20","edge.pixels-per-second" to "240")
        val s=LocalInputSettings(store::get) { k,v -> if(writable) { store[k]=v;true } else false }
        assertFalse(s.stepPointer(1));assertNotNull(s.error);assertEquals("20",store["pointer.tenths"])
        writable=true;assertTrue(s.stepPointer(1));assertNull(s.error);assertEquals("21",store["pointer.tenths"])
    }
}
