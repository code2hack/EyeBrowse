package com.code2hack.eyebrowse.rg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Requires a new exclusive grant. Raw pose replay is application evidence, never physical head evidence. */
@RunWith(AndroidJUnit4::class)
class EdgeScrollInstrumentedTest {
    @get:Rule val testName=TestName()
    private val evidence=JSONArray()
    private fun await(label: String, timeout: Long=10_000, condition: () -> Boolean) {
        val end=SystemClock.elapsedRealtime()+timeout
        do { if(condition()) return;SystemClock.sleep(5) } while(SystemClock.elapsedRealtime()<end)
        fail(label)
    }
    private fun scene(body: (ActivityScenario<LocalBrowserActivity>,RawPoseReplay) -> Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val metadata=context.getSharedPreferences("LocalBrowserActivity",Context.MODE_PRIVATE)
        val previousMetadata=metadata.getString("local.tabs",null)
        val preferences=context.getSharedPreferences("local-input-settings",Context.MODE_PRIVATE)
        val keys=listOf("pointer.sensitivity","edge.speed")
        val previous=keys.associateWith { preferences.getString(it,null) }
        val source=RawPoseReplay()
        val arguments=InstrumentationRegistry.getArguments()
        val invocationId=arguments.getString("evidenceRunId")
        val candidateHead=arguments.getString("candidateHead")
        val startedNs=SystemClock.elapsedRealtimeNanos()
        var scene: ActivityScenario<LocalBrowserActivity>?=null
        var failure: Throwable?=null
        var bodyCompleted=false
        val cleanup=JSONObject()
        try {
            assertFalse("fresh invocation evidence binding",invocationId.isNullOrBlank())
            assertTrue("exact source evidence binding",candidateHead?.matches(Regex("[0-9a-f]{40}"))==true)
            val active=ActivityScenario.launch(LocalBrowserActivity::class.java)
            scene=active
            active.onActivity { while(it.tabs.count>1) it.closeTab();it.closeTab();it.pointer.stop();it.pointer.replaceSourceForTest(source) }
            reset(active,source)
            body(active,source)
            bodyCompleted=true
        } catch(t: Throwable) { failure=t }
        finally {
            for((name,action) in listOf<Pair<String,() -> Unit>>(
                "activityAndRawSource" to { scene?.close();assertFalse(source.registered) },
                "localTabsRestored" to { assertTrue(metadata.edit().apply {
                    if(previousMetadata==null) remove("local.tabs") else putString("local.tabs",previousMetadata)
                }.commit()) },
                "settingsRestored" to { assertTrue(preferences.edit().apply { previous.forEach { (key,value) ->
                    if(value==null) remove(key) else putString(key,value)
                } }.commit()) },
            )) try { action();cleanup.put(name,true) } catch(t: Throwable) {
                cleanup.put(name,false)
                if(failure==null) failure=t else failure.addSuppressed(t)
            }
            val report=JSONObject().put("class",javaClass.name).put("method",testName.methodName)
                .put("invocationId",invocationId).put("candidateHead",candidateHead)
                .put("processPid",android.os.Process.myPid()).put("processUid",android.os.Process.myUid())
                .put("startedNs",startedNs).put("finishedNs",SystemClock.elapsedRealtimeNanos())
                .put("bodyCompleted",bodyCompleted).put("cleanup",cleanup)
                .put("failureClass",failure?.javaClass?.name ?: JSONObject.NULL)
                .put("suppressedFailureClasses",JSONArray(failure?.suppressed?.map { it.javaClass.name } ?: emptyList<String>()))
                .put("observations",evidence)
            try {
                // Distinct, durable originals survive the next method and retain partial failure evidence.
                context.openFileOutput("edge-scroll-${testName.methodName}-observations.json",0).use {
                    it.write(report.toString().toByteArray());it.fd.sync()
                }
            } catch(t: Throwable) { if(failure==null) failure=t else failure.addSuppressed(t) }
        }
        failure?.let { throw it }
    }
    private fun reset(scene: ActivityScenario<LocalBrowserActivity>, source: RawPoseReplay) {
        scene.onActivity { it.pointer.stop();source.orientationDegrees(0.0,0.0);source.flowing=true;it.pointer.start() }
        await("automatic neutral acquisition, no recenter") {
            var ready=false;scene.onActivity { ready=it.hasWindowFocus() && it.pointer.position.available &&
                abs(it.pointer.position.x-240)<.01 && abs(it.pointer.position.y-320)<.01 };ready
        }
    }
    private fun tap(scene: ActivityScenario<LocalBrowserActivity>, stopTrigger: String?=null, view: (LocalBrowserActivity) -> View) {
        await("current laid-out native target") {
            var ready=false
            scene.onActivity { val child=view(it)
                ready=!it.root.isLayoutRequested && child.isShown && child.isEnabled && child.isLaidOut && child.width>0 && child.height>0
            }
            ready
        }
        scene.onActivity {
            val child=view(it);assertTrue(child.isShown && child.isEnabled && child.isLaidOut && child.width>0 && child.height>0)
            val xy=IntArray(2);val origin=IntArray(2);child.getLocationOnScreen(xy);it.root.getLocationOnScreen(origin)
            val dispatchNs=if(stopTrigger!=null) SystemClock.elapsedRealtimeNanos() else 0L
            assertTrue(it.input.activate(InputPoint(xy[0]-origin[0]+child.width/2f,xy[1]-origin[1]+child.height/2f)))
            if(stopTrigger!=null) {
                val returnedNs=SystemClock.elapsedRealtimeNanos()
                evidence.put(edgeObservation(it).put("case","native-stop-trigger")
                    .put("trigger",stopTrigger).put("dispatchNs",dispatchNs).put("dispatchReturnedNs",returnedNs))
            }
        }
    }
    private fun control(scene: ActivityScenario<LocalBrowserActivity>, name: String, stopTrigger: String?=null)=tap(scene,stopTrigger) { it.controls.getValue("hud.$name") }
    private fun tagged(scene: ActivityScenario<LocalBrowserActivity>, name: String, stopTrigger: String?=null)=tap(scene,stopTrigger) { it.root.findViewWithTag(name) }
    private fun key(scene: ActivityScenario<LocalBrowserActivity>, key: RgKeyboardKeys.Key) {
        await("current native key layout") { var ready=false;scene.onActivity { ready=it.keyboard.isShown && !it.root.isLayoutRequested };ready }
        var exists=false
        scene.onActivity { exists=key in it.keyButtons }
        if(!exists && key is RgKeyboardKeys.Key.Character) {
            tap(scene) { it.keyButtons.getValue(RgKeyboardKeys.Key.Command.SYMBOLS) }
        }
        tap(scene) { it.keyButtons.getValue(key) }
    }
    private fun open(scene: ActivityScenario<LocalBrowserActivity>, route: String="keyboard.html") {
        val base=checkNotNull(InstrumentationRegistry.getArguments().getString("fixtureBaseUrl"))
        control(scene,"address");scene.onActivity { it.address.selectAll() }
        for(c in base.trimEnd('/')+"/"+route) key(scene,RgKeyboardKeys.Key.Character(c.toString()))
        key(scene,RgKeyboardKeys.Key.Command.ENTER)
        await("actual visible local fixture") { var ready=false;scene.onActivity {
            ready=it.tabs.current.session.state.phase==LocalBrowserSession.Phase.READY && it.tabs.current.session.page?.isShown==true
        };ready }
    }
    private fun js(scene: ActivityScenario<LocalBrowserActivity>, expression: String): String {
        val done=CountDownLatch(1);var value=""
        scene.onActivity { checkNotNull(it.tabs.current.session.page).evaluateJavascript(expression) { result -> value=result;done.countDown() } }
        assertTrue("read-only fixture observation",done.await(3,TimeUnit.SECONDS));return value
    }
    private fun y(scene: ActivityScenario<LocalBrowserActivity>): Int {
        var value=0;scene.onActivity { value=checkNotNull(it.tabs.current.session.page).scrollY };return value
    }
    /** Public/current native state only: no field text, DOM mutation or production probe. */
    private fun edgeObservation(activity: LocalBrowserActivity): JSONObject {
        val now=SystemClock.elapsedRealtimeNanos();val pointer=activity.pointer;val position=pointer.position
        val page=activity.tabs.current.session.page
        return JSONObject().put("sampleNs",now).put("pointerX",position.x).put("pointerY",position.y)
            .put("available",position.available).put("sampleReceiptNs",pointer.lastSampleReceiptNs)
            .put("expiresAtNs",pointer.expiresAtNs).put("sourceRegistered",pointer.sourceRegistered)
            .put("edgeScrollRunning",activity.edgeScrollRunning).put("utility",activity.utility.name)
            .put("selectedTab",activity.tabs.selectedIndex).put("tabCount",activity.tabs.count)
            .put("phase",activity.tabs.current.session.state.phase.name).put("pagePresent",page!=null)
            .put("pageShown",page?.isShown ?: false).put("nativeY",page?.scrollY ?: JSONObject.NULL)
            .put("canScrollUp",page?.canScrollVertically(-1) ?: JSONObject.NULL)
            .put("canScrollDown",page?.canScrollVertically(1) ?: JSONObject.NULL)
            .put("pageWidth",page?.width ?: JSONObject.NULL).put("pageHeight",page?.height ?: JSONObject.NULL)
            .put("pageFocused",page?.hasFocus() ?: false).put("addressFocused",activity.address.hasFocus())
            .put("keyboardShown",activity.keyboard.isShown).put("windowFocused",activity.hasWindowFocus())
            .put("layoutRequested",activity.root.isLayoutRequested).put("finishing",activity.isFinishing)
            .put("speed",activity.inputSettings.speed.pixelsPerSecond).put("sensitivity",activity.inputSettings.sensitivity.gain)
    }
    private fun stopped(scene: ActivityScenario<LocalBrowserActivity>) {
        val scenarioState=scene.state.name
        val start=SystemClock.elapsedRealtimeNanos()
        var end=start;var polls=0;var first: JSONObject?=null;var last: JSONObject?=null
        val observation=JSONObject().put("case","callback-stop").put("startNs",start).put("scenarioState",scenarioState)
        evidence.put(observation)
        try {
            await("ordinary callback cancelled",100) {
                var stopped=false;val callNs=SystemClock.elapsedRealtimeNanos()
                scene.onActivity { stopped=!it.edgeScrollRunning;last=edgeObservation(it).put("callNs",callNs) }
                last?.put("returnedNs",SystemClock.elapsedRealtimeNanos())
                if(first==null) first=last
                polls++;observation.put("polls",polls).put("first",first).put("last",last)
                stopped
            }
        } finally {
            end=SystemClock.elapsedRealtimeNanos()
            // Record even a timeout or over-budget observation before the original assertion.
            observation.put("observedNs",end).put("elapsedNs",end-start).put("polls",polls)
                .put("first",first ?: JSONObject.NULL).put("last",last ?: JSONObject.NULL)
        }
        assertTrue(end-start<=100_000_000)
    }
    private fun topEndpoint(scene: ActivityScenario<LocalBrowserActivity>, speed: LocalInputSettings.Speed) {
        val scenarioState=scene.state.name
        val start=SystemClock.elapsedRealtimeNanos()
        var first: JSONObject?=null;var last: JSONObject?=null;var polls=0;var reached=false;var checkedY: Int?=null
        val observation=JSONObject().put("case","top-endpoint").put("speed",speed.pixelsPerSecond)
            .put("startNs",start).put("scenarioState",scenarioState)
        evidence.put(observation)
        try {
            await("top endpoint") {
                var top=false
                scene.onActivity {
                    top=it.pointer.position.y==8f && it.tabs.current.session.page?.canScrollVertically(-1)==false
                    last=edgeObservation(it)
                }
                if(first==null) first=last
                checkedY=if(top) y(scene) else null
                reached=top && checkedY==0;polls++
                observation.put("polls",polls).put("reached",reached).put("checkedY",checkedY ?: JSONObject.NULL)
                    .put("first",first).put("last",last)
                reached
            }
        } finally {
            observation.put("finishedNs",SystemClock.elapsedRealtimeNanos()).put("polls",polls)
                .put("reached",reached).put("checkedY",checkedY ?: JSONObject.NULL)
                .put("first",first ?: JSONObject.NULL).put("last",last ?: JSONObject.NULL)
        }
    }
    private fun pageOracle(scene: ActivityScenario<LocalBrowserActivity>): JSONObject {
        val dom=JSONArray(js(scene,"[scrollY,innerWidth,innerHeight,document.scrollingElement.scrollHeight]"))
        var nativeY=0;var width=0
        scene.onActivity {val page=checkNotNull(it.tabs.current.session.page);nativeY=page.scrollY;width=page.width}
        val scale=width/dom.getDouble(1)
        assertEquals("independent actual DOM/native scroll agreement",dom.getDouble(0)*scale,nativeY.toDouble(),8.0)
        return JSONObject().put("nativeY",nativeY).put("dom",dom).put("nativePxPerCssPx",scale)
    }
    private fun settings(scene: ActivityScenario<LocalBrowserActivity>, tag: String) {
        control(scene,"more");tagged(scene,"menu.3");tagged(scene,tag);tagged(scene,"utility.done")
    }
    private fun capture(scene: ActivityScenario<LocalBrowserActivity>, name: String) {
        val visual=CountDownLatch(1)
        scene.onActivity { val page=it.tabs.current.session.page
            if(page?.isShown==true) page.postVisualStateCallback(0,object: WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { visual.countDown() }
            }) else visual.countDown()
        }
        assertTrue(visual.await(3,TimeUnit.SECONDS))
        val frame=CountDownLatch(1);val copy=CountDownLatch(1);val bitmap=Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888)
        var commit=0L;var result=-1;val data=JSONObject().put("name",name)
        try {
            scene.onActivity {
                val xy=IntArray(2);it.root.getLocationOnScreen(xy)
                assertEquals(0,xy[0]);assertEquals(0,xy[1]);assertEquals(480,it.root.width);assertEquals(640,it.root.height)
                assertTrue(it.hasWindowFocus());it.root.viewTreeObserver.registerFrameCommitCallback {commit=SystemClock.elapsedRealtimeNanos();frame.countDown()};it.root.invalidate()
            }
            assertTrue(frame.await(3,TimeUnit.SECONDS))
            scene.onActivity {
                data.put("utility",it.utility.name).put("pointerX",it.pointer.position.x).put("pointerY",it.pointer.position.y)
                    .put("available",it.pointer.position.available).put("sourceReceiptNs",it.pointer.lastSampleReceiptNs)
                    .put("frameCommitNs",commit).put("copyRequestNs",SystemClock.elapsedRealtimeNanos())
                PixelCopy.request(it.window,bitmap,{ code ->result=code;data.put("copyCompleteNs",SystemClock.elapsedRealtimeNanos());copy.countDown()},Handler(Looper.getMainLooper()))
            }
            assertTrue(copy.await(3,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result)
            scene.onActivity {
                it.openFileOutput("edge-scroll-$name.png",0).use { stream -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)) }
                it.openFileOutput("edge-scroll-$name.json",0).use { stream -> stream.write(data.toString().toByteArray()) }
            }
            if(name=="settings") for((x,y) in listOf(2 to 100,478 to 300,10 to 630)) assertEquals(Color.BLACK,bitmap.getPixel(x,y))
        } finally { bitmap.recycle() }
    }

    @Test fun allBoundariesDiscardOvershootWithPromptSlowInwardDraw()=scene { scene,source ->
        settings(scene,"settings.sensitivity.standard")
        for(h in -1..1) for(v in -1..1) {
            if(h==0 && v==0) continue
            for(extra in listOf(5.0,20.0,45.0)) for(hold in listOf(100L,500L,1000L)) {
                reset(scene,source)
                val yaw=h*(30.3+extra);val pitch=v*(22.3+extra/2)
                scene.onActivity { source.orientationDegrees(yaw,pitch) }
                await("clamped edge h=$h v=$v excess=$extra") { var edge=false;scene.onActivity {
                    val p=it.pointer.position;assertTrue(p.x in 8f..472f && p.y in 8f..632f)
                    edge=(h==0 || abs(p.x-if(h<0) 8f else 472f)<.001) && (v==0 || abs(p.y-if(v<0) 8f else 632f)<.001)
                };edge }
                SystemClock.sleep(hold)
                if(h==0 && v == -1 && extra==5.0 && hold==100L) capture(scene,"pointer-top")
                if(h==1 && v==1 && extra==45.0 && hold==1000L) capture(scene,"pointer-corner")
                var receiptBefore=0L;var start=0L;var visible=0L;var receipt=0L
                scene.onActivity { receiptBefore=it.pointer.lastSampleReceiptNs;start=SystemClock.elapsedRealtimeNanos();source.orientationDegrees(yaw-h*.05,pitch-v*.05) }
                await("first slow inward draw within 100ms",100) { var inward=false;scene.onActivity {
                    val p=it.pointer.lastDrawPosition
                    inward=it.pointer.lastDrawSampleReceiptNs>receiptBefore &&
                        (h==0 || if(h<0) p.x>8 else p.x<472) && (v==0 || if(v<0) p.y>8 else p.y<632)
                    if(inward) {visible=it.pointer.lastDrawElapsedNs;receipt=it.pointer.lastDrawSampleReceiptNs}
                };inward }
                assertTrue(visible-receipt in 0..100_000_000);assertTrue(visible-start in 0..100_000_000)
                evidence.put(JSONObject().put("case","boundary").put("h",h).put("v",v).put("excessDegrees",extra)
                    .put("holdMs",hold).put("inwardDegrees",.05).put("inputNs",start).put("sampleReceiptNs",receipt).put("drawNs",visible))
            }
        }
        scene.onActivity { source.flowing=false }
        await("tracking expires") {var unavailable=false;scene.onActivity {unavailable=!it.pointer.position.available};unavailable}
        capture(scene,"tracking-unavailable")
    }

    @Test fun settingsPersistAndActualPageUsesEveryDeclaredRateAndGain()=scene { scene,source ->
        settings(scene,"settings.sensitivity.standard");open(scene)
        val identity=js(scene,"fixtureIdentity")
        for(speed in LocalInputSettings.Speed.entries) {
            settings(scene,"settings.speed.${speed.name.lowercase()}")
            control(scene,"more");tagged(scene,"menu.3")
            scene.onActivity { assertEquals(speed,it.inputSettings.speed);assertTrue(it.root.findViewWithTag<View>("settings.speed.${speed.name.lowercase()}").isSelected) }
            if(speed==LocalInputSettings.Speed.STANDARD) capture(scene,"settings")
            tagged(scene,"utility.done")
            // Start at the real top endpoint through raw poses; no DOM scroll assignment.
            reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,-50.0)}
            topEndpoint(scene,speed);SystemClock.sleep(80)
            reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)}
            await("actual bottom-edge page movement") {y(scene)>10}
            val before=y(scene);val time=SystemClock.elapsedRealtimeNanos();SystemClock.sleep(400)
            val after=y(scene);val elapsed=SystemClock.elapsedRealtimeNanos()-time
            val observed=(after-before)*1e9/elapsed
            assertTrue("native rate ${speed.pixelsPerSecond}: $observed",abs(observed-speed.pixelsPerSecond)<=speed.pixelsPerSecond*.12+4)
            scene.onActivity {source.orientationDegrees(0.0,49.95)};stopped(scene)
            val dom=pageOracle(scene)
            evidence.put(JSONObject().put("case","rate").put("speed",speed.pixelsPerSecond).put("beforeY",before).put("afterY",after).put("elapsedNs",elapsed).put("observedNativePxPerSecond",observed).put("domObservation",dom))
        }
        val distances=mutableListOf<Float>()
        for(gain in LocalInputSettings.Sensitivity.entries) {
            settings(scene,"settings.sensitivity.${gain.name.lowercase()}");reset(scene,source)
            scene.onActivity {source.orientationDegrees(10.0,0.0)}
            SystemClock.sleep(500)
            scene.onActivity {distances+=it.pointer.position.x-240;assertEquals(gain,it.inputSettings.sensitivity)}
        }
        assertEquals(.75,distances[0]/distances[1].toDouble(),.015);assertEquals(1.25,distances[2]/distances[1].toDouble(),.015)
        // Actual Activity recreation rereads durable settings; metadata recovery never auto-navigates.
        scene.recreate();scene.onActivity {assertEquals(LocalInputSettings.Sensitivity.HIGH,it.inputSettings.sensitivity);assertEquals(LocalInputSettings.Speed.FAST,it.inputSettings.speed);it.pointer.stop()}
        assertTrue(identity.isNotEmpty())
        evidence.put(JSONObject().put("case","gains").put("distances",JSONArray(distances)).put("durableRecreation",true))
    }

    @Test fun fullScreenKeyboardEdgesAndOrdinaryCancellationPreserveNativeEditing()=scene { scene,source ->
        settings(scene,"settings.speed.standard");settings(scene,"settings.sensitivity.standard");open(scene)
        val rect=JSONArray(js(scene,"(()=>{const r=document.querySelector('#text').getBoundingClientRect();return [r.x,r.y,r.width,r.height,innerWidth]})()"))
        scene.onActivity {
            val page=checkNotNull(it.tabs.current.session.page);val scale=page.width/rect.getDouble(4)
            assertTrue(it.input.activate(InputPoint(((rect.getDouble(0)+rect.getDouble(2)/2)*scale).toFloat(),48+((rect.getDouble(1)+rect.getDouble(3)/2)*scale).toFloat())))
        }
        await("actual field focus and keyboard") {var ready=false;scene.onActivity {ready=it.keyboard.isShown && it.tabs.current.session.page?.hasFocus()==true && !it.root.isLayoutRequested};ready}
        key(scene,RgKeyboardKeys.Key.Character("a"));assertEquals("\"a\"",js(scene,"document.querySelector('#text').value"))
        val identity=js(scene,"fixtureIdentity")
        reset(scene,source)
        // Physical screen divider at y440 is an interior point, so it cannot start page scrolling.
        val dividerPitch=.3+(440.0-320)*44/624
        scene.onActivity {source.orientationDegrees(0.0,dividerPitch)};SystemClock.sleep(500);stopped(scene)
        val dividerY=y(scene);SystemClock.sleep(150);assertEquals(dividerY,y(scene))
        reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)}
        await("bottom screen edge scrolls page under fixed keyboard") {y(scene)>dividerY+20}
        scene.onActivity {
            val xy=IntArray(2);it.keyboard.getLocationOnScreen(xy)
            assertEquals(440,xy[1]);assertEquals(200,it.keyboard.height);assertTrue(it.tabs.current.session.page?.hasFocus()==true)
            assertEquals(632f,it.pointer.position.y,.001f)
        }
        capture(scene,"keyboard-bottom")
        scene.onActivity {source.orientationDegrees(0.0,49.95)};stopped(scene)
        val departure=y(scene);SystemClock.sleep(150);assertEquals(departure,y(scene))
        key(scene,RgKeyboardKeys.Key.Character("b"));assertEquals("\"ab\"",js(scene,"document.querySelector('#text').value"));assertEquals("0",js(scene,"fixtureSubmits"))
        reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)};await("scroll resumes") {y(scene)>departure+5}
        scene.onActivity {source.flowing=false}
        await("no effect at sample age cutoff") {var stale=false;scene.onActivity {stale=SystemClock.elapsedRealtimeNanos()>=it.pointer.expiresAtNs};stale}
        val staleY=y(scene);SystemClock.sleep(150);assertEquals(staleY,y(scene));stopped(scene)
        reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)}
        control(scene,"more");stopped(scene);val covered=y(scene);SystemClock.sleep(150);assertEquals(covered,y(scene))
        tagged(scene,"menu.3");SystemClock.sleep(150);assertEquals("Settings edge cannot scroll covered page",covered,y(scene));tagged(scene,"utility.done")
        scene.moveToState(Lifecycle.State.CREATED);stopped(scene);assertFalse(source.registered)
        scene.moveToState(Lifecycle.State.RESUMED)
        assertEquals(identity,js(scene,"fixtureIdentity"));assertEquals("\"ab\"",js(scene,"document.querySelector('#text').value"))
        // Current tab changes and navigation use ordinary callbacks, with no stale-tick protocol.
        reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)}
        control(scene,"more","More before New Tab");tagged(scene,"menu.0","New Tab");stopped(scene)
        scene.onActivity {it.selectTab(0);assertFalse("tab switch cancels synchronously",it.edgeScrollRunning)}
        control(scene,"close_tab");stopped(scene)
        open(scene)
        control(scene,"refresh");stopped(scene)
        await("new document after actual Refresh") {js(scene,"fixtureIdentity")!=identity}
        reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)}
        await("actual bottom endpoint") {var bottom=false;scene.onActivity {bottom=it.pointer.position.y==632f && it.tabs.current.session.page?.canScrollVertically(1)==false};bottom}
        val endpoint=pageOracle(scene);val endpointY=y(scene)
        scene.onActivity {source.orientationDegrees(0.0,65.0)};SystemClock.sleep(500)
        assertEquals("endpoint/outward hold has no scroll debt",endpointY,y(scene))
        scene.onActivity {source.orientationDegrees(0.0,64.95)};stopped(scene)
        assertEquals(endpointY,y(scene))
        scene.onActivity {source.orientationDegrees(0.0,-50.0)}
        await("opposite edge scrolls away from endpoint") {y(scene)<endpointY-10}
        capture(scene,"scrolling-top")
        reset(scene,source);scene.onActivity {source.orientationDegrees(50.0,0.0)}
        await("lateral-only saturation") {var lateral=false;scene.onActivity {lateral=it.pointer.position.x==472f};lateral};stopped(scene)
        val lateralY=y(scene);SystemClock.sleep(150);assertEquals(lateralY,y(scene))
        evidence.put(JSONObject().put("case","endpoint-and-lateral").put("endpoint",endpoint).put("lateralY",lateralY))
        evidence.put(JSONObject().put("case","keyboard-and-stops").put("dividerY",dividerY).put("departureY",departure).put("staleY",staleY).put("coveredY",covered))
    }
}
