package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test

class LocalTabsTest {
    @Test fun fourSurvivingObjectsKeepTheirHistoryAndBoundariesDoNotWrap() {
        var next = 0
        val closed = mutableListOf<MutableList<Int>>()
        val tabs = LocalTabs({ mutableListOf(next++) }, { closed.add(it) })
        repeat(3) { tabs.add(); tabs.current.add(99) }
        val four = tabs.items
        assertEquals(4, tabs.count); assertEquals(3, tabs.selectedIndex)
        assertFalse(tabs.swipe(1)); assertSame(four[3], tabs.current)
        repeat(3) { assertTrue(tabs.swipe(-1)) }
        assertFalse(tabs.swipe(-1)); assertSame(four[0], tabs.current)
        assertTrue(tabs.swipe(1)); assertSame(four[1], tabs.current)
        assertEquals(listOf(1, 99), tabs.current)
        assertTrue(closed.isEmpty())
    }

    @Test fun closeSelectsNextOrPreviousLastAndSoleCloseReplacesWithoutExit() {
        var next = 0; val closed = mutableListOf<Int>()
        val tabs = LocalTabs({ next++ }, { closed.add(it) })
        repeat(3) { tabs.add() }; tabs.select(1); tabs.closeCurrent()
        assertEquals(2, tabs.current); assertEquals(1, tabs.selectedIndex)
        tabs.select(2); tabs.closeCurrent(); assertEquals(2, tabs.current)
        tabs.closeCurrent(); assertEquals(0, tabs.current)
        tabs.closeCurrent(); assertEquals(4, tabs.current); assertEquals(1, tabs.count)
        assertEquals(listOf(1, 3, 2, 0), closed)
        tabs.destroy(); assertEquals(listOf(1, 3, 2, 0, 4), closed)
    }

    @Test fun failedAllocationDoesNotCloseOrReplaceAnExistingTab() {
        var rejectAllocation = false; val original = Any(); var closed = 0
        val tabs = LocalTabs({ if (rejectAllocation) throw IllegalStateException("allocation failed") else original }, { closed++ })
        rejectAllocation = true
        for (operation in listOf({ tabs.add() }, { tabs.closeCurrent() })) {
            try { operation(); fail("expected allocation failure") } catch (_: IllegalStateException) { }
            assertSame(original, tabs.current); assertEquals(1, tabs.count); assertEquals(0, closed)
        }
    }
}
