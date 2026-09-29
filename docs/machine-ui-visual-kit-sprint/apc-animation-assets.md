# APC animation asset provenance

**Scope status:** the layered 2D APC preview is implemented and the visual-kit sprint is owner accepted. The owner waived immediate manual animation checks as an accepted visual risk; the preview's appearance and render cost have not been empirically verified. The provenance, hashes and historical test results below are unaffected by that acceptance.

Original, byte-for-byte PNG copies from `C:\Users\William\Documents\SS14-dev\space-station-14\Resources\Textures\Structures\Power\apc.rsi` at source revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Destination: `src/main/resources/assets/moonstation14/textures/gui/apc/animation/`. No transforms, conversion, or recoloring. Source metadata: `apc.rsi/meta.json` (not copied). Each PNG has an adjacent license notice.

License: **CC-BY-SA-3.0**. Copyright attribution from source metadata: “Taken from tgstation at commit https://github.com/tgstation/tgstation/commit/9c7d509354ee030300f63c701da63c17928c3b3b and heavily modified by EmoGarbage404 (github)”.

| File | Source bytes = destination bytes | Source SHA-256 = destination SHA-256 | Dimensions | 32×32 cells |
| --- | ---: | --- | --- | ---: |
| `base.png` | 693 | `876711e903aba08d47302fcfffc06132005c3d3af3e41408baba498e5e88e796` | 64×64 | 4 (4 directions × 1 frame) |
| `display-full.png` | 440 | `3045c9745e9b3358ad22429616b26464f954b233c803bcd6137aba89afdb726a` | 64×128 | 8 (4 × 2) |
| `display-charging.png` | 948 | `acbfc4f6de3658f3e469dae5bd5fb7e77f54e4b824cc7cc75dfca088519b276f` | 256×128 | 32 (4 × 8) |
| `display-lack.png` | 659 | `8a6094baa2da930e0e1312443af8b4963e7a0bf30cab76f1d87435bbb3032171` | 128×128 | 16 (4 × 4) |

Upstream `meta.json` lists four identical direction delays per display: lack `[0.25, 0.25, 1, 3]` seconds; charging `[0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.2]`; full `[1, 1]`. The descriptor converts these to integer milliseconds. Direction indices 0–3 follow upstream RobustToolbox `RsiDirection`: South, North, East, West. Rows are addressed by sequential direction frames in row-major sheet order (full uses two columns and four rows). Base has no animation delays in metadata and is represented as one static frame per direction. Remote and panel were intentionally not imported. `ApcScreen` now renders the authoritative FULL/CHARGING/LACK display layered over the base as a fixed south-facing GUI preview; UNKNOWN uses the existing static APC image. The reusable sprite animation models support bounded 1–8 directions and do not introduce a 3D model. The initial animation test failure from the base sheet declaration was fixed; focused tests passed. The coordinator's subsequent 171-test isolated GameTest run had one failure (`servicepersistsallfacesandfloorfinishdoesnotmutaterecords`); new `ApcVisualState` and 20-viewer tests were not among the failures, but the suite is not green. The preview has not been manually tested or performance-measured; no numeric load or external-power telemetry is inferred.
