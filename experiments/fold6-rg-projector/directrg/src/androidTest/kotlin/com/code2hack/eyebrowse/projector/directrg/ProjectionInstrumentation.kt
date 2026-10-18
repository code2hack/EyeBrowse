package com.code2hack.eyebrowse.projector.directrg

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.code2hack.eyebrowse.projector.protocol.Tls
import com.code2hack.eyebrowse.projector.protocol.Wire
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import org.json.JSONArray
import org.json.JSONObject

/** Explicit bounded on-RG test driver. No input/media loop runs on a computer. */
class ProjectionInstrumentation : Instrumentation() {
    private lateinit var args: Bundle
    override fun onCreate(arguments: Bundle?) { args = arguments ?: Bundle(); super.onCreate(arguments); start() }
    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun clickConnect(activity: Activity) = runOnMainSync {
        buttons(activity.window.decorView).single { it.text == "Connect" }.performClick()
    }
    private fun status(): JSONObject = JSONObject(File(targetContext.filesDir, "status.json").readText())
    private fun waitStreaming() {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (runCatching { status().optString("state") == "STREAMING" }.getOrDefault(false)) return
            Thread.sleep(100)
        }
        error("No rendered stream")
    }
    private fun unauthenticated(): Boolean {
        val directory = File(targetContext.filesDir, "session")
        val certificate = File(directory, "peer.crt").inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("host", certificate) }
        val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
        val context = SSLContext.getInstance("TLS").apply { init(null, managers.trustManagers, null) }
        val endpoint = Tls.settings(directory)
        return try {
            (context.socketFactory.createSocket(endpoint.getProperty("host"), endpoint.getProperty("port").toInt()) as SSLSocket).use { socket ->
                socket.soTimeout = 3000
                socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                socket.startHandshake()
                Wire.writeRequest(DataOutputStream(socket.outputStream), Wire.STREAM)
                Wire.readWelcome(DataInputStream(socket.inputStream))
            }
            false
        } catch (_: javax.net.ssl.SSLException) { true }
    }

    override fun onStart() {
        val report = JSONObject().put("passed", false).put("driver", "on-RG Kotlin instrumentation")
        var activity: Activity? = null
        var file: File? = null
        var ownsRecord = false
        try {
            val id = args.getString("runId") ?: error("Run identity required")
            require(id.matches(Regex("[A-Za-z0-9_-]{1,48}")))
            file = File(targetContext.filesDir, "probe-$id.json")
            require(file.createNewFile()) { "Existing test record" }
            ownsRecord = true
            val operation = args.getString("operation") ?: "stream"
            report.put("operation", operation)
            if (operation == "tls-diagnostic") {
                report.put("phase", "LOAD_PLATFORM_TLS_CONTEXT")
                Tls.context(File(targetContext.filesDir, "session"))
                report.put("contextLoaded", true).put("phase", "AUTHENTICATED_HANDSHAKE")
                Tls.connect(File(targetContext.filesDir, "session")).use { report.put("tls", it.session.protocol) }
            } else if (operation == "unauthenticated") {
                require(unauthenticated()); report.put("unauthenticatedRefused", true).put("mediaWelcomeReceived", false)
            } else {
                require(operation in listOf("stream", "pause", "reconnect", "local-stop", "host-stop"))
                val seconds = args.getString("seconds")?.toInt() ?: 10
                require(seconds in 1..660)
                val intent = Intent().setClassName(targetContext.packageName, ReceiverActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity = startActivitySync(intent); waitForIdleSync(); Thread.sleep(250)
                clickConnect(activity); waitStreaming()
                report.put("first", status())
                if (operation == "reconnect") {
                    runOnMainSync { activity!!.finish() }; Thread.sleep(1500)
                    require(status().getString("state") == "STOPPED")
                    activity = startActivitySync(intent); waitForIdleSync(); Thread.sleep(250)
                    clickConnect(activity); waitStreaming()
                    require(status().getLong("touchesSent") == 0L)
                    report.put("reconnected", status())
                }
                if (args.getString("waitForGo") == "true") {
                    val go = File(targetContext.filesDir, "probe-$id.go")
                    val deadline = SystemClock.elapsedRealtime() + 60_000
                    while (!go.isFile && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
                    require(go.isFile) { "Bounded fixture setup did not complete" }
                    report.put("fixtureSetupBeforeMeasurement", true)
                }
                val start = SystemClock.elapsedRealtime()
                val samples = JSONArray()
                val tapAt = args.getString("tapAtSeconds")?.toInt() ?: -1
                val x = args.getString("tapX")?.toFloat() ?: 240f
                val y = args.getString("tapY")?.toFloat() ?: 320f
                require(x in 0f..<480f && y in 0f..<640f)
                var tapped = false; var nextSample = 0L
                while (SystemClock.elapsedRealtime() - start < seconds * 1000L) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    if (tapAt >= 0 && !tapped && elapsed >= tapAt * 1000L) {
                        val now = SystemClock.uptimeMillis()
                        sendPointerSync(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
                        sendPointerSync(MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, x, y, 0))
                        report.put("onRGTapElapsedMs", elapsed); tapped = true
                    }
                    if (elapsed >= nextSample) {
                        val sample = status(); require(sample.getString("state") == "STREAMING")
                        samples.put(sample); nextSample = elapsed + 10_000
                    }
                    Thread.sleep(100)
                }
                report.put("observedStreamingMs", SystemClock.elapsedRealtime() - start).put("last", status()).put("samples", samples)
                if (operation == "pause") {
                    targetContext.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    Thread.sleep(1500); require(status().getString("state") == "STOPPED")
                    report.put("paused", status())
                }
                if (operation == "local-stop") {
                    runOnMainSync { buttons(activity!!.window.decorView).single { it.text == "Stop" }.performClick() }
                    Thread.sleep(1000); require(status().getString("state") == "STOPPED")
                    report.put("localStop", status())
                }
                if (operation == "host-stop") {
                    Tls.connect(File(targetContext.filesDir, "session")).use { socket ->
                        Wire.writeRequest(DataOutputStream(socket.outputStream), Wire.STOP_HOST)
                        require(socket.inputStream.read() == -1)
                    }
                    val deadline = SystemClock.elapsedRealtime() + 5000
                    while (status().getString("state") == "STREAMING" && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
                    require(status().getString("state") in listOf("CONNECTION_FAILED", "DISCONNECTED"))
                    report.put("hostStop", status())
                }
            }
            report.put("passed", true)
        } catch (error: Exception) {
            report.put("errorType", error.javaClass.simpleName)
            val causes = JSONArray(); var cause: Throwable? = error
            repeat(6) { cause?.let { causes.put(it.javaClass.simpleName); cause = it.cause } }
            report.put("causeClasses", causes)
        }
        finally {
            activity?.let { runOnMainSync { it.finish() } }
            if (ownsRecord) file?.writeText(report.toString())
            finish(if (report.getBoolean("passed")) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("pav67", report.toString()) })
        }
    }
}
