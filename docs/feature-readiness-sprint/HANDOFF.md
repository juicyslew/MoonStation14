# Feature readiness — implementation handoff

## STATUS

Closed by owner on 2026-10-11; delivered on `agent/04`. Owner confirmed connected window/input/render/drinking checks and all six defects found during testing now work correctly. Full native suite is **not green**: two failures remain unattributed. See [ACCEPTANCE.md](ACCEPTANCE.md) for evidence and limitations, [README.md](README.md) for commands and [Instructions.md](Instructions.md) for scope.

Changes remain uncommitted; no staging, commit, push or merge requested. Preserve unrelated work. A new worktree from HEAD does not inherit these working-tree changes.

## ARCHITECTURE

- ChemMaster and airlock wires use existing shared chrome/geometry/title routing and pointer workspace owners. `PointerOverlayRegistry` owns registered-window promotion and capture; `PointerWorkspaceLayers` supplies matching paint/input family ranks. Storage/analyzer family ordering and fixed configurator panes are preserved. Captured drag/release precedes overlap hit testing. Locked cursor mode admits world clicks, not pointer-window hits; the click that dismisses free-cursor mode is consumed rather than replayed as world use.
- Gameplay keyboard capture emits neutral movement without sampling window movement. Existing epoch/sequence, prediction and server authority remain the owners.
- Both production UIs invoke `OrderedPaint` painters. `GuiGraphicsPaint` is the native adapter; test CPU rendering uses selected real bitmap-font/resources, transforms and GUI-scale clipping. No independently maintained imitation UI.
- `BodyHandActionNetworking.handleBlockUse` owns the complete decoded block-use decision/response path for networking and registered-player fixtures. No branch-specific competing admission helpers.
- ChemMaster observation validity retains identity/epoch, machine identity, range and power checks. Transient stun denies mutations with a correlated snapshot without closing read-only observation. Explicit activation (default **E**) supports occupied hands; ordinary USE remains item-insertion/empty-hand interaction.
- Existing Shift-left world-container dumping captures before examine and consumes the physical press. BEGIN/ACK/RELEASE and transfer conservation remain in the existing dump owner. The jug remains in the world; dumping is not slot insertion.
- Timed prediction uses the existing reconciler plus `TimedInteractionClockPolicy`: duplicate same-tick snapshots do not roll back render-clock progress; same-tick state corrections remain eligible. Liquid is not consumed provisionally.

## IMPORTANT FILES

Java paths are under `src/main/java/com/juicyslew/moonstation14/ms14/`:

- `ui/client/{paint/,pointer/,window/}`; `hands/client/{StoragePopupScreen,BodyHandClient,AimReleaseWorldDump}`.
- `chemistry/chemmaster/client/{ChemMasterWorkspace,ChemMasterOverlay,ChemMasterPaint}`; `chemistry/chemmaster/ChemMasterSessionService`; `doors/ui/client/AirlockWireScreen`.
- `hands/network/BodyHandActionNetworking`; `timed_interaction/client/{TimedInteractionReconciler,TimedInteractionClockPolicy}`.
- CPU renderer/generator: `src/test/java/.../ms14/ui/preview/`.
- Registered fixture: `src/gametest/java/.../gametest/support/RegisteredServerPlayerFixture`; lifecycle Ready driver and ChemMaster/wire/dump/ingestion fixtures under corresponding packages.
- `build.gradle`: single readiness catalog, existing NeoForge test-task reuse, current XML/report/manifest validation. Root `AGENTS.md` links scoped `ui.md`, `interaction.md`, `prediction.md` contracts.

## SS14 CORRESPONDENCE

Reference pin verified: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. [R0-AUDIT.md](R0-AUDIT.md) indexes resolved prototype inheritance/default/runtime mapping. [chemmaster-session-parity.md](chemmaster-session-parity.md) records BUI lifetime/action separation. Upstream explicit activation is independent of held contents; `InteractUsing` insertion and `SolutionDumpingSystem` fluid drag/drop are separate paths. Jug inherits a drainable drink solution; ChemMaster has an unlimited dumpable buffer.

No new assets/audio were imported. Retain existing attribution; do not check Minecraft resource archives into source control.

## DECISIONS / WHY

Reuse existing routing, authority, prototype, transaction and paint owners; no universal window framework or replacement network stack. Visible `Pending...` was removed, not request bookkeeping. Wire X painting and hits share `WindowChromeButtons.closeBounds`, correcting the old asymmetric region. Opt-in checks must use fresh evidence; missing manifest is UNVERIFIED, invalid/incomplete/out-of-root image paths FAIL.

## KNOWN GAPS

- Full native failures: `receiverReconcilesAlreadyLoadedMissingFootprint` and `fixtureTicketsKeepBothSidesOfSignedChunkEdgesEntityTicking`. Both pass focused baseline and current runs; baseline full run failed a different APC-light assertion. No cause inferred and no unrelated fixes made.
- Socket-free NeoForge mock-peer fixtures exercise real player registration/Ready/proof and server services, not real handshake/codec interoperability. Owner connected observation is separate evidence, not exhaustive transport validation.
- Offline text scope: selected bitmap providers and unstyled LTR; resource-pack overlays, styled/bidi text and universal GPU parity are not supported. Unsupported cases fail explicitly.
- Water crash was reported separately; owner handled test-world setup. No water fix claimed. Six root `net/` source excerpts remain because cleanup was denied; do not bypass that denial.

## VALIDATION

- Final full JVM: **3,190/3,190 passed**, 610 suites, zero failures/errors/skips, 106.86s.
- Final readiness: **112/112 passed**, 23 suites, 78.5s; 48 full previews, details/two sheets, 98 PNGs validated. Independent bounded offline review and owner representative connected comparison passed; generated artifacts are not auto-blessed goldens.
- Final focused native: **10/788 selected, 10 passed**; full native earlier: **785/787 passed**, two failures, zero incomplete. Full run preceded the final narrow corrections; focused current fixtures supplement it.
- Report negatives: missing crop/out-of-root path FAIL; absent manifest UNVERIFIED. Configuration-cache replay and fresh execution verified.
- Representative process-tree sampling: 200.92s, maximum sampled working set **1,055.22 MiB**, private bytes at that sample **1,210.37 MiB**. Actual intervals ~1.52s median/3.43s maximum; not absolute peaks or whole-host memory. Build success is from log; sampler exit field null.
- Logs/reports/previews: `build/reports/feature-readiness/`. Successful disposable worlds removed; useful failure artifacts retained, including full native world. Owner configuration unchanged.

## NEXT WORK

Use `featureReadiness -Pms14Readiness=ui,interaction,prediction,preview` selectively for future changes. Read scoped contracts and handoffs before extending owners. Investigate full-suite power/chunk failures separately if requested; do not silently classify them pre-existing. Heavy validation may run concurrently when necessary with isolated worlds and mindful resource use—no mandatory serialization.
