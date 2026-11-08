# Botany model delivery and binding contract

This contract is set. Modelers can author/export against it now; renderer integration remains pending and must consume this delivery through the existing Minecraft baked-model owners. This does not claim assets have been delivered, integrated, visually reviewed, or GPU-accepted.

## Format and coordinates

- Deliver native Minecraft Java block-model JSON and PNG textures. `.bbmodel` files are optional editable sources, not runtime assets. PNG `.mcmeta` is optional and only for native texture animation.
- No GeckoLib, glTF, Bedrock models, bones, custom shaders, or other runtime dependencies.
- One block is 16 model units. Origin `(0,0,0)` is the northwest bottom block corner; +X is east, +Y up, +Z south. Authored front faces `-Z` (north). The tray has fixed orientation: no directional variants are needed.
- Tray envelope is `x=1..15, y=0..5, z=1..15`; soil/plant attachment plane is `y=5`. Match the existing `HydroponicsTrayBlock` shape `Block.box(1, 0, 1, 15, 5, 15)`; do not request a larger envelope unless materially necessary.
- Plant root is `(8,5,8)` in the same block-local export coordinates. Plant models contain their own y=5 root coordinates and are overlaid at block origin: renderer adds no `(8,5,8)` translation or scaling. Recommended plant extent is x/z `1..15`, y `5..32` (top no higher than 32). Empty/very thin growth models and cross-planes are valid; use pivot `(8,5,8)` for authored transforms.
- Tray is independent geometry with no embedded plants. All plant quads, including cross-planes, must omit `cullface`. Ordinary world lighting and native cutout are supported; do not make models globally fullbright or require translucent blending. Use standard Java element rotations only (0, ±22.5°, ±45°); avoid non-exportable arbitrary rotations.
- No model animation or embedded growth timer is required. Server-authoritative status selects the whole model; models never determine growth or eligibility.

## Resource paths and manifest

Repository-relative runtime paths:

| Asset | Path / resource ID |
|---|---|
| Tray | `src/main/resources/assets/moonstation14/models/block/botany/hydroponics_tray.json` — `moonstation14:block/botany/hydroponics_tray` |
| Plants | `src/main/resources/assets/moonstation14/models/block/botany/plants/<key>/<state>.json` — `moonstation14:block/botany/plants/<key>/<state>` |
| Textures | `src/main/resources/assets/moonstation14/textures/block/botany/**.png` — reference as `moonstation14:block/botany/...` |
| Manifest | `docs/botany-sprint/model-delivery/plant-models.json` |
| Optional sources | `docs/botany-sprint/model-source/*.bbmodel` |

Manifest format version is `1`, with a tray resource ID and `plants` object keyed by stable visual key. Each plant maps every declared `stage-1` through `stage-N`, plus `harvest` and `dead`, to model resource IDs. Resource aliases are allowed; stages need not have unique meshes. Harvest and dead must each be visually identifiable from growing stages; do not silently alias every state to one appearance. Empty means render tray only: omit an empty plant model/state. Full illustrative manifest shape (the entries below are examples, not delivered assets):

```json
{
  "formatVersion": 1,
  "tray": "moonstation14:block/botany/hydroponics_tray",
  "plants": {
    "wheat": {
      "stage-1": "moonstation14:block/botany/plants/wheat/stage-1",
      "stage-2": "moonstation14:block/botany/plants/wheat/stage-1",
      "stage-3": "moonstation14:block/botany/plants/wheat/stage-3",
      "stage-4": "moonstation14:block/botany/plants/wheat/stage-4",
      "stage-5": "moonstation14:block/botany/plants/wheat/stage-5",
      "stage-6": "moonstation14:block/botany/plants/wheat/stage-6",
      "harvest": "moonstation14:block/botany/plants/wheat/harvest",
      "dead": "moonstation14:block/botany/plants/wheat/dead"
    }
  }
}
```

Exact logical-state coverage: wheat 6 stages, tomato 6, potato 4, poppy 3, aloe 5; each additionally has harvest and dead. Renderer status precedence is dead, then harvestable, then the authoritative stage, independent of age. The modeler supplies the mapping; the renderer owns binding and status selection.

## Delivery checklist

- [ ] One `hydroponics_tray.json`; normal and empty trays share this geometry (no duplicate base).
- [ ] Manifest covers all five keys and every exact logical state above; state aliases are intentional and harvest/dead remain identifiable.
- [ ] All referenced Java JSON models and PNG textures are present. Native texture dimensions such as 16/32/64 are preferred; power-of-two is recommended, not a 32-pixel requirement.
- [ ] Optional `.bbmodel` sources are included when available. Include license/credits and provenance for any new or derived source.
- [ ] No grinders, extractors, hoes, packets, or produce 3D assets are commissioned here; retain existing flat SS14 item icons. Tools/machine visuals have separate ownership.

Warning-light overlays, if desired, are renderer-owned and not a required extra tray mesh. Optional indicator surfaces may be noted with coordinates, but their absence does not block delivery.
