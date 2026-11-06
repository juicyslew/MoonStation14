# Idle support probe on a rotated native floor

## Evidence and scope

R34's negative-X cabin failure is a native **local-floor** response, not a
verified side-face collision merely because its selected block is a steel wall
block. Its floor is made from full blocks of that type. All thirteen captured
geometry rows select storage bounds Y128–129. Meaningful MTVs align with each
row's posed local +Y; that axis crosses world-Y dominance during the entity
collision sweep. World-axis aggregate `verticalCollision` is therefore not a
reliable local-face classification for every row.

The immutable bounded fixture is
`src/test/resources/com/juicyslew/moonstation14/ms14/shuttle/sable/r34-native-correction-trace.tsv`.
Its source hash is R34 raw
`4d69e31bcb8df9885b45289bfb92bd42b963cac61ca354764e854195742546d0`.
Focused `NativeSideCollisionWitnessTest` passed **4/4**, replaying all thirteen
captured geometries through the existing SAT witness within unchanged `1e-6`
and reconciling native motion to request plus recorded responses. Near-zero
iteration rows still need accounting but cannot independently establish a normal.
This proves replay identity/accounting, not movement admissibility by itself.

## Probe provenance is required

`CharacterMovementMotor` adds a small downward request solely to refresh real
native floor support when grounded with zero vertical velocity. It is not an
extra gravity force. A matching numerical request (`Y=-0.001`) alone does not
prove it was a probe.

The narrow repair conveys an **attested idle support probe** from that actual
kernel branch: pre-probe movement is exactly zero, no effective horizontal wish,
no external flight and no nonzero external/constraint impulse. Other cases must
remain on their existing collision paths, including real falling/jumping,
horizontal movement and tether/launch motion.

For this proven idle case, bounded own displacement is zero. Projecting the
artificial probe into tangent movement would fabricate own-walk/posture/pry
travel. Real native position correction and native carrier motion must remain
separate and each physically applied only once. The existing support-floor
position-correction certificate can represent this; no general 3D voluntary
movement model or new gravity behavior is required.

## Acceptance boundaries for the repair

- Reuse the shared resolver/kernel, server adapter and client prediction path;
  legacy uncontextual calls do not infer probe status. No networking wire or
  prototype/component values change.
- Reuse `NativeSideContactProof`, `NativeSideCollisionWitness`, captured
  `NativeCollisionGeometry` and loaded selected-state validation. Do not create
  another SAT solver or parallel response-accounting implementation.
- Admit only fresh native supported contact on the exact live frame, complete
  ordered geometry/response evidence, positive posed-local-up floor normals,
  one coherent local top plane and independently matched native/measured motion.
  Reject side/ceiling/mixed-plane, unknown/incomplete, wrong-frame/state, altered
  response and unproven probe cases. Residual rows must still replay and sum.
- Retain the existing displacement tolerance, side/axis-floor routes, accepted
  R10 exterior-wall fix, strict own-walk `.3` threshold and UNKNOWN/full-power
  pry rejection.
- Default-path geometry must come from the existing selected-contact callback,
  not a later pose. Extend observation only where angled native response needs
  it; no new hook, native/engine patch, rotation freeze, cap or recovery teleport.

## Implemented repair and author evidence

The motor now attests that exact idle-probe context through the shared resolver
factory to both server movement and client prediction. Legacy calls attest false.
`NativeSideContactProof.validateIdleSupportProbe` reuses shared row-order and SAT
replay helpers; captured poses may evolve between substeps but must agree within
one substep. Every row must share the local top plane, including residual MTVs.
Meaningful normals must align with captured local +Y and face upward in world Y.
`CurrentFrameCollisionScope.shouldCaptureNativeCollisionGeometry` is the single
eligibility policy used by both the existing mixin callback and completeness check.

Focused JVM validation passed **78/78**: motor52, SAT witness4, existing side
proof18 and captured idle-floor proof4, with zero failures/errors/skips. The
captured tests cover real rotating poses, same-substep inconsistent poses with
SAT preserved, and residual-row mismatched floor bounds with zero SAT separation.
XML is under `build/test-results/test/TEST-com.juicyslew.moonstation14.*.xml`.
An earlier partial implementation produced74/76 passes; subsequent compile-only
attempts exposed test/diagnostic vector/import/accessor mistakes. Those are not
current validation. No native fixture was rerun to obtain these JVM results.

Independent source/reuse review passed after correcting the per-substep pose
check, all-row plane check, actual mixin eligibility and duplicate SAT replay.
Trailing read-only `MoveResult.idleSupportProbeAttested` and
`idleSupportProbeCertified` fields bind live evidence to the actual forwarded
context and successful new certificate branch, not request magnitude or an old
axis-floor certificate. Added diagnostics report both and the first certified
trace/count. Final `compileGameTestJava` passed after this metadata-only addition;
the78-test run preceded that addition. Preparation tooling tests passed14/14.

R35 (`GATE-PLAN-08-SUBSTEPS6-R35.json`) is diagnostic-only: the original two
default-observation strong impulses and four accepted exterior-wall faces in a
fresh isolated six-step directory. Require explicit certification evidence for
the repaired route; a zero certification counter cannot establish its coverage.

R35 subsequently passed **6/894 selected,6/6 complete**, with clean saves/stop.
The default negative-X path first attested and certified the new route at tick57
and completed with3 certified ticks; the live trace had16 geometry rows (R34's
13-row unit fixture is not a mandatory iteration count). See
`SUBSTEPS6-R35-RESULT.md` for bound hashes and evidence. The original positive-Y
path and all four accepted exterior-wall faces passed. Combined-schedule
body/cargo/lifecycle interactions still require the fresh R36 diagnostic before
cost work.

This is a Minecraft/Sable adapter bugfix, not a claim of exact SS14 Robust solver
parity. Broader native interactions and connected prediction remain pending. Do not infer
M1 acceptance, six-step production adoption or server capacity from JVM evidence.
