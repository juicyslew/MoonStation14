# Impact penetration, cargo presentation and reconnect cargo loss

## Accepted boundary

The owner confirmed R10 fixes the connected exterior-wall jump issue. Interior
collision checks also appeared to work, though cabin space limited testing.
Do not reopen or weaken the accepted wall-correction proof to address the
separate failures below. M1 remains open; M2–M6 remain paused.

## Owner observations

- A normal cabin launched upward with a **40 m/s velocity nudge** sometimes
  lands partly inside terrain at an odd angle **without disconnecting**.
  An **80 m/s** upward nudge caused greater apparent terrain penetration.
  These are native velocity changes, not thruster settings. Exact impact speeds
  and authoritative versus rendered penetration have not yet been measured.
- Launches at **10 and 20** appeared to carry the owner normally. During a
  **20-nudge logout/rejoin** trial, the same living body and inventory remained
  aboard. This is a positive observation, not general persistence acceptance.
- In that reconnect trial, **two of three jugs fell through the floor and
  broke**. The owner waited a long time before rejoining and heard break audio
  on rejoin. Actual server break time versus delayed presentation/audio remains
  unestablished; do not assume the hull-impact failure explains this event.
- Cargo models still follow ship orientation while highlight/examine
  representations do not. Picking up and putting down cargo leaves the new
  model world-aligned rather than ship-aligned. R10 did not claim these fixes.

## Evidence

Stable read-only copies are in
`build/shuttle-owner-feedback-08/20261015T040337Z/`: `latest.log`, `debug.log`,
launcher logs and `manifest.json`. Source hashes, sizes and timestamps matched
before/after capture. Launch records identify agent-03 development classes,
not the exact class bytes loaded by either running process.

## Investigation boundaries and reuse

1. **Hull/terrain:** reproduce complete upward launch, descent, impact and
   settling through `ShuttleFixtureCommands`, `SableFixturePlot` and
   `ShuttleCabinFixture`. Reuse `SableFixtureScene` / `FixtureChunkTickets` for
   known-loaded terrain. Existing operating-envelope tests do not establish
   high-speed hull collision. Actor collision sampling is not rigid-body CCD.
   Establish pinned Sable/Rapier collider readiness, timestep and supported CCD
   behavior before changing engine policy. An overlapping world AABB alone is
   not evidence that the actual rotated hull intersects terrain.
2. **Cargo/rejoin:** trace server cargo ticking, frame/support, break/damage,
   residency and lifecycle transitions separately from client packet/audio
   arrival. Reuse falling-reconnect and strong-cargo fixture services; record
   all three cargo identities and the unchanged body/inventory identity.
3. **Cargo presentation/drop:** reuse `LooseFrameOrientation`,
   `LooseItemEntity`, `LooseItemRenderer`, `LoadedFrameRayQuery` and shared
   query/outline geometry. Resolve actual entity hit/query consumers and all
   hand placement/drop call paths before adding geometry or orientation state.
   Establish pinned SS14 held-item placement/drop transform semantics; generic
   parent-change preservation alone does not specify new-entity spawn rotation.

No cause is established yet for hull penetration or reconnect cargo loss.

## Pinned engine findings

Sable source at `ad8b4d377741fbd923f4d863b2cf9e462daff9a6` already enables
CCD for sublevel rigid bodies: `sable_rapier/src/main/rust/rapier/src/lib.rs`,
`createSubLevel`, builds `RigidBodyBuilder::dynamic().ccd_enabled(true)`.
Its integration parameters set `max_ccd_substeps: 3`; this is not the actor
collision-sampling cap or proof of the number of outer physics steps.
`RapierPhysicsPipeline.physicsTick(timeStep)` supplies native step duration.
The native `LevelCollider` is voxel-backed; Java section additions/removals and
block changes update native chunk/octree state. Inspect that availability as
well as geometry and timing; do not diagnose disabled CCD from launch speed.
Source inspection does not establish release binary/source build equivalence.

## Cargo consumer findings

`ExamineWorldSelector` intersects entity AABBs, `ExamineTargetResolver` computes
the authoritative nearest point on that AABB, and `ExamineClient.drawOutline`
renders it with positional interpolation only. These are the entity paths;
`FrameExamineGeometry` handles frame **blocks**, not loose entities.
`LooseItemRenderer` instead applies the entity orientation basis and local spin
about its floor pivot. Any fix must share coherent entity query geometry across
selection, outline and server revalidation, without rotating physical collision
as a side effect or claiming a physical-dimensions box is an exact model mesh.

New-stack spawns through `BodyHandItemTransfer.createTrustedDropEntity`,
`BodyLooseThrow.throwOwnedCommitted` and `NativeHeldItemTransfer` call
`LooseItemEntity.create`, whose orientation begins world-identity. Existing
loose-to-loose copying preserves orientation, but pickup stores an `ItemStack`,
not the destroyed loose entity's transform. Pinned SS14 hands drop the same
held entity; targeted drops retain its world rotation. Do not conflate that
with choosing a basis for a replacement loose entity in this 3D stack adapter.

Targeted follow-up confirmed that fixing examination alone is insufficient:
`BodyItemTargetProbe.nearestVisible` / `visibleCandidates` and its reach/LOS
helpers also use item AABBs. `BodyHandItemTransfer.pickupTransaction` re-probes
after staging, so selection and authoritative revalidation must share the new
query geometry. Examination's `currentBounds`, selection, response point,
`ExamineTargetResolver` and client outline form the other consumer group.
Keep physical collision AABBs unchanged; oriented query bounds are not an exact
model mesh. Extend existing orientation/frame-pose owners rather than adding a
second transform state. Client geometry uses render poses; server geometry uses
committed poses and existing loaded-only occlusion.

Replacement-spawn policy must be explicit for trusted hand drops, throws,
native held-slot drops and Creative conversion; static ChemMaster supplies
have no actor-frame context. Merely storing the old loose transform in a new
hand payload would introduce persistence/state requirements not yet justified
by the reported ship-alignment bug. Establish the upstream contract before
choosing the smallest frame-aware adaptation.

Pinned SS14 follow-up verified the game checkout at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Jug composes
`DrinkBase → BaseItem`, `DrinkBaseMaterialStrongPlastic →
DrinkBaseMaterialPlastic`, and `DrinkVisualsAll`; it supplies Normal item size,
jug sprite and 200-volume solution, with no jug-specific fixture/rotation
override (`chemical-containers.yml:2–25`, `drinks_base.yml:4–49`,
`drinks_base_materials.yml:81–99,120–144` under the upstream prototype tree).
This is not authorization to change the port's existing jug capacity/material.
`SharedHandsSystem.Pickup.cs:93–111,280–300` inserts the same entity into a hand;
`SharedHandsSystem.Drop.cs:141–168,221–254` removes it, and targeted placement
explicitly retains `GetWorldPositionRotation`'s rotation. Throwing also uses
that entity (`ThrowingSystem.cs:53–73,87–111,140–160`). New ship-aligned
replacement spawns would be a documented 3D adapter choice, not this same-entity
upstream behavior. This follow-up did not locate RobustToolbox at its expected
path, so it adds no engine-level container-transform or fixture-query proof;
the earlier verified Robust pin is not a substitute for that missing audit.

The inherited fixture is now verified in SS14 `base_item.yml:3–44`:
`BaseItem` supplies dynamic physics, `fixedRotation: false`, a square
`[-.25,-.25,.25,.25]` AABB fixture with density 20, `ItemMask`, restitution .3
and friction .2, plus `Clickable`, `InteractionOutline` and an Items-depth
sprite with `noRot: false`. Jug does not override that fixture.
`DrinkVisualsAll` inherits `DrinkVisualsOpenable` / `DrinkVisualsFill`
(`drinks_base.yml:164–185`). Client `InteractionOutlineSystem.cs:101–138`
uses `GetClickedEntity` and `InRangeUnobstructed`; the underlying engine hit
test and sprite-layer merge remain unverified. Do not characterize upstream
click geometry as sprite-mesh or fixture-derived on this evidence alone.

## Impact reproduction setup correction

The first focused impact run selected 3/881 fixtures. All three failed setup
before a launch was admitted: **0 passed, 3 failed**. The preserved native log
contains aggregate completion and clean save/shutdown at lines 1157–1178,
despite the gate's generic missing-pass-marker finding. It was not a native
server crash and does not establish impact behavior.

The fixture authored top stone cells at Y=-48, exposing a surface at Y=-47,
and correctly placed its cabin there. Its support checks instead compared the
hull minimum against -48. R2 derives placement, support checks, flight height
checks and slab boundaries from the same scene layout: exposed surface -47,
four stone layers -48 through -51, bottom -51. Terrain placement and tolerances
are unchanged. Focused tests include the observed supported hull minimum near
-47 and reject the former -48 expectation. The subsequent outcomes follow.

## R2 physical impact reproduction

R2 selected **3/881** and completed **1 passed, 2 failed**, with clean native
save/shutdown. All probes passed support admission and received their requested
nudge; these are physical-trajectory failures, not setup failures.

- **20 m/s:** passed. Initial impact showed about `.156` m overlap, falling to
  `.051` on the next sample and approximately `.0026` by tick 80 before natural
  settling. A single transient sample above `.10` does not trip the unchanged
  two-consecutive-sample failure criterion.
- **40 m/s:** tick 133 showed `.817` m penetration at about `-33` m/s; tick 134
  still showed `.255` m while angular motion developed. This proves excessive
  penetration, not persistent burial: the failed probe then removed its hull.
- **80 m/s:** ticks 247–248 showed about `.780` then `.989` m overlap while
  downward velocity remained near `-56` m/s. Hull minimum Y reached `-51.011`,
  entering the bottom pad layer, but not proving whole-hull slab traversal.

Evidence: `build/agent-workflow/shuttle-relocation-sprint/reports/feedback08-impact-r2-native.log`,
particularly lines 1160–1197. Further diagnostics must retain the original
failure while observing a bounded remainder of the trajectory, distinguishing
recovery, persistent burial and full escape without relaxing acceptance or
changing gameplay physics.

R3 keeps the first failure latched permanently, retains 12 preceding samples,
and observes 20–80 post-failure samples before reporting failure. A later
recovery cannot turn the probe green. When loaded coverage becomes unavailable,
block-state reads are guarded on that first sample and later diagnostics use
native pose/velocity only. Compile and 109 focused JVM checks passed, including
four failure-latch/window regressions; independent fixture review passed after
the coverage guard correction.

## R3 bounded post-impact results

R3 selected **3/881**, completed **1 passed, 2 failed**, and saved/shut down
cleanly. Gate compile/JVM selection passed **10/10**. Native evidence is
`build/agent-workflow/shuttle-relocation-sprint/reports/feedback08-impact-r3-native.log`.

- **20 m/s:** passed at tick 82 with ten naturally settled samples.
- **40 m/s:** remained failed for the original two deep samples, but **recovered**:
  subsequent penetration fell through `.0815`, `.0268`, `.0101` to `.00251` m;
  the final result recorded 18 settled samples during the 20-sample diagnostic
  window. This is excess transient penetration, not persistent burial in R3.
- **80 m/s:** stayed embedded through all 80 post-failure samples, reaching
  `1.0000000000000002` m SAT overlap, with no whole-hull slab escape and zero
  settled samples. Coverage stayed complete and authored hull states unchanged.
  This reproduces persistent tilted hull/terrain penetration without logout.

The gate's statement that R3 confirms persistent penetration at *both* 40/80
must not override the actual 40 m/s recovery rows (1193–1217). The 80 m/s result
and aggregate/save evidence are at lines 1232–1365. The native gate remains
**needs_changes**; diagnostic completion is not readiness or repair.

## Stock-engine source findings and comparison boundary

Independent review verified a **source-level CCD cast gap** at Sable
`ad8b4d377741fbd923f4d863b2cf9e462daff9a6`: dynamic sublevel bodies enable CCD,
but the installed `SableDispatcher.chain(DefaultQueryDispatcher)` has no
successful nonlinear cast for its custom `LevelCollider` pair. The custom
dispatcher declines it; `LevelCollider` supplies neither support-map nor
composite fallback. Pinned Rapier's `TOIEntry::try_from_colliders` converts the
unsupported result into no TOI entry. Specialized contact-manifold generation
is separate. **Actual runtime branch invocation and release-binary/source
equivalence remain unverified; this is not a proven runtime cause.**

Source anchors:
- [Sable native initialization](https://github.com/RyanHcode/sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/sable_rapier/src/main/rust/rapier/src/lib.rs), adjacent `dispatcher.rs`, `collider.rs` and `Cargo.toml`.
- [Pinned Rapier TOI consumer](https://github.com/ryanhcode/rapier/blob/38e92f117590862481a53df6fc69a5d893e29186/src/dynamics/ccd/toi_entry.rs).
- [Parry 0.26.0 default dispatcher](https://docs.rs/parry3d/0.26.0/src/parry3d/query/default_query_dispatcher.rs.html) and adjacent `query_dispatcher.rs`; declared API evidence, not a verified Sable-local resolved lockfile.

The existing server-wide `sub_level_substeps_per_tick` setting was 2 in R2/R3
(0.025 s per outer step); its supported range is 1–10. A fresh isolated-fixture
comparison at 10 is the next diagnostic, with unchanged probes and assertions.
It must not alter owner configuration or represent a default-setting repair.
Higher outer-step counts have documented global performance cost and do not
implement the missing CCD cast path. Production adoption would require separate
behavior/performance evidence and an explicit architectural decision.

The separate comparison uses `GATE-PLAN-08-IMPACT-SUBSTEPS-COMPARISON.json`.
Its preparation helper refuses an existing target and writes only the fresh
fixture's minimal Sable config. Before each launch, the existing probe reads
`SubLevelPhysicsSystem.require(level).getConfig().substepsPerTick` and checks
the opt-in GameTest expectation of 10; file contents alone are not evidence of
the effective setting. No production defaults or original failure criteria
changed. Author compile, 112 focused Java checks, four Python checks and
independent review passed. The fresh comparison gate passed: preparation checks
4/4, focused gate JVM checks 13/13, and native **3/881 selected, 3 passed** with
effective=expected=10 before all launches and clean save/shutdown. Maximum
penetrations were `.02010695` (20), `.09432970` (40), `.02075071` m (80).
These are comparison results only; default-two physics remains unresolved.

The owner explicitly directed work to continue without unnecessary check-ins.
The next isolated ten-substep gate reuses the accepted-wall/body/reconnect/
airlock/ventilation selectors plus existing cargo motion/transition and impact
fixtures (`GATE-PLAN-08-SUBSTEPS10-REGRESSION.json`). Its fresh-directory helper
extends the existing two-target allowlist and passed six focused Python checks
and independent review. No production setting is adopted. If regressions pass,
continue with a controlled headless server-cost comparison before proposing a
production change; no owner action is needed for these isolated diagnostics.

## Combined ten-substep regression: first hull-state mismatch

R1 selected **51/881** and completed **48 passed, 3 failed**, with aggregate
completion and clean save/shutdown (native log lines 1918–1939). All three
impact probes confirmed effective=expected=10, then latched
`authored-hull-changed` at relative tick **36**, before impact. Their state
check temporarily became false and later true again. The recorded terrain
penetration at detection was only about `1.89e-6` m. The other 48 selected
regressions passed; this is not evidence of the earlier penetration failure.

The probe checks exact `BlockState.equals`, but R1 did not record which block
or properties changed. A physical geometry change, harmless state transition,
or responsible system is **not established**. Preserve the strict invariant.
R2 adds an immutable first mismatch witness: storage position, before/after
state strings, original cached authored boxes and current boxes inspected
through the existing bounded `LoadedBlockGetter`. Unknown neighbor context is
explicitly incomplete and never force-loaded. Cached-box equality is a box-list
comparison, not a general voxel-volume-equivalence proof.

R2 source review passed after correcting the unbounded neighbor query. Seven
Python preparation checks passed before that Java-only correction. The new
Java and three evidence tests remain **uncompiled/unrun** by the author because
the shell tool explicitly rejected compilation before execution. The normal
authorized integration gate must establish compilation and focused JVM success
before one fresh 51-selector native run. Cost measurement is paused until this
invariant is classified and the regression gate passes.

### R2 result and next diagnostic

R2's authorized gate compiled the new Java and passed **16/16** focused JVM
checks. Native validation selected **51/881** and passed **51/51**, with all
three impact probes confirming effective=expected=10, aggregate completion,
world saves and clean shutdown. The strict authored-state check was not
weakened; the earlier temporary mismatch did not recur. **Its cause remains
unresolved**, and R2 must not be described as a fix for that transition.

Continue autonomously with matched fresh-directory cost cases at actual
substeps 2 and 10. The planned diagnostic measures buffered server-tick
Pre→Post envelopes for one warmed, furnished powered cabin: a parked window
and an identical schedule of ordinary 20 m/s launches with natural landings.
It does not isolate Sable CPU time or certify throughput, connected clients,
capacity or production suitability. Preserve the original failure witnesses
and all owner settings; no production change or M1 acceptance is implied.

The cost implementation is separately tagged `shuttle-cost-diagnostic` and
reuses the existing scene/cabin/plot/probe, expected-substep guard and bounded
tick-listener lifecycle. It pairs full server Pre/Post events, buffers 300
parked and 600 flight samples, and schedules six ordinary +20 m/s inputs at
flight offsets 0/100/200/300/400/500 after natural support checks. Validation
and native inputs precede the measurement clock; full shape checks occur at
phase boundaries, not per measured tick. Results report min/mean/p50/p95/p99/max
milliseconds with no arbitrary performance threshold.

Source review passed after correcting recorder startup and making the explicit
1800-tick deadline test-relative (before the 2000-tick GameTest timeout).
New source and tests are **uncompiled/unrun** by the author under the explicit
shell restriction; authorized integration must compile and run focused tests
before two sequential fresh-directory cases. See
`GATE-PLAN-08-COST-COMPARISON-R2.json`. The first cost dispatch exceeded the
workflow prompt limit before admission (`prompt_admitted=false`); no checks or
fixture preparation ran. The compact revision preserves the same source and
still-unused designated fixture directories, with new evidence logs. Default-two burial and the intermittent R1
state mismatch remain separate unresolved findings; cost validity does not
erase either.

Cost dispatch R2 was admitted but stopped before validation because its source
snapshot was stale; progress recorded no started check or owned runner/child.
The service restart cancelled the author waiter, not completed earlier gates.
Resume with a fresh R3 candidate, preserving the current workspace and using
the same compact plan and still-unused cost directories/log paths. No stale
snapshot bypass, world rerun or duplicate active validation is authorized.

Cost R3 reached validation but stopped at the first preparation unittest: an
older test still expected an obsolete directory set. No compilation, native
preparation or native case ran. R4 corrects the test's explicit expected map
to the unchanged helper's five approved targets and covers wrong counts,
unapproved aliases and preservation of existing targets. Independent source
review passed; tests remain unrun pending the authorized gate. R4 uses fresh
evidence logs and the same still-unused native target directories.

Cost R4 was operationally blocked, without technical verdict. Exact comparison
using the shared verifier's `gitid` and `git_changes` found matching HEAD/base,
611 committed rows and the same 297 dirty paths. Only
`tooling/shuttle/__pycache__/test_prepare_impact_substeps_comparison.cpython-313.pyc`
changed hash (`6b9502ba…` to `53a1d633…`). This identifies the stale input, not
the process responsible. No R4 check logs or prepared native target were found.
R5 preserves that artifact and owner changes, uses Python `-B` for all three
Python checks, and takes a fresh snapshot with new evidence paths. It does not
delete caches, change ignore rules or relax the freshness verifier.

Cost R5 passed preparation checks, then failed GameTest compilation because
the new fixture omitted the readiness helper's required diagnostic supplier.
Neither cost target was prepared or run. R6 supplies that argument without
changing readiness behavior, workload or physics; independent source review
passed, with compilation/JVM/native validation still pending. Python `-B` and
fresh R6 evidence paths remain part of the gate.

Cost R6 compiled successfully but passed **19/20** focused JVM checks: the
recorder incorrectly allowed re-arming after `finish`. Neither native case
ran. R7 rejects any repeated arm on that phase recorder. It also separates
initial-placement terrain validation (`1e-4` penetration bound) from subsequent
natural-rest admission: known normal 20 m/s landings settle around `.00251` m
contact depth. The shared strict setup helper is unchanged; the cost fixture
calls it once at initial readiness, retaining native rest checks before every
nudge and full original-hull probes at phase boundaries on its leased stone
pad. Both changes passed independent source review; fresh gate compilation,
JVM checks and the still-unrun native pair remain required.

### Completed cost comparison R7

R7 passed preparation checks, compilation and **20/20** focused JVM tests.
The two native invocations each selected **1/882** and passed **1/1**, with
effective SDK settings 2 and 10 respectively, 477 hull pieces, 200 warmup ticks,
300 parked samples, 600 flight samples and six launches. Both saved and shut
down cleanly. These two target directories are now **used**; do not rerun them.

| Server-tick envelope (ms) | 2 substeps mean / p95 | 10 substeps mean / p95 |
| --- | --- | --- |
| Parked | 5.2063 / 15.9743 | 3.3817 / 7.7333 |
| Flight | 3.6565 / 9.4276 | 4.4993 / 10.0798 |

The flight mean increased about 23%; the parked result went the other way.
This fixed-order, single-cabin, headless comparison does not establish a causal
multiplier, throughput/capacity or production suitability. It is server-tick
envelope timing, not isolated Sable CPU timing. The gate report is
`shuttle-feedback08-cost-comparison-20261015-r7-integration-lead-review.json`
under the sprint reports; corresponding native logs use `feedback08-cost-r7-`.
Old automated pre-run manual text is historical, not a request to repeat tests.
No owner action or production setting change follows from this result.

Next work is the separate three-jug vertical-20 reconnect reproduction at
default two substeps. Preserved owner logs show player disconnect/rejoin while
the server continued running (21:00:24–21:00:32, `latest.log:2227–2231`), not an
integrated-server stop/reload. Reuse the moving/falling reconnect lifecycle and
normal ledger-backed jug fixtures, recording per-jug UUID/content/support and
server removal/sound-emission timing across the offline interval. Client audio
playback timing and the cause of the reported losses remain unestablished.

### Cargo reconnect reproduction candidate

`LifecycleShuttleCargoReconnectGameTests` adds the separate
`shuttle-cargo-reconnect-reproduction` tag. It reuses
`MovingCabinAuthorityFixture`, lifecycle reconnect, `FixtureHeldTool`, and the
existing `SableOwnedBodyCarryGameTests` cargo registration/world-placement
helpers. Three normal jugs have distinct UUIDs, ownership tokens and water
contents. The cabin starts at Y=-47; one upward 20 m/s pulse precedes disconnect
by two ticks, followed by 160 offline ticks and 60 post-rejoin ticks at measured
default two substeps. No recovery or production behavior is added.

Continuous server samples and immutable per-jug witnesses distinguish removal,
content/ledger loss and nearby glass-sound emission across lifecycle phases.
They retain early failures after the 32-row recent ring wraps. Final assertions
resolve entities by UUID, compare canonical contents/ownership, verify cabin
storage-space bounds/support and require natural ascent/descent/rest plus the
same living body, Mind and held item. Cabin `roomCells` are absolute storage
positions, not local offsets; the final and initial checks use that same domain
with only the existing .10 m floor-side contact allowance. Physical failures
remain failures even after later recovery. This is server-side reproduction,
not client audio or causality proof.

Independent source review passed after corrections, including a sound-event
type mismatch and a summary variable scope defect. Author execution remains
prohibited: new source and the focused `CargoReconnectWitnessHistoryTest` are
**uncompiled/unrun** pending the serialized gate in
`GATE-PLAN-08-CARGO-RECONNECT-R1.json`. The fresh native target is
`build/gametest-shuttle-feedback-08-cargo-reconnect-r1`; expected selection is
1/883, to be replaced by actual evidence. No cargo reproduction pass is claimed.

Cargo R1 stopped in `compileGameTestJava`: `observeOffline` was missing the
third nested callback closure. No focused JVM or native test ran. R2 restores
that closure and normalizes its indentation without changing behavior or
assertions; targeted independent source review passed. Fresh compilation/JVM
and native evidence are still required, using new R2 logs and the still-unused
R1 native target. Do not interpret earlier source review as compilation proof.

Cargo R2 also stopped at compilation: `GameTestInfo.fail` requires a Throwable,
not the bounded String supplied by the new reporter. R3 wraps that unchanged
message in the existing `GameTestAssertException` pattern. Source review passed;
compilation, focused JVM and native evidence remain pending. No cargo test has
run, and the designated R1 native target remains unused.

### Cargo R3 result: reported loss not reproduced

R3 compiled and passed its **1/1** focused JVM test. The native filtered run
selected **1/883**, passed **1/1** with **0 failures**, and saved/stopped cleanly.
SDK substeps were 2. Server tick anchors were launch 50, detach request 52,
OFFLINE 54, offline end 214, reattached 218 and completion 278: 160 confirmed
offline ticks and 60 post-rejoin ticks. All three UUIDs and world-entity ledgers
survived with 500/600/700 reagent **cents** (5/6/7 units of water), not 500/600/700
whole units. Body/Mind/held-item and natural ascent/descent/rest checks passed;
final native linear/angular velocities were zero. Departure, cargo-failure and
nearby glass-sound witness counts were all zero; the recent ring held 32 rows.

Bound report: `shuttle-feedback08-cargo-reconnect-20261015-r3-integration-lead-review.json`;
native summary: `feedback08-cargo-reconnect-r3-native.log:1270`, both under the
sprint reports. The R1 native directory is now **used**. Do not repeat it because
the automated notification contains historical pre-run instructions. This
case does not reproduce or repair the owner's cargo loss and does not exercise
a real connected client's unload/audio timing. That cause remains unresolved.
Next bounded work is the established query/outline and replacement-drop
orientation mismatch; M1 acceptance and later milestones remain unchanged.

R3 coverage limits: `SableFixtureScene` retains entity-ticking chunk tickets
until terminal cleanup, intentionally preventing ordinary world-region unload.
It therefore does not establish cargo continuity when real logout changes
watchers/residency. `LooseItemEntity` guards movement/support with loaded-chunk
checks; `LoadedFrameRayQuery.currentActorPoint` also requires resident tracked
storage. None of these facts proves the owner lost cargo because of unloading.
The case also lacks an asserted angular/orientation trajectory; separate
`CargoOrientationGameTests` yaw coverage does not combine rotation, landing and
logout. No arbitrary torque variant is justified by the separate 40/80 impact
observations. A residency follow-up must first establish actual runtime ticket
owners and distinguish legitimate unload/save from physical removal; simply
closing the scene or treating every absent UUID as destruction would be invalid.

Follow-up source tracing found no repository-owned continuous offline-body or
Sable plot ticket controller proving residency after scene-ticket release.
`SableFixturePlot.close()` destroys authored blocks/sublevel, so it is not an
unload test. Only `FixtureChunkTickets.Lease.close()` releases its own tickets;
existing tests prove ownership-safe release, not that the region then unloads.
`LifecycleExistingBodyReconnect:459–492` can load the saved body's exact chunk
on reconnect, while `MinecraftCharacterDeathEvidence:42–67` refuses to infer
death from unloaded absence. Existing loose-motion chunk tests cover an
unloaded neighbor, not cargo entity save/unload/reload. Do not assign a cargo
ticket-release test until bounded observation can distinguish these states
and establish what actually owns the relevant chunks; no causal fix is justified
by the current source findings.

#### Cargo residency/save boundary: reference-bytecode findings

The official 2.0.6 reference download was independently authenticated against
Modrinth's published SHA-512 and byte count; see the digest correction in
`SABLE-INVENTORY.md`. This is not a hash check of the installed runtime jar.

- Keep separate the cargo entity's world/section chunk, the sublevel's world
  bounds and its global plot-storage chunks. Local saved orientation and
  `LooseFrameStoragePoint` are not a persisted frame UUID.
- Stock `entity.entities_stick_sublevels.EntityMixin` only transforms an already
  non-null `sable$plotPosition` through a containing sublevel on tick. This is
  **not** evidence of generic world-position-based tracking restoration.
- Stock `entity.entity_unloading.PersistentEntitySectionManagerMixin`'s
  `processChunkUnload` HEAD hook examines existing sections and the container's
  chunk-region predicate. For eligible vehicles with exactly one player
  passenger it dismounts that passenger; the inspected hook does not delete
  ordinary cargo. It does not establish vanilla persistence or successful
  restoration either.
- The holding manager's plot/sublevel serialization is separate from ordinary
  entity serialization. Follow the normal Minecraft entity save/removal/reload
  path before interpreting an unload fixture. Old-object absence alone is not
  loss: reconstruction should preserve UUID/content, not Java reference identity.
- No project caller of Sable's force-load-ticket API was found. Known scene and
  player-view tickets must be distinguished from runner/unidentified residency.
  Existing lease diagnostics cannot enumerate every competing ticket owner;
  a passive attempt that stays resident must report **resident/owner unknown**,
  not unload success or cargo loss. Never force eviction or close the plot to
  manufacture the premise.

These are read-only reference-artifact findings, not cargo continuity validation,
a cause for the owner's loss, or a production fix. Subsequent cargo-only R24
runtime evidence is recorded below; it does not extend R3's body/logout scope.

Minecraft 1.21.1's normal entity manager stores chunk entities separately from
block chunks, defers pending entity loads, and unregisters normally unloaded
instances with `UNLOADED_TO_CHUNK`. FULL block-chunk reload alone is insufficient:
await asynchronous entity reacquisition by UUID. A cargo-only diagnostic may
therefore test actual old-instance unload, below-FULL residency, then distinct
reconstructed objects with unchanged UUID/content/ownership and observed frame
support. Frame restoration remains an assertion under test, not a save-contract
assumption. This narrower check cannot prove logout/body or process-restart
continuity; a resident chunk makes the unload premise inconclusive.

R24 subsequently passed filtered1/892 native: actual old cargo
`UNLOADED_TO_CHUNK`/UUID absence and old plot removal at tick22, explicit reload41,
same plot UUID/new object42 and three original cargo UUIDs/new objects44 with
preserved admitted stack components, ledger ownership and exact frame support.
Quiet phase was20 observations over19 intervals. Numeric reagent quantities are
source-derived setup, not separately printed runtime evidence. See
`CARGO-CHUNK-DIAGNOSTIC.md` for the bound receipt/hash and precise limits.
This closes the **cargo-only ordinary chunk unload/reload** proof gap, not combined
logout/body/Mind, in-flight unload, process restart or the owner's unexplained loss.

### Query/drop correction sequence

1. Shared immutable floor-pivot query geometry and authoritative basis access,
   consuming existing resolved profile dimensions and synchronized local spin.
2. Wire examination, pickup/revalidation and both outline consumers to that
   geometry; bound candidate enumeration using current resolved profile extent.
3. Initialize actor-context replacement drops from the actor's current admitted
   frame world basis, without storing transforms in hands/stacks or assigning
   false native frame membership. Contextless spawns keep world identity.

These are ordered work units, not independently complete feature claims.
Physical collision, existing carry/break rules, network state and production
substeps remain unchanged. The box is a physical-profile query proxy, not an
exact mesh. Existing server orientation and client render/position interpolation
policies remain distinct. Validation and connected-visual boundaries will be
reported after consumer integration, not inferred from a math helper alone.

The shared owner is now `ms14/loose_objects/LooseItemQueryGeometry`: resolved
profile bounds at a feet pivot, stable world basis plus synchronized local spin,
inverse-space clipping/nearest point and an oriented outline transform. Its
catalog-derived broadphase extent avoids assuming the current profiles are all
smaller than a fixed margin. Pickup retains its existing .20 tolerance through
local-space inflation; unexpanded exact projection/overlap still gates blockers.
Examination and hand selection share geometry without sharing their different
range/authority rules. Both outline paths retain their own existing position
interpolation, applied once. Physical entity AABBs remain unchanged.

`LooseItemEntity.createForActor` is the common replacement materializer used by
hand drops/grants, throws, native-slot drops and Creative conversion. It seeds
a world snapshot from the current admitted actor frame, with containment/no-frame
fallback to world identity. Actual item attachment is still decided by native
movement. `LooseFrameOrientation.resolveAuthoritative` is shared by query basis
access and existing runtime orientation updates, preserving safe-hold behavior.
No new network, stack or saved-state fields were added.

Foundation and scoped adapter source reviews passed. Native tests are being
assembled around the existing `MovingCabinAuthorityFixture` and one ordinary
observed native yaw, with separate query/drop tags. All new orientation work
remains **uncompiled/unrun**; no connected-visual or production repair claim
follows from these source reviews.

The assembled source and new fixture reviews passed after correcting test
counterexamples and missing imports. Focused cases exercise actual native yaw,
examination/LOS, selected pickup with request-time spin change, and actor-authorized
hand drop. Three distinct world regions avoid overlap with existing orientation
fixtures. `awaitNativeYaw` reads and checks actual SDK substeps using the existing
expectation guard and emits one readiness row, without stopping rotation.

Ownership test correction: `LevelItemLocationLedgers` is explicitly opt-in
(`LevelItemLocationLedgers.java:7`). Normal `LiveHandsCommitGateway` and ordinary
pickup/drop routes do not publish universal ledger locations. New tests therefore
verify exact authoritative hand/world ownership, whole components/counts and
optional-ledger non-interference, not an invented global registration guarantee.
R3's manually registered cargo roots remain a separate valid fixture contract.
No production ledger or transfer behavior was changed to satisfy a test.

Execution is still **uncompiled/unrun** pending
`GATE-PLAN-08-CARGO-ORIENTATION-R1.json`: focused JVM (expected 16), three native
consumers (expected 3/886), then full JVM and unfiltered native regressions.
The full run includes the still-unfixed default-two 40/80 impact probes; genuine
failures must remain failures, not be excluded or relabeled to obtain readiness.

Orientation R1 stopped at `compileJava`: the pickup adapter imported
`EntityTypeTest` from `world.entity` instead of the existing `world.level.entity`
package. R2 corrects that import only, preserving both bounded query sites.
Independent source review passed; no JVM/native test has run. Fresh R2 logs
retain the two still-unused R1 native target directories.

Orientation R2 reached `compileTestJava` and stopped on two nonexistent
Minecraft `Vec3.distance` calls in the new geometry test. R3 uses `distanceTo`,
retaining the zero nearest-point error and three-unit separation expectations.
Targeted source review passed. No JVM or native test ran; all four checks remain
required with fresh R3 logs and the still-unused R1 native targets.

Orientation R3 compiled and passed **15/16** focused JVM tests. Its only failure
was a new test oracle: width-two local bounds rotated 45° have nearest point
`(0,.5,sqrt(2))` to `(0,.5,4)`, not Z=`1/sqrt(2)`. R4 corrects the expected
coordinates at unchanged `1e-9` tolerance; production geometry is unchanged.
Independent source review verified the inverse-transform/clamp/transform basis.
Native and full checks remain unrun; fresh R4 validation is required.

### R4 operational stop and manual-readiness follow-up

R4 (`f17c3f423763`) stopped **operationally blocked**, with no recorded technical
verdict. The existing lead session exhausted its bounded recovery. Calling the
published shared CLI for that same candidate returned the same saved block;
do not create a replacement validator or infer pass/failure. R4 native log files
now exist, including focused preflight selection **3/886**. This is not an
aggregate pass receipt. Treat both orientation R1 native directories as used or
reserved by that attempt, not fresh reusable targets; do not repeat them.

The updated workflow requires actual owner-test readiness, not implementation
milestone gates. Entry uses the authored front airlock; no recovery teleport is
needed. A vanilla five-unit jug component command is source-derived, but its
native-carrier-to-controlled-body acquisition route is unverified, and the
fixture had no owner-facing yaw operation. Required setup is therefore being
completed within the existing permission-2 fixture diagnostics: a single native
world-Y angular-velocity pulse and one five-unit jug through the existing
body-aware trusted grant/world-placement service. This adds no piloting,
pitch/roll, native pose assignment, force-loading or production physics changes.

This is a real scope extension, not a retry of R4's unchanged snapshot. Any
future submission needs the complete setup, accurate owner actions/expected
results, focused command coverage, and a fresh revision. New setup code remains
uncompiled/unrun; no new gate is dispatched while implementation is incomplete.

The setup implementation and independent source reviews are now complete:
`ShuttleFixtureCommands` shares native velocity admission between `nudge` and
`yaw`; `cargo` requires the exact admitted ship and uses
`BodyHandItemTransfer.spawnTrustedGrant` with resolved jug capacity and 500 water
cents. A reviewed correction ensures yaw uses `(0,delta,0)`, not world-Z roll.
`ShuttleCargoSetupGameTests` dispatches the actual commands, asserting Y-only
velocity change with immutable pose snapshots, one exact-content jug with world
basis but no false membership, slot-by-slot inventory preservation, and bounded
mutation-free refusal checks. Test actors start inside the cabin, not in its
airlock. Both command cases use the `shuttle-cargo-setup` tag.

`SHIP-FIXTURE.md` now supplies future owner setup/check steps. Prepared plan
`GATE-PLAN-08-CARGO-ORIENTATION-R5.json` covers the complete correction/setup scope:
expected focused JVM 16 and native 5/888, then full regressions, with new native
targets confirmed absent during preparation. It is **not submitted/dispatched**;
the existing operational stop has not been cleared. All new setup execution
remains uncompiled/unrun. A source-review pass is not technical readiness or
owner acceptance; high-speed burial and the reported cargo loss remain unresolved.

The owner subsequently requested **"Try again."** R5 is the fresh complete-scope
attempt, including the source-reviewed setup commands/tests and self-contained
permission-2 owner checklist. This authorizes retry through the normal workflow,
not clearing process quarantine or inferring that R4 passed. Prior-process safety,
freshness and shared serialization remain mandatory. No approved source was
changed for this retry; only the prepared plan's operational note was updated.

R5 also stopped operationally, without a technical verdict. Read-only inspection
of its saved lead reply established the specific blocker: the generated scoped
review brief exceeded the workflow's 12 KiB limit before prompt admission.
Preflight succeeded; no R5 check receipts were produced. The shared dispatcher
sets uncertain admission before checking prompt length, so do not retry that
review or edit its queue record. No shared workflow code was changed here.

R6 compacts the author-owned plan and submission metadata, retaining all four
commands, coverage and manual checks. A changed plan requires a fresh revision,
not a replacement reviewer for R5. It uses the still-unused R5 native targets
with fresh R6 logs; R4 directories remain used/reserved. Gameplay source is
unchanged by this operational correction. Safety/freshness checks still apply.

### R6 execution: focused correction passed, full native incomplete

R6 (`b2dcabcd199a`) recorded fresh compilation and focused JVM **16/16**,
filtered native **5/888 selected, 5 passed**, and full JVM **3,308/3,308**.
Full native selected **888/888** but timed out (exit 124, owned tree terminated)
without an aggregate result or verified clean shutdown. Its required pass marker
was absent; it must not be described as a full pass.

The full log retains genuine hull-impact failures: 80 m/s at line 22333
(approximately 1 m maximum depth, still embedded after 80 post-failure samples)
and 40 m/s at line 22619 (approximately .817 m maximum depth). Later settling
does not erase either failure. `defaultBatch:11` (50 tests) began at line 22668,
05:04:31; its apparent progress remained unchanged through the final log
timestamp 05:15:14. No later batch/aggregate appears. Current artifacts neither
identify the unfinished test nor distinguish stalled test ticks from severe
server-tick lag. Earlier over-capacity puddle errors at
`(4204867,-59,336194)` stop before batch 11; causality is not established.

Evidence is under the sprint reports: `...-r6-gate-validation.json` and
`feedback08-cargo-orientation-r6-{focused,full}-native.log`. The R5-named native
directories are now **used**, not fresh targets.

R6 remains operationally blocked, without a published technical verdict. The
full JVM check overwrote the same XML paths bound by the preceding focused JVM
check; receipt verification correctly rejected the stale earlier hash. Do not
alter artifacts or weaken freshness. A future plan should run the full JVM suite
once (including the focused classes) rather than bind two incompatible versions
of the same report paths. The saved scoped review preceded validation and is not
an assessment of these later test results.

Read-only API research found no public running-info getter on Minecraft 1.21.1
`GameTestTicker`. Test-only progress instrumentation is being added at its public
`add(GameTestInfo)` method, using the public `GameTestHelper(info).getTick()`
instead of private-field access. It must log bounded actual names/tick ages and
preserve every assertion/outcome, without changing production behavior or owner
launch settings. Exact 1.21.1 public API references: `mappings.dev/1.21.1/` pages
for `GameTestTicker`, `GameTestInfo`, `GameTestHelper` and `TestFunction`.

The new progress observer is source-complete and source-reviewed. Its fixture
common-setup bootstrap registers a fixture-only mixin configuration; one public
ticker-add injection admits infos to a bounded identity state owner. It reports
actual helper ticks, server ticks/game time, starts/terminal states and incomplete
names at 200 ticks **or** 30 monotonic seconds, with guarded shutdown cleanup and
explicit incomplete coverage on overflow. Six pure-state tests cover identity,
cap/cleanup, both clocks, new-cohort re-anchoring and exercised overflow reset.
The native hook probe has its own `gametest-progress` batch, preserving existing
fixture batch assignments. No production mixin, main code, build/run configuration
or owner launch setting was changed. Runtime bootstrap timing remains unverified;
the probe must fail if the hook is absent.

Prepared R7 uses one full JVM check (expected 3,314, including the old 16 plus
six new state tests), focused native 6/889, then unfiltered native. This retains
coverage while avoiding R6's XML self-overwrite. R7 native targets were confirmed
absent during preparation. Author validation subsequently passed
`compileGameTestJava compileTestJava test --tests "*GameTestProgressStateTest"`:
**6 tests, 0 failures/errors/skips**. This is focused development evidence only;
native hook application and fresh R7 integration checks remain unverified.

Review entrypoints for the correction are `LooseItemQueryGeometry`,
`LooseFrameOrientation`, `LooseItemEntity`, examination selector/resolver/client,
`BodyItemTargetProbe`, both outline paths, existing hand/drop/throw materializers,
`ModEventHooks` Creative conversion, and `ShuttleFixtureCommands`. Test progress
is entirely under `src/gametest` plus its focused JVM test. The immutable owner
base also exposes historical sprint/workspace changes: preserve them, do not reset
or narrow the base to hide them. This entrypoint list is not a per-file hash proof
that all other historical files are unchanged; unexpected diffs still need review.

## Six-step trial follow-up (R31–R37)

Production/default remains2; owner configuration is untouched. R31's five
six-step trajectories mitigated whole-slab escape but retained transient overlap
up to about14.4cm: not zero penetration or safe80 certification. See
`SUBSTEPS6-R31-RESULT.md` and the stock-only R30 result for their distinct scopes.

R33's three failures were repaired: authored-deck coordinate selection, jug
own-travel clipping versus stationary-axis pushout, and an attested idle support
probe contacting a rotated **local floor**. Existing SAT/capture, movement,
prototype thresholds, shared server/client ownership and position-only correction
owners were reused; see `IDLE-PROBE-TILTED-FLOOR.md`. R34 validated cargo/jug
repairs; R35 validated default-path impulse/exterior cases; the combined R36
selection completed **51/894,51 passed**, with actualSDK6 and clean saves/stop.

R37's matched fresh2/6 single-cabin cost runs both passed, players0/hull477.
Flight mean was **1.9715→2.6604ms**, P95 **5.0284→6.3101ms**; parked mean
**1.7934→1.9434ms**. The observed flight mean difference was+34.94%, not a causal
physics multiplier or capacity guarantee. See `SUBSTEPS6-R37-COST-RESULT.md` for
all distributions, workload bounds and receipt hashes. No settings adoption or
M1 acceptance follows. Flight-plus-actual-unload, process restart, connected
prediction/presentation, Creative/uncontrolled combinations and owner cargo loss
remain open; M2–M6 remain paused.

## Invariants

Do not silently cap velocity, freeze pitch/roll, add artificial gravity,
teleport crash recovery, fork native physics, or substitute another ship engine.
No assistant client launches, GUI automation, owner-world/config changes,
packaging, installation, commits or cleanup are authorized. High-speed testing
with valuable owner characters/items is unnecessary; use disposable fixtures.
