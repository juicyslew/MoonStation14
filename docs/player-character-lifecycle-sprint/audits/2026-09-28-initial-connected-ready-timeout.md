# Initial connected Ready-timeout incident — 2026-09-28

## Disposition

The first connected owner attempt failed and is **not acceptance**. The affected world is evidence and must be preserved
unchanged. Do not edit or delete its lifecycle records, retry in that world, or use an operator recovery command (none
exists). Any future retest must use a **new backed-up disposable world** or a restored pretest backup in a separate
disposable run.

## Observed timeline

The owner inspected `run/logs/latest.log`:

| Time | Observation |
| --- | --- |
| 07:26:04 | Startup reported `UNINITIALIZED`. |
| 07:26:26 | Login became visible as Steve. |
| 07:26:31 | `Character session requires explicit recovery` after approximately the 100-tick Ready timeout. |
| 07:26:56 | Rejoin was rejected. |

After shutdown, `run/world/moonstation14-lifecycle/profiles.json` was inspected read-only. The same body and Mind were
still recorded as `ACTIVE`. The failed world's records were not edited or deleted. The log did not contain detailed
Begin/Ready evidence, so the incident's precise handshake sequence cannot be reconstructed from that log alone.

## Deterministic source cause and narrow fix

Source inspection identified that `GhostControlClient.currentBody` / `characterPolicy` recognized a `CHARACTER` body
only through `host_entity_types`, while the custom character type is deliberately not mapped there. Consequently the
client could not send Ready for the custom body. There was also an early-Begin race when the player or level had not yet
been bound on the client.

The narrow fix explicitly recognizes the custom character type for lifecycle readiness without adding it to
`host_entity_types`; retains a deferred Begin across transient client binding state and clears it on explicit logout; and
delays server Begin until Post tick, with diagnostics. Begin's timeout therefore starts at the actual send. This is a
source-level explanation and fix, not evidence that an owner-connected session now succeeds.

## Validation boundary and retest safety

- Full `.\gradlew.bat test --rerun-tasks --no-daemon` and build passed after the fix.
- The isolated `runGameTestServer -Pms14GameTestDir=build/gametest-lifecycle-ready-fix --no-daemon` run reported 158
  GameTests, all required tests passed.
- **No owner-connected retest has been run.** There is no acceptance claim; Begin/Ready/Commit, movement and the
  end-to-end client handshake still need connected owner evidence using the [acceptance checklist](../connected-acceptance-checklist.md).

Do not overwrite, edit, or retry against the failed claim in `run/world`. Preserve it for diagnosis. A safe retest uses
only a new backed-up disposable world or a restored pretest backup in a separate run. No operator recovery command is
available. Crash/save disagreement and automatic repair remain deferred; see
[deferred crash/save disagreement](../deferred-crash-save-disagreement.md).
