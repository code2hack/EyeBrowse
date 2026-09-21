package com.code2hack.eyebrowse.phone.link

import android.content.Context
import com.code2hack.eyebrowse.core.link.session.FilePeerTrustStore
import com.code2hack.eyebrowse.core.link.session.PeerTrustStore
import java.io.File

/**
 * Phone-side persisted trust: the ONE remembered RG public identity plus protocol metadata
 * (ticket plan §4.3). App-private file, atomic commit, invitation secrets structurally absent.
 */
class PhonePairingStore private constructor(delegate: PeerTrustStore) : PeerTrustStore by delegate {

    constructor(context: Context) : this(
        FilePeerTrustStore(
            File(File(context.applicationContext.filesDir, "pairing"), "peer_trust.json"),
        ),
    )

    /** JVM-testable seam over the same store semantics. */
    constructor(file: File) : this(FilePeerTrustStore(file) as PeerTrustStore)
}
