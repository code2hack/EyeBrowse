package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.EditorLimits
import com.code2hack.eyebrowse.core.link.control.EditorTarget
import org.junit.Assert.*
import org.junit.Test

class RgKeyboardTest {
    private fun press(k: RgKeyboard, key: RgKeyboardKeys.Key) = k.local(k.capture(key))
    @Test fun keyMapAndStaleMeaning() {
        val k = RgKeyboard(); k.openAddress("")
        assertEquals("qwertyuiopasdfghjklzxcvbnm.,/@-_", k.rows().flatten().joinToString("") { (it as RgKeyboardKeys.Key.Character).text })
        val lower = k.capture(k.rows()[0][0])
        assertTrue(press(k, RgKeyboardKeys.Key.Command.SHIFT))
        assertFalse(k.local(lower)); assertEquals("Q", (k.rows()[0][0] as RgKeyboardKeys.Key.Character).text)
        press(k, RgKeyboardKeys.Key.Command.SYMBOLS)
        val symbols = k.rows().flatten().map { (it as RgKeyboardKeys.Key.Character).text }.toSet()
        "0123456789:/?.@-_=&%#+\"'[]()".forEach { assertTrue(symbols.contains(it.toString())) }
    }
    @Test fun addressCorrectionPreservesUnicodeAndBounds() {
        val k = RgKeyboard(); k.openAddress("a😀b")
        press(k, RgKeyboardKeys.Key.Command.LEFT); press(k, RgKeyboardKeys.Key.Command.BACKSPACE)
        assertEquals("ab", k.draft); assertEquals(1, k.caret)
        press(k, RgKeyboardKeys.Key.Character("x")); assertEquals("axb", k.draft)
        k.openAddress("a".repeat(EditorLimits.ADDRESS_BYTES))
        assertFalse(press(k, RgKeyboardKeys.Key.Command.SPACE)); assertEquals(EditorLimits.ADDRESS_BYTES, k.draft.length)
    }
    @Test fun fieldNeverMirrorsTextAndEverySessionFencesOldKeys() {
        val k = RgKeyboard(); k.openAddress("private draft")
        val old = k.capture(RgKeyboardKeys.Key.Character("x"))
        k.openField(EditorTarget("one", 1))
        assertFalse(k.current(old)); assertEquals("", k.draft)
        assertFalse(press(k, RgKeyboardKeys.Key.Character("p")))
        val field = k.capture(RgKeyboardKeys.Key.Command.ENTER)
        k.rebind(EditorTarget("two", 2)); assertFalse(k.current(field))
        k.close(); assertFalse(k.visible); assertNull(k.target)
        k.openAddress(""); assertFalse(k.uppercase); assertFalse(k.symbols)
        assertFalse(k.current(old)); assertFalse(field.toString().contains("ENTER"))
    }
}
