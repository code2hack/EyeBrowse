package com.code2hack.eyebrowse.phone

/** Measured renderer CSS pixels mapped with the actual WebView scale, never inferred from DPI. */
data class RendererViewport(val width: Double, val height: Double, val scale: Double,
                            val devicePixelRatio: Double, val pageFocused: Boolean) {
    fun matches(widthPx: Int, heightPx: Int, webViewScale: Float): Boolean =
        pageFocused && width.isFinite() && height.isFinite() && scale.isFinite() && devicePixelRatio.isFinite() &&
            width > 0 && height > 0 && scale > 0 && devicePixelRatio > 0 &&
            webViewScale.isFinite() && webViewScale > 0 &&
            kotlin.math.round(width * webViewScale).toInt() == widthPx &&
            kotlin.math.round(height * webViewScale).toInt() == heightPx
}
