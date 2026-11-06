# Wall contact: Sable step-up probe position

Sequence 2920 in `build/shuttle-owner-feedback-06/debug.log` proves an owned
character wall contact can receive roughly `+0.25` Y from native collision while
requesting `-0.001`: substep 10 has a horizontal MTV followed by four iterations
whose response Y is approximately `0.0625` each. The preceding vertical rows are
only about `0.0001`; they do not account for the accepted rise.

The pinned Sable 2.0.6 source (`ad8b4d377741fbd923f4d863b2cf9e462daff9a6`),
`common/src/main/java/dev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision.java`,
places the solver OBB at `entityBoundsCenter + collisionMotion` before its SAT
iterations. A selected nonvertical collision calls `tryStepUp`. Its candidate
center was instead based on `entityBoundsCenter`, then moved inward by `2/16`
along the normalized MTV. `hasCollision` mutates the OBB position while probing;
that mutable OBB cannot be used as a stable base. When prior collision motion
already advanced the actor toward the wall, the unadvanced probe can test a
different space, find a floor/step, and add 1/16 increments. Four later selected
iterations explain the observed quarter-block rise.
The exact pinned lines are `SubLevelEntityCollision.java:192,401-412,435-436,
509-594` (`boundsCenter` candidate at 539, second unrelated `set` at 542,
response increment at 553, OBB mutation at 561).

The local adaptation leaves Sable's generic behavior unchanged. Only the existing
exact owned-character scope offsets the copied step-probe center by the current
accumulated `collisionMotion`; stock movement, step heights/increments, SAT,
floor logic, and `1e-6` motor bounds are unchanged. Furnished-cabin probes are
separately tagged `shuttle-step-probe` and `shuttle-owner-regression`: four cardinal
wall faces are tested with diagonal inward/tangent requests for downward, upward,
and jump movement on an identity-pose frame built through the normal
`allocateAndAuthor` path. Two additional cases acquire yaw through native angular
velocity after identity-pose construction, brake it natively, and require exact-frame
oblique side rows; the downward WEST case also requires same-move floor rows. Each starts with fresh
floor contact proven by `awaitCabinFloorContact`
(no forced on-ground flag), runs through `GroundedHarnessWorldStep` and its shared
native movement resolver, requires an actual selected steel wall and verifies
`responseY - appliedY` contains no extra step lift; downward cases also retain real
floor support. `interiorCabinCeilingRemainsHeadCollisionNotStepSupport` checks an
actual upward head hit remains vertical and is not below support. Downward cases
assert live floor support after their wall move; the shared supported-floor
counterpart is `boundHumanStandsAndWalksRelativeToTranslatingNativeDeck`.
`ownedNativeStepOntoAuthoredHalfSlabRemainsEnabled` is a Sable-native positive
control: it first acquires actual deck support, then uses the owned-character
native bridge against an authored half slab and checks a fresh below-support
collision on that exact frame and deck floor (not a stale `onGround` flag), selected
obstacle shape, real step rise, final foot height and head clearance. This directly tests the SDK/native
step path; it does not claim the motor currently certifies this step through its
bounded voluntary-displacement contract. The existing ordinary control
`MovementCollisionGameTests.realVillagerCollisionResolutionBoundsMotorState`
separately proves the half-slab step through the vanilla collision motor. Fixtures
remain pending execution by the central serialized gate and do not establish
four-face connected wall/glide acceptance.

## Gate r1 fixture correction

Central r1 `build/shuttle-feedback-06-gate-r1-integration.log:1380-1389` showed
all four wall fixtures failing before body creation: the frame fell with about
`-36.92 Y` velocity and rotated near 180 degrees. Initial nonidentity fixture
placement and its mass-pivot interaction remain unresolved; the earlier tilted
setup failed to settle and is removed rather than retried with guessed geometry.
These wall regressions do not require a tilted normal: an identity-pose supported
furnished hull exercises the targeted upward-floor plus horizontal-wall collision.
They now use normal production-shaped `allocateAndAuthor` placement without a
rotation callback or direct teleport, retain the raised initial anchor
`(sceneOrigin.x+12.5, sceneOrigin.y+1, sceneOrigin.z+12.5)`, and verify transformed
floor corners plus loaded, fixture-owned stone coverage before native settling.
No grounding, freeze, gravity change, or widened bound is used. The source-level
fixture correction awaits central reproduction; r1 never reached a wall assertion.
Minor-angular and sustained-tilt connected-flight coverage remain a separate,
unresolved future requirement and are not claimed here.

R3 root cause for the initial false failures was phase ordering, not collision
acceptance: `GroundedHarnessWorldStep.stepCore` returns cached current state without
calling its observer when the owner's physical step budget was already consumed
for that server tick. `awaitCabinFloorContact` had attempted the probe after passive
physics in the same tick, so all four observed-move records were null without a
new request. The fixture now registers a one-shot, bounded `ServerTickEvent.Pre`
listener after support is established, waits for a tick count newer than registration,
checks the owner budget is unused, then makes the ordinary owned movement call.
The attempt must mark the budget consumed and produce a nonnull observed native
move; timeout/cleanup does not reset ownership or change production behavior.
The high-volume native diagnostic and full geometry trace are emitted only to the
server log under a per-actor tag; GameTest failure text stays bounded and includes
failure kind, face, actor UUID, request, measured displacement, frame UUID, and log
tag, avoiding oversized reporter payloads without weakening assertions.
R1's falling/rotation failure preceded body creation; the R3 phase failure preceded
any movement request. Neither gate establishes that the production collision
correction passed a wall-contact assertion. Initial nonidentity fixture placement
remains unresolved; no pitch/roll coverage is claimed.

## R4 native mixed-contact evidence and limits

Central r4 `build/shuttle-feedback-06-gate-r4-integration.log:1347-1371` captured
actual movement requests for the wall fixtures. WEST identity and WEST yaw moves
selected genuine positive-Y floor MTVs and side MTVs in the same native solve; the
old pure-floor admission rejected the horizontal request, while the old pure-side
certificate rejected vertical trace rows. The actual measured own Y residues were
`2.34526567e-5` and `1.57495523e-5` above the requested downward movement, with
native grounded/below flags. The North active-yaw trace had side rows only and its
normal changed during the solver interval as frame yaw advanced from about `.009`
to `.015`; that is a moving-normal/first-plane limitation, not floor proof.

The bounded adapter now reuses `NativeSideContactProof` for a mixed case: all
vertical rows must satisfy the existing SDK floor applied-up redirect rule and the
fresh grounded/below-frame context; all side rows still require complete captured
OBB/SAT geometry and exact witness separation. Solver ordering and whole native
response sums include both row types. The side certificate normal comes from the
first verified side row, not the per-frame first-collision summary which may be
the floor. `ContactPositionCorrectionEvidence` has one additional mixed domain,
validated by the same existing motor position-correction consumer: true downward
own-Y clipping is preserved; positive floor residual is bounded to zero own Y and
reconciled as position-only geometry. Floor-only and side-only constructors
retain their prior rules. Floor geometry snapshots are not captured; existing
SDK floor flags/trace and current loaded block state establish floor rows. Side SAT
snapshots were already captured for tracked characters in production regardless
of fixture-only geometry observation, so no new runtime capture cost is added.
If own XZ equals requested XZ, the mixed correction factory retains the existing
side-domain no-clipping/no-contact allowance; this covers a proven native volume
push for a neutral horizontal request without inventing a velocity/contact plane.
Any actual horizontal own clipping still requires the exact side-plane certificate.
During own-response replay, each verified floor row clips the current downward
per-substep own-Y accumulation before later side rows are projected; the SDK's full
floor response remains separately accounted in the native geometry budget and is
not injected into the side-plane own-motion projection.

The rotated fixtures now apply native angular velocity after identity construction,
then apply its opposite and wait for natural stationary yaw before actor movement.
R4's active-rotation North failure specifically showed normals changing across
substeps; active-angular collision contact remains unproven and is deferred to
future flight coverage. The code retains the existing first-plane witness and
does not relax normal epsilon, motor bounds, or upward-step rules.

R5 `build/shuttle-feedback-06-gate-r5-integration.log:1364-1377` narrowed the
remaining adapter boundary. WEST yaw requested `(-.1526741,-.02,-.0332369)` and
measured `(-.05113169,+8.29788e-7,-.03634759)`; existing `NativeCollisionSlop`
normalizes that small positive Y to zero, so mixed proof now receives raw measured
own motion and carries the sub-slop positive residual only as position correction.
SOUTH identity measured `(-.03789569,-1.63075e-5,+.04999141)` for request
`(-.03789467,-.02,+.15158515)`: its supported negative own-Y clip is retained in
bounded movement, not replaced by positive-floor position correction. Same-frame
floor/side trace response budgets remain required in both cases. NORTH yaw's SAT MTV
had raw X `.000325` and Z `.010632`, while normalized side normal X was `.03063`;
the fixture had tested raw MTV components rather than the unit side normal. It now
tests the normalized normal and requires known steel side geometry above the exact
authored floor layer on the expected frame, excluding floor/ceiling-only rows.

R6 central GameTest reported 40/42 focused passes; the two remaining wall-fixture
failures timed out before gameplay because chunks were loaded but not yet entity-
ticking. The scene's existing bounded readiness predicate correctly requires full
entity ticking. Only these affected native wall/carrier fixtures now wait up to 100
ticks (the generic fixture readiness budget remains 40); the clipped-carrier test
deadline is 220 ticks so native support and assertions retain time after readiness.
No ticket policy, entity-ticking predicate, runtime behavior, or wall/contact
assertion was weakened. The extended wait is a bounded opportunity, not proof the
central timeout is resolved; next gate reproduction remains required.

R10b's only wall/step readiness failure was the authored half-slab positive-step
fixture: scene cells were loaded, but the requested level-31 chunk was not
entity-ticking within the old 40-tick bound, so no movement assertion ran. It now
uses the same strict 100-tick entity-ticking readiness budget as the native wall
fixtures, and its timeout is 220 ticks to leave room for the existing native floor
support wait and step/head-clearance assertions. Loaded-only fallback, ticket policy,
floor support, slab selection, height, headroom, and the native step behavior are
unchanged. R10 also reported 12 reused-world profile deferrals/first-join failures;
those do not establish a source regression. Central R11 should use fresh fixture
directories rather than mutate or remove those reused profiles. The step readiness
change remains unverified until that fresh-directory gate.
