package com.code2hack.eyebrowse.core.link

import com.code2hack.eyebrowse.core.link.messages.HelloMessage

/** Authenticated capability result; missing #7 capabilities is explicit update-required state. */
sealed class CapabilityNegotiation {
    data object Accepted : CapabilityNegotiation()
    data object IncompatibleMajor : CapabilityNegotiation()
    data object UpdateRequired : CapabilityNegotiation()
}

object CapabilityNegotiator {
    fun negotiate(peer: HelloMessage, requirePresentation: Boolean): CapabilityNegotiation =
        when {
            peer.pmj != LinkProtocol.MAJOR -> CapabilityNegotiation.IncompatibleMajor
            !peer.hasRequiredCapabilities() -> CapabilityNegotiation.IncompatibleMajor
            requirePresentation && !peer.hasPresentationCapabilities() ->
                CapabilityNegotiation.UpdateRequired
            else -> CapabilityNegotiation.Accepted
        }
}
