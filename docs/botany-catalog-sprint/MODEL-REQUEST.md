# Botany catalog grown-plant art request

**Historical commission; delivery imported after independent review and user authorization (“Authorize plants and wearables”).** The 933-file delivery has verified target hashes; all 84 baseline plant assets remain unchanged. Manifest: 69 visual families, 428 state bindings, 63 geometry bounds; 65 playable species and four gated art families. Killer tomato is excluded and new meatwheat art omitted. The original request ID is lineage, not an open blocker. Import is not owner acceptance, client visual verification, or feature readiness.

## Scope and reuse

Create tray-attached plants for the 70 non-killer-tomato seed identities / 69 RSI families after alias deduplication represented by the authoritative inventory in [`../botany-genetics-sprint/PLANT-MODEL-BRIEF.md`](../botany-genetics-sprint/PLANT-MODEL-BRIEF.md), except the already-delivered six families wheat, tomato, potato, poppy, aloe, and meatwheat. Thus commission **63 new visual families**, not duplicate jobs per seed identity. Fake and real capfruit share one visual family. Preserve those six delivered families and all accepted tray/status art without regeneration or overwrite.

The source inventory, stage counts, source RSI metadata, attribution, and exact state names are defined by that brief. For every commissioned family make stage-1 through N, harvest, and dead. Exclude killer tomato (explicitly not requested), seed packets, loose/held produce, mobs, tools, machines, vines, collision, and gameplay. Do not represent unimplemented special outputs as feature-complete; if a family depends on unsupported gameplay, mark it gated/inactive rather than invent behavior.

## Contract and checkpoint

Reuse `assets/Structures/Botany/BasicBotany/collection/` and its existing pipeline/contract. Root `(8,5,8)` is already block-local: overlay at block origin, one tray plus zero/one whole plant, scale 1, no additional root translation, timer, collider, library, shader, or gameplay logic. Retain native state/frame/direction/delay metadata; preserve optical animation only where the source plant state requires it. Use original RSI states as fidelity references, not pixel-voxel extrusion or generic substitutes.

Before batch production submit original-first, gameplay-scale representative designs for tree/cap, mushroom, flower, vine/fruit, and animated families, alongside the preserved six. Get the agreed design checkpoint before broad production. Prefer one coherent master request with manageable family sub-batches routed through the existing modeler coordinator; do not create 63 duplicate queue jobs or claim/assign a modeler directly.

## Required handoff

Deliver editable embedded-texture `.bbmodel` masters, native JSON/PNG/`.mcmeta` exports, explicit seed-ID→stable visual key→resource mapping and aliases, complete flat destination manifest, source snapshots/hashes, verbatim source attribution and per-RSI license, original-first multi-view/state/animation previews, and read-only saved-file/state/bounds/UV/native-frame validation with export manifest. Preserve source silhouettes, palettes, distinguishing anatomy, progression, harvest and dead states. Record uncertain interpretations and deliberate 3D adaptations. No large assets or previews belong in repository documentation.

Use the existing contract's runtime destination `src/main/resources/assets/moonstation14/models/block/botany/plants/<resource-key>/<state>.json` with matching textures; no overwrite of existing art. Delivery or automated checks do not equal independent visual review, explicit owner acceptance, or gameplay installation. Import only after applicable asset acceptance; gameplay integration retains responsibility for live renderer behavior.

## Sources

Authoritative counts, exact RSI/state inventory, source pin, rendering contract, animation notes, and licensing rules: [`PLANT-MODEL-BRIEF.md`](../botany-genetics-sprint/PLANT-MODEL-BRIEF.md). Existing tray and five-plant art contract/source: `assets/Structures/Botany/BasicBotany/README.md` and its canonical collection. Attribution metadata is complete at `assets/moonstation14/provenance/botany_imports`; independent pinned-source audit verified all 63 exact-family license/copyright fields. GPU/native bake and client visual acceptance remain outstanding; fresh exact-coverage test is pending.
