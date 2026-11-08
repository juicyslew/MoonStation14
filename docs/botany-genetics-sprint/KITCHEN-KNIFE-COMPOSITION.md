# Authentic Sharp tool candidate

Source audit and bounded production binding at verified SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Owner explicitly authorized existing knife artwork; only its icon is imported.

## Source composition

`KitchenKnife → BaseKnife → BaseItem` (`Objects/Weapons/Melee/knife.yml` 3–65). BaseKnife composes Sharp, Utensil Knife, Tool quality **Slicing**, MeleeWeapon Slash 10 with bladeslice hit sound/−135° wide rotation, four-second Execution, Knife tag and Small item. KitchenKnife adds KitchenKnife tag, its sprite/pose and polygon fixture. Never relabel Slicing as CUTTING or infer Sharp from either a tag or utensil flag.

BaseItem supplies dynamic physics, gravity/wake/pressure movement, collision/land sound and impact components. KitchenKnife's fixture polygon vertices are (−.475,−.225), (−.225,−.475), (.525,.275), (.275,.525), with inherited density 20/restitution .3/friction .2. A local AABB adaptation, if chosen, must be documented separately; generic item damage composition is not held-weapon damage.

Sharp is represented by the registered `kitchen_knife` item's resolved `tool_profile` inheriting `base_item` and adding only `sharp: {}`. This is consumed through existing `ToolQualities.isSharp` utility behavior. The actual registered item is supplied by the ordinary botany fixture hand-grant path alongside (not instead of) wirecutters. Utensil slicing remains a distinct missing consumer; no held-item melee profile/controlled-body attack owner was established by the bounded port audit. Do not add inert Utensil/Slicing/Execution/melee metadata or claim full weapon parity.

## Art/reuse boundary

`Textures/Objects/Weapons/Melee/kitchen_knife.rsi`: static 32×32 `icon`; four-direction `inhand-left`, `inhand-right`, `equipped-BELT`. Source stored offset −1,1 and rotation −45. The installed generated-item/flat-sprite pipeline uses the unedited icon, but does not preserve those directional held/belt states. Its adjacent attribution record retains the source hash, license and exact copyright metadata.

RSI metadata assigns **CC-BY-SA-3.0** and: “Taken/modified from desertrose at https://github.com/DesertRose2/desertrose/pull/378/commits/9e081bd2a0d7614be5a9dabbca34886ce204105d”. Preserve exact metadata and per-file provenance/hash records, not just this excerpt. No matching authored local knife/scalpel/hatchet art or item registration was found in targeted searches; differently named assets were not exhaustively excluded.

Current boundary: real registered item and resolved Sharp utility profile are installed; ordinary fixture grant reuses `BodyGiveService`. Sharp-only mapping is intentionally not full knife parity. No tool-quality Slicing, Utensil behavior, melee attack, Execution, or associated semantics are installed. Another worker may extend the shared ToolProfile for utensil slicing; this binding owns only Sharp. Modeler-delivered plant requirements remain unchanged.
