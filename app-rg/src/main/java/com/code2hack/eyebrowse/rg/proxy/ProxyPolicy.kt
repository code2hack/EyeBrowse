package com.code2hack.eyebrowse.rg.proxy

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Node input only. Source URLs, imported rules and controller settings never enter this configuration. */
internal class ProxySource(val id: String, val enabled: Boolean, val nodes: List<Map<String, Any?>>) {
    override fun toString() = "ProxySource(enabled=$enabled, nodes=${nodes.size}, identity/credentials redacted)"
}

internal object ProxyPolicy {
    /** Android JSON escapes '/', which the core's YAML file loader refuses. Preserve other escapes. */
    fun configurationText(json: String): String = buildString(json.length) {
        var at = 0
        while (at < json.length) {
            val current = json[at++]
            if (current == '\\' && at < json.length) {
                val escaped = json[at++]
                if (escaped != '/') append(current)
                append(escaped)
            } else append(current)
        }
    }

    const val HEALTH_SECONDS = 300L
    const val TEST_URL = "https://www.gstatic.com/generate_204"
    const val PROVIDER = "ENABLED"
    const val AUTO = "AUTO"
    const val EGRESS = "EGRESS"
    private val protocols = setOf("http", "socks5", "ss", "ssr", "vmess", "vless", "trojan", "snell",
        "hysteria", "hysteria2", "tuic", "anytls")
    private val unsafeNodeKeys = setOf("dialer-proxy", "interface-name", "routing-mark", "ip-version",
        "ca", "certificate", "private-key", "script", "listeners", "external-controller", "rules", "dns")

    fun nodes(sources: List<ProxySource>): List<JSONObject> {
        require(sources.map { it.id }.distinct().size == sources.size) { "Duplicate source identity" }
        return sources.filter { it.enabled }.flatMap { source ->
            require(source.id.isNotBlank()) { "Missing source identity" }
            val prefix = MessageDigest.getInstance("SHA-256").digest(source.id.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            source.nodes.mapIndexed { index, node ->
                require(node["type"] in protocols) { "Unsupported node type" }
                require(node.keys.none { it in unsafeNodeKeys }) { "Unsupported node routing/file option" }
                // Deep copy at the trust boundary. Original display names can contain secrets or collide.
                val copy = JSONObject(JSONObject(node).toString())
                copy.put("name", "n_${prefix}_$index")
                copy
            }
        }
    }

    fun configuration(nodes: List<JSONObject>, port: Int, controller: String,
        dnsServers: List<String>, testUrl: String = TEST_URL): JSONObject {
        require(port in 1..65535)
        val regional = JSONObject()
            .put("CN-DOMAIN", JSONObject().put("type", "file").put("behavior", "domain")
                .put("format", "mrs").put("path", "cn-domain.mrs"))
            .put("CN-IP", JSONObject().put("type", "file").put("behavior", "ipcidr")
                .put("format", "mrs").put("path", "cn-ip.mrs"))
        val rules = mutableListOf(
            "DOMAIN,localhost,DIRECT", "DOMAIN-SUFFIX,local,DIRECT", "DOMAIN-SUFFIX,lan,DIRECT",
            "DOMAIN-SUFFIX,home.arpa,DIRECT", "DOMAIN-SUFFIX,ts.net,DIRECT",
            "DOMAIN-REGEX,^[^.]+$,DIRECT", "RULE-SET,CN-DOMAIN,DIRECT",
        )
        // China domains and local names are resolved directly. Address rules also cover public names
        // resolving privately; no no-resolve shortcut may send those destinations to remote nodes.
        for (range in listOf("0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
            "169.254.0.0/16", "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4")) {
            rules += "IP-CIDR,$range,DIRECT"
        }
        for (range in listOf("::/128", "::1/128", "fc00::/7", "fe80::/10", "ff00::/8")) {
            rules += "IP-CIDR6,$range,DIRECT"
        }
        rules += "RULE-SET,CN-IP,DIRECT"
        rules += "MATCH,${if (nodes.isEmpty()) "DIRECT" else EGRESS}"
        val config = JSONObject().put("mixed-port", port).put("bind-address", "127.0.0.1")
            .put("allow-lan", false).put("mode", "rule").put("log-level", "silent")
            .put("ipv6", true).put("find-process-mode", "off")
            .put("external-controller", "").put("external-controller-unix", controller)
            .put("profile", JSONObject().put("store-selected", false).put("store-fake-ip", false))
            .put("dns", JSONObject().put("enable", true).put("ipv6", true)
                .put("enhanced-mode", "redir-host").put("nameserver", JSONArray(dnsServers.ifEmpty { listOf("system") })))
            .put("rule-providers", regional).put("rules", JSONArray(rules))
        if (nodes.isNotEmpty()) {
            config.put("proxy-providers", JSONObject().put(PROVIDER, JSONObject().put("type", "inline")
                .put("payload", JSONArray(nodes)).put("health-check", JSONObject()
                    .put("enable", false).put("url", testUrl).put("expected-status", "204")
                    .put("timeout", 5000).put("lazy", false))))
            config.put("proxy-groups", JSONArray().put(JSONObject().put("name", AUTO).put("type", "url-test")
                .put("use", JSONArray(listOf(PROVIDER))).put("url", testUrl).put("interval", 0)
                .put("tolerance", 0).put("expected-status", "204").put("lazy", false).put("empty-fallback", "DIRECT"))
                .put(JSONObject().put("name", EGRESS).put("type", "select")
                    .put("proxies", JSONArray(listOf("DIRECT", AUTO)))))
        }
        return config
    }

    /** Core URLTest owns latency and selection. The adapter only distinguishes all-down from AUTO. */
    fun hasHealthyCandidate(provider: JSONObject, testUrl: String): Boolean {
        val nodes = provider.getJSONArray("proxies")
        return (0 until nodes.length()).any { i ->
            val health = nodes.getJSONObject(i).optJSONObject("extra")?.optJSONObject(testUrl)
            val history = health?.optJSONArray("history")
            health?.optBoolean("alive", false) == true && history != null && history.length() > 0 &&
                history.getJSONObject(history.length() - 1).optInt("delay", 0) > 0
        }
    }

    fun applyHealth(nodeCount: Int, testUrl: String,
        request: (String, String, JSONObject?, Int) -> JSONObject): String {
        val timeout = (7000L + ((nodeCount + 9L) / 10L) * 5000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        request("GET", "/providers/proxies/$PROVIDER/healthcheck", null, timeout)
        val provider = request("GET", "/providers/proxies/$PROVIDER", null, 7000)
        val selected = if (hasHealthyCandidate(provider, testUrl)) AUTO else "DIRECT"
        request("PUT", "/proxies/$EGRESS", JSONObject().put("name", selected), 7000)
        return selected
    }
}
