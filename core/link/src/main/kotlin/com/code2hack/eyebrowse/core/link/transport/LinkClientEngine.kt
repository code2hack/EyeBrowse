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
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket

/**
 * TLS client engine for the RG (ticket plan §4.1/§4.2, §6 RgLinkClient).
 *
 * Security properties enforced here:
 * - the server certificate is accepted ONLY when its leaf SPKI SHA-256 equals the pin carried by
 *   the invitation (initial) or the stored peer record (reconnect) — no accept-all manager;
 * - a fresh ECDSA proof over the versioned transcript (fresh nonce) authorizes the session;
 * - no invitation secret is sent on reconnect;
 * - ONE monotonic absolute operation deadline is captured at connect() invocation and carried
 *   unchanged through all locator attempts; after TCP succeeds, TLS + application auth share one
 *   absolute auth deadline additionally capped by the operation deadline (review R3/B4);
 * - each connect operation owns its raw socket BEFORE blocking Socket.connect(); raw→TLS ownership,
 *   cancellation and the final trust/CONNECTED commit are serialized by one operation lock so
 *   Cancel cannot race through local trust persistence (review R2/B3);
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

    /**
     * Per-connect cancellation/ownership token. Every field except [deadlineNanos] is accessed
     * only while holding [operationLock].
     */
    private class Operation(
        val deadlineNanos: Long,
        var cancelled: Boolean = false,
        var established: Boolean = false,
        var socket: Socket? = null,
    )

    private val running = AtomicBoolean(false)
    private val operationLock = Any()
    private var activeOperation: Operation? = null
    private var lastFailure: LinkError = LinkError.NetworkUnreachable

    /**
     * JVM-test seam only. Production leaves this null and calls Socket.connect directly.
     * Tests use it to deterministically hold or delay the TCP-connect phase while preserving
     * the production socket object and cancellation ownership.
     */
    internal var tcpConnectForTest: ((Socket, InetSocketAddress, Int) -> Unit)? = null

    /**
     * JVM-test seam only. Runs after wire authentication succeeds but before the final
     * cancellation-vs-trust-commit lock, allowing the precommit race to be exercised exactly.
     */
    internal var beforeFinalCommitForTest: (() -> Unit)? = null

    val isBusy: Boolean get() = running.get()

    /** Starts one bounded connect operation on a worker thread. */
    fun connect(attempt: Attempt) {
        check(running.compareAndSet(false, true)) { "client operation already running" }
        val operation = Operation(
            deadlineNanos = deadlineFromNowMs(timings.operationBudgetMs),
        )
        synchronized(operationLock) {
            check(activeOperation == null) { "client operation ownership not released" }
            activeOperation = operation
        }
        Thread({ runOperation(attempt, operation) }, "eyebrowse-link-client").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Bounded disconnect: atomically cancels the current operation and takes its owned socket,
     * then closes the socket outside the lock. Because raw TCP ownership is published before
     * connect(), this interrupts TCP connect, TLS/auth I/O and an established session.
     */
    fun disconnect() {
        val socketToClose = synchronized(operationLock) {
            val operation = activeOperation ?: return
            operation.cancelled = true
            operation.socket.also { operation.socket = null }
        }
        socketToClose?.let(::closeQuietly)
    }

    // ------------------------------------------------------------- operation

    private fun runOperation(attempt: Attempt, operation: Operation) {
        try {
            if (!emitStateIfLive(operation, PairingState.CONNECTING)) return
            var lastError: LinkError = LinkError.NetworkUnreachable

            for (locator in LocatorPolicy.orderForAttempts(attempt.locators)) {
                if (isCancelled(operation) || remainingMs(operation.deadlineNanos) <= 0) break
                val established = establishSession(attempt, locator, operation)
                if (isCancelled(operation)) {
                    established?.let { releaseAndClose(operation, it.first) }
                    return
                }
                if (established != null) {
                    // CONNECTED was emitted atomically with the trust callback in establishSession.
                    sessionLoop(established.first, established.second, operation)
                    return
                }
                lastError = lastFailure
                if (isAuthoritativeFailure(lastError)) break
            }
            reportFailureIfLive(operation, lastError)
        } finally {
            finishOperation(operation)
            running.set(false)
        }
    }

    /**
     * TCP connect + TLS + application authentication against one locator. The absolute operation
     * deadline is read from [operation]; after TCP succeeds, [authDeadlineNanos] is derived once
     * and carried unchanged through TLS handshake plus every application-auth read.
     */
    private fun establishSession(
        attempt: Attempt,
        locator: Locator,
        operation: Operation,
    ): Pair<SSLSocket, InputStream>? {
        lastFailure = LinkError.NetworkUnreachable
        val raw = Socket()
        if (!publishOwnedSocket(operation, raw)) {
            closeQuietly(raw)
            return null
        }

        val address = InetSocketAddress(locator.address, locator.port)
        try {
            val connectTimeout = boundedTimeoutMs(
                operation.deadlineNanos,
                timings.connectTimeoutMs,
            ) ?: return null.also { releaseAndClose(operation, raw) }
            connectRaw(raw, address, connectTimeout)
            raw.tcpNoDelay = true
        } catch (e: Exception) {
            releaseAndClose(operation, raw)
            return null
        }

        if (!canProceed(operation, operation.deadlineNanos)) {
            releaseAndClose(operation, raw)
            return null
        }

        // TLS handshake + ALL application-auth work share this one aggregate auth deadline.
        val authDeadlineNanos = minOf(
            operation.deadlineNanos,
            deadlineFromNowMs(timings.authTimeoutMs),
        )

        val tls = try {
            val context = SSLContext.getInstance("TLS")
            val trustManager = SpkiPinningTrustManager(attempt.phoneSpkiSha256Hex)
            context.init(null, arrayOf(trustManager), null)
            context.socketFactory.createSocket(
                raw,
                locator.address.hostAddress,
                locator.port,
                true,
            ) as SSLSocket
        } catch (e: Exception) {
            releaseAndClose(operation, raw)
            lastFailure = LinkError.WrongPhoneIdentity
            return null
        }

        if (!replaceOwnedSocket(operation, raw, tls)) {
            closeQuietly(tls)
            return null
        }

        // SO_TIMEOUT is per underlying socket read, and JSSE may perform multiple internal reads
        // while one TLS/application read is pending (for example post-handshake records). Enforce
        // the absolute TLS+auth deadline independently by closing the currently owned socket at
        // that deadline; this prevents internal reads from renewing the aggregate budget.
        armDeadlineCloser(operation, authDeadlineNanos)

        try {
            tls.setEnabledProtocols(arrayOf(TLS_V1_3))
            tls.useClientMode = true

            if (!prepareBlockingRead(tls, operation, authDeadlineNanos)) {
                lastFailure = LinkError.AuthenticationFailed
                return null.also { releaseAndClose(operation, tls) }
            }
            lastFailure = LinkError.AuthenticationFailed
            try {
                tls.startHandshake()
            } catch (e: SSLHandshakeException) {
                lastFailure = LinkError.WrongPhoneIdentity
                return null.also { releaseAndClose(operation, tls) }
            }

            if (!canProceed(operation, authDeadlineNanos)) {
                lastFailure = LinkError.AuthenticationFailed
                return null.also { releaseAndClose(operation, tls) }
            }
            if (!emitStateIfLive(operation, PairingState.AUTHENTICATING)) {
                return null.also { releaseAndClose(operation, tls) }
            }

            lastFailure = LinkError.AuthenticationFailed
            if (!authenticate(tls, attempt, operation, authDeadlineNanos)) {
                return null.also { releaseAndClose(operation, tls) }
            }

            beforeFinalCommitForTest?.invoke()

            // Linearization point for Cancel versus local trust persistence + CONNECTED.
            val phoneSpki = tls.session.peerCertificates[0].publicKey.encoded
            val committed = synchronized(operationLock) {
                if (
                    activeOperation !== operation ||
                    operation.cancelled ||
                    remainingMs(operation.deadlineNanos) <= 0 ||
                    remainingMs(authDeadlineNanos) <= 0
                ) {
                    false
                } else {
                    operation.established = true
                    listener.onAuthenticated(phoneSpki, locator)
                    listener.onStateChange(PairingState.CONNECTED)
                    true
                }
            }
            if (!committed) {
                releaseAndClose(operation, tls)
                return null
            }

            return Pair(tls, tls.inputStream.buffered())
        } catch (e: Exception) {
            releaseAndClose(operation, tls)
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

    /**
     * Performs hello exchange + proof. Before EVERY blocking auth read, the socket timeout is
     * recomputed from the same absolute auth deadline (which is itself capped by the operation
     * deadline). No auth read receives a fresh full timeout window.
     */
    private fun authenticate(
        tls: SSLSocket,
        attempt: Attempt,
        operation: Operation,
        authDeadlineNanos: Long,
    ): Boolean {
        if (!canProceed(operation, authDeadlineNanos)) return false
        val output = tls.outputStream.buffered()
        val input = tls.inputStream.buffered()
        sendFrame(output, LinkMessageCodec.encode(attempt.clientHello))

        if (!prepareBlockingRead(tls, operation, authDeadlineNanos)) return false
        val serverHello = readIncoming(input) as? HelloMessage ?: run {
            lastFailure = LinkError.IncompatibleProtocol
            return false
        }
        if (serverHello.pmj != LinkProtocol.MAJOR || !serverHello.hasRequiredCapabilities()) {
            lastFailure = LinkError.IncompatibleProtocol
            return false
        }

        if (!prepareBlockingRead(tls, operation, authDeadlineNanos)) return false
        val challenge = readIncoming(input) as? ChallengeMessage ?: run {
            lastFailure = LinkError.IncompatibleProtocol
            return false
        }
        val nonce = B64URL.decode(challenge.nonce) ?: run {
            lastFailure = LinkError.AuthenticationFailed
            return false
        }

        if (!canProceed(operation, authDeadlineNanos)) return false
        val phoneSpki = tls.session.peerCertificates[0].publicKey.encoded
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
        if (!canProceed(operation, authDeadlineNanos)) return false

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

        if (!prepareBlockingRead(tls, operation, authDeadlineNanos)) return false
        return when (val result = readIncoming(input)) {
            is AuthOkMessage -> true
            is AuthErrMessage -> {
                lastFailure = LinkError.fromWireCode(result.code) ?: LinkError.AuthenticationFailed
                false
            }
            else -> {
                lastFailure = LinkError.AuthenticationFailed
                false
            }
        }
    }

    // ------------------------------------------------------------- authenticated session

    private fun sessionLoop(tls: SSLSocket, input: InputStream, operation: Operation) {
        val output = tls.outputStream.buffered()
        val heartbeat = Thread({
            while (!isCancelled(operation) && tls.isConnected && !tls.isClosed) {
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
        }, "eyebrowse-link-client-heartbeat").apply {
            isDaemon = true
            start()
        }
        var lastInbound = System.nanoTime()
        try {
            while (!isCancelled(operation)) {
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
                    is PongMessage -> Unit
                    is ForgetNoticeMessage -> break
                    else -> Unit
                }
            }
        } catch (e: EOFException) {
            // peer closed
        } catch (e: Exception) {
            // deliberate cancellation is distinguished below under the operation lock
        } finally {
            heartbeat.interrupt()
            releaseAndClose(operation, tls)
            notifyLinkLostIfLive(operation)
        }
    }

    // ------------------------------------------------------------- cancellation / deadlines / ownership

    private fun connectRaw(socket: Socket, address: InetSocketAddress, timeoutMs: Int) {
        val seam = tcpConnectForTest
        if (seam != null) {
            seam(socket, address, timeoutMs)
        } else {
            socket.connect(address, timeoutMs)
        }
    }

    /**
     * Hard guard for an absolute pre-commit deadline. Socket SO_TIMEOUT remains useful for each
     * individual blocking call, but cannot by itself bound multiple internal JSSE reads. This
     * daemon closes whichever socket this operation still owns once [deadlineNanos] is reached.
     */
    private fun armDeadlineCloser(operation: Operation, deadlineNanos: Long) {
        Thread({
            while (true) {
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0) break
                try {
                    TimeUnit.NANOSECONDS.sleep(remaining)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
            val socketToClose = synchronized(operationLock) {
                if (
                    activeOperation !== operation ||
                    operation.cancelled ||
                    operation.established ||
                    System.nanoTime() < deadlineNanos
                ) {
                    null
                } else {
                    operation.socket.also { operation.socket = null }
                }
            }
            socketToClose?.let(::closeQuietly)
        }, "eyebrowse-link-auth-deadline").apply {
            isDaemon = true
            start()
        }
    }

    private fun publishOwnedSocket(operation: Operation, socket: Socket): Boolean =
        synchronized(operationLock) {
            if (activeOperation !== operation || operation.cancelled) {
                false
            } else {
                check(operation.socket == null) { "socket ownership already populated" }
                operation.socket = socket
                true
            }
        }

    private fun replaceOwnedSocket(operation: Operation, from: Socket, to: Socket): Boolean =
        synchronized(operationLock) {
            if (
                activeOperation !== operation ||
                operation.cancelled ||
                operation.socket !== from
            ) {
                false
            } else {
                operation.socket = to
                true
            }
        }

    private fun releaseAndClose(operation: Operation, socket: Socket) {
        synchronized(operationLock) {
            if (activeOperation === operation && operation.socket === socket) {
                operation.socket = null
            }
        }
        closeQuietly(socket)
    }

    private fun finishOperation(operation: Operation) {
        val socketToClose = synchronized(operationLock) {
            if (activeOperation !== operation) {
                null
            } else {
                operation.socket.also {
                    operation.socket = null
                    activeOperation = null
                }
            }
        }
        socketToClose?.let(::closeQuietly)
    }

    private fun isCancelled(operation: Operation): Boolean =
        synchronized(operationLock) {
            activeOperation !== operation || operation.cancelled
        }

    private fun canProceed(operation: Operation, deadlineNanos: Long): Boolean =
        synchronized(operationLock) {
            activeOperation === operation &&
                !operation.cancelled &&
                remainingMs(deadlineNanos) > 0
        }

    private fun emitStateIfLive(operation: Operation, state: PairingState): Boolean =
        synchronized(operationLock) {
            if (activeOperation !== operation || operation.cancelled) {
                false
            } else {
                listener.onStateChange(state)
                true
            }
        }

    private fun reportFailureIfLive(operation: Operation, error: LinkError) {
        synchronized(operationLock) {
            if (activeOperation === operation && !operation.cancelled) {
                listener.onConnectFailed(error)
            }
        }
    }

    private fun notifyLinkLostIfLive(operation: Operation) {
        synchronized(operationLock) {
            if (activeOperation === operation && !operation.cancelled) {
                listener.onStateChange(PairingState.PAIRED_DISCONNECTED)
                listener.onLinkLost()
            }
        }
    }

    private fun prepareBlockingRead(
        tls: SSLSocket,
        operation: Operation,
        deadlineNanos: Long,
    ): Boolean {
        val timeout = synchronized(operationLock) {
            if (activeOperation !== operation || operation.cancelled) {
                null
            } else {
                boundedTimeoutMs(deadlineNanos, Long.MAX_VALUE)
            }
        } ?: return false
        tls.soTimeout = timeout
        return true
    }

    private fun boundedTimeoutMs(deadlineNanos: Long, phaseCapMs: Long): Int? {
        val remaining = remainingMs(deadlineNanos)
        if (remaining <= 0) return null
        return minOf(phaseCapMs, remaining, Int.MAX_VALUE.toLong())
            .coerceAtLeast(1)
            .toInt()
    }

    private fun remainingMs(deadlineNanos: Long): Long {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0) return 0
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1)
    }

    private fun deadlineFromNowMs(durationMs: Long): Long =
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs.coerceAtLeast(0))

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

    internal fun closeQuietly(socket: Socket) {
        // Failed/cancelled TLS cleanup is part of the bounded operation. Force an abortive TCP
        // close so SSLSocket.close() cannot spend another read-timeout window on TLS shutdown
        // after the operation/auth deadline has already expired (review B3/B4).
        try {
            if (!socket.isClosed) socket.setSoLinger(true, 0)
        } catch (e: Exception) {
            // best effort; close below remains mandatory
        }
        try {
            socket.close()
        } catch (e: Exception) {
            // bounded best-effort close
        }
    }
}
