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
    @Test fun actualKeysEditThePairedPhoneAndRetireStaleIntents() = journey(false)
    @Test fun livePixelsAndNonemptyModeContinuity() = journey(true)
    @Test fun submissionPublishesFreshMarker() = journey(false,true)
    private fun journey(reviewEvidence: Boolean, submitEvidence: Boolean = false) {
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
        fun maskedDots(): Int {
            val geometry=JSONObject(checkNotNull(j.peer.browserState()?.title).substringAfter("KBD|")).getJSONArray("password")
            val bitmap=(j.activity.findViewById<android.widget.ImageView>(R.id.rg_page).drawable as android.graphics.drawable.BitmapDrawable).bitmap
            val width=geometry.getDouble(2)*bitmap.width;val height=geometry.getDouble(3)*bitmap.height
            val left=((geometry.getDouble(0)*bitmap.width)-width/2+8).toInt()
            val top=((geometry.getDouble(1)*bitmap.height)-height/2+8).toInt()
            val right=(left+width-16).toInt();val bottom=(top+height-16).toInt()
            check(left>=0 && top>=0 && right<=bitmap.width && bottom<=bitmap.height && right>left && bottom>top) { "password pixel region outside current frame" }
            val w=right-left;val h=bottom-top;val dark=BooleanArray(w*h)
            for(y in 0 until h)for(x in 0 until w) {
                val c=bitmap.getPixel(left+x,top+y)
                dark[y*w+x]=((c shr 16) and 255)<100 && ((c shr 8) and 255)<100 && (c and 255)<100
            }
            var dots=0
            val queue=java.util.ArrayDeque<Int>()
            for(start in dark.indices)if(dark[start]) {
                var minX=w;var maxX=0;var minY=h;var maxY=0;var area=0
                dark[start]=false;queue.add(start)
                while(!queue.isEmpty()) {
                    val at=queue.removeFirst();val x=at%w;val y=at/w;area++
                    minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)
                    for(next in intArrayOf(if(x>0)at-1 else -1,if(x+1<w)at+1 else -1,if(y>0)at-w else -1,if(y+1<h)at+w else -1))
                        if(next>=0 && dark[next]) { dark[next]=false;queue.add(next) }
                }
                val cw=maxX-minX+1;val ch=maxY-minY+1
                // Round dense mask glyphs; reject the thin caret and the field border.
                if(cw>=3 && ch>=3 && ch<h/2 && cw.toDouble()/ch in .65..1.5 && area>=cw*ch*.6)dots++
            }
            return dots
        }
        fun markerPixels(green: Boolean): Boolean {
            val rect=JSONObject(checkNotNull(j.peer.browserState()?.title).substringAfter("KBD|")).getJSONArray("submitMarker")
            val bitmap=(j.activity.findViewById<android.widget.ImageView>(R.id.rg_page).drawable as android.graphics.drawable.BitmapDrawable).bitmap
            for (dy in listOf(-.2,0.0,.2)) for (dx in listOf(-.2,0.0,.2)) {
                val x=((rect.getDouble(0)+dx*rect.getDouble(2))*bitmap.width).toInt()
                val y=((rect.getDouble(1)+dy*rect.getDouble(3))*bitmap.height).toInt()
                check(x in 0 until bitmap.width && y in 0 until bitmap.height) { "submit marker outside current frame" }
                val c=bitmap.getPixel(x,y);val r=(c shr 16) and 255;val g=(c shr 8) and 255;val b=c and 255
                if (if(green) !(r<80 && g>100 && b<120) else !(r>120 && g<80 && b<100))return false
            }
            return true
        }
        var completed = false
        try {
            j.setup(); j.native(R.id.rg_retry,"Retry")
            j.await("paired Phone state",10_000) { j.peer.browserState()?.owner==ControlOwner.PHONE && j.peer.canHandoff() }
            j.handoff(ControlOwner.RG,"A")
            if(submitEvidence) {
                field("text")
                var lastCapture=checkNotNull(j.peer.lastFrameHeader).captureTsMs
                var quietSince=SystemClock.uptimeMillis()
                j.await("prior captures quiescent",3_000) {
                    val current=checkNotNull(j.peer.lastFrameHeader).captureTsMs
                    if(current!=lastCapture) { lastCapture=current;quietSince=SystemClock.uptimeMillis() }
                    j.peer.canAct() && SystemClock.uptimeMillis()-quietSince>=300
                }
                j.main { assertTrue("initial marker pixels",markerPixels(false)) }
                checkPhone("submit_ready")
                val context=checkNotNull(j.peer.browserState()).context
                val beforeFrame=checkNotNull(j.peer.lastFrameHeader).captureTsMs
                press(RgKeyboard.Key.Command.ENTER)
                val deadline=SystemClock.uptimeMillis()+1_000
                assertEquals("SUBMISSION_REQUESTED",j.peer.lastActionResult?.reason)
                checkPhone("submit_applied")
                var fresh=false;var marker=false;var open=false;var observedAt=Long.MAX_VALUE
                while(SystemClock.uptimeMillis()<deadline && !(fresh && marker && open)) {
                    j.main {
                        open=j.peer.keyboard.visible
                        fresh=(j.peer.lastFrameHeader?.captureTsMs ?: -1)>beforeFrame
                        marker=markerPixels(true);observedAt=SystemClock.uptimeMillis()
                    }
                    if(!(fresh && marker && open))SystemClock.sleep(20)
                }
                Log.i("EyeBrowseKeyboardTest","SUBMIT_PIXELS submissionBranch=true fresh=$fresh markerUpdated=$marker keyboardOpen=$open inBudget=${observedAt<=deadline}")
                assertTrue("submit handler must produce fresh RG marker without further input",fresh && marker && open && observedAt<=deadline)
                assertEquals(context,j.peer.browserState()?.context)
                screenshot("submit-live");checkPhone("submit_pixels")
                completed=true;phase.writeText("complete");return
            }
            if (reviewEvidence) {
                field("plain");character('a');character('B');character('1')
                checkPhone("filled_before")
                val document=checkNotNull(j.peer.browserState()).context.documentId
                val beforeMode=j.actions
                j.pad(291);j.confirmWindow()
                j.await("filled Reading fresh geometry",2_000) { j.peer.reading && !j.peer.keyboard.visible && j.peer.canAct() }
                assertEquals(beforeMode,j.actions);assertEquals(document,j.peer.browserState()?.context?.documentId)
                checkPhone("filled_reading");screenshot("filled-reading")
                j.pad(291);j.confirmWindow()
                j.await("filled Normal without editor reopen",2_000) { !j.peer.reading && !j.peer.keyboard.visible && j.peer.canAct() }
                assertEquals(beforeMode,j.actions);assertEquals(document,j.peer.browserState()?.context?.documentId)
                checkPhone("filled_normal")
                field("password")
                var emptyDots=-1
                j.main { emptyDots=maskedDots() }
                assertTrue("empty focused password has no mask glyphs",emptyDots==0)
                checkPhone("live_password_before")
                val beforeFrame=checkNotNull(j.peer.lastFrameHeader).captureTsMs
                character('p');character('7')
                val deadline=SystemClock.uptimeMillis()+1_000
                checkPhone("live_password_applied")
                var fresh=false;var dots=-1;var open=false
                while(SystemClock.uptimeMillis()<deadline && !(fresh && dots==2 && open)) {
                    j.main {
                        open=j.peer.keyboard.visible
                        fresh=(j.peer.lastFrameHeader?.captureTsMs ?: -1)>beforeFrame
                        dots=maskedDots()
                    }
                    if(!(fresh && dots==2 && open))SystemClock.sleep(20)
                }
                Log.i("EyeBrowseKeyboardTest","LIVE_PASSWORD fresh=$fresh twoMaskGlyphs=${dots==2} keyboardOpen=$open boundMs=1000")
                assertTrue("password edits must produce fresh masked RG pixels with keyboard open",fresh && dots==2 && open)
                screenshot("password-live");checkPhone("live_password_pixels")
                completed=true;phase.writeText("complete");return
            }
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
