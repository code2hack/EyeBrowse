package com.code2hack.eyebrowse.rg

import android.app.AlertDialog
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.util.Log
import android.view.*
import android.widget.*
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.transport.LinkClientEngine
import com.code2hack.eyebrowse.rg.link.RgLinkClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** All positive browser operations use raw pose -> actual Activity KeyEvent -> production router -> TLS. */
@RunWith(AndroidJUnit4::class)
class PointerBrowserJourneyTest {
    private class Journey(val scenario: ActivityScenario<MainActivity>) {
        val source=RawPoseReplay()
        val pad=InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice).first { it.name=="ROKID,PSOC-TP-R" }
        lateinit var peer:RgPresentationController
        lateinit var pointer:PointerOverlay
        lateinit var activity:MainActivity
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val mission=UUID.fromString(InstrumentationRegistry.getArguments().getString("missionId")).toString()
        val cursor get() = app.getSharedPreferences("browser-command-sequence",0).all.toMap()
        fun main(action:(MainActivity)->Unit) { scenario.onActivity(action) }
        fun await(label:String,bound:Long=3_000,condition:()->Boolean) {
            val end=SystemClock.uptimeMillis()+bound
            while(SystemClock.uptimeMillis()<end) { var ok=false;main { ok=condition() };if(ok)return;SystemClock.sleep(10) }
            fail(label)
        }
        fun setup() {
            main {
                activity=it;peer=it.presentation;pointer=it.findViewById(R.id.rg_pointer)
                pointer.stop();pointer.replaceSourceForTest(source);pointer.start()
                assertEquals("declared RG mounting rotation",Surface.ROTATION_0,it.display!!.rotation)
            }
            await("focused surface/fresh raw replay") { activity.hasWindowFocus() && pointer.position.available }
        }
        fun title()=peer.browserState()?.title?.substringBefore("|G=")
        fun geometry()=JSONObject(checkNotNull(peer.browserState()?.title).substringAfter("|G="))
        fun nativeCenter(view:View):InputPoint {
            val at=IntArray(2);val origin=IntArray(2);view.getLocationInWindow(at);pointer.getLocationInWindow(origin)
            return InputPoint(at[0]-origin[0]+view.width/2f,at[1]-origin[1]+view.height/2f)
        }
        fun aim(point:InputPoint) {
            main { source.aim(it,point) }
            await("raw head aim reaches $point",2_000) { pointer.position.available && abs(pointer.position.x-point.x)<2 && abs(pointer.position.y-point.y)<2 }
        }
        fun aimNative(id:Int):InputPoint {
            lateinit var p:InputPoint;main { p=nativeCenter(it.findViewById(id)) };aim(p);return p
        }
        fun pageRoot(x:Float,y:Float):InputPoint {
            lateinit var point:InputPoint
            main { a ->
                val image=a.findViewById<ImageView>(R.id.rg_page)
                val drawable=image.drawable as BitmapDrawable
                // Independent forward Android transform; do not invert the production hit-test result to aim.
                val p=floatArrayOf(x/drawable.bitmap.width*drawable.intrinsicWidth,y/drawable.bitmap.height*drawable.intrinsicHeight)
                image.imageMatrix.mapPoints(p)
                val location=IntArray(2);val origin=IntArray(2);image.getLocationInWindow(location);pointer.getLocationInWindow(origin)
                point=InputPoint(location[0]-origin[0]+image.paddingLeft+p[0],location[1]-origin[1]+image.paddingTop+p[1])
                Log.i(TAG,"AIM_MAP native=${x},${y} root=$point image=${image.width}x${image.height} drawable=${drawable.intrinsicWidth}x${drawable.intrinsicHeight}")
            };return point
        }
        fun aimPage(next:Boolean=false):InputPoint {
            val g=geometry();val point=pageRoot(g.getDouble(if(next) "nx" else "x").toFloat(),g.getDouble(if(next) "ny" else "y").toFloat())
            aim(point);return point
        }
        data class Tap(val down:Long,val up:Long,val previous:RgInputRouter.DispatchTrace?)
        fun pad(code:Int=66):Tap {
            var down=0L;var up=0L;var previous:RgInputRouter.DispatchTrace?=null
            main { previous=it.inputRouter.lastDispatch;down=SystemClock.uptimeMillis()
                assertTrue(it.dispatchKeyEvent(KeyEvent(down,down,KeyEvent.ACTION_DOWN,code,0,0,pad.id,0,0,InputDevice.SOURCE_KEYBOARD))) }
            SystemClock.sleep(20)
            main { up=SystemClock.uptimeMillis()
                assertTrue(it.dispatchKeyEvent(KeyEvent(down,up,KeyEvent.ACTION_UP,code,0,0,pad.id,0,0,InputDevice.SOURCE_KEYBOARD))) }
            return Tap(down,up,previous)
        }
        fun dispatched(tap:Tap,name:String):RgInputRouter.DispatchTrace {
            await("confirmed dispatch $name",1_000) { activity.inputRouter.lastDispatch !== tap.previous }
            lateinit var trace:RgInputRouter.DispatchTrace
            main { trace=checkNotNull(it.inputRouter.lastDispatch);assertTrue("eligible $name",trace.accepted)
                assertTrue("enqueue/local invocation <=100ms",trace.finishedAt-trace.confirmedAt<=100) }
            Log.i(TAG,"DISPATCH name=$name localMs=${trace.finishedAt-trace.confirmedAt} recognitionMs=${trace.recognitionWaitMs} down=${tap.down} confirm=${trace.confirmedAt}")
            return trace
        }
        fun contextDuringTap(tap:Tap,name:String,changed:(BrowserStateMessage)->Boolean):Long {
            // The controller publishes a volatile snapshot. ActivityScenario's idle barrier can
            // observe an already-arrived update late; it is not a context-arrival clock.
            val deadline=tap.up+activity.inputRouter.doubleTapMs
            while(SystemClock.uptimeMillis()<deadline && peer.browserState()?.let(changed)!=true) SystemClock.sleep(2)
            val elapsed=SystemClock.uptimeMillis()-tap.up
            Log.i(TAG,"PENDING_CONTEXT name=$name observedMs=$elapsed windowMs=${activity.inputRouter.doubleTapMs}")
            assertTrue("$name context changed while tap pending ($elapsed ms)",elapsed<activity.inputRouter.doubleTapMs && peer.browserState()?.let(changed)==true)
            return elapsed
        }
        fun effect(name:String,trace:RgInputRouter.DispatchTrace) {
            val elapsed=SystemClock.uptimeMillis()-trace.confirmedAt
            Log.i(TAG,"NAV_EFFECT name=$name effectMs=$elapsed")
            assertTrue("$name fixture effect <=1s ($elapsed ms)",elapsed<=1_000)
        }
        fun native(id:Int,name:String):RgInputRouter.DispatchTrace { aimNative(id);return dispatched(pad(),name) }
        fun confirmWindow() { SystemClock.sleep(450) }
        fun request(name:String) { Log.i(TAG,"I8_PHONE_OP $name mission=$mission") }
        fun check(name:String) { request(name);await("Phone independent $name",8_000) { title()=="I8 ACK $name" } }
        fun qualified(page:String):Boolean {
            if(!peer.canAct()) return false
            val state=peer.browserState() ?: return false;val header=peer.lastFrameHeader ?: return false
            if(header.context!=state.context || header.width!=peer.profile()?.width || header.height!=peer.profile()?.height) return false
            val bitmap=(activity.findViewById<ImageView>(R.id.rg_page).drawable as? BitmapDrawable)?.bitmap ?: return false
            val expected=if(page=="A") intArrayOf(246,243,234) else intArrayOf(231,240,255)
            var matches=0;var count=0
            for(y in 16 until bitmap.height step 32) for(x in 16 until bitmap.width step 32) {
                val c=bitmap.getPixel(x,y);count++
                if(abs(((c shr 16) and 255)-expected[0])<=12 && abs(((c shr 8) and 255)-expected[1])<=12 && abs((c and 255)-expected[2])<=12) matches++
            }
            return count>0 && matches*8>count && peer.browserState()?.context==header.context
        }
        fun live(page:String,afterCapture:Long=-1,bound:Long=5_000) = await("fixture-qualified current $page frame",bound) {
            qualified(page) && (peer.lastFrameHeader?.captureTsMs ?: -1)>afterCapture
        }
        fun frameHash():Long {
            val bitmap=(activity.findViewById<ImageView>(R.id.rg_page).drawable as BitmapDrawable).bitmap
            val crc=java.util.zip.CRC32()
            for(y in 0 until bitmap.height step 4) for(x in 0 until bitmap.width step 4) {
                val pixel=bitmap.getPixel(x,y);crc.update(pixel ushr 16);crc.update(pixel ushr 8);crc.update(pixel)
            }
            return crc.value
        }
        fun handoff(owner:ControlOwner,page:String):RgInputRouter.DispatchTrace {
            val previous=peer.lastHandoffResult
            val trace=native(R.id.rg_handoff,"handoff-$owner")
            await("authoritative $owner result",1_000) { peer.lastHandoffResult !== previous && peer.lastHandoffResult?.let { it.accepted && it.owner==owner }==true }
            val ack=SystemClock.uptimeMillis()-trace.confirmedAt;assertTrue("handoff <=1s",ack<=1_000)
            await("owner state $owner",1_000) { peer.browserState()?.owner==owner }
            if(owner==ControlOwner.RG) {
                live(page,bound=2_000)
                val first=SystemClock.uptimeMillis()-trace.confirmedAt;assertTrue("qualified presentation <=2s",first<=2_000)
                Log.i(TAG,"HANDOFF owner=$owner ackMs=$ack firstQualifiedMs=$first profile=${peer.profile()} frame=${peer.lastFrameHeader}")
            } else Log.i(TAG,"HANDOFF owner=$owner ackMs=$ack")
            return trace
        }
        fun client():RgLinkClient=RgPresentationController::class.java.getDeclaredField("client").let { it.isAccessible=true;it.get(peer) as RgLinkClient }
        fun quiescent() = await("link cancellation quiescence",3_000) {
            val engine=RgLinkClient::class.java.getDeclaredField("engine").let { it.isAccessible=true;it.get(client()) as? LinkClientEngine }
            engine?.isBusy!=true
        }
        fun reconnect(page:String,oldEpoch:Long,oldCapture:Long) {
            quiescent();native(R.id.rg_retry,"Retry")
            live(page,oldCapture,10_000)
            assertEquals(ControlOwner.RG,peer.browserState()!!.owner);assertEquals(oldEpoch,peer.browserState()!!.context.controlEpoch)
            Log.i(TAG,"RECONNECT owner=RG epoch=$oldEpoch freshCapture=${peer.lastFrameHeader!!.captureTsMs}")
        }
        fun recoverPose() {
            await("fresh pose after interruption") { pointer.position.available && activity.hasWindowFocus() }
            main { source.adoptCurrentReference() }
        }
        fun raw(message:BrowserActionMessage):BrowserActionResultMessage {
            val previous=peer.lastActionResult;assertTrue(client().sendControl(message))
            await("correlated protocol negative") { peer.lastActionResult !== previous && peer.lastActionResult?.commandId==message.commandId }
            return peer.lastActionResult!!
        }
        fun result(previous:BrowserActionResultMessage?,name:String):BrowserActionResultMessage {
            await("$name admission",2_000) { peer.lastActionResult !== previous }
            return checkNotNull(peer.lastActionResult).also {
                assertTrue("$name accepted",it.accepted);assertNull(it.effectSucceeded)
                Log.i(TAG,"ACTION name=$name id=${it.commandId} accepted=true effectSucceeded=null")
            }
        }
    }

    @Test fun rgPointerAndPadDriveAuthenticatedActionsWithNoInterruptionReplay() {
        val scenario=ActivityScenario.launch(MainActivity::class.java);val j=Journey(scenario)
        val mission=UUID.fromString(InstrumentationRegistry.getArguments().getString("missionId")).toString()
        val ack=File(j.app.cacheDir,"i8-$mission.ack");ack.writeText("")
        var primary:Throwable?=null
        fun waitHost(name:String) {
            val end=SystemClock.uptimeMillis()+8_000
            while(SystemClock.uptimeMillis()<end) { if(ack.readText().trim()==name)return;SystemClock.sleep(10) }
            fail("host/Phone acknowledgement $name")
        }
        try {
            j.setup();j.native(R.id.rg_retry,"initial Retry")
            j.await("real authenticated Phone state",10_000) { j.peer.browserState()?.owner==ControlOwner.PHONE && j.peer.canHandoff() }
            val noAction=j.peer.lastActionResult;val initialCursor=j.cursor
            j.main { assertNull(j.peer.lastHandoffRequest) }
            j.aimNative(R.id.rg_page);j.pad();j.confirmWindow()
            j.aimNative(R.id.rg_reload);j.pad();j.confirmWindow();j.pad(292);j.confirmWindow()
            assertSame(noAction,j.peer.lastActionResult);assertEquals(initialCursor,j.cursor)
            assertEquals(ControlOwner.PHONE,j.peer.browserState()!!.owner);assertNull(j.peer.lastHandoffRequest)
            j.check("phone_owned")
            j.native(R.id.rg_recenter,"Recenter-before-consent");j.main { j.source.adoptCurrentReference() }
            j.handoff(ControlOwner.RG,"A");Log.i(TAG,"I8_FIRST_FIXTURE_FRAME")
            val oldRg=j.peer.browserState()!!.context
            j.await("Phone old-owner/foreground proof",8_000) { j.title()=="I8 RG VERIFIED" }
            j.handoff(ControlOwner.PHONE,"A")
            j.await("Phone roundtrip oracle",8_000) { j.title()=="I8 ROUNDTRIP VERIFIED" }
            val stale=BrowserActionMessage(BrowserCommandId.create(oldRg,Long.MAX_VALUE),oldRg,BrowserAction.Reload,Long.MAX_VALUE)
            assertEquals("STALE_CONTEXT",j.raw(stale).reason)
            j.handoff(ControlOwner.RG,"A")

            var before=j.cursor;var result=j.peer.lastActionResult
            j.aimNative(R.id.rg_forward);j.pad();j.confirmWindow()
            val modes=AtomicInteger();j.main { it.inputRouter.onModeToggleIntent={modes.incrementAndGet()} }
            j.aimPage();j.pad();SystemClock.sleep(40);j.pad();j.pad(291);j.confirmWindow()
            assertEquals(1,modes.get());assertEquals(before,j.cursor);assertSame(result,j.peer.lastActionResult)
            j.check("disabled_double")
            for(reason in listOf("sensor","pause","viewport","modal")) {
                j.live("A");j.aimPage();before=j.cursor;result=j.peer.lastActionResult
                val epoch=j.peer.browserState()!!.context.controlEpoch;val capture=j.peer.lastFrameHeader!!.captureTsMs
                val originalProfile=j.peer.profile()
                j.pad()
                var dialog:AlertDialog?=null;var originalPadding=IntArray(4)
                when(reason) {
                    "sensor" -> j.main { j.source.flowing=false }
                    "pause" -> scenario.moveToState(Lifecycle.State.CREATED)
                    "viewport" -> j.main {
                        val root=it.findViewById<View>(R.id.rg_root);originalPadding=intArrayOf(root.paddingLeft,root.paddingTop,root.paddingRight,root.paddingBottom)
                        root.setPadding(root.paddingLeft,root.paddingTop,root.paddingRight,root.paddingBottom+16)
                    }
                    "modal" -> j.main { dialog=AlertDialog.Builder(it).setMessage("Pointer interruption fixture").setPositiveButton("Close",null).show() }
                }
                j.confirmWindow();assertEquals("$reason did not reserve",before,j.cursor);assertSame(result,j.peer.lastActionResult)
                when(reason) {
                    "sensor" -> { j.main { j.source.flowing=true };j.recoverPose() }
                    "pause" -> { scenario.moveToState(Lifecycle.State.RESUMED);j.recoverPose();j.reconnect("A",epoch,capture) }
                    "viewport" -> {
                        j.await("viewport failure marks stale") { !j.peer.canAct() }
                        j.main { it.findViewById<View>(R.id.rg_root).setPadding(originalPadding[0],originalPadding[1],originalPadding[2],originalPadding[3]) }
                        j.await("measured original profile restored") { j.peer.profile()==originalProfile }
                        j.reconnect("A",epoch,capture)
                    }
                    else -> { j.main { dialog!!.dismiss() };j.await("modal closed") { j.activity.hasWindowFocus() } }
                }
                j.confirmWindow();assertEquals("$reason no replay",before,j.cursor);j.check(reason)
                Log.i(TAG,"INTERRUPTION name=$reason reserved=0 effects=0 recoveryReplay=0")
            }

            j.aimPage();before=j.cursor;result=j.peer.lastActionResult
            var beforeClickHash=0L;j.main { beforeClickHash=j.frameHash() }
            j.request("busy");waitHost("busy")
            val firstTap=j.pad();val actionTrace=j.dispatched(firstTap,"ActivateAt-A")
            j.main { assertFalse("real command is pending during Phone hold",j.peer.canAct()) }
            val reserved=j.cursor
            j.pad();j.pad(292)
            assertEquals("busy gestures reserve nothing",reserved,j.cursor)
            val click=j.result(result,"ActivateAt-A")
            j.await("actual visible A click",1_000) { j.title()=="T03 A click 1" && j.peer.canAct() && j.frameHash()!=beforeClickHash }
            val busyEffect=SystemClock.uptimeMillis()-actionTrace.confirmedAt
            assertTrue("bounded delayed fixture action",busyEffect<=1_000)
            Log.i(TAG,"BUSY_ACTION effectMs=$busyEffect fullGestureMs=${SystemClock.uptimeMillis()-firstTap.down} holdInjectedMs=900 extraReserved=0")
            j.check("busy_verified")
            val current=j.peer.browserState()!!.context;val g=j.geometry()
            assertEquals("STALE_COMMAND_SEQUENCE",j.raw(BrowserActionMessage(click.commandId,current,BrowserAction.ActivateAt(g.getDouble("x").toFloat(),g.getDouble("y").toFloat()),click.commandId.split(':')[2].toLong())).reason)
            var rejected:String?=null
            j.main { rejected=j.peer.activateAt(j.peer.profile()!!.width+1f,1f) } // Explicit protocol-negative injection only.
            j.await("definitive coordinate rejection") { j.peer.lastActionResult?.commandId==rejected }
            assertEquals("OUTSIDE_VIEWPORT",j.peer.lastActionResult!!.reason)
            assertEquals("STALE_COMMAND_SEQUENCE",j.raw(BrowserActionMessage(rejected!!,current,BrowserAction.ActivateAt(g.getDouble("x").toFloat(),g.getDouble("y").toFloat()),rejected!!.split(':')[2].toLong())).reason)
            Log.i(TAG,"PROTOCOL_NEGATIVES replay=true outsideViewport=true rejectedOrdinalConsumed=true oldEpoch=true")

            j.check("scroll_start");j.aimNative(R.id.rg_recenter)
            val scale=j.geometry().getDouble("scale")
            result=j.peer.lastActionResult;val positiveTap=j.pad(292);val positiveTrace=j.dispatched(positiveTap,"ScrollBy+160")
            j.result(result,"ScrollBy+160")
            j.await("positive CSS effect",1_000) { abs(j.geometry().getDouble("cssY")-160/scale)<=1 && j.peer.canAct() }
            val positiveMs=SystemClock.uptimeMillis()-positiveTrace.confirmedAt;assertTrue(positiveMs<=1_000)
            Log.i(TAG,"SCROLL_EFFECT sign=positive nativeCommand=160 css=${j.geometry().getDouble("cssY")} scale=$scale effectMs=$positiveMs")
            j.check("positive_scroll")
            result=j.peer.lastActionResult;val negativeTap=j.pad(293);val negativeTrace=j.dispatched(negativeTap,"ScrollBy-160")
            j.result(result,"ScrollBy-160")
            j.await("negative CSS effect",1_000) { abs(j.geometry().getDouble("cssY"))<=1 && j.peer.canAct() }
            val negativeMs=SystemClock.uptimeMillis()-negativeTrace.confirmedAt;assertTrue(negativeMs<=1_000)
            Log.i(TAG,"SCROLL_EFFECT sign=negative nativeCommand=-160 css=${j.geometry().getDouble("cssY")} effectMs=$negativeMs")
            j.check("negative_scroll")
            j.confirmWindow() // Distinct new tap after the declared swipe/composite suppression window.

            j.aimPage(next=true);result=j.peer.lastActionResult;var navTrace=j.dispatched(j.pad(),"ActivateAt-link-B");j.result(result,"ActivateAt-link-B")
            j.await("B navigation",5_000) { j.title()=="T03 B" && j.peer.canAct() };j.live("B");j.effect("ActivateAt-link-B",navTrace)
            result=j.peer.lastActionResult;navTrace=j.native(R.id.rg_back,"Back");j.result(result,"Back")
            j.await("Back A",5_000) { j.peer.browserState()?.url?.contains("/control.html?")==true && j.peer.canAct() };j.live("A");j.effect("Back",navTrace)
            result=j.peer.lastActionResult;navTrace=j.native(R.id.rg_forward,"Forward");j.result(result,"Forward")
            j.await("Forward B",5_000) { j.title()=="T03 B" && j.peer.canAct() };j.live("B");j.effect("Forward",navTrace)
            val reloadDoc=j.peer.browserState()!!.context.documentId
            result=j.peer.lastActionResult;navTrace=j.native(R.id.rg_reload,"Reload");j.result(result,"Reload")
            j.await("Reload new B document",5_000) { j.peer.browserState()?.context?.documentId!=reloadDoc && j.title()=="T03 B" && j.peer.canAct() };j.live("B");j.effect("Reload",navTrace)

            j.aimPage();before=j.cursor;result=j.peer.lastActionResult
            val navigationDoc=j.peer.browserState()!!.context.documentId;val navigationTap=j.pad();j.request("navigation")
            val navChanged=j.contextDuringTap(navigationTap,"navigation") { it.context.documentId!=navigationDoc }
            j.await("Phone navigation zero-effect oracle",8_000) { j.title()=="I8 ACK navigation" && j.peer.canAct() }
            j.confirmWindow();assertEquals(before,j.cursor);assertSame(result,j.peer.lastActionResult)
            Log.i(TAG,"NAVIGATION_PENDING changedMs=$navChanged commandDelta=0")

            val stalePagePoint=j.aimPage();j.aimNative(R.id.rg_handoff)
            val previousRequest=j.peer.lastHandoffRequest;val takeoverTap=j.pad();j.request("takeover")
            val takeoverChanged=j.contextDuringTap(takeoverTap,"takeover") { it.owner==ControlOwner.PHONE }
            j.await("Phone takeover oracle",8_000) { j.title()=="I8 ACK takeover" }
            j.confirmWindow();assertSame(previousRequest,j.peer.lastHandoffRequest);assertEquals(before,j.cursor)
            j.aim(stalePagePoint);j.pad();j.confirmWindow();j.aimNative(R.id.rg_reload);j.pad();j.confirmWindow();j.pad(292);j.confirmWindow()
            assertEquals(ControlOwner.PHONE,j.peer.browserState()!!.owner);assertEquals(before,j.cursor);assertSame(previousRequest,j.peer.lastHandoffRequest)
            j.check("phone_owned_again")
            Log.i(TAG,"PHONE_TAKEOVER_PENDING changedMs=$takeoverChanged staleConsent=0 phoneOwnedPageNav=0")

            j.handoff(ControlOwner.RG,"B");j.aimPage();result=j.peer.lastActionResult
            j.main { beforeClickHash=j.frameHash() }
            val finishTap=j.pad();val finishTrace=j.dispatched(finishTap,"ActivateAt-B");j.result(result,"ActivateAt-B")
            j.await("actual visible final B effect",1_000) { j.title()=="T03 done" && j.peer.canAct() && j.frameHash()!=beforeClickHash }
            val finishMs=SystemClock.uptimeMillis()-finishTrace.confirmedAt;assertTrue(finishMs<=1_000)
            Log.i(TAG,"ACTIVATE_EFFECT page=B effectMs=$finishMs fullGestureMs=${SystemClock.uptimeMillis()-finishTap.down}")
            j.request("actions_done");j.await("independent complete Phone ledger",8_000) { j.title()=="I8 EFFECTS VERIFIED" }
            j.handoff(ControlOwner.PHONE,"B");j.await("final continuity proof",8_000) { j.title()=="I8 FINAL RETURN VERIFIED" }
            j.handoff(ControlOwner.RG,"B");j.aimPage();before=j.cursor;result=j.peer.lastActionResult
            j.pad();j.main { j.peer.pause() };j.quiescent();j.confirmWindow()
            assertEquals(before,j.cursor);assertSame(result,j.peer.lastActionResult)
            val frames=j.peer.displayedFrames
            var centerX=0f
            j.native(R.id.rg_recenter,"Recenter-link-down");j.main {
                j.source.adoptCurrentReference();centerX=j.pointer.position.x
                j.source.aim(it,InputPoint(centerX+20,j.pointer.position.y))
            }
            j.await("local draw while actual frames withheld",100) { j.pointer.position.x>centerX+1 && j.pointer.lastDrawElapsedNs>=j.pointer.lastDrawSampleReceiptNs }
            j.main {
                val ms=(j.pointer.lastDrawElapsedNs-j.pointer.lastDrawSampleReceiptNs)/1e6
                assertTrue(ms<=100);assertEquals(frames,j.peer.displayedFrames)
                Log.i(TAG,"WITHHELD_FRAMES localDrawMs=$ms newFrames=0 recenterLocal=true linkClosed=true")
            }
            j.request("final_disconnected");waitHost("done")
            assertEquals(before,j.cursor)
            Log.i(TAG,"RG_I8_PASS realTls=true pointerActions=true allNegatives=true")
        } catch(failure:Throwable) { primary=failure;Log.e(TAG,"PRIMARY_FAILURE",failure);throw failure }
        finally {
            val errors=listOf<()->Unit>({scenario.close()},{ack.delete();assertFalse(ack.exists())}).mapNotNull { runCatching(it).exceptionOrNull() }
            if(primary!=null) errors.forEach { primary!!.addSuppressed(it) } else if(errors.isNotEmpty()) throw errors.first()
        }
    }
    companion object { private const val TAG="EyeBrowseI8" }
}
