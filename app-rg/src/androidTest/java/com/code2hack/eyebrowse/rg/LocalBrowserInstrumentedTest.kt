package com.code2hack.eyebrowse.rg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Actual main-source Activity, reserved RG and unchanged #28 fixture only.
 * Native events/raw pose replay qualify application effects, not wearer-facing direction or optics.
 */
@RunWith(AndroidJUnit4::class)
class LocalBrowserInstrumentedTest {
    @get:Rule val testName=TestName()
    private val aimEvidence=JSONArray()
    private var maxKeyDispatchMs = 0L
    private var keyDispatchCount = 0
    private val fixtureBase = checkNotNull(InstrumentationRegistry.getArguments().getString("fixtureBaseUrl")) {
        "A reserved fixtureBaseUrl is required; the suite never starts a service or connects a device"
    }.trimEnd('/')

    private fun await(label: String, bound: Long = 10_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + bound
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(25)
        }
        fail(label)
    }
    private fun js(scene: ActivityScenario<LocalBrowserActivity>, expression: String): String {
        val done = CountDownLatch(1); var result = ""
        scene.onActivity { checkNotNull(it.tabs.current.session.page).evaluateJavascript(expression) { value ->
            result = value; done.countDown()
        } }
        assertTrue("owned live fixture observation", done.await(3, TimeUnit.SECONDS))
        return result
    }
    private fun center(activity: LocalBrowserActivity, view: View): InputPoint {
        val child = IntArray(2); val root = IntArray(2)
        view.getLocationOnScreen(child); activity.root.getLocationOnScreen(root)
        return InputPoint(child[0] - root[0] + view.width / 2f, child[1] - root[1] + view.height / 2f)
    }
    private fun tap(scene: ActivityScenario<LocalBrowserActivity>, find: (LocalBrowserActivity) -> View): Long {
        await("current visible native target laid out") {
            var ready = false
            scene.onActivity {
                val view = find(it)
                ready = it.hasWindowFocus() && view.isShown && view.isLaidOut && view.width > 0 &&
                    view.height > 0 && !it.root.isLayoutRequested
            }
            ready
        }
        var elapsed = 0L
        scene.onActivity {
            val point = center(it, find(it)); val started = SystemClock.uptimeMillis()
            assertTrue(it.input.activate(point))
            elapsed = SystemClock.uptimeMillis() - started
        }
        return elapsed
    }
    private fun control(scene: ActivityScenario<LocalBrowserActivity>, name: String) =
        tap(scene) { it.controls.getValue("hud." + name) }
    private fun tagged(scene: ActivityScenario<LocalBrowserActivity>, tag: String) =
        tap(scene) { checkNotNull(it.root.findViewWithTag<View>(tag)) }
    private fun key(scene: ActivityScenario<LocalBrowserActivity>, key: RgKeyboardKeys.Key) {
        val elapsed = tap(scene) { it.keyButtons.getValue(key) }
        keyDispatchCount++; maxKeyDispatchMs = maxOf(maxKeyDispatchMs, elapsed)
        assertTrue("native built-in key dispatch <=100ms; actual=" + elapsed, elapsed <= 100)
    }
    private fun type(scene: ActivityScenario<LocalBrowserActivity>, value: String) {
        for (character in value) {
            val key = RgKeyboardKeys.Key.Character(character.toString())
            var exists = false
            scene.onActivity { exists = key in it.keyButtons }
            if (!exists) key(scene, RgKeyboardKeys.Key.Command.SYMBOLS)
            scene.onActivity { assertTrue("real current keyboard character", key in it.keyButtons) }
            key(scene, key)
        }
    }
    private fun address(scene: ActivityScenario<LocalBrowserActivity>, value: String) {
        control(scene, "address")
        scene.onActivity { it.address.selectAll() } // Native selection, never a DOM value assignment.
        type(scene, value)
    }
    private fun open(scene: ActivityScenario<LocalBrowserActivity>, route: String) {
        address(scene, fixtureBase + "/" + route); key(scene, RgKeyboardKeys.Key.Command.ENTER)
    }
    private fun ready(scene: ActivityScenario<LocalBrowserActivity>, bound: Long = 10_000) =
        await("current live page presented", bound) {
            var result = false
            scene.onActivity {
                result = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.READY &&
                    it.tabs.current.session.page?.isShown == true && !it.keyboard.isShown
            }
            result
        }
    private fun elementPoint(scene: ActivityScenario<LocalBrowserActivity>, selector: String): InputPoint {
        await("current page layout settled before native geometry observation") {
            var settled = false
            scene.onActivity { settled = !it.root.isLayoutRequested && it.tabs.current.session.page?.isLaidOut == true }
            settled
        }
        val rect = JSONArray(js(scene, "(()=>{const r=document.querySelector(" + JSONObject.quote(selector) +
            ").getBoundingClientRect();return [r.x,r.y,r.width,r.height,innerWidth,innerHeight]})()"))
        var point = InputPoint(0f, 0f)
        scene.onActivity {
            val page = checkNotNull(it.tabs.current.session.page)
            val origin = IntArray(2); val root = IntArray(2)
            page.getLocationOnScreen(origin); it.root.getLocationOnScreen(root)
            val scale = page.width / rect.getDouble(4)
            val x = (rect.getDouble(0) + rect.getDouble(2) / 2) * scale
            val y = (rect.getDouble(1) + rect.getDouble(3) / 2) * scale
            // Numeric admission evidence survives a failure without recording field contents.
            keyboardEvidence.put(JSONObject().put("stage", "native-element-admission")
                .put("observedAtNs", SystemClock.elapsedRealtimeNanos())
                .put("cssBounds", JSONArray((0..3).map { rect.getDouble(it) }))
                .put("cssViewport", JSONArray(listOf(rect.getDouble(4), rect.getDouble(5))))
                .put("pageBounds", bounds(page)).put("rootBounds", bounds(it.root))
                .put("scale", scale).put("pagePoint", JSONArray(listOf(x, y)))
                .put("keyboardShown", it.keyboard.isShown).put("pageFocused", page.hasFocus())
                .put("layoutRequested", it.root.isLayoutRequested))
            assertTrue("owned element inside current page", x in 0.0..page.width.toDouble() && y in 0.0..page.height.toDouble())
            point = InputPoint(origin[0] - root[0] + x.toFloat(), origin[1] - root[1] + y.toFloat())
        }
        return point
    }
    private fun element(scene: ActivityScenario<LocalBrowserActivity>, selector: String) {
        val point = elementPoint(scene, selector)
        scene.onActivity { assertTrue(it.input.activate(point)) }
    }
    private fun focusText(scene: ActivityScenario<LocalBrowserActivity>) {
        element(scene, "#text")
        await("current visible native editor and actual built-in keyboard") {
            var focused = false
            scene.onActivity {
                focused = it.tabs.current.session.page?.let { page -> page.isShown && page.hasFocus() } == true &&
                    it.keyboard.isShown && it.keyboard.height == 200 && !it.root.isLayoutRequested
            }
            focused && js(scene, "document.activeElement.id") == "\"text\""
        }
    }
    private fun bounds(view: View): JSONArray {
        val xy = IntArray(2); view.getLocationOnScreen(xy)
        return JSONArray(listOf(xy[0], xy[1], view.width, view.height))
    }
    private fun capture(scene: ActivityScenario<LocalBrowserActivity>, name: String, expectedError: String? = null): Bitmap {
        val visual = CountDownLatch(1)
        scene.onActivity {
            if (it.tabs.current.session.state.phase == LocalBrowserSession.Phase.READY &&
                it.tabs.current.session.page?.isShown == true) {
                checkNotNull(it.tabs.current.session.page).postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) { visual.countDown() }
                })
            } else visual.countDown() // Utilities/black backing need their native frame, not hidden page presentation.
        }
        assertTrue("public settled page observation", visual.await(3, TimeUnit.SECONDS))
        val frame = CountDownLatch(1); var committed = 0L
        scene.onActivity {
            assertTrue(it.root.isAttachedToWindow && it.root.isHardwareAccelerated && it.hasWindowFocus())
            assertEquals("[0,0,480,640]", bounds(it.window.decorView).toString())
            it.root.viewTreeObserver.registerFrameCommitCallback { committed = SystemClock.elapsedRealtimeNanos(); frame.countDown() }
            it.root.invalidate()
        }
        assertTrue("owned native frame committed", frame.await(3, TimeUnit.SECONDS))
        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        val copy = CountDownLatch(1); var code = -1
        val observation = JSONObject().put("mechanism", "owned-window PixelCopy after frame commit")
            .put("frameCommittedAtNs", committed).put("width", 480).put("height", 640)
        scene.onActivity { activity ->
            assertTrue(activity.hasWindowFocus())
            if (expectedError != null) {
                assertEquals(LocalBrowserSession.Phase.ERROR, activity.tabs.current.session.state.phase)
                assertEquals(expectedError, activity.tabs.current.session.state.error)
                val status = activity.root.findViewWithTag<TextView>("hud.status")
                assertTrue(status.isShown && status.isLaidOut && !status.isLayoutRequested)
                assertEquals(expectedError, status.text.toString())
                observation.put("statusBounds", bounds(status)).put("error", status.text.toString())
            }
            observation.put("phase", activity.tabs.current.session.state.phase.name)
                .put("utility", activity.utility.name).put("tabCount", activity.tabs.count)
                .put("selectedIndex", activity.tabs.selectedIndex).put("keyboardShown", activity.keyboard.isShown)
                .put("pageShown", activity.tabs.current.session.page?.isShown == true)
                .put("pageFocused", activity.tabs.current.session.page?.hasFocus() == true)
                .put("addressFocused", activity.address.hasFocus()).put("rootFocused", activity.root.isFocused)
                .put("rootDefaultFocusHighlightEnabled", activity.root.defaultFocusHighlightEnabled)
                .put("nativeFocusClass", activity.currentFocus?.javaClass?.simpleName)
                .put("rootBounds", bounds(activity.root)).put("toolbarBounds", bounds(activity.toolbar))
                .put("contentBounds", bounds(activity.content)).put("keyboardBounds", bounds(activity.keyboard))
                .put("pointerAvailable", activity.pointer.position.available)
                .put("pointerX", activity.pointer.position.x).put("pointerY", activity.pointer.position.y)
                .put("keyDispatchCount", keyDispatchCount).put("maxKeyDispatchMs", maxKeyDispatchMs)
                .put("lastConfirmedPadDispatchMs", activity.router.lastDispatchMs ?: JSONObject.NULL)
                .put("controls", JSONObject().apply { activity.controls.forEach { (tag, view) -> put(tag, bounds(view)) } })
                .put("copyRequestedAtNs", SystemClock.elapsedRealtimeNanos())
            PixelCopy.request(activity.window, bitmap, {
                code = it; observation.put("copyCompletedAtNs", SystemClock.elapsedRealtimeNanos()); copy.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        assertTrue("actual owned buffer copied", copy.await(3, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, code)
        scene.onActivity { activity ->
            activity.openFileOutput("local-browser-" + name + ".png", 0).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            activity.openFileOutput("local-browser-" + name + "-observation.json", 0).use { it.write(observation.toString().toByteArray()) }
            if (expectedError != null) {
                assertEquals(LocalBrowserSession.Phase.ERROR, activity.tabs.current.session.state.phase)
                assertEquals(expectedError, activity.tabs.current.session.state.error)
                val area = observation.getJSONArray("statusBounds")
                var lightPixels = 0
                for (y in area.getInt(1) + 4 until area.getInt(1) + area.getInt(3) - 4)
                    for (x in area.getInt(0) + 4 until area.getInt(0) + area.getInt(2) - 4) {
                        val color = bitmap.getPixel(x, y)
                        if (Color.red(color) >= 179 && Color.green(color) >= 179 && Color.blue(color) >= 179) lightPixels++
                    }
                assertTrue("current error text rendered in the copied buffer", lightPixels > 5)
            }
        }
        return bitmap
    }
    private inline fun Bitmap.checked(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
    private fun pagePixel(scene: ActivityScenario<LocalBrowserActivity>, shot: Bitmap,
                          selector: String, fractionX: Double, fractionY: Double): Int {
        val rect = JSONArray(js(scene, "(()=>{const r=document.querySelector(" + JSONObject.quote(selector) +
            ").getBoundingClientRect();return [r.x,r.y,r.width,r.height,innerWidth]})()"))
        var result = 0
        scene.onActivity {
            val page = checkNotNull(it.tabs.current.session.page); val xy = IntArray(2); page.getLocationOnScreen(xy)
            val scale = page.width / rect.getDouble(4)
            val x = ((rect.getDouble(0) + rect.getDouble(2) * fractionX) * scale).toInt()
            val y = ((rect.getDouble(1) + rect.getDouble(3) * fractionY) * scale).toInt()
            assertTrue("declared RGB point visible", x in 0 until page.width && y in 0 until page.height)
            result = shot.getPixel(xy[0] + x, xy[1] + y)
        }
        return result
    }

    private fun scene(body: (ActivityScenario<LocalBrowserActivity>, RawPoseReplay) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("LocalBrowserActivity", Context.MODE_PRIVATE)
        val previous = preferences.getString("local.tabs", null)
        val source = RawPoseReplay()
        val scenario = ActivityScenario.launch(LocalBrowserActivity::class.java)
        try {
            scenario.onActivity {
                // Only cold recovery metadata exists before this reserved Activity; no URL is loaded.
                while (it.tabs.count > 1) it.closeTab()
                it.closeTab()
                assertEquals(LocalBrowserSession.Phase.EMPTY, it.tabs.current.session.state.phase)
                it.pointer.stop(); it.pointer.replaceSourceForTest(source); it.pointer.start()
            }
            await("owned focused native window and fresh raw pose") {
                var ready = false
                scenario.onActivity { ready = it.hasWindowFocus() && it.pointer.inputPosition().available }
                ready
            }
            scenario.onActivity { source.adoptCurrentReference() }
            body(scenario, source)
        } finally {
            scenario.close()
            assertFalse("owned raw source settled", source.registered)
            // Preserve prior app recovery metadata in-process; no site/form values are recorded.
            assertTrue(preferences.edit().apply {
                if (previous == null) remove("local.tabs") else putString("local.tabs", previous)
            }.commit())
        }
    }
    private fun pad(scene: ActivityScenario<LocalBrowserActivity>, code: Int, count: Int = 1) {
        val device = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice).single { it.name == "ROKID,PSOC-TP-R" }
        repeat(count) {
            val down = SystemClock.uptimeMillis()
            fun event(action: Int) = KeyEvent(down, SystemClock.uptimeMillis(), action, code, 0, 0,
                device.id, 0, 0, InputDevice.SOURCE_KEYBOARD)
            scene.onActivity { assertTrue(it.dispatchKeyEvent(event(KeyEvent.ACTION_DOWN))) }
            SystemClock.sleep(20)
            scene.onActivity { assertTrue(it.dispatchKeyEvent(event(KeyEvent.ACTION_UP))) }
            if (count > 1) SystemClock.sleep(40)
        }
    }
    private fun aimState(activity: LocalBrowserActivity, point: PointerPosition): JSONObject {
        val bounds=activity.pointer.motionBounds
        return JSONObject().put("sampleNs",SystemClock.elapsedRealtimeNanos())
            .put("x",point.x).put("y",point.y).put("available",point.available)
            .put("bounds",JSONArray(listOf(bounds.left,bounds.top,bounds.right,bounds.bottom)))
            .put("gain",activity.inputSettings.sensitivity.gain).put("sourceRegistered",activity.pointer.sourceRegistered)
            .put("sourceReceiptNs",activity.pointer.lastSampleReceiptNs).put("expiresAtNs",activity.pointer.expiresAtNs)
            .put("windowFocused",activity.hasWindowFocus()).put("layoutRequested",activity.root.isLayoutRequested)
            .put("utility",activity.utility.name).put("selectedTab",activity.tabs.selectedIndex).put("tabCount",activity.tabs.count)
    }
    private fun aim(scene: ActivityScenario<LocalBrowserActivity>, source: RawPoseReplay, point: InputPoint) {
        val observation=JSONObject().put("targetX",point.x).put("targetY",point.y)
            .put("startedNs",SystemClock.elapsedRealtimeNanos())
        aimEvidence.put(observation)
        var failure: Throwable?=null
        var first: JSONObject?=null;var last: JSONObject?=null;var polls=0;var reached=false
        try {
            scene.onActivity {
                observation.put("before",aimState(it,it.pointer.inputPosition()))
                source.aimRelative(it, point)
                observation.put("dispatchReturnedNs",SystemClock.elapsedRealtimeNanos())
            }
            await("raw quaternion reaches current native target") {
                scene.onActivity {
                    val p=it.pointer.inputPosition()
                    reached=p.available && abs(p.x-point.x)<8 && abs(p.y-point.y)<8
                    last=aimState(it,p)
                }
                if(first==null) first=last
                polls++;reached
            }
        } catch(t: Throwable) { failure=t }
        finally {
            observation.put("finishedNs",SystemClock.elapsedRealtimeNanos()).put("reached",reached).put("polls",polls)
                .put("first",first ?: JSONObject.NULL).put("last",last ?: JSONObject.NULL)
                .put("failureClass",failure?.javaClass?.name ?: JSONObject.NULL)
            val arguments=InstrumentationRegistry.getArguments()
            val report=JSONObject().put("class",javaClass.name).put("method",testName.methodName)
                .put("invocationId",arguments.getString("evidenceRunId") ?: JSONObject.NULL)
                .put("candidateHead",arguments.getString("candidateHead") ?: JSONObject.NULL)
                .put("processPid",android.os.Process.myPid()).put("processUid",android.os.Process.myUid())
                .put("stage","aim-only; method result and scene cleanup remain in original JUnit/host evidence")
                .put("observations",aimEvidence)
            try {
                InstrumentationRegistry.getInstrumentation().targetContext.openFileOutput(
                    "local-browser-${testName.methodName}-aim-observations.json",0).use {
                    it.write(report.toString().toByteArray());it.fd.sync()
                }
            } catch(t: Throwable) { if(failure==null) failure=t else failure.addSuppressed(t) }
        }
        failure?.let { throw it }
    }

    private val keyboardEvidence = JSONArray()
    private fun observeNativeEvents(scene: ActivityScenario<LocalBrowserActivity>) {
        // Owned fixture observation only: no values, focus, selection or scroll assignments.
        js(scene,"""(()=>{window.fixtureNativeEvents=[];
            for(const type of ['pointerdown','pointerup','click','focusin'])
                document.addEventListener(type,e=>fixtureNativeEvents.push({type,target:e.target.id,
                    x:e.clientX,y:e.clientY,scrollY,viewport:innerHeight,at:performance.now()}),true);
        })()""")
    }
    private fun keyboardObservation(scene: ActivityScenario<LocalBrowserActivity>, stage: String) {
        // Values stay in the owned page. Reports contain geometry, lengths and native selection only.
        val row = JSONObject(js(scene, """(()=>{const e=document.activeElement,r=e.getBoundingClientRect();return {
            field:e.id,type:e.type||'plain',length:(e.value===undefined?e.textContent:e.value).length,
            selectionStart:e.selectionStart,selectionEnd:e.selectionEnd,
            bounds:[r.x,r.y,r.width,r.height],viewport:[innerWidth,innerHeight],
            inputs:fixtureInputs,submits:fixtureSubmits,nativeEvents:window.fixtureNativeEvents||[]}})()"""))
        scene.onActivity {
            row.put("stage",stage).put("observedAtNs",SystemClock.elapsedRealtimeNanos())
                .put("keyboardShown",it.keyboard.isShown).put("pageFocused",it.tabs.current.session.page?.hasFocus()==true)
                .put("addressFocused",it.address.hasFocus()).put("lastConfirmedPadDispatchMs",it.router.lastDispatchMs ?: JSONObject.NULL)
                .put("contentBounds",bounds(it.content))
                .put("keyboardBounds",bounds(it.keyboard)).put("nativeFocusClass",it.currentFocus?.javaClass?.simpleName)
        }
        keyboardEvidence.put(row)
    }
    private fun keyboardScene(body: (ActivityScenario<LocalBrowserActivity>,RawPoseReplay) -> Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("LocalBrowserActivity",Context.MODE_PRIVATE)
        val before=prefs.getString("local.tabs",null)
        val args=InstrumentationRegistry.getArguments()
        val started=SystemClock.elapsedRealtimeNanos()
        var source: RawPoseReplay?=null
        var failure: Throwable?=null
        var bodyFailure: Throwable?=null
        var completed=false
        try {
            assertFalse("fresh invocation binding",args.getString("evidenceRunId").isNullOrBlank())
            assertTrue("source binding",args.getString("candidateHead")?.matches(Regex("[0-9a-f]{40}"))==true)
            scene { active,replay ->
                source=replay
                try { body(active,replay);completed=true }
                catch(t: Throwable) {
                    bodyFailure=t
                    try { keyboardObservation(active,"failure") } catch(observation: Throwable) { t.addSuppressed(observation) }
                    try { capture(active,"keyboard-${testName.methodName}-failure").recycle() }
                    catch(capture: Throwable) { t.addSuppressed(capture) }
                    throw t
                }
            }
        } catch(t: Throwable) {
            failure=bodyFailure ?: t
            if(failure !== t) failure.addSuppressed(t)
        } finally {
            val cleanup=JSONObject()
            for((name,check) in listOf<Pair<String,() -> Unit>>(
                "rawSourceReleased" to { assertTrue(source?.registered != true) },
                "localTabsRestored" to { assertEquals(before,prefs.getString("local.tabs",null)) },
            )) try { check();cleanup.put(name,true) } catch(t: Throwable) {
                cleanup.put(name,false);if(failure==null) failure=t else failure.addSuppressed(t)
            }
            val report=JSONObject().put("class",javaClass.name).put("method",testName.methodName)
                .put("invocationId",args.getString("evidenceRunId")).put("candidateHead",args.getString("candidateHead"))
                .put("processPid",android.os.Process.myPid()).put("processUid",android.os.Process.myUid())
                .put("startedNs",started).put("finishedNs",SystemClock.elapsedRealtimeNanos())
                .put("bodyCompleted",completed).put("cleanup",cleanup)
                .put("failureClass",failure?.javaClass?.name ?: JSONObject.NULL)
                .put("suppressedFailureClasses",JSONArray(failure?.suppressed?.map { it.javaClass.name } ?: emptyList<String>()))
                .put("keyDispatchCount",keyDispatchCount).put("maxKeyDispatchMs",maxKeyDispatchMs)
                .put("observations",keyboardEvidence)
            try { context.openFileOutput("local-keyboard-${testName.methodName}-observations.json",0).use {
                it.write(report.toString().toByteArray());it.fd.sync()
            } } catch(t: Throwable) { if(failure==null) failure=t else failure.addSuppressed(t) }
        }
        failure?.let { throw it }
    }
    private fun focusEditor(scene: ActivityScenario<LocalBrowserActivity>, id: String) {
        element(scene,"#$id")
        await("current $id native editor and laid-out keyboard") {
            var native=false
            scene.onActivity { native=it.keyboard.isShown && !it.root.isLayoutRequested &&
                it.tabs.current.session.page?.hasFocus()==true }
            native && js(scene,"document.activeElement.id")==JSONObject.quote(id)
        }
        keyboardObservation(scene,"focused-$id")
    }
    private fun editorEquals(scene: ActivityScenario<LocalBrowserActivity>, id: String, expected: String): Boolean =
        js(scene,"(()=>{const e=document.getElementById('$id');return (e.value===undefined?e.innerText:e.value)==="+
            JSONObject.quote(expected)+"})()") == "true"
    private fun nativeEditorKey(scene: ActivityScenario<LocalBrowserActivity>, code: Int, meta: Int=0) {
        scene.onActivity {
            val page=checkNotNull(it.tabs.current.session.page)
            assertTrue(page.hasFocus() && page.isShown)
            val down=SystemClock.uptimeMillis()
            for(action in listOf(KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP)) {
                assertTrue("ordinary native selection/caret setup",page.dispatchKeyEvent(KeyEvent(down,
                    SystemClock.uptimeMillis(),action,code,0,meta)))
            }
        }
    }

    @Test fun fourNativeEditorsPreserveUnicodeSelectionCaseAndDone() = keyboardScene { scene,source ->
        open(scene,"local-keyboard.html");ready(scene)
        val identity=js(scene,"fixtureIdentity")
        for(id in listOf("text","password","multiline","plain")) {
            focusEditor(scene,id)
            assertTrue("declarative Unicode seed unchanged",editorEquals(scene,id,"aé中🙂z"))
            nativeEditorKey(scene,KeyEvent.KEYCODE_MOVE_END)
            key(scene,RgKeyboardKeys.Key.Command.BACKSPACE)
            await("native deletion preserves supplementary Unicode") { editorEquals(scene,id,"aé中🙂") }
            key(scene,RgKeyboardKeys.Key.Command.BACKSPACE)
            await("native deletion removes one supplementary character") { editorEquals(scene,id,"aé中") }
            nativeEditorKey(scene,KeyEvent.KEYCODE_A,KeyEvent.META_CTRL_ON)
            key(scene,RgKeyboardKeys.Key.Character("x"))
            await("built-in key replaces native selection") { editorEquals(scene,id,"x") }
            if(id=="text") {
                var point=InputPoint(0f,0f)
                scene.onActivity { point=center(it,it.keyButtons.getValue(RgKeyboardKeys.Key.Character("b"))) }
                aim(scene,source,point)
                val inputs=js(scene,"fixtureInputs").toInt()
                pad(scene,KeyEvent.KEYCODE_ENTER,2);SystemClock.sleep(700)
                assertTrue("double tap suppresses both native key actions",editorEquals(scene,id,"x"))
                assertEquals(inputs,js(scene,"fixtureInputs").toInt())
                pad(scene,KeyEvent.KEYCODE_ENTER)
                await("one recognized tap writes one actual built-in key") { editorEquals(scene,id,"xb") }
                assertEquals(inputs+1,js(scene,"fixtureInputs").toInt())
                scene.onActivity { assertTrue("recognized native key dispatch <=100ms",checkNotNull(it.router.lastDispatchMs)<=100) }
                keyboardObservation(scene,"recognized-key-single")
                key(scene,RgKeyboardKeys.Key.Command.BACKSPACE)
                await("normal native key remains usable after pad gesture") { editorEquals(scene,id,"x") }
            }
            if(id=="password") {
                assertEquals("\"password\"",js(scene,"document.activeElement.type"))
                keyboardObservation(scene,"password-after-key")
                capture(scene,"keyboard-password-early").recycle()
                SystemClock.sleep(2_000)
                capture(scene,"keyboard-password-later").recycle()
                keyboardObservation(scene,"password-after-mask-wait")
            }
            key(scene,RgKeyboardKeys.Key.Command.SHIFT);type(scene,"Q")
            key(scene,RgKeyboardKeys.Key.Command.SHIFT);type(scene,"7@")
            key(scene,RgKeyboardKeys.Key.Command.SYMBOLS);key(scene,RgKeyboardKeys.Key.Command.SPACE);type(scene,"z")
            await("actual case symbol and Space effects") { editorEquals(scene,id,"xQ7@ z") }
            nativeEditorKey(scene,KeyEvent.KEYCODE_DPAD_LEFT);type(scene,"b")
            await("built-in insertion uses native caret") { editorEquals(scene,id,"xQ7@ bz") }
            key(scene,RgKeyboardKeys.Key.Command.BACKSPACE)
            await("built-in Backspace uses current caret") { editorEquals(scene,id,"xQ7@ z") }
            nativeEditorKey(scene,KeyEvent.KEYCODE_MOVE_END)
            val submits=js(scene,"fixtureSubmits").toInt()
            key(scene,RgKeyboardKeys.Key.Command.ENTER)
            if(id in listOf("multiline","plain")) {
                type(scene,"b")
                await("native multiline Enter inserts a newline") { editorEquals(scene,id,"xQ7@ z\nb") }
            } else {
                await("ordinary single-line form action") { js(scene,"fixtureSubmits").toInt()==submits+if(id=="text") 1 else 0 }
                assertTrue("Enter preserves single-line content",editorEquals(scene,id,"xQ7@ z"))
            }
            keyboardObservation(scene,"edited-$id")
            capture(scene,"keyboard-$id").recycle()
            val submitted=js(scene,"fixtureSubmits")
            key(scene,RgKeyboardKeys.Key.Command.DONE)
            scene.onActivity { assertFalse(it.keyboard.isShown);assertTrue(it.tabs.current.session.page?.hasFocus()==true) }
            assertEquals("Done never deliberately submits",submitted,js(scene,"fixtureSubmits"))
            assertEquals("Done preserves current native field",JSONObject.quote(id),js(scene,"document.activeElement.id"))
            assertEquals("show/hide retains live document",identity,js(scene,"fixtureIdentity"))
            assertTrue("Done retains completed editing",editorEquals(scene,id,if(id in listOf("multiline","plain")) "xQ7@ z\nb" else "xQ7@ z"))
        }
    }

    @Test fun actualAddressKeysKeepDraftAndFixedToolbarUntilOpen() = keyboardScene { scene,_ ->
        open(scene,"local-keyboard.html");ready(scene)
        val identity=js(scene,"fixtureIdentity")
        val bookmarkFile=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"local-browser/bookmarks.properties")
        val url=fixtureBase+"/local-keyboard.html"
        var store: LocalBookmarks?=null
        var added=false
        scene.onActivity {
            store=it.bookmarks
            assertNull(it.bookmarks.error)
            assertFalse("owned fixture bookmark key must be absent; preserve unrelated entries",it.bookmarks.contains(url))
        }
        withOwnedBookmarkCleanup(body = {
        focusEditor(scene,"text") // Address mode must refresh Open even when the keyboard is already shown.
        control(scene,"address");scene.onActivity { it.address.selectAll() }
        val lower="qwertyuiopasdfghjklzxcvbnm"
        type(scene,"javascript:"+lower)
        key(scene,RgKeyboardKeys.Key.Command.SHIFT);type(scene,lower.uppercase());key(scene,RgKeyboardKeys.Key.Command.SHIFT)
        val symbols="0123456789:/.-_@?&=#%+,;!'\"()"
        type(scene,symbols);key(scene,RgKeyboardKeys.Key.Command.SPACE)
        key(scene,RgKeyboardKeys.Key.Command.BACKSPACE)
        val draft="javascript:"+lower+lower.uppercase()+symbols
        scene.onActivity {
            assertEquals(draft,it.address.text.toString());assertEquals(draft.length,it.address.selectionStart)
            assertTrue("full draft follows caret inside compact text slot",it.address.scrollX>0)
            assertEquals("[192,0,128,48]",bounds(it.address).toString())
            assertEquals("[320,0,48,48]",bounds(it.controls.getValue("hud.bookmark")).toString())
            assertEquals("[368,0,64,48]",bounds(it.controls.getValue("hud.tab_counter")).toString())
            assertEquals("[432,0,48,48]",bounds(it.controls.getValue("hud.more")).toString())
            assertEquals("[0,0,480,48]",bounds(it.toolbar).toString())
            assertEquals("Open",it.keyButtons.getValue(RgKeyboardKeys.Key.Command.ENTER).text.toString())
            assertEquals("Done",it.keyButtons.getValue(RgKeyboardKeys.Key.Command.DONE).text.toString())
        }
        key(scene,RgKeyboardKeys.Key.Command.ENTER)
        scene.onActivity { assertTrue(it.keyboard.isShown);assertEquals(draft,it.address.text.toString()) }
        assertEquals("invalid Open leaves page",identity,js(scene,"fixtureIdentity"))
        control(scene,"bookmark")
        scene.onActivity {
            added=it.bookmarks.contains(url)
            assertTrue("star saves committed page while draft is unsent",added)
            assertEquals(url,it.tabs.current.committedUrl)
            assertEquals(draft,it.address.text.toString())
            assertTrue(it.address.hasFocus() && it.keyboard.isShown)
            assertFalse(it.bookmarks.contains(draft))
            assertEquals("★",it.controls.getValue("hud.bookmark").text.toString())
        }
        keyboardObservation(scene,"invalid-address-open-and-star")
        capture(scene,"keyboard-address").recycle()
        key(scene,RgKeyboardKeys.Key.Command.DONE)
        scene.onActivity { assertFalse(it.keyboard.isShown) }
        assertEquals("address Done leaves page",identity,js(scene,"fixtureIdentity"))
        control(scene,"address")
        scene.onActivity { assertEquals("Done keeps unsent draft for correction",draft,it.address.text.toString());it.address.selectAll() }
        type(scene,fixtureBase+"/history.html");key(scene,RgKeyboardKeys.Key.Command.ENTER);ready(scene)
        assertEquals("valid Open navigates", "\"RG local history\"",js(scene,"document.title"))
        }, restoreAndRemove = {
            scene.onActivity {
                if(added) { removeOwnedBookmark(checkNotNull(store),bookmarkFile,url);added=false }
            }
        }, deleteBlocker = {})
    }

    @Test fun nativeFieldRevealSettlesOnceWithoutReloadOrFocusChange() = keyboardScene { scene,source ->
        open(scene,"local-keyboard.html");ready(scene)
        observeNativeEvents(scene)
        val identity=js(scene,"fixtureIdentity")
        // Admit the unchanged lower editor through ordinary head-edge scrolling before activation.
        scene.onActivity { source.aimRelative(it,InputPoint(240f,it.pointer.motionBounds.bottom)) }
        try {
            await("bottom-edge scroll brings the whole lower field inside the closed-keyboard viewport") {
                js(scene,"(()=>{const r=document.getElementById('lower').getBoundingClientRect();return r.top>=0 && r.bottom<=innerHeight-8})()") == "true"
            }
        } finally {
            scene.onActivity { source.aimRelative(it,center(it,it.root)) }
        }
        await("inward head movement stops the admission scroll") {
            var stopped=false
            scene.onActivity { stopped=!it.edgeScrollRunning && abs(it.pointer.inputPosition().y-320f)<8 }
            stopped
        }
        assertEquals("lower field still requires reveal after keyboard resize","true",
            js(scene,"(()=>{const r=document.getElementById('lower').getBoundingClientRect();return r.top>innerHeight*392/592 && r.bottom<=innerHeight})()"))
        focusEditor(scene,"lower")
        assertEquals("native tap focuses the selected editor without first-field focus", "[\"lower\"]",
            js(scene,"fixtureNativeEvents.filter(e=>e.type==='focusin').map(e=>e.target)"))
        await("native resize reveals selected field within page viewport") {
            js(scene,"(()=>{const e=document.activeElement,r=e.getBoundingClientRect();return e.id==='lower' && r.top>=0 && r.bottom<=innerHeight})()") == "true"
        }
        type(scene,"a")
        await("actual lower field effect") { editorEquals(scene,"lower","a") }
        keyboardObservation(scene,"lower-revealed")
        capture(scene,"keyboard-reveal").recycle()
        key(scene,RgKeyboardKeys.Key.Command.DONE)
        assertEquals(identity,js(scene,"fixtureIdentity"));assertTrue(editorEquals(scene,"lower","a"))
        assertEquals("\"lower\"",js(scene,"document.activeElement.id"))
        focusEditor(scene,"lower");nativeEditorKey(scene,KeyEvent.KEYCODE_MOVE_END);type(scene,"b")
        await("reshown keyboard edits the same current native focus") { editorEquals(scene,"lower","ab") }
        assertEquals(identity,js(scene,"fixtureIdentity"));assertEquals("0",js(scene,"fixtureSubmits"))
        keyboardObservation(scene,"lower-reshown")
    }

    @Test fun nativeDpadAdmissionPreservesScopeAndTabEffects() = scene { scene, _ ->
        val device = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice).single { it.name == "ROKID,PSOC-TP-R" }
        fun native(code: Int, scan: Int, repeat: Int = 0, meta: Int = 0, age: Long = 0, flags: Int = 0) {
            var down = 0L
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                scene.onActivity {
                    val at = SystemClock.uptimeMillis() - age
                    if (action == KeyEvent.ACTION_DOWN) down = at
                    // Actual native metadata observed on device3/source257, with fresh event/down times.
                    val event = KeyEvent(down, at, action, code, repeat, meta, device.id, scan, flags, 257)
                    assertEquals("ROKID,PSOC-TP-R", event.device?.name)
                    assertEquals(device.id, event.deviceId); assertEquals(scan, event.scanCode)
                    val start = SystemClock.uptimeMillis()
                    assertTrue("assigned horizontal sequence admitted", it.dispatchKeyEvent(event))
                    assertTrue("native dispatch <=100ms", SystemClock.uptimeMillis() - start <= 100)
                }
                if (action == KeyEvent.ACTION_DOWN) SystemClock.sleep(22)
            }
        }
        fun selected(index: Int) = scene.onActivity {
            assertEquals(index, it.tabs.selectedIndex)
            assertEquals("${index + 1}/4", it.controls.getValue("hud.tab_counter").text.toString())
        }
        open(scene, "keyboard.html"); ready(scene); focusText(scene)
        key(scene, RgKeyboardKeys.Key.Character("a")); key(scene, RgKeyboardKeys.Key.Command.DONE)
        val inputCount = js(scene, "fixtureInputs")
        repeat(3) { control(scene, "more"); tagged(scene, "menu.0"); key(scene, RgKeyboardKeys.Key.Command.DONE) }
        scene.onActivity { it.selectTab(1) }; selected(1)
        capture(scene, "native-dpad-before").recycle()
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106); selected(2)
        capture(scene, "native-dpad-after-right").recycle()
        native(KeyEvent.KEYCODE_DPAD_LEFT, 105); selected(1)
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106); native(KeyEvent.KEYCODE_DPAD_RIGHT, 106); selected(3)
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106); selected(3) // No wrap at last.
        repeat(3) { native(KeyEvent.KEYCODE_DPAD_LEFT, 105) }; selected(0)
        native(KeyEvent.KEYCODE_DPAD_LEFT, 105); selected(0) // No wrap at first.
        native(292, 183); selected(1); native(293, 184); selected(0) // Legacy OEM path.
        control(scene, "more")
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106); selected(0)
        scene.onActivity { assertEquals(LocalBrowserActivity.Utility.MORE, it.utility) }
        control(scene, "more")
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106, repeat = 1); selected(0)
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106, meta = KeyEvent.META_SHIFT_ON); selected(0)
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106, age = 300); selected(0)
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106, flags = KeyEvent.FLAG_CANCELED); selected(0)
        scene.onActivity { it.router.focus(false) }
        try { native(KeyEvent.KEYCODE_DPAD_RIGHT, 106); selected(0) }
        finally { scene.onActivity { it.router.focus(true) } }
        scene.moveToState(Lifecycle.State.CREATED)
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106); selected(0)
        scene.moveToState(Lifecycle.State.RESUMED)
        await("resumed owned native window and pointer") {
            var available = false
            scene.onActivity { available = it.hasWindowFocus() && it.pointer.inputPosition().available }
            available
        }
        focusText(scene)
        scene.onActivity {
            val now = SystemClock.uptimeMillis()
            for (code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
                assertFalse("other keyboard retains native input", it.router.key(KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                    code, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD)))
                assertFalse("non-keyboard source passes through", it.router.key(KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                    code, 0, 0, device.id, 0, 0, InputDevice.SOURCE_TOUCHSCREEN)))
            }
            for (code in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
                assertFalse("vertical input stays outside horizontal tab gestures", it.router.key(KeyEvent(now, now,
                    KeyEvent.ACTION_DOWN, code, 0, 0, device.id, if (code == KeyEvent.KEYCODE_DPAD_UP) 103 else 108, 0, 257)))
            }
            assertEquals(0, it.tabs.selectedIndex); assertTrue(it.keyboard.isShown)
        }
        assertEquals("\"a\"", js(scene, "document.getElementById('text').value"))
        assertEquals("admission guards do not edit", inputCount, js(scene, "fixtureInputs"))
        assertEquals("0", js(scene, "fixtureSubmits"))
        val editorObservations = JSONArray()
        fun observeEditor(stage: String) {
            val observation = JSONObject(js(scene, """(()=>{const e=document.getElementById('text');return {
                activeText:document.activeElement===e,textLength:e.value.length,
                selectionStart:e.selectionStart,selectionEnd:e.selectionEnd,
                expectedA:e.value==='a',expectedAB:e.value==='ab',
                inputs:fixtureInputs,submits:fixtureSubmits}})()"""))
            scene.onActivity {
                observation.put("stage", stage).put("observedAtNs", SystemClock.elapsedRealtimeNanos())
                    .put("phase", it.tabs.current.session.state.phase.name)
                    .put("pageShown", it.tabs.current.session.page?.isShown == true)
                    .put("pageFocused", it.tabs.current.session.page?.hasFocus() == true)
                    .put("keyboardShown", it.keyboard.isShown)
                    .put("nativeFocusClass", it.currentFocus?.javaClass?.simpleName)
                    .put("tabCount", it.tabs.count).put("selectedIndex", it.tabs.selectedIndex)
                editorObservations.put(observation)
                it.openFileOutput("local-browser-native-editor-observation.json", 0).use { file ->
                    file.write(editorObservations.toString().toByteArray())
                }
            }
        }
        observeEditor("after-refocus")
        // A refocus does not establish an append position. Dispatch the existing
        // native caret command; it has no button in this issue's compact keyboard.
        // Never assign a DOM value or selection.
        scene.onActivity {
            val start = SystemClock.uptimeMillis()
            assertTrue(it.input.key(RgKeyboardKeys.Key.Command.RIGHT))
            val elapsed = SystemClock.uptimeMillis() - start
            keyDispatchCount++; maxKeyDispatchMs = maxOf(maxKeyDispatchMs, elapsed)
            assertTrue("native caret dispatch <=100ms", elapsed <= 100)
        }
        await("native Right collapses the current caret at the unchanged field end") {
            js(scene, "(()=>{const e=document.getElementById('text');return document.activeElement===e && " +
                "e.value==='a' && e.selectionStart===1 && e.selectionEnd===1})()") == "true"
        }
        observeEditor("after-native-right")
        selected(0)
        assertEquals("native caret movement does not edit", inputCount, js(scene, "fixtureInputs"))
        assertEquals("0", js(scene, "fixtureSubmits"))
        try {
            key(scene, RgKeyboardKeys.Key.Character("b"))
            await("native current-focus keyboard appends at the observed caret") {
                js(scene, "document.getElementById('text').value") == "\"ab\""
            }
        } catch (failure: Throwable) {
            try { observeEditor("after-b-failure") }
            catch (observationFailure: Throwable) { failure.addSuppressed(observationFailure) }
            throw failure
        }
        observeEditor("after-b")
        assertEquals("native current-focus keyboard remains usable", "\"ab\"", js(scene, "document.getElementById('text').value"))
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106); selected(1)
        scene.onActivity { assertFalse("existing tab-switch keyboard dismissal", it.keyboard.isShown) }
        native(KeyEvent.KEYCODE_DPAD_LEFT, 105); selected(0)
        assertEquals("tab switch preserves actual field value", "\"ab\"", js(scene, "document.getElementById('text').value"))
        observeEditor("after-tab-return")
    }

    @Test fun approvedGeometryAndAddressEditingStayBlackAndRecoverable() = keyboardScene { scene, source ->
        fun geometry(editing: Boolean) {
            await("approved measured geometry after keyboard transition") {
                var settled = false
                scene.onActivity { settled = !it.root.isLayoutRequested && it.content.height == if (editing) 392 else 592 }
                settled
            }
            scene.onActivity {
            assertEquals("[0,0,480,640]", bounds(it.root).toString())
            assertEquals("[0,0,480,48]", bounds(it.toolbar).toString())
            val expected = linkedMapOf("close_tab" to Pair(0,48), "back" to Pair(48,48),
                "forward" to Pair(96,48), "refresh" to Pair(144,48), "address" to Pair(192,128),
                "bookmark" to Pair(320,48), "tab_counter" to Pair(368,64), "more" to Pair(432,48))
            expected.forEach { (name, slot) ->
                val control = it.controls.getValue("hud." + name)
                assertTrue(control.isShown)
                assertEquals(JSONArray(listOf(slot.first, 0, slot.second, 48)).toString(), bounds(control).toString())
                assertEquals(if (name in listOf("address", "tab_counter")) 20f else 24f, control.textSize, 0f)
            }
            assertEquals("[192,0,176,48]", bounds(it.root.findViewById(R.id.hud_address_slot)).toString())
            assertEquals(if (editing) "[0,48,480,392]" else "[0,48,480,592]", bounds(it.content).toString())
            if (editing) {
                assertEquals("[0,440,480,200]", bounds(it.keyboard).toString())
                assertEquals("Open", it.keyButtons.getValue(RgKeyboardKeys.Key.Command.ENTER).text.toString())
                assertEquals("Done", it.keyButtons.getValue(RgKeyboardKeys.Key.Command.DONE).text.toString())
                assertEquals("[296,588,96,48]", bounds(it.keyButtons.getValue(RgKeyboardKeys.Key.Command.ENTER)).toString())
                assertEquals("[396,588,76,48]", bounds(it.keyButtons.getValue(RgKeyboardKeys.Key.Command.DONE)).toString())
            }
            }
        }
        geometry(false)
        scene.onActivity {
            assertFalse(it.controls.getValue("hud.back").isEnabled)
            assertFalse(it.controls.getValue("hud.forward").isEnabled)
            assertFalse(it.controls.getValue("hud.refresh").isEnabled)
            assertFalse(it.controls.getValue("hud.bookmark").isEnabled)
        }
        capture(scene, "empty").checked { assertEquals(Color.BLACK, it.getPixel(450, 620)) }
        open(scene, "keyboard.html"); ready(scene)
        observeNativeEvents(scene)
        val document = js(scene, "fixtureIdentity")
        address(scene, "javascript:" + "a".repeat(48))
        key(scene, RgKeyboardKeys.Key.Command.ENTER)
        scene.onActivity { assertTrue(it.keyboard.isShown); assertTrue(it.address.hasFocus()) }
        geometry(true)
        assertEquals("invalid draft leaves live document unchanged", document, js(scene, "fixtureIdentity"))
        capture(scene, "address").checked {
            assertEquals("blank native keyboard margin", Color.BLACK, it.getPixel(2, 450))
            assertEquals(Color.BLACK, it.getPixel(390, 632))
        }
        val coveredField = elementPoint(scene, "#text")
        val inputsBeforeStatus = js(scene, "fixtureInputs")
        scene.onActivity {
            val status = it.root.findViewWithTag<View>("hud.status")
            val xy = IntArray(2); val root = IntArray(2)
            status.getLocationOnScreen(xy); it.root.getLocationOnScreen(root)
            assertTrue("declared field point is actually covered by visible status", status.isShown &&
                coveredField.x >= xy[0] - root[0] && coveredField.x < xy[0] - root[0] + status.width &&
                coveredField.y >= xy[1] - root[1] && coveredField.y < xy[1] - root[1] + status.height)
            assertTrue(it.input.activate(coveredField))
            assertTrue("owned error status consumes its covered-page tap", it.address.hasFocus())
        }
        assertEquals(inputsBeforeStatus, js(scene, "fixtureInputs"))
        assertNotEquals("\"text\"", js(scene, "document.activeElement.id"))
        key(scene, RgKeyboardKeys.Key.Command.DONE)
        geometry(false)
        scene.onActivity {
            assertEquals("compact browsing address remains the displayed location",
                it.tabs.current.session.state.url.removePrefix("http://"), it.address.text.toString())
        }
        element(scene, "a")
        await("actual link document") { js(scene, "document.title") == "\"RG local history\"" }; ready(scene)
        control(scene, "back")
        await("actual Back") { js(scene, "document.title") == "\"RG local editor fixture\"" }; ready(scene)
        control(scene, "forward")
        await("actual Forward") { js(scene, "document.title") == "\"RG local history\"" }; ready(scene)
        control(scene, "back")
        await("Back before Refresh") { js(scene, "document.title") == "\"RG local editor fixture\"" }; ready(scene)
        val before = js(scene, "fixtureIdentity")
        control(scene, "refresh")
        await("actual Refresh creates a new document") { js(scene, "fixtureIdentity") != before }; ready(scene)
        control(scene, "more")
        scene.onActivity {
            listOf("Add new tab", "Bookmarks", "QR scan", "Settings").forEachIndexed { i, label ->
                val row = it.root.findViewWithTag<TextView>("menu." + i)
                assertEquals(label, row.text.toString())
                assertEquals(JSONArray(listOf(256, 48 + i * 48, 224, 48)).toString(), bounds(row).toString())
            }
        }
        capture(scene, "more").checked { assertEquals(Color.BLACK, it.getPixel(470, 230)) }
        val inputs = js(scene, "fixtureInputs"); val submits = js(scene, "fixtureSubmits")
        element(scene, "#send") // Covered-page point only dismisses the menu.
        scene.onActivity { assertEquals(LocalBrowserActivity.Utility.BROWSING, it.utility) }
        assertEquals(inputs, js(scene, "fixtureInputs")); assertEquals(submits, js(scene, "fixtureSubmits"))
        control(scene, "more"); tagged(scene, "menu.2")
        scene.onActivity { assertEquals(LocalBrowserActivity.Utility.QR, it.utility); assertFalse(checkNotNull(it.tabs.current.session.page).isShown) }
        tagged(scene, "qr.cancel")
        control(scene, "more"); tagged(scene, "menu.3")
        scene.onActivity { assertEquals(LocalBrowserActivity.Utility.SETTINGS, it.utility) }
        tagged(scene, "utility.done")
        // Measured cursor bounds and unavailable indication, without a physical head-motion claim.
        control(scene, "close_tab")
        aim(scene, source, InputPoint(240f, 320f))
        scene.onActivity { source.flowing = false }
        await("raw tracking expires honestly") {
            var unavailable = false; scene.onActivity { unavailable = !it.pointer.inputPosition().available }; unavailable
        }
        capture(scene, "tracking-unavailable").checked { shot ->
            var p = InputPoint(0f, 0f)
            scene.onActivity { p = InputPoint(it.pointer.position.x, it.pointer.position.y) }
            val x = p.x.toInt(); val y = p.y.toInt()
            assertEquals("unavailable center dot", 0xff8a8a8a.toInt(), shot.getPixel(x, y))
            for (dy in -10..10) for (dx in -10..10) {
                if (abs(dx) > 8 || abs(dy) > 8) assertEquals(Color.BLACK, shot.getPixel(x + dx, y + dy))
            }
        }
    }

    @Test fun fourLiveTabsAndPadGesturesKeepCurrentNativeEffects() = scene { scene, source ->
        open(scene, "keyboard.html"); ready(scene); focusText(scene); type(scene, "a")
        await("first live editor effect") { js(scene, "document.getElementById('text').value") == "\"a\"" }
        key(scene, RgKeyboardKeys.Key.Command.DONE)
        val firstIdentity = js(scene, "fixtureIdentity")
        val firstInputs = js(scene, "fixtureInputs")
        val pages = mutableListOf<WebView>()
        scene.onActivity { pages.add(checkNotNull(it.tabs.current.session.page)) }
        repeat(3) { index ->
            control(scene, "more"); tagged(scene, "menu.0")
            scene.onActivity { assertTrue(it.keyboard.isShown); assertEquals(index + 2, it.tabs.count) }
            open(scene, "keyboard.html"); ready(scene)
            if (index == 0) {
                element(scene, "a")
                await("second tab owns a real history entry") { js(scene, "document.title") == "\"RG local history\"" }; ready(scene)
            }
            scene.onActivity { pages.add(checkNotNull(it.tabs.current.session.page)) }
        }
        scene.onActivity {
            assertEquals(4, pages.toSet().size)
            assertEquals("4/4", it.controls.getValue("hud.tab_counter").text.toString())
        }
        var more = InputPoint(0f,0f); scene.onActivity { more = center(it, it.controls.getValue("hud.more")) }
        aim(scene, source, more); pad(scene, KeyEvent.KEYCODE_ENTER)
        await("one confirmed pad tap opens More") { var shown = false; scene.onActivity { shown = it.utility == LocalBrowserActivity.Utility.MORE }; shown }
        scene.onActivity { assertTrue("confirmed native More dispatch <=100ms", checkNotNull(it.router.lastDispatchMs) <= 100) }
        SystemClock.sleep(700)
        var add = InputPoint(0f,0f); scene.onActivity { add = center(it, it.root.findViewWithTag("menu.0")) }
        aim(scene, source, add)
        pad(scene, KeyEvent.KEYCODE_ENTER, 2); SystemClock.sleep(700)
        scene.onActivity {
            assertEquals("double tap has no action", LocalBrowserActivity.Utility.MORE, it.utility)
            assertEquals("neither constituent tap adds a tab", 4, it.tabs.count)
        }
        pad(scene, KeyEvent.KEYCODE_ENTER)
        await("one confirmed Add creates exactly one fifth tab") {
            var added = false; scene.onActivity { added = it.tabs.count == 5 && it.keyboard.isShown }; added
        }
        scene.onActivity { assertTrue("confirmed native Add dispatch <=100ms", checkNotNull(it.router.lastDispatchMs) <= 100) }
        key(scene, RgKeyboardKeys.Key.Command.DONE); control(scene, "close_tab")
        scene.onActivity { assertEquals(4, it.tabs.count); assertSame(pages[3], it.tabs.current.session.page) }
        control(scene, "more")
        pad(scene, 293)
        scene.onActivity { assertEquals("utility swipes do not change hidden tabs", 3, it.tabs.selectedIndex) }
        control(scene, "more")
        pad(scene, 292) // Existing OEM forward binding; actual wearer-facing direction is a separate qualification.
        scene.onActivity { assertEquals("last boundary does not wrap", 3, it.tabs.selectedIndex) }
        repeat(3) { pad(scene, 293) }
        scene.onActivity { assertEquals(0, it.tabs.selectedIndex); assertSame(pages[0], it.tabs.current.session.page) }
        pad(scene, 293)
        scene.onActivity { assertEquals("first boundary does not wrap", 0, it.tabs.selectedIndex) }
        assertEquals(firstIdentity, js(scene, "fixtureIdentity"))
        assertEquals("\"a\"", js(scene, "document.getElementById('text').value"))
        assertEquals("swipes do not edit/click/submit", firstInputs, js(scene, "fixtureInputs"))
        assertEquals("0", js(scene, "fixtureSubmits"))
        scene.onActivity { assertEquals("no vertical scroll from swipes", 0, checkNotNull(it.tabs.current.session.page).scrollY) }
        focusText(scene)
        pad(scene, 292)
        scene.onActivity {
            assertEquals(1, it.tabs.selectedIndex); assertFalse("tab switch dismisses old keyboard", it.keyboard.isShown)
            assertFalse(pages[0].isShown); assertFalse(pages[0].hasFocus())
            assertSame(pages[1], it.tabs.current.session.page)
            assertTrue(it.controls.getValue("hud.back").isEnabled)
        }
        assertEquals("\"RG local history\"", js(scene, "document.title"))
        control(scene, "back")
        await("second tab retains its own Back") { js(scene, "document.title") == "\"RG local editor fixture\"" }; ready(scene)
        control(scene, "tab_counter")
        capture(scene, "four-tabs").checked { assertEquals(Color.BLACK, it.getPixel(450,620)) }
        tagged(scene, "tab.0")
        assertEquals(firstIdentity, js(scene, "fixtureIdentity"))
        control(scene, "close_tab")
        scene.onActivity { assertEquals(3, it.tabs.count); assertSame(pages[1], it.tabs.current.session.page) }
        control(scene, "tab_counter"); tagged(scene, "tab.2")
        control(scene, "close_tab")
        scene.onActivity { assertEquals(2, it.tabs.count); assertSame(pages[2], it.tabs.current.session.page) }
        control(scene, "close_tab"); control(scene, "close_tab")
        scene.onActivity {
            assertEquals(1, it.tabs.count); assertEquals("1/1", it.controls.getValue("hud.tab_counter").text.toString())
            assertEquals(LocalBrowserSession.Phase.EMPTY, it.tabs.current.session.state.phase)
        }
    }

    @Test fun bookmarksUseCommittedLocationAndDurableTruth() = scene { scene, source ->
        val url = fixtureBase + "/keyboard.html"
        var store: LocalBookmarks? = null
        var added = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val blocker = File.createTempFile("local-bookmark-failure-", ".owned", context.cacheDir)
        val bookmarkFile = File(context.filesDir, "local-browser/bookmarks.properties")
        withOwnedBookmarkCleanup(body = {
            open(scene, "keyboard.html"); ready(scene)
            scene.onActivity {
                store = it.bookmarks
                assertNull("normal bookmark storage readable", it.bookmarks.error)
                assertTrue("capture only owned dummy bookmarks; preserve any unexpected preexisting entries", it.bookmarks.entries.isEmpty())
            }
            address(scene, "https://invalid.example/unsent")
            control(scene, "bookmark")
            scene.onActivity {
                added = it.bookmarks.contains(url)
                assertTrue("actual durable bookmark result", added)
                assertTrue(it.address.hasFocus()); assertTrue(it.keyboard.isShown)
                assertEquals("https://invalid.example/unsent", it.address.text.toString())
                assertEquals(url, it.tabs.current.committedUrl)
                assertEquals("★", it.controls.getValue("hud.bookmark").text.toString())
                val disk = LocalBookmarks(File(it.filesDir, "local-browser/bookmarks.properties"))
                assertTrue(disk.contains(url)); assertFalse(disk.contains(it.address.text.toString()))
            }
            capture(scene, "bookmarked-draft").checked { assertEquals(Color.BLACK, it.getPixel(2,450)) }
            key(scene, RgKeyboardKeys.Key.Command.DONE)
            scene.recreate()
            scene.onActivity {
                store = it.bookmarks
                assertEquals("cold recovery does not replay navigation/form state", LocalBrowserSession.Phase.EMPTY, it.tabs.current.session.state.phase)
                assertEquals(url, it.tabs.current.recoveryUrl)
                assertFalse(checkNotNull(it.tabs.current.session.page).isShown)
                assertFalse(it.keyboard.isShown)
                assertTrue("bookmark survived actual Activity recreation", it.bookmarks.contains(url))
                it.pointer.stop(); it.pointer.replaceSourceForTest(source); it.pointer.start()
            }
            await("recreated owned window and pose resumed") {
                var focused = false
                scene.onActivity { focused = it.hasWindowFocus() && it.pointer.inputPosition().available }
                focused
            }
            capture(scene, "cold-recovery").checked { assertEquals(Color.BLACK, it.getPixel(450,620)) }
            control(scene, "more"); tagged(scene, "menu.1")
            capture(scene, "bookmarks").checked { assertEquals(Color.BLACK, it.getPixel(450,620)) }
            var index = 0
            scene.onActivity { index = it.bookmarks.entries.indexOfFirst { b -> b.url == url }; assertTrue(index >= 0) }
            tagged(scene, "bookmark.open." + index); ready(scene)
            await("bookmark opens the saved page instead of the draft") { js(scene, "document.title") == "\"RG local editor fixture\"" }
            open(scene, "does-not-exist.html")
            await("bookmarked tab reaches actual HTTP error") {
                var failed = false
                scene.onActivity { failed = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.ERROR }
                failed
            }
            scene.onActivity {
                assertFalse(it.controls.getValue("hud.bookmark").isEnabled)
                assertEquals("non-navigable page retains disabled outline, even with a prior saved page",
                    "☆", it.controls.getValue("hud.bookmark").text.toString())
            }
            control(scene, "more"); tagged(scene, "menu.1")
            tagged(scene, "bookmark.open." + index); ready(scene)
            control(scene, "more"); tagged(scene, "menu.1")
            tagged(scene, "bookmark.remove." + index)
            scene.onActivity {
                confirmOwnedBookmarkRemoved(it.bookmarks, bookmarkFile, url)
                added = false // Keep ownership until both memory and readable disk confirm removal.
                assertEquals("☆", it.controls.getValue("hud.bookmark").text.toString())
            }
            tagged(scene, "utility.done")
            scene.onActivity { it.bookmarks = LocalBookmarks(File(blocker, "bookmarks.properties")) }
            control(scene, "bookmark")
            scene.onActivity {
                assertFalse(it.bookmarks.contains(url)); assertNotNull(it.bookmarks.error)
                assertEquals("☆", it.controls.getValue("hud.bookmark").text.toString())
            }
            control(scene, "more"); tagged(scene, "menu.1")
            capture(scene, "bookmark-error").checked { assertEquals(Color.BLACK, it.getPixel(450,620)) }
            tagged(scene, "bookmarks.retry")
            scene.onActivity { assertNull(it.bookmarks.error); assertTrue(it.bookmarks.entries.isEmpty()) }
            capture(scene, "bookmarks-empty").checked { assertEquals(Color.BLACK, it.getPixel(450,620)) }
            tagged(scene, "utility.done")
        }, restoreAndRemove = {
            scene.onActivity {
                store?.let { original ->
                    it.bookmarks = original
                    if (added) {
                        removeOwnedBookmark(original, bookmarkFile, url)
                        added = false
                    }
                }
            }
        }, deleteBlocker = {
            assertTrue("remove only owned failure file", blocker.delete())
        })
    }

    @Test fun httpErrorControlsRecoverThroughNativeHistoryRefreshAndAddress() = scene { scene, _ ->
        fun error(name: String) {
            await("current ordinary main-frame HTTP404") {
                var failed = false; scene.onActivity {
                    failed = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.ERROR &&
                        it.tabs.current.session.state.error == "HTTP error 404"
                }; failed
            }
            scene.onActivity { assertFalse(checkNotNull(it.tabs.current.session.page).isShown) }
            capture(scene, name, expectedError = "HTTP error 404").recycle()
        }
        open(scene, "history.html"); ready(scene)
        open(scene, "does-not-exist.html"); error("http-initial-error")
        control(scene, "back"); ready(scene)
        scene.onActivity { assertTrue(it.tabs.current.session.state.url.endsWith("history.html")) }
        control(scene, "forward"); error("http-forward-error")
        control(scene, "refresh"); error("http-refresh-error")
        control(scene, "more"); tagged(scene, "menu.3"); tagged(scene, "utility.done")
        open(scene, "keyboard.html"); ready(scene)
        assertEquals("0", js(scene, "fixtureSubmits"))
        capture(scene, "http-controls-recovered").recycle()
    }

    @Test fun liveBlackAndHiddenRecoveryUseProductionWindow() = scene { scene, _ ->
        open(scene, "author-light.html"); ready(scene)
        await("unchanged media fixture loaded") { js(scene, "document.getElementById('media-image').complete") == "true" }
        capture(scene, "author-light").checked {
            assertEquals(Color.BLACK, pagePixel(scene, it, "input", .9, .5))
            assertEquals(0xffe04040.toInt(), pagePixel(scene, it, "#media-image", .5, .5))
            // H1.2's outer toolbar edge is an intentional outline; sample the black interior.
            assertEquals(Color.BLACK, it.getPixel(470,44))
        }
        element(scene, "button")
        await("authored dynamic handler ran") { js(scene, "document.getElementById('dynamic').style.backgroundColor") == "\"rgb(221, 221, 221)\"" }
        capture(scene, "dynamic").checked { assertEquals(Color.BLACK, pagePixel(scene, it, "#dynamic", .9, .8)) }
        open(scene, "keyboard.html"); ready(scene)
        focusText(scene); type(scene, "a")
        await("current field native effect") { js(scene, "document.getElementById('text').value") == "\"a\"" }
        val identity = js(scene, "fixtureIdentity")
        val inputs = js(scene, "fixtureInputs"); val submits = js(scene, "fixtureSubmits")
        capture(scene, "field").checked {
            assertEquals(Color.BLACK, pagePixel(scene, it, "#text", .9, .5))
            assertEquals(Color.BLACK, it.getPixel(2,450))
        }
        key(scene, RgKeyboardKeys.Key.Command.DONE)
        assertEquals(submits, js(scene, "fixtureSubmits"))
        scene.moveToState(Lifecycle.State.CREATED)
        scene.onActivity { assertFalse(it.pointer.sourceRegistered) }
        scene.moveToState(Lifecycle.State.RESUMED)
        await("owned focused window resumed") { var focus = false; scene.onActivity { focus = it.hasWindowFocus() }; focus }
        assertEquals(identity, js(scene, "fixtureIdentity")); assertEquals("\"a\"", js(scene, "document.getElementById('text').value"))
        val field = elementPoint(scene, "#text"); val submit = elementPoint(scene, "#send")
        scene.onActivity { assertTrue(it.tabs.current.session.open(fixtureBase + "/loading.html").accepted()) }
        scene.onActivity {
            val page = checkNotNull(it.tabs.current.session.page)
            assertEquals(LocalBrowserSession.Phase.LOADING, it.tabs.current.session.state.phase)
            assertFalse(page.isShown); assertFalse(page.hasFocus()); assertFalse(page.requestFocus())
            assertFalse(it.input.scroll(80)); it.root.requestFocus(); it.input.activate(field)
            assertFalse(it.input.key(RgKeyboardKeys.Key.Character("z")))
            assertFalse(it.input.key(RgKeyboardKeys.Key.Command.ENTER)); it.input.activate(submit)
        }
        assertEquals("\"a\"", js(scene, "document.getElementById('text').value"))
        assertEquals(inputs, js(scene, "fixtureInputs")); assertEquals(submits, js(scene, "fixtureSubmits"))
        capture(scene, "loading").checked {
            assertEquals(Color.BLACK, it.getPixel(450,300)); assertEquals(Color.BLACK, it.getPixel(450,620))
        }
        val httpStates = JSONArray()
        fun observeHttp(activity: LocalBrowserActivity, stage: String) {
            val state = activity.tabs.current.session.state
            httpStates.put(JSONObject().put("stage", stage).put("atNs", SystemClock.elapsedRealtimeNanos())
                .put("phase", state.phase.name).put("url", state.url).put("error", state.error)
                .put("callback", JSONArray(Thread.currentThread().stackTrace.filter {
                    it.className.startsWith(LocalBrowserSession::class.java.name)
                }.map { it.methodName }))
                .put("hiddenSubmitPoint", JSONArray(listOf(submit.x, submit.y))))
            activity.openFileOutput("local-browser-http-states.json", 0).use { it.write(httpStates.toString().toByteArray()) }
        }
        scene.onActivity { activity ->
            val session = activity.tabs.current.session
            val previous = session.onStateChanged
            session.onStateChanged = { state -> previous(state); observeHttp(activity, "session-callback") }
        }
        open(scene, "does-not-exist.html")
        await("actual fixed-fixture main-frame HTTP404") {
            var failed = false
            scene.onActivity { failed = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.ERROR &&
                it.tabs.current.session.state.error == "HTTP error 404" }
            failed
        }
        scene.onActivity {
            observeHttp(it, "before-hidden-error-input")
            val page = checkNotNull(it.tabs.current.session.page)
            assertFalse(page.isShown); assertFalse(page.hasFocus()); assertFalse(page.requestFocus())
            it.root.requestFocus(); it.input.activate(submit)
            assertFalse(it.input.key(RgKeyboardKeys.Key.Command.ENTER))
            assertTrue(it.address.isShown && it.address.isEnabled && it.controls.getValue("hud.more").isEnabled)
            observeHttp(it, "after-hidden-error-input")
        }
        capture(scene, "http-error", expectedError = "HTTP error 404").checked { assertEquals(Color.BLACK, it.getPixel(450,620)) }
        open(scene, "keyboard.html")
        await("actual authored replacement blank field") { js(scene, "document.getElementById('text')?.value") == "\"\"" }
        ready(scene); focusText(scene)
        val recovery = JSONArray()
        fun observeRecovery(stage: String) {
            val observation = JSONObject(js(scene, """(()=>{const e=document.getElementById('text');return {
                activeText:document.activeElement===e,emptyText:e?.value==='',expectedB:e?.value==='b',
                inputs:fixtureInputs,submits:fixtureSubmits}})()"""))
            scene.onActivity {
                observation.put("stage", stage).put("observedAtNs", SystemClock.elapsedRealtimeNanos())
                    .put("phase", it.tabs.current.session.state.phase.name)
                    .put("pageShown", it.tabs.current.session.page?.isShown == true)
                    .put("pageFocused", it.tabs.current.session.page?.hasFocus() == true)
                    .put("keyboardShown", it.keyboard.isShown)
                    .put("keyDispatchCount", keyDispatchCount).put("maxKeyDispatchMs", maxKeyDispatchMs)
                    .put("nativeFocusClass", it.currentFocus?.javaClass?.simpleName)
                recovery.put(observation)
                it.openFileOutput("local-browser-recovery-input-observation.json", 0).use { file ->
                    file.write(recovery.toString().toByteArray())
                }
            }
        }
        observeRecovery("before-b")
        try {
            type(scene, "b")
            await("visible recovery restores native field input") { js(scene, "document.getElementById('text').value") == "\"b\"" }
        } finally { observeRecovery("after-b") }
        key(scene, RgKeyboardKeys.Key.Command.DONE)
        assertEquals("0", js(scene, "fixtureSubmits"))
        ready(scene)
        capture(scene, "http-explicit-recovery").recycle()
    }
}
