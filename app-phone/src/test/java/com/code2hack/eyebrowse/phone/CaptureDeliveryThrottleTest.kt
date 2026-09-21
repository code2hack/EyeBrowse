package com.code2hack.eyebrowse.phone

import org.junit.Assert.*
import org.junit.Test

class CaptureDeliveryThrottleTest {
    /** Fake native latest-image slot and fake handler; no Android, sleeps, or repaint loop. */
    private class Pump {
        val throttle = CaptureDeliveryThrottle(200)
        val tasks = mutableListOf<CaptureDeliveryThrottle.Wakeup>()
        val output = mutableListOf<Pair<Long, String>>()
        var latest: String? = null

        fun surface(at: Long, pixels: String) {
            latest = pixels
            throttle.request(at)?.let { tasks += it }
        }

        fun advance(to: Long) {
            for (task in tasks.filter { it.dueElapsedMs <= to }.toList()) {
                tasks.remove(task)
                if (!throttle.consume(task)) continue
                val pixels = latest ?: continue
                latest = null
                output += to to pixels
                throttle.delivered(to)
            }
        }
    }

    @Test
    fun staticSecondPageIsDeliveredWithoutAnotherProducerCallback() {
        val pump = Pump()
        pump.surface(0, "old-document")
        pump.advance(0)
        pump.surface(50, "static-second-page")
        pump.advance(199)
        assertEquals(listOf(0L to "old-document"), pump.output)
        // No further surface event: the trailing wakeup must deliver the second page.
        pump.advance(200)
        assertEquals(listOf(0L to "old-document", 200L to "static-second-page"), pump.output)
    }

    @Test
    fun burstCoalescesToLatestAtOriginalDeadline() {
        val pump = Pump()
        pump.surface(0, "old")
        pump.advance(0)
        pump.surface(10, "white-startup")
        val ticket = pump.tasks.single()
        pump.surface(80, "partial")
        pump.surface(199, "final-blue")
        assertSame(ticket, pump.tasks.single())
        assertEquals(200L, ticket.dueElapsedMs)
        pump.advance(200)
        assertEquals(200L to "final-blue", pump.output.last())
        assertEquals(2, pump.output.size)
    }

    @Test
    fun idleDoesNotReplayFramesOrCreatePeriodicWork() {
        val pump = Pump()
        pump.surface(0, "static")
        pump.advance(0)
        pump.advance(200)
        pump.advance(20_000)
        assertEquals(listOf(0L to "static"), pump.output)
        assertTrue(pump.tasks.isEmpty())
        assertNull(pump.throttle.pending)
    }

    @Test
    fun resetRejectsDequeuedOldOwnerWakeup() {
        val throttle = CaptureDeliveryThrottle(200)
        val old = throttle.request(0)!!
        throttle.reset()
        val replacement = throttle.request(0)!!
        assertFalse(throttle.consume(old))
        assertSame(replacement, throttle.pending)
        assertTrue(throttle.consume(replacement))
    }

    @Test
    fun newArmReceivesImmediateFirstFrame() {
        val throttle = CaptureDeliveryThrottle(200)
        throttle.delivered(100)
        assertEquals(300L, throttle.request(101)!!.dueElapsedMs)
        throttle.reset()
        assertEquals(102L, throttle.request(102)!!.dueElapsedMs)
    }

    @Test
    fun failedAcquireDoesNotSpendDeliveryBudget() {
        val throttle = CaptureDeliveryThrottle(200)
        val empty = throttle.request(100)!!
        assertTrue(throttle.consume(empty))
        // No delivered() call: acquisition/copy did not yield a frame.
        assertEquals(101L, throttle.request(101)!!.dueElapsedMs)
    }

    @Test
    fun exactBoundaryAndSpacingArePreserved() {
        val pump = Pump()
        pump.surface(0, "a")
        pump.advance(0)
        pump.surface(200, "b")
        pump.advance(200)
        pump.surface(201, "c")
        pump.advance(399)
        assertEquals(2, pump.output.size)
        pump.advance(400)
        assertEquals(listOf(0L, 200L, 400L), pump.output.map { it.first })
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidIntervalIsRejected() {
        CaptureDeliveryThrottle(0)
    }
}
