package com.code2hack.eyebrowse.phone

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Printer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

private const val FW4_RECEIPT_TAG = "EyeBrowseFW4"

/** Public API snapshots only; a null park blocker does NOT exclude monitor/native blocking.
 * https://developer.android.com/reference/java/util/concurrent/locks/LockSupport#getBlocker(java.lang.Thread)
 * https://developer.android.com/reference/android/os/Looper#dump(android.util.Printer,%20java.lang.String)
 */
internal fun fw4WaitSnapshot(label: String, detail: String, caller: Thread = Thread.currentThread()) {
    Log.i(FW4_RECEIPT_TAG, "WAIT $label at=${SystemClock.elapsedRealtime()} $detail")
    try {
        val main = Looper.getMainLooper()
        Thread.getAllStackTraces().forEach { (thread, stack) ->
            if (thread === main.thread || thread === caller ||
                thread.name.contains("EyeBrowseWindowReadback") ||
                thread.name.contains("EyeBrowseHostingCapture") || thread.name == "RenderThread") {
                val blocker = LockSupport.getBlocker(thread)
                val identity = blocker?.let { "${it.javaClass.name}@${System.identityHashCode(it)}" }
                Log.i(FW4_RECEIPT_TAG, "WAIT $label thread=${thread.name}/${thread.id} " +
                    "state=${thread.state} parkBlocker=$identity monitorOwner=unavailable")
                stack.take(24).forEach { Log.i(FW4_RECEIPT_TAG, "WAIT $label $it") }
            }
        }
        // The head is queue evidence, not proof of lock ownership. Do not print message payloads.
        var lines = 0
        main.dump(Printer { line ->
            if (lines++ < 6) Log.i(FW4_RECEIPT_TAG,
                "QUEUE $label ${line.substringBefore(" obj=")}")
        }, "")
    } catch (error: RuntimeException) {
        Log.i(FW4_RECEIPT_TAG, "WAIT $label snapshotUnavailable=${error.javaClass.name}")
    }
}

/** One queued marker at a time; normal Handler service, never IdleHandler or front-of-queue.
 * A 25ms observation cadence allows at most 2000/25+1 = 81 samples in the original 2s window.
 * Cadence is NOT an acceptance bound. Record uptime queue latency and elapsedRealtime copy overlap.
 */
internal class Fw4MainServiceProbe(
    private val deadlineElapsedMs: Long,
    private val copyOutstanding: () -> Boolean,
    private val status: () -> Unit,
) : AutoCloseable {
    val first = CountDownLatch(1)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var closed = false
    private val samples = mutableListOf<Sample>()

    private class Sample(
        val queuedElapsed: Long, val dueUptime: Long, val queuedDuringCopy: Boolean,
    ) {
        @Volatile var enteredElapsed = -1L
        @Volatile var enteredUptime = -1L
        @Volatile var returnedElapsed = -1L
        @Volatile var duringCopy = false
        var accepted = false
        val done = CountDownLatch(1)
        lateinit var task: Runnable
    }

    init { postSample(0) }

    private fun postSample(delayMs: Long): Unit = synchronized(lock) {
        if (closed || SystemClock.elapsedRealtime() >= deadlineElapsedMs || samples.size >= 81) {
            return@synchronized
        }
        val sample = Sample(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis() + delayMs,
            copyOutstanding())
        sample.task = Runnable {
            if (synchronized(lock) { closed }) return@Runnable
            sample.enteredElapsed = SystemClock.elapsedRealtime()
            sample.enteredUptime = SystemClock.uptimeMillis()
            sample.duringCopy = copyOutstanding()
            try {
                status()
                sample.returnedElapsed = SystemClock.elapsedRealtime()
                first.countDown()
            } finally {
                sample.done.countDown()
            }
            if (copyOutstanding()) postSample(25)
        }
        samples.add(sample)
        sample.accepted = handler.postAtTime(sample.task, sample.dueUptime)
        if (!sample.accepted) sample.done.countDown()
    }

    override fun close() {
        try {
            // Finish a pending sample only within the unchanged window and existing 200ms bound.
            val last = synchronized(lock) { samples.lastOrNull() }
            last?.done?.await((deadlineElapsedMs - SystemClock.elapsedRealtime()).coerceIn(0, 200),
                TimeUnit.MILLISECONDS)
        } finally {
            val snapshot = synchronized(lock) {
                closed = true
                samples.forEach { handler.removeCallbacks(it.task) }
                samples.toList()
            }
            snapshot.forEachIndexed { index, s ->
                Log.i(FW4_RECEIPT_TAG, "MAIN_SAMPLE i=$index queued=${s.queuedElapsed} " +
                    "dueUptime=${s.dueUptime} entry=${s.enteredElapsed} " +
                    "entryUptime=${s.enteredUptime} exit=${s.returnedElapsed} " +
                    "queuedDuringCopy=${s.queuedDuringCopy} servedDuringCopy=${s.duringCopy} " +
                    "accepted=${s.accepted}")
            }
            // Include markers submitted during readback but only serviced AFTER it returned.
            // Excluding those would erase exactly the blocking interval under investigation.
            val latencies = snapshot.filter { it.enteredUptime >= 0 && it.queuedDuringCopy }
                .map { it.enteredUptime - it.dueUptime }.sorted()
            Log.i(FW4_RECEIPT_TAG, "MAIN_LATENCY_MS sorted=$latencies " +
                "unserved=${snapshot.count { it.enteredElapsed < 0 }} " +
                "unfinished=${snapshot.count { it.enteredElapsed >= 0 && it.returnedElapsed < 0 }} " +
                "observedAt=${SystemClock.elapsedRealtime()} observedUptime=${SystemClock.uptimeMillis()} " +
                "deadline=$deadlineElapsedMs")
        }
    }
}

/** HostingController.stop/completeStop @0448330 execute on Main; editor retirement is posted.
 * PrivateDisplayHost.releaseCaptureResources uses quitSafely, not join. Neither source proves
 * native teardown cannot block. Capture a live snapshot at the UNCHANGED 750ms wrapper bound.
 * The latch below controls ONLY this observer; it is not a product cancellation/readback latch.
 */
internal class Fw4StopReceipt(
    private val requestedElapsed: Long,
    private val detail: () -> String,
) : AutoCloseable {
    @Volatile private var enteredElapsed = -1L
    @Volatile private var returnedElapsed = -1L
    private val caller = Thread.currentThread()
    private val done = CountDownLatch(1)
    private fun receipt() = "requested=$requestedElapsed mainEntry=$enteredElapsed " +
        "mainExit=$returnedElapsed ${detail()}"
    private val observer = Thread({
        if (!done.await((requestedElapsed + 750 - SystemClock.elapsedRealtime()).coerceAtLeast(0),
                TimeUnit.MILLISECONDS)) {
            fw4WaitSnapshot("stop-at-750ms", receipt(), caller)
        }
    }, "fw4-stop-wait-observer").apply { isDaemon = true; start() }

    fun enterMain() { enteredElapsed = SystemClock.elapsedRealtime() }
    fun exitMain() { returnedElapsed = SystemClock.elapsedRealtime() }

    override fun close() {
        done.countDown()
        observer.join(250)
        Log.i(FW4_RECEIPT_TAG, "STOP_MAIN ${receipt()} observerAlive=${observer.isAlive}")
    }
}
