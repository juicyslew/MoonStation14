# Owner findings — sprint closed

Owner closed the sprint on 23 October 2026. See `HANDOFF.md` for the final state,
validation limits and deferred model work. Pending/rejected language below is
historical and does not supersede that closure.

## Owner-requested controlled airspeed — diagnostic only (23 October 2026)

Current resolved airspeed multiplier is 0.80, one third below the prior 1.20
candidate, to make flight speed controlled rather than too fast. The repeated
short-waypoint braking correction is validated: AI uses full wish only when the
route has multiple waypoints, retaining braking at the final waypoint. Natural
air movement now uses the converted resolved sprint policy at the same controlled
cap. Ground speed factor 0.25, authored walk/sprint values, human movement,
vertical control, throw recovery of 4 ticks and impulse are unchanged. Pinned SS14 NPC steering
uses `CurrentSprintSpeed`; seek-weight taper is adapted to the subtick movement
range. Local 3D route following and explicit airspeed are Minecraft adaptations,
not identical SS14 steering.

Diagnostic `run_481e11552a12d743130a8eeb` passed ACK/cleanup-confirmed selected
native fixtures (54/54), including the new normal-AI short-waypoint cruise test;
paired capture `build/reports/nonhostile-animals/population-profiles/mixed-0b8afaf7285f44adac810b99dd58f42b`
contains 1,288 rows, 16 UUIDs and zero rejects. Interaction/prediction readiness
passed; prior focused JVM validation passed 34 tests. This is filtered diagnostic
evidence, not full-sprint readiness, native possessed-control proof, visual review,
or owner acceptance. AI comparison is against the same resolved controlled-sprint
cap, not authenticated possession or live-pixel proof. Subjective feel and owner
acceptance remain open; no HANDOFF or sprint completion is claimed.

## Portable-body throw recovery — diagnostic only (23 October 2026)

Current candidate routes the committed portable-body throw through the external
flight launch, then eligible dry/conscious/alive/unmounted/uncontained flyers
recover into natural flight after reaction with momentum preserved and no duplicate
motor. Non-flyers and unmarked launches remain ballistic. Resolved typed settings
are airborne speed multiplier 1.20 and integer recovery 4 ticks (1–40); authored
species ground/sprint speeds and ground factor 0.25 are preserved. NPC recovery
starts a fresh AIR phase and catch anchor, clearing the prior wait. This is a
Minecraft adaptation, not the exact SS14 catch rule; upstream air-movement permission
does not cancel throws.

Correction to the manual recovery instructions: a direct-launch native bat fixture
does not exercise actual player pickup, and bat is not `PortableBody`. Do not
recommend bat pickup or possessed-bat throwing. Bee/butterfly are the applicable
portable flyers; their real player pickup-and-throw recovery remains unverified
until owner manual checking. Do not treat fixture coverage as proof of that path.

Coordinator diagnostic `run_afb8f50cab2c5dd82b4cc0de` passed selected native fixtures
(53/53; ACK/cleanup confirmed), focused JVM (34/34), and interaction/prediction
readiness. Paired capture `build/reports/nonhostile-animals/population-profiles/mixed-60c5480d0d414470b0d38b91adf838ee`
has 1,394 paired rows, 16 UUIDs, zero rejects. Filtered diagnostic, not full suite;
readiness boundaries are JVM dispatch/composition, not authenticated possessed
recovery. Independent review is pending; no visual-quality/performance conclusion,
full-sprint readiness, or owner acceptance is claimed. No HANDOFF.

## Current natural-flight correction — diagnostic only (23 October 2026)

Correction to historical reports: height/contact figures are blocks, not `ms`
latency or performance measurements. Earlier hover-retention and current-ground-
projection checks describe the superseded height-only candidate, not current
air-roaming behavior.

This replaces the earlier height-only candidate as the current behavior. NPC
flight uses planner-only vanilla 1.21.1 `PathFinder` / `FlyNodeEvaluator` 3D
routes, not `FlyingMoveControl` or `Navigation.tick`; `NpcBehaviorSystem` uses
the shared `MOTOR` once per tick. Autonomous flight has signed ascent/descent,
separate from explicit `LANDING`. Flyers walk/rest for nominal 160 ticks (8 s),
varied 0.75–1.25, then fly nominal 400 ticks (20 s), same variation, through
persistent takeoff, air-travel, land-approach and landing phases. Typed resolved
flight data supplies these values. Minecraft 3D routing is an explicit adaptation,
not SS14 Z-parity.

Air routes need no local GROUND support and may leave open-top walls; support is
queried for landing contact only. Ceilings remain obstacles. The 40-block cumulative
route-travel cap is distinct from 12-block X/Z and 8-block Y spatial limits; route
work also has shared/local 700-expansion and 12-call budgets (7,000 charged
neighbor expansions). These bounds do not solve every maze or assert vanilla's
exact popped-node count.

Latest `run_c2786cfb368fcf69ed62e62b` diagnostic passed: ACK/cleanup confirmed,
30/1,090 selected native fixtures passed, focused JVM 30 passed, paired capture
had 16 UUIDs and zero rejects, and prediction selector passed. Diagnostic only;
not full-suite, visual review, performance acceptance, or owner acceptance. Runtime
capture measurement review remains pending; do not infer medians. Earlier runs,
including the height-only `run_e39ae679f27000ed1a0c9d02`, remain historical and
immutable. No HANDOFF or sprint completion is claimed.

The coded candidate caches visible posed corpse contact against all 42 critical/
dead static bounds; distinguishes landed bat/parrot feet and static wings from
bee/butterfly legless slow body-scuttle with static wings (0.25 ground factor);
and derives ray-gait cadence from the resolved ground-walk baseline. Rear-abdomen
identity is a child using the same 30 boxes/UV and parent pose; original head/leg
paths remain, with gait-only local yaw (4/6 degrees) using actual clip duration.
Idle/dead poses are unchanged. Snake alive art/physics and human-approved movement
are untouched. These three visual corrections are coded, not visually accepted.
Ground-skimming remains the root open issue. Paired opt-in JFR telemetry reuses
population capture; default/older performance captures had no pose overhead.

Coordinator-only pending serialized diagnostic command:
`connected_join.ps1 -PopulationScenario Mixed -FlyerPoseDiagnostic -WallTimeoutSeconds 120`.
It creates an isolated 40-animal mixed profile, not the 37-animal manual showcase.
The pose option requires both flags and is bounded to 16 UUIDs, five ticks, and
four mesh scans/frame; matching requires UUID, dimension, host, same-host UTC
within 350 ms. Reported floor TOP and mesh BOTTOM are estimates, not camera proof.
Do not launch this from the manual instructions. Preserve normal ghost-role
eligibility; `[DEV]` remains operator-only. Preserve active runtime PORT Java rig/
curves on future asset imports; immutable accepted R4/R5 packages are not
regenerated, and no editable Blockbench source is claimed. No HANDOFF: owner has
not completed the sprint.

## Latest owner review — 23 October 2026

Implementation is in progress; none of these findings is claimed fixed. The
owner's 12 findings are:

1. Ground-skimming flyers.
2. No ambient NPC calls except mouse; clarification: watching NPCs is not IC
   (in-character) speech testing.
3. Showcase fixture is missing a mouse.
4. Bat teleports/snaps.
5. Powered bat fall damage depends on height.
6. Grounded flyers walk/fly slowly.
7. Crab gait is slow.
8. Gorilla has no walk/slow movement.
9. Roach tail is static.
10. Corpse contact feels bad.
11. Shift latches descent.
12. Snake behavior is accepted.

The prior 7/10/77 diagnostic passed its selected checks; that is not owner
acceptance and does not close these findings. Preserve the distinction between
ambient NPC vocalization and possessed-animal speech: pinned SS14 generic idle
composition does not imply NPCs speak; parrot Vocalizer is an exception. The
owner also asked what `[DEV]` means: these are operator-only test offers, unlike
upstream ordinary GhostRole entries. The question is recorded, not an assertion
that the entries are upstream roles. Do not write a full HANDOFF while the owner
has not completed the sprint.

R30 technical readiness did not establish acceptable gameplay. Owner feedback
requires the following work before another acceptance request.

## Latest owner review — corrections awaiting feature gate

The owner retested the gravity-air correction and reported that it felt like
Minecraft movement and worked well. This confirms the requested air-control feel
check, alongside the prior positive 100-Kobold harness-movement report. Neither
statement closes unrelated animal, sustained-profile, or observer acceptance
requirements.

After the deferred-pose correction and repeatable-pit diagnostic passed, the
owner reported movement was **really good with 100 crowded Kobolds**. This is
positive manual evidence for harness presentation under that load, not completion
of every sustained/observer acceptance check. The remaining reported issue is
airborne steering: jumping forward and switching to sideways input feels sluggish
compared with the owner's expectation of vanilla Minecraft. Investigate the
gravity-air acceleration constraint separately; do not conflate it with the
previous snapshot-driven camera pauses.

Current Kobold reproduction: the owner starts a **new world on every run**.
Small animal counts felt fine, but after a crowded Kobold population was placed,
their own movement looked like roughly 5–10 updates/second. The slowdown persists
regardless of player location; do not recast this as near/far or visibility
dependence. NPC movement was not obviously affected. Kobolds also began dying;
the damage source remains unverified. The owner requested a repeatable pit fixture
so they do not have to rebuild/spawn the population manually on every run.
The owner completed the movement trace using the instrumented build and reported
a further discriminator: movement is choppy in the player mob harness, while
creative-mode movement is clearly smooth under the same population load. Trace
analysis is pending; this points toward the harness path rather than establishing
that raw frame rate alone accounts for the symptom.

After diagnostic `run_28445f8a9c193569cb5a5bb0` passed selected headless checks,
the owner reported an immediate internal server error when joining their world.
Join remains blocked pending log diagnosis and correction; the diagnostic does
not establish that this connected entry path works. No movement improvement or
owner acceptance is established by that attempted join.

The owner log identified an uncaught `sequence must be positive` exception:
the character tick constructed a synthetic wire intent with sequence 0 before
receiving any input. Character and legacy harness owners now consume shared
control state directly; sequence validation remains strict. A registered-player
regression covers Ready, idle ticks with ACK 0, then the first real input.
Focused body-control JVM tests passed (352 tests, 52 suites), compilation and
source review passed. Diagnostic `run_0c2935bdcee536b8d18f5fea` subsequently passed
the selected native, JUnit, and readiness checks, including selection of the new
join fixture. Result acknowledged; cleanup CONFIRMED. The owner's actual world
join has not yet been retried successfully, so connected recovery remains
unverified.

### Sustained owner observations — 22 October

Latest retest `run_bc4a16f0d94126327e90f59b` failed the initial idle-phase
coverage check; the owner later clarified that they forgot/ignored the phase
instructions. No complete post-FIFO performance capture exists. Its human
observations remain important: movement was somewhat smoother, constant small
corrections became occasional larger ones, and circling while jumping appeared
to accelerate to roughly twice normal speed.

The latter exposed a reproducible projected-acceleration problem in the shared
gravity-air motor. A scoped correction limits new voluntary acceleration without
rescaling external momentum; 61 focused tests passed and source review passed.
The FIFO also retains a backlog after stalls. Tick-based state/prediction work is
in progress under `INPUT-TIMING.md`; the interim FIFO is not a final latency fix.
These changes have not been validated by another owner session. Failed-phase
metrics are now persisted for future diagnostic aborts without relaxing coverage.

- Mixed 40: owner reported mostly smooth play, with one brief airborne stutter
  during jumping. Sustained capture `run_1baea0e4486949491cf192c8` passed coverage;
  cleanup is confirmed. Report: `build/reports/nonhostile-animals/population-profiles/mixed-9d4b272697ac49298ef98a2f28f89f7e/`.
- Parrots 100: owner reported notable slowing/rubberbanding when looking toward
  loaded parrots, with smooth behavior looking away or moving far enough away.
  Client frame performance appeared reasonable to the owner. This is an observed
  movement/presentation defect; slow server ticks are a hypothesis, not established
  by rubberbanding alone. Capture `run_d2b739a1c7cfef81ca9d3bd5` passed coverage,
  not performance acceptance; cleanup is confirmed. Report:
  `build/reports/nonhostile-animals/population-profiles/parrots-70395b8ca6ab42c1b424ec4074d538ad/`.
- Owner has no time for more tests now. Kobolds 100 and connected owner-plus-observer
  takeover remain deferred until the owner returns. Do not launch another capture
  or infer these boundaries from the completed runs.

Input-cadence follow-up: code inspection found the shared server gate discarded
valid same-tick arrivals. The existing owner now uses a bounded 64-frame FIFO,
with one consumption attempt per authoritative tick, shared admission/history
bounds, and acknowledgements only for applied inputs. Headless diagnostic
`run_656a313543a7f68b0fc55ec5` passed 39/39 native fixtures, selected JVM checks,
and interaction/prediction readiness; cleanup is confirmed. This corrects a
demonstrated code mechanism, not a proven cause or resolution of the captured
Parrots incident. Queued inputs can introduce backlog latency. Live retesting
must wait for the owner's return.

The latest full gate `run_aae9fadfb67612976ede7554` passed coded checks but failed
review on sustained-load and connected-takeover coverage. The two captures above
partially address that evidence gap; the parrot observation opens a substantive
lag investigation. No further feature readiness or owner acceptance is claimed.

Selected diagnostic `run_8fb86e98fac3dc773be9ce4e` passed (38/38 native fixtures,
selected JVM command, interaction/prediction readiness); acknowledgement and
cleanup are confirmed. Local focused checks passed 47/47 tests across 11 classes.
The current corrections still need a new feature-readiness gate and manual review.
The original findings below are preserved; they are not claims of current failure.

The feature gate `run_386c0ea62b5d916c24781d9b` passed, but the following
subsequent gameplay findings reopen implementation work. That PASS is not
evidence that these defects are fixed, nor owner acceptance.

- Landed flyers visibly float above the ground; align grounded feet with the
  collision base without changing physical flight altitude.
- Flyers should be able to wait in the air and continue to another airborne
  destination, rather than land between every movement.
- Bat appears to fly backwards.
- Reduce butterfly, cockroach, crab, dionanymph, lizard, mothroach, and both
  reindeer appearances; reduce spiders slightly.
- Preserve gorilla model size, but enlarge its undersized collision box.
- Correct monkey diaper z-fighting while preserving the accepted artwork.
- A spawned bat could not be reached through `/ghostroles`. Investigate admission
  separately from movement after successful takeover.
- Controlled bat movement snaps back, continues sliding after input release,
  and is invisible to other players. Previously tested ordinary animals work;
  development-only animals do not. Preserve ordinary role restrictions.
- Almost no animal sounds were heard. Owner audio settings are uncertain;
  verify configured playback rather than assuming audio was disabled or
  inventing ambient calls from speech/emote sound mappings.

Source investigation established that trusted development offers bypass the
ordinary `GhostRoleComponent` admission requirement, but committed movement
still requires that component. It also found that an empty idle route triggers
landing during every NPC wait. These are concrete source defects, not a
connected reproduction or proof that all reported symptoms share those causes.
Owner-plus-observer takeover, physical input/release, visual presentation, and
sustained-play acceptance remain unverified.

Audio source audit: configured possessed-animal local speech resolves registered
voice cues server-side and plays them client-side as `SoundSource.NEUTRAL`,
including for the speaker. Registered animal cues have matching sound assets and
attribution. Ambient playback is separate: only mouse currently declares the
160–400 tick, volume 0.4 ambient policy. Upstream speech/emote mappings do not
establish periodic NPC calls; no new ambient calls are justified by this report.
Audible playback remains untested. For a focused check, enable Friendly Creatures
audio, possess a voiced animal such as parrot, and send ordinary local IC speech;
compare plain, `?`, and `!` endings. This is not an emote/vocal-system test.

The numbered R30–R35 material below is historical investigation context.

R32 measured 100-parrot and 100-kobold cases; mixed aborted before spawning on
fixture preflight. Parrot server samples identify route/support collision work
as a lead, not a proven sole cause. The short kobold run did not reproduce major
client stalls and rendered only part of the population. R33 will measure route
operations and a camera-facing fixture, preserving per-case join evidence and
precise failures. No planner optimization or population-lag fix is claimed;
prolonged runs and physical movement/jumping remain outside these initial captures.

R33 captured roughly 100 renderer calls per frame for parrots and kobolds; mixed
stopped before spawning because a station-floor block occupied fixture headroom.
Failed parrot routes had 26-ms median elapsed duration and heavy support/collision
work. A route-attempt-local support memo is under validation; no improvement is
accepted yet. Fixture placement now checks bounded safe candidates without clearing
station blocks. Native equivalence/work-reduction tests and new population captures
are needed. Sustained client degradation, moving/jumping and long-run growth remain
unreproduced, not disproven.

R34's four native route-reuse fixtures passed (filtered 4/1,063), including
measured work reduction with equal routes and fresh results after world changes.
The population capture still aborted before spawning: all bounded candidates
intersected station geometry. Mixed was not run. Deterministic isolated fixture
setup is required; widening the search or clearing station blocks is not the fix.
Population timing improvement and owner gameplay acceptance remain outstanding.

R35 captured 100 visible parrots with the station builder explicitly disabled.
Failed-route support memo hit rate was about 91%, but expensive edge collision
work remains; this is not a general lag fix. Mixed did not launch: Gradle
preparation's process-exit check failed. Descendant ancestry verification is being
hardened before another native request. The reported PIDs are no longer live in
read-only observation, but historical ownership and integration cleanup clearance
remain unresolved.

## Ordered work units

1. **Establish causes and parity.** Inspect actual owner logs and existing profiling
   evidence; audit pinned SS14 idle/task composition, bat flight and kobold hands.
   Trace the current `/ghostroles` entry path, not the legacy possession command.
2. **Correct locomotion and presentation.** Resolve autonomous flyers staying near
   ground, two-point oscillation and post-push orbiting. Match walk animation to
   actual movement and reuse established easing/blending; provide working bat wing
   animation. Preserve accepted models. Use shared movement/animation owners and
   resolved components, with explicit Minecraft adaptations for vertical flight.
3. **Correct population costs.** Measure server tick cost and client frame stalls
   separately. Compare mixed populations around 40 and matched populations of 100
   parrots and 100 kobolds, including idle/moving/jumping views and elapsed time.
   Investigate distant/unloaded lifetime without removing accepted persistence or
   disabling physiology, equipment or AI to hide costs. Fix demonstrated causes.
4. **Validate current interaction and gameplay.** Use `/ghostroles` in the owner
   manual; ensure intended operator development access to bats through the existing
   admission policy without fabricating upstream ordinary ghost roles. Add focused
   regressions for corrected behavior, obtain native/performance evidence through
   the integration-owned gate, and request owner testing only when complete.

## Owner observations to retain

- Bats have no visible animation and do not autonomously ascend into real flight.
- They shuttle between two points; a nudge can produce tight orbits around a goal.
- Species need source-grounded variation in idle movement, not one generic pattern.
- Many walk clips outlast movement and lack player-like easing in/out.
- Roughly 40 mixed creatures produced major, increasingly frequent stalls.
- 100 parrots increased server tick cost/spikes without comparable client stalls;
  walking away and returning did not materially change the result.
- Many kobolds increased server tick cost and also caused client stalls; jumping
  appeared to aggravate the latter. Causation requires measurement.
- Bats were absent from `/ghostroles`; old `/ms14dev possess` instructions were wrong
  for the owner's current workflow.
- Kobold one-hand and reduced equipment slots were positively received; preserve
  them and verify the hand count against pinned upstream ancestry.

TPS, server tick duration and client frame time must be distinguished in evidence.
No root cause, performance target, gameplay fix or owner acceptance is implied by
these observations. Do not rerun full gates merely to collect another green count.

## Investigation baseline

- The bat's exported `fly` clip exists. The adapter currently switches clips at
  full weight using vanilla's decaying walk amount; this is not cross-fading.
- Autonomous takeoff currently loses positive vertical intent once airborne.
  Ground-route idle completion also requires support/on-ground, unsuitable for an
  airborne goal. Orbit causation still needs a focused reproduction.
- `/ghostroles` currently lists ordinary prototype roles only. The existing
  operator development origin already permits eligible resolved animals without
  an ordinary role; integrate it into that menu with server-derived admission,
  not by inventing an upstream bat GhostRole.
- Both parrots and kobolds inherit `FOOD/DRINK/IDLE` from
  `supported_animal_core`; comparing concrete JSON alone gives the wrong workload.
  Kobolds add retaliation and hands. Their performance difference is unmeasured.
- Actual October 16 04:05–04:39 server/client logs are readable and preserved under
  `build/reports/nonhostile-animals/owner-r30-feedback`. No overload/GC warning
  match establishes neither cause nor absence of stalls. Native `jcmd` and `jfr`
  tools are available in the installed JDK 21; no recording has yet been run.
- Pinned SS14 confirms random accessible idle travel followed by waiting, source
  speeds, 2D flying physics and ancestor hands. Autonomous altitude and any added
  species-specific idle patterns must be labeled Minecraft adaptations.

## Source corrections awaiting validation

- Flight presentation previously called the server-only identity resolver on the
  client. Read-only client capability now uses `projectForActor`; explicit local
  prediction transitions remain separate from server-authoritative transitions.
- Animal native adapters now accept explicit clip/time. Per-entity transitions
  blend cached rig poses for 100 ms, use accepted interpolated displacement rather
  than vanilla's lingering walk amount, and retain authored flight motion.
- NPC flight uses a normalized vertical wish through the existing sole motor and
  resolved `Flight.npc_cruise_height`; fixed modulo-based takeoff/landing has been
  removed so it cannot fight the NPC task lifecycle.
- `/ghostroles` has server-derived, operator-only `[DEV]` entries for supported
  animals lacking ordinary roles. Catalog size is bounded, ordinary entries are
  prioritized, and requests revalidate the exact target/current permission.

Source review and test definitions are not runtime acceptance. Focused compilation
and JVM checks are pending; population profiling is not yet wired end-to-end and
no lag improvement has been measured. Actual client projection/synchronization,
flight/animation appearance and connected role takeover remain native boundaries.

## Population measurement scope

The optional population mode of `tooling/validation/connected_join.ps1` reuses the
isolated real join/process owner. Cases are 100 parrots, 100 kobolds and 40 mixed
animals (8 each bat, kobold, parrot, hamster and cow). It uses committed actor
ownership, an empty baseline followed by a population window, and server/client
JFR recordings. The analyzer derives early/late population windows from the actual
baseline/population/complete timestamps and reports tick/frame distributions, GC
and primary-thread sampled stacks. Actual animal render calls are recorded; calls
are not unique visible-animal counts. Missing coverage is UNVERIFIED, not a pass.

These are initial stationary-view diagnostic windows, not a recreation of the
owner’s exact mixed population or proof about minutes-long growth, physical camera
movement/jumping, unloaded regions or acceptable performance. R31's diagnostic
plan requests only those three captures; it cannot establish sprint readiness.

### Optional sustained operator capture

The isolated connected capture may additionally set
`-Pms14AnimalPopulationSustained=true` (alongside the ordinary scenario and capture
directory). This does not inject controls: leave normal committed keyboard control
enabled and follow the server's phase instructions. After the existing 100-tick
empty baseline and population spawn, the five phases are each 1,200 server ticks
(nominally 60 seconds): near-idle, walking, jumping, walking at least 64 blocks
from the population center, then returning within 32 blocks. Run on a safe,
naturally walkable flat test area; the 16x16 glass pen is not opened or modified
for a movement route, and movement among penned animals is not established.

The observer is called only after the existing committed character world step
accepts the owner's input and verifies the same player/body/epoch. Coverage is
aborted unless walking accumulates 16 blocks of accepted horizontal displacement,
jump phase observes at least three accepted jump requests while previously
grounded and separately at least one positive vertical displacement sample, the
away phase reaches 64 blocks from center, and return observes a sample within 32
blocks. “Accepted grounded jump request” is not proof of successful launch; ascent
is reported separately. Movement metrics concern the owned body, not rendered
camera position or animal visibility. Sustained capture completion is coverage
only, not a performance pass, lag fix, physics result, or native visual/owner
acceptance. The original 500-tick stationary population window remains unchanged
when this opt-in flag is absent.

## Current stationary capture comparison — diagnostic only

After authorized native diagnostic `run_96c745b32a9d05fda7bdd1e4` passed all 36
selected fixtures, stationary diagnostic `run_ccdb5bbe1b31bdffb76501bc` captured
Mixed (40), Parrots (100), and Kobolds (100) successfully with cleanup confirmed.
At 500 population ticks and rendering fraction 1, late server p95 was 11.78, 18.36,
and 22.88 ms; late client p95 was 13.45, 11.47, and 16.88 ms. These are short
stationary captures and not owner acceptance. Comparisons against the older
stationary capture are confounded by different geometry, randomness, and warmup;
late server p50 worsened for Mixed/Kobolds and was similar for Parrots, while
early p99 and client p95 moved in both directions. Do not claim a controlled
before/after or broad lag resolution. Movement/jumping, sustained operation, visual
review, and owner acceptance remain outstanding.
