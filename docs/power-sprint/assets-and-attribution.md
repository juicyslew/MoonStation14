# Power cable assets: provenance and model handoff

This is the project-local provenance ledger and art handoff for the power-cable visuals. It records reviewed upstream references, not files imported into MoonStation14. The current visual remains the texture-free procedural placeholder; no binary cable art was copied or generated for this handoff.

## Reviewed SS14 source assets (not imported)

The local reference checkout is `C:\Users\William\Documents\SS14-dev\space-station-14`. All three reviewed RSI directories are under `Resources/Textures/Structures/Power/Cables/`; their `meta.json` files declare a 32 × 32 sprite size. Each state below has the numbered frames 0 through 15 (16 frames). These are state/frame names from the metadata, not a claim that corresponding PNGs were imported.

| Reference RSI / exact source path | State names and frames | Metadata provenance | MoonStation14 import status |
|---|---|---|---|
| `lv_cable.rsi` — `C:\Users\William\Documents\SS14-dev\space-station-14\Resources\Textures\Structures\Power\Cables\lv_cable.rsi\` | `lvcable_0` … `lvcable_15` (16 frames) | **CC-BY-SA-3.0**; “Taken from tgstation at commit” [`fcf375d7d9ce6ceed5c7face899725e5655ab640`](https://github.com/tgstation/tgstation/commit/fcf375d7d9ce6ceed5c7face899725e5655ab640). Upstream author/artist is not named in this SS14 metadata. | **NONE** — no image/frame or RSI metadata copied. Reference only. |
| `mv_cable.rsi` — `C:\Users\William\Documents\SS14-dev\space-station-14\Resources\Textures\Structures\Power\Cables\mv_cable.rsi\` | `mvcable_0` … `mvcable_15` and `mvstripes_0` … `mvstripes_15` (two 16-frame states) | **CC-BY-SA-3.0**; “Taken from tgstation at commit” [`fcf375d7d9ce6ceed5c7face899725e5655ab640`](https://github.com/tgstation/tgstation/commit/fcf375d7d9ce6ceed5c7face899725e5655ab640). Upstream author/artist is not named in this SS14 metadata. | **NONE** — no image/frame or RSI metadata copied. Reference only. |
| `hv_cable.rsi` — `C:\Users\William\Documents\SS14-dev\space-station-14\Resources\Textures\Structures\Power\Cables\hv_cable.rsi\` | `hvcable_0` … `hvcable_15` (16 frames) | **CC-BY-SA-4.0**; metadata credit: **“Made by PJB3005”**. This is not the tgstation/CC-BY-SA-3.0 attribution above. | **NONE** — no image/frame or RSI metadata copied. Reference only. |

For exact source metadata, inspect each `meta.json` beside the RSI frames in the paths above. The LV/MV and HV provenance must remain separate in any future asset manifest; do not infer a single license for the cable family. The RSI state/frame inventory above records the metadata names and counts but does not assert the physical PNG filenames are present in this project. Before importing any image, enumerate the actual source image(s) and record each destination file, exact source filename/frame and URL, pinned revision, creator credit as stated, license, modifications, required notice and review status. The statement “NONE” above is the current actual-import ledger: there are no imported SS14 cable files at present.

### If an SS14 image is later imported

First verify the selected frame files at the pinned source revision and applicable license/attribution obligations; then add one ledger row per imported binary, not one broad row for the whole RSI. Preserve separate LV/MV CC-BY-SA-3.0 and HV CC-BY-SA-4.0 notices. Include the appropriate upstream attribution, source link/revision and license text or license link in the asset notice/credits shipped with the mod. For example, the SS14 metadata wording can be retained as: “LV/MV cable sprites: taken from tgstation at commit `fcf375d7d9ce6ceed5c7face899725e5655ab640` (CC-BY-SA-3.0). HV cable sprite: made by PJB3005 (CC-BY-SA-4.0).” Mark that images were modified if cropping, recoloring, resizing, compositing, or conversion is performed, and distribute adaptations under the applicable share-alike terms. This example is a starting credit, not a substitute for review of the actual selected files and applicable license requirements. Project/MoonStation code licensing does not relicense these assets.

## ProjectRed architecture reference (code only)

[ProjectRed](https://github.com/MrTJP/ProjectRed/tree/1.21.1) targets Minecraft 1.21.1 on its `1.21.1` branch and declares the [MIT License](https://github.com/MrTJP/ProjectRed/blob/1.21.1/LICENSE.md). Its `WireModelBuilder` / `WireModelRenderer` implementation is a useful architectural reference for assembling and rendering wire models from connection shape/state rather than treating each appearance as an unrelated static model. See [ProjectRed source search: `WireModelBuilder`](https://github.com/MrTJP/ProjectRed/search?q=WireModelBuilder&type=code) and [source search: `WireModelRenderer`](https://github.com/MrTJP/ProjectRed/search?q=WireModelRenderer&type=code) on that branch. Use the repository search links if source paths move.

This is **architecture reference only**: no ProjectRed runtime dependency is approved or needed, no ProjectRed code is copied, and no ProjectRed textures/assets are copied. MIT applies to its code; it does not change the licenses of the SS14 cable images and does not establish Minecraft/NeoForge APIs for this project. Re-audit the pinned branch/source and exact local 1.21.1 / NeoForge APIs before implementing anything.

## Practical model handoff for the owner

### Preferred next art task: Blockbench-ready 3D geometry

Ask the owner/artist to create a tiny, modular cable geometry set that can be textured independently by tier:

- Model a low-profile insulated run centered on a host face, inset enough to avoid z-fighting; provide straight, end-cap, T/cross junction and corner/edge-turn pieces as separate reusable elements where the engine renderer can select them.
- Keep the mesh centered and dimensioned against a 16-unit block convention, with a clear documented front/up axis and origin at the host-face center. Keep tier-identifying color/markings in a replaceable texture/material, not baked into shared topology geometry.
- Include explicit face placement/orientation variants or a documented transform convention for floor, wall and ceiling. Keep the cable on the face surface; bends only depict actual compatible neighboring face records/topology, never imply an electrical connection by themselves.
- Export the editable `.bbmodel` source plus any project-authored texture(s), document scale/origin/UV assumptions, and provide a small set of rendered previews. The owner decides final asset naming and which pieces are actually needed.

Blockbench geometry is a new project-authored visual and avoids accidental copying of SS14 pixel art. It still requires conversion/loader compatibility, correctly oriented face transforms, resource registration, and in-engine checks for seams, occlusion, z-fighting and chunk-boundary updates. Do not model a plug on a device: this sprint intentionally has no visual plug requirement.

### Alternative: SS14 2D sprite/UV handoff

If the owner chooses the reviewed 2D reference instead, extract only specifically approved numbered frames, preserve their pixel-art scale and alpha, and put each selected PNG through the per-file import ledger above. A practical static-model route needs an artist-prepared tileable strip or a small atlas with a documented cutout transparent base; the SS14 RSI's 32 × 32 state frames are not automatically Minecraft block textures/models. To use the complete states, map/animate all 16 frames intentionally; do not flatten the animation or import the complete atlas by accident. The MV RSI contains both the `mvcable_*` and `mvstripes_*` states, so confirm whether both are actually desired. Record any repacking/cropping/recolor/format conversion as a modification.

For a project-authored atlas/UV route, define repeatable strip UVs and atlas gutters to avoid bleed, keep tier colors and any stripe layer independently replaceable, and deliver the source art plus PNG/export recipe. 2D art is cheaper and closer to SS14, but it is a flat image on a face: it cannot supply cable thickness, convincing sides, edge-wrapped silhouettes or correct branches at arbitrary junctions by itself. Geometry or runtime-generated connection pieces are still necessary for those forms. It also carries the per-file share-alike attribution/notice obligations if based on the SS14 images.

## Current visual and limitations

The implemented visual is currently a **texture-free, tier-colored procedural placeholder**: client-side geometry draws strips from synchronized face-tier records and actual local topology neighbors. Each tier occupies an offset lane; same-face branches and coplanar quads preserve distinct lanes in either direction, and perpendicular edge-turn spokes project matching lanes around the shared cube edge. A hidden top cable beneath a floor tile hides all three tiers at that host face, while a visible neighbor stub still points to the covered tile boundary; hidden cable geometry is not drawn beneath the cover. This is procedural corner projection, not finished dedicated mitered/authored corner art or an SS14 sprite match. It provides functional visibility without importing binary assets but lacks bespoke surface detail and authored silhouettes. Client pixel/depth behavior and first-spawn visual timing remain unverified in a manual client; see [M14](audits/m14-multitier-cables.md), [M15](audits/m15-multitier-wire-visuals.md), and [M16](audits/m16-first-spawn-wire-visuals.md) for current implementation and automated evidence. The M2b audit is retained as historical baseline, not current visual limitation.

No new art, binary assets, code, or active implementation guide is created by this document. Future art must not change cable storage/topology semantics: visuals follow authoritative face state, and a drawn bend alone never creates connectivity.
