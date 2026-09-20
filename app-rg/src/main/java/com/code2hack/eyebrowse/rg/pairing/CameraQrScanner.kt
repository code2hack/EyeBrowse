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
import java.util.concurrent.Executor

/**
 * Production CameraX scanner (plan §4.4 CameraQrScanner). Real camera frames feed
 * [QrDecoder.decodeYPlane]; there is no fake camera path in production. [cancel] unbinds the
 * use cases synchronously on the main thread so camera resources are released well inside the
 * ≤2 s cancellation bound; restart re-binds from scratch (reacquisition evidence).
 */
class CameraQrScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val mainExecutor: Executor,
    private val onPayload: (String) -> Unit,
    private val onFirstFrame: () -> Unit,
) : ImageAnalysis.Analyzer {

    private val main = Handler(Looper.getMainLooper())
    private var provider: ProcessCameraProvider? = null
    @Volatile
    private var firstFrameSeen = false
    @Volatile
    private var cancelled = false

    fun start(previewView: PreviewView) {
        cancelled = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (cancelled) return@addListener
            val cameraProvider = try {
                future.get()
            } catch (e: Exception) {
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
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (e: Exception) {
                // Bind failure surfaces as "no frames"; the UI stays on its retry affordance.
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
