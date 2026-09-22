package com.code2hack.eyebrowse.rg

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** All preference loading and checked commits belong to the process reservation worker. */
internal class CommandSequenceStore(private val context: Context) {
    private var markerConfirmed=false // Accessed only by the single reservation worker.
    private fun offMain() { check(Looper.myLooper()!=Looper.getMainLooper()) { "Ordinal storage on Main" } }
    private fun prefs() = context.getSharedPreferences("browser-command-sequence",Context.MODE_PRIVATE)
    private fun marker() = context.getSharedPreferences("browser-command-sequence-integrity",Context.MODE_PRIVATE)

    fun read(): CommandSequence.Cursor? {
        offMain()
        val start=SystemClock.uptimeMillis();val cpu=SystemClock.currentThreadTimeMillis()
        try {
            val values=prefs().all
            if(values.isEmpty()) {
                check(!marker().getBoolean("initialized",false)) { "Missing initialized cursor" }
                return null
            }
            check(markInitialized()) { "Cursor integrity marker not confirmed" }
            val lifetime=values["lifetime"] as? String ?: error("Missing lifetime")
            val epoch=values["epoch"] as? Long ?: error("Missing epoch")
            val sequence=values["sequence"] as? Long ?: error("Missing sequence")
            check(lifetime.isNotBlank() && epoch>=0 && sequence>0) { "Corrupt ordinal cursor" }
            return CommandSequence.Cursor(lifetime,epoch,sequence)
        } finally {
            Log.i("EyeBrowseOrdinal","STORE_READ start="+start+" finish="+SystemClock.uptimeMillis()+
                " cpuMs="+(SystemClock.currentThreadTimeMillis()-cpu)+" thread="+Thread.currentThread().name+" main=false")
        }
    }

    private fun markInitialized(): Boolean {
        if(markerConfirmed) return true
        // A cached preference value alone is not confirmation after an earlier failed commit.
        return marker().edit().putBoolean("initialized",true).commit().also { if(it) markerConfirmed=true }
    }

    fun persist(cursor: CommandSequence.Cursor): Boolean {
        offMain()
        require(cursor.lifetime.isNotBlank() && cursor.epoch>=0 && cursor.sequence>0)
        val start=SystemClock.uptimeMillis();val cpu=SystemClock.currentThreadTimeMillis()
        var success=false
        try {
            // A separate durable marker makes a missing cursor fail closed after process restart.
            // On upgrade the existing cursor remains the floor; no pairing or cursor reset.
            if(!markInitialized()) return false
            success=prefs().edit().putString("lifetime",cursor.lifetime).putLong("epoch",cursor.epoch)
                .putLong("sequence",cursor.sequence).commit()
            return success
        } finally {
            Log.i("EyeBrowseOrdinal","STORE namespace="+cursor.namespace+" ordinal="+cursor.sequence+
                " start="+start+" finish="+SystemClock.uptimeMillis()+" cpuMs="+(SystemClock.currentThreadTimeMillis()-cpu)+
                " success="+success+" thread="+Thread.currentThread().name+" main=false")
        }
    }
}
