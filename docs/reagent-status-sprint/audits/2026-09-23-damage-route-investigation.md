# Damage-route regression investigation — 2026-09-23

## Scope and result

Added focused server GameTests in `DamageRouteGameTests` to separate vanilla damage mirroring from reagent metabolism timing and typed-effect delivery. No production change was justified by the observed behavior: the existing server damage post-hook imported the tested player fall hit, and bloodstream metabolism dispatched polytrinic acid's caustic `HealthChange` through the typed damage system. The reported symptoms are therefore not reproduced as a general server-side damage-route failure in this worktree.

## Test observations

- **Player fall route:** A non-creative/non-spectator `Player` test double was given a pre-existing slash ledger entry and consistent missing health without a preceding `hurt` call (to avoid Minecraft's player hurt-resistance window). Calling `player.hurt(level.damageSources().fall(), 5f)` reduced vanilla player health, appended blunt ledger damage corresponding exactly to the actual health delta times `DamageSystem.TYPED_PER_HEALTH`, and preserved the slash entry. A vanilla Player's actual applied amount can differ from the requested raw amount; the assertion intentionally verifies the actual applied amount rather than assuming raw `5f` always becomes `25` typed units. Damage classification has no special fall tag and therefore uses the documented blunt fallback. A physical fall from a height was not used; the test exercises the same vanilla `DamageSource.fall()` delivery and post-damage hook directly.
- **Bottle -> stomach -> bloodstream:** A real registered Bottle `ItemStack` with 5 units of polytrinic acid was passed through `StomachSystem.ingest` for an eligible Villager. Ingestion emptied the source into stomach only, with no immediate body reagent or typed damage. First `TickHooks.runDueActivities(...REAGENT_METABOLISM...)` processed body before stomach: stomach fell to `4.75`, and its 25 cents transferred at half efficacy as 12 body cents (`0.12`); health and damage ledger remained unchanged. On the following metabolism pass the bloodstream route applied typed caustic damage, and server health decreased by a subtle amount under `0.1` (the intended small-dose outcome, not an absent effect).
- **Direct bloodstream isolation:** Seeding 3 units directly in a Villager's body and running one metabolism pass applied the configured `11` typed caustic damage at scale one and projected `2.2` vanilla health damage. This isolates effect execution/damage projection from ingestion delay.

The existing Bottle interaction GameTest independently covers the registered item's sip callback, and this new test uses the actual Bottle item as the ingest source but invokes the provider-backed ingestion operation directly to make the two metabolism passes deterministic.

## Diagnosis / remaining factors

The damage producers are functional, but investigation of the reported “instant heal” exposed a separate vanilla sink: besides `FoodData.tick`, Minecraft 1.21.1 `Player.aiStep` has a distinct Peaceful branch at `build/moddev/artifacts/neoforge-21.1.224-sources.jar`, `net/minecraft/world/entity/player/Player.java`, lines 533–550. With natural regeneration enabled, this branch heals every 20 ticks without checking food level, and refills saturation/food on its own cadence. Therefore suppressing `FoodData.tick` and lowering the visible food bar were insufficient. `PlayerPeacefulNutritionMixin` now narrowly wraps only the `RULE_NATURAL_REGENERATION` boolean read in `Player.aiStep`, returning false for eligible server-side character players and delegating unchanged for all other calls. It does not alter the gamerule, suppress general healing, or interfere with ordinary player `aiStep`; explicit medicine-style `Player.heal` remains active and updates the typed ledger.

Fall damage is delivered to the typed ledger for a Player, including preservation of unrelated damage. Polytrinic acid is configured for bloodstream metabolism only; it is not stomach-digested into effects on the sip tick. The stomach-to-body transfer occurs after that pass's body-processing phase, so damage begins on a later metabolism activity; subsequent low body concentrations scale the configured effect down. A visual observer may not readily notice a sub-0.1 health change per pass. Whether the user's environment has other mods, armor/effects, nonstandard server damage configuration, or a client-side health display issue remains untested; there is no connected-client proof, and manual-client uncertainty remains with the project owner.

## Validation

- `gradlew.bat test compileJava compileGametestJava build` — passed (`BUILD SUCCESSFUL`).
- `gradlew.bat runGameTestServer -Pms14GameTestDir=build/gametest-run` — passed; `build/gametest-run/logs/latest.log` reports **All 83 required tests passed**, including `PeacefulPlayerRegenerationGameTests`. Launch output confirms `PlayerPeacefulNutritionMixin` mixed into `net.minecraft.world.entity.player.Player` on the dedicated GameTest server.
- The full GameTest log includes one intentional error-level trace from an existing missing-status-definition test (`StatusEffectGameTests.statusDerivedHandlersCoverOperationsAndQuietNoOps`), which tests the controlled `ModifyStatusEffect` failure path. It is not a failure from the added tests; no added damage-route test logs an application error.
- Python JSON sweep — parsed 602 resource JSON files.
- `git diff --check` — passed; Git emitted only pre-existing LF-to-CRLF working-copy warnings.

## Peaceful regeneration regression

`PeacefulPlayerRegenerationGameTests.eligibleCharacterSkipsPeacefulNutritionButExplicitHealingWorks` exercises the exact `Player.aiStep()` route on an eligible Player test double with Peaceful difficulty and natural regeneration enabled only inside a `try/finally` fixture. It checks no health/typed-ledger change and no food/saturation refill at tick zero, then checks explicit healing still works. A test double successfully invoking `aiStep` is server-side code-path coverage, not a connected-client verification.
