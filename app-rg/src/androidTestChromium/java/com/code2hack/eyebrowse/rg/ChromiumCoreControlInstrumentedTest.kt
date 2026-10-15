package com.code2hack.eyebrowse.rg

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.CoreControl
import com.code2hack.eyebrowse.rg.proxy.RouteFixture
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.DataInputStream
import java.util.concurrent.atomic.AtomicInteger

/** A fast control server closes after responding; no request is retried or replayed. */
@RunWith(AndroidJUnit4::class)
class ChromiumCoreControlInstrumentedTest {
    @Test fun bodylessAndBodyRequestsSurviveImmediateServerCloseWithoutReplay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val path = File(context.filesDir, "e57t02-fast-control.sock")
        require(!path.exists()) { "Unexpected existing fixture socket; preserve it" }
        val accepted = AtomicInteger()
        val completed = AtomicInteger()
        val withBody = AtomicInteger()
        val failures = mutableListOf<Throwable>()
        val listener = LocalSocket()
        listener.bind(LocalSocketAddress(path.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM))
        val server = LocalServerSocket(Os.dup(listener.fileDescriptor))
        val thread = Thread {
            try {
                repeat(257) {
                    server.accept().use { socket ->
                        accepted.incrementAndGet()
                        val header = RouteFixture.header(socket.inputStream)
                        val count = header.lineSequence().first { it.startsWith("Content-Length:") }
                            .substringAfter(':').trim().toInt()
                        val body = ByteArray(count)
                        DataInputStream(socket.inputStream).readFully(body)
                        if (count > 0) {
                            check(JSONObject(body.toString(Charsets.UTF_8)).getBoolean("controlled"))
                            withBody.incrementAndGet()
                        }
                        val response = "{\"received\":${accepted.get()}}".toByteArray()
                        socket.outputStream.write(("HTTP/1.1 200 OK\r\nConnection: close\r\n" +
                            "Content-Length: ${response.size}\r\n\r\n").toByteArray())
                        socket.outputStream.write(response)
                        completed.incrementAndGet()
                    }
                }
            } catch (failure: Throwable) { synchronized(failures) { failures += failure } }
        }.apply { isDaemon = true; start() }
        var successes = 0
        try {
            CoreControl(path.absolutePath).use { client ->
                repeat(256) { index ->
                    assertEquals(index + 1, client.request("GET", "/controlled").getInt("received"))
                    successes++
                }
                assertEquals(257, client.request("PUT", "/controlled", JSONObject().put("controlled", true))
                    .getInt("received"))
                successes++
            }
            thread.join(2000)
            assertFalse("fixture thread ended", thread.isAlive)
            assertTrue("fixture protocol complete", synchronized(failures) { failures.isEmpty() })
            assertEquals("exactly one connection per request", 257, accepted.get())
            assertEquals(257, completed.get())
            assertEquals(1, withBody.get())
        } finally {
            try { server.close() } finally { listener.close() }
            thread.join(2000)
            val report = JSONObject().put("successfulRequests", successes).put("accepted", accepted.get())
                .put("completed", completed.get()).put("withBody", withBody.get())
                .put("fixtureErrors", synchronized(failures) { failures.map { it.javaClass.name }.toString() })
                .put("fixtureThreadEnded", !thread.isAlive)
            File(context.filesDir, "chromium-core-control.json").writeText(report.toString(2))
            // Unlinking this owned socket is necessary IPC teardown, not app/data cleanup.
            check(path.delete() || !path.exists())
        }
    }
}
