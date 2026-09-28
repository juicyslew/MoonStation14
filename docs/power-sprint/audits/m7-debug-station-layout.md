# M7 deterministic debug-station layout

## Layout contract

`StarterStationLayout` is a pure local-coordinate plan and makes no world, startup, or registration calls. The inclusive footprint is x/z 0..19. It contains four 6x6 clear-interior rooms in a 2x2 arrangement, linked by 2-wide hallways through door openings. A two-block-wide entrance at the west edge (x=0, z=3..4) opens into a tiled two-wide route across the first room and into the connected hall network. It has four clear levels and a continuous roof above; no other outer-wall cells are opened. The interior classification provides a contiguous walkable floor grid; wall bands close the perimeter and room boundaries except at the specified doorways.

Every footprint x/z coordinate receives a floor tile: STEEL in rooms and WHITE_TILE on hallway runs. Interior columns receive four explicit AIR clearance cells and a STEEL roof; walls are STEEL through the same four levels and also receive a STEEL roof. Plan iteration is deterministic and includes no cells outside the footprint; it does not prescribe clearing any other space.

## Validation boundary

Pure unit tests check footprint/dimensions, all-floor tiling, clearance, walls/roof, BFS connectivity from the west entrance to all four rooms and hallways, and deterministic bounded plan iteration. Compile and focused test commands establish Java/build compatibility only. No Minecraft world mutation, startup wiring, registration, manual server run, or GameTest is included or claimed.
