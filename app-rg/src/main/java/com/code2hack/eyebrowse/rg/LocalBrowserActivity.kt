package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.doOnNextLayout
import com.code2hack.eyebrowse.core.browser.AddressPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** RG-local browser. The ordinary launcher stays unchanged until the #33 integration. */
class LocalBrowserActivity : Activity() {
    internal class Tab(val session: LocalBrowserSession) {
        var committedUrl = ""
        var committedTitle = ""
        var draft: String? = null
        var recoveryUrl = ""
    }
    internal enum class Utility { BROWSING, MORE, TABS, BOOKMARKS, QR_PENDING, SETTINGS }
    internal lateinit var root: FrameLayout
    internal lateinit var toolbar: FrameLayout
    internal lateinit var content: FrameLayout
    internal lateinit var address: EditText
    internal lateinit var keyboard: FrameLayout
    internal lateinit var pointer: PointerOverlay
    internal lateinit var input: NativeRgInputTarget
    internal lateinit var router: RgInputRouter
    internal lateinit var tabs: LocalTabs<Tab>
    internal lateinit var bookmarks: LocalBookmarks
    internal lateinit var inputSettings: LocalInputSettings
    internal val controls = linkedMapOf<String, TextView>()
    internal val keyButtons = linkedMapOf<RgKeyboardKeys.Key, TextView>()
    internal var utility = Utility.BROWSING
        private set
    private lateinit var pages: FrameLayout
    private lateinit var overlay: FrameLayout
    private lateinit var status: TextView
    private var addressEditing = false
    private var localError: String? = null
    private var resumed = false
    private var utilityScroll: ScrollView? = null
    private val edgeMotion = EdgeScrollMotion()
    internal var edgeScrollRunning = false
        private set
    private val edgeScroll = object : Runnable {
        override fun run() {
            val surface = scrollSurface()
            val now = SystemClock.elapsedRealtimeNanos()
            val direction = edgeMotion.direction(pointer.inputPosition(),pointer.motionBounds,pointer.expiresAtNs,now)
            if (surface == null || direction == 0) { stopEdgeScroll(); return }
            val pixels = edgeMotion.step(pointer.position,pointer.motionBounds,pointer.expiresAtNs,now,
                inputSettings.speed.pixelsPerSecond)
            if (surface.canScrollVertically(direction)) {
                if (utility==Utility.BROWSING) input.scroll(pixels) else surface.scrollBy(0,pixels)
            }
            else edgeMotion.stop() // Endpoints never bank unused travel.
            root.postDelayed(this,16)
        }
    }
    private fun scrollSurface(): View? {
        if (!resumed || !hasWindowFocus()) return null
        return when (utility) {
            Utility.BROWSING -> tabs.current.session.page?.takeIf {
                it.isShown && tabs.current.session.state.phase == LocalBrowserSession.Phase.READY
            }
            Utility.TABS, Utility.BOOKMARKS, Utility.SETTINGS -> utilityScroll?.takeIf { it.isShown }
            else -> null
        }
    }
    private fun updateEdgeScroll() {
        val now=SystemClock.elapsedRealtimeNanos()
        if (scrollSurface()==null || edgeMotion.direction(pointer.position,pointer.motionBounds,pointer.expiresAtNs,now)==0) {
            stopEdgeScroll();return
        }
        if (!edgeScrollRunning) {
            edgeScrollRunning=true
            edgeMotion.step(pointer.position,pointer.motionBounds,pointer.expiresAtNs,now,inputSettings.speed.pixelsPerSecond)
            root.postDelayed(edgeScroll,16)
        }
    }
    private fun stopEdgeScroll() {
        root.removeCallbacks(edgeScroll);edgeScrollRunning=false;edgeMotion.stop()
    }
    private var observeEditorUntil = 0L
    private val editorObservation = object : Runnable {
        override fun run() {
            val page = tabs.current.session.page
            if (!resumed || !hasWindowFocus() || utility != Utility.BROWSING || addressEditing ||
                page?.isShown != true) return
            if (page.hasFocus() && page.onCheckIsTextEditor()) {
                if (!keyboard.isShown) showKeyboard()
            } else if (SystemClock.uptimeMillis() < observeEditorUntil) root.postOnAnimation(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        root = FrameLayout(this).apply {
            id = R.id.rg_root
            setBackgroundColor(Color.BLACK); isFocusableInTouchMode = true
            defaultFocusHighlightEnabled = false
        }
        content = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(content, box(-1, -1, y = 48))
        pages = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        content.addView(pages, box(-1, -1))
        status = text("", 18f).apply { tag = "hud.status"; isClickable = true; isFocusable = false }
        content.addView(status, box(-1, 52, x = 16, y = 12).apply { rightMargin = 16 })
        overlay = FrameLayout(this).apply { visibility = View.GONE; isClickable = true }
        content.addView(overlay, box(-1, -1))
        toolbar = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(toolbar, box(-1, 48))
        toolbarControl("hud.close_tab", R.id.hud_close_tab, "×", "Close tab", 0, 48) { closeTab() }
        toolbarControl("hud.back", R.id.hud_back, "←", "Back", 48, 48) { navigate { it.back() } }
        toolbarControl("hud.forward", R.id.hud_forward, "→", "Forward", 96, 48) { navigate { it.forward() } }
        toolbarControl("hud.refresh", R.id.hud_refresh, "↻", "Refresh", 144, 48) { navigate { it.refresh() } }
        val slot = FrameLayout(this).apply { id = R.id.hud_address_slot; tag = "hud.address_slot" }
        toolbar.addView(slot, box(176, 48, 192))
        address = EditText(this).apply {
            id = R.id.hud_address; tag = "hud.address"; contentDescription = "Address"
            setSingleLine(true); setHorizontallyScrolling(true); showSoftInputOnFocus = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 20f); setTextColor(LIGHT); setHintTextColor(SECONDARY)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(4, 0, 4, 0); background = controlBorder(); defaultFocusHighlightEnabled = false
            hint = "Address"
            setOnFocusChangeListener { _, focused -> if (focused && ::tabs.isInitialized && !addressEditing) beginAddress() }
            doAfterTextChanged { if (addressEditing && ::tabs.isInitialized) tabs.current.draft = it.toString() }
            setOnKeyListener { _, code, event ->
                if (code != KeyEvent.KEYCODE_ENTER) false else {
                    if (event.action == KeyEvent.ACTION_UP) submitAddress()
                    true
                }
            }
        }
        slot.addView(address, box(128, 48))
        controls["hud.address"] = address
        val star = button("☆", "Bookmark page") { toggleBookmark() }.apply {
            id = R.id.hud_bookmark; tag = "hud.bookmark"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 24f)
        }
        slot.addView(star, box(48, 48, 128)); controls["hud.bookmark"] = star
        toolbarControl("hud.tab_counter", R.id.hud_tab_counter, "1/1", "Tabs", 368, 64) { showUtility(Utility.TABS) }
        toolbarControl("hud.more", R.id.hud_more, "⋮", "More", 432, 48) {
            if (utility == Utility.MORE) dismissUtility() else showUtility(Utility.MORE)
        }
        keyboard = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); visibility = View.GONE }
        root.addView(keyboard, box(-1, 200).apply { gravity = Gravity.BOTTOM })
        bookmarks = LocalBookmarks(File(filesDir, "local-browser/bookmarks.properties"))
        tabs = LocalTabs(::createTab) { tab -> pages.removeView(tab.session.surface); tab.session.destroy() }
        restoreMetadata(savedInstanceState?.getString("local.tabs") ?: getPreferences(MODE_PRIVATE).getString("local.tabs", null))
        val preferences=getSharedPreferences("local-input-settings",MODE_PRIVATE)
        inputSettings=LocalInputSettings({ key -> preferences.getString(key,null) }) { key,value ->
            preferences.edit().putString(key,value).commit()
        }
        pointer = PointerOverlay(this).apply { useLocalHudAppearance();sensitivity(inputSettings.sensitivity.gain) }
        root.addView(pointer, box(-1, -1))
        input = NativeRgInputTarget(root, pointer, {
            if (utility == Utility.BROWSING) tabs.current.session.page else null
        }, ::dismissKeyboard)
        router = RgInputRouter(this, pointer, root, input) {}
        // OEM forward/back bindings are retained; wearer-facing direction qualification is separate evidence.
        router.onSwipe = { direction -> if (utility == Utility.BROWSING) switchTab { tabs.swipe(direction) } }
        pointer.onPositionChanged = ::updateEdgeScroll
        pointer.onAvailabilityChanged = { available -> if (!available) router.cancel(); updateStatus() }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            pointer.bounds(0f, 0f, root.width.toFloat(), root.height.toFloat(), display?.rotation ?: 0)
        }
        renderKeys(); setContentView(root); showSelected(); root.requestFocus()
    }

    private fun createTab(): Tab {
        val tab = Tab(LocalBrowserSession(this, ::observeCurrentEditor))
        tab.session.surface.visibility = View.GONE
        pages.addView(tab.session.surface, box(-1, -1))
        tab.session.onStateChanged = { state ->
            if (state.phase == LocalBrowserSession.Phase.READY) {
                tab.committedUrl = state.url; tab.committedTitle = state.title; tab.recoveryUrl = ""
            }
            if (::tabs.isInitialized && tabs.current === tab) {
                if (state.phase != LocalBrowserSession.Phase.READY) stopEdgeScroll()
                if (state.phase == LocalBrowserSession.Phase.LOADING && !addressEditing) localError = null
                if (state.phase != LocalBrowserSession.Phase.READY && !addressEditing) dismissKeyboard()
                updateHud()
            }
        }
        return tab
    }

    private fun observeCurrentEditor() {
        // Renderer focus arrives asynchronously. Observe current public native focus briefly;
        // no tap/key, element, selection or previous tab is retained or replayed.
        root.removeCallbacks(editorObservation)
        observeEditorUntil = SystemClock.uptimeMillis() + 750
        root.postOnAnimation(editorObservation)
    }

    private fun toolbarControl(tag: String, id: Int, label: String, description: String,
                               x: Int, width: Int, action: () -> Unit) {
        val view = button(label, description, action).apply {
            this.id = id; this.tag = tag
            if (tag != "hud.tab_counter") setTextSize(TypedValue.COMPLEX_UNIT_PX, 24f)
        }
        toolbar.addView(view, box(width, 48, x)); controls[tag] = view
    }

    private fun border(selected: Boolean = false) = GradientDrawable().apply {
        setColor(Color.BLACK); setStroke(if (selected) 2 else 1, if (selected) LIGHT else OUTLINE)
    }
    private fun controlBorder() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), border(true))
        addState(intArrayOf(android.R.attr.state_focused), border(true))
        addState(intArrayOf(android.R.attr.state_selected), border(true))
        addState(intArrayOf(), border())
    }
    private fun text(label: String, pixels: Float = 20f) = TextView(this).apply {
        text = label; setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels); setTextColor(LIGHT)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setBackgroundColor(Color.BLACK); defaultFocusHighlightEnabled = false
    }
    private fun button(label: String, description: String = label, action: () -> Unit) = text(label).apply {
        gravity = Gravity.CENTER; contentDescription = description; isFocusable = false
        isFocusableInTouchMode = false; setPadding(0, 0, 0, 0)
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(INACTIVE, LIGHT)))
        background = controlBorder()
        setOnClickListener { action() }
    }
    private fun box(width: Int, height: Int, x: Int = 0, y: Int = 0) =
        FrameLayout.LayoutParams(width, height).apply { leftMargin = x; topMargin = y }

    internal fun beginAddress() {
        dismissUtility(); localError = null
        val full = tabs.current.draft ?: tabs.current.session.state.url.ifEmpty { tabs.current.recoveryUrl }
        addressEditing = true; address.ellipsize = null; address.setText(full)
        address.requestFocus(); address.setSelection(address.length()); showKeyboard(); updateStatus()
    }
    internal fun submitAddress() {
        val result = tabs.current.session.open(address.text.toString())
        if (!result.accepted()) { localError = result.message(); updateStatus(); return }
        tabs.current.draft = null; localError = null; dismissKeyboard(); updateHud()
    }
    internal fun showKeyboard() {
        if (keyboard.isShown) { renderKeys(); return }
        keyboard.visibility = View.VISIBLE
        (content.layoutParams as FrameLayout.LayoutParams).also { it.bottomMargin = 200; content.layoutParams = it }
        renderKeys()
        content.doOnNextLayout {
            val page = tabs.current.session.page ?: return@doOnNextLayout
            // The custom keyboard resizes the page without a system IME reveal request.
            page.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (resumed && hasWindowFocus() && utility == Utility.BROWSING &&
                        keyboard.isShown && !addressEditing && !edgeScrollRunning &&
                        page === tabs.current.session.page && page.isShown && page.hasFocus()) {
                        page.evaluateJavascript("""(()=>{
                            const e=document.activeElement;
                            if(e && (e.matches('input,textarea') || e.isContentEditable)) {
                                const r=e.getBoundingClientRect();
                                if(r.top<0 || r.bottom>innerHeight)
                                    e.scrollIntoView({block:'center',inline:'nearest'});
                            }
                        })()""".trimIndent(), null)
                    }
                }
            })
        }
    }
    internal fun dismissKeyboard() {
        if (!::keyboard.isInitialized) return
        root.removeCallbacks(editorObservation)
        if (keyboard.visibility != View.GONE) {
            keyboard.visibility = View.GONE
            (content.layoutParams as FrameLayout.LayoutParams).also { it.bottomMargin = 0; content.layoutParams = it }
        }
        if (addressEditing) { addressEditing = false; address.clearFocus(); root.requestFocus() }
        if (::tabs.isInitialized) updateHud()
    }

    private fun showSelected() {
        tabs.items.forEach { tab ->
            val active = tab === tabs.current
            tab.session.surface.visibility = if (active && utility in listOf(Utility.BROWSING, Utility.MORE)) View.VISIBLE else View.GONE
            if (active && resumed) tab.session.resume() else tab.session.pause()
        }
        updateHud()
    }
    private fun switchTab(change: () -> Boolean) {
        // Ordinary visibility/focus teardown; no captured event or editor identity survives a switch.
        val old = tabs.current
        if (!change()) return
        stopEdgeScroll(); dismissKeyboard(); old.session.surface.clearFocus(); localError = null
        dismissUtility(); root.requestFocus(); showSelected()
    }
    internal fun selectTab(index: Int) = switchTab { tabs.select(index) }
    internal fun addTab() {
        try {
            switchTab { tabs.add(); true }
            beginAddress()
        } catch (_: RuntimeException) { localError = "A new tab could not be created."; updateStatus() }
          catch (_: OutOfMemoryError) { localError = "A new tab could not be created."; updateStatus() }
    }
    internal fun closeTab() {
        try {
            stopEdgeScroll(); dismissKeyboard(); dismissUtility(); tabs.closeCurrent(); localError = null
            showSelected(); root.requestFocus()
        } catch (_: RuntimeException) { localError = "The tab could not be replaced."; updateStatus() }
          catch (_: OutOfMemoryError) { localError = "The tab could not be replaced."; updateStatus() }
    }
    private fun navigate(action: (LocalBrowserSession) -> Unit) {
        stopEdgeScroll(); dismissKeyboard(); dismissUtility(); tabs.current.draft = null; localError = null
        action(tabs.current.session); updateHud()
    }

    private fun updateHud() {
        if (!::tabs.isInitialized) return
        val tab = tabs.current; val state = tab.session.state
        if (!addressEditing) {
            val full = state.url.ifEmpty { tab.recoveryUrl }
            address.setText(full.removePrefix("https://").removePrefix("http://"))
            address.setSelection(0); address.ellipsize = TextUtils.TruncateAt.END
        }
        controls.getValue("hud.back").isEnabled = state.canGoBack
        controls.getValue("hud.forward").isEnabled = state.canGoForward
        controls.getValue("hud.refresh").isEnabled = state.phase != LocalBrowserSession.Phase.EMPTY && tab.session.page != null
        controls.getValue("hud.tab_counter").text = "${tabs.selectedIndex + 1}/${tabs.count}"
        controls.getValue("hud.bookmark").apply {
            isEnabled = state.phase == LocalBrowserSession.Phase.READY && AddressPolicy.resolve(tab.committedUrl).accepted()
            val savedPage = isEnabled && bookmarks.contains(tab.committedUrl)
            text = if (savedPage) "★" else "☆"
            contentDescription = if (savedPage) "Remove bookmark" else "Bookmark page"
        }
        updateStatus()
    }
    private fun updateStatus() {
        if (!::tabs.isInitialized) return
        val tab = tabs.current; val state = tab.session.state
        val message = localError ?: state.error ?: when (state.phase) {
            LocalBrowserSession.Phase.EMPTY -> if (tab.recoveryUrl.isEmpty()) "Enter an address to browse." else "Previous page interrupted. Open the address to recover."
            LocalBrowserSession.Phase.LOADING -> "Loading…"
            LocalBrowserSession.Phase.INTERRUPTED -> "Page interrupted. Open the address to recover."
            else -> if (::pointer.isInitialized && !pointer.position.available) "Tracking unavailable" else null
        }
        status.text = message.orEmpty(); status.visibility = if (message == null) View.GONE else View.VISIBLE
    }
    internal fun toggleBookmark() {
        val tab = tabs.current
        if (!controls.getValue("hud.bookmark").isEnabled) return
        val success = if (bookmarks.contains(tab.committedUrl)) bookmarks.remove(tab.committedUrl)
            else bookmarks.save(tab.committedUrl, tab.committedTitle)
        localError = if (success) null else bookmarks.error
        updateHud() // Never blur the address/native editor or navigate its draft.
        if (utility == Utility.BOOKMARKS) renderUtility()
    }

    internal fun showUtility(next: Utility) {
        stopEdgeScroll(); dismissKeyboard(); tabs.current.session.surface.clearFocus(); root.requestFocus()
        utility = next; localError = null; overlay.visibility = View.VISIBLE
        showSelected(); renderUtility()
    }
    internal fun dismissUtility() {
        if (!::overlay.isInitialized) return
        stopEdgeScroll(); utilityScroll=null
        utility = Utility.BROWSING; overlay.visibility = View.GONE; overlay.removeAllViews()
        if (::tabs.isInitialized) showSelected()
    }
    private fun renderUtility() {
        stopEdgeScroll(); utilityScroll=null
        overlay.removeAllViews(); overlay.setBackgroundColor(if (utility == Utility.MORE) Color.TRANSPARENT else Color.BLACK)
        overlay.setOnClickListener { if (utility == Utility.MORE) dismissUtility() }
        if (utility == Utility.MORE) {
            val menu = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
            overlay.addView(menu, box(224, 192, 256))
            listOf("Add new tab", "Bookmarks", "QR scan", "Settings").forEachIndexed { i, label ->
                menu.addView(button(label) {
                    when (i) {
                        0 -> addTab()
                        1 -> showUtility(Utility.BOOKMARKS)
                        2 -> showUtility(Utility.QR_PENDING)
                        else -> showUtility(Utility.SETTINGS)
                    }
                }.apply { tag = "menu.$i" }, box(224, 48, y = i * 48))
            }
            return
        }
        val title = when (utility) {
            Utility.TABS -> "Tabs"
            Utility.BOOKMARKS -> "Bookmarks"
            Utility.QR_PENDING -> "QR scan"
            else -> "Settings"
        }
        overlay.addView(text(title, 24f), box(340, 44, 16, 14))
        overlay.addView(button("Done") { dismissUtility() }.apply { tag = "utility.done" }, box(96, 44, 368, 14))
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK); isFillViewport = true }
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        scroll.addView(rows); overlay.addView(scroll, box(-1, -1, y = 76));utilityScroll=scroll
        fun row(view: View, height: Int) { rows.addView(view, LinearLayout.LayoutParams(-1, height).apply { setMargins(16, 0, 16, 8) }) }
        when (utility) {
            Utility.TABS -> tabs.items.forEachIndexed { index, tab ->
                row(button("${index + 1}. ${tab.committedTitle.ifEmpty { if (tab.recoveryUrl.isEmpty()) "New tab" else "Interrupted page" }}") {
                    selectTab(index); dismissUtility()
                }.apply { tag = "tab.$index"; isSelected = index == tabs.selectedIndex; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, 64)
            }
            Utility.BOOKMARKS -> {
                bookmarks.error?.let {
                    row(text(it, 18f), 52)
                    row(button("Retry") { bookmarks.reload(); updateHud(); renderUtility() }.apply { tag = "bookmarks.retry" }, 44)
                }
                if (bookmarks.entries.isEmpty() && bookmarks.error == null) row(text("No bookmarks yet."), 48)
                bookmarks.entries.forEachIndexed { index, bookmark ->
                    val group = LinearLayout(this)
                    group.addView(button(bookmark.title + "\n" + bookmark.url) {
                        val result = tabs.current.session.open(bookmark.url)
                        if (result.accepted()) { tabs.current.draft = null; localError = null; dismissUtility() }
                        else { localError = result.message(); updateStatus() }
                    }.apply { tag = "bookmark.open.$index"; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -1, 1f))
                    group.addView(button("Remove") {
                        if (!bookmarks.remove(bookmark.url)) localError = bookmarks.error
                        updateHud(); renderUtility()
                    }.apply { tag = "bookmark.remove.$index" }, LinearLayout.LayoutParams(96, -1).apply { leftMargin = 8 })
                    row(group, 80)
                }
            }
            Utility.SETTINGS -> {
                inputSettings.error?.let { row(text(it,18f),52) }
                row(text("Pointer sensitivity"),40)
                LocalInputSettings.Sensitivity.entries.forEach { value ->
                    row(button("${value.label} (${value.gain}×)") {
                        if (inputSettings.select(value)) pointer.sensitivity(value.gain)
                        renderUtility()
                    }.apply { tag="settings.sensitivity.${value.name.lowercase()}";isSelected=inputSettings.sensitivity==value },48)
                }
                row(text("Edge-scroll speed"),40)
                LocalInputSettings.Speed.entries.forEach { value ->
                    row(button("${value.label} (${value.pixelsPerSecond} px/s)") {
                        inputSettings.select(value);renderUtility()
                    }.apply { tag="settings.speed.${value.name.lowercase()}";isSelected=inputSettings.speed==value },48)
                }
            }
            else -> row(text("Not implemented here. QR scanning follows in issue #33.",18f),96)
        }
    }

    private fun renderKeys() {
        if (!::input.isInitialized) return
        keyboard.removeAllViews(); keyButtons.clear()
        input.keys.localLayout().forEach { placed ->
            val key = placed.key
            val label = when (key) {
                is RgKeyboardKeys.Key.Character -> key.text
                RgKeyboardKeys.Key.Command.SHIFT -> "⇧"
                RgKeyboardKeys.Key.Command.BACKSPACE -> "⌫"
                RgKeyboardKeys.Key.Command.SYMBOLS -> if (input.keys.symbols) "ABC" else "123"
                RgKeyboardKeys.Key.Command.SPACE -> "Space"
                RgKeyboardKeys.Key.Command.ENTER -> if (addressEditing) "Open" else "Enter"
                RgKeyboardKeys.Key.Command.DONE -> "Done"
                else -> key.toString()
            }
            val view = button(label) {
                input.key(key)
                if (key == RgKeyboardKeys.Key.Command.SHIFT || key == RgKeyboardKeys.Key.Command.SYMBOLS) renderKeys()
            }
            keyboard.addView(view, box(placed.width, placed.height, placed.x, placed.y)); keyButtons[key] = view
        }
    }

    private fun metadata() = JSONObject().put("selected", tabs.selectedIndex)
        .put("locations", JSONArray(tabs.items.map { it.committedUrl.ifEmpty { it.recoveryUrl } })).toString()
    private fun restoreMetadata(value: String?) {
        if (value == null) return
        try {
            val saved = JSONObject(value); val urls = saved.getJSONArray("locations")
            for (i in 0 until urls.length()) {
                val url = urls.getString(i)
                if (url.isNotEmpty() && !AddressPolicy.resolve(url).accepted()) throw IllegalArgumentException()
                if (i > 0) tabs.add()
                tabs.current.recoveryUrl = url // No GET/POST/form replay on cold/recreated pages.
            }
            tabs.select(saved.getInt("selected").coerceIn(0, tabs.count - 1))
        } catch (_: Exception) { localError = "Some previous tab locations could not be recovered." }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("local.tabs", metadata()); super.onSaveInstanceState(outState) }
    override fun onStop() {
        if (!getPreferences(MODE_PRIVATE).edit().putString("local.tabs", metadata()).commit()) {
            localError = "Tab recovery could not be saved."; updateStatus()
        }
        super.onStop()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::router.isInitialized && router.key(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) when {
                keyboard.isShown -> dismissKeyboard()
                utility != Utility.BROWSING -> dismissUtility()
                tabs.current.session.state.canGoBack -> tabs.current.session.back()
                else -> finish()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::router.isInitialized) {
            router.focus(hasFocus)
            if (resumed) {
                if (hasFocus) pointer.start() else { stopEdgeScroll(); pointer.stop(); root.removeCallbacks(editorObservation) }
            }
        }
    }
    override fun onResume() {
        super.onResume(); resumed = true
        if (::tabs.isInitialized) showSelected()
        if (hasWindowFocus()) pointer.start()
        router.resume()
    }
    override fun onPause() { resumed = false; stopEdgeScroll(); router.pause(); pointer.stop(); dismissKeyboard(); tabs.items.forEach { it.session.pause() }; super.onPause() }
    override fun onDestroy() { stopEdgeScroll(); router.pause(); pointer.stop(); tabs.destroy(); super.onDestroy() }

    companion object {
        private const val LIGHT = 0xfff2f2f2.toInt()
        private const val SECONDARY = 0xffb3b3b3.toInt()
        private const val INACTIVE = 0xff8a8a8a.toInt()
        private const val OUTLINE = 0xff606060.toInt()
    }
}
