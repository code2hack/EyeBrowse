from pathlib import Path
import re, subprocess, textwrap
BASE = 'b6818680e6e55a43d532d9d9c09d4c3307ebfb9f'
assert subprocess.check_output(['git','rev-parse','HEAD'], text=True).strip() == BASE
P = Path('app-phone/src/main/java/com/code2hack/eyebrowse/phone')
T = Path('app-phone/src/test/java/com/code2hack/eyebrowse/phone')

def replace(s,a,b):
    assert s.count(a)==1, ('anchor count',s.count(a),a[:160])
    return s.replace(a,b)

def method(s,name,body):
    m=list(re.finditer(r'^    (?:private |internal |public )?fun '+re.escape(name)+r'\b',s,re.M))
    assert len(m)==1,(name,len(m))
    a=m[0].start(); b=s.index('\n    }',a)+len('\n    }')
    return s[:a]+body.strip('\n')+s[b:]

def block(s,a,b,body):
    start=s.index(a); end=s.index(b,start)
    return s[:start]+body+s[end:]

def put(path,s):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(textwrap.dedent(s).lstrip('\n'),encoding='utf-8')

put(P/'HostingPresentationProfile.kt','''
package com.code2hack.eyebrowse.phone

/** Immutable PRIVATE geometry: Phone window measurements never modify this profile. */
data class HostingPresentationProfile(val width: Int, val height: Int, val densityDpi: Int) {
    init {
        require(HostingPolicy.viewportError(width, height) == null) { "unsupported presentation bounds" }
        require(densityDpi > 0) { "presentation density must be positive" }
    }
    companion object {
        /** #6 fallback, not a statement of final #7 Normal/Reading/keyboard content bounds. */
        val RG_DESIGN_FALLBACK = HostingPresentationProfile(480, 640, 160)
    }
}

/** Identity, not equal dimensions/generation numbers, fences delayed platform callbacks. */
internal class PresentationEpochs {
    class Token internal constructor(val generation: Int, val profile: HostingPresentationProfile)
    var current: Token? = null
        private set
    fun begin(generation: Int, profile: HostingPresentationProfile): Token =
        Token(generation, profile).also { current = it }
    fun owns(token: Token): Boolean = current === token
    fun retire() { current = null }
}

/** Current Phone content bounds; no remembered orientation baseline or pixel tolerance. */
internal object PhoneContentViewport {
    fun size(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int): Pair<Int, Int> =
        (width - left - right).coerceAtLeast(0) to (height - top - bottom).coerceAtLeast(0)
}
''')
(P/'PhoneViewportStability.kt').unlink()
(T/'PhoneViewportStabilityTest.kt').unlink()
put(T/'HostingPresentationProfileTest.kt','''
package com.code2hack.eyebrowse.phone
import org.junit.Assert.*
import org.junit.Test

class HostingPresentationProfileTest {
    @Test fun fallbackIsStableAndNotPhoneDensity() {
        assertEquals(HostingPresentationProfile(480, 640, 160), HostingPresentationProfile.RG_DESIGN_FALLBACK)
    }
    @Test fun reportedContentProfileNeedNotEqualFallback() {
        assertNotEquals(HostingPresentationProfile.RG_DESIGN_FALLBACK, HostingPresentationProfile(480, 480, 240))
    }
    @Test fun phonePaddingIsNotCaptureGeometry() {
        assertEquals(1034 to 1770, PhoneContentViewport.size(1038, 1776, 2, 3, 2, 3))
        assertEquals(480, HostingPresentationProfile.RG_DESIGN_FALLBACK.width)
    }
    @Test fun phoneRotationDoesNotChangePrivateProfile() {
        val epochs = PresentationEpochs()
        val epoch = epochs.begin(4, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        PhoneContentViewport.size(1034,1772,0,0,0,0)
        PhoneContentViewport.size(2239,499,0,0,0,0)
        assertSame(epoch, epochs.current)
        assertEquals(HostingPresentationProfile(480,640,160), epoch.profile)
    }
    @Test fun staleCallbackCannotOwnSuccessor() {
        val epochs = PresentationEpochs()
        val a = epochs.begin(1, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        epochs.retire()
        val b = epochs.begin(2, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        assertFalse(epochs.owns(a)); assertTrue(epochs.owns(b))
    }
    @Test fun sameGenerationNumberStillDoesNotAuthorizeOldToken() {
        val epochs = PresentationEpochs()
        val a = epochs.begin(1, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        val b = epochs.begin(1, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        assertFalse(epochs.owns(a)); assertTrue(epochs.owns(b))
    }
    @Test fun retiredGenerationHasNoAuthority() {
        val epochs = PresentationEpochs()
        val a = epochs.begin(1, HostingPresentationProfile.RG_DESIGN_FALLBACK)
        epochs.retire()
        assertFalse(epochs.owns(a)); assertNull(epochs.current)
    }
    @Test fun invalidProfileIsRejectedNotClamped() {
        assertThrows(IllegalArgumentException::class.java) { HostingPresentationProfile(0,640,160) }
        assertThrows(IllegalArgumentException::class.java) { HostingPresentationProfile(4096,4096,160) }
        assertThrows(IllegalArgumentException::class.java) { HostingPresentationProfile(480,640,0) }
    }
    @Test fun transientUnlaidOutPhoneIsNotPrivateFallback() {
        assertEquals(0 to 0, PhoneContentViewport.size(0,0,3,3,3,3))
    }
}
''')
s=(P/'HostingController.kt').read_text()
s=replace(s,'    private val phoneViewportStability = PhoneViewportStability()', '    private val presentationEpochs = PresentationEpochs()')
s=replace(s,'''    // Last STABLE VISIBLE Phone content viewport (F6): the geometry private output reconciles to.
    // One-pixel same-shape jitter is canonicalized independently for portrait/landscape so a
    // round trip cannot silently change browser/capture geometry.''','''    // Phone-only observation, never the private display/reader profile.
    // Phone geometry is freshly framework-measured; no pixel-tolerance workaround.''')
s=replace(s,'        generation++\n        pendingStartGeneration = generation','''        generation++
        presentationEpochs.begin(generation, HostingPresentationProfile.RG_DESIGN_FALLBACK.copy())
        pendingStartGeneration = generation''')
s=block(s,'        var metric = lastViewport\n','        val previousHost = displayHost\n','''        val epoch = checkNotNull(presentationEpochs.current)
        val metric = epoch.profile
''')
s=replace(s,'''        val host = PrivateDisplayHost(resourceFactory, Runnable { onRetirementSignal() })
        displayHost = host''','''        val host = PrivateDisplayHost(resourceFactory, Runnable { onRetirementSignal(epoch) })
        displayHost = host
        host.setUnavailableListener(Runnable { onPresentationUnavailable(epoch, host) })''')
s=replace(s,'        lastViewport = metric\n        reconcileAttachmentAfterReadiness()', '        reconcileAttachmentAfterReadiness()')
s=replace(s,'''    private fun failStart(reason: String?) {
        mainHandler.removeCallbacks(startTimeout)''','''    private fun failStart(reason: String?) {
        presentationEpochs.retire()
        mainHandler.removeCallbacks(startTimeout)''')
s=replace(s,'''    private fun completeStop() {
        mainHandler.removeCallbacks(watchdog)''','''    private fun completeStop() {
        presentationEpochs.retire()
        mainHandler.removeCallbacks(watchdog)''')
s=method(s,'onPhoneViewportChanged','''    fun onPhoneViewportChanged(
        activity: android.app.Activity,
        container: ViewGroup?,
        measuredWidth: Int,
        measuredHeight: Int,
        densityDpi: Int,
    ) {
        if (!phoneUiAvailable || phoneUiActivity !== activity || phoneUiContainer !== container ||
                container == null) return
        val size = PhoneContentViewport.size(measuredWidth, measuredHeight,
            container.paddingLeft, container.paddingTop, container.paddingRight, container.paddingBottom)
        if (size.first > 0 && size.second > 0) {
            lastViewport = WebViewMetric(size.first, size.second, densityDpi)
        }
        // Phone callbacks never resize PRIVATE display/reader/WebView or rewrite its epoch profile.
    }''')
s=method(s,'hostOffscreenWithReconciledGeometry','''    private fun hostOffscreenWithReconciledGeometry() {
        val epoch = presentationEpochs.current ?: return
        val host = displayHost ?: return
        val profile = epoch.profile
        val demand = lease
        val consumer = frameConsumer
        if (demand != null && consumer != null) {
            try {
                host.ensureCaptureSurface(hostingContext!!, profile.width, profile.height,
                    profile.densityDpi, session)
                if (!host.rearmCapture(generation, BoundSink(demand, generation, consumer),
                        freshFrameRequest(epoch, host))) {
                    throw HostingException("capture rearm failed on private handoff")
                }
            } catch (error: HostingException) {
                failureReason = error.message
                host.stopCapture()
                notifyHostingChanged()
                return
            }
        }
        // Demand-free handoff never recreates a reader or changes the idle deadline. The
        // surviving Presentation has the SAME profile as the next capture acquisition.
        host.attachSessionView(session)
        attachment = Attachment.PRIVATE_DISPLAY
        notifyHostingChanged()
    }''')
s=replace(s,'''        if (state != State.HOSTING) {
            return null
        }
        // PHONE_UI metadata''','''        if (state != State.HOSTING || !phoneUiAvailable || phoneUiActivity !== activity ||
                phoneUiContainer !== container) {
            return null // A stale Activity cannot reclaim a successor's presentation.
        }
        // PHONE_UI metadata''')
s=block(s,'        // Viewport authority: while the view is attached','        try {\n            host.ensureCaptureSurface','''        val epoch = presentationEpochs.current ?: return null
        val metric = epoch.profile
''')
s=replace(s,'        val freshFrameRequest = Runnable { session.requestFreshCaptureFrame() }','        val freshFrameRequest = freshFrameRequest(epoch, host)')
s=method(s,'onRetirementSignal','''    private fun onRetirementSignal(epoch: PresentationEpochs.Token) {
        mainHandler.post {
            if (presentationEpochs.owns(epoch)) {
                evaluateRetirements()
            } else {
                // Old callbacks may complete ONLY retired resources, not the current host.
                synchronized(this) {
                    for (host in retiringHosts.toList()) host.evaluateRetirementCompletion()
                    pruneQuiescedRetiringHostsLocked()
                    if (retiringHosts.any { it.isRetiring() }) {
                        mainHandler.postDelayed({ onRetirementSignal(epoch) }, RETIREMENT_RECHECK_MS)
                    }
                }
            }
        }
    }''')
insert='''    private fun freshFrameRequest(epoch: PresentationEpochs.Token, host: PrivateDisplayHost): Runnable =
        Runnable {
            mainHandler.post {
                if (presentationEpochs.owns(epoch) && displayHost === host && state == State.HOSTING) {
                    session.requestFreshCaptureFrame {
                        presentationEpochs.owns(epoch) && displayHost === host &&
                            state == State.HOSTING && attachment == Attachment.PRIVATE_DISPLAY
                    }
                }
            }
        }

    private fun onPresentationUnavailable(epoch: PresentationEpochs.Token, host: PrivateDisplayHost) {
        mainHandler.post {
            synchronized(this) {
                if (!presentationEpochs.owns(epoch) || displayHost !== host) return@post
                if (state == State.STARTING) {
                    failStart("private presentation became unavailable")
                } else if (state == State.HOSTING) {
                    failureReason = "private presentation became unavailable; explicit restart required"
                    state = State.STOPPING
                    completeStop()
                }
            }
        }
    }

    /** Read-only generation contract, shared by display, reader and test qualification. */
    @Synchronized
    fun presentationProfile(): HostingPresentationProfile =
        checkNotNull(presentationEpochs.current) { "no hosting presentation generation" }.profile

    @Synchronized
    fun privateDisplaySnapshot(): PrivateDisplayHost.DisplaySnapshot? = displayHost?.displaySnapshot()

'''
s=replace(s,'    // ---------------------------------------------------- lifecycle events\n',insert+'    // ---------------------------------------------------- lifecycle events\n')
s=s.replace('The measured WebView content viewport used as the private-display geometry.', 'Fallback measurement of Phone geometry only.')
(P/'HostingController.kt').write_text(s)
s=(P/'PhoneBrowserSession.kt').read_text()
s=replace(s,'    fun requestFreshCaptureFrame() {','    fun requestFreshCaptureFrame(isCurrentOwner: () -> Boolean = { true }) {')
s=replace(s,'            if (webView !== target || rendererGone) {','            if (webView !== target || rendererGone || !isCurrentOwner()) {')
(P/'PhoneBrowserSession.kt').write_text(s)
