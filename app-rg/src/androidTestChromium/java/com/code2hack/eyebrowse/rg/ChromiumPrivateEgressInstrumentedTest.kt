package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Window05 response probe only. Default Android certificate and hostname validation remain intact. */
class ChromiumPrivateEgressInstrumentedTest {
    @Test fun observeNormalTlsResponseThroughQualifiedPrivateRoute() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        require(args.getString("window05RouteBinding")?.matches(Regex("[0-9a-f]{64}")) == true)
        val report = JSONObject().put("scope", "PRIVATE_WINDOW05_HTTPS_RESPONSE_ONLY_NOT_BROWSER_ACCEPTANCE")
            .put("candidateHead", args.getString("candidateHead"))
            .put("evidenceRunId", args.getString("evidenceRunId"))
            .put("routeBinding", args.getString("window05RouteBinding"))
            .put("proxyEndpoint", "127.0.0.1:39072").put("origin", "https://chatgpt.com")
            .put("validation", "UNCHANGED_ANDROID_DEFAULT_TLS_AND_HOSTNAME_VALIDATION")
        val started = SystemClock.elapsedRealtime()
        var connection: HttpsURLConnection? = null
        try {
            connection = URL("https://chatgpt.com/").openConnection(
                Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 39072))) as HttpsURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            report.put("httpStatus", connection.responseCode).put("responseReceived", true)
            // Neither response headers/body nor shared proxy configuration are read.
        } catch (failure: Exception) {
            report.put("responseReceived", false).put("failureClass", failure.javaClass.name)
                .put("failureCauseClasses", JSONArray(generateSequence<Throwable>(failure) { it.cause }
                    .take(8).map { it.javaClass.name }.toList()))
        } finally {
            connection?.disconnect()
            report.put("elapsedMs", SystemClock.elapsedRealtime() - started)
            File(context.filesDir, "chromium-private-egress-window05-native.json").writeText(report.toString(2))
        }
    }
}
