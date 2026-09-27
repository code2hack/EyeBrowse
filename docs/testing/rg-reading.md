# Reading Mode verification (#10)

Reading uses the existing sensor quaternion source and pad recognizer. Double tap
switches the native layout; single taps in Reading have no target. The same live
Phone WebView receives a measured viewport, preserving document identity and form
state. Leaving Reading never reopens a keyboard. Without sensors, double tap can
still exit Reading to the normal recovery and handoff controls.

## Declared parameters

`HeadScrollModel.Settings` is the bounded tuning surface (no new preferences UI):
2-degree dead zone, 60 native pixels/second/degree gain, 600 pixels/second cap,
300 ms stable-neutral acquisition, 250 ms sensor staleness, 80 ms smoothing.
Drift adjustment occurs only within neutral, at at most 0.05 degrees/second and
at most 3 degrees from the acquired reference. Interruption retains that reference
and requires neutral again; it cannot redefine a held tilt as renewed input.
These are implementation/test parameters, not wearer calibration evidence.

The authenticated state stream is negotiated by `CONTINUOUS_SCROLL_V1` in protocol
minor 3. It is separate from discrete v2 command ordinals. A start grants a unique
lease and one-use credit; the first update must be zero. Every accepted update
rotates the credit and establishes a Phone `elapsedRealtime` deadline 300 ms later.
Expired, duplicate, foreign-context and foreign-connection input cannot renew it.
Only one update is outstanding on RG. Native scroll ticks and updates run at 50 ms;
each integration step caps elapsed time at 50 ms, with no catch-up after a stalled
Main thread. The Phone checks eligibility and expiry immediately before every
native scroll, under the same authority monitor used by link retirement.

Stop is best effort. Loss of updates expires independently of capture/hosting
leases and transport timeout. An already-issued valid credit may admit one last
update, so the declared loss bound is 600 ms from source cessation; the admission
bound remains 300 ms from the last accepted update. Actual deadline and effect
timestamps must be reported separately from later observer/scheduler completion.
Swipes, focus/occlusion, layout/document/ownership changes and stale sensor input
suspend RG motion; a swipe also revokes Phone continuous state before its discrete
action. Held tilt cannot resume until fresh geometry and stable neutral.

## Host checks

Run the full debug gate in DEV.md with a Manager-coordinated quiet host. Focused
checks are `HeadScrollModelTest`, `ContinuousScrollLeaseTest`, codec tests and the
existing head pointer suite. Both consumers and both instrumentation APKs compile.
No release build, production replay receiver or alternate effect queue is added.

## Reserved paired checks

Use the checked USB setup/identity/trust/digest and screen-off cleanup discipline
in `rg-keyboard.md`. Request a fresh Manager window and reconcile exact candidate,
four APK digests and the authenticated capability-metadata trust advancement
before execution. Do not re-pair or write trust records manually.

- Updated `KeyboardJourneyTest` covers actual keyboard dismissal by double tap,
  Reading/Normal viewport transitions, isolated-tap suppression and no automatic
  reopen, then continues the original four-field and stale-key checks.
- Paired `ReadingJourneyTest` uses `RawPoseReplay` through `PointerOverlay` and
  real ROKID pad `KeyEvent`s. Phone independently observes native WebView scroll
  and identity while in foreground and background hosting. It checks directions,
  neutral, swipe/held tilt, sensor absence, modal inactivity, sensor-free exit,
  authenticated reconnect while tilted, neutral renewal and Phone handoff.
- The lost-final-stop row stops the RG producer callback and raw pose source only
  in instrumentation, leaving authenticated transport and Phone hosting alive.
  Phone records its real input deadline and final native effect; neither a capture
  lease nor disconnect substitutes for this expiry. Protocol malformed/delayed
  credit boundaries are additionally checked by the host suite.
- Capture the real Reading layout, record screen/lock state and all timing/resource
  receipts. The host paired-command bound remains 65 s per journey / 180 s guarded child;
  no failed row is retried in the same window.

Status: host implementation and compiled instrumentation are preparation, not
paired acceptance. The prior keyboard live-edit screenshot freshness limitation
remains open: no newer frame was observed while editing the password; the Normal
screenshot showed masking and Phone independently checked the value/type. Do not
claim that this Reading implementation reran or resolved that evidence gap.

## R4 terminal observation

Final Manager routing `MGR3-T02-I10-READING-R4-ROUTE-FINAL-20260927-01`
retains the **65-second host paired-command bound** and 180-second outer guard.
The interim proposal to increase the former to 90 seconds was withdrawn before
execution. No product source, test assertion, input or deadline changes in R4.

Standalone keyboard R2 measured RG 57.835 s / Phone 59.719 s; integrated R3
measured RG 61.859 s / Phone 63.562 s, with both terminal JUnit results passing.
The 65 s host command nevertheless timed out during terminal recognition.
R3 remains NOT_PASSED as executed. Its timestamped Phone completion and retained
RG terminal stream do not establish an exact RG completion time relative to the
old deadline.

The host runner records launch and the shared deadline, observes each process
with its own bounded waiter, and keeps the existing phase-file relay off the
terminal-observation path. Terminal receipt timestamps must precede the original
deadline and both streams must contain terminal JUnit PASS. Late or unavailable
completion evidence still fails. The no-retry rule, phase gate, cleanup and all
product liveness/latency bounds remain unchanged.
