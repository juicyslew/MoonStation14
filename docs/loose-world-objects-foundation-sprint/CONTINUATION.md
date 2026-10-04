# Continuation checkpoint

**Historical checkpoint. The owner closed the sprint; read `HANDOFF.md` for current status and architecture. The unfinished/recheck statements below preserve earlier context, not current acceptance status.**

## Current request

The owner selected a loose-world-object foundation as the prerequisite for universal interactions, throwing and pulling, then authorized autonomous implementation on October 3, 2026. Four connected feedback rounds and a pickup-animation crash exposed failures beyond trusted fixtures. Start from `connected-feedback-04.md` for the latest behavior and validation; the earlier feedback documents preserve historical evidence. Shift+E contextual liquids are superseded by separate belt/alternate-world gestures; puddle sipping is now immediate until visible timed interactions exist. Owner acceptance remains outstanding.

## Direction

- Deliver a modeled, movable, interactable jug first, with shared world-object ownership, targeting, persistence, collision, throw/spin and a bounded pulling slice.
- Preserve existing hands/storage authority and window continuity. Extract jug capabilities rather than duplicating item and block-entity liquid ownership.
- Develop necessary shared interaction intents alongside objects; do not begin with a universal-key dispatcher around vanilla transient pickup entities.
- Prototype/component-driven special effects distinguish ordinary objects, spilling/breaking jugs and fused grenades.
- Use measured visuals/contact sheets before asking the owner to repeat manual sizing tests.
- Construction/crafting design and clothing remain future work.

## Completed prerequisite work

Hands/storage sprint was owner-closed October 3, 2026. `docs/hands-inventory-program/HANDOFF.md` is the current index; older `current-handoff.md` and G/H descriptions are historical. Pickaxe sprite fitting now measures actual alpha bounds, with cardinal-rotation inspection artifacts.

The owner merged main (incoming grenade/explosion work) into this branch. Conflicts were resolved and staged without a commit. Both hand/storage and grenade behaviors/tests were retained. Final validation: **1,876 unit tests and 417 GameTests pass**, logs `build/merge-main-unit.log` and `build/merge-main-gametest.log`. Passing disposable GameTest directory was removed. No conflict markers/unmerged entries remain; diff checks passed. Preserve unrelated incoming changes, especially the pre-existing staged `assets/` deletions of unknown intent.

## Repository caution

At sprint start, the owner's merge was committed: HEAD `87b2aa7`, no merge in progress. The assistant has not committed, staged, pushed or reset sprint changes. Recheck `git status`; do not stage everything or alter unrelated changes.

## Research boundaries

The legacy loose-object proposal is `docs/loose-world-objects-sprint/Instructions.md`; use its measurement/pile/collision concerns, but it is planning-only and has stale local baseline descriptions. The new plan narrows delivery; it does not establish performance guarantees.

The registered `entities/LooseItemEntity` uses its inherited item stack as the sole state owner, but replaces vanilla pickup/merge/age ticking and rendering. Stack-host inheritance keeps hands/world-storage APIs interoperable. Eligible body drops/throws and Creative mod-item drops create it. Uncontrolled Creative pickup is allowed after a short delay; controlled-body carriers cannot bypass body hands. Unrelated vanilla drops remain vanilla. The jug is now item-only: its old block and block entity are removed, while six-fill model assets remain directly baked geometry.

`ms14/loose_objects/LooseObjectProfile` and `ModLooseObjectProfiles` register with `PrototypeRuntime`; JSON inheritance lives under `data/moonstation14/moonstation14/loose_object_profile`. Missing profiles select generic or a 3D BlockItem fallback; unknown explicit IDs fail closed. Jug bounds are .5×.75 blocks, centered at feet, with directly baked six-fill models and liquid-only reagent tint. Its local glass break threshold is .5 blocks/tick, not upstream metal-pitcher parity. `LooseChunkBounds` converts block coordinates with floor-and-shift and half-open maxima. Do not use packed `ChunkPos.getX/getZ` accessors for this conversion: that bug froze real objects and matching fixture preloads masked it. Actually unloaded sweeps retain intent and retry without loading chunks.

`BodyHandItemTransfer.pickupSelected`, `BodyItemTargetProbe.probeSelected` and bounded 32-member candidates implement exact pile selection separately from legacy nearest-hint pickup. `BodyLooseThrow` preserves whole stacks, authority/revision proofs and failed-spawn compensation. Both generic and old grenade throw intents use it and publish exact `HostTransition` metadata.

`BodyLiquidInteraction` shares authenticated/trusted fill/sip transactions over copied components, integer-cent plans, source-first publication and exact known-failure compensation. Empty-hand world sip and held sip require an initialized body, explicit prototype Mouth and existing stomach eligibility. Mouth/stomach removable-organ simulation remains unsupported. Successful sip cooldown is 20 ticks; SS14 drinking audio has unresolved individual provenance recorded in `docs/audio-unknown-provenance.md`.

`BodyPuddleDrinkService` adds a 60-tick, 100-cent alternative drink action. Start uses an exact body-eye target ray; continued access uses the actual thin puddle outline center, not an assumed height above the surface. New damage interrupts; hand/aim changes alone do not. Empty-puddle cleanup is deferred until confirmed stomach admission. Command reagent validation occurs at `ItemInput.createItemStack`, not the global component codec. Native `/give` Creative dummy spawns are skipped only for mod items, preventing duplicate real objects.

`BodyPullService` reserves a hand through separate transient `LiveHands` metadata, not an occupant stack. Reservation gates prevent item entry; other-hand mutations retain it. The server tick revalidates authority and applies a bounded inward radial constraint only outside the captured center distance + .15; .95 voluntary movement multiplier, exact-target cleanup and unloaded-boundary checks are integrated. This is a Minecraft velocity solver, not a native rigid joint. Reservations are omitted from saves.

Movement snapshots now include a pulling flag, stamped for the controlled body. Client prediction consumes it before pending input replay and multiplies the shared .95 factor alongside knockdown scaling. Session reset clears the flag. The loose target is not client-predicted.

`BodyHandClient` has Q drop, Ctrl+Q generic throw, Z held use, E storage-first/target use, contextual Shift+E belt equipment or alternate liquid action, P/Ctrl+left-click pull, and ] pile cycling. `ShiftWorldUsePolicy` resolves contexts; one physical press must not dispatch both shared bindings. Standard SS14 storage bags/belts disable Z quick-equip, so no new Z equip rule is added. Generic use-then-throw waits for success and fresh proof. Screens/manual cursor suppress world-click fallthrough. Grid snapshots carry actual immutable reagent components for filled/tinted jug icons; shared `JugItemAppearance` owns six-tier presentation. Selection outline uses native render history (`xOld/yOld/zOld`) and muted blue-gray.

External impulse entry exists and is used by pulling. Existing explosion service has no impulse adapter; full atmospheric wind and new explosion impulses are not implemented. Source correspondence and adaptations are in `decisions.md`.

Known inherited grenade gap: armed bag-child stacks are not independently ticked, unlike upstream contained entity timers. Direct held/loose-root fuse continuity is implemented; do not claim general contained/offline timers.

## Next action

Latest automated validation is green: **1,904 units and all 432 required GameTests** (`build/loose-feedback-02-unit-final.log`, `build/loose-feedback-02-gametest-final.log`). The initial foundation run was 1,896/426; first feedback/crash runs reached 1,898/430. The second-pass coordinate guard, actual loaded/unloaded motion, command ingress and delayed puddle ingestion have live regression coverage. All current behavior, short recheck and remaining test limits are in `connected-feedback-02.md`. Fixed throw speed stays .75; charged throws/render-distance changes remain out of scope. No owner closure is implied.

Final logs use `build/loose-foundation-unit-final.log` and `build/loose-foundation-gametest-final-prediction.log`. The successful final fixture was removed after SHA-verified retention of 150 raw timing samples, summary and environment in `build/reports/loose-object-measurements/`. Benchmark distinguishes actual settled counts and continually jostled active counts; it is direct object work, not total-server/client/network/player-capacity proof. Run variability and a settled tail outlier are recorded in `implementation.md`; performance capacity is not established. The software visual sheet is `build/reports/loose-object-visuals/jug.png`; it was inspected, but is not a Minecraft viewport capture.

The latest inspected sprite diagnostic is `build/reports/loose-object-feedback-sprites.png`, asserting the production pose as well as actual alpha bounds. New raw measurements live at `build/reports/loose-feedback-measurements/`: enlarged active footprints show synthetic p95 7.336 ms, above the provisional 5 ms guardrail. Preserve that risk; no player-capacity proof exists. An earlier final run had an unrelated power-path UNKNOWN failure; its world/log are retained for optional power investigation, with no power changes included.

**Subsequent connected blocker:** the owner's next check crashed on pickaxe pickup animation. `run/crash-reports/crash-2026-10-03_15.35.28-client.txt` shows `ItemPickupParticle` dispatching a plain `ItemEntity` with custom loose entity type to the typed loose renderer. Native `ItemEntity.copy()` preserves entity type but not Java subtype. A covariant loose-item copy must preserve the registered subclass and presentation state without adding another world owner. See the crash follow-up in `connected-feedback.md`; prior green server/pose tests did not cover pickup animation.

The copy override is now implemented with registered subtype construction, `copyPosition` and a deep stack copy, not full entity NBT restoration. It retains visual rotation/settled state and has fresh identity without world registration. The regression calls through the same `ItemEntity` virtual method as the native pickup particle and checks source isolation; dedicated validation logs use the `build/loose-pickup-crash-*.log` prefix. Do not substitute a broad renderer fallback or disable pickup particles for this subtype-copy bug.

Crash-fix validation is green: 1,898 units and 430/430 GameTests, with the actual named/damaged pickaxe regression freshly compiled before the final run. Final server log: `build/loose-pickup-crash-gametest-final.log`. Compiled covariant dispatch was also verified with `javap`. Connected client animation still needs owner recheck; no viewport acceptance is claimed.

Owner recheck steps are in `connected-feedback.md`. Charged throws/render-distance changes remain out of scope. Do not claim owner sprint closure or create an owner-accepted handoff before the owner declares completion. No compaction tool is exposed; this checkpoint preserves continuation context, not actual conversation compaction.

The second-pass raw measurements are in `build/reports/loose-feedback-02-measurements/` (150 samples, hash verified): settled 1,024 p95 .771 ms, continually jostled 256 p95 7.299 ms. Heavy active work remains above the provisional 5 ms direct-work guardrail. The newest connected recheck is in `connected-feedback-02.md`; no new Minecraft viewport or paired-player acceptance is claimed.
