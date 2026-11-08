# Standard firelock production asset request

Historical R1 brief below. Its dedicated-host/frame assumption was superseded by the owner-approved frameless, colocated R4 delivery on 2026-10-30. Do not use this old brief as the current import or runtime contract; see `ASSETS.md` and the packaged R4 rig manifest.

Request ID: `automatic-atmos-standard-firelock-production-20261029` (parent submits; this preparation does not write to the public queue).

## Deliverable

Produce one finished, production-quality standard firelock asset group for the MoonStation14 NeoForge model pipeline. This is a complete production commission, not a representative checkpoint, concept approval loop, or placeholder. Include editable source project(s), source-faithful previews and the complete flat runtime manifest/dependencies. No glass or edge variants and no ordinary-airlock remodel.

The pinned source is SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, standard `Resources/Textures/Structures/Doors/Airlocks/Standard/firelock.rsi`; art metadata identifies CC-BY-SA-3.0 and tgstation commit `04e43d8c1d5097fdb697addd4395fb849dd341bd`. Preserve source art identity (inspect actual source frames; do not add generic hazard stripes or invent details). Required visual states: normal closed/open, opening/closing, pressure warning and temperature warning independently, both and neither; powered/unlit presentation where the source supplies it. Do not invent sirens. Source welded/bolted/panel states may be included only as explicitly future/unconsumed optional art, never claim runtime support.

## Geometry and runtime integration contract

One-wide/two-tall dedicated firelock host with lower controller; never co-occupy cells with an ordinary airlock. Canonical bounds x=0..1, y=0..2, z=0..1 blocks (native model coordinates x=0..16, y=0..32, z=0..16), aligned to current airlock renderer origin/orientation. Show a proper lintel/frame with rigid vertical drop-down shutter and consistent front, rear, side and inner surfaces; no floating frame. Open aperture must clear a 1.8-block human player height; a 2m line is acceptable if natural to the source. Use supported Minecraft JSON cuboid model components only (no rotated elements, OBJ/MTL, flat quad, icon extrusion, or stretched airlock). Existing `AirlockGeometry` consumes unrotated cuboids and `AirlockBlockEntityRenderer` supports authored components and rigid vertical translation. Define stable role/component names and canonical bounds, fixed/moving grouping and normalized upward travel for each moving part; main shutter can comprise slats but must move as one rigid group. Suggested minimal roles: frame, optional fixed trim, shutter, pressure indicator lit/unlit, temperature indicator lit/unlit. Runtime adapts resource IDs later; no source-code changes in this commission.

Include a fully closed assembled item wrapper/model plus every PNG/model dependency, all relevant block-display preview model roles, and a complete flat manifest to canonical destinations under `assets/moonstation14/models/block/firelock/standard/`, `textures/block/firelock/standard/`, and `models/item/firelock.json`. Preserve normal world lighting/shading; indicator appearance should remain faithful, not make the whole model emissive. Runtime animation respects existing door phase semantics (physical 0.1/0.6 phases); document evidenced source timing and do not invent timings or derive movement proportions from artwork.

## Production evidence and constraints

Provide editable BBModel(s) with embedded textures, exact attribution/license metadata, component/rig manifest, per-part bounds and geometry counts. Provide actual review-image paths for front/rear/left/right/top and closed/open/mid-animation views, plus a practical 1x2 firelock installed in an equivalent wall/floor doorway with player-scale reference; show an ordinary airlock separately for scale comparison and no overlap. A hard target of <=256 quads for the closed standard assembly is desirable; report justified exceptions rather than degrading the source art. Component baking does not establish performance. Freeze the complete production group, then independent review/import and explicit owner receipt are separate steps.

Context copies are private ignored files in `build/automatic-atmos-firelocks/model-request/context/`; request and scope JSON are adjacent. They include individual verified upstream source files and renderer/geometry references, not whole source bundles. Parent reviews and submits; no queue write, import, model/code edit, or staging is authorized here.
