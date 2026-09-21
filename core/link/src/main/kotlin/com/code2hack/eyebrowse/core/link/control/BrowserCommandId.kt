package com.code2hack.eyebrowse.core.link.control

/**
 * Canonical result-correlation key for one ordinal in a browser-lifetime/control epoch.
 * Decimal ordinals come first, so even a lifetime containing ':' cannot alias another key.
 * Document/viewport changes do not create a new ordinal namespace; only a handoff does.
 * This is an encoding, not a hash or a history cache.
 */
object BrowserCommandId {
    // "v1:" + two nonnegative Longs (19 chars each) + two ':' + a 128-char lifetime.
    const val MAX_LENGTH = 171

    fun create(context: ControlContext, commandSequence: Long): String {
        require(commandSequence > 0)
        return "v1:${context.controlEpoch}:$commandSequence:${context.lifetimeId}"
    }

    fun matches(commandId: String, context: ControlContext, commandSequence: Long): Boolean =
        commandSequence > 0 && commandId == create(context, commandSequence)
}
