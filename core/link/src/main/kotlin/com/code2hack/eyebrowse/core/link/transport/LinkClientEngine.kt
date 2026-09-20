package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.LinkTimings
import com.code2hack.eyebrowse.core.link.PairingState
import com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript
import com.code2hack.eyebrowse.core.link.crypto.RgSigningIdentity
import com.code2hack.eyebrowse.core.link.framing.LinkFrameCodec
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
import java.io.EOFException
import java.io.InputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * TLS client engine for the RG (ticket plan §4.1/§4.2, §6 RgLinkClient).
 *
 * Security properties enforced here:
 * - the server certificate is accepted ONLY when its leaf SPKI SHA-256 equals the pin carried by
 *   the invitation (initial) or the stored peer record (reconnect) — no accept-all manager;
 * - a fresh ECDSA proof over the versioned transcript (fresh nonce) authorizes the session;
 * - no invitation secret is sent on reconnect;
 * - ONE monotonic absolute operation deadline is carried through TCP connect, TLS handshake and
 *   application auth: every blocking phase uses min(per-phase maximum, remaining budget)
 *   (review R3/B4);
 * - the in-flight raw/TLS socket is cancellation-owned from the moment it exists; cancellation
 *   state is re-checked at the security boundaries (before the auth proof is sent and before
 *   authenticated trust/state is committed), so a cancel can never complete a pairing
 *   (review R2/B3);
 * - bounded per-locator connect (≤3 s), one-operation budget (≤10 s), auth window (≤5 s);
 * - heartbeat 10 s / liveness 30 s; cancellation closes owned sockets within the 2 s bound.
 */
class LinkClientEngine(
    private val timings: LinkTimings = LinkTimings.PRODUCT,
    private val listener: Listener,
) {

    interface Listener {
        fun onStateChange(state: PairingState)
        fun onStatus(status: HostStatusValue)
        fun onLinkLost()
        fun onConnectFailed(error: LinkError)

        /**
         * Invoked once per successful authentication with the pin-verified Phone SPKI and the
         * locator that actually carried the session; persistence happens only after this.
         */
        fun onAuthenticated(phoneSpki: ByteArray, usedLocator: Locator) {}
    }

    /** One bounded connection operation. */
    class Attempt(
        /** Pin from the scanned invitation or the stored peer record. */
        val phoneSpkiSha256Hex: String,
        val locators: List<Locator>,
        val signer: RgSigningIdentity,
        val clientHello: HelloMessage,
        /** Non-null for initial pairing: invitation id + secret. Null for reconnect. */
        val invitation: Pair<String, ByteArray>? = null,
    )

    private val running = AtomicBoolean(false)

    /**
     * The currently owned socket (raw during TCP connect, TLS afterwards). Assigned as soon as
     * it exists so [disconnect] closes it at any stage (review R2/B3). At most one operation is
     * in flight ([running] guard), so plain set/clear is safe.
     */
    private val inFlightSocket = AtomicReference<java.net.Socket?>(null)

    @Volatile private var stopped = false
    private var lastFailure: LinkError = LinkError.NetworkUnreachable

    val isBusy: Boolean get() = running.get()

    /** Starts one bounded connect operation on a worker thread. */
    fun connect(attempt: Attempt) {
        check(running.compareAndSet(false, true)) { "client operation already running" }
        stopped = false
        Thread({ runOperation(attempt) }, "eyebrowse-link-client").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Bounded disconnect: closes the in-flight socket at ANY stage (TCP connect, TLS handshake,
     * auth exchange or established session); worker threads exit promptly.
     */
    fun disconnect() {
        stopped = true
        inFlightSocket.getAndSet(null)?.let { s ->
            try { s.close() } catch (e: Exception) { /* bounded best-effort close */ }
        }
    }

    // ------------------------------------------------------------- operation

    private fun runOperation(attempt: Attempt) {
        try {
            listener.onStateChange(PairingState.CONNECTING)
            val deadline = System.nanoTime() + timings.operationBudgetMs * 1_000_000
            var lastError: LinkError = LinkError.NetworkUnreachable

            for (locator in LocatorPolicy.orderForAttempts(attempt.locators)) {
                if (stopped) return // bounded cancel: finally releases the operation
                val remainingMs = (deadline - System.nanoTime()) / 1_000_000
                if (remainingMs <= 0) break
                val established = establishSession(attempt, locator, remainingMs)
                if (stopped) {
                    // Cancellation during TCP/TLS/auth: no CONNECTED, no trust commit.
                    established?.let { closeQuietly(it.first) }
                    return
                }
                if (established != null) {
                    listener.onStateChange(PairingState.CONNECTED)
                    sessionLoop(established.first, established.second)
                    return // session ended (lost/stop): PAIRED_DISCONNECTED emitted in sessionLoop
                }
                lastError = lastFailure
                if (isAuthoritativeFailure(lastError)) break
            }
            if (!stopped) listener.onConnectFailed(lastError)
        } finally {
            running.set(false)
        }
    }

    /**
     * TCP connect + TLS + app authentication against one locator, all inside [budgetMs]
     * (the remaining absolute operation budget). Returns (socket, input) in the authenticated
     * phase, or null after [lastFailure] records the failure class.
     */
    private fun establishSession(attempt: Attempt, locator: Locator, budgetMs: Long): Pair<SSLSocket, InputStream>? {
        lastFailure = LinkError.NetworkUnreachable
        val raw = try {
            val address = InetSocketAddress(locator.address, locator.port)
            java.net.Socket().apply {
                connect(address, minOf(timings.connectTimeoutMs, budgetMs).toInt().coerceAtLeast(1))
                tcpNoDelay = true
            }
        } catch (e: Exception) {
            return null // unreachable: try next locator within the operation budget
        }
        inFlightSocket.set(raw) // cancellation-owned from the beginning of the attempt (R2/B3)
        val tls = try {
            val context = SSLContext.getInstance("TLS")
            val trustManager = SpkiPinningTrustManager(attempt.phoneSpkiSha256Hex)
            context.init(null, arrayOf(trustManager), null)
            context.socketFactory.createSocket(raw, locator.address.hostAddress, locator.port, true) as SSLSocket
        } catch (e: Exception) {
            closeQuietly(raw)
            inFlightSocket.compareAndSet(raw, null)
            lastFailure = LinkError.WrongPhoneIdentity
            return null
        }
        inFlightSocket.set(tls)
        try {
            tls.setEnabledProtocols(arrayOf(TLS_V1_3))
            tls.useClientMode = true
            // Review R3/B4: the auth window is min(per-phase maximum, remaining operation
            // budget) — the aggregate deadline stays authoritative across handshake and auth.
            val authWindowMs = minOf(timings.authTimeoutMs, budgetMs).coerceAtLeast(1L)
            tls.soTimeout = authWindowMs.toInt()
            try {
                // Explicit bounded handshake: pin enforcement failures surface here.
                tls.startHandshake()
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                lastFailure = LinkError.WrongPhoneIdentity
                return null.also { closeQuietly(tls) }
            }
            if (stopped) return null.also { closeQuietly(tls) } // cancel boundary pre-proof (R2)
            listener.onStateChange(PairingState.AUTHENTICATING)
            if (!authenticate(tls, attempt)) return null.also { closeQuietly(tls) }
            if (stopped) return null.also { closeQuietly(tls) } // cancel boundary pre-commit (R2)
            // Identity is authoritative from the pinned TLS peer, never from the IP address.
            listener.onAuthenticated(
                tls.session.peerCertificates[0].publicKey.encoded,
                locator,
            )
            return Pair(tls, tls.inputStream.buffered())
        } catch (e: Exception) {
            closeQuietly(tls)
            if (lastFailure == LinkError.NetworkUnreachable) lastFailure = LinkError.AuthenticationFailed
            return null
        }
    }

    /** Failures that will not improve by trying the next locator (plan §5/§7/§8 semantics). */
    private fun isAuthoritativeFailure(error: LinkError): Boolean = when (error) {
        LinkError.InvitationInvalid,
        LinkError.InvitationExpired,
        LinkError.InvitationCancelled,
        LinkError.InvitationReused,
        LinkError.AuthenticationFailed,
        LinkError.WrongPhoneIdentity,
        LinkError.WrongRgIdentity,
        LinkError.PeerReplacementRequired,
        LinkError.IncompatibleProtocol -> true
        else -> false
    }

    /** Performs hello exchange + proof; returns false (with [lastFailure] set) on failure. */
    private fun authenticate(tls: SSLSocket, attempt: Attempt): Boolean {
        val output = tls.outputStream.buffered()
        val input = tls.inputStream.buffered()
        sendFrame(output, LinkMessageCodec.encode(attempt.clientHello))

        val serverHello = readIncoming(input) as? HelloMessage ?: run {
            lastFailure = LinkError.IncompatibleProtocol; return false
        }
        if (serverHello.pmj != LinkProtocol.MAJOR || !serverHello.hasRequiredCapabilities()) {
            lastFailure = LinkError.IncompatibleProtocol; return false
        }
        val challenge = readIncoming(input) as? ChallengeMessage ?: run {
            lastFailure = LinkError.IncompatibleProtocol; return false
        }
        val nonce = B64URL.decode(challenge.nonce) ?: run {
            lastFailure = LinkError.AuthenticationFailed; return false
        }
        val phoneSpki = (tls.session.peerCertificates[0]).publicKey.encoded
        val transcript = ChallengeTranscript.build(
            purpose = if (attempt.invitation != null) ChallengeTranscript.Purpose.INITIAL_PAIRING
            else ChallengeTranscript.Purpose.RECONNECT,
            protocolMajor = attempt.clientHello.pmj,
            protocolMinor = attempt.clientHello.pmm,
            phoneSpki = phoneSpki,
            rgSpki = attempt.signer.spki(),
            nonce = nonce,
            invitationId = attempt.invitation?.first ?: "",
        )
        val signature = attempt.signer.sign(transcript)
        val wire = if (attempt.invitation != null) {
            PairAuthMessage(
                iid = attempt.invitation.first,
                sec = B64URL.encode(attempt.invitation.second),
                rgSpki = B64URL.encode(attempt.signer.spki()),
                nonce = B64URL.encode(nonce),
                sig = B64URL.encode(signature),
                hello = attempt.clientHello,
            )
        } else {
            ReconnectAuthMessage(
                rgSpki = B64URL.encode(attempt.signer.spki()),
                nonce = B64URL.encode(nonce),
                sig = B64URL.encode(signature),
                hello = attempt.clientHello,
            )
        }
        sendFrame(output, LinkMessageCodec.encode(wire))

        return when (val result = readIncoming(input)) {
            is AuthOkMessage -> true
            is AuthErrMessage -> {
                lastFailure = LinkError.fromWireCode(result.code) ?: LinkError.AuthenticationFailed
                false
            }
            else -> { lastFailure = LinkError.AuthenticationFailed; false }
        }
    }

    // ------------------------------------------------------------- authenticated session

    private fun sessionLoop(tls: SSLSocket, input: InputStream) {
        inFlightSocket.set(tls)
        val output = tls.outputStream.buffered()
        val heartbeat = Thread({
            while (!stopped && tls.isConnected && !tls.isClosed) {
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
        }, "eyebrowse-link-client-heartbeat").apply { isDaemon = true; start() }
        var lastInbound = System.nanoTime()
        try {
            while (!stopped) {
                tls.soTimeout = timings.heartbeatIntervalMs.toInt()
                val frame = try {
                    LinkFrameCodec.readOne(input)
                } catch (e: java.net.SocketTimeoutException) {
                    if (elapsedMs(lastInbound) > timings.livenessTimeoutMs) break
                    continue
                }
                lastInbound = System.nanoTime()
                when (val incoming = readIncoming(frame)) {
                    is StatusMessage -> {
                        HostStatusValue.entries.firstOrNull { it.name == incoming.state }
                            ?.let { listener.onStatus(it) }
                    }
                    is PingMessage -> sendFrame(output, LinkMessageCodec.encode(PongMessage))
                    is PongMessage -> Unit // liveness refreshed
                    is ForgetNoticeMessage -> break // best-effort notice: peer forgot this endpoint
                    else -> Unit
                }
            }
        } catch (e: EOFException) {
            // peer closed
        } catch (e: Exception) {
            if (!stopped) { /* fallthrough: link lost */ }
        } finally {
            heartbeat.interrupt()
            closeQuietly(tls)
            inFlightSocket.compareAndSet(tls, null)
            if (!stopped) {
                listener.onStateChange(PairingState.PAIRED_DISCONNECTED)
                listener.onLinkLost()
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private fun readIncoming(input: InputStream): Any? {
        val frame = LinkFrameCodec.readOne(input)
        return decodeMessage(frame)
    }

    private fun readIncoming(payload: ByteArray): Any? = decodeMessage(payload)

    private fun decodeMessage(payload: ByteArray): Any? {
        val incoming = LinkMessageCodec.decode(payload).getOrNull() ?: return null
        return when (incoming) {
            is LinkMessageCodec.Incoming.Known -> incoming.message
            is LinkMessageCodec.Incoming.Unknown -> LinkMessageCodec.UnknownMessage
        }
    }

    private fun sendFrame(output: java.io.OutputStream, payload: ByteArray) {
        output.write(LinkFrameCodec.encode(payload))
        output.flush()
    }

    private fun elapsedMs(sinceNanos: Long) = (System.nanoTime() - sinceNanos) / 1_000_000

    internal fun closeQuietly(socket: java.net.Socket) {
        try { socket.close() } catch (e: Exception) { /* bounded best-effort close */ }
    }
}
