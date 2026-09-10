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
import java.net.HttpURLConnection;
import java.net.URL;
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
 *   com.code2hack.eyebrowse.phone.test/androidx.test.runner.AndroidJUnitRunner
 * </pre>
 *
 * <p>The suite drives the page through the platform WebView APIs only: JavaScript is read with
 * {@link WebView#evaluateJavascript} (never a {@code javascript:} navigation, which the product
 * correctly refuses), and activation that must count as a user gesture (links, popup buttons, form
 * submission, scrolling) uses injected real touch events. Field text entry is synthetic automation
 * input; the physical IME observation belongs to the device procedure, not to these tests.
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
        setElementValue("text-field", "draft-value");
        int loadsBefore = loadCount("/form.html");

        scenario.recreate();

        assertEquals(marker, domText("load-marker"));
        assertEquals("draft-value", js("document.getElementById('text-field').value"));
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
        int loadsBefore = loadCount("/secure-ok.html");
        openAddress(SECURE_BASE + "/secure-ok.html");
        waitUntil("SSL refusal status", () -> statusText().contains("Could not load"));
        assertEquals("missing", js("document.querySelector('[data-testid=\"secure-page\"]')"
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

    @Test
    public void unsupportedNavigationsNeverLeaveTheSession() throws Exception {
        openAddress(fixtureUrl("/destinations.html"));
        String marker = waitForMarker();

        // A navigation-style unsupported scheme is refused with a compact notice.
        realClickElement("dest-mailto");
        waitUntil("blocked destination notice", () -> statusText().contains("Blocked"));
        assertEquals(marker, domText("load-marker"));
        assertEquals(fixtureUrl("/destinations.html"), sessionDisplayUrl());

        // content: is an engine-level no-op here: no navigation, no notice, page untouched.
        realClickElement("dest-content");
        SystemClock.sleep(800);
        assertEquals(marker, domText("load-marker"));
        assertEquals(fixtureUrl("/destinations.html"), sessionDisplayUrl());
        assertEquals(1, attachedWebViews());

        // javascript: is refused too. The engine then leaves a blank document instead of running
        // the script; the session URL must not change and no second context may appear.
        realClickElement("dest-javascript");
        waitUntil("javascript destination blocked", () -> statusText().contains("Blocked"));
        assertEquals(fixtureUrl("/destinations.html"), sessionDisplayUrl());
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
        openAddress(fixtureUrl("/form.html"));
        waitForMarker();
        setElementValue("text-field", "automation-text");
        setElementValue("password-field", "automation-secret");
        setElementValue("notes-field", "automation-notes");
        setElementText("editable-field", "automation-editable");
        assertEquals("automation-text", js("document.getElementById('text-field').value"));
        assertEquals("automation-editable", domText("editable-field"));

        realClickElement("submit-button");
        waitUntil("submission page", () -> "Submission recorded".equals(domText("page-title")));

        assertEquals(1, countPosts(SYNTHETIC_TEST_ID));
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

    // ---------------------------------------------------- page interaction

    /**
     * Evaluates JavaScript through the platform API. This never navigates, so the app's refusal of
     * {@code javascript:} destinations stays exactly as a user would experience it.
     */
    private String js(String expression) {
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
            return js(expression);
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
        return js("(function(){var el=document.getElementById('" + elementId + "');"
                + "return el ? el.textContent : null;})()");
    }

    private void setElementValue(String elementId, String value) {
        js("(function(){var el=document.getElementById('" + elementId + "');"
                + "el.focus();el.value=" + JSONObject.quote(value) + ";"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "return el.value;})()");
    }

    private void setElementText(String elementId, String value) {
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

    /** Injects a real touchscreen tap so the page treats activation as a user gesture. */
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
