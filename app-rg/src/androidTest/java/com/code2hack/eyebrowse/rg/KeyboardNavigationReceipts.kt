package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.TextView
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
    private var lastPredicate: String? = null
    @Volatile private var observing = true
    private val started = SystemClock.uptimeMillis()
    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { capture("ui-text-change") }
        override fun afterTextChanged(s: Editable?) = Unit
    }
    private val views = listOf(activity.findViewById<TextView>(R.id.rg_status), activity.findViewById<TextView>(R.id.rg_detail))
    private val observingListener = object : RgLinkClient.Listener by original {
        override fun onControl(message: BrowserControlMessage) {
            val entered = SystemClock.uptimeMillis()
            val previousTitle = peer.browserState()?.title
            original.onControl(message)
            val detail = when (message) {
                is BrowserActionResultMessage -> "action-result accepted=${message.accepted} reason=${message.reason}"
                is BrowserStateMessage -> "browser-state titleChanged=${message.title!=previousTitle} kbd=${message.title?.startsWith("KBD|")==true} loading=${message.loading}"
                else -> message.javaClass.simpleName
            }
            capture("wire:$detail entered=$entered")
        }
    }
    init {
        listenerField.set(client, observingListener)
        views.forEach { it.addTextChangedListener(watcher) }
        capture("armed-before-enter")
    }
    private fun field(name: String) = RgPresentationController::class.java.getDeclaredField(name).apply { isAccessible = true }
    fun capture(event: String, onlyChange: Boolean = false) {
        if (!observing) return
        val queuedAt = SystemClock.uptimeMillis()
        val snapshot = synchronized(monitor) {
            val state = peer.browserState()
            val address = field("pendingAddress").get(peer) as? Pair<*, *>
            "hidden=${!peer.keyboard.visible} canAct=${peer.canAct()} kbd=${state?.title?.startsWith("KBD|")==true}" +
                " loading=${state?.loading} stale=${state?.stale} generation=${peer.keyboard.generation}" +
                " pendingAddressGeneration=${address?.second} pendingCommand=${field("pendingCommand").get(peer)!=null}" +
                " layoutExpected=${field("layoutExpected").getBoolean(peer)} viewportPending=${field("viewportChange").get(peer)!=null}" +
                " profileMatch=${state?.profile==peer.profile()} frameMatch=${peer.lastFrameHeader?.context==state?.context}" +
                " document=${state?.context?.documentId} viewport=${state?.context?.viewportEpoch}"
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
        views.forEach { it.removeTextChangedListener(watcher) }
        synchronized(records) {
            records.forEach { Log.i("EyeBrowseKeyboardTest", "KBD_NAV $it") }
            Log.i("EyeBrowseKeyboardTest", "KBD_NAV_END records=${records.size} lost=$lost")
        }
    }
}
