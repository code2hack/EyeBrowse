package com.code2hack.eyebrowse.rg.proxy

import android.net.LocalSocket
import android.net.LocalSocketAddress
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException

/** Filesystem socket in app-private storage; no TCP management port or webpage/native bridge. */
internal class CoreControl(private val path: String) : Closeable {
    @Volatile private var current: LocalSocket? = null

    @Synchronized fun request(method: String, target: String, body: JSONObject? = null,
        timeoutMs: Int = 7000): JSONObject {
        LocalSocket().use { socket ->
            current = socket
            try {
                socket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
                socket.soTimeout = timeoutMs
                val bytes = body?.toString()?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
                val header = "$method $target HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n" +
                    "Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\n\r\n"
                socket.outputStream.write(header.toByteArray(Charsets.US_ASCII))
                // A bodyless request is complete after its header. The core may already have
                // replied and closed; Android's zero-byte socket write can then report EPIPE.
                if (bytes.isNotEmpty()) socket.outputStream.write(bytes)
                socket.outputStream.flush()
                val response = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = socket.inputStream.read(buffer)
                    if (count < 0) break
                    if (response.size() + count > MAX_RESPONSE) throw IOException("Core response too large")
                    response.write(buffer, 0, count)
                }
                return decode(response.toByteArray())
            } finally { current = null }
        }
    }

    // Closing the socket also cancels a long native health check when the foreground runtime stops.
    override fun close() { current?.close() }

    companion object {
        private const val MAX_RESPONSE = 4 * 1024 * 1024
        fun decode(bytes: ByteArray): JSONObject {
            val response = bytes.toString(Charsets.UTF_8)
            val end = response.indexOf("\r\n\r\n")
            if (end !in 0..16384) throw IOException("Invalid core response")
            val header = response.substring(0, end)
            val code = header.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull()
            if (code == null || code !in 200..299) throw IOException("Core request refused ($code)")
            var payload = response.substring(end + 4)
            if (header.lineSequence().any { it.equals("Transfer-Encoding: chunked", ignoreCase = true) }) {
                // Chunk lengths count bytes, including multibyte node fields.
                val input = bytes.copyOfRange(end + 4, bytes.size)
                val output = ByteArrayOutputStream()
                var at = 0
                while (true) {
                    var lineEnd = at
                    while (lineEnd + 1 < input.size && !(input[lineEnd] == 13.toByte() && input[lineEnd + 1] == 10.toByte())) lineEnd++
                    if (lineEnd + 1 >= input.size) throw IOException("Invalid core chunk")
                    val count = input.copyOfRange(at, lineEnd).toString(Charsets.US_ASCII)
                        .substringBefore(';').toIntOrNull(16) ?: throw IOException("Invalid core chunk size")
                    at = lineEnd + 2
                    if (count == 0) break
                    if (count < 0 || count > input.size - at - 2 || input[at + count] != 13.toByte() ||
                        input[at + count + 1] != 10.toByte()) throw IOException("Truncated core chunk")
                    output.write(input, at, count)
                    at += count + 2
                }
                payload = output.toByteArray().toString(Charsets.UTF_8)
            }
            return if (payload.isBlank()) JSONObject() else JSONObject(payload)
        }
    }
}
