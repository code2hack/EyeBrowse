package com.code2hack.eyebrowse.projector.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.min

data class Geometry(val width: Int, val height: Int) {
    init { require(width in 1..4096 && height in 1..4096) }
    fun bytes(): ByteArray = bytes { writeInt(width); writeInt(height) }
}

data class Tap(val x: Int, val y: Int, val geometry: Geometry) {
    init { require(x in 0 until geometry.width && y in 0 until geometry.height) }
    fun bytes(): ByteArray = bytes { writeInt(x); writeInt(y); writeInt(geometry.width); writeInt(geometry.height) }
    companion object {
        fun from(payload: ByteArray): Tap {
            require(payload.size == 16)
            val input = DataInputStream(ByteArrayInputStream(payload))
            val x = input.readInt(); val y = input.readInt()
            return Tap(x, y, Geometry(input.readInt(), input.readInt()))
        }
    }
}

data class Fit(val left: Int, val top: Int, val width: Int, val height: Int) {
    fun touch(x: Float, y: Float, geometry: Geometry): Tap? {
        if (!x.isFinite() || !y.isFinite() || x < left || y < top || x >= left + width || y >= top + height) return null
        return Tap(((x - left) * geometry.width / width).toInt(), ((y - top) * geometry.height / height).toInt(), geometry)
    }
    companion object {
        fun inside(width: Int, height: Int, geometry: Geometry): Fit {
            require(width > 0 && height > 0)
            val scale = min(width.toDouble() / geometry.width, height.toDouble() / geometry.height)
            val w = (geometry.width * scale).toInt().coerceAtLeast(1)
            val h = (geometry.height * scale).toInt().coerceAtLeast(1)
            return Fit((width - w) / 2, (height - h) / 2, w, h)
        }
    }
}

fun bytes(block: DataOutputStream.() -> Unit): ByteArray {
    val buffer = ByteArrayOutputStream()
    DataOutputStream(buffer).use(block)
    return buffer.toByteArray()
}

data class Frame(val channel: Int, val stamp: Long = 0, val payload: ByteArray = byteArrayOf())

object Wire {
    const val MAGIC = 0x50415631
    const val VERSION = 1
    const val SCRCPY_VERSION = "5.0.1"
    const val H264 = 0x68323634
    const val MAX_PAYLOAD = 1024 * 1024
    const val GEOMETRY = 0
    const val VIDEO = 1
    const val AUDIO = 2 // Reserved: same encoded packet/PTS framing; not negotiated in PAV-02.
    const val TAP = 3
    const val PING = 4
    const val PONG = 5
    const val END = 6
    const val AUDIO_CODEC = 7 // Reserved: four-byte upstream codec ID precedes AUDIO config/sample records.
    const val STREAM = 1
    const val STOP_HOST = 2

    fun writeRequest(output: DataOutputStream, operation: Int) {
        require(operation == STREAM || operation == STOP_HOST)
        output.writeInt(MAGIC); output.writeInt(VERSION); output.writeByte(operation); output.flush()
    }
    fun readRequest(input: DataInputStream): Int {
        require(input.readInt() == MAGIC && input.readInt() == VERSION)
        return input.readUnsignedByte().also { require(it == STREAM || it == STOP_HOST) }
    }
    fun writeWelcome(output: DataOutputStream) {
        output.writeInt(MAGIC); output.writeInt(VERSION); output.writeUTF(SCRCPY_VERSION)
        output.writeInt(H264); output.writeBoolean(false); output.flush()
    }
    fun readWelcome(input: DataInputStream) {
        require(input.readInt() == MAGIC && input.readInt() == VERSION)
        require(input.readUTF() == SCRCPY_VERSION && input.readInt() == H264)
        require(!input.readBoolean()) { "Audio not supported by this intermediate receiver" }
    }
    private fun validate(channel: Int, length: Int) {
        require(channel in GEOMETRY..AUDIO_CODEC && length in 0..MAX_PAYLOAD)
        when (channel) {
            GEOMETRY -> require(length == 8)
            TAP -> require(length == 16)
            AUDIO_CODEC -> require(length == 4)
            VIDEO, AUDIO -> require(length > 0)
            PING, PONG, END -> require(length == 0)
        }
    }
    fun readFrame(input: DataInputStream): Frame {
        val channel = input.readUnsignedByte(); val stamp = input.readLong(); val size = input.readInt()
        validate(channel, size)
        return Frame(channel, stamp, ByteArray(size).also(input::readFully))
    }
    fun writeFrame(output: DataOutputStream, frame: Frame) {
        validate(frame.channel, frame.payload.size)
        output.writeByte(frame.channel); output.writeLong(frame.stamp); output.writeInt(frame.payload.size)
        output.write(frame.payload); output.flush()
    }
}

sealed interface ScrcpyRecord {
    data class Session(val geometry: Geometry, val clientResize: Boolean) : ScrcpyRecord
    data class Packet(val stamp: Long, val data: ByteArray) : ScrcpyRecord {
        val config get() = stamp and Scrcpy.CONFIG != 0L
        val keyFrame get() = stamp and Scrcpy.KEY_FRAME != 0L
        val ptsUs get() = stamp and (Scrcpy.KEY_FRAME - 1)
    }
}

/** Exact upstream 5.0.1 Streamer/ControlMessageReader format, not the older 12-byte stream header. */
object Scrcpy {
    const val CONFIG = 1L shl 62
    const val KEY_FRAME = 1L shl 61
    fun readRecord(input: DataInputStream): ScrcpyRecord {
        val stamp = input.readLong(); val last = input.readInt()
        if (stamp < 0) {
            val flags = (stamp ushr 32).toInt()
            require(flags == Int.MIN_VALUE || flags == (Int.MIN_VALUE or 1))
            return ScrcpyRecord.Session(Geometry(stamp.toInt(), last), flags and 1 != 0)
        }
        require(last in 1..Wire.MAX_PAYLOAD)
        return ScrcpyRecord.Packet(stamp, ByteArray(last).also(input::readFully))
    }
    fun tap(tap: Tap): ByteArray = bytes {
        for (action in listOf(0, 1)) {
            writeByte(2); writeByte(action); writeLong(-2)
            writeInt(tap.x); writeInt(tap.y); writeShort(tap.geometry.width); writeShort(tap.geometry.height)
            writeShort(if (action == 0) 0xffff else 0); writeInt(0); writeInt(0)
        }
    }
}
