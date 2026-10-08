# Metal rod prototype audit

Verified against Space Station 14 commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Resolved upstream composition

`PartRodMetal` (`Resources/Prototypes/Entities/Objects/Materials/parts.yml`) inherits `PartBase`, which inherits abstract `BaseItem` (`Resources/Prototypes/Entities/Objects/base_item.yml`). The resolved base gives it Item size Small (overridden by the child to Normal), Animated/Clickable/InteractionOutline, pressure and collision sound behavior, high-speed impact damage, collision wake, gravity, dynamic physics with a quarter-block AABB fixture, Items draw depth Sprite, Pullable, and DamageExaminable. `PartBase` adds rods Sprite and Item sprites, inorganic/flimsy-metallic damageability and destruction at 100 damage, and ConstructionMaterial tag. `PartRodMetal` adds physical Steel composition 50; Stack type MetalRod; rods stack layers/sprite; Item size Normal/held prefix; MetalRod construction graph node; Appearance, FloorTile outputs lattice/reinforced floor, zero static price, stack price 7.5, extractable rod solution (4.5 Iron/0.5 Carbon), and guide help.

`MetalRod` in `Resources/Prototypes/Stacks/other.yml` inherits `BaseSheetStack` (`Resources/Prototypes/Stacks/base_stacks.yml`), hence maximum 30, names `stack-rods`, uses rods icon, and spawns `PartRodMetal1`; that single-item child sets stack count 1. Stack component consumers include `SharedStackSystem` (merge/split/count visuals), `FloorTileSystem`, material storage/reclaimer, and construction graph material completion. Graph `Resources/Prototypes/Recipes/Construction/Graphs/materials/metal_rod.yml` consumes one Steel and sets output stack count to two; default graph do-after is zero.

## Local composition and deliberate departures

Local `ModItems.METAL_ROD` is a plain, max-30 Minecraft item with the explicit `metal_rod` loose-object profile. Its resolved `metal_rod` item-footprint profile inherits the local `normal` profile (four cells), so body inventory and loose-object paths use the resolved profile/catalogue rather than a rod-specific hardcoded size. It is in the existing MoonStation item catalogue. The upstream `rods.png` is retained as a flat generated-item sprite with source attribution and pinned-source hash.

This item is not a full SS14 entity composition: local runtime has no supported equivalents for the upstream entity components, and this work intentionally does not introduce unsupported lattice placement, combat/damage, physics, crafting graph, reagent extraction, pricing, tags, or construction behavior. In particular, upstream has `PhysicalComposition` on the rod but not `Material`; no local `Material` component is invented. Minecraft max stack 30 and Normal body-inventory size are explicitly represented. No existing steel or plastic stack limit is changed.
