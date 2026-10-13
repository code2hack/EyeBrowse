package com.code2hack.eyebrowse.rg.proxy

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProxyPolicyTest {
    @Test fun androidSlashEscapesBecomeYamlCompatibleWithoutChangingJsonValues() {
        val inputs = listOf(
            """{"url":"http:\/\/example.test\/a","path":"\/private\/core.sock"}""",
            """{"node":{"password":"back\\\/slash","path":"\/a\\\/b","quoted":"say \"\/\"","unicode":"\u002f"}}""",
            """{"path":"back\\/slash"}""",
        )
        for (input in inputs) {
            val text = ProxyPolicy.configurationText(input)
            assertEquals(JSONObject(input).toString(), JSONObject(text).toString())
            assertEquals(text, ProxyPolicy.configurationText(text))
        }
        assertTrue(ProxyPolicy.configurationText(inputs.first()).contains("http://example.test/a"))
        assertTrue(ProxyPolicy.configurationText(inputs.first()).contains("/private/core.sock"))
    }

    private fun source(id: String, enabled: Boolean = true, nodes: List<Map<String, Any?>> = listOf(
        mapOf("name" to "same name with secret", "type" to "http", "server" to "127.0.0.1", "port" to 8001),
    )) = ProxySource(id, enabled, nodes)
    private fun configuration(sources: List<ProxySource>) = ProxyPolicy.configuration(
        ProxyPolicy.nodes(sources), 18789, "/private/app/control.sock", listOf("100.100.100.100"))

    @Test fun enabledUnionKeepsCollidingNamesDistinctAndDoesNotRetainTheirSecrets() {
        val sources = listOf(source("first-secret-url"), source("second-secret-url"), source("off", false))
        val nodes = ProxyPolicy.nodes(sources)
        assertEquals(2, nodes.size)
        assertNotEquals(nodes[0].getString("name"), nodes[1].getString("name"))
        assertFalse(configuration(sources).toString().contains("secret"))
        assertFalse(sources[0].toString().contains("secret"))
        assertEquals("same name with secret", sources[0].nodes[0]["name"])
    }

    @Test fun noSourcesAllOffAndEmptyUsableSetsAreThreeDirectStates() {
        for (sources in listOf(emptyList(), listOf(source("off", false)), listOf(source("empty", nodes = emptyList())))) {
            val config = configuration(sources)
            assertEquals("MATCH,DIRECT", config.getJSONArray("rules").let { it.getString(it.length() - 1) })
            assertFalse(config.has("proxy-providers"))
        }
    }

    @Test fun directCannotWinTheNativeLatencyContestAndCoreTimersDoNotDuplicateAdapter() {
        val config = configuration(listOf(source("one"), source("two")))
        val groups = config.getJSONArray("proxy-groups")
        assertEquals("url-test", groups.getJSONObject(0).getString("type"))
        assertFalse(groups.getJSONObject(0).has("proxies")) // Only enabled provider nodes enter AUTO.
        assertEquals(0, groups.getJSONObject(0).getInt("tolerance"))
        assertEquals(0, groups.getJSONObject(0).getInt("interval"))
        assertFalse(config.getJSONObject("proxy-providers").getJSONObject("ENABLED")
            .getJSONObject("health-check").getBoolean("enable"))
        assertEquals("DIRECT", groups.getJSONObject(1).getJSONArray("proxies").getString(0))
        assertEquals(300, ProxyPolicy.HEALTH_SECONDS)
    }

    @Test fun allUnhealthyAndRecoveryUseActualMatchingUrlHealthRatherThanOptimisticAlive() {
        fun node(alive: Boolean, delay: Int, url: String = ProxyPolicy.TEST_URL) = JSONObject()
            .put("alive", true).put("extra", JSONObject().put(url, JSONObject().put("alive", alive)
                .put("history", JSONArray().put(JSONObject().put("delay", delay)))))
        fun provider(vararg nodes: JSONObject) = JSONObject().put("proxies", JSONArray(nodes.toList()))
        assertFalse(ProxyPolicy.hasHealthyCandidate(provider(node(false, 0), node(false, 0)), ProxyPolicy.TEST_URL))
        assertFalse(ProxyPolicy.hasHealthyCandidate(provider(node(true, 10, "https://different.invalid")), ProxyPolicy.TEST_URL))
        assertFalse(ProxyPolicy.hasHealthyCandidate(provider(node(true, 0)), ProxyPolicy.TEST_URL))
        assertTrue(ProxyPolicy.hasHealthyCandidate(provider(node(false, 0), node(true, 50)), ProxyPolicy.TEST_URL))
    }

    @Test fun importedControlFileAndChainingOptionsCannotEnterTheOwnedPolicy() {
        for (node in listOf(mapOf("type" to "direct"), mapOf("type" to "http", "dialer-proxy" to "DIRECT"),
            mapOf("type" to "trojan", "certificate" to "/private/app/file"))) {
            assertThrows(IllegalArgumentException::class.java) { ProxyPolicy.nodes(listOf(source("one", nodes = listOf(node)))) }
        }
        assertThrows(IllegalArgumentException::class.java) { ProxyPolicy.nodes(listOf(source("one"), source("one"))) }
        val config = configuration(listOf(source("one")))
        assertEquals("rule", config.getString("mode"))
        assertEquals("127.0.0.1", config.getString("bind-address"))
        assertFalse(config.getBoolean("allow-lan"))
        assertEquals("", config.getString("external-controller"))
        assertFalse(config.has("tun"))
        assertFalse(config.getJSONObject("dns").has("listen"))
        val rules = config.getJSONArray("rules").let { array -> (0 until array.length()).map { array.getString(it) } }
        assertTrue(rules.containsAll(listOf("DOMAIN-SUFFIX,ts.net,DIRECT", "IP-CIDR,100.64.0.0/10,DIRECT",
            "IP-CIDR6,fc00::/7,DIRECT", "IP-CIDR6,fe80::/10,DIRECT")))
        assertTrue(rules.indexOf("RULE-SET,CN-DOMAIN,DIRECT") < rules.indexOf("RULE-SET,CN-IP,DIRECT"))
        assertEquals("MATCH,EGRESS", rules.last())
    }
}
