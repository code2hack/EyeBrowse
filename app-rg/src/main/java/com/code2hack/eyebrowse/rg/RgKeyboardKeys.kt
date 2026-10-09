package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.EditorLimits

/** Built-in key meanings/layout only: no field values, editor identity or delayed intents. */
internal class RgKeyboardKeys {
    sealed interface Key {
        data class Character(val text: String) : Key {
            init { require(text.length == 1 && EditorLimits.printable(text)) }
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
    fun rows() = rows(uppercase, symbols)

    companion object {
        fun rows(uppercase: Boolean, symbols: Boolean): List<List<Key>> {
            val characters = if (symbols) listOf("1234567890", ":/?.@-_=&%", "#+\"'()[]{}", "!*,;\\<>$^~|")
                else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", ".,/@-_")
            return characters.map { row -> row.map {
                Key.Character(if (uppercase && !symbols) it.uppercase() else it.toString())
            } }
        }
    }
}
