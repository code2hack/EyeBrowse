from pathlib import Path
import re
A=Path('app-phone/src/androidTest/java/com/code2hack/eyebrowse/phone/HostingInstrumentedTest.kt')
def replace(s,a,b):
    assert s.count(a)==1,('anchor count',s.count(a),a[:160])
    return s.replace(a,b)
def method(s,name,body):
    m=list(re.finditer(r'^    (?:private |internal |public )?fun '+re.escape(name)+r'\b',s,re.M))
    assert len(m)==1,(name,len(m))
    a=m[0].start(); b=s.index('\n    }',a)+len('\n    }')
    return s[:a]+body.strip('\n')+s[b:]
s=A.read_text()
s=replace(s,'import org.junit.AfterClass','import org.junit.After\nimport org.junit.AfterClass')
s=replace(s,'import org.junit.Assert.assertNull','import org.junit.Assert.assertNull\nimport org.junit.Assert.assertNotEquals')
s=replace(s,'    private lateinit var scenario: ActivityScenario<MainActivity>','''    private lateinit var scenario: ActivityScenario<MainActivity>
    private val ownedScenarios = mutableListOf<ActivityScenario<MainActivity>>()
    private val ownedRenewals = mutableListOf<LeaseRenewal>()

    private fun launchScenario(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { ownedScenarios.add(it) }

    @After
    fun releaseTestOwnership() {
        // Preserve the original JUnit failure. Cleanup failures are additional failures, not a
        // substitute for it. No process/app-data/trust reset or hidden foreground rescue.
        val failures = mutableListOf<Throwable>()
        fun attempt(action: () -> Unit) {
            try { action() } catch (failure: Throwable) { failures.add(failure) }
        }
        if (::hosting.isInitialized) {
            println("HYBRID_CLEANUP_BEFORE " + runOnMainSync(hosting::captureDiagnostics))
        }
        for (renewal in ownedRenewals) attempt { renewal.stopRenewing() }
        if (::hosting.isInitialized) attempt { runOnMain(hosting::stop) }
        for (owned in ownedScenarios.asReversed()) attempt { owned.close() }
        ownedScenarios.clear()
        if (::hosting.isInitialized) {
            attempt {
                waitUntilMain("test-owned resources retired", {
                    !hosting.captureResourcesPresent() && !hosting.hasDisplayResources() &&
                        !hosting.isWakeLockHeld() && !hosting.hasPhoneUiOwner()
                })
            }
            attempt { runOnMain { hosting.setResourceFactoryForTest(PrivateDisplayHost.PlatformFactory()) } }
        }
        org.junit.runners.model.MultipleFailureException.assertEmpty(failures)
    }''')
s=s.replace('scenario = ActivityScenario.launch(MainActivity::class.java)','scenario = launchScenario()')
s=replace(s,'    private class LeaseRenewal : Thread {','    private inner class LeaseRenewal : Thread {')
s=replace(s,'            this.lease = lease\n            setDaemon(true)','            this.lease = lease\n            ownedRenewals.add(this)\n            setDaemon(true)')
s=method(s,'bringMainActivityToFrontForTest','''    private fun bringMainActivityToFrontForTest() {
        val result = runShellCommandWithOutputForTest(
            "am start -W --display 0 -f 0x20000000 -n com.code2hack.eyebrowse.phone/.MainActivity"
        )
        println("PHONE_RETURN_RESULT " + result)
        assertTrue("single foreground request must succeed: " + result,
            result.contains("Status: ok") && !result.contains("Error:"))
        // One real launch, followed by the original attachment/focus checks and single tap.
    }''')
s=s.replace('val expectedSize: IntArray = currentWebViewSize()','val expectedSize: IntArray = expectedPrivateSize()')
s=s.replace('val replacementSize: IntArray = currentWebViewSize()','val replacementSize: IntArray = expectedPrivateSize()')
helpers='''    private fun expectedPrivateSize(): IntArray = runOnMainSync {
        val profile = hosting.presentationProfile()
        intArrayOf(profile.width, profile.height)
    }

    private fun awaitPrivateProfile(): PrivateDisplayHost.DisplaySnapshot {
        val profile = runOnMainSync(hosting::presentationProfile)
        waitUntil("private display, reader and WebView match epoch profile", {
            runOnMainSync {
                val snapshot = hosting.privateDisplaySnapshot()
                val view = session.view()
                hosting.status().attachment == HostingController.Attachment.PRIVATE_DISPLAY &&
                    snapshot != null && snapshot.valid && snapshot.state == android.view.Display.STATE_ON &&
                    snapshot.width == profile.width && snapshot.height == profile.height &&
                    snapshot.actualWidth == profile.width && snapshot.actualHeight == profile.height &&
                    snapshot.readerWidth == profile.width && snapshot.readerHeight == profile.height &&
                    snapshot.presentationContextDisplayId == snapshot.displayId &&
                    view != null && view.width == profile.width && view.height == profile.height &&
                    view.display?.displayId == snapshot.displayId
            }
        })
        return runOnMainSync { hosting.privateDisplaySnapshot()!! }
    }

    private fun awaitPhoneContent() {
        waitUntil("Phone owner uses fresh Phone content bounds", {
            runOnMainSync {
                val view = session.view()
                val container = view?.parent as? android.view.ViewGroup
                view != null && container != null && view.display?.displayId == 0 &&
                    hosting.status().attachment == HostingController.Attachment.PHONE_UI &&
                    !view.isLayoutRequested && !container.isLayoutRequested &&
                    view.width == container.width - container.paddingLeft - container.paddingRight &&
                    view.height == container.height - container.paddingTop - container.paddingBottom
            }
        })
    }

'''
s=replace(s,'    private fun currentWebViewSize(): IntArray {',helpers+'    private fun currentWebViewSize(): IntArray {')
s=method(s,'hostingWindowChangeReconcilesGeometryWithExactRestoration','''    fun hostingWindowChangeReconcilesGeometryWithExactRestoration() {
        openFixture("/hosting.html", "Hosting capture page")
        val marker = domText("load-marker")
        setFieldValue("geometry-value")
        val loadsBefore = loadCount("/hosting.html")
        val viewIdentity = webViewIdentityHash()
        val phoneBefore = currentWebViewSize()
        val originalPortrait = phoneBefore[1] >= phoneBefore[0]
        val opposite = if (originalPortrait) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val restore = if (originalPortrait) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val profile = runOnMainSync(hosting::presentationProfile)
        val generation = runOnMainSync(hosting::currentGeneration)
        scenario.onActivity { it.moveTaskToBack(true) }
        waitUntil("private owner before Phone rotation", {
            hostViewSnapshot().status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
        })
        val consumer = CollectingConsumer()
        consumer.expectQualification(profile.width, profile.height, CAPTURE_PAGE_COLOR)
        val eligible = SystemClock.uptimeMillis()
        val lease = runOnMainSync { hosting.acquireLease(consumer) }
        assertNotNull(lease)
        val renewal = LeaseRenewal(lease!!); renewal.start()
        try {
            val before = awaitPrivateProfile()
            waitUntil("first valid RG-profile frame", { consumer.qualifyingCountFrom(0) > 0 })
            val first = consumer.earliestQualifyingIndexFrom(0)
            assertTrue(OutputQualification.validWithinBound(consumer.earliestQualifyingDelayMsFrom(0, eligible)))
            scenario.onActivity { it.requestedOrientation = opposite }
            SystemClock.sleep(1_000)
            val during = awaitPrivateProfile()
            assertEquals(before.displayId, during.displayId)
            assertEquals(before.serial, during.serial)
            assertEquals(profile, runOnMainSync(hosting::presentationProfile))
            assertEquals(generation, runOnMainSync(hosting::currentGeneration))
            assertTrue(consumer.tailFramesMatchSize(first, profile.width, profile.height))
            assertTrue(consumer.tailFramesNearColor(first, CAPTURE_PAGE_COLOR))
            assertEquals(viewIdentity, webViewIdentityHash())
            milestones!!.record("hybrid Phone rotation preserved private profile " + during)
        } finally { renewal.stopRenewing(); runOnMain(lease::release) }
        bringMainActivityToFrontForTest()
        awaitPhoneContent()
        scenario.onActivity { it.requestedOrientation = restore }
        awaitPhoneContent()
        val restored = currentWebViewSize()
        assertEquals(originalPortrait, restored[1] >= restored[0])
        assertEquals(viewIdentity, webViewIdentityHash())
        assertEquals(marker, domText("load-marker"))
        assertEquals("geometry-value", readFieldValue())
        assertEquals("no reload across owner handoff", loadsBefore, loadCount("/hosting.html"))
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }''')
s=method(s,'liveLeaseSurvivesGeometryRebuildWithRearmedDelivery','''    fun liveLeaseSurvivesGeometryRebuildWithRearmedDelivery() {
        openFixture("/hosting.html", "Hosting capture page")
        val originalSize = currentWebViewSize()
        val marker = domText("load-marker")
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val profile = runOnMainSync(hosting::presentationProfile)
        val generation = runOnMainSync(hosting::currentGeneration)
        scenario.onActivity { it.moveTaskToBack(true) }
        waitUntil("private owner for live lease", {
            hostViewSnapshot().status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
        })
        val consumer = CollectingConsumer()
        consumer.expectQualification(profile.width, profile.height, CAPTURE_PAGE_COLOR)
        val lease = runOnMainSync { hosting.acquireLease(consumer) }
        assertNotNull(lease)
        val renewal = LeaseRenewal(lease!!); renewal.start()
        try {
            val before = awaitPrivateProfile()
            waitUntil("real current-document frames before handoff", { consumer.qualifyingCountFrom(0) > 0 })
            bringMainActivityToFrontForTest()
            awaitPhoneContent()
            val opposite = if (originalSize[1] >= originalSize[0])
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            scenario.onActivity { it.requestedOrientation = opposite }
            waitUntil("actual Phone layout changed", {
                val size = currentWebViewSize()
                size[0] != originalSize[0] || size[1] != originalSize[1]
            })
            awaitPhoneContent()
            assertEquals(profile, runOnMainSync(hosting::presentationProfile))
            val from = consumer.count()
            scenario.onActivity { it.moveTaskToBack(true) }
            val after = awaitPrivateProfile()
            assertEquals(before.displayId, after.displayId)
            assertEquals(before.serial, after.serial)
            waitUntil("same live lease resumes current page on immutable RG profile", {
                consumer.qualifyingCountFrom(from) > 0
            })
            val first = consumer.earliestQualifyingIndexFrom(from)
            assertTrue(consumer.tailFramesMatchSize(first, profile.width, profile.height))
            assertTrue(consumer.tailFramesNearColor(first, CAPTURE_PAGE_COLOR))
            assertTrue(consumer.allFramesMatchGeneration(generation))
            assertEquals(marker, domText("load-marker"))
        } finally { renewal.stopRenewing(); runOnMain(lease::release) }
        bringMainActivityToFrontForTest()
        awaitPhoneContent()
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }''')
newtest='''    @Test
    fun stalePresentationCallbackCannotStopFreshGeneration() {
        val callbacks = mutableListOf<Runnable>()
        val platform = PrivateDisplayHost.PlatformFactory()
        runOnMain { hosting.setResourceFactoryForTest(object : PrivateDisplayHost.Factory by platform {
            override fun createPresentation(context: Context, display: android.view.Display): PrivateDisplayHost.PresentationHost {
                val delegate = platform.createPresentation(context, display)
                return object : PrivateDisplayHost.PresentationHost by delegate {
                    override fun setUnavailableListener(listener: Runnable?) {
                        if (listener != null) callbacks.add(listener)
                        delegate.setUnavailableListener(listener)
                    }
                }
            }
        }) }
        openFixture("/hosting.html", "Hosting capture page")
        val marker = domText("load-marker")
        val viewId = webViewIdentityHash()
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val old = runOnMainSync { hosting.privateDisplaySnapshot()!! }
        val staleCallback = runOnMainSync { callbacks.first() }
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
        waitUntilMain("prior display and capture released", {
            !hosting.hasDisplayResources() && !hosting.captureResourcesPresent()
        })
        tapHostingToggleOnce(HostingController.State.HOSTING, START_BOUND_MS)
        val gen = runOnMainSync(hosting::currentGeneration)
        scenario.onActivity { it.moveTaskToBack(true) }
        waitUntil("successor privately attached", {
            hostViewSnapshot().status.attachment == HostingController.Attachment.PRIVATE_DISPLAY
        })
        val profile = runOnMainSync(hosting::presentationProfile)
        val consumer = CollectingConsumer()
        consumer.expectQualification(profile.width, profile.height, CAPTURE_PAGE_COLOR)
        val eligible = SystemClock.uptimeMillis()
        val lease = runOnMainSync { hosting.acquireLease(consumer) }
        assertNotNull(lease)
        val renewal = LeaseRenewal(lease!!); renewal.start()
        try {
            val fresh = awaitPrivateProfile()
            assertNotEquals("next generation cannot inherit old/OFF display", old.displayId, fresh.displayId)
            runOnMain { staleCallback.run() }
            runOnMain { }
            waitUntil("successor still produces qualified frames", { consumer.qualifyingCountFrom(0) > 0 })
            assertTrue(OutputQualification.validWithinBound(consumer.earliestQualifyingDelayMsFrom(0, eligible)))
            assertEquals(HostingController.State.HOSTING, runOnMainSync(hosting::status).state)
            assertEquals(gen, runOnMainSync(hosting::currentGeneration))
            assertEquals(fresh.displayId, awaitPrivateProfile().displayId)
            assertEquals(viewId, webViewIdentityHash())
            assertEquals(marker, domText("load-marker"))
            milestones!!.record("fresh private generation after stale callback " + fresh)
        } finally { renewal.stopRenewing(); runOnMain(lease::release) }
        bringMainActivityToFrontForTest()
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)
    }

'''
s=replace(s,'    private fun expectedPrivateSize(): IntArray',newtest+'    private fun expectedPrivateSize(): IntArray')
A.write_text(s)
p=Path('docs/plans/issue-6-hybrid-presentation.md')
p.write_text('''# I6 stability — hybrid presentation implementation

Authority: Planner SPEC Design, issue #6 comment 5757375322,
I6-PLANNER-STABILITY-DISPOSITION-GLMR2-20260921-01.

Phone control uses the actual Phone content rectangle measured by its parent layout. Private
presentation uses a single immutable profile for the hosting generation. The #6 fallback is
480 x 640 physical pixels at 160 dpi (480 x 640 logical dp); it does not inherit Phone density.
This is a design fallback, not a permanent #7 Normal/Reading/keyboard content contract.
No RG streaming or remote-control protocol is introduced. Existing Phone-background/private
attachment remains the local hosting seam for the shared primitive until #7 handoff.

Phone layout/orientation/inset callbacks update Phone-only observations. They cannot resize the
private display, reader or profile. The prior +/-1px orientation normalizer is removed. On return
the SAME WebView uses MATCH_PARENT in the current Phone content container; responsive reflow
is allowed, intentional reload/session replacement is not.

Each start binds a new presentation epoch. Delayed render/dismiss/retirement work is fenced by
resource identity and epoch identity. A stopped host never supplies display resources to the next
generation. Unexpected presentation loss stops the matching generation with an explicit reason;
it does not stop a successor. Invalid current resources rebuild only under genuine demand.
Idle surface detachment is intentional: demand must reattach and prove actual ON/current-profile
output. A new generation creates a new display and must not inherit the previous display's OFF
state. Presentation's own display/window context creates the container. Its capture window is
non-focusable/non-touchable, with no dimming/inset chrome. No Phone focus bypass is authorized.

Hosting instrumentation closes every launched ActivityScenario and renewal thread even after
failure. Cleanup failures remain failures. Foreground return is one observed
`am start -W --display 0`, not repeated rescue launches. No app-data/trust reset is added.

Predeclared physical suite: 36 identities (previous Phone35 plus
HostingInstrumentedTest#stalePresentationCallbackCannotStopFreshGeneration). Existing Phone-only
private-geometry rebuild/restoration assertions are replaced by the Planner-approved immutable
private-profile and fresh-Phone-content assertions. Frame qualification still requires EXACT
profile dimensions, correct current-document pixels and original timing bounds. Existing
static/120s capture/renewal/idle/Stop/borrowed-frame checks remain.

Required evidence: focused physical S20+ diagnostics on the exact candidate, then ONE predeclared
full physical run and independent exact-head review. Cloud host checks do not establish device
success. Fold6: NOT EXERCISED. Preserve S20/RG pairing: no app-data clear, uninstall, Forget,
seeding or identity rotation. Compatible signed APK replacement uses -r.
''')
