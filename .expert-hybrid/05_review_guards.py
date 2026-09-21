from pathlib import Path
p = Path('app-phone/src/androidTest/java/com/code2hack/eyebrowse/phone/HostingInstrumentedTest.kt')
s = p.read_text()
def rep(a,b):
    global s
    assert s.count(a)==1, (s.count(a),a[:100])
    s=s.replace(a,b)
rep('''        scenario.onActivity { it.requestedOrientation = restore }
        awaitPhoneContent()
        val restored = currentWebViewSize()''','''        scenario.onActivity { it.requestedOrientation = restore }
        waitUntil("Phone returned to original orientation class", {
            val size = currentWebViewSize()
            (size[1] >= size[0]) == originalPortrait
        })
        awaitPhoneContent()
        val restored = currentWebViewSize()''')
rep('''        val staleCallback = runOnMainSync { callbacks.first() }
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)''','''        val staleCallback = runOnMainSync { callbacks.first() }
        val oldActivity = AtomicReference<MainActivity>()
        val oldContainer = AtomicReference<android.view.ViewGroup>()
        val oldToken = AtomicReference<PhoneBrowserSession.Attachment>()
        scenario.onActivity { activity ->
            oldActivity.set(activity)
            oldContainer.set(activity.findViewById(R.id.web_container))
            // Read the real token without minting test-only product ownership.
            val field = MainActivity::class.java.getDeclaredField("attachment")
            field.isAccessible = true
            oldToken.set(field.get(activity) as PhoneBrowserSession.Attachment)
        }
        tapHostingToggleOnce(HostingController.State.NOT_HOSTING, STOP_BOUND_MS)''')
rep('''        val gen = runOnMainSync(hosting::currentGeneration)
        scenario.onActivity { it.moveTaskToBack(true) }
        waitUntil("successor privately attached",''','''        val gen = runOnMainSync(hosting::currentGeneration)
        scenario.recreate()
        awaitPhoneContent()
        val successorParent = runOnMainSync { session.view()!!.parent }
        runOnMain {
            val token = oldToken.get()
            hosting.onPhoneUiHidden(token)
            hosting.moveWebViewToPrivateDisplay(token)
            hosting.onPhoneUiDestroyed(token)
            hosting.ensurePhoneUiAttachment(oldActivity.get(), oldContainer.get(), token)
            assertNull("stale Activity cannot reclaim Phone presentation",
                hosting.moveWebViewToPhoneUi(oldActivity.get(), oldContainer.get()))
            session.detach(token)
            assertTrue("old Activity cleanup cannot detach successor", session.view()!!.parent === successorParent)
            assertTrue("successor UI registration survives old callback", hosting.hasPhoneUiOwner())
            assertEquals(gen, hosting.currentGeneration())
        }
        scenario.onActivity { it.moveTaskToBack(true) }
        waitUntil("successor privately attached",''')
s=s.replace('''     * A safe app-scoped window change while hosting reconciles the private geometry to the last
     * measured Phone content viewport (frames at the size, same document, no reload) and the exact
     * page state is restored when the window returns.''','''     * Hybrid contract: Phone rotation cannot mutate the private epoch profile. Return uses fresh
     * Phone content bounds; exact document/form/history continuity is preserved across reflow.''')
s=s.replace('''    /** R6: a live lease survives a geometry rebuild; delivery rearms at the rebuilt viewport. */''','''    /** Hybrid R6: a live lease survives owner handoff without Phone-driven private resizing. */''')
p.write_text(s)
p=Path('app-phone/src/main/java/com/code2hack/eyebrowse/phone/PhoneBrowserSession.kt')
s=p.read_text().replace('''     * {@code baseContext} (the alive hosting service), releasing any Activity reference so a''','''     * {@code baseContext} (the presentation display/window context), releasing any Activity reference so a''')
p.write_text(s)
p=Path('docs/plans/issue-6-hybrid-presentation.md')
s=p.read_text()+'''\nThe new stale-presentation identity also recreates the real Phone Activity and replays old\nattachment-token hide/destroy/detach/reclaim callbacks. Its successor must retain its real parent,\nUI registration, generation and protected current-document output. This is callback-race evidence,\nnot a substitute for real Start/Stop control taps. Phone rotation restoration waits for the actual\norientation class before comparing fresh Phone content bounds.\n'''
p.write_text(s)
