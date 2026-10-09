package com.code2hack.eyebrowse.rg

/** Ordered live browser objects, not input targets or delayed actions. No allocation cap or wrap. */
internal class LocalTabs<T>(private val create: () -> T, private val close: (T) -> Unit) {
    private val opened = mutableListOf(create())
    val items: List<T> get() = opened.toList()
    var selectedIndex = 0
        private set
    val current: T get() = opened[selectedIndex]
    val count: Int get() = opened.size

    fun add() {
        val new = create() // Allocation failure must leave all existing tabs and selection intact.
        opened.add(new)
        selectedIndex = opened.lastIndex
    }

    fun select(index: Int): Boolean {
        if (index !in opened.indices || index == selectedIndex) return false
        selectedIndex = index
        return true
    }

    fun swipe(direction: Int) = select(selectedIndex + direction.compareTo(0))

    fun closeCurrent() {
        if (opened.size == 1) {
            val replacement = create()
            val old = opened[0]
            opened[0] = replacement
            close(old)
        } else {
            val old = opened.removeAt(selectedIndex)
            selectedIndex = selectedIndex.coerceAtMost(opened.lastIndex)
            close(old)
        }
    }

    fun destroy() { opened.forEach(close); opened.clear() }
}
