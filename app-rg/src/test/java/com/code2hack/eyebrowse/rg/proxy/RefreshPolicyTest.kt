package com.code2hack.eyebrowse.rg.proxy

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RefreshPolicyTest {
    @Test fun dueTimesSurviveRestartAndHandleOverdueOrChangedWallClock() {
        val last = 100_000L
        assertEquals(1800L, RefreshPolicy.SUBSCRIPTION_SECONDS)
        assertEquals(86400L, RefreshPolicy.REGIONAL_SECONDS)
        assertEquals(300L, ProxyPolicy.HEALTH_SECONDS)
        assertEquals(1L, RefreshPolicy.remaining(last, last + 1_799_999, 1800))
        assertEquals(0L, RefreshPolicy.remaining(last, last + 1_800_000, 1800))
        assertEquals(1L, RefreshPolicy.remaining(last, last + 86_399_999, 86400))
        assertEquals(0L, RefreshPolicy.remaining(last, last + 86_400_000, 86400))
        assertEquals(0L, RefreshPolicy.remaining(last, last + 90_000_000, 1800))
        assertEquals(0L, RefreshPolicy.remaining(0, last, 1800))
        assertEquals(0L, RefreshPolicy.remaining(last, last - 1, 1800))
    }

    @Test fun delayedRefreshRetainsLatestNameToggleAndCannotRecreateDeletedSources() {
        var disk: String? = null
        val store = ProfileStore({ disk }) { disk = it; true }
        val original = mapOf<String, Any?>("type" to "http", "server" to "proxy.test", "port" to 80)
        val replacement = original + ("port" to 81)
        val source = "https://owned.test/one"
        assertTrue(store.save(store.imported(source, listOf(original))))
        assertTrue(store.save(store.profiles.map { it.enabled(false) }))
        assertTrue(store.save(store.refreshed(source, listOf(replacement))))
        assertEquals("Profile 1", store.profiles.single().name)
        assertFalse(store.profiles.single().enabled)
        assertTrue(ProxyPolicy.nodes(store.profiles.map { it.asSource() }).isEmpty())
        assertTrue(store.save(emptyList()))
        assertTrue(store.save(store.refreshed(source, listOf(replacement))))
        assertTrue(ProfileStore({ disk }) { false }.profiles.isEmpty())
    }

    @Test(timeout = 60000) fun genuineCoreRejectsCorruptPartialWrongBehaviorAndSelectsOnlyCompletePair() {
        val binary = System.getenv("EYEBROWSE_MIHOMO_HOST_BINARY")?.let(::File)
        assumeTrue("Pinned Linux core required", binary?.canExecute() == true)
        val home = Files.createTempDirectory("regional-").toFile()
        try {
            val candidate = File(home, "regional-owned").apply { mkdirs() }
            for (name in RegionalRules.sources.keys)
                File("src/main/assets/mihomo/$name").copyTo(File(candidate, name))
            assertTrue(RegionalRules.validate(binary!!, candidate))
            assertEquals(home, RegionalRules.current(home))
            RegionalRules.select(home, candidate)
            assertEquals(candidate, RegionalRules.current(home))
            val pointer = File(home, "regional-current").readBytes()
            val lastGood = File(candidate, "cn-domain.mrs").readBytes()
            val bad = File(home, "regional-bad").apply { mkdirs() }
            File(candidate, "cn-ip.mrs").copyTo(File(bad, "cn-ip.mrs"))
            for (bytes in listOf("malformed".toByteArray(), File(candidate, "cn-domain.mrs").readBytes().take(128).toByteArray(),
                File(candidate, "cn-ip.mrs").readBytes())) {
                File(bad, "cn-domain.mrs").writeBytes(bytes)
                assertFalse(RegionalRules.validate(binary, bad))
                assertArrayEquals(pointer, File(home, "regional-current").readBytes())
                assertArrayEquals(lastGood, File(RegionalRules.current(home), "cn-domain.mrs").readBytes())
            }
            File(bad, "cn-ip.mrs").delete()
            assertFalse(RegionalRules.validate(binary, bad))
            assertEquals(candidate, RegionalRules.current(home))
        } finally { home.deleteRecursively() }
    }
}
