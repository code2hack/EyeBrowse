package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CommandSequenceTest {
    private val ctx=ControlContext("life",1,"doc",2,3)
    private val key=CommandSequence.Namespace.of(ctx)
    private class Harness(initial: CommandSequence.Cursor?=null) {
        var saved=initial
        var clock=0L
        var reads=0
        val writes=mutableListOf<CommandSequence.Cursor>()
        val jobs=ArrayDeque<()->Unit>()
        var reader: ()->CommandSequence.Cursor? = { saved }
        var writer: (CommandSequence.Cursor)->Boolean = { saved=it;true }
        val model=CommandSequence({reads++;reader()},{writes.add(it);writer(it)},{jobs.addLast(it)},{clock})
        var owner=model.claim {}
        fun demand(key: CommandSequence.Namespace) = model.demand(owner,key)
        fun run() { jobs.removeFirst().invoke() }
        fun state() = model.snapshot(owner)
        fun take(key: CommandSequence.Namespace) = model.consume(owner,key,state().revision)
        fun retry(key: CommandSequence.Namespace) { model.cancel(owner);demand(key) }
    }
    private fun cursor(n:Long,epoch:Long=1)=CommandSequence.Cursor("life",epoch,n)

    @Test fun recreationAndReconnectRetainOrdinalAcrossFullContextChanges() {
        val h=Harness()
        h.demand(key);h.run();assertEquals(1L,h.take(key)!!.sequence)
        h.demand(CommandSequence.Namespace.of(ctx.copy(documentId="new",viewportEpoch=9)))
        h.run();assertEquals(2L,h.take(key)!!.sequence)
        h.demand(key);h.run() // 3 is durable but never consumed.
        h.owner=h.model.claim {};h.demand(key);h.run()
        val afterRecreation=h.take(key)!!
        assertEquals(4L,afterRecreation.sequence)
        val current=ctx.copy(documentId="new",viewportEpoch=9,hostingGeneration=7)
        assertEquals(BrowserCommandId.create(current,4),afterRecreation.message(current,BrowserAction.Back).commandId)
        val restarted=Harness(h.saved);restarted.demand(key);restarted.run()
        assertEquals(5L,restarted.take(key)!!.sequence)
        val next=CommandSequence.Namespace.of(ctx.copy(controlEpoch=2))
        restarted.demand(next);restarted.run();assertEquals(1L,restarted.take(next)!!.sequence)
    }
    @Test fun failedPersistenceAndExhaustedSequenceNeverProduceSendableCommand() {
        val failed=Harness();failed.writer={false};failed.demand(key);failed.run()
        assertEquals(CommandSequence.Phase.FAILED,failed.state().phase);assertNull(failed.take(key))
        val exhausted=Harness(cursor(Long.MAX_VALUE));exhausted.demand(key);exhausted.run()
        assertEquals(CommandSequence.Phase.FAILED,exhausted.state().phase);assertTrue(exhausted.writes.isEmpty())
    }
    @Test fun stableEpochHasNoSmallActionCeilingAndEachIdIsUnique() {
        val h=Harness()
        for(i in 1L..10_001L) {
            h.demand(key);assertEquals(1,h.jobs.size);h.run()
            val next=h.take(key)!!.message(ctx,BrowserAction.ScrollBy(0f,1f))
            assertEquals(i,next.commandSequence);assertTrue(BrowserCommandId.matches(next.commandId,ctx,i))
            assertTrue(h.jobs.isEmpty())
        }
        assertEquals(10_001L,h.saved!!.sequence);assertEquals(10_001L,h.model.consumedCount())
    }

    @Test fun slowPersistenceDoesNotHoldMemoryLockOrPublishBeforeDurability() {
        val h=Harness();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        h.writer={entered.countDown();check(release.await(3,TimeUnit.SECONDS));h.saved=it;true}
        h.demand(key);val job=h.jobs.removeFirst()
        val io=Executors.newSingleThreadExecutor();val ui=Executors.newSingleThreadExecutor()
        try {
            val work=io.submit(job);assertTrue(entered.await(1,TimeUnit.SECONDS))
            ui.submit {
                assertEquals(CommandSequence.Phase.PREPARING,h.state().phase)
                assertNull(h.take(key))
                repeat(100) { h.demand(key) }
                assertTrue(h.jobs.isEmpty())
            }.get(1,TimeUnit.SECONDS)
            release.countDown();work.get(1,TimeUnit.SECONDS)
            assertEquals(1L,h.take(key)!!.sequence)
        } finally { release.countDown();io.shutdownNow();ui.shutdownNow() }
    }
    @Test fun slowPreferenceLoadDoesNotBlockClaimCancelOrSnapshot() {
        val h=Harness();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        h.reader={entered.countDown();check(release.await(3,TimeUnit.SECONDS));null}
        h.demand(key);val job=h.jobs.removeFirst()
        val io=Executors.newSingleThreadExecutor();val ui=Executors.newSingleThreadExecutor()
        try {
            val work=io.submit(job);assertTrue(entered.await(1,TimeUnit.SECONDS))
            ui.submit { h.model.cancel(h.owner);h.owner=h.model.claim {};assertNull(h.take(key)) }.get(1,TimeUnit.SECONDS)
            release.countDown();work.get(1,TimeUnit.SECONDS)
            assertTrue(h.writes.isEmpty())
        } finally { release.countDown();io.shutdownNow();ui.shutdownNow() }
    }
    @Test fun duplicateConsumersCannotSharePreparedNumber() {
        val h=Harness();h.demand(key);h.run();val revision=h.state().revision
        val go=CountDownLatch(1);val threads=Executors.newFixedThreadPool(2)
        try {
            val consumers=(1..2).map { threads.submit<CommandSequence.Cursor?> {
                check(go.await(1,TimeUnit.SECONDS));h.model.consume(h.owner,key,revision)
            } }
            go.countDown()
            assertEquals(1,consumers.map { it.get(1,TimeUnit.SECONDS) }.count { it!=null })
            assertEquals(1L,h.model.consumedCount());assertEquals(1,h.writes.size)
        } finally { threads.shutdownNow() }
    }
    @Test fun repeatedDemandCannotRenewDeadlineOrQueueExtraWork() {
        val h=Harness();h.demand(key)
        repeat(39) { h.clock+=100;h.demand(key);assertEquals(CommandSequence.PREPARATION_GUARD_MS,h.state().deadline) }
        assertEquals(1,h.jobs.size)
        h.clock=CommandSequence.PREPARATION_GUARD_MS;assertEquals(CommandSequence.Phase.FAILED,h.state().phase)
        h.demand(key);h.run();assertTrue(h.writes.isEmpty())
        assertEquals(CommandSequence.Phase.FAILED,h.state().phase);assertTrue(h.jobs.isEmpty())
    }
    @Test fun timeoutDoesNotCancelWriterOrPermitCompetingReplacementWrite() {
        val h=Harness();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        h.writer={entered.countDown();check(release.await(3,TimeUnit.SECONDS));h.saved=it;true}
        h.demand(key);val job=h.jobs.removeFirst();val io=Executors.newSingleThreadExecutor()
        try {
            val work=io.submit(job);assertTrue(entered.await(1,TimeUnit.SECONDS))
            h.clock=CommandSequence.PREPARATION_GUARD_MS;assertEquals(CommandSequence.Phase.FAILED,h.state().phase)
            h.retry(key);assertTrue(h.jobs.isEmpty()) // Old IO is still physically active.
            release.countDown();work.get(1,TimeUnit.SECONDS)
            assertNull(h.take(key));assertEquals(1,h.jobs.size);assertEquals(1L,h.saved!!.sequence)
            h.run();assertEquals(2L,h.take(key)!!.sequence)
        } finally { release.countDown();io.shutdownNow() }
    }
    @Test fun replacementFencesOldPublicationAndBurnsItsDurableOrdinal() {
        val h=Harness()
        h.writer={ value ->
            h.owner=h.model.claim {};h.demand(key)
            assertTrue(h.jobs.isEmpty());h.saved=value;true
        }
        h.demand(key);val old=h.owner;h.run()
        assertEquals(CommandSequence.Phase.RETIRED,h.model.snapshot(old).phase)
        assertNull(h.take(key));assertEquals(1,h.jobs.size)
        h.writer={h.saved=it;true};h.run()
        assertEquals(2L,h.take(key)!!.sequence)
    }
    @Test fun epochChangeDuringIoCannotRollbackDiskOrPublishOldReadiness() {
        val h=Harness();val next=key.copy(epoch=2)
        h.writer={ value -> h.demand(next);assertTrue(h.jobs.isEmpty());h.saved=value;true }
        h.demand(key);h.run();assertNull(h.take(next))
        h.writer={h.saved=it;true};h.run()
        assertEquals(cursor(1,2),h.take(next))
        assertEquals(listOf(cursor(1),cursor(1,2)),h.writes)
        h.demand(key);h.run() // Even a stale caller cannot reset a newer stored epoch.
        assertEquals(CommandSequence.Phase.FAILED,h.state().phase)
        assertEquals(cursor(1,2),h.saved);assertEquals(2,h.writes.size)
    }
    @Test fun cancellationBeforeWorkerStartDoesNoPreferenceIo() {
        val h=Harness();h.demand(key);h.model.cancel(h.owner);h.run()
        assertEquals(0,h.reads);assertTrue(h.writes.isEmpty());assertNull(h.take(key))
    }
    @Test fun missingOrRolledBackKnownCursorFailsClosed() {
        for(replacement in listOf(null,cursor(1))) {
            val h=Harness(cursor(1));h.demand(key);h.run();assertEquals(2L,h.take(key)!!.sequence)
            h.saved=replacement;h.demand(key);h.run()
            assertEquals(CommandSequence.Phase.FAILED,h.state().phase);assertNull(h.take(key))
            assertEquals(1,h.writes.size)
        }
    }
    @Test fun cancelledLegacyReadStillRemembersTheExpectedStoredFloor() {
        val h=Harness(cursor(7))
        h.reader={ val old=h.saved;h.model.cancel(h.owner);old }
        h.demand(key);h.run();assertTrue(h.writes.isEmpty())
        h.reader={null};h.demand(key);h.run()
        assertEquals(CommandSequence.Phase.FAILED,h.state().phase);assertTrue(h.writes.isEmpty())
    }
    @Test fun malformedCursorCannotBecomeOrdinalOne() {
        for(saved in listOf(cursor(0),cursor(-1),cursor(2,-1),CommandSequence.Cursor("",1,2))) {
            val h=Harness(saved);h.demand(key);h.run()
            assertEquals(CommandSequence.Phase.FAILED,h.state().phase);assertTrue(h.writes.isEmpty())
        }
    }
    @Test fun failedOrThrowingWriteIsNotPublishedAndUncertainAllocationIsBurned() {
        for(throws in listOf(false,true)) {
            val h=Harness();h.writer={h.saved=it;if(throws) error("uncertain completion") else false}
            h.demand(key);h.run();assertNull(h.take(key));assertEquals(CommandSequence.Phase.FAILED,h.state().phase)
            h.writer={h.saved=it;true};h.retry(key);h.run()
            assertEquals(2L,h.take(key)!!.sequence)
        }
    }
    @Test fun queueRejectionCannotRecycleConsumedNumber() {
        val h=Harness();h.demand(key);h.run()
        val rejected=h.take(key)!!.message(ctx,BrowserAction.Reload)
        assertNull(h.take(key)) // Queue rejection has no put-back/retry operation.
        h.demand(key);h.run();val next=h.take(key)!!.message(ctx,BrowserAction.Reload)
        assertEquals(1L,rejected.commandSequence);assertEquals(2L,next.commandSequence)
        assertNotEquals(rejected.commandId,next.commandId)
    }
    @Test fun fullSlotAndSameNamespaceDocumentUpdatesDoNotWriteAgain() {
        val h=Harness();h.demand(key);h.run();val ready=h.state()
        repeat(100) { h.demand(CommandSequence.Namespace.of(ctx.copy(documentId="next",viewportEpoch=8))) }
        assertEquals(ready,h.state());assertTrue(h.jobs.isEmpty());assertEquals(1,h.writes.size)
        val context=ctx.copy(documentId="next",viewportEpoch=8)
        assertEquals(BrowserCommandId.create(context,1),h.take(key)!!.message(context,BrowserAction.Back).commandId)
    }
    @Test fun unavailableOrConsumedSnapshotCannotResurrectAfterRefill() {
        val h=Harness();h.demand(key);val unavailable=h.state().revision
        h.run();assertNull(h.model.consume(h.owner,key,unavailable))
        val ready=h.state().revision;assertNotNull(h.model.consume(h.owner,key,ready))
        h.demand(key);h.run()
        assertNull(h.model.consume(h.owner,key,ready));assertEquals(2L,h.take(key)!!.sequence)
    }
    @Test fun lateCompletionCannotPublishEvenWhenTimeoutWasNotPolled() {
        val h=Harness();h.writer={h.clock=CommandSequence.PREPARATION_GUARD_MS+1;h.saved=it;true}
        h.demand(key);h.run()
        assertEquals(CommandSequence.Phase.FAILED,h.state().phase);assertNull(h.take(key))
        assertEquals(1L,h.saved!!.sequence)
    }
    @Test fun workerRejectionFailsClosedWithoutRetrySpin() {
        var submits=0
        val model=CommandSequence({null},{true},{submits++;error("executor stopped")},{0})
        val owner=model.claim {};repeat(100) { model.demand(owner,key) }
        assertEquals(1,submits);assertEquals(CommandSequence.Phase.FAILED,model.snapshot(owner).phase)
    }
    @Test fun releasedConsumerCannotRefillOrConsumeItsSuccessorSlot() {
        val h=Harness();h.demand(key);h.run();val old=h.owner
        h.model.release(old);h.owner=h.model.claim {};h.demand(key);h.run()
        h.model.cancel(old);h.model.demand(old,key.copy(epoch=2))
        assertNull(h.model.consume(old,key,h.state().revision))
        assertEquals(2L,h.take(key)!!.sequence)
    }
}
