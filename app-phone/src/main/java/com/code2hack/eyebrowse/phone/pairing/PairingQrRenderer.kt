package com.code2hack.eyebrowse.phone.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Production QR renderer for the v1 pairing invitation (plan §4.4 PairingQrRenderer).
 *
 * The payload is the compact EyeBrowse pairing URI produced by the invitation codec — it carries
 * pairing metadata only (version, id, secret, phone SPKI pin, locators, capabilities) and never
 * Wi-Fi credentials. Rendering failures are surfaced, never silently degraded to a blank image.
 */
object PairingQrRenderer {

    /** Minimum edge length in px that keeps a v1 payload scannable by the RG camera. */
    const val MIN_SIZE_PX: Int = 400

    /** Pure-JVM matrix core (deterministic; unit-tested without android.graphics). */
    fun renderMatrix(payload: String, sizePx: Int): BitMatrix {
        require(payload.isNotBlank()) { "empty QR payload" }
        require(sizePx >= MIN_SIZE_PX) { "QR size below scan floor: $sizePx" }
        val hints = mapOf(
            EncodeHintType.MARGIN to 2, // quiet zone, modules
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
        )
        return QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }

    fun render(payload: String, sizePx: Int): Bitmap {
        val matrix = renderMatrix(payload, sizePx)
        val px = matrix.width
        val pixels = IntArray(px * px)
        for (y in 0 until px) {
            for (x in 0 until px) {
                pixels[y * px + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
    }
}
