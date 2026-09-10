package com.code2hack.eyebrowse.phone;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Instrumentation checks of the Phone browser against the local fixture server.
 *
 * <p>Run with the fixture server bound to 127.0.0.1:25341 (and the untrusted TLS endpoint on
 * 25342) plus {@code adb reverse tcp:25341 tcp:25341}:
 *
 * <pre>
 * adb shell am instrument -w -e fixtureBaseUrl http://127.0.0.1:25341 \
 *   -e secureBaseUrl https://127.0.0.1:25342 \
 *   com.code2hack.eyebrowse.phone.test/androidx.test.runner.AndroidJUnitRunner
 * </pre>
 *
 * <p>This suite reads exactly two arguments: {@code fixtureBaseUrl} and {@code secureBaseUrl}. Both
 * have defaults for the reserved ports, and the runner passes them explicitly. Revision 1 of the
 * ticket plan showed {@code untrustedHttpsUrl}; that name is not consumed here.
 *
 * <p>Evidence boundaries: page reads use {@link WebView#evaluateJavascript}; activation uses
 * synthetic instrumented pointer events ({@code sendPointerSync}) which prove ordinary activation in
 * the focused EyeBrowse window but are <em>not</em> human touch or IME evidence; field values and
 * sentinels are set through page JavaScript as fixture setup, not typing; and
 * {@code simulateProcessRestartForTest()} is a simulation, not real process-death evidence. Bounded
 * waits: 20&nbsp;s for a condition, 10&nbsp;s for one JavaScript evaluation, 15&nbsp;s for window
 * focus, up to 3 injection attempts 500&nbsp;ms apart, and 10&nbsp;s for fixture readiness. Real IME,
 * cover/inner display, actual process-loss and RG optical rows belong to the device procedure.
 */
@RunWith(AndroidJUnit4.class)
public class BrowserInstrumentedTest {

    private static final String DEFAULT_FIXTURE_BASE = "http://127.0.0.1:25341";
    private static final String FIXTURE_BASE = trimTrailingSlash(
            InstrumentationRegistry.getArguments().getString("fixtureBaseUrl", DEFAULT_FIXTURE_BASE));
    private static final String SECURE_BASE = trimTrailingSlash(
            InstrumentationRegistry.getArguments().getString("secureBaseUrl", "https://127.0.0.1:25342"));
    private static final String SYNTHETIC_TEST_ID = "fixture-post-1";
    private static final long TIMEOUT_MS = 20_000;

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    private ActivityScenario<MainActivity> scenario;
    private PhoneBrowserSession session;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        session = PhoneBrowserSession.get(context);
        scenario = activityRule.getScenario();
        scenario.onActivity(activity -> session.resetForTest());
        awaitFixtureServer();
        dismissIme();
    }

    // ------------------------------------------------------------------ tests

    @Test
    public void freshStartShowsHintWithNoPageAndDisabledHistory() {
        onView(withId(R.id.status_text)).check(matches(withText(R.string.status_empty)));
        onView(withId(R.id.address_input)).check(matches(withText("")));
        assertEquals(0, attachedWebViews());
        onView(withId(R.id.button_back)).check(matches(not(isEnabled())));
        onView(withId(R.id.button_forward)).check(matches(not(isEnabled())));
    }

    @Test
    public void opensFixtureThroughAddressControl() throws Exception {
        String url = fixtureUrl("/basic.html");
        int loadsBefore = loadCount("/basic.html");
        openAddress(url);
        String marker = waitForMarker();
        assertTrue("marker looks like a per-load marker: " + marker, marker.matches("L\\d+"));
        assertEquals(url, sessionDisplayUrl());
        assertEquals("Basic page", domText("page-title"));
        assertEquals(loadsBefore + 1, loadCount("/basic.html"));
        assertEquals(1, attachedWebViews());
        onView(withId(R.id.button_reload)).check(matches(isEnabled()));
    }

    @Test
    public void startedPageShowsClickCounterThroughRealTouch() throws Exception {
        openAddress(fixtureUrl("/basic.html"));
        waitForMarker();
        realClickElement("click-button");
        waitUntil("click counter updates", () -> "1".equals(domText("click-count")));
    }

    /**
     * The mandatory address-bar contract: every unsupported or invalid input is refused before any
     * WebView navigation, the live document and its page script state survive, the exact typed draft
     * is kept, feedback belongs to that input (the page is reloaded first so no earlier notice can
     * satisfy the check), and a following valid address still loads. The text is entered with the
     * native toolbar control; this is not IME evidence.
     */
    @Test
    public void nativeAddressBarRefusesUnsupportedAndInvalidInputWithoutSideEffects() throws Exception {
        String valid = fixtureUrl("/basic.html");
        openAddress(valid);
        waitForMarker();
        String previousMarker = null;

        String[][] cases = {
                {"javascript:alert(1)", "Only http:// and https:// addresses are supported"},
                {"content://com.example.fixture/item",
                        "Only http:// and https:// addresses are supported"},
                {"file:///etc/hosts", "Only http:// and https:// addresses are supported"},
                {"intent://scan/#Intent;scheme=zxing;end",
                        "Only http:// and https:// addresses are supported"},
                {"mailto:fixture@example.invalid",
                        "Only http:// and https:// addresses are supported"},
                {"data:text/html,hello", "Only http:// and https:// addresses are supported"},
                {"hello world", "Addresses cannot contain spaces"},
                {"bareword", "Enter a full address such as example.com or http://printer"},
                {"http://", "Enter a full address such as example.com or http://printer"},
                {"example.com/%zz", "That address is not valid"},
        };
        for (String[] testCase : cases) {
            String input = testCase[0];
            String expectedFeedback = testCase[1];

            // Clean baseline: reload and wait for the settled, CHANGED document so a pending load can
            // never be mistaken for a change caused by the address submission below.
            onView(withId(R.id.button_reload)).perform(click());
            previousMarker = waitForFreshBaseline("Basic page", previousMarker);
            waitUntil("baseline status for " + input, () -> !statusText().contains("http://")
                    && !statusText().contains("cannot contain"));
            js("window.__eyeProbe='kept'");
            String marker = previousMarker;
            String location = jsRead("String(document.location.href)");
            int loads = loadCount("/basic.html");

            submitAddress(input);

            assertEquals("draft preserved for " + input, input, addressFieldText());
            assertEquals("feedback for " + input, expectedFeedback, statusText());
            assertEquals("document preserved for " + input, marker, domText("load-marker"));
            assertEquals("location preserved for " + input, location,
                    jsRead("String(document.location.href)"));
            assertEquals("no page load for " + input, loads, loadCount("/basic.html"));
            assertEquals("page script state preserved for " + input, "kept",
                    jsRead("String(window.__eyeProbe)"));
            assertEquals("single engine for " + input, 1, attachedWebViews());
        }

        // A valid address still works after the refusals.
        int loadsBeforeCorrection = loadCount("/basic.html");
        submitAddress(valid);
        waitUntil("correction loads", () -> "Basic page".equals(domText("page-title")));
        assertEquals(loadsBeforeCorrection + 1, loadCount("/basic.html"));
    }

    /** The same page-script payload is refused when typed into the native address bar. */
    @Test
    public void nativeAddressBarRefusesTheScriptProbePayloadWithoutRunningIt() throws Exception {
        String url = fixtureUrl("/destinations.html");
        openAddress(url);
        waitForMarker();
        assertEquals("idle", domText("script-probe"));
        String marker = domText("load-marker");

        submitAddress("javascript:void(document.getElementById('script-probe').textContent='ran')");

        assertEquals("Only http:// and https:// addresses are supported", statusText());
        assertEquals("the payload must not run", "idle", domText("script-probe"));
        assertEquals(marker, domText("load-marker"));
        assertEquals(url, jsRead("String(document.location.href)"));
        assertEquals(1, attachedWebViews());
    }

    @Test
    public void activatedTargetBlankLinkStaysInCurrentTabAndBackReturns() throws Exception {
        openAddress(fixtureUrl("/target-blank.html"));
        waitUntil("target=_blank page", () -> "New-window link page".equals(domText("page-title")));

        realClickElement("blank-link");
        waitUntil("activated target=_blank link loads in this tab",
                () -> "Opened page".equals(domText("page-title")));
        assertEquals(1, attachedWebViews());
        assertEquals(fixtureUrl("/opened.html"), sessionDisplayUrl());

        waitUntil("Back becomes enabled", () -> viewEnabled(R.id.button_back));
        onView(withId(R.id.button_back)).perform(click());
        waitUntil("Back returns to the previous page",
                () -> "New-window link page".equals(domText("page-title")));
    }

    @Test
    public void unsolicitedPopupDoesNotReplaceThePage() throws Exception {
        openAddress(fixtureUrl("/popup.html"));
        String marker = waitForMarker();
        waitUntil("unsolicited window.open attempt", () -> {
            String status = domText("popup-status");
            return status != null && status.contains("unsolicited");
        });
        SystemClock.sleep(1500);

        assertEquals(marker, domText("load-marker"));
        assertEquals(fixtureUrl("/popup.html"), sessionDisplayUrl());
        assertEquals(1, attachedWebViews());
    }

    @Test
    public void gesturePopupUsesCurrentTab() throws Exception {
        openAddress(fixtureUrl("/popup.html"));
        waitForMarker();
        realClickElement("gesture-popup-button");
        waitUntil("gesture window.open stays in this tab",
                () -> "Opened page".equals(domText("page-title")));
        assertEquals(1, attachedWebViews());
    }

    @Test
    public void recreationRetainsLiveDocumentFieldValuesAndLoadCount() throws Exception {
        openAddress(fixtureUrl("/form.html"));
        String marker = waitForMarker();
        setElementValue("text-field", "draft-value");
        int loadsBefore = loadCount("/form.html");

        scenario.recreate();

        assertEquals(marker, domText("load-marker"));
        assertEquals("draft-value", jsRead("document.getElementById('text-field').value"));
        assertEquals(loadsBefore, loadCount("/form.html"));
        assertEquals(1, attachedWebViews());
        assertEquals(fixtureUrl("/form.html"), sessionDisplayUrl());
    }

    @Test
    public void simulatedProcessRestartOffersSavedUrlWithoutAutoLoading() throws Exception {
        // A simulated new browser-process lifetime; real process death remains a device row.
        String url = fixtureUrl("/basic.html");
        openAddress(url);
        waitForMarker();
        int loadsBefore = loadCount("/basic.html");

        scenario.onActivity(activity -> session.simulateProcessRestartForTest());
        scenario.recreate();

        onView(withId(R.id.status_text)).check(matches(withText(containsString("no longer live"))));
        onView(withId(R.id.address_input)).check(matches(withText(url)));
        assertEquals("no automatic reload on recovery", loadsBefore, loadCount("/basic.html"));
        assertEquals(0, attachedWebViews());

        onView(withId(R.id.button_open)).perform(click());
        waitUntil("explicit recovery load", () -> "Basic page".equals(domText("page-title")));
        assertEquals(loadsBefore + 1, loadCount("/basic.html"));
    }

    @Test
    public void untrustedHttpsIsRefusedAndPageNeverRenders() throws Exception {
        int loadsBefore = loadCount("/secure-ok.html");
        openAddress(SECURE_BASE + "/secure-ok.html");
        waitUntil("SSL refusal status", () -> statusText().contains("Could not load"));
        assertEquals("missing", jsRead("document.querySelector('[data-testid=\"secure-page\"]')"
                + " ? 'present' : 'missing'"));
        assertEquals("the untrusted endpoint must never serve the page",
                loadsBefore, loadCount("/secure-ok.html"));
        assertEquals(1, attachedWebViews());
    }

    @Test
    public void controlledHttpFailureRendersTheServerBody() throws Exception {
        openAddress(fixtureUrl("/fail"));
        waitUntil("controlled 500 body", () -> "Controlled failure page".equals(domText("page-title")));
        assertTrue(waitForMarker().matches("L\\d+"));
        assertEquals(fixtureUrl("/fail"), sessionDisplayUrl());
    }

    /**
     * Page-origin destinations must never leave the session. The page's own sentinel proves the tap
     * activated the link; the document, real location and single-engine state must not change; with a
     * clean baseline any status change can only be this action's refusal. Schemes the engine refuses
     * on its own stay a no-op with no app notice - that outcome is recorded rather than asserted away
     * - and at least one scheme must reach the app as a genuine refusal.
     */
    @Test
    public void pageOriginDestinationsNeverLeaveTheSession() throws Exception {
        int freshRefusals = 0;
        String previousMarker = null;
        for (String element : new String[] {"dest-mailto", "dest-content", "dest-file", "dest-intent",
                "dest-data"}) {
            openAddress(fixtureUrl("/destinations.html"));
            // A grouped case must baseline the NEW document: wait until the load has settled and the
            // marker differs from the previously recorded one, otherwise a stale read of the outgoing
            // document looks like a reload during the activation.
            previousMarker = waitForFreshBaseline("Unsupported destinations", previousMarker);
            String baselineStatus = statusText();
            assertEquals("clean baseline before " + element, "Unsupported destinations", baselineStatus);
            String markerBefore = previousMarker;
            String locationBefore = jsRead("String(document.location.href)");
            reportCase(element, "start", markerBefore, locationBefore, "dispatched");

            realClickElement(element);
            waitUntil("activation of " + element, () -> element.equals(domText("last-activated")));
            SystemClock.sleep(600);

            String statusAfter = statusText();
            String markerAfter = domText("load-marker");
            String locationAfter = jsRead("String(document.location.href)");
            boolean refused = "Blocked unsupported address. Only http:// and https:// load here."
                    .equals(statusAfter);
            // Capture before asserting so a recurrence keeps its own current evidence.
            reportCase(element, "end", markerAfter, locationAfter,
                    markerBefore.equals(markerAfter) ? (refused ? "refused" : "no-op") : "reloaded");

            assertTrue("unexpected status for " + element + ": " + statusAfter,
                    refused || baselineStatus.equals(statusAfter));
            if (refused) {
                freshRefusals++;
            }
            // Faithful C1 continuity: an unsupported page-origin destination must leave the original
            // document intact. A re-fetched same URL is not continuity and is not accepted.
            assertEquals("document marker preserved for " + element, markerBefore, markerAfter);
            assertEquals("location preserved for " + element, locationBefore, locationAfter);
            assertEquals("single engine for " + element, 1, attachedWebViews());
            onView(withId(R.id.button_reload)).check(matches(isEnabled()));
        }
        assertTrue("at least one page-origin destination must reach the app as a refusal",
                freshRefusals > 0);
    }

    @Test
    public void contentDestinationIsAnEngineNoOpWithoutAFabricatedNotice() throws Exception {
        String caseId = "content-standalone";
        openAddress(fixtureUrl("/destinations.html"));
        String markerBefore = waitForFreshBaseline("Unsupported destinations", null);
        String baselineStatus = statusText();
        String locationBefore = jsRead("String(document.location.href)");
        reportCase(caseId, "start", markerBefore, locationBefore, "dispatched");

        realClickElement("dest-content");
        waitUntil("content activation", () -> "dest-content".equals(domText("last-activated")));
        SystemClock.sleep(600);

        String statusAfter = statusText();
        String markerAfter = domText("load-marker");
        String locationAfter = jsRead("String(document.location.href)");
        reportCase(caseId, "end", markerAfter, locationAfter,
                markerBefore.equals(markerAfter) ? "no-op" : "reloaded");

        assertEquals("a no-op must not fabricate an app notice", baselineStatus, statusAfter);
        assertEquals("document marker preserved for content", markerBefore, markerAfter);
        assertEquals(locationBefore, locationAfter);
        assertEquals(1, attachedWebViews());
        onView(withId(R.id.button_reload)).check(matches(isEnabled()));
    }

    @Test
    public void pageOwnedJavascriptRunsInThePageSandbox() throws Exception {
        openAddress(fixtureUrl("/destinations.html"));
        waitForMarker();
        String marker = domText("load-marker");
        String location = jsRead("String(document.location.href)");
        assertEquals("idle", domText("script-probe"));

        realClickElement("dest-script-probe");

        waitUntil("page script runs", () -> "ran".equals(domText("script-probe")));
        assertEquals(marker, domText("load-marker"));
        assertEquals(location, jsRead("String(document.location.href)"));
        assertEquals(1, attachedWebViews());
    }

    @Test
    public void realSwipeScrollsLongDocument() throws Exception {
        openAddress(fixtureUrl("/scroll.html"));
        waitForMarker();
        dismissIme();
        onView(withId(R.id.browser_web_view)).perform(swipeUp());
        waitUntil("document scrolled", () -> {
            String scrollY = jsOrNull("String(Math.round(window.scrollY))");
            return scrollY != null && Double.parseDouble(scrollY) > 0;
        });
    }

    @Test
    public void harmlessPostIsRecordedOnceWithoutTypedValues() throws Exception {
        // Field values are filled by page JavaScript as fixture setup (not typing); activation and
        // submission use the ordinary touch path.
        openAddress(fixtureUrl("/form.html"));
        waitForMarker();
        setElementValue("text-field", "automation-text");
        setElementValue("password-field", "automation-secret");
        setElementValue("notes-field", "automation-notes");
        setElementText("editable-field", "automation-editable");
        assertEquals("automation-text", jsRead("document.getElementById('text-field').value"));
        assertEquals("automation-editable", domText("editable-field"));

        int postsBefore = countPosts(SYNTHETIC_TEST_ID);

        realClickElement("submit-button");
        waitUntil("submission page", () -> "Submission recorded".equals(domText("page-title")));

        assertEquals("exactly one submission for this test id", postsBefore + 1,
                countPosts(SYNTHETIC_TEST_ID));
        String fields = lastPost(SYNTHETIC_TEST_ID).getJSONArray("fields").toString();
        assertTrue(fields, fields.contains("test_id"));
        assertTrue(fields, fields.contains("message"));
        assertTrue(fields, fields.contains("secret"));
        assertTrue(fields, fields.contains("notes"));

        String raw = observationsRaw();
        assertFalse("typed text must never reach the fixture record", raw.contains("automation-text"));
        assertFalse("password text must never reach the fixture record", raw.contains("automation-secret"));
    }

    // -------------------------------------------------------------- utilities

    private void openAddress(String url) {
        submitAddress(url);
    }

    /**
     * Types into the native address control and presses the native Open button. The text arrives as
     * an instrumentation edit of the toolbar field, so it proves the address pipeline, not IME use.
     */
    private void submitAddress(String text) {
        onView(withId(R.id.address_input)).perform(click(), replaceText(text));
        onView(withId(R.id.button_open)).perform(click());
        dismissIme();
    }

    private String addressFieldText() {
        AtomicReference<String> text = new AtomicReference<>("");
        scenario.onActivity(activity -> {
            EditText field = activity.findViewById(R.id.address_input);
            text.set(field.getText().toString());
        });
        return text.get();
    }

    private String fixtureUrl(String path) {
        return FIXTURE_BASE + path;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void dismissIme() {
        try {
            androidx.test.espresso.Espresso.closeSoftKeyboard();
        } catch (RuntimeException | AssertionError ignored) {
            // No IME window to close; nothing to dismiss.
        }
    }

    private void awaitFixtureServer() {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                fetch(FIXTURE_BASE + "/healthz");
                return;
            } catch (Exception e) {
                SystemClock.sleep(200);
            }
        }
        fail("fixture server not reachable at " + FIXTURE_BASE
                + " (start tools/browser-fixtures.py and adb reverse tcp:25341 tcp:25341)");
    }

    private String waitForMarker() {
        AtomicReference<String> marker = new AtomicReference<>();
        waitUntil("fixture load marker", () -> {
            String value = domText("load-marker");
            if (value != null && value.matches("L\\d+")) {
                marker.set(value);
                return true;
            }
            return false;
        });
        return marker.get();
    }

    private void waitUntil(String description, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        Throwable last = null;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (RuntimeException | AssertionError e) {
                last = e;
            }
            SystemClock.sleep(150);
        }
        fail("timed out waiting for " + description + (last == null ? "" : " (last: " + last + ")"));
    }

    // ---------------------------------------------------- page interaction

    /**
     * Evaluates JavaScript through the platform API. This never navigates, so the app's refusal of
     * {@code javascript:} destinations stays exactly as a user would experience it.
     */
    /**
     * Read-only page observation with one bounded retry: the platform callback for
     * {@code evaluateJavascript} can occasionally be dropped while a navigation settles (observed on
     * the LAN origin). Only single-shot mutations (setters, scroll-and-measure) may use {@link #js},
     * so a retry can never replay a mutation, click or submission. Callers compare every read against
     * a previously captured marker/location, so a value from a different document fails the
     * assertion instead of silently passing.
     */
    private String jsRead(String expression) {
        IllegalStateException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return jsOnce(expression);
            } catch (IllegalStateException e) {
                last = e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    /** Single-attempt evaluation for expressions that mutate or scroll the document. */
    private String js(String expression) {
        return jsOnce(expression);
    }

    private String jsOnce(String expression) {
        WebView view = attachedWebView();
        if (view == null) {
            throw new IllegalStateException("no WebView is attached");
        }
        AtomicReference<String> raw = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                view.evaluateJavascript(expression, value -> {
                    raw.set(value);
                    latch.countDown();
                }));
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaScript evaluation timed out: " + expression);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while evaluating: " + expression, e);
        }
        return decodeJsValue(raw.get());
    }

    private String jsOrNull(String expression) {
        try {
            return jsRead(expression);
        } catch (RuntimeException | AssertionError e) {
            return null;
        }
    }

    private static String decodeJsValue(String raw) {
        if (raw == null || "null".equals(raw)) {
            return null;
        }
        try {
            Object value = new JSONObject("{\"v\":" + raw + "}").get("v");
            return value == JSONObject.NULL ? null : String.valueOf(value);
        } catch (JSONException e) {
            return raw;
        }
    }

    private String domText(String elementId) {
        return jsRead("(function(){var el=document.getElementById('" + elementId + "');"
                + "return el ? el.textContent : null;})()");
    }

    private void setElementValue(String elementId, String value) {
        // Fixture setup through page JavaScript: this is not typing, IME or input-path evidence.
        js("(function(){var el=document.getElementById('" + elementId + "');"
                + "el.focus();el.value=" + JSONObject.quote(value) + ";"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "return el.value;})()");
    }

    private void setElementText(String elementId, String value) {
        // Fixture setup through page JavaScript: this is not typing, IME or input-path evidence.
        js("(function(){var el=document.getElementById('" + elementId + "');"
                + "el.focus();el.textContent=" + JSONObject.quote(value) + ";"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "return el.textContent;})()");
    }

    private WebView attachedWebView() {
        AtomicReference<WebView> view = new AtomicReference<>();
        scenario.onActivity(activity -> view.set(activity.findViewById(R.id.browser_web_view)));
        return view.get();
    }

    private boolean sessionLoading() {
        AtomicReference<Boolean> loading = new AtomicReference<>(false);
        scenario.onActivity(activity -> loading.set(session.isLoading()));
        return loading.get();
    }

    private String statusText() {
        AtomicReference<String> text = new AtomicReference<>("");
        scenario.onActivity(activity -> {
            TextView status = activity.findViewById(R.id.status_text);
            text.set(status.getVisibility() == View.VISIBLE ? status.getText().toString() : "");
        });
        return text.get();
    }

    private String sessionDisplayUrl() {
        AtomicReference<String> url = new AtomicReference<>();
        scenario.onActivity(activity -> url.set(session.displayUrl()));
        return url.get();
    }

    private boolean viewEnabled(int viewId) {
        AtomicReference<Boolean> enabled = new AtomicReference<>(false);
        scenario.onActivity(activity -> enabled.set(activity.findViewById(viewId).isEnabled()));
        return enabled.get();
    }

    private int attachedWebViews() {
        AtomicInteger count = new AtomicInteger();
        scenario.onActivity(activity -> {
            ViewGroup container = activity.findViewById(R.id.web_container);
            count.set(container.getChildCount());
        });
        return count.get();
    }

    /**
     * Injects synthetic instrumented touch through the system input pipeline. It establishes ordinary
     * activation in the foreground EyeBrowse window; it is not human touch and not IME evidence. The
     * {@code scrollIntoView} call is setup only - the dedicated swipe test proves input-driven
     * scrolling - and the geometry is measured after it, before this single tap attempt.
     */
    private void realClickElement(String elementId) throws Exception {
        String rectJson = js("(function(){var el=document.getElementById('" + elementId + "');"
                + "if(!el){return null;}el.scrollIntoView({block:'center'});"
                + "var r=el.getBoundingClientRect();"
                + "return JSON.stringify({x:(r.left+r.width/2),y:(r.top+r.height/2),"
                + "w:window.innerWidth,h:window.innerHeight});})()");
        if (rectJson == null) {
            fail("fixture element not found: " + elementId);
        }
        JSONObject rect = new JSONObject(rectJson);
        double cssWidth = rect.getDouble("w");
        double cssHeight = rect.getDouble("h");
        if (cssWidth <= 0 || cssHeight <= 0) {
            fail("no CSS viewport reported for " + elementId);
        }
        float[] viewSize = new float[2];
        int[] location = new int[2];
        scenario.onActivity(activity -> {
            WebView view = activity.findViewById(R.id.browser_web_view);
            viewSize[0] = view.getWidth();
            viewSize[1] = view.getHeight();
            view.getLocationOnScreen(location);
        });
        // Map CSS viewport pixels to WebView pixels using the measured ratio (density and zoom).
        float x = (float) (rect.getDouble("x") * viewSize[0] / cssWidth);
        float y = (float) (rect.getDouble("y") * viewSize[1] / cssHeight);
        if (x < 1 || y < 1 || x > viewSize[0] - 1 || y > viewSize[1] - 1) {
            fail("computed touch point for " + elementId + " is outside the WebView: "
                    + x + "," + y + " of " + viewSize[0] + "x" + viewSize[1]);
        }
        // Real input events through the system input pipeline: Espresso's view-level injection is
        // not delivered to this WebView, while sendPointerSync is a genuine user gesture. Injection
        // only works while this app owns the focused window.
        for (int attempt = 1; attempt <= 3; attempt++) {
            awaitWindowFocus();
            try {
                sendTap(location[0] + x, location[1] + y);
                return;
            } catch (RuntimeException e) {
                if (attempt == 3) {
                    fail("could not inject a touch event at " + (location[0] + x) + ","
                            + (location[1] + y) + ": " + e);
                }
                SystemClock.sleep(500);
            }
        }
    }

    private void sendTap(float screenX, float screenY) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, screenX, screenY, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, screenX, screenY, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    /** Real input injection is only accepted while this app owns the focused window. */
    private void awaitWindowFocus() {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> focused = new AtomicReference<>(false);
            try {
                scenario.onActivity(activity -> focused.set(activity.hasWindowFocus()));
            } catch (RuntimeException e) {
                throw new IllegalStateException("browser activity unavailable for input", e);
            }
            if (Boolean.TRUE.equals(focused.get())) {
                return;
            }
            SystemClock.sleep(200);
        }
        fail("the browser window never gained input focus");
    }

    // -------------------------------------------------------- observations

    private JSONObject observations() throws Exception {
        return new JSONObject(observationsRaw());
    }

    private String observationsRaw() throws Exception {
        return fetch(FIXTURE_BASE + "/api/observations");
    }

    private int loadCount(String path) throws Exception {
        JSONObject loads = observations().optJSONObject("loads");
        return loads == null ? 0 : loads.optInt(path, 0);
    }

    private int countPosts(String testId) throws Exception {
        JSONArray requests = observations().getJSONArray("requests");
        int count = 0;
        for (int i = 0; i < requests.length(); i++) {
            JSONObject entry = requests.getJSONObject(i);
            if ("POST".equals(entry.optString("method"))
                    && "/submit".equals(entry.optString("path"))
                    && testId.equals(entry.optString("testId"))) {
                count++;
            }
        }
        return count;
    }

    private JSONObject lastPost(String testId) throws Exception {
        JSONArray requests = observations().getJSONArray("requests");
        for (int i = requests.length() - 1; i >= 0; i--) {
            JSONObject entry = requests.getJSONObject(i);
            if ("POST".equals(entry.optString("method"))
                    && "/submit".equals(entry.optString("path"))
                    && testId.equals(entry.optString("testId"))) {
                return entry;
            }
        }
        fail("no POST submission recorded for " + testId);
        return null;
    }

    /**
     * Waits for a settled document that is demonstrably NEW (its marker differs from the previous
     * one) and returns that marker. Grouped and matrix cases must never baseline the outgoing
     * document, otherwise a pending load looks like a change caused by the activation under test.
     */
    private String waitForFreshBaseline(String expectedTitle, String previousMarker) {
        AtomicReference<String> marker = new AtomicReference<>();
        waitUntil("fresh baseline (", () -> {
            String value = domText("load-marker");
            boolean fresh = value != null && value.matches("L\\d+")
                    && (previousMarker == null || !previousMarker.equals(value));
            if (fresh && !sessionLoading() && expectedTitle.equals(domText("page-title"))) {
                marker.set(value);
                return true;
            }
            return false;
        });
        return marker.get();
    }

    private void reportCase(String caseId, String phase, String marker, String location,
                            String outcome) {
        // Immediate per-activation evidence: case id, monotonic timing, dispatch outcome and the
        // observed marker/location. Field contents and passwords are never included. A capture
        // failure must not mask the behaviour under test, so the assertions still run afterwards.
        try {
            fetch(FIXTURE_BASE + "/api/case?case=" + enc(caseId) + "&phase=" + enc(phase)
                    + "&marker=" + enc(marker) + "&location=" + enc(location)
                    + "&outcome=" + enc(outcome) + "&t=" + SystemClock.uptimeMillis());
        } catch (Exception e) {
            System.out.println("case-capture failed for " + caseId + "/" + phase + ": " + e);
        }
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        try (InputStream in = connection.getInputStream()) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }
}
