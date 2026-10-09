package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test

class RgKeyboardKeysTest {
    @Test fun visibleLocalRowsContainEveryRequiredKeyInBothCasesAndLayers() {
        val keys = RgKeyboardKeys()
        fun characters(y: Int) = keys.localLayout().filter { it.y == y }.mapNotNull {
            (it.key as? RgKeyboardKeys.Key.Character)?.text
        }.joinToString("")
        assertEquals("qwertyuiop", characters(4))
        assertEquals("asdfghjkl", characters(52))
        assertEquals("zxcvbnm", characters(100))
        keys.changeLayer(RgKeyboardKeys.Key.Command.SHIFT)
        assertEquals("QWERTYUIOP", characters(4))
        assertEquals("ASDFGHJKL", characters(52))
        assertEquals("ZXCVBNM", characters(100))
        keys.changeLayer(RgKeyboardKeys.Key.Command.SYMBOLS)
        val symbols = keys.localLayout().mapNotNull { (it.key as? RgKeyboardKeys.Key.Character)?.text }.toSet()
        "0123456789:/.-_@?&=#%+,;!'\"()".forEach { assertTrue("visible symbol $it", it.toString() in symbols) }
        keys.changeLayer(RgKeyboardKeys.Key.Command.SYMBOLS)
        assertEquals("QWERTYUIOP", characters(4))
        keys.changeLayer(RgKeyboardKeys.Key.Command.SHIFT)
        assertEquals("qwertyuiop", characters(4))
    }

    @Test fun allVisibleKeysFitHaveReachableInteriorsAndKeepEnterAndDoneSeparate() {
        val keys = RgKeyboardKeys()
        repeat(4) {
            val layout = keys.localLayout()
            assertEquals(layout.size, layout.map { it.key }.distinct().size)
            assertEquals(setOf(4, 52, 100, 148), layout.map { it.y }.toSet())
            layout.forEach { key ->
                assertTrue(key.x >= 8 && key.x + key.width <= 472)
                assertTrue(key.y >= 4 && key.y + key.height <= 196)
                assertTrue(key.width >= 36 && key.height >= 44)
                layout.filter { it !== key }.forEach { other ->
                    assertTrue("native keys cannot overlap", key.x + key.width <= other.x ||
                        other.x + other.width <= key.x || key.y + key.height <= other.y || other.y + other.height <= key.y)
                }
            }
            val commands = layout.filter { it.key is RgKeyboardKeys.Key.Command }.associateBy { it.key }
            for (command in listOf(RgKeyboardKeys.Key.Command.SHIFT, RgKeyboardKeys.Key.Command.SYMBOLS,
                RgKeyboardKeys.Key.Command.SPACE, RgKeyboardKeys.Key.Command.BACKSPACE,
                RgKeyboardKeys.Key.Command.ENTER, RgKeyboardKeys.Key.Command.DONE)) assertTrue(command in commands)
            assertEquals(RgKeyboardKeys.PositionedKey(RgKeyboardKeys.Key.Command.ENTER, 296, 148, 96, 48),
                commands[RgKeyboardKeys.Key.Command.ENTER])
            assertEquals(RgKeyboardKeys.PositionedKey(RgKeyboardKeys.Key.Command.DONE, 396, 148, 76, 48),
                commands[RgKeyboardKeys.Key.Command.DONE])
            keys.changeLayer(if (it % 2 == 0) RgKeyboardKeys.Key.Command.SHIFT else RgKeyboardKeys.Key.Command.SYMBOLS)
        }
    }

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
