package com.norman.webviewup.lib.hook

/** Android names an instance by both component and instanceName; null is also a distinct name. */
data class RendererKey(val component: String, val instance: String?)

internal fun isRendererService(targetPackage: String, packageName: String, className: String,
    pattern: java.util.regex.Pattern?): Boolean = packageName == targetPackage &&
    (pattern?.matcher(className)?.find()
        ?: Regex("(?:^|\\.)SandboxedProcessService\\d+$").containsMatchIn(className))

/** A slot stays reserved until all connections are unbound AND its child process has died. */
class RendererInstances<C : Any, P : Any>(private val capacity: Int) {
    data class Lease(val key: RendererKey, val slot: Int, val serial: Long)
    data class Binding<C>(val lease: Lease, val connection: C, val added: Boolean)
    private class Entry<C, P>(val lease: Lease) {
        val connections = mutableSetOf<C>()
        var accepted = false
        var process: P? = null
        var dead = false
    }
    private val entries = mutableListOf<Entry<C, P>>()
    private var serial = 0L

    init { require(capacity > 0) }

    @Synchronized fun acquire(key: RendererKey, connection: C): Binding<C>? {
        val entry = entries.firstOrNull { it.lease.key == key } ?: run {
            val slot = (0 until capacity).firstOrNull { slot -> entries.none { it.lease.slot == slot } }
                ?: return null
            Entry<C, P>(Lease(key, slot, ++serial)).also { entries += it }
        }
        return Binding(entry.lease, connection, entry.connections.add(connection))
    }

    @Synchronized fun accepted(binding: Binding<C>) {
        entry(binding.lease)?.accepted = true
    }

    /** Only an explicit negative AMS result proves that this new binding never took effect. */
    @Synchronized fun rejected(binding: Binding<C>) {
        val entry = entry(binding.lease) ?: return
        if (binding.added) entry.connections.remove(binding.connection)
        if (entry.connections.isEmpty() && !entry.accepted && entry.process == null) entries.remove(entry)
        else collect(entry)
    }

    @Synchronized fun unbound(connection: C) {
        entries.toList().forEach { entry -> entry.connections.remove(connection); collect(entry) }
    }

    @Synchronized fun started(lease: Lease, process: P): Boolean {
        val entry = entry(lease) ?: return false
        if (entry.process != null && !entry.dead && entry.process != process) return false
        entry.process = process
        entry.dead = false
        return true
    }

    @Synchronized fun died(lease: Lease, process: P) {
        val entry = entry(lease) ?: return
        if (entry.process != process) return
        entry.dead = true
        collect(entry)
    }

    @Synchronized fun leases(): List<Lease> = entries.map { it.lease }

    private fun entry(lease: Lease) = entries.firstOrNull { it.lease == lease }
    private fun collect(entry: Entry<C, P>) {
        if (entry.connections.isEmpty() && entry.dead) entries.remove(entry)
    }
}
