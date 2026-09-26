package com.code2hack.eyebrowse.phone

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Test execution only. A caller deadline is NOT cancellation of a running Main/native action. */
internal class Fw4BoundedMainCall<T>(private val action: () -> T) : Runnable {
    enum class Phase { QUEUED, RUNNING, COMPLETED, CANCELLED_BEFORE_ENTRY }

    class DeadlineExceeded(val observedPhase: Phase, boundMs: Long) : AssertionError(
        "Main-test call exceeded ${boundMs}ms; phase=$observedPhase; " +
            "only queued work can be cancelled; running product work remains unresolved",
    )

    private val state = AtomicReference(Phase.QUEUED)
    private val completed = CountDownLatch(1)
    private var value: Any? = null
    private var failure: Throwable? = null

    fun phase(): Phase = state.get()

    override fun run() {
        if (!state.compareAndSet(Phase.QUEUED, Phase.RUNNING)) return
        try {
            value = action()
        } catch (error: Throwable) {
            // Preserve the exact test failure on the test thread, not as an uncaught Main error.
            failure = error
        } finally {
            state.set(Phase.COMPLETED)
            completed.countDown()
        }
    }

    private fun cancelBeforeEntry(): Boolean =
        state.compareAndSet(Phase.QUEUED, Phase.CANCELLED_BEFORE_ENTRY)

    fun await(boundMs: Long): T {
        require(boundMs > 0)
        val finished = try {
            completed.await(boundMs, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            cancelBeforeEntry()
            Thread.currentThread().interrupt()
            throw interrupted
        }
        if (!finished) {
            // This CAS races safely with run(): a cancelled queued callback cannot later mutate
            // the fixture during failure cleanup. A running callback is never interrupted/retried.
            cancelBeforeEntry()
            throw DeadlineExceeded(state.get(), boundMs)
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
