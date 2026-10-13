package com.code2hack.eyebrowse.rg

/** Numeric H3.1 settings; successful new keys also mark the one-time legacy migration. */
internal class LocalInputSettings(
    read: (String) -> String?,
    private val write: (String,String) -> Boolean,
) {
    var pointerTenths = 20
        private set
    val pointerGain get() = pointerTenths/10.0
    var scrollSpeed = 240
        private set
    var error: String? = null
        private set
    init {
        val pointer=read("pointer.tenths")
        val scroll=read("edge.pixels-per-second")
        pointerTenths=if(pointer==null) when(read("pointer.sensitivity")) {
            "LOW" -> 15; "HIGH" -> 25; else -> 20
        } else pointer.toIntOrNull()?.takeIf { it in 1..60 } ?: 20
        scrollSpeed=if(scroll==null) when(read("edge.speed")) {
            "SLOW" -> 120; "FAST" -> 360; else -> 240
        } else scroll.toIntOrNull()?.takeIf { it in 10..1200 && it%10==0 } ?: 240
        val pointerSaved=pointer==pointerTenths.toString() || write("pointer.tenths",pointerTenths.toString())
        val scrollSaved=scroll==scrollSpeed.toString() || write("edge.pixels-per-second",scrollSpeed.toString())
        if(!pointerSaved || !scrollSaved) error="Settings could not be saved. Try again."
    }
    fun stepPointer(direction: Int): Boolean {
        require(direction == -1 || direction == 1)
        val value=(pointerTenths+direction).coerceIn(1,60)
        return saved("pointer.tenths",value.toString()) { pointerTenths=value }
    }
    fun stepScroll(direction: Int): Boolean {
        require(direction == -1 || direction == 1)
        val value=(scrollSpeed+direction*10).coerceIn(10,1200)
        return saved("edge.pixels-per-second",value.toString()) { scrollSpeed=value }
    }
    private fun saved(key: String, value: String, apply: () -> Unit): Boolean {
        if (!write(key,value)) { error="Settings could not be saved. Try again."; return false }
        apply();error=null;return true
    }
}
