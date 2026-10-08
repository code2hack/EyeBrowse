package com.code2hack.eyebrowse.rg

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Scoped native-input checks, not HUD/engine qualification or a stale-target/parity suite. */
@RunWith(AndroidJUnit4::class)
class NativeInputInstrumentedTest {
    private fun await(label: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 3_000
        while (SystemClock.uptimeMillis() < end) { if (condition()) return; SystemClock.sleep(20) }
        fail(label)
    }
    private fun js(scenario: ActivityScenario<NativeInputTestActivity>, expression: String): String {
        val done = CountDownLatch(1); var result = ""
        scenario.onActivity { it.page.evaluateJavascript(expression) { value -> result = value; done.countDown() } }
        assertTrue("fixture observation", done.await(3, TimeUnit.SECONDS))
        return result
    }
    private fun center(activity: NativeInputTestActivity, view: View): InputPoint {
        val child = IntArray(2); val root = IntArray(2)
        view.getLocationOnScreen(child); activity.root.getLocationOnScreen(root)
        return InputPoint(child[0] - root[0] + view.width / 2f, child[1] - root[1] + view.height / 2f)
    }
    private fun tap(scenario: ActivityScenario<NativeInputTestActivity>, source: RawPoseReplay, point: InputPoint,
                    count: Int = 1) {
        scenario.onActivity { source.aim(it, point) }
        await("raw pose aims at native target") {
            var aimed = false
            scenario.onActivity { val p = it.pointer.inputPosition(); aimed = p.available &&
                kotlin.math.abs(p.x - point.x) < 8 && kotlin.math.abs(p.y - point.y) < 8 }
            aimed
        }
        val pad = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice).first { it.name == "ROKID,PSOC-TP-R" }
        repeat(count) {
            val down = SystemClock.uptimeMillis()
            fun event(phase: Int) = KeyEvent(down, SystemClock.uptimeMillis(), phase, KeyEvent.KEYCODE_ENTER,
                0, 0, pad.id, 0, 0, InputDevice.SOURCE_KEYBOARD)
            scenario.onActivity { assertTrue(it.dispatchKeyEvent(event(KeyEvent.ACTION_DOWN))) }
            SystemClock.sleep(20)
            scenario.onActivity { assertTrue(it.dispatchKeyEvent(event(KeyEvent.ACTION_UP))) }
            if (count > 1) SystemClock.sleep(40)
        }
    }
    private fun scene(body: (ActivityScenario<NativeInputTestActivity>, RawPoseReplay) -> Unit) {
        val scenario = ActivityScenario.launch(NativeInputTestActivity::class.java)
        val source = RawPoseReplay()
        try {
            scenario.onActivity { it.pointer.stop(); it.pointer.replaceSourceForTest(source); it.pointer.start() }
            await("native window and fresh pose") {
                var ready = false
                scenario.onActivity { ready = it.hasWindowFocus() && it.pointer.inputPosition().available && it.pageReady }
                ready
            }
            scenario.onActivity { source.adoptCurrentReference() }
            body(scenario, source)
        } finally { scenario.close() }
        assertFalse("owned source released", source.registered)
    }

    @Test fun ordinaryPadGestureActivatesNativeViewExactlyOnce() = scene { scenario, source ->
        var point = InputPoint(0f, 0f)
        scenario.onActivity { point = center(it, it.button) }
        tap(scenario, source, point)
        await("native button effect") { var done = false; scenario.onActivity { done = it.activations == 1 }; done }
        SystemClock.sleep(400)
        tap(scenario, source, point, count = 2)
        SystemClock.sleep(700)
        scenario.onActivity {
            assertEquals(1, it.activations)
            assertTrue("native dispatch <=100ms", checkNotNull(it.router.lastDispatchMs) <= 100)
            Log.i("EyeBrowseNativeInput", "activationCount=${it.activations} dispatchMs=${it.router.lastDispatchMs}")
        }
        scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse("paused native source", source.registered)
        scenario.moveToState(Lifecycle.State.RESUMED)
        await("native resume") { var ready = false; scenario.onActivity { ready = it.hasWindowFocus() && it.pointer.inputPosition().available }; ready }
        scenario.onActivity { assertEquals("resume does not activate", 1, it.activations) }
    }

    @Test fun builtInKeysUseNativeWebViewFocusAndDoneDoesNotEdit() = scene { scenario, source ->
        fun field(id: String) {
            val rect = JSONArray(js(scenario, "(()=>{const r=document.getElementById('$id').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2]})()"))
            var point = InputPoint(0f, 0f)
            scenario.onActivity {
                val origin = IntArray(2); val root = IntArray(2)
                it.page.getLocationOnScreen(origin); it.root.getLocationOnScreen(root)
                val scale = it.resources.displayMetrics.density
                point = InputPoint(origin[0] - root[0] + rect.getDouble(0).toFloat() * scale,
                    origin[1] - root[1] + rect.getDouble(1).toFloat() * scale)
            }
            tap(scenario, source, point)
            await("native WebView field focus") { js(scenario, "document.activeElement.id") == "\"$id\"" }
        }
        fun key(key: RgKeyboardKeys.Key) {
            var point = InputPoint(0f, 0f)
            scenario.onActivity { assertTrue(it.buttons.getValue(key).isShown); point = center(it, it.buttons.getValue(key)) }
            tap(scenario, source, point)
            SystemClock.sleep(700) // Existing double-tap recognition interval, not a deferred key queue.
            scenario.onActivity { assertTrue("native key dispatch <=100ms", it.keyDispatchMs <= 100) }
        }
        field("text"); key(RgKeyboardKeys.Key.Character("x"))
        await("actual text effect") { js(scenario, "document.getElementById('text').value") == "\"x\"" }
        key(RgKeyboardKeys.Key.Command.BACKSPACE)
        await("native backspace") { js(scenario, "document.getElementById('text').value.length") == "0" }
        field("multiline"); key(RgKeyboardKeys.Key.Command.ENTER)
        await("native multiline Enter") { js(scenario, "document.getElementById('multiline').value") == "\"\\n\"" }
        field("password"); repeat(3) { key(RgKeyboardKeys.Key.Character("x")) }
        await("password edit without a local value mirror") { js(scenario, "document.getElementById('password').value.length") == "3" }
        assertEquals("\"password\"", js(scenario, "document.activeElement.type"))
        val shot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        scenario.onActivity { activity -> activity.openFileOutput("native-input-password.png", 0).use {
            assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it))
        } }
        shot.recycle() // Real rendered evidence; masking must also be inspected, not inferred from input.type.
        key(RgKeyboardKeys.Key.Command.DONE)
        scenario.onActivity { assertEquals(1, it.dismissals); assertEquals(View.GONE, it.keyboard.visibility) }
        assertEquals("3", js(scenario, "document.getElementById('password').value.length"))
        var before = 0
        scenario.onActivity { before = it.page.scrollY; it.input.scroll(80) }
        await("actual native page scroll") { var changed = false; scenario.onActivity { changed = it.page.scrollY > before }; changed }
        scenario.onActivity { Log.i("EyeBrowseNativeInput", "focusedNativeKeys=true doneWithoutEdit=true scrollY=${it.page.scrollY} keyDispatchMs=${it.keyDispatchMs} maskingCapture=native-input-password.png") }
    }
}
