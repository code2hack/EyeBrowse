package com.code2hack.eyebrowse.rg

/** Approved H1.2 presets; storage succeeds before a new selection becomes visible or active. */
internal class LocalInputSettings(
    read: (String) -> String?,
    private val write: (String,String) -> Boolean,
) {
    enum class Sensitivity(val label: String, val gain: Double) {
        LOW("Low",.75), STANDARD("Standard",1.0), HIGH("High",1.25)
    }
    enum class Speed(val label: String, val pixelsPerSecond: Int) {
        SLOW("Slow",120), STANDARD("Standard",240), FAST("Fast",360)
    }
    var sensitivity = Sensitivity.entries.find { it.name == read("pointer.sensitivity") } ?: Sensitivity.STANDARD
        private set
    var speed = Speed.entries.find { it.name == read("edge.speed") } ?: Speed.STANDARD
        private set
    var error: String? = null
        private set
    fun select(value: Sensitivity): Boolean = saved("pointer.sensitivity",value.name) { sensitivity=value }
    fun select(value: Speed): Boolean = saved("edge.speed",value.name) { speed=value }
    private fun saved(key: String, value: String, apply: () -> Unit): Boolean {
        if (!write(key,value)) { error="Settings could not be saved. Try again."; return false }
        apply();error=null;return true
    }
}
