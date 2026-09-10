package com.code2hack.eyebrowse.phone;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.web.sugar.Web.onWebView;
import static androidx.test.espresso.web.webdriver.DriverAtoms.findElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.webClick;
import static androidx.test.espresso.web.webdriver.DriverAtoms.webKeys;
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
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.action.GeneralClickAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Tap;
import androidx.test.espresso.web.model.Atoms;
import androidx.test.espresso.web.model.Evaluation;
import androidx.test.espresso.web.webdriver.Locator;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Instrumentation checks of the Phone browser against the local fixture server.
 *
 * <p>Run with the fixture server bound to 127.0.0.1:25341 (and the untrusted TLS endpoint on
 * 25342) plus {@code adb reverse tcp:25341 tcp:25341} on a real device:
 *
 * <pre>
 * adb shell am instrument -w -e fixtureBaseUrl http://127.0.0.1:25341 \
 *   com.code2hack.eyebrowse.phone.test/androidx.test.runner.AndroidJUnitRunner
 * </pre>
 *
 * <p>Activation that must count as a user gesture (target=_blank links, window.open buttons, form
 * submission and scrolling) is performed with injected real touch events, not page JavaScript.
 * Typed text for form fields is synthetic automation input; the physical IME observation belongs to
 * the device procedure, not to these tests.
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
        openAddress(url);
        String marker = waitForMarker();
        assertTrue("marker looks like a per-load marker: " + marker, marker.matches("L\\d+"));
        assertEquals(url, sessionDisplayUrl());
        assertEquals("Basic page", domText("page-title"));
        assertEquals(1, loadCount("/basic.html"));
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

    @Test
    public void rejectedAddressKeepsTypedDraftAndLiveDocument() throws Exception {
        String url = fixtureUrl("/basic.html");
        openAddress(url);
        String marker = waitForMarker();
        int loadsBefore = loadCount("/basic.html");

        String rejected = "javascript:alert(1)";
        onView(withId(R.id.address_input)).perform(click(), replaceText(rejected));
        onView(withId(R.id.button_open)).perform(click());

        onView(withId(R.id.address_input)).check(matches(withText(rejected)));
        onView(withId(R.id.status_text)).check(matches(withText(
                "Only http:// and https:// addresses are supported")));
        assertEquals(marker, domText("load-marker"));
        assertEquals(loadsBefore, loadCount("/basic.html"));
        assertEquals(url, sessionDisplayUrl());
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
        onWebView().withElement(findElement(Locator.ID, "text-field")).perform(webClick())
                .perform(webKeys("draft-value"));
        int loadsBefore = loadCount("/form.html");

        scenario.recreate();

        assertEquals(marker, domText("load-marker"));
        assertEquals("draft-value",
                js("document.getElementById('text-field').value"));
        assertEquals(loadsBefore, loadCount("/form.html"));
        assertEquals(1, attachedWebViews());
        assertEquals(fixtureUrl("/form.html"), sessionDisplayUrl());
    }

    @Test
    public void processRestartOffersSavedUrlWithoutAutoLoading() throws Exception {
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
        openAddress(SECURE_BASE + "/secure-ok.html");
        waitUntil("SSL refusal status", () -> statusText().contains("Could not load"));
        assertEquals(null, jsOrNull("document.querySelector('[data-testid=\"secure-page\"]')"
                + " ? 'present' : null"));
        assertEquals(0, loadCount("/secure-ok.html"));
        assertEquals(1, attachedWebViews());
    }

    @Test
    public void controlledHttpFailureRendersTheServerBody() throws Exception {
        openAddress(fixtureUrl("/fail"));
        waitUntil("controlled 500 body", () -> "Controlled failure page".equals(domText("page-title")));
        assertTrue(waitForMarker().matches("L\\d+"));
        assertEquals(fixtureUrl("/fail"), sessionDisplayUrl());
    }

    @Test
    public void unsupportedDestinationIsBlockedWithoutReplacingPage() throws Exception {
        openAddress(fixtureUrl("/destinations.html"));
        String marker = waitForMarker();
        realClickElement("dest-javascript");
        waitUntil("blocked destination notice", () -> statusText().contains("Blocked"));
        assertEquals(marker, domText("load-marker"));
        assertEquals(fixtureUrl("/destinations.html"), sessionDisplayUrl());
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
        openAddress(fixtureUrl("/form.html"));
        waitForMarker();
        onWebView().withElement(findElement(Locator.ID, "text-field"))
                .perform(webClick()).perform(webKeys("automation-text"));
        onWebView().withElement(findElement(Locator.ID, "password-field"))
                .perform(webClick()).perform(webKeys("automation-secret"));
        onWebView().withElement(findElement(Locator.ID, "notes-field"))
                .perform(webClick()).perform(webKeys("automation-notes"));
        onWebView().withElement(findElement(Locator.ID, "editable-field"))
                .perform(webClick()).perform(webKeys("automation-editable"));
        assertEquals("automation-text", js("document.getElementById('text-field').value"));
        assertEquals("automation-editable",
                js("document.getElementById('editable-field').textContent"));

        realClickElement("submit-button");
        waitUntil("submission page", () -> "Submission recorded".equals(domText("page-title")));

        assertEquals(1, countPosts(SYNTHETIC_TEST_ID));
        JSONObject entry = lastPost(SYNTHETIC_TEST_ID);
        assertTrue(entry.getJSONArray("fields").toString().contains("test_id"));
        assertTrue(entry.getJSONArray("fields").toString().contains("message"));
        assertTrue(entry.getJSONArray("fields").toString().contains("secret"));
        assertTrue(entry.getJSONArray("fields").toString().contains("notes"));

        String raw = observationsRaw();
        assertFalse("typed text must never reach the fixture record", raw.contains("automation-text"));
        assertFalse("password text must never reach the fixture record", raw.contains("automation-secret"));
    }

    // -------------------------------------------------------------- utilities

    private void openAddress(String url) {
        onView(withId(R.id.address_input)).perform(click(), replaceText(url));
        onView(withId(R.id.button_open)).perform(click());
        dismissIme();
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

    private String js(String expression) {
        Evaluation evaluation = onWebView().forceJavascriptEnabled()
                .perform(Atoms.script(expression)).get();
        Object value = evaluation == null ? null : evaluation.getValue();
        return value == null ? null : String.valueOf(value);
    }

    private String jsOrNull(String expression) {
        try {
            return js(expression);
        } catch (RuntimeException | AssertionError e) {
            return null;
        }
    }

    private String domText(String elementId) {
        return jsOrNull("(function(){var el=document.getElementById('" + elementId + "');"
                + "return el ? el.textContent : null;})()");
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

    @SuppressWarnings("deprecation")
    private float webViewScale() {
        AtomicReference<Float> scale = new AtomicReference<>(1f);
        scenario.onActivity(activity -> {
            WebView view = activity.findViewById(R.id.browser_web_view);
            if (view != null) {
                scale.set(view.getScale());
            }
        });
        return scale.get();
    }

    /** Injects a real touch event so the page treats activation as a user gesture. */
    private void realClickElement(String elementId) throws Exception {
        String rectJson = js("(function(){var el=document.getElementById('" + elementId + "');"
                + "if(!el){return null;}el.scrollIntoView({block:'center'});"
                + "var r=el.getBoundingClientRect();"
                + "return JSON.stringify({x:(r.left+r.width/2),y:(r.top+r.height/2)});})()");
        if (rectJson == null) {
            fail("fixture element not found: " + elementId);
        }
        JSONObject rect = new JSONObject(rectJson);
        float scale = webViewScale();
        float x = (float) (rect.getDouble("x") * scale);
        float y = (float) (rect.getDouble("y") * scale);
        onView(withId(R.id.browser_web_view)).perform(new GeneralClickAction(
                Tap.SINGLE,
                view -> new float[] {x, y},
                Press.FINGER,
                InputDevice.SOURCE_UNKNOWN,
                MotionEvent.BUTTON_PRIMARY));
    }

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
