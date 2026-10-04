# Loose world objects proposal

## STATUS

Reconstructed 2026-10-03. **Planning only; no implementation authorized.** Current hands/storage functionality is delivered separately; this proposal does not supersede its owner acceptance or authorize new UX/performance work.

## ARCHITECTURE

Proposed sequence: presentation/lifecycle, exact pile selection, collision, pulling, then scale evaluation. Baseline is mod-owned loose objects represented as entities, with whole-stack entity↔LIVE_HANDS transfers. Existing body-eye probing chooses a first hit, not any nominated member of an overlapping pile.

## IMPORTANT FILES

- `Instructions.md`: scope, measurement contract, M0–M5 gates.
- `../hands-inventory-program/HANDOFF.md`: current accepted hands/storage baseline; older `current-handoff.md` is historical where superseded.
- `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/{BodyHandItemTransfer,BodyItemTargetProbe}.java`.
- `ms14/player_body_control/character/GroundedHarnessWorldStep.java` below the same Java root.

## SS14 CORRESPONDENCE

Conceptual references: `base_item.yml`, upstream `PullingSystem.cs`, physics islands/sleeping, and PVS. They do not establish Minecraft distance-joint support, a local virtual pull-hand item, zero entity-tick cost, or server simulation culling.

## DECISIONS / WHY

Do not replace vanilla rendering/pickup globally. Keep entity ownership stable during resting/pulling; consider records/hybrids only after measured evidence and owner choice. Desired click selection is separate from existing actions. Transfer compensation is not crash-atomicity.

## KNOWN GAPS

No implemented pull-hand reservation or proposed arbitrary-pile picker. Loaded-only getters alone do not prove arbitrary collision callbacks cannot load chunks. Proposed 20-player typical/100-player worst-case workloads and thousands of objects are goals, not measured capacity.

## VALIDATION

Plan contains architecture evidence and benchmark requirements, not proof of proposed gameplay or scale. No implementation tests or benchmarks performed for this proposal/reconstruction.

## NEXT WORK

Obtain authorization for M0: exact upstream composition audit, actor/interaction contract, safe probe audit, reproducible baseline, and owner-approved measurement budgets. Reconcile historical keybind descriptions with current hands controls before design.
