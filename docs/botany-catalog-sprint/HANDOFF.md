# Botany catalog completion

## STATUS

**Closed by owner, 2026-10-29:** “Good. I guess this is done then. Close the sprint.” Owner reported the fixture mostly fine; the subsequent peel-size and catalog-sign repairs passed focused checks and independent review. This acceptance closes the current installed scope, not the rejected replacement tree delivery or every remaining SS14 parity gap. No commit, push, or merge was performed by this sprint.

## ARCHITECTURE

- The playable set contains 65 species. Production uses resolved seed, packet, produce, food, processing, extraction, equipment and loose-object compositions. Reuse `ProduceFactory`, `BotanyHandUseService`, `HydroponicsTrayBlockEntity`, shared output publication and existing prototype synchronization; do not introduce a second catalog or processing pipeline.
- `BotanySpeciesAvailability` bounds unsupported species. Mutation selects the original source target and reports unavailable outcomes without rerolling or renormalizing probabilities.
- `/ms14 botany_fixture catalog` builds a separate 23×29×5, ten-column gallery containing all 65 supported species. Trays start normally at age/stage 1 with full water/nutrition, suitable finite atmosphere and ordinary growth/resource use. Pause/resume covers all 65. The normal 52-bay planting/genetics fixture remains separate. Setup selects a clear site; repeated commands can leave multiple rooms.
- Static plant quads use native chunk meshes through `PlantTrayBakedModel` and live model registration. The BER retains warning indicators and plant fallback when geometry crosses a 16³ render-section boundary. Shared routing selects exactly one plant path. Cache lifetime follows the bake epoch; an immutable volatile tray appearance snapshot limits client model invalidation to visual changes.
- Cached plant quads disable AO and directional shading to preserve the former BER appearance while retaining world-derived light. Geometry renders at native scale 1, with raw manifest bounds; runtime tree stretching was removed. Real floor lights under all 65 labels supplement roof lights.
- Loose banana/mimana peels use native OBJ item models through the existing `block_model` world-presentation path, not the jug special case. Each bakes to 66 quads. Banana's resolved visual scale is **1.2**, floor offset **0.6**; mimana inherits these. This is 50% larger than the first imported presentation. Collider/slip behavior, held transforms and separate HEAD-worn models are unchanged. Decoder-required sprite defaults remain in the visual object.
- Catalog sign packing preserves numbered headers and complete multiword names, including tea plant and world pea. Previously it overwrote the header/first word. Already placed signs retain their saved text.

## IMPORTANT FILES

- `src/main/java/com/juicyslew/moonstation14/ms14/botany/`: tray, catalog policy, shared consumers; `client/` contains manifest, baked model, registration and BER owners; `fixture/` contains gallery planning, commands, pacing and signs.
- `src/main/resources/assets/moonstation14/botany/plant-models.json`: 69 visual families / 428 bindings, including gated art. `provenance/botany_imports/` retains original attribution.
- `src/main/resources/assets/moonstation14/models/item/botany/peels/`, canonical `models/item/trash_*_peel.json`, matching textures and `data/moonstation14/moonstation14/loose_object_profile/`: loose peel assets/composition. Editable peel source/provenance lives under `asset-source/moonstation14/botany/peels/`.
- `PlantNativeOBJChunkBakeTest`, `LooseObjectProfileTest`, `BotanyFixtureSignsTest`, plant-cache/bounds tests and `BotanyCatalogGalleryGameTests`: focused starting points.
- `CATALOG.md`: species inventory, aliases, exclusions and historical evidence. Earlier subsystem indexes: `docs/botany-genetics-sprint/HANDOFF.md`, `docs/botany-sprint/HANDOFF.md`.

## SS14 CORRESPONDENCE

Source checkout was verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; source-reference fixtures preserve inherited composition. Growth uses a three-second poll, 15-second cycle and 80% aging chance; maturation is not immediate harvest readiness. Cannabis first harvest averages approximately 225 seconds.

Banana peel resolves `TrashBananaPeel -> BaseItem`; mimana inherits banana and overrides its art and slip sound volume. Upstream uses separate loose/held/worn 2D states; authored loose 3D geometry is a Minecraft adaptation. Preserve RSI attribution and CC-BY-SA-3.0 obligations. Seven HEAD appearances are installed; holy-watermelon uses four authored frames at 0.5 seconds through shared frame timing.

## DECISIONS / WHY

Use native geometry, resolved composition and shared render/output services. Do not stretch models, force fullbright, create new rooms solely for performance inspection, or delete existing fixtures. Examine prioritizes the first loose-item candidate over floor items while preserving window targeting, opaque occlusion and manual cycling. Stationary gravity-only watermelon drops survive; deliberate throws/impacts retain damage and spill behavior.

## KNOWN GAPS

- Replacement tree/performance delivery `botany-catalog-tree-performance-production-r3-20261028`, digest `bca2fa2df19dd7b314253888562b6fab1e16281eb4e4d6056236295eaaf181da`, was **rejected and not imported**: low live foliage violated the 2.1-block clearance requirement, and pumpkin/blue-pumpkin states exceeded geometry caps. Complete correction request `botany-catalog-production-corrections-r4-20261029` was submitted. Closure does not accept, import or cancel that external request; check its scoped status before any follow-up. See `PRODUCTION-CORRECTIONS-R4.md`.
- Killer tomato, anomaly berry, gatfruit, real/fake capfruit, spaceman's trumpet and separately kudzu remain unavailable. Ambrosia shading polish and the extradimensional-orange 3D/4D redesign are deferred.
- Stop-moving snapback was reported. No movement-path changes were found in this sprint's diff; whether it is a regression remains unverified. Movement was deliberately left to its active owners.
- No measured client FPS/GPU result or comprehensive client visual certification is claimed.

## VALIDATION

Final owner-feedback compile/focused batch: **20 tests across four classes passed**, including all 65 sign headers/line limits, complete tea/world-pea names, resolved peel composition and native ground contact at 1.2/0.6. Independent scoped review and `git diff --check` passed. Earlier render checks passed 26 tests/12 classes with main/test/GameTest compilation; initial peel integration passed 12 tests/two classes. These invocations overlap: do not add their counts.

Historical serialized feature PASS/ACK `run_b501b1738bb48bff9aa79fea` predates the latest renderer/assets/owner repairs. Earlier filtered native diagnostics covered gallery care/growth, baseline fixture, lighting, examine and item damage, but predate some final changes. The later render-state diagnostic was not admitted due to `SCOPE_BINDING_MISMATCH`; no new final serialized or post-acceptance full-suite pass is claimed. Synthetic BER vertex-emission probes are CPU evidence only, not FPS proof.

## NEXT WORK

No further sprint implementation is authorized by closure. Future work should start from the unresolved items above, retain the installed acceptance baseline, and use the canonical workflow owner for validation infrastructure repair and any required post-acceptance regression. Review corrected frozen assets independently before seeking fresh exact-delivery import authorization.
