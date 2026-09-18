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
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
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

/**
 * Instrumentation checks of the Phone browser against the local fixture server.
 *
 * Behavior/evidence boundaries are unchanged from the Java source: page reads use
 * WebView.evaluateJavascript; activation uses the pinned Espresso controller with synthetic pointer
 * events (not human touch/IME evidence); fixture field setup uses page JavaScript; and simulated
 * process restart is not physical process-death evidence.
 */
@RunWith(AndroidJUnit4::class)
class BrowserInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var session: PhoneBrowserSession
    private val main = Handler(Looper.getMainLooper())
    private val diagnosticQueue = object : HarnessProtocol.MainQueue {
        override fun post(work: Runnable) {
            if (!main.post(work)) throw IllegalStateException("main queue rejected diagnostic")
        }

        override fun remove(work: Runnable) {
            main.removeCallbacks(work)
        }
    }
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
                trace.dispatch.actionOnce(Runnable { submitAddress(input) }, SystemClock::uptimeMillis)
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

