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
    private fun await(label: String, bound: Long, predicate: () -> Boolean) {
        val end=SystemClock.elapsedRealtime()+bound
        while(SystemClock.elapsedRealtime()<end) { if(predicate()) return; SystemClock.sleep(25) }
        fail(label)
    }
}
