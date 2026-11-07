# Gun presentation bindings

`assets/moonstation14/gun_presentation_bindings.json` is the client-only typed
mapping input loaded at the existing gun model-bake/reload hook. Coordinates are
artist OBJ units; bore/up are raw authored axes, not display-context axes. Scale
intentionally remains owned by the resolved loose-object profile.
Svalinn uses profile `model_scale: 0.65` as a deliberate Minecraft size
adaptation; this shared profile scale governs both held and world presentation.

Sources: R4 cartridge-handle `provenance/anchors.json` (`assets.*.OBJ_blocks`);
manual-feed R1 `provenance/assets/Objects/Weapons/Guns/ManualFeed/candidates/source_family_r1/anchors_and_placement.json`
(`assets.*.anchors.*.OBJ_blocks`); energy/Svalinn R3 `provenance/model_spec.json`
(`laser`, `shotgun`, `svalinn` `anchors_pixels`, converted as
`(x+8,y,z+8)/16`). R4 frames use bore +Z/up +Y; manual-feed frames +Z/up -X;
energy frames +Z/up +X, per the frozen rest-frame review. Optional support is
only populated where the source specifies an authored support-hand anchor.

Gun `muzzle` bindings identify the actual imported-model muzzle in native item
coordinates. Projectile sprite `forward_offset` is separate authored sprite-art
centering (for example the laser muzzle sprite's half-unit offset); it moves a
point flash volume along the captured bore, not the gun anchor or a beam origin.
Neither value is an eye/camera-front correction.

Energy pixel anchors convert as `[(x+8)/16, y/16, (z+8)/16]`; the authored Y
coordinate is not offset. Minecraft's one-bone support-arm adaptation aims the
actual native hand center at the farthest reachable point on the corrected
grip-to-authored-support segment. It searches a bounded cross-body main-arm yaw
and never stretches the arm or moves imported geometry or the primary grip.

The durable test oracle `src/test/resources/guns-sprint/artist-anchor-reference.json`
contains only raw anchor triples and source paths/SHA-256 hashes for the frozen
R4, manual-feed R1 and energy/Svalinn R3 deliveries. Tests compare runtime
bindings to those raw values (including the energy pixel conversion above) and
check their anchors against the imported OBJ bounds; the fixture is not runtime
configuration.
# Prone articulated hand contact

Only the supported, validated-wield articulated prone pose adapts the gun mount to the physical palm contact point (the native hand centre). The authored grip remains the asset binding and ordinary upright/one-hand mounting continues to pivot around that authored grip; this pose-only translation ensures the rendered palm, rather than a virtual authored grip point, contacts the weapon.
