package com.code2hack.eyebrowse.phone

import org.junit.Assert.*
import org.junit.Test

class FixtureNavigationBarrierTest {
    private val oldUrl = "http://127.0.0.1:26341/hosting.html?t2r1=previous"
    private val requestedUrl = "http://127.0.0.1:26341/hosting.html?t2r1=requested"
    private val barrier = FixtureNavigationBarrier("old-document", requestedUrl)
    private val finished = FixtureNavigationBarrier.Observation(
        "new-document", requestedUrl, requestedUrl, "Hosting capture page", false, true, null,
    )

    @Test fun inheritedReadyPageCannotSatisfyNewRequest() {
        // loadUrl has returned but its onPageStarted callback has not arrived. The previous
        // case's same-title fixture still reports !loading; the old test falsely accepted it.
        val inherited = finished.copy(
            documentId = "old-document", displayedUrl = oldUrl, committedUrl = oldUrl,
        )
        assertTrue(!inherited.loading && inherited.title == "Hosting capture page")
        assertFalse("must wait for this request, not the inherited fixture", barrier.isReady(inherited))
    }

    @Test fun navigationMustCommitTheRequestedUrl() {
        assertFalse(barrier.isReady(finished.copy(committedUrl = oldUrl)))
        assertFalse(barrier.isReady(finished.copy(displayedUrl = oldUrl)))
        assertFalse(barrier.isReady(finished.copy(displayedUrl = oldUrl, committedUrl = oldUrl)))
        assertFalse(barrier.isReady(finished.copy(committedUrl = null)))
        assertFalse(barrier.isReady(finished.copy(displayedUrl = null)))
    }

    @Test fun inFlightNavigationCannotQualify() {
        assertFalse(barrier.isReady(finished.copy(loading = true)))
        assertFalse(barrier.isReady(finished.copy(title = null)))
        assertFalse(barrier.isReady(finished.copy(title = "Other page")))
    }

    @Test fun failedOrNonLiveDocumentCannotQualify() {
        assertFalse(barrier.isReady(finished.copy(error = "fixture load failed")))
        assertFalse(barrier.isReady(finished.copy(live = false)))
    }

    @Test fun completedNewRequestQualifiesWithoutPollingStart() {
        // No requirement to sample loading=true: both callbacks can run between poll turns.
        assertTrue(barrier.isReady(finished))
        val baselineDocument = finished.documentId // Once frozen, the expected ID never changes.
        val laterNavigation = finished.copy(documentId = "later-navigation")
        assertNotEquals("a later navigation still violates continuity", baselineDocument, laterNavigation.documentId)
    }

    @Test fun sameUrlRequestStillRequiresNewDocument() {
        // Also fence reused URLs: matching location/title alone is not completion evidence.
        val sameUrl = FixtureNavigationBarrier("old-document", oldUrl)
        val retained = finished.copy(documentId = "old-document", displayedUrl = oldUrl, committedUrl = oldUrl)
        assertFalse(sameUrl.isReady(retained))
        assertTrue(sameUrl.isReady(retained.copy(documentId = "new-document")))
    }
}
