package com.code2hack.eyebrowse.core.link.framing

import com.code2hack.eyebrowse.core.link.LinkProtocol
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Bounded wire framing (ticket plan §5): 4-byte unsigned big-endian payload length + UTF-8 JSON
 * payload. Decoded frames are capped at [LinkProtocol.FRAME_MAX_BYTES]; zero-length, oversize and
 * malformed headers are rejected before any large allocation.
 */
object LinkFrameCodec {

    const val HEADER_BYTES: Int = 4

    class FrameTooLargeException(declared: Int) :
        IOException("frame length $declared exceeds ${LinkProtocol.FRAME_MAX_BYTES}")

    class ZeroLengthFrameException : IOException("zero-length frame")

    fun encodeLength(payloadBytes: Int): ByteArray {
        require(payloadBytes in 1..LinkProtocol.FRAME_MAX_BYTES)
        return byteArrayOf(
            ((payloadBytes ushr 24) and 0xFF).toByte(),
            ((payloadBytes ushr 16) and 0xFF).toByte(),
            ((payloadBytes ushr 8) and 0xFF).toByte(),
            (payloadBytes and 0xFF).toByte(),
        )
    }

    /** Validates a 4-byte header; returns the declared payload length or throws. */
    fun parseHeader(header: ByteArray): Int {
        require(header.size == HEADER_BYTES)
        val declared =
            ((header[0].toInt() and 0xFF) shl 24) or
                ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
        if (declared == 0) throw ZeroLengthFrameException()
        if (declared > LinkProtocol.FRAME_MAX_BYTES) throw FrameTooLargeException(declared)
        return declared
    }

    fun encode(payloadUtf8Json: ByteArray): ByteArray {
        require(payloadUtf8Json.isNotEmpty())
        require(payloadUtf8Json.size <= LinkProtocol.FRAME_MAX_BYTES) {
            "payload exceeds frame cap"
        }
        val out = ByteArrayOutputStream(HEADER_BYTES + payloadUtf8Json.size)
        out.write(encodeLength(payloadUtf8Json.size))
        out.write(payloadUtf8Json)
        return out.toByteArray()
    }

    /** Reads exactly one frame from [input]; every failure mode is bounded and pre-validated. */
    fun readOne(input: java.io.InputStream): ByteArray {
        val header = ByteArray(HEADER_BYTES)
        readFully(input, header)
        val declared = parseHeader(header)
        val payload = ByteArray(declared)
        readFully(input, payload)
        return payload
    }

    fun decodeUtf8(payload: ByteArray): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(payload))
            .toString()

    private fun readFully(input: java.io.InputStream, buffer: ByteArray) {
        var off = 0
        while (off < buffer.size) {
            val n = input.read(buffer, off, buffer.size - off)
            if (n < 0) throw EOFException("stream ended mid-frame")
            off += n
        }
    }
}
