package com.code2hack.eyebrowse.rg

import android.graphics.Bitmap
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

/** Isolated native layout: no Activity launch, peer connection, page script, or sensor start. */
@RunWith(AndroidJUnit4::class)
class ChromeMeasurementInstrumentedTest {
    @Test fun attributeContentHeightChangesToOneNativeViewAtATime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext,R.style.Theme_EyeBrowseRg)
            val root = LayoutInflater.from(context).inflate(R.layout.activity_main,null)
            val column = root.findViewById<LinearLayout>(R.id.rg_root)
            val status = root.findViewById<TextView>(R.id.rg_status)
            val location = root.findViewById<TextView>(R.id.rg_detail)
            val image = root.findViewById<ImageView>(R.id.rg_page)
            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            fun measure(): List<Int> {
                root.forceLayout()
                root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
                root.layout(0,0,width,height)
                return (0 until column.childCount).map { column.getChildAt(it).height }
            }
            fun description(view: TextView) = "height=${view.height} textSize=${view.textSize}" +
                " lineHeight=${view.lineHeight} layout=${view.layout?.javaClass?.simpleName}" +
                " pad=${view.compoundPaddingTop},${view.compoundPaddingBottom}" +
                " fontTop=${view.paint.fontMetricsInt.top} fontBottom=${view.paint.fontMetricsInt.bottom}"
            status.text="Connected to Phone"
            location.text="http://127.0.0.1:27341/keyboard.html?case=00000000-0000-0000-0000-000000000000"
            val baseline=measure()
            val pageIndex=(0 until column.childCount).first { column.getChildAt(it)===image }
            var statusChanges=0;var locationChanges=0;var imageChanges=0
            Log.i("EyeBrowseChromeMeasure","BASE density=${metrics.densityDpi} fontScale=${metrics.scaledDensity} size=${width}x$height children=$baseline")
            val statusCases=listOf("Connected to Phone","Waiting for current frame","Live page · authenticated","Updating page layout","Presentation stale")
            repeat(3) { cycle ->
                statusCases.forEachIndexed { index,text ->
                    status.text=text
                    val measured=measure()
                    if(measured[pageIndex]!=baseline[pageIndex]) statusChanges++
                    Log.i("EyeBrowseChromeMeasure","STATUS cycle=$cycle case=$index children=$measured ${description(status)}")
                }
            }
            status.text="Connected to Phone";assertEquals(baseline,measure())
            for ((index,text) in listOf("No live page","KBD|{\"inputs\":0,\"submits\":0,\"text\":[0.5,0.2]}","example.com").withIndex()) {
                location.text=text
                val measured=measure()
                if(measured[pageIndex]!=baseline[pageIndex]) locationChanges++
                Log.i("EyeBrowseChromeMeasure","LOCATION case=$index children=$measured ${description(location)}")
            }
            location.text="http://127.0.0.1:27341/keyboard.html?case=00000000-0000-0000-0000-000000000000"
            assertEquals(baseline,measure())
            for (bitmapHeight in listOf(323,344,345,344)) {
                val bitmap=Bitmap.createBitmap(480,bitmapHeight,Bitmap.Config.ARGB_8888)
                image.setImageBitmap(bitmap)
                val measured=measure()
                if(measured[pageIndex]!=baseline[pageIndex]) imageChanges++
                Log.i("EyeBrowseChromeMeasure","BITMAP height=$bitmapHeight children=$measured")
                image.setImageDrawable(null);bitmap.recycle()
            }
            Log.i("EyeBrowseChromeMeasure","RESULT statusChanges=$statusChanges locationChanges=$locationChanges imageChanges=$imageChanges")
            assertTrue("isolate the observed content-height change before proposing a fix",statusChanges+locationChanges+imageChanges>0)
        }
    }
}
