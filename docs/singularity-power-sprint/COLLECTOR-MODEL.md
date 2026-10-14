# Radiation collector model integration

## Status

The client-only BER is registered from a collector-owned mod event subscriber.
It draws the shell (accepted `collector_base` + `lower_controls` elements),
one pivoted panel (`collector_lid` + `collector_glass` + `panel_rear_hinge`),
and a tiny fullbright pressure material tile. It never draws the accepted whole-q
poses. The panel pivots at native `[8,10,11.5]/16`; source keyframes are at
0/.1/.2/.3/.4s and hold to .8s. Tank and LED atlas patches preserve exact
accepted PNG pixels; integration-only 8 atlas variants are under
`textures/block/reactor_bindings/collector/` and their source/crop/hash record is
`models/block/reactor_bindings/collector/provenance.json`.

The BE publishes a derived-only native update packet view, refreshed only when
enabled/tank presence/pressure/profile/motion changes. Tank pressure uses local
kPa converted at source thresholds. Running motion direction is latched; a
changed target reconciles after the active .8s clip. Late join/chunk state reads
the current clip phase or settled enabled target; it does not start an animation
on load.

## Source contract

Accepted whole-assembly OBJ pose files remain unused alternatives. The adapter
copies accepted named elements and UVs without modifying source JSON: shell uses
`collector_base`/`lower_controls`; panel uses `collector_lid`/`collector_glass`/
`panel_rear_hinge` from the raw, unposed accepted `default.json`, not the already-
vertical static glass. The default glass is flat `[1.7,10,4.5]..[14.3,10.16,11.5]`;
lid, glass, and hinge all receive the one native clip pivot transform.
`ca_off` and `ca_on` were pixel-checked over all raw-default panel UV faces:
inactive matches the default atlas; active matches except the accepted glass-up
screen patch `[92,23,117,37)`, which carries the source active cells. The adapter
retains those unchanged source UVs and uses the corresponding ca_off/ca_on art.
Its lower-controls tiles are tank `[28,45,42,53)` and LED
`[43,46,45,53)` pixels in the accepted 128px atlas; they are disjoint and their
coordinates map inside the named lower-controls north face UV. The profile/tank
remains authoritative in the server block entity; visual state is a minimal
derived client view, never serialized `MachineItemSlot` state.
Transformed raw panel vertex bounds (both closed and 90-degree poses) fit the
renderer bounds `[1,0,2.8]/16..[15,20,21]/16`.
Pinned SS14 contract is documented in `COLLECTOR-LIFECYCLE.md`; motion is a
presentation of `enabled`, not a gameplay timer.
