package com.code2hack.eyebrowse.phone.link

import com.code2hack.eyebrowse.core.link.session.PeerTrustRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Phone pairing store wiring: app-private file location, atomic commit, fail-closed corruption
 * handling, Forget semantics (plan §4.3/§8).
 */
class PhonePairingStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun storeIn(file: File) = PhonePairingStore(file)

    @Test
    fun `save load clear lifecycle`() {
        val store = storeIn(File(tmp.newFolder(), "pairing/peer_trust.json"))
        assertFalse(store.isPaired())
        assertNull(store.load())
        val record = PeerTrustRecord(
            peerSpkiSha256Hex = "cd".repeat(32),
            peerSpkiB64 = "c3BraQ",
            lastLocators = listOf(),
            protocolMajor = 1,
            protocolMinor = 0,
            peerCapabilities = listOf("PAIRING_V1", "STATUS_V1"),
        )
        store.save(record)
        assertTrue(store.isPaired())
        assertEquals(record, store.load())
        store.clear()
        assertFalse(store.isPaired())
    }

    @Test
    fun `corrupt file fails closed`() {
        val dir = tmp.newFolder()
        val store = storeIn(File(dir, "pairing/peer_trust.json"))
        store.save(
            PeerTrustRecord("ab".repeat(32), "c3BraQ", listOf(), 1, 0, listOf("PAIRING_V1", "STATUS_V1")),
        )
        File(dir, "pairing/peer_trust.json").writeText("garbage{")
        assertNull(store.load())
        assertFalse(store.isPaired())
    }
}
