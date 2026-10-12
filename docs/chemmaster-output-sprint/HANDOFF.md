# ChemMaster output sprint

## STATUS

**CLOSED — owner accepted.** Output production, usable pills/canisters, bottle filling, direct field editing, dose persistence, and held-storage activation are implemented. The owner confirmed the final dose-preservation and second-Z-close retest worked and requested closure. Changes remain uncommitted; no commit, push, or merge was requested.

## ARCHITECTURE

- ChemMaster has distinct input/output `MachineItemSlot` instances under the existing slot owner and ledger lifecycle. Output accepts resolved bottle/canister profile identities through the machine's inherited policy; the machine never manufactures either package.
- `ChemMasterPackagingPlan` allocates whole mixtures with existing cent-exact reagent splitting. The BE's extended `commitSolutions` stages/revalidates source, output, buffer, revisions, tokens, and placement identity. New pill ownership uses ledger child-claim batching/compensation and existing `GridContents`; there is no parallel ownership or mixture engine.
- Typed `chem_container` profiles preserve optional component presence: Bottle has solution, Pill has solution, Canister has storage. Runtime consumers use inherited resolved values. Bare valid hosts are virtually empty; pickup does not need an eagerly written grid component.
- The existing session/control owner authenticates processing and publishes immutable snapshots. Packaging uses current state/input/output and **buffer revision proofs for both sources**; clients must use snapshot `bufferRevision`, not infer it from state revision. Zero is a valid initial revision. Results remain authoritative, with normal request correlation and no visible pending status.
- The Output form is local draft state. Numeric-input signatures prevent unrelated frames from overwriting edits. Explicit pill dose survives production; count is recalculated using that dose. An invalid/unavailable chosen dose disables creation rather than silently lowering it. Label regeneration is a separate server flag.
- Native `EditBox` handles editing. Client-only accessors expose display/selection state to shared `OrderedPaint`. Live and offline rendering share `ChemMasterPaint.OutputPresentation`; uniform 10x2 palette cells use one sprite pixel scale. Registry focus is cleared before dispatch, **not again after the clicked field acquires it**. Tabs precede header dragging.
- Both world and popup Z routes reuse `HeldUseRequestDispatch`: quick-equip → confirmed held-storage toggle → ordinary held use. Bags/belts resolve quick-equip false; other clothing retains prototype-driven priority.

## IMPORTANT FILES

Paths under `src/main/java/com/juicyslew/moonstation14/`:

- `ms14/chemistry/chemmaster/`: BE, packaging plan, control request, window state, wire codec, session service, fixture.
- `ms14/chemistry/chemmaster/client/`: form, overlay/workspace, layout, Output painter, text-field painter/editors, `ChemMasterPillArt`.
- `ms14/chemistry/output/`: typed profiles, merge strategy, catalog.
- `ms14/storage/grid/GridContents.java`, `ms14/hands/network/BodyHandStateService.java`: host contents and snapshot representation.
- `ms14/hands/client/`: held-use dispatch, body client/policy, `StoragePopupScreen` focus ownership.
- `ms14/hands/live/BodyLiquidInteraction.java`, timed ingestion owners: whole-pill completion and capacity feedback.
- `mixin/client/EditBoxDisplayAccessor.java`; client registration in `moonstation14.mixins.json`.

Resources: `data/moonstation14/moonstation14/{chem_container,ingestion_profile,equipment_item}/`; pill models/textures and `sounds/ss14/items/pill.ogg` under `assets/moonstation14/`. See `COMPOSITION.md` for mappings/attribution and `MANUAL.md` for regression steps.

Tests: chemistry prototype/packaging/protocol/form/refresh/painter tests; `StoragePopupHostFocusRoutingTest`, `HeldUseRequestDispatchTest`; authenticated output and lifecycle GameTests; `HeldTimedIngestionGameTests`; existing `ProductionUiPreviewTest` catalog.

## SS14 CORRESPONDENCE

Pinned source: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- Canonical chemistry Bottle: 30u, not the unrelated 100u DrinkBottle. Pill: 20u ceiling, nonreacting food, whole-dose swallowing after 0.6s/12 ticks. Canister: 5x2 pill-only grid. Twenty appearances, type 0–19; labels limited to 50 UTF-16 characters and displayed additively as `baseName (label)`.
- Buffer is the default draw source; Input draws from the beaker. Successful transfers regenerate labels; buffer discard does too. Input discard, source/type changes, packaging, and ordinary refreshes do not.
- Exact SS14 pill audio and original contributors are attributed beside the asset: **CC-BY-NC-3.0**, explicitly approved by the owner for this noncommercial project. Pill sprites retain their CC-BY-SA-3.0 attribution. Audio plays once at authoritative successful completion, not predicted/start playback.
- Capacity rejection sends the owner-only localized actionbar “You can't swallow any more!” after completion, retaining the pill. This adapts SS14 popup presentation, not physiology.

## DECISIONS / WHY

- Reject requests exceeding actual free canister cells **before withdrawal**. Upstream's unchecked oversized insertion loop is not claimed atomic; local rejection preserves conservation/ownership.
- Preserve explicit dosage to prevent accidental dosage changes during production. This deliberately differs from SS14's per-state numeric resuggestion; see `COMPOSITION.md` for exact refresh rules.
- Existing 5u bottle transfer/drinking and bag/belt storage projections remain adaptations. Local bag/belt grids are 7x4/8x2; the mapped upstream leather waist bag's storage is 4x2, distinct from outer item footprint.

## KNOWN GAPS

Force-feeding and generic nested storage remain unsupported. Lifecycle validation covers BE save/recreate with the same live ledger, not chunk unload or persisted-ledger restart. Local stomach capacity remains the existing 50u limit; resolved physiology parity is not claimed. Static previews cannot independently establish native GPU/input behavior; connected owner acceptance supplies the final live check.

## VALIDATION

- Final full JVM: **3,219 passed**, zero failures/errors/skips.
- Final full unfiltered native GameTests: **800/800 passed**.
- Latest affected readiness: **158/158 passed** (`ui,interaction,prediction,preview`). Preview matrix: 72 full cases, two contact sheets, 146 PNGs, all 20 pill hashes; actual Output controls/sprites asserted. Independent visual review passed for uniform scaling, fields, and reagent colors.
- Registered-player tests cover ordinary bare-canister pickup, normal insert/open/control/eject paths, both sources, bottle production, conservation/child ownership, and the exact capacity-message packet once at completion. Owner confirmed final live behavior and closure.
- Evidence: `build/reports/chemmaster-output/explicit-dose-final-jvm-summary.json`, `explicit-dose-final-jvm-xml-pass/`, `explicit-dose-final-native.log`, `explicit-dose-final-native-summary.json`; preview manifest at `build/reports/feature-readiness/previews/manifest.json`.

## NEXT WORK

No sprint work remains. Extend existing prototype, session, transaction, grid, ingestion, and paint owners for future features. Preserve explicit-dose safety, both Z routes, host focus retention, and complete snapshot revision proofs. Consult the beaker and feature-readiness handoffs for prerequisite subsystems.
