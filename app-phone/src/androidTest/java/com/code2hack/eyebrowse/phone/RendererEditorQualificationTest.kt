package com.code2hack.eyebrowse.phone

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.webkit.WebView
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.BrowserActionMessage
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Direct production-adapter substrate qualification. No RG-key/TLS admission claim. */
@RunWith(AndroidJUnit4::class)
class RendererEditorQualificationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val browser = PhoneBrowserSession.get(app)
    private val host = HostingController.get(app)
    private val helper = BrowserControlJourneyTest()
    private lateinit var adapter: RendererEditorAdapter
    private lateinit var context: ControlContext
    private lateinit var instance: String
    private var order = 0L
    private var sequence = 0L
    private var lease: HostingController.Lease? = null
    private var effectEnqueuedMs: Long? = null
    private var effectReported = false
    private var effectExpected = false
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun js(script: String) = helper.js(browser, script)
    private fun truth(script: String) = js("Boolean($script)") == "true"
    private fun observeEffect(script: String): Boolean {
        val answer = js("Boolean($script)") == "true"
        if (answer && effectExpected && !effectReported) {
            val elapsed = SystemClock.elapsedRealtime() - checkNotNull(effectEnqueuedMs)
            effectReported = true
            Log.i(TAG, "OBSERVED_EFFECT nativeEnqueueToIndependentBooleanMs=$elapsed")
            assertTrue("conservative enqueue-to-observed fixture effect <=1000ms, observed=$elapsed", elapsed <= 1000)
        }
        return answer
    }
    private fun await(label: String, bound: Long = 2000,
                      diagnostics: () -> Map<String, Boolean> = {
                          mapOf("browserLive" to browser.isLive(), "notLoading" to !browser.isLoading(),
                              "errorAbsent" to (browser.errorMessage() == null))
                      }, predicate: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + bound
        while (SystemClock.elapsedRealtime() < until) { if (predicate()) return; SystemClock.sleep(15) }
        val checks = runCatching { diagnostics() }.getOrElse { mapOf("diagnosticsAvailable" to false) }
        Log.e(TAG, "AWAIT_TIMEOUT barrier=$label lastPredicateSatisfied=false checks=${JSONObject(checks)}")
        fail(label)
    }
    private fun call(label: String, action: ((RendererEditorAdapter.Result) -> Unit) -> Unit): RendererEditorAdapter.Result {
        val done = CountDownLatch(1); var result: RendererEditorAdapter.Result? = null
        val start = SystemClock.elapsedRealtime()
        main { lease?.renew(); action { result = it; done.countDown() } }
        assertTrue("$label renderer callback", done.await(2, TimeUnit.SECONDS))
        val answer = result!!
        Log.i(TAG, "STAGE label=$label status=${answer.status} nativeElapsedMs=${SystemClock.elapsedRealtime()-start} renderer=${answer.timing}")
        return answer
    }
    private fun reset() { assertEquals("true", js("resetFields();true")) }
    private fun focus(id: String, value: String = "", start: Int = value.length, end: Int = start) {
        // Fixed fixture setup/observation only; these are not keyboard-generated positives.
        val encoded = JSONObject.quote(value)
        assertEquals("true", js("""(()=>{const e=document.getElementById('$id');e.focus();
            if(e.isContentEditable){e.textContent=$encoded;const r=document.createRange();
              if(e.firstChild){r.setStart(e.firstChild,$start);r.setEnd(e.firstChild,$end)}else{r.selectNodeContents(e);r.collapse(true)}
              const s=getSelection();s.removeAllRanges();s.addRange(r)
            }else{e.value=$encoded;e.setSelectionRange($start,$end)}return document.activeElement===e})()"""))
    }
    private fun unchanged(id: String, expected: String): Boolean = observeEffect("(()=>{const e=document.getElementById('$id');return (e.isContentEditable?e.textContent:e.value)===${JSONObject.quote(expected)}})()")
    private fun grant(): PhoneEditorAuthority.Grant {
        val opening = PhoneEditorAuthority.Opening(UUID.randomUUID().toString(), context, 1, ++order)
        val result = call("grant") { adapter.grant(instance, opening, callback = it) }
        assertEquals(RendererEditorAdapter.Status.READY, result.status)
        return PhoneEditorAuthority.Grant(opening, EditorTarget(result.token!!, result.generation!!), instance,
            result.revision!!, result.kind!!, result.enter!!)
    }
    private fun message(g: PhoneEditorAuthority.Grant, operation: EditorOperation, n: Long = ++sequence) =
        BrowserActionMessage(BrowserCommandId.create(g.opening.context, n), g.opening.context,
            BrowserAction.Edit(g.target, operation), n)
    private fun edit(g: PhoneEditorAuthority.Grant, operation: EditorOperation, label: String,
                     n: Long = ++sequence): RendererEditorAdapter.Result {
        val text = (operation as? EditorOperation.Insert)?.text
        js("window.expectedData=${text?.let(JSONObject::quote) ?: "null"};markEventPhase('before-adapter-edit');true")
        effectEnqueuedMs = SystemClock.elapsedRealtime(); effectReported = false; effectExpected = false
        return call(label) { adapter.edit(g, message(g, operation, n), it) }.also {
            effectExpected = it.status == RendererEditorAdapter.Status.APPLIED || it.status == RendererEditorAdapter.Status.SUBMISSION_REQUESTED
        }
    }
    private fun row(name: String, body: () -> Unit) {
        Log.i(TAG, "ROW_START name=$name")
        effectEnqueuedMs = null; effectReported = false; effectExpected = false
        body()
        Log.i(TAG, "ROW_PASS name=$name")
    }

    @Test fun hostedRendererQualificationMatrix() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val link = PhoneLinkServer.obtain(app)
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        val base = InstrumentationRegistry.getArguments().getString("fixtureBaseUrl", "http://127.0.0.1:26491")
        val mission = InstrumentationRegistry.getArguments().getString("missionId") ?: UUID.randomUUID().toString()
        val url = "$base/editor-fixture.html?case=$mission"
        var original: WebView? = null
        var primary: Throwable? = null
        try {
            lateinit var barrier: FixtureNavigationBarrier
            scenario.onActivity {
                link.stop()
                barrier = FixtureNavigationBarrier(browser.documentIdentity(), url, expectedTitle = "I9 renderer fixture")
                browser.openAddress(url)
            }
            var observed: FixtureNavigationBarrier.Observation? = null
            await("fresh renderer fixture", 10000, diagnostics = {
                val last = observed
                mapOf("observationAvailable" to (last != null), "live" to (last?.live == true),
                    "errorAbsent" to (last != null && last.error == null), "notLoading" to (last?.loading == false),
                    "documentChanged" to (last != null && last.documentId != barrier.previousDocumentId),
                    "displayedUrlMatches" to (last?.displayedUrl == barrier.requestedUrl),
                    "committedUrlMatches" to (last?.committedUrl == barrier.requestedUrl),
                    "titleMatches" to (last?.title == "I9 renderer fixture"))
            }) {
                val current = FixtureNavigationBarrier.Observation(browser.documentIdentity(), browser.displayUrl(),
                    browser.lastCommittedUrl(), browser.pageTitle(), browser.isLoading(), browser.isLive(), browser.errorMessage())
                observed = current
                barrier.isReady(current)
            }
            context = ControlContext("substrate-${UUID.randomUUID()}", 1, browser.documentIdentity(), 1, 1)
            val program = app.assets.open("eyebrowse-editor.js").bufferedReader().use { it.readText() }
            main { adapter = RendererEditorAdapter(browser::view, browser::documentIdentity, program) }
            instance = call("install", adapter::install).instance!!
            scenario.onActivity {
                original = browser.view()
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            await("hosting", 5000) { host.status().state == HostingController.State.HOSTING }
            main { assertTrue(host.presentOnRg(HostingPresentationProfile(480, 344, 204))); lease = host.acquireLease { } }
            assertNotNull(lease)
            scenario.moveToState(Lifecycle.State.CREATED)
            await("real private local editor focus", 1000) {
                var ready=false;main {ready=host.localEditorFocusReady()};ready&&truth("document.hasFocus()")
            }
            row("private-background-contract") {
                main {
                    val view = browser.view()!!
                    assertSame(original, view)
                    val flags = (view.rootView.layoutParams as WindowManager.LayoutParams).flags
                    assertTrue(view.display!!.displayId != 0)
                    assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
                    assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
                    assertTrue(flags and WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE != 0)
                    assertTrue(view.hasWindowFocus() && view.hasFocus())
                    Log.i(TAG,"LOCAL_FOCUS display=${view.display!!.displayId} window=${view.hasWindowFocus()} view=${view.hasFocus()} flags=$flags")
                }
                assertEquals(0, automation.windows.count { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD })
                val focusDump=automation.executeShellCommand("dumpsys window").use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use{r->r.readText()}}
                val focusLines=focusDump.lineSequence().filter{it.contains("mCurrentFocus=")||it.contains("mFocusedApp=")||it.contains("mTopFocusedDisplayId=")}.map{it.trim()}.toList()
                Log.i(TAG,"GLOBAL_FOCUS metadata=${org.json.JSONArray(focusLines)}")
                assertTrue(focusLines.any{it=="mTopFocusedDisplayId=0"})
            }
            focusTransitionCases()
            for (id in listOf("a", "p", "t", "c")) {
                row("$id-caret-case-symbol-events") {
                    reset(); focus(id)
                    val text = "aZ9 :/?@-_=&#%+\"\\[]\u2028😀"
                    val r = edit(grant(), EditorOperation.Insert(text), "caret-$id")
                    assertEquals(RendererEditorAdapter.Status.APPLIED, r.status); assertTrue(unchanged(id, text))
                    assertTrue(truth("events.filter(e=>e.type==='beforeinput'&&e.target==='$id'&&e.dataMatches&&e.bubbles&&!e.trusted).length===1"))
                    assertTrue(truth("events.filter(e=>e.type==='input'&&e.target==='$id'&&e.dataMatches&&e.bubbles&&!e.trusted).length===1"))
                    if (id == "p") assertTrue(truth("document.getElementById('p').type==='password'"))
                    js("markEventPhase('before-b-focus');document.getElementById('b').focus();markEventPhase('after-b-focus');true")
                    // Snapshot the original assertion predicate at its original next-evaluation
                    // boundary, before logging. No wait or second sample can replace its result.
                    val diagnostic = JSONObject(js("""(()=>{
                        const changes=events.filter(e=>e.type==='change'&&e.target==='$id');
                        const delivered=eventTrace.filter(e=>e.type==='change'&&e.target==='$id');
                        return {changeCount:changes.length,exactlyOneChange:changes.length===1,
                          nativeChanges:delivered.filter(e=>e.source==='webview-native').length,
                          adapterFocusoutChanges:delivered.filter(e=>e.source==='adapter-focusout').length,
                          otherScriptChanges:delivered.filter(e=>e.source==='script-other').length,
                          captureMatchesBubble:delivered.length===changes.length,
                          beforeinputOnce:events.filter(e=>e.type==='beforeinput'&&e.target==='$id'&&e.dataMatches&&e.bubbles&&!e.trusted).length===1,
                          inputOnce:events.filter(e=>e.type==='input'&&e.target==='$id'&&e.dataMatches&&e.bubbles&&!e.trusted).length===1,
                          activeB:document.activeElement===document.getElementById('b'),
                          overflow:eventTraceOverflow,order:eventTrace};})()"""))
                    Log.i(TAG, "CHANGE_DIAGNOSTIC field=$id metadata=$diagnostic")
                    assertTrue("exactly one change on blur", diagnostic.getBoolean("exactlyOneChange"))
                }
                row("$id-selection-replacement") {
                    reset(); focus(id, "A😀B", 1, 3)
                    assertEquals(RendererEditorAdapter.Status.APPLIED, edit(grant(), EditorOperation.Insert("X"), "replace-$id").status)
                    assertTrue(unchanged(id, "AXB"))
                }
                row("$id-selection-deletion") {
                    reset(); focus(id, "A😀B", 1, 3)
                    assertEquals(RendererEditorAdapter.Status.APPLIED, edit(grant(), EditorOperation.Backspace, "selected-delete-$id").status)
                    assertTrue(unchanged(id, "AB"))
                }
                row("$id-codepoint-deletion") {
                    reset(); focus(id, "A😀B", 3)
                    assertEquals(RendererEditorAdapter.Status.APPLIED, edit(grant(), EditorOperation.Backspace, "codepoint-$id").status)
                    assertTrue(unchanged(id, "AB"))
                }
                row("$id-empty-and-beginning") {
                    reset(); focus(id)
                    assertEquals(RendererEditorAdapter.Status.NO_CHANGE, edit(grant(), EditorOperation.Backspace, "empty-$id").status)
                    focus(id, "A", 0)
                    assertEquals(RendererEditorAdapter.Status.NO_CHANGE, edit(grant(), EditorOperation.Backspace, "begin-$id").status)
                    assertTrue(unchanged(id, "A"))
                }
            }
            for (id in listOf("t", "c")) row("$id-multiline-enter") {
                reset(); focus(id, "AB", 1)
                val g = grant()
                val inserted = edit(g, EditorOperation.Enter, "line-break-$id")
                assertEquals(RendererEditorAdapter.Status.APPLIED, inserted.status)
                assertTrue(if (id == "t") unchanged(id, "A\nB") else observeEffect("document.getElementById('c').innerText==='A\\nB'"))
                assertTrue(truth("submits===0"))
                assertTrue(truth("events.filter(e=>e.type==='input'&&e.inputType==='insertLineBreak'&&e.dataMatches).length===1"))
                assertEquals(RendererEditorAdapter.Status.APPLIED, edit(g.copy(revision = inserted.revision!!), EditorOperation.Backspace, "delete-line-break-$id").status)
                assertTrue(unchanged(id, "AB"))
            }
            for (id in listOf("a", "p")) row("$id-enter-default-click-and-submit") {
                reset(); focus(id, "Dummy9")
                assertEquals(RendererEditorAdapter.Status.SUBMISSION_REQUESTED, edit(grant(), EditorOperation.Enter, "submit-$id").status)
                assertTrue(observeEffect("submits===1 && clicks===1"))
            }
            row("disabled-default-no-fallback") {
                reset(); focus("a"); js("document.getElementById('send').disabled=true;true")
                assertEquals(RendererEditorAdapter.Status.NO_CHANGE, edit(grant(), EditorOperation.Enter, "disabled-submit").status)
                assertTrue(truth("submits===0 && clicks===0"))
            }
            row("no-default-implicit-submission-rules") {
                reset(); focus("a"); js("document.getElementById('send').remove();true")
                assertEquals(RendererEditorAdapter.Status.NO_CHANGE, edit(grant(), EditorOperation.Enter, "two-blocking").status)
                assertTrue(truth("submits===0"))
                js("document.getElementById('p').remove();true")
                assertEquals(RendererEditorAdapter.Status.SUBMISSION_REQUESTED, edit(grant(), EditorOperation.Enter, "one-blocking").status)
                assertTrue(observeEffect("submits===1"))
            }
            row("required-pattern-and-maxlength") {
                reset(); focus("a"); js("document.getElementById('a').required=true;document.getElementById('a').pattern='[0-9]+';true")
                assertEquals(RendererEditorAdapter.Status.NO_CHANGE, edit(grant(), EditorOperation.Enter, "required").status)
                focus("a", "X")
                assertEquals(RendererEditorAdapter.Status.NO_CHANGE, edit(grant(), EditorOperation.Enter, "pattern").status)
                assertTrue(truth("submits===0"))
                js("document.getElementById('a').maxLength=1;true")
                assertEquals(RendererEditorAdapter.Status.NO_CHANGE, edit(grant(), EditorOperation.Insert("Y"), "maxlength").status)
                assertTrue(unchanged("a", "X"))
            }
            row("done-no-submit-and-old-close-isolation") {
                reset(); focus("a", "kept"); val a = grant()
                assertEquals(RendererEditorAdapter.Status.REVOKED, call("done") { adapter.revoke(instance, a.target.token, ++order, callback = it) }.status)
                assertTrue(unchanged("a", "kept")); assertTrue(truth("submits===0"))
                focus("b"); val b = grant()
                call("old-close") { adapter.revoke(instance, a.target.token, order - 1, callback = it) }
                assertEquals(RendererEditorAdapter.Status.APPLIED, edit(b, EditorOperation.Insert("B"), "successor").status)
            }
            isolationCases()
            reentrantCases()
            replayAndOrdering()
            profileAndDocumentCases(scenario, url, original!!)
            rendererReplacement(scenario, url)
            controllerLifecycleCases(scenario)
            phoneImeAndDisplayState(scenario, automation)
            Log.i(TAG, "SUBSTRATE_MATRIX_PASS directAdapter=true rgKeyboardOrTlsClaim=false")
        } catch (error: Throwable) {
            primary = error; Log.e(TAG, "SUBSTRATE_MATRIX_FAIL type=${error.javaClass.simpleName} assertion=${error.message}"); throw error
        } finally {
            val failures = listOf<() -> Unit>(
                { if (::adapter.isInitialized && ::instance.isInitialized) revokeCurrent() },
                { if (browser.isLive() && browser.displayUrl()?.contains("case=$mission") == true) js("resetFields();true") },
                { scenario.moveToState(Lifecycle.State.RESUMED) },
                { main { lease?.release(); host.stop(); link.stop() } },
                { scenario.close() },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if (primary != null) failures.forEach { primary!!.addSuppressed(it) } else if (failures.isNotEmpty()) throw failures.first()
        }
    }

    private fun isolationCases() {
        val stimuli = linkedMapOf(
            "focus-A-B" to "document.getElementById('b').focus()",
            "focus-A-B-A" to "document.getElementById('b').focus();a.focus()",
            "same-id-replacement" to "a.replaceWith(a.cloneNode(true));document.getElementById('a').focus()",
            "detach-reinsert" to "const parent=a.parentNode;a.remove();parent.prepend(a);a.focus()",
            "readonly-ABA" to "a.readOnly=true;a.readOnly=false",
            "disabled-ABA" to "a.disabled=true;a.disabled=false;a.focus()",
            "type-ABA" to "a.type='password';a.type='text'",
            "selection-change" to "a.setSelectionRange(0,0)",
        )
        for ((name, stimulus) in stimuli) row("pending-observer-$name") {
            reset(); focus("a", "sentinel"); val g = grant()
            val result = rawEdit(g, "const a=document.getElementById('a');$stimulus;", ++sequence)
            assertEquals("STALE_EDITOR", result.getString("status"))
            assertTrue(unchanged("a", "sentinel")); assertTrue(unchanged("b", ""))
        }
        for ((name, stimulus) in linkedMapOf(
            "rich-host" to "const c=document.getElementById('c');c.innerHTML='<b>rich</b>';c.focus()",
            "shadow-host" to "const h=document.createElement('div');document.body.append(h);const s=h.attachShadow({mode:'open'});s.innerHTML='<input>';s.firstChild.focus()",
            "frame-host" to "const f=document.createElement('iframe');document.body.append(f);f.contentDocument.body.innerHTML='<input>';f.contentDocument.querySelector('input').focus()",
        )) row("unsupported-$name") {
            reset(); js("(()=>{$stimulus;return true})()")
            val opening = PhoneEditorAuthority.Opening(UUID.randomUUID().toString(), context, 1, ++order)
            assertEquals(RendererEditorAdapter.Status.UNSUPPORTED, call("unsupported") { adapter.grant(instance, opening, callback = it) }.status)
            js("document.querySelectorAll('iframe').forEach(e=>e.remove());true")
        }
    }

    private fun reentrantCases() {
        val handlers = linkedMapOf(
            "cancel" to "event.preventDefault()",
            "refocus" to "document.getElementById('b').focus()",
            "replace" to "a.replaceWith(a.cloneNode(true));document.getElementById('a').focus()",
            "readonly" to "a.readOnly=true",
            "disable" to "a.disabled=true",
            "type" to "a.type='password'",
            "selection" to "a.setSelectionRange(0,0)",
        )
        for ((name, body) in handlers) row("beforeinput-$name") {
            reset(); focus("a", "safe"); val g = grant()
            js("document.getElementById('a').addEventListener('beforeinput',function(event){const a=this;$body},{once:true});true")
            val result = edit(g, EditorOperation.Insert("Q"), "beforeinput-$name")
            assertEquals(if (name == "cancel") RendererEditorAdapter.Status.CANCELLED else RendererEditorAdapter.Status.STALE_EDITOR, result.status)
            assertTrue(unchanged("a", "safe")); assertTrue(unchanged("b", ""))
            assertTrue(truth("events.filter(e=>e.type==='input').length===0"))
        }
        row("input-refocus-after-one-original-mutation") {
            reset(); focus("a"); val g = grant()
            js("document.getElementById('a').addEventListener('input',()=>document.getElementById('b').focus(),{once:true});true")
            val result = edit(g, EditorOperation.Insert("Q"), "after-input")
            assertEquals(RendererEditorAdapter.Status.APPLIED, result.status); assertFalse(result.ready)
            assertTrue(unchanged("a", "Q")); assertTrue(unchanged("b", ""))
            assertTrue(truth("events.filter(e=>e.type==='input').length===1"))
        }
        row("plain-range-change-during-beforeinput-rejects") {
            reset(); focus("c", "AB", 1); val g = grant()
            js("document.getElementById('c').addEventListener('beforeinput',()=>{const s=getSelection();s.collapse(document.getElementById('c').firstChild,0)},{once:true});true")
            assertEquals(RendererEditorAdapter.Status.STALE_EDITOR, edit(g, EditorOperation.Insert("Q"), "range-change").status)
            assertTrue(unchanged("c", "AB")); assertTrue(unchanged("b", ""))
        }
        row("enter-click-refocus-cancels-adapter-submit") {
            reset(); focus("a"); val g = grant()
            js("document.getElementById('send').addEventListener('click',()=>document.getElementById('b').focus(),{once:true});true")
            assertEquals(RendererEditorAdapter.Status.CANCELLED, edit(g, EditorOperation.Enter, "submit-refocus").status)
            assertTrue(truth("submits===0"))
            assertTrue(truth("submitEvents===1")) // Cancelled submit notification, no allowed default.
        }
    }

    private fun replayAndOrdering() {
        row("applied-before-focus-one-original-effect") {
            reset(); focus("a"); val g = grant()
            assertEquals(RendererEditorAdapter.Status.APPLIED, edit(g, EditorOperation.Insert("A"), "first-order").status)
            js("document.getElementById('b').focus();true")
            assertTrue(unchanged("a", "A")); assertTrue(unchanged("b", ""))
        }
        row("admitted-before-focus-executes-after-focus-rejects") {
            reset(); focus("a"); val g = grant(); val msg = message(g, EditorOperation.Insert("Q"))
            assertTrue(truth("document.activeElement===document.getElementById('a')"))
            // Bound renderer performance.now to an Android elapsedRealtime request/reply interval.
            // Include 1ms endpoint quantization; do not subtract uncorrelated clocks.
            val sent = SystemClock.elapsedRealtime()
            val rendererNow = js("performance.now()").toDouble()
            val received = SystemClock.elapsedRealtime()
            val offsetLow = sent - rendererNow - 1
            val offsetHigh = received - rendererNow + 1
            var nativeQueued = 0L
            val result = call("queued-conflict") { callback ->
                browser.view()!!.evaluateJavascript("(()=>{window.raceTimes={start:performance.now()};while(performance.now()-raceTimes.start<300){};raceTimes.focus=performance.now();document.getElementById('b').focus();return true})()", null)
                adapter.edit(g, msg, callback)
                nativeQueued = SystemClock.elapsedRealtime()
            }
            val focusIssued = js("raceTimes.focus").toDouble()
            val margin = focusIssued + offsetLow - nativeQueued
            Log.i(TAG, "RACE nativeQueued=$nativeQueued rendererFocus=$focusIssued offsetLow=$offsetLow offsetHigh=$offsetHigh conservativeAdmissionBeforeFocusMs=$margin")
            assertTrue("INVALID_SETUP: original-A enqueue must precede earliest possible B-focus issue", margin > 0)
            assertEquals(RendererEditorAdapter.Status.STALE_EDITOR, result.status)
            assertTrue(unchanged("a", "")); assertTrue(unchanged("b", ""))
        }
        row("exact-replay-and-cancelled-ordinal-consumed") {
            reset(); focus("a"); val g = grant(); val n = ++sequence
            js("document.getElementById('a').addEventListener('beforeinput',e=>e.preventDefault(),{once:true});true")
            assertEquals(RendererEditorAdapter.Status.CANCELLED, edit(g, EditorOperation.Insert("Q"), "cancel-consume", n).status)
            assertEquals(RendererEditorAdapter.Status.REPLAY, edit(g, EditorOperation.Insert("Q"), "replay-cancel", n).status)
            assertTrue(unchanged("a", ""))
        }
        row("wrong-editor-rejection-consumes-current-ordinal") {
            reset(); focus("a"); val g = grant(); val n = ++sequence
            val payload = packet(g, n).put("token", "retired-token").toString()
            val raw = js("window.__eyebrowseEditorV1.request(${JSONObject.quote(payload)})")
            assertEquals("STALE_EDITOR", JSONObject(JSONTokener(raw).nextValue() as String).getString("status"))
            assertEquals(RendererEditorAdapter.Status.REPLAY, edit(g, EditorOperation.Insert("Q"), "consumed-current-rejection", n).status)
            assertTrue(unchanged("a", ""))
        }
        row("stale-full-context-does-not-consume-current-ordinal") {
            reset(); focus("a"); val g = grant(); val n = ++sequence
            val payload = packet(g, n); val stale = g.opening.context.copy(viewportEpoch = g.opening.context.viewportEpoch + 1)
            payload.getJSONObject("context").put("viewportEpoch", stale.viewportEpoch.toString())
            payload.put("commandId", BrowserCommandId.create(stale, n))
            val raw = js("window.__eyebrowseEditorV1.request(${JSONObject.quote(payload.toString())})")
            assertEquals("STALE_CONTEXT", JSONObject(JSONTokener(raw).nextValue() as String).getString("status"))
            assertEquals(RendererEditorAdapter.Status.APPLIED, edit(g, EditorOperation.Insert("Q"), "current-context-same-ordinal", n).status)
            assertTrue(unchanged("a", "Q"))
        }
        row("discarded-result-never-replays-an-effect") {
            reset(); focus("a"); val g = grant(); val n = ++sequence
            edit(g, EditorOperation.Insert("Q"), "discarded-result", n)
            assertEquals(RendererEditorAdapter.Status.REPLAY, edit(g, EditorOperation.Insert("Q"), "lost-result-replay", n).status)
            assertTrue(unchanged("a", "Q")); assertTrue(truth("events.filter(e=>e.type==='input').length===1"))
        }
        row("late-grant-cannot-overtake-revocation-barrier") {
            reset(); focus("a"); val g = grant()
            call("retire-order") { adapter.revoke(instance, g.target.token, ++order, callback = it) }
            assertEquals(RendererEditorAdapter.Status.STALE_EDITOR, call("late-open") { adapter.grant(instance, g.opening, callback = it) }.status)
            assertTrue(unchanged("a", "")); assertFalse(call("not-ready", adapter::inspect).ready)
        }
        row("nested-dispatch-is-busy-and-consumes-ordinal") {
            reset(); focus("a"); val g = grant(); val outer = ++sequence; val nested = ++sequence
            val payload = packet(g, nested).toString()
            js("document.getElementById('a').addEventListener('beforeinput',()=>{window.nestedStatus=JSON.parse(window.__eyebrowseEditorV1.request(${JSONObject.quote(payload)})).status},{once:true});true")
            assertEquals(RendererEditorAdapter.Status.APPLIED, edit(g, EditorOperation.Insert("Q"), "outer", outer).status)
            assertTrue(truth("nestedStatus==='BUSY'")); assertTrue(unchanged("a", "Q"))
            assertEquals(RendererEditorAdapter.Status.REPLAY, edit(g.copy(revision = 1), EditorOperation.Insert("Q"), "nested-replay", nested).status)
        }
        row("long64-context-and-ordinal-no-rounding") {
            reset(); focus("a"); val previous = context
            context = ControlContext("large-identity", Long.MAX_VALUE, browser.documentIdentity(), Long.MAX_VALUE-1, Long.MAX_VALUE-2)
            val g = grant()
            val result = edit(g, EditorOperation.Insert("Q"), "long64", Long.MAX_VALUE)
            assertEquals(RendererEditorAdapter.Status.APPLIED, result.status)
            assertEquals(BrowserCommandId.create(context, Long.MAX_VALUE), result.commandId)
            assertTrue(unchanged("a", "Q"))
            assertEquals(RendererEditorAdapter.Status.REPLAY, edit(g, EditorOperation.Insert("Q"), "long64-replay", Long.MAX_VALUE-1).status)
            context = previous.copy(lifetimeId = previous.lifetimeId + "-next")
        }
        row("malformed-oversized-and-data-not-source") {
            reset(); focus("a"); var g = grant()

            // RAW SYNTAX ERROR: JSON.parse throws inside RendererEditor.request(). The catch calls
            // invalidate() and returns UNCERTAIN so exception/payload details never escape.
            val malformedSyntax = js(
                "window.__eyebrowseEditorV1.request(" + JSONObject.quote("{") + ")"
            )
            assertEquals(
                "UNCERTAIN",
                JSONObject(JSONTokener(malformedSyntax).nextValue() as String)
                    .getString("status"),
            )
            assertFalse(
                "syntax-error UNCERTAIN retires the current renderer grant",
                call("syntax-error-retired-grant", adapter::inspect).ready,
            )

            // C12A's observed STALE_EDITOR is correct in this lifecycle state:
            // edit() validates context/sequence/id, then finds no current grant and returns
            // STALE_EDITOR before it can inspect the unsupported action field. This stale packet
            // does not advance highWater because that return precedes replay reservation.
            val retiredInvalidAction =
                packet(g, ++sequence).put("action", "EXEC").toString()
            val retiredInvalidActionRaw = js(
                "window.__eyebrowseEditorV1.request(" +
                    JSONObject.quote(retiredInvalidAction) + ")"
            )
            assertEquals(
                "STALE_EDITOR",
                JSONObject(JSONTokener(retiredInvalidActionRaw).nextValue() as String)
                    .getString("status"),
            )

            // Re-establish the editor explicitly. Without this precondition, every valid edit
            // packet below must classify STALE_EDITOR at edit(): grant ?: return, before its
            // operation field can be inspected.
            focus("a")
            g = grant()

            // OVERSIZE: the >32768 guard runs before JSON.parse/reconcile and returns definite
            // MALFORMED without invalidating the fresh current grant.
            val oversized = " ".repeat(32769)
            val oversizedRaw = js(
                "window.__eyebrowseEditorV1.request(" + JSONObject.quote(oversized) + ")"
            )
            assertEquals(
                "MALFORMED",
                JSONObject(JSONTokener(oversizedRaw).nextValue() as String)
                    .getString("status"),
            )
            assertTrue(
                "oversized pre-parse rejection preserves the fresh renderer grant",
                call("oversized-preserves-grant", adapter::inspect).ready,
            )

            // VALID JSON / INVALID OPERATION: context, sequence, commandId, current grant,
            // target identity and validity all pass first. edit() then reaches
            // operation !in [INSERT,BACKSPACE,ENTER] and returns definite MALFORMED.
            // That definitive current-context rejection consumes this ordinal but does not
            // invalidate the grant or increment its revision.
            val invalidAction = packet(g, ++sequence).put("action", "EXEC").toString()
            val invalidActionRaw = js(
                "window.__eyebrowseEditorV1.request(" + JSONObject.quote(invalidAction) + ")"
            )
            assertEquals(
                "MALFORMED",
                JSONObject(JSONTokener(invalidActionRaw).nextValue() as String)
                    .getString("status"),
            )
            assertTrue(
                "unsupported-action MALFORMED leaves the current grant ready",
                call("invalid-action-preserves-grant", adapter::inspect).ready,
            )

            // DATA-NOT-SOURCE: adapter.edit() serializes this string as JSON data. The preceding
            // invalid action consumed only its ordinal, so the next ordinal remains admissible.
            val text = "');window.forbidden=true;//\"\\\u2028"
            assertEquals(
                RendererEditorAdapter.Status.APPLIED,
                edit(g, EditorOperation.Insert(text), "escaped-data").status,
            )
            assertTrue(unchanged("a", text))
            assertTrue(truth("window.forbidden!==true"))
        }
    }

    private fun focusTransitionCases() {
        for ((a,b) in listOf("a" to "b","p" to "p2","t" to "t2","c" to "c2")) {
            for (method in listOf("cached","bound","prototype","neutral","body")) row("focus-history-$a-$method") {
                focus(a,"AB",1)
                js("(()=>{const b=document.getElementById('$b');if(b.isContentEditable)b.textContent='';else b.value='';return true})()")
                val g=grant()
                js("window.events=[];window.eventTrace=[];true")
                val spec=JSONObject().put("a",a).put("b",b).put("method",method).put("instance",instance)
                    .put("packet",packet(g,++sequence).toString())
                val observed=JSONObject(JSONTokener(js("focusHistoryCase(${JSONObject.quote(spec.toString())})")).nextValue() as String)
                Log.i(TAG,"FOCUS_HISTORY metadata=$observed")
                val c=observed.getJSONObject("cycle")
                assertTrue("actual ABA setup",c.getBoolean("middleReached")&&c.getBoolean("sameNode")&&c.getBoolean("sameType")&&c.getBoolean("sameSelection"))
                if(a=="c"&&method=="cached")assertTrue("exact saved-Range counterexample",c.getBoolean("sameRange"))
                assertTrue("retired at departure",c.getBoolean("retiredAtDeparture"))
                assertEquals("STALE_EDITOR",observed.getString("status"))
                assertTrue(observed.getBoolean("originalUnchanged")&&observed.getBoolean("successorUntouched"))
                assertEquals(0,observed.getInt("inputCount"));assertEquals(0,observed.getInt("changeCount"))
            }
            row("focus-history-$a-beforeinput") {
                focus(a,"AB",1)
                js("(()=>{const b=document.getElementById('$b');if(b.isContentEditable)b.textContent='';else b.value='';return true})()")
                val g=grant();js("window.events=[];window.eventTrace=[];true")
                val spec=JSONObject().put("a",a).put("b",b).put("method","cached").put("instance",instance)
                    .put("beforeinput",true).put("packet",packet(g,++sequence).toString())
                val observed=JSONObject(JSONTokener(js("focusHistoryCase(${JSONObject.quote(spec.toString())})")).nextValue() as String)
                Log.i(TAG,"BEFOREINPUT_HISTORY metadata=$observed")
                val c=observed.getJSONObject("cycle")
                assertTrue(c.getBoolean("middleReached")&&c.getBoolean("sameNode")&&c.getBoolean("sameSelection")&&c.getBoolean("retiredAtDeparture"))
                assertEquals("STALE_EDITOR",observed.getString("status"))
                assertTrue(observed.getBoolean("originalUnchanged")&&observed.getBoolean("successorUntouched"))
                assertEquals(0,observed.getInt("inputCount"));assertEquals(0,observed.getInt("changeCount"))
            }
        }
        row("clean-focus-move-does-not-invent-change") {
            reset();focus("a");grant();js("window.events=[];document.getElementById('b').focus();true")
            assertTrue(truth("events.filter(e=>e.type==='change').length===0"))
        }
        row("sustained-typing-survives-secondary-reconciliation") {
            reset();focus("a");var g=grant();var expected=""
            repeat(20) {
                val result=edit(g,EditorOperation.Insert("Q"),"sustained-$it")
                assertEquals(RendererEditorAdapter.Status.APPLIED,result.status)
                expected+="Q";assertTrue(unchanged("a",expected));g=g.copy(revision=result.revision!!)
                SystemClock.sleep(60) // Deliberately cross the secondary50ms observation interval.
            }
            assertTrue(truth("events.filter(e=>e.type==='change').length===0"))
        }
        row("dirty-change-refocus-reentry-notifies-once-and-retires-old-token") {
            reset();focus("a","AB",1);var g=grant()
            val done=edit(g,EditorOperation.Insert("X"),"dirty-refocus");assertEquals(RendererEditorAdapter.Status.APPLIED,done.status)
            g=g.copy(revision=done.revision!!)
            js("document.getElementById('a').addEventListener('change',()=>{document.getElementById('a').focus();window.__eyebrowseEditorV1.request(JSON.stringify({op:'inspect'}));},{once:true});document.getElementById('b').focus();true")
            assertTrue(truth("events.filter(e=>e.type==='change'&&e.target==='a').length===1"))
            assertEquals(RendererEditorAdapter.Status.STALE_EDITOR,edit(g,EditorOperation.Insert("Q"),"old-after-refocus").status)
            assertTrue(unchanged("a","AXB"));assertTrue(unchanged("b",""))
        }
        row("late-old-node-observer-cannot-retire-successor-or-repeat-change") {
            reset();focus("a");val a=grant();assertEquals(RendererEditorAdapter.Status.APPLIED,edit(a,EditorOperation.Insert("X"),"dirty-old").status)
            focus("b");val b=grant()
            js("document.getElementById('a').setAttribute('readonly','');true")
            call("repeated-reconcile",adapter::inspect);call("repeated-reconcile2",adapter::inspect)
            assertEquals(RendererEditorAdapter.Status.APPLIED,edit(b,EditorOperation.Insert("Q"),"fresh-successor").status)
            assertTrue(unchanged("b","Q"));assertTrue(truth("events.filter(e=>e.type==='change'&&e.target==='a').length===1"))
        }
    }

    private fun profileAndDocumentCases(scenario: ActivityScenario<MainActivity>, url: String, original: WebView) {
        row("same-target-and-live-value-across-shrink-grow") {
            reset(); focus("a", "retained"); var g = grant()
            for (height in listOf(240, 344)) {
                call("profile-revoke") { adapter.revoke(instance, g.target.token, ++order, true, it) }
                main { lease?.release(); lease = null }
                await("capture drain before profile") { !host.captureResourcesPresent() }
                context = context.copy(viewportEpoch = context.viewportEpoch + 1, hostingGeneration = host.status().generation.toLong())
                val opening = PhoneEditorAuthority.Opening(UUID.randomUUID().toString(), context, 1, ++order)
                val rebound = java.util.concurrent.atomic.AtomicReference<RendererEditorAdapter.Result>()
                val early = java.util.concurrent.atomic.AtomicBoolean()
                val frames = java.util.concurrent.atomic.AtomicInteger()
                val degraded = java.util.concurrent.atomic.AtomicReference<String>()
                lateinit var publisher: PhonePresentationPublisher
                val profile = com.code2hack.eyebrowse.core.link.presentation.PresentationProfile(480, height, 204)
                val requestAt = SystemClock.elapsedRealtime()
                main {
                    publisher = PhonePresentationPublisher(host, {
                        if (rebound.get()?.status != RendererEditorAdapter.Status.READY) early.set(true)
                        true
                    }, { frame ->
                        if (frame.header.context != context || frame.header.height != height) early.set(true)
                        frames.incrementAndGet(); true
                    }, { browser.requestFreshCaptureFrame() }, beforeCapture = { _, capture ->
                        adapter.grant(instance, opening, previousTarget = g.target) { result -> rebound.set(result); capture() }
                    }, degraded = { _, reason -> degraded.set(reason) })
                    publisher.reconcile(ControlSnapshot(context, ControlOwner.RG, profile, true, PresentationStatus.STALE, true, true))
                }
                try {
                    await("rebound editor precedes matching fresh profile", 2000) { frames.get() > 0 }
                    val elapsed = SystemClock.elapsedRealtime() - requestAt
                    Log.i(TAG, "PROFILE_READY height=$height nativeRequestToObservedFrameMs=$elapsed editorBeforeCapture=${!early.get()}")
                    assertTrue(elapsed <= 2000); assertFalse(early.get()); assertNull(degraded.get())
                } finally { main { publisher.stop() } }
                val result = rebound.get()!!
                assertEquals(RendererEditorAdapter.Status.READY, result.status)
                g = PhoneEditorAuthority.Grant(opening, EditorTarget(result.token!!, result.generation!!), instance, result.revision!!, result.kind!!, result.enter!!)
                assertTrue(unchanged("a", "retained")); main { assertSame(original, browser.view()) }
                await("profile producer retired") { !host.captureResourcesPresent() }
                main { lease = host.acquireLease { } }
                assertNotNull(lease)
            }
        }
        row("profile-rebind-cannot-adopt-replacement-editor") {
            reset(); focus("a"); val g = grant()
            call("retain-profile-target") { adapter.revoke(instance, g.target.token, ++order, true, it) }
            js("const a=document.getElementById('a');a.replaceWith(a.cloneNode(true));document.getElementById('a').focus();true")
            val opening = PhoneEditorAuthority.Opening(UUID.randomUUID().toString(), context.copy(viewportEpoch = context.viewportEpoch + 1), 1, ++order)
            assertEquals(RendererEditorAdapter.Status.STALE_EDITOR, call("replacement-rebind") { adapter.grant(instance, opening, previousTarget = g.target, callback = it) }.status)
            assertTrue(unchanged("a", ""))
        }
        row("new-document-cannot-adopt-old-instance-or-grant") {
            reset(); focus("a"); val old = grant(); val oldInstance = instance
            main { browser.openAddress("$url&document=next") }
            await("new document", 10000) { !browser.isLoading() && browser.documentIdentity() != context.documentId }
            context = context.copy(documentId = browser.documentIdentity(), viewportEpoch = context.viewportEpoch + 1)
            instance = call("new-install", adapter::install).instance!!
            assertNotEquals(oldInstance, instance)
            assertEquals(RendererEditorAdapter.Status.STALE_DOCUMENT, edit(old, EditorOperation.Insert("Q"), "old-document").status)
            assertTrue(unchanged("a", "")); assertTrue(unchanged("b", ""))
        }
        row("phone-return-keeps-single-webview-and-no-old-edit") {
            revokeCurrent()
            main { lease?.release(); lease = null }
            scenario.moveToState(Lifecycle.State.RESUMED)
            main { assertTrue(host.presentOnPhone()); assertSame(original, browser.view()) }
        }
    }

    private fun revokeCurrent() {
        val current = call("cleanup-state", adapter::inspect)
        order = maxOf(order, current.lifecycleOrder ?: 0)
        if (current.instance != null) call("revoke-current") { adapter.revoke(current.instance, current.token, ++order, callback = it) }
    }

    private fun rendererReplacement(scenario: ActivityScenario<MainActivity>, url: String) {
        row("actual-renderer-replacement-rejects-old-grant") {
            reset(); focus("a"); val old = grant(); var oldView: WebView? = null
            main { oldView = browser.view(); assertTrue(oldView!!.webViewRenderProcess!!.terminate()) }
            await("owned renderer cleared", 5000) { !browser.isLive() && browser.view() == null }
            await("hosting ended after renderer loss", 5000) { host.status().state == HostingController.State.NOT_HOSTING }
            scenario.onActivity { browser.openAddress("$url&renderer=new") }
            await("explicit renderer recovery", 10000) { browser.isLive() && !browser.isLoading() && browser.pageTitle() == "I9 renderer fixture" }
            main { assertNotSame(oldView, browser.view()) }
            context = context.copy(documentId = browser.documentIdentity(), viewportEpoch = context.viewportEpoch + 1)
            instance = call("replacement-install", adapter::install).instance!!
            assertNotEquals(old.instance, instance)
            assertEquals(RendererEditorAdapter.Status.STALE_DOCUMENT, edit(old, EditorOperation.Insert("Q"), "dead-renderer-grant").status)
            assertTrue(unchanged("a", ""))
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("recovered hosting", 5000) { host.status().state == HostingController.State.HOSTING }
            main { assertTrue(host.presentOnRg(HostingPresentationProfile(480, 344, 204))); lease = host.acquireLease { } }
            scenario.moveToState(Lifecycle.State.CREATED)
        }
    }

    private fun controllerLifecycleCases(scenario: ActivityScenario<MainActivity>) {
        // Direct controller fixture, not authenticated RG admission. Uses the actual async
        // controller/adapter/Stop integration with an explicit local authority snapshot.
        var snapshot = ControlSnapshot(context, ControlOwner.RG,
            com.code2hack.eyebrowse.core.link.presentation.PresentationProfile(480, 344, 204),
            true, PresentationStatus.READY, true, true)
        var connection = 1L
        lateinit var controller: PhoneEditorController
        val saved = browser.remoteEditor
        main { controller = PhoneEditorController(app, browser, { snapshot }, { connection }, {}); browser.remoteEditor = controller }
        fun quiescent() { await("editor barrier", 3000) { var yes = false; main { yes = controller.isQuiescent() }; yes } }
        fun open(): PhoneEditorAuthority.Grant {
            reset(); focus("a")
            val rect = JSONObject(js("(()=>{const r=document.getElementById('a').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()"))
            val done = CountDownLatch(1); var success = false
            main {
                val view = browser.view()!!
                val activation = BrowserAction.ActivateAt((rect.getDouble("x")*view.scale).toFloat(), (rect.getDouble("y")*view.scale).toFloat())
                browser.executeRemoteAction(activation)
                controller.openAfterActivation(activation) { success = it; done.countDown() }
            }
            assertTrue("controller grant callback", done.await(2, TimeUnit.SECONDS)); assertTrue("controller granted", success)
            return controller.authority.grant!!
        }
        try {
            await("fixed asset loaded off main") { var ready = false; main { ready = controller.isAvailable() }; ready }
            // Direct probe lifecycle orders and production controller orders must not alias.
            // Use a fresh document for the production controller's independent lifecycle instance.
            val currentUrl = browser.displayUrl()!!
            main { browser.openAddress("$currentUrl&controller=fresh") }
            await("controller fixture document", 10000) { !browser.isLoading() && browser.documentIdentity() != context.documentId }
            context = context.copy(documentId = browser.documentIdentity(), viewportEpoch = context.viewportEpoch + 1)
            snapshot = snapshot.copy(context = context)
            instance = call("controller-document-install", adapter::install).instance!!
            order = 0
            row("cancelled-open-callback-cannot-retire-successor") {
                reset(); focus("a")
                val rect = JSONObject(js("(()=>{const r=document.getElementById('a').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()"))
                val done = CountDownLatch(2); var first: Boolean? = null; var second: Boolean? = null
                main {
                    val view = browser.view()!!
                    val activation = BrowserAction.ActivateAt((rect.getDouble("x")*view.scale).toFloat(), (rect.getDouble("y")*view.scale).toFloat())
                    browser.executeRemoteAction(activation)
                    controller.openAfterActivation(activation) { first = it; done.countDown() }
                    controller.close { verified ->
                        assertTrue(verified)
                        controller.openAfterActivation(activation) { second = it; done.countDown() }
                    }
                }
                assertTrue("both original and successor callbacks resolve", done.await(3, TimeUnit.SECONDS))
                assertEquals(false, first); assertEquals(true, second)
                SystemClock.sleep(1100) // Pass the retired opening's deadline; no new input is queued.
                main { lease?.renew(); assertEquals(PhoneEditorAuthority.Phase.READY, controller.authority.phase); controller.close { } }
                quiescent()
            }
            row("controller-local-focus-loss-retires-before-regain") {
                val old=open();var focusProbe:android.view.View?=null
                try {
                    main {
                        val view=browser.view()!!;val parent=view.parent as android.view.ViewGroup
                        val probe=android.view.View(parent.context).apply {isFocusableInTouchMode=true}
                        focusProbe=probe;parent.addView(probe,android.view.ViewGroup.LayoutParams(1,1));assertTrue(probe.requestFocus())
                        assertFalse(host.localEditorFocusReady())
                        assertEquals(PhoneEditorAuthority.Phase.REVOKING,controller.authority.phase)
                        assertFalse(controller.stateMessage().ready)
                        parent.removeView(probe);assertTrue(view.requestFocus())
                    }
                    quiescent()
                    assertEquals(PhoneEditorAuthority.Phase.EMPTY,controller.authority.phase)
                    assertFalse(controller.stateMessage().ready)
                    assertEquals(RendererEditorAdapter.Status.STALE_EDITOR,edit(old,EditorOperation.Insert("Q"),"after-focus-regain").status)
                    assertTrue(unchanged("a",""));val fresh=open();assertNotEquals(old.target,fresh.target)
                    main {controller.close { }};quiescent()
                } finally {main {focusProbe?.let{(it.parent as? android.view.ViewGroup)?.removeView(it)}}}
            }
            for (loss in listOf("owner", "link", "connection")) row("controller-$loss-loss") {
                val g = open()
                main {
                    when (loss) {
                        "owner" -> snapshot = snapshot.copy(owner = ControlOwner.PHONE)
                        "link" -> snapshot = snapshot.copy(linkAuthenticated = false)
                        else -> connection++
                    }
                    controller.reconcile()
                }
                quiescent()
                assertEquals(RendererEditorAdapter.Status.STALE_EDITOR, edit(g, EditorOperation.Insert("Q"), "retired-$loss").status)
                assertTrue(unchanged("a", "")); assertTrue(unchanged("b", ""))
                snapshot = snapshot.copy(owner = ControlOwner.RG, linkAuthenticated = true)
            }
            row("controller-timeout-late-result-no-retry") {
                val g = open(); val completed = CountDownLatch(1); var count = 0; var reason: String? = null
                main {
                    browser.view()!!.evaluateJavascript("(()=>{const until=performance.now()+1300;while(performance.now()<until){};return true})()", null)
                    controller.execute(message(g, EditorOperation.Insert("Q"))) { count++; reason = it.reason; completed.countDown() }
                }
                assertTrue(completed.await(2, TimeUnit.SECONDS)); assertEquals("EDITOR_UNCERTAIN", reason)
                quiescent()
                assertEquals(1, count)
                assertTrue(truth("events.filter(e=>e.type==='input').length<=1")); assertTrue(unchanged("b", ""))
            }
            row("normal-stop-awaits-editor-retirement") {
                open()
                main { host.stop() }
                await("Stop complete", 5000) { host.status().state == HostingController.State.NOT_HOSTING }
                quiescent(); assertFalse(host.isRgPresentationOwned()); assertFalse(host.captureResourcesPresent()); assertFalse(host.hasDisplayResources())
            }
        } finally {
            main { controller.close { } }
            quiescent()
            main { browser.remoteEditor = saved }
        }
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    private fun phoneImeAndDisplayState(scenario: ActivityScenario<MainActivity>, automation: android.app.UiAutomation) {
        row("ordinary-phone-ime-native-edit-after-return") {
            reset(); focus("a")
            val imeBefore = android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)
            val manager = app.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            main { val view = browser.view()!!; view.requestFocus(); manager.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
            await("ordinary Phone IME visible") { automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_Q)
            await("native Phone edit") { unchanged("a", "q") }
            assertEquals(imeBefore, android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD))
            main { manager.hideSoftInputFromWindow(browser.view()!!.windowToken, 0) }
        }
        row("fresh-private-grant-after-native-phone-edit") {
            val before = browser.documentIdentity(); val sameView = browser.view()
            val current = call("fixture-order-after-controller", adapter::inspect)
            order = maxOf(order, current.lifecycleOrder ?: 0)
            context = context.copy(viewportEpoch = context.viewportEpoch + 1)
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("final hosting", 5000) { host.status().state == HostingController.State.HOSTING }
            main { assertTrue(host.presentOnRg(HostingPresentationProfile(480, 344, 204))); lease = host.acquireLease { } }
            scenario.moveToState(Lifecycle.State.CREATED)
            assertEquals(before, browser.documentIdentity()); assertSame(sameView, browser.view()); assertTrue(unchanged("a", "q"))
            js("const a=document.getElementById('a');a.focus();a.setSelectionRange(a.value.length,a.value.length);true")
            val g = grant()
            assertEquals(RendererEditorAdapter.Status.APPLIED, edit(g, EditorOperation.Insert("R"), "fresh-after-native").status)
            assertTrue(unchanged("a", "qR"))
        }
        if (InstrumentationRegistry.getArguments().getString("displayOffRecoveryVerified") != "true") {
            // Plan section10: do not deliberately strand the only management transport.
            Log.i(TAG, "ROW_NOT_EXERCISED name=actual-display-off-private-edit reason=NO_VERIFIED_RECOVERY_TRANSPORT")
        } else row("actual-display-off-private-edit") {
            reset(); focus("a"); val g = grant()
            val power = app.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val keyguard = app.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            automation.executeShellCommand("input keyevent KEYCODE_SLEEP").use { }
            await("display off") { !power.isInteractive }
            Log.i(TAG, "DEVICE_STATE interactive=${power.isInteractive} locked=${keyguard.isDeviceLocked}")
            assertEquals(RendererEditorAdapter.Status.APPLIED, edit(g, EditorOperation.Insert("Q"), "display-off").status)
            assertTrue(unchanged("a", "Q")); assertFalse(power.isInteractive)
            Log.i(TAG, "DEVICE_STATE_AFTER interactive=${power.isInteractive} locked=${keyguard.isDeviceLocked}")
        }
    }

    /** Negative renderer-boundary packet only; never an RG wire bypass or positive keyboard path. */
    private fun packet(g: PhoneEditorAuthority.Grant, n: Long): JSONObject {
        val c = g.opening.context
        return JSONObject().put("op", "edit").put("instance", g.instance).put("token", g.target.token)
            .put("generation", g.target.focusGeneration.toString()).put("revision", g.revision.toString())
            .put("sequence", n.toString()).put("commandId", BrowserCommandId.create(c, n)).put("action", "INSERT").put("text", "Q")
            .put("context", JSONObject().put("lifetimeId", c.lifetimeId).put("controlEpoch", c.controlEpoch.toString())
                .put("documentId", c.documentId).put("viewportEpoch", c.viewportEpoch.toString()).put("hostingGeneration", c.hostingGeneration?.toString() ?: "n"))
    }
    private fun rawEdit(g: PhoneEditorAuthority.Grant, stimulus: String, n: Long): JSONObject {
        val payload = JSONObject.quote(packet(g, n).toString())
        val raw = js("(()=>{$stimulus return window.__eyebrowseEditorV1.request($payload)})()")
        return JSONObject(JSONTokener(raw).nextValue() as String)
    }
    companion object { private const val TAG = "EyeBrowseI9Editor" }
}
