package com.code2hack.eyebrowse.phone

import org.junit.Assert.*
import org.junit.Test

/** Exercises the production pre-admission driver with deterministic native observations and Main queue. */
class ProfileOffSettlementTest {
    private val off=ProfileOffSettlement.State(true,true,true,true,true,true,true,true,true,true,ProfileOffSettlement.Display.OFF)
    private class Clock { var at=0L }
    private inner class Harness {
        val clock=Clock()
        var state=off
        var failPost=false
        var onAdmit:(()->Unit)?=null
        var allocations=0
        var mutations=0
        var peak=0
        val failures=mutableListOf<Boolean>()
        val queue=mutableListOf<Pair<Long,Runnable>>()
        val gate=ProfileOffSettlement(100, { clock.at }, { state }, { task,delay ->
            if(failPost) false else { assertTrue(delay>0);queue.add(clock.at+delay to task);peak=maxOf(peak,queue.size);true }
        }, { task -> queue.removeAll { it.second===task } }, { if(onAdmit!=null)onAdmit!!.invoke() else { allocations++;mutations++ } }, { retired -> failures.add(retired) })
        fun next(at:Long?=null):Runnable {
            val (due,task)=queue.removeAt(0);clock.at=at ?: due;task.run();return task
        }
    }
    @Test fun offToOnUsesOneWaitAndOneOriginalDeadline() {
        val h=Harness();h.gate.start()
        repeat(3) { h.next();assertEquals(0,h.allocations);assertEquals(0,h.mutations);assertTrue(h.failures.isEmpty()) }
        h.state=h.state.copy(display=ProfileOffSettlement.Display.ON);h.next()
        assertEquals(1,h.allocations);assertEquals(1,h.mutations);assertTrue(h.queue.isEmpty());assertEquals(1,h.peak)
        h.gate.start();assertEquals(1,h.mutations)
        h.clock.at=100;assertFalse("ON did not grant a fresh completion budget",h.gate.completionInTime())
    }
    @Test fun alreadyOnControlDoesNotPostOrWait() {
        val h=Harness();h.state=h.state.copy(display=ProfileOffSettlement.Display.ON);h.gate.start()
        assertEquals(1,h.mutations);assertTrue(h.queue.isEmpty());assertTrue(h.failures.isEmpty())
    }
    @Test fun persistentOffExpiresOnceWithoutAnyAllocation() {
        val h=Harness();h.gate.start()
        while(h.queue.isNotEmpty())h.next()
        assertEquals(100L,h.clock.at);assertEquals(listOf(false),h.failures)
        assertEquals(0,h.allocations);assertEquals(1,h.peak)
        h.gate.start();assertEquals(1,h.failures.size)
    }
    @Test fun onAtOrAfterDeadlineAndLateWakeCannotAdmit() {
        for(at in listOf(100L,101L,500L)) {
            val h=Harness();h.gate.start();h.state=h.state.copy(display=ProfileOffSettlement.Display.ON)
            h.next(at);assertEquals(listOf(false),h.failures);assertEquals(0,h.mutations)
        }
    }
    @Test fun onBeforeDeadlineDoesNotAuthorizeLateFrameCompletion() {
        val h=Harness();h.gate.start();h.state=h.state.copy(display=ProfileOffSettlement.Display.ON);h.next(99)
        assertEquals(1,h.mutations);assertTrue(h.gate.completionInTime())
        h.clock.at=100;assertFalse(h.gate.completionInTime())
        h.clock.at=101;assertFalse(h.gate.completionInTime())
    }
    @Test fun allNonStateGuardsRemainFailuresWhileOff() {
        val invalid=listOf(off.copy(reader=false),off.copy(noConflict=false),off.copy(attached=false),
            off.copy(density=false),off.copy(focus=false),off.copy(focusHistory=false),
            off.copy(available=false),off.copy(validDisplay=false),off.copy(display=ProfileOffSettlement.Display.UNSUPPORTED))
        for(state in invalid) {
            val h=Harness();h.state=state;h.gate.start()
            assertEquals(listOf(false),h.failures);assertTrue(h.queue.isEmpty());assertEquals(0,h.allocations)
        }
    }
    @Test fun aGuardLostDuringWaitCannotBeRescuedByOn() {
        val h=Harness();h.gate.start();h.state=h.state.copy(reader=false,display=ProfileOffSettlement.Display.ON);h.next()
        assertEquals(listOf(false),h.failures);assertEquals(0,h.mutations)
    }
    @Test fun focusLossAndReturnDoesNotEraseHistory() {
        val h=Harness();h.gate.start()
        // Focus is true again at observation; original wait-entry history still invalidates the transaction.
        h.state=h.state.copy(focus=true,focusHistory=false,display=ProfileOffSettlement.Display.ON);h.next()
        assertEquals(listOf(false),h.failures);assertEquals(0,h.mutations)
    }
    @Test fun ownershipOrIdentityReplacementRetiresInsteadOfCurrentFailure() {
        for(state in listOf(off.copy(owned=false),off.copy(identities=false))) {
            val h=Harness();h.gate.start();h.state=state;h.next()
            assertEquals(listOf(true),h.failures);assertEquals(0,h.mutations);assertTrue(h.queue.isEmpty())
        }
    }
    @Test fun cancelledDequeuedCallbackCannotAdmitOrFailSuccessor() {
        val old=Harness();old.gate.start();val dequeued=old.queue.removeAt(0).second
        old.gate.cancel()
        val successor=Harness();successor.state=off.copy(display=ProfileOffSettlement.Display.ON);successor.gate.start()
        old.state=off.copy(display=ProfileOffSettlement.Display.ON);dequeued.run();old.gate.start()
        assertEquals(0,old.mutations);assertTrue(old.failures.isEmpty());assertEquals(1,successor.mutations)
    }
    @Test fun failedSchedulingFailsOnceAndLeavesNoOwnedCheck() {
        val h=Harness();h.failPost=true;h.gate.start();h.gate.start()
        assertEquals(listOf(false),h.failures);assertTrue(h.queue.isEmpty());assertEquals(0,h.allocations)
    }
    @Test fun deadlineCancellationCollisionNeverDuplicatesTerminalWork() {
        val h=Harness();h.gate.start();val callback=h.queue.first().second
        h.next(100);h.gate.cancel();callback.run()
        assertEquals(listOf(false),h.failures);assertEquals(0,h.mutations);assertTrue(h.queue.isEmpty())
    }
    @Test fun repeatedNotificationsCoalesceIntoOneOutstandingCheck() {
        val h=Harness();h.gate.start();repeat(40){h.gate.start()}
        assertEquals(1,h.queue.size);assertEquals(0,h.allocations)
        h.next();assertEquals(1,h.queue.size);assertEquals(1,h.peak)
    }
    @Test fun returningToOffBeforeNativeWorkUsesSameOwnedWaitWithoutReentry() {
        val h=Harness();var offers=0
        h.onAdmit={
            offers++
            if(offers==1) { h.state=off;h.gate.awaitOffAgain() }
            else { h.gate.cancel();h.allocations++;h.mutations++ }
        }
        h.state=off.copy(display=ProfileOffSettlement.Display.ON);h.gate.start()
        assertEquals(0,h.allocations);assertEquals(1,h.queue.size)
        h.next();assertEquals(0,h.mutations)
        h.state=off.copy(display=ProfileOffSettlement.Display.ON);h.next()
        assertEquals(1,h.mutations);assertEquals(1,h.allocations);assertEquals(1,h.peak)
        h.gate.awaitOffAgain();assertTrue(h.queue.isEmpty())
        h.clock.at=100;assertFalse(h.gate.completionInTime())
    }

}
