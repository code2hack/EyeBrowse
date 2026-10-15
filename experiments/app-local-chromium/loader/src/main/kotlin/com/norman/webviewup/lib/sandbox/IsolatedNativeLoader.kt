package com.norman.webviewup.lib.sandbox

/** Loaded from the installed host APK by the engine's defining loader, for ordinary JNI ownership. */
object IsolatedNativeLoader {
    @JvmStatic fun load(path: String) { System.load(path) }
}
