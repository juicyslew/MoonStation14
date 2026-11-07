# Round-four parity and 3D presentation boundaries

Reference: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Earlier gun composition and values remain indexed in [PARITY.md](PARITY.md).

## Existing gun operations on world sources

Verified upstream verb consumers:

- `SharedGunSystem.ChamberMagazine.cs`: chamber activation/interaction verbs and
  `UseChambered` require access/interact and hands presence, not an empty hand.
- `SharedGunSystem.Ballistic.cs:OnBallisticVerb`: the same distinction applies to
  manual cycling.
- `SharedGunSystem.Magazine.cs:OnMagazineVerb`: magazine verbs require access and
  interaction and forward to the contained magazine.
- `BatteryWeaponFireModesSystem.cs:OnGetVerb`: mode verbs additionally check
  complex interaction and any configured access reader.

These paths are under `Content.Shared/Weapons/Ranged/Systems/`. They establish
verb semantics, not an upstream custom ground-gun window. The port's retained
window and exact actor-hand proof are interaction adaptations. World operations
reuse the held staging policy and existing world-output owner. Ejected outputs
appear beside the gun. Exact upstream **manual magazine-output placement** remains
unverified; do not describe this placement as proven SS14 parity.

## Deliberate 3D adaptations

- Closed tubes and visible-pixel extrusions retain source textures, frames, tint,
  and unshaded data. Native Minecraft entity shaders discard texture alpha below
  `.1`; volume occupancy respects that cutoff and sidewalls sample an opaque
  source-core texel rather than an invisible glow-border texel.
- Muzzle frames come from the final native held-model matrix. Entity render poses
  are camera-relative world coordinates; add camera position, not inverse camera
  rotation. Authored sprite centering remains separate: the laser muzzle sprite's
  source `.5` forward offset places its near edge at the actual muzzle.
- The travel volume is one continuous muzzle-to-authoritative-endpoint beam.
  Localized muzzle/impact volumes are not extra logical shots. A hit behind the
  visible muzzle suppresses backward travel; physics and the hit point do not move.
- Supported prone aiming is owner-directed: validated wield + harm while conscious
  and down forces stomach orientation and aim-facing. The articulated rig, palm
  correction, supported torso, and shared physical eye are Minecraft presentation
  geometry, not claimed upstream 3D animation. Ordinary posture rules remain outside
  that narrow mode.
- Svalinn's `.65` and Energy Shotgun's `.7` are shared held/world presentation
  scales, not SS14 numeric scale claims. Imported artist files remain unchanged.

Validation and owner acceptance must be recorded separately. CPU geometry and
previews do not establish native GPU appearance.
