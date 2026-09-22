package com.code2hack.eyebrowse.rg

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real production store/backend, isolated preference names. Never edits the real command/trust files. */
@RunWith(AndroidJUnit4::class)
class PreparedOrdinalStoreInstrumentedTest {
    private fun withStore(test:(Context,CommandSequenceStore)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val prefix="i8-store-"+UUID.randomUUID()+"-"
        val scoped=object:ContextWrapper(base) {
            override fun getSharedPreferences(name:String,mode:Int) = base.getSharedPreferences(prefix+name,mode)
            override fun deleteSharedPreferences(name:String) = base.deleteSharedPreferences(prefix+name)
        }
        val worker=Executors.newSingleThreadExecutor()
        try { worker.submit { test(scoped,CommandSequenceStore(scoped)) }.get(10,TimeUnit.SECONDS) }
        finally {
            worker.shutdownNow()
            for(name in listOf("browser-command-sequence","browser-command-sequence-integrity")) {
                scoped.deleteSharedPreferences(name)
                val file=File(base.applicationInfo.dataDir,"shared_prefs/"+prefix+name+".xml")
                assertFalse(file.exists());assertFalse(File(file.path+".bak").exists())
            }
        }
    }
    @Test fun realCheckedStoreRunsOffMainAndRejectsMainThreadAccess() = withStore { _,store ->
        assertNull(store.read())
        val cursor=CommandSequence.Cursor("real-store-fixture",7,41)
        assertTrue(store.persist(cursor));assertEquals(cursor,store.read())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertThrows(IllegalStateException::class.java) { store.read() }
            assertThrows(IllegalStateException::class.java) { store.persist(cursor.copy(sequence=42)) }
        }
        assertEquals(cursor,store.read())
    }
    @Test fun missingInitializedCursorIsRejectedAcrossStoreReconstruction() = withStore { context,store ->
        assertTrue(store.persist(CommandSequence.Cursor("real-store-fixture",7,41)))
        assertTrue(context.deleteSharedPreferences("browser-command-sequence"))
        assertThrows(IllegalStateException::class.java) { CommandSequenceStore(context).read() }
        assertTrue(context.getSharedPreferences("browser-command-sequence-integrity",0).getBoolean("initialized",false))
    }
    @Test fun malformedCursorCannotBecomeBootstrapAfterPrimaryFileLoss() = withStore { context,store ->
        assertTrue(context.getSharedPreferences("browser-command-sequence",0).edit()
            .putString("lifetime","broken-fixture").putLong("epoch",7).putString("sequence","invalid").commit())
        assertThrows(IllegalStateException::class.java) { store.read() }
        assertTrue(context.deleteSharedPreferences("browser-command-sequence"))
        assertThrows(IllegalStateException::class.java) { CommandSequenceStore(context).read() }
    }
}
