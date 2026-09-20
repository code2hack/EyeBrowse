package com.code2hack.eyebrowse.rg.pairing

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * THE one production QR decoder (plan §4.4: "one production decoder implementation shared by
 * camera frames and instrumentation image input"). Both entry points produce a payload string that
 * is then handed to the SAME production pairing controller — the image-input seam only differs in
 * where the pixels come from, never in what happens after decoding.
 */
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
        if (width <= 0 || height <= 0) return null
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

    private fun decode(source: com.google.zxing.LuminanceSource): String? = try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (e: NotFoundException) {
        null
    } catch (e: ChecksumException) {
        null
    } catch (e: FormatException) {
        null
    }
}
