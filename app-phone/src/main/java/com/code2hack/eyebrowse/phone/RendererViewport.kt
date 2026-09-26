package com.code2hack.eyebrowse.phone

/** Renderer extent only; exact native/image geometry and context freshness are separate gates. */
data class RendererViewport(val width: Double, val height: Double, val scale: Double,
                            val devicePixelRatio: Double, val pageFocused: Boolean) {
    fun matches(widthPx: Int, heightPx: Int, webViewScale: Float): Boolean =
        widthPx > 0 && heightPx > 0 &&
            pageFocused && width.isFinite() && height.isFinite() && scale.isFinite() && devicePixelRatio.isFinite() &&
            width > 0 && height > 0 && scale > 0 && devicePixelRatio > 0 &&
            webViewScale.isFinite() && webViewScale > 0 &&
            matchesAxis(width, widthPx, webViewScale) && matchesAxis(height, heightPx, webViewScale)

    private fun matchesAxis(css: Double, nativePx: Int, webViewScale: Float): Boolean {
        // Chromium 99 ViewAndroid rounds native px up to integer DIP; WidgetBase rounds
        // DIP back up to Blink px. Both conversions use float scale, not display DPI.
        val dipScale = devicePixelRatio.toFloat()
        if (!dipScale.isFinite() || dipScale <= 0) return false
        val expected = kotlin.math.ceil(kotlin.math.ceil(nativePx / dipScale) * dipScale).toDouble()
        val measured = css * webViewScale
        return expected.isFinite() && measured.isFinite() && kotlin.math.round(measured) == expected
    }
}
