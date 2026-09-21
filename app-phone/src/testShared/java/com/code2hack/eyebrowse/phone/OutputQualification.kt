package com.code2hack.eyebrowse.phone

/**
 * Test-only temporal qualification for raw capture callbacks (Owner Option A).
 *
 * The FIRST VALID current-document/current-geometry callback must reach the consumer within
 * [VALIDITY_BOUND_MS] of the ORIGINAL eligibility instant. That clock never restarts at the first
 * raw callback, renewal or readiness wait. Non-qualifying initialization callbacks are retained,
 * never hidden; perpetual initialization fails. Qualification is not permission for later stale or
 * wrong output.
 *
 * Each [Observation] copies its pixel sample inside the callback. The borrowed bitmap is never
 * retained or reread, so later buffer reuse cannot retroactively qualify an earlier callback.
 * Shared by JVM and instrumentation tests only; never included in the app APK.
 */
internal object OutputQualification {
    const val VALIDITY_BOUND_MS = 2_000L
    const val CHANNEL_TOLERANCE = 8

    /** Immutable facts and predicates computed at callback time. */
    class Observation(
        val deliveryUptimeMs: Long,
        val deliveryElapsedMs: Long,
        val width: Int,
        val height: Int,
        val generation: Int,
        val sequence: Long,
        val contentHash: Long,
        val sampledColor: Int,
        val expectedWidth: Int,
        val expectedHeight: Int,
        val expectedColor: Int,
        val geometryMatches: Boolean,
        val contentMatches: Boolean,
        val qualified: Boolean,
    ) {
        fun delayFrom(eligibleUptimeMs: Long): Long = deliveryUptimeMs - eligibleUptimeMs
    }

    /** Zero expected dimensions mean not evaluated and cannot qualify. */
    fun observe(
        deliveryUptimeMs: Long,
        deliveryElapsedMs: Long,
        width: Int,
        height: Int,
        generation: Int,
        sequence: Long,
        contentHash: Long,
        sampledColor: Int,
        expectedWidth: Int,
        expectedHeight: Int,
        expectedColor: Int,
    ): Observation {
        val geometryMatches =
            expectedWidth > 0 &&
                expectedHeight > 0 &&
                width == expectedWidth &&
                height == expectedHeight
        val contentMatches = nearColor(sampledColor, expectedColor)
        return Observation(
            deliveryUptimeMs,
            deliveryElapsedMs,
            width,
            height,
            generation,
            sequence,
            contentHash,
            sampledColor,
            expectedWidth,
            expectedHeight,
            expectedColor,
            geometryMatches,
            contentMatches,
            geometryMatches && contentMatches,
        )
    }

    fun nearColor(actual: Int, expected: Int): Boolean =
        kotlin.math.abs(red(actual) - red(expected)) <= CHANNEL_TOLERANCE &&
            kotlin.math.abs(green(actual) - green(expected)) <= CHANNEL_TOLERANCE &&
            kotlin.math.abs(blue(actual) - blue(expected)) <= CHANNEL_TOLERANCE

    fun validWithinBound(deliveryDelayMs: Long): Boolean =
        deliveryDelayMs >= 0 && deliveryDelayMs <= VALIDITY_BOUND_MS

    fun earliestQualifyingIndex(observations: List<Observation>, fromIndex: Int): Int {
        for (i in maxOf(0, fromIndex) until observations.size) {
            if (observations[i].qualified) return i
        }
        return -1
    }

    fun earliestQualifyingDelayMs(
        observations: List<Observation>,
        fromIndex: Int,
        eligibleUptimeMs: Long,
    ): Long {
        val index = earliestQualifyingIndex(observations, fromIndex)
        return if (index < 0) -1L else observations[index].delayFrom(eligibleUptimeMs)
    }

    fun nonQualifyingBefore(observations: List<Observation>, fromIndex: Int, index: Int): Int {
        var count = 0
        for (i in maxOf(0, fromIndex) until index) {
            if (!observations[i].qualified) count++
        }
        return count
    }

    /** Sparse immutable geometry/sample/verdict evidence; caps only the displayed facts at ten. */
    fun summary(observations: List<Observation>, fromIndex: Int, eligibleUptimeMs: Long): String {
        val from = maxOf(0, fromIndex)
        val firstValid = earliestQualifyingIndex(observations, from)
        val text =
            StringBuilder("observed=")
                .append(observations.size - from)
                .append(" fromIndex=")
                .append(from)
                .append(" firstValid=")
                .append(firstValid)
        if (firstValid >= 0) {
            text
                .append(" nonQualifyingBefore=")
                .append(nonQualifyingBefore(observations, from, firstValid))
            if (eligibleUptimeMs > 0) {
                text
                    .append(" firstValidDelay=")
                    .append(observations[firstValid].delayFrom(eligibleUptimeMs))
                    .append("ms")
            }
        } else {
            text
                .append(" nonQualifyingBefore=none-valid totalNonQualifying=")
                .append(observations.size - from)
        }
        text.append(" facts=[")
        var shown = 0
        var i = from
        while (i < observations.size && shown < 10) {
            if (i > from) text.append("; ")
            val observation = observations[i]
            text
                .append(i)
                .append(':')
                .append(observation.width)
                .append('x')
                .append(observation.height)
                .append(" expected=")
                .append(observation.expectedWidth)
                .append('x')
                .append(observation.expectedHeight)
                .append(" #")
                .append(java.lang.Long.toHexString(observation.contentHash))
                .append(" sample=#")
                .append(String.format("%08x", observation.sampledColor))
                .append(" expectedSample=#")
                .append(String.format("%08x", observation.expectedColor))
                .append(" gm=")
                .append(if (observation.geometryMatches) "Y" else "N")
                .append(" cm=")
                .append(if (observation.contentMatches) "Y" else "N")
                .append(" q=")
                .append(if (observation.qualified) "Y" else "N")
            i++
            shown++
        }
        if (observations.size - from > shown) {
            text.append("; +").append(observations.size - from - shown).append(" more")
        }
        return text.append(']').toString()
    }

    private fun red(color: Int): Int = (color shr 16) and 0xff

    private fun green(color: Int): Int = (color shr 8) and 0xff

    private fun blue(color: Int): Int = color and 0xff
}
