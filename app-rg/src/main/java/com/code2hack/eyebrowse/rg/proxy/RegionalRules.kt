package com.code2hack.eyebrowse.rg.proxy

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/** A complete validated pair is selected by one atomic pointer, never two in-place overwrites. */
internal object RegionalRules {
    val sources = linkedMapOf(
        "cn-domain.mrs" to "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geosite/cn.mrs",
        "cn-ip.mrs" to "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geoip/cn.mrs",
    )
    const val MAX_BYTES = 8 * 1024 * 1024

    fun current(home: File): File {
        val name = File(home, "regional-current").takeIf { it.isFile }?.readText()?.trim()
        if (name != null && name.matches(Regex("regional-[A-Za-z0-9-]+"))) {
            val candidate = File(home, name)
            if (sources.keys.all { File(candidate, it).isFile }) return candidate
        }
        return home // Bundled initial data, also retained after a failed first download.
    }

    fun validate(binary: File, directory: File): Boolean = sources.keys.all { name ->
        val input = File(directory, name)
        if (!input.isFile || input.length() !in 1..MAX_BYTES.toLong()) return@all false
        val output = File(directory, "$name.txt")
        var process: Process? = null
        try {
            process = ProcessBuilder(binary.absolutePath, "convert-ruleset",
                if (name == "cn-domain.mrs") "domain" else "ipcidr", "mrs", input.absolutePath, output.absolutePath)
                .redirectErrorStream(true).redirectOutput(File("/dev/null"))
                .apply { environment()["GOMAXPROCS"] = "2"; environment()["GOMEMLIMIT"] = "64MiB" }.start()
            process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0 && output.length() > 0
        } catch (_: Exception) { false }
        finally { process?.destroyForcibly(); process?.waitFor(2, TimeUnit.SECONDS); output.delete() }
    }

    fun select(home: File, candidate: File) {
        require(candidate.parentFile == home && candidate.name.startsWith("regional-"))
        val next = File(home, "regional-next")
        next.outputStream().use { it.write(candidate.name.toByteArray()); it.fd.sync() }
        Files.move(next.toPath(), File(home, "regional-current").toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
