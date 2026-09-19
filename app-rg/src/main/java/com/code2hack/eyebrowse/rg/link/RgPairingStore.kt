package com.code2hack.eyebrowse.rg.link

import android.content.Context
import com.code2hack.eyebrowse.core.link.session.FilePeerTrustStore
import com.code2hack.eyebrowse.core.link.session.PeerTrustStore
import java.io.File

/**
 * RG-side persisted trust: the pinned Phone SPKI plus last successful explicit locators and
 * protocol metadata (ticket plan §4.3). App-private file, atomic commit, fail-closed reads.
 */
class RgPairingStore private constructor(delegate: PeerTrustStore) : PeerTrustStore by delegate {

    constructor(context: Context) : this(
        FilePeerTrustStore(
            File(File(context.applicationContext.filesDir, "pairing"), "peer_trust.json"),
        ),
    )

    /** JVM-testable seam over the same store semantics. */
    constructor(file: File) : this(FilePeerTrustStore(file) as PeerTrustStore)
}
