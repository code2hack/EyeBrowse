from pathlib import Path
P=Path('app-phone/src/main/java/com/code2hack/eyebrowse/phone')
def replace(s,a,b):
    assert s.count(a)==1, ('anchor count',s.count(a),a[:160])
    return s.replace(a,b)
def block(s,a,b,body):
    start=s.index(a); end=s.index(b,start)
    return s[:start]+body+s[end:]
s=(P/'PrivateDisplayHost.kt').read_text()
s=replace(s,'        fun container(): FrameLayout\n    }','''        fun container(): FrameLayout

        /** Optional for fake factories; platform implementation checks its window/display. */
        fun isAvailable(): Boolean = true
        fun setUnavailableListener(listener: Runnable?) {}
    }''')
s=replace(s,'    private var virtualDisplay: VirtualDisplay? = null // main-thread only','''    private var unavailableListener: Runnable? = null
    private var resourceSerial: Long = 0
    private var virtualDisplay: VirtualDisplay? = null // main-thread only

    fun setUnavailableListener(listener: Runnable?) { unavailableListener = listener }

    data class DisplaySnapshot(
        val serial: Long, val displayId: Int, val valid: Boolean, val state: Int,
        val width: Int, val height: Int, val actualWidth: Int, val actualHeight: Int,
        val densityDpi: Int, val readerWidth: Int, val readerHeight: Int,
        val presentationContextDisplayId: Int, val surfaceDetached: Boolean,
    )

    fun displaySnapshot(): DisplaySnapshot {
        val display = virtualDisplay?.display
        val size = android.graphics.Point()
        if (display?.isValid == true) display.getRealSize(size)
        val readerSize = synchronized(nativeLock) {
            (imageReader?.width ?: 0) to (imageReader?.height ?: 0)
        }
        val contextDisplay = runCatching {
            presentation?.container()?.context?.display?.displayId ?: -1
        }.getOrDefault(-1)
        return DisplaySnapshot(resourceSerial, display?.displayId ?: -1,
            display?.isValid == true, display?.state ?: Display.STATE_UNKNOWN,
            width, height, size.x, size.y, densityDpi, readerSize.first, readerSize.second,
            contextDisplay, displaySurfaceDetached)
    }''')
s=replace(s,'        width = measuredWidth\n        height = measuredHeight','        resourceSerial++\n        width = measuredWidth\n        height = measuredHeight')
s=replace(s,'''            presentation = presentationHost
            presentationHost.show()''','''            presentation = presentationHost
            val createdSerial = resourceSerial
            presentationHost.setUnavailableListener(Runnable {
                // Retired Presentation events cannot invalidate a replacement in the same host.
                if (presentation === presentationHost && resourceSerial == createdSerial) {
                    unavailableListener?.run()
                }
            })
            presentationHost.show()''')
s=replace(s,'''        synchronized(nativeLock) {
            if (imageReader != null && width == desiredWidth''','''        if (virtualDisplay?.display?.isValid != true || presentation?.isAvailable() != true) {
            rebuildAtSize(serviceContext, desiredWidth, desiredHeight, desiredDensityDpi, session)
            return
        }
        synchronized(nativeLock) {
            if (imageReader != null && width == desiredWidth''')
s=s.replace('                shown.dismiss()','                shown.setUnavailableListener(null)\n                shown.dismiss()')
s=s.replace('                presentation?.dismiss()','                presentation?.setUnavailableListener(null)\n                presentation?.dismiss()')
s=block(s,'    private class HostingPresentation(context: Context, display: Display) :','    companion object {','''    private class HostingPresentation(outerContext: Context, display: Display) :
            android.app.Presentation(outerContext, display), PresentationHost {

        // Presentation.getContext(): a display/window context on API31+, NOT the outer service.
        private val content = FrameLayout(this.context).apply { setBackgroundColor(Color.WHITE) }

        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            super.onCreate(savedInstanceState)
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window?.apply {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.WHITE))
                setDecorFitsSystemWindows(false)
                attributes = attributes.apply { setFitInsetsTypes(0) }
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            setContentView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        override fun container(): FrameLayout = content
        override fun isAvailable(): Boolean = isShowing && display.isValid
        override fun setUnavailableListener(listener: Runnable?) {
            setOnDismissListener(if (listener == null) null else
                android.content.DialogInterface.OnDismissListener { listener.run() })
        }
    }

''')
s=replace(s,'                " owner=" + ownerPhase.phase()', '                " owner=" + ownerPhase.phase() + " display={" + displaySnapshot() + "}"')
(P/'PrivateDisplayHost.kt').write_text(s)
