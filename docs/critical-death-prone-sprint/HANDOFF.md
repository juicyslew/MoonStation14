# Critical State, Death & Prone

## STATUS

Closed by owner acceptance on 2026-10-07. Connected ghost entry/movement and the
final sprite size/placement are confirmed. Owner also confirmed pulling and the
other medical/posture behavior, including villager/pig Critical and Dead states,
and removal of the native dev head. Automated gaps below remain recorded;
closure does not imply a clean full GameTest suite. No commit was requested.

## ARCHITECTURE

Main integration adds disposal containment and outlet flight to these boundaries.
See `merge-main-validation.md` for merge-specific decisions and validation.
Contained Critical/Dead bodies remain pinned and skip passive corpse stepping;
ordinary corpse physics resumes on exit. Native death must not leave a downed
medical corpse in `DYING` pose with standing-height collision: the existing
medical mixin reapplies physical prone posture after vanilla death handling.

- Typed damage is authoritative. Resolved `MobState`/`MobThresholds` determine
  medical state; native health is a projection, not the threshold source.
  Medical state, posture, and stun remain separate composed systems.
- Retained character bodies preserve worn gear/storage and exact identity;
  incapacitation drops hands and gates conscious actions. Critical and Dead
  body physics continue independently of mind ownership, with `NoAI=true`.
  Passive native-tick physics and owned movement must not both step a body.
- Death first observes the corpse. Fresh movement after release starts ghosting.
  `ObserverSystem` owns observation/corpse identity; lifecycle authority owns
  staged ghost binding. Existing Begin/Ready/Commit, shared motor, prediction,
  and reconciliation control medical ghosts—no parallel observer movement loop.
- Corpse `GhostOnMove` controls admission. **Observer movement/incorporeality
  belong to `observer_base → observer`, not Human.** Pass the already resolved
  observer movement through `BoundObserverAuthority.startGhost` to the lifecycle
  bridge and Begin. Do not recover rates from the corpse or add them to Human.
  Missing policy is rejected before staging; staged generations advance normally.
- Pulling uses a resolved compliant 3D tether on both endpoints. Incoming pulled
  controllers use authoritative native interpolation instead of replaying against
  current tether snapshots; outgoing-only pullers retain prediction. Release
  barriers and acknowledged input sequencing preserve this boundary.

## IMPORTANT FILES

- `SPRINT.md`: historical owner reports and validation/failure evidence;
  `PARITY.md`: detailed composition, physics, rendering, and authority invariants;
  `MANUAL.md`: retained connected checks.
- `src/main/resources/data/moonstation14/moonstation14/character/`:
  Human/Pig medical components and Observer inheritance.
- `src/main/java/com/juicyslew/moonstation14/ms14/medical_observer/`:
  `ObserverSystem`, `BoundObserverAuthority`, client camera/look and carrier policy.
- `ms14/player_body_control/lifecycle/server/`:
  `LifecycleMedicalObserverBridge`, `LifecycleGhostSessionControl`, death/staging.
- `ms14/player_body_control/client/`: `GhostControlClient`, medical prediction gates;
  `character/OwnedCorpsePhysics.java`: detached incapacitated-body motion.
- `ms14/loose_objects/pulling/`: `BodyPullService`, `TetherSpring`, `TetherMotion`,
  role-bearing rope state. `ms14/standing/`: shared prone geometry/orientation.
- `lifecycle/character/client/PlayerCharacterHarnessRenderer.java` is the actual
  character renderer override; fallback-only pose changes do not affect it.
- `ghost/client/GhostMobHarnessRenderer.java` and `GhostSpriteGeometry.java`:
  eye-centered sprite. `CharacterPrototypeTest`, `MobStatePolicyTest`, sprite
  geometry tests, and `LifecycleObserverMovementGameTests` cover relevant seams.
  Java paths above are relative to the common `ms14/player_body_control/` root
  where appropriate.

## SS14 CORRESPONDENCE

Pinned checkout: `c9df5ef5d675b0d1d226828bddf6b78c28502d91` at
`C:/Users/William/Documents/SS14-dev/space-station-14`.
Human and Pig inherit Alive=0, Critical=100, Dead=200. Pig's Critical/Dead both
use dead art; that does not eliminate Critical. Local typed prototypes support
inheritance and live resolved consumption; tests also use an inherited test pig
with Alive/Dead-only states and death at 60 to exclude hardcoded human limits.
Human has crawling; Pig does not. SS14 ghost sprites and respiratory gasp assets
are retained with existing attribution. See `PARITY.md` for upstream source paths.
The progressive tether, vertical physics/flight, and 1.15-block sprite size are
deliberate Minecraft adaptations, not upstream physics/scale claims.

## DECISIONS / WHY

- Default Human/Pig Dead threshold 200 projects to native maximum 40; Critical
  starts at 100. Ordinary healing cannot revive default Dead. `/ms14 revive`
  clears damage on the exact linked body. Corpse-first revival resumes control;
  ghosted revival requires R to return.
- Ctrl+left-click toggles dragging; C toggles prone, K rolls, Shift+K turns another
  downed body. P does not drag; F5 remains unrestricted.
- Tether defaults: stiffness 60, progressive stiffness 40, damping 6, maximum
  acceleration 160, slack allowance 1.5 blocks plus initial 3D foot separation.
  Apply owned tether impulse after friction/input/jump and before collision.
  Damping 10 caused alternating standing-controller response. Keep ordinary jumps,
  gravity, collision, and endpoint restraint; this is not a mass/density solver.
- Already-prone medical transitions preserve orientation; voluntary prone and
  knockdown start face down. Prone facing follows movement, not mouse look.
  Native renderer scale precedes setup rotations; do not apply entity scale twice.
- Ghost sprite is 1.15 blocks square, centered at actual eye height **before**
  billboard rotation/scale, with centered local vertices. Do not alter the camera
  or physical bounds to position art. Local distinct medical carrier models are
  hidden; remote carriers are suppressed through tracking, not global spectator
  invisibility. Actual bodies/ghost sprites remain visible by their policies.
- Hurt sound remains optional/off by default. Poison has no hurt feedback or
  action interruption. Critical fog, temporary death progress, wrapped chat paging,
  and gasps are implemented. Retired configuration gates must not be restored.

## KNOWN GAPS

Pig prone appearance is explicitly accepted unchanged; species-specific pose/
model configuration is future work. Dedicated opt-in hurt-sound playback coverage
is absent. Last completed full GameTests: **554/554 selected, 543 passed / 11
failed** (`build/elastic-pulling-completion-full.log`). Corpse ledge hauling and
native loose-jug motion assertions failed before later fixes; they were not rerun.
Nine other failures remain listed in `SPRINT.md` (atmosphere/ventilation/power,
puddle/slip consumers, and despawn fixture setup). Do not infer they are all one
cause or that owner acceptance makes those fixtures pass. Preserve failure artifacts.

## VALIDATION

Latest full lightweight chain: **2,173 tests / 359 suites**, zero failures/errors/
skips; GameTest compilation and server/client preparation passed
(`build/ghost-policy-source-unit.log`). Final sprite checks: **2 selected tests /
2 suites**, zero failures/errors/skips, plus Java compilation
(`build/ghost-sprite-geometry-unit.log`). No runtime launches in the final repair
batches. Independent source reviews passed; owner connected acceptance supplies
the entry/movement/rendering evidence that units alone cannot establish.

## NEXT WORK

Basic Medical Treatment may proceed separately. Deferred: medical supplies,
stripping/body inventory UI, active capabilities, Last Words/Succumb, body tossing,
and artificial-gravity controls. Preserve these ownership/prototype boundaries;
do not reopen accepted pulling/rendering while adding treatment. Run focused
tagged fixtures when appropriate and keep prior full-suite failures distinct.
