package com.code2hack.eyebrowse.rg

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.RgProfiles
import com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime
import com.code2hack.eyebrowse.rg.proxy.RgRefresh
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit

/** Runs existing feature checks under the accepted outer state-restoration seam, using normal JUnit. */
@RunWith(AndroidJUnit4::class)
class VersionIntegrationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun controlled(name: String, online: Boolean = false, body: () -> Unit) =
        ProxyRefreshInstrumentedTest().preserving("issue46-$name") {
            // Prevent controlled launches from refreshing the Owner's real source. The outer guard
            // restores its exact starting profile, due metadata, jobs, files and numeric choices.
            instrumentation.runOnMainSync { RgRefresh.get(context).testConnectivity = { online } }
            context.getSharedPreferences("proxy-profiles", Context.MODE_PRIVATE).edit().remove("profiles").commit().also { assertTrue(it) }
            context.getSharedPreferences("LocalBrowserActivity", Context.MODE_PRIVATE).edit().remove("local.tabs").commit().also { assertTrue(it) }
            RgProfiles.get(context).activate().get(45, TimeUnit.SECONDS)
            if (name == "browser") {
                val bookmarks = File(context.filesDir, "local-browser/bookmarks.properties")
                if (bookmarks.exists()) assertTrue(bookmarks.delete()) // Original bytes are in the outer guard.
            }
            body()
        }
    private fun regressions(name: String, type: Class<*>, methods: List<String>, online: Boolean = false) = controlled(name, online) {
        val observations = JSONArray()
        for (method in methods) {
            val result = JUnitCore().run(Request.method(type, method))
            Closeable {
                observations.put(JSONObject().put("class", type.name).put("method", method).put("runs", result.runCount)
                    .put("failures", result.failureCount).put("ignored", result.ignoreCount).put("timeMs", result.runTime))
                File(context.filesDir, "issue46-$name-methods.json").writeText(observations.toString(2))
                if (name == "refresh") File(context.filesDir, "issue45-refresh-results.json")
                    .copyTo(File(context.filesDir, "issue46-refresh-$method-results.json"), overwrite = true)
            }.use {
                assertEquals("Exactly the declared existing check executed", 1, result.runCount)
                assertEquals(0, result.ignoreCount)
                if (!result.wasSuccessful()) {
                    val failure = result.failures.first().exception
                    result.failures.drop(1).forEach { failure.addSuppressed(it.exception) }
                    throw failure
                }
            }
        }
    }
    @Test fun pointerSettingsAndFiniteStrips() = regressions("pointer", EdgeScrollInstrumentedTest::class.java, listOf(
        "numericRowsMigrateEveryPresetOnceAndPersistStepsAndLimits",
        "bothHiddenStripsContinueInwardThenStopAtVisibleReturnAndRejectTaps",
        "longVisibleTabListOwnsStripScrollingWithoutMovingCoveredPage",
        "allBoundariesDiscardOvershootWithPromptSlowInwardDraw",
        "settingsPersistAndActualPageUsesEveryDeclaredRateAndGain",
        "fullScreenKeyboardEdgesAndOrdinaryCancellationPreserveNativeEditing"))
    @Test fun nativeBrowserKeyboardToolbarAndBookmarks() = regressions("browser", LocalBrowserInstrumentedTest::class.java, listOf(
            "fourNativeEditorsPreserveUnicodeSelectionCaseAndDone",
            "actualAddressKeysKeepDraftAndFixedToolbarUntilOpen",
            "nativeFieldRevealSettlesOnceWithoutReloadOrFocusChange",
            "nativeDpadAdmissionPreservesScopeAndTabEffects",
            "approvedGeometryAndAddressEditingStayBlackAndRecoverable",
            "fourLiveTabsAndPadGesturesKeepCurrentNativeEffects",
            "bookmarksUseCommittedLocationAndDurableTruth",
            "httpErrorControlsRecoverThroughNativeHistoryRefreshAndAddress",
            "liveBlackAndHiddenRecoveryUseProductionWindow"))
    @Test fun profileImportsSelectionAndFallback() = regressions("profiles", ProxyProfilesInstrumentedTest::class.java, listOf(
        "exceptionalTeardownAndReportKeepStartingControlledState",
        "actualImportsPoolFallbackLastGoodAndRecreation",
        "longListGeometryAndOrdinaryInputRemainUsable"))
    @Test fun genuineRoutesAndRecovery() = regressions("routing", MihomoRoutingInstrumentedTest::class.java, listOf(
        "actualProviderCoreAndNativeRoutesFallbackAndRecreation",
        "teardownAndReportFailuresCannotSkipRecoveryRestoration"))
    @Test fun refreshJobsLastGoodFormsAndLifecycle() = regressions("refresh", ProxyRefreshInstrumentedTest::class.java, listOf(
        "regionalReadAndUncertainApplyFailuresSettleAndKeepLastGood",
        "actualJobsRefreshOnAndOffKeepLastGoodAndPreserveLiveForm",
        "exceptionalReportStillRestoresDueAndPrivateProfileState"), online = true)
    @Test fun sharedScannerPurposesAndCameraLifecycle() = regressions("scanner", QrReliabilityInstrumentedTest::class.java, listOf(
        "profilePurposeReturnsRawPayloadOnceWithoutOpeningThePage",
        "profileCancelAndWebPurposeKeepTheirSeparateResultBehavior",
        "webPreviewRequiresDeliberateOpenInCurrentTab",
        "repeatedProfileScanCancelAndPauseResumeReleaseTheCamera"))

    @Test fun representativeActiveAndIdleResources() = controlled("resources") {
        val runtime = RgProxyRuntime.get(context)
        val samples = JSONArray()
        fun sample(phase: String) {
            val cores = JSONArray()
            val binary = File(context.applicationInfo.nativeLibraryDir, "libmihomo.so").canonicalPath
            for (process in File("/proc").listFiles().orEmpty().filter { it.name.toIntOrNull() != null }) {
                if (runCatching { File(process, "exe").canonicalPath == binary }.getOrDefault(false))
                    cores.put(JSONObject().put("pid", process.name).put("stat", File(process, "stat").readText())
                        .put("memory", JSONArray(File(process, "status").readLines().filter {
                            it.startsWith("VmRSS:") || it.startsWith("VmHWM:") || it.startsWith("RssAnon:")
                        })))
            }
            samples.put(JSONObject().put("phase", phase).put("elapsedMs", SystemClock.elapsedRealtime())
                .put("instrumentedAppPssKiB", Debug.getPss()).put("appStat", File("/proc/self/stat").readText())
                .put("cores", cores)
                .put("corePort", runtime.proxyPort ?: JSONObject.NULL).put("webViewPort", runtime.webViewPort ?: JSONObject.NULL))
            File(context.filesDir, "issue46-resources.json").writeText(JSONObject().put("samples", samples)
                .put("method", "Instrumented app PSS/CPU; controlled page and foreground idle, then background; no battery endurance claim")
                .put("savedProfileCount", RgProfiles.get(context).profiles.size)
                .put("enabledNodeCount", RgProfiles.get(context).profiles.filter { it.enabled }.sumOf { it.nodes.size })
                .put("coreBytes", File(context.applicationInfo.nativeLibraryDir, "libmihomo.so").length()).toString(2))
        }
        val start = SystemClock.elapsedRealtime()
        ActivityScenario.launch(LocalBrowserActivity::class.java).use { scene ->
            val end = start + 45_000
            while (runtime.webViewPort == null && SystemClock.elapsedRealtime() < end) SystemClock.sleep(20)
            assertNotNull(runtime.webViewPort)
            samples.put(JSONObject().put("startupToProviderReadyMs", SystemClock.elapsedRealtime() - start))
            val page = "http://100.92.81.33:39030/local-keyboard.html"
            scene.onActivity { assertTrue(it.tabs.current.session.open(page).accepted()) }
            val pageDeadline = SystemClock.elapsedRealtime() + 15_000
            var pageReady = false
            while (!pageReady && SystemClock.elapsedRealtime() < pageDeadline) {
                scene.onActivity { pageReady = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.READY &&
                    it.tabs.current.session.state.url == page }
                SystemClock.sleep(20)
            }
            assertTrue("Owned workload page committed", pageReady)
            sample("active-start")
            repeat(30) {
                scene.onActivity { activity ->
                    activity.showUtility(LocalBrowserActivity.Utility.SETTINGS)
                    activity.inputSettings.stepPointer(1)
                    activity.dismissUtility()
                }
                SystemClock.sleep(1000)
            }
            sample("active-end")
            SystemClock.sleep(30_000)
            sample("foreground-idle-end")
            scene.moveToState(Lifecycle.State.CREATED)
            val stop = SystemClock.elapsedRealtime() + 15_000
            while ((runtime.proxyPort != null || runtime.webViewPort != null) &&
                SystemClock.elapsedRealtime() < stop) SystemClock.sleep(20)
            assertNull("Background core port released", runtime.proxyPort)
            assertNull("Background provider callback completed", runtime.webViewPort)
            SystemClock.sleep(10_000)
            sample("background-idle-end")
        }
    }
}
