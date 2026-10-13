package com.code2hack.eyebrowse.rg.proxy

import org.json.JSONArray
import org.json.JSONObject

internal class SavedProfile(val source: String, val name: String, val enabled: Boolean,
    val nodes: List<Map<String, Any?>>) {
    fun asSource() = ProxySource(source, enabled, nodes)
    fun enabled(value: Boolean) = SavedProfile(source, name, value, nodes)
    override fun toString() = "SavedProfile(enabled=$enabled, name/credentials redacted)"
}

/** A single atomic preference value keeps names, identities, toggles and last-good nodes together. */
internal class ProfileStore(private val read: () -> String?, private val write: (String) -> Boolean) {
    var profiles = emptyList<SavedProfile>()
        private set
    var readable = true
        private set
    private var nextNumber = 1

    init { reload() }
    fun reload() {
        try {
            var loaded = emptyList<SavedProfile>()
            var number = 1
            read()?.let { text ->
                require(text.toByteArray().size <= 4 * ProfileImport.MAX_BYTES)
                val root = JSONObject(text)
                number = root.getInt("nextNumber").also { require(it > 0) }
                val array = root.getJSONArray("profiles")
                loaded = (0 until array.length()).map { index ->
                    val profile = array.getJSONObject(index)
                    val source = profile.getString("source")
                    require(ProfileImport.source(source) == source)
                    val name = profile.getString("name")
                    require(name.isNotBlank() && name.length <= 128 && name.none { it.isISOControl() } && "://" !in name)
                    val nodes = ProfileImport.nodes(JSONObject().put("proxies", profile.getJSONArray("nodes")).toString())
                    SavedProfile(source, name, profile.getBoolean("enabled"), nodes)
                }
                require(loaded.map { it.source }.distinct().size == loaded.size)
            }
            profiles = loaded; nextNumber = number; readable = true
        } catch (_: Exception) { readable = false }
    }

    fun imported(source: String, nodes: List<Map<String, Any?>>): List<SavedProfile> {
        check(readable)
        val old = profiles.find { it.source == source }
        val next = SavedProfile(source, old?.name ?: "Profile $nextNumber", old?.enabled ?: true, nodes)
        return if (old == null) profiles + next else profiles.map { if (it.source == source) next else it }
    }

    fun save(next: List<SavedProfile>): Boolean {
        if (!readable) return false // Never replace an unreadable existing store with an empty success.
        val number = if (next.any { candidate -> profiles.none { it.source == candidate.source } }) nextNumber + 1 else nextNumber
        val text = JSONObject().put("nextNumber", number).put("profiles", JSONArray(next.map {
            JSONObject().put("source", it.source).put("name", it.name).put("enabled", it.enabled)
                .put("nodes", JSONArray(it.nodes))
        })).toString()
        if (text.toByteArray().size > 4 * ProfileImport.MAX_BYTES || !write(text)) return false
        profiles = next; nextNumber = number
        return true
    }
}
