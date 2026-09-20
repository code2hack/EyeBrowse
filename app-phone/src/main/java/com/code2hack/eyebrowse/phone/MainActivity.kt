package com.code2hack.eyebrowse.phone

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import com.code2hack.eyebrowse.core.browser.AddressPolicy
import com.code2hack.eyebrowse.core.browser.StartupPolicy

/**
 * The Phone browser surface: address/Open, Back/Forward/Reload, compact status, explicit hosting
 * Start/Stop and the one session-owned WebView.
 *
 * <p>The Activity is replaceable; the {@link PhoneBrowserSession} is not. Recreation reattaches the
 * same live document instead of creating a second page. While hosting is active, backgrounding the
 * Activity moves the live WebView to the private presentation and returning reattaches it; the
 * hosting service outlives the Activity. Back dismisses the IME first, then walks WebView history,
 * then leaves the Activity.
 *
 * <p>Migration note: FQCN and manifest instantiation unchanged. View/controller fields use
 * `lateinit` for the create-then-use lifecycle (no legal null path exists between onCreate
 * assignment and every use — disclosed in the migration ledger); all other semantics, including
 * listener registration order and identity hashes in diagnostics, are unchanged.
 */
class MainActivity : ComponentActivity() {

    private lateinit var session: PhoneBrowserSession
    private var attachment: PhoneBrowserSession.Attachment? = null
    private lateinit var addressBar: AddressBarModel
    private lateinit var hosting: HostingController

    private lateinit var addressInput: EditText
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var reloadButton: ImageButton
    private lateinit var openButton: Button
    private lateinit var hostingButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var hostingStatusText: TextView
    private lateinit var pairRgButton: Button
    private lateinit var webContainer: ViewGroup

    private var updatingField = false

    private val sessionListener = object : PhoneBrowserSession.Listener {
        override fun onSessionChanged(session: PhoneBrowserSession) {
            ensureAttached()
            render()
        }
    }

    private val hostingListener = HostingController.Listener {
        ensureAttached()
        render()
    }

    private val addressWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable?) {
            if (!updatingField) {
                addressBar.onUserEdit(s.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("EyeBrowseHost", "activity onCreate " + identityHash())
        setContentView(R.layout.activity_main)

        session = PhoneBrowserSession.get(applicationContext)
        addressBar = AddressBarModel()

        addressInput = findViewById(R.id.address_input)
        backButton = findViewById(R.id.button_back)
        forwardButton = findViewById(R.id.button_forward)
        reloadButton = findViewById(R.id.button_reload)
        openButton = findViewById(R.id.button_open)
        hostingButton = findViewById(R.id.button_hosting_toggle)
        pairRgButton = findViewById(R.id.button_pair_rg)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)
        hostingStatusText = findViewById(R.id.hosting_status)
        webContainer = findViewById(R.id.web_container)
        hosting = HostingController.get(applicationContext)

        if (savedInstanceState != null) {
            val draft = savedInstanceState.getString(STATE_DRAFT, "")
            addressBar.onUserEdit(draft)
            if (!savedInstanceState.getBoolean(STATE_EDITING, false)) {
                addressBar.onSubmittedAccepted(draft)
            }
            setFieldText(addressBar.draft())
        }

        addressInput.addTextChangedListener(addressWatcher)
        addressInput.setOnEditorActionListener { _, _, _ ->
            submitAddress()
            true
        }
        openButton.setOnClickListener { submitAddress() }
        backButton.setOnClickListener { session.goBack() }
        forwardButton.setOnClickListener { session.goForward() }
        reloadButton.setOnClickListener { session.reload() }
        hostingButton.setOnClickListener { toggleHosting() }
        pairRgButton.setOnClickListener {
            startActivity(android.content.Intent(this, com.code2hack.eyebrowse.phone.pairing.PairingActivity::class.java))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isImeVisible()) {
                    hideIme()
                    return
                }
                if (session.canGoBack()) {
                    session.goBack()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        session.addListener(sessionListener)
        hosting.addListener(hostingListener)
        attachment = session.attach(this, webContainer)
        if (session.startupDecision() != StartupPolicy.Decision.REATTACH_LIVE_SESSION) {
            addressBar.syncTo(session.lastCommittedUrl())
            setFieldText(addressBar.draft())
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DRAFT, addressBar.draft())
        outState.putBoolean(STATE_EDITING, addressBar.editing())
    }

    override fun onStart() {
        super.onStart()
        Log.i("EyeBrowseHost", "activity onStart " + identityHash())
        // UI availability is tracked through STARTING so delayed service readiness reconciles;
        // the controller decides between reattach-from-presentation, parentless reattach, and
        // no-op, and returns the ownership token this Activity must keep.
        attachment = hosting.onPhoneUiAvailable(this, webContainer, attachment)
    }

    override fun onStop() {
        super.onStop()
        Log.i("EyeBrowseHost", "activity onStop " + identityHash() + " finishing=$isFinishing")
        session.flushCookies()
        // Hidden Phone UI: during HOSTING the live view moves offscreen with reconciled geometry;
        // during STARTING the transition is deferred and service readiness reconciles. A finishing
        // Activity keeps its token for the destroy path (system transition ordering can run
        // another Activity's onStart before this onStop).
        attachment = hosting.onPhoneUiHidden(attachment)
    }

    override fun onDestroy() {
        Log.i("EyeBrowseHost", "activity onDestroy " + identityHash())
        hosting.removeListener(hostingListener)
        session.removeListener(sessionListener)
        // A destroyed Activity must not steal the view from a successor; only when this Activity
        // still owns the session does the host move the view offscreen and keep hosting alive.
        hosting.moveWebViewToPrivateDisplay(attachment)
        // R5: actual destruction releases the controller-held Activity/container references for
        // the matching UI owner and clears availability (identity-checked by token).
        hosting.onPhoneUiDestroyed(attachment)
        session.detach(attachment)
        super.onDestroy()
    }

    private fun identityHash(): String {
        return "act=" + System.identityHashCode(this)
    }

    private fun toggleHosting() {
        val hostingStatus = hosting.status()
        Log.i("EyeBrowseHost", "toggleHosting state=" + hostingStatus.state)
        if (hostingStatus.state == HostingController.State.HOSTING ||
                hostingStatus.state == HostingController.State.STARTING ||
                hostingStatus.state == HostingController.State.STOPPING) {
            hosting.stop()
        } else {
            requestNotificationPermissionIfNeeded()
            hosting.start()
        }
        render()
    }

    /**
     * One ordinary runtime-permission request when starting hosting. Hosting proceeds regardless of
     * the outcome; if notifications are denied the foreground-service notification is suppressed by
     * the platform and the in-app Stop control remains available.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission
                        .POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    /** Reattaches a live parentless view (e.g. after Stop released the hosting presentation). */
    private fun ensureAttached() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return // Never steal the hosted view while this Activity is backgrounded.
        }
        val parentlessView = session.view()
        if (parentlessView != null && parentlessView.parent == null) {
            // Renderer recovery creates a new token too; register it without making a hidden
            // or stale Activity visible merely because a session callback arrived.
            attachment = hosting.ensurePhoneUiAttachment(this, webContainer, attachment)
        }
    }

    private fun submitAddress() {
        val result = session.openAddress(addressInput.text.toString())
        if (result.accepted()) {
            addressBar.onSubmittedAccepted(result.url())
            setFieldText(addressBar.draft())
        } else {
            addressBar.onSubmittedRejected()
        }
        hideIme()
        render()
    }

    private fun setFieldText(text: String?) {
        updatingField = true
        addressInput.setText(text)
        addressInput.setSelection(addressInput.text.length)
        updatingField = false
    }

    private fun render() {
        val committed = session.displayUrl()
        addressBar.syncTo(committed)
        if (!addressBar.editing() && addressInput.text.toString() != addressBar.draft()) {
            setFieldText(addressBar.draft())
        }

        val message: String?
        var isError = false
        val decision = session.startupDecision()
        if (session.errorMessage() != null) {
            message = session.errorMessage()
            isError = true
        } else if (session.noticeMessage() != null) {
            message = session.noticeMessage()
        } else if (session.isLoading()) {
            message = getString(R.string.status_loading, committed ?: "")
        } else if (decision == StartupPolicy.Decision.OFFER_SAVED_URL) {
            // Not live: never present the saved address as an existing page.
            message = getString(R.string.status_recovery)
        } else if (committed != null) {
            val title = session.pageTitle()
            message = if (title == null || title.isEmpty()) committed else title
        } else {
            message = getString(R.string.status_empty)
        }

        statusText.text = message
        statusText.setTextColor(getColor(
                if (isError) R.color.eyebrowse_error else R.color.eyebrowse_text_secondary))
        statusText.visibility =
                if (message == null || message.isEmpty()) View.GONE else View.VISIBLE

        progressBar.visibility = if (session.isLoading()) View.VISIBLE else View.INVISIBLE
        progressBar.progress = session.progress()

        backButton.isEnabled = session.canGoBack()
        forwardButton.isEnabled = session.canGoForward()
        reloadButton.isEnabled = session.isLive()

        renderHosting()
    }

    private fun renderHosting() {
        val hostingStatus = hosting.status()
        val text: String
        when (hostingStatus.state) {
            HostingController.State.HOSTING -> {
                hostingButton.setText(R.string.action_hosting_stop)
                text = if (hostingStatus.failureReason != null) {
                    // R6: a rebuild/attachment failure inside HOSTING must not keep a healthy
                    // label; the recoverable failure is surfaced while hosting remains Stop-able.
                    getString(R.string.hosting_status_failed, hostingStatus.failureReason)
                } else {
                    getString(R.string.hosting_status_active, hostingStatus.generation,
                            getString(if (hostingStatus.captureActive) R.string.hosting_capture_active
                                    else R.string.hosting_capture_idle))
                }
            }
            HostingController.State.STARTING,
            HostingController.State.STOPPING,
            -> {
                text = getString(R.string.hosting_status_starting)
                hostingButton.setText(R.string.action_hosting_stop)
            }
            else -> {
                text = if (hostingStatus.failureReason == null) {
                    getString(R.string.hosting_status_idle)
                } else {
                    getString(R.string.hosting_status_failed, hostingStatus.failureReason)
                }
                hostingButton.setText(R.string.action_hosting_start)
            }
        }
        hostingStatusText.text = text
    }

    private fun isImeVisible(): Boolean {
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        return insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())
    }

    private fun hideIme() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.ime())
        val focused = currentFocus
        if (focused != null) {
            focused.clearFocus()
        }
    }

    companion object {
        private const val STATE_DRAFT = "address_draft"
        private const val STATE_EDITING = "address_editing"
    }
}
