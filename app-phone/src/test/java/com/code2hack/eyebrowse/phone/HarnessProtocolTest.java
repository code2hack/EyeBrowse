package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic protocol regressions, not a simulation claimed as Android execution. */
public class HarnessProtocolTest {
    private static final String URL = "http://127.0.0.1:25341/basic.html";

    private static HarnessProtocol.Snapshot snapshot(String marker, String title, String ready) {
        return new HarnessProtocol.Snapshot(marker, title, URL, ready, 10, 20);
    }

    @Test public void firstReloadRejectsOutgoingSnapshotEvenWhenLaterStateIsSettled() {
        HarnessProtocol.Snapshot outgoing = snapshot("L20", "Basic page", "complete");
        HarnessProtocol.Baseline reload = HarnessProtocol.Baseline.reloading(outgoing, "Basic page", URL);
        // An L21 load/title callback cannot change this coherent, earlier L20 sample into a new ID.
        HarnessProtocol.Snapshot later = snapshot("L21", "Basic page", "complete");
        assertFalse(reload.accepts(outgoing));
        assertTrue(reload.accepts(later));
        assertFalse(reload.accepts(outgoing));
    }

    @Test public void reloadCannotDiscardThePreviouslyObservedIdentity() {
        assertThrows(IllegalStateException.class,
                () -> HarnessProtocol.Baseline.reloading(null, "Basic page", URL));
        assertThrows(IllegalStateException.class, () -> HarnessProtocol.Baseline.reloading(
                snapshot("L20", "Basic page", "loading"), "Basic page", URL));
    }

    @Test public void freshIdentityAlsoNeedsCoherentReadyTitleAndLocation() {
        HarnessProtocol.Baseline gate = HarnessProtocol.Baseline.reloading(
                snapshot("L20", "Basic page", "complete"), "Basic page", URL);
        assertFalse(gate.accepts(snapshot("L21", "Basic page", "interactive")));
        assertFalse(gate.accepts(snapshot("L21", "Wrong page", "complete")));
        assertFalse(gate.accepts(new HarnessProtocol.Snapshot("L21", "Basic page",
                URL + "?other", "complete", 21, 22)));
        assertFalse(gate.accepts(snapshot(null, "Basic page", "complete")));
        assertTrue(gate.accepts(snapshot("L21", "Basic page", "complete")));
    }

    @Test public void initialOpenAllowsNoPriorWebViewButExistingDocumentsAreNotIgnored() {
        assertTrue(HarnessProtocol.Baseline.opening(null, "Basic page", URL)
                .accepts(snapshot("L1", "Basic page", "complete")));
        HarnessProtocol.Snapshot prior = snapshot("L1", "Basic page", "complete");
        assertFalse(HarnessProtocol.Baseline.opening(prior, "Basic page", URL).accepts(prior));
    }

    @Test public void partialTapFailureIsPreservedAndNeverRetried() {
        HarnessProtocol.Dispatch dispatch = new HarnessProtocol.Dispatch();
        AtomicInteger down = new AtomicInteger();
        AtomicInteger up = new AtomicInteger();
        AtomicLong clock = new AtomicLong(100);
        RuntimeException failure = new RuntimeException("synthetic UP failure");
        assertSame(failure, assertThrows(RuntimeException.class, () -> dispatch.tapOnce(
                down::incrementAndGet, () -> { up.incrementAndGet(); throw failure; },
                clock::getAndIncrement)));
        assertEquals("up-attempted", dispatch.stage);
        assertEquals(100, dispatch.startMs);
        assertEquals(101, dispatch.endMs);
        assertThrows(IllegalStateException.class, () -> dispatch.tapOnce(
                down::incrementAndGet, up::incrementAndGet, clock::getAndIncrement));
        assertEquals(1, down.get());
        assertEquals(1, up.get());
    }

    @Test public void failedDownNeverAttemptsUp() {
        HarnessProtocol.Dispatch dispatch = new HarnessProtocol.Dispatch();
        AtomicInteger up = new AtomicInteger();
        assertThrows(RuntimeException.class, () -> dispatch.tapOnce(
                () -> { throw new RuntimeException("synthetic DOWN failure"); }, up::incrementAndGet,
                () -> 10L));
        assertEquals("down-attempted", dispatch.stage);
        assertEquals(0, up.get());
    }

    @Test public void successfulTapRecordsApiReturnNotAssumedWebsiteDelivery() {
        HarnessProtocol.Dispatch dispatch = new HarnessProtocol.Dispatch();
        AtomicInteger calls = new AtomicInteger();
        dispatch.tapOnce(calls::incrementAndGet, calls::incrementAndGet, () -> 10L);
        assertEquals(2, calls.get());
        assertEquals("returned", dispatch.stage);
    }

    @Test public void nativeActionFailureRemainsUnknownAndSingleShot() {
        HarnessProtocol.Dispatch dispatch = new HarnessProtocol.Dispatch();
        AtomicInteger calls = new AtomicInteger();
        assertThrows(AssertionError.class, () -> dispatch.actionOnce(
                () -> { calls.incrementAndGet(); throw new AssertionError("synthetic"); }, () -> 1L));
        assertEquals("action-attempted", dispatch.stage);
        assertThrows(IllegalStateException.class, () -> dispatch.actionOnce(calls::incrementAndGet, () -> 2L));
        assertEquals(1, calls.get());
    }

    @Test public void traceLocationOmitsQueryFragmentAndRejectsOriginLookalikes() {
        assertEquals(URL, HarnessProtocol.traceLocation(URL + "?SYNTHETIC=query#fragment", URL));
        for (String bad : new String[] {"http://127.0.0.1:25341@evil.invalid/basic.html",
                "http://127.0.0.1:253410/basic.html", "http://127.0.0.1:25341/private-id",
                "http://name:secret@127.0.0.1:25341/basic.html", "not a URI", "file:///basic.html"}) {
            assertEquals(bad, "(other)", HarnessProtocol.traceLocation(bad, URL));
        }
    }

    @Test public void missingCaptureAcknowledgementIsNotSuccess() {
        HarnessProtocol.requireRecorded(true);
        assertThrows(IllegalStateException.class, () -> HarnessProtocol.requireRecorded(false));
    }

    @Test public void captureFailureDoesNotMaskOriginalFailure() {
        AssertionError original = new AssertionError("original assertion");
        IllegalStateException capture = new IllegalStateException("missing capture");
        HarnessProtocol.preserveFailure(original, () -> { throw capture; });
        assertArrayEquals(new Throwable[] {capture}, original.getSuppressed());
    }
}
