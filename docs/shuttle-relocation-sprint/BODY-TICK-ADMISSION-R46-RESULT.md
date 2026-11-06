# R46 — stationary continuity passes; flight saves the wrong local point

**Filtered3/895 selected,3 completed,2 passed,1 failed,0 unfinished.** Compile
and preparation passed; effectiveSDK2 was reached. Preserve R46 and all earlier
used/abandoned evidence. No readiness/M1 acceptance; M2–M6 stay paused.

## Native outcomes

- Stationary cargo reconstruction passed at fixture48 (unload25, quiet44).
- Stationary offline body/cargo passed at56. Body saved FRAME/unloaded, initialized
  against its exact frame and completed normal reconnect at53, including the
  strict UUID/Mind/hands/held-token/stack/ACTIVE/current-point/grounding oracle.
  Actual unload28 and quiet47 were reached.
- Flight body/cargo failed at92, only after strict cargo checks and normal body
  reconnect attempt. Stimulus19 measured rise0.984771728515625m/native upward
  speed about19.36m/s; OFFLINE20, unload22, quiet41, first observed reload42.

## Flight body boundary

Body `88a0bd3e-89c3-4580-9fbd-900e0ffc08a6`, frame
`d74a47dd-247f-451b-ab80-c106c564d085`:

- SDK callback at level gameTime127: exact native owner, registered/not removed,
  physical chunk FULL but not entity-ticking. WorldY **301.98477716305854**.
- Natural save129: **exactly unchanged world position**, own velocity0 and
  grounded; native owner reference retained, now removed/unregistered; physical
  chunk absent and not entity-ticking. No trace error/truncation.
- FRAME/unloaded serialization now succeeds, but the saved localY is
  **128.04711830259092**, about0.95288m below the fixture's accepted near129 point.
- Initialization faithfully used that saved point, worldY302.81244886848776,
  while the jugs initialized near303.76533. It did not invent a recovery point.
- Final body was restored, alive, grounded, normal physics/gravity, exact native
  frame/tracking; listed binding/Mind/ACTIVE/hands/held-token/stack checks true.
  Local distance **1.4983535745554577m** still exceeds **0.12m**. No full flight
  continuity or health-value claim follows from these partial facts.

Do not copy R45's bodyY302.937656 into R46. The body's unchanged world point was
inverted through a newer frozen frame pose, so wrong local composition already
existed at save. Subsequent displacement is not evidence that hydration ignored
the tag. The admission fix prevented R45's observed callback-to-save motion and
owner loss in this run, without identifying the exact earlier clearing call.

All nine cargo roots saved FRAME/unloaded and initialized. Flight observer:
SAVE/READ/JOIN1 each,50 post-JOIN samples,3 impact candidates,0 spills per jug;
168 events, bounded coverage complete without overflow/capture loss.

## R47 design and author implementation

Reuse `SableOwnedBodyMotion.MoveResult.afterPoint`, sampled after actual native
movement/carry/bump handling, to retain current physical frame composition in
the existing actor-owned `FrameAnchorState`. Commit only fresh, valid,
server-side exact-owner results. Both living and incapacitated body consumers
already use this shared seam; no extra physics callback or move is needed.

At serialization require unchanged exact world point, level and native owner
identity, plus live registration or that exact owner's ordinary-UNLOADED proof.
Serialize the committed local point rather than reinverting against a newer
pose. Missing/mismatched evidence stays UNKNOWN; WORLD departure clears it.
No-fresh moves cannot refresh from a newer pose. Reads invalidate prior commits.
This is actual completed physical composition, not a last-safe point, and must
not drive ongoing queries/movement/rendering or post-gameplay placement. Existing
non-committed cargo capture and one-time initialization remain unchanged.

The actor-owned state, shared movement-completion adapter and committed-save
qualification are now implemented. The phase guard permits initialized actors
only; a compiled call-path test checks the actual pending-true skip branch and
observer-before-return ordering. Both inverse/default and committed capture use
one ownership qualifier; committed LIVE needs allocation/identity, not a newer
pose query. Positive WORLD save and read clear stale composition. Bounded commit/
callback/save witnesses include current sequence and local/world points.

Compilation and nine focused JVM classes passed **62/62**, no skips/failures/
errors (state19, capture10, native wiring1, trace4, fixture phase4, watch7,
payload5, history1, body resolver11); parent verified all nine XMLs. Independent
final source/negative-control/consumer/plan review passed. Its preflight caveat
referenced the earlier broad181/895 selection, superseded by the exact combined
three-tag offline preflight **3/895**. Strict preparer Python14/14 and scoped
diff checks passed. At submission R47 was absent and unprepared. At that point a
qualifying pre-unload commit and actual flight continuity remained unproven. See
`FRAME-COMPOSITION-R47-RESULT.md` for the subsequent native result. Do not weaken
the oracle, add staged actor unload ownership, infer from last UUID/render
metadata or undo tick admission.

Stopping/saved markers appeared; no crash, repeated writable-book/lectern-save
failure or diagnostic error/truncation was found. Missing fresh `server.properties`
and unknown `create:flywheel` startup tags remain separate findings; no R26 diagnosis.

## Bound evidence

- Candidate `shuttle-feedback08-body-tick-admission-20261016-r46`.
- Snapshot `5db1cf6b752de1faf337e0bf102045f34b7a7ff85503ce7831acad9d981c1e86`.
- Frozen plan `GATE-PLAN-08-BODY-TICK-ADMISSION-R46.json`.
- Reports root `build/agent-workflow/shuttle-relocation-sprint/reports/`.
- Receipt `shuttle-feedback08-body-tick-admission-20261016-r46-gate-validation.json`,
  SHA-256 `bd3f6be8419d2e0f09437b43e2d3060a4419461b006293e7eb5c68c1085958c3`.
- Raw `feedback08-body-tick-admission-r46-native.log`,356,519 bytes,
  SHA-256 `62fffd51a897404dc4391153e963e238c9f52273777926b59045b2c39f992fd8`.
- Raw stationary reconnect1322/pass1326; flight callback1351/save1352–1356,
  initialize1367–1370, final facts1383/failure1384–1385, observer1388,
  stopping1397/saved1411. Independent receipt/hash/log review verified the binding
  and2-pass/1-fail boundary; architecture review is not implementation acceptance.
