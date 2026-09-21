package com.code2hack.eyebrowse.core.link.framing

import com.code2hack.eyebrowse.core.link.LinkProtocol
import java.io.ByteArrayInputStream
import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan §10-B: 32 KiB frame bound and malformed-length rejection before large allocation. */
class LinkFrameCodecTest {

    @Test
    fun `roundtrip small and boundary-sized frames`() {
        for (size in intArrayOf(1, 2, 255, 256, 4096, LinkProtocol.FRAME_MAX_BYTES)) {
            val payload = ByteArray(size) { (it % 251).toByte() }
            val frame = LinkFrameCodec.encode(payload)
            assertEquals(size + 4, frame.size)
            val decoded = LinkFrameCodec.readOne(ByteArrayInputStream(frame))
            assertArrayEquals(payload, decoded)
        }
    }

    @Test
    fun `header encodes unsigned big-endian`() {
        val header = LinkFrameCodec.encodeLength(0x00007FFF)
        assertEquals(byteArrayOf(0, 0, 0x7F, 0xFF.toByte()).toList(), header.toList())
        assertEquals(0x00007FFF, LinkFrameCodec.parseHeader(header))
        val boundary = LinkFrameCodec.encodeLength(LinkProtocol.FRAME_MAX_BYTES)
        assertEquals(byteArrayOf(0, 0, 0x80.toByte(), 0).toList(), boundary.toList())
        assertEquals(LinkProtocol.FRAME_MAX_BYTES, LinkFrameCodec.parseHeader(boundary))
    }

    @Test(expected = com.code2hack.eyebrowse.core.link.framing.LinkFrameCodec.ZeroLengthFrameException::class)
    fun `zero-length frames are rejected`() {
        LinkFrameCodec.parseHeader(byteArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `oversize frames are rejected before allocation`() {
        val declared = LinkProtocol.FRAME_MAX_BYTES + 1
        val header = byteArrayOf(
            ((declared ushr 24) and 0xFF).toByte(),
            ((declared ushr 16) and 0xFF).toByte(),
            ((declared ushr 8) and 0xFF).toByte(),
            (declared and 0xFF).toByte(),
        )
        try {
            LinkFrameCodec.parseHeader(header)
            throw AssertionError("expected FrameTooLargeException")
        } catch (e: LinkFrameCodec.FrameTooLargeException) {
            assertTrue(e.message!!.contains(LinkProtocol.FRAME_MAX_BYTES.toString()))
        }
        // Stream path: header followed by far fewer bytes must fail without huge reads.
        val stream = ByteArrayInputStream(header + ByteArray(10))
        try {
            LinkFrameCodec.readOne(stream)
            throw AssertionError("expected FrameTooLargeException")
        } catch (e: LinkFrameCodec.FrameTooLargeException) {
            // expected
        }
    }

    @Test(expected = EOFException::class)
    fun `truncated stream fails with EOF`() {
        LinkFrameCodec.readOne(ByteArrayInputStream(byteArrayOf(0, 0, 0, 5, 1, 2)))
    }

    @Test
    fun `utf8 decode is strict`() {
        val invalid = byteArrayOf(0xC3.toByte(), 0x28) // malformed two-byte sequence
        try {
            LinkFrameCodec.decodeUtf8(invalid)
            throw AssertionError("expected strict decode failure")
        } catch (e: java.nio.charset.CharacterCodingException) {
            // expected
        }
        assertEquals("ok", LinkFrameCodec.decodeUtf8("ok".toByteArray()))
    }
}
