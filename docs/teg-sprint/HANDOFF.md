# TEG / atmosphere work — paused for ECS overhaul

## STATUS

**PAUSED by owner instruction. Sprint NOT complete or accepted.** Resume only after the codebase's SS14-style ECS refactor and explicit owner direction. Stop feature fixes, scheduler investigation, validation submissions and owner-testing handoffs. The proposed hotspot-scheduler change was **not approved and was not implemented**.

The paused work was committed as **`384c5a39`** (`Sprint 49: TEG and Pipe routing / tools`) on `agent/01`, based on `85f52120b5ad010bfcbce8ff062e2535f4592fc0`. The owner subsequently requested resolution of the incoming `main` merge at **`342eb055`**. Merge-only integration preserves main's alarm/firelock additions alongside shared PipeNet authority and nonmodal workspaces; it does not resume the sprint or authorize scheduler changes. Follow Git history/status for the resulting merge commit; unrelated incoming work is not newly accepted by this handoff.

Current normal feature run: **`run_a41a839f14eff258af724c60`, generation 2, DECIDED/FAIL, result acknowledged, cleanup CONFIRMED**. Status was reconfirmed at pause. No replacement was submitted. Owner stop supersedes the SDK's otherwise available replacement action. Preserve historical ERROR/FAIL results; do not replay old keys.

## ARCHITECTURE

**Current structure is not approved as an SS14 ECS implementation.** Typed inherited profiles and separated numerical helpers do not substitute for runtime entity/component/system composition. `AtmosMachineBlockEntity` still combines prototype/settings access, port/lifecycle ownership, power integration, processing orchestration, counters and presentation. Pipe/world services use SavedData and static per-level owners/caches. Treat these as migration inputs, not architectural precedents to perpetuate. Do not start the overhaul inside this paused sprint.

The implemented gas-authority invariant is worth preserving through that refactor: **PipeNode metadata → PipeNet membership → one authoritative `GasMixture`**. `PipeGasInventory` schema 2 owns this per dimension. Ordinary gas operations capture/update unique networks without redistributing gas to members or revising every node. Topology invalidation/remakes handle mixing and division; node shares are inspection projections only. World atmosphere remains a separate spatial owner.

Existing runtime consumers include pumps, mixer/filter, thermostats, exchangers, vents and TEG. `LoadedPipeGasWork` captures immutable network state; `PipeGasTransaction` supplies validate/raw-swap/rollback/publication boundaries. Reuse established numerical kernels and authority contracts during migration rather than copying behavior into replacement systems.

## IMPORTANT FILES

Paths below are under `src/main/java/com/juicyslew/moonstation14/ms14/` unless stated otherwise:

- `atmos/piping/`: `PipeGasInventory`, `LoadedPipeNetworks`, `LoadedPipeComponent`, `LoadedPipeGasWork`, `LoadedDevicePortGasOwner`, `TrustedPipeFixtureInitialization`, `PipeGasLedger`, `PipeGasTransaction`.
- `atmos/machine/`: `AtmosMachineBlockEntity`, typed profiles/settings and processor subpackages. `atmos/teg/` and its `fixture/` contain generation, public fixture setup and automatic operation.
- `atmos/analyzer/`, `atmos/piping/alarm/ui/`: Analyzer and nonmodal atmosphere workspaces; shared hands/UI services own admission and interaction.
- Matching `src/gametest/java/.../atmos/` fixtures; `teg/fixture/FinitePipeSpeciesAudit` and its continuous/finite-fueled callers distinguish gas revisions from node metadata versions.
- Runtime prototypes/assets: `src/main/resources/data/moonstation14/moonstation14/{atmos_machine,gas_analyzer,gas_miner,ventilation_device}/` and canonical `assets/moonstation14/` resources. Attribution: `ASSETS.md`, `ASSET_RUNTIME_CONTRACT.md`, `docs/audio-unknown-provenance.md`.
- Start with `PIPE_NET_CONTRACT.md`, `PARITY.md` and `OWNER_FEEDBACK_R30.md`. Older owner-feedback documents are historical indexes, not current readiness.

## SS14 CORRESPONDENCE

Reference pin: **`c9df5ef5d675b0d1d226828bddf6b78c28502d91`**, verified during investigation. Relevant prototype parent chains and consumers are indexed in `PARITY.md`.

- Manual valves merge networks when open and split when closed. Manifold composition connects all six cross-layer 50 L ports into **one 300 L network**, not three independent pairs.
- Remakes apportion old network gas by full receiving-network volumes. Local node-volume projections follow shared-network contents, including stored-float effects.
- Minecraft adaptations include six-face geometry, loaded-only admission, retained known membership across dormancy, affected-network blocking at unknown geometry, immutable transactions and nonempty retired-gas archives. Pipe chemistry, full anchoring/construction and exact upstream prediction are not delivered.
- Analyzer deliberately retains six named manifold entries rather than upstream's combined manifold sample. Preserve approved named own-volume entries, full same-row gas names/moles/percentages and colored bars, range zero, active-hand wheel ownership, retained sessions and opaque/glass selection. Do not restore removed tabs, coordinates, distance/instruction clutter or a constant air-volume row.

## DECISIONS / WHY

Latest source corrections include post-admission topology invalidation for newly named ports; preserving unrelated network identities during relocation; unique-network gas-revision audit counters; canonical-volume seeds; instantaneous valve/manifold expectations; exact retirement/survivor checks; and fixture admission, tick sequencing and ownership-proof ordering. **These corrections have not been rerun natively.**

Merge compatibility retains main's monitor composition and distinct child alarmable tags, the nonmodal Alarm controller/readout tab, and per-cell wide-scrubber transfer receipts. The obsolete modal screen remains removed; incoming controller coverage exercises the canonical workspace.

Preserve approved R6 assets and attribution. Continuous fixture controls/inventory are local adaptations, not SS14 defaults: 43 mol initial N₂ hot carrier, 43 mol plasma cold carrier, all-gas waste recirculation into the hot return, five radiators, eleven receivers / 1,725 W, actual paid startup and no refill. The finite demonstration retains its separate two-N₂-loop setup. Exhaust collection is not necessarily exterior export.

Keep **2 J / 1,000 J / 1,200 ticks**, qualification/warmup deadlines, actual electrical receipts and resolved SS14 conversion coefficients. Modeled gas thermal sink and electrical generation are distinct. Schema 2 requires a fresh disposable fixture world; deleting only gas SavedData is not a supported reset.

## KNOWN GAPS

- The last native run had **23 required failures**. Candidate fixes address many causes, but none may be called natively resolved without a fresh complete gate.
- Foundation chamber chemical-excess/positive-exchange proof remains unexplained; planetary comparison expired its original 1,000-tick warmup with 769.64 W input versus 1,725 W output. No coefficients, loads or limits were weakened.
- Incoming main's vent pressure-lockout/override behavior is retained alongside the profile pressure checks; see `vent-pressure-parity.md`. Low-pressure fixture operation needs fresh qualification on resume. The protected operating plan and approved TEG model/texture resources were not retuned by the merge.
- A hotspot pass waits for background chunk-scan completion locally, unlike the inspected upstream hotspot stage. This may delay later cycles, but retained logs lack scheduler counters proving it caused either failure. LINDA also performs chemistry; do not claim chemistry runs only in HOTSPOTS. **Owner chose to leave this alone.**
- The earlier hour-long hang's cause remains unproven. GPU/audio/input review, owner acceptance, current full regression and ECS architectural acceptance remain outstanding.

## VALIDATION

- **Main-merge resolution:** all three compile tasks passed; **20 focused JVM suites / 118 tests**, zero failures/errors/skips. Thirteen focused Python workflow tests passed with mocked launches. JSON duplicate-key/merge-union checks and source reviews passed. These are merge checks, not native, GPU, package, full-suite or feature-readiness acceptance.
- **Paused implementation checkpoint, before the main merge:** all three compile tasks passed; six focused JVM classes / **30 tests**, zero failures/errors/skips; source stability checked. Protected power, operating-plan and atmosphere hashes were unchanged during those corrections/investigation. Incoming main changes require fresh verification; old guard hashes/passes are not proof for merged bytes.
- **Earlier R29 bytes only:** 175 classes / 1,013 JVM tests passed; not a current complete-floor pass.
- **Last normal native:** 248 completed, 23 required failures; no timeout in generation 2. Completion does not identify the earlier hang.
- Preserve coordinator evidence, including native log artifact `artifact_task_native_retry_52e96514c2e73aeaa2c7eb8f_log_ea3a2a33f15e018e1c87`. Local scratch/evidence indexes are under `build/agent-workflow/teg-sprint/`; they are not substitutes for immutable receipts.

## NEXT WORK

1. Consolidate the merged branch with the overhaul owner; preserve unrelated work and failure history.
2. Rework runtime ownership/composition into the agreed ECS architecture before resuming feature acceptance. Preserve tested behavior, not the current monolithic arrangement.
3. On explicit resume, verify current code/evidence and re-establish the complete retained floor: 13 criteria, original 42 native tags, 175-class JVM floor, applicable four readiness categories and package/provenance coverage. Do not reuse old-source passes.
4. Only after a complete normal technical PASS offer `MANUAL.md`; owner acceptance and post-acceptance/pre-merge full GameTests remain separate.
