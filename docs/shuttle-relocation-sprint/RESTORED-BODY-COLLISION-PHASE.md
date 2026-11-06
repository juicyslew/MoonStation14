# R47 first-step collision: bounded offline reconstruction

2026-10-20 update: the owner retired the legacy in-flight jobs/hold and directed
new validation. The quarantine references below describe the earlier block;
they do not establish a current hold. R48 still has no technical verdict. New
coordinator intake requires a completed feature/milestone, not diagnostic-only
submission; finish the reconstruction correction before submitting.

This explains numerical relationships in one recorded native movement attempt.
It does **not** establish correct hydration timing, a Sable fault, later drift,
whole-fixture continuity or a movement-policy correction. R48 produced no
technical verdict and remains quarantined; see
`POST-RESTORE-MOTION-R48-OPERATIONAL-BLOCK.md`.

## Bound input and reusable types

Source: `feedback08-frame-composition-r47-native.log`, line1376, under
`build/agent-workflow/shuttle-relocation-sprint/reports/`;371,128 bytes, SHA-256
`1fc98215d016f9215f9ad64e597c0e7b9310c9c732213b7d21804e8527e4b24b`.
The preceding COMMIT and warning describe the same body/native attempt, not
several movement attempts. Use the full matrix/geometry values, not rounded
`Pose3d.toString()` positions. The trace's substep4/10 is an entity-collision
subdivision, not a change from the reached effective physics setting2.

Relevant runtime paths:

- `FrameAnchor.hydrate` → `LoadedFrameRayQuery.projectActorPoint`: one-time
  projection through the server provider pose, otherwise logical pose.
- `GroundedHarnessWorldStep` → `SableOwnedBodyMotion.move`: tracked-character
  body policy with stock native collision interval. The scope wraps native
  `Entity.move`; inherited motion is applied separately afterward.
- `NativeCollisionGeometry`, `NativeFrameInterval.PoseValue` and
  `OwnedNativeMoveDiagnostic`: existing immutable captured geometry/matrix and
  diagnostic representation. No new collision solver is needed for this check.

## Recorded relationships

Let initial feetY `w=303.76533056589795`, height
`h=1.7999999523162842`, full requested ownY `r=-.07683200000000001`,
inheritedY `c=.8276672363281818`, collision fraction `f=4/10`.

1. **Collision-box advancement.** Recorded centerY is304.96566463658735:

   `w + h/2 + f*(c+r) = 304.96566463658735`.

   Thus the collision box includes the fractional own **and carrier** advance;
   it is not merely the original physical center `w+h/2`. Omitting the carrier
   misses `.33106689453127m`.

2. **Frame samples.** The captured collision matrix projects storage
   `(20481034.5,129,20481034.5)` to worldY303.26873022410103. The logged latest
   matrix projects it to303.76533056589795. Their Y translation difference is
   `.4966003417969205 = (1-f)*c`. The latter also matches the warning's current
   actor-point projection. This does not log the provider matrix at the earlier
   instant of hydration.

3. **Selected ceiling overlap.** Transforming all eight corners of selected
   storage bounds `[20481034,131,20481034]..[20481035,132,20481035]` through the
   captured matrix gives enclosing worldY range
   `[305.2687290812004,306.2687313669951]`. The yaw-only actor box has topY
   `305.8656646127455`, giving vertical overlap `.5969355315451139`.
   Recorded MTV responseY is `-.5969350743790778`, within about4.6e-7 of that
   enclosing-AABB calculation. The calculation is **not** a native OBB-solver
   reimplementation or an exact MTV proof. Under the logged latest matrix the
   original body's head clearance is about `.19999890478305815m`.

4. **Full-move arithmetic.** The observed full own delta is

   `r + responseY = -.6737670743790778`.

   Adding `c` gives `.15390016194910405`, matching final-minus-initial worldY.
   The callback's `collisionMotionBeforeY=-.0307328` is only fractional motion
   at that callback; adding it to the response yields `-.6276678743790778`,
   **not** the full measured own displacement. An earlier research report made
   this addition incorrectly; do not propagate it.

## Interpretation and remaining evidence

These relationships are consistent with current-pose placement entering a
collision interval whose body prediction already advances by carrier motion.
They explain why a small original head clearance can coexist with the recorded
large ceiling response. Different interval pose samples are normal by themselves;
the correct physical entry phase for restored actors remains to be established.
This is not proof of two actual carrier applications, nor permission to reset
native state, skip a move, use a previous-pose placement or add recovery motion.

The first response occurs before the high-level motor rejects its bounded-own
result; physical position is not rolled back. Later successful/rejected movement
attempts are still needed to explain final current localY129.545472. R48's bounded
witness implementation was intended to collect that missing sequence, not to
change the `.12` oracle or repair the body. A fresh native diagnostic requires
explicit verified quarantine clearance and a fresh plan/candidate/target.

Offline test-only work extends `NativeCollisionGeometryTest` using the recorded
immutable snapshot, JOML transforms and `MovementVector` arithmetic. It guards
these reconstruction relationships and negative controls, not live timing or
native continuity; focused validation is separate from R48 gate execution.

## Offline validation

The existing six geometry tests remain alongside three recorded-evidence cases.
Focused `test --tests '*NativeCollisionGeometryTest'` passed **9/9**, zero skips,
failures or errors; parent verified the resulting XML. Scoped diff check and
independent math/claim-bound and final test-source reviews passed. The reviewer's
XML-location caveat is superseded by the parent's direct XML read, not a rerun.
No production source changed.
The first evidence-test attempt failed due to test input mutation and incorrect
comparison formulas, not a runtime defect. Its XML/HTML was preserved under
`reports/r47-offline-geometry-failure/` before the corrected focused run.

Before the new focused invocation, all nine prior R48 author XMLs totaling70
tests were copied with matching hashes into `reports/r48-author-jvm70/`.
These are preserved author checks, **not** an R48 native receipt or technical
verdict. Neither offline result clears quarantine or authorizes a gate retry.
