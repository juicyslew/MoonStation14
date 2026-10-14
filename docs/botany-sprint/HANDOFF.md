# Basic botany sprint handoff

## STATUS

**Closed with explicit owner acceptance on 2026-10-14:** “Your work appears to be done. Close your sprint.” Final gameplay candidate: `botany-owner-feedback-20261013-r14`, independently finalized `ready_for_owner` by the central integration lead. Closure is not a claim of complete SS14 parity, a commit, merge, or release.

The five-species loop is delivered: wheat, tomato, potato, poppy, aloe; planting, tending, natural growth, harvesting, eligible eating/grinding, seed extraction, and replanting. Authored tray/plant/grinder/extractor assets are installed. `/ms14 botany_fixture` builds and initializes the fixture, enters the existing registered body, supplies the kit, and arranges scenarios; optional `status` reports setup state. No job/lobby assignment was added.

## ARCHITECTURE

- `PrototypeRuntime` resolves typed seed, produce, tray, tool, and machine profiles with inherited defaults. Block entities consume those profiles; art never drives simulation.
- Server-authoritative tray state owns growth, resources, weeds, readiness, and persisted lifecycle. `LocalAtmosphereService` supplies environmental samples; unknown atmosphere pauses growth. `PlantReagentMetabolism` consumes supported resolved adjustments.
- Ordinary world use goes through `BodyHandRequestService`, `BodyBlockUseTargetProbe`, and live actor/hand authority. One empty-hand USE may be retained for 20 ticks during a hand query. Primary input must reach nomination even when `inventoryView()` is unavailable; replay consumes fresh matching proof once and never retries DENIED automatically.
- `FreshWorldOutputBatch` stages all generated outputs before source commit and compensates failed publication. Fresh harvest/extractor outputs are world-owned `LooseItemEntity`s; normal pickup creates hand ownership. Existing hand/machine items retain ledger tokens.
- Grinder sessions use authenticated snapshots/control revisions, shared machine-slot transfers, and exact reagent math. Eject All refreshes authenticated LiveHands proof per slot because each successful transfer changes hands state. One post-batch cue follows successful nonempty ejection.
- `PowerLiveLoop` and existing APC authorization supply processing machines. The fixture has HV→MV→APC delivery and lighting; active grinder demand is 750 W.

## IMPORTANT FILES

- `src/main/java/com/juicyslew/moonstation14/ms14/botany/`: profiles, tray lifecycle, hand use, produce factory, machines, fixture, audio, and grinder sessions/UI.
- `src/main/resources/data/moonstation14/moonstation14/`: resolved botany profile definitions, including `botany_machine_profile/grinder.json`.
- `ms14/hands/client/BodyHandClient.java`, `BodyHandClientPolicy.java`, `DeferredEmptyHandWorldUse.java`: live nomination/replay and proof policy.
- `ms14/hands/live/FreshWorldOutputBatch.java`, `BodyHandItemTransfer.java`: publication, compensation, and collision-safe harvest placement.
- `ms14/loose_objects/client/LooseItemRenderLayers.java`: stable client-only pile layering; aiming/pile ranking remains in hands client owners.
- Native fixtures: `BotanyHarvestAuthenticatedGameTests`, `GrinderRegisteredPlayerGameTests`, `FreshWorldOutputBatchGameTests`.
- Audits: [CORE-COMPOSITION.md](CORE-COMPOSITION.md), [PROCESSING-COMPOSITION.md](PROCESSING-COMPOSITION.md), [PARITY.md](PARITY.md); setup: [../botany-fixture.md](../botany-fixture.md).

## SS14 CORRESPONDENCE

Audited reference revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Growth uses the audited 3-second poll/15-second cycle and 80% age advancement. Only tomato repeats; other starter plants clear after harvesting.

Upstream manual harvest spawns at user coordinates with `RandomOffset(entity, 0.25f)` and no harvest-side wall rejection (`PlantHolderSystem.cs:371–374,706–740`; `BotanySystem.Seed.cs:133–180,195–198`). The Minecraft accommodation preserves valid independent X/Z scatter within ±0.25, then checks a finite 3×3 fallback at unchanged feet height using resolved physical AABBs. **Include the center unless it was already tried as the preferred point.** Exhaustion denies before plant commit without outputs. Do not globally weaken collision guards.

Grinder processing uses the blender one-shot, explicitly stopped at 70 ticks; power loss does not abort an active job. Chamber insertion is silent; successful chamber ejection uses `machine_switch` at −2 dB once per batch. Beaker insert/eject inherit magazine cues at 0 dB, pitch 1. Profile cues name registered events (`chemistry/chemmaster/insert`, `eject`), not sample filenames. Delivery follows the controlled body-view audience, not the hidden native carrier.

## DECISIONS / WHY

- Reuse shared liquid transfer, ledger, slot, publication, power, examination, and pointer services; no competing ChemMaster or ownership implementation.
- Produce never refills depleted contents. Wheat starts with 650 cents/1650-cent capacity; tomato 441/1841.
- Harvest output placement remains stationary near feet. Pile ordering uses angular aim proximity, depth, UUID; client render offsets are bounded to 0.03 blocks and do not affect physics.
- Native sound position assertions reuse Minecraft packet encoding because distant fixture coordinates lose half-block precision.
- Registered tests require fresh worlds: reusing an ACTIVE lifecycle world can enter `RECOVERY_REQUIRED` and deny enrollment before gameplay.

## KNOWN GAPS

Mutations, genetics, kudzu/special plants, pests, required-gas and light-based plant rules, and machine-specific active model binding are outside the delivered slice or unsupported. Tray warning indicators/examination exist; they do not establish every upstream environmental warning rule. Audio provenance remains unresolved in `docs/audio-unknown-provenance.md`; attribution notices were retained. Complete upstream physics contact behavior was not audited. Owner closure accepts the delivered sprint, not every unimplemented parity boundary or an exhaustive connected-client/GPU test matrix.

## VALIDATION

- Final R14 central gate: build/jar PASS; 8 focused JVM tests, 50 interaction readiness checks, **25/848 selected native tests passed**, including first-request wheat/tomato wall/corner harvest and deterministic preferred/fallback/exhaustion checks.
- R11 audio/input/batch gate: 52 focused JVM, 168 readiness, 27/842 selected native PASS. Native recipient captures cover insert/eject, two-item batch, and busy/stale/empty silence.
- Earlier broad validation is historical: 3376 JVM and 842/842 native PASS; not rerun for R14.
- Owner confirmed models, indicators, examination, extraction/replanting, pile selection/spread, beaker insertion sound, empty-eject silence, nonempty-eject click, then explicitly closed the sprint.
- Plans: `feedback6-gate-plan.json`, `feedback7-gate-plan.json`. Canonical evidence is in sibling `Asset Building Workspace/work/agent_exchange/gate-reports/`. The successful R14 disposable world was removed; diagnostic failure artifacts were retained. `gradlew clean` removes generated build evidence/jars/worlds, not source documentation.

## NEXT WORK

Start with this handoff, then only the matching audit and shared owner. Extend parity gaps only under a new assigned scope. If harvesting regresses, distinguish input suppression, server admission denial, and output-placement exhaustion before changing authority or retry behavior. No further work is assigned by sprint closure.
