package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.CoreControl
import com.code2hack.eyebrowse.rg.proxy.ProxyPolicy
import com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.security.MessageDigest

/** Owned window07 fixture only. The original browser methods retain their actions and deadlines. */
@RunWith(AndroidJUnit4::class)
class ChromiumPrivateEgressBrowserInstrumentedTest {
    @Test fun originalAnonymousChatGptAddressAndOpen() = throughPrivateFixture("acceptance") {
        ChromiumAnonymousSiteInstrumentedTest().observePublicRootThroughNativeAddressAndOpen()
    }

    @Test fun originalChatGptDocumentDiagnostic() = throughPrivateFixture("document") {
        ChromiumAnonymousDocumentDiagnosticInstrumentedTest().observePublicDocumentForOneBoundedWindow()
    }

    private fun throughPrivateFixture(label: String, original: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        require(args.getString("anonymousSite") == "chatgpt")
        val route = checkNotNull(args.getString("window07RouteBinding"))
        require(route.matches(Regex("[0-9a-f]{64}")))
        val run = checkNotNull(args.getString("evidenceRunId"))
        require(run.matches(Regex("[A-Za-z0-9-]{1,80}")))
        val profiles = File(context.dataDir, "shared_prefs/proxy-profiles.xml")
        check(!profiles.exists()) { "Only the qualified empty prototype profile baseline is allowed" }
        val preferences = localBrowserTestPreferences(context)
        val recovery = preferences.getString("local.tabs", null)
        val configuration = File(context.filesDir, "mihomo/config.json")
        val before = configuration.takeIf { it.isFile }?.readBytes()
        val fixture = File(context.filesDir, "mihomo/private-egress-window07.json")
        check(!fixture.exists()) { "Existing fixture state must be preserved" }
        val baseline = File(context.filesDir, "chromium-window07-$run-baseline.json")
        check(!baseline.exists()) { "Use a new invocation; an old baseline must not be overwritten" }
        // Opaque owned-state backup survives an interrupted instrumentation process. Never publish it.
        baseline.writeText(JSONObject().put("scope", "OWNED_PROTOTYPE_FIXTURE_BASELINE_ONLY")
            .put("evidenceRunId", run).put("routeBinding", route)
            .put("profileFilePresent", false).put("recovery", recovery ?: JSONObject.NULL)
            .put("configurationBase64", before?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            .toString(2))
        val report = JSONObject().put("scope", "PRIVATE_WINDOW07_FIXTURE_NOT_PRODUCTION_NETWORK_ADOPTION")
            .put("candidateHead", args.getString("candidateHead")).put("evidenceRunId", run)
            .put("routeBinding", route).put("originalMethod", label)
            .put("baselineFile", baseline.name).put("baselineSHA256", sha256(baseline.readBytes()))
            .put("configurationBeforeSHA256", before?.let(::sha256) ?: JSONObject.NULL)
            .put("state", "SETUP_INCOMPLETE_ORIGINAL_NOT_INVOKED")
        val runtime = RgProxyRuntime.get(context)
        var acquired = false
        var createdFixture = false
        // Each use attempts its restoration even if the original assertion or another close fails.
        Closeable {
            File(context.filesDir, "chromium-private-egress-window07-fixture-$label.json").writeText(report.toString(2))
        }.use {
            Closeable {
                val saved = preferences.edit().apply {
                    if (recovery == null) remove("local.tabs") else putString("local.tabs", recovery)
                }.commit()
                val restored = saved && preferences.getString("local.tabs", null) == recovery
                report.put("recoveryRestored", restored).put("profileFileStillAbsent", !profiles.exists())
                check(restored && !profiles.exists()) { "Prototype recovery/profile restoration incomplete" }
            }.use {
                Closeable {
                    try {
                        if (acquired) {
                            runtime.release()
                            acquired = false
                            awaitSetupOrClose("completed provider removal") {
                                runtime.proxyPort == null && runtime.webViewPort == null
                            }
                            report.put("runtimeReleasedAndProviderCleared", true)
                        }
                    } finally {
                        if (before == null) {
                            if (configuration.exists()) check(configuration.delete())
                        } else configuration.writeBytes(before)
                        val restored = if (before == null) !configuration.exists()
                            else configuration.isFile && configuration.readBytes().contentEquals(before)
                        report.put("configurationRestored", restored)
                        if (createdFixture && fixture.exists()) check(fixture.delete())
                        report.put("ownedFixtureFileAbsent", !fixture.exists())
                        check(restored && !fixture.exists()) { "Owned fixture/configuration restoration incomplete" }
                    }
                }.use {
                    runtime.acquire()
                    acquired = true
                    awaitSetupOrClose("real core and completed provider configuration") {
                        runtime.status == "Running" && runtime.proxyPort != null && runtime.webViewPort == runtime.proxyPort
                    }
                    val port = checkNotNull(runtime.proxyPort)
                    val config = JSONObject(configuration.readText())
                    check(!config.has("proxy-providers") && !config.has("proxy-groups"))
                    val rules = config.getJSONArray("rules")
                    check(rules.getString(rules.length() - 1) == "MATCH,DIRECT")
                    val node = "I57_WINDOW07_OWNED_HTTP"
                    val group = "I57_WINDOW07_PRIVATE_EGRESS"
                    config.put("proxies", JSONArray().put(JSONObject().put("name", node).put("type", "http")
                        .put("server", "127.0.0.1").put("port", 39072)))
                    config.put("proxy-groups", JSONArray().put(JSONObject().put("name", group).put("type", "select")
                        .put("proxies", JSONArray(listOf(node)))))
                    val routed = JSONArray()
                    for (index in 0 until rules.length() - 1) routed.put(rules.get(index))
                    routed.put("MATCH,$group")
                    config.put("rules", routed) // Keep every preceding DIRECT rule, DNS and listener setting.
                    check(fixture.createNewFile())
                    createdFixture = true
                    fixture.writeText(ProxyPolicy.configurationText(config.toString()))
                    CoreControl(File(context.filesDir, "mihomo/control.sock").absolutePath).use { api ->
                        api.request("PUT", "/configs", JSONObject().put("path", fixture.absolutePath))
                        check(api.request("GET", "/configs").getInt("mixed-port") == port)
                        val selected = api.request("GET", "/proxies/$group")
                        check(selected.getString("now") == node)
                        report.put("actualFixtureGroup", selected.getString("name"))
                            .put("actualFixtureSelection", selected.getString("now"))
                            .put("nativeProxyPort", port).put("webViewProxyPort", runtime.webViewPort)
                            .put("fixtureSHA256", sha256(fixture.readBytes()))
                        try {
                            report.put("state", "ORIGINAL_METHOD_INVOKED_ONCE")
                            original()
                            report.put("state", "ORIGINAL_METHOD_RETURNED_NOT_ACCOUNT_OR_ADOPTION_ACCEPTANCE")
                        } catch (failure: Throwable) {
                            report.put("state", "ORIGINAL_METHOD_THREW").put("failureClass", failure.javaClass.name)
                            throw failure
                        }
                    }
                }
            }
        }
    }

    /** Fixture setup/closure only; the invoked original observation window is unchanged. */
    private fun awaitSetupOrClose(label: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(25)
        check(condition()) { label }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
