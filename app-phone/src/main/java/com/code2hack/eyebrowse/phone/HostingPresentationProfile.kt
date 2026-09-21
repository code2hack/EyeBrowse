package com.code2hack.eyebrowse.phone

/** Immutable PRIVATE geometry: Phone window measurements never modify this profile. */
data class HostingPresentationProfile(val width: Int, val height: Int, val densityDpi: Int) {
    init {
        require(HostingPolicy.viewportError(width, height) == null) { "unsupported presentation bounds" }
        require(densityDpi > 0) { "presentation density must be positive" }
    }
    companion object {
        /** #6 fallback, not a statement of final #7 Normal/Reading/keyboard content bounds. */
        val RG_DESIGN_FALLBACK = HostingPresentationProfile(480, 640, 160)
    }
}

/** Identity, not equal dimensions/generation numbers, fences delayed platform callbacks. */
internal class PresentationEpochs {
    class Token internal constructor(val generation: Int, val profile: HostingPresentationProfile)
    var current: Token? = null
        private set
    fun begin(generation: Int, profile: HostingPresentationProfile): Token =
        Token(generation, profile).also { current = it }
    fun owns(token: Token): Boolean = current === token
    fun retire() { current = null }
}

/** Current Phone content bounds; no remembered orientation baseline or pixel tolerance. */
internal object PhoneContentViewport {
    fun size(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int): Pair<Int, Int> =
        (width - left - right).coerceAtLeast(0) to (height - top - bottom).coerceAtLeast(0)
}
