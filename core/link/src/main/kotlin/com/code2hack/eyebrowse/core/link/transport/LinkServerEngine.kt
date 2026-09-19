package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.LinkTimings
import com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript
import com.code2hack.eyebrowse.core.link.crypto.TlsServerIdentity
import com.code2hack.eyebrowse.core.link.framing.LinkFrameCodec
import com.code2hack.eyebrowse.core.link.invitation.InvitationLifecycle
import com.code2hack.eyebrowse.core.link.invitation.toLinkErrorOrNull
import com.code2hack.eyebrowse.core.link.invitation.B64URL
import com.code2hack.eyebrowse.core.link.messages.AuthErrMessage
import com.code2hack.eyebrowse.core.link.messages.AuthOkMessage
import com.code2hack.eyebrowse.core.link.messages.ChallengeMessage
import com.code2hack.eyebrowse.core.link.messages.ForgetNoticeMessage
import com.code2hack.eyebrowse.core.link.messages.HelloMessage
import com.code2hack.eyebrowse.core.link.messages.LinkMessageCodec
import com.code2hack.eyebrowse.core.link.messages.PairAuthMessage
import com.code2hack.eyebrowse.core.link.messages.PingMessage
import com.code2hack.eyebrowse.core.link.messages.PongMessage
import com.code2hack.eyebrowse.core.link.messages.ReconnectAuthMessage
import com.code2hack.eyebrowse.core.link.messages.StatusMessage
import com.code2hack.eyebrowse.core.link.session.BindingPolicy
import com.code2hack.eyebrowse.core.link.session.OutboundQueue
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * Bounded TLS 1.3 server engine for the Phone (ticket plan §4.1/§4.2/§5, §6 PhoneLinkServer).
 *
 * Security properties enforced here:
 * - TLS 1.3 only, platform JSSE, Phone Keystore identity (via [TlsServerIdentity]);
 * - NO frame — in particular no host status — is sent before RG authentication succeeds;
 * - initial pairing verifies a fresh ECDSA proof over the versioned transcript containing a
 *   fresh single-use nonce, then consumes the invitation exactly once (atomic);
 * - reconnect verifies a fresh proof against the presented key bound to the STORED peer;
 *   no invitation secret is involved after pairing;
 * - peer replacement is refused while a different pairing exists (explicit Forget required);
 * - exactly one active authenticated RG link; concurrent TLS clients are closed pre-auth;
 * - bounded framing/queues/timeouts; heartbeat 10 s; liveness 30 s.
 */
class LinkServerEngine(
    private val tlsIdentity: TlsServerIdentity,
    private val trust: TrustController,
    private val timings: LinkTimings = LinkTimings.PRODUCT,
    private val listener: Listener = Listener.NONE,
) {

    /** Phone-side trust operations the engine drives; implemented by app-phone components. */
    interface TrustController {
        fun serverHello(): HelloMessage
        fun consumeInvitation(id: String, secretB64: String): InvitationLifecycle.ConsumeOutcome
        fun pairedPeerSpki(): ByteArray?
        fun commitPairedPeer(spki: ByteArray, clientHello: HelloMessage)
        fun currentHostStatus(): HostStatusValue

        /** Notification that the authenticated link dropped (state only; trust retained). */
        fun onLinkLost()
    }

    interface Listener {
        fun onAuthFailed(error: LinkError) {}
        fun onLinkUp() {}
        fun onLinkDown() {}
        companion object { val NONE = object : Listener {} }
    }

    enum class Phase { IDLE, AUTHENTICATING, LINK_UP }

    private val serverSocket = AtomicReference<ServerSocket?>(null)
    private val activeSocket = AtomicReference<SSLSocket?>(null)
    private val phase = AtomicReference(Phase.IDLE)
    @Volatile private var stopped = false
    private val outbound = OutboundQueue()
    private val writeLock = Any()
    private val secureRandom = SecureRandom()

    val currentPhase: Phase get() = phase.get()

    fun start(port: Int = LinkProtocol.LOCAL_PORT) {
        check(serverSocket.get() == null) { "server already started" }
        stopped = false
        val context = tlsIdentity.tlsContext()
        val server = context.serverSocketFactory.createServerSocket(port) as SSLServerSocket
        server.setEnabledProtocols(arrayOf(TLS_V1_3))
        // TLS client authentication is app-layer (ECDSA proof); no TLS client certs.
        server.setNeedClientAuth(false)
        server.soTimeout = ACCEPT_POLL_MS
        serverSocket.set(server)
        Thread({ acceptLoop() }, "eyebrowse-link-acceptor").apply {
            isDaemon = true
            start()
        }
    }

    /** Bounded stop: closes sockets; worker threads exit within the cancel-quiesce bound. */
    fun stop() {
        stopped = true
        serverSocket.get()?.close()
        activeSocket.get()?.close()
        serverSocket.set(null)
        phase.set(Phase.IDLE)
    }

    /** Best-effort pre-close Forget notice to the authenticated peer (plan §8). */
    fun sendForgetNotice() {
        val socket = activeSocket.get() ?: return
        try {
            sendFrame(socket.outputStream.buffered(), LinkMessageCodec.encode(ForgetNoticeMessage))
        } catch (e: Exception) { /* security never depends on delivery */ }
    }

    /** Queues a latest-state host status for the active authenticated link (coalesced). */
    fun pushStatus(value: HostStatusValue) {
        outbound.offer(StatusMessage.of(value))
        drainOutbound()
    }

    fun isLinkUp(): Boolean = phase.get() == Phase.LINK_UP

    /** Locally bound port (ephemeral when started with 0); -1 when not running. */
    fun boundPort(): Int = (serverSocket.get() as? ServerSocket)?.localPort ?: -1

    // ------------------------------------------------------------- accept / session

    private fun acceptLoop() {
        while (!stopped) {
            val server = serverSocket.get() ?: break
            val socket = try {
                server.accept() as? SSLSocket ?: continue
            } catch (e: java.net.SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                if (stopped) break else continue
            }
            socket.setEnabledProtocols(arrayOf(TLS_V1_3))
            if (!activeSocket.compareAndSet(null, socket)) {
                // Exactly one active RG link: reject concurrent clients pre-auth, no status.
                closeQuietly(socket)
                continue
            }
            phase.set(Phase.AUTHENTICATING)
            outbound.clear()
            Thread({ runSession(socket) }, "eyebrowse-link-session").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun runSession(socket: SSLSocket) {
        var authenticated = false
        try {
            socket.soTimeout = timings.authTimeoutMs.toInt()
            val input = socket.inputStream.buffered()
            val output = socket.outputStream.buffered()

            // 1. Client hello (first frame ever read; malformed/oversized frames never pass).
            val clientHello = readIncoming(input) as? HelloMessage
                ?: return reject(socket, output, LinkError.IncompatibleProtocol)
            helloError(clientHello)?.let { return reject(socket, output, it) }

            // 2. Server hello + fresh single-use authentication nonce.
            sendFrame(output, LinkMessageCodec.encode(trust.serverHello()))
            val nonce = ByteArray(LinkProtocol.NONCE_BYTES).also { secureRandom.nextBytes(it) }
            sendFrame(output, LinkMessageCodec.encode(ChallengeMessage(B64URL.encode(nonce))))

            // 3. Initial pairing or reconnect proof.
            val authFrame = LinkFrameCodec.readOne(input)
            val error = when (val incoming = readIncoming(authFrame)) {
                is PairAuthMessage -> authenticateInitial(clientHello, incoming, nonce)
                is ReconnectAuthMessage -> authenticateReconnect(clientHello, incoming, nonce)
                else -> LinkError.AuthenticationFailed
            }
            if (error != null) return reject(socket, output, error)

            // 4. Authenticated: AuthOk, then the FIRST protected status frame.
            sendFrame(output, LinkMessageCodec.encode(AuthOkMessage))
            sendFrame(output, LinkMessageCodec.encode(StatusMessage.of(trust.currentHostStatus())))
            authenticated = true
            drainOutboundTo(output)
            phase.set(Phase.LINK_UP)
            listener.onLinkUp()
            readLoop(socket, input, output)
        } catch (e: EOFException) {
            // peer closed during handshake/auth
        } catch (e: java.net.SocketTimeoutException) {
            // auth-phase stall or read-loop liveness timeout: bounded close
        } catch (e: Exception) {
            if (!stopped && authenticated) listener.onLinkDown()
        } finally {
            closeQuietly(socket)
            if (authenticated && !stopped) {
                trust.onLinkLost()
                listener.onLinkDown()
            }
            phase.set(Phase.IDLE)
            activeSocket.compareAndSet(socket, null)
        }
    }

    /** Returns the protocol error to reject with, or null when authentication succeeded. */
    private fun authenticateInitial(
        clientHello: HelloMessage,
        auth: PairAuthMessage,
        nonce: ByteArray,
    ): LinkError? {
        val rgSpki = B64URL.decode(auth.rgSpki) ?: return LinkError.AuthenticationFailed
        val signature = B64URL.decode(auth.sig) ?: return LinkError.AuthenticationFailed
        val presentedNonce = B64URL.decode(auth.nonce) ?: return LinkError.AuthenticationFailed
        if (!MessageDigest.isEqual(nonce, presentedNonce)) return LinkError.AuthenticationFailed
        // Peer replacement first: a different RG while paired is refused before the invitation is
        // touched (plan §8) — a refused peer must not burn a valid invitation.
        BindingPolicy.phoneAcceptInitial(trust.pairedPeerSpki(), rgSpki)?.let { return it }
        val transcript = ChallengeTranscript.build(
            purpose = ChallengeTranscript.Purpose.INITIAL_PAIRING,
            protocolMajor = clientHello.pmj,
            protocolMinor = clientHello.pmm,
            phoneSpki = tlsIdentity.spki(),
            rgSpki = rgSpki,
            nonce = nonce,
            invitationId = auth.iid,
        )
        val publicKey = ecPublicKey(rgSpki) ?: return LinkError.AuthenticationFailed
        if (!ChallengeTranscript.verify(transcript, signature, publicKey)) {
            return LinkError.AuthenticationFailed
        }
        // Atomic one-time consume, after proof-of-possession (plan §4.2 step 5).
        val outcome = trust.consumeInvitation(auth.iid, auth.sec)
        return outcome.toLinkErrorOrNull() ?: run { trust.commitPairedPeer(rgSpki, clientHello); null }
    }

    private fun authenticateReconnect(
        clientHello: HelloMessage,
        auth: ReconnectAuthMessage,
        nonce: ByteArray,
    ): LinkError? {
        val rgSpki = B64URL.decode(auth.rgSpki) ?: return LinkError.AuthenticationFailed
        val signature = B64URL.decode(auth.sig) ?: return LinkError.AuthenticationFailed
        val presentedNonce = B64URL.decode(auth.nonce) ?: return LinkError.AuthenticationFailed
        if (!MessageDigest.isEqual(nonce, presentedNonce)) return LinkError.AuthenticationFailed
        BindingPolicy.phoneAcceptReconnect(trust.pairedPeerSpki(), rgSpki)?.let { return it }
        val transcript = ChallengeTranscript.build(
            purpose = ChallengeTranscript.Purpose.RECONNECT,
            protocolMajor = clientHello.pmj,
            protocolMinor = clientHello.pmm,
            phoneSpki = tlsIdentity.spki(),
            rgSpki = rgSpki,
            nonce = nonce,
        )
        val publicKey = ecPublicKey(rgSpki) ?: return LinkError.AuthenticationFailed
        if (!ChallengeTranscript.verify(transcript, signature, publicKey)) {
            return LinkError.AuthenticationFailed
        }
        return null
    }

    // ------------------------------------------------------------- authenticated read loop

    private fun readLoop(socket: SSLSocket, input: InputStream, output: OutputStream) {
        val heartbeat = Thread({
            while (!stopped && phase.get() == Phase.LINK_UP) {
                try {
                    sendFrame(output, LinkMessageCodec.encode(PingMessage))
                } catch (e: Exception) {
                    break
                }
                try {
                    Thread.sleep(timings.heartbeatIntervalMs)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }, "eyebrowse-link-server-heartbeat").apply { isDaemon = true; start() }
        var lastInbound = System.nanoTime()
        try {
            while (!stopped && phase.get() == Phase.LINK_UP) {
                socket.soTimeout = timings.heartbeatIntervalMs.toInt()
                val frame = try {
                    LinkFrameCodec.readOne(input)
                } catch (e: java.net.SocketTimeoutException) {
                    if (elapsedMs(lastInbound) > timings.livenessTimeoutMs) break
                    continue
                }
                lastInbound = System.nanoTime()
                when (val incoming = readIncoming(frame)) {
                    is PingMessage -> sendFrame(output, LinkMessageCodec.encode(PongMessage))
                    is PongMessage -> Unit // liveness refreshed above
                    else -> Unit // status/unknown RG→Phone frames are not part of v1: ignore
                }
                drainOutboundTo(output)
            }
        } finally {
            heartbeat.interrupt()
        }
    }

    private fun drainOutbound() {
        val socket = activeSocket.get() ?: return
        if (phase.get() != Phase.LINK_UP) return
        try {
            drainOutboundTo(socket.outputStream.buffered())
        } catch (e: Exception) {
            // link death is handled by the session loop
        }
    }

    private fun drainOutboundTo(output: OutputStream) {
        synchronized(writeLock) {
            while (true) {
                val message = outbound.poll() ?: break
                sendFrame(output, LinkMessageCodec.encode(message))
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private fun helloError(hello: HelloMessage): LinkError? =
        if (hello.pmj == LinkProtocol.MAJOR && hello.hasRequiredCapabilities()) null
        else LinkError.IncompatibleProtocol

    private fun readIncoming(input: InputStream): Any? {
        val frame = LinkFrameCodec.readOne(input)
        return decodeIncoming(frame)
    }

    private fun readIncoming(payload: ByteArray): Any? = decodeIncoming(payload)

    private fun decodeIncoming(payload: ByteArray): Any? {
        val incoming = LinkMessageCodec.decode(payload).getOrNull() ?: return null
        return when (incoming) {
            is LinkMessageCodec.Incoming.Known -> incoming.message
            is LinkMessageCodec.Incoming.Unknown -> LinkMessageCodec.UnknownMessage
        }
    }

    private fun reject(socket: SSLSocket, output: OutputStream, error: LinkError) {
        try {
            sendFrame(output, LinkMessageCodec.encode(AuthErrMessage(error.wireCode)))
        } catch (e: Exception) { /* socket already dead */ }
        listener.onAuthFailed(error)
        closeQuietly(socket)
    }

    private fun sendFrame(output: OutputStream, payload: ByteArray) {
        synchronized(writeLock) {
            output.write(LinkFrameCodec.encode(payload))
            output.flush()
        }
    }

    private fun elapsedMs(sinceNanos: Long) = (System.nanoTime() - sinceNanos) / 1_000_000

    private fun ecPublicKey(spki: ByteArray): PublicKey? = try {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
    } catch (e: Exception) {
        null
    }

    internal fun closeQuietly(socket: java.net.Socket) {
        try { socket.close() } catch (e: Exception) { /* bounded best-effort close */ }
    }

    private companion object {
        const val ACCEPT_POLL_MS = 1000
    }
}

internal const val TLS_V1_3 = "TLSv1.3"
