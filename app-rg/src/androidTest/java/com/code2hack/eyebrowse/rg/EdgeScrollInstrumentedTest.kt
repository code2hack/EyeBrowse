package com.code2hack.eyebrowse.rg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.ScrollView
import android.widget.TextView
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
    private fun scene(legacyPointer: String?=null, legacyScroll: String?=null, body: (ActivityScenario<LocalBrowserActivity>,RawPoseReplay) -> Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val metadata=localBrowserTestPreferences(context)
        val previousMetadata=metadata.getString("local.tabs",null)
        val preferences=context.getSharedPreferences("local-input-settings",Context.MODE_PRIVATE)
        val keys=listOf("pointer.sensitivity","edge.speed","pointer.tenths","edge.pixels-per-second")
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
            if(legacyPointer!=null) assertTrue(preferences.edit().remove("pointer.tenths").remove("edge.pixels-per-second")
                .putString("pointer.sensitivity",legacyPointer).putString("edge.speed",legacyScroll).commit())
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
    private fun nativeTap(activity: LocalBrowserActivity, child: View) {
        assertTrue(child.isShown && child.isEnabled && child.isLaidOut && child.width>0 && child.height>0)
        val xy=IntArray(2);val origin=IntArray(2);child.getLocationOnScreen(xy);activity.root.getLocationOnScreen(origin)
        assertTrue(activity.input.activate(InputPoint(xy[0]-origin[0]+child.width/2f,xy[1]-origin[1]+child.height/2f)))
    }
    private fun tap(scene: ActivityScenario<LocalBrowserActivity>, view: (LocalBrowserActivity) -> View) {
        await("current laid-out native target") {
            var ready=false
            scene.onActivity { val child=view(it)
                ready=!it.root.isLayoutRequested && child.isShown && child.isEnabled && child.isLaidOut && child.width>0 && child.height>0
            }
            ready
        }
        scene.onActivity { nativeTap(it,view(it)) }
    }
    private fun control(scene: ActivityScenario<LocalBrowserActivity>, name: String)=tap(scene) { it.controls.getValue("hud.$name") }
    private fun tagged(scene: ActivityScenario<LocalBrowserActivity>, name: String)=tap(scene) { it.root.findViewWithTag(name) }
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
        try { await("actual visible local fixture") { var ready=false;scene.onActivity {
            ready=it.tabs.current.session.state.phase==LocalBrowserSession.Phase.READY && it.tabs.current.session.page?.isShown==true
        };ready } } catch(failure: Throwable) {
            try { scene.onActivity {
                val expected=base.trimEnd('/')+"/"+route
                val runtime=com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime.get(it)
                evidence.put(edgeObservation(it).put("kind","owned-page-ready-failure")
                    .put("committedOwnedDestination",it.tabs.current.committedUrl==expected)
                    .put("sessionOwnedDestination",it.tabs.current.session.state.url==expected)
                    .put("pageError",it.tabs.current.session.state.error ?: JSONObject.NULL)
                    .put("coreStatus",runtime.status).put("corePort",runtime.proxyPort ?: JSONObject.NULL)
                    .put("webViewPort",runtime.webViewPort ?: JSONObject.NULL))
            } } catch(observation: Throwable) { failure.addSuppressed(observation) }
            // Read status only from the same owned destination after failure; never replay the page.
            val expected=base.trimEnd('/')+"/"+route
            val runtime=com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime.get(
                InstrumentationRegistry.getInstrumentation().targetContext)
            for(direct in listOf(false,true)) {
                val probe=JSONObject().put("kind","owned-page-failure-http-probe").put("direct",direct)
                    .put("corePortBefore",runtime.proxyPort ?: JSONObject.NULL)
                evidence.put(probe)
                try {
                    val url=java.net.URL(expected)
                    val connection=if(direct) url.openConnection(java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
                        else runtime.openConnection(url)
                    try { connection.connectTimeout=2000;connection.readTimeout=2000
                        probe.put("httpStatus",connection.responseCode)
                    } finally { connection.disconnect() }
                } catch(diagnostic: Exception) { probe.put("exceptionClass",diagnostic.javaClass.name) }
                probe.put("corePortAfter",runtime.proxyPort ?: JSONObject.NULL)
            }
            throw failure
        }
    }
    private fun js(scene: ActivityScenario<LocalBrowserActivity>, expression: String): String {
        val done=CountDownLatch(1);var value=""
        scene.onActivity { checkNotNull(it.tabs.current.session.page).evaluateJavascript(expression) { result -> value=result;done.countDown() } }
        assertTrue("read-only fixture observation",done.await(3,TimeUnit.SECONDS));return value
    }
    private fun y(scene: ActivityScenario<LocalBrowserActivity>): Int {
        var value=0;scene.onActivity { value=checkNotNull(it.tabs.current.session.page).scrollY };return value
    }

    private fun nestedFixture(scene: ActivityScenario<LocalBrowserActivity>) {
        val base=checkNotNull(InstrumentationRegistry.getArguments().getString("nestedFixtureBaseUrl"))
        val url=base.trimEnd('/')+"/keyboard.html"
        scene.onActivity { assertTrue("owned nested navigation admitted",it.tabs.current.session.open(url).accepted()) }
        try {
            await("owned nested fixture ready") { var ready=false;scene.onActivity {
                ready=it.tabs.current.session.state.phase==LocalBrowserSession.Phase.READY &&
                    it.tabs.current.session.page?.isShown==true
            };ready }
        } catch(failure: Throwable) {
            try { scene.onActivity {
                evidence.put(edgeObservation(it).put("kind","owned-nested-readiness-failure")
                    .put("sessionOwnedDestination",it.tabs.current.session.state.url==url)
                    .put("pageOwnedDestination",it.tabs.current.session.page?.url==url))
            } } catch(observation: Throwable) { failure.addSuppressed(observation) }
            throw failure
        }
    }
    private fun nestedY(scene: ActivityScenario<LocalBrowserActivity>, id: String): Int =
        js(scene,"document.getElementById(${JSONObject.quote(id)}).scrollTop").toDouble().toInt()
    private fun nestedPoint(scene: ActivityScenario<LocalBrowserActivity>, id: String): InputPoint {
        val position=JSONArray(js(scene,"""(()=>{const r=document.getElementById(${JSONObject.quote(id)}).getBoundingClientRect();
            return [(r.left+r.width/2)/innerWidth,(r.top+Math.min(r.height,120)/2)/innerHeight]})()"""))
        var result=InputPoint(0f,0f)
        scene.onActivity { val page=checkNotNull(it.tabs.current.session.page);val origin=IntArray(2);val pointer=IntArray(2)
            page.getLocationOnScreen(origin);it.pointer.getLocationOnScreen(pointer)
            result=InputPoint((position.getDouble(0)*page.width+origin[0]-pointer[0]).toFloat(),
                (position.getDouble(1)*page.height+origin[1]-pointer[1]).toFloat()) }
        return result
    }
    private fun nestedScroll(scene: ActivityScenario<LocalBrowserActivity>, source: RawPoseReplay, id: String, delta: Int) {
        val point=nestedPoint(scene,id)
        scene.onActivity { source.aimRelative(it,point) }
        await("raw pose aimed at owned panel") { var aimed=false;scene.onActivity {
            aimed=abs(it.pointer.position.x-point.x)<3 && abs(it.pointer.position.y-point.y)<3
        };aimed }
        scene.onActivity { assertTrue("geometry request admitted once",it.input.scroll(delta)) }
    }

    @Test fun fixedDocumentNestedPanelsUseNativeWheelAndKeepEndpointsIsolated()=scene { scene,source ->
        nestedFixture(scene)
        scene.onActivity { val page=checkNotNull(it.tabs.current.session.page)
            assertFalse("fixed outer document",page.canScrollVertically(1));assertFalse(page.canScrollVertically(-1)) }
        val scaledTick=JSONArray(js(scene,"$BROWSER_SCROLL_TARGET(.8,.5,3,324)"))
        assertEquals("fractional CSS scaling preserves a native pixel tick",3,scaledTick.getInt(2))
        nestedScroll(scene,source,"right",80)
        await("native wheel actually moved intended nested panel") { abs(nestedY(scene,"right")-80)<=2 }
        assertEquals(0,nestedY(scene,"left"));assertEquals(0,y(scene))
        js(scene,"document.documentElement.style.overflow='auto';document.body.style.overflow='visible';document.body.style.height='4000px';document.getElementById('right').scrollTop=9999")
        val end=nestedY(scene,"right")
        nestedScroll(scene,source,"right",80);SystemClock.sleep(180)
        assertEquals(end,nestedY(scene,"right"));assertEquals(0,y(scene))
        nestedScroll(scene,source,"right",-80)
        await("reversal has no banked endpoint distance") { abs(nestedY(scene,"right")-(end-80))<=2 }
        js(scene,"document.getElementById('right').scrollTop=3")
        nestedScroll(scene,source,"right",-80)
        await("final upward tick stops at panel zero") { nestedY(scene,"right")==0 }
        assertEquals(0,y(scene))
        nestedScroll(scene,source,"inner",60)
        await("deepest panel scrolls") { abs(nestedY(scene,"inner")-60)<=2 }
        assertEquals(0,nestedY(scene,"left"))
        js(scene,"document.getElementById('inner').scrollTop=9999")
        nestedScroll(scene,source,"inner",60)
        await("ordinary auto chain inside panel") { nestedY(scene,"left")>0 }
        assertEquals(0,y(scene))
        evidence.put(JSONObject().put("case","native-nested-wheel-endpoints").put("physicalHeadUse",false)
            .put("rightEndpoint",end).put("innerY",nestedY(scene,"inner")).put("leftY",nestedY(scene,"left")))
    }

    @Test fun modalAndCoveredNativeMenuNeverScrollTheBackground()=scene { scene,source ->
        nestedFixture(scene)
        val popup=nestedPoint(scene,"popup")
        scene.onActivity { assertTrue(it.input.activate(popup)) }
        await("actual native tap opened modal") { js(scene,"document.getElementById('modal').open")=="true" }
        nestedScroll(scene,source,"modal",80)
        await("modal scroll effect") { abs(nestedY(scene,"modal")-80)<=2 }
        assertEquals(0,nestedY(scene,"left"));assertEquals(0,nestedY(scene,"right"));assertEquals(0,y(scene))
        js(scene,"document.getElementById('modal').close()")
        reset(scene,source)
        scene.onActivity { source.aimRelative(it,InputPoint(384f,664f)) }
        await("raw head pose stream scrolls fixed-root nested panel") { nestedY(scene,"right")>12 }
        scene.onActivity { assertTrue(it.edgeScrollRunning);assertFalse(it.pointer.cursorVisible) }
        stopped(scene,"More covers nested page",effect={it.utility==LocalBrowserActivity.Utility.MORE}) {
            nativeTap(it,it.controls.getValue("hud.more"))
        }
        SystemClock.sleep(100)
        val stoppedY=nestedY(scene,"right");SystemClock.sleep(180)
        assertEquals("inert More leaves background panel fixed",stoppedY,nestedY(scene,"right"))
        assertEquals(0,y(scene))
        evidence.put(JSONObject().put("case","modal-and-more-isolation").put("physicalHeadUse",false)
            .put("nestedStopY",stoppedY))
    }
    /** Public/current native state only: no field text, DOM mutation or production probe. */
    private fun edgeObservation(activity: LocalBrowserActivity): JSONObject {
        val now=SystemClock.elapsedRealtimeNanos();val pointer=activity.pointer;val position=pointer.position
        val page=activity.tabs.current.session.page
        return JSONObject().put("sampleNs",now).put("pointerX",position.x).put("pointerY",position.y)
            .put("available",position.available).put("cursorVisible",pointer.cursorVisible).put("sampleReceiptNs",pointer.lastSampleReceiptNs)
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
            .put("speed",activity.inputSettings.scrollSpeed).put("sensitivity",activity.inputSettings.pointerGain)
    }
    private fun startScrolling(scene: ActivityScenario<LocalBrowserActivity>, source: RawPoseReplay) {
        reset(scene,source)
        val before=y(scene)
        scene.onActivity { source.orientationDegrees(0.0,50.0) }
        await("fresh literal bottom edge is actually scrolling before cancellation") {
            var running=false
            scene.onActivity { running=it.edgeScrollRunning && it.pointer.position.y==664f &&
                it.tabs.current.session.page?.let { page -> page.scrollY>before+5 && page.canScrollVertically(1) }==true }
            running
        }
    }
    private fun stopped(scene: ActivityScenario<LocalBrowserActivity>, triggerName: String="observation",
                        requireRunning: Boolean=false, effect: (LocalBrowserActivity) -> Boolean={true},
                        trigger: (LocalBrowserActivity) -> Unit={}) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        var activity: LocalBrowserActivity?=null
        var start=0L;var end=0L;var polls=0;var first: JSONObject?=null;var last: JSONObject?=null
        val observation=JSONObject().put("case","callback-stop").put("trigger",triggerName)
            .put("requireRunning",requireRunning).put("scenarioState",scene.state.name)
        evidence.put(observation)
        try {
            // Acquire the current Activity before the timing window. onActivity waits for idle;
            // measured samples instead use runOnMainSync directly, without that extra barrier.
            scene.onActivity {
                activity=it;observation.put("before",edgeObservation(it))
                if(requireRunning) {
                    assertTrue("running callback before $triggerName",it.edgeScrollRunning)
                    assertEquals(664f,it.pointer.position.y,.001f)
                    assertTrue(it.pointer.position.available && SystemClock.elapsedRealtimeNanos()<it.pointer.expiresAtNs)
                    assertEquals(LocalBrowserActivity.Utility.BROWSING,it.utility)
                    assertEquals(LocalBrowserSession.Phase.READY,it.tabs.current.session.state.phase)
                    assertTrue(it.hasWindowFocus() && !it.root.isLayoutRequested)
                    assertTrue(it.tabs.current.session.page?.let { page -> page.isShown && page.canScrollVertically(1) }==true)
                }
                start=SystemClock.elapsedRealtimeNanos();observation.put("startNs",start)
                trigger(it)
                observation.put("dispatchReturnedNs",SystemClock.elapsedRealtimeNanos())
            }
            await("ordinary callback cancelled",100) {
                var stopped=false;val callNs=SystemClock.elapsedRealtimeNanos()
                instrumentation.runOnMainSync {
                    val current=checkNotNull(activity)
                    stopped=!current.edgeScrollRunning && effect(current)
                    last=edgeObservation(current).put("callNs",callNs).put("effectReached",stopped)
                }
                last?.put("returnedNs",SystemClock.elapsedRealtimeNanos())
                if(first==null) first=last
                polls++;observation.put("polls",polls).put("first",first).put("last",last)
                stopped
            }
        } finally {
            end=SystemClock.elapsedRealtimeNanos()
            observation.put("observedNs",end).put("elapsedNs",if(start==0L) JSONObject.NULL else end-start).put("polls",polls)
                .put("first",first ?: JSONObject.NULL).put("last",last ?: JSONObject.NULL)
        }
        assertTrue(end-start<=100_000_000)
    }
    private fun topEndpoint(scene: ActivityScenario<LocalBrowserActivity>, speed: Int) {
        val scenarioState=scene.state.name
        val start=SystemClock.elapsedRealtimeNanos()
        var first: JSONObject?=null;var last: JSONObject?=null;var polls=0;var reached=false;var checkedY: Int?=null
        val observation=JSONObject().put("case","top-endpoint").put("speed",speed)
            .put("startNs",start).put("scenarioState",scenarioState)
        evidence.put(observation)
        try {
            await("top endpoint") {
                scene.onActivity {
                    val page=it.tabs.current.session.page
                    val top=it.pointer.position.y== -24f && page?.canScrollVertically(-1)==false
                    checkedY=if(top) page?.scrollY else null
                    reached=top && checkedY==0
                    last=edgeObservation(it)
                }
                if(first==null) first=last
                polls++
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
    private fun settings(scene: ActivityScenario<LocalBrowserActivity>, gain: Double?=null, speed: Int?=null) {
        control(scene,"more");tagged(scene,"menu.3")
        for((name,target) in listOf("pointer" to gain?.let { (it*10).toInt() },"scroll" to speed)) {
            if(target==null) continue
            var current=0
            scene.onActivity { current=if(name=="pointer") it.inputSettings.pointerTenths else it.inputSettings.scrollSpeed }
            val step=if(name=="pointer") 1 else 10
            repeat(abs(target-current)/step) { tagged(scene,"settings.$name.${if(target>current) "increase" else "decrease"}") }
        }
        tagged(scene,"utility.done")
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
        val arguments=InstrumentationRegistry.getArguments()
        var commit=0L;var result=-1;val data=JSONObject().put("name",name)
            .put("invocationId",arguments.getString("evidenceRunId"))
            .put("candidateHead",arguments.getString("candidateHead")).put("processPid",android.os.Process.myPid())
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

    @Test fun numericRowsMigrateEveryPresetOnceAndPersistStepsAndLimits() {
        for((legacyPointer,tenths) in mapOf("LOW" to 15,"STANDARD" to 20,"HIGH" to 25))
            for((legacyScroll,speed) in mapOf("SLOW" to 120,"STANDARD" to 240,"FAST" to 360))
                scene(legacyPointer,legacyScroll) { scene,source ->
                    fun checkRows(pointer: Int, scroll: Int) {
                        await("numeric rows laid out") {var ready=false;scene.onActivity {
                            val value=it.root.findViewWithTag<TextView>("settings.pointer.value")
                            ready=!it.root.isLayoutRequested && value.isLaidOut && value.width>0
                        };ready}
                        scene.onActivity {
                        assertEquals(pointer,it.inputSettings.pointerTenths);assertEquals(pointer/10.0,it.pointer.gain,0.0)
                        assertEquals(scroll,it.inputSettings.scrollSpeed)
                        assertEquals("${pointer/10.0}×",it.root.findViewWithTag<TextView>("settings.pointer.value").text.toString())
                        assertEquals("$scroll px/s",it.root.findViewWithTag<TextView>("settings.scroll.value").text.toString())
                        for((name,values) in listOf("pointer" to intArrayOf(pointer,1,60,112),
                            "scroll" to intArrayOf(scroll,10,1200,176))) {
                            val (value,minimum,maximum,y)=values
                            for((suffix,x) in listOf("decrease" to 208,"increase" to 384)) {
                                val arrow=it.root.findViewWithTag<TextView>("settings.$name.$suffix")
                                val xy=IntArray(2);arrow.getLocationOnScreen(xy)
                                assertEquals(x,xy[0]);assertEquals(y,xy[1]);assertEquals(48,arrow.width);assertEquals(56,arrow.height)
                                val enabled=if(suffix=="decrease") value!=minimum else value!=maximum
                                assertEquals(enabled,arrow.isEnabled)
                                assertEquals(if(enabled) 0xfff2f2f2.toInt() else 0xff8a8a8a.toInt(),arrow.currentTextColor)
                            }
                        }
                    }
                    }
                    control(scene,"more");tagged(scene,"menu.3");checkRows(tenths,speed)
                    if(tenths==20 && speed==240) capture(scene,"settings-default")
                    tagged(scene,"settings.pointer.increase");tagged(scene,"settings.scroll.decrease")
                    checkRows(tenths+1,speed-10)
                    scene.recreate();scene.onActivity {it.pointer.stop();it.pointer.replaceSourceForTest(source)};reset(scene,source)
                    control(scene,"more");tagged(scene,"menu.3");checkRows(tenths+1,speed-10)
                    evidence.put(JSONObject().put("case","migration").put("legacyPointer",legacyPointer)
                        .put("legacyScroll",legacyScroll).put("migratedTenths",tenths).put("migratedScroll",speed)
                        .put("recreatedTenths",tenths+1).put("recreatedScroll",speed-10))
                    tagged(scene,"utility.done")
                    if(tenths==20 && speed==240) {
                        settings(scene,gain=.1,speed=10);control(scene,"more");tagged(scene,"menu.3");checkRows(1,10)
                        capture(scene,"settings-minimum");tagged(scene,"utility.done")
                        settings(scene,gain=6.0,speed=1200);control(scene,"more");tagged(scene,"menu.3");checkRows(60,1200)
                        capture(scene,"settings-maximum");tagged(scene,"utility.done")
                    }
                }
    }

    @Test fun bothHiddenStripsContinueInwardThenStopAtVisibleReturnAndRejectTaps()=scene { scene,source ->
        settings(scene,gain=2.0,speed=240);open(scene)
        for(direction in listOf(1,-1)) {
            reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,direction*50.0)}
            await("hidden outer strip") { var ready=false;scene.onActivity {
                ready=it.pointer.position.y==if(direction<0) -24f else 664f
                ready=ready && it.edgeScrollRunning && it.pointer.position.available && !it.pointer.cursorVisible
            };ready }
            val before=y(scene);SystemClock.sleep(180)
            assertTrue("actual active page scrolls in strip",direction*(y(scene)-before)>10)
            scene.onActivity {
                val page=it.tabs.current.session.page;val count=it.tabs.count
                assertFalse(it.input.activate(InputPoint(24f,it.pointer.position.y)))
                assertFalse(it.input.activate(InputPoint(408f,it.pointer.position.y)))
                assertEquals(count,it.tabs.count);assertSame(page,it.tabs.current.session.page)
                source.orientationDegrees(0.0,direction*49.95)
            }
            await("small inward movement remains outside and available") {var inward=false;scene.onActivity {
                val p=it.pointer.position
                inward=direction*(p.y-if(direction<0) -24f else 664f)<0 && it.edgeScrollRunning &&
                    p.available && !it.pointer.cursorVisible
            };inward}
            val inwardY=y(scene);SystemClock.sleep(120)
            assertTrue("small inward movement continues actual page effect",direction*(y(scene)-inwardY)>10)
            capture(scene,if(direction<0) "strip-top-hidden" else "strip-bottom-hidden")
            // Use enough return travel to clear the strip with the retained smoothing and interior deadband.
            stopped(scene,"visible re-entry",effect={it.pointer.cursorVisible}) {source.orientationDegrees(0.0,direction*48.0)}
            val returned=y(scene);SystemClock.sleep(120);assertEquals(returned,y(scene))
            capture(scene,if(direction<0) "strip-top-return" else "strip-bottom-return")
            evidence.put(JSONObject().put("case","strip-continuation").put("direction",direction)
                .put("beforeY",before).put("smallInwardY",inwardY).put("visibleReturnY",returned).put("pageOracle",pageOracle(scene)))
        }
        // Existing native pad admission still switches tabs while the pointer is deliberately hidden.
        scene.onActivity {it.addTab();it.selectTab(0)}
        reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)}
        await("offscreen before native swipe") {var hidden=false;scene.onActivity {hidden=!it.pointer.cursorVisible};hidden}
        val device=InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice).single {it.name=="ROKID,PSOC-TP-R"}
        for(code in listOf(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT)) {
            val down=SystemClock.uptimeMillis()
            for(action in listOf(KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP)) {
                scene.onActivity {assertTrue(it.dispatchKeyEvent(KeyEvent(down,SystemClock.uptimeMillis(),action,code,0,0,
                    device.id,0,0,InputDevice.SOURCE_KEYBOARD)))}
                SystemClock.sleep(20)
            }
            scene.onActivity {assertEquals(if(code==KeyEvent.KEYCODE_DPAD_RIGHT) 1 else 0,it.tabs.selectedIndex)}
        }
    }

    @Test fun longVisibleTabListOwnsStripScrollingWithoutMovingCoveredPage()=scene { scene,source ->
        settings(scene,gain=2.0,speed=240);open(scene)
        scene.onActivity {repeat(14) { _ -> it.addTab() };it.selectTab(0)}
        val pageY=y(scene);control(scene,"tab_counter")
        var list: ScrollView?=null
        scene.onActivity {list=it.root.findViewById<android.view.ViewGroup>(R.id.rg_root).let { root ->
            fun find(view: View): ScrollView? = if(view is ScrollView) view else
                (view as? android.view.ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { find(group.getChildAt(it)) } }
            find(root)
        }}
        reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)}
        await("actual visible utility list scrolls") {var moved=false;scene.onActivity {moved=checkNotNull(list).scrollY>20};moved}
        assertEquals("covered page did not scroll",pageY,y(scene))
        scene.onActivity {source.orientationDegrees(0.0,49.95)};SystemClock.sleep(120)
        var before=0;scene.onActivity {before=checkNotNull(list).scrollY;assertTrue(it.edgeScrollRunning)}
        scene.onActivity {source.orientationDegrees(0.0,48.5)}
        await("visible return cancels list scroll") {var stopped=false;scene.onActivity {stopped=!it.edgeScrollRunning && it.pointer.cursorVisible};stopped}
        var after=0;scene.onActivity {after=checkNotNull(list).scrollY};SystemClock.sleep(120)
        scene.onActivity {assertEquals(after,checkNotNull(list).scrollY)}
        assertEquals(pageY,y(scene));capture(scene,"long-list-return")
        evidence.put(JSONObject().put("case","visible-list").put("listBeforeReturn",before).put("listAfterReturn",after).put("coveredPageY",pageY))
    }

    @Test fun allBoundariesDiscardOvershootWithPromptSlowInwardDraw()=scene { scene,source ->
        settings(scene,gain=2.0)
        for(h in -1..1) for(v in -1..1) {
            if(h==0 && v==0) continue
            for(extra in listOf(5.0,20.0,45.0)) for(hold in listOf(100L,500L,1000L)) {
                reset(scene,source)
                val yaw=h*(30.3+extra);val pitch=v*(22.3+extra/2)
                scene.onActivity { source.orientationDegrees(yaw,pitch) }
                await("clamped edge h=$h v=$v excess=$extra") { var edge=false;scene.onActivity {
                    val p=it.pointer.position;assertTrue(p.x in 8f..472f && p.y in -24f..664f)
                    edge=(h==0 || abs(p.x-if(h<0) 8f else 472f)<.001) && (v==0 || abs(p.y-if(v<0) -24f else 664f)<.001)
                };edge }
                SystemClock.sleep(hold)
                if(h==0 && v == -1 && extra==5.0 && hold==100L) capture(scene,"pointer-top")
                if(h==1 && v==1 && extra==45.0 && hold==1000L) capture(scene,"pointer-corner")
                var receiptBefore=0L;var start=0L;var visible=0L;var receipt=0L
                scene.onActivity { receiptBefore=it.pointer.lastSampleReceiptNs;start=SystemClock.elapsedRealtimeNanos();source.orientationDegrees(yaw-h*.05,pitch-v*.05) }
                await("first slow inward movement within 100ms",100) { var inward=false;scene.onActivity {
                    val p=it.pointer.lastDrawPosition
                    inward=it.pointer.lastDrawSampleReceiptNs>receiptBefore &&
                        (h==0 || if(h<0) p.x>8 else p.x<472) && (v==0 || if(v<0) p.y> -24 else p.y<664)
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
        settings(scene,gain=2.0);open(scene)
        val identity=js(scene,"fixtureIdentity")
        for(speed in listOf(120,240,360)) {
            settings(scene,speed=speed)
            control(scene,"more");tagged(scene,"menu.3")
            scene.onActivity { assertEquals(speed,it.inputSettings.scrollSpeed);assertEquals("$speed px/s",it.root.findViewWithTag<android.widget.TextView>("settings.scroll.value").text.toString()) }
            if(speed==240) capture(scene,"settings")
            tagged(scene,"utility.done")
            // Start at the real top endpoint through raw poses; no DOM scroll assignment.
            reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,-50.0)}
            topEndpoint(scene,speed);SystemClock.sleep(80)
            reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)}
            await("actual bottom-edge page movement") {y(scene)>10}
            val before=y(scene);val time=SystemClock.elapsedRealtimeNanos();SystemClock.sleep(400)
            val after=y(scene);val elapsed=SystemClock.elapsedRealtimeNanos()-time
            val observed=(after-before)*1e9/elapsed
            assertTrue("native rate ${speed}: $observed",abs(observed-speed)<=speed*.12+4)
            stopped(scene,"edge departure",requireRunning=true) { source.orientationDegrees(0.0,48.5) }
            val dom=pageOracle(scene)
            evidence.put(JSONObject().put("case","rate").put("speed",speed).put("beforeY",before).put("afterY",after).put("elapsedNs",elapsed).put("observedNativePxPerSecond",observed).put("domObservation",dom))
        }
        val distances=mutableListOf<Float>()
        for(gain in listOf(1.5,2.0,2.5)) {
            settings(scene,gain=gain);reset(scene,source)
            scene.onActivity {source.orientationDegrees(10.0,0.0)}
            SystemClock.sleep(500)
            scene.onActivity {distances+=it.pointer.position.x-240;assertEquals(gain,it.inputSettings.pointerGain,0.0)}
        }
        assertEquals(.75,distances[0]/distances[1].toDouble(),.015);assertEquals(1.25,distances[2]/distances[1].toDouble(),.015)
        // Actual Activity recreation rereads durable settings; metadata recovery never auto-navigates.
        scene.recreate();scene.onActivity {assertEquals(2.5,it.inputSettings.pointerGain,0.0);assertEquals(360,it.inputSettings.scrollSpeed);it.pointer.stop()}
        assertTrue(identity.isNotEmpty())
        evidence.put(JSONObject().put("case","gains").put("distances",JSONArray(distances)).put("durableRecreation",true))
    }

    @Test fun fullScreenKeyboardEdgesAndOrdinaryCancellationPreserveNativeEditing()=scene { scene,source ->
        settings(scene,speed=240);settings(scene,gain=2.0);open(scene)
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
        val dividerPitch=.3+(440.0-320)*44/624/2
        scene.onActivity {source.orientationDegrees(0.0,dividerPitch)};SystemClock.sleep(500);stopped(scene)
        val dividerY=y(scene);SystemClock.sleep(150);assertEquals(dividerY,y(scene))
        reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)}
        await("bottom screen edge scrolls page under fixed keyboard") {
            var ready=false;scene.onActivity {
                ready=it.pointer.position.y==664f && it.edgeScrollRunning &&
                    (it.tabs.current.session.page?.scrollY ?: 0)>dividerY+20
            };ready
        }
        scene.onActivity {
            val xy=IntArray(2);it.keyboard.getLocationOnScreen(xy)
            assertEquals(440,xy[1]);assertEquals(200,it.keyboard.height);assertTrue(it.tabs.current.session.page?.hasFocus()==true)
            assertEquals(664f,it.pointer.position.y,.001f)
        }
        capture(scene,"keyboard-bottom")
        stopped(scene,"edge departure",requireRunning=true) { source.orientationDegrees(0.0,48.5) }
        val departure=y(scene);SystemClock.sleep(150);assertEquals(departure,y(scene))
        key(scene,RgKeyboardKeys.Key.Character("b"));assertEquals("\"ab\"",js(scene,"document.querySelector('#text').value"));assertEquals("0",js(scene,"fixtureSubmits"))
        reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)};await("scroll resumes") {y(scene)>departure+5}
        scene.onActivity {source.flowing=false}
        await("no effect at sample age cutoff") {var stale=false;scene.onActivity {stale=SystemClock.elapsedRealtimeNanos()>=it.pointer.expiresAtNs};stale}
        val staleY=y(scene);SystemClock.sleep(150);assertEquals(staleY,y(scene));stopped(scene)
        startScrolling(scene,source)
        stopped(scene,"More cover",requireRunning=true,effect={it.utility==LocalBrowserActivity.Utility.MORE}) {
            nativeTap(it,it.controls.getValue("hud.more"))
        }
        val covered=y(scene);SystemClock.sleep(150);assertEquals(covered,y(scene))
        tagged(scene,"menu.3");SystemClock.sleep(150);assertEquals("Settings edge cannot scroll covered page",covered,y(scene));tagged(scene,"utility.done")
        startScrolling(scene,source)
        scene.moveToState(Lifecycle.State.CREATED);stopped(scene);assertFalse(source.registered)
        scene.moveToState(Lifecycle.State.RESUMED)
        assertEquals(identity,js(scene,"fixtureIdentity"));assertEquals("\"ab\"",js(scene,"document.querySelector('#text').value"))
        // Current tab changes and navigation use ordinary callbacks, with no stale-tick protocol.
        startScrolling(scene,source)
        stopped(scene,"More before New Tab",requireRunning=true,effect={it.utility==LocalBrowserActivity.Utility.MORE}) {
            nativeTap(it,it.controls.getValue("hud.more"))
        }
        // More already cancelled the page callback. New Tab must stay stopped, not claim a second cancellation.
        tagged(scene,"menu.0")
        await("actual new tab") {var added=false;scene.onActivity {added=it.tabs.count==2 && it.tabs.selectedIndex==1};added}
        stopped(scene)
        scene.onActivity {it.selectTab(0)}
        startScrolling(scene,source)
        stopped(scene,"current tab switch",requireRunning=true,effect={it.tabs.selectedIndex==1}) {
            it.selectTab(1);assertFalse("tab switch cancels synchronously",it.edgeScrollRunning)
        }
        scene.onActivity {it.selectTab(0)}
        startScrolling(scene,source)
        stopped(scene,"close current tab",requireRunning=true,effect={it.tabs.count==1}) {
            nativeTap(it,it.controls.getValue("hud.close_tab"))
        }
        open(scene)
        val beforeRefresh=js(scene,"fixtureIdentity")
        startScrolling(scene,source)
        stopped(scene,"Refresh",requireRunning=true) {nativeTap(it,it.controls.getValue("hud.refresh"))}
        await("new document after actual Refresh") {js(scene,"fixtureIdentity")!=beforeRefresh}
        reset(scene,source);scene.onActivity {source.orientationDegrees(0.0,50.0)}
        await("actual bottom endpoint") {var bottom=false;scene.onActivity {bottom=it.pointer.position.y==664f && it.tabs.current.session.page?.canScrollVertically(1)==false};bottom}
        val endpoint=pageOracle(scene);val endpointY=y(scene)
        scene.onActivity {source.orientationDegrees(0.0,65.0)};SystemClock.sleep(500)
        assertEquals("endpoint/outward hold has no scroll debt",endpointY,y(scene))
        scene.onActivity {source.orientationDegrees(0.0,63.5)};stopped(scene)
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
