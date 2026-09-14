package com.code2hack.eyebrowse.core.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Destination policy: only explicit http/https destinations stay inside the session. */
public class NavigationPolicyTest {

    @Test
    public void allowsHttpAndHttps() {
        assertTrue(NavigationPolicy.isAllowed("http://127.0.0.1:25341/"));
        assertTrue(NavigationPolicy.isAllowed("https://example.com/a/b?c=d#e"));
        assertTrue(NavigationPolicy.isAllowed("HTTP://EXAMPLE.COM"));
        assertTrue(NavigationPolicy.isAllowed("  https://example.com  "));
        assertEquals("Blocked unsupported address", NavigationPolicy.refusalMessage("javascript:x"));
    }

    @Test
    public void refusesEverythingElse() {
        assertFalse(NavigationPolicy.isAllowed(null));
        assertFalse(NavigationPolicy.isAllowed(""));
        assertFalse(NavigationPolicy.isAllowed("   "));
        assertFalse(NavigationPolicy.isAllowed("example.com"));
        assertFalse(NavigationPolicy.isAllowed("//example.com"));
        assertFalse(NavigationPolicy.isAllowed("http:/example.com"));
        assertFalse(NavigationPolicy.isAllowed("http://"));
        assertFalse(NavigationPolicy.isAllowed("javascript:alert(1)"));
        assertFalse(NavigationPolicy.isAllowed("data:text/html,hello"));
        assertFalse(NavigationPolicy.isAllowed("file:///sdcard/x.html"));
        assertFalse(NavigationPolicy.isAllowed("content://com.example/x"));
        assertFalse(NavigationPolicy.isAllowed("blob:https://example.com/id"));
        assertFalse(NavigationPolicy.isAllowed("about:blank"));
        assertFalse(NavigationPolicy.isAllowed("intent://x/#Intent;scheme=y"));
        assertFalse(NavigationPolicy.isAllowed("history.back()"));
    }
}
