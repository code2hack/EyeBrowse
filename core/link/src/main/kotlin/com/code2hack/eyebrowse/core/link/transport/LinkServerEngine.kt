package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.framing.LinkRecord
import com.code2hack.eyebrowse.core.link.messages.BrowserControlMessage
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
import com.code2hack.eyebrowse.core.link.session.PeerTrustRead
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

        /** Tri-state stored peer; CORRUPT fails closed inside the binding policy (review R5). */
        fun pairedPeer(): PeerTrustRead
        fun commitPairedPeer(spki: ByteArray, clientHello: HelloMessage)
        fun currentHostStatus(): HostStatusValue

        /** Notification that the authenticated link dropped (state only; trust retained). */
        fun onLinkLost()
    }

    interface Listener {
        fun onAuthFailed(error: LinkError) {}
        fun onLinkUp() {}
        fun onLinkDown() {}
        fun onAuthenticatedSession(session: AuthenticatedControlSession, peer: HelloMessage) {}
        fun onControl(session: AuthenticatedControlSession, message: BrowserControlMessage) {}
        companion object { val NONE = object : Listener {} }
    }

    enum class Phase { IDLE, AUTHENTICATING, LINK_UP }

    private val serverSocket = AtomicReference<ServerSocket?>(null)
    private val activeSocket = AtomicReference<SSLSocket?>(null)

    /**
     * Serializes active-slot replacement with owner cleanup. In particular, stale eviction closes
     * A and installs B while holding this lock; A's finally must acquire the same lock before it
     * can clear the slot. This removes the close(A) -> A clears -> CAS(A,B) race (T03 E2).
     */
    private val activeOwnershipLock = Any()

    /** JVM-only deterministic race seams; production leaves all null. */
    @Volatile
    internal var staleCloseBeforeInstallForTest: (() -> Unit)? = null
    @Volatile
    internal var beforeSessionOwnerCleanupForTest: (() -> Unit)? = null
    @Volatile
    internal var afterSessionOwnerClearedForTest: (() -> Unit)? = null
    @Volatile
    internal var beforeNormalAdmissionOwnershipLockForTest: (() -> Unit)? = null

    /**
     * Last inbound activity of the active session (nanoTime), 0 when no session runs. A live peer
     * refreshes it at least every heartbeat interval (ping/pong ≤10 s); livenessTimeoutMs without
     * inbound ⇒ the occupant is provably dead and may be evicted for a new client (T03 listener
     * recovery: a wedged half-open session must not hold the single link slot indefinitely).
     * Internal (not private) so the integration regression can freeze it deterministically.
     */
    internal val activeSessionLastInboundNanos = java.util.concurrent.atomic.AtomicLong(0)

    /** Whether the ACTIVE session reached authenticated LINK_UP (eviction-down notification). */
    @Volatile
    internal var activeSessionAuthenticated: Boolean = false
    private val phase = AtomicReference(Phase.IDLE)
    @Volatile private var stopped = false
    @Volatile private var controlSession: AuthenticatedControlSession? = null
    private val writeLock = Any()
    private val secureRandom = SecureRandom()

    val currentPhase: Phase get() = phase.get()

    @Synchronized
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
        Thread({ acceptLoop(server) }, "eyebrowse-link-acceptor").apply {
            isDaemon = true
            start()
        }
    }

    @Volatile internal var beforeStopTlsCloseForTest: (() -> Unit)? = null

    @Volatile internal var beforePairingCommitForTest: (() -> Unit)? = null

    /** State retirement is synchronous; TLS close_notify must never execute on a UI caller. */
    @Synchronized
    fun stop() {
        stopped = true
        // A listening ServerSocket has no TLS session/close_notify. Release its bind before a
        // synchronous restart; only accepted SSLSockets require the asynchronous network close.
        serverSocket.getAndSet(null)?.let { runCatching { it.close() } }
        val retired = synchronized(activeOwnershipLock) {
            val socket = activeSocket.getAndSet(null)
            val session = controlSession
            controlSession = null
            activeSessionAuthenticated = false
            activeSessionLastInboundNanos.set(0)
            phase.set(Phase.IDLE)
            socket to session
        }
        if (retired.first != null || retired.second != null) {
            val beforeClose = beforeStopTlsCloseForTest
            Thread({
                try { beforeClose?.invoke() } finally {
                    // Normal TLS close (not abortive shutdown): preserve close_notify semantics.
                    // Only captured objects are touched; late cleanup cannot close a successor.
                    retired.second?.close()
                    retired.first?.let(::closeQuietly)
                }
            }, "eyebrowse-link-stop").apply { isDaemon = true; start() }
        }
    }

    /**
     * Best-effort pre-close Forget notice to the authenticated peer (plan §8).
     * Authenticated-only (review R6/B7): never emitted during TLS/AUTHENTICATING — a pre-auth
     * frame would be a control frame to an unauthenticated peer.
     */
    fun sendForgetNotice() {
        if (phase.get() == Phase.LINK_UP) controlSession?.sendControl(ForgetNoticeMessage)
    }

    /** Only current-session state is queued; a new session gets a fresh status snapshot. */
    fun pushStatus(value: HostStatusValue) {
        if (phase.get() == Phase.LINK_UP) controlSession?.sendControl(StatusMessage.of(value))
    }

    fun isLinkUp(): Boolean = phase.get() == Phase.LINK_UP

    /** Locally bound port (ephemeral when started with 0); -1 when not running. */
    fun boundPort(): Int = (serverSocket.get() as? ServerSocket)?.localPort ?: -1

    // ------------------------------------------------------------- accept / session

    private fun acceptLoop(server: ServerSocket) {
        while (!stopped && serverSocket.get() === server) {
            val socket = try {
                server.accept() as? SSLSocket ?: continue
            } catch (e: java.net.SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                System.err.println("EyeBrowseLink: accept failed: " + e.javaClass.name + ": " + e.message)
                if (stopped) break else continue
            }
            socket.setEnabledProtocols(arrayOf(TLS_V1_3))

            // E3: ordinary empty-slot admission participates in the SAME ownership fence as final
            // owner cleanup and stale replacement. Once null -> socket succeeds, the associated
            // auth/liveness/phase initialization is complete before an older cleanup can proceed.
            var normalAdmissionInstalled = false
            beforeNormalAdmissionOwnershipLockForTest?.invoke()
            synchronized(activeOwnershipLock) {
                if (!stopped && serverSocket.get() === server && activeSocket.compareAndSet(null, socket)) {
                    activeSessionAuthenticated = false
                    activeSessionLastInboundNanos.set(System.nanoTime())
                    phase.set(Phase.AUTHENTICATING)
                    controlSession?.close()
                    controlSession = null
                    normalAdmissionInstalled = true
                }
            }
            if (normalAdmissionInstalled) {
                Thread({ runSession(socket) }, "eyebrowse-link-session").apply {
                    isDaemon = true
                    start()
                }
                continue
            }

            // Exactly one active RG link: reject concurrent clients pre-auth, no status —
            // UNLESS the occupant is provably dead (no inbound for the liveness window): a
            // wedged half-open session must not hold the slot indefinitely (T03 recovery).
            val existing = activeSocket.get()
            var replacementInstalled = false
            var evictedWasAuthenticated = false
            if (existing != null) {
                // E2: stale decision, close(A), and CAS(A -> B) form one ownership critical
                // section. A's finally uses the same lock, so it cannot clear A to null between
                // close and installation and spuriously reject this same Retry attempt.
                synchronized(activeOwnershipLock) {
                    if (!stopped && serverSocket.get() === server &&
                        activeSocket.get() === existing &&
                        shouldEvictStaleSession(
                            activeSessionLastInboundNanos.get(),
                            System.nanoTime(),
                            timings.livenessTimeoutMs,
                        )
                    ) {
                        evictedWasAuthenticated = activeSessionAuthenticated
                        closeQuietly(existing)
                        staleCloseBeforeInstallForTest?.invoke()
                        // Stop may begin while close/test seam is in progress; never install a
                        // replacement after stopped flips true.
                        if (!stopped && serverSocket.get() === server) {
                            replacementInstalled = activeSocket.compareAndSet(existing, socket)
                            if (replacementInstalled) {
                                activeSessionAuthenticated = false
                                activeSessionLastInboundNanos.set(System.nanoTime())
                                phase.set(Phase.AUTHENTICATING)
                                controlSession?.close()
                                controlSession = null
                            }
                        }
                    }
                }
            }
            if (replacementInstalled) {
                // The evictor owns the one truthful down publication for A. A's session-final
                // cleanup can no longer win ownership after B is installed.
                if (evictedWasAuthenticated && !stopped) {
                    trust.onLinkLost()
                    listener.onLinkDown()
                }
                Thread({ runSession(socket) }, "eyebrowse-link-session").apply {
                    isDaemon = true
                    start()
                }
                continue
            }
            closeQuietly(socket)
        }
    }

    private fun runSession(socket: SSLSocket) {
        var authenticated = false
        var session: AuthenticatedControlSession? = null
        try {
            synchronized(activeOwnershipLock) {
                if (stopped || activeSocket.get() !== socket) return
                activeSessionLastInboundNanos.set(System.nanoTime())
            }
            socket.soTimeout = timings.authTimeoutMs.toInt()
            val input = socket.inputStream.buffered()
            val output = socket.outputStream.buffered()

            // 1. Client hello (first frame ever read; malformed/oversized frames never pass).
            val clientHello = readIncoming(input) as? HelloMessage
                ?: return reject(socket, output, LinkError.IncompatibleProtocol)
            helloError(clientHello)?.let { return reject(socket, output, it) }

            // 2. Server hello + fresh single-use authentication nonce.
            val serverHello = trust.serverHello()
            sendFrame(output, LinkMessageCodec.encode(serverHello))
            val nonce = ByteArray(LinkProtocol.NONCE_BYTES).also { secureRandom.nextBytes(it) }
            sendFrame(output, LinkMessageCodec.encode(ChallengeMessage(B64URL.encode(nonce))))

            // 3. Initial pairing or reconnect proof.
            val authFrame = LinkFrameCodec.readOne(input)
            val error = when (val incoming = readIncoming(authFrame)) {
                is PairAuthMessage -> authenticateInitial(socket, clientHello, incoming, nonce)
                is ReconnectAuthMessage -> authenticateReconnect(clientHello, incoming, nonce)
                else -> LinkError.AuthenticationFailed
            }
            if (error != null) return reject(socket, output, error)

            // 4. Authenticated: AuthOk, then the FIRST protected status frame.
            sendFrame(output, LinkMessageCodec.encode(AuthOkMessage))
            val current = AuthenticatedControlSession(socket, input,
                clientHello.hasPresentationCapabilities() && serverHello.hasPresentationCapabilities(), false)
            session = current
            synchronized(activeOwnershipLock) {
                if (stopped || activeSocket.get() !== socket) return
                authenticated = true
                activeSessionAuthenticated = true
                controlSession = current
                phase.set(Phase.LINK_UP)
                current.sendControl(StatusMessage.of(trust.currentHostStatus()))
                listener.onAuthenticatedSession(current, clientHello)
                listener.onLinkUp()
            }
            readLoop(socket, current)
        } catch (e: EOFException) {
            // peer closed during handshake/auth
        } catch (e: java.net.SocketTimeoutException) {
            // auth-phase stall or read-loop liveness timeout: bounded close
        } catch (e: Exception) {
            // Bounded diagnostics: failure class + message only, never payloads or key material.
            // E1: do NOT publish listener-down here. All authenticated session-derived down events
            // are centralized in owner-fenced cleanup (or the stale-session evictor).
            System.err.println("EyeBrowseLink: session failed: " + e.javaClass.name)
        } finally {
            session?.close()
            closeQuietly(socket)
            beforeSessionOwnerCleanupForTest?.invoke()
            // Owner-fenced cleanup: global link state and listener-down may only be mutated by the
            // session that still owns the active slot. The ownership lock also linearizes this CAS
            // against stale close -> replacement installation in the acceptor.
            synchronized(activeOwnershipLock) {
                val stillOwner = activeSocket.compareAndSet(socket, null)
                if (stillOwner) {
                    // E3 deterministic seam: owner is already null, but cleanup deliberately holds
                    // the ownership lock until all old-session global state is finished.
                    afterSessionOwnerClearedForTest?.invoke()
                    if (authenticated && !stopped) {
                        trust.onLinkLost()
                        listener.onLinkDown()
                    }
                    controlSession = null
                    phase.set(Phase.IDLE)
                    activeSessionAuthenticated = false
                    activeSessionLastInboundNanos.set(0)
                }
            }
        }
    }

    /** Returns the protocol error to reject with, or null when authentication succeeded. */
    private fun authenticateInitial(
        socket: SSLSocket,
        clientHello: HelloMessage,
        auth: PairAuthMessage,
        nonce: ByteArray,
    ): LinkError? {
        if (auth.hello != clientHello) return LinkError.IncompatibleProtocol
        val rgSpki = B64URL.decode(auth.rgSpki) ?: return LinkError.AuthenticationFailed
        val signature = B64URL.decode(auth.sig) ?: return LinkError.AuthenticationFailed
        val presentedNonce = B64URL.decode(auth.nonce) ?: return LinkError.AuthenticationFailed
        if (!MessageDigest.isEqual(nonce, presentedNonce)) return LinkError.AuthenticationFailed
        // Peer replacement first: a different RG while paired is refused before the invitation is
        // touched (plan §8) — a refused peer must not burn a valid invitation. CORRUPT stored
        // state fails closed here (review R5/B6).
        BindingPolicy.phoneAcceptInitial(trust.pairedPeer(), rgSpki)?.let { return it }
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
        beforePairingCommitForTest?.invoke()
        return synchronized(activeOwnershipLock) {
            // A stopped/retired authenticator cannot restore trust after synchronous Forget.
            if (stopped || activeSocket.get() !== socket) return@synchronized LinkError.AuthenticationFailed
            val outcome = trust.consumeInvitation(auth.iid, auth.sec)
            outcome.toLinkErrorOrNull() ?: run { trust.commitPairedPeer(rgSpki, clientHello); null }
        }
    }

    private fun authenticateReconnect(
        clientHello: HelloMessage,
        auth: ReconnectAuthMessage,
        nonce: ByteArray,
    ): LinkError? {
        if (auth.hello != clientHello) return LinkError.IncompatibleProtocol
        val rgSpki = B64URL.decode(auth.rgSpki) ?: return LinkError.AuthenticationFailed
        val signature = B64URL.decode(auth.sig) ?: return LinkError.AuthenticationFailed
        val presentedNonce = B64URL.decode(auth.nonce) ?: return LinkError.AuthenticationFailed
        if (!MessageDigest.isEqual(nonce, presentedNonce)) return LinkError.AuthenticationFailed
        BindingPolicy.phoneAcceptReconnect(trust.pairedPeer(), rgSpki)?.let { return it }
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

    private fun readLoop(socket: SSLSocket, session: AuthenticatedControlSession) {
        val heartbeat = Thread({
            while (!stopped && activeSocket.get() === socket) {
                if (!session.sendControl(PingMessage)) break
                try { Thread.sleep(timings.heartbeatIntervalMs) } catch (_: InterruptedException) { break }
            }
        }, "eyebrowse-link-server-heartbeat").apply { isDaemon = true; start() }
        var lastInbound = System.nanoTime()
        try {
            while (!stopped && activeSocket.get() === socket) {
                socket.soTimeout = timings.heartbeatIntervalMs.toInt()
                val record = session.readNext()
                if (record == null) {
                    if (elapsedMs(lastInbound) > timings.livenessTimeoutMs) break
                    continue
                }
                synchronized(activeOwnershipLock) {
                    if (activeSocket.get() !== socket) return
                    lastInbound = System.nanoTime()
                    activeSessionLastInboundNanos.set(lastInbound)
                    when (val incoming = (record as? LinkRecord.Control)?.message) {
                        is PingMessage -> session.sendControl(PongMessage)
                        is BrowserControlMessage -> {
                            if (!session.presentationCompatible) throw java.io.IOException("presentation update required")
                            listener.onControl(session, incoming)
                        }
                        else -> Unit
                    }
                }
            }
        } finally { heartbeat.interrupt() }
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

    internal companion object {
        private const val ACCEPT_POLL_MS = 1000

        /**
         * Pure eviction decision (T03 listener recovery): the occupant is evictable only when a
         * session is tracked (lastInbound > 0) and has produced NO inbound for the full liveness
         * window — a live peer refreshes it at least every heartbeat interval, so this can never
         * evict a healthy link. Internal for the JVM regression.
         */
        internal fun shouldEvictStaleSession(
            lastInboundNanos: Long,
            nowNanos: Long,
            livenessTimeoutMs: Long,
        ): Boolean =
            lastInboundNanos > 0 && (nowNanos - lastInboundNanos) > livenessTimeoutMs * 1_000_000
    }
}

internal const val TLS_V1_3 = "TLSv1.3"
