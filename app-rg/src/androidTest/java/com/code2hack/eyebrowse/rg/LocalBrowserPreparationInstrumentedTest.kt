package com.code2hack.eyebrowse.rg

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Requires reserved RG + the #28 owned HTTP fixture. No device or service bootstrap here. */
@RunWith(AndroidJUnit4::class)
class LocalBrowserPreparationInstrumentedTest {
    private val fixtureBase = checkNotNull(InstrumentationRegistry.getArguments().getString("fixtureBaseUrl")) {
        "Pass the reserved, reachable owned fixtureBaseUrl; this suite never starts services"
    }.trimEnd('/')

    private fun await(label: String, bound: Long = 10_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + bound
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(25)
        }
        fail(label)
    }

    private fun js(scene: ActivityScenario<LocalBrowserPreparationActivity>, expression: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        scene.onActivity { checkNotNull(it.session.page).evaluateJavascript(expression) { value -> result = value; latch.countDown() } }
        assertTrue("owned fixture observation", latch.await(3, TimeUnit.SECONDS))
        return result
    }

    private fun center(activity: LocalBrowserPreparationActivity, view: View): InputPoint {
        val child = IntArray(2); val root = IntArray(2)
        view.getLocationOnScreen(child); activity.root.getLocationOnScreen(root)
        return InputPoint(child[0] - root[0] + view.width / 2f, child[1] - root[1] + view.height / 2f)
    }

    private fun key(scene: ActivityScenario<LocalBrowserPreparationActivity>, key: RgKeyboardKeys.Key) {
        scene.onActivity {
            val button = it.keyButtons.getValue(key)
            assertTrue("visible real built-in preparation key", button.isShown)
            assertTrue(it.input.activate(center(it, button)))
        }
    }

    private fun type(scene: ActivityScenario<LocalBrowserPreparationActivity>, text: String) {
        for (character in text) {
            val key = RgKeyboardKeys.Key.Character(character.toString())
            scene.onActivity {
                if (key !in it.keyButtons) it.keyButtons.getValue(RgKeyboardKeys.Key.Command.SYMBOLS).performClick()
                assertTrue("character exists in current built-in layer", key in it.keyButtons)
            }
            key(scene, key)
        }
    }

    private fun address(scene: ActivityScenario<LocalBrowserPreparationActivity>, text: String) {
        scene.onActivity {
            assertTrue(it.input.activate(center(it, it.address)))
            it.address.selectAll() // Ordinary native editor selection, not expected page-data assignment.
        }
        type(scene, text)
    }

    private fun open(scene: ActivityScenario<LocalBrowserPreparationActivity>, route: String) {
        address(scene, "$fixtureBase/$route")
        key(scene, RgKeyboardKeys.Key.Command.ENTER)
    }

    private fun ready(scene: ActivityScenario<LocalBrowserPreparationActivity>, bound: Long = 10_000) {
        await("live page styled and presented", bound) {
            var ready = false
            scene.onActivity { ready = it.session.state.phase == LocalBrowserSession.Phase.READY && it.session.page?.alpha == 1f }
            ready
        }
    }

    private fun tapElement(scene: ActivityScenario<LocalBrowserPreparationActivity>, selector: String) {
        val rect = JSONArray(js(scene, """(()=>{const r=document.querySelector(${JSONObject.quote(selector)}).getBoundingClientRect();return [r.x,r.y,r.width,r.height,innerWidth]})()"""))
        scene.onActivity {
            val page = checkNotNull(it.session.page)
            val origin = IntArray(2); val root = IntArray(2)
            page.getLocationOnScreen(origin); it.root.getLocationOnScreen(root)
            val scale = page.width / rect.getDouble(4)
            val x = (rect.getDouble(0) + rect.getDouble(2) / 2) * scale
            val y = (rect.getDouble(1) + rect.getDouble(3) / 2) * scale
            assertTrue("owned element visible in current viewport", x in 0.0..page.width.toDouble() && y in 0.0..page.height.toDouble())
            assertTrue(it.input.activate(InputPoint(origin[0] - root[0] + x.toFloat(), origin[1] - root[1] + y.toFloat())))
        }
    }

    private fun capture(scene: ActivityScenario<LocalBrowserPreparationActivity>, name: String): Bitmap {
        val visual = CountDownLatch(1)
        scene.onActivity {
            if (it.session.state.phase == LocalBrowserSession.Phase.READY) {
                checkNotNull(it.session.page).postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) { visual.countDown() }
                })
            } else visual.countDown() // Pending/error backing is native UI, not a settled page claim.
        }
        assertTrue("actual settled page visual callback", visual.await(3, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scene.onActivity { assertTrue("capture only owned focused window", it.hasWindowFocus()) }
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        assertEquals(480, bitmap.width); assertEquals(640, bitmap.height)
        scene.onActivity { activity ->
            activity.openFileOutput("local-preparation-$name.png", 0).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        return bitmap
    }

    private fun screenshotPixel(scene: ActivityScenario<LocalBrowserPreparationActivity>, shot: Bitmap,
                                selector: String, fractionX: Double, fractionY: Double): Int {
        val rect = JSONArray(js(scene, """(()=>{const r=document.querySelector(${JSONObject.quote(selector)}).getBoundingClientRect();return [r.x,r.y,r.width,r.height,innerWidth]})()"""))
        var color = 0
        scene.onActivity {
            val page = checkNotNull(it.session.page); val origin = IntArray(2); page.getLocationOnScreen(origin)
            val scale = page.width / rect.getDouble(4)
            val x = ((rect.getDouble(0) + rect.getDouble(2) * fractionX) * scale).toInt()
            val y = ((rect.getDouble(1) + rect.getDouble(3) * fractionY) * scale).toInt()
            assertTrue("predeclared sample lies in visible page", x in 0 until page.width && y in 0 until page.height)
            color = shot.getPixel(origin[0] + x, origin[1] + y)
        }
        return color
    }

    private fun scene(block: (ActivityScenario<LocalBrowserPreparationActivity>) -> Unit) {
        ActivityScenario.launch(LocalBrowserPreparationActivity::class.java).use { scene ->
            scene.onActivity {
                assertEquals(LocalBrowserSession.Phase.EMPTY, it.session.state.phase)
                assertFalse(it.controls.getValue("Back").isEnabled)
                assertFalse(it.controls.getValue("Forward").isEnabled)
            }
            block(scene)
        }
    }

    @Test fun localNavigationPreservesInvalidDraftAndUsesActualHistory() = scene { scene ->
        open(scene, "keyboard.html"); ready(scene)
        val initial = js(scene, "fixtureIdentity")
        address(scene, "javascript:alert(1)")
        key(scene, RgKeyboardKeys.Key.Command.ENTER)
        scene.onActivity {
            assertEquals("javascript:alert(1)", it.address.text.toString())
            assertTrue(it.keyboard.isShown)
            assertEquals(LocalBrowserSession.Phase.READY, it.session.state.phase)
        }
        assertEquals("invalid draft does not reload the page", initial, js(scene, "fixtureIdentity"))
        key(scene, RgKeyboardKeys.Key.Command.DONE)
        tapElement(scene, "a")
        await("actual link navigation") { js(scene, "document.title") == "\"RG local history\"" }
        ready(scene)
        scene.onActivity { assertTrue(it.controls.getValue("Back").performClick()) }
        await("actual Back document") { js(scene, "document.title") == "\"RG local editor fixture\"" }; ready(scene)
        scene.onActivity { assertTrue(it.controls.getValue("Forward").performClick()) }
        await("actual Forward document") { js(scene, "document.title") == "\"RG local history\"" }; ready(scene)
        scene.onActivity { it.controls.getValue("Back").performClick() }
        await("Back reaches editor before Refresh") { js(scene, "document.title") == "\"RG local editor fixture\"" }
        ready(scene)
        val before = js(scene, "fixtureIdentity")
        scene.onActivity { it.controls.getValue("Refresh").performClick() }
        await("actual refreshed live document") { js(scene, "fixtureIdentity") != before }; ready(scene)
    }

    @Test fun currentNativeFocusAndDonePreserveEditingAcrossPauseResume() = scene { scene ->
        open(scene, "keyboard.html"); ready(scene)
        val identity = js(scene, "fixtureIdentity")
        tapElement(scene, "#text")
        await("native text field focus") { js(scene, "document.activeElement.id") == "\"text\"" }
        scene.onActivity { it.controls.getValue("Keys").performClick() }
        type(scene, "a")
        await("real built-in native text effect") { js(scene, "document.getElementById('text').value") == "\"a\"" }
        key(scene, RgKeyboardKeys.Key.Command.DONE)
        scene.onActivity { assertFalse(it.keyboard.isShown) }
        assertEquals("\"a\"", js(scene, "document.getElementById('text').value"))
        val submits = js(scene, "fixtureSubmits")
        scene.moveToState(Lifecycle.State.CREATED)
        scene.onActivity { assertFalse("paused sensor listener", it.pointer.sourceRegistered) }
        scene.moveToState(Lifecycle.State.RESUMED)
        await("normal focused window resumes") { var focused = false; scene.onActivity { focused = it.hasWindowFocus() }; focused }
        assertEquals(identity, js(scene, "fixtureIdentity"))
        assertEquals(submits, js(scene, "fixtureSubmits"))
        assertEquals("\"a\"", js(scene, "document.getElementById('text').value"))
    }

    @Test fun liveBlackPresentationKeepsFieldsDynamicUpdatesAndMediaColors() = scene { scene ->
        open(scene, "author-light.html"); ready(scene)
        await("real fixture media loaded") { js(scene, "document.getElementById('media-image').complete") == "true" }
        capture(scene, "author-light").useBitmap { shot ->
            assertEquals(Color.BLACK, screenshotPixel(scene, shot, "input", .9, .5))
            assertEquals(0xffe04040.toInt(), screenshotPixel(scene, shot, "#media-image", .5, .5))
            assertEquals("native black app edge", Color.BLACK, shot.getPixel(479, 20))
        }
        tapElement(scene, "button")
        await("fixture's actual dynamic handler ran") {
            js(scene, "document.getElementById('dynamic').style.backgroundColor") == "\"rgb(221, 221, 221)\""
        }
        capture(scene, "dynamic").useBitmap { shot ->
            assertEquals(Color.BLACK, screenshotPixel(scene, shot, "#dynamic", .9, .8))
        }
        open(scene, "keyboard.html"); ready(scene)
        capture(scene, "author-dark").useBitmap { shot ->
            var x = 0; var y = 0
            scene.onActivity {
                val page = checkNotNull(it.session.page); val origin = IntArray(2); page.getLocationOnScreen(origin)
                x = origin[0] + (page.width * .9).toInt(); y = origin[1] + (page.height * .9).toInt()
            }
            assertEquals(Color.BLACK, shot.getPixel(x, y))
        }
    }

    @Test fun pendingLoadAndHttpFailureLeaveBlackRecoverableLocalControls() = scene { scene ->
        open(scene, "loading.html")
        scene.onActivity { assertEquals(LocalBrowserSession.Phase.LOADING, it.session.state.phase) }
        capture(scene, "loading").useBitmap { shot ->
            assertEquals(Color.BLACK, shot.getPixel(450, 300))
            assertEquals(Color.BLACK, shot.getPixel(450, 620))
        }
        ready(scene, 20_000)
        open(scene, "does-not-exist.html")
        await("actual owned HTTP failure") { var failed = false; scene.onActivity { failed = it.session.state.phase == LocalBrowserSession.Phase.ERROR }; failed }
        scene.onActivity {
            assertTrue(it.status.text.startsWith("HTTP error"))
            assertTrue(it.controls.getValue("Open").isEnabled)
        }
        capture(scene, "error").useBitmap { shot -> assertEquals(Color.BLACK, shot.getPixel(450, 620)) }
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
}
