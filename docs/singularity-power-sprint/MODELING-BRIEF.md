# Machinery model brief

The singularity itself and field surfaces are renderer work. No 3D singularity
model is requested. Machinery needs Minecraft-native models with SS14 styling.

Reference checkout: `C:/Users/William/Documents/SS14-dev/space-station-14`, pinned
to `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Models

- Particle accelerator: control box, end cap, fuel chamber, power box, and
  port/fore/starboard emitter parts. Seven placed entities; preserve directional
  alignment and recognizable part distinctions. Reference `PA/*.yml` and each
  referenced RSI, not guessed layout from sprite appearance.
- Containment emitter, with inactive/firing and locked/unlocked presentation.
- Containment-field generator, with inactive/charging/connected indicators.
- Singularity generator, with uncharged/charging/spent presentation where the
  verified upstream visual definitions support it.
- Radiation collector, with enabled/disabled, inserted-tank and pressure states.
- Portable plasma tank if no suitable existing model is available.
- Plasma gas canister/refill station, with a separate inserted-tank attachment
  and valve/pressure presentation. This is not the collector's fuel item.

Prototype references: `Resources/Prototypes/Entities/Structures/Power/Generation/`
under `PA/` and `Singularity/`; gas tank definitions under `Entities/Objects/Tools/`.
Inspect the referenced RSI layers/states before choosing textures and animation.

## Delivery constraints

The user has authorized placeholders while authored models are being prepared.
Notify the user when additional 3D-model needs arise, then continue implementation;
do not wait for final art. The portable tank currently uses a flat SS14 icon.
Before declaring a model missing, check the asset workspace's **accepted**
packages using `MODEL_HANDOFF.md`, acceptance records and export manifests.
Generated/pending art is not automatically accepted. Preserve accepted files and
retrieve their complete dependency set without regeneration.

Accepted canister art already exists at
`assets/Structures/Storage/Canisters/candidates/rounded_r2/` in the asset workspace.
Use its accepted R2 revision and binding/export contracts, not the older
`collection/` or mixed revisions. The example whole models are pressure-3 review
states, not initial gameplay defaults; select authoritative independent states.

Use the existing asset-workspace Minecraft resource-pack export conventions.
Retain editable sources, textures, attribution/license notices and model exports.
Keep screen/indicator faces separable for native emissive state selection. Do not
bake containment walls or a singularity into machinery models: those are live
effects. Runtime blockstates, collider bounds and new 3D field connections must
be agreed with integration rather than invented by the asset export.

Full 3D containment is an intentional adaptation. Generators will need legible
horizontal and vertical connections; the initial design uses eight box corners.
Final placement/mounting dimensions remain pending, so establish model anchors
with integration before finalizing generator geometry.
