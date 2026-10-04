# Future extensions after hands/storage

Recorded at owner-requested sprint closure, October 3, 2026. These are future design/implementation candidates, not commitments or completed features. Investigate SS14 and relevant subsystem handoffs before choosing behavior. Preserve prototype/component-driven architecture, server authority and attribution; deliberate departures should be documented.

## 1. Universal interaction keys

Unify Left Click / E / Z and their different default uses into a coherent interaction model. Establish SS14's actual context, target, alternate-use and held-item rules first; do not simply assign one fixed operation to each key. Keep actions rebindable and resolve world/UI precedence explicitly so storage clicks cannot accidentally trigger world interaction.

Provide a universal throw action for **any held object**, distinct from Q drop. Shift+Q or Ctrl+Q are owner-proposed candidates, not finalized bindings. Ordinary objects share launch behavior; prototype/components supply special effects, such as jug breakage/spillage. Avoid separate per-item throw controls. Coordinate this with movable world-item entities rather than treating throwing as a jug-only feature.

## 2. Construction design

This needs a design phase before implementation. SS14 construction and crafting are distinct, and Minecraft's systems differ from both. The target is to **feel like SS14 construction**, not blindly copy recipes whose spatial/material meaning has changed.

In particular, SS14's four steel for an entire wall does not establish a sensible price for each Minecraft-sized wall segment. Decide the physical construction unit and resulting material economy together. New recipes and methods are explicitly acceptable where necessary.

Useful design questions:

- What corresponds to an SS14 wall/tile or construction assembly in Minecraft space?
- Which tasks are staged, tool-driven construction versus crafting?
- How do placement, intermediate assemblies, material consumption, completion and dismantling work?
- How are recipes/construction graphs represented through prototypes rather than hard-coded item exceptions?

Prototype a small wall/frame or comparable construction loop, including dismantling, before broad recipe coverage. No recipe values or dimensional conversion are settled yet.

## 3. Interactable 3D world-item entities

Lay groundwork for dropped objects that are persistent, modeled and interactable—not bobbing, transient pickup icons. At rest they should stay put until jostled. They are **not blocks or block entities**: they must support overlap, fractional block positions and movement, without inherently blocking the player as blocks do. Despawn/settling/contact rules need deliberate design rather than inheriting vanilla item behavior unnoticed.

Support displacement by space wind, explosions and throws. Preserve item-owned state across held, stored and world forms; an object's model and interaction capability should not be tied to a block-grid position. Keep physical identity, interaction targeting, rendering and networking responsibilities separate where practical.

**First vertical slice: the jug.** Reuse its current model and investigate its block-entity liquid interactions. Move filling and sipping capabilities into reusable item/entity behavior, then expose them on a movable modeled jug. Throw the same modeled object with launch motion and spin. Jug-specific breakage/spillage is specialized behavior; most ordinary thrown items should not break or spill.

This is broader groundwork for world objects, not permission to implement all physics, fluids or explosions in one sprint. Validate a jug retaining contents across pickup/drop/storage, stationary interaction and a throw before expanding coverage. Avoid a second mutable inventory/contents owner during representation changes.

## 4. Clothing

Record clothing as a separate follow-on area. Research SS14 equipment slots, garment prototypes, appearance layers and gameplay components, then decide their Minecraft presentation. Detailed scope is not yet agreed. Reuse the completed equipment/authority infrastructure rather than creating a parallel inventory system.

## 5. Visible timed interactions and continuous ingestion

The owner explicitly deferred continuous drinking during the final jug feedback. Design its start/stop controls and visible progress alongside a generic timed-interaction system; do not implement an invisible repeating timer. Puddle drinking is temporarily an immediate one-unit action per explicit request, by owner direction.

For future parity research, pinned SS14 `IngestionSystem.OnEatingDoAfter` uses `IngestedEvent.Repeat`; repetition is not unconditional and ends on full consumption/destruction or cancellation. Establish the drink-specific repeat policy and explicit stop input before claiming “continues until actively stopped.” Held-item ingestion and puddle verbs are distinct paths. These are future requirements, not part of the current corrections.

## Suggested sequencing and additional ideas

**Subsequent owner-directed sequencing:** world-object foundations are prerequisites for universal interactions, throwing and pulling. The owner authorized [Loose World Objects — Foundation + Jug](../loose-world-objects-foundation-sprint/Instructions.md) on October 3, 2026, with necessary interaction intents developed alongside it. This supersedes the interaction-first recommendation below; see that folder for implementation status and validation.

1. Establish universal interaction intents and targeting rules; retain provisional bindings until the actions are coherent.
2. Build the modeled-jug entity slice alongside universal throwing. Its targeting and identity rules become foundations for other world items.
3. Give construction a dedicated design spike, then a small playable vertical slice. It can be designed independently of clothing.
4. Scope clothing as its own prototype/equipment/appearance slice.

Additional recommendations, not owner requirements:

- **Interaction feedback:** a small contextual target/action cue, derived from the same authoritative eligibility rules as interaction—not an independently hard-coded tooltip system.
- **Automated visual regression fixtures:** extend the real-asset contact-sheet approach to modeled items and clothing so scale, rotation, alignment and clipping are checked before asking for repeated manual tests.

Do not turn these recommendations into unrelated expansion of the authorized foundation-and-jug sprint.
