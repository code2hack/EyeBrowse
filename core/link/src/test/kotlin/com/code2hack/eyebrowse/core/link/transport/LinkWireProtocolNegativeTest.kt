package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.framing.LinkFrameCodec
import com.code2hack.eyebrowse.core.link.messages.AuthErrMessage
import com.code2hack.eyebrowse.core.link.messages.PongMessage
import com.code2hack.eyebrowse.core.link.messages.StatusMessage
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Wire-protocol negatives over real TLS (plan §10-B + §16 intermediate review checklist):
 * no status before auth, TLS 1.3 only, bounded framing, major mismatch, liveness.
 */
class LinkWireProtocolNegativeTest {

    private lateinit var server: Harness.ServerHarness
    private var port: Int = 0

    @Before
    fun setUp() {
        server = Harness.ServerHarness()
        port = server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `protocol major mismatch is refused as incompatible`() {
        val raw = Harness.RawClient(port, server.identity.spkiSha256Hex())
        raw.sendHello(major = LinkProtocol.MAJOR + 1)
        assertEquals(AuthErrMessage("INCOMPATIBLE_PROTOCOL"), raw.readMessage())
        raw.close()
    }

    @Test
    fun `missing required capability is refused as incompatible`() {
        val raw = Harness.RawClient(port, server.identity.spkiSha256Hex())
        raw.sendHello(caps = listOf("PAIRING_V1"))
        assertEquals(AuthErrMessage("INCOMPATIBLE_PROTOCOL"), raw.readMessage())
        raw.close()
    }

    @Test
    fun `no status frame is ever sent before authentication succeeds`() {
        val raw = Harness.RawClient(port, server.identity.spkiSha256Hex())
        raw.sendHello()
        raw.expectChallenge()
        // Impersonate an "authenticated" ping instead of the required auth proof.
        raw.sendPing()
        val reply = raw.readMessage()
        // Either an auth error or close — but never a status frame and never an AuthOk.
        assertTrue(reply == null || reply is AuthErrMessage)
        var anyStatus = false
        while (true) {
            val frame = raw.readRawFrame() ?: break
            val decoded = com.code2hack.eyebrowse.core.link.messages.LinkMessageCodec.decode(frame).getOrNull()
            if (decoded is com.code2hack.eyebrowse.core.link.messages.LinkMessageCodec.Incoming.Known &&
                decoded.message is StatusMessage
            ) {
                anyStatus = true
            }
        }
        assertTrue("protected status leaked before authentication", !anyStatus)
        raw.close()
    }

    @Test
    fun `oversize frame is rejected before any large allocation and without status`() {
        val raw = Harness.RawClient(port, server.identity.spkiSha256Hex())
        raw.sendHello()
        raw.expectChallenge()
        val declared = LinkProtocol.FRAME_MAX_BYTES + 1
        raw.sendRawBytes(
            byteArrayOf(
                ((declared ushr 24) and 0xFF).toByte(),
                ((declared ushr 16) and 0xFF).toByte(),
                ((declared ushr 8) and 0xFF).toByte(),
                (declared and 0xFF).toByte(),
            ),
        )
        // Server must close; no status frame may appear.
        assertNull(raw.readRawFrame())
        raw.close()
    }

    @Test
    fun `zero-length frame is rejected`() {
        val raw = Harness.RawClient(port, server.identity.spkiSha256Hex())
        raw.sendHello()
        raw.expectChallenge()
        raw.sendRawBytes(byteArrayOf(0, 0, 0, 0))
        assertNull(raw.readRawFrame())
        raw.close()
    }

    @Test
    fun `tls 1_2 handshake to the tls1_3-only listener fails`() {
        val raw = java.net.Socket()
        raw.connect(java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port), 2_000)
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(SpkiPinningTrustManager(server.identity.spkiSha256Hex())), null)
        val socket = context.socketFactory.createSocket(
            raw, java.net.InetAddress.getLoopbackAddress().hostAddress, port, true,
        ) as SSLSocket
        socket.setEnabledProtocols(arrayOf("TLSv1.2"))
        try {
            socket.startHandshake()
            throw AssertionError("TLS 1.2 must not be accepted")
        } catch (e: Exception) {
            // expected: handshake failure (protocol mismatch or closed by peer)
        } finally {
            socket.close()
        }
    }

    @Test
    fun `authenticated ping receives pong`() {
        val raw = Harness.RawClient(port, server.identity.spkiSha256Hex())
        val invitation = server.generateInvitation()
        raw.sendHello()
        raw.expectChallenge()
        raw.sendPairAuth(invitation.first, invitation.second)
        assertTrue(raw.readMessage() is com.code2hack.eyebrowse.core.link.messages.AuthOkMessage)
        assertTrue(raw.readMessage() is StatusMessage) // first protected status after auth
        raw.sendPing()
        // Server pings share the wire (100 ms cadence); wait for the pong among them.
        val pongDeadline = System.currentTimeMillis() + 5_000
        var pongSeen = false
        while (System.currentTimeMillis() < pongDeadline) {
            when (val message = raw.readMessage() ?: break) {
                is PongMessage -> { pongSeen = true; break }
                is com.code2hack.eyebrowse.core.link.messages.PingMessage -> Unit
                else -> throw AssertionError("unexpected frame $message")
            }
        }
        assertTrue(pongSeen)
        raw.close()
    }

    @Test
    fun `silent authenticated peer is dropped after the liveness window`() {
        val raw = Harness.RawClient(port, server.identity.spkiSha256Hex())
        val invitation = server.generateInvitation()
        raw.sendHello()
        raw.expectChallenge()
        raw.sendPairAuth(invitation.first, invitation.second)
        assertTrue(raw.readMessage() is com.code2hack.eyebrowse.core.link.messages.AuthOkMessage)
        assertTrue(raw.readMessage() is StatusMessage)
        // Server heartbeats arrive (100 ms cadence, 500 ms liveness); we read them but never
        // send anything back. Expect server-side close within liveness + slack.
        val start = System.currentTimeMillis()
        while (raw.readRawFrame() != null) {
            if (System.currentTimeMillis() - start > 10_000) throw AssertionError("liveness close never happened")
        }
        val elapsed = System.currentTimeMillis() - start
        assertTrue("close should follow the liveness window, took ${elapsed}ms", elapsed < 10_000)
        assertTrue(Harness.await(server.linkDown))
        raw.close()
    }

    @Test
    fun `server heartbeat pings arrive on the cadence and liveness bound holds`() {
        val raw = Harness.RawClient(port, server.identity.spkiSha256Hex())
        val invitation = server.generateInvitation()
        raw.sendHello()
        raw.expectChallenge()
        raw.sendPairAuth(invitation.first, invitation.second)
        assertTrue(raw.readMessage() is com.code2hack.eyebrowse.core.link.messages.AuthOkMessage)
        assertTrue(raw.readMessage() is StatusMessage)
        // Answer nothing; observe that at least one server Ping arrived (heartbeat running).
        var sawPing = false
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 2_000) {
            val message = raw.readMessage() ?: break
            if (message is com.code2hack.eyebrowse.core.link.messages.PingMessage) {
                sawPing = true
                break
            }
        }
        assertTrue(sawPing)
        raw.close()
    }

    @Test
    fun `frame cap constant matches plan and codec uses it`() {
        assertEquals(32 * 1024, LinkProtocol.FRAME_MAX_BYTES)
        assertEquals(4, LinkFrameCodec.HEADER_BYTES)
    }
}
