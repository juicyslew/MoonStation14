# Second connected feedback

Superseded where noted by `connected-feedback-03.md`, especially the contextual Shift+E decision and storage footprints. The validation below belongs to the second pass, not the subsequent changes.

The owner reported another twelve observations after the pickup-copy crash fix. This pass is not owner acceptance; prior trusted fixtures masked a real coordinate-conversion error.

## Corrections

- **Motion:** `ChunkPos.getX/getZ(long)` decode a packed chunk position; they do not convert world block coordinates. The loose motion guard incorrectly passed floored block coordinates to them, checking unrelated chunks. `LooseChunkBounds` now uses floor-and-shift (`>> 4`) for both axes and half-open maximum bounds. Related fixture preloads used the same wrong conversion and have been corrected. Chunk eligibility uses the actual sweep, not an epsilon-expanded phantom neighbor. The guard still refuses an actually unloaded swept path without force-loading it. Slipping changing apparent behavior is consistent with spatially wrong checks, but a slip-triggered causal trace has not been captured.
- **Outline:** muted blue-gray, with its box moved to the entity's partial-tick rendered position before camera subtraction. Interpolation uses `xOld/yOld/zOld`, matching native `LevelRenderer.renderEntity`, rather than the separate eye/look interpolation history. A named presentation color is sufficient for now; player theme configuration remains future work.
- **Shift+E:** contextual belt equipment and alternate liquid actions coexist. Held belt equipment takes its existing route; held liquids use alternate transfer. An empty hand aimed at a loose liquid container or puddle chooses ingestion rather than manipulating a worn belt. Existing belt fallback remains when no liquid target takes precedence. World actions remain suppressed in screens/free-cursor mode.
- **Z parity:** upstream standard backpacks and storage belts set `Clothing.quickEquip: false`. Some clothing/cosmetic belts opt in. No new Z-to-equip rule is added for the port's standard storage bag/belt; Shift+E remains the local accepted equipment shortcut.
- **`/give`:** native successful inventory admission spawns a temporary dummy, then marks it fake *after* the drop returns. Creative loose conversion happened before that marking, retaining a second real item. The narrowly scoped give-command hook skips only this mod-item Creative dummy branch. Real survival full-inventory overflow and unrelated vanilla paths remain intact. Native Creative infinite-material behavior on a full inventory is unchanged; the port does not invent another overflow rule.
- **Invalid reagents:** command-created item stacks are validated at `ItemInput.createItemStack`, after components are applied but before inventory/world publication. Unknown positive reagent prototypes produce a command error instead of creating an unusable jug. The general persistence/network codec is not changed to consult a server-only catalog.
- **Item-only jug:** the placeable jug block, block entity and their runtime registrations are removed. Its six-fill model assets remain as directly baked render geometry, without requiring a registered block state. The jug item and its authoritative REAGENT component remain.
- **Storage appearance:** body and world grid snapshots now carry each owned stack's immutable reagent component. Storage icons render the fill layer and blended tint. Shared appearance logic preserves negative packed ARGB colors and uses six integer fill tiers; world and ordinary item rendering use the same tier selection. Glass remains untinted; liquid quads/layers receive reagent color.
- **Puddle ingestion:** empty-hand Shift+E adapts SS14's alternative Drink verb: one unit after three seconds. Mouth, initialized body, stomach admission, source access and authority are required. New damage interrupts; expiration of an old hurt cooldown does not. Self-ingestion does not cancel merely because hands or aim change, but the source must remain accessible. Source-first publication defers empty-puddle removal until verified admission, with conditional known-failure compensation.
- **Throw strength:** fixed speed remains 0.75 blocks/tick. No further tuning is inferred from throws that were freezing; charged throwing and render-distance changes remain out of scope.
- **Diagnostics:** a bounded recent-object stall/resume watcher distinguishes entity-tick eligibility from a blocked motion sweep without scanning the world or changing physics.

## SS14 reference

Pinned SS14: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `Resources/Prototypes/Entities/Clothing/Back/backpacks.yml`, `Clothing/Belt/base_clothingbelt.yml`: standard storage quick-equip disabled.
- `Content.Shared/Clothing/EntitySystems/ClothingSystem.cs`: component-driven quick-equip handling.
- `Resources/Prototypes/Entities/Effects/puddle.yml`: base Puddle is `Edible: Drink`, delay 3, transferAmount 1, optional Spoon.
- `Content.Shared/Nutrition/EntitySystems/IngestionSystem.API.cs`: alternative ingestion verb and admission; main system handles do-after and stomach transfer. `BadDrink` is not a player-ingestion blocker.

## Connected recheck

1. Walk to several positive/negative coordinates, repeatedly Q-drop and Ctrl+Q-throw. Objects should keep falling/moving; the subtle outline should track them smoothly.
2. Shift+E a held belt, then use held/loose jugs with Shift+E while wearing a belt. Neither liquid action should accidentally manipulate that belt.
3. Empty-hand Shift+E a puddle; wait three seconds for a one-unit drink. Damage should cancel without consuming it.
4. `/give` a mod item: only one real owner. Misspell a jug reagent: command fails and creates nothing. Valid jugs should show their fill/color in storage and cannot place a jug block.

## Validation and limits

- **1,904 unit tests**, zero failures/errors/skips; main/test/GameTest compilation passes. Log: `build/loose-feedback-02-unit-final.log`.
- **All 432 required GameTests pass** in the final fresh run. Log: `build/loose-feedback-02-gametest-final.log`.
- Live motion coverage invokes production `LooseItemEntity.tick()` with only the actual far-away chunk loaded, verifies no forced neighbor load, blocks a genuinely unloaded crossing without settling or losing velocity, and resumes after test-only loading. This complements the negative/positive/exclusive-boundary conversion checks.
- Actual native command tests cover one-copy Creative `/give`, valid reagent components, unknown-reagent rejection without publication, native full-Creative behavior and real survival overflow.
- The registered puddle fixture covers no immediate/early consumption and a conserved 100-cent transfer after 60 ticks. Empty-puddle cleanup has a separate committed/noncommitted regression. Independent source review covers new-damage interruption, authority, access and conditional compensation; a rejected-stomach-publication fault-injection fixture is not included.
- Body/world grid packet round trips retain the actual 7,500-cent jug mixture and placement metadata. Fill-tier and negative-ARGB tests pass. A new storage-icon compositing sheet was not produced; actual client appearance still needs the short connected recheck above.
- `git diff --check` passes. Source reviews covered motion, context routing, native liquid-only model tint and ingestion conservation. No client viewport or paired-player check was performed.
- Latest raw benchmark evidence: `build/reports/loose-feedback-02-measurements/` (150 samples, hash-verified). Direct-work p95: 1,024 settled **0.771 ms**, 256 continuously jostled **7.299 ms**. Active work still exceeds the provisional 5 ms guardrail; these are not whole-server/network/client or player-capacity measurements.

Successful disposable validation worlds are removed. Earlier unrelated failure artifacts/logs remain where useful. No owner acceptance, commit or staging is implied.
