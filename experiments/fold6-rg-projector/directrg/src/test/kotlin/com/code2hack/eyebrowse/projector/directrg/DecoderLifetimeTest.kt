package com.code2hack.eyebrowse.projector.directrg

import android.media.MediaCodec
import android.view.Surface
import com.code2hack.eyebrowse.projector.protocol.Geometry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DecoderLifetimeTest {
    @get:Rule val temporary = TemporaryFolder()
    private val size = Geometry(480, 640)
    private fun session() = ProjectionSession(temporary.root.resolve("session"), Surface(), { _, _ -> }, { _, _ -> })
    private fun decoder() = SurfaceDecoder(Surface(), size, {}, {}, {}, {})

    @Test fun stopDuringConstructionReleasesUnpublishedDecoder() {
        val codec = MediaCodec().also { MediaCodec.created = it }
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        codec.at = { if (it == "configure") { entered.countDown(); check(resume.await(5, TimeUnit.SECONDS)) } }
        val session = session()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val construction = worker.submit { session.replaceDecoder(size) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            session.close()
            resume.countDown()
            construction.get(5, TimeUnit.SECONDS)
            assertEquals("Stop must close a decoder whose constructor finishes late", 1, codec.releases.get())
            assertFalse(Thread.getAllStackTraces().keys.any { it.name == "pav67-decoder" && it.isAlive })
        } finally { resume.countDown(); session.close(); worker.shutdownNow() }
    }

    @Test fun replacementAndRepeatedStopReleaseEachCodecOnce() {
        val session = session()
        val first = MediaCodec().also { MediaCodec.created = it }
        session.replaceDecoder(size)
        val second = MediaCodec().also { MediaCodec.created = it }
        session.replaceDecoder(size)
        session.close(); session.close()
        assertEquals(1, first.releases.get())
        assertEquals(1, second.releases.get())
    }

    @Test fun initializationFailuresReleaseCodecAndPreserveOriginalError() {
        for (stage in listOf("name", "configure", "listener", "start")) {
            val original = IllegalStateException("public test failure at $stage")
            val codec = MediaCodec().also { MediaCodec.created = it }
            codec.at = { if (it == stage) throw original }
            codec.releaseFailure = IllegalStateException("cleanup failure")
            val caught = assertThrows(IllegalStateException::class.java) { decoder() }
            assertSame(original, caught)
            assertEquals("Failed $stage must release its allocation", 1, codec.releases.get())
        }
    }
}
