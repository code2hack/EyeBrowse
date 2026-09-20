package com.code2hack.eyebrowse.phone

/**
 * Pure transfer plan for moving the single live Phone WebView between owner containers.
 *
 * Cross-parent moves must detach while the MutableContextWrapper still points at the OLD
 * owner/display. Only then may the destination context be installed. Same-parent claims must not
 * cause a gratuitous detach/re-add cycle.
 */
internal object BrowserAttachmentTransfer {

    internal enum class Step {
        DETACH_OLD_PARENT,
        UPDATE_CONTEXT,
        CLEAR_TARGET,
        ATTACH_TARGET,
    }

    internal fun plan(hasView: Boolean, alreadyInTarget: Boolean): List<Step> {
        if (!hasView) {
            return listOf(Step.UPDATE_CONTEXT, Step.CLEAR_TARGET)
        }
        if (alreadyInTarget) {
            return listOf(Step.UPDATE_CONTEXT)
        }
        return listOf(
            Step.DETACH_OLD_PARENT,
            Step.UPDATE_CONTEXT,
            Step.CLEAR_TARGET,
            Step.ATTACH_TARGET,
        )
    }
}
