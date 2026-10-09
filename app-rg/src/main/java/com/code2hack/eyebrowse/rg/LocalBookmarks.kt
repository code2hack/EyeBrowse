package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.browser.AddressPolicy
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** App-private durable bookmarks. Exact normalized URLs retain path/query/fragment distinctions. */
internal class LocalBookmarks(private val file: File) {
    class Entry(val url: String, val title: String) {
        override fun toString() = "Bookmark(location/title redacted)"
    }
    private var saved = linkedMapOf<String, String>()
    private var readable = false
    var error: String? = null
        private set
    val entries get() = saved.map { (url, title) -> Entry(url, title) }

    init { reload() }
    fun contains(url: String) = saved.containsKey(url)

    fun reload(): Boolean = try {
        val properties = Properties()
        if (file.exists()) file.inputStream().use(properties::load)
        val loaded = linkedMapOf<String, String>()
        for (url in properties.stringPropertyNames().sorted()) {
            val resolved = AddressPolicy.resolve(url)
            require(resolved.accepted() && resolved.url() == url)
            loaded[url] = properties.getProperty(url)
        }
        saved = loaded; readable = true; error = null
        true
    } catch (_: IOException) { unavailable() }
      catch (_: IllegalArgumentException) { unavailable() }
      catch (_: SecurityException) { unavailable() }

    private fun unavailable(): Boolean {
        readable = false; error = "Bookmarks could not be read. Retry to recover."
        return false
    }

    fun save(url: String, title: String): Boolean {
        val resolved = AddressPolicy.resolve(url)
        if (!resolved.accepted() || resolved.url() != url) {
            error = "This page cannot be bookmarked."
            return false
        }
        return write(LinkedHashMap(saved).apply { put(url, title.ifEmpty { url }) })
    }

    fun remove(url: String) = write(LinkedHashMap(saved).apply { remove(url) })

    private fun write(next: LinkedHashMap<String, String>): Boolean {
        if (!readable) return false // Never overwrite an unreadable existing bookmark file.
        var temporary: File? = null
        return try {
            val parent = checkNotNull(file.parentFile)
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Bookmark directory unavailable")
            temporary = File.createTempFile("bookmarks-", ".pending", parent)
            val properties = Properties().apply { next.forEach { (url, title) -> setProperty(url, title) } }
            FileOutputStream(temporary).use { stream ->
                properties.store(stream, null)
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            saved = next; error = null
            true
        } catch (_: IOException) {
            error = "Bookmark change could not be saved. Try again."
            false
        } catch (_: SecurityException) {
            error = "Bookmark change could not be saved. Try again."
            false
        } finally { temporary?.delete() }
    }
}
