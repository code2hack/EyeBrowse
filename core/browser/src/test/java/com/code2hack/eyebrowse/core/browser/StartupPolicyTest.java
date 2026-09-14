package com.code2hack.eyebrowse.core.browser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Startup decisions: a live session is reattached, a saved URL is only ever offered. */
public class StartupPolicyTest {

    @Test
    public void liveSessionWins() {
        assertEquals(
                StartupPolicy.Decision.REATTACH_LIVE_SESSION,
                StartupPolicy.decide(true, "https://example.com"));
        assertEquals(
                StartupPolicy.Decision.REATTACH_LIVE_SESSION,
                StartupPolicy.decide(true, null));
    }

    @Test
    public void savedUrlIsOfferedButNotLoaded() {
        assertEquals(
                StartupPolicy.Decision.OFFER_SAVED_URL,
                StartupPolicy.decide(false, "http://127.0.0.1:25341/"));
        assertEquals(
                StartupPolicy.Decision.OFFER_SAVED_URL,
                StartupPolicy.decide(false, "https://example.com/page"));
    }

    @Test
    public void unusableSavedStateFallsBackToEmptyStart() {
        assertEquals(StartupPolicy.Decision.EMPTY_START, StartupPolicy.decide(false, null));
        assertEquals(StartupPolicy.Decision.EMPTY_START, StartupPolicy.decide(false, ""));
        assertEquals(StartupPolicy.Decision.EMPTY_START, StartupPolicy.decide(false, "example.com"));
        assertEquals(
                StartupPolicy.Decision.EMPTY_START,
                StartupPolicy.decide(false, "javascript:alert(1)"));
        assertEquals(
                StartupPolicy.Decision.EMPTY_START,
                StartupPolicy.decide(false, "content://com.example/x"));
    }
}
