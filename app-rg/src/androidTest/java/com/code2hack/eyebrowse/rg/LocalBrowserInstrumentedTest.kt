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
            ").getBoundingClientRect();return [r.x,r.y,r.width,r.height,innerWidth]})()"))
        var point = InputPoint(0f, 0f)
        scene.onActivity {
            val page = checkNotNull(it.tabs.current.session.page)
            val origin = IntArray(2); val root = IntArray(2)
            page.getLocationOnScreen(origin); it.root.getLocationOnScreen(root)
            val scale = page.width / rect.getDouble(4)
            val x = (rect.getDouble(0) + rect.getDouble(2) / 2) * scale
            val y = (rect.getDouble(1) + rect.getDouble(3) / 2) * scale
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
    private fun capture(scene: ActivityScenario<LocalBrowserActivity>, name: String): Bitmap {
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
    private fun aim(scene: ActivityScenario<LocalBrowserActivity>, source: RawPoseReplay, point: InputPoint) {
        scene.onActivity { source.aim(it, point) }
        await("raw quaternion reaches current native target") {
            var aimed = false
            scene.onActivity {
                val p = it.pointer.inputPosition()
                aimed = p.available && abs(p.x - point.x) < 8 && abs(p.y - point.y) < 8
            }
            aimed
        }
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
        key(scene, RgKeyboardKeys.Key.Character("b"))
        assertEquals("native current-focus keyboard remains usable", "\"ab\"", js(scene, "document.getElementById('text').value"))
        native(KeyEvent.KEYCODE_DPAD_RIGHT, 106); selected(1)
        scene.onActivity { assertFalse("existing tab-switch keyboard dismissal", it.keyboard.isShown) }
        native(KeyEvent.KEYCODE_DPAD_LEFT, 105); selected(0)
        assertEquals("tab switch preserves actual field value", "\"ab\"", js(scene, "document.getElementById('text').value"))
    }

    @Test fun approvedGeometryAndAddressEditingStayBlackAndRecoverable() = scene { scene, source ->
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
        scene.onActivity { assertEquals(LocalBrowserActivity.Utility.QR_PENDING, it.utility); assertFalse(checkNotNull(it.tabs.current.session.page).isShown) }
        tagged(scene, "utility.done")
        control(scene, "more"); tagged(scene, "menu.3")
        scene.onActivity { assertEquals(LocalBrowserActivity.Utility.SETTINGS_PENDING, it.utility) }
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
        open(scene, "does-not-exist.html")
        await("actual fixed-fixture main-frame HTTP404") {
            var failed = false
            scene.onActivity { failed = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.ERROR &&
                it.tabs.current.session.state.error == "HTTP error 404" }
            failed
        }
        scene.onActivity {
            val page = checkNotNull(it.tabs.current.session.page)
            assertFalse(page.isShown); assertFalse(page.hasFocus()); assertFalse(page.requestFocus())
            it.root.requestFocus(); it.input.activate(submit)
            assertFalse(it.input.key(RgKeyboardKeys.Key.Command.ENTER))
            assertTrue(it.address.isShown && it.address.isEnabled && it.controls.getValue("hud.more").isEnabled)
        }
        capture(scene, "http-error").checked { assertEquals(Color.BLACK, it.getPixel(450,620)) }
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
    }
}
