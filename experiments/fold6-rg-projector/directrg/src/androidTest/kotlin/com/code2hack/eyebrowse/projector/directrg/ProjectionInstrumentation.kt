package com.code2hack.eyebrowse.projector.directrg

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.code2hack.eyebrowse.projector.protocol.Tls
import com.code2hack.eyebrowse.projector.protocol.Wire
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import org.json.JSONArray
import org.json.JSONObject

internal fun fixtureBootstrapReady(
    state: String, tls: String?, peerAddress: String?, localAddress: String?, width: Int, height: Int, touchesSent: Long,
): Boolean = state in listOf("WAITING_FOR_VIDEO", "STREAMING") && tls in listOf("TLSv1.2", "TLSv1.3") &&
    peerAddress == "10.199.167.157" && localAddress == "10.199.167.154" && width == 480 && height == 640 && touchesSent == 0L

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
    private fun surfaces(view: View): List<SurfaceView> = when (view) {
        is SurfaceView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { surfaces(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** One own-Surface read for diagnosis. Neither a callback nor copied pixels prove composition or optical output. */
    private fun readSurface(activity: Activity, id: String, report: JSONObject) {
        val detail = JSONObject().put("diagnosticOnly", true).put("maximumPixelCopyRequests", 1)
        report.put("surfaceRead", detail)
        require(status().getString("state") == "STREAMING" && status().getLong("touchesSent") == 0L)
        val destination = File(targetContext.filesDir, "probe-$id-surface.png")
        require(destination.createNewFile()) { "Existing Surface diagnostic" }
        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        var result = -1
        val begin = SystemClock.elapsedRealtime()
        try {
            runOnMainSync {
                require(activity is ReceiverActivity && activity.hasWindowFocus() && !activity.isFinishing)
                val surface = surfaces(activity.window.decorView).single()
                require(surface.isShown && surface.width == 480 && surface.height == 640 && surface.holder.surface.isValid)
                detail.put("focusedOwnSurface480x640AtRequest", true)
                PixelCopy.request(surface, bitmap, { code -> result = code; done.countDown() }, Handler(Looper.getMainLooper()))
            }
            val arrived = done.await(5, TimeUnit.SECONDS)
            detail.put("callbackArrived", arrived).put("requestToWaitReturnMs", SystemClock.elapsedRealtime() - begin)
            require(arrived) { "Bounded Surface read did not complete" }
            val category = when (result) {
                PixelCopy.SUCCESS -> "SUCCESS"
                PixelCopy.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
                PixelCopy.ERROR_TIMEOUT -> "ERROR_TIMEOUT"
                PixelCopy.ERROR_SOURCE_NO_DATA -> "ERROR_SOURCE_NO_DATA"
                PixelCopy.ERROR_SOURCE_INVALID -> "ERROR_SOURCE_INVALID"
                PixelCopy.ERROR_DESTINATION_INVALID -> "ERROR_DESTINATION_INVALID"
                else -> "UNRECOGNIZED_RESULT"
            }
            detail.put("result", category)
            if (result == PixelCopy.SUCCESS) {
                FileOutputStream(destination).use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    output.fd.sync()
                }
                detail.put("PNGBytes", destination.length()).put("copiedWidth", bitmap.width).put("copiedHeight", bitmap.height)
            }
            detail.put("touchesSentAfter", status().getLong("touchesSent"))
            require(status().getLong("touchesSent") == 0L)
        } finally {
            // A timed-out copy may still own the bitmap until its callback; do not recycle it underneath the native read.
            if (done.count == 0L) bitmap.recycle()
        }
    }
    private fun waitStreaming() {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (runCatching { status().optString("state") == "STREAMING" }.getOrDefault(false)) return
            Thread.sleep(100)
        }
        error("No rendered stream")
    }
    private fun waitFixtureGeometry() {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val ready = runCatching {
                val value = status()
                fixtureBootstrapReady(value.getString("state"), value.optString("tls"), value.optString("peerAddress"),
                    value.optString("localAddress"), value.getInt("width"), value.getInt("height"), value.getLong("touchesSent"))
            }.getOrDefault(false)
            if (ready) return
            Thread.sleep(100)
        }
        error("No authenticated fixture geometry")
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
                require(operation in listOf("stream", "pause", "reconnect", "local-stop", "host-stop", "surface-diagnostic", "input-observation"))
                val seconds = args.getString("seconds")?.toInt() ?: 10
                require(seconds in 1..660)
                val waitForFixture = args.getString("waitForGo") == "true"
                val intent = Intent().setClassName(targetContext.packageName, ReceiverActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity = startActivitySync(intent); waitForIdleSync(); Thread.sleep(250)
                clickConnect(activity)
                if (waitForFixture) waitFixtureGeometry() else waitStreaming()
                report.put("connected", status())
                if (operation == "reconnect") {
                    runOnMainSync { activity!!.finish() }; Thread.sleep(1500)
                    require(status().getString("state") == "STOPPED")
                    activity = startActivitySync(intent); waitForIdleSync(); Thread.sleep(250)
                    clickConnect(activity)
                    if (waitForFixture) waitFixtureGeometry() else waitStreaming()
                    require(status().getLong("touchesSent") == 0L)
                    report.put("reconnected", status())
                }
                if (waitForFixture) {
                    val go = File(targetContext.filesDir, "probe-$id.go")
                    val deadline = SystemClock.elapsedRealtime() + 180_000
                    while (!go.isFile && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
                    require(go.isFile) { "Bounded fixture setup did not complete" }
                    report.put("fixtureSetupBeforeMeasurement", true)
                    waitStreaming()
                }
                if (operation == "surface-diagnostic") {
                    require(waitForFixture && (args.getString("tapAtSeconds") ?: "-1") == "-1")
                    readSurface(activity, id, report)
                } else {
                    report.put("first", status())
                    if (operation == "input-observation") runOnMainSync {
                        report.put("inputBefore", (activity as ReceiverActivity).inputSnapshot())
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
                            require(status().getString("state") == "STREAMING")
                            runOnMainSync { require(activity!!.hasWindowFocus() && !activity!!.isFinishing) }
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
                    if (operation == "input-observation") runOnMainSync {
                        report.put("inputAfter", (activity as ReceiverActivity).inputSnapshot())
                    }
                    report.put("observedStreamingMs", SystemClock.elapsedRealtime() - start).put("last", status()).put("samples", samples)
                    if (operation == "pause") {
                        targetContext.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        Thread.sleep(1500); require(status().getString("state") == "STOPPED")
                        report.put("paused", status())
                    }
                    if (operation == "local-stop") {
                        runOnMainSync { (activity as ReceiverActivity).disconnect() }
                        report.put("localStopInvocation", "Direct Disconnect method; physical control binding not qualified")
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
            }
            report.put("passed", true)
        } catch (error: Exception) {
            report.put("errorType", error.javaClass.simpleName)
            if (activity != null) runCatching { report.put("failureStatus", status()) }
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
