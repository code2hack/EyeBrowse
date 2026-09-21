package com.code2hack.eyebrowse.phone

/**
 * A bounded trailing-edge throttle: one identity-bound wakeup, never a frame queue.
 * The caller leaves pixels in ImageReader until the due time, then acquires the LATEST
 * image. A final static frame inside the interval must not need another producer event.
 * All access is serialized by PrivateDisplayHost.nativeLock; this class is Android-free.
 */
internal class CaptureDeliveryThrottle(private val intervalMs: Long) {
    init {
        require(intervalMs > 0)
    }

    internal class Wakeup internal constructor(val dueElapsedMs: Long)

    private var lastDeliveryMs: Long? = null
    internal var pending: Wakeup? = null
        private set

    /** Null means an existing wakeup already covers this event; its due time never slides. */
    internal fun request(nowMs: Long): Wakeup? {
        if (pending != null) return null
        val previous = lastDeliveryMs
        val due = if (previous == null) nowMs else maxOf(nowMs, previous + intervalMs)
        return Wakeup(due).also { pending = it }
    }

    /** A cancelled/replaced owner's already-dequeued runnable cannot consume a new ticket. */
    internal fun consume(wakeup: Wakeup): Boolean {
        if (pending !== wakeup) return false
        pending = null
        return true
    }

    /** Empty acquisition or failed copy does not spend the delivery budget. */
    internal fun delivered(nowMs: Long) {
        lastDeliveryMs = nowMs
    }

    internal fun reset() {
        pending = null
        lastDeliveryMs = null
    }
}
