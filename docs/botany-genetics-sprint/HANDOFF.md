# Botany genetics — handoff

## STATUS

**Closed and owner accepted, 2026-10-24.** The owner accepted the final fixes and explicitly requested sprint closure. Final serialized integration gate: **PASS**, `run_d9aa4f5efb6352bbbd0eebd4`, generation 1; result acknowledged. No commit or merge was performed as part of closure.

Delivered scope is working mutation/genetics acquisition, persistence and transmission, a paced 52-bay demonstration fixture, and the final interaction/presentation repairs. Six playable crop families are installed: wheat, tomato, potato, poppy, aloe and meatwheat. The wider seed/candidate catalog is **not** a complete playable plant catalog.

Start here rather than the long historical checkpoint. Read [MANUAL.md](MANUAL.md) for fixture actions and [PARITY.md](PARITY.md) for source correspondence. The accepted basic-botany foundation is indexed by [../botany-sprint/HANDOFF.md](../botany-sprint/HANDOFF.md).

## ARCHITECTURE

- Resolved typed `SeedProfile` prototypes supply inherited defaults. `GeneticValue` carries instance genetics; `PlantSeedReference` and `GeneticCarriers` preserve copy-on-write identity through planting, harvest, seed extraction and replanting. `PlantInstance` owns mutable tray state separately.
- `PlantMutationTable` / `PlantMutationSystem` consume resolved ordered mutation descriptors. Persistent effect records are snapshots, not mutable references into prototype tables. Species identity and supported consumers remain separate: a resolved mutation target is not proof its product, art or special gameplay is installed.
- Existing `BotanyHandUseService`, liquid transactions, sampling/pollen owners, `ProduceFactory`, seed extractor and fresh-output publication remain the live consumers. Do not introduce plant-specific replacement interaction or ownership paths.
- The fixture resolves server catalogs and mutation data rather than maintaining competing hardcoded genetics. It starts paused; named sets independently start, pause and resume. Station-local supplies and healthy comparison controls support paced testing. Presets are not evidence of naturally acquired mutations.

## IMPORTANT FILES

Under `src/main/java/com/juicyslew/moonstation14/ms14/`:

- `botany/`: `SeedProfile`, `GeneticValue`, `PlantSeedReference`, `GeneticCarriers`, `PlantMutationSystem`, `PlantMutationRecord`, `PlantPollenCross`, `ProduceFactory`, `HydroponicsTrayBlockEntity`.
- `botany/BotanyMutationShowcase.java`, `SmallBotanyFixtureLayout.java`, `BotanyFixtureScenarios.java`, and `botany/fixture/`: arrangement, scenarios, signs, supplies and pacing.
- `botany/BotanyInspectCommand.java`: authorized development capture/compare, distinct from ordinary examination.
- `examine/ExamineWorldSelector.java`: common bounded candidate selection; `BotanyExamineContributor` / `BotanyExaminePresentation`: ordinary information boundary.
- `hands/client/HandInfoLayout.java`, `BodyHandClient.java`: compact hand labels and existing ammo-row projection.
- `loose_objects/client/LoosePresentationBounds.java`: shared draw-only flat-produce outline geometry, consumed by selected-item and examination outlines.

Runtime data is under `src/main/resources/data/moonstation14/moonstation14/`. Crop bindings are `assets/moonstation14/botany/plant-models.json`. Consult [POTENCY-VISUALS-AUDIT.md](POTENCY-VISUALS-AUDIT.md), [../botany-sprint/ASSETS.md](../botany-sprint/ASSETS.md), and the specific composition documents referenced by PARITY before extending those owners. `CURRENT.md` and `SPRINT.md` retain historical research, not authoritative current activation counts.

## SS14 CORRESPONDENCE

Audited upstream pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91` in the developer's `Documents/SS14-dev/space-station-14` checkout.

- Mutation, seed generation and transmission follow the audited seed prototypes and Botany systems; unsupported special consumers remain explicit boundaries.
- Trays inherit `RefillableSolution`, not `DumpableSolution`. SS14 drag-pouring requires the latter; held-container use transfers through the separate interaction path. Local pouring remains held-beaker **E**, not drag-and-drop.
- Meatwheat yields `MeatwheatBushel`, inheriting `ProduceBaseRuminant → ProduceBase`. The registered bushel retains seed extraction and special-digestion restrictions. Its existing one-use conversion produces the separate `FoodMeatWheat` clump: no Produce or seed metadata. Source initial protein is 10u in the bushel versus 1u in the clump; harvested chemistry follows the seed-generation consumer.
- Canonical meatwheat RSI supplies stages 1–6, harvest/dead, packet and bushel sprites. Retained metadata records CC-BY-SA-3.0 and upstream credits. Crossed-plane crop rendering is an explicit Minecraft presentation adaptation, not an upstream 3D model.

## DECISIONS / WHY

- Inherited produce visual bounds are **0.5–4.5**, retaining unclamped interpolation: potency 5→0.7, 10→0.9, 50→2.5, 100→4.5, 150→6.5. This owner-requested visual adaptation does not change genetics, chemistry, collision, picking or reach.
- Both outlines use actual flat baked-model geometry, including NONE transform, centering, rotation, render-layer offset and interpolation once. Unsupported presentation paths retain physical bounds.
- Ordinary hand labels are 12px; only a panel actually painting the existing ammo row reserves 27px. Movement was explicitly out of scope and left untouched by these repairs.
- Dense entity piles could evict the tray from the 32 examination candidates. The shared selector now retains the nearest **already-admitted** visible block within that cap; range and occlusion remain unchanged. Shift-scroll can reach it.
- Ordinary examination does not disclose full chemical/genetic dumps. Fixture beakers no longer have custom names exposing exact chemicals or station identifiers; legitimate recognizable solution cues remain.

## KNOWN GAPS

The owner's original post-mutagen Shift-click failure was not conclusively reproduced; candidate starvation was a concrete repaired bug, not a proven description-length diagnosis. Closure does not establish exhaustive connected-client/GPU testing or every upstream environmental rule. Full-catalog special products, kudzu and killer-tomato gameplay are not delivered by this sprint. Historical candidate files and asset briefs must not be presented as installed functionality.

## VALIDATION

Final gate passed scoped unit, readiness, native GameTest and packaging/provenance checks. Policy selected 55 unit classes, readiness `interaction,prediction,ui`, and 12 native tags. Earlier focused runs passed 41 tests across nine classes and seven tests across the five activation-sensitive classes; these are separate, overlapping evidence, not an additive final-suite count.

Native growth fixtures now establish a finite sealed atmosphere through the existing room builder and trusted setup owner. `BotanyTrayGameTestEnvironment.prepareAuthenticatedRoom` initializes all 75 unique interior cells (5×5×3), including tray exposure; roof clearance uses consistent absolute coordinates. `BotanyHarvestAuthenticatedGameTests` retains ordinary BE growth and authenticated actions. Production unknown-atmosphere growth pausing was not weakened. Final native validation supersedes earlier readiness and growth-timeout failures.

## NEXT WORK

Next sprint: implement the full playable plant catalog and wilder mutations, including kudzu and killer tomatoes. Begin with existing candidate composition/source audits and real shared consumer/asset availability; verify activation rather than trusting historical counts. Preserve prototype inheritance, seed/product capability separation, shared interaction services and asset attribution. Do not reopen movement work as part of botany expansion.
