package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.LinkTimings
import com.code2hack.eyebrowse.core.link.crypto.TlsServerIdentity
import com.code2hack.eyebrowse.core.link.framing.LinkFrameCodec
import com.code2hack.eyebrowse.core.link.invitation.B64URL
import com.code2hack.eyebrowse.core.link.invitation.InvitationLifecycle
import com.code2hack.eyebrowse.core.link.messages.ChallengeMessage
import com.code2hack.eyebrowse.core.link.messages.HelloMessage
import com.code2hack.eyebrowse.core.link.messages.LinkMessageCodec
import com.code2hack.eyebrowse.core.link.session.PeerTrustRead
import com.code2hack.eyebrowse.core.link.session.PeerTrustRecord
import com.code2hack.eyebrowse.core.link.testfix.TestCrypto
import java.io.InputStream
import java.net.InetAddress
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Shared TLS-engine harness: software phone/RG identities, controllable trust, raw wire client. */
object Harness {

    val FAST_TIMINGS = LinkTimings(
        connectTimeoutMs = 1_000,
        authTimeoutMs = 2_000,
        heartbeatIntervalMs = 100,
        livenessTimeoutMs = 500,
        operationBudgetMs = 4_000,
    )

    fun await(latch: CountDownLatch, timeoutMs: Long = 10_000): Boolean =
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)

    class ServerHarness(
        val timings: LinkTimings = FAST_TIMINGS,
        keyPair: KeyPair = TestCrypto.ecKeyPair(),
        val lifecycle: InvitationLifecycle = InvitationLifecycle(System::currentTimeMillis),
    ) {
        val keyPair: KeyPair = keyPair
        val identity: TlsServerIdentity =
            TestCrypto.softwareTlsIdentity(keyPair, TestCrypto.selfSignedCertificate(keyPair, "eb-phone-test"))
        val storedPeerSpki = AtomicReference<ByteArray?>(null)

        /** Review R5/B6 regression seam: force the store read to report CORRUPT. */
        @Volatile
        var corruptTrust: Boolean = false

        /** Review R2/R3 regression seam: when set, serverHello blocks until released. */
        @Volatile
        var helloGate: CountDownLatch? = null

        /** Review R3/B4 regression seam: deterministic delay before server Hello is returned. */
        @Volatile
        var serverHelloDelayMs: Long = 0

        /** Review R3/B4 regression seam: deterministic delay before invitation consumption/AuthOk. */
        @Volatile
        var consumeInvitationDelayMs: Long = 0

        val committedPeer = AtomicReference<ByteArray?>(null)
        val committedClientHello = AtomicReference<HelloMessage?>(null)
        val committed = CountDownLatch(1)
        val hostStatus = AtomicReference(HostStatusValue.HOST_INACTIVE)
        val authFailures = LinkedBlockingDeque<LinkError>()
        val linkUp = CountDownLatch(1)
        val linkDown = CountDownLatch(1)
        val linkLostNotifications = java.util.concurrent.atomic.AtomicInteger(0)
        lateinit var engine: LinkServerEngine

        private val trustController = object : LinkServerEngine.TrustController {
            override fun serverHello(): HelloMessage {
                helloGate?.await()
                if (serverHelloDelayMs > 0) Thread.sleep(serverHelloDelayMs)
                return HelloMessage(LinkProtocol.MAJOR, LinkProtocol.MINOR, LinkProtocol.REQUIRED_CAPABILITIES)
            }

            override fun consumeInvitation(id: String, secretB64: String): InvitationLifecycle.ConsumeOutcome {
                if (consumeInvitationDelayMs > 0) Thread.sleep(consumeInvitationDelayMs)
                val secret = B64URL.decode(secretB64) ?: return InvitationLifecycle.ConsumeOutcome.Invalid
                return lifecycle.consume(id, secret)
            }

            override fun pairedPeer(): PeerTrustRead = when {
                corruptTrust -> PeerTrustRead.Corrupt
                storedPeerSpki.get() != null -> PeerTrustRead.Valid(
                    PeerTrustRecord(
                        peerSpkiSha256Hex = com.code2hack.eyebrowse.core.link.crypto.SpkiFingerprint.sha256Hex(
                            storedPeerSpki.get()!!,
                        ),
                        peerSpkiB64 = B64URL.encode(storedPeerSpki.get()!!),
                        lastLocators = listOf(),
                        protocolMajor = LinkProtocol.MAJOR,
                        protocolMinor = LinkProtocol.MINOR,
                        peerCapabilities = LinkProtocol.REQUIRED_CAPABILITIES,
                    ),
                )
                else -> PeerTrustRead.Absent
            }

            override fun commitPairedPeer(spki: ByteArray, clientHello: HelloMessage) {
                committedPeer.set(spki.copyOf())
                committedClientHello.set(clientHello)
                storedPeerSpki.set(spki.copyOf())
                committed.countDown()
            }

            override fun currentHostStatus(): HostStatusValue = hostStatus.get()

            override fun onLinkLost() {
                linkLostNotifications.incrementAndGet()
            }
        }

        private val listener = object : LinkServerEngine.Listener {
            override fun onAuthFailed(error: LinkError) {
                authFailures.add(error)
            }

            override fun onLinkUp() {
                linkUp.countDown()
            }

            override fun onLinkDown() {
                linkDown.countDown()
            }
        }

        fun start(): Int {
            engine = LinkServerEngine(identity, trustController, timings, listener)
            engine.start(0)
            return engine.boundPort()
        }

        fun stop() = engine.stop()

        fun generateInvitation(
            secret: ByteArray = randomBytes(LinkProtocol.INVITATION_SECRET_BYTES),
        ): Pair<String, ByteArray> {
            // Production ids are base64url of 16 random bytes (codec-enforced); mirror that here.
            val id = B64URL.encode(randomBytes(LinkProtocol.INVITATION_ID_BYTES))
            lifecycle.generate(id, secret)
            return id to secret
        }
    }

    class ClientHarness(
        keyPair: KeyPair = TestCrypto.ecKeyPair(),
        val timings: LinkTimings = FAST_TIMINGS,
    ) {
        val keyPair: KeyPair = keyPair
        val signer = TestCrypto.softwareSigningIdentity(keyPair)
        val connected = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val connectFailed = LinkedBlockingDeque<LinkError>()
        val statuses = LinkedBlockingDeque<HostStatusValue>()
        val stateChanges = LinkedBlockingDeque<com.code2hack.eyebrowse.core.link.PairingState>()
        val stateTimesNanos = ConcurrentHashMap<com.code2hack.eyebrowse.core.link.PairingState, Long>()
        val authenticated = LinkedBlockingDeque<ByteArray>()

        private val listener = object : LinkClientEngine.Listener {
            override fun onStateChange(state: com.code2hack.eyebrowse.core.link.PairingState) {
                stateTimesNanos.putIfAbsent(state, System.nanoTime())
                stateChanges.add(state)
                if (state == com.code2hack.eyebrowse.core.link.PairingState.CONNECTED) connected.countDown()
                if (state == com.code2hack.eyebrowse.core.link.PairingState.PAIRED_DISCONNECTED) {
                    disconnected.countDown()
                }
            }

            override fun onAuthenticated(phoneSpki: ByteArray, usedLocator: Locator) {
                authenticated.add(phoneSpki.copyOf())
            }

            override fun onStatus(status: HostStatusValue) {
                statuses.add(status)
            }

            override fun onLinkLost() {}

            override fun onConnectFailed(error: LinkError) {
                connectFailed.add(error)
            }
        }

        lateinit var engine: LinkClientEngine

        fun startEngine() {
            engine = LinkClientEngine(timings, listener)
        }

        fun hello(): HelloMessage =
            HelloMessage(LinkProtocol.MAJOR, LinkProtocol.MINOR, LinkProtocol.REQUIRED_CAPABILITIES)

        fun attempt(
            phonePinHex: String,
            port: Int,
            invitation: Pair<String, ByteArray>? = null,
            signerOverride: com.code2hack.eyebrowse.core.link.crypto.RgSigningIdentity = signer,
        ): LinkClientEngine.Attempt = LinkClientEngine.Attempt(
            phoneSpkiSha256Hex = phonePinHex,
            locators = listOf(Locator(InetAddress.getLoopbackAddress(), port)),
            signer = signerOverride,
            clientHello = hello(),
            invitation = invitation,
        )
    }

    /**
     * Minimal raw wire client used for protocol-negative tests; pins the phone SPKI exactly like
     * production (same trust-manager class).
     */
    class RawClient(
        port: Int,
        phoneSpkiHex: String,
        val keyPair: KeyPair = TestCrypto.ecKeyPair(),
    ) {
        val socket: SSLSocket
        private val input: InputStream

        init {
            val raw = java.net.Socket()
            raw.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000)
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf(SpkiPinningTrustManager(phoneSpkiHex)), null)
            socket = context.socketFactory.createSocket(
                raw,
                InetAddress.getLoopbackAddress().hostAddress,
                port,
                true,
            ) as SSLSocket
            socket.setEnabledProtocols(arrayOf(TLS_V1_3))
            socket.soTimeout = 3_000
            socket.startHandshake()
            input = socket.inputStream.buffered()
        }

        fun send(message: Any) {
            val output = socket.outputStream.buffered()
            output.write(LinkFrameCodec.encode(LinkMessageCodec.encode(message)))
            output.flush()
        }

        fun sendRawFrame(payload: ByteArray) {
            val output = socket.outputStream.buffered()
            output.write(LinkFrameCodec.encode(payload))
            output.flush()
        }

        fun sendRawBytes(bytes: ByteArray) {
            val output = socket.outputStream.buffered()
            output.write(bytes)
            output.flush()
        }

        fun readRawFrame(): ByteArray? = try {
            LinkFrameCodec.readOne(input)
        } catch (e: java.io.EOFException) {
            null
        } catch (e: java.net.SocketTimeoutException) {
            throw AssertionError("read timed out", e)
        }

        fun readMessage(): Any? {
            val frame = readRawFrame() ?: return null
            val decoded = LinkMessageCodec.decode(frame).getOrNull() ?: return null
            return when (decoded) {
                is LinkMessageCodec.Incoming.Known -> decoded.message
                is LinkMessageCodec.Incoming.Unknown -> LinkMessageCodec.UnknownMessage
            }
        }

        fun phoneSpki(): ByteArray = socket.session.peerCertificates[0].publicKey.encoded

        fun close() = socket.close()

        // ----- protocol helpers for negative tests

        private var lastNonce: ByteArray? = null

        fun sendHello(
            major: Int = LinkProtocol.MAJOR,
            minor: Int = LinkProtocol.MINOR,
            caps: List<String> = LinkProtocol.REQUIRED_CAPABILITIES,
        ) = send(HelloMessage(major, minor, caps))

        /** Reads server hello + challenge; remembers the nonce for the auth builders. */
        fun challenge(): ByteArray {
            val first = readMessage()
            check(first is HelloMessage) { "expected server hello, got $first" }
            val challenge = readMessage()
            check(challenge is ChallengeMessage) { "expected challenge, got $challenge" }
            val nonce = B64URL.decode(challenge.nonce)!!
            lastNonce = nonce
            return nonce
        }

        fun expectChallenge(): ByteArray = challenge()

        fun sendPairAuth(
            invitationId: String,
            secret: ByteArray,
            nonceOverride: ByteArray? = null,
            signKey: KeyPair = keyPair,
            presentedSpki: ByteArray = keyPair.public.encoded,
        ) {
            val nonce = nonceOverride ?: lastNonce ?: error("challenge not read")
            val transcript = com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.build(
                purpose = com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.Purpose.INITIAL_PAIRING,
                protocolMajor = LinkProtocol.MAJOR,
                protocolMinor = LinkProtocol.MINOR,
                phoneSpki = phoneSpki(),
                rgSpki = presentedSpki,
                nonce = nonce,
                invitationId = invitationId,
            )
            val signature = com.code2hack.eyebrowse.core.link.crypto.ChallengeTranscript.sign(
                transcript,
                signKey.private,
            )
            send(
                com.code2hack.eyebrowse.core.link.messages.PairAuthMessage(
                    iid = invitationId,
                    sec = B64URL.encode(secret),
                    rgSpki = B64URL.encode(presentedSpki),
                    nonce = B64URL.encode(nonce),
                    sig = B64URL.encode(signature),
                    hello = HelloMessage(
                        LinkProtocol.MAJOR,
                        LinkProtocol.MINOR,
                        LinkProtocol.REQUIRED_CAPABILITIES,
                    ),
                ),
            )
        }

        fun sendPing() = send(com.code2hack.eyebrowse.core.link.messages.PingMessage)
    }
}

/** ByteArray helper for tests. */
fun randomBytes(n: Int): ByteArray =
    ByteArray(n).also { java.security.SecureRandom().nextBytes(it) }
