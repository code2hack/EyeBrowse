package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecoveryJourneyTest {
    @Test fun reconcilesLiveHostWithoutReplayingInputOrStealingPhoneControl() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val mission = UUID.fromString(checkNotNull(InstrumentationRegistry.getArguments().getString("missionId"))).toString()
        val ack = File(app.cacheDir, "i11-$mission.ack").apply { writeText("") }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var peer: RgPresentationController
        fun await(label: String, bound: Long = 10_000, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + bound
            while (SystemClock.elapsedRealtime() < deadline) {
                var passed = false
                scenario.onActivity { passed = condition() }
                if (passed) return
                SystemClock.sleep(20)
            }
            fail(label)
        }
        fun waitHost(phase: String) = await("Phone receipt $phase", 15_000) { ack.readText().trim() == phase }
        fun phase(name: String) = peer.browserState()?.title?.substringBefore("|G=") == "I11-$mission-$name"
        fun retry() = scenario.onActivity { it.findViewById<Button>(R.id.rg_retry).performClick() }
        fun consent() = scenario.onActivity { it.findViewById<Button>(R.id.rg_handoff).performClick() }
        fun disconnected() {
            scenario.onActivity { peer.pause() }
            assertFalse(peer.canAct())
            assertNull("disconnected click not queued", peer.activateAt(10f, 10f))
            assertNull("disconnected navigation not queued", peer.reload())
            scenario.onActivity {
                val text = it.findViewById<TextView>(R.id.rg_status).text.toString().lowercase()
                assertTrue("visible stale/disconnected feedback: $text", text.contains("stale") || text.contains("disconnected") || text.contains("paused"))
            }
        }
        var failure: Throwable? = null
        try {
            scenario.onActivity { peer = it.presentation }
            retry()
            await("run-specific Phone state") { phase("ready") && peer.canHandoff() && peer.profile() != null }
            consent()
            await("first current frame", 2_000) { peer.canAct() }
            var context = peer.browserState()!!.context
            await("Phone requests link interruption") { phase("drop") }
            disconnected()
            waitHost("reconnect")
            val reconnectAt = SystemClock.elapsedRealtime()
            retry()
            await("fresh reconnect presentation") { peer.canAct() }
            var recovered = peer.browserState()!!.context
            assertEquals(context.lifetimeId, recovered.lifetimeId)
            assertEquals(context.controlEpoch, recovered.controlEpoch)
            assertTrue(recovered.viewportEpoch > context.viewportEpoch)
            assertEquals(recovered, peer.lastFrameHeader!!.context)
            Log.i("EyeBrowseRecovery", "RG_RECONNECT mission=$mission readyMs=${SystemClock.elapsedRealtime() - reconnectAt}")
            context = recovered
            await("Phone recreation verified") { phase("recreate-rg") }
            scenario.recreate()
            scenario.onActivity { peer = it.presentation }
            assertFalse(peer.canAct())
            waitHost("recreate")
            retry()
            await("new RG interface reconciled") { peer.canAct() }
            recovered = peer.browserState()!!.context
            assertEquals(context.lifetimeId, recovered.lifetimeId)
            assertEquals(context.documentId, recovered.documentId)
            assertEquals(context.controlEpoch, recovered.controlEpoch)
            assertTrue(recovered.viewportEpoch > context.viewportEpoch)
            await("Phone requests disconnected takeover") { phase("takeover") }
            disconnected()
            waitHost("takeover")
            retry()
            await("Phone owner reconciled") { peer.browserState()?.owner == ControlOwner.PHONE && peer.canHandoff() }
            assertFalse("reconnect cannot steal control", peer.canAct())
            assertNull(peer.activateAt(10f, 10f))
            consent()
            await("new explicit RG consent", 2_000) { peer.canAct() }
            await("Phone ready for Stop") { phase("stop") }
            Log.i("EyeBrowseRecovery", "RG_RELEASE mission=$mission phase=stop")
            await("Stop disables remote page input", 5_000) { !peer.canAct() }
            assertNull(peer.reload())
            Log.i("EyeBrowseRecovery", "RG_PASS mission=$mission noImplicitTakeover=true staleInputRejected=true")
        } catch (t: Throwable) { failure = t; throw t }
        finally {
            val cleanup = listOf<() -> Unit>({ scenario.close() }, { ack.delete(); assertFalse(ack.exists()) })
                .mapNotNull { runCatching(it).exceptionOrNull() }
            if (failure != null) cleanup.forEach { failure.addSuppressed(it) }
            else if (cleanup.isNotEmpty()) { cleanup.drop(1).forEach { cleanup.first().addSuppressed(it) }; throw cleanup.first() }
        }
    }
}
