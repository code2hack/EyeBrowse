package com.code2hack.eyebrowse.rg

/** Built-in key meanings/layout only: no field values, editor identity or delayed intents. */
internal class RgKeyboardKeys {
    sealed interface Key {
        data class Character(val text: String) : Key {
            init { require(text.length == 1 && text[0].code >= 32 && text[0] != '\u007f' && !text[0].isSurrogate()) }
            override fun toString() = "Character(redacted)"
        }
        enum class Command : Key { SHIFT, SYMBOLS, SPACE, BACKSPACE, ENTER, DONE, LEFT, RIGHT }
    }
    var uppercase = false
        private set
    var symbols = false
        private set

    fun changeLayer(key: Key): Boolean = when(key) {
        Key.Command.SHIFT -> { uppercase = !uppercase; true }
        Key.Command.SYMBOLS -> { symbols = !symbols; true }
        else -> false
    }

    data class PositionedKey(val key: Key, val x: Int, val y: Int, val width: Int, val height: Int)

    /** Actual local 480×200 keyboard. */
    fun localLayout(): List<PositionedKey> = buildList {
        val rows = if (symbols) listOf("1234567890", ":-@_?&=#%", "+,;!'\"()")
            else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEachIndexed { row, characters ->
            val letters = characters.map { Key.Character(if (uppercase && !symbols) it.uppercase() else it.toString()) }
            val keys = if (row == 2) listOf(Key.Command.SHIFT) + letters + Key.Command.BACKSPACE else letters
            val available = 464 - (keys.size - 1) * 4
            keys.forEachIndexed { i, key ->
                add(PositionedKey(key, 8 + i * 4 + i * available / keys.size, 4 + row * 48,
                    (i + 1) * available / keys.size - i * available / keys.size, 44))
            }
        }
        val bottom = listOf(Key.Command.SYMBOLS, Key.Command.SPACE, Key.Character("."), Key.Character("/"),
            Key.Command.ENTER, Key.Command.DONE)
        val widths = listOf(52, 148, 36, 36, 96, 76)
        var x = 8
        bottom.forEachIndexed { i, key ->
            add(PositionedKey(key, x, 148, widths[i], 48)); x += widths[i] + 4
        }
    }

}
