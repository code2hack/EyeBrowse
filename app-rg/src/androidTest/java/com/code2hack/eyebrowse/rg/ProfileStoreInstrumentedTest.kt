package com.code2hack.eyebrowse.rg

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.ProfileImport
import com.code2hack.eyebrowse.rg.proxy.ProfileStore
import com.code2hack.eyebrowse.rg.proxy.CoreControl
import com.code2hack.eyebrowse.rg.proxy.ProxyPolicy
import com.code2hack.eyebrowse.rg.proxy.RgProfiles
import com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime
import com.code2hack.eyebrowse.rg.proxy.RouteFixture
import com.code2hack.eyebrowse.rg.proxy.checkProfileTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.Closeable
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Opt-in isolated store checks; the transaction probe additionally uses an owned local core fixture. */
@RunWith(AndroidJUnit4::class)
class ProfileStoreInstrumentedTest {
    @Test(timeout = 90000) fun productionTransactionRestoresRuntimeAfterSaveFailure() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("transactionProbe") == "true")
        val invocation = UUID.fromString(checkNotNull(arguments.getString("invocationId"))).toString()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = RgProxyRuntime.get(context)
        val preferences = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
        val previous = preferences.getString("profiles", null)
        val owner = ProfileStore({ previous }) { false }
        assertTrue("Starting Owner store readable", owner.readable)
        assertTrue("No active browsing runtime", runtime.proxyPort == null && runtime.webViewPort == null)
        val tabs = localBrowserTestPreferences(context)
        val previousTabs = tabs.getString("local.tabs", null)
        val bookmarks = File(context.filesDir, "local-browser/bookmarks.properties")
        val previousBookmarks = bookmarks.takeIf { it.exists() }?.readBytes()
        val directory = File(context.filesDir, "issue59-transaction-$invocation")
        assertTrue("New owned fixture directory", directory.mkdir())
        val report = JSONObject().put("invocation", invocation).put("result", "NOT_PASSED")
        val membership = JSONArray().also { report.put("runtimeMembership", it) }
        val previousEndpoint = runtime.testUrl
        var acquired = false
        fun awaitStopped() {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
            while ((runtime.proxyPort != null || runtime.webViewPort != null) && System.nanoTime() < end)
                Thread.sleep(25)
            assertTrue("Owned runtime released", runtime.proxyPort == null && runtime.webViewPort == null)
        }
        try {
            Closeable {
                if (acquired) { runtime.release(); awaitStopped() }
                runtime.useTestEndpoint(previousEndpoint)
                assertTrue("Starting saved runtime sources restored", runtime.replaceSources(owner.profiles.map { it.asSource() }).get(30, TimeUnit.SECONDS))
                assertTrue("Owner profile preference unchanged", preferences.getString("profiles", null) == previous)
                assertTrue("Owner tab metadata unchanged", tabs.getString("local.tabs", null) == previousTabs)
                assertTrue("Owner bookmarks unchanged", previousBookmarks?.let {
                    bookmarks.exists() && bookmarks.readBytes().contentEquals(it)
                } ?: !bookmarks.exists())
                report.put("ownerStoreTabsBookmarksUnchanged", true).put("foregroundRuntimeReleased", true)
            }.use {
                RouteFixture(context.assets.open("proxy-fixture.p12")).use { fixture ->
                    runtime.useTestEndpoint("http://127.0.0.1:${fixture.http}/health")
                    runtime.acquire(); acquired = true
                    val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
                    while (runtime.proxyPort == null && System.nanoTime() < end) Thread.sleep(25)
                    assertTrue("Owned core ready", runtime.proxyPort != null)
                    CoreControl(File(context.filesDir, "mihomo/control.sock").absolutePath).use { api ->
                        fixture.checkProfileTransaction(directory, { sources ->
                            if (!runtime.replaceSources(sources).get(30, TimeUnit.SECONDS)) false else {
                                val config = JSONObject(File(context.filesDir, "mihomo/candidate.json").readText())
                                    .put("hosts", JSONObject().put("route.test", "127.0.0.1"))
                                // The owned loopback origin exercises the ordinary public EGRESS route.
                                val rules = JSONArray().put("DOMAIN,route.test,EGRESS")
                                val original = config.getJSONArray("rules")
                                for (index in 0 until original.length()) rules.put(original.get(index))
                                config.put("rules", rules)
                                val mapped = File(directory, "route.json").apply {
                                    writeText(ProxyPolicy.configurationText(config.toString()))
                                }
                                api.request("PUT", "/configs", JSONObject().put("path", mapped.absolutePath))
                                assertTrue("Controlled core health completed", runtime.checkHealthNow().get(30, TimeUnit.SECONDS))
                                val provider = api.request("GET", "/providers/proxies/ENABLED").getJSONArray("proxies")
                                val expected = ProxyPolicy.nodes(sources).map { it.getString("name") }.toSet()
                                val actual = (0 until provider.length()).map { provider.getJSONObject(it).getString("name") }.toSet()
                                assertTrue("Exact applied runtime pool membership", actual == expected)
                                membership.put(JSONObject().put("expectedNames", JSONArray(expected))
                                    .put("actualNames", JSONArray(actual)))
                                true
                            }
                        }) { label ->
                            val connection = runtime.openConnection(URL("http://route.test:${fixture.http}/observe-$label"))
                            try { connection.inputStream.bufferedReader().use { it.readText() } }
                            finally { connection.disconnect() }
                        }
                    }
                }
            }
            report.put("result", "PRODUCTION_TRANSACTION_CHECKS_PASSED")
        } finally { File(directory, "result.json").writeText(report.toString(2)) }
    }

    @Test fun preflightWriteAndFreshReadRemainDistinct() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("savePathProbe") == "true")
        val invocation = UUID.fromString(checkNotNull(arguments.getString("invocationId"))).toString()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val realPreferences = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
        val previous = realPreferences.getString("profiles", null)
        val name = "issue59-save-probe-$invocation"
        val fixture = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val report = JSONObject().put("invocation", invocation).put("mode", "CONTROLLED_STORE_PROBE")
        fun report() = File(context.filesDir, "issue59-store-$invocation.json").writeText(report.toString(2))
        try {
            assertTrue("Unused owned preference fixture", fixture.all.isEmpty())
            val current = ProfileStore({ previous }) { false }
            report.put("currentStoreReadable", current.readable).put("currentProfileCount", current.profiles.size)

            // Nested supported options, JSON escapes and UTF-8 exercise the actual Android codec.
            val nodes = ProfileImport.nodes("""
                proxies:
                  - type: vless
                    server: fixture.invalid
                    port: 443
                    uuid: 00000000-0000-0000-0000-000000000001
                    tls: true
                    network: ws
                    ws-opts: {path: '/owned/é', headers: {X-Owned: 'dummy "quoted" value'}}
                    reality-opts: {public-key: owned-dummy, short-id: '01'}
                    alpn: [h2, http/1.1]
            """.trimIndent())
            val source = "https://fixture.invalid/sub?account=owned-dummy"
            var writes = 0
            val store = ProfileStore({ fixture.getString("profiles", null) }) {
                writes++
                fixture.edit().putString("profiles", it).commit()
            }
            val candidate = store.imported(source, nodes)
            val canSave = store.canSave(candidate)
            report.put("boundedCandidatePreflightAccepted", canSave).put("preflightWriteCount", writes)
            assertTrue("Supported bounded candidate passes Android preflight", canSave)
            assertTrue("Preflight never writes", writes == 0)
            val saved = store.save(candidate)
            report.put("actualPreferenceCommitSucceeded", saved)
            assertTrue("Owned Android preference commit succeeds", saved)
            val lastGood = fixture.getString("profiles", null)
            val fresh = ProfileStore({ lastGood }) { false }
            val exact = fresh.readable && fresh.profiles.single().let {
                it.source == source && it.name == "Profile 1" && it.enabled && it.nodes == nodes
            }
            report.put("freshReaderExact", exact)
            assertTrue("Fresh Android reader preserves nested data exactly", exact)

            val rejected = store.imported(source, listOf(mapOf("type" to "http", "server" to "fixture.invalid",
                "port" to 80, "password" to "\\".repeat(1100 * 1024))))
            assertFalse("Oversized escaped candidate rejects before any write", store.canSave(rejected))
            assertFalse("Rejected candidate cannot be saved", store.save(rejected))
            report.put("preflightRejectionKeptLastGood", writes == 1 && fixture.getString("profiles", null) == lastGood)
            assertTrue("Rejected candidate keeps disk and memory", writes == 1 &&
                fixture.getString("profiles", null) == lastGood && store.profiles === candidate)

            var attemptedWrites = 0
            val failingWriter = ProfileStore({ lastGood }) { attemptedWrites++; false }
            val before = failingWriter.profiles
            val changed = before.map { it.enabled(false) }
            assertTrue("Write-failure candidate passes preflight", failingWriter.canSave(changed))
            assertFalse("Injected writer failure remains failure", failingWriter.save(changed))
            val kept = attemptedWrites == 1 && failingWriter.profiles === before &&
                fixture.getString("profiles", null) == lastGood
            report.put("injectedWriteFailureKeptLastGood", kept)
            assertTrue("Failed writer keeps prior store", kept)
            report.put("result", "CONTROLLED_STORE_PROBE_PASSED")
        } catch (_: AssertionError) {
            report.put("result", "NOT_PASSED").put("failureCategory", "ASSERTION")
            throw AssertionError("Controlled store probe failed; see fixed-category report")
        } catch (_: Exception) {
            report.put("result", "NOT_PASSED").put("failureCategory", "OPERATION")
            throw AssertionError("Controlled store probe failed; see fixed-category report")
        } finally {
            try {
                val unchanged = realPreferences.getString("profiles", null) == previous
                report.put("realProfilesUnchanged", unchanged)
                assertTrue("Real profile preference stays unchanged", unchanged)
                val removed = context.deleteSharedPreferences(name)
                report.put("ownedPreferenceFixtureRemoved", removed)
                assertTrue("Owned preference fixture teardown succeeds", removed)
            } finally { report() }
        }
    }
}
