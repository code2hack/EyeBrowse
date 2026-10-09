package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test

class RgKeyboardKeysTest {
    @Test fun localKeyLayoutNeedsNoEditorOrRemoteController() {
        val keys = RgKeyboardKeys()
        assertEquals("qwertyuiopasdfghjklzxcvbnm.,/@-_", keys.rows().flatten().joinToString("") {
            (it as RgKeyboardKeys.Key.Character).text
        })
        assertTrue(keys.changeLayer(RgKeyboardKeys.Key.Command.SHIFT))
        assertEquals("Q", (keys.rows()[0][0] as RgKeyboardKeys.Key.Character).text)
        assertTrue(keys.changeLayer(RgKeyboardKeys.Key.Command.SYMBOLS))
        val symbols = keys.rows().flatten().map { (it as RgKeyboardKeys.Key.Character).text }.toSet()
        "0123456789:/?.@-_=&%#+\"'[]()".forEach { assertTrue(symbols.contains(it.toString())) }
        assertFalse(keys.changeLayer(RgKeyboardKeys.Key.Command.DONE))
        assertFalse(RgKeyboardKeys.Key.Character("x").toString().contains("x"))
    }
}
