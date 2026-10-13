package com.code2hack.eyebrowse.rg.proxy

internal object RefreshPolicy {
    const val SUBSCRIPTION_SECONDS = 1800L
    const val REGIONAL_SECONDS = 86400L

    fun remaining(lastAttempt: Long, now: Long, seconds: Long): Long =
        if (lastAttempt <= 0 || lastAttempt > now) 0 else
            (seconds * 1000 - (now - lastAttempt)).coerceAtLeast(0)
}
