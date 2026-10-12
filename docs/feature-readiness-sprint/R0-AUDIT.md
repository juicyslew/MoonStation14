# R0 — current boundaries and validation plan

## Baseline

- Worktree: `agent/04`, base `695cab96f26fcb7a8343b5b1e0ff32638693aef1`; initially clean. No staging, publishing or owner configuration changes.
- Minecraft 1.21.1, NeoForge 21.1.224, Java 21.
- Current baseline: `./gradlew test compileGametestJava --console=plain`: **3,158 tests, 602 suites, zero failures/errors/skips**, 86.34 seconds wall time. Compilation succeeded with existing deprecation warnings. Logs/cost: `build/reports/feature-readiness/baseline-{jvm.log,cost.txt}`. Compile/cache paths were mixed; peak memory was not measured.
- Historical native failures are not current baseline evidence. No native baseline has been run. Another worktree had an active server at bootstrap. Owner clarification removes mandatory serialization: another running game is not an automatic blocker; use isolated outputs/worlds and resources only when necessary.

## Owners and reuse

| Responsibility | Current owner / planned extension |
| --- | --- |
| Window identity, movement/text ownership | `ui/client/GameplayWindow`; retain this contract, not a competing marker. |
| Placement, captured drag and resize | `ui/client/window/WindowGeometryController`; reuse for wire UI's currently unclamped raw-coordinate drag. |
| Chrome / painting | `WindowChromeButtons`, `TitlePressRouting`, `MachineWindowRenderer`; couple shared chrome paint/hit geometry without replacing feature layouts. |
| Overlay host, ordering and input | `PointerOverlayRegistry`, `PointerWorkspaceLayers`, `StoragePopupScreen`; registration currently requires separate named host admission. Extend existing boundary, preserve modal exclusions. |
| ChemMaster presentation and correlation | `ChemMasterWorkspace`, `ChemMasterOverlay`, `ChemMasterLayout`; retained half-scale overlay, 169×136 GUI pixels, independent row scrolling and absolute scissors. Preserve request watermark/session owners. |
| Wire presentation | `AirlockWireScreen`, layout/init/lifecycle policies; embedded screen initialized before paint and resized through workspace. Preserve session cleanup and authority. |
| Actions and admission | `BodyHandClient`, shared gesture policies, `BodyHandRequestService`, `HandActorAuthority`, `BodyInteractionAccess`, feature services. Test actual adapters, not a new dispatcher. |
| Timed ingestion | `BodyTimedIngestionService`, `TimedIngestionAccess`, timed engine/client prediction identity; no early liquid consumption or private hands writes. |

R1 owns shared window/input integration first. R2 may extract only ordered paint primitives required by both consumers (fill, text, sprite/UV, transform, clip, explicit animation time). R3 wraps existing transport/correlation in test-only scheduling; common adapters require explicit ownership, not parallel edits.

**Deliberate chrome correction:** the wire UI's old close route covered a different rectangle from its painted X (right-edge/top-band clicks could close; part of the painted control was outside the route). R1 intentionally uses `WindowChromeButtons.closeBounds` for both painting and hit testing, including its inset 18×18 wire control. This changes the old oversized/asymmetric hit region to meet the sprint's paint/hit agreement requirement; it is a Minecraft presentation correction, not an upstream gameplay/prototype rule. Do not restore the mismatched legacy rectangle merely to preserve an accidental click region.

## Parity references

External checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `Content.Client/UserInterface/Controls/FancyWindow.xaml.cs` derives from Robust `BaseWindow`; this is not a universal compact-size specification. Minecraft retained windows/pointer controls are adaptations.
- ChemMaster client `Content.Client/Chemistry/UI/ChemMasterWindow.xaml(.cs)` and BUI; shared `SharedChemMaster.cs`; server `ChemMasterSystem.cs`. Prototype `chem_master.yml`: `ChemMaster → [BaseMachinePowered, ConstructibleMachine]`, powered ancestry through `BaseMachine → BaseStructure`; resolved demand **250 W**. Full existing mapping: `../chemmaster-beaker-sprint/COMPOSITION.md`. Preserve actual local resolved runtime policies rather than substituting upstream range blindly.
- Wire UI: `Content.Client/Wires/UI/{WiresBoundUserInterface,WiresMenu}.cs`; server `Content.Server/Wires/WiresSystem.cs`. Menu sends requests and displays server states. Upstream menu uses height 200/minimum width 320, name/serial/help/close. `Airlock → BaseStructure` in `base_structureairlocks.yml` supplies wire/panel/security/visual/UI composition; selected variants and tools remain feature-specific. Local mapping: `../airlocks-sprint/{PARITY,WIRE-UI}.md`.
- Shared DoAfter and `Content.Shared/Nutrition/EntitySystems/IngestionSystem.cs` defer consumption to completion. Jug resolved inheritance/defaults are indexed in `../timed-interactions-ingestion-sprint/PARITY.md` (5u, 1 second, no destruction). Progress-only prediction is the local adaptation, not proof all upstream DoAfter mutations are client-predicted.

| Selected action | Immediate feedback / prediction | Commit and correction owner |
| --- | --- | --- |
| ChemMaster request | Selection/pending presentation; no reagent prediction | Existing session/revision/request-ID server responses and ACK watermark. |
| Wire cut/mend/pulse | Responsive controls/pending; no predicted wire mutation | Server validates tools/state/authority; refresh reconciles session/revision state. Upstream timed tool work is not new sprint scope. |
| Held ingestion | Provisional progress, never early consumption | Timed snapshots correlate body UUID/sequence; authoritative completion transaction and owner commit hint refresh hands. |

## Final lifetime parity finding

## Connected interaction parity clarification

Pinned `ActivatableUISystem.cs:139–168,183–246` distinguishes `ActivateInWorldEvent` from held-item `InteractUsingEvent`. ChemMaster has no `RequiredItems`, so held-item use does not itself activate its BUI; explicit activation is independent of hand contents, subject to power/interaction permission. The local default explicit activation binding is **E** (`world_storage`), issuing `ACTIVATE_WORLD`; normal `USE` retains existing insertion/empty-hand behavior. No rule making jugs dispenser-slot-compatible was added.

Jug inherits `DrinkBase`'s `DrainableSolution` (`drink`); ChemMaster has unlimited `DumpableSolution` (`buffer`). `SolutionDumpingSystem.cs:33–145` checks complex interaction, nonempty/open source/target and transfers full source volume for unlimited targets. The local existing Shift-left press/aim/release flow is a fluid dump, not item-slot insertion. Capture must precede examination and consume the originating physical press; its normal BEGIN/ACK/RELEASE authority and transfer transaction remain the owner. Sources: `chem_master.yml:55–98`, `drinks_base.yml:23–43`, `chemical-containers.yml:1–25` at the verified pin.

### Passive session lifetime

Pinned Robust `SharedUserInterfaceSystem.cs:1163–1204` maintains open BUIs by range, not action-blocker permission; actor teardown closes them (`:151–154`). `UserInterfaceComponent` defaults `RequireInputValidation=true`; `SharedInteractionSystem.cs:154–190` rejects validated input when `CanInteract` fails. ChemMaster does not override those defaults in `chem_master.yml:55–61`. Thus transient stun should deny mutations without closing otherwise valid read-only observation. The local ChemMaster now separates session validity from mutation eligibility; real registered-player tests verified retained query, correlated denied mutation, recovery and range closure. Identity/epoch, loaded machine, dimension, resolved range and existing power invalidation are preserved. Airlock wire sessions already separate passive query from action denial. This correction does not authorize actions while stunned or retain sessions through body replacement/disconnect. See `chemmaster-session-parity.md`.

## Tests, states and remaining R0 risks

Existing focused catalog: title/chrome/geometry tests; pointer policy/workspace/registry/layers; ChemMaster layout/protocol/session ACK/processing/prototype; wire layout/lifecycle; hand client/gesture policies; ingestion profiles/commit and timed engine/snapshots; prototype runtime. Existing native ChemMaster, wire and timed-ingestion fixtures are reuse targets, but trusted-body/FakePlayer success is not authenticated user admission.

Preview cases: empty/populated/overflow, long labels/large quantities, pending/disabled/rejected, hover/focus, scroll endpoints, moved/clamped windows; wire no-state/power/cut/animation/timeout states. Include full viewport and detail crops at representative 320×240 and 640×360 GUI viewports and scales 1–3 where minimum dimensions permit. Use deterministic clock/state/resources. Unsupported states/providers must fail or be labeled, not approximated.

Packet scenarios: zero delay and 100/200/300ms round trips with an explicit clock; dropped/duplicate/reordered replies, unsolicited updates, stale revisions/body/epoch/session changes. Required evidence layers remain separate: helper, composed routing, authoritative fixture, connected observation.

**Font fidelity remains a blocking subgate for R2 adoption.** Assets are available in development jars/object stores. The lead verified that `build/moddev/artifacts/neoforge-21.1.224-sources.jar` contains `BitmapProvider`, `SpaceProvider`, `GlyphInfo`, `FontSet`, `BakedGlyph`, `Font` and `StringSplitter` sources, so matching source-backed bitmap metrics can be investigated without launching a client. Backend preparation is assigned separately from production paint integration. Actual resource stacking/provider ordering, advances/raster and translation/fallback still require executable verification; an empty include in the resources jar alone does not establish the complete runtime provider set. No system-font substitution, redistributed vanilla assets, or second hand-authored UI renderer.

Cheap checks initially target under two minutes warm incremental per selected category; this is a budget to measure, not an achieved claim. Limit CPU preview states to a bounded contact-sheet set; measure time/memory before expanding. JVM checks are routine. Necessary native fixtures and one full suite belong at the integrated gate, without mandatory serialization or stopping other running games. Consider actual resource contention and preserve world/build isolation; connected cursor/GPU comparison remains explicitly pending until observed.
