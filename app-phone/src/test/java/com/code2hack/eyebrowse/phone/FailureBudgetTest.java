package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Exercises the exact queued-operation/token path used by Browser diagnostics, not a classifier. */
public class FailureBudgetTest {
    private static final class Clock {
        long now;
    }

    private static final class Queue implements HarnessProtocol.MainQueue {
        final List<Runnable> queued = new ArrayList<>();
        int posts;
        @Override public void post(Runnable work) { posts++; queued.add(work); }
        @Override public void remove(Runnable work) { queued.remove(work); }
        void runNext() { queued.remove(0).run(); }
    }

    @Test public void remainingBudgetCountsDownToExpiry() {
        Clock clock = new Clock();
        HarnessProtocol.FailureBudget budget = new HarnessProtocol.FailureBudget(() -> clock.now, 2_000);
        assertEquals(2_000, budget.stepBudgetMs());
        assertFalse(budget.expired());
        clock.now = 1_500;
        assertEquals(500, budget.stepBudgetMs());
        assertEquals(1_500, budget.elapsedMs());
        clock.now = 2_000;
        assertEquals(0, budget.stepBudgetMs());
        assertTrue(budget.expired());
    }

    @Test public void queuedWorkExpiredBeforeMainDispatchNeverAccessesTarget() {
        Clock clock = new Clock();
        Queue queue = new Queue();
        AtomicInteger accesses = new AtomicInteger();
        HarnessProtocol.DiagnosticOwner owner = new HarnessProtocol.DiagnosticOwner();
        HarnessProtocol.FailureBudget budget = new HarnessProtocol.FailureBudget(() -> clock.now, 2_000);
        HarnessProtocol.Diagnostic operation = new HarnessProtocol.Diagnostic(owner, budget, queue,
                token -> accesses.incrementAndGet());
        operation.schedule();
        Runnable staleQueueEntry = queue.queued.get(0);
        clock.now = 2_000;
        assertNull(operation.await());
        assertTrue(queue.queued.isEmpty());
        staleQueueEntry.run(); // A dequeued-but-delayed main runnable is also fenced.
        assertEquals(0, accesses.get());
        assertEquals(0, owner.pendingCount());
    }

    @Test public void withheldAndLateCallbackCannotPublishOrDispatchSecondStep() {
        Clock clock = new Clock();
        Queue queue = new Queue();
        HarnessProtocol.DiagnosticOwner owner = new HarnessProtocol.DiagnosticOwner();
        HarnessProtocol.FailureBudget budget = new HarnessProtocol.FailureBudget(() -> clock.now, 2_000);
        HarnessProtocol.Diagnostic[] platformCallback = {null};
        HarnessProtocol.Diagnostic first = new HarnessProtocol.Diagnostic(owner, budget, queue,
                token -> platformCallback[0] = token); // JS dispatched, completion withheld.
        first.schedule();
        queue.runNext();
        clock.now = 2_001;
        assertNull(first.await());
        platformCallback[0].complete("late old document");
        assertNull(first.await());
        AtomicInteger secondAccess = new AtomicInteger();
        HarnessProtocol.Diagnostic second = new HarnessProtocol.Diagnostic(owner, budget, queue,
                token -> secondAccess.incrementAndGet());
        second.schedule();
        assertNull(second.await());
        assertEquals("expired second step never even posts", 1, queue.posts);
        assertEquals(0, secondAccess.get());
        assertEquals(0, owner.pendingCount());
    }

    @Test public void withheldCallbackTimesOutWithoutAnAbandonedWorker() {
        Queue queue = new Queue();
        HarnessProtocol.DiagnosticOwner owner = new HarnessProtocol.DiagnosticOwner();
        HarnessProtocol.FailureBudget budget = new HarnessProtocol.FailureBudget(
                () -> System.nanoTime() / 1_000_000, 10);
        HarnessProtocol.Diagnostic operation = new HarnessProtocol.Diagnostic(owner, budget, queue,
                token -> { /* platform withholds completion */ });
        operation.schedule();
        if (!queue.queued.isEmpty()) queue.runNext();
        assertNull(operation.await());
        assertFalse(operation.active());
        assertEquals(0, owner.pendingCount());
    }

    @Test public void teardownAndReplacementOwnerCannotReceiveOldCompletion() {
        Queue queue = new Queue();
        Clock clock = new Clock();
        HarnessProtocol.DiagnosticOwner oldOwner = new HarnessProtocol.DiagnosticOwner();
        HarnessProtocol.Diagnostic old = new HarnessProtocol.Diagnostic(oldOwner,
                new HarnessProtocol.FailureBudget(() -> clock.now, 2_000), queue, token -> {});
        old.schedule();
        queue.runNext();
        oldOwner.close();
        HarnessProtocol.DiagnosticOwner replacement = new HarnessProtocol.DiagnosticOwner();
        HarnessProtocol.Diagnostic current = new HarnessProtocol.Diagnostic(replacement,
                new HarnessProtocol.FailureBudget(() -> clock.now, 2_000), queue,
                token -> token.complete("current"));
        current.schedule();
        old.complete("obsolete");
        queue.runNext();
        assertEquals("current", current.await());
        assertNull(old.await());
        assertEquals(0, oldOwner.pendingCount());
        assertEquals(0, replacement.pendingCount());
    }

    @Test public void teardownRemovesQueuedClosureBeforeItCanAccessTheOldActivity() {
        Queue queue = new Queue();
        AtomicInteger accesses = new AtomicInteger();
        HarnessProtocol.DiagnosticOwner owner = new HarnessProtocol.DiagnosticOwner();
        HarnessProtocol.Diagnostic operation = new HarnessProtocol.Diagnostic(owner,
                new HarnessProtocol.FailureBudget(() -> 0L, 2_000), queue,
                token -> accesses.incrementAndGet());
        operation.schedule();
        Runnable stale = queue.queued.get(0);
        owner.close();
        stale.run();
        assertEquals(0, accesses.get());
        assertTrue(queue.queued.isEmpty());
        assertNull(operation.await());
    }

    @Test public void interruptionCancelsSharedBudgetAndPreservesInterruptFlag() {
        Queue queue = new Queue();
        HarnessProtocol.DiagnosticOwner owner = new HarnessProtocol.DiagnosticOwner();
        HarnessProtocol.FailureBudget budget = new HarnessProtocol.FailureBudget(() -> 0L, 2_000);
        HarnessProtocol.Diagnostic operation = new HarnessProtocol.Diagnostic(owner, budget, queue, token -> {});
        operation.schedule();
        Thread.currentThread().interrupt();
        try {
            assertNull(operation.await());
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(budget.expired());
            assertTrue(queue.queued.isEmpty());
            assertEquals(0, owner.pendingCount());
        } finally {
            Thread.interrupted();
        }
    }

    @Test public void schedulingFailurePreservesTheSamePrimaryErrorAndReleasesWork() {
        AssertionError primary = new AssertionError("original rejected DOWN");
        IllegalStateException scheduling = new IllegalStateException("main queue closed");
        HarnessProtocol.DiagnosticOwner owner = new HarnessProtocol.DiagnosticOwner();
        HarnessProtocol.MainQueue queue = new HarnessProtocol.MainQueue() {
            @Override public void post(Runnable work) { throw scheduling; }
            @Override public void remove(Runnable work) {}
        };
        assertSame(primary, assertThrows(AssertionError.class, () -> {
            try { throw primary; }
            catch (AssertionError failure) {
                HarnessProtocol.preserveFailure(failure, () -> {
                    HarnessProtocol.Diagnostic operation = new HarnessProtocol.Diagnostic(owner,
                            new HarnessProtocol.FailureBudget(() -> 0L, 2_000), queue, token -> {});
                    operation.schedule();
                    operation.await();
                });
                throw failure;
            }
        }));
        assertArrayEquals(new Throwable[]{scheduling}, primary.getSuppressed());
        assertEquals(0, owner.pendingCount());
    }

    @Test public void actualUiReplacementAfterDispatchRejectsCompletionBeforeTestTeardown() {
        Queue queue = new Queue();
        HarnessProtocol.DiagnosticOwner testOwner = new HarnessProtocol.DiagnosticOwner();
        Object intended = new Object();
        java.lang.ref.WeakReference<Object> intendedRef = new java.lang.ref.WeakReference<>(intended);
        java.util.concurrent.atomic.AtomicReference<Object> currentUi =
                new java.util.concurrent.atomic.AtomicReference<>(intended);
        HarnessProtocol.Diagnostic[] callback = {null};
        HarnessProtocol.Diagnostic operation = new HarnessProtocol.Diagnostic(testOwner,
                new HarnessProtocol.FailureBudget(() -> 0L, 2_000), queue,
                token -> callback[0] = token);
        operation.schedule();
        queue.runNext(); // JS dispatched while the intended owner is current.
        currentUi.set(new Object()); // Same test remains open; Activity/view ownership changes.
        callback[0].completeIfOwned("obsolete document", () -> currentUi.get() == intendedRef.get());
        assertTrue("test lifetime is deliberately still open", testOwner.isOpen());
        assertNull(operation.await());
        assertFalse(operation.active());
        assertEquals(0, testOwner.pendingCount());
    }

    @Test public void unchangedActualUiOwnerAdmitsCompletion() {
        Queue queue = new Queue();
        HarnessProtocol.Diagnostic operation = new HarnessProtocol.Diagnostic(
                new HarnessProtocol.DiagnosticOwner(), new HarnessProtocol.FailureBudget(() -> 0L, 2_000),
                queue, token -> token.completeIfOwned("current", () -> true));
        operation.schedule();
        queue.runNext();
        assertEquals("current", operation.await());
    }

    @Test public void completionBeforeDeadlineIsCollectedOnlyOnce() {
        Queue queue = new Queue();
        HarnessProtocol.Diagnostic operation = new HarnessProtocol.Diagnostic(
                new HarnessProtocol.DiagnosticOwner(), new HarnessProtocol.FailureBudget(() -> 0L, 2_000),
                queue, token -> {
                    token.complete("on time");
                    token.complete("duplicate before readback");
                });
        operation.schedule();
        queue.runNext();
        assertEquals("on time", operation.await());
        operation.complete("duplicate");
        assertNull(operation.await());
    }
}
