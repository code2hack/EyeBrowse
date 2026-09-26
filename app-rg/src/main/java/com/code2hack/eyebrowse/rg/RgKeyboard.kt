package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.EditorLimits
import com.code2hack.eyebrowse.core.link.control.EditorTarget

/** Only address text is held locally. Webpage values/passwords never enter this model. */
internal class RgKeyboard {
    enum class Destination { ADDRESS, FIELD }
    sealed interface Key {
        data class Character(val text: String) : Key {
            init { require(text.length == 1 && EditorLimits.printable(text)) }
            override fun toString() = "Character(redacted)"
        }
        enum class Command : Key { SHIFT, SYMBOLS, SPACE, BACKSPACE, ENTER, DONE, LEFT, RIGHT }
    }
    data class Intent(val generation: Long, val key: Key) {
        override fun toString() = "KeyboardIntent(redacted)"
    }
    var destination: Destination? = null
        private set
    var activationCommandId: String? = null
        private set
    var target: EditorTarget? = null
        private set
    var generation = 0L
        private set
    var uppercase = false
        private set
    var symbols = false
        private set
    var draft = ""
        private set
    var caret = 0
        private set
    val visible get() = destination != null

    fun openAddress(address: String) {
        reset(Destination.ADDRESS)
        // Never truncate an address into a different navigable address.
        draft = address.takeIf { it.toByteArray(Charsets.UTF_8).size <= EditorLimits.ADDRESS_BYTES } ?: ""
        caret = draft.length
    }
    fun openField(editor: EditorTarget, activation: String? = null) {
        reset(Destination.FIELD); target = editor; activationCommandId = activation
    }
    private fun reset(next: Destination) {
        generation++; destination = next; target = null; activationCommandId = null; uppercase = false; symbols = false
        draft = ""; caret = 0
    }
    fun close() { generation++; destination = null; target = null; activationCommandId = null; draft = ""; caret = 0 }
    fun rebind(editor: EditorTarget?) { if (target != editor) { target = editor; generation++ } }
    fun capture(key: Key) = Intent(generation, key)
    fun current(intent: Intent) = visible && intent.generation == generation

    fun rows(): List<List<Key>> {
        val characters = if (symbols) listOf("1234567890", ":/?.@-_=&%", "#+\"'()[]{}", "!*,;\\<>$^~|")
            else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", ".,/@-_")
        return characters.map { row -> row.map { Key.Character(if (uppercase && !symbols) it.uppercase() else it.toString()) } }
    }

    /** Returns false on a bound violation; the draft and caret remain intact. */
    fun local(intent: Intent): Boolean {
        if (!current(intent)) return false
        when (val key = intent.key) {
            Key.Command.SHIFT -> uppercase = !uppercase
            Key.Command.SYMBOLS -> symbols = !symbols
            else -> {
                if (destination != Destination.ADDRESS) return false
                when (key) {
                    Key.Command.LEFT -> if (caret > 0) caret = draft.offsetByCodePoints(caret, -1)
                    Key.Command.RIGHT -> if (caret < draft.length) caret = draft.offsetByCodePoints(caret, 1)
                    Key.Command.BACKSPACE -> if (caret > 0) {
                        val start = draft.offsetByCodePoints(caret, -1)
                        draft = draft.removeRange(start, caret); caret = start
                    }
                    else -> {
                        val text = when (key) { is Key.Character -> key.text; Key.Command.SPACE -> " "; else -> return false }
                        val next = draft.substring(0, caret) + text + draft.substring(caret)
                        if (next.toByteArray(Charsets.UTF_8).size > EditorLimits.ADDRESS_BYTES) return false
                        draft = next; caret += text.length
                    }
                }
            }
        }
        generation++
        return true
    }
}
