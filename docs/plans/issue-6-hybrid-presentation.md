# I6 stability — hybrid presentation implementation

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

The new stale-presentation identity also recreates the real Phone Activity and replays old
attachment-token hide/destroy/detach/reclaim callbacks. Its successor must retain its real parent,
UI registration, generation and protected current-document output. This is callback-race evidence,
not a substitute for real Start/Stop control taps. Phone rotation restoration waits for the actual
orientation class before comparing fresh Phone content bounds.
