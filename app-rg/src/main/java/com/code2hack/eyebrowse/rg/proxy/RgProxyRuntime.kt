package com.code2hack.eyebrowse.rg.proxy

import android.content.Context
import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One app-local foreground owner. Neither node changes nor health selection touch WebViews/forms. */
internal class RgProxyRuntime private constructor(private val context: Context) {
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "EyeBrowse-Mihomo").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    private val directory = File(context.filesDir, "mihomo")
    @Volatile private var users = 0
    @Volatile private var process: Process? = null
    @Volatile private var stopping: Process? = null
    @Volatile private var control: CoreControl? = null
    @Volatile var proxyPort: Int? = null
        private set
    @Volatile var webViewPort: Int? = null
        private set
    @Volatile var status = "Stopped"
        private set
    private var webConfiguring = false // Main-thread only; coalesce normal start/stop callbacks.
    private var nodes = emptyList<JSONObject>() // Owned deep copies, not profile storage.
    private var health: ScheduledFuture<*>? = null
    private val checkingConnection = AtomicBoolean(false)
    internal var testUrl: String = ProxyPolicy.TEST_URL
        private set

    @Synchronized fun acquire() {
        users++
        if (users == 1) worker.execute { if (users > 0 && process == null) start() }
    }

    @Synchronized fun release() {
        check(users > 0)
        users--
        if (users == 0) {
            status = "Stopped"
            health?.cancel(false)
            stopProcess()
        }
    }

    fun replaceSources(sources: List<ProxySource>): CompletableFuture<Boolean> {
        val result = CompletableFuture<Boolean>()
        val candidate = try { ProxyPolicy.nodes(sources) } catch (_: Exception) {
            result.complete(false); return result
        }
        worker.execute {
            try {
                val port = proxyPort
                if (port != null && process?.isAlive == true) {
                    val file = File(directory, "candidate.json")
                    writeConfiguration(file, candidate, port)
                    control!!.request("PUT", "/configs", JSONObject().put("path", file.absolutePath))
                }
                nodes = candidate
                if (port != null && process?.isAlive == true) checkHealth()
                result.complete(true)
            } catch (_: Exception) {
                // An invalid replacement is refused by the genuine core before applying it.
                // Do not log the config/core exception; it may contain subscription credentials.
                result.complete(false)
            }
        }
        return result
    }

    /** Explicit route for subscription and regional-data HTTP clients; never the global ProxySelector. */
    fun openConnection(url: URL): HttpURLConnection {
        require(url.protocol == "http" || url.protocol == "https") { "Unsupported HTTP scheme" }
        val port = proxyPort.takeIf { process?.isAlive == true }
        val proxy = port?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", it)) } ?: Proxy.NO_PROXY
        return (url.openConnection(proxy) as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000; instanceFollowRedirects = false
        }
    }

    fun onNetworkError() {
        if (proxyPort == null || !checkingConnection.compareAndSet(false, true)) return
        worker.execute {
            try { control?.request("GET", "/version", timeoutMs = 2000) }
            catch (_: Exception) { status = "Core unavailable; browsing directly"; stopProcess() }
            finally { checkingConnection.set(false) }
        }
    }

    internal fun checkHealthNow(): CompletableFuture<Boolean> = CompletableFuture<Boolean>().also { result ->
        worker.execute { checkHealth(); result.complete(process?.isAlive == true) }
    }

    private fun start() {
        try {
            status = "Starting"
            stopping?.let { old ->
                if (!old.waitFor(2, TimeUnit.SECONDS)) { old.destroyForcibly(); check(old.waitFor(2, TimeUnit.SECONDS)) }
                stopping = null
            }
            directory.mkdirs()
            for ((name, digest) in RULE_HASHES) {
                val destination = File(directory, name)
                val bytes = context.assets.open("mihomo/$name").use { it.readBytes() }
                check(sha256(bytes) == digest) { "Bundled rules invalid" }
                if (!destination.isFile) destination.writeBytes(bytes) // Keep last-good rule updates across foreground stops.
            }
            val binary = File(context.applicationInfo.nativeLibraryDir, "libmihomo.so")
            check(binary.isFile && binary.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var count = input.read(buffer)
                while (count >= 0) { digest.update(buffer, 0, count); count = input.read(buffer) }
                digest.digest().joinToString("") { "%02x".format(it) }
            } == CORE_HASH) { "Bundled core invalid" }
            val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val file = File(directory, "config.json")
            writeConfiguration(file, nodes, port)
            if (users == 0) return
            val child = ProcessBuilder(binary.absolutePath, "-d", directory.absolutePath, "-f", file.absolutePath)
                .redirectErrorStream(true).redirectOutput(File("/dev/null"))
                .apply { environment()["GOMAXPROCS"] = "2" }.start()
            process = child
            val api = CoreControl(File(directory, "control.sock").absolutePath)
            control = api
            Thread({
                child.waitFor()
                if (process === child) {
                    status = "Core stopped; browsing directly"
                    stopProcess()
                }
            }, "EyeBrowse-Mihomo-exit").apply { isDaemon = true; start() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var ready = false
            while (users > 0 && child.isAlive && System.nanoTime() < deadline) {
                try {
                    check(api.request("GET", "/version", timeoutMs = 1000).getString("version") == "v1.19.32")
                    check(api.request("GET", "/configs", timeoutMs = 1000).getInt("mixed-port") == port)
                    ready = true; break
                } catch (_: Exception) { Thread.sleep(50) }
            }
            check(ready && users > 0 && child.isAlive) { "Core startup unavailable" }
            // /version is served before the core finishes loading providers and starts forwarding.
            // Once its listener exists, this synchronous command serializes behind that load.
            api.request("PUT", "/configs", JSONObject().put("path", file.absolutePath), timeoutMs = 10_000)
            check(api.request("GET", "/configs").getInt("mixed-port") == port && users > 0 && child.isAlive)
            proxyPort = port
            syncWebView()
            checkHealth()
            if (users == 0 || process !== child) return
            health?.cancel(false)
            health = worker.scheduleWithFixedDelay({ checkHealth() }, ProxyPolicy.HEALTH_SECONDS,
                ProxyPolicy.HEALTH_SECONDS, TimeUnit.SECONDS)
            status = "Running"
        } catch (_: Exception) {
            status = "Core unavailable; browsing directly"
            stopProcess()
        }
    }

    private fun writeConfiguration(file: File, candidates: List<JSONObject>, port: Int) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val dns = cm.getLinkProperties(cm.activeNetwork)?.dnsServers.orEmpty().mapNotNull { it.hostAddress }
        val config = ProxyPolicy.configurationText(ProxyPolicy.configuration(candidates, port,
            File(directory, "control.sock").absolutePath, dns, testUrl).toString()).toByteArray(Charsets.UTF_8)
        check(config.size <= 4 * 1024 * 1024) { "Proxy configuration too large" }
        file.writeBytes(config)
    }

    private fun checkHealth() {
        if (nodes.isEmpty() || users == 0 || process?.isAlive != true) return
        try {
            val api = checkNotNull(control)
            ProxyPolicy.applyHealth(nodes.size, testUrl, api::request)
        } catch (_: Exception) {
            // Control failure must not leave ordinary browsing at a dead loopback proxy.
            status = "Core unavailable; browsing directly"
            stopProcess()
        }
    }

    @Synchronized private fun stopProcess() {
        health?.cancel(false)
        proxyPort = null
        val old = process
        process = null
        if (old != null) stopping = old
        control?.close(); control = null
        old?.destroy()
        syncWebView()
    }

    private fun syncWebView() {
        main.post {
            if (webConfiguring) return@post
            val supported = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
            if (!supported) {
                status = "WebView proxy API unavailable; browser uses direct routing"
                webViewPort = null
                return@post
            }
            val desired = proxyPort
            webConfiguring = true
            val complete = Runnable {
                webConfiguring = false
                webViewPort = desired
                if (desired != proxyPort) syncWebView()
            }
            try {
                @SuppressLint("RequiresFeature") // Checked above; lint does not follow return@post.
                val controller = ProxyController.getInstance()
                if (desired == null) controller.clearProxyOverride(context.mainExecutor, complete)
                else controller.setProxyOverride(ProxyConfig.Builder()
                    .addProxyRule("http://127.0.0.1:$desired").removeImplicitRules().build(), context.mainExecutor, complete)
            } catch (_: Exception) {
                webConfiguring = false
                status = "WebView proxy configuration unavailable"
                // No request replay: a later explicit navigation uses direct routing after cleanup.
                if (desired != null) stopProcess()
            }
        }
    }

    /** Test-only endpoint choice; production always uses the documented common URL. */
    internal fun useTestEndpoint(url: String) { check(users == 0); testUrl = url }

    companion object {
        private const val CORE_HASH = "bb9afbd48717f92d59e07da35fb694b83d40d6edb63eb9033c2ee4174137d195"
        private val RULE_HASHES = mapOf(
            "cn-domain.mrs" to "e92bda4f0b9f05623663a882331a320ab4e266cada2046d3828a94572cdded46",
            "cn-ip.mrs" to "d95b3d125f6c74ce3c15e19ef794f142de91e21c3b8fb7767e76aebfabff5149",
        )
        @SuppressLint("StaticFieldLeak") // Retains applicationContext only, never an Activity.
        private var instance: RgProxyRuntime? = null
        @Synchronized fun get(context: Context): RgProxyRuntime = instance ?: RgProxyRuntime(context.applicationContext).also { instance = it }
        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
