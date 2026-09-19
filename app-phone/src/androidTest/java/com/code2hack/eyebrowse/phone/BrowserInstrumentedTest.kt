package com.code2hack.eyebrowse.phone

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not as notMatcher
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier
import java.util.function.LongSupplier

/**
 * Instrumentation checks of the Phone browser against the local fixture server.
 *
 * Behavior/evidence boundaries are unchanged from the Java source: page reads use
 * WebView.evaluateJavascript; activation uses Espresso's pinned event construction/precision but
 * submits synthetic pointer events once through Instrumentation (not human touch/IME evidence);
 * fixture field setup uses page JavaScript; and simulated process restart is not physical
 * process-death evidence. Dispatch.stage tracks API-call progress, not proof of input delivery.
 */
@RunWith(AndroidJUnit4::class)
class BrowserInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var session: PhoneBrowserSession
    private val diagnosticOwner = HarnessProtocol.DiagnosticOwner()
    private var intendedActivity = WeakReference<MainActivity>(null)
    private var intendedView = WeakReference<WebView>(null)
    private var failureBudget: HarnessProtocol.FailureBudget? = null

    @After
    fun cancelDiagnostics() {
        diagnosticOwner.close()
        intendedActivity.clear()
        intendedView.clear()
    }

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        session = PhoneBrowserSession.get(context)
        if (milestones == null) {
            milestones = MilestoneSink(context, System.currentTimeMillis())
        }
        scenario = activityRule.scenario
        scenario.onActivity { activity -> session.resetForTest() }
        awaitFixtureServer()
        dismissIme()
    }

    @Test
    fun freshStartShowsHintWithNoPageAndDisabledHistory() {
        onView(withId(R.id.status_text)).check(matches(withText(R.string.status_empty)))
        onView(withId(R.id.address_input)).check(matches(withText("")))
        assertEquals(0, attachedWebViews())
        onView(withId(R.id.button_back)).check(matches(notMatcher(isEnabled())))
        onView(withId(R.id.button_forward)).check(matches(notMatcher(isEnabled())))
    }

    @Test
    fun opensFixtureThroughAddressControl() {
        val url = fixtureUrl("/basic.html")
        val loadsBefore = loadCount("/basic.html")
        openAddress(url)
        val marker = waitForMarker()
        assertTrue("marker looks like a per-load marker: $marker", Regex("L\\d+").matches(marker))
        assertEquals(url, sessionDisplayUrl())
        assertEquals("Basic page", domText("page-title"))
        assertEquals(loadsBefore + 1, loadCount("/basic.html"))
        assertEquals(1, attachedWebViews())
        onView(withId(R.id.button_reload)).check(matches(isEnabled()))
    }

    @Test
    fun startedPageShowsClickCounterThroughRealTouch() {
        openAddress(fixtureUrl("/basic.html"))
        waitForMarker()
        realClickElement("click-button")
        waitUntil("click counter updates") { domText("click-count") == "1" }
    }

    @Test
    fun queuedDomOnlyTargetMoveBeforeFinalTapSampleDispatchesZeroInput() {
        openAddress(fixtureUrl("/basic.html"))
        waitForMarker()
        val dispatch = HarnessProtocol.Dispatch()
        try {
            realClickElement(
                "click-button",
                dispatch,
                null,
                InputSeamHook { view ->
                    awaitQueuedDomMutation(
                        view,
                        "(function(){var el=document.getElementById('click-button');" +
                            "el.style.transform='translateY(24px)';return 'moved';})()",
                    )
                },
            )
            fail("DOM-only target movement must stop before tap dispatch")
        } catch (expected: IllegalStateException) {
            assertEquals("DOM target/viewport changed after preparation", expected.message)
        }
        assertEquals("not-attempted", dispatch.stage)
        assertEquals("0", domText("click-count"))
    }

    @Test
    fun nativeAddressBarRefusesUnsupportedAndInvalidInputWithoutSideEffects() {
        val valid = fixtureUrl("/basic.html")
        openFreshFixture("/basic.html", "Basic page")
        var caseIndex = 0

        val cases = arrayOf(
            arrayOf("javascript:alert(1)", "Only http:// and https:// addresses are supported"),
            arrayOf(
                "content://com.example.fixture/item",
                "Only http:// and https:// addresses are supported",
            ),
            arrayOf("file:///etc/hosts", "Only http:// and https:// addresses are supported"),
            arrayOf(
                "intent://scan/#Intent;scheme=zxing;end",
                "Only http:// and https:// addresses are supported",
            ),
            arrayOf(
                "mailto:fixture@example.invalid",
                "Only http:// and https:// addresses are supported",
            ),
            arrayOf("data:text/html,hello", "Only http:// and https:// addresses are supported"),
            arrayOf("hello world", "Addresses cannot contain spaces"),
            arrayOf("bareword", "Enter a full address such as example.com or http://printer"),
            arrayOf("http://", "Enter a full address such as example.com or http://printer"),
            arrayOf("example.com/%zz", "That address is not valid"),
        )
        for (testCase in cases) {
            val input = testCase[0]
            val expectedFeedback = testCase[1]

            val baseline = reloadFreshFixture("/basic.html", "Basic page")
            waitUntil("baseline status for $input") {
                !statusText().contains("http://") && !statusText().contains("cannot contain")
            }
            js("window.__eyeProbe='kept'")
            val loads = loadCount("/basic.html")
            val trace = CaseTrace("native-${caseIndex++}", valid)
            try {
                trace.capture("baseline", "prepared", baseline)
                trace.step = "dispatch"
                trace.dispatch.actionOnce(Runnable { submitAddress(input) }, LongSupplier { SystemClock.uptimeMillis() })
                trace.capture("dispatch", "returned", baseline)
                trace.step = "observation"
                val after = readFixtureSnapshot()
                trace.capture(
                    "observation",
                    if (baseline.marker != after.marker) {
                        "changed"
                    } else if (expectedFeedback == statusText()) {
                        "refused"
                    } else {
                        "unexpected"
                    },
                    after,
                )
                trace.step = "assertion"

                assertEquals("draft preserved for $input", input, addressFieldText())
                assertEquals("feedback for $input", expectedFeedback, statusText())
                assertEquals("document preserved for $input", baseline.marker, after.marker)
                assertEquals("location preserved for $input", baseline.location, after.location)
                assertEquals("no page load for $input", loads, loadCount("/basic.html"))
                assertEquals(
                    "page script state preserved for $input",
                    "kept",
                    jsRead("String(window.__eyeProbe)"),
                )
                assertEquals("single engine for $input", 1, attachedWebViews())
            } catch (failure: Exception) {
                trace.failure(failure)
                throw failure
            } catch (failure: AssertionError) {
                trace.failure(failure)
                throw failure
            }
        }

        val loadsBeforeCorrection = loadCount("/basic.html")
        openFreshFixture("/basic.html", "Basic page")
        assertEquals(loadsBeforeCorrection + 1, loadCount("/basic.html"))
    }


    @Test
    fun nativeAddressBarRefusesTheScriptProbePayloadWithoutRunningIt() {
        val url = fixtureUrl("/destinations.html")
        openAddress(url)
        waitForMarker()
        assertEquals("idle", domText("script-probe"))
        val marker = domText("load-marker")

        submitAddress("javascript:void(document.getElementById('script-probe').textContent='ran')")

        assertEquals("Only http:// and https:// addresses are supported", statusText())
        assertEquals("the payload must not run", "idle", domText("script-probe"))
        assertEquals(marker, domText("load-marker"))
        assertEquals(url, jsRead("String(document.location.href)"))
        assertEquals(1, attachedWebViews())
    }

    @Test
    fun activatedTargetBlankLinkStaysInCurrentTabAndBackReturns() {
        openAddress(fixtureUrl("/target-blank.html"))
        waitUntil("target=_blank page") { domText("page-title") == "New-window link page" }
        realClickElement("blank-link")
        waitUntil("activated target=_blank link loads in this tab") {
            domText("page-title") == "Opened page"
        }
        assertEquals(1, attachedWebViews())
        assertEquals(fixtureUrl("/opened.html"), sessionDisplayUrl())
        waitUntil("Back becomes enabled") { viewEnabled(R.id.button_back) }
        onView(withId(R.id.button_back)).perform(click())
        waitUntil("Back returns to the previous page") {
            domText("page-title") == "New-window link page"
        }
    }

    @Test
    fun unsolicitedPopupDoesNotReplaceThePage() {
        openAddress(fixtureUrl("/popup.html"))
        val marker = waitForMarker()
        waitUntil("unsolicited window.open attempt") {
            val status = domText("popup-status")
            status != null && status.contains("unsolicited")
        }
        SystemClock.sleep(1500)
        assertEquals(marker, domText("load-marker"))
        assertEquals(fixtureUrl("/popup.html"), sessionDisplayUrl())
        assertEquals(1, attachedWebViews())
    }

    @Test
    fun gesturePopupUsesCurrentTab() {
        openAddress(fixtureUrl("/popup.html"))
        waitForMarker()
        realClickElement("gesture-popup-button")
        waitUntil("gesture window.open stays in this tab") {
            domText("page-title") == "Opened page"
        }
        assertEquals(1, attachedWebViews())
    }

    @Test
    fun recreationRetainsLiveDocumentFieldValuesAndLoadCount() {
        openAddress(fixtureUrl("/form.html"))
        val marker = waitForMarker()
        setElementValue("text-field", "draft-value")
        val loadsBefore = loadCount("/form.html")
        scenario.recreate()
        assertEquals(marker, domText("load-marker"))
        assertEquals("draft-value", jsRead("document.getElementById('text-field').value"))
        assertEquals(loadsBefore, loadCount("/form.html"))
        assertEquals(1, attachedWebViews())
        assertEquals(fixtureUrl("/form.html"), sessionDisplayUrl())
    }

    @Test
    fun simulatedProcessRestartOffersSavedUrlWithoutAutoLoading() {
        val url = fixtureUrl("/basic.html")
        openAddress(url)
        waitForMarker()
        val loadsBefore = loadCount("/basic.html")
        scenario.onActivity { session.simulateProcessRestartForTest() }
        scenario.recreate()
        onView(withId(R.id.status_text)).check(matches(withText(containsString("no longer live"))))
        onView(withId(R.id.address_input)).check(matches(withText(url)))
        assertEquals("no automatic reload on recovery", loadsBefore, loadCount("/basic.html"))
        assertEquals(0, attachedWebViews())
        onView(withId(R.id.button_open)).perform(click())
        waitUntil("explicit recovery load") { domText("page-title") == "Basic page" }
        assertEquals(loadsBefore + 1, loadCount("/basic.html"))
    }

    @Test
    fun untrustedHttpsIsRefusedAndPageNeverRenders() {
        val loadsBefore = loadCount("/secure-ok.html")
        openAddress("$SECURE_BASE/secure-ok.html")
        waitUntil("SSL refusal status") { statusText().contains("Could not load") }
        assertEquals(
            "missing",
            jsRead(
                "document.querySelector('[data-testid=\"secure-page\"]')" +
                    " ? 'present' : 'missing'",
            ),
        )
        assertEquals(
            "the untrusted endpoint must never serve the page",
            loadsBefore,
            loadCount("/secure-ok.html"),
        )
        assertEquals(1, attachedWebViews())
    }

    @Test
    fun controlledHttpFailureRendersTheServerBody() {
        openAddress(fixtureUrl("/fail"))
        waitUntil("controlled 500 body") { domText("page-title") == "Controlled failure page" }
        assertTrue(Regex("L\\d+").matches(waitForMarker()))
        assertEquals(fixtureUrl("/fail"), sessionDisplayUrl())
    }

    @Test
    fun pageOriginDestinationsNeverLeaveTheSession() {
        var freshRefusals = 0
        for (element in arrayOf("dest-mailto", "dest-content", "dest-file", "dest-intent", "dest-data")) {
            if (checkPageOriginCase(element, element, false)) freshRefusals++
        }
        assertTrue(
            "at least one page-origin destination must reach the app as a refusal",
            freshRefusals > 0,
        )
    }

    @Test
    fun contentDestinationIsAnEngineNoOpWithoutAFabricatedNotice() {
        checkPageOriginCase("content-standalone", "dest-content", true)
    }

    private fun checkPageOriginCase(
        caseId: String,
        element: String,
        requireNoOp: Boolean,
    ): Boolean {
        val baseline = openFreshFixture("/destinations.html", "Unsupported destinations")
        val trace = CaseTrace(caseId, fixtureUrl("/destinations.html"))
        try {
            trace.capture("baseline", "prepared", baseline)
            val baselineStatus = statusText()
            assertEquals("clean baseline before $element", "Unsupported destinations", baselineStatus)
            trace.step = "preparation"
            realClickElement(element, trace.dispatch, baseline)
            trace.capture("dispatch", "returned", baseline)
            trace.step = "observation"
            waitUntil("activation of $element") { domText("last-activated") == element }
            SystemClock.sleep(600)
            val after = readFixtureSnapshot()
            val statusAfter = statusText()
            val refused =
                statusAfter == "Blocked unsupported address. Only http:// and https:// load here."
            trace.capture(
                "observation",
                if (baseline.marker != after.marker) {
                    "changed"
                } else if (refused) {
                    "refused"
                } else if (baselineStatus == statusAfter) {
                    "no-op"
                } else {
                    "unexpected"
                },
                after,
            )
            trace.step = "assertion"
            assertTrue("unexpected status for $element", refused || baselineStatus == statusAfter)
            if (requireNoOp) {
                assertEquals(
                    "a no-op must not fabricate an app notice",
                    baselineStatus,
                    statusAfter,
                )
            }
            assertEquals("document marker preserved for $element", baseline.marker, after.marker)
            assertEquals("location preserved for $element", baseline.location, after.location)
            assertEquals("single engine for $element", 1, attachedWebViews())
            onView(withId(R.id.button_reload)).check(matches(isEnabled()))
            return refused
        } catch (failure: Exception) {
            trace.failure(failure)
            throw failure
        } catch (failure: AssertionError) {
            trace.failure(failure)
            throw failure
        }
    }

    @Test
    fun pageOwnedJavascriptRunsInThePageSandbox() {
        openAddress(fixtureUrl("/destinations.html"))
        waitForMarker()
        val marker = domText("load-marker")
        val location = jsRead("String(document.location.href)")
        assertEquals("idle", domText("script-probe"))
        realClickElement("dest-script-probe")
        waitUntil("page script runs") { domText("script-probe") == "ran" }
        assertEquals(marker, domText("load-marker"))
        assertEquals(location, jsRead("String(document.location.href)"))
        assertEquals(1, attachedWebViews())
    }

    @Test
    fun realSwipeScrollsLongDocument() {
        performSwipeScroll(fixtureUrl("/scroll.html"), HarnessProtocol.Dispatch(), null)
    }

    @Test
    fun queuedDomOnlyDocumentChangeBeforeFinalSwipeSampleDispatchesZeroInput() {
        val dispatch = HarnessProtocol.Dispatch()
        try {
            performSwipeScroll(
                fixtureUrl("/scroll.html"),
                dispatch,
                InputSeamHook { view ->
                    awaitQueuedDomMutation(
                        view,
                        "(function(){document.getElementById('load-marker').textContent=" +
                            "'R1-swapped';return 'changed';})()",
                    )
                },
            )
            fail("DOM-only document change must stop before swipe dispatch")
        } catch (expected: IllegalStateException) {
            assertEquals("document changed after preparation", expected.message)
        }
        assertEquals("not-attempted", dispatch.stage)
        assertEquals("0", jsRead("String(Math.round(window.scrollY))"))
    }

    private fun performSwipeScroll(
        expectedLocation: String,
        dispatch: HarnessProtocol.Dispatch,
        seamHook: InputSeamHook?,
    ) {
        openAddress(expectedLocation)
        waitForMarker()
        dismissIme()
        val before = readScrollFacts("before-swipe", expectedLocation)
        recordInputEvidence("scroll ${before.describe()}")
        awaitWindowFocus()
        val ready = readScrollFacts("ready-after-focus", expectedLocation)
        recordInputEvidence("scroll ${ready.describe()}")
        assertTrue(
            "coherent fixture observation before swipe",
            before.error == null && ready.error == null,
        )
        assertEquals(expectedLocation, ready.rawLocation)
        assertEquals("same document after readiness wait", before.marker, ready.marker)
        val preparedDom = swipeDomState(ready)
        val prepared = captureSwipePreparation()
        recordInputEvidence(
            "swipe intended-provider-path=${prepared.path} " +
                "${prepared.input.describe()} dom=${preparedDom.describe()}",
        )
        try {
            val preBoundaryDom = swipeDomState(js(SCROLL_FACTS_JS))
            requireDomUnchanged(preparedDom, preBoundaryDom)
            seamHook?.run(prepared.input.state.target as WebView)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val current = captureFinalReadiness(
                prepared.input,
                SCROLL_FACTS_JS,
                FinalPathSampler { view, _, _ -> swipeProviderPath(view) },
                FinalAdmission { domJson, input, path ->
                    val currentDom = swipeDomState(domJson)
                    DispatchReadiness.requireRecomputedReady(
                        prepared.input.state,
                        input.state,
                        path,
                        preparedDom,
                        currentDom,
                    )
                },
            )
            dispatch.actionOnce(
                Runnable { SingleShotSwipe.send(current.path) },
                LongSupplier { SystemClock.uptimeMillis() },
            )
            recordInputEvidence(
                "swipe final-sample intended=${current.path} ${current.input.describe()}",
            )
        } catch (failure: Exception) {
            recordFailureEvidence("swipe.perform stage=${dispatch.stage}", failure, expectedLocation)
            throw failure
        } catch (failure: AssertionError) {
            recordFailureEvidence("swipe.perform stage=${dispatch.stage}", failure, expectedLocation)
            throw failure
        }

        try {
            waitUntil("document scrolled") {
                val scrollY = jsOrNull("String(Math.round(window.scrollY))")
                scrollY != null && scrollY.toDouble() > 0
            }
        } catch (timeout: AssertionError) {
            recordFailureEvidence("scroll-timeout", timeout, expectedLocation)
            throw timeout
        }

        val after = readScrollFacts("after-swipe", expectedLocation)
        recordInputEvidence("scroll ${after.describe()}")
        assertTrue(
            "actual input-driven scroll observed: before [${before.describe()}] " +
                "after [${after.describe()}]",
            after.error == null && after.scrollY > 0 && after.scrollY > ready.scrollY,
        )
        assertEquals("scroll stayed in the intended document", ready.marker, after.marker)
        assertEquals(expectedLocation, after.rawLocation)
    }

    @Test
    fun harmlessPostIsRecordedOnceWithoutTypedValues() {
        openAddress(fixtureUrl("/form.html"))
        waitForMarker()
        setElementValue("text-field", "automation-text")
        setElementValue("password-field", "automation-secret")
        setElementValue("notes-field", "automation-notes")
        setElementText("editable-field", "automation-editable")
        assertEquals("automation-text", jsRead("document.getElementById('text-field').value"))
        assertEquals("automation-editable", domText("editable-field"))

        val postsBefore = countPosts(SYNTHETIC_TEST_ID)
        realClickElement("submit-button")
        waitUntil("submission page") { domText("page-title") == "Submission recorded" }
        assertEquals(
            "exactly one submission for this test id",
            postsBefore + 1,
            countPosts(SYNTHETIC_TEST_ID),
        )
        val fields = lastPost(SYNTHETIC_TEST_ID).getJSONArray("fields").toString()
        assertTrue(fields, fields.contains("test_id"))
        assertTrue(fields, fields.contains("message"))
        assertTrue(fields, fields.contains("secret"))
        assertTrue(fields, fields.contains("notes"))
        val raw = observationsRaw()
        assertFalse("typed text must never reach the fixture record", raw.contains("automation-text"))
        assertFalse(
            "password text must never reach the fixture record",
            raw.contains("automation-secret"),
        )
    }

    private fun openAddress(url: String) {
        submitAddress(url)
    }

    private fun submitAddress(text: String) {
        onView(withId(R.id.address_input)).perform(replaceText(text))
        onView(withId(R.id.button_open)).perform(click())
        dismissIme()
    }

    private fun addressFieldText(): String {
        val text = AtomicReference("")
        scenario.onActivity { activity ->
            val field: EditText = activity.findViewById(R.id.address_input)
            text.set(field.text.toString())
        }
        return text.get()
    }

    private fun fixtureUrl(path: String): String = FIXTURE_BASE + path

    private fun dismissIme() {
        try {
            androidx.test.espresso.Espresso.closeSoftKeyboard()
        } catch (_: RuntimeException) {
        } catch (_: AssertionError) {
        }
    }

    private fun awaitFixtureServer() {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                fetch("$FIXTURE_BASE/healthz")
                return
            } catch (_: Exception) {
                SystemClock.sleep(200)
            }
        }
        fail(
            "fixture server not reachable at $FIXTURE_BASE " +
                "(start tools/browser-fixtures.py and adb reverse tcp:25341 tcp:25341)",
        )
    }

    private fun waitForMarker(): String {
        val marker = AtomicReference<String?>()
        waitUntil("fixture load marker") {
            val value = domText("load-marker")
            if (value != null && Regex("L\\d+").matches(value)) {
                marker.set(value)
                true
            } else {
                false
            }
        }
        return checkNotNull(marker.get()) { "fixture marker missing after readiness" }
    }

    private fun waitUntil(description: String, condition: BooleanSupplier) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        var last: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                if (condition.asBoolean) return
            } catch (failure: RuntimeException) {
                last = failure
            } catch (failure: AssertionError) {
                last = failure
            }
            SystemClock.sleep(150)
        }
        fail(
            "timed out waiting for $description" +
                if (last == null) "" else " (last: $last)",
        )
    }

    private fun jsRead(expression: String): String? {
        var last: IllegalStateException? = null
        for (attempt in 0 until 2) {
            try {
                return jsOnce(expression)
            } catch (failure: IllegalStateException) {
                last = failure
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw failure
                }
            }
        }
        throw checkNotNull(last)
    }

    private fun js(expression: String): String? = jsOnce(expression)

    private fun jsOnce(expression: String): String? {
        val view = attachedWebView()
        if (view == null) throw IllegalStateException("no WebView is attached")
        val raw = AtomicReference<String?>()
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.evaluateJavascript(expression) { value ->
                raw.set(value)
                latch.countDown()
            }
        }
        try {
            if (!latch.await(10_000, TimeUnit.MILLISECONDS)) {
                throw IllegalStateException("JavaScript evaluation timed out")
            }
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("interrupted while evaluating JavaScript", failure)
        }
        return decodeJsValue(raw.get())
    }

    private fun jsOrNull(expression: String): String? =
        try {
            jsRead(expression)
        } catch (_: RuntimeException) {
            null
        } catch (_: AssertionError) {
            null
        }

    private fun decodeJsValue(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return try {
            val value = JSONObject("{\"v\":$raw}").get("v")
            if (value === JSONObject.NULL) null else value.toString()
        } catch (_: JSONException) {
            raw
        }
    }

    private fun domText(elementId: String): String? =
        jsRead(
            "(function(){var el=document.getElementById('$elementId');" +
                "return el ? el.textContent : null;})()",
        )

    private fun setElementValue(elementId: String, value: String) {
        js(
            "(function(){var el=document.getElementById('$elementId');" +
                "el.focus();el.value=${JSONObject.quote(value)};" +
                "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                "return el.value;})()",
        )
    }

    private fun setElementText(elementId: String, value: String) {
        js(
            "(function(){var el=document.getElementById('$elementId');" +
                "el.focus();el.textContent=${JSONObject.quote(value)};" +
                "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                "return el.textContent;})()",
        )
    }

    private fun attachedWebView(): WebView? {
        val view = AtomicReference<WebView?>()
        scenario.onActivity { activity ->
            val current: WebView? = activity.findViewById(R.id.browser_web_view)
            view.set(current)
            intendedActivity = WeakReference(activity)
            intendedView = WeakReference(current)
        }
        return view.get()
    }

    private fun sessionLoading(): Boolean {
        val loading = AtomicReference(false)
        scenario.onActivity { loading.set(session.isLoading) }
        return loading.get()
    }

    private fun statusText(): String {
        val text = AtomicReference("")
        scenario.onActivity { activity ->
            val status: TextView = activity.findViewById(R.id.status_text)
            text.set(if (status.visibility == View.VISIBLE) status.text.toString() else "")
        }
        return text.get()
    }

    private fun sessionDisplayUrl(): String? {
        val url = AtomicReference<String?>()
        scenario.onActivity { url.set(session.displayUrl()) }
        return url.get()
    }

    private fun viewEnabled(viewId: Int): Boolean {
        val enabled = AtomicReference(false)
        scenario.onActivity { activity ->
            enabled.set(activity.findViewById<View>(viewId).isEnabled)
        }
        return enabled.get()
    }

    private fun attachedWebViews(): Int {
        val count = AtomicInteger()
        scenario.onActivity { activity ->
            val container: ViewGroup = activity.findViewById(R.id.web_container)
            count.set(container.childCount)
        }
        return count.get()
    }

    private fun interface InputSeamHook {
        fun run(view: WebView)
    }

    private fun interface FinalPathSampler {
        fun sample(view: WebView, input: InputContext, domJson: String?): InputSafety.Path
    }

    private fun interface FinalAdmission {
        fun run(domJson: String?, input: InputContext, path: InputSafety.Path)
    }

    private fun realClickElement(elementId: String) {
        realClickElement(elementId, HarnessProtocol.Dispatch(), null, null)
    }

    private fun realClickElement(
        elementId: String,
        dispatch: HarnessProtocol.Dispatch,
        expectedDocument: HarnessProtocol.Snapshot?,
    ) {
        realClickElement(elementId, dispatch, expectedDocument, null)
    }

    private fun realClickElement(
        elementId: String,
        dispatch: HarnessProtocol.Dispatch,
        expectedDocument: HarnessProtocol.Snapshot?,
        seamHook: InputSeamHook?,
    ) {
        awaitWindowFocus()
        val identityGuard =
            if (expectedDocument == null) {
                ""
            } else {
                "if(document.getElementById('load-marker')?.textContent!==" +
                    JSONObject.quote(expectedDocument.marker) +
                    "||String(document.location.href)!==" +
                    JSONObject.quote(expectedDocument.location) +
                    "){return JSON.stringify({marker:document.getElementById('load-marker')?.textContent," +
                    "location:String(document.location.href)});}"
            }
        val rectJson = js(
            "(function(){$identityGuard" +
                "var el=document.getElementById('$elementId');" +
                "if(!el){return null;}el.scrollIntoView({block:'center'});" +
                "var r=el.getBoundingClientRect();" +
                "return JSON.stringify({x:(r.left+r.width/2),y:(r.top+r.height/2)," +
                "w:window.innerWidth,h:window.innerHeight," +
                "marker:document.getElementById('load-marker')?.textContent," +
                "location:String(document.location.href)});})()",
        )
        if (rectJson == null) {
            fail("fixture element not found: $elementId")
            return
        }
        val rect = JSONObject(rectJson)
        if (expectedDocument != null) {
            assertEquals(
                "document changed during touch preparation",
                expectedDocument.marker,
                rect.optString("marker", null),
            )
            assertEquals(
                "location changed during touch preparation",
                expectedDocument.location,
                rect.getString("location"),
            )
        }
        val preparedDom = tapDomState(elementId, rect)
        val mapped = captureInputContext()
        val point = tapPath(mapped, rect)
        sendTap(mapped, preparedDom, elementId, point, dispatch, seamHook)
    }


    private fun sendTap(
        prepared: InputContext,
        preparedDom: DispatchReadiness.DomState,
        elementId: String,
        point: InputSafety.Path,
        dispatch: HarnessProtocol.Dispatch,
        seamHook: InputSeamHook?,
    ) {
        recordInputEvidence(
            "tap intended=$point ${prepared.describe()} dom=${preparedDom.describe()}",
        )
        awaitWindowFocus()
        try {
            val preBoundaryJson = js(tapFactsJs(elementId))
            if (preBoundaryJson == null) {
                throw IllegalStateException("pre-boundary tap target unavailable")
            }
            val preBoundaryDom = tapDomState(elementId, json(preBoundaryJson))
            requireDomUnchanged(preparedDom, preBoundaryDom)
            seamHook?.run(prepared.state.target as WebView)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val current = captureFinalReadiness(
                prepared,
                tapFactsJs(elementId),
                FinalPathSampler { _, input, domJson -> tapPath(input, domJson) },
                FinalAdmission { domJson, input, currentPoint ->
                    val currentDom = tapDomState(elementId, json(domJson))
                    DispatchReadiness.requireRecomputedReady(
                        prepared.state,
                        input.state,
                        currentPoint,
                        preparedDom,
                        currentDom,
                    )
                },
            )
            val down = SingleShotSwipe.obtainTapDown(current.path)
            try {
                dispatch.tapOnce(
                    Runnable { SingleShotSwipe.injectTapDown(down) },
                    Runnable { SingleShotSwipe.injectTapUp(down, current.path) },
                    LongSupplier { SystemClock.uptimeMillis() },
                )
            } finally {
                down.recycle()
            }
            recordInputEvidence(
                "tap final-sample intended=${current.path} ${current.input.describe()}",
            )
        } catch (failure: Exception) {
            recordFailureEvidence("tap-dispatch stage=${dispatch.stage}", failure, null)
            throw failure
        } catch (failure: AssertionError) {
            recordFailureEvidence("tap-dispatch stage=${dispatch.stage}", failure, null)
            throw failure
        }
    }

    private fun tapFactsJs(elementId: String): String =
        "(function(){var el=document.getElementById(${JSONObject.quote(elementId)});" +
            "if(!el)return null;var r=el.getBoundingClientRect();" +
            "return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2," +
            "w:window.innerWidth,h:window.innerHeight," +
            "marker:document.getElementById('load-marker')?.textContent," +
            "location:String(document.location.href)});})()"

    private fun tapDomState(
        elementId: String,
        value: JSONObject,
    ): DispatchReadiness.DomState =
        try {
            DispatchReadiness.DomState(
                value.optString("marker", null),
                value.getString("location"),
                elementId,
                "x=${value.getDouble("x")};y=${value.getDouble("y")};" +
                    "w=${value.getDouble("w")};h=${value.getDouble("h")}",
            )
        } catch (invalid: JSONException) {
            throw IllegalStateException("tap DOM readiness was not valid JSON", invalid)
        }

    private fun tapPath(input: InputContext, domJson: String?): InputSafety.Path =
        tapPath(input, json(domJson))

    private fun tapPath(input: InputContext, value: JSONObject): InputSafety.Path =
        try {
            val cssWidth = value.getDouble("w")
            val cssHeight = value.getDouble("h")
            if (cssWidth <= 0 || cssHeight <= 0) {
                throw IllegalStateException("tap CSS viewport unavailable")
            }
            val localX = (value.getDouble("x") * input.state.width / cssWidth).toFloat()
            val localY = (value.getDouble("y") * input.state.height / cssHeight).toFloat()
            if (
                localX < 1 ||
                localY < 1 ||
                localX > input.state.width - 1 ||
                localY > input.state.height - 1
            ) {
                throw IllegalStateException("computed touch point outside the WebView")
            }
            InputSafety.Path(
                input.state.x + localX,
                input.state.y + localY,
                input.state.x + localX,
                input.state.y + localY,
            )
        } catch (invalid: JSONException) {
            throw IllegalStateException("tap path DOM readiness was not valid JSON", invalid)
        }

    private fun swipeDomState(facts: ScrollFacts): DispatchReadiness.DomState {
        if (facts.error != null) {
            throw IllegalStateException("swipe DOM readiness unavailable: ${facts.error}")
        }
        return DispatchReadiness.DomState(
            facts.marker,
            facts.rawLocation,
            "document-scroll",
            swipeGeometry(
                facts.scrollTop,
                facts.scrollY,
                facts.scrollHeight,
                facts.clientHeight,
                facts.innerWidth,
                facts.innerHeight,
            ),
        )
    }

    private fun swipeDomState(jsonValue: String?): DispatchReadiness.DomState {
        val value = json(jsonValue)
        return try {
            DispatchReadiness.DomState(
                value.optString("marker", null),
                value.getString("location"),
                "document-scroll",
                swipeGeometry(
                    value.getInt("scrollTop"),
                    value.getInt("scrollY"),
                    value.getInt("scrollHeight"),
                    value.getInt("clientHeight"),
                    value.getInt("innerWidth"),
                    value.getInt("innerHeight"),
                ),
            )
        } catch (invalid: JSONException) {
            throw IllegalStateException("swipe DOM readiness was not valid JSON", invalid)
        }
    }

    private fun swipeGeometry(
        scrollTop: Int,
        scrollY: Int,
        scrollHeight: Int,
        clientHeight: Int,
        innerWidth: Int,
        innerHeight: Int,
    ): String =
        "scrollTop=$scrollTop;scrollY=$scrollY;scrollHeight=$scrollHeight;" +
            "clientHeight=$clientHeight;innerWidth=$innerWidth;innerHeight=$innerHeight"

    private fun json(value: String?): JSONObject {
        if (value == null) throw IllegalStateException("DOM readiness unavailable")
        return try {
            JSONObject(value)
        } catch (invalid: JSONException) {
            throw IllegalStateException("DOM readiness was not valid JSON", invalid)
        }
    }

    private fun requireDomUnchanged(
        prepared: DispatchReadiness.DomState,
        current: DispatchReadiness.DomState,
    ) {
        val reason = prepared.revalidationReason(current)
        if (reason != null) throw IllegalStateException(reason)
    }

    private class FinalReadiness(
        val domJson: String?,
        val input: InputContext,
        val path: InputSafety.Path,
    )

    /**
     * Final DOM/native/path sampling and fail-closed admission occur in the same WebView callback
     * after the last deliberate idle boundary. The callback then releases the instrumentation test
     * thread; no later ActivityScenario, Espresso ViewInteraction/main-loop pump, focus wait,
     * evidence write, or other observation occurs before the single Instrumentation input attempt.
     */
    private fun captureFinalReadiness(
        prepared: InputContext,
        expression: String,
        pathSampler: FinalPathSampler,
        admission: FinalAdmission,
    ): FinalReadiness {
        val activity = prepared.state.activity as MainActivity
        val view = prepared.state.target as WebView
        val dom = AtomicReference<String?>()
        val input = AtomicReference<InputContext?>()
        val path = AtomicReference<InputSafety.Path?>()
        val handoff = DispatchReadiness.CallbackHandoff()
        val done = CountDownLatch(1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                if (
                    activity.isDestroyed ||
                    activity.isFinishing ||
                    !view.isAttachedToWindow ||
                    activity.findViewById<WebView>(R.id.browser_web_view) !== view
                ) {
                    throw IllegalStateException("intended Activity/view unavailable")
                }

                view.evaluateJavascript(expression) { value ->
                    try {
                        handoff.capture {
                            val currentDom = decodeJsValue(value)
                            val currentInput = readInputContext(activity, view)
                            val currentPath = pathSampler.sample(view, currentInput, currentDom)
                            admission.run(currentDom, currentInput, currentPath)
                            dom.set(currentDom)
                            input.set(currentInput)
                            path.set(currentPath)
                        }
                    } finally {
                        done.countDown()
                    }
                }
            } catch (unavailable: RuntimeException) {
                handoff.capture { throw unavailable }
                done.countDown()
            } catch (unavailable: AssertionError) {
                handoff.capture { throw unavailable }
                done.countDown()
            }
        }

        try {
            if (!done.await(10_000, TimeUnit.MILLISECONDS)) {
                throw IllegalStateException("final readiness callback timed out")
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("interrupted while awaiting final readiness", interrupted)
        }

        handoff.rethrowIfPresent()
        val finalInput = input.get()
        val finalPath = path.get()
        if (finalInput == null || finalPath == null) {
            throw IllegalStateException("final input readiness unavailable")
        }
        return FinalReadiness(dom.get(), finalInput, finalPath)
    }

    private class SwipePreparation(
        val input: InputContext,
        val path: InputSafety.Path,
    )

    private fun captureSwipePreparation(): SwipePreparation {
        val expected = captureInputContext()
        val captured = AtomicReference<SwipePreparation?>()
        scenario.onActivity { activity ->
            val view: WebView = activity.findViewById(R.id.browser_web_view)
            if (view !== expected.state.target) {
                throw IllegalStateException("intended WebView changed during swipe preparation")
            }
            val input = readInputContext(expected.state.activity as MainActivity, view)
            captured.set(SwipePreparation(input, swipeProviderPath(view)))
        }
        return captured.get() ?: throw IllegalStateException("swipe preparation unavailable")
    }

    private fun swipeProviderPath(view: WebView): InputSafety.Path {
        val start = GeneralLocation.translate(
            GeneralLocation.BOTTOM_CENTER,
            0f,
            -0.083f,
        ).calculateCoordinates(view)
        val end = GeneralLocation.TOP_CENTER.calculateCoordinates(view)
        return InputSafety.Path(start[0], start[1], end[0], end[1])
    }

    private fun awaitQueuedDomMutation(view: WebView, expression: String) {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        if (
            !MAIN.post {
                try {
                    if (!view.isAttachedToWindow) {
                        throw IllegalStateException("mutation target WebView detached")
                    }
                    view.evaluateJavascript(expression) { done.countDown() }
                } catch (unavailable: RuntimeException) {
                    failure.set(unavailable)
                    done.countDown()
                } catch (unavailable: AssertionError) {
                    failure.set(unavailable)
                    done.countDown()
                }
            }
        ) {
            throw IllegalStateException("main queue rejected DOM mutation regression")
        }

        try {
            if (!done.await(10_000, TimeUnit.MILLISECONDS)) {
                throw IllegalStateException("queued DOM mutation timed out")
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("interrupted while awaiting DOM mutation", interrupted)
        }

        when (val unavailable = failure.get()) {
            is RuntimeException -> throw unavailable
            is AssertionError -> throw unavailable
        }
    }

    private fun recordInputEvidence(line: String) {
        milestones?.record("INPUT $line")
    }

    private fun failureBudget(): HarnessProtocol.FailureBudget {
        if (failureBudget == null) {
            failureBudget = HarnessProtocol.FailureBudget(
                LongSupplier { SystemClock.elapsedRealtime() },
                HarnessProtocol.FailureBudget.DEFAULT_BUDGET_MS,
            )
        }
        return checkNotNull(failureBudget)
    }

    private fun captureDiagnostic(work: HarnessProtocol.DiagnosticWork): String? {
        val operation = HarnessProtocol.Diagnostic(
            diagnosticOwner,
            failureBudget(),
            DIAGNOSTIC_QUEUE,
            work,
        )
        operation.schedule()
        return operation.await()
    }

    private fun diagnosticJs(
        owner: WeakReference<MainActivity>,
        target: WeakReference<WebView>,
        expression: String,
    ): HarnessProtocol.DiagnosticWork =
        HarnessProtocol.DiagnosticWork { operation ->
            if (!operation.active()) return@DiagnosticWork
            val activity = owner.get()
            val view = target.get()
            if (
                activity == null ||
                activity.isDestroyed ||
                activity.isFinishing ||
                view == null ||
                !view.isAttachedToWindow ||
                activity.findViewById<WebView>(R.id.browser_web_view) !== view
            ) {
                operation.complete(null)
                return@DiagnosticWork
            }
            if (!operation.active()) return@DiagnosticWork
            view.evaluateJavascript(expression) { value ->
                operation.completeIfOwned(value) {
                    intendedOwnerStillCurrent(owner, target)
                }
            }
        }

    private fun intendedOwnerStillCurrent(
        owner: WeakReference<MainActivity>,
        target: WeakReference<WebView>,
    ): Boolean {
        val activity = owner.get()
        val view = target.get()
        return activity != null &&
            !activity.isDestroyed &&
            !activity.isFinishing &&
            view != null &&
            view.isAttachedToWindow &&
            activity.findViewById<WebView>(R.id.browser_web_view) === view
    }

    private fun recordFailureEvidence(
        stage: String,
        primary: Throwable,
        expectedLocation: String?,
    ) {
        HarnessProtocol.preserveFailure(primary) {
            val budget = failureBudget()
            val activityRef = intendedActivity
            val viewRef = intendedView
            val context = captureDiagnostic(
                HarnessProtocol.DiagnosticWork { operation ->
                    if (!operation.active()) return@DiagnosticWork
                    val activity = activityRef.get()
                    val view = viewRef.get()
                    if (activity == null || view == null) {
                        operation.complete(null)
                        return@DiagnosticWork
                    }
                    if (!operation.active()) return@DiagnosticWork
                    operation.complete(readInputContext(activity, view).describe())
                },
            )

            var dom = "not-applicable"
            if (expectedLocation != null) {
                val start = SystemClock.uptimeMillis()
                val raw = captureDiagnostic(diagnosticJs(activityRef, viewRef, SCROLL_FACTS_JS))
                dom =
                    if (raw == null) {
                        "unavailable(deadline/cancelled)"
                    } else {
                        parseScrollFacts(
                            "failure",
                            expectedLocation,
                            decodeJsValue(raw),
                            start,
                        ).describe()
                    }
            }

            checkNotNull(milestones).recordDeferred(
                "INPUT FAILURE_EVIDENCE stage=$stage elapsedMs=${budget.elapsedMs()} " +
                    "context=${context ?: "unavailable"} dom=$dom " +
                    "primary=${primary.javaClass.simpleName}",
            )
        }
    }

    private class InputContext(
        val activityName: String,
        val state: InputSafety.State,
    ) {
        val uptimeMs: Long = SystemClock.uptimeMillis()

        fun describe(): String =
            "t=$uptimeMs activity=$activityName ${state.mapping()}"
    }

    private fun captureInputContext(): InputContext {
        val captured = AtomicReference<InputContext?>()
        scenario.onActivity { activity ->
            val view: WebView = activity.findViewById(R.id.browser_web_view)
            intendedActivity = WeakReference(activity)
            intendedView = WeakReference(view)
            captured.set(readInputContext(activity, view))
        }
        return captured.get() ?: throw IllegalStateException("input context unavailable")
    }

    private fun readInputContext(
        activity: MainActivity,
        targetView: WebView?,
    ): InputContext {
        val view = targetView
            ?: throw IllegalStateException("intended Activity/view unavailable")
        if (
            activity.isDestroyed ||
            activity.isFinishing ||
            activity.findViewById<WebView>(R.id.browser_web_view) !== view
        ) {
            throw IllegalStateException("intended Activity/view unavailable")
        }

        val decor = activity.window.decorView
        val screen = IntArray(2)
        val window = IntArray(2)
        decor.getLocationOnScreen(screen)
        decor.getLocationInWindow(window)
        val root = Rect()
        val visible = Rect()
        if (!decor.getGlobalVisibleRect(root)) root.setEmpty()
        if (!view.getGlobalVisibleRect(visible)) visible.setEmpty()
        root.offset(screen[0] - window[0], screen[1] - window[1])
        visible.offset(screen[0] - window[0], screen[1] - window[1])

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val insets: WindowInsets? = view.rootWindowInsets
        val display: Display? = view.display

        val state = InputSafety.State(
            activity,
            view,
            activity.hasWindowFocus(),
            decor.isAttachedToWindow,
            view.isAttachedToWindow,
            view.isShown,
            view.hasFocus(),
            display?.displayId ?: -1,
            location[0],
            location[1],
            view.width,
            view.height,
            view.scrollX,
            view.scrollY,
            InputSafety.Bounds(root.left, root.top, root.right, root.bottom),
            InputSafety.Bounds(visible.left, visible.top, visible.right, visible.bottom),
            InputSafety.imeState(
                insets != null,
                insets?.isVisible(WindowInsets.Type.ime()) == true,
            ),
            insets?.getInsets(WindowInsets.Type.ime())?.bottom ?: -1,
            if (insets == null) {
                "unavailable"
            } else {
                "ime=${insets.getInsets(WindowInsets.Type.ime())} " +
                    "bars=${insets.getInsets(WindowInsets.Type.systemBars())} " +
                    "cutout=${insets.getInsets(WindowInsets.Type.displayCutout())}"
            },
        )

        return InputContext(
            "${activity.packageName}/${activity.javaClass.simpleName}",
            state,
        )
    }

    private inner class ScrollFacts(
        val phase: String,
        val sampleStartMs: Long,
        val sampleEndMs: Long,
        val rawLocation: String?,
        val location: String,
        val marker: String?,
        val scrollTop: Int,
        val scrollY: Int,
        val scrollHeight: Int,
        val clientHeight: Int,
        val innerWidth: Int,
        val innerHeight: Int,
        val error: String?,
    ) {
        fun describe(): String {
            if (error != null) {
                return "phase=$phase t=$sampleStartMs-$sampleEndMs ($error)"
            }
            return "phase=$phase t=$sampleStartMs-$sampleEndMs marker=$marker " +
                "location=$location scrollTop=$scrollTop scrollY=$scrollY " +
                "scrollHeight=$scrollHeight clientHeight=$clientHeight " +
                "viewport=${innerWidth}x$innerHeight"
        }
    }

    private fun readScrollFacts(phase: String, expectedLocation: String): ScrollFacts {
        val start = SystemClock.uptimeMillis()
        return try {
            parseScrollFacts(phase, expectedLocation, jsRead(SCROLL_FACTS_JS), start)
        } catch (unavailable: RuntimeException) {
            ScrollFacts(
                phase,
                start,
                SystemClock.uptimeMillis(),
                null,
                "(other)",
                null,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1,
                "DOM observation unavailable: $unavailable",
            )
        } catch (unavailable: AssertionError) {
            ScrollFacts(
                phase,
                start,
                SystemClock.uptimeMillis(),
                null,
                "(other)",
                null,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1,
                "DOM observation unavailable: $unavailable",
            )
        }
    }

    private fun parseScrollFacts(
        phase: String,
        expectedLocation: String,
        jsonValue: String?,
        start: Long,
    ): ScrollFacts {
        try {
            val value = JSONObject(jsonValue ?: throw NullPointerException("missing scroll facts"))
            val rawLocation = value.getString("location")
            return ScrollFacts(
                phase,
                start,
                SystemClock.uptimeMillis(),
                rawLocation,
                HarnessProtocol.traceLocation(rawLocation, expectedLocation),
                value.optString("marker", null),
                value.getInt("scrollTop"),
                value.getInt("scrollY"),
                value.getInt("scrollHeight"),
                value.getInt("clientHeight"),
                value.getInt("innerWidth"),
                value.getInt("innerHeight"),
                null,
            )
        } catch (unavailable: RuntimeException) {
            return ScrollFacts(
                phase,
                start,
                SystemClock.uptimeMillis(),
                null,
                "(other)",
                null,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1,
                "DOM observation unavailable: ${unavailable.javaClass.simpleName}",
            )
        } catch (unavailable: JSONException) {
            return ScrollFacts(
                phase,
                start,
                SystemClock.uptimeMillis(),
                null,
                "(other)",
                null,
                -1,
                -1,
                -1,
                -1,
                -1,
                -1,
                "DOM observation unavailable: ${unavailable.javaClass.simpleName}",
            )
        }
    }

    private fun awaitWindowFocus() {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val focused = AtomicReference(false)
            try {
                scenario.onActivity { activity -> focused.set(activity.hasWindowFocus()) }
            } catch (failure: RuntimeException) {
                throw IllegalStateException("browser activity unavailable for input", failure)
            }
            if (focused.get() == true) return
            SystemClock.sleep(200)
        }
        fail("the browser window never gained input focus")
    }


    private fun observations(): JSONObject = JSONObject(observationsRaw())

    private fun observationsRaw(): String = fetch("$FIXTURE_BASE/api/observations")

    private fun loadCount(path: String): Int {
        val loads = observations().optJSONObject("loads")
        return loads?.optInt(path, 0) ?: 0
    }

    private fun countPosts(testId: String): Int {
        val requests: JSONArray = observations().getJSONArray("requests")
        var count = 0
        for (i in 0 until requests.length()) {
            val entry = requests.getJSONObject(i)
            if (
                entry.optString("method") == "POST" &&
                entry.optString("path") == "/submit" &&
                entry.optString("testId") == testId
            ) {
                count++
            }
        }
        return count
    }

    private fun lastPost(testId: String): JSONObject {
        val requests = observations().getJSONArray("requests")
        for (i in requests.length() - 1 downTo 0) {
            val entry = requests.getJSONObject(i)
            if (
                entry.optString("method") == "POST" &&
                entry.optString("path") == "/submit" &&
                entry.optString("testId") == testId
            ) {
                return entry
            }
        }
        fail("no POST submission recorded for $testId")
        throw AssertionError("unreachable after JUnit fail")
    }

    private fun readFixtureSnapshot(): HarnessProtocol.Snapshot {
        val start = SystemClock.uptimeMillis()
        return decodeSnapshot(
            jsRead(FIXTURE_SNAPSHOT),
            start,
            SystemClock.uptimeMillis(),
        )
    }

    private fun decodeSnapshot(
        jsonValue: String?,
        start: Long,
        end: Long,
    ): HarnessProtocol.Snapshot {
        try {
            val value = JSONObject(
                jsonValue ?: throw NullPointerException("fixture snapshot unavailable"),
            )
            return HarnessProtocol.Snapshot(
                value.optString("marker", null),
                value.optString("title", null),
                value.getString("location"),
                value.getString("readyState"),
                start,
                end,
            )
        } catch (invalid: JSONException) {
            throw IllegalStateException("fixture snapshot was not valid JSON", invalid)
        } catch (invalid: NullPointerException) {
            throw IllegalStateException("fixture snapshot was not valid JSON", invalid)
        }
    }

    private fun openFreshFixture(path: String, title: String): HarnessProtocol.Snapshot {
        val before =
            if (attachedWebView() == null) {
                null
            } else {
                readFixtureSnapshot()
            }
        val gate = HarnessProtocol.Baseline.opening(before, title, fixtureUrl(path))
        openAddress(fixtureUrl(path))
        return awaitFreshFixture(gate)
    }

    private fun reloadFreshFixture(path: String, title: String): HarnessProtocol.Snapshot {
        val gate = HarnessProtocol.Baseline.reloading(
            readFixtureSnapshot(),
            title,
            fixtureUrl(path),
        )
        onView(withId(R.id.button_reload)).perform(click())
        return awaitFreshFixture(gate)
    }

    private fun awaitFreshFixture(gate: HarnessProtocol.Baseline): HarnessProtocol.Snapshot {
        val accepted = AtomicReference<HarnessProtocol.Snapshot?>()
        waitUntil("new complete fixture document") {
            val sample = readFixtureSnapshot()
            if (gate.accepts(sample)) {
                accepted.set(sample)
                true
            } else {
                false
            }
        }
        return accepted.get()
            ?: throw IllegalStateException("fresh fixture accepted without retained snapshot")
    }

    private inner class CaseTrace(
        val caseId: String,
        val expectedLocation: String,
    ) {
        val dispatch = HarnessProtocol.Dispatch()
        var step: String = "preparation"

        fun capture(
            phase: String,
            outcome: String,
            snapshot: HarnessProtocol.Snapshot?,
        ) {
            val previousStep = step
            step = "capture"
            reportCase(phase, outcome, snapshot)
            step = previousStep
        }

        fun failure(original: Throwable) {
            val outcome =
                when {
                    dispatch.stage != "not-attempted" && dispatch.stage != "returned" ->
                        "dispatch-unknown"
                    step == "capture" -> "capture-failed"
                    step == "assertion" -> "assertion-failed"
                    step == "observation" -> "observation-failed"
                    else -> "prepare-failed"
                }

            val suppressedBefore = original.suppressed.size
            HarnessProtocol.preserveFailure(original) {
                val start = SystemClock.uptimeMillis()
                val raw = captureDiagnostic(
                    diagnosticJs(intendedActivity, intendedView, FIXTURE_SNAPSHOT),
                )
                val snapshot =
                    if (raw == null) {
                        null
                    } else {
                        decodeSnapshot(
                            decodeJsValue(raw),
                            start,
                            SystemClock.uptimeMillis(),
                        )
                    }

                checkNotNull(milestones).recordDeferred(
                    "CASE_FAILURE_LOCAL case=$caseId outcome=$outcome " +
                        "dispatch=${dispatch.stage} marker=${snapshot?.marker ?: "unavailable"} " +
                        "location=" +
                        if (snapshot == null) {
                            "unavailable"
                        } else {
                            HarnessProtocol.traceLocation(snapshot.location, expectedLocation)
                        } +
                        " fixtureAck=not-attempted diagnosticElapsedMs=${failureBudget().elapsedMs()}",
                )
            }

            if (original.suppressed.size > suppressedBefore) {
                println("CASE_EVIDENCE_INCOMPLETE $caseId")
            }
        }

        private fun reportCase(
            phase: String,
            outcome: String,
            snapshot: HarnessProtocol.Snapshot?,
        ) {
            val location =
                if (snapshot == null) {
                    "(other)"
                } else {
                    HarnessProtocol.traceLocation(snapshot.location, expectedLocation)
                }

            val response = fetch(
                "$FIXTURE_BASE/api/case?case=${enc(caseId)}" +
                    "&phase=${enc(phase)}" +
                    "&marker=${enc(snapshot?.marker ?: "")}" +
                    "&location=${enc(location)}" +
                    "&outcome=${enc(outcome)}" +
                    "&t=${SystemClock.uptimeMillis()}" +
                    "&sampleStart=${snapshot?.sampleStartMs ?: -1}" +
                    "&sampleEnd=${snapshot?.sampleEndMs ?: -1}" +
                    "&actionStart=${dispatch.startMs}" +
                    "&actionEnd=${dispatch.endMs}" +
                    "&dispatch=${enc(dispatch.stage)}",
            )
            HarnessProtocol.requireRecorded(JSONObject(response).optBoolean("recorded", false))
        }
    }

    private fun enc(value: String?): String =
        try {
            URLEncoder.encode(value ?: "", "UTF-8")
        } catch (_: UnsupportedEncodingException) {
            ""
        }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        return try {
            connection.inputStream.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                    buildString {
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            append(line).append('\n')
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val DEFAULT_FIXTURE_BASE = "http://127.0.0.1:25341"
        private val FIXTURE_BASE = trimTrailingSlash(
            InstrumentationRegistry.getArguments()
                .getString("fixtureBaseUrl", DEFAULT_FIXTURE_BASE)
                ?: DEFAULT_FIXTURE_BASE,
        )
        private val SECURE_BASE = trimTrailingSlash(
            InstrumentationRegistry.getArguments()
                .getString("secureBaseUrl", "https://127.0.0.1:25342")
                ?: "https://127.0.0.1:25342",
        )
        private const val SYNTHETIC_TEST_ID = "fixture-post-1"
        private const val TIMEOUT_MS = 20_000L

        private const val SCROLL_FACTS_JS =
            "(function(){var d=document.documentElement;" +
                "var m=document.getElementById('load-marker');" +
                "return JSON.stringify({marker:m?m.textContent:null," +
                "location:String(document.location.href)," +
                "scrollTop:Math.round(d.scrollTop),scrollY:Math.round(window.scrollY)," +
                "scrollHeight:d.scrollHeight,clientHeight:d.clientHeight," +
                "innerWidth:window.innerWidth,innerHeight:window.innerHeight});})()"

        private const val FIXTURE_SNAPSHOT =
            "(function(){" +
                "var m=document.getElementById('load-marker'),t=document.getElementById('page-title');" +
                "return JSON.stringify({marker:m?m.textContent:null,title:t?t.textContent:null," +
                "location:String(document.location.href),readyState:document.readyState});})()"

        private val MAIN = Handler(Looper.getMainLooper())
        private val DIAGNOSTIC_QUEUE = object : HarnessProtocol.MainQueue {
            override fun post(work: Runnable) {
                if (!MAIN.post(work)) {
                    throw IllegalStateException("main queue rejected diagnostic")
                }
            }

            override fun remove(work: Runnable) {
                MAIN.removeCallbacks(work)
            }
        }

        private var milestones: MilestoneSink? = null

        @JvmStatic
        @AfterClass
        fun flushInputMilestones() {
            milestones?.flushToStream("Browser input evidence")
        }

        private fun trimTrailingSlash(value: String): String =
            if (value.endsWith("/")) value.substring(0, value.length - 1) else value
    }
}
