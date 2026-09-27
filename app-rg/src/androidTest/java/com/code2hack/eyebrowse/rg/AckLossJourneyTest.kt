package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.transport.LinkClientEngine
import com.code2hack.eyebrowse.rg.link.RgLinkClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Decoded-message delivery faults only; all effects still traverse the authenticated sender. */
@RunWith(AndroidJUnit4::class)
class AckLossJourneyTest {
    @Test fun duplicateLostAndDelayedResultsDoNotRepeatEffects() = journey(false)
    @Test fun uncertainActionRetiresAcrossReconnectAndStop() = journey(true)

    private fun journey(recovery: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val mission = UUID.fromString(checkNotNull(InstrumentationRegistry.getArguments().getString("missionId"))).toString()
        val ack = File(app.cacheDir, "i11-$mission.ack").apply { writeText("") }
        val phaseFile = File(app.cacheDir, "i11-$mission.phase").apply { writeText("") }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var peer: RgPresentationController
        lateinit var client: RgLinkClient
        lateinit var faults: DeliveryFaults
        lateinit var surface: RgPresentationController.Surface
        val statuses = mutableListOf<Pair<Long, String>>()
        fun await(label: String, bound: Long = 2_000, condition: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + bound
            while (SystemClock.uptimeMillis() < end) { if (condition()) return; SystemClock.sleep(10) }
            fail(label)
        }
        fun pending(): String? = synchronized(field(peer, "lock")) { fieldOrNull(peer, "pendingCommand") as String? }
        fun title() = peer.browserState()?.title?.substringBefore("|G=")
        fun receipt(phase: String) { phaseFile.writeText(phase); Log.i("EyeBrowseAckLoss", "RG_PHASE mission=$mission phase=$phase") }
        fun consent() = scenario.onActivity { it.findViewById<Button>(R.id.rg_handoff).performClick() }
        fun click(): Pair<BrowserActionMessage, Long> {
            await("fresh frame before explicit input") { peer.canAct() }
            val state = checkNotNull(peer.browserState())
            assertEquals(state.context, peer.lastFrameHeader!!.context)
            val geometry = JSONObject(checkNotNull(state.title).substringAfter("|G="))
            val action = BrowserAction.ActivateAt(geometry.getDouble("x").toFloat(), geometry.getDouble("y").toFloat())
            var id: String? = null
            var at = 0L
            scenario.onActivity { at = SystemClock.uptimeMillis(); id = peer.activateAt(action.x, action.y) }
            assertNotNull("explicit action queued", id)
            val request = BrowserActionMessage(id!!, state.context, action, id!!.split(':')[2].toLong())
            Log.i("EyeBrowseAckLoss", "ACTION mission=$mission id=$id frame=${peer.lastFrameHeader} at=$at")
            return request to at
        }
        fun held(id: String): BrowserActionResultMessage {
            await("real result captured $id") { faults.result(id) != null }
            return checkNotNull(faults.result(id)).also { assertTrue(it.accepted); assertNull(it.effectSucceeded) }
        }
        fun timeout(at: Long, text: String) {
            val deadline = at + 5_500
            while (SystemClock.uptimeMillis() < deadline && synchronized(statuses) { statuses.none { it.first >= at && it.second == text } }) SystemClock.sleep(10)
            val observed = synchronized(statuses) { statuses.firstOrNull { it.first >= at && it.second == text } }
            assertNotNull("existing 5s timeout + predeclared 500ms observer allowance", observed)
            val elapsed = observed!!.first - at
            assertTrue("timeout elapsed=$elapsed", elapsed in 5_000..5_500)
            Log.i("EyeBrowseAckLoss", "TIMEOUT mission=$mission elapsedMs=$elapsed text=$text")
        }
        var failure: Throwable? = null
        try {
            scenario.onActivity {
                peer = it.presentation
                client = field(peer, "client") as RgLinkClient
                val original = field(client, "listener") as RgLinkClient.Listener
                faults = DeliveryFaults(original)
                setField(client, "listener", faults)
                surface = field(peer, "surface") as RgPresentationController.Surface
                setField(peer, "surface", object : RgPresentationController.Surface by surface {
                    override fun status(text: String) {
                        synchronized(statuses) { statuses.add(SystemClock.uptimeMillis() to text) }
                        surface.status(text)
                    }
                })
                peer.reconnect()
            }
            await("mission Phone state", 10_000) { peer.browserState()?.url?.contains(mission) == true && peer.canHandoff() && peer.profile() != null }
            // A4: the real accepted result is withheld; authoritative state alone resolves consent.
            faults.holdHandoff = true
            consent()
            await("state reconciles lost handoff ACK") { peer.canAct() && peer.canHandoff() }
            await("accepted handoff result captured") { faults.handoffs().any { it.accepted && it.owner == ControlOwner.RG } }
            val initial = peer.browserState()!!.context
            faults.releaseHandoffs() // duplicates of captured results, no manufactured success
            assertEquals(initial, peer.browserState()!!.context)
            val baseline = peer.actionAccounting()
            faults.holdActions = true
            val (first, firstAt) = click()
            held(first.commandId)
            assertEquals(first.commandId, pending()); assertFalse(peer.canAct())
            await("first page effect") { title() == "T03 A click 1" }
            if (recovery) {
                scenario.onActivity { peer.pause() }
                await("client quiescence") { !(field(client, "engine") as LinkClientEngine).isBusy }
                assertFalse(peer.canAct()); assertNull(peer.reload()); assertNull(pending())
                // Discard test-held callbacks at the retired boundary; do not bypass engine fencing.
                faults.discardActions()
                receipt("disconnected")
                await("Phone continuity receipt", 10_000) { ack.readText().trim() == "reconnect" }
                scenario.onActivity { peer.reconnect() }
                await("authenticated state", 10_000) { peer.browserState()?.stale == false && peer.browserState()?.context?.viewportEpoch != initial.viewportEpoch }
                await("fresh recovered frame") { peer.canAct() }
                val recovered = peer.browserState()!!.context
                assertEquals(initial.lifetimeId, recovered.lifetimeId)
                assertEquals(initial.documentId, recovered.documentId)
                assertEquals(initial.controlEpoch, recovered.controlEpoch)
                assertTrue(recovered.viewportEpoch > initial.viewportEpoch)
                assertEquals(ControlOwner.RG, peer.browserState()!!.owner)
                faults.holdActions = false
                assertTrue(client.sendControl(first))
                await("obsolete action rejected") { peer.lastActionResult?.commandId == first.commandId }
                assertEquals("STALE_CONTEXT", peer.lastActionResult!!.reason)
                assertEquals(baseline.constructed + 1, peer.actionAccounting().constructed)
                assertEquals("T03 A click 1", title())
                faults.holdActions = true
                val (last, _) = click(); held(last.commandId)
                await("second effect before Stop") { title() == "T03 A click 2" }
                receipt("stop")
                await("Stop invalidates pending action", 5_000) { peer.browserState()?.stale == true && pending() == null && !peer.canAct() }
                assertNull(peer.reload())
                faults.discardActions()
                assertEquals(baseline.constructed + 2, peer.actionAccounting().constructed)
                assertEquals(baseline.queued + 2, peer.actionAccounting().queued)
            } else {
                faults.releaseAction(first.commandId)
                await("first ACK clears pending") { pending() == null }
                faults.holdActions = false
                assertTrue(client.sendControl(first))
                await("duplicate rejection") { peer.lastActionResult?.commandId == first.commandId && peer.lastActionResult?.reason == "STALE_COMMAND_SEQUENCE" }
                assertEquals("T03 A click 1", title())
                assertEquals(baseline.consumed + 1, peer.actionAccounting().consumed)
                faults.holdActions = true
                val (lost, lostAt) = click(); held(lost.commandId)
                faults.discardActions() // genuinely lost, never released later
                timeout(lostAt, "Could not confirm action — not retried")
                assertNull(pending()); assertEquals(baseline.queued + 2, peer.actionAccounting().queued)
                val (a, aAt) = click(); held(a.commandId)
                timeout(aAt, "Could not confirm action — not retried")
                val (b, _) = click(); held(b.commandId)
                faults.releaseAction(a.commandId)
                assertEquals("old A cannot retire B", b.commandId, pending())
                faults.releaseAction(b.commandId)
                assertNull(pending())
                await("four effects observed") { title() == "T03 A click 4" && peer.canAct() }
                assertEquals(baseline.consumed + 4, peer.actionAccounting().consumed)
                assertEquals(baseline.constructed + 4, peer.actionAccounting().constructed)
                assertEquals(baseline.queued + 4, peer.actionAccounting().queued)
                // A4 timeout branch: neither result nor confirming state reaches the controller.
                faults.holdState = true; faults.holdHandoff = true
                var handoffAt = 0L
                scenario.onActivity { handoffAt = SystemClock.uptimeMillis(); assertTrue(peer.requestPhone()) }
                timeout(handoffAt, "Control change not confirmed — not retried")
                assertTrue(peer.canHandoff())
                faults.releaseState(); faults.releaseHandoffs()
                await("Phone authoritative state") { peer.browserState()?.owner == ControlOwner.PHONE }
                assertEquals(initial.controlEpoch + 1, peer.browserState()!!.context.controlEpoch)
                consent()
                await("fresh RG ownership and frame") { peer.canAct() }
                assertEquals(initial.controlEpoch + 2, peer.browserState()!!.context.controlEpoch)
                assertTrue("HostStatus really duplicated", faults.statusCopies > 0)
                assertTrue("BrowserState really duplicated", faults.stateCopies > 0)
                assertTrue("HandoffResult really duplicated", faults.handoffCopies > 0)
                receipt("done")
                await("Phone effect oracle and Stop", 10_000) { peer.browserState()?.stale == true && !peer.canAct() }
            }
            Log.i("EyeBrowseAckLoss", "RG_PASS mission=$mission recovery=$recovery accounting=${peer.actionAccounting()} initial=$initial firstAt=$firstAt")
        } catch (t: Throwable) { failure = t; throw t }
        finally {
            val cleanup = listOf<() -> Unit>(
                { scenario.onActivity { peer.pause(); setField(client, "listener", faults.original); setField(peer, "surface", surface) }; faults.discardActions() },
                { scenario.close() }, { ack.delete(); assertFalse(ack.exists()); phaseFile.delete(); assertFalse(phaseFile.exists()) },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if (failure != null) cleanup.forEach { failure.addSuppressed(it) }
            else if (cleanup.isNotEmpty()) { cleanup.drop(1).forEach { cleanup.first().addSuppressed(it) }; throw cleanup.first() }
        }
    }

    internal class DeliveryFaults(val original: RgLinkClient.Listener) : RgLinkClient.Listener by original {
        @Volatile var holdActions = false
        @Volatile var holdHandoff = false
        @Volatile var holdState = false
        @Volatile var statusCopies = 0
        @Volatile var stateCopies = 0
        @Volatile var handoffCopies = 0
        private val actions = linkedMapOf<String, BrowserActionResultMessage>()
        private val handoff = mutableListOf<HandoffResultMessage>()
        private var state: BrowserStateMessage? = null
        override fun onStatus(status: HostStatusValue) { original.onStatus(status); original.onStatus(status); statusCopies++ }
        @Synchronized override fun onControl(message: BrowserControlMessage) {
            when {
                message is BrowserActionResultMessage && holdActions -> { check(actions.size < 8); actions[message.commandId] = message }
                message is HandoffResultMessage && holdHandoff -> { check(handoff.size < 8); handoff.add(message) }
                message is BrowserStateMessage && holdState -> state = message
                else -> deliver(message)
            }
        }
        private fun deliver(message: BrowserControlMessage) {
            original.onControl(message)
            if (message is BrowserStateMessage || message is HandoffResultMessage || message is BrowserActionResultMessage) {
                original.onControl(message)
                if (message is BrowserStateMessage) stateCopies++
                if (message is HandoffResultMessage) handoffCopies++
            }
        }
        @Synchronized fun result(id: String) = actions[id]
        @Synchronized fun releaseAction(id: String) { deliver(checkNotNull(actions.remove(id))) }
        @Synchronized fun discardActions() { actions.clear() }
        @Synchronized fun handoffs() = handoff.toList()
        @Synchronized fun releaseHandoffs() { holdHandoff = false; handoff.forEach(::deliver); handoff.clear() }
        @Synchronized fun releaseState() { holdState = false; deliver(checkNotNull(state)); state = null }
    }
    private fun fieldOrNull(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(target) }
    private fun field(target: Any, name: String): Any = checkNotNull(fieldOrNull(target, name))
    private fun setField(target: Any, name: String, value: Any) = target.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(target, value) }
}
