package com.code2hack.eyebrowse.editor

external val window: dynamic
external val document: dynamic
external val JSON: dynamic
external class MutationObserver(callback: (dynamic, dynamic) -> Unit) {
    fun observe(node: dynamic, options: dynamic)
    fun takeRecords(): dynamic
}
external class InputEvent(type: String, options: dynamic)
external class Event(type: String, options: dynamic)
external class TextEncoder { fun encode(text: String): dynamic }

private fun obj(): dynamic = js("({})")
private fun string(value: dynamic, max: Int): String? =
    if (jsTypeOf(value) == "string" && (value as String).length <= max) value else null
private fun decimal(value: dynamic, positive: Boolean = false): String? = string(value, 19)?.takeIf {
    Regex(if (positive) "[1-9][0-9]*" else "0|[1-9][0-9]*").matches(it) &&
        (it.length < 19 || it <= "9223372036854775807")
}
private fun greater(a: String, b: String) = a.length > b.length || (a.length == b.length && a > b)

private data class Context(val lifetime: String, val epoch: String, val documentId: String,
                           val viewport: String, val hosting: String) {
    fun command(sequence: String) = "v2:$epoch:$sequence:$viewport:$hosting:${lifetime.length}:$lifetime${documentId.length}:$documentId"
    companion object {
        fun read(value: dynamic): Context? {
            if (value == null) return null
            return Context(string(value.lifetimeId, 128)?.takeIf { it.isNotBlank() } ?: return null,
                decimal(value.controlEpoch) ?: return null,
                string(value.documentId, 256)?.takeIf { it.isNotBlank() } ?: return null,
                decimal(value.viewportEpoch) ?: return null,
                if (value.hostingGeneration == "n") "n" else decimal(value.hostingGeneration) ?: return null)
        }
    }
}

private class Selection(val start: dynamic, val startOffset: Int, val end: dynamic, val endOffset: Int) {
    fun same(other: Selection?) = other != null && start === other.start && startOffset == other.startOffset &&
        end === other.end && endOffset == other.endOffset
}

private class Grant(val node: dynamic, val token: String, val context: Context, val generation: String,
                    val kind: String, var selection: Selection) {
    var revision = 0L
}

/** One document, one node-bound grant, one in-flight operation; no text/value history. */
private class Editor {
    private val doc = document
    private val random = js("new Uint32Array(4)")
    private val instance: String
    private var generation = 0L
    private var grant: Grant? = null
    private var retired: Grant? = null // One explicitly retained profile-transition target only.
    private var highWater = "0"
    private var transition = "0"
    private var namespace: Pair<String, String>? = null
    private var busy = false
    private var ownInput = false
    private var dirtyNode: dynamic = null
    // Capture public primitives once. Element-defined overrides cannot introduce callbacks at
    // the audited synchronous mutation boundary. This is not a hostile-page security sandbox.
    private val inputRange = window.HTMLInputElement.prototype.setRangeText
    private val textareaRange = window.HTMLTextAreaElement.prototype.setRangeText
    private val dispatchEvent = window.EventTarget.prototype.dispatchEvent
    private val submit = window.HTMLFormElement.prototype.requestSubmit
    private val click = window.HTMLElement.prototype.click
    private val reveal = window.Element.prototype.scrollIntoView
    private val observer = MutationObserver { records, _ -> mutations(records) }

    init {
        window.crypto.getRandomValues(random)
        instance = (0..3).joinToString("-") { random[it].toString() }
        val options = obj()
        options.subtree = true; options.childList = true; options.attributes = true; options.characterData = true
        options.attributeFilter = arrayOf("disabled", "readonly", "type", "contenteditable", "maxlength", "form")
        observer.observe(doc, options)
        doc.addEventListener("focusin", { _: dynamic -> invalidate() }, true)
        doc.addEventListener("focusout", { event: dynamic ->
            invalidate()
            if (dirtyNode === event.target) {
                val changed = dirtyNode; dirtyNode = null
                val options = obj(); options.bubbles = true
                dispatchEvent.call(changed, Event("change", options))
            }
        }, true)
        doc.addEventListener("input", { _: dynamic ->
            if (!ownInput) { dirtyNode = null; invalidate() }
        }, true)
        doc.addEventListener("selectionchange", { _: dynamic ->
            val current = grant
            if (current != null && !busy && !current.selection.same(selection(current.node, current.kind))) invalidate()
        }, true)
    }

    private fun invalidate() { grant = null; retired = null }

    private fun mutations(records: dynamic) {
        val current = grant ?: retired ?: return
        val node = current.node
        for (i in 0 until (records.length as Int)) {
            val record = records[i]
            // Includes ancestor fieldset/attachment changes and target attribute ABA.
            if (record.target === node || node.contains(record.target) == true ||
                (record.type == "attributes" && record.target.contains(node) == true)) {
                invalidate(); return
            }
            if (record.type == "childList") for (j in 0 until (record.removedNodes.length as Int)) {
                val removed = record.removedNodes[j]
                if (removed === node || removed.contains(node) == true) { invalidate(); return }
            }
        }
    }

    private fun kind(node: dynamic): String? {
        if (node == null || node.ownerDocument !== doc || node.getRootNode() !== doc || node.isConnected != true ||
            node.matches(":disabled") == true || node.readOnly == true) return null
        return when (node.tagName as? String) {
            "INPUT" -> when (node.type as? String) { "text" -> "TEXT"; "password" -> "PASSWORD"; else -> null }
            "TEXTAREA" -> "MULTILINE"
            else -> if (node.isContentEditable == true && node.parentElement?.isContentEditable != true && plain(node)) "PLAIN" else null
        }
    }

    private fun plain(node: dynamic): Boolean {
        // Only direct text/BR children: never flatten or reinterpret arbitrary rich markup.
        if ((node.childNodes.length as Int) > 4096) return false
        for (i in 0 until (node.childNodes.length as Int)) {
            val child = node.childNodes[i]
            if (child.nodeType != 3 && !(child.nodeType == 1 && child.tagName == "BR")) return false
        }
        return true
    }

    private fun selection(node: dynamic, kind: String): Selection? {
        if (kind != "PLAIN") {
            val start = node.selectionStart; val end = node.selectionEnd
            if (jsTypeOf(start) != "number" || jsTypeOf(end) != "number") return null
            return Selection(node, start as Int, node, end as Int)
        }
        val selection = doc.getSelection()
        if (selection.rangeCount != 1) return null
        val range = selection.getRangeAt(0)
        if (node.contains(range.startContainer) != true || node.contains(range.endContainer) != true) return null
        return Selection(range.startContainer, range.startOffset as Int, range.endContainer, range.endOffset as Int)
    }

    private fun valid(current: Grant): Boolean {
        mutations(observer.takeRecords())
        return grant === current && current.node.ownerDocument === doc && window.document === doc &&
            doc.activeElement === current.node && kind(current.node) == current.kind &&
            current.selection.same(selection(current.node, current.kind))
    }

    private fun result(status: String, current: Grant? = grant, id: String? = null): dynamic {
        val out = obj()
        out.status = status; out.instance = instance; out.commandId = id
        out.lifecycleOrder = transition
        out.token = current?.token; out.generation = current?.generation
        out.revision = current?.revision?.toString(); out.kind = current?.kind
        out.ready = current != null && grant === current
        out.enter = when (current?.kind) { "TEXT", "PASSWORD" -> "IMPLICIT_SUBMIT"; "MULTILINE", "PLAIN" -> "LINE_BREAK"; else -> null }
        return out
    }

    fun request(encoded: String): String {
        if (encoded.length > 32768) return JSON.stringify(result("MALFORMED")) as String
        return try {
            val trace = obj(); trace.start = window.performance.now()
            val r = JSON.parse(encoded)
            val answer = when {
                r == null -> result("MALFORMED")
                r.op == "inspect" -> { grant?.let { if (!valid(it)) invalidate() }; result("STATE") }
                r.instance != instance -> result("STALE_DOCUMENT", null)
                r.op == "grant" -> open(r)
                r.op == "revoke" -> {
                    // An old close cannot revoke a successor. A matching close revokes even
                    // during a synchronous page callback; the outer edit revalidates afterward.
                    val order = decimal(r.transition, true)
                    if (order == null) result("MALFORMED") else {
                        if (greater(order, transition)) {
                            transition = order
                            if (r.token == grant?.token) {
                                val old = grant
                                val retain = r.retainForProfile == true && old != null && valid(old)
                                invalidate()
                                if (retain) retired = old
                            } else if (r.retainForProfile != true) retired = null
                        }
                        result("REVOKED")
                    }
                }
                r.op == "edit" -> edit(r, trace)
                else -> result("MALFORMED")
            }
            answer.rendererStartMs = trace.start; answer.rendererGuardMs = trace.guard
            answer.rendererMutationMs = trace.mutation; answer.rendererResultMs = window.performance.now()
            JSON.stringify(answer) as String
        } catch (_: Throwable) {
            invalidate()
            JSON.stringify(result("UNCERTAIN", null)) as String // Never stringify a script exception/payload.
        }
    }

    private fun open(r: dynamic): dynamic {
        if (busy) return result("BUSY", null)
        val order = decimal(r.transition, true) ?: return result("MALFORMED", null)
        if (!greater(order, transition)) return result("STALE_EDITOR", null)
        transition = order
        mutations(observer.takeRecords())
        val previous = retired
        val context = Context.read(r.context) ?: return result("MALFORMED", null)
        val token = string(r.token, 128)?.takeIf { it.isNotBlank() } ?: return result("MALFORMED", null)
        val node = doc.activeElement
        val kind = kind(node) ?: return result("UNSUPPORTED", null)
        if (r.previousToken != null && (previous == null || r.previousToken != previous.token ||
                node !== previous.node || kind != previous.kind || context.lifetime != previous.context.lifetime ||
                context.epoch != previous.context.epoch || context.documentId != previous.context.documentId ||
                context.hosting != previous.context.hosting || !previous.selection.same(selection(node, kind))))
            return result("STALE_EDITOR", null)
        invalidate()
        if (r.x != null || r.y != null) {
            if (jsTypeOf(r.x) != "number" || jsTypeOf(r.y) != "number") return result("MALFORMED", null)
            val hit = doc.elementFromPoint(r.x, r.y)
            if (hit == null || !(hit === node || (kind == "PLAIN" && node.contains(hit) == true) || hit.control === node))
                return result("UNSUPPORTED", null)
        }
        val selection = selection(node, kind) ?: return result("UNSUPPORTED", null)
        if (generation == Long.MAX_VALUE) return result("EXHAUSTED", null)
        generation++
        val ns = context.lifetime to context.epoch
        if (ns != namespace) { namespace = ns; highWater = "0" }
        val current = Grant(node, token, context, generation.toString(), kind, selection)
        grant = current
        val options = obj(); options.block = "nearest"; options.inline = "nearest"
        reveal.call(node, options)
        if (!valid(current)) { invalidate(); return result("STALE_EDITOR", null) }
        return result("READY", current)
    }

    private fun edit(r: dynamic, trace: dynamic): dynamic {
        val context = Context.read(r.context) ?: return result("MALFORMED", null)
        val sequence = decimal(r.sequence, true) ?: return result("MALFORMED", null)
        val id = string(r.commandId, 475) ?: return result("MALFORMED", null)
        if (id != context.command(sequence)) return result("MALFORMED", null)
        val current = grant ?: return result("STALE_EDITOR", null, id)
        if (context != current.context) return result("STALE_CONTEXT", null, id)
        if (!greater(sequence, highWater)) return result("REPLAY", current, id)
        highWater = sequence // Definitive current-context rejection consumes its ordinal.
        if (r.token != current.token || r.generation != current.generation || r.revision != current.revision.toString())
            return result("STALE_EDITOR", current, id)
        if (busy) return result("BUSY", current, id)
        if (!valid(current)) { invalidate(); return result("STALE_EDITOR", null, id) }
        if (current.revision == Long.MAX_VALUE) { invalidate(); return result("EXHAUSTED", null, id) }
        val operation = string(r.action, 16) ?: return result("MALFORMED", current, id)
        val text = if (operation == "INSERT") string(r.text, 256) ?: return result("MALFORMED", current, id) else ""
        if (operation == "INSERT" && (!printable(text) || (TextEncoder().encode(text).length as Int) > 256))
            return result("MALFORMED", current, id)
        if (operation !in listOf("INSERT", "BACKSPACE", "ENTER")) return result("MALFORMED", current, id)
        busy = true
        try {
            if (operation == "ENTER" && current.kind in listOf("TEXT", "PASSWORD")) {
                return result(enter(current, trace), current, id)
            }
            val insertion = if (operation == "ENTER") "\n" else text
            val node = current.node
            val beforeValue: String? = if (current.kind == "PLAIN") null else node.value as String
            val options = obj(); options.bubbles = true; options.cancelable = true
            options.inputType = when (operation) { "BACKSPACE" -> "deleteContentBackward"; "ENTER" -> "insertLineBreak"; else -> "insertText" }
            options.data = if (operation == "INSERT") insertion else null
            val allowed = dispatchEvent.call(node, InputEvent("beforeinput", options)) == true
            if (!valid(current) || (beforeValue != null && node.value != beforeValue)) {
                invalidate(); return result("STALE_EDITOR", null, id)
            }
            if (!allowed) return result("CANCELLED", current, id)
            trace.guard = window.performance.now()
            // Audited synchronous public primitives; no event dispatch until mutation completes.
            val changed = if (current.kind == "PLAIN") editPlain(current, operation, insertion)
                          else editControl(current, operation, insertion, beforeValue!!)
            if (changed) trace.mutation = window.performance.now()
            observer.takeRecords() // Only our synchronous text/BR mutation records occur here.
            current.selection = selection(node, current.kind) ?: run { invalidate(); return result("UNCERTAIN", null, id) }
            current.revision++
            if (changed) {
                dirtyNode = node
                ownInput = true
                try { options.cancelable = false; dispatchEvent.call(node, InputEvent("input", options)) }
                finally { ownInput = false }
            }
            if (!valid(current)) invalidate()
            return result(if (changed) "APPLIED" else "NO_CHANGE", current, id)
        } finally { busy = false }
    }

    private fun printable(text: String): Boolean {
        if (text.isEmpty()) return false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.code < 32 || c == '\u007f') return false
            if (c.isHighSurrogate()) { if (++i >= text.length || !text[i].isLowSurrogate()) return false }
            else if (c.isLowSurrogate()) return false
            i++
        }
        return true
    }

    private fun previous(text: String, offset: Int): Int = if (offset >= 2 && text[offset - 1].isLowSurrogate() &&
        text[offset - 2].isHighSurrogate()) offset - 2 else (offset - 1).coerceAtLeast(0)

    private fun editControl(current: Grant, operation: String, insertion: String, value: String): Boolean {
        val node = current.node
        var start = current.selection.startOffset
        val end = current.selection.endOffset
        if (operation == "BACKSPACE" && start == end) start = previous(value, start)
        val replacement = if (operation == "BACKSPACE") "" else insertion
        if (start == end && replacement.isEmpty()) return false
        val maximum = node.maxLength as Int
        if (maximum >= 0 && replacement.length > end - start && value.length - (end - start) + replacement.length > maximum) return false
        (if (current.kind == "MULTILINE") textareaRange else inputRange).call(node, replacement, start, end, "end")
        return true
    }

    private fun editPlain(current: Grant, operation: String, insertion: String): Boolean {
        val s = current.selection
        val range = doc.createRange()
        range.setStart(s.start, s.startOffset); range.setEnd(s.end, s.endOffset)
        if (operation == "BACKSPACE" && range.collapsed == true) {
            var child = s.start
            var offset = s.startOffset
            if (child === current.node) {
                if (offset == 0) return false
                child = current.node.childNodes[offset - 1]
                offset = if (child.nodeType == 3) (child.data as String).length else 0
            }
            if (child.nodeType == 3 && offset > 0) range.setStart(child, previous(child.data as String, offset))
            else {
                val previousNode = if (child.nodeType == 1) child else child.previousSibling
                if (previousNode == null) return false
                if (previousNode.nodeType == 3 && (previousNode.data as String).isNotEmpty())
                    range.setStart(previousNode, previous(previousNode.data as String, (previousNode.data as String).length))
                else range.setStartBefore(previousNode)
            }
        }
        val selected = range.collapsed != true
        range.deleteContents()
        if (operation != "BACKSPACE") {
            val inserted = if (operation == "ENTER") doc.createElement("br") else doc.createTextNode(insertion)
            range.insertNode(inserted); range.setStartAfter(inserted); range.collapse(true)
        }
        val selection = doc.getSelection()
        selection.removeAllRanges(); selection.addRange(range)
        return selected || operation != "BACKSPACE"
    }

    private fun enter(current: Grant, trace: dynamic): String {
        val form = current.node.form ?: return "NO_CHANGE"
        if (form.ownerDocument !== doc || form.isConnected != true) return "STALE_EDITOR"
        // Normal form association, including externally associated controls. First submitter in
        // document order is the default; a disabled default must not fall through to another.
        val controls = doc.querySelectorAll("button,input")
        var submitter: dynamic = null
        var blockers = 0
        for (i in 0 until (controls.length as Int)) {
            val control = controls[i]
            if (control.form !== form) continue
            val type = control.type as String
            if (submitter == null && type in listOf("submit", "image")) submitter = control
            if (control.tagName == "INPUT" && type in listOf("text", "search", "url", "tel", "email", "password",
                    "date", "month", "week", "time", "datetime-local", "number")) blockers++
        }
        if (submitter != null && submitter.matches(":disabled") == true) return "NO_CHANGE"
        if (submitter == null && blockers > 1) return "NO_CHANGE"
        if (!valid(current) || current.node.form !== form) return "STALE_EDITOR"
        trace.guard = window.performance.now()
        var reached = false
        var rejected = false
        val guard: (dynamic) -> Unit = { event ->
            // Default-button click and constraint validation invoke page code synchronously.
            // Catch reassociation/refocus before the resulting browser submit default proceeds.
            if (event.target !== form || !valid(current) || current.node.form !== form) {
                event.preventDefault(); rejected = true
            } else { reached = true; trace.mutation = window.performance.now() }
        }
        window.addEventListener("submit", guard, true)
        try {
            // HTML implicit submission clicks the eligible default. Only the no-default,
            // <=1 blocking-field case requests submission from the original form itself.
            if (submitter == null) submit.call(form) else click.call(submitter)
        } finally { window.removeEventListener("submit", guard, true) }
        if (!valid(current)) invalidate()
        return if (rejected) "CANCELLED" else if (reached) "SUBMISSION_REQUESTED" else "NO_CHANGE"
    }
}

/** Fixed, generated asset only. No page-to-Android bridge or executable wire payload. */
fun main() {
    if (window.__eyebrowseEditorV1 == null) {
        val editor = Editor()
        val api = js("({})")
        api.version = 1
        api.request = { encoded: String -> editor.request(encoded) }
        window.__eyebrowseEditorV1 = api
    }
}
