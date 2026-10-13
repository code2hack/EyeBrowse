package com.code2hack.eyebrowse.rg.proxy

import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import org.json.JSONArray
import org.json.JSONObject

/** Real TCP/TLS destinations record peer IP: .1=DIRECT, .2=slow source, .3=fast source. */
internal class RouteFixture(keyStore: InputStream) : Closeable {
    val tls: SSLContext
    val observations = CopyOnWriteArrayList<Pair<String, String>>()
    val proxyRequests = CopyOnWriteArrayList<Pair<String, String>>()
    val subscriptions = ConcurrentHashMap<String, Pair<Int, String>>()
    @Volatile var subscriptionAgent: String? = null
    @Volatile var slowHealthy = true
    @Volatile var fastHealthy = true
    private val sockets = Collections.newSetFromMap(ConcurrentHashMap<Closeable, Boolean>())
    private val loopback = InetAddress.getByName("127.0.0.1")
    val http: Int
    val https: Int
    val ipv6: Int
    val slow: Int
    val fast: Int
    init {
        val keys = KeyStore.getInstance("PKCS12").apply { keyStore.use { load(it, "fixture".toCharArray()) } }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, "fixture".toCharArray()) }
        val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keys) }
        tls = SSLContext.getInstance("TLS").apply { init(km.keyManagers, tm.trustManagers, null) }
        http = listen(ServerSocket(0, 16, loopback), ::origin)
        https = listen(tls.serverSocketFactory.createServerSocket(0, 16, loopback), ::origin)
        ipv6 = listen(ServerSocket(0, 16, InetAddress.getByName("::1")), ::origin)
        slow = listen(ServerSocket(0, 16, loopback)) { node(it, "127.0.0.2", 180, slowHealthy) }
        fast = listen(ServerSocket(0, 16, loopback)) { node(it, "127.0.0.3", 15, fastHealthy) }
    }
    fun sources() = listOf(ProxySource("source-one", true, listOf(node(slow))), ProxySource("source-two", true, listOf(node(fast))))
    fun proxyReceipts(fromIndex: Int = 0) = JSONArray(proxyRequests.drop(fromIndex).map {
        JSONObject().put("nodePeer", it.first).put("authority", it.second)
    })
    private fun node(port: Int) = mapOf<String, Any?>("name" to "same", "type" to "http", "server" to "127.0.0.1", "port" to port)
    private fun listen(server: ServerSocket, handler: (Socket) -> Unit): Int {
        sockets.add(server)
        thread {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                sockets.add(socket)
                thread { try { socket.use(handler) } catch (_: Exception) { } finally { sockets.remove(socket) } }
            }
        }
        return server.localPort
    }
    private fun node(client: Socket, source: String, delay: Long, healthy: Boolean) {
        val request = header(client.inputStream)
        require(request.startsWith("CONNECT "))
        val authority = request.substringBefore("\r\n").split(' ')[1]
        proxyRequests += source to authority
        if (!healthy) return
        Thread.sleep(delay)
        val port = authority.substringAfterLast(':').toInt()
        require(port == http || port == https)
        val destination = Socket().apply { bind(InetSocketAddress(source, 0)); connect(InetSocketAddress(loopback, port), 2000) }
        sockets.add(destination)
        try {
            client.outputStream.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            client.outputStream.flush()
            thread { try { client.inputStream.copyTo(destination.outputStream) } catch (_: Exception) { } }
            destination.inputStream.copyTo(client.outputStream)
        } finally { destination.close(); sockets.remove(destination) }
    }
    private fun origin(socket: Socket) {
        val request = header(socket.inputStream)
        val path = request.substringBefore("\r\n").split(' ')[1]
        val peer = checkNotNull(socket.inetAddress.hostAddress)
        observations += path to peer
        subscriptions[path.substringBefore('?')]?.let { (status, body) ->
            val agent = request.lineSequence().firstOrNull { it.startsWith("User-Agent:", true) }?.substringAfter(':')?.trim()
            val bytes = (if (subscriptionAgent != null && agent != subscriptionAgent) "cHJveGllczogW10=" else body).toByteArray(Charsets.UTF_8)
            socket.outputStream.write("HTTP/1.1 $status Fixture\r\nContent-Type: application/yaml\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            socket.outputStream.write(bytes)
            return
        }
        if (path.startsWith("/hold")) Thread.sleep(2000) // Observe the core's matched rule while the connection is live.
        if (path.startsWith("/health")) {
            socket.outputStream.write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray()); return
        }
        if (path.startsWith("/ws")) {
            val key = request.lineSequence().first { it.startsWith("Sec-WebSocket-Key:", true) }.substringAfter(':').trim()
            val accept = java.util.Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
            socket.outputStream.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
                "Connection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n").toByteArray())
            val frame = peer.toByteArray()
            socket.outputStream.write(byteArrayOf(0x81.toByte(), frame.size.toByte()) + frame)
            socket.outputStream.flush()
            Thread.sleep(250)
            return
        }
        val body = if (path.startsWith("/page")) """<!doctype html><body><input id="note" value="retained"><script>
            fetch('/observe-web').then(r=>r.text()).then(t=>document.body.dataset.route=t);
            const ws=new WebSocket(location.origin.replace('http','ws')+'/ws');
            ws.onmessage=e=>document.body.dataset.ws=e.data;
            </script></body>""".toByteArray() else peer.toByteArray()
        val type = if (path.startsWith("/page")) "text/html" else "text/plain"
        socket.outputStream.write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        socket.outputStream.write(body)
    }
    override fun close() { sockets.toList().forEach { try { it.close() } catch (_: Exception) { } }; sockets.clear() }
    companion object {
        private fun thread(task: () -> Unit) = Thread(task, "Owned-route-fixture").apply { isDaemon = true; start() }
        fun header(input: InputStream): String {
            val text = StringBuilder()
            while (!text.endsWith("\r\n\r\n")) {
                val next = input.read(); if (next < 0) error("Closed request")
                text.append(next.toChar()); check(text.length <= 16384)
            }
            return text.toString()
        }
    }
}
