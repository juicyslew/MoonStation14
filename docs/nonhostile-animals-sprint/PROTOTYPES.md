# Ordinary non-hostile animal composition

## Reachable idle-route resolution

Pinned SS14 (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`) idle behavior is
grounded in `IdleCompound` task selection and `PickAccessible`/`GetRandomPath`:
bounded breadth-first discovery selects uniformly among discovered reachable
tiles (including the start when appropriate), then movement waits for normal
arrival. Local route planning adapts that intent to Minecraft's 3D collision and
surface model: a per-attempt surface cache, at most 40 uniform reachable
candidates, shared traversable-neighbor rules, and target A* with existing
collision/budget limits. The route consumer reuses the resolved route rather
than launching a second A*. Valid start-only routes retain ordinary arrival and
wait behavior; off-center starts normalize to a center waypoint. This is a
Minecraft geometry adaptation, not a claim of identical upstream range math;
the upstream expansion-range field is assigned but not consumed by the observed
selection path. Focused native coverage is tagged `animals_idle_reachable` in
`AnimalRouteReuseGameTests`; new behavior has not yet passed native execution.

## Reference and inclusion contract

Reference SS14 checkout `SS14-dev/space-station-14`, revision
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Upstream source entity definitions
are in `Resources/Prototypes/Entities/Mobs/NPCs/animals.yml`, `space.yml`,
`pets.yml`, `scurret.yml`, `base.yml` and `regalrat.yml`. The common simple mob
composition resolves `SimpleMobBase` → `MobRespirator`, `MobAtmosStandard`,
`SimpleSpaceMobBase` → `BaseSimpleMob`, `MobAtmosExposed`, `MobCombat`,
`MobBloodstream`, `BaseMobAnimal`. Normal simple mobs have 100 Critical / 200
Dead defaults and 4/4 walk/sprint defaults. Combat composition alone does not
imply proactive hostility; NPCRetaliation is a separate explicit capability.

New concrete breathing hosts resolve `supported_nonhostile_animal` →
`supported_animal` → `supported_animal_core` → `supported_simple_mob`; the snail
resolves `supported_animal_core` directly to
match its SimpleSpaceMobBase ancestry without a Respirator. Core typed composition
includes body/lungs/bloodstream/stomach/metabolizer, temperature and thermal
policy, simple-mob hunger/thirst, 100/200 thresholds, 4/4 speed, 5 blunt melee,
and FOOD/DRINK/IDLE task policy. `supported_animal` adds Respirator and
MobAtmosStandard's barotrauma baseline. Local lungs intentionally adapt animals
to the existing pig-lung organ prototype; this is not an upstream claim of
identical organ topology. The new non-hostile wrapper supplies the local aggregate
speech fallback without changing legacy mouse/native-pig speech composition.
Respirator damage/recovery values map MobRespirator's
2/-1 defaults; local timing follows the established animal profile. Gas exposure
and detailed per-species organ composition remain documented fidelity gaps.

### Temperature-profile audit (pinned `c9df5ef5d675b0d1d226828bddf6b78c28502d91`)

Resolved ordinary air-breathing simple mobs use `MobAtmosStandard` defaults:
Temperature current 310.15 K / specific heat 42; TemperatureDamage heat/cold
thresholds 325/260 K and rates 1.5/1; ThermalRegulator metabolism/radiated/
implicit/sweat/shivering 800/100/250/500/500 J/s, normal 310.15 K, threshold 2 K.
Local `supported_animal_core` carries those existing typed defaults. Diona nymph
inherits that standard chain unchanged. Source-specific differences are mapped:

| Source profile | Temperature override | Damage override | Regulator override |
| --- | --- | --- | --- |
| `MobBaseKobold` (`animals.yml:1601–1606`) | none (42 / 310.15 inherited) | 360/285 K; standard 1.5/1 rates inherited | none |
| `MobBaseScurret` (`scurret.yml:38–43`) | none (42 / 310.15 inherited) | 360/285 K; standard 1.5/1 rates inherited | none |
| `MobPenguin` (`animals.yml:2357–2368`) | specific heat 46; 310.15 inherited | 335/230 K; heat/cold 0.2/0.05 | none (standard) |
| `MobDionaNymph` (`animals.yml:3663–3667`) | standard defaults | standard defaults | standard defaults |
| `MobSnail` (`space.yml:385–399, 515–518`) | `SimpleSpaceMobBase` → `MobAtmosExposed`: 42 / 310.15 | source 500/0 K; heat/cold 1.5/0.1 inherited from exposed parent | exposed 800/100/500/2000/2000 J/s, normal 310.15 K, threshold 2 K |

Local typed profiles map the kobold, scurret, penguin and snail source overrides,
including the snail's exact 500/0 K thresholds, 0.1 cold rate and exposed thermal
regulator values. A zero-K cold threshold is valid absolute zero: the existing
damage projection never enters its cold-damage equation at any nonnegative body
temperature. Body mass is not inferred from sprite bounds; local mass remains its
existing adaptation.

Concrete non-hostile catalog: bat, bee, butterfly, chicken, duck, cockroach,
mothroach, moproach, cow, crab, goat, goose, gorilla, kangaroo, monkey, kobold,
lizard, slug, frog, parrot, penguin, snake, possum, named possum Poppy, old
possum, raccoon, fox, hamster, Diona nymph, reindeer buck, reindeer doe, scurret,
snail, snoth, neutral giant tarantula spider and custom pig. No rat-family host
is included. Rat king,
servant, buff rat and other hostile rats are excluded. The native pig prototype
retains its `minecraft:pig` binding; the new pig host has a separate character
profile. Native villager and sheep mappings are untouched.

The normal tarantula is `MobGiantSpider` → `MobSpiderBase` → `SimpleMobBase`,
`BaseMobHemocyanin`, `MobCombat`; angry spider prototypes instead inherit
`MobSpiderAngryBase` with an explicit Xeno faction and hostile task and are not
included. Spider has MobCombat but no upstream NPCRetaliation declaration, so it
does not acquire retaliation locally. Other retaliation profiles are only where
upstream declares it: goat (5s), gorilla (unbounded), kangaroo/fox/ancestor
primate-family (10s), and reindeer (unbounded). Monkey, kobold and scurret inherit
the one-hand plus ComplexInteraction contract from MobBaseAncestor; other fauna
are not granted fabricated hands.

At the pinned revision, `SimpleMobBase` → `SimpleSpaceMobBase` supplies `MobCombat`
(`Resources/Prototypes/Entities/Mobs/NPCs/base.yml`); `MobBaseHuman` also supplies
`MobCombat` and `MobVocal` (`base.yml`). The local `supported_animal_core` is the
only consumer of `supported_simple_mob`, so its inherited `CombatMode` references
`moonstation14:combat_mode_toggle` without per-species duplicates; human retains
its own `CombatMode` and `Vocal`. Kobold (`animals.yml:1601–1606`) and scurret
(`scurret.yml:38–43`) explicitly inherit upstream vocal composition. Their local
`Vocal` data names the shared `moonstation14:scream` / `scream` action/emote while
preserving each species' existing Speech allowed-emote set and ambient voice.
This is prototype mapping only: the local action backend/voice adapter still needs
species-correct audio integration, so this does not claim SS14 scream-audio parity.
Bat, bee and monkey do not receive `Vocal` by generic animal inheritance.

Source-specific thresholds retained: bat 30/60; bee and butterfly 10 Dead only;
cockroach 1 Dead only; mothroach/hamster/snoth 40/60; moproach 45/65; gorilla 200/250;
kobold/scurret 60/125; nymph 30/60; snail 10/20; spider 90 Dead only.
Others inherit 100/200. Source walk/sprint overrides retained: bat/butterfly 3/6,
bee 3/7, cockroach 2.5/5, mothroach 2.5/4, moproach 2.5/5, hamster 2.5/4, lizard/slug 2/3,
frog 3/6, parrot 2.5/6, kangaroo 3.5/4.5, kobold 3.5/5, nymph 2.5/4.5,
scurret 5/7, snail 2/3. Reindeer does not override the common speed/health.
Current source model keys retained for chickens (`chicken_0/1/2`), ducks
(`duck_0/1/2`), old possum (`possum_old`), Poppy (`poppy`), moproach
(`moproach`) and nymph (`nymph`). Poppy and moproach are separate named host
profiles, not randomized possum/mothroach variants. Chicken/duck
multi-variant allowlists are an explicit local random-selection adaptation; the
current base appearance component does not encode upstream subtype-specific
spawn selection. Hamlet is a named pet variant, not an ordinary random
appearance choice, and remains excluded from the base hamster allowlist. The
catalog has 40 presentation keys for 36 host entries. Chick, baby penguin and
shamebrero penguin assets have no concrete prototype/runtime selector in the
pinned source and remain unused art, not omitted host profiles.

## Runtime and adaptations

`AnimalAppearance` is resolved prototype data. Server entity initialization
selects one allowlisted key, syncs/persists it, and reselects a saved key not
present in the current resolved allowlist. `AnimalMob.captureVisualState()`
captures that key immutably. Renderer contract: `AnimalCatalog.entries()`,
`AnimalMobRegistration.types()` (model key → DeferredHolder) and
`AnimalMob.appearanceKey()`. Catalog dimensions are authored Minecraft collision
geometry: every catalog width is twice the source fixture radius. Upstream fixtures
are circles and have no vertical height; height is a deliberate Minecraft collision
adaptation and must be reviewed against the separately authored 3D model bounds.
These values do not scale the model itself.

Owner-review correction: gorilla collision dimensions are adjusted independently
of its accepted model size, to 2.01 × 2.27 blocks using the broad baked rest-pose
envelope as a Minecraft collision fit. This is not an animated swept-volume fit;
owner collision review remains pending. The original twice-radius
mapping above describes the initial import, not a parity requirement for the
revised Minecraft gorilla collider. Species-local render reductions and grounded
rig alignment are model adaptations, not changes to upstream physics fixtures.
The shared adapter scale is 0.82 for butterfly, cockroach, crab, nymph, lizard,
mothroach, and both reindeer; tarantula is 0.9. Other scales remain 1, including
gorilla and moproach. World and portable rendering consume the same scale.
Ground contact offsets affect living grounded presentation only, not flight
altitude or collider origin. Bat uses foot geometry; bee/butterfly use body contact
because the rigs have no feet. Parrot needs no contact offset. Hidden gore is
excluded from contact measurements. Visual acceptance remains pending.

PortableBody is only declared for hosts that have typed local item geometry
profiles (bee, butterfly, cockroach, hamster, mothroach, moproach). Hamster,
mothroach and inherited child moproach receive CanEscapeInventory.
The three flying simple profiles plus parrot declare the supported Flight
component. Local supported reagent IDs use lowercase concatenated namespaced paths:
`moonstation14:insectblood` and `moonstation14:copperblood` (not the source enum-like
display names `InsectBlood` and `CopperBlood`); source references map to the existing
local Blood, insectblood, copperblood, Sap and Cryoxadone prototypes for matching
species. The shipped character/reagent catalogs are cross-validated at prototype
reload and by the resource regression test. NpcRetaliation is only on
source-opt-in species and is paired with typed MeleeAttack data; the shared
simple-mob melee default and audited overrides are also resolved. The resolved
speech mapping, including explicit source voices versus text-only/default-silent
profiles, is documented in [`animal-speech-parity.md`](../animal-speech-parity.md).
No ambient vocalization is invented.
Only profiles whose upstream ordinary host supplies GhostRole expose local role
admission (mothroach, moproach, hamster, snail, kangaroo, monkey, kobold); upstream raffle
probabilities are not represented by the current local GhostRole schema.
Moproach inherits from the local mothroach profile and overrides its host binding,
name, appearance, 45/65 thresholds and sprint speed 5.0. Its resolved
AbsorbentCleaning capability supplies 2-unit pickup, a 50-unit capacity, a
half-second use delay and one-second water regeneration/non-water purge cycles.
CLEAN planning uses the shared NPC route/motor; controlled cleaning uses
`/cleanpuddle`. Source and native execution remain subject to sprint validation.
Reproduction/butchering, unique damage resistances, full space atmos, per-species
anatomy and all audio/emote/model animation metadata remain adaptation boundaries.
Udder production is wired for cow (source 30-second growth, 250 capacity) and goat;
source goat growth is 20 seconds, and both source animals produce 25 units per
eligible interval. Parrot speech learning uses its typed listener and memory contracts.

Verified udder hunger mapping at the pinned revision: `SimpleMobBase` supplies
Overfed/Okay/Peckish/Starving/Dead thresholds 100/50/25/10/0. Upstream
`Content.Shared/Animals/UdderSystem.cs:53–85` requires the threshold state to be at
least Okay: 49 blocks production; 50 and 51 allow it. The local udder comparison
uses the resolved `okay` scalar, not a human-default 150. The existing local
`HungerReducer.classify` bands cannot express this exact upstream boundary (49
is locally labeled OKAY); changing global hunger presentation/decay is deferred
rather than expanding this bugfix. Sources: `simplemob.yml:66–82`, cow/goat entries
in `animals.yml:869–923,1020–1078`, and upstream `HungerSystem.cs:189–214,254–258`.

Station-animal lifetime is not vanilla wildlife lifetime: `AnimalMob` uses the
same custom-persistence host contract as the existing mouse, so player distance
does not discard an original creature. This Minecraft adaptation does not force
the persisted `PersistenceRequired` flag or change native pig behavior.

## Owner-feedback idle and flight audit

### Natural 3D flight correction (23 October 2026)

Resolved `FlightComponent` now provides planner settings and phase durations to
the live `NpcBehaviorSystem` / `NpcFlightController` consumer. `AirNpcRoutePlanner`
uses vanilla 1.21.1 `PathFinder` and `FlyNodeEvaluator` for planner-only 3D
navigation; it does not tick `FlyingMoveControl` or `Navigation`. The shared
`MOTOR` runs once per tick through `GroundedHarnessWorldStep`. Autonomous signed
`FLYING` descent is distinct from explicit `LANDING`. Defaults are 160 grounded
ticks (8 s), then 400 airborne ticks (20 s), each randomized 0.75–1.25, with
persistent takeoff, air-travel, land-approach and landing phases.

Air routes do not require local GROUND support; support is queried only for target
landing contact, so an open-top wall is not a flight enclosure. `NpcRouteBudget`
keeps cumulative travel (40 blocks) separate from spatial bounds (12 X/Z, 8 Y),
with local/shared 700-expansion limits, 12 calls and 7,000 charged neighbor
expansions. These limits neither guarantee maze solving nor claim vanilla's exact
popped-node count. This is a Minecraft 3D adaptation, not SS14 Z-parity.

### Portable-body throw recovery candidate (23 October 2026)

Historical candidate used airborne speed multiplier 1.20; the current owner-requested
controlled-speed candidate uses 0.80 (one third lower) and strict integer
throw-recovery duration 4 ticks (valid 1–40). Authored SS14 species walk/sprint
speeds and grounded factor 0.25 remain unchanged. Voluntary flight uses the existing
shared `FlightGroundSpeedPolicy` factor for authority and prediction. In the actual
committed portable-body throw branch, `ExternalBodyFlight.launchPortableBodyThrow`
launches eligible dry, conscious, alive, unmounted, uncontained flying bodies; an
unmarked launch remains ballistic. After reaction, a flyer transfers to shared
natural flight with velocity preserved and one motor; fall distance clears only
after successful transfer. NPC recovery starts a fresh AIR phase/catch anchor and
clears the old wait. This is a Minecraft 3D adaptation, not SS14's exact catch rule:
upstream `CanMoveInAir` permits movement but does not cancel throws (`ThrowingSystem`,
`ThrownItemSystem`, `SharedMoverController` at the pinned revision).

### Pose/contact correction candidate (23 October 2026)

The newer, not-yet-gated candidate caches visible posed corpse contact against
all 42 critical/dead static bounds. Landed bat/parrot use feet and static wings;
bee/butterfly are legless, slow body-scutters (0.25 ground factor) with static
wings. Ray-gait cadence uses the resolved ground-walk baseline. Rear abdomen is
a child reusing the same 30 boxes/UV and parent pose; original head/leg paths are
preserved, with gait-only local yaw (4/6 degrees) timed to actual clip duration.
Idle/dead poses remain unchanged. Snake alive art/physics and human-approved
movement are untouched. These are Minecraft presentation adaptations, not SS14
parity claims; connected appearance remains unverified and ground-skimming stays
open. Keep active runtime PORT Java rig/curves on future asset imports; immutable
accepted R4/R5 packages are not regenerated, and no editable Blockbench source is
claimed.

At the same pinned revision, `Content.Server/NPC/NPCBlackboard.cs:16–37` supplies
`IdleRange=7`, `MinimumIdleTime=2` and `MaximumIdleTime=7` seconds. Local
`NpcBehaviorComponent` defaults of range 7 and wait 40–140 ticks match these values
at 20 ticks/second. `Resources/Prototypes/NPCs/idle.yml:13–39` picks a reachable
random point, travels, then waits; it does not prescribe two-point patrols or
orbiting. Bat inherits `IdleCompound` through `SimpleSpaceMobBase`; cow and crab
use `RuminantCompound` (food then idle). The local shared FOOD/DRINK/IDLE policy
is broader than those individual roots, not a complete HTN reproduction.

`FlyingMobBase` (`Entities/Mobs/NPCs/flying_animals.yml:1–12`) supplies 2D InAir
physics, no slipping and traversal over chasms/lava. Safe autonomous altitude in
Minecraft is an explicit port adaptation, not an upstream 3D steering algorithm.
The bat's authored native wing clip already exists; correct state projection and
runtime selection are required to display it. Exported models remain unchanged.

`MobBaseAncestor` explicitly defines one left `Hand` and `ComplexInteraction`
(`animals.yml:1348–1401`); monkey, kobold and scurret inherit it. Ordinary bat has
no upstream GhostRole. Operator development entries in `/ghostroles` must reuse
the existing trusted development-offer origin rather than alter that composition.

Source-backed variety is limited: scurret inherits monkey AI and has 5/7 movement;
cow/crab are food-first; crab/hamster have idle/moving sprite-state presentation.
Butterfly/parrot do not declare unique wander patterns. Do not describe invented
sideways crab travel, following or twitch routines as upstream behavior. See
`OWNER-FEEDBACK.md` for ongoing corrections and unverified performance boundaries.
