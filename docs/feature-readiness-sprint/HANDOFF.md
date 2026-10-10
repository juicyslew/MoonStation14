# Feature readiness — delegation handoff

## STATUS

**Planning/research package, 2026-10-10. No implementation, builds, screenshots or runtime validation delivered.** The owner requests execution by another agent. Read [Instructions.md](Instructions.md). This is a separate task from Sable/ship feasibility; no particular worktree is assigned automatically.

Copy the whole directory if uncommitted: a worktree created from HEAD will not inherit working-tree documents. Preserve unrelated changes; no staging/commit/push requested. Coordinate overlapping shared UI/input work with other agents and serialize heavy validation.

## ARCHITECTURE

Goal: catch recurring window behavior, layout, feature inaccessibility, composition and missing-prediction defects before owner manual testing, without repeatedly launching servers/clients.

Extend existing window geometry/chrome and pointer/workspace owners into a tested default contract. A feature supplies content and session rules; shared behavior supplies drag, title-left/X-right, compact metrics, host/init/paint/input routing and cursor/movement ownership. Keep read-only observation lifetime separate from active-action permission.

Offline previews consume production layout/painting and supported actual text/assets through a narrow shared drawing boundary. CPU renderer produces deterministic full-viewport/cropped PNGs and diagnostics; no independently maintained imitation UI. It cannot claim native GPU/event/cursor proof. Resource/glyph/operation limitations must fail or be labeled, not silently approximated.

Accessibility tests drive actual entry/dispatch/authority/retention/result seams with external input/transport/clock seams only. Use focused platform fixtures for authenticated world boundaries that cannot be honestly tested in a JVM harness. Delay/reorder/rejection tests wrap existing correlation owners, not new networking.

First production consumers: ChemMaster workspace, airlock wire UI; held timed ingestion demonstrates non-window interaction/progress prediction. No new ChemMaster output or ingestion gameplay. Only proven shared behavior is extracted; no universal UI/testing/prediction framework or wholesale screen migration.

## IMPORTANT FILES

Read handoff indexes: hands-inventory-program, machine-ui-visual-kit, airlocks, chemmaster-beaker, timed-interactions-ingestion and construction-crafting. Device-ui-platform remains a deferred proposal; historical experimental movement/default gates do not describe every current route.

Java paths below are under `src/main/java/com/juicyslew/moonstation14/ms14/`:

- `ui/client/{GameplayWindow,GameplayHudPolicy,MachineWindowRenderer,MachineActionButtonRenderer}`; `window/{WindowGeometryController,WindowChromeButtons,TitlePressRouting}`.
- `ui/client/pointer/{PointerOverlayWindow,PointerOverlayRegistry,PointerWorkspaceLayers}`; `hands/client/{StoragePopupScreen,PointerModePolicy,StoragePopupWorkspace,StoragePopupLifecycle,PopupMovementIntent}`. Registry host admission currently includes named screen families; registering a window alone does not establish every host/input route.
- `chemistry/chemmaster/client/{ChemMasterLayout,ChemMasterOverlay,ChemMasterWorkspace}`, `doors/ui/client/{AirlockWireScreen,AirlockWireScreenLayout,WireEmbeddedScreenInitPolicy,AirlockWireLifecycle}` and compact chat renderer.
- Hands gesture/client/request/access/authority/commit owners; `character/CharacterControlSystem`, current body leases, `timed_interaction/`, `ingestion/`, `prototype/` and typed catalogs.

Existing tests: `TitlePressRoutingTest`, pointer policy/workspace/registry/layers tests, `ChemMasterLayoutTest`, wire/storage layouts, hand-client policy, timed engine, prototype runtime and live hand/menu/ingestion fixtures. `GridEntrySpriteRendererTest` already generates real-resource diagnostic PNGs using `StorageSpriteGeometry`; reuse its conventions, not its limited rendering as a complete UI backend. Other loose-object image diagnostics exist.

## SS14 CORRESPONDENCE

Verify external checkout at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. `FancyWindow` derives from Robust `BaseWindow`; it is not one universal compact-size specification. ChemMaster BUI/client/server sources distinguish local presentation from authoritative machine commands. Shared hands/interaction/DoAfter consumers establish action-specific prediction.

Before extending feature fields/behavior, resolve relevant full prototype ancestry/defaults/actual consumers and map supported local composition. Existing parity docs are indexes, not proof every new behavior has been audited. Test resolved runtime consumption, not just JSON. Pure harness changes do not require unrelated entity audits but cannot smuggle in new rules.

Retain SS14 asset attribution and per-file licenses. Development use of vanilla font/resources does not authorize checking in redistributed Minecraft assets.

## DECISIONS / WHY

Ordered gates: **R0 audit/baseline → R1 shared window behavior → R2 offline previews → R3 accessibility/latency/composition proof → R4 cheap readiness command/scoped guidance → R5 integrated review**. Split independent implementation after boundaries are established; do not parallel-edit common adapters or run competing game processes.

Make omissions difficult through shared defaults and executable sequence checks. Do not rely on more warnings. Keep root `AGENTS.md` additions short and link focused contracts only after checks exist.

Images must include full viewport to expose excessive footprint, supported real font/text/assets, empty/populated/overflow/pending/error states and GUI-scale variations. Reviewer opens images; merely generating or automatically accepting baselines is insufficient. Geometry/hit/clip checks complement visual judgment.

Prediction matrices explicitly distinguish immediate feedback, predicted state, server commit and correction. Neither predict-all nor visuals-only is universal. Existing ingestion predicts progress without early liquid consumption.

## KNOWN GAPS

Current policy/layout tests do not prove complete actual user routing or OS mouse behavior. Trusted body fixtures do not prove authenticated admission. No complete production UI offline renderer or reusable fake-packet-clock harness was found; recheck current source before adding one.

Font/provider fidelity, engine-coupled screen initialization and authenticated fixture construction are R0 risks. Report limitations honestly. CPU previews cannot prove GPU/scissor/native input behavior; use one short coordinated boundary comparison when needed. No existing test counts or full-suite green status is claimed by this planning session.

## VALIDATION

Research/read-only source inspection only. Execution uses focused JUnit/image checks for routine iterations. Add tagged authoritative fixtures for missing boundaries; `docs/gametest-tags.md` governs selectors/templates/isolation. One full GameTest run at the broad integrated gate, plus concise connected checks where necessary; not per worker/layout edit. Measure incremental time/memory and demonstrate detection of representative broken states. Preserve useful failures, clean disposable successful worlds, and do not alter owner configuration.

## NEXT WORK

R0 confirms current service ownership, upstream references, consumer states, fonts/assets access and measurable check budget. R1/R2 prove two real windows before adopting shared preview/golden checks. R3/R4 make normal user-path and prediction evidence discoverable for future features. R5 independently reviews reuse, evidence layers and native limits. Update this handoff on actual owner closure, not when proposed tooling merely compiles.
