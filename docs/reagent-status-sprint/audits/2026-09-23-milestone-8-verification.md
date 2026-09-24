# Milestone 8 verification and acceptance record

**Date:** 2026-09-23
**Outcome:** The documented bounded implementation and its recorded checks
passed; Milestone 8's original Definition of Done is **not fully met**. In
particular, `Instructions.md` requires supported reactive effects to be
reachable through gameplay exposure, while puddle exposure is intentionally
disabled until real slip mechanics exist. Passing tests and builds therefore
do not constitute unqualified M8 acceptance or project-wide completion. This
record was updated after the initial bounded M8 work when reported runtime
issues required correction and follow-up validation.

## Acceptance checks recorded

- Every one of the 34 `EffectData` variants has exactly one classification in
  the [final effect matrix](2026-09-23-milestone-8-final-effect-matrix.md): 11
  supported within a local boundary, 9 partial/compatibility-limited, and 14
  explicitly unsupported. The exhaustive support test independently asserts
  20 handler registrations plus 14 explicit unsupported registrations.
- Prior [Milestone 7 status](2026-09-23-milestone-7-deferred-effects.md)
  remains intact: bounded unsupported policy and owner-supported slices are
  complete, while the 14 explicitly unsupported effects are still deferred.
- The jug loot-table parse error was fixed; runtime server recipe/loot loading
  completed without that error. Ingestion callback GameTests use an eligible
  `Player` test double, and registered puddle GameTests exercise the public
  `BlockState` dispatch boundary.
- Resource semantic validation covered 411 raw / 406 resolved reagents, 19
  status prototypes, and 5 alerts. Python syntax parsing covered all 602
  resource JSON files.
- Static JUnit tests cover the exhaustive support matrix, and the dedicated
  GameTest server ran successfully. Together, the static JUnit scan and
  successful server run provide server-side classloading evidence; no GameTest
  exclusively checks classloading. The isolated
  `build/gametest-run/logs/latest.log` from the latest post-fix run records
  **83 required tests passed**. This includes the Peaceful `Player.aiStep`
  regression coverage; its eligible Player test double verifies the server-side
  method path, not a connected-client interaction. It also contains the expected
  test-generated missing-status error; that diagnostic did not fail the
  required suite. This run used
  `runGameTestServer -Pms14GameTestDir=build/gametest-run` to isolate the
  GameTest output.
- Coordinator reports `test --rerun-tasks --no-daemon` and `build` successful
  after the fixes.
  Manual command strings are parser-checked by `ManualCommandGameTests`, which
  does not execute them.

## Residual risks / not accepted as full fidelity

The 14 unsupported effects remain unsupported by design; their existence is
not the outstanding M8 acceptance blocker. Supported/partial effects retain
the per-row boundaries in the matrix, including typed-damage mitigation
allocation, status presentation, and fire simulation limits.
Stomach/thirst eligibility still uses temporary player-and-villager opt-in
rather than character-prototype ownership; stomach routing is not full
physiology, and stomach `AdjustReagent` remains skipped. The prior M6 direct
puddle-contact gameplay claim is superseded: contact consumption/effects are
now intentionally deferred until real slip mechanics exist. The 34-effect
handler matrix remains 20 functional/partial registrations plus 14 explicit
unsupported registrations; serializable reactive definitions are not reachable
from puddle gameplay. This means the supported-reactive-effects gameplay
exposure requirement in the original Definition of Done is still open.

Dynamic runtime reload behavior, death/clone transport guarantees beyond
documented defaults, and 20+ player multiplayer behavior remain residual
integration risks. Isolated GameTests do not simulate the full client/network
interaction path, including mixed client/server version wire compatibility or
physical interaction targeting. Client tint/render appearance is unverified.
There is no automatic puddle wash/cleanup system. Two-holder provider commits
are sequential rather than exception-atomic, and recipe/metabolite transformations
do not carry a global cent-conservation guarantee. No manual client session was
performed for this record;
see the [manual checklist](../manual-test-checklist.md) for optional human
smoke tests. The [dependency ledger](../deferred-effect-dependency-ledger.md)
records slip mechanics as the prerequisite for revisiting contact exposure;
the [Milestone 7 audit](2026-09-23-milestone-7-deferred-effects.md) retains
the other deferred owners.
