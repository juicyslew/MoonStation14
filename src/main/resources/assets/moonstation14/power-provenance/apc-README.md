# apc — first-pass, not yet approved

[Editable master](authoring/apc.bbmodel) · [model views](previews/model_views.png) · [states](previews/states.png) · [block scale](previews/block_scale.png)

- **8 x 7 x 3.3 canonical units** / **0.5000 x 0.4375 x 0.2062 blocks**; 16 units/block, +Y up, -Z front. Ground-centered authoring converts X/Z +8 for Java. Wall fixtures intentionally mount at the south boundary facing north; blockstate rotations preserve their existing placement convention.
- State samples: full, charging, lack, remote, unpowered, panel_open, damaged. Native surface cycles: full, charging, lack, remote. No invented rigid movement or empty animation clips. Masters contain embedded static atlases; native-timed GIFs and PNG/mcmeta strips live separately. Java animation is quantized to whole ticks; repeated loops preserve source average duration exactly. GIF centisecond rounding is visual-review-only.
- Wall-panel visual adaptation inside existing power-device block. Current facing-only state; optional display/panel states need later authoritative binding. No new charge/access mechanics.
- Family: assets/Structures/Power/APC. Related pipe variants reuse eight-facet hollow shells, materials, part names and source state mappings; unrelated silhouettes are not forced into the locker template. Shared atlas/BB/rasterizer reused without changing lockers.
- Actual texture PNGs: `textures/`; ordinary Java exports and dependency-complete tube OBJ candidates: `export/resourcepack/`. Exact intended destinations: [manifest](export/manifest.json). No transfer, gameplay/dependency/networking/collision changes performed. Java JSON is runtime art; `.bbmodel` is editable authoring only.
- Persistent channels/composable overlay fragments: [binding contract](export/binding_contract.json), `export/components/`. Keep maintenance overlays independent when selecting display cycles. Sample projects are named single-state reviews, not a full Cartesian runtime state matrix.
- Structural checks, saved authoring/Java UV-render equality, finite rotation bounds and texture schedules recorded in [validation](validation.json). Actual Blockbench execution **not run**; in-game loading/placement/lighting/performance **not tested**. CPU renderer does nearest-neighbor orthographic geometry/UV review, not Minecraft culling/light/loader emulation. Inspect all six views before transfer.
- Assumptions: Hidden back/underside surfaces conservatively reuse visible construction/materials. Source sprite is not a UV map; only suitable isolated flat screen overlays may be reused. No reference repository modified. Local CPU render is not Blockbench execution or an in-game check.

## Open / rebuild / preserve

Open `authoring/apc.bbmodel` in Blockbench; embedded texture needs no path repair/plugin. Other persistent samples are sibling projects. Texture clips preview externally; no false claim of authoring mechanical animation.

From workspace root: `python tooling/Batches/ImplementedItems/build.py --asset apc`. Install only the pinned workspace `tooling/requirements-models.txt` if needed. Source checkout not needed to rebuild. Specs saved before geometry in `model_spec.json`; generated UV specification in `authoring/compiled_spec.json`. Do not regenerate specifications to retrieve assets. Save hand edits in a separate candidate and deliberately promote them into spec/painting recipes. `generated_hashes.json` refuses overwriting edited/missing outputs; do not remove it to bypass the guard.

[Attribution](ATTRIBUTION.md). Tools used: Python 3.13.13, Pillow 11.3.0, NumPy 2.3.3; Blockbench codec reference 4.12.6/4.10 (not execution).
