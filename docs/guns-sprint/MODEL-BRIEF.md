# Guns: early 3D asset brief

## Scope

Produce source-faithful **3D handheld and loose-world gun models**, not flat sprites,
sprite extrusions, wearable meshes, generic Minecraft gun-shaped boxes, or code-generated
placeholders. Eight representative SS14 guns are divided into three independent requests.
The requester will implement gameplay separately. Do not change gun statistics or code.

Upstream checkout: `C:/Users/William/Documents/SS14-dev/space-station-14`, pinned to
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Resolve the named entity IDs there and
inspect their RSI states, metadata, palettes and in-hand imagery. Preserve attribution.
`MODEL-SAMPLES.md` identifies the actual prototypes and resources; descriptions alone
are not proof of mechanical or visual states.

## Three independent groups

| Request | Guns | Associated 3D items owned by this request |
|---|---|---|
| `guns-magazine-fed-20261105-r1` | Mk58, Lecter | `MagazinePistol`, `MagazineRifle` |
| `guns-manual-feed-20261105-r1` | Inspector, Kammerer, Mosin | `SpeedLoaderMagnum` |
| `guns-energy-20261105-r1` | retro Laser Gun, Energy Shotgun, Svalinn | `PowerCellSmall` |

Each group owns only its listed assets. Do not duplicate another group's shared item.
No existing loose-gun request was found in the public queue. Check approved reuse through
the managed workflow if an applicable asset becomes available; worn armor is not a gun.
Loose cartridges/shells and projectile effects are not commissioned by these requests.

## Runtime format: use the existing baked-item route

Target: Minecraft Java 1.21.1 / NeoForge 21.1.224, namespace `moonstation14`.

Preferred complete runtime group: **OBJ + MTL + atlas PNG + item-wrapper JSON**, using
the existing `neoforge:obj` baked-model loader and `#atlas` texture convention. Do not
deliver bare OBJ files, archives instead of runtime files, a custom loader, or the
body-worn `AuthoredWearableMesh` interchange. Editable sources and review material
are sidecars, not runtime destinations.

Concrete supported-format reference:

- `src/main/resources/assets/moonstation14/models/item/botany/peels/trash_banana_peel.json`
- its neighboring `.obj` and `.mtl` files.

This is a loader/coordinate/texture reference, **not a gun design or scale reference**.
Wrappers must declare `loader`, `model`, `textures.atlas`, `textures.particle`,
culling/shading, UV flip, AO and render type intentionally. Preserve relative MTL
references. Use an appropriate opaque/cutout route; do not mark the entire gun
translucent/fullbright to approximate a small indicator.

The loose `block_model` path calls `ItemRenderer.renderStatic` with **NONE**, not
GROUND. Therefore the `display.ground` transform alone cannot fix loose-world fit.
Report raw mesh bounds and the scale/floor offset used in the NONE-context preview.
Implementation will bind these through the existing loose-object profile. Storage
footprint and physical collider values are separate; the artist must not invent them.

## Canonical paths and frames

Base item wrappers:
`src/main/resources/assets/moonstation14/models/item/<local_asset_id>.json`.
Geometry/materials: `models/item/guns/<local_asset_id>.obj` and `.mtl` under that
asset root. Atlas: `textures/item/guns/<local_asset_id>.png`. All referenced variants
and dependencies must be included in the flat delivery manifest.

Use OBJ block units (one unit = one Minecraft block), a common documented model
frame centered around X/Z = 0.5, +Y up, and neutral barrel direction +Z. Do not
double-scale vertices as both pixels and blocks. Supply first/third-person left
and right hand, GUI, fixed and ground display transforms appropriate to the gun.
Show scale against a WIDE/Steve humanoid; readable GUI size is not world-scale proof.

Provide a small **documentation sidecar**, not an assumed runtime schema, with:
raw AABB, units/axes, grip point, muzzle point/direction, and any magazine/cell
insertion direction and ejection region. Long guns also need a support-hand point.
Keep these points consistent across static variants. Named mechanical parts/pivots
in the editable project are useful, but this request does not promise an animated
OBJ-parts renderer or authorize inventing one.

## Required appearance and state coverage

- Follow each weapon's pinned silhouette, proportions, recognizable features,
  materials and palette. Model actual volume, barrel, grip and relevant mechanical
  parts; do not map an entire perspective sprite onto a box.
- Mk58/Lecter: provide assembled and magazine-absent 3D appearances, plus their
  separate listed magazine models. Match source fullness layers where they are
  visibly represented; document the source-to-variant mapping.
- Inspector: recognizable six-chamber cylinder and reload-access geometry. Include
  the listed speedloader and its source-visible loaded/empty differences. Do not
  invent an upstream cylinder-opening animation or a different chamber count.
- Kammerer/Mosin: recognizable pump/bolt hardware in the editable geometry. Their
  gameplay cycling uses a generic ballistic provider, not a dedicated animation
  component. Include source-visible fill states; do not add invented attachments.
- Energy weapons: reproduce the source charge-indicator steps and any actual
  source-visible mode distinctions. Provide Svalinn cell-present/cell-absent
  appearance and the separate small cell. LaserGun/EnergyShotgun have integral
  batteries: **do not invent replaceable-cell doors for them**.
- Source unshaded indicator regions must remain independently identifiable in the
  atlas/sidecar. If faithful emissive rendering needs additional runtime support,
  identify that integration boundary explicitly rather than inventing a shader or
  baking the whole weapon fullbright. Mode shot effects are not commissioned here.

Static variants must be complete supported runtime models with an explicit mapping
in the binding sidecar. Do not assume the existing runtime already consumes new
model predicates, anchors or animation fields. Report face/material counts and
keep geometry practical for many held/dropped instances without sacrificing shape.

## Review and delivery

Before final delivery, provide clear contact sheets showing source comparison,
six views, atlas, first/third-person grips (both hands), long-gun support-hand fit,
and dropped **NONE-context** pose/scale with ground plane and axes. Include all
required magazine, cell, fullness and charge appearances. Inspect the actual atlas
and source files; hash checks alone do not establish source fidelity.

Deliver through the public asset workflow as one complete group per request:

`{"files":[{"file":"export/...","destination":"src/main/resources/assets/..."}]}`

Include editable projects, source/provenance records, per-file attribution/license,
and real review-image paths as sidecars. Runtime destinations must be actual asset
paths, not documents/previews/archives. No automatic acceptance or import is implied.
The owner will launch modelers; do not self-claim another group's request.
