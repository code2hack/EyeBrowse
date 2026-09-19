package com.code2hack.eyebrowse.phone.link

import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.phone.HostingController

/**
 * Read-only projection of the hosting state for the authenticated link (ticket plan §6:
 * "Pairing/link code MUST only read HostingController.status()"). No start/stop/lease access.
 */
object PhoneStatusProvider {

    fun toHostStatus(state: HostingController.State): HostStatusValue = when (state) {
        HostingController.State.NOT_HOSTING -> HostStatusValue.HOST_INACTIVE
        HostingController.State.STARTING -> HostStatusValue.HOST_STARTING
        HostingController.State.HOSTING -> HostStatusValue.HOSTING
        HostingController.State.STOPPING -> HostStatusValue.HOST_STOPPING
    }
}
