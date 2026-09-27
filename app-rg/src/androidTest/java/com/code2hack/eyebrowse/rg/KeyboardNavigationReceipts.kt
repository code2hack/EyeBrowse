package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import android.view.View
import com.code2hack.eyebrowse.core.link.messages.ViewportUpdateResultMessage
import com.code2hack.eyebrowse.core.link.messages.ViewportUpdateMessage
import com.code2hack.eyebrowse.core.link.messages.BrowserControlMessage
import com.code2hack.eyebrowse.core.link.messages.BrowserActionResultMessage
import com.code2hack.eyebrowse.core.link.messages.BrowserStateMessage
import com.code2hack.eyebrowse.rg.link.RgLinkClient

/** Observation only: forwards each real callback once; never changes admission or the await. */
internal class KeyboardNavigationReceipts(private val activity: MainActivity) : AutoCloseable {
    private val peer = activity.presentation
    private val monitor = checkNotNull(field("lock").get(peer))
    private val client = field("client").get(peer) as RgLinkClient
    private val listenerField = RgLinkClient::class.java.getDeclaredField("listener").apply { isAccessible = true }
    private val original = listenerField.get(client) as RgLinkClient.Listener
    private val records = mutableListOf<String>()
    private var lost = 0
    private var originalLayoutDeadline: Long? = null
    private val requestDeadlines = linkedMapOf<Long, Pair<String, Long>>()
    fun preservedDeadlineFor(document: String): Boolean = synchronized(monitor) {
        val deadline = originalLayoutDeadline
        deadline != null && requestDeadlines.values.any { it.first == document } &&
            requestDeadlines.values.all { it.second == deadline }
    }
    private var lastPredicate: String? = null
    @Volatile private var observing = true
    private val started = SystemClock.uptimeMillis()
    private val image = activity.findViewById<View>(R.id.rg_page)
    private val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        capture("image-layout old=${oldRight-oldLeft}x${oldBottom-oldTop} new=${right-left}x${bottom-top}")
    }
    private val views = listOf(activity.findViewById<TextView>(R.id.rg_status), activity.findViewById<TextView>(R.id.rg_detail))
    private val textObserver = PreDrawTextObserver(activity.findViewById(R.id.rg_root),views) { index ->
        capture(if (index == 0) "ui-status-change-at-predraw" else "ui-location-change-at-predraw")
    }
    private val observingListener = object : RgLinkClient.Listener by original {
        override fun onControl(message: BrowserControlMessage) {
            val entered = SystemClock.uptimeMillis()
            val previousTitle = peer.browserState()?.title
            original.onControl(message)
            val detail = when (message) {
                is BrowserActionResultMessage -> "action-result accepted=${message.accepted} reason=${message.reason}"
                is BrowserStateMessage -> "browser-state titleChanged=${message.title!=previousTitle} kbd=${message.title?.startsWith("KBD|")==true} loading=${message.loading}"
                is ViewportUpdateResultMessage -> "viewport-result id=${message.transitionId} accepted=${message.accepted}" +
                    " profile=${message.profile} document=${message.context.documentId} viewport=${message.context.viewportEpoch}"
                else -> message.javaClass.simpleName
            }
            capture("wire:$detail entered=$entered")
        }
    }
    init {
        listenerField.set(client, observingListener)
        image.addOnLayoutChangeListener(layoutListener)
        capture("armed-before-enter")
    }
    private fun field(name: String) = RgPresentationController::class.java.getDeclaredField(name).apply { isAccessible = true }
    fun capture(event: String, onlyChange: Boolean = false) {
        if (!observing) return
        val queuedAt = SystemClock.uptimeMillis()
        val snapshot = synchronized(monitor) {
            val state = peer.browserState()
            val address = field("pendingAddress").get(peer) as? Pair<*, *>
            val change = field("viewportChange").get(peer)
            val request = change?.javaClass?.getDeclaredField("request")?.apply { isAccessible=true }?.get(change) as? ViewportUpdateMessage
            if (!peer.keyboard.visible && field("layoutExpected").getBoolean(peer) && originalLayoutDeadline == null)
                originalLayoutDeadline = field("layoutStartedAt").getLong(peer) + 2_000
            val deadline = change?.javaClass?.getDeclaredField("deadline")?.apply { isAccessible=true }?.getLong(change)
            if (request != null && deadline != null && requestDeadlines.size < 256)
                requestDeadlines[request.transitionId] = request.context.documentId to deadline
            val measured = peer.profile()
            val frame = peer.lastFrameHeader
            val status = field("statusText").get(peer) as? String
            val statusClass = when {
                status?.startsWith("Viewport changed") == true -> "UNPLANNED_LAYOUT"
                status?.startsWith("Viewport rejected") == true -> "VIEWPORT_REJECTED"
                status?.startsWith("Viewport change timed out") == true -> "VIEWPORT_TIMEOUT"
                status?.startsWith("Viewport request not sent") == true -> "VIEWPORT_QUEUE_FAILURE"
                status?.startsWith("Disconnected") == true -> "LINK_LOST"
                status?.startsWith("Presentation stale:") == true -> "PHONE_STALE"
                status == "Updating page layout" -> "UPDATING_LAYOUT"
                status == "Live page · authenticated" -> "LIVE_FRAME"
                else -> "OTHER"
            }
            "hidden=${!peer.keyboard.visible} canAct=${peer.canAct()} kbd=${state?.title?.startsWith("KBD|")==true}" +
                " loading=${state?.loading} stale=${state?.stale} generation=${peer.keyboard.generation}" +
                " pendingAddressGeneration=${address?.second} pendingCommand=${field("pendingCommand").get(peer)!=null}" +
                " layoutExpected=${field("layoutExpected").getBoolean(peer)} viewportPending=${field("viewportChange").get(peer)!=null}" +
                " profileMatch=${state?.profile==peer.profile()} frameMatch=${peer.lastFrameHeader?.context==state?.context}" +
                " document=${state?.context?.documentId} viewport=${state?.context?.viewportEpoch}" +
                " status=$statusClass measured=${measured?.width}x${measured?.height}" +
                " stateProfile=${state?.profile?.width}x${state?.profile?.height} frame=${frame?.width}x${frame?.height}" +
                " requestId=${request?.transitionId} requestProfile=${request?.profile?.width}x${request?.profile?.height}" +
                " requestDocument=${request?.context?.documentId} requestViewport=${request?.context?.viewportEpoch}" +
                " originalLayoutDeadline=$originalLayoutDeadline requestDeadline=$deadline"
        }
        val at = SystemClock.uptimeMillis()
        synchronized(records) {
            if (onlyChange && snapshot == lastPredicate) return
            lastPredicate = snapshot
            if (records.size < 256) records.add("queuedAt=$queuedAt at=$at sinceArmMs=${at-started} event=$event $snapshot") else lost++
        }
    }
    override fun close() {
        capture("observation-end")
        observing = false
        // Called on Main after the await; do not retain Activity or install a successor observer.
        if (listenerField.get(client) === observingListener) listenerField.set(client, original)
        textObserver.close()
        image.removeOnLayoutChangeListener(layoutListener)
        synchronized(records) {
            records.forEach { Log.i("EyeBrowseKeyboardTest", "KBD_NAV $it") }
            Log.i("EyeBrowseKeyboardTest", "KBD_NAV_END records=${records.size} lost=$lost")
        }
    }
}
