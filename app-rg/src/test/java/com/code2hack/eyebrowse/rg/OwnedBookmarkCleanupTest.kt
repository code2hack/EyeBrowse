package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OwnedBookmarkCleanupTest {
    @get:Rule val files = System.getenv("TMPDIR")?.let { TemporaryFolder(File(it)) } ?: TemporaryFolder()
    private val owned = "https://example.com/owned-fixture"
    private val unrelated = "https://example.com/unrelated"

    private fun savedStore(): Pair<File, LocalBookmarks> {
        val file = File(files.newFolder(), "bookmarks.properties")
        val store = LocalBookmarks(file)
        assertTrue(store.save(owned, "Owned")); assertTrue(store.save(unrelated, "Unrelated"))
        return file to store
    }

    private fun blockWrites(file: File): File {
        val backup = File(file.parentFile, "original.owned")
        assertTrue(file.renameTo(backup)); assertTrue(file.mkdir())
        File(file, "blocker.owned").writeText("owned")
        return backup
    }

    @Test fun confirmedRemovalReleasesOwnershipAndStillDeletesBlocker() {
        val (file, store) = savedStore()
        val blocker = files.newFile(); var added = true
        withOwnedBookmarkCleanup(body = {
            assertTrue(store.remove(owned))
            confirmOwnedBookmarkRemoved(store, file, owned); added = false
        }, restoreAndRemove = {
            if (added) removeOwnedBookmark(store, file, owned)
        }, deleteBlocker = { assertTrue(blocker.delete()) })
        assertFalse(added); assertFalse(blocker.exists())
        assertFalse(LocalBookmarks(file).contains(owned)); assertTrue(LocalBookmarks(file).contains(unrelated))
    }

    @Test fun failedRemovalKeepsOwnershipAndRecoveredCleanupPreservesCurrentUnrelatedEntries() {
        val (file, store) = savedStore()
        val backup = blockWrites(file); val blocker = files.newFile(); var added = true
        val latest = "https://example.com/another-unrelated"
        val failure = assertThrows(IllegalStateException::class.java) {
            withOwnedBookmarkCleanup(body = {
                assertFalse("actual failed atomic replacement", store.remove(owned))
                confirmOwnedBookmarkRemoved(store, file, owned); added = false
            }, restoreAndRemove = {
                assertTrue("ownership survives failed native removal", added)
                assertTrue(File(file, "blocker.owned").delete()); assertTrue(file.delete())
                assertTrue(backup.renameTo(file))
                assertTrue(LocalBookmarks(file).save(latest, "Latest"))
                removeOwnedBookmark(store, file, owned); added = false
            }, deleteBlocker = { assertTrue(blocker.delete()) })
        }
        assertEquals("Owned fixture bookmark removal not confirmed in memory", failure.message)
        assertTrue(failure.suppressed.isEmpty()); assertFalse(added); assertFalse(blocker.exists())
        val disk = LocalBookmarks(file)
        assertFalse(disk.contains(owned)); assertTrue(disk.contains(unrelated)); assertTrue(disk.contains(latest))
    }

    @Test fun persistentStorageAndBlockerFailuresRemainExplicitWithoutConcealingOriginalFailure() {
        val (file, store) = savedStore()
        val backup = blockWrites(file); val originalBytes = backup.readBytes()
        val blocker = files.newFolder(); File(blocker, "child.owned").writeText("owned")
        var added = true; var blockerAttempted = false
        val failure = assertThrows(IllegalStateException::class.java) {
            withOwnedBookmarkCleanup(body = {
                assertFalse(store.remove(owned))
                confirmOwnedBookmarkRemoved(store, file, owned); added = false
            }, restoreAndRemove = {
                if (added) { removeOwnedBookmark(store, file, owned); added = false }
            }, deleteBlocker = {
                blockerAttempted = true
                check(blocker.delete()) { "Owned failure file cleanup unresolved" }
            })
        }
        assertEquals("Owned fixture bookmark removal not confirmed in memory", failure.message)
        assertEquals(listOf("Owned fixture bookmark cleanup unresolved: storage unreadable",
            "Owned failure file cleanup unresolved"), failure.suppressed.map { it.message })
        assertTrue(added); assertTrue(blockerAttempted)
        assertArrayEquals(originalBytes, backup.readBytes())
        assertTrue(LocalBookmarks(backup).contains(owned)); assertTrue(LocalBookmarks(backup).contains(unrelated))
    }
}
