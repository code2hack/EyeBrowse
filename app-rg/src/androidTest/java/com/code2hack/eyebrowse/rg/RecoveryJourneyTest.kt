package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import android.util.Log
import android.widget.Button
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

@RunWith(AndroidJUnit4::class)
class RecoveryJourneyTest {
    @Test fun rgObservesScreenOffEffectsLiveStopAndExplicitRestart() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val mission=UUID.fromString(InstrumentationRegistry.getArguments().getString("missionId")).toString()
        val phone=File(app.cacheDir,"i12-screen-$mission.phone");val rg=File(app.cacheDir,"i12-screen-$mission.rg");val screen=File(app.cacheDir,"i12-screen-$mission.screen")
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        lateinit var peer:RgPresentationController
        lateinit var activity:MainActivity
        scenario.onActivity { activity=it;peer=it.presentation }
        fun await(label:String,bound:Long=10_000,condition:()->Boolean) {
            val end=SystemClock.elapsedRealtime()+bound
            while(SystemClock.elapsedRealtime()<end) {
                check(!screen.exists() || screen.readText().trim()!="abort") { "screen runner aborted" }
                var ok=false;scenario.onActivity { ok=condition() };if(ok)return;SystemClock.sleep(20)
            };fail(label)
        }
        fun waitPhone(value:String)=await("Phone $value",15_000) { phone.exists() && phone.readText().trim()==value }
        fun publish(value:String) { rg.writeText(value);Log.i("EyeBrowseA06","RG_PHASE mission=$mission phase=$value") }
        fun native(id:Int) = scenario.onActivity { it.findViewById<Button>(id).performClick() }
        fun fresh(label:String) {
            await(label,2_000) { peer.canAct() }
            val state=checkNotNull(peer.browserState());val frame=checkNotNull(peer.lastFrameHeader);val profile=checkNotNull(peer.profile())
            assertFalse(state.stale);assertEquals(ControlOwner.RG,state.owner);assertEquals(state.context,frame.context)
            assertEquals(profile,state.profile);assertEquals(profile.width,frame.width);assertEquals(profile.height,frame.height)
        }
        fun action(send:()->String?) {
            var id:String?=null;scenario.onActivity { id=send() };assertNotNull(id)
            await("correlated action",2_000) { peer.lastActionResult?.commandId==id }
            assertTrue(checkNotNull(peer.lastActionResult).accepted)
        }
        fun geometry()=org.json.JSONObject(checkNotNull(peer.browserState()?.title).substringAfter("|G="))
        val client=RgPresentationController::class.java.getDeclaredField("client").apply { isAccessible=true }.get(peer) as com.code2hack.eyebrowse.rg.link.RgLinkClient
        val field=client.javaClass.getDeclaredField("listener").apply { isAccessible=true }
        val original=field.get(client) as com.code2hack.eyebrowse.rg.link.RgLinkClient.Listener
        data class Seen(val header:com.code2hack.eyebrowse.core.link.framing.PresentationFrameHeader,val hash:String)
        val samples=java.util.concurrent.ConcurrentHashMap<Long,Seen>()
        val recording=java.util.concurrent.atomic.AtomicReference<com.code2hack.eyebrowse.core.link.control.ControlContext?>()
        val overflow=java.util.concurrent.atomic.AtomicBoolean()
        val receivedDuringOff=java.util.concurrent.atomic.AtomicInteger()
        val contextMismatches=java.util.concurrent.atomic.AtomicInteger()
        val mismatchHeaders=java.util.concurrent.ConcurrentLinkedQueue<String>()
        field.set(client,object:com.code2hack.eyebrowse.rg.link.RgLinkClient.Listener by original {
            override fun onPresentation(frame:com.code2hack.eyebrowse.core.link.framing.PresentationFrame) {
                val expected=recording.get()
                if(expected!=null) {
                    receivedDuringOff.incrementAndGet()
                    if(expected!=frame.header.context) {
                        contextMismatches.incrementAndGet()
                        if(mismatchHeaders.size<4)mismatchHeaders.add(frame.header.toString())
                    }
                }
                if(recording.get()==frame.header.context) {
                    if(samples.size<128) samples[frame.header.frameSeq]=Seen(frame.header,java.security.MessageDigest.getInstance("SHA-256").digest(frame.pixels()).joinToString("") { "%02x".format(it) })
                    else overflow.set(true)
                }
                original.onPresentation(frame)
            }
        })
        var failure:Throwable?=null
        try {
            waitPhone("ready");native(R.id.rg_retry)
            await("Phone consent") { peer.canHandoff() && peer.browserState()?.owner==ControlOwner.PHONE }
            native(R.id.rg_handoff);fresh("initial frame");publish("owned")
            waitPhone("background")
            var navSamples=0;var navLastSample=0L
            fun navReceipt(stage:String,force:Boolean=false) {
                val now=SystemClock.elapsedRealtime()
                if(navSamples>=24 || (!force && now-navLastSample<250))return
                navSamples++;navLastSample=now
                runCatching {
                    fun operand(name:String)=peer.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(peer)
                    val state=peer.browserState();val frame=peer.lastFrameHeader;val profile=peer.profile()
                    val result=peer.lastActionResult
                    Log.i("EyeBrowseA06","NAV_DIAG mission=$mission stage=$stage elapsedMs=$now clock=RG_elapsedRealtime titleB=${state?.title?.startsWith("T03 B")==true} canAct=${peer.canAct()} baseEligible=${peer.baseActionEligible()} owner=${state?.owner} stale=${state?.stale} loading=${state?.loading} context=${state?.context} frame=$frame profile=$profile profileMatches=${state?.profile==profile} compatible=${operand("compatible")} closed=${operand("closed")} pending=${operand("pendingCommand")} viewportPending=${operand("viewportChange")!=null} layoutExpected=${operand("layoutExpected")} editorClose=${operand("editorClose")!=null} resultId=${result?.commandId} accepted=${result?.accepted} effect=${result?.effectSucceeded} input=${peer.inputSnapshot()}")
                }.onFailure { Log.i("EyeBrowseA06","NAV_DIAG mission=$mission stage=$stage elapsedMs=$now unavailable=${it.javaClass.simpleName}") }
            }
            val g=geometry()
            scenario.onActivity { navReceipt("before-dispatch",true);Log.i("EyeBrowseA06","NAV_POINT mission=$mission elapsedMs=${SystemClock.elapsedRealtime()} nx=${g.getDouble("nx")} ny=${g.getDouble("ny")}") }
            action { peer.activateAt(g.getDouble("nx").toFloat(),g.getDouble("ny").toFloat()) }
            scenario.onActivity { navReceipt("accepted",true) }
            try {
                await("background navigation B",5_000) { navReceipt("predicate");peer.browserState()?.title?.startsWith("T03 B")==true && peer.canAct() }
            } finally { scenario.onActivity { navReceipt("predicate-exit",true) } }
            val previous=peer.lastActionResult;native(R.id.rg_back)
            await("background native Back",5_000) { peer.lastActionResult!==previous && peer.browserState()?.title?.startsWith("T03 A")==true && peer.canAct() }
            fresh("background returned frame");publish("background-done")
            waitPhone("sleep");await("physical OFF confirmed",10_000) { screen.exists() && screen.readText().trim()=="off" }
            fresh("OFF initial frame")
            val context=checkNotNull(peer.browserState()).context;recording.set(context)
            var sampledColor=0
            fun proof(expected:IntArray,afterSequence:Long):Boolean {
                val state=peer.browserState() ?: return false
                val h=peer.lastFrameHeader ?: return false
                val profile=peer.profile() ?: return false
                if(!peer.canAct() || state.stale || state.context!=context || h.context!=context ||
                    state.profile!=profile || h.width!=profile.width || h.height!=profile.height || h.frameSeq<=afterSequence)return false
                val bitmap=(activity.findViewById<android.widget.ImageView>(R.id.rg_page).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return false
                if(bitmap.width!=h.width || bitmap.height!=h.height)return false
                val g=geometry();if(!g.has("proofX") || !g.has("proofY"))return false
                val x=g.getDouble("proofX").toInt();val y=g.getDouble("proofY").toInt()
                if(x !in 0 until bitmap.width || y !in 0 until bitmap.height)return false
                sampledColor=bitmap.getPixel(x,y)
                return android.graphics.Color.alpha(sampledColor)>=240 &&
                    kotlin.math.abs(android.graphics.Color.red(sampledColor)-expected[0])<=20 &&
                    kotlin.math.abs(android.graphics.Color.green(sampledColor)-expected[1])<=20 &&
                    kotlin.math.abs(android.graphics.Color.blue(sampledColor)-expected[2])<=20
            }
            await("seeded proof patch",2_000) { proof(intArrayOf(220,30,30),-1) }
            val observed=mutableSetOf<Long>();val displayed=linkedSetOf<Long>();val started=SystemClock.elapsedRealtime()
            fun change(slot:Long,label:String,beforeColor:IntArray,expected:IntArray,send:()->String?) {
                while(SystemClock.elapsedRealtime()-started<slot) {
                    check(screen.readText().trim()!="abort") { "screen runner aborted" }
                    scenario.onActivity { assertTrue(peer.canAct()) };SystemClock.sleep(20)
                }
                assertTrue("change dispatched inside OFF interval",SystemClock.elapsedRealtime()-started<10_000)
                var sequence=0L;var dispatch=0L;var id:String?=null
                scenario.onActivity { assertTrue("$label baseline pixels",proof(beforeColor,-1));sequence=checkNotNull(peer.lastFrameHeader).frameSeq;dispatch=SystemClock.elapsedRealtime();id=send() }
                assertNotNull(id);val deadline=dispatch+2_000
                var matched=false
                try {
                    await("$label action result",(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0)) { peer.lastActionResult?.commandId==id }
                    assertTrue(checkNotNull(peer.lastActionResult).accepted)
                    await("$label resulting displayed pixels",(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0)) { proof(expected,sequence) }
                    assertTrue("original per-change observation endpoint",SystemClock.elapsedRealtime()<=deadline)
                    matched=true
                    observed.add(checkNotNull(peer.lastFrameHeader).frameSeq)
                } finally {
                    Log.i("EyeBrowseA06","CHANGE_PROPAGATION mission=$mission phase=$label command=$id dispatchMs=$dispatch observedMs=${SystemClock.elapsedRealtime()} slotMs=${dispatch-started} beforeSeq=$sequence afterSeq=${peer.lastFrameHeader?.frameSeq} matched=$matched argb=$sampledColor tolerance=20 alphaMin=240 clock=RG_elapsedRealtime")
                }
                publish(label);waitPhone("$label-verified")
            }
            val point=geometry()
            change(0,"off-click",intArrayOf(220,30,30),intArrayOf(30,70,220)) { peer.activateAt(point.getDouble("x").toFloat(),point.getDouble("y").toFloat()) }
            change(3_000,"off-scroll",intArrayOf(30,70,220),intArrayOf(30,180,70)) { peer.scrollBy(0f,160f) }
            change(6_000,"off-reverse",intArrayOf(30,180,70),intArrayOf(220,30,30)) { peer.scrollBy(0f,-160f) }
            assertTrue("changes settle inside original OFF observation window",SystemClock.elapsedRealtime()-started<=10_000)
            while(SystemClock.elapsedRealtime()-started<10_000) {
                check(screen.readText().trim()!="abort") { "screen runner aborted" }
                scenario.onActivity {
                    assertTrue("live OFF input remains eligible",peer.canAct())
                    val h=checkNotNull(peer.lastFrameHeader);assertEquals(context,h.context)
                    if(displayed.size<128)displayed.add(h.frameSeq)
                    if(samples.containsKey(h.frameSeq))observed.add(h.frameSeq)
                }
                SystemClock.sleep(20)
            }
            recording.set(null)
            val snapshot=samples.values.sortedBy { it.header.frameSeq }
            Log.i("EyeBrowseA06","OFF_FRAME_DIAG mission=$mission intervalMs=${SystemClock.elapsedRealtime()-started} rawReceived=${receivedDuringOff.get()} matched=${snapshot.size} mismatches=${contextMismatches.get()} overflow=${overflow.get()} receivedSeq=${snapshot.take(32).map { it.header.frameSeq }} observedCount=${observed.size} observedSeq=${observed.take(32)} displayedCount=${displayed.size} displayedSeq=${displayed.take(32)} distinctReceived=${snapshot.map { it.hash }.distinct().size} distinctObserved=${observed.mapNotNull { samples[it]?.hash }.distinct().size} firstCapture=${snapshot.firstOrNull()?.header?.captureTsMs} lastCapture=${snapshot.lastOrNull()?.header?.captureTsMs} expectedContext=$context matchedHeaders=${(snapshot.take(2)+snapshot.takeLast(2)).distinct().map { it.header }} mismatchHeaders=$mismatchHeaders")
            assertFalse(overflow.get())
            val ordered=samples.values.sortedBy { it.header.frameSeq }
            ordered.zipWithNext().forEach { (a,b) -> assertTrue("Phone producer cadence <=5fps",b.header.captureTsMs-a.header.captureTsMs>=200) }
            Log.i("EyeBrowseA06","OFF_FRAMES mission=$mission intervalMs=${SystemClock.elapsedRealtime()-started} received=${ordered.size} observed=${observed.size} distinct=${observed.mapNotNull { samples[it]?.hash }.distinct().size} captureClock=Phone_elapsedRealtime firstCapture=${ordered.firstOrNull()?.header?.captureTsMs} lastCapture=${ordered.lastOrNull()?.header?.captureTsMs} cadence200ms=true noMinimumFps=true")
            publish("off-done");waitPhone("stop");fresh("live frame before Stop");publish("stop-ready")
            await("live Stop disables input",5_000) { !peer.canAct() };assertNull(peer.reload());waitPhone("stopped")
            native(R.id.rg_retry)
            await("Retry cannot start Phone host",10_000) {
                val text=activity.findViewById<TextView>(R.id.rg_status).text.toString()
                text.contains("NetworkUnreachable") && !peer.canAct()
            }
            publish("retry-refused");waitPhone("restarted")
            native(R.id.rg_retry);await("restart requires explicit consent") { peer.canHandoff() && peer.browserState()?.owner==ControlOwner.PHONE }
            native(R.id.rg_handoff);fresh("fresh explicit restart frame")
            assertNotEquals(context,peer.browserState()!!.context)
            var restartSamples=0;var restartSampleAt=0L
            fun restartReceipt(stage:String,force:Boolean=false) {
                val now=SystemClock.elapsedRealtime()
                if(restartSamples>=16 || (!force && now-restartSampleAt<250))return
                restartSamples++;restartSampleAt=now
                runCatching {
                    val state=peer.browserState();val point=geometry();val result=peer.lastActionResult
                    Log.i("EyeBrowseA06","RESTART_GEOMETRY mission=$mission stage=$stage elapsedMs=$now clock=RG_elapsedRealtime cssY=${point.optDouble("cssY")} x=${point.optDouble("x")} y=${point.optDouble("y")} canAct=${peer.canAct()} stale=${state?.stale} loading=${state?.loading} context=${state?.context} profile=${peer.profile()} frame=${peer.lastFrameHeader} resultId=${result?.commandId} accepted=${result?.accepted}")
                }.onFailure { Log.i("EyeBrowseA06","RESTART_GEOMETRY mission=$mission stage=$stage unavailable=${it.javaClass.simpleName}") }
            }
            scenario.onActivity { restartReceipt("before-scroll",true) }
            action { peer.scrollBy(0f,-160f) }
            scenario.onActivity { restartReceipt("scroll-accepted",true) }
            try { await("restart scroll top",2_000) { restartReceipt("scroll-predicate");geometry().optDouble("cssY")==0.0 && peer.canAct() } }
            finally { scenario.onActivity { restartReceipt("scroll-predicate-exit",true) } }
            val again=geometry();action { peer.activateAt(again.getDouble("x").toFloat(),again.getDouble("y").toFloat()) }
            await("new explicit effect",2_000) { peer.browserState()?.title?.startsWith("T03 A click 2")==true && peer.canAct() }
            scenario.onActivity { restartReceipt("click-effect",true) }
            publish("restart-effect");waitPhone("complete");publish("done");waitPhone("release")
        } catch(t:Throwable) { failure=t;throw t }
        finally {
            recording.set(null)
            val errors=listOf<()->Unit>({ scenario.close() },{ field.set(client,original) },{ listOf(phone,rg,screen).forEach { it.delete();assertFalse(it.exists()) } }).mapNotNull { runCatching(it).exceptionOrNull() }
            if(failure!=null)errors.forEach { failure.addSuppressed(it) } else org.junit.runners.model.MultipleFailureException.assertEmpty(errors)
        }
    }

    @Test fun reconcilesLiveHostWithoutReplayingInputOrStealingPhoneControl() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val mission = UUID.fromString(checkNotNull(InstrumentationRegistry.getArguments().getString("missionId"))).toString()
        val ack = File(app.cacheDir, "i11-$mission.ack").apply { writeText("") }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var peer: RgPresentationController
        fun await(label: String, bound: Long = 10_000, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + bound
            while (SystemClock.elapsedRealtime() < deadline) {
                var passed = false
                scenario.onActivity { passed = condition() }
                if (passed) return
                SystemClock.sleep(20)
            }
            fail(label)
        }
        fun waitHost(phase: String) = await("Phone receipt $phase", 15_000) { ack.readText().trim() == phase }
        fun phase(name: String) = peer.browserState()?.title?.substringBefore("|G=") == "I11-$mission-$name"
        fun retry() = scenario.onActivity { it.findViewById<Button>(R.id.rg_retry).performClick() }
        fun consent() = scenario.onActivity { it.findViewById<Button>(R.id.rg_handoff).performClick() }
        fun disconnected() {
            scenario.onActivity { peer.pause() }
            assertFalse(peer.canAct())
            assertNull("disconnected click not queued", peer.activateAt(10f, 10f))
            assertNull("disconnected navigation not queued", peer.reload())
            scenario.onActivity {
                val text = it.findViewById<TextView>(R.id.rg_status).text.toString().lowercase()
                assertTrue("visible stale/disconnected feedback: $text", text.contains("stale") || text.contains("disconnected") || text.contains("paused"))
            }
        }
        var failure: Throwable? = null
        try {
            scenario.onActivity { peer = it.presentation }
            retry()
            await("run-specific Phone state") { phase("ready") && peer.canHandoff() && peer.profile() != null }
            consent()
            await("first current frame", 2_000) { peer.canAct() }
            var context = peer.browserState()!!.context
            await("Phone requests link interruption") { phase("drop") }
            disconnected()
            waitHost("reconnect")
            val reconnectAt = SystemClock.elapsedRealtime()
            retry()
            await("fresh reconnect presentation") { peer.canAct() }
            var recovered = peer.browserState()!!.context
            assertEquals(context.lifetimeId, recovered.lifetimeId)
            assertEquals(context.controlEpoch, recovered.controlEpoch)
            assertTrue(recovered.viewportEpoch > context.viewportEpoch)
            assertEquals(recovered, peer.lastFrameHeader!!.context)
            Log.i("EyeBrowseRecovery", "RG_RECONNECT mission=$mission readyMs=${SystemClock.elapsedRealtime() - reconnectAt}")
            context = recovered
            await("Phone recreation verified") { phase("recreate-rg") }
            scenario.recreate()
            scenario.onActivity { peer = it.presentation }
            assertFalse(peer.canAct())
            waitHost("recreate")
            retry()
            await("new RG interface reconciled") { peer.canAct() }
            recovered = peer.browserState()!!.context
            assertEquals(context.lifetimeId, recovered.lifetimeId)
            assertEquals(context.documentId, recovered.documentId)
            assertEquals(context.controlEpoch, recovered.controlEpoch)
            assertTrue(recovered.viewportEpoch > context.viewportEpoch)
            await("Phone requests disconnected takeover") { phase("takeover") }
            disconnected()
            waitHost("takeover")
            retry()
            await("Phone owner reconciled") { peer.browserState()?.owner == ControlOwner.PHONE && peer.canHandoff() }
            assertFalse("reconnect cannot steal control", peer.canAct())
            assertNull(peer.activateAt(10f, 10f))
            consent()
            await("new explicit RG consent", 2_000) { peer.canAct() }
            await("Phone ready for Stop") { phase("stop") }
            Log.i("EyeBrowseRecovery", "RG_RELEASE mission=$mission phase=stop")
            await("Stop disables remote page input", 5_000) { !peer.canAct() }
            assertNull(peer.reload())
            Log.i("EyeBrowseRecovery", "RG_PASS mission=$mission noImplicitTakeover=true staleInputRejected=true")
        } catch (t: Throwable) { failure = t; throw t }
        finally {
            val cleanup = listOf<() -> Unit>({ scenario.close() }, { ack.delete(); assertFalse(ack.exists()) })
                .mapNotNull { runCatching(it).exceptionOrNull() }
            if (failure != null) cleanup.forEach { failure.addSuppressed(it) }
            else if (cleanup.isNotEmpty()) { cleanup.drop(1).forEach { cleanup.first().addSuppressed(it) }; throw cleanup.first() }
        }
    }
}
