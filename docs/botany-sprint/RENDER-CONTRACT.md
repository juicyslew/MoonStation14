# Botany model delivery and binding contract

The original collection's 34 plant models/textures and plant-state map remain unchanged. The tray-only front correction from `Asset Building Workspace/assets/Structures/Botany/BasicBotany/candidates/front_status_slots_r2` replaces the tray model/texture and adds five optional status surface models/textures. The corrected artwork is installed at the mapped resource paths but awaits GPU/user review; installation is not owner acceptance. The existing tray consumer still draws only the base tray; no status-overlay renderer/controller is implemented by this asset delivery.

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
| Independent optional indicator surfaces | `moonstation14:block/botany/indicators/{harvest,health,alert,nutrition,water}` |
| Surface binding/geometry contract | `docs/botany-sprint/model-delivery/indicator-bindings.json` |
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

- [x] One corrected `hydroponics_tray.json`; normal and empty trays share this geometry (no duplicate base). Tray footprint is 14×5×13.52 units within the 14×5×14 envelope; root stays `(8,5,8)` and no translation is required.
- [ ] If status surfaces are implemented, draw the base tray once and independently draw at most one optional overlay per true authoritative boolean; do not include them in plant selection or invent status logic in the asset layer. Inactive apertures show the base's dark backing.
- [ ] Manifest covers all five keys and every exact logical state above; state aliases are intentional and harvest/dead remain identifiable.
- [ ] All referenced Java JSON models and PNG textures are present. Native texture dimensions such as 16/32/64 are preferred; power-of-two is recommended, not a 32-pixel requirement.
- [ ] Optional `.bbmodel` sources are included when available. Include license/credits and provenance for any new or derived source.
- [ ] No grinders, extractors, hoes, packets, or produce 3D assets are commissioned here; retain existing flat SS14 item icons. Tools/machine visuals have separate ownership.

Five independent surfaces are delivered. In front-view left-to-right they bind harvest (`HarvestLight`, center X 12.8), health (`HealthLight`, X 10.4), alert (`AlertLight`, X 8), nutrition (`NutritionLight`, X 5.6), water (`WaterLight`, X 3.2). Each is one north-facing quad spanning X center±1, Y1.3..2.8, Z2.48 in block coordinates. Draw at origin, normal lighting, native cutout. False means omit the overlay; true means draw that overlay once, preserving independent booleans and all 32 combinations. PNGs are native seven-frame strips (2 ticks / 100ms per frame, 14 ticks / 0.7s loop). Resource IDs, exact UVs and SS14 source crops are in `indicator-bindings.json`. These resources do not implement or authorize renderer/controller behavior; GPU animation/cutout/lighting remains unverified.
