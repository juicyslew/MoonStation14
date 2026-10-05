# Loose World Objects — Foundation + Jug

## Status and purpose

**Implementation authorized October 3, 2026.** The owner directed autonomous execution through completion. Resolve uncertainties from SS14 and local evidence, make bounded decisions where necessary, and record consequential adaptations for owner judgement. Connected owner acceptance must still be distinguished from automated evidence.

Build the world-object foundation that universal interaction, throwing and pulling actually need, using a movable, interactable jug as the first complete vertical slice. Universal keys must grow from this playable object contract—not from a dispatcher designed around transient vanilla pickup icons.

This plan narrows [the existing loose-object proposal](../loose-world-objects-sprint/Instructions.md); it does not discard its targeting, collision, ownership or measurement concerns. Its older G/H control descriptions are historical. Use [the completed hands handoff](../hands-inventory-program/HANDOFF.md) for current behavior and [the explosions closeout](../explosions-sprint/closeout.md) for merged grenade support.

## Intended outcome

A jug rests as its recognizable 3D model at arbitrary world coordinates, without bobbing or automatic rotation. It can overlap other small objects and does not block the controlled body. It can be selected, filled, sipped from, picked up, dropped, thrown with spin and pulled without becoming a block or duplicating its contents. Ordinary objects reuse the foundation; jug spill/break behavior and grenade fuse/detonation are composed special behaviors.

The same object's state survives representation changes. A held stack and a world object must never simultaneously be authoritative owners of its contents. Pulling changes motion/control, not ownership into a hand. Settled objects remain until interaction, removal or a physical cause moves them; settling is not despawning.

## Scope and boundaries

### Included

- A bounded mod-owned entity representation with item state, stable world identity, fractional position, orientation, persistence, motion/settling and interaction targeting.
- Generic hosting of eligible stacks through controlled-body drop/throw routes, with the jug as the modeled, liquid-capable acceptance slice.
- Exact, bounded selection of visible members in overlapping piles, validated server-side.
- Shared interaction intents needed for pickup, held-item use on a target, alternate/self use, drop, throw and pull. Preserve fresh authority, epoch, revision, evidence and reach checks.
- Universal throw support for eligible held objects. Q remains drop; Shift+Q or Ctrl+Q are candidate rebindable throw defaults, not final decisions. Integrate incoming grenade behavior rather than leaving a second incompatible throw owner.
- Jug fill/sip capabilities usable on held and loose forms, retained contents, and prototype/component-driven break/spill behavior.
- A bounded pulling slice once its constraint/motion contract is established.
- Measured representative piles, save/load behavior and automated visual fixtures.

### Excluded

- Construction/crafting recipes, clothing, large machinery implementation, broad new item content and a complete universal verb/menu system.
- A general rigid-body engine, full pressure-wind implementation, explosion rebalance or new fluid chemistry.
- A global vanilla renderer/pickup/despawn replacement, unrelated Creative behavior changes, mod compatibility, migrations or crash-durable transaction guarantees.
- Record/entity hybrid storage or large-scale networking infrastructure without measured need. Objects remain entities at rest and while pulled.

The design must distinguish small non-body-blocking objects, large body-blocking unanchored objects and anchored fixtures. Implement only the small-object category now; document the extension seam without adding vending-machine behavior.

## Architectural invariants

1. **One authoritative owner:** reuse `LiveHands`, item components and existing conditional publication/compensation. Inventory↔world is a transfer, never a client-created copy. Preserve whole-stack semantics unless a specific capability requires an explicitly designed split.
2. **Separate identity from location:** moving, settling, throwing and pulling retain a world object's identity. Pickup/drop boundary mappings remain authoritative. Open storage observations follow their host until manually closed or materially invalid, not until an animation/representation transition occurs.
3. **Composition:** separate object state, rendering, motion, interaction and specialized effects. Reuse existing prototype catalogs/component conventions; do not add per-item input branches or parallel jug liquid owners.
4. **Server motion and actions:** clients nominate targets and may present responsive previews; server state decides ownership, collision, contents and effects. Explicitly choose transform synchronization/interpolation and any prediction in M0. Never advertise an unimplemented prediction scheme.
5. **Bounded work:** targeting, collision probes, pile candidates and impulse handling have explicit bounds. Unloaded targets fail closed. Audit collision callbacks rather than assuming a loaded-only getter prevents all chunk loads.
6. **Resting is distinct from simulation cost:** no bob/spin at rest; orientation is retained. Measure idle cost before claiming sleeping or zero entity tick cost.
7. **Visual evidence before owner loops:** inspect actual model renders and asset-derived fixtures for scale, pivot, ground contact, rotation and clipping. Geometry-only tests are insufficient evidence of appearance.

## Ordered work units

### M0 — Establish parity, contracts and baseline

Investigate pinned SS14/Robust sources for floor-item identity, storage/interaction access, throw range/velocity/spin, collision/settling, jug/container behavior and pulling. In particular, verify distance-joint behavior and conditional `NeedsHands` virtual-item use; neither is a ready-made Minecraft API.

Audit local jug, grenade, body movement, item components, world-storage sessions and transfer code. Establish:

- Eligibility/actor matrix for controlled body, carrier, unrelated vanilla/Creative actors and NPCs.
- Persistence, lifetime, stacking/merging and whole-stack versus exceptional split policies.
- World model pivot/scale, small-object collision and ground contact; prototype-driven mass/shape/impulse seams.
- Target selection, reach/occlusion, input precedence and provisional action/key table.
- Throw/settle and pull contracts, hand conflicts and cleanup on body epoch changes.
- A deterministic baseline/pile fixture and measured tick/frame/update costs.

**Exit:** concise design decisions and SS14 correspondence are recorded. Escalate only material unresolved choices, not routine implementation questions. Use the prior proposal's 20-typical/100-worst-case workload goals as future measurement references, not demonstrated capacity or mandatory synthetic-client infrastructure for this slice. Agree budgets after baseline evidence; reserve headroom for the rest of the server.

### M1 — Entity lifecycle, ownership and model

Implement the mod-owned world-object representation and integrate authoritative pickup/drop for eligible controlled-body items. Preserve actual item components and storage-host continuity. Reuse the jug model with a world transform; do not replace every vanilla `ItemEntity` globally. Set explicit retention and merge rules, including identity/content-sensitive objects.

Validate fractional positions, overlapping objects, world collision without body blocking, stable ground contact/orientation, chunk unload/load and save/restart. Keep unrelated vanilla/Creative routes unchanged.

**Exit:** the modeled jug retains state across held↔world moves and normal persistence; no bobbing, duplicate owners, content loss or accidental player obstruction. Inspect model captures before requesting connected visual acceptance.

### M2 — Targeting and jug interaction capabilities

Implement bounded pile selection and shared action eligibility/resolution. A client nomination identifies the intended visible member; the server rechecks actual ownership, body authority, reach and visibility. Define overflow, stale candidate and contested-selection behavior. Provide a modest target/action cue from the same eligibility model.

Move jug filling/sipping logic into reusable item/entity capabilities. Existing `JugBlockEntity` attachment contents and `JugItem` component contents are separate today; do not perpetuate that split as two authoritative world owners. Keep placed-block behavior only where still needed, sharing capability logic rather than duplicating it. Do not silently invoke `JugItem.use` on the vanilla carrier.

**Exit:** loose and held jug actions operate on the correct object/contents; two-actor contests, stale/occluded targets and UI clicks preserve ownership. Storage interactions, manual cursor and hand-selection behavior remain intact.

### M3 — Shared throwing, spin and impulses

Implement universal throw intents on the world-object foundation, distinct from reliable Q drop. Establish bounded launch, angular motion, contact response and settling from M0 parity findings. The same model travels and spins, then settles; a throw is not a transient projectile that duplicates a floor item.

Integrate grenade fuse/deadline/beep/bounce behavior and jug break/spill through specialized components. Ordinary objects do not acquire jug breakage. Reuse existing reagent spill paths and preserve conservation under rejected throws and failed spawns.

Provide a bounded external-impulse entry point. Connect existing explosion behavior where an actual suitable impulse exists; audit atmosphere wind and document unavailable integration rather than implementing a full wind solver or claiming metadata constitutes motion.

**Exit:** ordinary item, jug and armed grenade exercise shared transfer/throw behavior; fuse deadlines and contents survive transfers, and rejected actions leave the source intact. Old grenade N/M controls are retained only as documented transitional aliases if needed, not another independent authority path.

### M4 — Pulling with stable world ownership

Implement a small, server-authoritative pulling slice using the chosen constraint/motion route. The target remains the same world entity. Define range, obstruction, start/stop, competing actors, held-item conflicts, and cleanup on stun, lease/body loss, removal and unloaded boundaries.

If SS14 correspondence requires a hand reservation/virtual indicator, represent it explicitly and separately from real held stacks. Current `LiveHands` has no pull-hand reservation; do not fake one by inserting the pulled item's stack. Do not change representation when pulling begins.

**Exit:** start/move/stop and invalidation preserve identity and contents, do not drag through blocking world geometry, and never duplicate the target. If the engine audit reveals an essential unsupported behavior, stop for a concrete decision rather than substituting teleport-follow or silently dropping pulling.

### M5 — Regression, measurement and connected acceptance

Run focused units, live server conservation/lifecycle fixtures and a serial integration suite. Measure sparse, dense-pile, settled and active-jostle workloads against M0; label synthetic loads separately from authenticated connected clients. Report costs and limits, not unsupported player-count guarantees.

Generate and inspect model/selection contact sheets or reproducible client captures before a concise owner test covering all jug actions, overlapping selection, storage continuity, throw/settle, pull cleanup and unrelated actor boundaries. Require relevant two-client contest evidence without pretending trusted fixtures prove authenticated input.

**Exit:** acceptance evidence and outstanding limitations are explicit. Close with `HANDOFF.md` using the project template. Expand to the old proposal's scale/large-object milestones only in separately scoped work.

## Starting points

- `docs/hands-inventory-program/HANDOFF.md`: current authority, transfer, popup and measured sprite architecture.
- `docs/explosions-sprint/m3-player-grenade.md`: temporary controls, fuse/throw behavior and unverified balance/audio boundaries.
- `item/custom/JugItem.java`, `block/custom/JugBlock.java`, `block/block_entity/JugBlockEntity.java`: current placed/item liquid ownership and interactions.
- `entities/ThrownJugEntity.java`, `entities/ThrownGrenadeEntity.java`, `entities/ModEntities.java`, `MoonStation14Client.java`: current entity/model registration and bespoke thrown renderers.
- `ms14/hands/live/BodyHandItemTransfer.java`, `BodyItemTargetProbe.java`, `LiveHands.java`; `hands/network/BodyWorldGridService.java`: authoritative transfer, targeting and observation seams.
- `ms14/hands/live/BodyGrenadePrime.java`, `BodyGrenadeThrow.java`, `BodyGrenadeHeldFuseService.java`: merged grenade integration.
- `ms14/explosion/server/ServerExplosionService.java`; `ms14/player_body_control/character/GroundedHarnessWorldStep.java`: explosion and actual controlled-body movement integration points.
- Existing jug, pickup, grenade and world-transfer GameTests; `GridEntrySpriteRendererTest` demonstrates real-asset visual diagnostics.

Paths above are relative to `src/main/java/com/juicyslew/moonstation14/` unless prefixed with `docs/`. Verify actual source and relevant subsystem handoffs before implementation; historical capability proposals are not current authorization.
