package com.code2hack.eyebrowse.phone

/** Test-only readiness for one explicitly requested fixture navigation, not a retained page. */
internal class FixtureNavigationBarrier(
    val previousDocumentId: String,
    val requestedUrl: String,
    private val expectedTitle: String = "Hosting capture page",
) {
    data class Observation(
        val documentId: String,
        val displayedUrl: String?,
        val committedUrl: String?,
        val title: String?,
        val loading: Boolean,
        val live: Boolean,
        val error: String?,
    )

    // loadUrl returns before onPageStarted; neither a retained title nor !loading alone
    // establishes that this request completed. A unique URL fences older finish callbacks.
    fun isReady(observed: Observation): Boolean =
        observed.live && observed.error == null && !observed.loading &&
            observed.documentId != previousDocumentId &&
            observed.displayedUrl == requestedUrl && observed.committedUrl == requestedUrl &&
            observed.title == expectedTitle
}
