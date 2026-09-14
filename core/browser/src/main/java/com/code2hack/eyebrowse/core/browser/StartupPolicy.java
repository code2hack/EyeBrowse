package com.code2hack.eyebrowse.core.browser;

/**
 * Decides what the Phone browser shows when an activity attaches to the session.
 *
 * <p>A surviving in-process session is reattached without touching the live document. A saved URL
 * is only ever offered, never loaded automatically: after a process restart the former JavaScript
 * memory, form state and history are gone, so the browser must not fake a restored page.
 */
public final class StartupPolicy {

    /** What the Phone activity should present for the current session state. */
    public enum Decision {
        /** A live WebView exists in this process; reattach it and keep the current document. */
        REATTACH_LIVE_SESSION,
        /** No live document, but a usable last committed URL is persisted: offer an explicit load. */
        OFFER_SAVED_URL,
        /** No live document and no usable saved URL: show the empty start state. */
        EMPTY_START
    }

    private StartupPolicy() {
    }

    public static Decision decide(boolean liveSession, String savedUrl) {
        if (liveSession) {
            return Decision.REATTACH_LIVE_SESSION;
        }
        if (NavigationPolicy.isAllowed(savedUrl)) {
            return Decision.OFFER_SAVED_URL;
        }
        return Decision.EMPTY_START;
    }
}
