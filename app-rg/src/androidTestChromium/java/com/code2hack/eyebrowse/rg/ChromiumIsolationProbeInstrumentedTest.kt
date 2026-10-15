package com.code2hack.eyebrowse.rg

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.ResultReceiver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.chromium.IsolatedEngineProbeService
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Feasibility evidence only, never a claim that Chromium's real renderers are isolated. */
@RunWith(AndroidJUnit4::class)
class ChromiumIsolationProbeInstrumentedTest {
    @Test fun isolatedServiceCanOnlyUseExplicitReadOnlyEngineDescriptors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val received = CountDownLatch(1)
        var result: JSONObject? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {}
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        val report = JSONObject().put("scope", "ISOLATED_SERVICE_FEASIBILITY_NOT_RENDERER_ACCEPTANCE")
            .put("hostUid", Process.myUid())
        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(code: Int, data: Bundle?) {
                result = JSONObject(checkNotNull(data?.getString("report")))
                received.countDown()
            }
        }
        val engine = File(context.filesDir, "chromium-engine")
        ParcelFileDescriptor.open(File(engine, "engine.apk"), ParcelFileDescriptor.MODE_READ_ONLY).use { archive ->
            ParcelFileDescriptor.open(File(engine, "libs/arm64-v8a/libwebviewchromium.so"),
                ParcelFileDescriptor.MODE_READ_ONLY).use { library ->
                val intent = Intent(context, IsolatedEngineProbeService::class.java)
                    .putExtra("result", receiver).putExtra("archive", archive).putExtra("library", library)
                var bound = false
                try {
                    bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    assertTrue("isolated probe binding", bound)
                    assertTrue("isolated probe report", received.await(20, TimeUnit.SECONDS))
                    val observed = checkNotNull(result).also { report.put("child", it) }
                    assertNotEquals("different isolated UID", Process.myUid(), observed.getInt("uid"))
                    assertTrue("isolated UID range", observed.getInt("uid") % 100000 >= 90000)
                    assertFalse("no ambient app-private read", observed.getBoolean("privateDummyReadable"))
                } finally {
                    if (bound) context.unbindService(connection)
                    report.put("reported", result != null)
                    File(context.filesDir, "chromium-isolation-probe.json").writeText(report.toString(2))
                }
            }
        }
    }
}
