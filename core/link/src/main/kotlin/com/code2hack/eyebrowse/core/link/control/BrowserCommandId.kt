package com.code2hack.eyebrowse.core.link.control

/**
 * Canonical result-correlation key for a full action-target context and its ordinal.
 * Numeric fields are delimited canonical decimal; null generation is the literal 'n'.
 * Each opaque string is length-prefixed in Kotlin/UTF-16 code units, so embedded colons,
 * digits and Unicode cannot alias field boundaries. This is an encoding, not a hash/cache.
 */
object BrowserCommandId {
    // "v2:" + four (Long + ':') + (length + ':' + text) for lifetime (128) and document (256).
    const val MAX_LENGTH = 475

    fun create(context: ControlContext, commandSequence: Long): String {
        require(commandSequence > 0)
        return "v2:${context.controlEpoch}:$commandSequence:${context.viewportEpoch}:" +
            "${context.hostingGeneration ?: "n"}:${context.lifetimeId.length}:${context.lifetimeId}" +
            "${context.documentId.length}:${context.documentId}"
    }

    fun matches(commandId: String, context: ControlContext, commandSequence: Long): Boolean =
        commandSequence > 0 && commandId == create(context, commandSequence)
}
