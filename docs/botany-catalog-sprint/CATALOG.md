# Botany catalog activation

**Sprint closed by owner, 2026-10-29.** [HANDOFF.md](HANDOFF.md) is the current architectural index. Final feedback enlarged loose peels to scale 1.2/floor offset 0.6 and fixed catalog sign packing (numbered headers and full multiword names); 20 focused tests and independent review passed. Movement was not changed. Older pending-acceptance statements below describe historical checkpoints, not the closure verdict. Rejected tree replacement art remains unimported; its correction request is follow-up work, not delivered sprint content.

## Scope and source

Catalog sources are the independently captured product compositions in `src/test/resources/botany-catalog-candidate/` and `src/test/resources/botany-catalog-source-reference/`. Upstream checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. The source catalog is not itself production content: production must resolve each composition and bind it to a registered item consumed by the existing botany services.

## Current activation inventory

| Capability | Production state | Candidate state |
|---|---:|---:|
| Seed profiles | 71 species + defaults | Already production; do not duplicate |
| Seed packet profiles | 72 | Audited packet/pit compositions promoted where absent |
| Produce profiles | 67 | Includes abstract base and secondary outputs; 65 seed leaves link to a primary profile |
| Extractable profiles | 79 | Independent grinding and juice capabilities; not universal eligibility |
| Food/ingestion composition | 84 / 89 | Independent source solution and consumption policy |

Canonical inventory footprints now contain 207 raw / 202 concrete profiles. All 65 intended species have both packet and product footprint bindings; corrected packet spellings do not replace genuine packet records with product records. Blue/blood tomato and papercane/wood-log/steel-log products resolve Small from the pinned upstream item composition; their packets remain Tiny.

This inventory describes files, not a claim of universal parity. All 65 supported seed leaves link to declared primary-product and packet item registrations. Source-sprite assets cover those families, and grown models are integrated; connected-client rendering and visual acceptance remain pending. The six previously accepted grown-plant families are preserved. Shared owners remain `ProduceFactory`, `BotanyHandUseService`, tray/extractor output publication, food/ingestion, and processing.

The intended supported set is explicit in `BotanyCatalogProductionTest`: aloe, ambrosia_deus, ambrosia_vulgaris, apple, banana, berries, blood_tomato, bloonion, blue_pumpkin, blue_tomato, bungo, cabbage, cannabis, carrots, chanterelle, cherry, chili, chilly, cocoa, corn, cotton, death_nettle, eggplant, eggy, extradimensional_orange, fly_amanita, galaxythistle, garlic, glasstle, golden_apple, grape, holymelon, koibean, laughin_pea, lemon, lemoon, lily, lime, lingzhi, meatwheat, mimana, nettle, oat, onion, onionred, orange, papercane, pea, pineapple, poppy, potato, pumpkin, pyrotton, rainbow_cannabis, rice, soybeans, steelcap, sugarcane, tea_plant, tobacco, tomato, towercap, watermelon, wheat, world_pea. Canonical product/packet aliases come from the resolved seed profiles; pea/world_pea intentionally share a packet identity but not carried genetics.

## Explicit boundaries

| Unavailable plant/effect | Reason |
|---|---|
| Killer tomato | Explicit exclusion; requires living harvest products and hostile mob composition |
| Kudzu mutation | Explicit exclusion; requires world spreading/growth behavior; not a separate seed-catalog row |
| Anomaly berry | Anomaly-flora timed effect/entity-table spawning consumer not implemented |
| Spaceman's trumpet | Instrument playback/UI/network consumer not implemented |
| Gatfruit, real capfruit, fake capfruit | Source firearm identities are registered, but firearm behavior is explicitly inactive; mere identity is not usable gun output |

These remain future parity work, not inert food substitutions. `BotanySpeciesAvailability` provides a temporary incomplete-catalog boundary: mutation still selects from the source ordered target list, but reports an unavailable selected outcome without transforming or rerolling it. This preserves other target probabilities and source graph data; it is not a claim of complete SS14 species-mutation parity.

## Asset boundary

### Pre-closure rendering and asset checkpoint (2026-10-29)

The corrected-render head is R6a, `run_b501b1738bb48bff9aa79fea`: PASS/ACKNOWLEDGED, not owner acceptance. Gallery dimensions are 23×29×5; Y-stretching is removed and floor lights under all 65 labels plus roof lights remain. Existing chunk-cache/native-mesh rendering and lighting behavior is unchanged.

Earlier focused render-code evidence (26 tests / 12 classes and main, test, native compiles) predates peel asset integration. A separate focused peel integration invocation passed 12 tests / 2 classes: `LooseObjectProfileTest` and `PlantNativeOBJChunkBakeTest`. Both actual native NeoForge OBJ assets baked to 66 quads, with textures, parents, worn bindings and ground contact (0.8/0.4) checked; resolved runtime profiles retained collider/trigger and sounds. The banana visual object got required sprite defaults because the decoder requires complete visual fields, a deliberate integration addition beyond the frozen imported profile; do not claim all current asset bytes unchanged. Final independent review `ses_e79522c0affeDuamJuZqh5bdED` passed with no issues. The earlier 26-test and new 12-test invocations may overlap and must not be represented as 38 distinct tests. Peels are installed and ready for limited owner appearance inspection, but the full-feature OWNER_READY gate has not passed; client appearance and FPS remain unverified.

R3 full-production tree delivery `botany-catalog-tree-performance-production-r3-20261028` (digest `bca2fa2df19dd7b314253888562b6fab1e16281eb4e4d6056236295eaaf181da`, revision `production_completion_r7_full_catalog_bounds_optics`) had all 1,420 file hashes verified but failed independent review `ses_e795e7d5bffeTWCBoz6xfVfwii`: adult foliage bottom 1.2625 blocks (<2.1 required) and ordinary pumpkin/blue-pumpkin states exceed the 384-face hard cap. R3 was not imported. Full correction request `botany-catalog-production-corrections-r4-20261029` is submitted; see `PRODUCTION-CORRECTIONS-R4.md`. Runtime tree assets therefore remain unchanged; do not describe R3 as pending import or fixed.

Peel production delivery `botany-peels-3d-loose-production-20261028`, digest `8c80caf07998e20736a89a3fc20947f9b2409573a8050b4ac5679e72ef16b76e`, revision `source-peels-r1-complete-frozen`, passed independent review `ses_e795e47d1ffeFqeceSp2UNQEWU` (117 files, 20 metadata hashes) and was imported with explicit authorization. Both OBJ models use the existing ordinary native-item `block_model` / `WorldItemPresentationRenderer` path, with no jug-specific branch, renderer or gameplay change. The focused native bake/profile checks and final independent review passed as detailed above. The banana visual object received decoder-required sprite defaults beyond the frozen imported profile. Limited owner appearance inspection can proceed; full-feature OWNER_READY has not passed, and live client appearance/FPS remain unverified. Plant R3's public review recorded `needs_changes`; correction request `botany-catalog-production-corrections-r4-20261029` remains open.

Display names use existing PNGs; no new texture is required. Ambrosia flat-shade polish and orange 3D/4D art are deferred. Growth is a three-second poll on the SS14 15-second cycle, three sprouts and 80% aging chance; cannabis first harvest averages about 225 seconds. Existing catalog rooms should receive client render updates after restart/asset reload; do not create another render-test room or delete world contents without authorization.

**2026-10-26 reconciliation:** Both runtime-transfer requests received independent review and user authorization (“Authorize plants and wearables”). Plant delivery: 933 files, all target hashes verified, baseline 84 plant assets unchanged; manifest has 69 visual families, 428 state bindings and 63 geometry bounds. It covers 65 playable species; four gated art families do not activate unsupported gameplay. Killer excluded; new meatwheat art omitted. Wearables: 20 files, 10 mesh/PNG pairs, seven appearances, imported after review/authorization; all hashes match and the 35-asset R2 freeze is unchanged. Historical request IDs are lineage, not open blockers. Import is not acceptance/readiness.

| Current asset scope | Verified disposition / integration boundary |
|---|---|
| Plant delivery (five partitions) | 933 files hash-verified; 69 visual families, 428 state bindings, 63 geometry bounds; four gated art families. All 84 baseline assets unchanged. |
| Part02 Bungo R10 | Explicit owner acceptance for the six revised Bungo states, preserving the complete 101-state / 256-native dependency map. This scoped acceptance is not blanket approval of unrelated plants. |
| Part03 pumpkin/blue-pumpkin foliage R8 | Explicit owner acceptance for the two harvest-state corrections; the complete 75-state / 232-runtime dependency map retains prior states. The historical queue blockage was superseded by normal reviewed/authorized import; the delivered runtime map contains 224 entries (killer excluded). |
| Part04 death-nettle R6 | Explicit owner acceptance/closure of this correction; the complete 82-state / 168-runtime map retains exact prior dependencies. Preserve the scope of each earlier review. |
| Part05 readable-depth R6 | Explicitly accepted as-is; receipt is `candidates/parts/05_fungi_ferns_unusual/readable_depth_r6/user_acceptance.json`. Normal verified import and runtime integration remain separate work. |
| Wearable delivery | Seven appearances imported; 20 files (10 mesh/PNG pairs), all hashes match; 35-asset R2 freeze unchanged. |

Asset paths in this table are relative to `Asset Building Workspace/assets/Structures/Botany/PlantCatalog/` except the wearable candidates under `assets/Objects/Clothing/BotanyProducts/`. Preserve exact effective revisions rather than selecting directories by timestamp.

The historical Part05 metadata-freshness and receipt-vocabulary import blocker was superseded by the repaired MAIN workflow and the fresh reviewed, authorized 141-runtime import. No historical receipt was patched or rewritten.

Original request `botany-catalog-growth-models-2026-10` commissioned 63 new grown-plant visual families; delivery/import is complete as described above. Four gated art families do not activate missing special gameplay consumers. The wearable request `botany-catalog-wearable-products-2026-10` is likewise imported; its seven prototype `worn_model` references are implemented. Wearables are HEAD-only; GPU/native bake, client-camera/alignment and playback acceptance remain unverified. The later owner-rejected tree scaling has been removed; existing authored tree geometry is not yet remodeled taller.

Request `botany-catalog-wearable-products-2026-10` commissions seven source-backed HEAD appearances: both Ambrosias, lily, both watermelon hats, and banana/mimana peels. Carved pumpkin has no source Clothing component and is not included. Existing typed fit/quick-equip/armor composition and all seven `worn_model` references are wired; fit, camera-edge alignment and visual acceptance remain for owner testing, with no placeholder-art claim.

## Validation checkpoint

### Gallery mode update (2026-10-27)

The distinct catalog command constructs a 23×29×5 room with ten columns for all 65 supported species; pea and world_pea remain distinct genetics. Trays are automatically active with compatible finite-air headspaces and shared atmosphere. They begin planted at visual stage 1/age 1, full water/nutrition and healthy soil; natural aging and resource use are not bypassed. Pause/resume freezes or resumes all 65 trays. Seven HEAD-wearable samples are centralized on a raised counter. The existing normal 52-bay fixture and its genetics/planting workflow are preserved; the six unsupported species and kudzu remain excluded. Bounded clear-site setup does not destroy an existing fixture and may choose a nearby empty site; operator authorization is required.

Diagnostic `run_e03420425b53d77338dbaa1d` remains historical filtered evidence (8/1,285 fixtures; gallery and baseline included), not the current render-code gate. Current R6a focused checks and the distinct height-7 lighting diagnostic are summarized above. No full suite, connected-client visual review, FPS proof, or owner acceptance is claimed.

Historical pre-model checkpoint: 39 JVM classes / 97 focused tests passed before new models. Bounds coverage: four Python tests, a 428-entry audit and four consumer tests passed; earlier scoped reviews passed. The model-focused candidate passed 14 JVM classes / 55 tests, with compileJava, compileTestJava and compileGametestJava complete; see `build/botany-catalog-validation/focused-models/complete-candidate.log`. R1/R2/R3 feature gates failed at their respective stages; R3 (`run_6cb9d3185eed4c4a31c2b3cb`) is the settled feature head. R3's native throw reached publication, then exposed a missing host transition and pending-landing marker; the empty-hand result forwarding and committed ordinary-throw marker publication fixes were independently reviewed. The focused four-class/15-test JVM batch and Java/GameTest compiles passed. Follow-up diagnostic `run_ae118fa9a0eee51ff2d9d094` passed all 4 selected `botany-item-damage` fixtures (4/1282 total) after ACK, verifying throw receipt, world entity, pending marker/NBT roundtrip, genetics, exact-current chemistry, natural 70-tick landing and destruction/spill. This diagnostic is not the OWNER_READY replacement gate. R4 retains all 24 former unit selectors, adds the body-hand result codec and both item-contact-damage selectors (27 unit selectors total), and adds prediction readiness alongside interaction; its replacement gate is pending. An incidental unfiltered compile/test timed out without an exit code, is not pass evidence, and unrelated broader failures remain outside scope. Focused creative-catalog corrections passed four classes / 10 tests with independent review. Attribution covers the exact 63-family and 10-frame-name sets; separate four-test Python and 428-entry bounds audits are earlier focused checks, not a full suite.

Initial native diagnostic `run_38a85a9325e9b5190fd38851` failed during server prototype loading, before any selected tests executed: the glasstle destruction output `moonstation14:shard_glass` lacked an item registration. The strict shared output validator correctly rejected the catalog. This was repaired; the subsequent focused native diagnostic PASS is recorded below and predates final model integration.

The shard repair uses existing `ItemFoodSolutionItem`, its real loose-object profile and the authored nonrefillable `shardglass` silicon solution/extraction profile, without granting ingestion, Produce or seed capability. Its source-authentic flat item appearance uses `Objects/Materials/Shards/shard.rsi/shard1`; all three upstream random sprite states and metadata are retained, but per-stack random appearance is not implemented. A bounded static output audit checked 40 processing/trash/destruction references against 213 literal item registrations without further missing local IDs; runtime validators remain authoritative.

Repair checkpoint `build/botany-catalog-validation/shard-output-fix/`: all three compile tasks passed and a fresh 39-class / 97-test JVM selection passed without failures, errors or skips. The final native assertion distinguishes 500 cents from the public trait's 5-unit capacity; its source compiled again in `native-final.txt`.

Corrected native diagnostic `run_393d94b236c05abe416e4857`, generation 1: **PASS**, 4 / 1,282 selected fixtures passed. This predates model integration and is not a full suite, readiness gate, client visual review, manual test or owner acceptance. Fresh checks are currently running; do not conflate them with this result.

`PlantModelManifest` uses per-family local-block `render_bounds`; the shared query unions plant geometry with the unchanged tray/status box. Bounds Python tests and independent review passed. Imported model bindings do not establish client rendering or acceptance.

### Owner correction and new model request (2026-10-27)

Historical rollback removed 2x tree-height scaling and darker lighting; native model rendering was restored. Gallery height 7 was temporary and is superseded by the current 23×29×5 room. Existing tree art remains compact pending corrected, reviewed production assets. Floor sea lanterns light all 65 labels alongside roof lights; finite-air footprint is unchanged. Historical focused checks did not establish current client visual acceptance.

The public asset CLI successfully created model request `botany-catalog-tree-performance-r1-20261027`; coordinator routing is pending. No modeler assignment, delivery, prototype approval, or import authorization is confirmed. It requests a concept checkpoint, native 1:1 geometry for nine woody families with juvenile progression, and performance-budgeted geometry for active imported plants. See `TREE-PERFORMANCE-MODEL-REQUEST.md`; do not treat the request as delivered art or permission to overwrite/import.

Combined JSON+OBJ source audit covers 65/65 resolved visuals without zero/fallback geometry: 33,736 stage-1, 120,624 final-growth, 161,348 harvest triangle-equivalents. Evidence: `build/botany-catalog-validation/performance/`. This is not CPU/GPU frame profiling or proof of an FPS/lag fix; client profiling and visible-gallery review remain pending. The active task is blocked on proper models and client-performance correction, not OWNER_READY. Carrot seed/peel reference existing canonical PNGs and require no new art. Ambrosia flat-frond shade polish and orange 3D/4D art remain deferred.

The local asset-importer source repair passed five focused public-CLI lifecycle regressions covering immutable-copy import, exact acceptance binding and OBJ/MTL dependency closure. Broader workflow checks were not clean due to reported environment/session/launch-smoke failures; no installed-runtime, publication or production-import pass is claimed.

Shared production prototype validators check registered processing, edible-trash and destruction-output identities before catalog publication, with additional food/solution prerequisites for slicing. Reuse those validators rather than a second output-graph implementation. Successful loading does not prove every secondary action executes correctly: native trash emission, slicing, butchering, conversion and destruction remain separate coverage boundaries.

Special loose profiles are attached through existing default item components; blue/blood tomatoes reuse the source-equivalent tomato impact/destruction profile. Server/client catalogs use the existing prototype synchronization owner, not a new bundled loose-profile catalog. Connected-client rendering remains unverified.

Asset audit: all 110 tracked botany source PNG/JSON blobs match HEAD under Git's clean-filter comparison; tracked botany block models are unchanged. Raw checkout line endings must not be mistaken for source changes. Orange, holymelon and rainbow-cannabis runtime atlases are byte-identical to pinned source `produce.png` files. Their intended 32×32 frame grids require explicit animation dimensions. Orange/rainbow 0.08-second source frames use deterministic 1/2-tick schedules over five cycles, preserving average source cadence; holymelon uses six ticks per frame. These source/resource checks are not a connected-client visual review.

## Parity reference

The source mappings and inherited defaults are documented in `docs/botany-genetics-sprint/CATALOG-CANDIDATE.md`, `docs/botany-genetics-sprint/CATALOG-REFERENCES.md`, and the source-reference fixture. The current upstream revision is the pinned checkout recorded above. Candidate files remain the audit fixtures; production activation must retain the source-reference coverage and add production resolution/runtime-consumer coverage.
