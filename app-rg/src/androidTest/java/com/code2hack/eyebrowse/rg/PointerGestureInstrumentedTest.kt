package com.code2hack.eyebrowse.rg

import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.*
import android.widget.*
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.code2hack.eyebrowse.core.link.CapabilityNegotiation
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.framing.*
import com.code2hack.eyebrowse.core.link.messages.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class PointerGestureInstrumentedTest {
    private class Scene(val scenario: ActivityScenario<MainActivity>,val source: RawPoseReplay) {
        val pad=InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.first { it.name=="ROKID,PSOC-TP-R" }
        fun main(block: (MainActivity)->Unit) { scenario.onActivity(block) }
        fun await(label: String,bound: Long=2_000,condition: (MainActivity)->Boolean) {
            val end=SystemClock.uptimeMillis()+bound
            while(SystemClock.uptimeMillis()<end) { var ok=false;main { ok=condition(it) };if(ok)return;SystemClock.sleep(10) }
            fail(label)
        }
        fun center(activity: MainActivity, view: View): InputPoint {
            val location=IntArray(2);val origin=IntArray(2)
            view.getLocationInWindow(location);activity.findViewById<View>(R.id.rg_pointer).getLocationInWindow(origin)
            return InputPoint(location[0]-origin[0]+view.width/2f,location[1]-origin[1]+view.height/2f)
        }
        fun aim(id: Int): InputPoint {
            lateinit var point: InputPoint
            main { point=center(it,it.findViewById(id));source.aim(it,point) }
            await("raw pose reaches native target") { val p=it.findViewById<PointerOverlay>(R.id.rg_pointer).position;abs(p.x-point.x)<2 && abs(p.y-point.y)<2 }
            return point
        }
        fun down(code: Int=66,repeat: Int=0,downTime: Long?=null): Long {
            var at=0L
            main { at=SystemClock.uptimeMillis();assertTrue(it.dispatchKeyEvent(event(code,KeyEvent.ACTION_DOWN,downTime ?: at,at,repeat))) }
            return downTime ?: at
        }
        fun up(down: Long,code: Int=66,flags: Int=0) { main { assertTrue(it.dispatchKeyEvent(event(code,KeyEvent.ACTION_UP,down,SystemClock.uptimeMillis(),flags=flags))) } }
        fun tap(code: Int=66) { val down=down(code);SystemClock.sleep(20);up(down,code) }
        fun event(code:Int,action:Int,down:Long,at:Long,repeat:Int=0,flags:Int=0,deviceId:Int=pad.id,meta:Int=0) =
            KeyEvent(down,at,action,code,repeat,meta,deviceId,0,flags,InputDevice.SOURCE_KEYBOARD)
        fun waitConfirmation() { var ms=0L;main { ms=it.inputRouter.doubleTapMs };SystemClock.sleep(ms+100) }
        fun phoneState(doc: String="A",epoch: Long=1): BrowserStateMessage {
            lateinit var state: BrowserStateMessage
            main {
                state=BrowserStateMessage(ControlOwner.PHONE,ControlContext("gesture-fixture",epoch,doc,1,1),it.presentation.profile(),stale=true)
                it.presentation.onPresentationCompatibility(CapabilityNegotiation.Accepted)
                it.presentation.onControl(state)
            }
            await("synthetic local handoff enabled") { it.findViewById<Button>(R.id.rg_handoff).isEnabled }
            return state
        }
    }
    private fun scene(block: (Scene)->Unit) {
        val scenario=ActivityScenario.launch(MainActivity::class.java);val source=RawPoseReplay()
        try {
            val scene=Scene(scenario,source)
            scene.main {
                val pointer=it.findViewById<PointerOverlay>(R.id.rg_pointer)
                pointer.stop();pointer.replaceSourceForTest(source);pointer.start()
                Log.i("EyeBrowseGestureTest","SURFACE focus=${it.hasWindowFocus()} doubleMs=${it.inputRouter.doubleTapMs} longMs=${ViewConfiguration.getLongPressTimeout()} source=${scene.pad.name}")
            }
            scene.await("ordinary app window focus and fresh pose") { it.hasWindowFocus() && it.findViewById<PointerOverlay>(R.id.rg_pointer).position.available }
            block(scene)
        } finally { scenario.close() }
        assertFalse(source.registered)
    }
    private fun observeRecenter(scene: Scene): AtomicInteger {
        val calls=AtomicInteger()
        scene.main { activity -> activity.findViewById<Button>(R.id.rg_recenter).setOnClickListener {
            calls.incrementAndGet();activity.findViewById<PointerOverlay>(R.id.rg_pointer).recenter()
        } }
        return calls
    }

    @Test fun confirmedTapKeepsOriginalTargetAndInvokesOnceWithinLocalBound() = scene { s ->
        val calls=observeRecenter(s);val point=s.aim(R.id.rg_recenter)
        s.main { it.findViewById<Button>(R.id.rg_recenter).requestFocus() }
        s.tap();assertEquals("no focused-button leak at UP",0,calls.get())
        s.main { s.source.aim(it,InputPoint(350f,460f)) }
        s.await("confirmed original native target") { calls.get()==1 }
        s.main {
            val trace=checkNotNull(it.inputRouter.lastDispatch)
            assertTrue(trace.accepted);assertTrue(trace.finishedAt-trace.confirmedAt<=100)
            assertTrue(trace.recognitionWaitMs>=it.inputRouter.doubleTapMs-20)
            assertNull(it.presentation.lastHandoffRequest)
            Log.i("EyeBrowseGestureTest","CONFIRMED_LOCAL_MS=${trace.finishedAt-trace.confirmedAt} RECOGNITION_WAIT_MS=${trace.recognitionWaitMs} originalPoint=$point")
        }
        s.waitConfirmation();assertEquals(1,calls.get())
    }
    @Test fun doubleAndFirmwareCompositeConsumeBothTapsWithoutReadingTransition() = scene { s ->
        val calls=observeRecenter(s);val modes=AtomicInteger();s.aim(R.id.rg_recenter)
        s.main { it.inputRouter.onModeToggleIntent={modes.incrementAndGet()} }
        s.tap();SystemClock.sleep(40);s.tap();s.tap(291)
        s.waitConfirmation();assertEquals(0,calls.get());assertEquals(1,modes.get())
        Log.i("EyeBrowseGestureTest","DOUBLE_COMPOSITE singles=0 modeIntents=1 readingTransition=false")
    }
    @Test fun swipeRepeatHoldAndCancelCannotLeakClicksOrAcquirePhoneControl() = scene { s ->
        val calls=observeRecenter(s);s.phoneState();s.aim(R.id.rg_recenter)
        s.tap();s.tap(292);s.waitConfirmation();s.tap(293)
        var down=s.down();s.down(repeat=1,downTime=down);s.up(down)
        down=s.down();SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+20L);s.up(down)
        down=s.down();s.up(down,flags=KeyEvent.FLAG_CANCELED);s.waitConfirmation()
        down=s.down();s.up(down,flags=KeyEvent.FLAG_LONG_PRESS);s.waitConfirmation()
        assertEquals(0,calls.get())
        s.main { assertNull(it.presentation.lastHandoffRequest);assertNull(it.presentation.lastActionResult);assertEquals(0L,it.inputRouter.nativeInvocations) }
        Log.i("EyeBrowseGestureTest","PHONE_OWNED_GESTURES swipeBoth=true repeatedHoldCancel=true handoffAttempts=0 clicks=0")
    }
    @Test fun changedConsentMeaningAndContextCancelBeforeTypedRequest() = scene { s ->
        val state=s.phoneState();s.aim(R.id.rg_handoff);s.tap()
        s.await("one fresh typed glasses request") { it.presentation.lastHandoffRequest!=null }
        lateinit var glassesRequest: HandoffRequestMessage
        s.main {
            glassesRequest=checkNotNull(it.presentation.lastHandoffRequest)
            assertEquals(HandoffTargetWire.RG,glassesRequest.target)
            assertEquals(state.context.controlEpoch,glassesRequest.observedControlEpoch)
            assertEquals(it.presentation.profile(),glassesRequest.profile)
            assertEquals(1L,it.inputRouter.nativeInvocations)
            assertFalse("no page input before an authoritative grant and frame",it.presentation.canAct())
            assertNull(it.presentation.lastHandoffResult)
        }
        s.tap()
        s.main { it.findViewById<Button>(R.id.rg_handoff).setText(R.string.use_on_phone);it.inputRouter.surfaceChanged() }
        s.waitConfirmation();s.main { assertSame(glassesRequest,it.presentation.lastHandoffRequest);assertEquals(1L,it.inputRouter.nativeInvocations) }
        s.main { it.presentation.onControl(state.copy(title="restore local fixture")) }
        s.await("original meaning restored") { it.findViewById<Button>(R.id.rg_handoff).text.toString()==it.getString(R.string.use_on_glasses) }
        s.tap()
        s.main { it.presentation.onControl(state.copy(owner=ControlOwner.RG,context=state.context.copy(controlEpoch=2,documentId="B"))) }
        s.waitConfirmation();s.main { assertSame(glassesRequest,it.presentation.lastHandoffRequest);assertEquals(1L,it.inputRouter.nativeInvocations) }
        // Fresh deliberate activation invokes the real existing handoff method. There is no socket in this RG-only fixture.
        s.tap();s.await("one fresh typed return request") { it.presentation.lastHandoffRequest?.target==HandoffTargetWire.PHONE }
        s.main {
            assertEquals(HandoffTargetWire.PHONE,it.presentation.lastHandoffRequest!!.target)
            assertEquals(2L,it.presentation.lastHandoffRequest!!.observedControlEpoch)
            assertEquals(2L,it.inputRouter.nativeInvocations)
            assertFalse(it.presentation.canAct());assertNull(it.presentation.lastHandoffResult)
        }
        Log.i("EyeBrowseGestureTest","CONSENT localSyntheticState=true staleLabel=0 staleContext=0 freshRequests=RG,PHONE actualLink=false")
    }
    @Test fun pauseSilenceAndModalOcclusionCancelWithoutRecoveryReplay() {
        for(reason in listOf("pause","silence","modal")) scene { s ->
            val calls=observeRecenter(s);s.aim(R.id.rg_recenter);s.tap()
            var dialog: AlertDialog?=null
            when(reason) {
                "pause" -> s.scenario.moveToState(Lifecycle.State.CREATED)
                "silence" -> s.main { s.source.flowing=false }
                else -> s.main { dialog=AlertDialog.Builder(it).setMessage("Gesture cancellation fixture").setPositiveButton("Close",null).show() }
            }
            s.waitConfirmation();assertEquals(reason,0,calls.get())
            when(reason) {
                "pause" -> s.scenario.moveToState(Lifecycle.State.RESUMED)
                "silence" -> s.main { s.source.flowing=true }
                else -> s.main { dialog!!.dismiss() }
            }
            s.waitConfirmation();assertEquals("no replay after $reason",0,calls.get())
            Log.i("EyeBrowseGestureTest","INTERRUPTION reason=$reason invocations=0 replay=0")
        }
    }
    @Test fun liveViewGeometryAccountsForPaddingAndBlocksCoveredOrDisabledTargets() = scene { s ->
        s.main { activity ->
            val image=activity.findViewById<ImageView>(R.id.rg_page);val profile=checkNotNull(activity.presentation.profile())
            val bitmap=Bitmap.createBitmap(profile.width,profile.height,Bitmap.Config.ARGB_8888)
            image.setImageBitmap(bitmap);image.scaleType=ImageView.ScaleType.FIT_CENTER;image.setPadding(16,16,16,16)
        }
        SystemClock.sleep(100)
        s.main { activity ->
            val image=activity.findViewById<ImageView>(R.id.rg_page)
            val input=activity.presentation.inputSnapshot().copy(owner=ControlOwner.RG,pageReady=true)
            val center=s.center(activity,image);val g=checkNotNull(activity.inputRouter.imageGeometry(input))
            val mapped=checkNotNull(g.pagePoint(center))
            assertEquals(g.profile.width/2f,mapped.x,1f);assertEquals(g.profile.height/2f,mapped.y,1f)
            val recenter=activity.findViewById<Button>(R.id.rg_recenter);recenter.isEnabled=false
            assertNull(activity.inputRouter.targetAt(s.center(activity,recenter),input))
            val margin=InputPoint(center.x-image.width/2+1,center.y)
            assertNull(activity.inputRouter.targetAt(margin,input))
            val content=activity.findViewById<android.widget.FrameLayout>(android.R.id.content)
            val cover=Button(activity).apply { text="Covered fixture";isEnabled=false }
            content.addView(cover,android.widget.FrameLayout.LayoutParams(100,80).apply { leftMargin=(center.x-50).toInt();topMargin=(center.y-40).toInt() })
        }
        SystemClock.sleep(100)
        s.main { activity ->
            val image=activity.findViewById<ImageView>(R.id.rg_page)
            assertNull(activity.inputRouter.targetAt(s.center(activity,image),activity.presentation.inputSnapshot().copy(pageReady=true)))
            Log.i("EyeBrowseGestureTest","LIVE_GEOMETRY padding=true matrix=true marginsRejected=true disabledAndCoveredBlock=true syntheticMappingOnly=true")
        }
    }
    @Test fun expectedContextBridgeSerializesValidationAndRejectsRecoveredOldIntents() = scene { s ->
        val state=s.phoneState();var effects=0
        s.main { activity ->
            val controller=activity.presentation;val original=controller.inputSnapshot()
            assertTrue(controller.dispatchIfCurrent(original) { effects++;true })
            for(context in listOf(state.context.copy(documentId="B"),state.context.copy(viewportEpoch=2),state.context.copy(hostingGeneration=2))) {
                controller.onControl(state.copy(context=context))
                assertFalse(controller.dispatchIfCurrent(original) { effects++;true })
            }
            controller.onControl(state);assertFalse(controller.dispatchIfCurrent(original) { effects++;true })
            val current=controller.inputSnapshot();val started=CountDownLatch(1);val changed=CountDownLatch(1)
            val update=Thread { started.countDown();controller.onControl(state.copy(context=state.context.copy(documentId="C")));changed.countDown() }
            assertTrue(controller.dispatchIfCurrent(current) {
                update.start();assertTrue(started.await(1,TimeUnit.SECONDS));assertFalse(changed.await(30,TimeUnit.MILLISECONDS));effects++;true
            })
            update.join(1_000);assertEquals(0L,changed.count)
            val callbackReturned=CountDownLatch(1)
            assertTrue(controller.dispatchIfCurrent(controller.inputSnapshot()) {
                Thread { controller.onLinkLost();callbackReturned.countDown() }.start()
                assertTrue("engine-held callbacks must not block on our monitor",callbackReturned.await(500,TimeUnit.MILLISECONDS));true
            })
        }
        s.waitConfirmation()
        s.main { activity -> assertFalse(activity.presentation.canAct());assertEquals(2,effects) }
        Log.i("EyeBrowseGestureTest","EXPECTED_CONTEXT original=rejectAfterChange recoveredOld=reject serialized=true engineCallbackNonblocking=true noCommandIssued=true")
    }
    @Test fun newDecodedFramesWithSameContextDoNotCancelThePendingLocalTap() = scene { s ->
        val calls=observeRecenter(s);lateinit var context: ControlContext;lateinit var pixels: ByteArray
        s.main { activity ->
            val profile=activity.presentation.profile()!!;context=ControlContext("frame-fixture",1,"A",1,1)
            // Synthetic receiver input only; compatibility remains false, so no page command can reserve an ordinal.
            activity.presentation.onControl(BrowserStateMessage(ControlOwner.RG,context,profile,stale=false))
            val bitmap=Bitmap.createBitmap(profile.width,profile.height,Bitmap.Config.ARGB_8888)
            pixels=ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY,85,it);it.toByteArray() };bitmap.recycle()
            activity.presentation.onPresentation(PresentationFrame(PresentationFrameHeader(context,1,SystemClock.elapsedRealtime(),profile.width,profile.height),pixels))
        }
        s.await("first synthetic frame decoded") { it.presentation.displayedFrames>=1 }
        s.aim(R.id.rg_recenter);s.tap()
        for(seq in 2L..3L) {
            SystemClock.sleep(70)
            s.main { val profile=it.presentation.profile()!!;it.presentation.onPresentation(PresentationFrame(PresentationFrameHeader(context,seq,SystemClock.elapsedRealtime(),profile.width,profile.height),pixels)) }
        }
        s.await("tap survives frame sequence changes") { calls.get()==1 && it.presentation.displayedFrames>=3 }
        s.main { assertFalse(it.presentation.canAct());assertNull(it.presentation.lastHandoffRequest) }
        Log.i("EyeBrowseGestureTest","SAME_CONTEXT_FRAMES decoded=3 localTap=1 synthetic=true compatibility=false pageCommands=0")
    }
    @Test fun unrelatedDevicesAndSystemKeysAreNotReinterpretedAsPadGestures() = scene { s ->
        s.main {
            val now=SystemClock.uptimeMillis()
            assertFalse(it.inputRouter.key(s.event(66,KeyEvent.ACTION_DOWN,now,now,deviceId=-1)))
            assertFalse(it.inputRouter.key(s.event(KeyEvent.KEYCODE_BACK,KeyEvent.ACTION_DOWN,now,now)))
            assertTrue(it.inputRouter.key(s.event(66,KeyEvent.ACTION_DOWN,now,now,meta=KeyEvent.META_SHIFT_ON)))
            assertEquals(0L,it.inputRouter.nativeInvocations);assertNull(it.presentation.lastHandoffRequest)
        }
        Log.i("EyeBrowseGestureTest","PAD_SCOPE virtualPassThrough=true systemBackPassThrough=true modifiedPadRejected=true")
    }
}
