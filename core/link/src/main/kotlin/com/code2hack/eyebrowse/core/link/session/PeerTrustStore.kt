package com.code2hack.eyebrowse.core.link.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Persisted, app-private, NON-SECRET peer trust metadata (ticket plan §4.3): peer SPKI bytes and
 * fingerprint, last successful explicit locators, protocol major/minor/capabilities, optional
 * non-authoritative display label. Invitation secrets are structurally absent — they are never
 * persisted.
 *
 * Writes are atomic (temp file + rename) and identity is written before locators in the same
 * record so a torn write can never yield locator-only trust.
 */
@Serializable
data class PeerTrustRecord(
    /** Authoritative peer identity: SHA-256 SPKI fingerprint, lowercase hex. */
    val peerSpkiSha256Hex: String,
    /** Peer SPKI DER (base64url) — needed for transcript construction/verification. */
    val peerSpkiB64: String,
    /** Last successful explicit locators (validated wire form). */
    val lastLocators: List<String>,
    val protocolMajor: Int,
    val protocolMinor: Int,
    val peerCapabilities: List<String>,
    val label: String? = null,
) {
    init {
        require(lastLocators.size <= com.code2hack.eyebrowse.core.link.LinkProtocol.LOCATOR_MAX_CANDIDATES)
    }
}

/**
 * Tri-state trust read (review R5/B6). A CORRUPT store is NOT "unpaired": the endpoint cannot
 * prove whether a remembered peer exists, so fail-closed semantics apply — new pairing and
 * reconnect are refused until an explicit Forget clears the state. Returning null/unpaired for a
 * corrupt record would silently erase the peer-replacement guard.
 */
sealed class PeerTrustRead {
    /** No persisted trust. */
    object Absent : PeerTrustRead()

    /** Valid remembered peer. */
    data class Valid(val record: PeerTrustRecord) : PeerTrustRead()

    /** Persisted state exists but cannot be trusted; fail closed until explicit Forget. */
    object Corrupt : PeerTrustRead()
}

interface PeerTrustStore {
    fun read(): PeerTrustRead
    fun save(record: PeerTrustRecord)
    fun clear()
    fun isPaired(): Boolean = read() is PeerTrustRead.Valid
}

/** File-backed store using the endpoint's app-private directory (context.filesDir on Android). */
class FilePeerTrustStore(private val file: File) : PeerTrustStore {

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun read(): PeerTrustRead {
        if (!file.exists()) return PeerTrustRead.Absent
        return try {
            PeerTrustRead.Valid(json.decodeFromString(PeerTrustRecord.serializer(), file.readText(Charsets.UTF_8)))
        } catch (e: Exception) {
            // Fail closed: corrupt trust is explicitly CORRUPT, not "never paired" (review R5/B6).
            PeerTrustRead.Corrupt
        }
    }

    @Synchronized
    override fun save(record: PeerTrustRecord) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val tmp = File(parent, file.name + ".tmp")
        tmp.writeText(json.encodeToString(PeerTrustRecord.serializer(), record), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("atomic trust commit failed for ${file.name}")
        }
    }

    @Synchronized
    override fun clear() {
        if (file.exists()) {
            if (!file.delete()) throw IOException("failed to clear trust store ${file.name}")
        }
    }
}
