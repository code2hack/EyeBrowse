package com.code2hack.eyebrowse.rg

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class PreparedOrdinalJourneyTest {
    /** Delays one real store call or withholds its success, never fabricates a durable success. */
    @Suppress("UNCHECKED_CAST")
    private class StoreGate(peer:RgPresentationController,val uncertain:Boolean=false) : AutoCloseable {
        val entered=CountDownLatch(1)
        val release=CountDownLatch(if(uncertain) 0 else 1)
        val completed=CountDownLatch(1)
        val armed=AtomicBoolean(true)
        private val sequence=RgPresentationController::class.java.getDeclaredField("commands").apply { isAccessible=true }.get(peer)
        private val field=CommandSequence::class.java.getDeclaredField("persist").apply { isAccessible=true }
        private val original=field.get(sequence) as (CommandSequence.Cursor)->Boolean
        init {
            field.set(sequence,{ cursor:CommandSequence.Cursor ->
                if(!armed.compareAndSet(true,false)) original(cursor) else {
                    check(Looper.myLooper()!=Looper.getMainLooper())
                    entered.countDown()
                    try {
                        check(release.await(12,TimeUnit.SECONDS))
                        val real=original(cursor)
                        Log.i(TAG,"STORE_FAULT_GATE namespace="+cursor.namespace+" ordinal="+cursor.sequence+
                            " realStoreResult="+real+" withheld="+uncertain+" thread="+Thread.currentThread().name+" main=false")
                        if(uncertain) false else real
                    } finally { completed.countDown() }
                }
            })
        }
        override fun close() {
            release.countDown()
            if(entered.count==0L) check(completed.await(3,TimeUnit.SECONDS))
            field.set(sequence,original)
        }
    }

    @Test fun preparedOrdinalMatrixUsesActualQueueAndFixtureEffects() {
        val args=InstrumentationRegistry.getArguments()
        val mode=args.getString("ordinalCase","cold")
        require(mode in listOf("cold","rapid","refill","recreate","stall","uncertain"))
        val count=when(mode) { "rapid" -> 5;"refill","recreate" -> 2;else -> 1 }
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val j=PointerBrowserJourneyTest.Journey(scenario)
        val mission=UUID.fromString(args.getString("missionId")).toString()
        val ack=File(j.app.cacheDir,"i8-"+mission+".ack");ack.writeText("")
        var gate:StoreGate?=null
        var failure:Throwable?=null
        fun waitHost(name:String) {
            val end=SystemClock.uptimeMillis()+8_000
            while(SystemClock.uptimeMillis()<end) { if(ack.readText().trim()==name)return;SystemClock.sleep(10) }
            fail("matrix host acknowledgement "+name)
        }
        fun ready(label:String) {
            j.await("prepared eligibility "+label,5_000) { j.peer.canAct() }
            val state=j.peer.reservationState()
            assertEquals(CommandSequence.Phase.READY,state.phase)
            assertNotNull(state.readyAt);assertNotNull(state.demandAt)
            assertTrue(state.readyAt!!-state.demandAt!!<CommandSequence.PREPARATION_GUARD_MS)
            Log.i(TAG,"MATRIX_READY mode="+mode+" stage="+label+" namespace="+state.namespace+
                " ordinal="+state.ordinal+" demand="+state.demandAt+" published="+state.readyAt+
                " prepareMs="+(state.readyAt!!-state.demandAt!!)+" observed="+SystemClock.uptimeMillis())
        }
        fun localWhileBlocked() {
            assertTrue(gate!!.entered.await(2,TimeUnit.SECONDS))
            j.main { assertFalse(j.peer.canAct());assertTrue(j.peer.canHandoff()) }
            val before=j.actions
            j.native(R.id.rg_recenter,"Recenter-during-storage")
            j.main { j.source.adoptCurrentReference() }
            var x=0f
            j.main { x=j.pointer.position.x;j.source.aim(it,InputPoint(x+20,j.pointer.position.y)) }
            j.await("local draw while writer blocked",100) {
                j.pointer.position.x>x+1 && j.pointer.lastDrawElapsedNs>=j.pointer.lastDrawSampleReceiptNs
            }
            j.main {
                val ms=(j.pointer.lastDrawElapsedNs-j.pointer.lastDrawSampleReceiptNs)/1e6
                assertTrue(ms<=100);Log.i(TAG,"STORE_BLOCKED_UI drawMs="+ms+" recenter=true controllerReadable=true")
            }
            assertEquals(before,j.actions)
        }
        fun activate(index:Int) {
            ready("before-"+index);j.aimPage()
            val before=j.actions;val previous=j.peer.lastActionResult
            var pixels=0L;j.main { pixels=j.frameHash() }
            val tap=j.pad();val trace=j.dispatched(tap,"matrix-"+mode+"-"+index)
            val immediate=j.actions
            assertEquals(before.consumed+1,immediate.consumed)
            assertEquals(before.constructed+1,immediate.constructed)
            assertEquals(before.queued+1,immediate.queued)
            j.result(previous,"matrix-"+mode+"-"+index)
            // Actual visible pixels and title, with independent Phone ledger below.
            j.await("matrix fixture effect "+index,1_000) {
                j.title()=="T03 A click "+index && j.frameHash()!=pixels
            }
            val elapsed=SystemClock.uptimeMillis()-trace.confirmedAt
            assertTrue("matrix effect <=1s: "+elapsed,elapsed<=1_000)
            Log.i(TAG,"MATRIX_ACTION mode="+mode+" index="+index+" localMs="+(trace.finishedAt-trace.confirmedAt)+
                " effectMs="+elapsed+" consumedDelta=1 constructedDelta=1 queuedDelta=1")
            if(mode!="rapid") j.check("click_"+index)
        }
        try {
            j.setup();j.native(R.id.rg_retry,"matrix Retry")
            j.await("authenticated Phone owner",10_000) { j.peer.browserState()?.owner==ControlOwner.PHONE && j.peer.canHandoff() }
            if(mode=="stall" || mode=="uncertain") gate=StoreGate(j.peer,mode=="uncertain")
            if(gate==null) j.handoff(ControlOwner.RG,"A") else {
                val transfer=j.native(R.id.rg_handoff,"matrix handoff while preparing")
                j.await("authoritative handoff") { j.peer.browserState()?.owner==ControlOwner.RG && j.peer.lastHandoffResult?.accepted==true }
                assertTrue(SystemClock.uptimeMillis()-transfer.confirmedAt<=1_000)
                j.await("real frame/base eligible during storage") { j.peer.baseActionEligible() && j.qualified("A",false) }
            }
            Log.i(TAG,"I8_FIRST_FIXTURE_FRAME")
            if(mode=="stall" || mode=="uncertain") {
                if(mode=="stall") localWhileBlocked()
                val before=j.actions
                j.aimPage();j.pad();j.confirmWindow()
                assertEquals(before,j.actions)
                if(mode=="stall") {
                    val demand=checkNotNull(j.peer.reservationState().demandAt)
                    j.await("bounded visible preparation failure",5_000) {
                        j.peer.reservationState().phase==CommandSequence.Phase.FAILED &&
                            j.activity.findViewById<TextView>(R.id.rg_status).text.toString()=="Page controls unavailable — Retry"
                    }
                    val observed=SystemClock.uptimeMillis()-demand
                    assertTrue("failure reported <=5s: "+observed,observed<=5_000)
                    Log.i(TAG,"PREPARATION_GUARD configuredMs="+CommandSequence.PREPARATION_GUARD_MS+" observedMs="+observed)
                } else j.await("uncertain result remains unavailable") { j.peer.reservationState().phase==CommandSequence.Phase.FAILED }
                gate!!.close();gate=null
                j.confirmWindow();assertFalse(j.peer.canAct());assertEquals(before,j.actions)
                j.check("unavailable_verified")
                val old=j.peer.browserState()!!.context
                j.native(R.id.rg_retry,"Retry-preparation-on-live-link")
                ready("explicit-recovery")
                assertEquals(old,j.peer.browserState()!!.context)
                assertEquals(before,j.actions)
                Log.i(TAG,"LOCAL_PREPARATION_RETRY liveLinkRetained=true sameFullContext=true")
            } else ready("cold-first")
            if(mode=="refill") gate=StoreGate(j.peer)
            for(i in 1..count) {
                activate(i)
                if(mode=="refill" && i==1) {
                    localWhileBlocked()
                    val before=j.actions;j.aimPage();val unavailableTap=j.pad()
                    gate!!.close();gate=null
                    ready("refill")
                    val tail=j.pad()
                    val tailMs=tail.down-unavailableTap.up
                    assertTrue("tail is inside the original double window: "+tailMs,tailMs<j.activity.inputRouter.doubleTapMs)
                    j.confirmWindow()
                    assertEquals(before,j.actions);j.check("unavailable_verified")
                    Log.i(TAG,"REFILL_OLD_INTENT consumed=0 constructed=0 queued=0 tailMs="+tailMs)
                }
                if(mode=="recreate" && i==1) {
                    ready("before-recreation")
                    val abandoned=j.peer.reservationState()
                    val old=j.peer.browserState()!!.context
                    val previousCapture=j.peer.lastFrameHeader!!.captureTsMs
                    val consumed=j.actions.consumed
                    j.main { j.probe?.close() }
                    scenario.recreate();j.setup()
                    j.reconnect("A",old.controlEpoch,previousCapture)
                    val current=j.peer.browserState()!!.context
                    assertEquals(old.lifetimeId,current.lifetimeId);assertEquals(old.controlEpoch,current.controlEpoch)
                    assertEquals(old.documentId,current.documentId)
                    ready("after-recreation")
                    val replacement=j.peer.reservationState()
                    assertTrue(replacement.ordinal!!>abandoned.ordinal!!)
                    assertEquals(consumed,j.actions.consumed)
                    Log.i(TAG,"SAME_NAMESPACE_RECREATE namespace="+replacement.namespace+" abandoned="+abandoned.ordinal+
                        " next="+replacement.ordinal+" consumedUnchanged="+consumed)
                }
                if(mode!="refill" || i!=1) ready("after-"+i)
            }
            if(mode=="rapid") j.check("click_"+count)
            j.request("matrix_done");waitHost("done")
            Log.i(TAG,"PREPARED_RG_PASS mode="+mode+" intendedEnqueues="+count+" expectedEffects="+count)
        } catch(t:Throwable) { failure=t;Log.e(TAG,"PRIMARY_FAILURE",t);throw t }
        finally {
            val errors=listOf<()->Unit>(
                { gate?.close() },{ j.main { j.probe?.close() } },{ scenario.close() },
                { ack.delete();assertFalse(ack.exists()) },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if(failure!=null) errors.forEach { failure!!.addSuppressed(it) } else if(errors.isNotEmpty()) throw errors.first()
        }
    }
    companion object { private const val TAG="EyeBrowseI8" }
}
