package com.code2hack.eyebrowse.projector.directrg

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.code2hack.eyebrowse.projector.protocol.Geometry
import com.code2hack.eyebrowse.projector.protocol.ScrcpyRecord
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** No encoded frame queue. Hardware input wait and Surface delivery are bounded and observable. */
class SurfaceDecoder(
    surface: Surface,
    geometry: Geometry,
    private val rendered: (Double) -> Unit,
    name: (String) -> Unit,
    private val format: (String) -> Unit,
    private val failed: (Exception) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(true)
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val received = LinkedHashMap<Long, Long>()
    private val released = LinkedHashMap<Long, Long>()
    private val outputThread: Thread

    init {
        try {
            name(codec.name)
            codec.configure(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, geometry.width, geometry.height).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
            }, surface, null, 0)
            codec.setOnFrameRenderedListener({ _, pts, _ ->
                val readAt = synchronized(received) { released.remove(pts) }
                // Use one local clock. The vendor render timestamp was incompatible with
                // System.nanoTime() on the RG; callback receipt is the measured endpoint.
                if (running.get() && readAt != null) rendered((System.nanoTime() - readAt) / 1e6)
            }, Handler(Looper.getMainLooper()))
            codec.start()
            outputThread = Thread({
                val info = MediaCodec.BufferInfo()
                try {
                    while (running.get()) {
                        val index = codec.dequeueOutputBuffer(info, 20_000)
                        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val output = codec.outputFormat
                            val width = output.getInteger(MediaFormat.KEY_WIDTH)
                            val height = output.getInteger(MediaFormat.KEY_HEIGHT)
                            fun crop(key: String, fallback: Int) = if (output.containsKey(key)) output.getInteger(key) else fallback
                            val croppedWidth = crop("crop-right", width - 1) - crop("crop-left", 0) + 1
                            val croppedHeight = crop("crop-bottom", height - 1) - crop("crop-top", 0) + 1
                            format(output.toString())
                            require(croppedWidth == geometry.width && croppedHeight == geometry.height) { "Unsupported crop" }
                        }
                        if (index >= 0) {
                            synchronized(received) {
                                received.remove(info.presentationTimeUs)?.let { released[info.presentationTimeUs] = it }
                                check(released.size <= 16) { "Surface stalled" }
                            }
                            codec.releaseOutputBuffer(index, true)
                        }
                    }
                } catch (error: Exception) { if (running.get()) failed(error) }
            }, "pav67-decoder").apply { start() }
        } catch (error: Throwable) {
            running.set(false)
            runCatching { codec.release() }
            throw error
        }
    }

    fun enqueue(packet: ScrcpyRecord.Packet, receivedAt: Long) {
        check(running.get())
        val index = codec.dequeueInputBuffer(50_000)
        check(index >= 0) { "Decoder input stalled" }
        val buffer = codec.getInputBuffer(index) ?: error("No decoder buffer")
        require(packet.data.size <= buffer.capacity())
        buffer.clear(); buffer.put(packet.data)
        if (!packet.config) synchronized(received) {
            check(received.size < 8) { "Decoder backlog" }
            received[packet.ptsUs] = receivedAt
        }
        codec.queueInputBuffer(index, 0, packet.data.size, packet.ptsUs,
            if (packet.config) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0)
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        if (Thread.currentThread() !== outputThread) outputThread.join(1000)
        runCatching { codec.stop() }; codec.release()
        synchronized(received) { received.clear(); released.clear() }
    }
}
