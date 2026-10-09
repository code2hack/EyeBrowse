package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.EditorLimits
import com.code2hack.eyebrowse.core.link.control.EditorTarget

/** Historical remote editor/intent model. Local View/WebView input uses only RgKeyboardKeys. */
internal class RgKeyboard {
    enum class Destination { ADDRESS, FIELD }
    data class Intent(val generation: Long, val key: RgKeyboardKeys.Key) {
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
    fun capture(key: RgKeyboardKeys.Key) = Intent(generation, key)
    fun current(intent: Intent) = visible && intent.generation == generation

    fun rows() = RgKeyboardKeys.rows(uppercase, symbols)

    /** Returns false on a bound violation; the draft and caret remain intact. */
    fun local(intent: Intent): Boolean {
        if (!current(intent)) return false
        when (val key = intent.key) {
            RgKeyboardKeys.Key.Command.SHIFT -> uppercase = !uppercase
            RgKeyboardKeys.Key.Command.SYMBOLS -> symbols = !symbols
            else -> {
                if (destination != Destination.ADDRESS) return false
                when (key) {
                    RgKeyboardKeys.Key.Command.LEFT -> if (caret > 0) caret = draft.offsetByCodePoints(caret, -1)
                    RgKeyboardKeys.Key.Command.RIGHT -> if (caret < draft.length) caret = draft.offsetByCodePoints(caret, 1)
                    RgKeyboardKeys.Key.Command.BACKSPACE -> if (caret > 0) {
                        val start = draft.offsetByCodePoints(caret, -1)
                        draft = draft.removeRange(start, caret); caret = start
                    }
                    else -> {
                        val text = when (key) { is RgKeyboardKeys.Key.Character -> key.text; RgKeyboardKeys.Key.Command.SPACE -> " "; else -> return false }
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
