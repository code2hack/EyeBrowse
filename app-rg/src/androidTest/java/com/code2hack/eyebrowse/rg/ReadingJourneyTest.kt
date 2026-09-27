package com.code2hack.eyebrowse.rg

import android.app.AlertDialog
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Raw pose and real pad drive production Reading. Files carry oracle barriers, never input. */
@RunWith(AndroidJUnit4::class)
class ReadingJourneyTest {
    @Test fun neutralScrollInterruptionsAndLostStop() {
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val j=PointerBrowserJourneyTest.Journey(scenario)
        val phase=File(j.app.cacheDir,"kbd-${j.mission}.phase")
        val ack=File(j.app.cacheDir,"kbd-${j.mission}.ack")
        fun checkPhone(name:String) {
            phase.writeText(name)
            val end=SystemClock.elapsedRealtime()+5_000
            while(SystemClock.elapsedRealtime()<end && ack.takeIf { it.exists() }?.readText()?.trim()!=name) SystemClock.sleep(20)
            assertEquals(name,ack.takeIf { it.exists() }?.readText()?.trim())
        }
        fun pose(degrees:Double) { j.main { j.source.pitchDegrees(degrees) };SystemClock.sleep(100) }
        fun toggle() { j.pad(291);j.confirmWindow() }
        fun freshFrame(label:String) {
            j.await("$label current frame",2_000) { j.peer.canAct() }
            val state=checkNotNull(j.peer.browserState())
            val frame=checkNotNull(j.peer.lastFrameHeader)
            val profile=checkNotNull(j.peer.profile())
            assertFalse(state.stale)
            assertEquals(state.context,frame.context)
            assertEquals(profile,state.profile)
            assertEquals(profile.width,frame.width);assertEquals(profile.height,frame.height)
            Log.i("EyeBrowseReadingTest","I11_FRAME mission=${j.mission} phase=$label context=${state.context} seq=${frame.frameSeq} capture=${frame.captureTsMs}")
        }
        var complete=false
        try {
            j.setup();j.native(R.id.rg_retry,"Retry")
            j.await("Phone consent available",10_000) { j.peer.browserState()?.owner==ControlOwner.PHONE && j.peer.canHandoff() }
            j.handoff(ControlOwner.RG,"A")
            pose(0.0);toggle()
            j.await("Reading fresh viewport") { j.peer.reading && j.peer.canAct() }
            freshFrame("reading-entry")
            val context=checkNotNull(j.peer.browserState()).context
            val actions=j.actions
            j.main {
                for(id in listOf(R.id.rg_status,R.id.rg_detail,R.id.rg_utilities,R.id.rg_navigation,R.id.rg_handoff,R.id.rg_pointer))
                    assertFalse("Reading hides $id",it.findViewById<View>(id).isShown)
                assertFalse(j.peer.keyboard.visible)
            }
            val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            File(j.app.cacheDir,"kbd-${j.mission}-reading.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            j.pad();j.confirmWindow();assertEquals(actions,j.actions)
            checkPhone("reading_ready")
            SystemClock.sleep(400);pose(10.0);checkPhone("reading_down")
            pose(0.0);checkPhone("reading_neutral")
            pose(-10.0);checkPhone("reading_up")
            pose(10.0);SystemClock.sleep(350)
            val before=j.peer.lastActionResult
            j.dispatched(j.pad(292),"Reading swipe")
            j.result(before,"Reading swipe");checkPhone("reading_swipe")
            // Held tilt remains suppressed after the discrete scroll, despite fresh frames.
            SystemClock.sleep(400);checkPhone("reading_held")
            pose(0.0);SystemClock.sleep(400);pose(10.0);checkPhone("reading_renewed")
            j.main { j.source.flowing=false };checkPhone("reading_sensor")
            j.main { j.source.flowing=true };SystemClock.sleep(450);checkPhone("reading_sensor_held")
            pose(0.0);SystemClock.sleep(400);pose(10.0);checkPhone("reading_before_modal")
            lateinit var dialog:AlertDialog
            j.main { dialog=AlertDialog.Builder(it).setMessage("Reading interruption fixture").setPositiveButton("Close",null).show() }
            checkPhone("reading_inactive")
            j.main { dialog.dismiss() }
            j.await("Reading focus restored") { j.activity.hasWindowFocus() }
            SystemClock.sleep(400);checkPhone("reading_inactive_held")
            pose(0.0);SystemClock.sleep(400);pose(10.0);checkPhone("reading_before_loss")
            // Test-only abrupt producer stall: omit BOTH further updates and the final Stop,
            // while leaving authenticated transport/hosting alive to isolate Phone expiry.
            j.main {
                val type=RgPresentationController::class.java
                val handler=type.getDeclaredField("main").apply { isAccessible=true }.get(j.peer) as Handler
                val tick=type.getDeclaredField("readingTick").apply { isAccessible=true }.get(j.peer) as Runnable
                handler.removeCallbacks(tick);j.source.flowing=false
            }
            checkPhone("reading_lost_stop")
            toggle() // Safe exit does not require a sensor or pointer.
            j.await("Normal without auto-reopen") { !j.peer.reading && !j.peer.keyboard.visible && j.peer.canAct() }
            assertEquals(context.documentId,j.peer.browserState()?.context?.documentId)
            assertEquals(context.controlEpoch,j.peer.browserState()?.context?.controlEpoch)
            checkPhone("reading_exit")
            j.main { j.source.flowing=true }
            pose(0.0);j.await("fresh sensor for second Reading entry") { j.pointer.position.available }
            toggle();j.await("Reading ready again") { j.peer.reading && j.peer.canAct() }
            SystemClock.sleep(400);pose(10.0);checkPhone("reading_before_disconnect")
            val beforeDisconnect=checkNotNull(j.peer.browserState()).context
            j.client().disconnect()
            j.await("link loss retires input") { !j.peer.canAct() }
            checkPhone("reading_disconnected")
            j.quiescent()
            // Explicit recovery through the same local controller used by Retry, with held tilt.
            j.main { j.peer.reconnect() }
            j.await("fresh authenticated state after reconnect",10_000) { j.peer.canAct() }
            freshFrame("reconnected-held")
            val recovered=checkNotNull(j.peer.browserState()).context
            assertEquals(beforeDisconnect.lifetimeId,recovered.lifetimeId)
            assertEquals(beforeDisconnect.documentId,recovered.documentId)
            assertEquals(beforeDisconnect.controlEpoch,recovered.controlEpoch)
            assertTrue(recovered.viewportEpoch>beforeDisconnect.viewportEpoch)
            SystemClock.sleep(400);checkPhone("reading_reconnected_held")
            pose(0.0);SystemClock.sleep(400);pose(10.0);checkPhone("reading_reconnected_neutral")
            toggle();j.await("Normal ready for Phone handoff") { !j.peer.reading && j.peer.canAct() }
            j.native(R.id.rg_handoff,"Use on Phone")
            j.await("Phone owns control") { j.peer.browserState()?.owner==ControlOwner.PHONE }
            checkPhone("reading_phone_owner")
            phase.writeText("complete");complete=true
        } finally {
            if(!complete)phase.writeText("abort")
            j.main { j.probe?.close() };scenario.close()
        }
    }
}
