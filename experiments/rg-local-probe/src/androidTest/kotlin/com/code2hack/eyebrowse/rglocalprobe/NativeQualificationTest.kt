package com.code2hack.eyebrowse.rglocalprobe

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** JS is fixture setup/observation only; editing uses the real native probe keys. */
@RunWith(AndroidJUnit4::class)
class NativeQualificationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: ProbeActivity
    private val fields = listOf("text", "password", "multiline", "plain")
    private val base = InstrumentationRegistry.getArguments().getString("fixtureBaseUrl") ?: error("fixtureBaseUrl required")
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun js(script: String): String {
        val latch = CountDownLatch(1)
        var result = "null"
        main { activity.web.evaluateJavascript(script) { result = it; latch.countDown() } }
        assertTrue("fixture observation callback", latch.await(3, TimeUnit.SECONDS))
        return result
    }
    private fun truth(expression: String) = js("Boolean($expression)") == "true"
    private fun await(label: String, bound: Long = 10000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + bound
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(20)
        }
        fail(label)
    }
    private fun withFixture(engineOnly: Boolean = false, body: (ActivityScenario<ProbeActivity>) -> Unit) {
        val intent = Intent(instrumentation.targetContext, ProbeActivity::class.java)
            .putExtra("fixtureUrl", "$base/keyboard.html").putExtra("engineDarkeningOnly",engineOnly)
        ActivityScenario.launch<ProbeActivity>(intent).use { scenario ->
            scenario.onActivity { activity = it }
            await("RG-owned fixture loaded") { truth("window.fixtureIdentity") }
            await("ordinary visible page") { var visible=false;main {visible=activity.web.visibility==View.VISIBLE&&activity.web.alpha==1f};visible }
            main { assertEquals(480,activity.window.decorView.width); assertEquals(640,activity.window.decorView.height) }
            body(scenario)
        }
    }
    private fun fresh() {
        val old = js("window.fixtureIdentity") // Continuity observation only, never an input prerequisite.
        main { activity.open("$base/keyboard.html") }
        await("fresh fixture loaded") { truth("window.fixtureIdentity") && js("window.fixtureIdentity") != old }
        await("fresh page presented") { var visible=false;main {visible=activity.web.visibility==View.VISIBLE&&activity.web.alpha==1f};visible }
    }
    private fun click(label: String) { main { activity.controls.getValue(label).performClick() } }
    private fun value(id: String, expected: String) = truth(
        "(()=>{const e=document.getElementById('$id');return (e.isContentEditable?e.textContent:e.value)===${JSONObject.quote(expected)}})()")
    private fun row(name: String, action: () -> Unit) {
        Log.i("RGProbe", "NATIVE_ROW_START name=$name")
        val start = SystemClock.elapsedRealtime()
        action()
        Log.i("RGProbe", "NATIVE_ROW_PASS name=$name elapsedMs=${SystemClock.elapsedRealtime()-start}")
    }
    private fun visualReady() {
        val ready = CountDownLatch(1)
        main { activity.web.postVisualStateCallback(SystemClock.elapsedRealtime(), object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { ready.countDown() }
        }) }
        assertTrue("visual state callback within2s",ready.await(2,TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }
    private fun box(selector: String, x: Double, y: Double): JSONObject = JSONObject(JSONTokener(js(
        "JSON.stringify((()=>{const r=document.querySelector('$selector').getBoundingClientRect();return {x:r.x+r.width*$x,y:r.y+r.height*$y,width:innerWidth}})())")).nextValue() as String)
    private fun tap(selector: String) {
        val point = box(selector,0.5,0.5)
        main {
            val ratio=activity.web.width/point.getDouble("width")
            val x=(point.getDouble("x")*ratio).toFloat()
            val y=(point.getDouble("y")*ratio).toFloat()
            assertTrue("point in current native View",x in 0f..activity.web.width.toFloat() && y in 0f..activity.web.height.toFloat())
            val time=SystemClock.uptimeMillis()
            MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0).let { activity.web.dispatchTouchEvent(it); it.recycle() }
            MotionEvent.obtain(time,time+30,MotionEvent.ACTION_UP,x,y,0).let { activity.web.dispatchTouchEvent(it); it.recycle() }
        }
    }
    private fun focus(id: String) {
        js("document.getElementById('$id').scrollIntoView({block:'center'});true")
        visualReady(); tap("#$id")
        await("actual native tap focuses $id") { truth("document.activeElement.id==='$id'") }
        click("Field")
        await("local key viewport") { var height=0;main {height=activity.web.height};height==372 }
        assertTrue("current focus survives resize",truth("document.activeElement.id==='$id'"))
    }
    private fun capture(name: String): Bitmap {
        main { assertTrue("only owned foreground fixture capture",activity.hasWindowFocus()) }
        SystemClock.sleep(150)
        val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("screenshot unavailable")
        assertEquals(480,bitmap.width);assertEquals(640,bitmap.height)
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"qualification/rev2-$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        return bitmap
    }
    private fun sample(bitmap: Bitmap, selector: String, xFraction: Double, yFraction: Double): Int {
        // Fixture region/fraction is declared before capture; no choosing regions from output.
        val point=box(selector,xFraction,yFraction)
        var x=0;var y=0
        main {
            val origin=IntArray(2);activity.web.getLocationOnScreen(origin)
            val ratio=activity.web.width/point.getDouble("width")
            x=origin[0]+(point.getDouble("x")*ratio).toInt()
            y=origin[1]+(point.getDouble("y")*ratio).toInt()
        }
        Log.i("RGProbe","PIXEL_REGION selector=$selector x=$x y=$y")
        return bitmap.getPixel(x,y) and 0xffffff
    }

    @Test fun fourEditorClassesUseActualNativeKeys() = withFixture {
        for(id in fields) row("$id-native-insert-selection-delete-enter-done") {
            fresh();focus(id)
            click("A");await("first actual insert") { value(id,"a") }
            click("B");await("second actual insert") { value(id,"ab") }
            click("Space");await("space") { value(id,"ab ") }
            click("Delete");await("native Backspace") { value(id,"ab") }
            visualReady()
            assertTrue("focused editor remains visible in current viewport",truth("""(()=>{
                const e=document.getElementById('$id'),r=e.getBoundingClientRect();
                return document.activeElement===e&&r.top>=0&&r.bottom<=innerHeight&&r.left>=0&&r.right<=innerWidth
            })()"""))
            capture(if(id=="password") "password-open" else "$id-open")
            js("""(()=>{const e=document.getElementById('$id');
                if(e.isContentEditable){const r=document.createRange();r.selectNodeContents(e);const s=getSelection();s.removeAllRanges();s.addRange(r)}
                else e.setSelectionRange(0,e.value.length);return true})()""")
            SystemClock.sleep(80)
            click("B");await("native selection replacement") { value(id,"b") }
            click("Enter")
            when(id) {
                "text" -> await("ordinary singleline form submission") { truth("fixtureSubmits===1") }
                "password" -> { SystemClock.sleep(100);assertTrue(value(id,"b"));assertTrue(truth("fixtureSubmits===0")) }
                "multiline" -> await("native multiline newline") { value(id,"b\n") }
                "plain" -> await("native plain editable linebreak") { truth("document.getElementById('plain').innerText.includes('\\n')||document.getElementById('plain').querySelector('br,div,p')") }
            }
            val identity=js("fixtureIdentity");val submits=js("fixtureSubmits");val entered=js("document.getElementById('$id').isContentEditable?document.getElementById('$id').innerHTML:document.getElementById('$id').value")
            click("Done")
            await("normal viewport restored") { var height=0;main {height=activity.web.height};height==532 }
            assertEquals(identity,js("fixtureIdentity"));assertEquals(submits,js("fixtureSubmits"))
            assertTrue("entered content preserved",entered==js("document.getElementById('$id').isContentEditable?document.getElementById('$id').innerHTML:document.getElementById('$id').value"))
        }
    }

    @Test fun currentFocusViewportAndPauseUseOrdinaryNativeBehavior() = withFixture { scenario ->
        focus("text");click("A");await("text value") { value("text","a") }
        focus("other");click("B");await("new current field value") { value("other","b") }
        assertTrue(value("text","a"))
        val identity=js("fixtureIdentity")
        click("Done");await("expanded viewport") { var h=0;main {h=activity.web.height};h==532 }
        click("Field")
        await("reduced viewport with same current field") { var h=0;main {h=activity.web.height};h==372&&truth("document.activeElement.id==='other'") }
        click("A");await("same document input after resize") { value("other","ba") }
        assertEquals(identity,js("fixtureIdentity"));visualReady();capture("keyboard-viewport")
        scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED)
        await("resumed ordinary focus") { var ready=false;main {ready=activity.hasWindowFocus()};ready }
        assertEquals(identity,js("fixtureIdentity"));assertTrue(value("other","ba"))
        main { assertEquals(532,activity.web.height) }
        assertTrue("ordinary current field after resume",truth("document.activeElement.id==='other'"))
        click("Field");click("B");await("normal editing after Activity resume") { value("other","bab") }
    }

    @Test fun localNavigationAndAddressUseActualControls() = withFixture {
        click("Done");js("document.querySelector('a').scrollIntoView();true");visualReady();tap("a")
        await("ordinary local link") { truth("document.title==='RG local history'") }
        click("Back");await("Back") { truth("document.title==='RG local editor fixture'") }
        click("Forward");await("Forward") { truth("document.title==='RG local history'") }
        click("Back");await("Back again") { truth("window.fixtureIdentity") }
        val before=js("fixtureIdentity");click("Reload")
        await("real Reload document") { truth("window.fixtureIdentity")&&js("fixtureIdentity")!=before }
        main { activity.address.requestFocus();activity.address.selectAll() }
        click("A");main { assertEquals("a",activity.address.text.toString()) }
        val current=js("fixtureIdentity");click("Open")
        assertEquals(current,js("fixtureIdentity"))
        main { assertEquals("Invalid address",activity.status.text.toString());assertEquals("a",activity.address.text.toString()) }
    }

    @Test fun boundedWorkloadMeasuresMemoryAndActualEffectRendering() = withFixture {
        val before=Debug.getPss()
        repeat(12) { index -> row("native-workload-$index") {
            fresh();focus(fields[index%fields.size])
            val start=SystemClock.elapsedRealtime();click("A")
            val dispatch=SystemClock.elapsedRealtime()-start
            await("actual workload field effect") { value(fields[index%fields.size],"a") };visualReady()
            val elapsed=SystemClock.elapsedRealtime()-start
            assertTrue("predeclared probe effect/visual2s bound",elapsed<=2000)
            assertTrue("native key dispatch100ms bound",dispatch<=100)
            Log.i("RGProbe","WORKLOAD sample=$index nativeDispatchMs=$dispatch effectAndVisualMs=$elapsed appPssKiB=${Debug.getPss()} nativeAllocatedBytes=${Debug.getNativeHeapAllocatedSize()}")
            if(index==0||index==11)capture("workload-$index")
            click("Done")
        } }
        Log.i("RGProbe","WORKLOAD_SUMMARY samples=12 startPssKiB=$before endPssKiB=${Debug.getPss()}")
    }

    @Test fun darkOutputAndMediaAreClassifiedFromRealPixels() = withFixture {
        main {assertEquals("color sample lies inside page",532,activity.web.height)}
        click("Done");visualReady()
        val dark=capture("author-dark")
        assertEquals("predeclared primary blank450,620",Color.BLACK,dark.getPixel(450,620))
        main { activity.open("$base/author-light.html") }
        await("owned author-light/media loaded") { truth("document.title==='Author light qualification'&&document.getElementById('media-image').complete&&document.getElementById('media-image').naturalWidth===64") }
        await("fixed presentation sheet applied") { truth("document.getElementById('rg-probe-colors')") }
        await("styled page visible") { var visible=false;main {visible=activity.web.visibility==View.VISIBLE&&activity.web.alpha==1f};visible }
        main {assertEquals(532,activity.web.height)}
        visualReady();val light=capture("author-light")
        Log.i("RGProbe","DARK_SAMPLE region=primary-450-620 rgb=${light.getPixel(450,620) and 0xffffff} conforms=${light.getPixel(450,620)==Color.BLACK}")
        assertEquals(Color.BLACK,light.getPixel(450,620))
        val field=sample(light,"#light-field",0.9,0.5)
        Log.i("RGProbe","DARK_SAMPLE region=field-interior rgb=$field")
        assertEquals(0,field)
        val media=sample(light,"#media-image",0.5,0.5)
        Log.i("RGProbe","DARK_SAMPLE region=image-media-center rgb=$media preserved=${media==0xe04040}")
        assertEquals(0xe04040,media)
        tap("button");await("actual dynamic action") { truth("document.getElementById('dynamic').style.backgroundColor==='rgb(221, 221, 221)'") }
        visualReady();val dynamic=capture("author-light-dynamic")
        val surface=sample(dynamic,"#dynamic",0.9,0.8)
        Log.i("RGProbe","DARK_SAMPLE region=dynamic-interior rgb=$surface")
        assertEquals(0,surface)
    }

    @Test fun automaticEngineDarkeningIsMeasuredSeparately() = withFixture(engineOnly=true) {
        main {activity.open("$base/author-light.html")}
        await("engine-only fixture loaded") {truth("document.getElementById('media-image')?.complete")}
        await("engine-only page visible") {var ready=false;main {ready=activity.web.alpha==1f};ready}
        main {assertEquals("sample must be page pixels",532,activity.web.height)}
        visualReady();val output=capture("engine-darkening-only")
        val pixel=output.getPixel(450,620) and 0xffffff
        Log.i("RGProbe","ENGINE_ONLY_DARK rgb=$pixel conforms=${pixel==0}")
        // This classifier preserves an unsupported output result instead of accepting a flag.
    }

    @Test fun appLoadingPresentationUsesBlackFrames() {
        ActivityScenario.launch<ProbeActivity>(Intent(instrumentation.targetContext,ProbeActivity::class.java)).use { scenario ->
            scenario.onActivity { activity=it }
            await("owned startup window visible") {var visible=false;main {visible=activity.hasWindowFocus()&&activity.window.decorView.width==480&&activity.window.decorView.height==640};visible}
            fun blackFrame(name: String) {
                val frame=capture(name)
                assertEquals("native window blank edge",Color.BLACK,frame.getPixel(479,20))
                assertEquals("initial or styled page surface",Color.BLACK,frame.getPixel(450,300))
                assertEquals("primary backing or styled surface",Color.BLACK,frame.getPixel(450,620))
                Log.i("RGProbe","PRESENTATION_FRAME name=$name primaryBlack=true nativeBlack=true")
            }
            blackFrame("startup-empty")
            main {activity.open("$base/loading.html")}
            fun loading(): Boolean {var result=false;main {result=activity.web.alpha==0f&&activity.status.text=="Loading"};return result}
            await("actual pending network load") {loading()}
            repeat(3) { sample ->
                assertTrue("sample begins during actual load",loading())
                blackFrame("loading-$sample")
                assertTrue("sample ends during actual load",loading())
            }
            await("styled network response presented") {
                var presented=false;main {presented=activity.web.alpha==1f}
                truth("document.title==='Author light qualification'&&document.getElementById('rg-probe-colors')")&&presented
            }
            visualReady();blackFrame("loading-ready")
        }
        // Startup/loading/ready samples are bounded evidence, not every possible frame.
    }

    @Test fun publicInternetRequestRecordsTheActualOutcome() = withFixture {
        main { activity.open("https://example.org") }
        await("public page or mainframe network error",30000) {
            var failed=false;main {failed=activity.networkError!=null}
            failed||truth("document.title==='Example Domain'")
        }
        var error: Int?=null;main {error=activity.networkError}
        Log.i("RGProbe","INTERNET_OUTCOME exampleOrgLoaded=${truth("document.title==='Example Domain'")} mainFrameError=$error")
        visualReady();capture("public-internet")
    }

    @Test fun authorizedRealSleepPreservesDocumentAndNormalEditing() {
        assumeTrue("requires a separate bounded device grant",InstrumentationRegistry.getArguments().getString("deviceCycle")=="authorized")
        withFixture {
            focus("text");click("A");await("prepared disposable typed fixture") { value("text","a") }
            val identity=js("fixtureIdentity")
            var pauses=0L;main {pauses=activity.pauseCount}
            val power=instrumentation.targetContext.getSystemService(PowerManager::class.java)
            assertTrue(power.isInteractive)
            Log.i("RGProbe","DEVICE_CYCLE_READY wallTimeMs=${System.currentTimeMillis()} trigger=external-authorized-controller")
            await("authorized real RG sleep",120000) { !power.isInteractive }
            Log.i("RGProbe","DEVICE_CYCLE_SCREEN_OFF wallTimeMs=${System.currentTimeMillis()}")
            await("authorized normal wake",30000) { power.isInteractive }
            await("actual pause and resumed native window") { var resumed=false;main {resumed=activity.pauseCount>pauses&&activity.hasWindowFocus()};resumed }
            assertEquals(identity,js("fixtureIdentity"));assertTrue(value("text","a"));assertTrue(truth("fixtureSubmits===0"))
            assertTrue("ordinary current field after physical resume",truth("document.activeElement.id==='text'"))
            click("Field");click("B");await("native editing after physical resume") { value("text","ab") }
            visualReady();capture("physical-resume")
            Log.i("RGProbe","DEVICE_CYCLE_PASS wallTimeMs=${System.currentTimeMillis()} sameLiveDocument=true normalCurrentFocusEditing=true")
        }
    }
}
