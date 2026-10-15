package com.code2hack.eyebrowse.rg.chromium

import android.app.Application
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.webkit.WebView
import com.norman.webviewup.lib.WebViewReplace
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Explicitly provisioned, fixed engine; no download, profile migration or system update. */
class ChromiumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getProcessName() != packageName) return // Upstream owns its renderer service initialization.
        val started = SystemClock.elapsedRealtime()
        val report = JSONObject().put("pid", Process.myPid()).put("uid", Process.myUid())
            .put("engineSha256", ENGINE_SHA256).put("engineSource", "a296091d1ed08d4dde44b8a427477fe570911472")
            .put("engineInput", "official-chromium-snapshot-1719582").put("loaderCommit", LOADER_COMMIT)
            .put("loaderRepair", "issue57-instance-lifetime-readonly-fd")
            .put("rendererIsolation", "ISOLATED_SERVICE_DECLARED_NATIVE_BOUNDARY_CHECKS_PENDING")
        val output = File(filesDir, "chromium-startup.json")
        try {
            val directory = File(filesDir, "chromium-engine-upstream158")
            val archive = File(directory, "engine.apk")
            check(archive.isFile && sha256(archive) == ENGINE_SHA256) { "Missing or unqualified engine.apk" }
            val libs = File(directory, "libs")
            ZipFile(archive).use { zip ->
                NATIVE_SHA256.forEach { (name, expected) ->
                    val target = File(libs, "arm64-v8a/$name")
                    if (!target.exists()) {
                        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                        val pending = File(target.parentFile, "$name.pending")
                        zip.getInputStream(checkNotNull(zip.getEntry("lib/arm64-v8a/$name"))).use { input ->
                            pending.outputStream().use { out -> input.copyTo(out) }
                        }
                        check(sha256(pending) == expected) { "Native extraction digest mismatch" }
                        check(pending.renameTo(target)) { "Native extraction rename failed" }
                    }
                    check(sha256(target) == expected) { "Native library digest mismatch" }
                    check(target.setReadable(true, false))
                }
            }
            check(archive.setReadable(true, false))
            report.put("inputVerifiedMs", SystemClock.elapsedRealtime() - started)
            // Owned dummy file only: the child reports whether its actual UID can read this boundary.
            File(filesDir, "chromium-boundary-probe.txt").writeText("i57-owned-dummy")
            // The pinned loader checks that WebViewFactory has never been initialized.
            WebViewReplace.replace(this, archive.absolutePath, libs.absolutePath)
            val provider = checkNotNull(WebView.getCurrentWebViewPackage())
            report.put("provider", provider.packageName).put("version", provider.versionName)
            check(provider.versionName == ENGINE_VERSION) { "Unexpected provider; refusing fallback" }
            val mappings = File("/proc/self/maps").readLines().filter {
                it.contains("webview", ignoreCase = true) || it.contains("chromium-engine")
            }
            report.put("nativeMappings", JSONArray(mappings))
            val expectedLibrary = File(libs, "arm64-v8a/libwebviewchromium.so").canonicalPath
            check(mappings.any {
                val fields = it.trim().split(Regex("\\s+"), limit = 6)
                fields.size == 6 && 'x' in fields[1] &&
                    File(fields[5]).canonicalPath == expectedLibrary
            }) {
                "Pinned native engine mapping not observed"
            }
            report.put("state", "NATIVE_ENGINE_LOADED_API_CHECKS_PENDING")
                .put("provider", provider.packageName).put("version", provider.versionName)
                .put("pssKiB", Debug.getPss())
                .put("startupMs", SystemClock.elapsedRealtime() - started)
            output.writeText(report.toString(2))
        } catch (failure: Throwable) {
            report.put("state", "FAILED_NO_FALLBACK").put("startupMs", SystemClock.elapsedRealtime() - started)
                .put("failure", failure.stackTraceToString().take(16000))
            output.writeText(report.toString(2))
            throw IllegalStateException("Experimental Chromium initialization failed; see chromium-startup.json", failure)
        }
    }

    companion object {
        const val ENGINE_VERSION = "158.0.8107.0"
        const val ENGINE_SHA256 = "8daa731574cf1d0e7f24996dd18c8d51554dc7f27806f2619528021674b78710"
        const val LOADER_COMMIT = "5a5a7d11b5e67bba37787a697f45c9f5d45e6dda"
        private val NATIVE_SHA256 = mapOf(
            "libwebviewchromium.so" to "4a2cd075e018ef3827d21f5e721ee1b4a28ed03429ce19b03bea3ff8931cfad2",
            "libcrashpad_handler_trampoline.so" to "ba112c5254fa29c239341f3f610f0c3d3495268a6f4849a102b76adf2f872d32",
        )

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
