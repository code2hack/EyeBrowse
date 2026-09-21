package com.code2hack.eyebrowse.phone.link

import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.phone.HostingController
import org.junit.Assert.assertEquals
import org.junit.Test

/** Read-only hosting-status projection (plan §6/§7; pairing never touches hosting control). */
class PhoneStatusProviderTest {

    private fun status(state: HostingController.State) = HostingController.Status(
        state = state,
        generation = 1,
        attachment = HostingController.Attachment.NONE,
        browserLive = true,
        captureActive = false,
        wakeLockHeld = false,
        failureReason = null,
    )

    @Test
    fun `every hosting state maps to its observation value`() {
        assertEquals(
            HostStatusValue.HOST_INACTIVE,
            PhoneStatusProvider.toHostStatus(HostingController.State.NOT_HOSTING),
        )
        assertEquals(
            HostStatusValue.HOST_STARTING,
            PhoneStatusProvider.toHostStatus(HostingController.State.STARTING),
        )
        assertEquals(
            HostStatusValue.HOSTING,
            PhoneStatusProvider.toHostStatus(HostingController.State.HOSTING),
        )
        assertEquals(
            HostStatusValue.HOST_STOPPING,
            PhoneStatusProvider.toHostStatus(HostingController.State.STOPPING),
        )
    }
}
