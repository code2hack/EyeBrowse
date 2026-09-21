package com.code2hack.eyebrowse.phone

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.IOException

/** Compression cannot grow a buffer beyond the wire budget, even for incompressible pages. */
internal class BoundedFrameOutput(private val limit: Int) : OutputStream() {
    private val bytes = ByteArrayOutputStream(limit)
    init { require(limit > 0) }
    override fun write(value: Int) {
        if (bytes.size() == limit) throw IOException("presentation too large")
        bytes.write(value)
    }
    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (length < 0 || length > limit - bytes.size()) throw IOException("presentation too large")
        bytes.write(buffer, offset, length)
    }
    fun toByteArray(): ByteArray = bytes.toByteArray()
}
