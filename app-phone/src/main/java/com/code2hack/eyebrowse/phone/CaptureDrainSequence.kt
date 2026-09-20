package com.code2hack.eyebrowse.phone

/**
 * Orders capture re-arming work so pre-arm/stale buffers are discarded before the producer is
 * asked to render a fresh current frame. The fresh-frame request still runs if draining throws.
 */
internal object CaptureDrainSequence {
    internal fun run(drain: () -> Unit, requestFresh: () -> Unit) {
        try {
            drain()
        } finally {
            requestFresh()
        }
    }
}
