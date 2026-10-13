package com.code2hack.eyebrowse.rg.proxy

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture

/** One serialized owner survives Activity recreation; its IO thread expires after each operation. */
internal class RgProfiles private constructor(context: Context) {
    private val runtime = RgProxyRuntime.get(context)
    private val preferences = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
    private val store = ProfileStore({ preferences.getString("profiles", null) }) {
        val previous = preferences.getString("profiles", null)
        val saved = runCatching { preferences.edit().putString("profiles", it).commit() }.getOrDefault(false)
        if (!saved) {
            // commit() updates memory before reporting a disk failure. Restore that view too.
            preferences.edit().apply {
                if (previous == null) remove("profiles") else putString("profiles", previous)
            }.commit()
        }
        saved
    }
    private val worker = ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
        Thread(task, "EyeBrowse-profile-import").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>() // Main-thread Activity observers only.
    private var refresh: CompletableFuture<Int>? = null
    // Opt-in in-process instrumentation inspection. It never receives the source URL, persists
    // a document, or changes import behavior. The ordinary app leaves this unset.
    @Volatile internal var inspectDownload: ((Int, String?, String) -> Unit)? = null
    @Volatile var profiles = store.profiles
        private set
    @Volatile var busy = false
        private set
    @Volatile var message: String? = if (store.readable) null else "Profiles could not be read. Existing data kept."
        private set

    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun unobserve(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyChanged() { main.post { listeners.toList().forEach { it() } } }

    fun activate(): CompletableFuture<Unit> = CompletableFuture<Unit>().also { result ->
        worker.execute {
            try {
                store.reload()
                profiles = store.profiles
                if (!store.readable) message = "Profiles could not be read. Existing data kept."
                if (!runtime.replaceSources(store.profiles.map { it.asSource() }).get())
                    message = "Saved profiles could not be applied. Existing data kept."
            } catch (_: Exception) { message = "Saved profiles could not be applied. Existing data kept." }
            notifyChanged()
            result.complete(Unit)
        }
    }

    fun import(payload: String) = change("Importing…") {
        val source = try { ProfileImport.source(payload) } catch (_: Exception) {
            error("Unsupported profile URL. Use HTTP(S) or a Clash install link.")
        }
        val text = try { fetch(source) } catch (_: Exception) {
            error("Profile download failed. Existing data kept.")
        }
        val nodes = try { ProfileImport.nodes(text) } catch (_: Exception) {
            error("Unsupported or invalid profile. A usable YAML proxies list is required.")
        }
        if (!runtime.validateSource(ProxySource(source, true, nodes)).get())
            error("Profile nodes were rejected. Existing data kept.")
        store.imported(source, nodes)
    }

    fun toggle(source: String) = change("Saving…") {
        store.profiles.map { if (it.source == source) it.enabled(!it.enabled) else it }
    }
    fun delete(source: String) = change("Saving…") { store.profiles.filter { it.source != source } }
    @Synchronized internal fun idle(): CompletableFuture<Int> = refresh ?: CompletableFuture.completedFuture(0)

    /** Jobs, launch and connectivity share the same writer; no core subscription loop exists. */
    @Synchronized fun refreshSaved(): CompletableFuture<Int> {
        refresh?.let { return it }
        val result = CompletableFuture<Int>()
        refresh = result
        worker.execute {
            var updated = 0
            try {
                store.reload()
                check(store.readable)
                runtime.checkHealthNow().get() // Refresh stale all-down selection before a bootstrap download.
                for (source in store.profiles.map { it.source }) {
                    try {
                        val nodes = ProfileImport.nodes(fetch(source))
                        if (!runtime.validateSource(ProxySource(source, true, nodes)).get()) continue
                        val candidate = store.refreshed(source, nodes) // Never adds a deleted source.
                        if (candidate.none { it.source == source }) continue
                        applyAndSave(candidate)
                        updated++
                    } catch (_: Exception) { /* One failed source keeps its cache; other sources still run. */ }
                }
                profiles = store.profiles
                result.complete(updated)
            } catch (_: Exception) { result.completeExceptionally(IllegalStateException("Saved data kept")) }
            finally { synchronized(this) { refresh = null }; notifyChanged() }
        }
        return result
    }

    @Synchronized private fun change(progress: String, next: () -> List<SavedProfile>) {
        if (busy || !store.readable) return
        busy = true; message = progress; notifyChanged()
        worker.execute {
            try {
                applyAndSave(next())
                message = "Profile saved."
            } catch (failure: IllegalStateException) {
                // Only our fixed public messages are allowed out of this boundary.
                message = failure.message?.takeIf { it in publicErrors } ?: "Profile change failed. Existing data kept."
            } catch (_: Exception) { message = "Profile change failed. Existing data kept." }
            finally { busy = false; notifyChanged() }
        }
    }

    private fun applyAndSave(candidate: List<SavedProfile>) {
        val previous = store.profiles
        if (!store.canSave(candidate)) error("Profile change could not be saved. Existing data kept.")
        // Core/health calls already have bounds; an outer timeout could abandon an applied pool.
        if (!runtime.replaceSources(candidate.map { it.asSource() }).get())
            error("Profile change could not be applied. Existing data kept.")
        if (!runCatching { store.save(candidate) }.getOrDefault(false)) {
            check(runtime.replaceSources(previous.map { it.asSource() }).get())
            error("Profile change could not be saved. Existing data kept.")
        }
        profiles = store.profiles
    }

    private fun fetch(source: String): String {
        var url = URL(source)
        repeat(6) { hop ->
            val connection = runtime.openConnection(url)
            try {
                connection.setRequestProperty("Accept", "application/yaml, text/yaml, text/plain")
                // Identify the supported subscription representation instead of Android's
                // generic client, which some servers use to choose an encoded format.
                connection.setRequestProperty("User-Agent", "mihomo/1.19.32 EyeBrowse/0.0.3")
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) runCatching { inspectDownload?.invoke(status, connection.contentType, "") }
                when (status) {
                    in setOf(301, 302, 303, 307, 308) -> {
                        check(hop < 5)
                        val next = URL(url, checkNotNull(connection.getHeaderField("Location")))
                        check(url.protocol != "https" || next.protocol == "https")
                        url = URL(ProfileImport.source(next.toString()))
                    }
                    HttpURLConnection.HTTP_OK -> {
                        require(connection.contentLengthLong <= ProfileImport.MAX_BYTES)
                        val bytes = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= ProfileImport.MAX_BYTES)
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        require(bytes.size <= ProfileImport.MAX_BYTES)
                        val document = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                        runCatching { inspectDownload?.invoke(status, connection.contentType, document) }
                        return document
                    }
                    else -> error("HTTP failure")
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }

    companion object {
        private val publicErrors = setOf(
            "Unsupported profile URL. Use HTTP(S) or a Clash install link.",
            "Profile download failed. Existing data kept.",
            "Unsupported or invalid profile. A usable YAML proxies list is required.",
            "Profile nodes were rejected. Existing data kept.",
            "Profile change could not be applied. Existing data kept.",
            "Profile change could not be saved. Existing data kept.",
        )
        @Volatile private var instance: RgProfiles? = null
        fun get(context: Context): RgProfiles = instance ?: synchronized(this) {
            instance ?: RgProfiles(context.applicationContext).also { instance = it }
        }
    }
}
