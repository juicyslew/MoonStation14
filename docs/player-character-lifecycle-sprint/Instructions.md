# Player Character Lifecycle Sprint — instructions

This is the sprint index. See the [proposal](proposal.md) for the behavioral contract and the [current handoff](current-handoff.md)
for implementation and validation status. **M0/M1 are accepted; M2–M7 are not accepted until connected owner evidence
is recorded.** This sprint is closed as a handoff, not as acceptance. Never describe planned or automated behavior as a
passed manual test.

## Documents

- [Proposal](proposal.md) — goals, invariants and stage gates.
- [M0 contract](m0-contract.md) and [M1 status](m1-status.md) — accepted investigation and pure model.
- [M2 progress](m2-progress.md) — durable store and startup foundations.
- [M3 foundation](m3-foundation.md), [M4 handoff](m4-handoff.md), [M5 foundation](m5-dormant-foundation.md),
  [M6 foundation](m6-death-foundation.md) — implementation boundaries, not stage acceptance.
- [Connected acceptance checklist](connected-acceptance-checklist.md) — OWNER-only disposable-world smoke and abort criteria.
- [Current handoff](current-handoff.md) — latest integrated lifecycle and exact automated evidence.
- [Sprint closure](closure.md) — closed-as-handoff status, partial connected observations, and next-sprint boundary.
- [Creative carrier-escape audit](audits/2026-09-28-creative-carrier-escape.md) — owner-reported Creative kick,
  implemented development escape, and safe connected retest boundary.
- [Deferred crash/save disagreement](deferred-crash-save-disagreement.md) — future fail-closed recovery work.

## Safety and validation

Use only a disposable world and matching rebuilt client/server. Stop the server before deleting/recreating a test world;
never edit lifecycle profile files. In the active installation's
`config/moonstation14-common.toml` in the installation actually used by the server, set
`experimentalMindGhostControl=true` and `experimentalVerticalSliceMovement=false`, then restart. In a development setup,
the active installation may be the gitignored `run/config`; do not edit a repository template or enable this on
production. The master flag remains default-off; when disabled, lifecycle behavior is vanilla.

Focused lifecycle tests and the latest `gradlew.bat build --no-daemon` passed after ghost tolerance/messaging changes.
The earlier isolated lifecycle-creative GameTest run had 159 tests with one unrelated atmos failure; this was not a full
GameTest pass. Connected observations are partial: first join/Ready/Commit and body control worked, `/gamemode creative`
worked, but moving after death ended the ghost session twice while retaining `DEAD_CLAIM`. No connected retest followed
the latest ghost changes; M2–M7 remain unaccepted. See the [current handoff](current-handoff.md), [sprint closure](closure.md),
and [Creative carrier-escape audit](audits/2026-09-28-creative-carrier-escape.md). Crash/save disagreement and automatic
repair remain deferred.

This handoff is documentation-only. Do not edit Java, config, power, or atmosphere files, and do not run tests as part
of this task.
