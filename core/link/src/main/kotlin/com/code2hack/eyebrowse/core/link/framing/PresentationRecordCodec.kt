package com.code2hack.eyebrowse.core.link.framing

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.control.ControlContext
import com.code2hack.eyebrowse.core.link.messages.LinkMessageCodec
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PresentationFrameHeader(
    val context: ControlContext,
    val frameSeq: Long,
    val captureTsMs: Long,
    val width: Int,
    val height: Int,
    val format: String = "WEBP",
    val formatVersion: Int = 1,
) {
    init {
        require(frameSeq >= 0 && captureTsMs >= 0)
        require(context.hostingGeneration != null)
        require(width in 1..PresentationProfile.MAX_DIMENSION && height in 1..PresentationProfile.MAX_DIMENSION)
        require(width.toLong() * height <= PresentationProfile.MAX_PIXELS)
        require(format == "WEBP" && formatVersion == 1)
    }
}

class PresentationFrame(val header: PresentationFrameHeader, pixels: ByteArray) {
    // Own the buffer; an encoder/caller cannot mutate a queued or admitted frame.
    init { require(pixels.isNotEmpty() && pixels.size <= LinkProtocol.PRESENTATION_RECORD_MAX_BYTES) }
    private val bytes = pixels.copyOf()
    fun pixels(): ByteArray = bytes.copyOf()
    internal fun writePixels(output: java.io.OutputStream) = output.write(bytes)
    internal val pixelCount get() = bytes.size
}

sealed class LinkRecord {
    data class Control(val message: Any) : LinkRecord()
    data class Presentation(val frame: PresentationFrame) : LinkRecord()
}

/** Negotiated post-auth format: kind byte, unsigned BE length, bounded body. Pre-auth uses v1 JSON. */
object PresentationRecordCodec {
    private const val CONTROL = 1
    private const val PRESENTATION = 2
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun encodeControl(message: Any): ByteArray {
        val body = LinkMessageCodec.encode(message)
        require(body.size in 1..LinkProtocol.FRAME_MAX_BYTES)
        return byteArrayOf(CONTROL.toByte()) + LinkFrameCodec.encode(body)
    }

    fun encodePresentation(frame: PresentationFrame): ByteArray {
        val metadata = json.encodeToString(PresentationFrameHeader.serializer(), frame.header).toByteArray(Charsets.UTF_8)
        require(metadata.size in 1..LinkProtocol.PRESENTATION_METADATA_MAX_BYTES)
        val bodySize = 4 + metadata.size + frame.pixelCount
        require(bodySize <= LinkProtocol.PRESENTATION_RECORD_MAX_BYTES)
        val result = ByteArrayOutputStream(5 + bodySize)
        DataOutputStream(result).apply {
            writeByte(PRESENTATION)
            writeInt(bodySize)
            writeInt(metadata.size)
            write(metadata)
            frame.writePixels(this)
        }
        return result.toByteArray()
    }

    /** Kind, length and authorization checked BEFORE pixel allocation. Partial read errors close session. */
    fun read(input: InputStream, authenticated: Boolean, presentationOwner: Boolean,
        acceptHeader: (PresentationFrameHeader) -> Boolean = { false }): LinkRecord {
        val stream = DataInputStream(input)
        val kind = stream.readUnsignedByte()
        if (kind != CONTROL && kind != PRESENTATION) throw IOException("unknown record kind")
        val cap = if (kind == CONTROL) LinkProtocol.FRAME_MAX_BYTES else LinkProtocol.PRESENTATION_RECORD_MAX_BYTES
        val size = stream.readInt()
        if (size !in 1..cap) throw IOException("invalid record length")
        if (!authenticated) throw IOException("record before authentication")
        if (kind == CONTROL) {
            val body = ByteArray(size)
            stream.readFully(body)
            return when (val decoded = LinkMessageCodec.decode(body).getOrThrow()) {
                is LinkMessageCodec.Incoming.Known -> LinkRecord.Control(decoded.message)
                is LinkMessageCodec.Incoming.Unknown -> LinkRecord.Control(LinkMessageCodec.UnknownMessage)
            }
        }
        if (!presentationOwner) throw IOException("presentation without RG ownership")
        if (size < 6) throw IOException("short presentation")
        val metaSize = stream.readInt()
        if (metaSize !in 1..LinkProtocol.PRESENTATION_METADATA_MAX_BYTES || metaSize >= size - 4) throw IOException("invalid metadata length")
        val meta = ByteArray(metaSize)
        stream.readFully(meta)
        val header = json.decodeFromString(PresentationFrameHeader.serializer(), LinkFrameCodec.decodeUtf8(meta))
        if (!acceptHeader(header)) throw IOException("stale or invalid presentation context")
        val pixels = ByteArray(size - 4 - metaSize)
        stream.readFully(pixels)
        return LinkRecord.Presentation(PresentationFrame(header, pixels))
    }
}

/** One latest frame, bounded control FIFO with priority; all queued bytes are immutable snapshots. */
class MultiplexedRecordQueue(private val capacity: Int = LinkProtocol.OUTBOUND_QUEUE_MAX) {
    init { require(capacity > 0) }
    private val controls = ArrayDeque<ByteArray>()
    private var pendingFrame: ByteArray? = null
    private var context: ControlContext? = null
    private var profile: PresentationProfile? = null
    private var sequence = -1L
    private var lastContext: ControlContext? = null
    private var lastProfile: PresentationProfile? = null

    @Synchronized fun setPresentation(context: ControlContext?, profile: PresentationProfile?) {
        if (context != null) {
            if (context == lastContext) require(profile == lastProfile) { "profile changed within an epoch" }
            else { sequence = -1; lastContext = context; lastProfile = profile }
        }
        if (this.context != context) pendingFrame = null
        this.context = context
        this.profile = profile
    }

    @Synchronized fun offerControl(message: Any): Boolean {
        if (controls.size >= capacity) return false
        val bytes = try { PresentationRecordCodec.encodeControl(message) } catch (_: IllegalArgumentException) { return false }
        controls.addLast(bytes)
        return true
    }

    @Synchronized fun offerFrame(frame: PresentationFrame): Boolean {
        val h = frame.header
        if (context == null || h.context != context || h.width != profile?.width || h.height != profile?.height || h.frameSeq <= sequence) return false
        val bytes = try { PresentationRecordCodec.encodePresentation(frame) } catch (_: IllegalArgumentException) { return false }
        pendingFrame = bytes
        sequence = h.frameSeq
        return true
    }

    @Synchronized fun poll(): ByteArray? =
        if (controls.isNotEmpty()) controls.removeFirst() else pendingFrame.also { pendingFrame = null }
    @Synchronized fun controlSize(): Int = controls.size
    @Synchronized fun hasPendingFrame(): Boolean = pendingFrame != null
    @Synchronized fun clear() { controls.clear(); pendingFrame = null; context = null; profile = null; sequence = -1; lastContext = null; lastProfile = null }
}
