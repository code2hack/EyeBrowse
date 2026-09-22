# I8-T01 — ordinary-app pose source and local pointer

Worker: Worker-v0.01. Controlling [R2 ticket plan5771447930](https://github.com/code2hack/EyeBrowse/issues/8#issuecomment-5771447930); prior5771347324 is void. Manager baseline314cf212; registry-only successor75f3864 carries the renamed participants. This slice does not implement gestures, consent routing, keyboard or Reading. T02/T03 will connect gestures to the existing authenticated controller.

## Observed platform before implementation

Physical RG1906092617103125, API32; firmware `Rokid/glasses/glasses:12/SKQ1.240613.001/1.25.012-20260901-150201:user/release-keys`. Initial read-only sensorservice/input/getevent/key-layout inventory was followed by ordinary-app SensorManager/InputDevice enumeration and acquisition. No owner-worn motion/finger gestures were requested or inferred.

Selected source: **QTI Game Rotation Vector Non-wakeup**, Android type15, version1. App SensorManager lists16 sensors; the selected sensor exposes minDelay5000µs/maxDelay200000µs. The requested `SENSOR_DELAY_GAME` produced five finite samples19.801666–19.801667ms apart; callback ages2.429–3.077ms against elapsedRealtimeNanos. Test listener release was observed. A vendor game rotation vector is available, so no gyro integration/fusion subsystem was added. An ordinary rotation-vector alternative is selected only if the game source is absent; absence/failed registration otherwise gives recoverable unavailable status.

The Android [game rotation vector](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position#sensors-pos-gamerot) provides relative orientation without a magnetic-north guarantee. Components are copied as quaternion(x,y,z,w), not retained framework arrays or mislabeled Euler yaw/pitch. [Sensor timestamps](https://developer.android.com/reference/android/hardware/SensorEvent#timestamp) and age checks use elapsed realtime including sleep; callback-to-draw uses the same clock. Future, old, duplicate/out-of-order and non-finite/zero-quaternion samples do not refresh availability. Key/motion uptime is not mixed into these calculations.

Pad inventory for later T02, **not physical gesture execution**:

| Observed layer | Result |
| --- | --- |
| InputDevice | `ROKID,PSOC-TP-R`, id3 at inventory time, sources257/SOURCE_KEYBOARD, no motion ranges |
| Linux node | `/dev/input/event1`, key device; path/name alone not an Android gesture proof |
| Key layout | `/system/usr/keylayout/Generic.kl` |
| ENTER | Linux28 → Android66/KEYCODE_ENTER supported |
| Vendor double tap | Linux202 → Android291/KEYCODE_SPRITE_DOUBLE_TAP supported |
| Vendor forward/back swipe | Linux183/184 → Android292/293, SPRITE_SWIPE_FORWARD/BACK supported |
| Other keys | BACK4, DPAD19–22, NOTIFICATION83, SETTINGS176, PROG_BLUE186; unrelated keys remain untouched |

Both the vendor key-layout symbols and actual InputDevice.hasKeys/keyCodeToString support were recorded. The physical down/up/repeat/composite sequence is unobserved; T01 adds no event decoder or speculative mapping.

## Declared mapping and tuning

The declared mounting convention uses Android's natural-display axes: +X screen right, +Y screen up, +Z toward viewer. At inventory the RG display rotation is0 with480×640 full display; the content area is measured separately. Relative quaternion `inverse(neutral) * current` rotates the forward ray(0,0,-1). Its right/down angles drive **position**, not a velocity mouse. Negative rotation around declared +Y aims right; negative rotation around +X aims down. Display rotations0/90/180/270 remap the ray axes; this does not equate Android azimuth/pitch labels with a worn head turn. [Android sensor coordinate-system reference](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview#sensors-coords).

**Physical mounting signs/calibration/comfort remain NOT EXERCISED.** The transform is an explicit, unit-tested Android-coordinate contract, not a claim of observed wearer calibration. There is no absolute compass-heading promise; Recenter is available for relative-source drift.

Settings are constructor-bounded, committed before acceptance:

| Setting | Selected value / behavior |
| --- | --- |
| Horizontal / vertical half-range |30° /22° maps neutral→corresponding root edge, after deadband |
| Angular deadband |0.3° per projected axis; subtract outside band to avoid a discontinuous jump |
| Smoothing | Exponential position filter, time constant60ms, advanced with real elapsed frame time |
| Maximum speed |3 full normalized root spans/s (Euclidean normalized displacement), independent of sensor callback count |
| Stale cutoff / maximum stream gap |250ms; exact deadline makes aim unavailable; next valid sample rebases to fresh neutral |
| Initial neutral |First valid copied sample; no Owner pose hold or calibration prompt |
| Recenter |Use latest fresh orientation, immediately center, no ownership/action/ordinal mutation |
| Bounds |Actual root padding/insets, inset by the8dp cursor radius; clamp center inside measured usable root |
| Display-axis change |Invalidate reference, require next valid sample; ordinary page frames never recenter |

Held pose converges to one fixed position. No full-display480×640 or historical page-profile480×405 constants enter pointer geometry. A resize recomputes bounds, with no remote profile mutation from cursor motion.

## Lifecycle and rendering

`SensorHeadPoseSource` registers on the main callback handler with GAME delay. It fences queued callbacks by listener identity before unregistering. `PointerOverlay` adds a registration generation so an injected or retired callback also cannot update a restarted/recreated surface. MainActivity starts it onResume and stops it onPause/destroy/detach; no background service, high-rate permission or keep-awake setting.

There is one pending visual update and one stale deadline. Animation follows display frames while the filter settles; settled/no-change samples do not require new draws. No bitmap, sensor FIFO, disk write or network action runs per sample. The overlay sits above the existing local controls/image without affecting their layout, does not consume touches or focus, and is absent from Phone capture. Recenter adds one fixed-height chrome row; the existing ImageView callback still measures the resulting content profile. Availability text stays inside that fixed row.

Sensor silence freezes/greys the pointer and displays a recoverable status. Local native exit/pairing/handoff controls remain present; page activation and gesture cancellation are T02 responsibilities using this availability boundary. Pose/recenter cannot call `RgPresentationController`: this slice has no route to page effects. Only the source is replaceable by in-process instrumentation; replay still feeds raw quaternions through the same model and actual overlay. No production receiver or external replay switch exists.

## Verification and inherited scope

New JVM class `HeadPointerModelTest` has12 identities for axes/wrap/quaternion sign, display remap/roll, deadband/held pose, time-based filtering, speed/clipping/resize, recenter, invalid/order/age, exact silence/recovery, large gaps, lifecycle and unavailable layout. Baseline328 identities plus12 = **52 suites /340 JVM identities**; freeze exact list before the full gate.

New `PointerInputInstrumentedTest` has7 identities: ordinary-app platform inventory/acquisition/release; actual-source pause/resume/recreate; raw replay/local draw/recenter/no commands; silence/recovery; old-registration fencing; absence/invalid input; transparent overlay/stable measured content. Local small-change draw bound100ms is measured from callback receipt on the same RG clock, separately from a settling observation window; no Phone frames are required. No gesture-enqueue timing or paired page effect is claimed in T01.

Seven accepted #7 canonical device identities were assessed: unchanged Phone/core/camera/crypto source remains baseline evidence; the two paired presentation identities and two control-journey identities are affected by the added RG chrome/lifecycle and must be re-exercised in the assigned later integration scope. The three Phone Stop/attachment identities have no Phone production change. They are not silently reclassified as executed on this head. T01/T02 are RG-only per Manager I8-T01-S20-GATE-ROUTED-GLMR2-20260922-01; S20 not freshly certified, last verified locked/Dozing at #7 T04. Full dual trust precheck and guarded S20 access resume before T03.

All preserved #7 invariants remain: measured hybrid profile, same-WebView handoff, v2 IDs/ordinal reservation, no implicit takeover, same TLS, latest-only≤5fps WebP, and no uncertain action retry. T01 changes no consent or production browser-action code. Keyboard/Reading/head-scroll, physical finger encoding/comfort, optical/Fold6/Doze/Freecess/hotspot/VPN/unplugged qualification remain outside this todo.
