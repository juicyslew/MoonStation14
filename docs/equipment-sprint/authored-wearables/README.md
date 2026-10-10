# Standard-player wearables — eleven 3D pieces

[Browse models](index.html) · [Collection preview](previews/collection.png) · [Editable combined fit rig](authoring/outfit_fit.bbmodel) · [Six outfit views](previews/outfit_views.png) · [Walking fit preview](previews/fit_walk.gif)

**Built for review, not approved or installed.** Each garment has an independent editable Blockbench master, embedded texture, external PNG, semantic player-bone groups, source evidence, machine-readable specification, and bone-local OBJ/MTL/quad-JSON export candidates. Individual masters contain equipment only; the combined fit rig includes a neutral reference mannequin whose cubes are marked non-exportable. No source sprite was pasted around a box.

## Fit and format

- Minecraft **Java 1.21.1 / NeoForge 21.1.224**, inspected in the read-only `MoonStation14-agent-02` worktree (`agent/02`), not just main.
- Reused family: **standard_player_wearables_v1**, a new reusable body-anchor family. Reuses this workspace's padded atlas, Blockbench writer/readback and CPU renderer, **not locker/object geometry**.
- **Wide / Steve arms only.** Head 8×8×8; torso 8×12×4; each arm/leg 4×12×4 model units. The vanilla visual body is 32 units tall; this is not a change to Minecraft's 1.8-block player hitbox.
- 16 model units per block; 2 texels per model unit (32 texels/block). Each item has a 256×256 padded atlas; fit rig 512×512. Regions are shared intentionally. Crisp PNGs, no smoothing.
- Authoring axes: +Y up, -Z forward, +X wearer's left; ground origin between feet. Head/body pivot `[0,24,0]`, arms `[±5,22,0]`, legs `[±1.9,12,0]`. Actual per-item render bounds and named part extents are in `specifications/<id>.json`.
- Blockbench **free-format 4.10 schema subset** follows existing known-good projects/shared writer. This is an editable cuboid rig, **not a Java block JSON or GeckoLib model**. No dependency added to the mod.
- No collision volumes; these are worn visual attachments. Layers fit the base skin and one another, not arbitrary armor or thick custom skin overlays. Minor limb-seam overlap follows vanilla body conventions.

## Masters and assumptions

| Item/master | Worn interpretation |
|---|---|
| [grey_cap](authoring/grey_cap.bbmodel) | Forward grey soft cap; crown and shallow projecting brim; not flipped |
| [grey_jumpsuit](authoring/grey_jumpsuit.bbmodel) | Grey shirt/trousers, short sleeves, untinted white collar and waist trinket detail |
| [simple_coat](authoring/simple_coat.bbmodel) | Pale open-front labcoat-derived coat; pockets/lapels; articulated lower hems |
| [black_gloves](authoring/black_gloves.bbmodel) | Paired dark gloves/cuffs on arms; source black RSI, not tinted white gloves |
| [black_shoes](authoring/black_shoes.bbmodel) | Paired black shoes with independent grey soles and toe projection |
| [glasses](authoring/glasses.bbmodel) | Dark rims/temples and source-blue reflected lenses; opaque pixel reflections |
| [red_tie](authoring/red_tie.bbmodel) | Dark red knot, blade and stepped tip, body-bound |
| [black_bandana](authoring/black_bandana.bbmodel) | Lower-face mask bandana with folded taper, rear knot/tail; not head-worn |
| [earpieces](authoring/earpieces.bbmodel) | One left-side grey radio earpiece, cyan source indicator, small boom; not bilateral headphones |
| [bag](authoring/bag.bbmodel) | Grey backpack, straps, top flap, pocket, side pockets and carrying handle |
| [belt](authoring/belt.bbmodel) | Plain neutral-grey strap and buckle; no engineering pouch/tool set |

“Simple coat”, “earpieces” and “belt” are not exact upstream prototype names. Pale/grey art direction follows agent/02 `docs/equipment-sprint/ASSETS.md`. Labcoat construction is a reference, not a medical gameplay assignment. Utility belt construction informed the buckle/strap only: brown engineering pouches were deliberately omitted. Hidden surfaces are conservative and symmetric except the source radio/knot placements. Species-specific, smaller-body and slim-arm sizing are future work, not claimed here.

## States and motion

Each piece implements **one persistent equipped pose**. Arms, legs and head are attached independently so the eventual renderer can inherit the authoritative player's walk/head poses. Clothing does not have independent source animation clips or networking state.

The fit master contains a **1-second, ±25° illustrative walking clip** (`fit_walk_preview_not_source`) with neutral / quarter / midpoint / opposite / neutral keys; `previews/fit_walk.gif` renders 20 matching samples. It is a fit test, not SS14 timing or replacement player locomotion. `previews/fit_poses.png` shows neutral and both strides. Coat lower hems follow the legs; this is a simple segmented Minecraft garment, not cloth simulation.

Every RSI state is explicitly covered in each spec and the [source manifest](source/manifest.json). Open labcoat worn state supplies the chosen coat reference; closed/folded coat, flipped cap, head-worn bandana, other species, alternate prisoner overlays and source inventory/inhand states are **reference-only/out of scope**, not silently implemented. Existing mod inventory/held art is untouched.

## Agent/02 integration handoff

Evidence: `docs/equipment-sprint/MODELLER.md`, `ASSETS.md`, `ms14/equipment/client/EquipmentPlaceholderGeometry.java`, `BodyAppearanceClothingLayer`, `EquipmentRenderPlan.Anchor`, and `PlayerCharacterHarnessRenderer` in the agent/02 worktree. Research was read-only; no Java build/application was run. Direct main-session reads of this worktree were permission-denied; the focused read-only investigator returned the contract and exact placeholder coordinates.

**The current renderer only builds code-defined cuboids and uses a white tinted texture. It does not load worn BBModel/OBJ/JSON files.** Therefore these are integration candidates, not drop-in wearable resources. [Destination manifest](export/manifest.json) and [binding contract](export/binding_contract.json) specify PNG resource paths and required renderer work; geometry destination is intentionally `null` rather than fabricated.

- `export/obj/<id>/<bone>.obj` + `<id>.mtl`: separate textured bone meshes. Positions are **model pixels**, bone-local, +Y down / -Z forward. OBJ V is flipped conventionally. Keep MTL/PNG dependencies together; remap relative texture paths if relocating.
- `export/meshes/<id>.json`: equivalent explicit per-face vertices, normals, normalized top-left UVs, canonical pivot evidence and anchor mapping. **Proposed interchange schema only**, not an existing mod loader/schema. No Blockbench runtime import dependency needed.
- A renderer adapter should apply the selected native `ModelPart.translateAndRotate`, divide vertex coordinates by 16 **once**, select the authored PNG with white tint, and render cutout/no-cull. Do not apply the canonical authoring pivot again. Preserve registry IDs, equipped slots and inventory/held transforms.
- Real crawling/swimming/crouching, arm-width variation, head rotation with body attachments, F5 lighting/occlusion, first-person treatment and resizing require later integration/testing. No gameplay/access/collision/placement/networking changes included.

## Validation and attribution

[Structural report](structural_verification.json) · [Generation report](validation.json) · [Visual review](visual_review.json) · [Rebuild check](reproducibility.json) · [Attribution](ATTRIBUTION.md)

Structural checks cover JSON parsing, unique stable IDs, hierarchy, UV bounds/density, positive finite cuboids, embedded PNG equality, all source-state coverage and preservation hashes, resolved OBJ/MTL/texture dependencies, outward OBJ winding and native-axis conversion. All eleven OBJ exports reproduce their actual BB readback renders **pixel-identically in six views**. Regeneration refuses changed generated outputs; source references are hash-preserved.

Visual review uses the **actual saved models and UVs**, nearest-neighbour CPU orthographic rasterization, front/back/sides/top/three-quarter views, combined outfit, block-scale reference and stride samples. This is not Blockbench execution or Minecraft shader/renderer validation. **Blockbench application import and in-game checks were not run.** Nothing was installed in either reference repository.

Source references and repainted source-derived art retain **CC BY-SA 3.0** attribution; per-resource author/copyright metadata and hashes are preserved in the source manifest and ATTRIBUTION.md. Earlier exploratory references remain preserved but the manifest specifies the selected RSI for each item.

## Open and rebuild

Open any `authoring/<id>.bbmodel` directly in Blockbench; its texture is embedded. Open `outfit_fit.bbmodel` for combined fit/preview animation. Save hand edits to a **separate candidate filename**. Do not overwrite the protected generated collection or delete protection manifests. Promote approved geometry/painter edits into the family scripts deliberately.

From workspace root:

```powershell
python -m venv work/Objects/Clothing/Wearables/venv
work/Objects/Clothing/Wearables/venv/Scripts/python -m pip install -r tooling/requirements-models.txt
# Only recapture references when needed; reads SS14, never writes it:
work/Objects/Clothing/Wearables/venv/Scripts/python tooling/Objects/Clothing/Wearables/references.py
work/Objects/Clothing/Wearables/venv/Scripts/python tooling/Objects/Clothing/Wearables/build.py
work/Objects/Clothing/Wearables/venv/Scripts/python tooling/Objects/Clothing/Wearables/verify.py
```

Tool pins: Python 3.13, Pillow 11.3.0, numpy 2.3.3, PyYAML 6.0.3; shared Blockbench schema 4.10 subset. `specification.py` is the deterministic family descriptor; emitted spec JSON and UV tables record its exact output. Palette/material-role painter is in `build.py`. Both preserve semantic bone names for future fit profiles. No automatic GUI launches.
