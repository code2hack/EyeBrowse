package com.code2hack.eyebrowse.core.link.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T03 listener recovery: a wedged half-open session (no inbound for the full liveness window)
 * must be evictable so an explicit Retry can reconnect; a live link (heartbeat refreshes inbound
 * at least every heartbeat interval) is never evicted.
 */
class StaleSessionEvictionTest {

    private val livenessMs = 30_000L
    private val now = 1_000_000_000_000L

    @Test
    fun `session with no inbound beyond the liveness window is evictable`() {
        val stale = now - (livenessMs + 1) * 1_000_000
        assertTrue(LinkServerEngine.shouldEvictStaleSession(stale, now, livenessMs))
    }

    @Test
    fun `session with recent inbound is never evicted`() {
        val fresh = now - 5_000_000_000L // 5 s ago: well inside the heartbeat cadence
        assertFalse(LinkServerEngine.shouldEvictStaleSession(fresh, now, livenessMs))
    }

    @Test
    fun `session exactly at the liveness boundary is not evicted`() {
        val boundary = now - livenessMs * 1_000_000
        assertFalse(LinkServerEngine.shouldEvictStaleSession(boundary, now, livenessMs))
    }

    @Test
    fun `no tracked session is never evictable`() {
        assertFalse(LinkServerEngine.shouldEvictStaleSession(0L, now, livenessMs))
    }
}
