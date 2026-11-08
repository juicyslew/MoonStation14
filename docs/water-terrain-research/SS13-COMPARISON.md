# SS13 water and finite-liquid comparison

Research only; no gameplay code changed. The owner's revised requirement is physical depth for swimming, containment, leveling and flow into newly opened cavities, potentially thinning into puddles. A static SS14-style surface does not meet that requirement; a deliberate Minecraft adaptation is permitted. Implementation remains undecided.

SS13 is not one implementation. The following are verified examples, not an exhaustive fork survey.

## tgstation: static water with swimming gameplay

Local checkout: `SS14-dev/tgstation`, commit `89d3d193b9e94696b4f4092e6c1fbdf137385c84` (2026-10-14).

`/turf/open/water` is map-authored terrain. Shallow and deep variants are separate turf types; deep variants enable a swimming element. Immersion overlays visually submerge occupants. Swimming applies movement/stamina effects and conditional drowning rules, rather than calculating a physical water column. The water turf also supplies fishing sources and scoopable reagents; beach water supplies salt water.

Collection is genuinely inexhaustible in this implementation: the scoop element adds a random 5–10 units of the configured reagent to an open container without subtracting from any pool-volume store. Container capacity still bounds collection.

Breaking adjacent floor does not spread this water into the new space. A lower-Z hole does not drain the water turf. Its entry/initialization hooks handle occupants, not fluid transfer. Baseturf restoration metadata is not a liquid model. Water-specific destruction outcomes should not be generalized across every turf/damage path; an explicitly indestructible water-like turf also exists.

Sources:
- [Water turf](https://github.com/tgstation/tgstation/blob/89d3d193b9e94696b4f4092e6c1fbdf137385c84/code/game/turfs/open/water.dm)
- [Swimming element/status](https://github.com/tgstation/tgstation/blob/89d3d193b9e94696b4f4092e6c1fbdf137385c84/code/datums/elements/swimming_tile.dm)
- [Reagent scooping](https://github.com/tgstation/tgstation/blob/89d3d193b9e94696b4f4092e6c1fbdf137385c84/code/datums/elements/reagent_scoopable_atom.dm)

**Usefulness:** swimming/gameplay references, but not a solution to 3D containment or flooding.

## Goonstation: connected finite-liquid groups

Source observed at commit `f210f80afb0cfee0c43c0e09380aba3df89982cc`.

`/datum/fluid_group` holds a shared reagent mixture and a set of member fluid tiles. Total volume divided by member count determines average amount per tile. Volume permits spreading into eligible neighbors; removing volume can shrink coverage. Groups merge and split, distributing reagents when disconnected. Drains and evaporation remove liquid.

Depth is represented by bands such as very shallow, knee height, chest height and very deep. These affect appearance, descriptions and movement/audio feedback. They are not continuous 3D geometry. The inspected group implementation establishes 2D spreading, not elevation-aware free-surface leveling or vertical flow.

Processing uses queued groups and cached state, with unchanged-state early-outs. These are useful performance patterns, not proof that the exact algorithm is appropriate for Minecraft.

Source: [fluid_groups.dm](https://github.com/goonstation/goonstation/blob/f210f80afb0cfee0c43c0e09380aba3df89982cc/code/modules/fluids/fluid_groups.dm), especially `update_amt_per_tile`, `update_once`, `spread`, `try_split`, `join`, and `drain`.

**Usefulness:** reagent-bearing floods and puddles with finite stored quantities, but not a verified 3D liquid solver.

## Yogstation: liquid groups with elevation and downward transfer

Verified source at commit `ae2886be95c234202212a3090ee7637173ea2c1d`, linked by generated Yogstation documentation.

The liquid group owns a reagent mixture and member turfs. `process_group` derives average volume per turf and an expected liquid height. Visual states range from puddle through ankles, waist and shoulders to full-tile liquid. Shallow puddles and deeper liquid are states of the same system.

`spread_liquid` implements concrete containment/elevation behavior:

- Cardinal spreading on equal-height eligible turfs.
- Transfers to lower-height neighboring turfs until height conditions prevent them.
- Overflow toward higher-height terrain when liquid is sufficiently high.
- For an open-space destination, `GET_TURF_BELOW` locates a lower turf and transfers reagents to its liquid group, with splash presentation. The inspected branch does not establish a continuous falling column or arbitrary multi-level traversal.

The model also supports chemical exposure, reagent-dependent burning, merging/splitting and volume removal. `water_rush` can push unanchored occupants and knock living occupants down. Space and application paths include explicit removal; a finite store must not be mistaken for a demonstrated strict closed-system conservation invariant.

This remains a gameplay approximation. Equal-height groups use an average amount; height transitions use separate rules and transfer amounts. It is not a general per-cell 3D pressure/equalization solver. Swimming/immersion behavior was not established by this liquid-group audit.

Sources:
- [liquid_groups.dm](https://github.com/yogstation13/Yogstation/blob/ae2886be95c234202212a3090ee7637173ea2c1d/yogstation/code/modules/liquids/liquid_groups.dm), especially `process_group`, `spread_liquid`, `trans_to_seperate_group`, `transfer_reagents_to_secondary_group`, `process_turf_disperse`, and `water_rush`.
- [liquid_turf.dm](https://github.com/yogstation13/Yogstation/blob/ae2886be95c234202212a3090ee7637173ea2c1d/yogstation/code/modules/liquids/liquid_turf.dm).

**Usefulness:** the closest verified reference here for actual flow into a lower cavity, while retaining reagent mixtures and puddle/depth transitions.

## Implications for this port

The revised requirement is reasonable, but it calls for a finite-liquid subsystem rather than SS14 terrain water or vanilla infinite source blocks. SS13 supplies useful precedents without eliminating the 3D design work.

A suitable direction to investigate is finite volume/mixture storage, gravity-driven transfer and local free-surface leveling, with depth driving wading/swimming. Thin and deep liquid should share one canonical quantity: a transition to existing puddle gameplay must transfer or share ownership, never duplicate reagent contents. Preserve SS14 reagent/effect rules where appropriate; document liquid-motion and swimming rules as deliberate adaptations.

Before implementation, resolve reagent units versus physical block volume, supported geometry and vertical flow, atmosphere displacement, movement authority/prediction, source/sink policies, explosion-induced topology changes and bounded active-region processing. Full particle-based hydrodynamics is not required merely to obtain conserved pooling, leveling and draining, but these references are not a drop-in Minecraft solver.

No claim is made that these systems represent every SS13 fork. A preliminary BeeStation lookup did not establish a relevant implementation and is not negative evidence about that codebase.
