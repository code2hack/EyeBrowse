package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.messages.BrowserActionMessage

/**
 * One process owner, one prepared number, one serial IO job. No gesture or action is queued here.
 * [lock] protects memory only. read/persist, executor submission and notifications run outside it.
 * Controller -> this short memory lock is permitted; IO never takes a controller/link lock.
 */
internal class CommandSequence(
    private val read: () -> Cursor?,
    private val persist: (Cursor) -> Boolean,
    private val execute: (() -> Unit) -> Unit,
    private val now: () -> Long,
    private val trace: (String) -> Unit = {},
) {
    data class Namespace(val lifetime: String, val epoch: Long) {
        init { require(lifetime.isNotBlank() && epoch>=0) }
        override fun toString() = lifetime+":"+epoch
        companion object { fun of(context: ControlContext) = Namespace(context.lifetimeId,context.controlEpoch) }
    }
    data class Cursor(val lifetime: String, val epoch: Long, val sequence: Long) {
        val namespace get() = Namespace(lifetime,epoch)
        fun message(context: ControlContext, action: BrowserAction): BrowserActionMessage {
            require(namespace==Namespace.of(context))
            return BrowserActionMessage(BrowserCommandId.create(context,sequence),context,action,sequence)
        }
    }
    enum class Phase { IDLE, PREPARING, READY, FAILED, RETIRED }
    data class Snapshot(val phase: Phase, val revision: Long, val namespace: Namespace?,
        val ordinal: Long?, val demandAt: Long?, val deadline: Long?, val failure: String?, val readyAt: Long?=null)
    private data class Demand(val owner: Long, val generation: Long, val namespace: Namespace, val at: Long)
    private data class Work(val demand: Demand)
    private val lock = Any()
    private var owner = 0L
    private var generation = 0L
    private var revision = 0L
    private var notify: (() -> Unit)? = null
    private var demand: Demand? = null
    private var ready: Cursor? = null
    private var readyAt: Long? = null
    private var active: Work? = null
    private var failure: String? = null
    private var consumed = 0L
    // Only the single IO job touches these floors. Uncertain attempts are burned, too.
    private var knownFloor: Cursor? = null
    private var attemptedFloor: Cursor? = null

    fun claim(changed: () -> Unit): Long {
        val previous: (() -> Unit)?
        val token: Long
        synchronized(lock) {
            previous=notify;owner++;token=owner;notify=changed
            invalidateLocked()
        }
        previous?.invoke()
        return token
    }
    fun release(token: Long) {
        synchronized(lock) {
            if(token!=owner) return
            invalidateLocked();notify=null;owner++
        }
    }
    fun cancel(token: Long) {
        val changed=synchronized(lock) {
            if(token!=owner) return
            if(demand==null && ready==null && failure==null) return
            invalidateLocked();notify
        }
        changed?.invoke()
    }
    private fun invalidateLocked() {
        generation++;revision++;demand=null;ready=null;readyAt=null;failure=null
        // active remains owned until the actual IO job returns; cancellation cannot stop commit().
    }

    fun demand(token: Long, namespace: Namespace) {
        val work: Work?
        val changed: (() -> Unit)?
        val started: Demand
        synchronized(lock) {
            if(token!=owner) return
            if(demand?.namespace==namespace) return // Full/preparing/failed: never renew the guard.
            generation++;revision++;ready=null;readyAt=null;failure=null
            started=Demand(owner,generation,namespace,now());demand=started
            work=startLocked();changed=notify
        }
        trace("PREPARE_DEMAND owner="+token+" generation="+started.generation+" namespace="+namespace+" at="+started.at)
        changed?.invoke();submit(work)
    }

    fun snapshot(token: Long): Snapshot {
        var changed: (() -> Unit)?=null
        val result=synchronized(lock) {
            if(token!=owner) return@synchronized Snapshot(Phase.RETIRED,revision,null,null,null,null,null)
            if(expireLocked()) changed=notify
            snapshotLocked()
        }
        changed?.invoke()
        return result
    }
    fun consumedCount(): Long = synchronized(lock) { consumed }

    /** Called only after the original expected-context check. The number is burned even on queue failure. */
    fun consume(token: Long, namespace: Namespace, expectedRevision: Long): Cursor? {
        val cursor: Cursor
        val changed: (() -> Unit)?
        synchronized(lock) {
            if(token!=owner || revision!=expectedRevision || failure!=null || demand?.namespace!=namespace) return null
            cursor=ready?.takeIf { it.namespace==namespace } ?: return null
            ready=null;readyAt=null;demand=null;generation++;revision++;consumed++
            changed=notify
        }
        trace("ORDINAL_CONSUME owner="+token+" namespace="+namespace+" ordinal="+cursor.sequence+" at="+now())
        changed?.invoke()
        return cursor
    }

    private fun snapshotLocked(): Snapshot {
        val current=demand
        val phase=when { failure!=null -> Phase.FAILED;ready!=null -> Phase.READY;current!=null -> Phase.PREPARING;else -> Phase.IDLE }
        return Snapshot(phase,revision,current?.namespace,ready?.sequence,current?.at,
            current?.let { it.at+PREPARATION_GUARD_MS },failure,readyAt)
    }
    private fun expireLocked(): Boolean {
        val current=demand ?: return false
        if(ready!=null || failure!=null || now()-current.at<PREPARATION_GUARD_MS) return false
        failure="Preparation timed out";revision++
        return true
    }
    private fun currentLocked(work: Work) = active===work && demand===work.demand &&
        owner==work.demand.owner && failure==null
    private fun startLocked(): Work? {
        expireLocked()
        if(active!=null || ready!=null || failure!=null) return null
        return demand?.let { Work(it).also { job -> active=job } }
    }
    private fun submit(work: Work?) {
        if(work==null) return
        try { execute { prepare(work) } }
        catch (_: Exception) { finish(work,null,"Preparation worker unavailable") }
    }
    private fun valid(work: Work): Boolean = synchronized(lock) {
        expireLocked()
        currentLocked(work)
    }
    private fun prepare(work: Work) {
        val requested=work.demand
        var result: Cursor?=null
        var problem: String?=null
        try {
            if(!valid(work)) return
            trace("PREPARE_START owner="+requested.owner+" generation="+requested.generation+
                " namespace="+requested.namespace+" at="+now()+" thread="+Thread.currentThread().name)
            val stored=read()
            require(stored==null || stored.lifetime.isNotBlank() && stored.epoch>=0 && stored.sequence>0) { "Corrupt cursor" }
            val confirmed=knownFloor
            require(stored!=null || confirmed==null && attemptedFloor==null) { "Missing expected cursor" }
            if(confirmed!=null && stored!=null) {
                require(stored.namespace!=confirmed.namespace || stored.sequence>=confirmed.sequence) { "Cursor rolled back" }
            }
            if(confirmed!=null && stored!=null && stored.namespace!=confirmed.namespace) {
                require(stored.namespace==attemptedFloor?.namespace) { "Unexpected cursor namespace" }
            }
            if(stored!=null) knownFloor=stored // Preserve every validated existing floor, including cancelled/exhausted jobs.
            val highestEpoch=listOfNotNull(stored,confirmed,attemptedFloor)
                .filter { it.lifetime==requested.namespace.lifetime }.maxOfOrNull { it.epoch }
            require(highestEpoch==null || requested.namespace.epoch>=highestEpoch) { "Obsolete epoch" }
            val floor=maxOf(stored?.takeIf { it.namespace==requested.namespace }?.sequence ?: 0,
                attemptedFloor?.takeIf { it.namespace==requested.namespace }?.sequence ?: 0)
            require(floor<Long.MAX_VALUE) { "Ordinal exhausted" }
            val next=Cursor(requested.namespace.lifetime,requested.namespace.epoch,floor+1)
            if(!valid(work)) return
            attemptedFloor=next
            trace("PERSIST_START owner="+requested.owner+" namespace="+next.namespace+" ordinal="+next.sequence+" at="+now())
            val success=persist(next)
            trace("PERSIST_END owner="+requested.owner+" namespace="+next.namespace+" ordinal="+next.sequence+
                " at="+now()+" success="+success)
            if(success) { knownFloor=next;result=next } else problem="Persistence not confirmed"
        } catch (_: Exception) { problem="Cursor storage unavailable or invalid" }
        finally { finish(work,result,problem) }
    }
    private fun finish(work: Work, result: Cursor?, problem: String?) {
        var published=false
        val next: Work?
        val changed: (() -> Unit)?
        synchronized(lock) {
            if(active!==work) return
            expireLocked()
            if(currentLocked(work)) {
                if(result!=null) { ready=result;readyAt=now();published=true } else failure=problem ?: "Preparation cancelled"
                revision++
            }
            active=null
            next=startLocked();changed=notify
        }
        trace("PREPARE_FINISH owner="+work.demand.owner+" generation="+work.demand.generation+
            " namespace="+work.demand.namespace+" ordinal="+result?.sequence+" at="+now()+" published="+published+
            " problem="+problem)
        changed?.invoke();submit(next)
    }
    companion object { const val PREPARATION_GUARD_MS=4_000L }
}
