# Integrated Reading/keyboard recovery (#11)

The #11 integration candidate merges the #10 Reading/keyboard implementation,
including its local-disconnect retirement and failure-preserving Phone teardown
correction. It reuses the two paired journeys documented in `rg-reading.md` and
`rg-keyboard.md`; it does not add a substitute scroll controller or expiry timer.

Run paired `ReadingJourneyTest`, then paired `KeyboardJourneyTest`, on the same
exact four-APK candidate. The Reading pair exercises real Phone input-lease expiry
with updates and final Stop absent while transport/hosting stay active; it also
checks disconnect, held tilt after reconnect, fresh neutral renewal and Phone
return. The keyboard pair exercises actual keyboard dismissal on Reading entry,
Normal return without automatic reopen, and typed field continuity. Raw pose
replay is not physical sensor calibration or wearer evidence.

The integration adds explicit current-frame/full-context/profile assertions and
sequence/capture timestamp receipts at Reading entry and reconnect. Reconnect
must preserve lifetime/document/owner epoch and advance viewport epoch before
new input. `ContinuousScrollLeaseTest` adds an independent recovered-connection
regression: old start/credit/Stop cannot affect the successor; non-neutral first
input is rejected; a fresh neutral grant permits bounded motion then expires.

Timing is unchanged: Phone credit expiry300ms from last admitted update, at most
one already-valid in-flight update and source-loss bound600ms. The real Phone
observer allows1000ms to observe retirement, then checks stable native position
and step count; **no effect may occur at/after the admitted deadline**. Observer
completion is not an enlarged input lifetime. Current-frame barriers are2s,
authenticated reconnect10s, instrumentation65s and guarded child180s. Report
actual deadlines/effect timestamps separately from observer completion.

Use the single checked USB setup, four installed-APK digests, physical identities,
raw trust hashes and qualified screen-off adapter. Fixture27341/27342, app39818,
owned Phone reverse27341 only. At assignment, Manager accepted RG capability
metadata baseline `15cc10ea1469f51834d52c1e20fbcf02d6a5a528fdaa8eac375ca19c27bbfe67`;
Phone remains `4acad66fc5f9b99564758705715581f6da153297b5ddf07c4321da06c3221d07`.
Reconcile any later Manager-published baseline before entry, never restore trust
files. Capture mission-scoped receipts/layout screenshots; clean phase/ack/images,
owned fixture/reverse and app operations; final screen OFF/Dozing, no lock action.
No failed-row retry. Device booking remains through Manager after #10's window;
host compilation alone is not integrated device acceptance.
