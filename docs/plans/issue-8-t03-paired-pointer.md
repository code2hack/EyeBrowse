# I8-T03 — paired pointer and pad journey

Worker: Worker-v0.01. [Controlling R2 plan 5771447930](https://github.com/code2hack/EyeBrowse/issues/8#issuecomment-5771447930), sections 5–8. Baseline `2a926733`. The initial T03 candidate added paired Kotlin companions and fixture observations. Outcome B adds the [single-prepared-ordinal correction](issue-8-t03-prepared-ordinal.md) under Planner supplement5774967990; Phone/wire/v2 identity/admission semantics remain unchanged.

## Input and effect provenance

The physical RG uses the same raw quaternion replay source as T02, extracted into `RawPoseReplay`. It substitutes only `HeadPoseSource`: normal relative mapping, filtering, overlay drawing, measured hit testing and gesture routing remain active. Its saved reference tracks real Recenter and interrupted-stream rebasing. It never sets cursor coordinates. Constructed framework KeyEvents carry the observed `ROKID,PSOC-TP-R` device ID and keyboard source and enter the actual Activity adapter (ENTER66, double291, forward292, backward293). This is physical-device framework replay, not wearer motion or finger-gesture evidence.

Every positive remote operation goes through that pointer/pad path and the existing authenticated direct-LAN link to the physical S20+. Native controls are aimed using measured bounds. Page targets use fixture CSS metadata multiplied by the measured WebView scale and the Android ImageView forward transform; the production inverse hit-test output does not define the test's aim. Phone independently observes actual WebView native scroll, CSS rectangles, document identity and fixture event history. The fixture's harmless buttons change visible text so an accepted action must also produce changed returned pixels. Admission acknowledgement alone is not an effect assertion.

## Paired identities and checkpoints

- Phone `PointerBrowserJourneyTest#phoneVerifiesPointerEffectsAndEveryInterruptedIntent`.
- RG `PointerBrowserJourneyTest#rgPointerAndPadDriveAuthenticatedActionsWithNoInterruptionReplay`.

They cover explicit Use on glasses / Use on phone round trips; stable WebView, document, history, form and heap markers; ActivateAt on two buttons and a link; native Back/Forward/Reload; +/-160 native-pixel scrolling while aiming at local chrome; and explicit disconnected Phone takeover followed by Hosting Stop. Context/profile/frame headers agree with the RG-measured content surface. No fallback-size or density-based CSS conversion is assumed.

Negative checkpoints retain exact Phone effect counts and unchanged gesture-bound consumption, construction and queue-admission counters (proactive reservations are separate): Phone-owned page/navigation gestures; disabled Forward; double-tap with OEM composite marker; sensor silence, pause, profile change and modal interruption; busy actions; navigation during a pending page tap; Phone takeover during a pending handoff tap; link loss; and no queued replay after recovery. A real action is delayed by a bounded 900 ms Phone main-thread test task to exercise busy admission. That injected delay is disclosed separately from ordinary healthy-LAN latency.

Only protocol-negative probes bypass the pointer: stale old context, replay of a resolved ID, out-of-viewport rejection, and reuse of its consumed ordinal. They use the existing real authenticated client and Phone admission. Positive effects never use that shortcut. Legitimate cursor advancement remains persisted; it is not cleanup residue.

## Timing and coordination

The scratch host driver writes enumerated phase names to UUID-scoped private instrumentation cache files; the Phone companion performs fixture observations or test-only interruptions. The RG companion reports phase requests in its own log, and the driver verifies ordering. These files are not product receivers, app APIs, a second control transport or trust seeds. Product control and frames remain direct LAN; the Phone-only ADB reverse carries the local webpage fixture and is removed afterward.

Local confirmed-gesture-to-invocation/enqueue must be <=100 ms; the configured 300 ms double-tap recognition wait is reported separately. Handoff acknowledgement <=1 s, current fixture-qualified presentation <=2 s, and non-network fixture action effects <=1 s are measured on the RG's monotonic clock. Navigation/takeover must change the actual published context before the original tap's confirmation deadline. The test observes the controller's volatile snapshot directly: ActivityScenario's UI-idleness wait is unsuitable as a context-arrival clock. No production timing or recognition window is changed.

Phone and RG uptime have separate origins. Host before/after uptime samples provide conservative offset intervals for cross-device frame-age envelopes; device wall clocks are never subtracted. During an explicit product-link pause, fresh local pointer drawing and Recenter must remain <=100 ms with zero additional received frames.

## Gates and preservation

Initial T03 retained the T02 **56 suites /366 JVM identities** (T01 52/340 + T02 4/26). Outcome B retains those identities and adds allocator/readiness/gesture regressions; freeze and reconcile the exact final names before the committed-head full gate. The original18 selected Android identities remain, with added real-store and preparation-matrix identities explicitly declared separately. Fixture-server tests are a separate Python count, not JVM identities.

Use physical S20+ R5CN30NA8GX (guarded TCP5555-first management) and RG1906092617103125 USB. Before/after every paired run, require full trust hashes S20 `21de469a282d05d2d2a2814658b5bbff2612f8a35d6840e3bbd26877f41a06d5` and RG `4480b02f546780798f7514f7d236f77414148cb38cab76ffc433e954b7ebfac8`. Hash-match all four canonical APKs and install with `-r` only. Preserve pairing and settings; restore temporary screen timeout; remove only mission signal files, fixture cert/key and owned reverse; stop fixture/logcat collectors; verify Hosting/listener cleanup and S20 actual locked state. RG sleep is reported as sleep, not secure lock.

Retain original development failures and corrected evidence under `/tmp/eyebrowse-worker-v001-issue8/t03/`, and append executed results to the mission's single matrix/disclosure ledger. No emulator, optical/wearer proof, keyboard, Reading or system-input service is claimed. Final whole-ticket consolidation and fresh paired sweep remain T04.
