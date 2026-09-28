# Creative carrier-escape connected handoff and audit — 2026-09-28

## Disposition

The owner's first join and control were reported working: startup reached Ready/Commit and the player controlled the
character. The subsequent Creative-mode escape failed and the rejoin refusal is **not** a successful lifecycle test.
The operator Creative escape/return implementation described below has not yet been exercised by the owner in a connected
session. M0/M1 remain the only accepted stages; this report does not accept M2–M7. Use the [connected acceptance
checklist](../connected-acceptance-checklist.md) for the next disposable-world test.

## Owner-reported incident

The owner reported the following from the disposable run:

| Time | Observation |
| --- | --- |
| 07:59:14 | Server Begin. |
| 07:59:15 | Server Ready/Commit. First join and control worked. |
| 07:59:55 | Log: `[Dev: Set own game mode to Creative Mode]`. |
| About 90 ms later | Owner was kicked with `Character session requires explicit recovery`. |
| On rejoin | Rejected; saved `run/world/moonstation14-lifecycle/profiles.json` showed `ACTIVE`. |

That ACTIVE profile explains why ordinary rejoin refused: the persisted claim still described an owned active session
instead of an OFFLINE body eligible for same-body reconnect. The records were not manually changed. This is an incident
observation, not evidence of data corruption or a successful recovery path.

## Implemented development escape

The operator-only `PlayerChangeGameModeEvent` pre-change path now handles a request for Creative while the operator owns
a lifecycle CHARACTER session. Before vanilla Creative is allowed, the server durably parks the same body as OFFLINE,
preserving its current location and appearance and advancing the profile generation. It then retires the character
session, sends Stop, and restores the carrier camera. Successful parking should not kick. A persistence/CAS failure
cancels the requested mode change without losing the session. This is deliberately bounded to operators and is a
development escape, not a production mode-selection policy.

While parked, Survival and Adventure requests are refused. `/ms14dev return` on the same operator connection attempts to
reclaim the same already-loaded body and Mind. If it cannot safely do so, the owner stays in Creative and receives retry
or logout/rejoin guidance; it does not activate a replacement body. Logging out while parked leaves the profile OFFLINE,
so ordinary gated same-body reconnect is the separate fallback. None of these connected behaviors has yet been confirmed
by the owner after this implementation.

## Safe next connected test

1. Use a disposable world and matching rebuilt client/server. Set `experimentalMindGhostControl=true` only in the active
   installation's `config/moonstation14-common.toml`, retain `experimentalVerticalSliceMovement=false`, and restart; the
   master flag is default-off and startup-sampled. Do not enable it on production.
2. Verify first join completes Begin/Ready/Commit and the owner controls the character. Record the body UUID and relevant
   log evidence.
3. As an operator actively possessing that body, issue `/gamemode creative` once. Verify no kick; verify the same body
   remains in-world and parked OFFLINE with its current position/appearance and an advanced generation; verify
   possession stops and the carrier camera is restored before Creative is entered.
4. While parked, verify Survival/Adventure are refused. Run `/ms14dev return` in the same connection and verify the same
   already-loaded body and Mind are reclaimed with a completed Begin/Ready/Commit. If it cannot return, remain in
   Creative and follow the reported retry/logout-rejoin guidance. Do not force a missing-body case or alter any saved
   profile data.
5. Optionally test normal logout from parked Creative and a later clean rejoin of the same body. Stop the server before
   deleting/recreating the disposable world. Profile files, if inspected for evidence, are read-only and only after
   shutdown; they must never be edited.

Abort on a kick, incomplete handshake, wrong body/Mind, unexpected camera/authority change, or save/store ambiguity. Record
PASS/FAIL/NOT RUN per step. A failed test does not require preserving a disposable world or backup: stop the server before
deleting it, retain useful log evidence if available, and recreate a clean disposable world for another attempt. This
updates the earlier initial Ready-timeout audit's world-preservation recommendation for the current disposable-test
workflow; do not edit profile files or reuse the failed profile as a recovery technique.

## Validation boundary and future direction

After the development escape change, `gradlew.bat test --rerun-tasks --no-daemon` and
`gradlew.bat build --no-daemon` passed. The isolated
`runGameTestServer -Pms14GameTestDir=build/gametest-lifecycle-creative-stopped --no-build-cache --no-daemon` ran 159
tests and had exactly one required unrelated atmos failure: `skyexposedcellusesambientandrejectsinjection`. No lifecycle
fixture was listed as failing; this is **not** a full GameTest pass. In its first attempt, a running client/server
prevented deletion of compiled classes; a forced compile with `--rerun-tasks --no-build-cache` succeeded once both were
stopped, and the second GameTest run completed. That contention is not evidence of a lifecycle feature bug.

These automated checks do not test the real client/server Creative request, kick prevention, same-connection return, or
profile state across owner relog. Crash/save automatic repair remains deferred; do not edit profiles or infer crash
recovery from this test. The current command is a temporary operator escape. A real server-owned lobby with explicit
readiness and character-selection flow remains future work, as outlined in the [future lobby handoff](../future-lobby-handoff.md).
