package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Paired with the Phone oracle. Test files coordinate assertions, never browser input. */
@RunWith(AndroidJUnit4::class)
class KeyboardJourneyTest {
    @Test fun actualKeysEditThePairedPhoneAndRetireStaleIntents() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val j = PointerBrowserJourneyTest.Journey(scenario)
        val phase = File(j.app.cacheDir,"kbd-${j.mission}.phase")
        val ack = File(j.app.cacheDir,"kbd-${j.mission}.ack")
        val fixture = InstrumentationRegistry.getArguments().getString("fixtureBaseUrl","http://127.0.0.1:26341") + "/keyboard.html?case=" + j.mission
        fun buttons(view: View): List<Button> = when (view) {
            is Button -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
            else -> emptyList()
        }
        fun keyView(key: RgKeyboard.Key): Button = buttons(j.activity.findViewById(R.id.rg_keyboard_container))
            .first { (it.tag as? RgKeyboard.Intent)?.key == key }
        fun checkPhone(name: String) {
            phase.writeText(name)
            val end = SystemClock.elapsedRealtime()+15_000
            while (SystemClock.elapsedRealtime()<end && (!ack.exists() || ack.readText().trim()!=name)) SystemClock.sleep(30)
            assertEquals("Phone independently verified $name",name,ack.takeIf { it.exists() }?.readText()?.trim())
        }
        fun press(key: RgKeyboard.Key, pointer: Boolean = true) {
            j.await("key is eligible",3_000) { j.peer.canKey(key) }
            val before = j.peer.lastActionResult
            var remote = false
            j.main { remote = j.peer.keyboard.destination == RgKeyboard.Destination.FIELD && key !in listOf(RgKeyboard.Key.Command.SHIFT,RgKeyboard.Key.Command.SYMBOLS,RgKeyboard.Key.Command.DONE) }
            if (pointer) {
                lateinit var point: InputPoint
                j.main { point = j.nativeCenter(keyView(key)) }
                j.aim(point);j.dispatched(j.pad(),"keyboard")
            } else j.main { val view=keyView(key);assertTrue(view.isEnabled);assertTrue(view.performClick()) }
            if (remote) j.await("Phone edit outcome",2_000) { j.peer.lastActionResult !== before }
            if (remote) assertTrue("Phone admitted key",j.peer.lastActionResult?.accepted==true)
            SystemClock.sleep(40) // Local view publication only, not a product success oracle.
        }
        fun character(char: Char, pointer: Boolean = true) {
            if (char == ' ') { press(RgKeyboard.Key.Command.SPACE,pointer);return }
            val letter = char.isLetter()
            if (letter && j.peer.keyboard.symbols) press(RgKeyboard.Key.Command.SYMBOLS,pointer)
            if (letter && char.isUpperCase()!=j.peer.keyboard.uppercase) press(RgKeyboard.Key.Command.SHIFT,pointer)
            if (j.peer.keyboard.rows().flatten().none { it == RgKeyboard.Key.Character(char.toString()) }) press(RgKeyboard.Key.Command.SYMBOLS,pointer)
            press(RgKeyboard.Key.Character(char.toString()),pointer)
        }
        fun clearAddress() { while(j.peer.keyboard.draft.isNotEmpty()) press(RgKeyboard.Key.Command.BACKSPACE,false) }
        fun readyKeyboard() = j.await("fresh resized field grant",2_000) { j.peer.keyboard.visible && j.peer.canKey(RgKeyboard.Key.Character("a")) && j.peer.canAct() }
        fun field(id: String) {
            fun point() = JSONObject(checkNotNull(j.peer.browserState()?.title).substringAfter("KBD|")).getJSONArray(id)
            j.await("fresh page coordinates") { j.peer.canAct() && j.peer.browserState()?.title?.startsWith("KBD|")==true }
            var swipes = 0
            while (point().getDouble(1) !in 0.0..<1.0) {
                assertTrue("fixture field must be reachable with bounded pad scrolling",swipes++ < 4)
                val beforeY = point().getDouble(1)
                val beforeCapture = checkNotNull(j.peer.lastFrameHeader).captureTsMs
                val beforeResult = j.peer.lastActionResult
                val trace = j.dispatched(j.pad(if (beforeY >= 1.0) 292 else 293),"reveal-field")
                j.await("scroll admitted without uncertain replay",2_000) { j.peer.lastActionResult !== beforeResult }
                assertTrue(j.peer.lastActionResult?.accepted == true)
                assertNull(j.peer.lastActionResult?.reason)
                j.await("scroll updates fixture geometry and current frame",2_000) {
                    j.peer.canAct() && point().getDouble(1) != beforeY &&
                        (j.peer.lastFrameHeader?.captureTsMs ?: -1) > beforeCapture
                }
                // Honor the production swipe/tap suppression interval; never retry a suppressed tap.
                val remaining = trace.confirmedAt + j.activity.inputRouter.doubleTapMs + 1 - SystemClock.uptimeMillis()
                if (remaining > 0) SystemClock.sleep(remaining)
            }
            val target = point()
            assertTrue("fixture field is horizontally inside the page",target.getDouble(0) in 0.0..<1.0)
            val profile = checkNotNull(j.peer.profile())
            j.aim(j.pageRoot((target.getDouble(0)*profile.width).toFloat(),(target.getDouble(1)*profile.height).toFloat()))
            j.dispatched(j.pad(),"field")
            readyKeyboard()
        }
        fun done() {
            press(RgKeyboard.Key.Command.DONE)
            j.await("Done restores current page",2_000) { !j.peer.keyboard.visible && j.peer.canAct() }
        }
        fun screenshot(name: String) {
            val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            File(j.app.cacheDir,"kbd-${j.mission}-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        var completed = false
        try {
            j.setup(); j.native(R.id.rg_retry,"Retry")
            j.await("paired Phone state",10_000) { j.peer.browserState()?.owner==ControlOwner.PHONE && j.peer.canHandoff() }
            j.handoff(ControlOwner.RG,"A")
            j.native(R.id.rg_detail,"address")
            j.await("address keyboard",2_000) { j.peer.keyboard.visible && j.peer.canSubmitAddress() }
            clearAddress()
            "not an address".forEach { character(it,false) }
            val draft = j.peer.keyboard.draft
            val before = j.peer.lastActionResult
            press(RgKeyboard.Key.Command.ENTER)
            j.await("address rejection") { j.peer.lastActionResult !== before }
            assertEquals("ADDRESS_REJECTED",j.peer.lastActionResult?.reason)
            assertEquals(draft,j.peer.keyboard.draft);assertTrue(j.peer.keyboard.visible)
            checkPhone("invalid_address")
            j.native(R.id.rg_detail,"existing address draft")
            assertEquals("reopening address does not erase correction",draft,j.peer.keyboard.draft)
            lateinit var stale: RgKeyboard.Intent
            j.main {
                stale=j.peer.keyboard.capture(RgKeyboard.Key.Character("x"))
                assertTrue(keyView(RgKeyboard.Key.Command.DONE).performClick())
                assertTrue(it.findViewById<Button>(R.id.rg_detail).performClick())
                assertFalse("closed-session key cannot enter a successor",j.peer.key(stale))
            }
            j.await("coalesced close-open layout settles",2_000) { j.peer.canSubmitAddress() }
            checkPhone("coalesced_layout")
            clearAddress();fixture.forEach { character(it,false) }
            val beforeNavigationContext = checkNotNull(j.peer.browserState()).context
            val beforeNavigationActions = j.actions
            lateinit var receipts: KeyboardNavigationReceipts
            j.main { receipts=KeyboardNavigationReceipts(it) }
            try {
                press(RgKeyboard.Key.Command.ENTER)
                receipts.capture("await-start boundMs=5000")
                j.await("real address navigation",5_000) {
                    receipts.capture("await-predicate",onlyChange=true)
                    !j.peer.keyboard.visible && j.peer.canAct() && j.peer.browserState()?.title?.startsWith("KBD|")==true
                }
                receipts.capture("await-passed")
                val current = checkNotNull(j.peer.browserState())
                assertNotEquals(beforeNavigationContext.documentId,current.context.documentId)
                assertEquals("navigation keeps the ordinal namespace",beforeNavigationContext.controlEpoch,current.context.controlEpoch)
                assertEquals(beforeNavigationContext.lifetimeId,current.context.lifetimeId)
                assertEquals("one address effect, never replayed",beforeNavigationActions.consumed+1,j.actions.consumed)
                assertEquals("one address enqueue, never replayed",beforeNavigationActions.queued+1,j.actions.queued)
                assertEquals(j.peer.profile(),current.profile)
                assertEquals(current.context,j.peer.lastFrameHeader?.context)
                assertTrue("fresh-document viewport keeps the original deadline",receipts.preservedDeadlineFor(current.context.documentId))
            } catch (failure: Throwable) {
                receipts.capture("navigation-scope-failed type=${failure.javaClass.simpleName}")
                throw failure
            } finally { j.main { receipts.close() } }
            checkPhone("address_opened")

            field("text")
            screenshot("field")
            // Real double tap on a key must not produce either constituent character.
            lateinit var keyPoint: InputPoint
            j.main { keyPoint=j.nativeCenter(keyView(RgKeyboard.Key.Character("a"))) }
            j.aim(keyPoint);val count=j.actions
            j.pad();SystemClock.sleep(40);j.pad();j.confirmWindow()
            assertEquals(count,j.actions);checkPhone("double_tap")
            j.await("Reading dismisses keyboard and receives fresh geometry",2_000) {
                j.peer.reading && !j.peer.keyboard.visible && j.peer.canAct()
            }
            screenshot("reading")
            j.pad();j.confirmWindow() // Isolated Reading tap has no page/key effect.
            assertEquals(count,j.actions)
            j.pad();SystemClock.sleep(40);j.pad();j.confirmWindow()
            j.await("Normal returns without keyboard reopen",2_000) {
                !j.peer.reading && !j.peer.keyboard.visible && j.peer.canAct()
            }
            field("text")
            val afterReactivation=j.actions
            j.main { keyPoint=j.nativeCenter(keyView(RgKeyboard.Key.Character("a"))) }
            j.aim(keyPoint)
            // A pending lowercase key cannot become an uppercase key after the layer changes.
            j.pad();j.main { keyView(RgKeyboard.Key.Command.SHIFT).performClick() };j.confirmWindow()
            assertEquals(afterReactivation,j.actions);checkPhone("stale_case")
            character('a');character('B');character('1')
            press(RgKeyboard.Key.Command.SPACE);press(RgKeyboard.Key.Command.BACKSPACE)
            press(RgKeyboard.Key.Command.ENTER);checkPhone("text_submit")
            if (j.peer.keyboard.symbols) press(RgKeyboard.Key.Command.SYMBOLS)
            lateinit var pendingPoint: InputPoint
            j.main { pendingPoint=j.nativeCenter(keyView(j.peer.keyboard.rows().first().first())) }
            j.aim(pendingPoint);val beforeDismiss=j.actions
            j.pad()
            j.main { val done=keyView(RgKeyboard.Key.Command.DONE);assertTrue(done.isEnabled);assertTrue(done.performClick()) }
            j.confirmWindow()
            assertEquals("Done invalidates a key awaiting confirmation",beforeDismiss,j.actions)
            j.await("Done restores fresh geometry",2_000) { !j.peer.keyboard.visible && j.peer.canAct() }
            checkPhone("dismiss_pending")
            field("password");character('p')
            val passwordFrameBeforeFinalKey = j.peer.lastFrameHeader?.captureTsMs ?: -1
            character('7')
            val captureDeadline = SystemClock.uptimeMillis()+1_000
            var newerPasswordFrame = false
            while (SystemClock.uptimeMillis()<captureDeadline && !newerPasswordFrame) {
                j.main { newerPasswordFrame = (j.peer.lastFrameHeader?.captureTsMs ?: -1) > passwordFrameBeforeFinalKey }
                if (!newerPasswordFrame) SystemClock.sleep(20)
            }
            Log.i("EyeBrowseKeyboardTest","PASSWORD_CAPTURE newerFrame=$newerPasswordFrame visualMaskingRequiresInspection=true")
            screenshot("password");done();checkPhone("password")
            field("multiline");character('m');press(RgKeyboard.Key.Command.ENTER);character('n');done();checkPhone("multiline")
            field("plain");character('e');done();checkPhone("plain")
            field("text");checkPhone("invalidate")
            j.await("readonly field closes",2_000) { !j.peer.keyboard.visible && j.peer.canAct() }
            checkPhone("invalidated")
            screenshot("normal")
            j.handoff(ControlOwner.PHONE,"A");checkPhone("return")
            completed = true
            phase.writeText("complete")
            Log.i("EyeBrowseKeyboardTest","RG_KBD_PASS mission=${j.mission} pointerKeys=true addressNativeKeys=true")
        } finally {
            if (!completed) phase.writeText("abort")
            j.main { j.probe?.close() };scenario.close()
            // The host relay removes these exact mission files after both terminal results.
            ack.delete()
        }
    }
}
