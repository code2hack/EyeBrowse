package com.code2hack.eyebrowse.rg.pairing

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.code2hack.eyebrowse.core.link.LinkError
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production CameraX scanner (plan §4.4 CameraQrScanner). Real camera frames feed
 * [QrDecoder.decodeYPlane]; there is no fake camera path in production. [cancel] unbinds the
 * use cases synchronously on the main thread so camera resources are released well inside the
 * ≤2 s cancellation bound; restart re-binds from scratch (reacquisition evidence).
 *
 * Review B3: provider-acquisition and bind failures are REPORTED through [onCameraError] with
 * the mandated [LinkError.CameraUnavailable] code (never swallowed into a silent "scanning"
 * state), and partial camera resources are released on the failure path.
 */
class CameraQrScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val mainExecutor: Executor,
    private val onPayload: (String) -> Unit,
    private val onFirstFrame: () -> Unit,
    private val onCameraError: (LinkError) -> Unit = {},
    /** Production default; overridable only for deterministic failure-path instrumentation. */
    private val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
) : ImageAnalysis.Analyzer {

    private val main = Handler(Looper.getMainLooper())
    private var provider: ProcessCameraProvider? = null
    private val failureReported = AtomicBoolean(false)
    @Volatile
    private var firstFrameSeen = false
    @Volatile
    private var cancelled = false

    /** Reports CameraUnavailable exactly once and releases partial resources (review B3). */
    private fun failCamera(cameraProvider: ProcessCameraProvider?) {
        cameraProvider?.let { runCatching { it.unbindAll() } }
        provider = null
        if (failureReported.compareAndSet(false, true)) {
            main.post { onCameraError(LinkError.CameraUnavailable) }
        }
    }

    fun start(previewView: PreviewView) {
        cancelled = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (cancelled) return@addListener
            val cameraProvider = try {
                future.get()
            } catch (e: Exception) {
                failCamera(null)
                return@addListener
            }
            provider = cameraProvider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(mainExecutor, this) }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    analysis,
                )
            } catch (e: Exception) {
                // Bind failure must not leave the UI in "camera active" forever (review B3).
                failCamera(cameraProvider)
            }
        }, mainExecutor)
    }

    override fun analyze(image: ImageProxy) {
        val closed = try {
            if (!firstFrameSeen) {
                firstFrameSeen = true
                onFirstFrame()
            }
            val plane = image.planes.firstOrNull()
            val width = image.width
            val height = image.height
            if (plane == null || width <= 0 || height <= 0) {
                null
            } else {
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                QrDecoder.decodeYPlane(bytes, plane.rowStride, width, height)
            }
        } finally {
            image.close()
        }
        if (closed != null && !cancelled) onPayload(closed)
    }

    /** Unbinds all use cases immediately on the main thread (≤2 s cancellation bound). */
    fun cancel() {
        cancelled = true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            provider?.unbindAll()
            provider = null
        } else {
            main.post {
                provider?.unbindAll()
                provider = null
            }
        }
    }
}
