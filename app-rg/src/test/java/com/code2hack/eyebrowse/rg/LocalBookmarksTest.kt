package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalBookmarksTest {
    @get:Rule val files = System.getenv("TMPDIR")?.let { TemporaryFolder(File(it)) } ?: TemporaryFolder()

    @Test fun saveReloadRemovePreserveUnicodeAndMeaningfulUrlComponents() {
        val file = File(files.newFolder(), "bookmarks.properties")
        val store = LocalBookmarks(file)
        val urls = listOf("https://example.com/path?a=1#one", "https://example.com/path?a=2#one",
            "https://example.com/path?a=1#two")
        urls.forEach { assertTrue(store.save(it, "Title café 漢字")) }
        assertTrue(store.save(urls[0], "Updated")); assertEquals(3, store.entries.size)
        val restored = LocalBookmarks(file)
        assertNull(restored.error); assertEquals(3, restored.entries.size)
        assertEquals("Title café 漢字", restored.entries.first { it.url == urls[1] }.title)
        assertTrue(restored.remove(urls[1]))
        assertFalse(LocalBookmarks(file).contains(urls[1])); assertTrue(LocalBookmarks(file).contains(urls[2]))
    }

    @Test fun actualFilesystemWriteFailureNeverProducesSavedSuccess() {
        val parent = files.newFile("not-a-directory")
        val store = LocalBookmarks(File(parent, "bookmarks.properties"))
        assertFalse(store.save("https://example.com/", "Example"))
        assertFalse(store.contains("https://example.com/")); assertNotNull(store.error)
    }

    @Test fun unreadableCorruptFileIsNotSilentlyReplacedAndCanBeRetried() {
        val file = files.newFile("bookmarks.properties"); file.writeText("broken=\\uZZZZ")
        val original = file.readBytes(); val store = LocalBookmarks(file)
        assertNotNull(store.error); assertFalse(store.save("https://example.com/", "Example"))
        assertArrayEquals(original, file.readBytes())
        file.writeText(""); assertTrue(store.reload()); assertTrue(store.save("https://example.com/", "Example"))
        assertTrue(LocalBookmarks(file).contains("https://example.com/"))
    }
}
