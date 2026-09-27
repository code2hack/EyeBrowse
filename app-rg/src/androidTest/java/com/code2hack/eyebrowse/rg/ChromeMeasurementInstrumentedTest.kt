package com.code2hack.eyebrowse.rg

import android.graphics.Bitmap
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Detached native-layout qualification, not a rendered-frame or browser-acceptance claim. */
@RunWith(AndroidJUnit4::class)
class ChromeMeasurementInstrumentedTest {
    @Test fun qualifyReadOnlyObserverAndAttributeNativeHeightChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext,R.style.Theme_EyeBrowseRg)
            val root = LayoutInflater.from(context).inflate(R.layout.activity_main,null)
            val column = root.findViewById<LinearLayout>(R.id.rg_root)
            val status = root.findViewById<TextView>(R.id.rg_status)
            val location = root.findViewById<TextView>(R.id.rg_detail)
            val image = root.findViewById<ImageView>(R.id.rg_page)
            val views = listOf(status,location)
            val metrics = context.resources.displayMetrics
            fun measure(): List<Int> {
                root.forceLayout()
                root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(metrics.heightPixels,View.MeasureSpec.EXACTLY))
                root.layout(0,0,metrics.widthPixels,metrics.heightPixels)
                return (0 until column.childCount).map { column.getChildAt(it).height }
            }
            fun description(view: TextView) = "height=${view.height} textSize=${view.textSize}" +
                " layout=${view.layout?.javaClass?.simpleName} buffer=${view.text.javaClass.simpleName} editable=${view.text is Editable}"
            val address="http://127.0.0.1:27341/keyboard.html?case=00000000-0000-0000-0000-000000000000"
            val statusCases=listOf("Connected to Phone","Waiting for current frame","Live page · authenticated","Updating page layout","Presentation stale")
            val locationCases=listOf(address,"No live page","KBD|{\"inputs\":0,\"text\":[0.5,0.2]}","example.com")
            status.text=statusCases.first();location.text=address
            val baseline=measure()
            val pageIndex=(0 until column.childCount).first { column.getChildAt(it)===image }
            var callbacks=0
            val initialBuffers=views.map { it.text }
            val observer=PreDrawTextObserver(root,views) { callbacks++ }
            views.forEachIndexed { index,view -> assertSame("observer install retains actual buffer",initialBuffers[index],view.text) }
            var nativeChanges=0
            try {
                for ((target,cases) in listOf(status to statusCases,location to locationCases)) {
                    cases.forEachIndexed { index,text ->
                        target.text=text
                        val beforeBuffer=target.text
                        val before=measure()
                        val layout=target.layout
                        val beforeCallbacks=callbacks
                        val layoutRequested=root.isLayoutRequested
                        // Explicit test notification: qualifies observer side effects, not GPU rendering.
                        root.viewTreeObserver.dispatchOnPreDraw()
                        assertSame("pre-draw cannot replace buffer",beforeBuffer,target.text)
                        assertSame("pre-draw cannot replace layout",layout,target.layout)
                        assertEquals("observer cannot change layout-request state",layoutRequested,root.isLayoutRequested)
                        assertFalse(target.text is Editable)
                        val after=measure();assertEquals("observation preserves exact geometry",before,after)
                        if (after[pageIndex]!=baseline[pageIndex]) nativeChanges++
                        Log.i("EyeBrowseChromeMeasure","READ_ONLY view=${if(target===status) "status" else "location"} case=$index callbacks=${callbacks-beforeCallbacks} children=$after ${description(target)}")
                    }
                    target.text=if(target===status) statusCases.first() else address
                    measure();root.viewTreeObserver.dispatchOnPreDraw()
                }
                for (height in listOf(323,344,345,344)) {
                    val bitmap=Bitmap.createBitmap(480,height,Bitmap.Config.ARGB_8888)
                    image.setImageBitmap(bitmap);val before=measure();root.viewTreeObserver.dispatchOnPreDraw()
                    assertEquals(before,measure())
                    if(before[pageIndex]!=baseline[pageIndex]) nativeChanges++
                    Log.i("EyeBrowseChromeMeasure","READ_ONLY bitmap=$height children=$before")
                    image.setImageDrawable(null);bitmap.recycle()
                }
            } finally { observer.close() }
            val closedCallbacks=callbacks
            status.text="After observer close";measure();root.viewTreeObserver.dispatchOnPreDraw()
            assertEquals("retired observer emits nothing",closedCallbacks,callbacks)
            assertTrue("real changed-text reads exercised",callbacks>0)
            status.text=statusCases.first();location.text=address;assertEquals(baseline,measure())
            // Controlled reproduction of the OLD instrumentation on detached fixture widgets only.
            val watcher=object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=Unit
                override fun afterTextChanged(s:Editable?)=Unit
            }
            var legacyChanges=0
            try {
                views.forEach { it.addTextChangedListener(watcher) }
                for ((index,text) in statusCases.withIndex()) {
                    status.text=text;location.text=locationCases[index%locationCases.size]
                    val sizes=measure()
                    assertTrue("old observer changes buffer path",status.text is Editable && location.text is Editable)
                    if(sizes[pageIndex]!=baseline[pageIndex]) legacyChanges++
                    Log.i("EyeBrowseChromeMeasure","LEGACY_WATCHER case=$index children=$sizes status:${description(status)} location:${description(location)}")
                }
            } finally { views.forEach { it.removeTextChangedListener(watcher) } }
            Log.i("EyeBrowseChromeMeasure","QUALIFIED bufferIdentity=true layoutIdentity=true geometry=true retired=true nativeHeightChanges=$nativeChanges legacyHeightChanges=$legacyChanges")
        }
    }
}
