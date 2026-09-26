package com.code2hack.eyebrowse.phone

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*

/**
 * Main-thread editor lifecycle. No controller/link lock is held across asset IO, renderer work,
 * or a wait. The one pending effect never becomes a retry queue. A missing callback requires a
 * renderer revoke barrier before another grant, handoff, profile transition or completed Stop.
 */
class PhoneEditorController(
    app: Context,
    private val browser: PhoneBrowserSession,
    private val state: () -> ControlSnapshot,
    private val connection: () -> Long,
    private val publish: (EditorStateMessage) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    val authority = PhoneEditorAuthority()
    private var adapter: RendererEditorAdapter? = null
    private val closing = ArrayList<(Boolean) -> Unit>()
    private val observe = Runnable { observeCurrent() }
    private var awaitingProfile: ControlContext? = null
    private val hosting = HostingController.get(app)
    private var profilePreparation: Runnable? = null
    private var cancelProfilePreparation: (() -> Unit)? = null

    init {
        val assets = app.applicationContext.assets
        Thread({
            val program = runCatching { assets.open("eyebrowse-editor.js").bufferedReader().use { it.readText() } }.getOrNull()
            if (program != null) main.post { adapter = RendererEditorAdapter(browser::view, browser::documentIdentity, program) }
        }, "eyebrowse-editor-asset").start()
    }

    fun isQuiescent(): Boolean = authority.phase == PhoneEditorAuthority.Phase.EMPTY
    fun isAvailable(): Boolean = adapter != null

    fun stateMessage(): EditorStateMessage {
        val snapshot = state()
        val current = authority.grant?.takeIf {
            it.opening.context == snapshot.context && it.opening.connection == connection() &&
                snapshot.owner == ControlOwner.RG && snapshot.linkAuthenticated && snapshot.hostingActive
        }
        return EditorStateMessage(snapshot.context, current?.target, current?.kind, current?.enter,
            current != null && authority.phase == PhoneEditorAuthority.Phase.READY &&
                hosting.localEditorFocusReady() && snapshot.presentationStatus == PresentationStatus.READY)
    }

    private fun changed() {
        main.removeCallbacks(observe)
        publish(stateMessage())
        if (stateMessage().ready) main.postDelayed(observe, 100)
    }

    /** Only an explicit already-admitted activation may request a new editor grant. */
    fun openAfterActivation(activation: BrowserAction.ActivateAt, callback: (Boolean) -> Unit = {}) =
        open(activation, null, callback)

    fun resumeAfterProfile(previousTarget: EditorTarget, callback: (Boolean) -> Unit = {}) =
        preparePresentation(previousTarget) { geometry, editor -> callback(geometry && editor) }

    /** One bounded geometry preparation, not a queue of keys or automatic field selection. */
    fun preparePresentation(previousTarget: EditorTarget? = null,
                            deadlineElapsedMs: Long = android.os.SystemClock.elapsedRealtime() + 1000,
                            callback: (Boolean, Boolean) -> Unit) {
        checkMain()
        cancelProfilePreparation?.invoke()
        val snapshot = state()
        val profile = snapshot.profile ?: return callback(false, false)
        val context = snapshot.context
        val link = connection()
        val started = android.os.SystemClock.elapsedRealtime()
        var done = false
        fun finish(ready: Boolean) {
            if (done) return
            done = true
            profilePreparation?.let(main::removeCallbacks)
            profilePreparation = null; cancelProfilePreparation = null
            android.util.Log.i("EyeBrowseEditor", "profile geometry settle ms=${android.os.SystemClock.elapsedRealtime()-started} ready=$ready")
            if (!ready) callback(false, false)
            else if (previousTarget == null) callback(true, false)
            else open(null, previousTarget) { callback(true, it) }
        }
        val check = object : Runnable {
            override fun run() {
                if (done) return
                val latest = state()
                if (latest.context != context || connection() != link || latest.owner != ControlOwner.RG ||
                    !latest.linkAuthenticated || !latest.hostingActive || !isQuiescent() ||
                    android.os.SystemClock.elapsedRealtime() >= deadlineElapsedMs) { finish(false); return }
                val currentAdapter = adapter
                if (!hosting.localEditorFocusReady() || currentAdapter == null) { main.postDelayed(this,16); return }
                currentAdapter.install { installed ->
                    if (done) return@install
                    if (installed.instance == null) { finish(false); return@install }
                    currentAdapter.viewport { result ->
                        if (done) return@viewport
                        val view = browser.view()
                        val current = state()
                        if (current.context != context || connection() != link || !hosting.localEditorFocusReady() ||
                            android.os.SystemClock.elapsedRealtime() >= deadlineElapsedMs) finish(false)
                        else if (view != null && result.viewport?.matches(profile.width,profile.height,view.scale) == true) {
                            android.util.Log.i("EyeBrowseEditor", "profile renderer=${result.viewport} native=${view.width}x${view.height} scale=${view.scale}")
                            finish(true)
                        } else main.postDelayed(this,16)
                    }
                }
            }
        }
        profilePreparation = check; cancelProfilePreparation = { finish(false) }; check.run()
    }

    private fun open(activation: BrowserAction.ActivateAt?, previousTarget: EditorTarget?, callback: (Boolean) -> Unit) {
        checkMain()
        val adapter = adapter ?: return callback(false)
        val opening = authority.beginOpen(state(), connection(), profileTransition = previousTarget != null,
            localFocusReady = hosting.localEditorFocusReady()) ?: return callback(false)
        awaitingProfile = if (previousTarget != null) opening.context else null
        var reported = false
        fun report(ok: Boolean) { if (!reported) { reported = true; callback(ok) } }
        val timeout = Runnable {
            if (authority.isOpening(opening)) {
                authority.opened(opening, null, state(), connection()); close { }; report(false)
            }
        }
        main.postDelayed(timeout, 1000)
        adapter.install { installed ->
            if (!authority.isOpening(opening)) {
                main.removeCallbacks(timeout); report(false); return@install
            }
            val instance = installed.instance
            if (installed.status != RendererEditorAdapter.Status.STATE || instance == null) {
                main.removeCallbacks(timeout)
                authority.opened(opening, null, state(), connection()); close { }; report(false)
            } else adapter.grant(instance, opening, activation, previousTarget) { result ->
                main.removeCallbacks(timeout)
                if (!authority.isOpening(opening)) { report(false); return@grant }
                val granted = if (result.status == RendererEditorAdapter.Status.READY && result.ready &&
                    result.token == opening.id && result.generation != null && result.generation > 0 &&
                    result.revision != null && result.kind != null && result.enter != null && result.instance == instance)
                    PhoneEditorAuthority.Grant(opening, EditorTarget(opening.id, result.generation), instance,
                        result.revision, result.kind, result.enter) else null
                val ready = authority.opened(opening, granted, state(), connection(), hosting.localEditorFocusReady())
                if (!ready && authority.phase != PhoneEditorAuthority.Phase.EMPTY) close { }
                changed(); report(ready)
            }
        }
    }

    /** Caller already consumed the v2 ordinal; every result, including rejection, stays consumed. */
    fun execute(message: BrowserActionMessage, callback: (BrowserActionResultMessage) -> Unit) {
        checkMain()
        val edit = message.action as BrowserAction.Edit
        val operation = authority.beginEdit(message.commandId, edit.target, state(), connection(), hosting.localEditorFocusReady())
        val adapter = adapter
        if (operation == null || adapter == null) {
            callback(BrowserActionResultMessage(message.commandId, false, reason = "EDITOR_NOT_READY")); return
        }
        changed()
        var reported = false
        fun report(applied: Boolean?, reason: String?) {
            if (!reported) { reported = true; callback(BrowserActionResultMessage(message.commandId, true, applied, reason)) }
        }
        val timeout = Runnable {
            if (authority.timedOut(operation)) close { }
            report(null, "EDITOR_UNCERTAIN")
        }
        main.postDelayed(timeout, 1000)
        adapter.edit(operation.grant, message) { result ->
            main.removeCallbacks(timeout)
            val correlated = result.commandId == message.commandId && result.instance == operation.grant.instance &&
                result.token == operation.grant.target.token && result.generation == operation.grant.target.focusGeneration
            val completed = authority.completed(operation, if (correlated) result.revision else null,
                correlated && result.ready && hosting.localEditorFocusReady())
            if (!completed) {
                // Resolve the original transport callback without touching the successor.
                report(null, "EDITOR_UNCERTAIN"); return@edit
            }
            when (if (correlated) result.status else RendererEditorAdapter.Status.UNCERTAIN) {
                RendererEditorAdapter.Status.APPLIED -> report(true, "EDITOR_APPLIED")
                RendererEditorAdapter.Status.SUBMISSION_REQUESTED -> report(null, "SUBMISSION_REQUESTED")
                RendererEditorAdapter.Status.UNCERTAIN -> report(null, "EDITOR_UNCERTAIN")
                else -> report(false, result.status.name)
            }
            if (authority.phase == PhoneEditorAuthority.Phase.UNCERTAIN) close { }
            changed()
        }
    }

    /** Completion means the old renderer grant cannot subsequently act, not just native reset. */
    fun close(retainForProfile: Boolean = false, callback: (Boolean) -> Unit) {
        checkMain()
        cancelProfilePreparation?.invoke()
        main.removeCallbacks(observe)
        awaitingProfile = null
        if (isQuiescent()) return callback(true)
        if (closing.size >= 16) return callback(false)
        closing.add(callback)
        if (closing.size != 1) return
        val barrier = authority.revoke()
        val adapter = adapter
        if (adapter == null) { finishClose(barrier, barrier.token == null); return }
        val timeout = Runnable { finishClose(barrier, false) }
        main.postDelayed(timeout, 1000)
        adapter.inspect { inspected ->
            if (inspected.status == RendererEditorAdapter.Status.STALE_DOCUMENT) {
                main.removeCallbacks(timeout); finishClose(barrier, true)
            } else if (inspected.instance == null) {
                main.removeCallbacks(timeout); finishClose(barrier, false)
            } else adapter.revoke(inspected.instance, barrier.token, barrier.transition, retainForProfile) { result ->
                main.removeCallbacks(timeout)
                finishClose(barrier, result.status == RendererEditorAdapter.Status.STALE_DOCUMENT ||
                    (result.status == RendererEditorAdapter.Status.REVOKED && !(result.ready && result.token == barrier.token)))
            }
        }
    }

    private fun finishClose(barrier: PhoneEditorAuthority.Revocation, verified: Boolean) {
        if (!authority.isRevoking(barrier)) return
        val completed = authority.revoked(barrier, verified)
        val callbacks = closing.toList(); closing.clear()
        changed()
        callbacks.forEach { it(completed) }
        if (completed) browser.editorRetired() // Also completes a Stop whose first barrier timed out.
    }

    fun reconcile() {
        checkMain()
        val current = authority.grant ?: return
        val latest = state()
        if (current.opening.context != latest.context || current.opening.connection != connection() ||
            !latest.linkAuthenticated || latest.owner != ControlOwner.RG || !latest.hostingActive || !hosting.localEditorFocusReady() ||
            (latest.presentationStatus != PresentationStatus.READY && awaitingProfile != latest.context)) close { }
        else if (awaitingProfile == latest.context && latest.presentationStatus == PresentationStatus.READY) {
            awaitingProfile = null; changed()
        }
    }

    /** Called only by the current presentation generation. No renderer/IO under the host lock. */
    fun onLocalFocusChanged() {
        checkMain()
        if (!hosting.localEditorFocusReady() && !isQuiescent()) {
            val barrier = authority.revoke() // Fence an opening/in-flight edit synchronously.
            main.post {
                if (authority.isRevoking(barrier)) close { }
            } // A retired focus callback cannot close a successor; renderer work is outside locks.
        } else main.post { changed() } // Availability alone never opens/reopens an editor.
    }

    private fun observeCurrent() {
        val before = authority.grant ?: return
        if (authority.phase != PhoneEditorAuthority.Phase.READY) return
        reconcile()
        adapter?.inspect { result ->
            if (authority.grant !== before || authority.phase != PhoneEditorAuthority.Phase.READY) return@inspect
            if (!result.ready || result.token != before.target.token || result.instance != before.instance ||
                result.generation != before.target.focusGeneration || result.revision != before.revision) close { }
            else main.postDelayed(observe, 100)
        }
    }

    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper())
}
