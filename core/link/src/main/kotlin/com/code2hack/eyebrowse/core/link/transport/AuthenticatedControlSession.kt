package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.control.ControlContext
import com.code2hack.eyebrowse.core.link.framing.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.net.SocketTimeoutException
import javax.net.ssl.SSLSocket

/** Created by the TLS engines only AFTER application authentication. Each connection owns its queue. */
class AuthenticatedControlSession internal constructor(
    private val socket: SSLSocket,
    private val input: InputStream,
    val presentationCompatible: Boolean,
    private val receivesPresentation: Boolean,
    private val writeTimeoutMs: Long = 30_000,
    val keyboardCompatible: Boolean = false,
) {
    private val queue = MultiplexedRecordQueue()
    private val lock = java.lang.Object()
    @Volatile private var closed = false
    private var context: ControlContext? = null
    private var profile: PresentationProfile? = null
    private var receivedSequence = -1L
    private var lastReceiveContext: ControlContext? = null
    private val legacyControls = ArrayDeque<ByteArray>()
    @Volatile private var writeStartedNanos = 0L
    @Volatile private var readStartedNanos = 0L
    private val writeGuard = Thread({
        try {
            while (!closed) {
                Thread.sleep(50)
                val now = System.nanoTime()
                if (listOf(writeStartedNanos, readStartedNanos).any {
                    it != 0L && (now - it) / 1_000_000 > writeTimeoutMs
                }) close()
            }
        } catch (_: InterruptedException) { }
    }, "eyebrowse-write-deadline").apply { isDaemon = true; start() }
    private val writer = Thread({ writeLoop() }, "eyebrowse-control-writer").apply { isDaemon = true; start() }

    /** Owner adapter publishes a new grant; old pending frames cannot outlive it. */
    fun setPresentation(context: ControlContext?, profile: PresentationProfile?) = synchronized(lock) {
        if (closed || !presentationCompatible) return@synchronized
        require((context == null) == (profile == null))
        if (context != null && context != lastReceiveContext) {
            receivedSequence = -1
            lastReceiveContext = context
        }
        this.context = context
        this.profile = profile
        queue.setPresentation(context, profile)
    }

    fun sendControl(message: Any): Boolean = synchronized(lock) {
        if (closed || (message is BrowserControlMessage && !presentationCompatible)) return@synchronized false
        val accepted = if (presentationCompatible) queue.offerControl(message) else {
            if (legacyControls.size >= LinkProtocol.OUTBOUND_QUEUE_MAX) false else {
                legacyControls.addLast(LinkFrameCodec.encode(LinkMessageCodec.encode(message))); true
            }
        }
        if (accepted) lock.notifyAll()
        accepted
    }

    fun sendPresentation(frame: PresentationFrame): Boolean = synchronized(lock) {
        if (closed || receivesPresentation || !presentationCompatible) return@synchronized false
        queue.offerFrame(frame).also { if (it) lock.notifyAll() }
    }

    /** Timeout while idle is harmless; timeout after the first byte is a truncated record and fatal. */
    internal fun readNext(): LinkRecord? {
        val first = try { input.read() } catch (_: SocketTimeoutException) { return null }
        if (first < 0) throw java.io.EOFException("session closed")
        readStartedNanos = System.nanoTime()
        try {
        val recordInput = SequenceInputStream(ByteArrayInputStream(byteArrayOf(first.toByte())), input)
        if (!presentationCompatible) {
            return when (val decoded = LinkMessageCodec.decode(LinkFrameCodec.readOne(recordInput)).getOrThrow()) {
                is LinkMessageCodec.Incoming.Known -> LinkRecord.Control(decoded.message)
                is LinkMessageCodec.Incoming.Unknown -> LinkRecord.Control(LinkMessageCodec.UnknownMessage)
            }
        }
        val record = PresentationRecordCodec.read(recordInput, true, receivesPresentation && synchronized(lock) { context != null }) { header ->
            synchronized(lock) {
                !closed && header.context == context && header.width == profile?.width && header.height == profile?.height && header.frameSeq > receivedSequence
            }
        }
        if (record is LinkRecord.Presentation) synchronized(lock) {
            val h = record.frame.header
            // Recheck after the blocking pixel read: takeover may have invalidated the grant.
            if (closed || context != h.context || h.frameSeq <= receivedSequence) throw java.io.IOException("retired presentation")
            receivedSequence = h.frameSeq
        }
        return record
        } finally { readStartedNanos = 0 }
    }

    fun close() {
        closed = true
        runCatching { socket.close() } // Interrupt a blocked write before acquiring the queue lock.
        synchronized(lock) { queue.clear(); legacyControls.clear(); context = null; profile = null; lock.notifyAll() }
        writer.interrupt()
        writeGuard.interrupt()
    }

    private fun writeLoop() {
        try {
            val output = socket.outputStream
            while (!closed) {
                val bytes = synchronized(lock) {
                    val next = if (presentationCompatible) queue.poll() else legacyControls.removeFirstOrNull()
                    if (next == null) lock.wait(100)
                    next
                } ?: continue
                // One bounded in-flight record can finish after a grant changes; the receiver
                // rejects its old context. Handoff control records precede the next frame.
                writeStartedNanos = System.nanoTime()
                try { output.write(bytes); output.flush() } finally { writeStartedNanos = 0 }
            }
        } catch (_: Exception) {
            close()
        }
    }
}
