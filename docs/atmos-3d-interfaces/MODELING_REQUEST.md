# Atmospheric machinery: 3D pipe-interface revision

Owner authorization: 2026-10-28, following the decision to retain freeform 3D pipes and make atmospheric machinery visibly interface with them. This is an asset request, not permission to import artwork or change gameplay.

## Goal and scope

Reuse the existing SS14-derived models, textures, state definitions and authoring tools. Produce a coherent, separately versioned interface revision, not a replacement art style or another original-model commission.

Primary inline family: pressure pump (`GasPressurePump`), volumetric pump (`GasVolumePump`), mixer (`GasMixer`), filter (`GasFilter`), valve (`GasValve`) and passive exchanger (`HeatExchanger`). Existing candidates are under `assets/Structures/Atmospherics/V03/collection/` in the asset workspace. Respect later same-ID overrides identified by its current registry and handoff.

Room terminals: vent and scrubber (`GasVentPump` / `GasVentScrubber`; verify resolved local mapping). Reuse their current package and preserve distinctive SS14 silhouettes. Deliver real 3D room-facing grilles/housings and rear pipe connections that remain readable on walls, floors and ceilings. Do not confuse the V03 **portable scrubber** with the installed unary room scrubber.

Audit, but do not automatically remodel, the existing thermomachine and circulator connection interfaces. Preserve the accepted taller-heater R3, accepted gas miners, and all owner-directed TEG revisions. If they need connector changes, identify the exact mismatch and propose a separate connector-only revision; do not redo approved bodies or duplicate the TEG feedback request. Miners discharge into their containing atmosphere and the TEG center has no gas ports: do not invent pipe sockets for them.

## Connection contract

- Retain the current six-direction exposed-pipe system; this request does **not** convert piping to wire-like surface placement.
- Coordinate system: Minecraft block-local `[0,16]`, +Y up, canonical NORTH front = -Z. The primary pipe's core occupies `[6,10]` on each axis and its arms meet block faces. Its centerline is `(8,8,8)`; primary face sockets meet the matching block face at mid-height, with a compatible four-unit pipe cross-section. Apply any ground-centered export translation exactly once.
- Actual socket directions and roles come from **resolved local prototype ports**, including inheritance, facing transforms and selected pipe layer. Mesh artwork must not redefine topology. Read the supplied port/visual consumers before fixing socket coordinates.
- Secondary/tertiary pipes have existing offset geometry. Document their actual shared endpoints and supply an explicit connection solution for every supported machine layer. Do not stack three copies of an entire model, bridge independent lanes, or invent unsupported ports.
- Visible fittings must continuously meet the connected pipe: no gaps, floating mouths, unexpected offsets, or apparent connections to non-port faces. Use short visible elbows, risers, collars or internal machine-side adapters when a source-faithful housing needs a different socket height. These are geometry, not new gas nodes, pipe volume, automatic connections or gameplay features.
- Preserve a consistent connector vocabulary across the family. Pumps must retain distinct pressure/rate identities. Show clear flow direction and distinguish mixer inputs and filter outputs with source-faithful markings and shape; do not rely on color alone or imply bidirectional pump behavior.
- Do not change gas rules, settings, receiver loads, sounds, collision, selection, placement admission or networking. Preserve existing state meanings and SS14 optical timings. A modeled socket does not grant a logical connection.

## Wall, floor and ceiling terminals

The current vent/scrubber runtime already supports six clicked-surface orientations, gated by resolved `allowVerticalMounts`. Device `FACING` points into the room; the network connection is behind it. Produce geometry that rotates correctly for all six orientations, not three unrelated machine designs.

Show representative wall, floor and ceiling installations with the rear pipe visible in a cutaway and concealed in the finished view. Preserve the room-facing opening and distinguish vent from scrubber. The terminal is not an airtight wall replacement; illustrations must retain a proper support and sealed pipe crossing where needed. Exposed rear connections are also valid installations.

## Sequence and review

1. Inspect the pinned SS14 sources (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`) and existing candidate lineage. Publish the required source-fidelity checkpoint and a port/socket matrix before family-wide authoring.
2. Pilot the **volumetric pump** with straight pipes and a vertical riser. Provide close and gameplay-distance views, all four supported machine facings, primary/secondary/tertiary connection examples, and a compact mixer/filter layout illustrating role readability. Resolve geometry-contract gaps before propagating the interface to the family.
3. Complete the six inline fixtures and the two room terminals. Reuse compliant existing geometry rather than changing it unnecessarily; explicitly list unchanged dependencies.
4. Supply source-versus-candidate comparisons, a complete state/binding map, numeric socket/pipe-endpoint alignment checks, and actual reviewed images. Asset renders and numeric checks are not Minecraft/GPU verification. Runtime installation and connected-client alignment remain separate work.

## Delivery and preservation

Deliver through the normal shared asset queue with editable Blockbench sources, complete native runtime geometry/material/texture dependencies, exact destination manifest, provenance/license attribution, previews, and validation/review records. Keep models and bulk output in canonical asset/work locations, not in documentation. Preserve accepted artwork, manual edits, prior candidates and frozen guards unchanged. Do not regenerate another package merely to retrieve it. No import or owner acceptance is implied by this request.

## Runtime references

- `docs/teg-sprint/ASSET_RUNTIME_CONTRACT.md` and `docs/teg-sprint/PARITY.md`.
- `ms14/atmos/piping/ExposedPipeVisualBlock.java`, `VisualPipeBlockEntity.java` and `FunctionalExposedPipePlacement.java`.
- `ms14/atmos/machine/AtmosMachineBlockEntity.java` and the supplied typed prototype JSON parent chains.
- `ms14/atmos/piping/VentilationDeviceBlock.java`, `VentilationDeviceBlockEntity.java` and `FunctionalVentilationDevicePlacement.java`.
- `src/gametest/.../VentilationDeviceFaceGameTests.java` and `src/test/.../WallMountBlockstateOrientationTest.java` establish placement/resource coverage, not manual visual acceptance.
