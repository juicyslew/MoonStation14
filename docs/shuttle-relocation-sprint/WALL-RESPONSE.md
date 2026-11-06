# Side-contact response — evidence and pending correction

**M1 remains blocked.** Geometry-46 passed 6/9 selected of 868; WEST/NORTH/SOUTH
wall probes rejected movement. No connected wall fix or current full-suite pass is
claimed. Reference physics remains official Sable 2.0.6, stock single precision.

## Established boundary

- WEST/SOUTH fail the existing native-versus-final Y matching gate. Pinned Sable
  `EntityMixin.sable$collideRedirect` calls vanilla `this.collide(info.motion)`
  after native sublevel collision, explaining their subsequent terrain floor clip.
- NORTH reaches the first-plane projection check. Its native normal correction
  exceeds the requested inward component. SOUTH additionally receives an outward
  corner correction on a second plane; first-plane response alone is insufficient.
- The selected-contact callback in `SubLevelEntityCollision.collide` precedes
  step handling and addition of the selected MTV. It retains `maxAABB`, the actual
  winning shape box. Actor OBB, selected storage box and exact pose are copied by
  `NativeCollisionGeometry`, not recomputed from a later shape or shared scratch.
- Diagnostic observation remains fixture-opt-in. Invalid snapshots are omitted;
  finite/null/alias guards passed six focused `NativeCollisionGeometryTest` cases
  after geometry-46. Observations alone are not movement authority.

Pinned native source under `common/src/main/java/dev/ryanhcode/sable/`:
`sublevel/entity_collision/SubLevelEntityCollision.java`,
`api/math/OrientedBoundingBox3d.java`, and
`mixin/entity/entity_sublevel_collision/EntityMixin.java`, revision
`ad8b4d377741fbd923f4d863b2cf9e462daff9a6`.

## Offline witnesses

`build/shuttle-feedback-05-geometry-46.log` contains three first-failure ERROR
records. Each repeats its trace in a second field; count only
`nativeCorrectionTrace` before `yawSamplesComplete`. Unique rows: WEST **12**,
NORTH **10**, SOUTH **4**, not 24/20/8.

The derived `-overlaps.txt` and `-sat-witness.txt` use all recorded box axes and
nonzero cross axes. Every row has positive computed minimum overlap; recorded MTV
length matches it within the existing `1e-6` bound. Maximum projected residuals
are WEST `2.04e-9`, NORTH `3.43e-9`, SOUTH `2.88e-9`. Tiny later-iteration contacts
are numerical residues, not evidence of a new physical tolerance.

The corrected `-own-budget.txt` model verifies every row's cumulative request /
response budget and the final native sum. It advances **all** remaining substeps
after the last contact. Inward own clipping is capped by the observed correction
and remaining inward wish; additional correction is position-only XZ. The final
bounded own vectors pass the **unchanged** existing first-plane evidence algebra
for all three faces. SOUTH's additional geometry X is `-0.00205026727`.

These offline results are not a live consumer or an accepted correction.
They do not establish connected artifact identity, camera behavior, prediction,
or broad rotation/contact coverage.

## Current implementation direction — not validated

Reuse the existing native trace, loaded-state checks, first-plane certificate and
floor position-correction consumer. Generalize its position-only payload with
distinct supported-floor and side factories; keep one motor consumer. A side
proof must reconstruct actual captured geometry using SDK SAT with private
scratch, verify complete request/response accounting, and bind the same exact
entity/frame/substep/iteration tuple. Do not reconstruct a body at its later pose.
The verified request budget excludes unaccounted SDK velocity inputs; inherited
frame-center motion remains represented in the actual contact OBB and separately
applied carrier, not voluntary movement.

Only bounded own movement may reach velocity/animation consumers. Side correction
is XZ-only; final Y remains subject to ordinary motor bounds. Existing first-plane
evidence must validate the resulting own vector, or the new proof fails closed.
Supported-floor grounding requirements remain unchanged. No tolerance widening,
fake grounding, speed cap, recurrent teleport or parallel SAT solver is authorized.

Server/client prediction share the native bridge. Proof capture is distinct from
debug observation. Contact snapshot costs are bounded but not capacity-certified.
Expensive validation/readiness now belong to the central integration lead through
the shared serialized gate. Source implementation is now present. Independent
review's aggregate-budget finding is
source-corrected: use a whole-vector residual rather than admitting a diagonal
residual of up to `sqrt(3) * 1e-6`. Side-48 compiled but passed only **81/83
filtered unit tests** because of two invalid positive fixtures; those premises
are corrected. The witness class passed its subsequent focused run; budget-49
was blocked by a shared test-output lock. Serial side-50 then passed **84/84
filtered unit tests** across eight classes, with source/native-fixture compilation
and independent source review passing. Central full/native validation is pending;
no connected correction is claimed. Central r2 subsequently passed **3,233/3,233
full JUnit** and **33/868 filtered native integration, all 33 passed**, including
the four exterior faces. Its unfiltered run crashed during Sable autosave before
an aggregate; the lead persisted `needs_changes`. The side-contact headless gate
is positive, but full native readiness and M1 acceptance remain blocked.
