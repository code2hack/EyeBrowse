package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.Window

/** Temporary field-free observer. Only the debug variant and explicit launch extra enable it. */
class PadDispatchDiagnosticApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
                val browser = enabled(activity) ?: return
                browser.router.diagnostic = ::record
                val original = browser.window.callback
                browser.window.callback = object : Window.Callback by original {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        if (event.device?.name != "ROKID,PSOC-TP-R") return original.dispatchKeyEvent(event)
                        val receipt = SystemClock.uptimeMillis()
                        val before = state(browser)
                        // Delegate exactly once, unchanged. Write the event only after normal dispatch.
                        val handled = original.dispatchKeyEvent(event)
                        record("window-key device=${event.deviceId} source=${event.source} scan=${event.scanCode} " +
                            "key=${event.keyCode} action=${event.action} repeat=${event.repeatCount} " +
                            "meta=${event.metaState} canceled=${event.isCanceled} long=${event.isLongPress} " +
                            "event=${event.eventTime} down=${event.downTime} receipt=$receipt handled=$handled " +
                            "before=[$before] after=[${state(browser)}]")
                        return handled
                    }
                    override fun onWindowFocusChanged(hasFocus: Boolean) {
                        original.onWindowFocusChanged(hasFocus)
                        record("window-focus value=$hasFocus ${state(browser)}")
                    }
                }
                record("enabled ${state(browser)}")
            }
            override fun onActivityPostResumed(activity: Activity) {
                enabled(activity)?.let { record("resumed ${state(it)}") }
            }
            override fun onActivityPostPaused(activity: Activity) {
                enabled(activity)?.let { record("paused ${state(it)}") }
            }
            override fun onActivityDestroyed(activity: Activity) {
                enabled(activity)?.let { it.router.diagnostic = null; record("destroyed") }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }

    private fun enabled(activity: Activity) = (activity as? LocalBrowserActivity)?.takeIf {
        it.intent.getBooleanExtra("i30.padDispatchProbe", false)
    }
    private fun state(browser: LocalBrowserActivity) = "${browser.router.diagnosticState()} " +
        "utility=${browser.utility} addressFocus=${browser.address.hasFocus()} keyboard=${browser.keyboard.isShown} " +
        "counter=${browser.tabs.selectedIndex + 1}/${browser.tabs.count}"
    private fun record(message: String) { Log.i("I30PadDispatch", "uptime=${SystemClock.uptimeMillis()} $message") }
}
