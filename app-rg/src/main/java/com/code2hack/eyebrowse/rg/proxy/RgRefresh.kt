package com.code2hack.eyebrowse.rg.proxy

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.util.concurrent.CompletableFuture

/** All wake-ups enter one due check. No timer keeps a process/core alive in the background. */
internal class RgRefresh private constructor(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val preferences = context.getSharedPreferences("proxy-refresh", Context.MODE_PRIVATE)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val scheduler = context.getSystemService(JobScheduler::class.java)
    private var users = 0
    private var running: CompletableFuture<Unit>? = null
    internal var clock: () -> Long = System::currentTimeMillis // Replaced only by in-process tests.
    internal var ruleUrls: Map<String, String> = RegionalRules.sources
    internal var inspectJob: ((Int) -> Unit)? = null // Opt-in instrumentation; unset in ordinary use.
    internal var testConnectivity: (() -> Boolean)? = null
    private val wake = Runnable { kick() }
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) main.post { kick() }
        }
    }

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (users++ == 0) connectivity.registerDefaultNetworkCallback(network)
        scheduleJobs()
        kick()
    }

    fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(users > 0)
        if (--users == 0) {
            main.removeCallbacks(wake)
            connectivity.unregisterNetworkCallback(network)
        }
    }

    internal fun online(): Boolean = testConnectivity?.invoke() ?: (connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)

    internal fun idle(): CompletableFuture<Unit> = running ?: CompletableFuture.completedFuture(Unit)

    private fun remaining(key: String, seconds: Long) =
        RefreshPolicy.remaining(preferences.getLong(key, 0), clock(), seconds)

    internal fun scheduleJobs(replace: Int? = null) {
        for ((id, key, seconds) in policies) {
            val last = preferences.getLong(key, 0)
            val due = if (last <= 0 || last > clock()) 0 else last + seconds * 1000
            val old = scheduler.getPendingJob(id)
            if (id != replace && old?.extras?.getLong("due", -1) == due) continue
            val extras = PersistableBundle().apply { putLong("due", due); putLong("intervalSeconds", seconds) }
            if (scheduler.schedule(JobInfo.Builder(id, ComponentName(context, RgRefreshJob::class.java))
                .setMinimumLatency(remaining(key, seconds)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true).setExtras(extras).build()) != JobScheduler.RESULT_SUCCESS)
                android.util.Log.w("EyeBrowse", "Background update scheduling unavailable")
        }
    }

    private fun kick() {
        runDue().whenComplete { _, failure -> main.post { if (failure == null) { scheduleJobs(); scheduleActive() } } }
    }

    private fun scheduleActive() {
        main.removeCallbacks(wake)
        if (users > 0 && online()) {
            val delay = policies.minOf { (_, key, seconds) -> remaining(key, seconds) }
            main.postDelayed(wake, delay.coerceAtLeast(1000)) // Only a due wake, never an app polling loop.
        }
    }

    internal fun runDue(): CompletableFuture<Unit> {
        check(Looper.myLooper() == Looper.getMainLooper())
        running?.let { return it }
        if (!online()) return CompletableFuture.completedFuture(Unit) // Keep overdue state for usable connectivity.
        val now = clock()
        val result = CompletableFuture<Unit>()
        running = result
        val due = policies.filter { (_, key, seconds) -> remaining(key, seconds) == 0L }
        var work = CompletableFuture.completedFuture(Unit)
        for ((id, key, _) in due) {
            work = work.thenCompose {
                val operation: CompletableFuture<*> = if (id == SUBSCRIPTION_JOB)
                    RgProfiles.get(context).refreshSaved() else RgProxyRuntime.get(context).refreshRegional(ruleUrls)
                operation.handle { _, _ ->
                    // Failed attempts keep last-good data and retry next cadence, without a tight failure loop.
                    check(preferences.edit().putLong(key, now).commit())
                    Unit
                }
            }
        }
        work.whenComplete { _, failure -> main.post {
            running = null
            if (failure == null) result.complete(Unit) else result.completeExceptionally(failure)
            if (failure == null) scheduleActive()
        } }
        return result
    }

    companion object {
        const val SUBSCRIPTION_JOB = 4501
        const val REGIONAL_JOB = 4502
        internal val policies = listOf(
            Triple(SUBSCRIPTION_JOB, "subscriptions", RefreshPolicy.SUBSCRIPTION_SECONDS),
            Triple(REGIONAL_JOB, "regional", RefreshPolicy.REGIONAL_SECONDS),
        )
        @SuppressLint("StaticFieldLeak") // Application context only.
        private var instance: RgRefresh? = null
        @Synchronized fun get(context: Context): RgRefresh = instance ?: RgRefresh(context.applicationContext).also { instance = it }
    }
}

class RgRefreshJob : JobService() {
    private val main = Handler(Looper.getMainLooper())
    override fun onStartJob(params: JobParameters): Boolean {
        val owner = RgRefresh.get(this)
        owner.inspectJob?.invoke(params.jobId)
        if (!owner.online()) { main.post { jobFinished(params, true) }; return true }
        owner.runDue().whenComplete { _, failure -> main.post {
            jobFinished(params, failure != null)
            if (failure == null) owner.scheduleJobs(params.jobId)
        } }
        return true
    }
    // Finite HTTP/core work may finish after Android stops a job; the same owner still coalesces
    // subsequent wake-ups, and a completed source cannot be duplicated/resurrected by a second loop.
    override fun onStopJob(params: JobParameters) = true
}
