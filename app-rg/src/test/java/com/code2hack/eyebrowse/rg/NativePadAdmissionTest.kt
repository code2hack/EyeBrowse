package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test

class NativePadAdmissionTest {
    // Recorded native metadata, not OEM swipe enums. Device 3 resolves to this name on the RG.
    private val padName = "ROKID,PSOC-TP-R"
    private val keyboardSource = 257
    private class Tabs {
        val tabs = LocalTabs({ Any() }, {})
        var taps = 0
        var modes = 0
        val recognizer = PadGestureRecognizer<Unit>(300, 500, { Unit }, { taps++ }, { modes++ }, { tabs.swipe(it) })
        init { repeat(3) { tabs.add() }; tabs.select(1) }
    }

    private fun swipe(t: Tabs, code: Int, scan: Int, down: Long) {
        // The router calls this same admission function with KeyEvent.device.name/source/keyCode.
        val key = nativePadKey(padName, keyboardSource, code)
        assertNotNull("observed native scan=$scan/key=$code must reach the existing recognizer", key)
        // Observed DOWN: action0/repeat0/meta0, receipt-event=4ms; ordinary matched UP.
        t.recognizer.accept(PadGestureRecognizer.Event(key!!, PadGestureRecognizer.Phase.DOWN, down, down, 0), down + 4)
        t.recognizer.accept(PadGestureRecognizer.Event(key, PadGestureRecognizer.Phase.UP, down + 22, down, 0), down + 23)
    }

    @Test fun observedHorizontalNativeKeysSelectCurrentTabsWithoutWrapping() {
        val t = Tabs()
        swipe(t, 22, 106, 101244114)
        assertEquals(2, t.tabs.selectedIndex)
        swipe(t, 22, 106, 101244614)
        assertEquals(3, t.tabs.selectedIndex)
        swipe(t, 22, 106, 101245114)
        assertEquals("last tab does not wrap", 3, t.tabs.selectedIndex)
        repeat(3) { swipe(t, 21, 105, 101245614L + it * 500) }
        assertEquals(0, t.tabs.selectedIndex)
        swipe(t, 21, 105, 101247114)
        assertEquals("first tab does not wrap", 0, t.tabs.selectedIndex)
        assertEquals(0, t.taps); assertEquals(0, t.modes)
        assertFalse(t.recognizer.hasWork)
    }

    @Test fun otherKeyboardsSourcesAndVerticalKeysKeepTheirNativeInput() {
        for (code in listOf(21, 22, 66, 291, 292, 293)) {
            assertNull(nativePadKey("Virtual", keyboardSource, code))
            assertNull(nativePadKey(null, keyboardSource, code))
            assertNull(nativePadKey(padName, 0, code))
            assertNull(nativePadKey(padName, 0x2002, code))
        }
        for (code in listOf(19, 20, 4, 83)) assertNull(nativePadKey(padName, keyboardSource, code))
    }

    @Test fun legacyOemSwipesAndShortDoubleMappingsRemainSupported() {
        val t = Tabs()
        swipe(t, 292, 183, 1000); assertEquals(2, t.tabs.selectedIndex)
        swipe(t, 293, 184, 1500); assertEquals(1, t.tabs.selectedIndex)
        assertEquals(PadGestureRecognizer.Key.TAP, nativePadKey(padName, keyboardSource, 66))
        assertEquals(PadGestureRecognizer.Key.DOUBLE, nativePadKey(padName, keyboardSource, 291))
    }
}
