package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.transport.LinkClientEngine
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Review correction: real key/effect paths; only decoded results and TCP scheduling are faulted. */
@RunWith(AndroidJUnit4::class)
class ReviewRecoveryJourneyTest {
    @Test fun editorResultsLossRecoveryAndNavigation() = run(false)
    @Test fun phoneStopRetiresAnInFlightRememberedReconnect() = run(true)

    private fun run(stopReconnect:Boolean) {
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val j=PointerBrowserJourneyTest.Journey(scenario)
        val phase=File(j.app.cacheDir,"i11-review-${j.mission}.phase")
        val ack=File(j.app.cacheDir,"i11-review-${j.mission}.ack")
        var faults:AckLossJourneyTest.DeliveryFaults?=null
        var engine:LinkClientEngine?=null
        val release=CountDownLatch(1)
        var completed=false
        fun checkPhone(name:String) {
            phase.writeText(name)
            j.await("independent Phone receipt $name",5_000) { ack.exists() && ack.readText().trim()==name }
        }
        fun screenshot(name:String) {
            val bitmap=checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            File(j.app.cacheDir,"i11-review-${j.mission}-$name.png").outputStream().use {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
            bitmap.recycle()
        }
        fun pending():String? = synchronized(field(j.peer,"lock")) { nullableField(j.peer,"pendingCommand") as String? }
        fun fresh() {
            j.await("fresh matching authority/frame",2_000) { j.peer.canAct() }
            val state=checkNotNull(j.peer.browserState());val frame=checkNotNull(j.peer.lastFrameHeader)
            assertEquals(state.context,frame.context);assertEquals(state.profile,j.peer.profile())
            Log.i("EyeBrowseReviewRecovery","FRAME mission=${j.mission} context=${state.context} seq=${frame.frameSeq} capture=${frame.captureTsMs}")
        }
        fun capture(id:String):BrowserActionResultMessage {
            j.await("captured real result",2_000) { faults!!.result(id)!=null }
            return checkNotNull(faults!!.result(id))
        }
        fun replay(request:BrowserActionMessage,reason:String) {
            faults!!.holdActions=false
            val before=j.peer.lastActionResult
            assertTrue(j.client().sendControl(request))
            j.await("duplicate request rejected",2_000) { j.peer.lastActionResult!==before && j.peer.lastActionResult?.commandId==request.commandId }
            assertEquals(reason,j.peer.lastActionResult!!.reason)
            faults!!.holdActions=true
        }
        fun fieldKeyboard() {
            fresh()
            val geometry=JSONObject(checkNotNull(j.peer.browserState()?.title).substringAfter("KBD|")).getJSONArray("text")
            val profile=checkNotNull(j.peer.profile())
            val x=(geometry.getDouble(0)*profile.width).toFloat();val y=(geometry.getDouble(1)*profile.height).toFloat()
            j.aim(j.pageRoot(x,y));j.dispatched(j.pad(),"activate real field")
            j.await("real field keyboard ready",2_000) { j.peer.keyboard.visible && j.peer.canKey(RgKeyboard.Key.Character("a")) }
            fresh()
        }
        fun buttons(view:View):List<Button> = when(view) {
            is Button -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
            else -> emptyList()
        }
        fun key(key:RgKeyboard.Key,operation:EditorOperation):Pair<BrowserActionMessage,Long> {
            j.await("native key eligible",2_000) { j.peer.canKey(key) }
            val context=checkNotNull(j.peer.browserState()).context
            val target=checkNotNull(j.peer.keyboard.target)
            var id:String?=null;var at=0L
            j.main {
                val view=buttons(it.findViewById(R.id.rg_keyboard_container)).first { b -> (b.tag as? RgKeyboard.Intent)?.key==key }
                assertTrue(view.isEnabled);at=SystemClock.uptimeMillis();assertTrue(view.performClick());id=pending()
            }
            assertNotNull("real key constructed an outstanding command",id)
            val request=BrowserActionMessage(id!!,context,BrowserAction.Edit(target,operation),id!!.split(':')[2].toLong())
            val result=capture(id!!);assertTrue(result.accepted)
            if(operation is EditorOperation.Insert) {
                assertEquals("EDITOR_APPLIED",result.reason);assertEquals(true,result.effectSucceeded)
            } else {
                assertEquals(EditorOperation.Enter,operation)
                assertEquals("SUBMISSION_REQUESTED",result.reason);assertNull(result.effectSucceeded)
            }
            Log.i("EyeBrowseReviewRecovery","KEY mission=${j.mission} id=$id context=$context target=$target accepted=true")
            return request to at
        }
        fun expired(at:Long) {
            val end=at+5_500
            while(SystemClock.uptimeMillis()<end && pending()!=null) SystemClock.sleep(10)
            assertNull("uncertainty timer retires pending within5000+500ms",pending())
            val elapsed=SystemClock.uptimeMillis()-at
            assertTrue("timeout not early or beyond declared allowance",elapsed in 5_000..5_500)
            assertFalse(j.peer.keyboard.visible)
            Log.i("EyeBrowseReviewRecovery","TIMEOUT mission=${j.mission} elapsedMs=$elapsed keyboardDismissed=true")
        }
        try {
            j.setup()
            val client=j.client()
            val original=field(client,"listener") as com.code2hack.eyebrowse.rg.link.RgLinkClient.Listener
            faults=AckLossJourneyTest.DeliveryFaults(original)
            setField(client,"listener",faults!!)
            j.native(R.id.rg_retry,"Retry")
            j.await("authenticated Phone state",10_000) { j.peer.browserState()?.owner==ControlOwner.PHONE && j.peer.canHandoff() }
            j.handoff(ControlOwner.RG,"A");fresh()
            val baseline=j.actions
            if(stopReconnect) {
                faults!!.holdActions=true
                val state=checkNotNull(j.peer.browserState())
                val g=JSONObject(checkNotNull(state.title).substringAfter("|G="))
                var id:String?=null
                j.main { id=j.peer.activateAt(g.getDouble("x").toFloat(),g.getDouble("y").toFloat()) }
                assertNotNull(id);assertTrue(capture(id!!).accepted)
                checkPhone("effect_before_reconnect")
                j.main { client.disconnect() };j.quiescent();faults!!.discardActions()
                checkPhone("link_down")
                engine=field(client,"engine") as LinkClientEngine
                val entered=CountDownLatch(1)
                val hook:(Socket,InetSocketAddress,Int)->Unit={ socket,address,timeout ->
                    Log.i("EyeBrowseReviewRecovery","RECONNECT_TCP_ENTER mission=${j.mission} uptimeMs=${SystemClock.uptimeMillis()}")
                    entered.countDown()
                    check(release.await(5,TimeUnit.SECONDS)) { "Stop barrier exceeded original reconnect window" }
                    socket.connect(address,timeout)
                }
                setField(engine!!,"tcpConnectForTest",hook)
                j.native(R.id.rg_retry,"remembered Retry")
                assertTrue(entered.await(2,TimeUnit.SECONDS));assertTrue(engine!!.isBusy)
                assertFalse(j.peer.canAct());assertNull(pending())
                checkPhone("reconnect_inflight") // Phone presses Stop while the owned reconnect socket is paused.
                release.countDown()
                j.await("reconnect settles after Stop",5_000) { !engine!!.isBusy }
                assertFalse(j.peer.canAct());assertNull(j.peer.reload())
                screenshot("stopped");checkPhone("settled_after_stop")
            } else {
                fieldKeyboard();faults!!.holdActions=true
                val (a,at)=key(RgKeyboard.Key.Character("a"),EditorOperation.Insert("a"))
                val originalTarget=j.peer.keyboard.target
                expired(at);checkPhone("lost_edit")
                faults!!.holdActions=false;fieldKeyboard();faults!!.holdActions=true
                assertNotEquals(originalTarget,j.peer.keyboard.target)
                val (b,_)=key(RgKeyboard.Key.Character("b"),EditorOperation.Insert("b"))
                val successor=j.peer.keyboard.target
                j.main { faults!!.releaseAction(a.commandId) }
                assertEquals(b.commandId,pending());assertEquals(successor,j.peer.keyboard.target)
                j.main { faults!!.releaseAction(b.commandId) }
                j.await("B matching result retires",2_000) { pending()==null }
                replay(b,"STALE_COMMAND_SEQUENCE");checkPhone("duplicate_edit")
                val (enter,enterAt)=key(RgKeyboard.Key.Command.ENTER,EditorOperation.Enter)
                assertEquals("SUBMISSION_REQUESTED",capture(enter.commandId).reason)
                faults!!.discardActions();expired(enterAt);fresh()
                replay(enter,"STALE_CONTEXT");checkPhone("lost_enter")
                faults!!.holdActions=false;fieldKeyboard();faults!!.holdActions=true
                val intent=j.peer.keyboard.capture(RgKeyboard.Key.Character("x"))
                val (c,_)=key(RgKeyboard.Key.Character("c"),EditorOperation.Insert("c"))
                val before=checkNotNull(j.peer.browserState()).context
                assertTrue(j.peer.keyboard.visible);assertEquals(c.commandId,pending())
                screenshot("pending");checkPhone("pending_edit")
                j.main { client.disconnect() };j.quiescent();faults!!.discardActions()
                assertFalse(j.peer.keyboard.visible);assertNull(pending());assertNull(j.peer.keyboard.target)
                j.main { assertFalse(j.peer.key(intent)) }
                checkPhone("editor_link_down")
                faults!!.holdActions=false
                j.native(R.id.rg_retry,"recover editor")
                j.await("authenticated recovered state",10_000) { j.peer.browserState()?.stale==false && j.peer.browserState()?.context?.viewportEpoch!=before.viewportEpoch }
                fresh()
                val recovered=checkNotNull(j.peer.browserState()).context
                assertEquals(before.lifetimeId,recovered.lifetimeId);assertEquals(before.documentId,recovered.documentId)
                assertEquals(before.controlEpoch,recovered.controlEpoch);assertTrue(recovered.viewportEpoch>before.viewportEpoch)
                assertFalse(j.peer.keyboard.visible);j.main { assertFalse(j.peer.key(intent)) }
                replay(c,"STALE_CONTEXT");screenshot("recovered");checkPhone("editor_recovered")
                faults!!.holdActions=false;fieldKeyboard();faults!!.holdActions=true
                assertNotEquals((c.action as BrowserAction.Edit).target,j.peer.keyboard.target)
                val (d,_)=key(RgKeyboard.Key.Character("d"),EditorOperation.Insert("d"))
                j.main { faults!!.releaseAction(d.commandId) };checkPhone("explicit_fresh_edit")
                j.main { j.peer.dismissKeyboard() };fresh()
                faults!!.holdActions=true
                val navigationContext=checkNotNull(j.peer.browserState()).context
                var nav:String?=null
                j.main { nav=j.peer.reload() };assertNotNull(nav);assertTrue(capture(nav!!).accepted)
                j.await("new document from one real Reload",5_000) { j.peer.browserState()?.context?.documentId!=navigationContext.documentId }
                fresh();assertFalse(j.peer.keyboard.visible)
                checkPhone("navigation_effect")
                val reload=BrowserActionMessage(nav!!,navigationContext,BrowserAction.Reload,nav!!.split(':')[2].toLong())
                j.main { faults!!.releaseAction(nav!!) } // Late result twice, after the document changed.
                replay(reload,"STALE_CONTEXT")
                assertFalse(j.peer.keyboard.visible);checkPhone("navigation_duplicate")
            }
            val expected=if(stopReconnect) 1 else 10
            assertEquals(baseline.consumed+expected,j.actions.consumed)
            assertEquals(baseline.constructed+expected,j.actions.constructed)
            assertEquals(baseline.queued+expected,j.actions.queued)
            phase.writeText("complete");completed=true
            Log.i("EyeBrowseReviewRecovery","RG_PASS mission=${j.mission} stopReconnect=$stopReconnect accounting=${j.actions}")
        } finally {
            release.countDown();engine?.let { setField(it,"tcpConnectForTest",null) }
            if(!completed)phase.writeText("abort")
            try { j.main { faults?.let { f -> setField(j.client(),"listener",f.original) };j.probe?.close() } }
            finally { scenario.close() }
        }
    }
    private fun nullableField(target:Any,name:String):Any?=target.javaClass.getDeclaredField(name).let { it.isAccessible=true;it.get(target) }
    private fun field(target:Any,name:String):Any=checkNotNull(nullableField(target,name))
    private fun setField(target:Any,name:String,value:Any?)=target.javaClass.getDeclaredField(name).let { it.isAccessible=true;it.set(target,value) }
}
