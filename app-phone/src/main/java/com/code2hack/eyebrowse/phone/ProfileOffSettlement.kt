package com.code2hack.eyebrowse.phone

/** One pre-admission wait owned by the existing profile transaction and its absolute deadline. */
internal class ProfileOffSettlement(
    private val deadline: Long,
    private val now: () -> Long,
    private val observe: () -> State,
    private val post: (Runnable, Long) -> Boolean,
    private val remove: (Runnable) -> Unit,
    private val admitted: () -> Unit,
    private val failed: (retired: Boolean) -> Unit,
) {
    enum class Display { OFF, ON, UNSUPPORTED }
    data class State(
        val owned: Boolean,
        val identities: Boolean,
        val reader: Boolean,
        val noConflict: Boolean,
        val attached: Boolean,
        val density: Boolean,
        val focus: Boolean,
        val focusHistory: Boolean,
        val available: Boolean,
        val validDisplay: Boolean,
        val display: Display,
    )
    private var ended = false
    private var cancelled = false
    private var queued = false
    private val check = Runnable { queued = false; advance() }
    fun start() = advance()
    fun completionInTime(): Boolean = now() < deadline
    fun cancel() { cancelled = true; ended = true; if (queued) remove(check); queued = false }
    /** ON was observed, but it returned to OFF before staging any native work. Same request/D. */
    fun awaitOffAgain() {
        if (cancelled || !ended) return
        ended = false
        schedule()
    }
    private fun schedule() {
        if (!queued) {
            queued = true
            if (!post(check, minOf(RECHECK_MS, (deadline-now()).coerceAtLeast(1)))) reject(false)
        }
    }
    private fun reject(retired: Boolean) { cancel(); failed(retired) }
    private fun advance() {
        if (ended) return
        val state = observe()
        if (!state.owned || !state.identities) { reject(true); return }
        if (now() >= deadline || !state.reader || !state.noConflict || !state.attached ||
            !state.density || !state.focus || !state.focusHistory || !state.available || !state.validDisplay ||
            state.display == Display.UNSUPPORTED) { reject(false); return }
        if (state.display == Display.ON) {
            ended = true
            if (queued) remove(check)
            queued = false
            admitted(); return
        }
        schedule()
    }
    companion object {
        // Main Handler wakeup, independent of draws/animations on the currently OFF display.
        const val RECHECK_MS = 20L
    }
}
