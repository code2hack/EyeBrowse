package com.code2hack.eyebrowse.rg

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Owned page regression for transparent overlays, black fills, media and native page activation. */
@RunWith(AndroidJUnit4::class)
class LocalPagePresentationInstrumentedTest {
    @Test fun transparentLayersDoNotCoverTheLivePage() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=localBrowserTestPreferences(context)
        val previous=prefs.getString("local.tabs",null)
        val scene=ActivityScenario.launch(LocalBrowserActivity::class.java)
        fun js(script:String):String {
            val done=CountDownLatch(1);var result=""
            scene.onActivity { it.tabs.current.session.page!!.evaluateJavascript(script) { value->result=value;done.countDown() } }
            assertTrue(done.await(3,TimeUnit.SECONDS));return result
        }
        fun await(label:String,check:()->Boolean) {
            val end=SystemClock.elapsedRealtime()+10_000
            while(SystemClock.elapsedRealtime()<end) { if(check())return;SystemClock.sleep(30) };fail(label)
        }
        try {
            val base=checkNotNull(InstrumentationRegistry.getArguments().getString("fixtureBaseUrl")).trimEnd('/')
            scene.onActivity { it.tabs.current.session.open("$base/presentation.html") }
            await("owned page ready") { var ready=false;scene.onActivity { ready=it.tabs.current.session.state.phase==LocalBrowserSession.Phase.READY };ready }
            assertEquals("\"rgba(0, 0, 0, 0)\"",js("getComputedStyle(document.getElementById('shield')).backgroundColor"))
            assertEquals("\"rgb(0, 0, 0)\"",js("getComputedStyle(document.getElementById('opaque')).backgroundColor"))
            assertEquals("\"rgb(255, 255, 255)\"",js("getComputedStyle(document.getElementById('heading')).color"))
            // Add a second transparent host, then change a real opaque fill after initial presentation.
            js("""(()=>{const layer=document.createElement('div');layer.id='late';layer.style='position:fixed;inset:0;pointer-events:none;z-index:20';document.body.appendChild(layer);document.getElementById('opaque').style.backgroundColor='#abcdef';return true})()""")
            await("dynamic alpha/fill preserved") {
                js("getComputedStyle(document.getElementById('late')).backgroundColor") == "\"rgba(0, 0, 0, 0)\"" &&
                    js("getComputedStyle(document.getElementById('opaque')).backgroundColor") == "\"rgb(0, 0, 0)\""
            }
            fun rect(id:String)=JSONArray(js("""(()=>{const r=document.getElementById('$id').getBoundingClientRect();return [r.x,r.y,r.width,r.height,innerWidth]})()"""))
            val button=rect("action")
            scene.onActivity { a ->
                val page=a.tabs.current.session.page!!;val scale=page.width/button.getDouble(4)
                assertTrue(a.input.activate(InputPoint(((button.getDouble(0)+button.getDouble(2)/2)*scale).toFloat(),(48+(button.getDouble(1)+button.getDouble(3)/2)*scale).toFloat())))
            }
            await("native tap reaches uncovered page control") { js("document.getElementById('status').textContent")=="\"Pressed\"" }
            val media=rect("media");val heading=rect("heading")
            val frame=CountDownLatch(1)
            scene.onActivity { it.tabs.current.session.page!!.postVisualStateCallback(0,object:android.webkit.WebView.VisualStateCallback(){override fun onComplete(requestId:Long){frame.countDown()}}) }
            assertTrue(frame.await(3,TimeUnit.SECONDS));instrumentation.waitForIdleSync()
            val image=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val scale=image.width/media.getDouble(4)
                assertEquals(Color.RED,image.getPixel(((media.getDouble(0)+16)*scale).toInt(),(48+(media.getDouble(1)+16)*scale).toInt()))
                assertEquals(Color.BLACK,image.getPixel(4,44))
                assertEquals(Color.BLACK,image.getPixel(4,80))
                var light=0
                for(y in (48+heading.getDouble(1)*scale).toInt() until (48+(heading.getDouble(1)+heading.getDouble(3))*scale).toInt())
                    for(x in (heading.getDouble(0)*scale).toInt() until ((heading.getDouble(0)+heading.getDouble(2))*scale).toInt())
                        if(Color.red(image.getPixel(x,y))>180)light++
                assertTrue("heading actually rendered through transparent layers",light>30)
                context.openFileOutput("issue42-owned-presentation.png",0).use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                context.openFileOutput("issue42-owned-presentation.json",0).use { it.write(JSONObject().put("headingLightPixels",light).put("nativeButtonActivated",true).put("redCanvasPreserved",true).toString().toByteArray()) }
            } finally { image.recycle() }
        } finally {
            scene.close()
            assertTrue(prefs.edit().apply { if(previous==null)remove("local.tabs")else putString("local.tabs",previous) }.commit())
        }
    }
}
