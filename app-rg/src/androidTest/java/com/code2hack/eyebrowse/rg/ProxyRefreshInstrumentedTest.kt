package com.code2hack.eyebrowse.rg

import android.app.job.JobScheduler
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProxyRefreshInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val runtime = RgProxyRuntime.get(context)
    private val owner = RgRefresh.get(context)
    private val scheduler = context.getSystemService(JobScheduler::class.java)
    private val due = context.getSharedPreferences("proxy-refresh", Context.MODE_PRIVATE)
    private val profilesPrefs = context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE)
    private val report = JSONObject().put("clock", "TEST_ONLY; system clock unchanged; no elapsed 30-minute/day claim")
    private val cases = JSONArray().also { report.put("cases", it) }
    private val origin = "http://100.92.81.33:39030"

    private fun main(body: () -> Unit) = instrumentation.runOnMainSync(body)
    private fun await(label: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 45_000
        while (!condition()) { if (SystemClock.elapsedRealtime() > end) fail(label); SystemClock.sleep(20) }
    }
    private fun runDue() {
        lateinit var result: CompletableFuture<Unit>
        main { result = owner.runDue() }
        result.get(90, TimeUnit.SECONDS)
    }
    private fun control(mode: String) {
        URL("$origin/control?mode=$mode").openConnection().apply { connectTimeout = 3000; readTimeout = 3000 }
            .getInputStream().use { it.readBytes() }
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun runtimeCounts(phase: String, active: Boolean, port: Int) {
        val binary = File(context.applicationInfo.nativeLibraryDir, "libmihomo.so").absolutePath
        var pids = emptyList<String>(); var sockets = 0; var listeners = 0
        await("Owned core/socket/listener counts: $phase") {
            pids = shell("ps -A -n -w -o UID,PID,ARGS").lineSequence().map { it.trim().split(Regex("\\s+"), limit = 3) }
                .filter { it.size == 3 && it[0] == android.os.Process.myUid().toString() &&
                    runCatching { File("/proc/${it[1]}/exe").canonicalPath == binary }.getOrDefault(false) }
                .map { it[1] }.toList()
            sockets = shell("cat /proc/net/unix").lineSequence().count { it.endsWith("/files/mihomo/control.sock") }
            listeners = shell("ss -ltn").lineSequence().map { it.trim().split(Regex("\\s+")) }
                .count { it.size >= 4 && it[0] == "LISTEN" && it[3].substringAfterLast(':') == port.toString() }
            report.put("lastRuntimeCountObservation", JSONObject().put("phase", phase).put("coreCount", pids.size)
                .put("controlSockets", sockets).put("mixedListeners", listeners))
            val expected = if (active) 1 else 0
            pids.size == expected && sockets == expected && listeners == expected
        }
        val samples = report.optJSONArray("runtimeCounts") ?: JSONArray().also { report.put("runtimeCounts", it) }
        samples.put(JSONObject().put("phase", phase).put("corePids", JSONArray(pids)).put("controlSockets", sockets)
            .put("mixedPort", port).put("mixedListeners", listeners).put("elapsedRealtimeMs", SystemClock.elapsedRealtime()))
    }

    /** Restoration is outside scenario/runtime/fixture/report closures; original failures stay primary. */
    private fun preserving(afterReport: () -> Unit = {}, body: () -> Unit) {
        val prefs = listOf("proxy-profiles", "proxy-refresh", "LocalBrowserActivity").associateWith {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap()
        }
        val startingProfileCount = ProfileStore({ profilesPrefs.getString("profiles", null) }) { false }.profiles.size
        val jobs = scheduler.allPendingJobs.filter { it.id in listOf(4501, 4502) }
        val home = File(context.filesDir, "mihomo")
        val names = listOf("cn-domain.mrs", "cn-ip.mrs", "regional-current", "regional-next", "config.json", "candidate.json", "regional-config.json", "issue45-route.json")
        val files = names.associateWith { File(home, it).takeIf(File::isFile)?.readBytes() }
        val previousDirectories = home.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.toSet()
        val clock = owner.clock; val urls = owner.ruleUrls; val testUrl = runtime.testUrl; val network = owner.testConnectivity
        val regionalInspection = runtime.inspectRegional
        Closeable {
            var failure: Throwable? = null
            fun restore(action: () -> Unit) { try { action() } catch (caught: Throwable) {
                if (failure == null) failure = caught else failure!!.addSuppressed(caught)
            } }
            restore {
                lateinit var pending: CompletableFuture<Unit>
                main { pending = owner.idle() }
                pending.get(90, TimeUnit.SECONDS)
                RgProfiles.get(context).idle().get(90, TimeUnit.SECONDS)
                await("Profile mutation completed") { !RgProfiles.get(context).busy }
            }
            restore { main { owner.inspectJob = null; owner.clock = clock; owner.ruleUrls = urls; owner.testConnectivity = network
                runtime.inspectRegional = regionalInspection } }
            restore { scheduler.cancel(4501); scheduler.cancel(4502) }
            restore {
                await("Owned runtime released before state restoration") { runtime.proxyPort == null && runtime.webViewPort == null }
                val stopped = RgProxyRuntime::class.java.getDeclaredField("stopping").apply { isAccessible = true }.get(runtime) as Process?
                if (stopped != null) assertTrue("Owned child exited before restoring rule files", stopped.waitFor(5, TimeUnit.SECONDS))
            }
            restore { runtime.useTestEndpoint(testUrl) }
            restore {
                for ((name, bytes) in files) {
                    val file = File(home, name)
                    if (bytes == null) { if (file.exists()) assertTrue(file.delete()) } else file.writeBytes(bytes)
                }
                home.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("regional-") && it.name !in previousDirectories }
                    .forEach { assertTrue(it.deleteRecursively()) }
            }
            for ((file, snapshot) in prefs) restore {
                val preferences = context.getSharedPreferences(file, Context.MODE_PRIVATE)
                assertTrue(preferences.edit().clear().apply { snapshot.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    else -> error("Unexpected controlled preference type")
                } } }.commit())
                assertTrue("Starting private state restored", snapshot == preferences.all)
            }
            restore { RgProfiles.get(context).activate().get(45, TimeUnit.SECONDS) }
            restore { // activate restores in-memory nodes; then preserve the exact starting private files.
                for ((name, bytes) in files) {
                    val file = File(home, name)
                    if (bytes == null) { if (file.exists()) assertTrue(file.delete()) } else file.writeBytes(bytes)
                }
            }
            restore { for (job in jobs) assertEquals(JobScheduler.RESULT_SUCCESS, scheduler.schedule(job)) }
            report.put("startingStateRestored", failure == null).put("startingProfileCount", startingProfileCount)
            restore { File(context.filesDir, "issue45-refresh-restoration.json").writeText(JSONObject()
                .put("startingStateRestored", failure == null).put("privateProfileDataLogged", false).toString()) }
            failure?.let { throw it }
        }.use {
            Closeable { File(context.filesDir, "issue45-refresh-results.json").writeText(report.toString(2)); afterReport() }.use { body() }
        }
        File(context.filesDir, "issue45-refresh-results.json").writeText(report.toString(2))
    }

    @Test fun exceptionalReportStillRestoresDueAndPrivateProfileState() {
        val before = due.all.toMap()
        val privateBefore = profilesPrefs.getString("profiles", null)
        val failure = assertThrows(IllegalStateException::class.java) {
            preserving(afterReport = { error("owned report closure failure") }) {
                assertTrue(due.edit().putLong("subscriptions", 123).commit())
                error("owned primary failure")
            }
        }
        assertEquals("owned primary failure", failure.message)
        assertTrue(failure.suppressed.any { it.message == "owned report closure failure" })
        assertEquals(before, due.all)
        assertTrue("Private profile state unchanged", privateBefore == profilesPrefs.getString("profiles", null))
    }

    @Test fun savedOwnerProfileRemainsPrivateInFreshProcess() {
        val raw = checkNotNull(profilesPrefs.getString("profiles", null))
        val tabs = context.getSharedPreferences("LocalBrowserActivity", Context.MODE_PRIVATE).getString("local.tabs", null)
        val store = ProfileStore({ profilesPrefs.getString("profiles", null) }) { error("Read-only verification") }
        assertTrue(store.readable)
        assertEquals(1, store.profiles.size)
        assertEquals(6, store.profiles.single().nodes.size)
        assertTrue(store.profiles.single().enabled)
        assertTrue("Private cache unchanged", raw == profilesPrefs.getString("profiles", null))
        assertTrue("Private tabs unchanged", tabs == context.getSharedPreferences("LocalBrowserActivity", Context.MODE_PRIVATE).getString("local.tabs", null))
        assertNull(runtime.proxyPort)
        File(context.filesDir, "issue45-owner-preservation.json").writeText(JSONObject().put("profiles", 1).put("nodes", 6)
            .put("enabled", true).put("cacheSha256", digest(raw.toByteArray())).put("tabsSha256", tabs?.let { digest(it.toByteArray()) })
            .put("rawCacheAndTabsUnchanged", true).put("privateDataLogged", false).put("coreStarted", false).toString(2))
    }

    @Test(timeout = 300000) fun regionalReadAndUncertainApplyFailuresSettleAndKeepLastGood() = preserving {
        report.put("suite", "correction02-regional-faults")
        var now = System.currentTimeMillis()
        main { owner.clock = { now }; owner.testConnectivity = { true } }
        assertTrue(due.edit().putLong("subscriptions", now).putLong("regional", now).commit())
        scheduler.cancel(4501); scheduler.cancel(4502)
        val home = File(context.filesDir, "mihomo").apply { mkdirs() }
        val lastGood = File(home, "regional-owned-last-good").apply { assertTrue(mkdir()) }
        val download = File(home, "regional-owned-download").apply { assertTrue(mkdir()) }
        for (name in RegionalRules.sources.keys) File(lastGood, name).writeBytes(context.assets.open("mihomo/$name").use { it.readBytes() })
        RegionalRules.select(home, lastGood)
        val input = File(download, "domain.txt").apply { writeText("owned-regional.test\nregional-refresh.test\n") }
        val binary = File(context.applicationInfo.nativeLibraryDir, "libmihomo.so")
        val child = ProcessBuilder(binary.absolutePath, "convert-ruleset", "domain", "text", input.absolutePath,
            File(download, "cn-domain.mrs").absolutePath).redirectErrorStream(true).redirectOutput(File("/dev/null"))
            .apply { environment()["GOMAXPROCS"] = "2" }.start()
        try { assertTrue(child.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, child.exitValue()) }
        finally { child.destroyForcibly(); child.waitFor(2, TimeUnit.SECONDS) }
        RouteFixture(instrumentation.context.assets.open("proxy-fixture.p12")).use { fixture ->
            val local = "http://127.0.0.1:${fixture.http}"
            val yaml = "proxies: [{type: http, server: 127.0.0.1, port: ${fixture.fast}}]"
            fixture.subscriptions["/subscription"] = 200 to yaml
            fixture.binaryResponses["/cn-domain.mrs"] = 200 to File(download, "cn-domain.mrs").readBytes()
            fixture.binaryResponses["/cn-ip.mrs"] = 200 to File(lastGood, "cn-ip.mrs").readBytes()
            val urls = RegionalRules.sources.mapValues { (name, _) -> "$local/$name" }
            val store = ProfileStore({ profilesPrefs.getString("profiles", null) }) { profilesPrefs.edit().putString("profiles", it).commit() }
            assertTrue(store.save(listOf(SavedProfile("$local/subscription", "Owned regional", true, ProfileImport.nodes(yaml)))))
            runtime.useTestEndpoint("$local/health")
            main { owner.ruleUrls = urls }
            ActivityScenario.launch(LocalBrowserActivity::class.java).use { scene ->
                await("Controlled genuine core ready") { runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort }
                val api = CoreControl(File(home, "control.sock").absolutePath)
                fun count() = api.request("GET", "/providers/rules").getJSONObject("providers").getJSONObject("CN-DOMAIN").getInt("ruleCount")
                val oldCount = count()
                assertTrue(oldCount > 2)
                fun direct(label: String) {
                    val connection = runtime.openConnection(URL("$local/observe-$label"))
                    try { assertEquals("127.0.0.1", connection.inputStream.bufferedReader().use { it.readText() }) }
                    finally { connection.disconnect() }
                }
                fun resetLastGood() {
                    RegionalRules.select(home, lastGood)
                    val config = ProxyPolicy.configuration(ProxyPolicy.nodes(store.profiles.map { it.asSource() }), runtime.proxyPort!!,
                        File(home, "control.sock").absolutePath, listOf("system"), runtime.testUrl, lastGood.absolutePath)
                    val file = File(home, "issue45-route.json").apply { writeText(ProxyPolicy.configurationText(config.toString())) }
                    api.request("PUT", "/configs", JSONObject().put("path", file.absolutePath), 10000)
                    assertEquals(oldCount, count())
                }
                val page = "$local/page"
                scene.onActivity { assertTrue(it.tabs.current.session.open(page).accepted()) }
                fun js(code: String): String {
                    val result = CompletableFuture<String>()
                    scene.onActivity { it.tabs.current.session.page!!.evaluateJavascript(code) { value -> result.complete(value) } }
                    return result.get(5, TimeUnit.SECONDS)
                }
                await("Controlled fault-test document ready") { js("location.href === ${JSONObject.quote(page)} && document.readyState === 'complete' && !!document.querySelector('#note')") == "true" }
                assertEquals("true", js("document.querySelector('#note').value='regional-retained-form';window.regionalSentinel=456;true"))
                val pageRequests = fixture.observations.count { it.first == "/page" }
                val launchPort = runtime.proxyPort!!
                runtimeCounts("regional-launch", true, launchPort)

                val pointer = File(home, "regional-current")
                val mode = android.system.Os.stat(pointer.absolutePath).st_mode and 0x1ff
                val requests = fixture.observations.count { it.first.endsWith(".mrs") }
                android.system.Os.chmod(pointer.absolutePath, 0)
                try {
                    assertThrows(java.io.IOException::class.java) { RegionalRules.current(home) }
                    assertFalse(runtime.refreshRegional(urls).get(5, TimeUnit.SECONDS))
                    assertTrue(due.edit().putLong("regional", 0).commit())
                    lateinit var completed: CompletableFuture<Unit>
                    main { completed = owner.runDue() }
                    completed.get(10, TimeUnit.SECONDS)
                    assertEquals(now, due.getLong("regional", 0))
                    main { assertTrue(owner.idle().isDone) }
                } finally { android.system.Os.chmod(pointer.absolutePath, mode) }
                assertEquals(requests, fixture.observations.count { it.first.endsWith(".mrs") })
                assertEquals(lastGood, RegionalRules.current(home)); assertEquals(oldCount, count()); direct("read-failure")
                cases.put(JSONObject().put("case", "real-pointer-read-failure").put("regionalAndDueFuturesSettled", true).put("loadedRuleCount", oldCount))
                val subscriptions = fixture.observations.count { it.first == "/subscription" }
                now += 86_400_000; runDue()
                assertNotEquals(lastGood, RegionalRules.current(home)); assertEquals(2, count())
                assertTrue(fixture.observations.count { it.first == "/subscription" } > subscriptions)
                cases.put(JSONObject().put("case", "later-due-retry").put("subscriptionAndRegionalProgress", true).put("loadedRuleCount", 2))

                for (fault in listOf("apply-response", "pointer-write", "rollback-refused")) {
                    resetLastGood()
                    var staged: File? = null
                    var appliedCount = 0; var rollback = false
                    val blocker = File(home, "regional-next")
                    if (fault != "apply-response") assertTrue(blocker.mkdir()) // A real pointer-write I/O failure.
                    runtime.inspectRegional = { phase, file ->
                        if (phase == "applied") {
                            staged = file; appliedCount = count()
                            check(appliedCount == 2 && RegionalRules.current(home) == lastGood)
                            check(RegionalRules.sources.keys.all { File(file, it).isFile })
                            if (fault == "apply-response") throw java.io.IOException("Owned response loss after genuine apply")
                        } else if (phase == "rollback") {
                            rollback = true
                            check(RegionalRules.sources.keys.all { File(checkNotNull(staged), it).isFile })
                            if (fault == "rollback-refused") file.writeText("invalid: [") // Genuine core refuses this rollback.
                        }
                    }
                    try { assertFalse(runtime.refreshRegional(urls).get(30, TimeUnit.SECONDS)) }
                    finally { runtime.inspectRegional = null; if (fault != "apply-response") assertTrue(blocker.delete()) }
                    assertEquals(2, appliedCount); assertTrue(rollback); assertEquals(lastGood, RegionalRules.current(home))
                    assertTrue(RegionalRules.sources.keys.all { File(lastGood, it).isFile })
                    if (fault == "rollback-refused") {
                        await("Unconfirmed rollback stops owned core and clears WebView proxy") { runtime.proxyPort == null && runtime.webViewPort == null }
                        assertTrue(RegionalRules.sources.keys.all { File(checkNotNull(staged), it).isFile })
                        runtimeCounts("rollback-direct", false, launchPort)
                    } else { assertEquals(oldCount, count()); assertFalse(checkNotNull(staged).exists()) }
                    direct(fault)
                    cases.put(JSONObject().put("case", fault).put("candidateLoadedRuleCount", appliedCount)
                        .put("pointerLastGood", true).put("coreStoppedForDirect", runtime.proxyPort == null)
                        .put("candidateFilesRetained", checkNotNull(staged).exists()))
                }
                assertEquals("\"regional-retained-form\"", js("document.querySelector('#note').value"))
                assertEquals("456", js("window.regionalSentinel"))
                assertEquals(pageRequests, fixture.observations.count { it.first == "/page" })
                cases.put(JSONObject().put("case", "regional-fault-form-retained").put("pageRequests", pageRequests))
                now += 86_400_000; runDue()
                assertNotEquals(lastGood, RegionalRules.current(home)); assertNull(runtime.proxyPort); direct("stopped-core-retry")
                scene.recreate()
                await("Recreated runtime loads selected retry pair") { runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort }
                assertEquals(2, count()); runtimeCounts("regional-recreate", true, runtime.proxyPort!!)
                report.put("provider", WebView.getCurrentWebViewPackage()?.versionName).put("result", "PASS_REGIONAL_FAULT_CASES")
            }
        }
    }

    @Test(timeout = 300000) fun actualJobsRefreshOnAndOffKeepLastGoodAndPreserveLiveForm() = preserving {
        val jobStarts = CopyOnWriteArrayList<Int>()
        var now = System.currentTimeMillis()
        main { owner.clock = { now }; owner.inspectJob = { jobStarts += it } }
        assertTrue(due.edit().putLong("subscriptions", now).putLong("regional", now).commit())
        scheduler.cancel(4501); scheduler.cancel(4502)
        control("valid-one")
        RouteFixture(instrumentation.context.assets.open("proxy-fixture.p12")).use { fixture ->
            val first = "http://127.0.0.1:${fixture.http}/subscription"
            val second = "$origin/subscription"
            fun yaml(port: Int) = "proxies: [{type: http, server: 127.0.0.1, port: $port}]\nrules: ['MATCH,REJECT']\nmode: global"
            fixture.subscriptions["/subscription"] = 200 to yaml(fixture.slow)
            val store = ProfileStore({ profilesPrefs.getString("profiles", null) }) { profilesPrefs.edit().putString("profiles", it).commit() }
            assertTrue(store.save(listOf(SavedProfile(first, "Owned ON", true, ProfileImport.nodes(yaml(fixture.slow))),
                SavedProfile(second, "Owned OFF", false, ProfileImport.nodes(yaml(fixture.fast))))))
            runtime.useTestEndpoint("http://127.0.0.1:${fixture.http}/health")
            main { owner.ruleUrls = RegionalRules.sources.mapValues { (name, _) -> "$origin/$name" } }
            ActivityScenario.launch(LocalBrowserActivity::class.java).use { scene ->
                val profiles = RgProfiles.get(context)
                await("Genuine runtime ready") { runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort && profiles.profiles.size == 2 }
                runtimeCounts("launch", true, runtime.proxyPort!!)
                val api = CoreControl(File(context.filesDir, "mihomo/control.sock").absolutePath)
                fun regionalProbe(expectedRule: String) {
                    val config = ProxyPolicy.configuration(ProxyPolicy.nodes(profiles.profiles.map { it.asSource() }), runtime.proxyPort!!,
                        File(context.filesDir, "mihomo/control.sock").absolutePath, listOf("system"), runtime.testUrl,
                        RegionalRules.current(File(context.filesDir, "mihomo")).absolutePath)
                        .put("hosts", JSONObject().put("regional-refresh.test", "127.0.0.1"))
                    val file = File(context.filesDir, "mihomo/issue45-route.json").apply { writeText(ProxyPolicy.configurationText(config.toString())) }
                    api.request("PUT", "/configs", JSONObject().put("path", file.absolutePath), 10000)
                    val response = CompletableFuture.supplyAsync {
                        val connection = runtime.openConnection(URL("http://regional-refresh.test:${fixture.http}/hold-regional"))
                        try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
                    }
                    var matched: JSONObject? = null
                    await("Live regional connection observed") {
                        val connections = api.request("GET", "/connections").optJSONArray("connections") ?: JSONArray()
                        matched = (0 until connections.length()).map { connections.getJSONObject(it) }
                            .firstOrNull { it.getJSONObject("metadata").optString("host") == "regional-refresh.test" }
                        matched != null
                    }
                    assertEquals(expectedRule, matched!!.getString("rule"))
                    if (expectedRule == "RuleSet") assertEquals("CN-DOMAIN", matched!!.getString("rulePayload"))
                    assertEquals("127.0.0.1", response.get(5, TimeUnit.SECONDS))
                    cases.put(JSONObject().put("case", "regional-data-$expectedRule").put("matchedRule", expectedRule)
                        .put("rulePayload", matched!!.getString("rulePayload")).put("destinationPeer", "127.0.0.1"))
                }
                fun probe(label: String, expected: String, egress: String = "AUTO") {
                    val config = ProxyPolicy.configuration(ProxyPolicy.nodes(profiles.profiles.map { it.asSource() }), runtime.proxyPort!!,
                        File(context.filesDir, "mihomo/control.sock").absolutePath, listOf("system"), runtime.testUrl,
                        RegionalRules.current(File(context.filesDir, "mihomo")).absolutePath)
                        .put("hosts", JSONObject().put("route.test", "127.0.0.1"))
                    val rules = JSONArray().put("DOMAIN,route.test,EGRESS")
                    val original = config.getJSONArray("rules"); for (i in 0 until original.length()) rules.put(original.get(i))
                    config.put("rules", rules)
                    val file = File(context.filesDir, "mihomo/issue45-route.json").apply { writeText(ProxyPolicy.configurationText(config.toString())) }
                    api.request("PUT", "/configs", JSONObject().put("path", file.absolutePath), 10000)
                    assertEquals(egress, ProxyPolicy.applyHealth(1, runtime.testUrl, api::request))
                    val connection = runtime.openConnection(URL("http://route.test:${fixture.http}/observe-$label"))
                    try { assertEquals(expected, connection.inputStream.bufferedReader().use { it.readText() }) }
                    finally { connection.disconnect() }
                    cases.put(JSONObject().put("case", label).put("destinationPeer", expected))
                }
                probe("before-refresh", "127.0.0.2")
                val page = "http://127.0.0.1:${fixture.http}/page"
                scene.onActivity { assertTrue(it.tabs.current.session.open(page).accepted()) }
                fun js(code: String): String {
                    val answer = CompletableFuture<String>()
                    scene.onActivity { it.tabs.current.session.page!!.evaluateJavascript(code) { value -> answer.complete(value) } }
                    return answer.get(5, TimeUnit.SECONDS)
                }
                await("Controlled WebView document committed") {
                    var ready = false
                    scene.onActivity { ready = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.READY &&
                        it.tabs.current.session.state.url == page && it.tabs.current.session.page!!.isShown }
                    ready && js("location.href === ${JSONObject.quote(page)} && document.readyState === 'complete' && !!document.querySelector('#note')") == "true"
                }
                assertEquals("true", js("document.querySelector('#note').value='owned-retained-form';window.refreshSentinel=123;true"))
                assertEquals("\"owned-retained-form\"", js("document.querySelector('#note').value"))
                val pageRequests = fixture.observations.count { it.first == "/page" }
                report.put("pageRequestsBeforeRefresh", pageRequests).put("initialFormValueVerified", true)
                fixture.subscriptions["/subscription"] = 200 to yaml(fixture.fast)
                control("valid-two")
                now += 1_800_000 // TEST ONLY: production constants and real JobService/network path unchanged.
                instrumentation.uiAutomation.executeShellCommand("cmd jobscheduler run -f com.code2hack.eyebrowse.rg 4501").use {
                    report.put("jobCommand", it.fileDescriptor.let { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() })
                }
                await("Real JobService performed ON and OFF refresh") {
                    profiles.profiles.first().nodes.first()["port"] == fixture.fast && profiles.profiles.last().nodes.size == 2
                }
                runDue() // Coalesces with any running job; no second download at the same due time.
                assertTrue(jobStarts.contains(4501))
                assertEquals(listOf("Owned ON", "Owned OFF"), profiles.profiles.map { it.name })
                assertEquals(listOf(true, false), profiles.profiles.map { it.enabled })
                assertEquals(1, ProxyPolicy.nodes(profiles.profiles.map { it.asSource() }).size)
                probe("after-refresh", "127.0.0.3")
                val count = fixture.observations.count { it.first == "/subscription" }
                runDue(); runDue()
                assertEquals(count, fixture.observations.count { it.first == "/subscription" })
                val lastGood = profilesPrefs.getString("profiles", null)
                for ((mode, response) in listOf("malformed" to (200 to "malformed"), "http-error" to (503 to "error"), "offline" to (0 to ""),
                    "timeout" to (200 to yaml(fixture.slow)))) {
                    control(mode)
                    fixture.subscriptions["/subscription"] = response
                    if (mode == "timeout") fixture.responseDelay["/subscription"] = 16_000
                    now += 1_800_000; runDue()
                    fixture.responseDelay.clear()
                    assertEquals(lastGood, profilesPrefs.getString("profiles", null))
                    cases.put(JSONObject().put("case", mode).put("lastGoodKept", true))
                }
                probe("failure-keeps-cached-route", "127.0.0.3")
                fixture.fastHealthy = false; Thread.sleep(1100)
                probe("all-down-direct-fallback", "127.0.0.1", "DIRECT")
                fixture.fastHealthy = true; Thread.sleep(1100)
                // A failing ON source must not prevent the OFF source's valid update.
                control("valid-one")
                now += 1_800_000; runDue()
                assertEquals(1, profiles.profiles.last().nodes.size)
                assertFalse(profiles.profiles.last().enabled)
                val beforeOffline = profilesPrefs.getString("profiles", null)
                val beforeDue = due.all.toMap()
                main { owner.testConnectivity = { false } }
                now += 1_800_000; runDue()
                assertEquals(beforeOffline, profilesPrefs.getString("profiles", null)); assertEquals(beforeDue, due.all)
                main { owner.testConnectivity = null }
                runDue()
                cases.put(JSONObject().put("case", "test-only-offline-and-connectivity-return").put("lastGoodAndOverdueKept", true))
                val home = File(context.filesDir, "mihomo")
                val before = RegionalRules.current(home)
                regionalProbe("IPCIDR")
                control("valid-two")
                now += 86_400_000; runDue()
                val activated = RegionalRules.current(home)
                assertNotEquals(before, activated)
                assertTrue(RegionalRules.validate(File(context.applicationInfo.nativeLibraryDir, "libmihomo.so"), activated))
                regionalProbe("RuleSet")
                val hashes = RegionalRules.sources.keys.associateWith { digest(File(activated, it).readBytes()) }
                for (mode in listOf("rule-error", "rule-corrupt", "rule-partial")) {
                    control(mode); now += 86_400_000; runDue()
                    assertEquals(activated, RegionalRules.current(home))
                assertEquals(hashes, RegionalRules.sources.keys.associateWith { digest(File(activated, it).readBytes()) })
                    cases.put(JSONObject().put("case", mode).put("lastGoodRulesKept", true))
                }
                val retainedValue = js("document.querySelector('#note').value")
                report.put("formValueAfterRefresh", retainedValue).put("pageRequestsAfterRefresh", fixture.observations.count { it.first == "/page" })
                assertEquals("\"owned-retained-form\"", retainedValue)
                assertEquals("123", js("window.refreshSentinel"))
                assertEquals(pageRequests, fixture.observations.count { it.first == "/page" })
                cases.put(JSONObject().put("case", "page-and-form-retained").put("pageRequests", pageRequests))
                assertEquals("rule", api.request("GET", "/configs").getString("mode"))
                scene.onActivity {
                    it.showUtility(LocalBrowserActivity.Utility.SETTINGS)
                    assertNotNull(it.root.findViewWithTag<View>("settings.pointer.value"))
                    assertNotNull(it.root.findViewWithTag<View>("settings.scroll.value"))
                    it.dismissUtility(); assertTrue(it.address.requestFocus())
                    it.address.setText("owned"); it.address.setSelection(it.address.length())
                    assertTrue(it.keyButtons.getValue(RgKeyboardKeys.Key.Character("a")).performClick())
                    assertEquals("owneda", it.address.text.toString())
                    it.dismissKeyboard()
                    assertEquals(8, it.controls.size)
                    it.showUtility(LocalBrowserActivity.Utility.PROXY)
                }
                fixture.responseDelay["/subscription"] = 2000
                fixture.subscriptions["/subscription"] = 200 to yaml(fixture.slow)
                val beforeDelete = fixture.observations.count { it.first == "/subscription" }
                val delayed = profiles.refreshSaved()
                await("Delayed response started") { fixture.observations.count { it.first == "/subscription" } > beforeDelete }
                scene.onActivity { assertTrue(it.root.findViewWithTag<View>("profile.delete.0").performClick()) }
                delayed.get(60, TimeUnit.SECONDS)
                await("Deletion queued during refresh persisted") { !profiles.busy && profiles.profiles.none { it.source == first } }
                fixture.responseDelay.clear()
                val deletedCount = fixture.observations.count { it.first == "/subscription" }
                now += 1_800_000; runDue()
                assertEquals(deletedCount, fixture.observations.count { it.first == "/subscription" })
                cases.put(JSONObject().put("case", "delete-during-delayed-refresh").put("sourceStayedDeleted", true))
                val processField = RgProxyRuntime::class.java.getDeclaredField("process").apply { isAccessible = true }
                val process = processField.get(runtime)
                scene.recreate()
                await("Recreated foreground runtime ready") { runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort }
                runtimeCounts("recreate", true, runtime.proxyPort!!)
                val previousPort = runtime.proxyPort!!
                scene.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                await("Background runtime stopped") { runtime.proxyPort == null && runtime.webViewPort == null }
                runtimeCounts("background", false, previousPort)
                scene.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                await("Resumed runtime ready") { runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort }
                runtimeCounts("resume", true, runtime.proxyPort!!)
                main { owner.scheduleJobs() }
                val jobs = scheduler.allPendingJobs.filter { it.id in listOf(4501, 4502) }
                assertEquals(2, jobs.size)
                assertEquals(setOf(1800L, 86400L), jobs.map { it.extras.getLong("intervalSeconds") }.toSet())
                assertTrue(jobs.all { it.isPersisted && !it.isPeriodic && it.networkType == android.app.job.JobInfo.NETWORK_TYPE_ANY })
                val health = RgProxyRuntime::class.java.getDeclaredField("health").apply { isAccessible = true }.get(runtime) as java.util.concurrent.ScheduledFuture<*>
                assertTrue(health.getDelay(TimeUnit.SECONDS) in 0..300)
                report.put("jobs", JSONArray(jobs.map { JSONObject().put("id", it.id).put("intervalSeconds", it.extras.getLong("intervalSeconds"))
                    .put("minimumLatencyMs", it.minLatencyMillis).put("persisted", it.isPersisted).put("periodic", it.isPeriodic) }))
                    .put("jobStarts", JSONArray(jobStarts)).put("healthSeconds", ProxyPolicy.HEALTH_SECONDS)
                    .put("provider", WebView.getCurrentWebViewPackage()?.versionName).put("rules", JSONObject(hashes))
                    .put("coreWasGenuineProcess", process is Process).put("result", "PASS_EXERCISED_CASES")
                instrumentation.uiAutomation.takeScreenshot().also { image ->
                    File(context.filesDir, "issue45-refresh-native.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    image.recycle()
                }
            }
            await("No foreground core/provider after scenario") { runtime.proxyPort == null && runtime.webViewPort == null }
        }
    }
}
