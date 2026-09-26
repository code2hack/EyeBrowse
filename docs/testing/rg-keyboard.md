# RG keyboard consumer (#9 remainder / I10-KBD)

This is a verification procedure and implementation contract, not device acceptance.
Reading Mode is the following I10-IMPL todo. Protected project articles remain unchanged.

## Input and ownership

The native RG key views feed the existing head-pointer/pad router. The captured key includes
its keyboard generation and semantic output; the router also captures the original view,
geometry, browser context, readiness and prepared-ordinal revision. Shift, symbols, caret or
draft changes, editor turnover and dismissal invalidate pending keys. Double-tap arbitration
is unchanged (Android timeout clamped to 100–600 ms); neither constituent tap types a key.

Address drafts exist only in the current RG process. Left/right and Backspace operate on
code points; the maximum is 4096 UTF-8 bytes, rejected rather than truncated. The existing
2048-character browser-status URL is only used as a prefill below its ceiling, to avoid
turning a truncated display URL into a different navigation. Letters and symbols have the
same four-row height; Shift is a two-state toggle, reset on a new session. Ordinary native
buttons support accessibility activation without installing a system IME.

Page values, including passwords, are never mirrored into the RG keyboard. Field activation
is correlated by the optional `EditorStateMessage.activationCommandId`, echoed from the
original admitted `ActivateAt`. Unsolicited/old state cannot reopen a dismissed keyboard.
The field's actual pixels remain in the live page above the keys. Optical readability still
requires physical qualification; screenshots and measured bounds establish only layout.

All edits and address submission use the existing one prepared v2 ordinal, one pending
command and authenticated queue. No key waits in a replay queue. Local draft/layer/Done
operations do not require an ordinal. Controller memory changes, validation and queue offers
are serialized by its existing monitor; view rendering is posted to Main. No preference IO,
WebView call or wait is added under that monitor. Native Retry retains its outside-monitor
trust-read path.

Phone's shared action-result helper preserves admission versus page effect: `ADDRESS_REJECTED`
means policy rejected the input without navigation, while `ADDRESS_OPENED` means navigation
was dispatched, not website success. An uncertain dispatch remains `DISPATCH_UNCERTAIN`.
A late result cannot close a changed draft or a successor keyboard session.

## Viewport and cancellation

Keyboard layout is measured after Android traversal. One correlated viewport request is
in flight, with only the latest desired layout retained. Ordinary page/edit input stays
suspended until the acknowledged context, authoritative state and a decoded current frame
agree. Control epochs and the durable ordinal floor do not reset during a resize.

The RG transition budget is 2000 ms from the requested layout; the Phone's existing 2000 ms
profile budget is unchanged. Editor-close acknowledgement is bounded at 1000 ms. Deadlines
are not renewed by callbacks or retries. Failure makes the surface stale with local recovery;
it does not invent a ready frame or replay text. Unplanned size changes retain the prior
fail-closed disconnect behavior.

`ViewportUpdateMessage.retainEditor` defaults to true for existing callers. Keyboard hide
and address mode send false, so Done during an in-flight retained-field resize cannot leave
a rebound editing grant behind. A final close targets only the exact Phone token; it never
submits or blurs a successor. Window/presentation loss dismisses entry and cancels pending
activation. Return to the foreground does not reopen it.

## Host checks

Use JDK17, the assigned SDK, the committed two-worker/2 GiB settings and explicit debug tasks:

```sh
./gradlew --no-daemon --console=plain \
  :core:browser:test :core:link:test \
  :app-phone:testDebugUnitTest :app-rg:testDebugUnitTest \
  :app-phone:assembleDebug :app-rg:assembleDebug \
  :app-phone:assembleDebugAndroidTest :app-rg:assembleDebugAndroidTest \
  :app-phone:lintDebug :app-rg:lintDebug
python3 -m unittest discover -s tools/tests
```

The two baseline `LinkEnginePairingTest` failures at `830c057` are retained in the mission
scratch records and routed to #11. The full acceptance gate requires the authorized baseline
including that fix; targeted development results do not waive it. Lint's existing Kotlin
metadata-version diagnostics must be disclosed even when the tasks return success.

## Reserved real-device journey

Reserve both S20+ and RG through Manager before any device action. Verify physical identity,
canonical trust hashes, screen/lock state and exact source/APK hashes using DEV procedures.
Use the existing fixture server (HTTP 26341), `/keyboard.html?case=<mission UUID>`, and the
existing authenticated application link on 39818. An S20 ADB reverse for the fixture is test
routing, not evidence of an all-wireless route. Never clear app data or re-pair as setup.

Run `KeyboardJourneyTest` in each app's debug test APK with identical `missionId` and
`fixtureBaseUrl` arguments. The Phone test starts the fixture and Hosting through the real
Activity. Launch RG after its `PHONE_KBD_READY` receipt. A mission-owned host relay copies
only `cache/kbd-<mission>.phase` from RG to Phone and `.ack` from Phone to RG. These files
synchronize assertions; they do not provide input, pair devices or grant browser authority.

RG uses actual native address keys for repetitive draft correction, and raw-pose replay plus
real ROKID pad KeyEvents for field selection, key activation, navigation consent and Done.
Declare those input sources separately. The Phone independently checks actual form values,
harmless submit count, unchanged WebView identity and live page identity. The journey covers
invalid/corrected addresses, rapid close/open with an unchanged final layout, old-session
key rejection, double-tap suppression, stale case, all four field types,
Shift/symbols/Space/Backspace/Enter/Done, Done during pending key confirmation, readonly
invalidation and explicit return to Phone. The multiline/plain phases also run while the
Phone Activity is backgrounded, then foreground it without an ownership transfer.
No positive field effect is produced by DOM assignment. Test-only readonly mutation supplies
the negative, through the real renderer observation path.

Declared automated bounds: confirmed RG dispatch ≤100 ms; correlated key result ≤2 s;
viewport readiness ≤2 s; address navigation fixture observation ≤5 s. The longer host phase
waits coordinate independent tests and are not product latency claims. Collect actual times,
terminal JUnit identities/results and same-invocation receipts; neither an ADB exit code nor
a screenshot alone is a pass. Inspect RG normal/field/password screenshots and retain their
app-build bindings. Never publish unrelated device content.

Required follow-on evidence remains explicit: display-off conditions, guarded
available lock state, further pending focus/geometry and link interruption negatives, measured key
bounds and resource/stop observations. These must be executed against the candidate before
claiming full I10-KBD verification. Reading/tilt tests belong to I10-IMPL/I10-VERIFY.

After both terminal results, stop owned instrumentation/services and the fixture server,
remove only mission-specific reverse mappings/cache files, verify trust unchanged and the
actual final screen state. Keep required screenshots/logs before cleanup. Unavailable wearer,
optical, physical-fold and unplugged conditions are `NOT EXERCISED — unattended profile`;
software failures or missing essential device integration are not physical waivers.
