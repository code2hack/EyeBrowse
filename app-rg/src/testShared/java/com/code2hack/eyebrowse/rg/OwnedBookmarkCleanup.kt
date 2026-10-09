package com.code2hack.eyebrowse.rg

import java.io.File

/** Test-only cleanup of the previously absent fixture entry; never replace an unreadable store. */
internal fun confirmOwnedBookmarkRemoved(store: LocalBookmarks, file: File, url: String) {
    check(!store.contains(url)) { "Owned fixture bookmark removal not confirmed in memory" }
    val disk = LocalBookmarks(file)
    check(disk.error == null && !disk.contains(url)) { "Owned fixture bookmark removal not confirmed on disk" }
}

internal fun removeOwnedBookmark(store: LocalBookmarks, file: File, url: String) {
    // Reconcile the current file before removing only our entry, preserving any other entries.
    check(store.reload()) { "Owned fixture bookmark cleanup unresolved: storage unreadable" }
    if (store.contains(url)) {
        check(store.remove(url)) { "Owned fixture bookmark cleanup unresolved: removal could not be saved" }
    }
    confirmOwnedBookmarkRemoved(store, file, url)
}

/** Attempt both obligations, retaining the original failure and every cleanup failure. */
internal fun withOwnedBookmarkCleanup(body: () -> Unit, restoreAndRemove: () -> Unit, deleteBlocker: () -> Unit) {
    var failure: Throwable? = null
    try { body() } catch (caught: Throwable) { failure = caught }
    for (cleanup in listOf(restoreAndRemove, deleteBlocker)) {
        try { cleanup() } catch (caught: Throwable) {
            val original = failure
            if (original == null) failure = caught else if (original !== caught) original.addSuppressed(caught)
        }
    }
    failure?.let { throw it }
}
