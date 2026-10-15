package com.code2hack.eyebrowse.rg

import android.content.Context
import android.content.SharedPreferences

/** Activity.getPreferences uses getLocalClassName, which changes with an applicationId suffix. */
internal fun localBrowserTestPreferences(context: Context): SharedPreferences =
    context.getSharedPreferences(
        LocalBrowserActivity::class.java.name.removePrefix(context.packageName + "."),
        Context.MODE_PRIVATE,
    )
