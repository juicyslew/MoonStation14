# R43 — saved anchors unresolved; initialization never completes

**3/895 selected,3 completed,0 passed,3 failed,0 unfinished.** Preserve R43 and
all earlier used/abandoned evidence. M1 remains unaccepted; M2–M6 paused.

## What changed from R42

Corrected fixture phase/scope guards allowed actual ordinary unload and reload.
The cargo-only and stationary body/cargo cases confirmed unload at ticks23/27,
completed quiet at42/46 and timed out during reload at242/246. Flight confirmed
actual unload at21, quiet through40, first observed frame at41 and timed out240.
Its saved frame payload still had upward native velocity about18.73m/s and first
observed reload about16.85m/s. These are not exact first-integration claims.

## Failure boundary

Reconstructed actors reported initialization WAIT with `UNRESOLVED` status and
reason `current-actor-point-unknown`. The flight natural save/read witnesses
retained this unresolved marker, not a usable authoritative FRAME anchor. Thus
the initialization barrier did not complete: alive/pending cargo is not proof
of successful restored tracking or gameplay continuity.

Flight observer terminal counts were SAVE/READ/JOIN1 each and199 post-JOIN
observations per jug, two prelaunch below-threshold impact candidates each,
zero spills. There were612 events with no overflow or field-capture loss. This
does not repeat R41's fall/spill path; it exposes save-time ownership capture as
the next failure boundary. No final cargo/body identity/content/reconnect proof
was completed. No automatic retry or relaxed pending/UNKNOWN policy is justified.

Live gameplay queries correctly require exact live frame/residency and remain
strict. Investigation must establish whether ordinary frame teardown precedes
entity serialization and whether the normal unload snapshot can preserve the
current native association for persistence. Historical render/last-safe metadata
is not an authority fallback; neither nearby-frame discovery nor post-gameplay
repositioning is permitted.

Independent receipt/hash/log review verified these counts and bounded observer
coverage. All three reached effective substeps2 preparation/release witnesses.
Flight stimulus at18 measured upward displacement0.9848m and native19.36m/s.
Natural SAVE events7–9 show entity `removed=false`, no entity removal reason;
the exact native owner state at that callback was not captured.

Stopping and dimensions-saved markers are present. No repeated writable-book/
lectern-save error or crash marker was found. Fresh-directory missing
`server.properties` and three Sable unknown `create:flywheel` tag errors remain
separate startup findings; this is not an error-free run or a diagnosis of R26.
Production/default remains2; this is not six-step adoption, full regression,
process restart or connected-client proof.

## Reviewed correction for a fresh diagnostic

`FrameAnchorCaptureService` separates save-time ownership from gameplay residency.
LIVE capture uses the exact current owner and authoritative provider pose without
FULL storage requirements. UNLOADED capture requires that same retained owner
and an explicit public-SDK removal witness; it copies `logicalPose`, matching
Sable serialization. Destructive removal invalidates proof; holding-entry
presence and last-owner/render/safe-point metadata cannot qualify. Unknown
ownership still saves UNRESOLVED; no gameplay resolver or hydration policy was
relaxed. Both actor serializers reuse `FrameAnchorState`; early shared
initialization installs the observer before the WORLD fast path. Level close
clears and deactivates it (SDK has no public observer-removal API).

Compilation, focused JVM **46/46** (no skips/failures/errors), strict preparer
Python **14/14**, scoped diff checks and independent source/architecture/plan/
corrected-test review passed. Negative tests were corrected to use valid FRAME
controls before varying ownership/removal facts. No fixture assertions, motion,
velocity, gravity, rotation, jug thresholds, Mind/UUID/token/content ownership or
prototype values changed. See `ACTOR-FRAME-ANCHOR-RESTORATION.md` for source
correspondence and invariants. R44 must use its fresh target; the observed native
capture branch, save ordering and full reconstructed continuity remain unverified.

## Bound evidence

- Candidate `shuttle-feedback08-frame-anchor-20261016-r43`.
- Snapshot `7fd9fb57a73afdc0dd693cc724cd31e55b1c4fe8cbb96085c8a6edbed7824089`.
- Frozen plan `GATE-PLAN-08-FRAME-ANCHOR-R43.json`.
- Reports root `build/agent-workflow/shuttle-relocation-sprint/reports/`.
- Receipt `shuttle-feedback08-frame-anchor-20261016-r43-gate-validation.json`,
  SHA-256 `10e4f774baa82241448054092622d8c185370988c7ad4fd4dbe3470789709089`.
- Raw `feedback08-frame-anchor-r43-native.log`,683,691 bytes,
  SHA-256 `baf2dab572384854471b30b98042a428dbaa7e46f7dcb540d6ac80d5ab72b04a`.
- Raw unload1276/1282/1352; quiet1286/1293/1356; pending waits1290 onward;
  failures1316/1324/1379; flight terminal1383; stop/saved1394/1407.
