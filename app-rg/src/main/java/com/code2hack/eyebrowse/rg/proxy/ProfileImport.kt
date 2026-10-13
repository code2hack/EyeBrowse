package com.code2hack.eyebrowse.rg.proxy

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.net.URI
import java.net.URLDecoder

/** Subscription input is data, never an app/core configuration. Errors must not echo its secrets. */
internal object ProfileImport {
    /** Static categories only. Neither exception text nor diagnostic fields contain input values. */
    class Rejected(val category: String, val field: String? = null, val protocol: String? = null) :
        IllegalArgumentException("Profile rejected: $category")
    const val MAX_BYTES = 2 * 1024 * 1024
    private val types = setOf("http", "socks5", "ss", "ssr", "vmess", "vless", "trojan", "snell",
        "hysteria", "hysteria2", "tuic", "anytls")
    private val scalarKeys = setOf("name", "type", "server", "port", "username", "password", "udp", "tls",
        "skip-cert-verify", "sni", "servername", "fingerprint", "client-fingerprint", "uuid", "alterId",
        "cipher", "network", "flow", "encryption", "packet-encoding", "plugin", "obfs", "obfs-password",
        "up", "down", "auth-str", "auth", "recv-window-conn", "recv-window", "disable-mtu-discovery",
        "fast-open", "reduce-rtt", "heartbeat-interval", "congestion-controller", "udp-relay-mode",
        "zero-rtt-handshake", "version", "protocol", "protocol-param", "obfs-param", "psk", "tfo",
        "udp-over-tcp", "ports", "hop-interval")
    private val optionKeys = mapOf(
        "ws-opts" to setOf("path", "headers", "max-early-data", "early-data-header-name"),
        "http-opts" to setOf("method", "path", "headers"),
        "h2-opts" to setOf("host", "path"),
        "grpc-opts" to setOf("grpc-service-name"),
        "reality-opts" to setOf("public-key", "short-id", "support-x25519mlkem768"),
        "plugin-opts" to setOf("mode", "host", "path", "tls", "skip-cert-verify", "mux", "headers"),
        "smux" to setOf("enabled", "protocol", "max-connections", "min-streams", "max-streams",
            "statistic", "only-tcp", "padding", "brutal-opts"),
    )
    private val knownUnsafeFields = setOf("dialer-proxy", "interface-name", "routing-mark", "ip-version",
        "ca", "certificate", "private-key", "script", "listeners", "external-controller", "rules", "dns")

    fun source(payload: String): String {
        require(payload.length <= 16384 && payload.none { it.isWhitespace() || it.isISOControl() })
        val uri = URI(payload)
        if (uri.scheme.equals("clash", true)) {
            require(uri.host == "install-config" && uri.rawPath.isNullOrEmpty() && uri.rawUserInfo == null)
            val urls = uri.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') == "url" }
            require(urls.size == 1)
            return httpSource(URLDecoder.decode(urls.single().substringAfter('=', ""), "UTF-8"))
        }
        return httpSource(payload)
    }

    private fun httpSource(value: String): String {
        require(value.length <= 16384 && value.none { it.isWhitespace() || it.isISOControl() })
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase()
        require(scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null)
        require(uri.port == -1 || uri.port in 1..65535)
        val port = uri.port.takeUnless { it == -1 || it == if (scheme == "https") 443 else 80 }
        // Preserve path escapes and the entire account-bearing query, including its ordering.
        return "$scheme://${uri.host.lowercase()}${port?.let { ":$it" }.orEmpty()}${uri.rawPath.ifEmpty { "/" }}" +
            (uri.rawQuery?.let { "?$it" } ?: "")
    }

    fun nodes(document: String): List<Map<String, Any?>> {
        if (document.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw Rejected("BODY_LIMIT")
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 0
            nestingDepthLimit = 20
            codePointLimit = MAX_BYTES
        }
        val parsed = try { Yaml(SafeConstructor(options)).load<Any?>(document) }
        catch (_: Exception) { throw Rejected("YAML_PARSE_OR_LIMIT") }
        val root = parsed as? Map<*, *> ?: throw Rejected("UNSUPPORTED_ROOT")
        val nodes = root["proxies"] as? List<*> ?: throw Rejected("MISSING_PROXIES")
        if (nodes.size !in 1..2000) throw Rejected("NODE_COUNT")
        return nodes.mapNotNull { raw ->
            val node = raw as? Map<*, *> ?: throw Rejected("NODE_SHAPE")
            if (node["type"] !in types) return@mapNotNull null
            val protocol = node["type"] as String // Whitelisted above.
            val unsupported = node.keys.filterNot { it in scalarKeys || it in optionKeys || it == "alpn" }
            if (unsupported.isNotEmpty()) throw Rejected("NODE_FIELD",
                unsupported.first().takeIf { it in knownUnsafeFields } as? String ?: "OTHER", protocol)
            if ((node["server"] as? String)?.isNotBlank() != true) throw Rejected("NODE_SERVER", "server", protocol)
            if (node["port"] !is Int || node["port"] as Int !in 1..65535) throw Rejected("NODE_PORT", "port", protocol)
            for ((key, value) in node) {
                when (key) {
                    in optionKeys -> {
                        val settings = value as? Map<*, *> ?: throw Rejected("NODE_OPTION_SHAPE", key as String, protocol)
                        if (settings.keys.any { it !in optionKeys.getValue(key as String) })
                            throw Rejected("NODE_OPTION_FIELD", key as String, protocol)
                    }
                    "alpn" -> if (value !is List<*> || value.any { it !is String }) throw Rejected("NODE_ALPN", "alpn", protocol)
                    else -> if (!(value is String || value is Boolean || value is Number || value == null))
                        throw Rejected("NODE_VALUE_TYPE", key as String, protocol)
                }
            }
            @Suppress("UNCHECKED_CAST")
            val clean = try { jsonData(node) as Map<String, Any?> }
            catch (_: Exception) { throw Rejected("NODE_DATA_TYPE_OR_DEPTH", protocol = protocol) }
            clean + ("name" to "node") // Imported labels are unnecessary and may contain credentials.
        }.also { if (it.isEmpty()) throw Rejected("NO_SUPPORTED_NODES") }
    }

    private fun jsonData(value: Any?, depth: Int = 0): Any? {
        require(depth <= 8)
        return when (value) {
            null, is String, is Boolean -> value
            is Number -> value.also { require(it.toDouble().isFinite()) }
            is List<*> -> value.map { jsonData(it, depth + 1) }
            is Map<*, *> -> value.entries.associate { (key, item) ->
                require(key is String)
                key to jsonData(item, depth + 1)
            }
            else -> error("Unsupported YAML value")
        }
    }
}
