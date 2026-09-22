package com.code2hack.eyebrowse.rg

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.rg.link.RgLinkClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControlJourneyTest {
    @Test fun rgDrivesHandoffAndEveryTypedActionThroughAuthenticatedLink() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val scenario=ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java))
        lateinit var peer:RgPresentationController
        try {
            scenario.onActivity { peer=it.presentation;peer.reconnect() }
            await("Phone state",10_000) { peer.browserState()?.owner==ControlOwner.PHONE && peer.profile()!=null }
            val firstStart=SystemClock.elapsedRealtime()
            scenario.onActivity { it.findViewById<Button>(R.id.rg_handoff).performClick() }
            await("ownership ack",1_000) { peer.lastHandoffResult?.let { it.accepted && it.owner==ControlOwner.RG }==true }
            Log.i("EyeBrowseT03","PHONE_TO_RG_ACK_MS=${SystemClock.elapsedRealtime()-firstStart}")
            await("RG presentation",2_000) { peer.canAct() }
            assertTrue(SystemClock.elapsedRealtime()-firstStart <= 2_000)
            Log.i("EyeBrowseT03","PHONE_TO_RG_READY_MS=${SystemClock.elapsedRealtime()-firstStart}")
            val oldContext=peer.browserState()!!.context
            await("Phone foreground fence verified",5_000) { peer.browserState()?.title=="RG ownership verified" }
            val client=RgPresentationController::class.java.getDeclaredField("client").let { it.isAccessible=true;it.get(peer) as RgLinkClient }
            scenario.onActivity { peer.pause() }
            val engine=RgLinkClient::class.java.getDeclaredField("engine").let { it.isAccessible=true;it.get(client) as com.code2hack.eyebrowse.core.link.transport.LinkClientEngine }
            await("cancel quiescence",2_000) { !engine.isBusy }
            scenario.onActivity { it.findViewById<Button>(R.id.rg_retry).performClick() }
            await("authenticated reconnect without takeover",10_000) { peer.canAct() }
            assertEquals(oldContext.controlEpoch,peer.browserState()!!.context.controlEpoch)
            assertEquals(ControlOwner.RG,peer.browserState()!!.owner)
            Log.i("EyeBrowseT03","RECONNECT_PRESERVES_OWNER epoch=${oldContext.controlEpoch}")
            val returnStart=SystemClock.elapsedRealtime()
            scenario.onActivity { it.findViewById<Button>(R.id.rg_handoff).performClick() }
            await("Phone owner ack",1_000) { peer.browserState()?.owner==ControlOwner.PHONE }
            Log.i("EyeBrowseT03","RG_TO_PHONE_MS=${SystemClock.elapsedRealtime()-returnStart}")
            await("Phone continuity verified",10_000) { peer.browserState()?.title=="Phone verified" }
            // Inject only through the existing authenticated production client, never a test receiver.
            fun raw(request:BrowserActionMessage):BrowserActionResultMessage {
                val previous=peer.lastActionResult
                assertTrue(client.sendControl(request))
                await("correlated rejection",2_000) { peer.lastActionResult !== previous && peer.lastActionResult?.commandId==request.commandId }
                return peer.lastActionResult!!
            }
            val stale=BrowserActionMessage(BrowserCommandId.create(oldContext,Long.MAX_VALUE),oldContext,BrowserAction.Reload,Long.MAX_VALUE)
            assertEquals("STALE_CONTEXT",raw(stale).reason)
            scenario.onActivity { it.findViewById<Button>(R.id.rg_handoff).performClick() }
            await("RG reacquired",2_000) { peer.canAct() }
            val active=peer.browserState()!!.context
            assertTrue(active.controlEpoch>oldContext.controlEpoch)
            val profile=peer.profile()!!;val scale=profile.densityDpi/160f
            val x=86f*scale;val y=36f*scale
            fun accepted(send:()->String?):String {
                var id:String?=null
                scenario.onActivity { id=send() }
                assertNotNull("action submitted",id)
                await("action admitted",2_000) { peer.lastActionResult?.commandId==id }
                assertTrue(peer.lastActionResult!!.accepted)
                assertNull(peer.lastActionResult!!.effectSucceeded)
                Log.i("EyeBrowseT03","ACTION_ADMITTED sequence=${id!!.split(':')[2]}")
                return id!!
            }
            fun navigationButton(id:Int) {
                val previous=peer.lastActionResult
                scenario.onActivity { activity ->
                    val button=activity.findViewById<Button>(id)
                    assertTrue("navigation UI enabled",button.isEnabled)
                    button.performClick()
                }
                await("navigation acknowledged",2_000) { peer.lastActionResult !== previous }
                assertTrue(peer.lastActionResult!!.accepted)
                assertNull(peer.lastActionResult!!.effectSucceeded)
            }
            val click=accepted { peer.activateAt(x,y) }
            await("ActivateAt effect",1_000) { peer.browserState()?.title=="T03 A click 1" && peer.canAct() }
            assertEquals("STALE_COMMAND_SEQUENCE",raw(BrowserActionMessage(click,active,BrowserAction.ActivateAt(x,y),click.split(':')[2].toLong())).reason)
            var rejected:String?=null
            scenario.onActivity { rejected=peer.activateAt(profile.width+1f,y) }
            assertNotNull(rejected)
            await("outside viewport rejection",2_000) { peer.lastActionResult?.commandId==rejected }
            assertEquals("OUTSIDE_VIEWPORT",peer.lastActionResult!!.reason)
            assertEquals("STALE_COMMAND_SEQUENCE",raw(BrowserActionMessage(rejected!!,active,BrowserAction.ActivateAt(x,y),rejected!!.split(':')[2].toLong())).reason)
            Log.i("EyeBrowseT03","NEGATIVE_ACTIONS stale=true replay=true rejectedOrdinalConsumed=true")
            accepted { peer.scrollBy(0f,160f) }
            await("positive scroll",1_000) { peer.browserState()?.title?.let { it.startsWith("T03 A scroll ") && kotlin.math.abs(it.substringAfterLast(' ').toInt()-kotlin.math.round(160f/scale).toInt())<=1 }==true && peer.canAct() }
            accepted { peer.scrollBy(0f,-160f) }
            await("negative scroll",1_000) { peer.browserState()?.title=="T03 A scroll 0" && peer.canAct() }
            accepted { peer.activateAt(x,92f*scale) }
            await("B navigation",5_000) { peer.browserState()?.title=="T03 B" && peer.canAct() }
            navigationButton(R.id.rg_back)
            await("Back effect",5_000) { peer.browserState()?.url?.contains("/control.html?")==true && peer.canAct() }
            navigationButton(R.id.rg_forward)
            await("Forward effect",5_000) { peer.browserState()?.title=="T03 B" && peer.canAct() }
            val beforeReload=peer.browserState()!!.context.documentId
            navigationButton(R.id.rg_reload)
            await("Reload effect",5_000) { peer.browserState()?.context?.documentId!=beforeReload && peer.browserState()?.title=="T03 B" && peer.canAct() }
            accepted { peer.activateAt(x,y) }
            await("final page effect",1_000) { peer.browserState()?.title=="T03 done" }
            Log.i("EyeBrowseT03","RG_T03_ACTIONS_PASS")
            scenario.onActivity { peer.pause() }
        } finally { scenario.close() }
    }
    private fun await(label:String,bound:Long,condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+bound
        while(SystemClock.elapsedRealtime()<end) { if(condition())return;SystemClock.sleep(25) }
        fail(label)
    }
}
