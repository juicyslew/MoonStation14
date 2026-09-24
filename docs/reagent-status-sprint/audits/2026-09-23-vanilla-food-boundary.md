# Vanilla food boundary — 2026-09-23

## Decision

The vanilla survival boundary has two independent routes. `FoodData.tick(Player)` is canceled at its head only for a server-side entity whose type is eligible according to `HungerSystem.isEligible`. Minecraft 1.21.1 also implements Peaceful healing and food/saturation refill directly in `Player.aiStep`; `PlayerPeacefulNutritionMixin` wraps only that method's `GameRules.getBoolean(GameRules.RULE_NATURAL_REGENERATION)` call, returning false only for an eligible server-side player. The relevant NeoForge source is `build/moddev/artifacts/neoforge-21.1.224-sources.jar`, `net/minecraft/world/entity/player/Player.java`, lines 533–550. Together these are entity-scoped replacement boundaries, not a global gamerule change. Client-side players and ineligible entities retain vanilla behavior.

Vanilla `FoodData.tick` bundles normal natural regeneration with exhaustion-to-food/saturation drain and starvation damage. Canceling only the natural-heal callsite would leave vanilla food depletion and starvation active alongside the custom `HungerSystem`/`ThirstSystem` lifecycle, so the full tick remains suppressed for enrolled character bodies. Peaceful `Player.aiStep` is a separate bypass: its healing condition does not inspect food level, so displaying a half-full food bar cannot prevent its health heal. The narrow gamerule-read wrapper skips that entire Peaceful branch for eligible character players (including its food/saturation refills), without changing the actual gamerule. Explicit healing calls—including potions, medicines, and heal commands—remain unaffected; `DamageHooks.onLivingHeal` continues routing eligible typed damage healing.

## Remaining vanilla boundaries

- The vanilla FoodData fields and HUD can remain visible while their periodic FoodData tick is inactive.
- Vanilla food consumables can still mutate FoodData fields through their item-use path; this change does not suppress ingestion or define its future character-food mapping.
- Future character/prototype/controller separation and food-item ingestion need an explicit owner decision before bridging vanilla food items to custom hunger. The current enrollment is temporary.
- This is separate from the SS14-style custom hunger lifecycle and does not provide natural health regeneration.

## Verification target

`FoodDataCharacterGameTests` exercises repeated direct FoodData ticks on an eligible server-side Player test body, checks unchanged health, typed damage and food fields, then verifies explicit healing. `PeacefulPlayerRegenerationGameTests` invokes `Player.aiStep()` on that kind of body under temporarily enabled Peaceful natural regeneration, checking health, typed damage, food and saturation, then explicit healing; the fixture restores game-server difficulty and gamerule in `finally`. Neither test establishes a connected-client result; a real client session remains a manual-world verification boundary.
