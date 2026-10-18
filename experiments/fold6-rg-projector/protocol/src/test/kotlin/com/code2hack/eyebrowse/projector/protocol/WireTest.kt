package com.code2hack.eyebrowse.projector.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test

class WireTest {
    private fun input(block: DataOutputStream.() -> Unit): DataInputStream {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use(block)
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }

    @Test fun scrcpy501SessionAndFlagsAreDecodedExactly() {
        val stream = input {
            writeInt(Int.MIN_VALUE or 1); writeInt(480); writeInt(640)
            writeLong(1L shl 62); writeInt(3); write(byteArrayOf(1, 2, 3))
            writeLong((1L shl 61) or 1234L); writeInt(1); writeByte(5)
        }
        assertEquals(ScrcpyRecord.Session(Geometry(480, 640), true), Scrcpy.readRecord(stream))
        val config = Scrcpy.readRecord(stream) as ScrcpyRecord.Packet
        assertTrue(config.config); assertEquals(0L, config.ptsUs)
        val frame = Scrcpy.readRecord(stream) as ScrcpyRecord.Packet
        assertTrue(frame.keyFrame); assertFalse(frame.config); assertEquals(1234L, frame.ptsUs)
    }

    @Test fun badLengthsAndTruncatedPayloadFailClosed() {
        for (length in listOf(-1, 0, Wire.MAX_PAYLOAD + 1)) {
            assertThrows(Exception::class.java) { Scrcpy.readRecord(input { writeLong(0); writeInt(length) }) }
        }
        assertThrows(Exception::class.java) { Scrcpy.readRecord(input { writeLong(0); writeInt(3); writeByte(1) }) }
        assertThrows(Exception::class.java) { Scrcpy.readRecord(input { writeInt(Int.MIN_VALUE or 2); writeInt(480); writeInt(640) }) }
    }

    @Test fun transportPreservesVideoPtsAndReservesAudio() {
        val original = Frame(Wire.VIDEO, (1L shl 61) or 100L, byteArrayOf(9, 8, 7))
        val result = Wire.readFrame(input { Wire.writeFrame(this, original) })
        assertEquals(original.channel, result.channel); assertEquals(original.stamp, result.stamp)
        assertArrayEquals(original.payload, result.payload)
        assertEquals(2, Wire.AUDIO)
        val audioMeta = Wire.readFrame(input { Wire.writeFrame(this, Frame(Wire.AUDIO_CODEC, payload = byteArrayOf(0x6f, 0x70, 0x75, 0x73))) })
        assertEquals(7, audioMeta.channel)
        assertThrows(Exception::class.java) { Wire.readWelcome(input { writeInt(Wire.MAGIC); writeInt(999) }) }
    }

    @Test fun coordinateMappingRejectsLetterboxAndUsesCurrentGeometry() {
        val fit = Fit.inside(480, 640, Geometry(640, 480))
        assertEquals(140, fit.top); assertEquals(360, fit.height)
        assertNull(fit.touch(240f, 100f, Geometry(640, 480)))
        assertEquals(Tap(320, 240, Geometry(640, 480)), fit.touch(240f, 320f, Geometry(640, 480)))
        assertNull(fit.touch(Float.NaN, 320f, Geometry(640, 480)))
        assertNull(fit.touch(480f, 320f, Geometry(640, 480)))
    }

    @Test fun touchEncodingIsExactlyPinnedScrcpyTouchDownAndUp() {
        val bytes = Scrcpy.tap(Tap(10, 20, Geometry(480, 640)))
        assertEquals(64, bytes.size)
        val stream = DataInputStream(ByteArrayInputStream(bytes))
        for (action in listOf(0, 1)) {
            assertEquals(2, stream.readUnsignedByte()); assertEquals(action, stream.readUnsignedByte())
            assertEquals(-2L, stream.readLong()); assertEquals(10, stream.readInt()); assertEquals(20, stream.readInt())
            assertEquals(480, stream.readUnsignedShort()); assertEquals(640, stream.readUnsignedShort())
            assertEquals(if (action == 0) 65535 else 0, stream.readUnsignedShort())
            assertEquals(0, stream.readInt()); assertEquals(0, stream.readInt())
        }
        assertThrows(Exception::class.java) { Tap(-1, 20, Geometry(480, 640)) }
    }
}
