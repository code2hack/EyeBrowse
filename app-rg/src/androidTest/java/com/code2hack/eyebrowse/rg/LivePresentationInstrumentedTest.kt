package com.code2hack.eyebrowse.rg

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.util.Log
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LivePresentationInstrumentedTest {
    @Test fun directLanLiveFramesUseMeasuredContentProfile() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java))
        lateinit var controller: RgPresentationController
        try {
            scenario.onActivity { controller = it.presentation; controller.reconnect() }
            await("authenticated browser state",10_000) { controller.browserState() != null && controller.profile() != null }
            val profile = controller.profile()!!
            Log.i("EyeBrowseT02", "RG_PROFILE ${profile.width}x${profile.height}@${profile.densityDpi}")
            val start = SystemClock.elapsedRealtime()
            scenario.onActivity { assertTrue(controller.requestPresentation()) }
            var sequence = -1L
            var capture = -1L
            var firstContext: com.code2hack.eyebrowse.core.link.control.ControlContext? = null
            var qualifiedAt: Long? = null
            val pixelHashes = mutableSetOf<Int>()
            fun examine() {
                scenario.onActivity { activity ->
                    val header = controller.lastFrameHeader ?: return@onActivity
                    if (header.frameSeq == sequence) return@onActivity
                    assertTrue(header.frameSeq > sequence)
                    if (capture >= 0) assertTrue("<=5 fps",header.captureTsMs-capture >= 200)
                    if (firstContext == null) firstContext = header.context
                    assertEquals(firstContext,header.context)
                    assertEquals(profile.width,header.width); assertEquals(profile.height,header.height)
                    sequence = header.frameSeq; capture = header.captureTsMs
                    val bitmap = (activity.findViewById<ImageView>(R.id.rg_page).drawable as BitmapDrawable).bitmap
                    val color = bitmap.getPixel(2,2)
                    val background = kotlin.math.abs(Color.red(color)-246) <= 8 &&
                        kotlin.math.abs(Color.green(color)-243) <= 8 && kotlin.math.abs(Color.blue(color)-234) <= 8
                    var hash = 1; var blueInk = 0
                    for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
                        val pixel = bitmap.getPixel(x,y)
                        hash = 31*hash+pixel
                        if (Color.blue(pixel)>Color.red(pixel)+60 && Color.blue(pixel)>Color.green(pixel)+40) blueInk++
                    }
                    val qualified = background && blueInk >= 5
                    val now = SystemClock.elapsedRealtime()
                    Log.i("EyeBrowseT02","FRAME_QUALIFICATION seq=$sequence background=$background blueInk=$blueInk hash=$hash qualified=$qualified sinceRequestMs=${now-start}")
                    if (qualified) {
                        pixelHashes.add(hash)
                        if (qualifiedAt == null) {
                            qualifiedAt = now
                            Log.i("EyeBrowseT02","QUALIFIED_FIRST_FRAME_MS=${now-start} seq=$sequence capture=$capture local=$now")
                        }
                    }
                }
            }
            await("first fixture-qualified current frame",2_000) { examine();qualifiedAt != null }
            assertTrue("first qualified frame <=2s",qualifiedAt!!-start <= 2_000)
            val deadline = SystemClock.elapsedRealtime()+15_000
            while (SystemClock.elapsedRealtime()<deadline) { examine();SystemClock.sleep(25) }
            assertTrue("changing counter frames",controller.displayedFrames >= 8)
            assertTrue("changing fixture pixels, not replayed static bytes",pixelHashes.size >= 3)
            Log.i("EyeBrowseT02", "CURRENT_FRAMES=${controller.displayedFrames} DISTINCT_PIXEL_HASHES=${pixelHashes.size}")
        } finally { scenario.close() }
    }
    /**
     * FW5 paired companion. Every frame used here arrives through the stored authenticated
     * direct-LAN session. The decoder-thread barrier is androidTest-only: it blocks decoding, not
     * Main/link receive, so a real old-context frame can be observed pending and then retired by
     * the real Phone handoff before decode/display resumes.
     */
    @Test
    fun fw5AuthenticatedPendingOldFrameIsRejectedAndFreshContextDisplaysAfterReacquire() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        lateinit var controller: RgPresentationController
        var gate: DecoderGate? = null
        try {
            scenario.onActivity {
                controller = it.presentation
                controller.reconnect()
            }
            await("FW5 authenticated Phone state", 10_000) {
                controller.browserState()?.owner ==
                    com.code2hack.eyebrowse.core.link.control.ControlOwner.PHONE &&
                    controller.profile() != null
            }
            val profile = checkNotNull(controller.profile())
            val firstRequestAt = SystemClock.elapsedRealtime()
            scenario.onActivity { assertTrue(controller.requestPresentation()) }
            await("FW5 first current authenticated frame", 2_000) {
                val state = controller.browserState()
                val header = controller.lastFrameHeader
                state?.owner == com.code2hack.eyebrowse.core.link.control.ControlOwner.RG &&
                    state.stale == false && header?.context == state.context &&
                    controller.displayedFrames > 0
            }
            assertTrue("FW5 first presentation ready inside 2s",
                SystemClock.elapsedRealtime() - firstRequestAt <= 2_000)

            val firstState = checkNotNull(controller.browserState())
            val firstHeader = checkNotNull(controller.lastFrameHeader)
            assertEquals(firstState.context, firstHeader.context)
            assertEquals(profile.width, firstHeader.width)
            assertEquals(profile.height, firstHeader.height)
            val firstBitmapHash = displayedBitmapHash(scenario)
            Log.i(
                "EyeBrowseFW5",
                "RG_FIRST_DISPLAY seq=" + firstHeader.frameSeq +
                    " capture=" + firstHeader.captureTsMs +
                    " profile=" + firstHeader.width + "x" + firstHeader.height +
                    " controlEpoch=" + firstHeader.context.controlEpoch +
                    " viewportEpoch=" + firstHeader.context.viewportEpoch +
                    " hash=" + firstBitmapHash,
            )

            await("FW5 decoder idle before barrier", 1_000) {
                !decoderBusyForTest(controller)
            }
            gate = blockDecoderForTest(controller)
            assertTrue("FW5 decoder barrier entered without blocking Main",
                gate!!.entered.await(1_000, TimeUnit.MILLISECONDS))

            // hosting.html changes once per second. With decode blocked, onPresentation() still
            // accepts the real authenticated frame into PresentationInbox and queues decodeLoop.
            await("FW5 real authenticated frame pending behind decoder barrier", 2_500) {
                pendingFrameForTest(controller)?.header?.context == firstState.context
            }
            val staleCandidate = checkNotNull(pendingFrameForTest(controller))
            assertEquals(firstState.context, staleCandidate.header.context)
            assertEquals(profile.width, staleCandidate.header.width)
            assertEquals(profile.height, staleCandidate.header.height)
            assertTrue("FW5 pending frame sequence is newer than displayed frame",
                staleCandidate.header.frameSeq > firstHeader.frameSeq)
            val displayedBeforeRetire = controller.displayedFrames
            Log.i(
                "EyeBrowseFW5",
                "RG_OLD_FRAME_PENDING seq=" + staleCandidate.header.frameSeq +
                    " capture=" + staleCandidate.header.captureTsMs +
                    " profile=" + staleCandidate.header.width + "x" + staleCandidate.header.height +
                    " controlEpoch=" + staleCandidate.header.context.controlEpoch +
                    " viewportEpoch=" + staleCandidate.header.context.viewportEpoch,
            )

            scenario.onActivity { assertTrue(controller.requestPhone()) }
            await("FW5 Phone handoff retires old presentation grant", 2_000) {
                controller.browserState()?.owner ==
                    com.code2hack.eyebrowse.core.link.control.ControlOwner.PHONE
            }
            val phoneState = checkNotNull(controller.browserState())
            assertTrue("FW5 handoff advances control epoch",
                phoneState.context.controlEpoch > firstState.context.controlEpoch)
            assertNull("FW5 real old pending frame removed by retired inbox grant",
                pendingFrameForTest(controller))
            assertEquals("FW5 old frame never displayed before decoder release",
                displayedBeforeRetire, controller.displayedFrames)

            gate!!.release.countDown()
            await("FW5 decoder drains retired work", 1_000) {
                !decoderBusyForTest(controller)
            }
            SystemClock.sleep(200)
            assertEquals("FW5 retired authenticated frame cannot display after decode resumes",
                displayedBeforeRetire, controller.displayedFrames)

            val secondRequestAt = SystemClock.elapsedRealtime()
            scenario.onActivity { assertTrue(controller.requestPresentation()) }
            await("FW5 fresh-context authenticated frame after reacquire", 2_000) {
                val state = controller.browserState()
                val header = controller.lastFrameHeader
                state?.owner == com.code2hack.eyebrowse.core.link.control.ControlOwner.RG &&
                    state.context != firstState.context && state.stale == false &&
                    header?.context == state.context &&
                    controller.displayedFrames > displayedBeforeRetire
            }
            assertTrue("FW5 reacquired presentation ready inside 2s",
                SystemClock.elapsedRealtime() - secondRequestAt <= 2_000)
            val freshState = checkNotNull(controller.browserState())
            val freshHeader = checkNotNull(controller.lastFrameHeader)
            assertTrue("FW5 reacquired context advances control epoch",
                freshState.context.controlEpoch > firstState.context.controlEpoch)
            assertEquals(freshState.context, freshHeader.context)
            assertEquals(profile.width, freshHeader.width)
            assertEquals(profile.height, freshHeader.height)
            val freshBitmapHash = displayedBitmapHash(scenario)
            Log.i(
                "EyeBrowseFW5",
                "RG_FRESH_DISPLAY seq=" + freshHeader.frameSeq +
                    " capture=" + freshHeader.captureTsMs +
                    " profile=" + freshHeader.width + "x" + freshHeader.height +
                    " controlEpoch=" + freshHeader.context.controlEpoch +
                    " viewportEpoch=" + freshHeader.context.viewportEpoch +
                    " hash=" + freshBitmapHash,
            )

            scenario.onActivity { activity ->
                val drawable = activity.findViewById<ImageView>(R.id.rg_page).drawable
                    as BitmapDrawable
                assertEquals("FW5 decoded bitmap width is unscaled header width",
                    freshHeader.width, drawable.bitmap.width)
                assertEquals("FW5 decoded bitmap height is unscaled header height",
                    freshHeader.height, drawable.bitmap.height)
                assertTrue("FW5 RG UI identifies authenticated live page",
                    activity.findViewById<android.widget.TextView>(R.id.rg_status)
                        .text.toString().contains("authenticated", ignoreCase = true))
            }

            // Final Phone ownership is the cross-device completion handshake used by the Phone
            // companion. No pairing/trust state is changed.
            scenario.onActivity { assertTrue(controller.requestPhone()) }
            await("FW5 final Phone owner", 2_000) {
                controller.browserState()?.owner ==
                    com.code2hack.eyebrowse.core.link.control.ControlOwner.PHONE
            }
        } finally {
            gate?.release?.countDown()
            scenario.close()
        }
    }

    private data class DecoderGate(
        val entered: CountDownLatch,
        val release: CountDownLatch,
    )

    private fun blockDecoderForTest(controller: RgPresentationController): DecoderGate {
        val executor = RgPresentationController::class.java.getDeclaredField("decoder").let {
            it.isAccessible = true
            it.get(controller) as ExecutorService
        }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.execute {
            entered.countDown()
            try {
                release.await(3_000, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return DecoderGate(entered, release)
    }

    private fun decoderBusyForTest(controller: RgPresentationController): Boolean =
        RgPresentationController::class.java.getDeclaredField("decoding").let {
            it.isAccessible = true
            it.getBoolean(controller)
        }

    private fun pendingFrameForTest(
        controller: RgPresentationController,
    ): com.code2hack.eyebrowse.core.link.framing.PresentationFrame? {
        val inbox = RgPresentationController::class.java.getDeclaredField("inbox").let {
            it.isAccessible = true
            it.get(controller)
        }
        val pendingField = PresentationInbox::class.java.getDeclaredField("pending").apply {
            isAccessible = true
        }
        return synchronized(inbox) {
            pendingField.get(inbox) as?
                com.code2hack.eyebrowse.core.link.framing.PresentationFrame
        }
    }

    private fun displayedBitmapHash(
        scenario: ActivityScenario<MainActivity>,
    ): Int {
        var hash = 0
        scenario.onActivity { activity ->
            val bitmap = (activity.findViewById<ImageView>(R.id.rg_page).drawable
                as BitmapDrawable).bitmap
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            var value = 1
            for (y in 0 until bitmap.height step 4) {
                for (x in 0 until bitmap.width step 4) {
                    value = 31 * value + bitmap.getPixel(x, y)
                }
            }
            hash = value
        }
        return hash
    }

    private fun await(label: String, bound: Long, predicate: () -> Boolean) {
        val end=SystemClock.elapsedRealtime()+bound
        while(SystemClock.elapsedRealtime()<end) { if(predicate()) return; SystemClock.sleep(25) }
        fail(label)
    }
}
