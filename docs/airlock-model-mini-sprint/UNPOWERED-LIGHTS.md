# Unpowered exported airlock lamp correction

The exported `header_lamps` component is a separate baked model and its shared
atlas contains colored lamp pixels even when no powered overlay is selected.
For example, the component's `lamp_1_front` UVs sample the green `#83a35d`
portion of `textures/block/airlock/vertical_all/standard_basic/atlas.png`.
Consequently, suppressing `closed_unlit`/reader overlays alone does not remove
the colored base-model lamps. The renderer now draws that component only while
the synchronized view is powered; powered rendering and overlay selection are
unchanged. When unpowered, the underlying header remains visible as the dull
backplate rather than retaining colored lamp texels. This reuses existing
component geometry, model atlas, and power presentation state; no derived art
or new provenance claims are introduced.

Focused planner coverage asserts the component-selection bit both powered and
unpowered. A connected-client visual retest remains necessary to confirm the
backplate appearance in-game.
