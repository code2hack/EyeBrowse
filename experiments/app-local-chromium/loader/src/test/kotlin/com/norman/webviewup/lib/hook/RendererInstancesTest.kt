package com.norman.webviewup.lib.hook

import org.junit.Assert.*
import org.junit.Test

class RendererInstancesTest {
    private fun key(instance: String?, component: String = "engine/Service0") = RendererKey(component, instance)
    private fun pool(capacity: Int = 5) = RendererInstances<String, String>(capacity)

    @Test fun unrelatedPackagesAndServicesNeverEnterTheRendererPool() {
        assertTrue(isRendererService("engine", "engine", "org.chromium.content.app.SandboxedProcessService0", null))
        assertFalse(isRendererService("engine", "other", "org.chromium.content.app.SandboxedProcessService0", null))
        assertFalse(isRendererService("engine", "engine", "org.chromium.OtherService", null))
        assertFalse(isRendererService("engine", "engine", "org.chromium.NotSandboxedProcessService0", null))
        assertTrue(isRendererService("engine", "engine", "vendor.Renderer", java.util.regex.Pattern.compile("^vendor.Renderer$")))
    }

    @Test fun sameComponentDifferentNamesAndNullAreDistinctLiveInstances() {
        val pool = pool()
        val leases = listOf(null, "0", "1", "99999").mapIndexed { index, name ->
            pool.acquire(key(name), "connection$index")!!.also(pool::accepted).lease
        }
        assertEquals(4, leases.map { it.slot }.toSet().size)
    }

    @Test fun sameNameOnDifferentComponentsDoesNotAlias() {
        val pool = pool()
        assertNotEquals(pool.acquire(key("0"), "a")!!.lease.slot,
            pool.acquire(key("0", "engine/Service1"), "b")!!.lease.slot)
    }

    @Test fun repeatedAndAdditionalBindingsKeepTheirInstanceAndOneUnbindRemovesThatConnection() {
        val pool = pool(1)
        val first = pool.acquire(key("0"), "a")!!.also(pool::accepted)
        assertEquals(first.lease, pool.acquire(key("0"), "a")!!.lease)
        val second = pool.acquire(key("0"), "b")!!.also(pool::accepted)
        assertEquals(first.lease, second.lease)
        assertTrue(pool.started(first.lease, "pid1"))
        pool.died(first.lease, "pid1")
        pool.unbound("a")
        assertNull(pool.acquire(key("1"), "c"))
        pool.unbound("b")
        assertNotNull(pool.acquire(key("1"), "c"))
    }

    @Test fun unbindAloneCannotReuseALiveChild() {
        val pool = pool(1)
        val first = pool.acquire(key("0"), "a")!!.also(pool::accepted)
        pool.started(first.lease, "pid1")
        pool.unbound("a")
        assertNull(pool.acquire(key("1"), "b"))
        pool.died(first.lease, "pid1")
        assertNotNull(pool.acquire(key("1"), "b"))
    }

    @Test fun processDeathAloneCannotReuseAnInstanceWithLiveBindings() {
        val pool = pool(1)
        val first = pool.acquire(key("0"), "a")!!.also(pool::accepted)
        pool.started(first.lease, "pid1")
        pool.died(first.lease, "pid1")
        assertNull(pool.acquire(key("1"), "b"))
        assertEquals(first.lease, pool.acquire(key("0"), "b")!!.lease)
        assertTrue(pool.started(first.lease, "pid2"))
        pool.unbound("a"); pool.unbound("b")
        pool.died(first.lease, "pid1") // Delayed old-process event cannot release the replacement.
        assertNull(pool.acquire(key("1"), "c"))
        pool.died(first.lease, "pid2")
        assertNotNull(pool.acquire(key("1"), "c"))
    }

    @Test fun exhaustedPoolDoesNotCollideOrDropExistingBindings() {
        val pool = pool()
        val live = (0..4).map { pool.acquire(key(it.toString()), "c$it")!!.also(pool::accepted).lease }
        assertNull(pool.acquire(key("5"), "c5"))
        assertEquals(live, pool.leases())
        live.forEachIndexed { index, lease -> assertEquals(lease, pool.acquire(lease.key, "c$index")!!.lease) }
    }

    @Test fun acceptedButUnobservedChildStaysReserved() {
        val pool = pool(1)
        pool.acquire(key("0"), "a")!!.also(pool::accepted)
        pool.unbound("a")
        assertNull(pool.acquire(key("1"), "b"))
    }

    @Test fun definiteBindRejectionReleasesOnlyANewUnusedLease() {
        val pool = pool(1)
        val rejected = pool.acquire(key("0"), "a")!!
        pool.rejected(rejected)
        assertNotNull(pool.acquire(key("1"), "b"))
    }

    @Test fun failedRepeatCannotRemoveAnExistingConnection() {
        val pool = pool(1)
        val first = pool.acquire(key("0"), "a")!!.also(pool::accepted)
        pool.rejected(pool.acquire(key("0"), "a")!!)
        pool.started(first.lease, "pid1"); pool.died(first.lease, "pid1")
        assertNull(pool.acquire(key("1"), "b"))
        pool.unbound("a")
        assertNotNull(pool.acquire(key("1"), "b"))
    }

    @Test fun unrelatedUnbindAndStaleProcessCallbacksDoNotReleaseNewOccupants() {
        val pool = pool(1)
        val old = pool.acquire(key("0"), "a")!!.also(pool::accepted).lease
        pool.started(old, "pid1"); pool.unbound("a"); pool.died(old, "pid1")
        val next = pool.acquire(key("1"), "b")!!.also(pool::accepted).lease
        pool.unbound("unrelated"); pool.died(old, "pid1")
        assertFalse(pool.started(old, "pid1"))
        assertEquals(listOf(next), pool.leases())
    }

    @Test fun aSecondLiveProcessCannotClaimAnOccupiedLease() {
        val pool = pool()
        val first = pool.acquire(key("0"), "a")!!.also(pool::accepted).lease
        assertTrue(pool.started(first, "pid1"))
        assertFalse(pool.started(first, "pid2"))
    }
}
