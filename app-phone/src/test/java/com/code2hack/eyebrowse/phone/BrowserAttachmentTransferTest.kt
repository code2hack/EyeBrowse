package com.code2hack.eyebrowse.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserAttachmentTransferTest {

    @Test
    fun `cross-parent transfer detaches before destination context switch`() {
        assertEquals(
            listOf(
                BrowserAttachmentTransfer.Step.DETACH_OLD_PARENT,
                BrowserAttachmentTransfer.Step.UPDATE_CONTEXT,
                BrowserAttachmentTransfer.Step.CLEAR_TARGET,
                BrowserAttachmentTransfer.Step.ATTACH_TARGET,
            ),
            BrowserAttachmentTransfer.plan(hasView = true, alreadyInTarget = false),
        )
    }

    @Test
    fun `same-parent ownership claim does not detach and readd live WebView`() {
        assertEquals(
            listOf(BrowserAttachmentTransfer.Step.UPDATE_CONTEXT),
            BrowserAttachmentTransfer.plan(hasView = true, alreadyInTarget = true),
        )
    }

    @Test
    fun `empty session updates destination context before clearing target`() {
        assertEquals(
            listOf(
                BrowserAttachmentTransfer.Step.UPDATE_CONTEXT,
                BrowserAttachmentTransfer.Step.CLEAR_TARGET,
            ),
            BrowserAttachmentTransfer.plan(hasView = false, alreadyInTarget = false),
        )
    }
}
