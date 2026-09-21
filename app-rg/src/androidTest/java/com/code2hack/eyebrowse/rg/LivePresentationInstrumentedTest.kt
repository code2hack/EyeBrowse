package com.code2hack.eyebrowse.rg

import android.content.Intent
import android.os.SystemClock
import android.util.Log
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
            await("first current frame",2_000) { controller.displayedFrames > 0 }
            Log.i("EyeBrowseT02", "FIRST_FRAME_MS=${SystemClock.elapsedRealtime()-start}")
            val first = controller.lastFrameHeader!!
            assertEquals(profile.width,first.width); assertEquals(profile.height,first.height)
            val deadline = SystemClock.elapsedRealtime()+15_000
            var seq = first.frameSeq
            var capture = first.captureTsMs
            val pixelHashes = mutableSetOf<Int>()
            while (SystemClock.elapsedRealtime()<deadline) {
                val next = controller.lastFrameHeader!!
                assertEquals(first.context,next.context)
                assertEquals(profile.width,next.width); assertEquals(profile.height,next.height)
                if (next.frameSeq != seq) {
                    assertTrue(next.frameSeq > seq)
                    assertTrue("<=5 fps",next.captureTsMs-capture >= 200)
                    seq=next.frameSeq; capture=next.captureTsMs
                    scenario.onActivity { activity ->
                        val bitmap = (activity.findViewById<android.widget.ImageView>(R.id.rg_page).drawable as android.graphics.drawable.BitmapDrawable).bitmap
                        val color = bitmap.getPixel(2,2)
                        assertTrue("fixture background red", kotlin.math.abs(android.graphics.Color.red(color)-246) <= 8)
                        assertTrue("fixture background green", kotlin.math.abs(android.graphics.Color.green(color)-243) <= 8)
                        assertTrue("fixture background blue", kotlin.math.abs(android.graphics.Color.blue(color)-234) <= 8)
                        var hash = 1
                        for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) hash = 31*hash+bitmap.getPixel(x,y)
                        pixelHashes.add(hash)
                    }
                }
                SystemClock.sleep(50)
            }
            assertTrue("changing counter frames",controller.displayedFrames >= 8)
            assertTrue("changing fixture pixels, not replayed static bytes", pixelHashes.size >= 3)
            Log.i("EyeBrowseT02", "CURRENT_FRAMES=${controller.displayedFrames} DISTINCT_PIXEL_HASHES=${pixelHashes.size}")
        } finally { scenario.close() }
    }
    private fun await(label: String, bound: Long, predicate: () -> Boolean) {
        val end=SystemClock.elapsedRealtime()+bound
        while(SystemClock.elapsedRealtime()<end) { if(predicate()) return; SystemClock.sleep(25) }
        fail(label)
    }
}
