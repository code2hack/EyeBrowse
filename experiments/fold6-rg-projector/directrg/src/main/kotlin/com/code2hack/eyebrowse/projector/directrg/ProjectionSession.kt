package com.code2hack.eyebrowse.projector.directrg

import android.util.AtomicFile
import android.view.Surface
import com.code2hack.eyebrowse.projector.protocol.*
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocket
import org.json.JSONArray
import org.json.JSONObject

class ProjectionSession(
    private val directory: File,
    private val surface: Surface,
    private val onState: (ProjectionSession, String) -> Unit,
    private val onGeometry: (ProjectionSession, Geometry) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(true)
    private val timer = Executors.newScheduledThreadPool(2)
    private val taps = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, SynchronousQueue())
    private val outputLock = Any()
    private val writing = AtomicLong()
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var output: DataOutputStream? = null
    @Volatile private var decoder: SurfaceDecoder? = null
    @Volatile var geometry: Geometry? = null
        private set
    @Volatile private var readyForTouch = false
    private val started = System.nanoTime()
    private val stats = JSONObject().put("state", "CONNECTING").put("audio", "disabled-intermediate")
    private var packets = 0L
    private var changes = 0L
    private var rendered = 0L
    private var touches = 0L
    private var lastHash: ByteArray? = null
    private val renderDelayMs = ArrayDeque<Double>()
    private val roundTripMs = ArrayDeque<Double>()

    @Synchronized private fun record(state: String? = null) {
        if (state != null) stats.put("state", state)
        val seconds = (System.nanoTime() - started) / 1e9
        fun p95(values: ArrayDeque<Double>): Double = values.sorted().let { if (it.isEmpty()) 0.0 else it[((it.size - 1) * .95).toInt()] }
        stats.put("elapsedMs", (seconds * 1000).toLong()).put("packets", packets).put("changingPackets", changes)
            .put("rendered", rendered).put("receivedFps", if (seconds > 0) packets / seconds else 0)
            .put("renderedFps", if (seconds > 0) rendered / seconds else 0).put("touchesSent", touches)
            .put("packetReadToCallbackP95Ms", p95(renderDelayMs)).put("networkRoundTripP95Ms", p95(roundTripMs))
            .put("latencyScope", "RG System.nanoTime packet-read to render-callback receipt; network echo RTT; no optical/input end-to-end claim")
            .put("maxEncodedPacketBytes", Wire.MAX_PAYLOAD).put("maxInflightFrames", 8).put("queuedTaps", 0)
        val file = AtomicFile(File(directory.parentFile, "status.json"))
        val out = file.startWrite()
        try { out.write(stats.toString().toByteArray()); file.finishWrite(out) }
        catch (error: Exception) { file.failWrite(out); throw error }
    }

    private fun state(value: String) { record(value); onState(this, value) }

    @Synchronized private fun failure(error: Exception) {
        val causes = JSONArray()
        var cause: Throwable? = error
        repeat(6) { cause?.let { causes.put(it.javaClass.simpleName); cause = it.cause } }
        stats.put("errorType", error.javaClass.simpleName).put("causeClasses", causes)
    }

    private fun send(frame: Frame) = synchronized(outputLock) {
        check(running.get())
        writing.set(System.nanoTime())
        try { Wire.writeFrame(output ?: error("No authenticated connection"), frame) }
        finally { writing.set(0) }
    }

    fun start() {
        state("CONNECTING")
        Thread({
            try {
                val peer = Tls.connect(directory)
                socket = peer
                if (!running.get()) { peer.close(); return@Thread }
                output = DataOutputStream(peer.outputStream)
                Wire.writeRequest(output!!, Wire.STREAM)
                val input = DataInputStream(peer.inputStream)
                Wire.readWelcome(input)
                synchronized(this) {
                    stats.put("tls", peer.session.protocol).put("peerAddress", peer.inetAddress.hostAddress)
                        .put("localAddress", peer.localAddress.hostAddress).put("scrcpy", Wire.SCRCPY_VERSION)
                }
                state("WAITING_FOR_VIDEO")
                timer.scheduleWithFixedDelay({
                    try { send(Frame(Wire.PING, System.nanoTime())); record() }
                    catch (_: Exception) { closeWithState("DISCONNECTED") }
                }, 0, 1, TimeUnit.SECONDS)
                timer.scheduleWithFixedDelay({
                    val since = writing.get()
                    if (since != 0L && System.nanoTime() - since > 2_000_000_000L) closeWithState("WRITE_STALLED")
                }, 250, 250, TimeUnit.MILLISECONDS)
                while (running.get()) {
                    val frame = Wire.readFrame(input)
                    when (frame.channel) {
                        Wire.GEOMETRY -> {
                            val data = DataInputStream(frame.payload.inputStream())
                            val size = Geometry(data.readInt(), data.readInt())
                            readyForTouch = false; decoder?.close()
                            geometry = size
                            onGeometry(this, size)
                            synchronized(this) { stats.put("width", size.width).put("height", size.height) }
                            decoder = SurfaceDecoder(surface, size, { delay ->
                                synchronized(this) {
                                    rendered++; renderDelayMs.addLast(delay)
                                    if (renderDelayMs.size > 256) renderDelayMs.removeFirst()
                                }
                                if (!readyForTouch && running.get()) { readyForTouch = true; state("STREAMING") }
                            }, { name -> synchronized(this) { stats.put("decoder", name) } },
                                { format -> synchronized(this) { stats.put("decoderFormat", format) } }, { error -> failure(error); closeWithState("DECODE_FAILED") })
                        }
                        Wire.VIDEO -> {
                            require(frame.stamp >= 0)
                            val packet = ScrcpyRecord.Packet(frame.stamp, frame.payload)
                            if (!packet.config) synchronized(this) {
                                packets++
                                val hash = MessageDigest.getInstance("SHA-256").digest(frame.payload)
                                if (lastHash?.contentEquals(hash) != true) changes++
                                lastHash = hash
                            }
                            (decoder ?: error("Missing geometry")).enqueue(packet, System.nanoTime())
                        }
                        Wire.PONG -> synchronized(this) {
                            val delay = (System.nanoTime() - frame.stamp) / 1e6
                            require(delay in 0.0..5000.0)
                            roundTripMs.addLast(delay); if (roundTripMs.size > 256) roundTripMs.removeFirst()
                        }
                        Wire.END -> break
                        else -> error("Unnegotiated media")
                    }
                }
                closeWithState("DISCONNECTED")
            } catch (error: Exception) { failure(error); closeWithState("CONNECTION_FAILED") }
        }, "pav67-receiver").start()
    }

    fun tap(tap: Tap): Boolean {
        if (!running.get() || !readyForTouch || tap.geometry != geometry) return false
        return try {
            // SynchronousQueue retains no command. Pause/loss closes the socket and rejects later work.
            taps.execute {
                try {
                    if (running.get() && readyForTouch && tap.geometry == geometry) {
                        send(Frame(Wire.TAP, payload = tap.bytes())); synchronized(this) { touches++ }
                    }
                } catch (_: Exception) { closeWithState("DISCONNECTED") }
            }
            true
        } catch (_: java.util.concurrent.RejectedExecutionException) { false }
    }

    private fun closeWithState(value: String) {
        if (!running.compareAndSet(true, false)) return
        readyForTouch = false
        runCatching { socket?.close() }; timer.shutdownNow(); taps.shutdownNow()
        runCatching { decoder?.close() }; decoder = null
        state(value)
    }
    override fun close() = closeWithState("STOPPED")
}
