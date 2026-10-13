package com.code2hack.eyebrowse.rg.proxy

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileImportTest {
    private val yaml = """
        proxies:
          - {name: 'credential-looking-name', type: http, server: 127.0.0.1, port: 8123}
          - {name: same, type: socks5, server: proxy.test, port: 1080, username: dummy, password: dummy-secret}
    """.trimIndent()

    @Test fun canonicalUrlAndWrapperPreserveAccountQueryAndPathIdentity() {
        val source = "https://example.test/sub/a%2Fb?token=first%2Ftoken&account=one&account=two"
        assertEquals(source, ProfileImport.source("HTTPS://EXAMPLE.test:443/sub/a%2Fb?token=first%2Ftoken&account=one&account=two#display"))
        assertEquals(source, ProfileImport.source("clash://install-config?url=" + java.net.URLEncoder.encode(source, "UTF-8") + "&name=ignored"))
        assertNotEquals(ProfileImport.source(source), ProfileImport.source(source.replace("token=first", "token=second")))
        assertNotEquals(ProfileImport.source(source), ProfileImport.source(source.replace("account=one&account=two", "account=two&account=one")))
        assertEquals("http://example.test/", ProfileImport.source("http://example.test:80"))
        for (value in listOf("file:///private/config", "https://user:password@example.test/", "https://", "https://host/a\nsecret",
            "clash://other?url=https%3A%2F%2Fexample.test", "clash://install-config?url=a&url=b"))
            assertThrows(Exception::class.java) { ProfileImport.source(value) }
    }

    @Test fun fullConfigsContributeOnlyNodesAndCollidingNamesRemainDistinctAcrossSources() {
        val text = yaml + """

            mode: global
            mixed-port: 9090
            allow-lan: true
            external-controller: 0.0.0.0:9091
            dns: {enable: true, listen: '0.0.0.0:53'}
            rules: ['MATCH,REJECT']
            script: {code: 'owned dummy'}
        """.trimIndent()
        val nodes = ProfileImport.nodes(text)
        assertEquals(2, nodes.size)
        val sources = listOf(ProxySource("one", true, nodes), ProxySource("two", true, nodes))
        val extracted = ProxyPolicy.nodes(sources)
        assertEquals(4, extracted.map { it.getString("name") }.distinct().size)
        assertFalse(extracted.toString().contains("credential-looking-name"))
        val config = ProxyPolicy.configuration(extracted, 12345, "/app/owned.sock", listOf("system"))
        assertEquals("rule", config.getString("mode"))
        assertEquals("", config.getString("external-controller"))
        assertFalse(config.getBoolean("allow-lan"))
        assertFalse(config.getJSONObject("dns").has("listen"))
        assertFalse(config.has("script"))
    }

    @Test fun safeYamlRejectsTagsAliasesDuplicateKeysUnsafeNodeOptionsAndEmptyUnsupportedFormats() {
        for (text in listOf("!!javax.script.ScriptEngineManager []", "proxies: &a [*a]",
            "proxies: []\nproxies: []", "proxies: []", "proxy-providers: {remote: {url: 'https://dummy.test'}}",
            "cHJveGllczogW10=", yaml.replace("port: 8123", "port: 0"),
            yaml.replace("type: http", "type: http, dialer-proxy: DIRECT"),
            yaml.replace("type: http", "type: http, certificate: /private/file"),
            yaml.replace("type: http", "type: http, ws-opts: {ca: /private/file}"),
            "proxies: [{type: unsupported, server: proxy.test, port: 80}]"))
            assertThrows("Unsafe/unsupported document", Exception::class.java) { ProfileImport.nodes(text) }
        assertEquals(1, ProfileImport.nodes("proxies: [{type: unsupported}, {type: http, server: proxy.test, port: 80}]").size)
        assertThrows(Exception::class.java) { ProfileImport.nodes("x".repeat(ProfileImport.MAX_BYTES + 1)) }
        val rejected = assertThrows(ProfileImport.Rejected::class.java) {
            ProfileImport.nodes(yaml.replace("type: http", "type: http, private-secret-field: dummy-secret-value"))
        }
        assertEquals("NODE_FIELD", rejected.category)
        assertEquals("OTHER", rejected.field)
        assertEquals("http", rejected.protocol)
        assertFalse(rejected.toString().contains("private-secret"))
        assertEquals("UNSUPPORTED_ROOT", assertThrows(ProfileImport.Rejected::class.java) {
            ProfileImport.nodes("cHJveGllczogW10=")
        }.category)
    }

    @Test fun sameSourceUpdatePreservesNameToggleWhileDistinctAccountsAndRestartsStayIndependent() {
        var disk: String? = null
        fun store() = ProfileStore({ disk }) { disk = it; true }
        val store = store()
        val first = ProfileImport.source("https://subscription.test/?token=one")
        val second = ProfileImport.source("https://subscription.test/?token=two")
        assertTrue(store.save(store.imported(first, ProfileImport.nodes(yaml))))
        assertTrue(store.save(store.profiles.map { it.enabled(false) }))
        assertTrue(store.save(store.imported(second, ProfileImport.nodes(yaml))))
        assertTrue(store.save(store.imported(first, ProfileImport.nodes(yaml.replace("8123", "8124")))))
        val restarted = store()
        assertEquals(listOf(first, second), restarted.profiles.map { it.source })
        assertEquals(listOf("Profile 1", "Profile 2"), restarted.profiles.map { it.name })
        assertEquals(listOf(false, true), restarted.profiles.map { it.enabled })
        assertEquals(8124, restarted.profiles.first().nodes.first()["port"])
        assertTrue(restarted.save(restarted.profiles.drop(1)))
        assertEquals(listOf(second), store().profiles.map { it.source })
    }

    @Test fun saveFailureAndUnreadableStoreCannotEraseLastGoodData() {
        var disk: String? = null
        var writable = true
        val store = ProfileStore({ disk }) { if (writable) { disk = it; true } else false }
        val source = "https://dummy.test/"
        assertTrue(store.save(store.imported(source, ProfileImport.nodes(yaml))))
        val previous = disk
        writable = false
        assertFalse(store.save(emptyList()))
        assertEquals(previous, disk)
        assertEquals(1, store.profiles.size)
        var wrote = false
        val unreadable = ProfileStore({ "{broken" }) { wrote = true; true }
        assertFalse(unreadable.readable)
        assertFalse(unreadable.save(emptyList()))
        assertFalse(wrote)
        assertFalse(store.profiles.toString().contains("dummy-secret"))
        assertFalse(store.profiles.toString().contains("dummy.test"))
    }
}
