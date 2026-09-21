# I7-T02 — one-way presentation

Worker: Worker-#6-r3-Codex-#7

Baseline: 9811d1b8178619f0a20ae81c25d904291c699229. Implements T02 of Planner
comment 5759769926; T03 retains bidirectional handoff UI and browser effects.

The RG browser surface measures its image rectangle after local chrome/insets.
`requestPresentation()` is an explicit production controller API (used by paired-device
instrumentation in this slice), never called automatically by reconnect/foreground.
The Phone arbiter admits the existing handoff message. Hosting then retires the fallback
presentation and creates an immutable private epoch at that measured profile, retaining
the same WebView. Phone UI callbacks cannot reclaim the RG-owned view or resize its profile.
Stop still releases hosting and returns ordinary Phone browsing.

One hosting lease encodes its borrowed bitmap synchronously on the existing capture thread:
Android lossy WebP, quality 85, no spatial scaling. A fixed-capacity output buffer reserves
space for the maximum record metadata, rejecting overflow. The inherited capture throttle
and an encoder admission check retain the 200 ms minimum frame interval. No raw bitmap queue
or new transport exists. The T01 same-TLS queue keeps one pending frame and control priority.
Context change retires the lease/grant; disconnect stops demand. Existing retirement callbacks
allow acquisition after capture teardown, without a second capture owner.

RG has one pending encoded frame and one pending decoded UI frame, with one background decoder.
Before bitmap allocation it checks WebP magic and decoded bounds against the admitted header.
Display rechecks full context/profile/sequence after decode. Superseded buffers are released;
there is no frame FIFO. BrowserState supplies local title/location/status separately from pixels.
Disconnect, backgrounding, invalid profile and decode/encode failures show stale/degraded status.
Closing/backgrounding the RG surface closes its link and ends Phone capture demand.
No RG WebView, MediaProjection, page action effect or keyboard is added.

Verification: retain the 303 baseline JVM identities and add BoundedFrameOutputTest (2) and
PresentationInboxTest (3), expected 46 nonempty suites / 308 identities. Both debug APKs,
androidTest APKs and lintDebug are required. New paired physical-device instrumentation checks
measured profile, first frame <=2 s, changing counter frames, <=5 fps capture timestamps,
Phone configuration isolation, same-WebView continuity and Stop cleanup. Instrumentation
uses production APIs and a fixture-only reverse; product traffic stays direct LAN.
A compiled device test is not device acceptance; execution/evidence status lives in the
mission receipt. Fold6/optical/wearer conditions are not claimed.
