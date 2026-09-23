package com.code2hack.eyebrowse.phone

import android.os.Looper
import android.webkit.WebView
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.BrowserActionMessage
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Main-thread, asynchronous renderer boundary. The bundled program is Kotlin/JS-generated.
 * Only fixed entry calls are source text; all bounded input is serialized JSON STRING DATA.
 * No addJavascriptInterface, native remote InputConnection, DOM selector or script on the wire.
 */
class RendererEditorAdapter(
    private val view: () -> WebView?,
    private val documentIdentity: () -> String,
    private val bundledProgram: String,
) {
    enum class Status {
        STATE, VIEWPORT, READY, REVOKED, APPLIED, NO_CHANGE, SUBMISSION_REQUESTED, CANCELLED,
        STALE_DOCUMENT, STALE_CONTEXT, STALE_EDITOR, REPLAY, BUSY, MALFORMED,
        UNSUPPORTED, EXHAUSTED, UNCERTAIN,
    }
    /** Renderer performance timebase only; never subtract these from Android elapsedRealtime. */
    data class Timing(val startMs: Double?, val guardMs: Double?, val mutationMs: Double?, val resultMs: Double?)
    data class Result(val status: Status, val instance: String? = null, val token: String? = null,
                      val generation: Long? = null, val revision: Long? = null,
                      val kind: EditorKind? = null, val enter: EditorEnter? = null,
                      val ready: Boolean = false, val commandId: String? = null, val timing: Timing? = null,
                      val lifecycleOrder: Long? = null, val viewport: RendererViewport? = null)

    /** Installs once in this actual document. Edit/revoke never re-install an old grant. */
    fun install(callback: (Result) -> Unit) = evaluate(bundledProgram + "\n" + invocation(JSONObject().put("op", "inspect")), callback)

    fun inspect(callback: (Result) -> Unit) = request(JSONObject().put("op", "inspect"), callback)
    fun viewport(callback: (Result) -> Unit) = request(JSONObject().put("op", "viewport"), callback)

    fun grant(instance: String, opening: PhoneEditorAuthority.Opening,
              activation: BrowserAction.ActivateAt? = null, previousTarget: EditorTarget? = null,
              callback: (Result) -> Unit) {
        if (opening.context.documentId != documentIdentity()) return callback(Result(Status.STALE_DOCUMENT))
        val payload = JSONObject().put("op", "grant").put("instance", instance).put("token", opening.id)
            .put("transition", opening.transition.toString()).put("context", context(opening.context))
        if (previousTarget != null) payload.put("previousToken", previousTarget.token)
        if (activation != null) {
            val scale = view()?.scale ?: return callback(Result(Status.STALE_DOCUMENT))
            if (!scale.isFinite() || scale <= 0) return callback(Result(Status.UNSUPPORTED))
            payload.put("x", activation.x / scale).put("y", activation.y / scale)
        }
        request(payload, callback)
    }

    fun edit(grant: PhoneEditorAuthority.Grant, message: BrowserActionMessage, callback: (Result) -> Unit) {
        val edit = message.action as? BrowserAction.Edit ?: error("editor action required")
        require(edit.target == grant.target && message.context == grant.opening.context)
        if (message.context.documentId != documentIdentity()) return callback(Result(Status.STALE_DOCUMENT))
        val payload = JSONObject().put("op", "edit").put("instance", grant.instance)
            .put("context", context(message.context)).put("sequence", message.commandSequence.toString())
            .put("commandId", message.commandId).put("token", grant.target.token)
            .put("generation", grant.target.focusGeneration.toString()).put("revision", grant.revision.toString())
        when (val operation = edit.operation) {
            is EditorOperation.Insert -> payload.put("action", "INSERT").put("text", operation.text)
            EditorOperation.Backspace -> payload.put("action", "BACKSPACE")
            EditorOperation.Enter -> payload.put("action", "ENTER")
        }
        request(payload, callback)
    }

    fun revoke(instance: String, token: String?, transition: Long, retainForProfile: Boolean = false,
               callback: (Result) -> Unit) =
        request(JSONObject().put("op", "revoke").put("instance", instance).put("token", token)
            .put("transition", transition.toString()).put("retainForProfile", retainForProfile), callback)

    private fun request(payload: JSONObject, callback: (Result) -> Unit) {
        require(payload.toString().toByteArray(Charsets.UTF_8).size <= 32 * 1024) { "renderer request exceeds bound" }
        evaluate(invocation(payload), callback)
    }

    private fun invocation(payload: JSONObject): String {
        val quoted = JSONObject.quote(payload.toString()).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
        return "window.__eyebrowseEditorV1 ? window.__eyebrowseEditorV1.request($quoted) : null"
    }

    private fun evaluate(script: String, callback: (Result) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val current = view() ?: return callback(Result(Status.STALE_DOCUMENT))
        val document = documentIdentity()
        current.evaluateJavascript(script) { raw ->
            if (view() !== current || documentIdentity() != document) callback(Result(Status.STALE_DOCUMENT))
            else callback(decode(raw))
        }
    }

    private fun decode(raw: String?): Result = try {
        if (raw == null || raw.length > 8192) Result(Status.UNCERTAIN) else {
            val encoded = JSONTokener(raw).nextValue() as? String
            if (encoded == null) Result(Status.STALE_DOCUMENT) else {
                val objectValue = JSONObject(encoded)
                fun bounded(key: String, max: Int): String? =
                    (objectValue.opt(key) as? String)?.takeIf { it.length <= max }
                fun long(key: String): Long? = bounded(key, 19)?.takeIf { Regex("0|[1-9][0-9]*").matches(it) }?.toLongOrNull()
                fun time(key: String): Double? = (objectValue.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 }
                Result(Status.valueOf(bounded("status", 32) ?: "UNCERTAIN"), bounded("instance", 128),
                    bounded("token", 128), long("generation"), long("revision"),
                    bounded("kind", 16)?.let(EditorKind::valueOf), bounded("enter", 32)?.let(EditorEnter::valueOf),
                    objectValue.opt("ready") == true, bounded("commandId", BrowserCommandId.MAX_LENGTH),
                    Timing(time("rendererStartMs"), time("rendererGuardMs"), time("rendererMutationMs"), time("rendererResultMs")), long("lifecycleOrder"),
                    if (objectValue.optString("status") == "VIEWPORT") RendererViewport(
                        time("viewportWidth") ?: 0.0, time("viewportHeight") ?: 0.0,
                        time("viewportScale") ?: 0.0, time("devicePixelRatio") ?: 0.0,
                        objectValue.opt("pageFocused") == true) else null)
            }
        }
    } catch (_: Exception) { Result(Status.UNCERTAIN) } // Never export raw result/exception text.

    companion object {
        /** Long values stay canonical decimal STRINGS through JSON and Kotlin/JS. */
        private fun context(c: ControlContext) = JSONObject().put("lifetimeId", c.lifetimeId)
            .put("controlEpoch", c.controlEpoch.toString()).put("documentId", c.documentId)
            .put("viewportEpoch", c.viewportEpoch.toString()).put("hostingGeneration", c.hostingGeneration?.toString() ?: "n")
    }
}
