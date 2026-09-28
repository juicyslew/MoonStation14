# M1 floor finish implementation evidence

## Chosen representation

`station_floor` is one registered `StationFloorBlock` at its world position. Its `tile_finish` enum is bounded to `none`, `steel`, and `white`; it is normal persistent and vanilla-synchronized blockstate. Distinct `station_floor_tile` and `station_floor_tile_white` items set their matching finish only on an untiled station floor. The separate registered `station_floor_tile_pry` tool resets either finish from the TOP face and returns its exact tile item. The registered `crowbar` retains only its existing steel-wall/girder behavior. Neither floor interaction replaces the host block. Placeholder models use vanilla polished-andesite, iron-block, and white-concrete textures.

This is the narrowly scoped M1 exception to the M0 audit's prohibition on tile bits in a chunk attachment: the finish is intrinsic presentation state of the dedicated station floor block, not a second per-coordinate store, custom tile packet, custom tile renderer, or a second tile block. Future cables remain in their own sparse chunk attachment, keyed by host face. Future top-face cable rendering hides cable when this floor's current finish is not `NONE`; the cable record remains untouched. Wall/ceiling cable visibility is not affected.

## Evidence and limits

- `StationFloorBlock` declares only the three-value finish property and defaults to `NONE`.
- Tile installation/removal accept only the TOP face, validate the server-side interaction permissions before mutation, and change only that property's BlockState on the same block. Installation consumes one tile outside creative mode; the separate floor-tile pry tool emits one returned tile item, one crowbar-use sound, and takes one durability point after a successful change. Failed state changes and missing/unauthorized players produce no tile drop or durability loss.
- The state variants select distinct standard Minecraft textures. Both finishes conceal top-face cables in `CableVisualRenderer`; wall/ceiling cable visibility remains unaffected. No imported reference art or custom tile rendering code was added.
- `StationFloorInteractionGameTests` exercise registered items against the actual GameTest world: both variants, occupied-floor replacement rejection, exact-variant pry-tool return, same-block state changes, stack consumption and drops, pry-tool durability, wrong hosts, side/bottom rejection, missing/unauthorized players, untiled-floor rejection, and creative behavior. These tests check the live world BlockState; no chunk unload/reload persistence test is claimed.
- Cable records remain independent of tile finish; no code in tile placement/removal reads or mutates cable storage.
- No manual Minecraft tests or GameTestServer runs were performed. Compilation and automated test results are reported with the implementation task.
