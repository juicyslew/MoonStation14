# Validation map

This is a coverage index, **not a passing test report**. Fresh invocation results
and final candidate receipts determine technical readiness.

## Production-path native fixtures

All paths below are under `src/gametest/java/com/juicyslew/moonstation14/ms14/`.

| Area | Fixture |
| --- | --- |
| Natural flight, input/ownership, floor landing, containment and medical state | `animals/flight/AnimalFlightGameTests.java` |
| Shared NPC enrollment and defensive melee/control suppression | `mobs/AnimalNpcRetaliationGameTests.java` |
| Original-body operator takeover through existing offer/ready/commit flow | `player_body_control/lifecycle/server/DevelopmentPossessionGameTests.java` |
| Actual hamster/bee/moproach pickup, identity, presentation and escape | `hands/live/PortableBodyCarrierOwnerGameTests.java` |
| One-hand equipment transfer and handless typed fitting | `hands/live/BodyEquipmentTransferGameTests.java` |
| Udder production, timed Alt+E, cancellation/access loss and transfer | `animals/UdderMilkingGameTests.java` |
| Egg output and failed-publication rollback | `animals/EggLayerGameTests.java` |
| Cleaning exchange, cooldown, production cycles, NPC/controlled entry | `mobs/AbsorbentCleaningGameTests.java` |
| Exact loose-beaker summon and filled-bottle command/spill setup | `animals/AnimalManualSetupGameTests.java` |

The registered-player/ready-driver fixtures exercise production payload handlers
with an embedded peer. They do **not** establish real-client handshake or connected
network interoperability. Source review found the new fixtures discoverable by
the unfiltered namespace/source-set configuration; an actual native run must still
report selection, completion and failures.

## Focused JVM evidence

Animal prototype tests cover inheritance, blood policy, appearance keys, source
threshold/speed overrides, capability composition and entity localization. Model
coverage checks bind resolved appearances to native layers/textures and legal
clips. Movement, retaliation, parrot memory, egg/udder/absorption policies,
portable descriptors, footprint geometry and existing client drag/drop policies
have focused tests. One-hand client policies/layouts exercise real one-slot views,
active-hand routing and swap suppression while retaining two-hand behavior. These
are not substitutes for their production-path fixtures or native visual checks.

`AnimalPrototypeTest` also loads the shipped character and reagent catalogs and
invokes the production cross-catalog validator. Resolved animal bloodstream and
metabolism references must exist in that reagent catalog; isolated character-only
fixtures cannot establish this dependency closure.

## Final serialized checks

`gate-plan.json` declares candidate compilation/jar, broad JVM regression, affected
`featureReadiness` selectors (`ui,interaction,prediction`) and the full unfiltered
GameTest suite in `build/gametest-tags-fixture`. The integration lead alone executes
those expensive checks after the whole candidate is owner-testable. Category
readiness does not certify new animal gameplay merely because shared tests pass.

For repeatable full-suite runs, its GameTest command creates a unique
`build/gametest-tags-fixture/fresh-<guid>` directory and passes that exact path as
`-Pms14GameTestDir`. Gradle's GameTest run configuration uses this property as its
`gameDirectory`, so the entire world/configuration state starts fresh; no assumption
is made about the world's level name or internal persistence path. The command
removes only that generated directory after a successful run. On failure it retains
the complete directory for diagnosis and reports its path. The existing fixture
directory, shared report log, and owner run configuration are untouched. In-run
persistence remains enabled for fixtures exercising real within-run behavior.

## Manual boundaries

Use `MANUAL.md` only after the candidate receives technical readiness. Connected
camera/control/prediction, native model/held-item alignment and appearance quality
remain owner checks. Animal clothing meshes and first-person replacement arms are
not supplied. Artwork acceptance and asset transport hashes do not certify native
rendering, gameplay acceptance or sprint closure.
