package com.code2hack.eyebrowse.core.link.control

import kotlinx.serialization.Serializable

/** #9 payload limits are inside the unchanged 32KiB control-record limit. */
object EditorLimits {
    const val TEXT_BYTES = 256
    const val ADDRESS_BYTES = 4096
    const val TOKEN_CHARS = 128

    fun printable(text: String): Boolean {
        if (text.isEmpty() || text.length > TEXT_BYTES || text.toByteArray(Charsets.UTF_8).size > TEXT_BYTES) return false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.code < 32 || c == '\u007f') return false
            if (c.isHighSurrogate()) {
                if (++i == text.length || !text[i].isLowSurrogate()) return false
            } else if (c.isLowSurrogate()) return false
            i++
        }
        return true
    }
}

@Serializable enum class EditorKind { TEXT, PASSWORD, MULTILINE, PLAIN }
@Serializable enum class EditorEnter { IMPLICIT_SUBMIT, LINE_BREAK }

/** Opaque Phone grant, never an HTML id, selector, value or an InputConnection identity. */
@Serializable data class EditorTarget(val token: String, val focusGeneration: Long) {
    init { require(token.isNotBlank() && token.length <= EditorLimits.TOKEN_CHARS); require(focusGeneration > 0) }
}

@Serializable sealed class EditorOperation {
    @Serializable data class Insert(val text: String) : EditorOperation() {
        init { require(EditorLimits.printable(text)) { "invalid text payload" } }
        override fun toString() = "Insert(redacted)"
    }
    @Serializable data object Backspace : EditorOperation()
    @Serializable data object Enter : EditorOperation()
}
