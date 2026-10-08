package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.EditorLimits
import org.junit.Assert.*
import org.junit.Test

class RgKeyboardTest {
    private fun press(k: RgKeyboard<*>, key: RgKeyboard.Key) = k.local(k.capture(key))
    @Test fun keyMapAndStaleMeaning() {
        val k = RgKeyboard<String>(); k.openAddress("")
        assertEquals("qwertyuiopasdfghjklzxcvbnm.,/@-_", k.rows().flatten().joinToString("") { (it as RgKeyboard.Key.Character).text })
        val lower = k.capture(k.rows()[0][0])
        assertTrue(press(k, RgKeyboard.Key.Command.SHIFT))
        assertFalse(k.local(lower)); assertEquals("Q", (k.rows()[0][0] as RgKeyboard.Key.Character).text)
        press(k, RgKeyboard.Key.Command.SYMBOLS)
        val symbols = k.rows().flatten().map { (it as RgKeyboard.Key.Character).text }.toSet()
        "0123456789:/?.@-_=&%#+\"'[]()".forEach { assertTrue(symbols.contains(it.toString())) }
    }
    @Test fun addressCorrectionPreservesUnicodeAndBounds() {
        val k = RgKeyboard<String>(); k.openAddress("a😀b")
        press(k, RgKeyboard.Key.Command.LEFT); press(k, RgKeyboard.Key.Command.BACKSPACE)
        assertEquals("ab", k.draft); assertEquals(1, k.caret)
        press(k, RgKeyboard.Key.Character("x")); assertEquals("axb", k.draft)
        k.openAddress("a".repeat(EditorLimits.ADDRESS_BYTES))
        assertFalse(press(k, RgKeyboard.Key.Command.SPACE)); assertEquals(EditorLimits.ADDRESS_BYTES, k.draft.length)
    }
    @Test fun fieldNeverMirrorsTextAndEverySessionFencesOldKeys() {
        val k = RgKeyboard<String>(); k.openAddress("private draft")
        val old = k.capture(RgKeyboard.Key.Character("x"))
        k.openField("one")
        assertFalse(k.current(old)); assertEquals("", k.draft)
        assertFalse(press(k, RgKeyboard.Key.Character("p")))
        val field = k.capture(RgKeyboard.Key.Command.ENTER)
        k.rebind("two"); assertFalse(k.current(field))
        k.close(); assertFalse(k.visible); assertNull(k.target)
        k.openAddress(""); assertFalse(k.uppercase); assertFalse(k.symbols)
        assertFalse(k.current(old)); assertFalse(field.toString().contains("ENTER"))
    }
    @Test fun localEditorTabAndElementReturnCannotReviveAnOldKey() {
        data class LocalEditor(val tab: String, val document: Long, val element: String)
        val k = RgKeyboard<LocalEditor>()
        val a = LocalEditor("A", 1, "field")
        k.openField(a)
        val old = k.capture(RgKeyboard.Key.Command.BACKSPACE)
        k.rebind(LocalEditor("B", 1, "field")); k.rebind(a)
        assertFalse(k.current(old))
        val fresh = k.capture(RgKeyboard.Key.Command.BACKSPACE)
        assertTrue(k.current(fresh)); assertEquals("", k.draft)
        k.close(); k.openField(a)
        assertFalse(k.current(fresh)); assertEquals("", k.draft)
    }
}
