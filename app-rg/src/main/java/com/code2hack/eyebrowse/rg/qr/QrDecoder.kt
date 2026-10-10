package com.code2hack.eyebrowse.rg.qr

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/** The same QR decoder for real camera luminance and deterministic bitmap verification. */
object QrDecoder {

    /** Decodes a QR payload from a bitmap (instrumentation seam, gallery-free). */
    fun decode(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return decode(com.google.zxing.RGBLuminanceSource(width, height, pixels))
    }

    /** Decodes a QR payload from a camera Y (luminance) plane. */
    fun decodeYPlane(luma: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || rowStride < width ||
            (height - 1L) * rowStride + width > luma.size) return null
        val source = if (rowStride == width) {
            com.google.zxing.PlanarYUVLuminanceSource(
                luma, rowStride, height, 0, 0, width, height, false,
            )
        } else {
            // Trim per-row stride padding into a tight buffer.
            val tight = ByteArray(width * height)
            for (row in 0 until height) {
                System.arraycopy(luma, row * rowStride, tight, row * width, width)
            }
            com.google.zxing.PlanarYUVLuminanceSource(
                tight, width, height, 0, 0, width, height, false,
            )
        }
        return decode(source)
    }

    private fun decode(source: com.google.zxing.LuminanceSource): String? {
        val image = BinaryBitmap(HybridBinarizer(source))
        // Perfect screen/printed codes can confuse the detector; try ZXing's pure-code extractor too.
        for (hints in listOf(emptyMap(), mapOf(DecodeHintType.PURE_BARCODE to true))) {
            try { return QRCodeReader().decode(image, hints).text }
            catch (_: ReaderException) { /* No complete QR in this interpretation. */ }
        }
        return null
    }
}
