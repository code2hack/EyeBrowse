package com.code2hack.eyebrowse.core.link.session

import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.invitation.toLinkErrorOrNull
import com.code2hack.eyebrowse.core.link.messages.StatusMessage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Plan §10-B: bounded queue, binding/replacement rules, persisted peer state + Forget. */
class SessionUnitTest {

    // ------------------------------------------------------------- OutboundQueue

    @Test
    fun `queue caps at 16 and refuses overflow for non-status`() {
        val queue = OutboundQueue()
        repeat(LinkProtocol.OUTBOUND_QUEUE_MAX) { i ->
            assertTrue(queue.offer("m$i"))
        }
        assertEquals(LinkProtocol.OUTBOUND_QUEUE_MAX, queue.size)
        assertFalse(queue.offer("m-overflow"))
    }

    @Test
    fun `status messages coalesce to latest state and never build history`() {
        val queue = OutboundQueue()
        assertTrue(queue.offer("m1"))
        assertTrue(queue.offer(StatusMessage.of(HostStatusValue.HOST_STARTING)))
        assertTrue(queue.offer("m2"))
        assertTrue(queue.offer(StatusMessage.of(HostStatusValue.HOSTING)))
        // Newest status replaces the pending status slot in place (latest-state, bounded).
        assertEquals(
            listOf<Any>("m1", StatusMessage.of(HostStatusValue.HOSTING), "m2"),
            queue.snapshot(),
        )
        // Statuses only: the queue must stay bounded with exactly one pending status.
        val statusOnly = OutboundQueue()
        repeat(100) { i ->
            assertTrue(
                statusOnly.offer(
                    StatusMessage.of(if (i % 2 == 0) HostStatusValue.HOSTING else HostStatusValue.HOST_INACTIVE),
                ),
            )
            assertEquals(1, statusOnly.size)
        }
        assertEquals(StatusMessage.of(HostStatusValue.HOST_INACTIVE), statusOnly.poll())
    }

    @Test
    fun `full queue with incoming status drops oldest to admit latest state`() {
        val queue = OutboundQueue()
        repeat(LinkProtocol.OUTBOUND_QUEUE_MAX) { assertTrue(queue.offer("m$it")) }
        assertTrue(queue.offer(StatusMessage.of(HostStatusValue.HOSTING)))
        assertEquals(LinkProtocol.OUTBOUND_QUEUE_MAX, queue.size)
        assertEquals("m1", queue.poll()) // oldest ("m0") was evicted to admit the latest status
    }

    // ------------------------------------------------------------- BindingPolicy

    private val rgA = ByteArray(91) { 0x0A }
    private val rgB = ByteArray(91) { 0x0B }

    @Test
    fun `phone accepts initial pairing when unpaired or same peer`() {
        assertNull(BindingPolicy.phoneAcceptInitial(null, rgA))
        assertNull(BindingPolicy.phoneAcceptInitial(rgA, rgA))
    }

    @Test
    fun `phone refuses a different rg while paired until explicit forget`() {
        assertEquals(LinkError.PeerReplacementRequired, BindingPolicy.phoneAcceptInitial(rgA, rgB))
    }

    @Test
    fun `phone reconnect requires stored matching peer`() {
        assertNull(BindingPolicy.phoneAcceptReconnect(rgA, rgA))
        assertEquals(LinkError.WrongRgIdentity, BindingPolicy.phoneAcceptReconnect(rgA, rgB))
        assertEquals(LinkError.WrongRgIdentity, BindingPolicy.phoneAcceptReconnect(null, rgA))
    }

    @Test
    fun `rg refuses a different phone in the QR while paired`() {
        assertNull(BindingPolicy.rgAcceptInvitation(null, "fpA"))
        assertNull(BindingPolicy.rgAcceptInvitation("fpA", "fpA"))
        assertEquals(LinkError.PeerReplacementRequired, BindingPolicy.rgAcceptInvitation("fpA", "fpB"))
    }

    // ------------------------------------------------------------- FilePeerTrustStore

    @get:Rule
    val tmp = TemporaryFolder()

    private fun record() = PeerTrustRecord(
        peerSpkiSha256Hex = "ab".repeat(32),
        peerSpkiB64 = "c3BraS1kZXI",
        lastLocators = listOf("192.168.1.20:39818"),
        protocolMajor = 1,
        protocolMinor = 0,
        peerCapabilities = listOf("PAIRING_V1", "STATUS_V1"),
        label = "Living room glasses",
    )

    @Test
    fun `peer trust persists and reloads across a fresh store instance`() {
        val file = File(tmp.root, "pairing/peer_trust.json")
        val store = FilePeerTrustStore(file)
        assertFalse(store.isPaired())
        assertNull(store.load())
        store.save(record())
        assertTrue(store.isPaired())
        assertEquals(record(), FilePeerTrustStore(file).load())
    }

    @Test
    fun `corrupt trust store fails closed as unpaired`() {
        val file = File(tmp.root, "peer.json")
        val store = FilePeerTrustStore(file)
        store.save(record())
        file.writeText("{ definitely not json")
        assertNull(store.load())
        assertFalse(store.isPaired())
    }

    @Test
    fun `forget clears trust and re-pair works`() {
        val file = File(tmp.root, "peer.json")
        val store = FilePeerTrustStore(file)
        store.save(record())
        store.clear()
        assertNull(store.load())
        store.save(record())
        assertNotNull(store.load())
    }

    @Test
    fun `trust record has no field that could carry an invitation secret`() {
        // Structural proof for plan §4.3 ("Do not persist invitation secrets"):
        // the record serializes exactly the documented non-secret fields.
        val json = kotlinx.serialization.json.Json.encodeToString(
            PeerTrustRecord.serializer(),
            record(),
        )
        for (forbidden in listOf("secret", "invitationSecret", "sec=", "token")) {
            assertFalse(json.contains(forbidden, ignoreCase = false))
        }
    }
}
