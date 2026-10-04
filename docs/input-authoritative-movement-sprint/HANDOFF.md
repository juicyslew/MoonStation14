# Experimental input-authoritative movement

## STATUS

Reconstructed 2026-10-03. Owner closed bounded experiment on 2026-09-24. experimentalVerticalSliceMovement remains default-disabled/server-startup gated; default path is vanilla. Closure is not permission to resume or full movement acceptance.

## ARCHITECTURE

Shared human motor runs authoritative server input and client prediction. Sequenced Begin/Ack/Commit and snapshot/ack protocol handles authority. Prediction resolves local collision; correction preserves bounded grounded displacement arithmetically, not complete pending-input replay. Later possessed-body motor routing needs its own proof.

## IMPORTANT FILES

`closure.md`, `architecture/authority-and-prediction.md`, `single-owner-handoff-gates.md`; `src/main/java/com/juicyslew/moonstation14/ms14/movement/` controllers/networking and CharacterMovementMotor.

## SS14 CORRESPONDENCE

Recorded source-derived projected-wish acceleration, human friction 20, and space-lube factor 0.05 for friction/acceleration. Subjective feel did not justify changing those factors. Local authority/correction/collision adaptations are not full SS14 prediction parity.

## DECISIONS / WHY

Keep opt-in ownership until handoff proof. Slip contact uses accepted endpoint AABB, not swept path; very fast crossings can miss. Do not silently activate the experiment through unrelated feature work.

## KNOWN GAPS

No proof latest reported connected session used this motor. Authority handoff, teleport/dimension/respawn, adverse network, reconnect, full replay, AI adaptation, three-puddle bound, HUD/audio/prone and scale lacked certification. Turn-animation/jump-particle reports were anecdotal/deferred.

## VALIDATION

Historical pre-commit test/build and 116/116 GameTests passed; no post-commit rerun claimed. Later owner playtest had no logs/video proving experimental authority. Reconstruction performs no tests.

## NEXT WORK

Any follow-up needs authorization and starts with single-owner gates plus newer body-control/lifecycle handoffs. First prove which runtime actually owns the tested body.
