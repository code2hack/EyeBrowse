package com.code2hack.eyebrowse.rg.proxy

import java.io.File
import java.util.concurrent.CompletableFuture
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*

/** Isolated dummy disk state, with the production transaction and the caller's genuine core. */
internal fun RouteFixture.checkProfileTransaction(directory: File,
    replace: (List<ProxySource>) -> Boolean, observe: (String) -> String): JSONObject {
    val report = JSONObject().put("result", "NOT_PASSED")
    val routes = JSONArray().also { report.put("routes", it) }
    val applications = JSONArray().also { report.put("applications", it) }
    val disk = File(directory, "transaction-profiles.json")
    check(!disk.exists())
    fun route(label: String, expected: String) {
        val start = proxyRequests.size
        val peer = observe(label)
        val receipts = proxyReceipts(start)
        assertEquals(expected, peer)
        assertTrue("New controlled core connection", proxyRequests.drop(start).any {
            it.first == expected && it.second == "127.0.0.1:$http"
        })
        routes.put(JSONObject().put("case", label).put("destinationPeer", peer).put("newReceipts", receipts))
    }
    var failWrite = false
    var writes = 0
    var candidateObservedBeforeFailedWrite = false
    val store = ProfileStore({ disk.takeIf { it.exists() }?.readText() }) {
        writes++
        if (failWrite) {
            route("candidate-applied-before-save-failure", "127.0.0.2")
            candidateObservedBeforeFailedWrite = true
            false
        } else { disk.writeText(it); true }
    }
    val apply: (List<ProxySource>) -> CompletableFuture<Boolean> = { sources ->
        val accepted = replace(sources)
        applications.put(JSONObject().put("enabledSources", sources.count { it.enabled })
            .put("candidateAccepted", accepted))
        CompletableFuture.completedFuture(accepted)
    }
    fun state(profiles: List<SavedProfile>) = profiles.map { listOf(it.source, it.name, it.enabled, it.nodes) }
    try {
        val seed = sources().mapIndexed { index, source -> SavedProfile(
            "https://source-${index + 1}.invalid/sub?account=owned-${index + 1}",
            "Profile ${index + 1}", true,
            ProfileImport.nodes(JSONObject().put("proxies", JSONArray(source.nodes)).toString())) }
        assertEquals(state(seed), state(RgProfiles.applyAndSave(store, seed, apply)))
        route("transaction-last-good", "127.0.0.3")
        val lastGood = disk.readText()
        val previous = store.profiles
        val candidate = previous.mapIndexed { index, profile -> if (index == 1) profile.enabled(false) else profile }
        assertTrue(store.canSave(candidate))
        failWrite = true
        val beforeApplications = applications.length()
        val failedSave = runCatching { RgProfiles.applyAndSave(store, candidate, apply) }.exceptionOrNull()
        assertEquals("Profile change could not be saved. Existing data kept.", failedSave?.message)
        assertTrue(candidateObservedBeforeFailedWrite)
        assertEquals(2, writes)
        assertEquals(beforeApplications + 2, applications.length())
        assertTrue(applications.getJSONObject(beforeApplications).getBoolean("candidateAccepted"))
        assertTrue(applications.getJSONObject(beforeApplications + 1).getBoolean("candidateAccepted"))
        assertSame(previous, store.profiles)
        assertEquals(lastGood, disk.readText())
        assertEquals(state(previous), state(ProfileStore({ disk.readText() }) { false }.profiles))
        route("failed-save-restored-prior-pool", "127.0.0.3")
        report.put("candidateAppliedBeforeFailedWrite", true).put("rollbackApplied", true)
            .put("otherSourceAndDiskAndFreshReaderRestored", true)

        failWrite = false
        val rejected = store.imported(previous[0].source, listOf(mapOf("name" to "invalid-owned-cipher",
            "type" to "ss", "server" to "127.0.0.1", "port" to slow,
            "cipher" to "invalid-owned-cipher", "password" to "owned-dummy")))
        assertTrue("Runtime refusal must follow successful store preflight", store.canSave(rejected))
        val beforeRefusal = applications.length()
        val failedApply = runCatching { RgProfiles.applyAndSave(store, rejected, apply) }.exceptionOrNull()
        assertEquals("Profile change could not be applied. Existing data kept.", failedApply?.message)
        assertEquals(beforeRefusal + 1, applications.length())
        assertFalse(applications.getJSONObject(beforeRefusal).getBoolean("candidateAccepted"))
        assertEquals("No write after runtime refusal", 2, writes)
        assertSame(previous, store.profiles)
        assertEquals(lastGood, disk.readText())
        assertEquals(state(previous), state(ProfileStore({ disk.readText() }) { false }.profiles))
        route("refused-apply-kept-prior-pool", "127.0.0.3")
        report.put("runtimeRefusalBeforeAnyWrite", true).put("result", "PRODUCTION_TRANSACTION_CHECKS_PASSED")
        return report
    } finally { File(directory, "transaction-observations.json").writeText(report.toString(2)) }
}
