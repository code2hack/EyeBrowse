package com.code2hack.eyebrowse.core.link.session

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.messages.StatusMessage

/**
 * Bounded outbound queue (ticket plan §5): maximum [LinkProtocol.OUTBOUND_QUEUE_MAX] pending
 * small messages; status updates are latest-state/coalescible and never build an unbounded
 * history queue.
 *
 * Overflow policy: a status always succeeds by replacing the most recent pending status (or the
 * oldest element when no status is pending); a non-status message is refused (returns false)
 * when the queue is full.
 */
class OutboundQueue(private val capacity: Int = LinkProtocol.OUTBOUND_QUEUE_MAX) {

    private val elements = ArrayDeque<Any>()

    val size: Int get() = elements.size

    @Synchronized
    fun offer(message: Any): Boolean {
        if (message is StatusMessage) {
            // Latest state wins: replace the newest pending status, else drop oldest to fit.
            val existingIndex = elements.indexOfLast { it is StatusMessage }
            if (existingIndex >= 0) {
                elements[existingIndex] = message
                return true
            }
            if (elements.size >= capacity) elements.removeFirst()
            elements.addLast(message)
            return true
        }
        if (elements.size >= capacity) return false
        elements.addLast(message)
        return true
    }

    @Synchronized
    fun poll(): Any? = if (elements.isEmpty()) null else elements.removeFirst()

    @Synchronized
    fun snapshot(): List<Any> = elements.toList()

    @Synchronized
    fun clear() = elements.clear()
}
