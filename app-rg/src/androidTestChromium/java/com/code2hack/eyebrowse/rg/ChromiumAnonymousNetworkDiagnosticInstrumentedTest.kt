package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.security.MessageDigest

/** Anonymous GET transport evidence only; no cookies, credentials or browser success claim. */
class ChromiumAnonymousNetworkDiagnosticInstrumentedTest {
    @Test fun compareApprovedPublicResourcesThroughOwnedRoutes() {
        val args = InstrumentationRegistry.getArguments()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val site = checkNotNull(args.getString("anonymousSite"))
        val root = when (site) {
            "paseo" -> URL("https://app.paseo.sh/")
            "chatgpt" -> URL("https://chatgpt.com/")
            else -> error("Only approved public roots")
        }
        val results = JSONArray()
        val runtime = RgProxyRuntime.get(context)
        ActivityScenario.launch(LocalBrowserActivity::class.java).use {
            val configured = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < configured &&
                (runtime.status == "Starting" || runtime.proxyPort != runtime.webViewPort)) SystemClock.sleep(25)
            fun observe(url: URL, route: String): String? {
                val started = SystemClock.elapsedRealtime()
                val row = JSONObject().put("origin", "${url.protocol}://${url.host}")
                    .put("resource", if (url.path == "/") "ROOT" else "PUBLIC_STATIC_SCRIPT")
                    .put("route", route).put("coreStatus", runtime.status)
                    .put("corePort", runtime.proxyPort).put("webViewPort", runtime.webViewPort)
                var connection: HttpURLConnection? = null
                var body: String? = null
                try {
                    connection = if (route == "OWNED_PROXY") runtime.openConnection(url)
                        else url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
                    connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                    connection.instanceFollowRedirects = false
                    val status = connection.responseCode
                    row.put("status", status).put("contentLength", connection.contentLengthLong)
                        .put("contentEncoding", connection.contentEncoding)
                    val digest = MessageDigest.getInstance("SHA-256")
                    val prefix = java.io.ByteArrayOutputStream()
                    var total = 0L
                    val stream = if (status >= 400) connection.errorStream else connection.inputStream
                    stream?.use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            check(SystemClock.elapsedRealtime() - started < 60_000) { "Bounded anonymous GET" }
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count; check(total <= 32L * 1024 * 1024)
                            digest.update(buffer, 0, count)
                            if (url.path == "/" && prefix.size() + count <= 256 * 1024) prefix.write(buffer, 0, count)
                        }
                    }
                    row.put("bytes", total).put("bodySHA256", digest.digest().joinToString("") { "%02x".format(it) })
                        .put("complete", true)
                    if (status == 200 && url.path == "/") body = prefix.toString("UTF-8")
                } catch (failure: Exception) {
                    row.put("complete", false).put("failureClass", failure.javaClass.name)
                    // Exception text can include server content or URLs; retain only its type.
                } finally {
                    connection?.disconnect()
                    row.put("elapsedMs", SystemClock.elapsedRealtime() - started)
                    results.put(row)
                }
                return body
            }
            for (route in listOf("OWNED_PROXY", "DIRECT")) {
                val body = observe(root, route)
                if (site == "paseo" && body != null) {
                    val path = Regex("/_expo/static/js/web/index-[0-9a-f]+\\.js").find(body)?.value
                    path?.let { observe(URL(root, it), route) }
                }
            }
        }
        File(context.filesDir, "chromium-anonymous-network-$site.json").writeText(JSONObject()
            .put("scope", "ANONYMOUS_GET_TRANSPORT_ONLY_NOT_BROWSER_SUCCESS")
            .put("candidateHead", args.getString("candidateHead"))
            .put("evidenceRunId", args.getString("evidenceRunId"))
            .put("site", site).put("results", results).toString(2))
    }
}
