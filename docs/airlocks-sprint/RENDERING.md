# Airlock client rendering

## Current exported-model replacement

The subsequent model mini-sprint replaces the procedural mesh below with the
modeler's exported Java component models. Current architecture, validation and
manual checks are indexed in `../airlock-model-mini-sprint/IMPLEMENTATION.md` and
`../airlock-model-mini-sprint/MANUAL.md`. Asset provenance is recorded in
`NEW-MODEL-ASSETS.md` and packaged alongside the new model resources.

Rendering uses baked components, existing synchronized appearance and resolved
controller timings. Geometry/UVs are clipped to the root's 1×2×1 block volume;
shared authored geometry supplies examination hits and outlines. No door collision,
power, atmosphere, controller, or wire behavior is changed. The static item uses
the same exported closed model. Client and server must both receive the new jar.

## Historical procedural renderer

The following records the preceding accepted renderer, not the current mesh.

The lower/root block entity renderer consumes its synchronized `clientView()` DTO
and resolved client `AirlockData` timings. `onDataPacket` routes render-only
`View*` tags through `handleUpdateTag`; persistent `Airlock*` NBT is not loaded
from update packets. The upper half has no independent renderer, and render
bounds cover the two-block height and maximum leaf travel in every facing.

The renderer adapts the pinned SS14 Standard Airlock art (see `ASSETS.md`) into
a Minecraft-only 3D cuboid frame with two horizontally retracting textured
leaves. Door motion is based on each controller's synchronized phase/start
time and configured stage durations, so reversals and timing variations remain
independent. At full open the leaves retract clear of the doorway. The panel
state shows a contrasting maintenance hatch; it does not imply an access lock.
Powered glow and bolt indicators remain appearance overlays. Horizontal facing
is applied to the whole model. Vertically sliding leaves are explicitly deferred.

This frame/depth/sliding geometry is a Minecraft adaptation, not upstream SS14
geometry parity: upstream uses a 32x32 sprite and animated appearance layers.
The source frame art is from `Resources/Textures/Structures/Doors/Airlocks/Standard/basic.rsi`
at pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; attribution and
license are recorded in `ASSETS.md` (CC-BY-SA-3.0, tgstation).

The frame's four static UV patches intentionally leave the center transparent;
the renderer must draw both opaque closed-art leaf patches as well as the frame
patches. Emissive and bolt layers are overlays only, never a substitute for the
base leaf art. Previously the closed-art frame was drawn but the base leaf-art
call was omitted, leaving the doorway center as the neutral backing material.
The rear hatch is placed outside the rear cuboid face; using the hatch box's
front-side depth for its rear quad embeds it inside the door and hides it.
Non-art cuboid faces use the uniform `#545454` neutral-metal backing derived
from closed-art pixel `(2,0)` (see `ASSETS.md`), not a bright vanilla concrete
texture. Rear frame UVs are mirrored horizontally to match the rear leaf view.
The panel hatch samples the complete nontransparent `panel_open.png` bounds:
pixel x=23..27 and y=14..25 inclusive on the 32×32 source canvas. Rear hatch
UVs reverse horizontally while retaining the same full source crop.

Manual visual check remains necessary: place doors in all horizontal facings,
observe closed/open/transitions and reversal, remove power, operate bolts and
open the maintenance panel. Confirm root-only rendering and static item art.
