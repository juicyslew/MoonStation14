# Feature readiness — gameplay windows, offline previews and interaction proof

## Status and assignment

**2026-10-10: implementation plan for another agent; no toolkit implementation delivered.** Read [HANDOFF.md](HANDOFF.md) first. The owner requests a separate sprint to reduce recurring UI, layout, accessibility, composition and prediction defects **before manual testing**. This is independent of the Sable/ship sprint; do not add it to that implementation or assume it belongs in agent/04.

Deliver a small, reusable readiness toolkit using current services, two real production UI consumers, a representative ordinary interaction path, and concise agent-facing contracts. Documentation alone, a new checklist alone, or an attractive mock screenshot does not satisfy this assignment.

### Owner requirements

- Gameplay windows are draggable and closable, remain available while the cursor is locked, and do not prevent ordinary controlled-body movement in locked mode. Opening a click-operated UI provides the appropriate pointer access; switching pointer mode does not arbitrarily close its session. Retain existing movement in free-cursor mode where supported, except while a text editor legitimately owns input.
- Default machine chrome: title top left, X top right, compact dimensions, small chat-like text, no unnecessary empty areas, and recognizable SS14 layout/content parity. Respect accepted specialized storage/HUD layouts rather than silently restyling everything.
- Features must be reachable through their real user entry path and remain available through subsequent admission/lifecycle checks. Direct helper invocation is not accessibility proof.
- Resolved prototypes, inherited defaults and supported composition drive runtime behavior. Reuse current owners; do not bypass them to make a test pass.
- Prediction is considered for every action. Preserve verified action-specific SS14 prediction and server authority; missing prediction cannot be deferred to the owner's manual discovery.
- **Use resources only when necessary.** Prefer focused JVM checks and offline images for routine iteration; avoid client/server launches per worker or layout edit when cheaper checks suffice. Heavy validation may run concurrently when justified. Be mindful of CPU/memory contention, but a running server/client or another agent's validation is not an automatic blocker and does not need to stop.

### Bootstrap

1. Confirm branch/base revision, `AGENTS.md`, Java/loader targets, worktree status, and currently active owners. Preserve unrelated edits. No commit, staging, push, merge, or migration is requested.
2. Ensure this whole directory is present. Worktrees created from HEAD omit uncommitted documents; copy the package or include it through the owner's documentation workflow.
3. Read relevant handoffs in the reuse map, then targeted current source. Historical experiments/retired gates are not current authority; especially do not re-enable `ms14/movement` experimental routing.
4. Record baseline test results and the current paths actually being exercised. Existing failures cannot be declared unrelated without evidence. Reuse existing language services; no duplicate LSP backend.
5. Coordinate ownership of shared UI/input/hand/prototype files with agents working elsewhere before overlapping integration. Use independent source/build outputs per worktree and isolated validation worlds. Avoid unnecessary resource use; coordinate heavy runs when actual contention warrants it, not as a mandatory prerequisite.

## 1. Scope and architecture

### Deliverables

1. A tested default gameplay-window contract extending existing chrome, geometry, workspace and input owners.
2. Lightweight offline PNG previews driven by production layout/paint logic, with real supported text/assets and automatic geometry checks.
3. Reusable accessibility-sequence and deterministic delayed-packet test support, exercised by current features rather than a production-only dummy device.
4. A small readiness command/report, selective contracts and a short root instruction pointer, so future agents know which checks to run without expanding every prompt.

Default first consumers: **ChemMaster workspace and airlock wire UI**. They exercise a retained overlay and an embedded screen with different lifecycle/paint boundaries. Confirm their current acceptance/source in R0; replace a consumer only with a concrete blocking reason and an equally representative existing feature. Reuse **held timed ingestion** as the non-window action/prediction example. This sprint does not implement missing ChemMaster output gameplay or expand ingestion mechanics.

### Non-goals

No new general UI platform, universal scene graph, replacement networking/prediction engine, new gameplay devices, wholesale UI migration/restyling, asset relicensing, generic robot that drives Minecraft, exhaustive screenshots of every UI, unrelated bug cleanup, permanent client/server farms, or claim that headless tests prove every native event/GPU behavior. Do not revive the deferred device UI proposal as an implementation mandate.

Use the smallest shared behavior needed by the selected current callers. Leave feature-specific authority, session identity, data, transaction and lifecycle ownership where they belong. A shared window is presentation/input composition, not a new source of inventory or machine truth.

## 2. Reuse map

Java paths below are relative to `src/main/java/com/juicyslew/moonstation14/ms14/`; tests mirror these below `src/test/java/.../ms14/`. Verify current symbol responsibilities before extending them.

| Area | Existing implementation / evidence |
| --- | --- |
| Default window and chrome | `ui/client/{GameplayWindow,GameplayHudPolicy,MachineWindowRenderer,MachineActionButtonRenderer}`; `ui/client/window/{WindowGeometryController,WindowChromeButtons,TitlePressRouting}`. `GameplayWindow` already represents movement intent/text capture; do not add a competing marker/interface just because its name differs from this plan. |
| Workspace/input/z-order | `hands/client/{StoragePopupScreen,StoragePopupWorkspace,StoragePopupLifecycle,PointerModePolicy,PopupMovementIntent}`; `ui/client/pointer/{PointerOverlayWindow,PointerOverlayRegistry,PointerWorkspaceLayers}`. Host admission, input and paint ownership are separate integration obligations. |
| Layout/text references | `chemistry/chemmaster/client/{ChemMasterLayout,ChemMasterOverlay,ChemMasterWorkspace}`; `doors/ui/client/{AirlockWireScreen,AirlockWireScreenLayout,WireEmbeddedScreenInitPolicy,AirlockWireLifecycle}`; compact `chat/client/LocalSpeechReviewOverlay`. Small text currently includes half-scale chat; do not invent an unrelated font approximation. |
| Existing UI models/widgets | `ui/model/`, `ui/client/{navigation,collection,animation}/`, `ui/client/gallery/MachineUiGalleryScreen`. The gallery is client-only, not authoritative interaction proof. |
| Current tests | `TitlePressRoutingTest`, `PointerModePolicyTest`, `PointerWorkspaceRoutingTest`, `PointerOverlayRegistryTest`, `PointerWorkspaceLayersTest`, `ChemMasterLayoutTest`, `AirlockWireScreenLayoutTest`, `StoragePopupLayoutTest`, and current window/chrome tests. |
| Existing image generation | `hands/client/GridEntrySpriteRendererTest` loads the real pickaxe PNG, reuses `StorageSpriteGeometry`, and writes an inspection sheet. Also `loose_objects/client/{FlatLooseSpriteGeometryTest,JugModelVisualTest}`. Reuse image/resource-loading/output conventions; these are diagnostics, not a complete production UI rasterizer. |
| User action routing | `hands/client/{BodyHandClient,BodyInputGesturePolicy,BodyHandClientPolicy,PrimaryWorldGesture}`; `hands/network/BodyHandRequestService`; `hands/live/{HandActorAuthority,BodyInteractionAccess,BodyItemTargetProbe,LiveHandsCommitGateway}`; `character/CharacterControlSystem`; current body-control/lease owner. Check registration and actual event adapters, not just pure route selection. |
| Timed work/prediction | `timed_interaction/{TimedInteractionEngine,TimedInteractionSystem,client/}`; `ingestion/{BodyTimedIngestionService,TimedIngestionAccess}`; existing body/epoch/sequence reconciliation and hand publication. |
| Composition | `prototype/{PrototypeRuntime,PrototypeManager,PrototypeResolver}` and typed catalogs; `PrototypeRuntimeTest`; selected feature's actual runtime consumer and inheritance tests. |
| Live evidence | Current hand/menu/ingestion GameTests, `docs/gametest-tags.md`; trusted-body fixtures do not prove authenticated player admission. |

Read first: `docs/hands-inventory-program/HANDOFF.md`, `docs/machine-ui-visual-kit-sprint/HANDOFF.md`, `docs/airlocks-sprint/HANDOFF.md`, `docs/chemmaster-beaker-sprint/HANDOFF.md`, `docs/timed-interactions-ingestion-sprint/HANDOFF.md`, and `docs/construction-crafting-sprint/HANDOFF.md` for recent pointer/event-order lessons. Body/lifecycle and newer machine handoffs narrow further lookup. `docs/device-ui-platform-sprint/HANDOFF.md` is a **deferred proposal**, not delivered shared-window infrastructure.

Every implementation report identifies reused/extended paths and justifies substantial overlap. Independent review rejects parallel chrome/input logic, duplicate layout formulas, copied authority checks, private inventory writes and competing packet-correlation owners.

## 3. SS14 and composition requirements

Verify checkout `C:\Users\William\Documents\SS14-dev\space-station-14` at **`c9df5ef5d675b0d1d226828bddf6b78c28502d91`**. Reuse its existing services; no duplicate Roslyn backend. If unavailable, label source mappings unverified.

Known anchors: `Content.Client/UserInterface/Controls/FancyWindow.xaml.cs` derives from Robust `BaseWindow`; it is not a universal compact-size specification. `Content.Client/Chemistry/UI/{ChemMasterBoundUserInterface.cs,ChemMasterWindow.xaml.cs,ChemMasterWindow.xaml}`, `Content.Shared/Chemistry/SharedChemMaster.cs`, and server `ChemMasterSystem` distinguish UI messages from authoritative machine work. `Content.Shared/DoAfter` and shared hands/interaction systems establish action-specific prediction; do not extrapolate ChemMaster authority to every action.

R0 must correct exact upstream paths and record the selected window/layout/control references. For any feature behavior/composition extended here, resolve its full relevant prototype parent chain, inherited components/defaults and actual consuming systems **before fields/code**. Reuse existing verified feature parity documents as indexes, then check changed behavior. A pure harness/adapter addition need not repeat unrelated prototype audits; it cannot introduce new behavior under that exception.

Only supported local composition is represented. No fabricated prototype family for universal UI/testing policy. Keep test/view fixture states separate from authoritative gameplay data. Test at least one inherited value at the actual selected runtime consumer, not only JSON resolution.

## 4. Contracts to implement

### A. Gameplay window behavior

Prefer existing factories/helpers and narrow composition over a new base-class hierarchy. Common chrome geometry must drive **both painting and hit testing**. Registration/admission should make a new standard window usable without editing several forgotten class-name lists; improve the existing boundary only within its responsibilities, preserving vanilla modal-screen exclusions.

Default windows provide title-left, X-right, drag capture excluding controls, clamped GUI-space placement, compact styling, and correct init/resize. Embedded `Screen` consumers undergo normal initialization before paint/input. Closing invokes existing session cleanup; title dragging cannot send machine actions. Pointer capture releases on mouse-up outside the window and on context loss.

Keep window observation lifetime separate from active-action eligibility. Stun can reject an action without automatically destroying a read-only UI. Preserve current body/epoch, machine/host identity, reach and material-unavailability rules; don't retain an invalid actionable session to satisfy a visual test.

Pointer intent has an owner/reason: automatic opening, explicit manual unlock, and legitimate context resets are not interchangeable. Opening a new interactive window provides usable pointer access; refresh packets do not undo an explicit cursor lock. Toggling L-Alt retains eligible windows. Keep rebindable controls, hands/HUD/chat and body movement routing, with explicit text-focus exceptions. Existing specialized last-window semantics must be tested, not silently normalized.

Shared sequence tests exercise production controller/router seams: open, several ticks/refreshes, click, title drag, release, lock, movement dispatch, unlock, close; include another overlapping window, viewport/GUI-scale changes, chat/text focus and authority reset. Assert painted-front window receives input and an occluded lower window/world does not also activate. Do not confuse a sampled movement-intent helper with actual dispatch evidence.

### B. Offline UI preview

Use actual immutable view states, production layout and shared painting operations. If a small drawing boundary is needed, extract it for both current production consumers and the preview backend; do not maintain a second hand-authored UI renderer or force every future control into a speculative scene graph. Minecraft adapter stays thin; test-only CPU raster backend stays out of the production runtime where practical. Do not record/replay all painting every game frame solely to generate previews.

The backend covers operations the two selected screens actually use: fills, sprite/UV drawing, text, transforms, clipping, ordering and supported animation time. Use deterministic fonts/metrics from the supported game resources or a verified matching provider, including actual translation/fallback and formatting paths. **Java2D system fonts are not acceptable substitutes for review-critical UI text.** Reuse game resources as development inputs; do not check in/redistribute Minecraft jars or vanilla font assets merely to make previews self-contained. Preserve licenses/attribution on SS14 resources and any imported libraries.

Resource packs, unsupported glyphs/providers, GPU effects, native item/entity rendering and any unimplemented operation must be explicitly identified. Fail or label unsupported output, never silently replace it with a plausible fake. Scope can be narrowed with a concrete limitation, but title/body text, core controls and clipping for the selected UIs must be reliable before adoption. Test graphics/text metric agreement against the supported production path; use a representative real-render comparison at the proof gate if necessary.

Produce a full-viewport image plus useful cropped detail/contact sheet for each representative case. Full viewport establishes world-view obstruction; optional world backdrop is a fixed diagnostic reference, not live gameplay evidence. Include empty/populated/overflow, long labels/large values, pending/disabled/rejected, hover/focus, scroll endpoints and moved/clamped windows across supported GUI scales/viewports. Fixed state/clock/seed and explicit resource inputs make output reproducible.

Derive footprint/text/padding budgets from accepted chat/current UI examples and pinned upstream content. Do not invent one percent cap for all UIs or stretch panels to fill the screen. Assert close/title bounds, no unintended overlap, actual text fit/ellipsis policy, clip/hit agreement, compact footprint and reachable controls. Empty content may legitimately have space; visual review determines needless whitespace rather than enforcing a magic density ratio.

Images go under a worktree-local `build/reports/...` directory with a manifest: revision, screen/state, viewport/GUI scale, fonts/resources, supported operations, diagnostics and paths. Determinism checks compare decoded pixels/geometry under pinned inputs, not incidental PNG metadata. Baselines require independent review; never automatically bless freshly generated screenshots. Reviewers **open the images** and record findings against current accepted and SS14 references. Generating files alone is not visual acceptance.

### C. Accessibility and lifecycle proof

One reusable harness drives selected production entry points with synthetic external input/transport and a controllable clock. It must not be a second dispatcher or a pile of mocks that always return permission. Exercise the actual routing adapter and handler chain where runnable outside Minecraft; factor only narrow current seams when engine coupling prevents this.

Required evidence chain: gesture/binding/modifier → shared route/registration → issued request with correct identity/proof → authoritative admission → activation → subsequent revalidation/update → observable user result. For menus, prove next ticks/refreshes retain a usable session and click delivery; for ingestion, prove accepted progress survives normal ticks/movement before one completion and normal inventory refresh.

Keep exact actor/body/epoch, status, source tokens/revisions, loaded reach/LOS and session identity checks real at their appropriate boundary. A pure JVM harness cannot pretend to authenticate a live `ServerPlayer`/world by mocking every check: cover that remaining boundary with a focused existing-platform GameTest. Report each layer honestly: helper, composed routing, authoritative fixture, or connected observation. Full native mouse/OS cursor proof remains a small connected check, not a claim from CPU tests.

Include rejection/context-change cases: stunned new action, stale body/epoch/proof, replaced target, out of reach/unloaded, session mismatch and duplicate request. Failure leaves authority/state conserved; eligible observations remain or close according to real policy. Record structured denial/closure reasons using current result/logging channels; no new telemetry service or production spam.

### D. Prediction and composition readiness

Each selected action gets a short matrix: verified SS14 behavior; immediate local feedback; predicted state if any; authoritative commit; identity/sequence key; rejection correction; pending timeout/context-reset behavior. Neither “only visuals may ever be predicted” nor “predict all mutations” is a universal rule. Machine requests may remain authoritative while hover/selection/pending feedback reacts immediately; held ingestion currently predicts progress without consuming liquid before commitment.

Wrap current transport/reconciliation seams in a deterministic test packet scheduler: configurable delay, dropped/duplicate/reordered replies and unsolicited snapshots with an explicit tick source. It is test support, not a replacement network stack. Test zero delay plus representative roughly 100–300 ms round trips, before acknowledgement and after resolution. Local feedback must appear on the next appropriate UI/update cycle; quantify what is and is not expected immediately.

No double completion, duplicate sound/consumption, stale revision overwrite, rollback flicker, or pending state stuck after rejection/body change. Exercise actual current correlation owners. Preserve server transactions and prototype-driven admission. Add a resolved-inheritance/runtime-consumer assertion for selected supported behavior and a negative unsupported-composition case. Don't broaden physiological or machine gameplay to manufacture prediction examples.

## 5. Ordered work units

### R0 — Current-boundary audit and measurable plan

Record actual window/input/authority ownership, selected consumers, upstream references, current tests/diagnostics, baseline failures, fonts/assets access and the minimum offline operations. Define representative states, dimensions, evidence layers, packet delays and bounded runtime/memory budget for readiness checks. Read known limitations before promising authenticated or GPU proof.

**Exit:** concise architecture/parity map and test catalog; current reuse targets confirmed; no stale historical movement/UI assumptions. Stop and report a concrete obstacle if a faithful text/render path cannot be provided cheaply; don't solve it with a fake preview.

### R1 — Standard window behavior and two production consumers

Extend existing chrome/geometry/registration/pointer helpers; integrate ChemMaster and wire consumers without gratuitous restyling. Add composed sequence tests, title/control paint-hit agreement, host admission/init, lock/unlock/close and keyboard/movement ownership checks. Preserve specialized layouts, quotas and lifecycle rules. New defaults should make omission difficult, not just add another optional helper.

**Exit:** selected production UIs use shared behavior and sequence tests pass; consumer-specific differences are explicit; no competing window/input owners. No manual Minecraft iteration required for ordinary changes.

### R2 — Production-driven offline previews and visual review

Extract only the required shared painting boundary, add CPU backend/assets/text support, and generate the selected state/viewport contact sheets with geometry diagnostics. Reuse existing image-test conventions. Independently inspect the images and compare a representative real-render frame at the boundary check if needed. Fix fidelity before relying on new golden outputs.

**Exit:** a documented lightweight command generates faithful supported previews without a client/server; unsupported cases fail/label clearly; reviewer opens images; footprint/overflow/hit/clip checks catch defects; CPU output is not claimed universal GPU parity.

### R3 — Reachable interaction and prediction sequences

Build the narrow shared entry-path/packet-clock test support. Exercise selected window activation/click/retention/closure and held ingestion's user dispatch, progress, denial and completion. Cover actual authenticated authoritative admission in a tagged platform fixture where pure tests cannot represent it. Audit resolved composition at the live consumer and retain existing transaction conservation.

**Exit:** successful ordinary user path plus meaningful gate failures and delayed/reordered-reply cases pass. Tests do not call only the final feature helper or bypass all authorities. Document any remaining native-input boundary for the final short connected check.

### R4 — Cheap readiness gate and scoped agent guidance

Add an opt-in task/command using the current Gradle/JUnit arrangement for applicable focused checks and preview generation; do not force the full native suite/client boot into routine readiness. Reuse the test catalog to select UI versus interaction versus prediction checks. Unknown/empty selectors fail clearly. Output an evidence report with checked categories, pass/fail/not-applicable/unverified, tested route, image manifest, latency cases, resource limitations and cost.

After executable checks exist, add a **short** root `AGENTS.md` pointer to focused contracts (for example `docs/feature-readiness/{README,ui,interaction,prediction}.md`, naming finalized during implementation). Include composition by referring to the existing rule and prototype tests, not duplicating a long audit. Use directory-specific instruction pointers only where the agent tooling actually loads them; don't maintain conflicting sources of policy.

Feature-ready requires applicable checks plus independent review; justified not-applicable categories and unverified boundaries remain visible. A report cannot self-certify aesthetic review or invent screenshots. Provide a very short report template and a new-window example using the shared implementation, not copied boilerplate. Distinguish ready-for-owner-review from owner acceptance.

**Exit:** future workers can discover and run the right cheap checks; reviewer can see what was bypassed/missing; root guidance stays compact; no universal framework or parallel policy catalog.

### R5 — Integrated validation and delegation handoff

Run applicable focused JVM checks/preview review and the minimum live boundary fixtures; because shared UI/input/lifecycle changes are broad, run/report the full GameTest suite **once at the integrated gate**. Perform a short connected window/input/render comparison when needed, rather than repeatedly booting after layout edits. Use isolated outputs/worlds and consider actual resource contention; concurrency itself is not a validation blocker. Separate existing failures with baseline evidence; don't fix unrelated systems in this sprint.

Demonstrate that ordinary defects cause readiness failure: remove/misplace X, enlarge/overlap a control, omit host/input registration, introduce immediate closure, drop prediction feedback or mishandle a stale reply in isolated test mutations. Use existing parameterized negative cases where practical; no dependency on a heavyweight mutation-testing product. Revert disposable mutations. Measure check time/peak memory honestly on the owner's computer and report whether repeated runs use incremental/cache paths.

**Exit:** acceptance matrix linked to actual evidence; connected/native limitations explicit; useful failure artifacts retained; disposable successful worlds removed. Update handoff only with delivered state, not this proposal's aspirations. Owner closure is separate.

Sequencing: R0 → R1 → R2 → R3 → R4 → R5. After R0/R1 establish boundaries, offline backend and action/packet tests may be delegated in parallel **only if source ownership is disjoint**. A lead owns common adapter changes. Run resource-intensive validation only when needed; parallel runs are permitted with isolated outputs/worlds and mindful resource use.

## 6. Validation and acceptance

Use current JUnit/source sets; task names must be checked in the implementation worktree. Typical existing commands:

```powershell
./gradlew test --tests "<actual focused test class or package>"
./gradlew compileGametestJava
# Implement a cheap readiness/preview task in R4; it does not exist yet.
./gradlew runGameTestServer "-Pms14GameTestTags=feature-readiness" -Pms14GameTestDir=build/gametest-tags-fixture
# Once at the broad integrated gate:
./gradlew runGameTestServer -Pms14GameTestDir=build/gametest-tags-fixture
```

Add `@GameTestTags` and `feature-readiness` before using that new selector; current preflight catalog is authoritative. Tags use OR and do not isolate/order tests. Fixtures require explicit `templateNamespace = "moonstation14"`; follow `docs/gametest-tags.md`. Never overwrite owner world/configuration or re-enable retired simulation gates. Coordinate use of the designated disposable fixture directory; never share an actively running world between worktrees.

Report filtered/full, selected/total, pass/fail/incomplete counts and baseline comparisons. Unit/image loops do not need GameTests on each run. Reuse incremental compilation and run caches, but a stale report is not evidence for current source. No separate game process per subagent. Preserve logs and useful failures, and clean successful disposable worlds at the end.

| Criterion | Required evidence |
| --- | --- |
| Default UI behavior | Two real consumers; drag/X/init/resize/host and lock/unlock/input sequences; no duplicated shared service. |
| Compact/parity layout | Actual production-driven full-viewport previews plus cropped detail, supported real text/assets, automated geometry/footprint checks and image review against references. |
| User accessibility | Ordinary entry dispatch, admitted activation, later revalidation and observable result; authoritative authenticated fixture where needed; no helper-only claim. |
| Composition | Verified relevant ancestry/default/consumer mapping and resolved runtime assertion; no fake unsupported components or private ownership bypass. |
| Prediction | Action-specific matrix and deterministic delay/rejection/reorder/epoch cases; immediate eligible feedback, correct authority and reconciliation. |
| Readiness process | Cheap discoverable command/report, tiny root pointer, scoped contracts, negative regression detection and clearly labeled unverified/native boundaries. |
| Resource discipline | Measured time/memory, routine checks need no Minecraft process, necessary native/connected checks rather than gratuitous repeated boots; no mandatory serialization or automatic blocking on another running game. |

Manual check, if needed, stays concise: open each selected UI, click/drag/X, toggle cursor and move, check text focus/overlap, compare one preview frame, start/stop ingestion. Native/GPU results require actual observation; mark pending if unavailable, not falsely passed.

## 7. Reporting and closure

Per work unit: changed/reused paths, verified/adapted/pending parity, evidence layer, test results, preview files actually reviewed, costs, limitations and next gate. No implementation claim from this planning session. Do not stage or publish screenshots with unclear/new attribution; preserve existing media notices and use `docs/audio-unknown-provenance.md` for genuinely unclear imported audio.

On actual owner closure, the lead updates `HANDOFF.md` using `docs/HANDOFF_TEMPLATE.md`, roughly under 1,200 words: shipped commands/contracts, window/paint/route seams, supported offline operations/font/resource inputs, consumers, prediction/authority boundaries, tests/cost/native evidence, known limits and next useful targets. Keep manual instructions short. This file is an implementation assignment, not sprint-completion acceptance.
