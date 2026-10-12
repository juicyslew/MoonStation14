# Readiness timeout and persistent rejoin rejection

## Scope

Separate access-blocking bug reported during ChemMaster client testing. The owner
deleted the affected world before diagnosis. Do not delete player/profile/body
data as a recovery strategy. Existing lifecycle experiment closure does not
authorize unrelated lifecycle work; this follow-up addresses timeout retryability.

## Established defect

`LifecycleCharacterSessionControl.serverTick` routes an uncommitted readiness
timeout to `fail(READY_TIMEOUT)`. That removes the live session, clears movement
ownership, and calls `PlayerLifecycleRegistry.suspendActiveSessionForRecovery`.
The in-memory claim becomes recovery-required while the durable row remains
`ACTIVE`. `LifecycleFirstJoinHandler` accepts `OFFLINE` for living reconnect, not
`ACTIVE`. The later logout callback cannot park the character because the session
has already been removed. This is a confirmed timeout-to-lockout path.

The reported literal `BEGIN_TIMEOUT` is not present in current source, and the
original logs/save are unavailable. Matching that particular message to this
path remains unverified.

## Required correction

For an uncommitted technical readiness timeout, prove the exact current session,
connection, live body, binding and authority; reuse `cleanLogout` and
`LifecycleServerContext.disconnectCleanly` to persist `OFFLINE` before retiring
the session. Reconnect must use the same body/Mind and a fresh epoch. No automatic
replacement bodies, profile edits, migration framework or operator recovery UI.

Failed ownership/body proof or persistence must remain fail-closed. A cleanly
parked timeout should tell the player to reconnect, not demand explicit recovery.
Network/loading timeouts can still occur; they must not poison a valid character
claim. Merely increasing the deadline is not the fix.

## Validation requirements

Deterministic timeout followed by same-player/same-body reconnect; repeated
timeouts; durable reload/restart; retained contents and identity; late Ready and
stale connection/epoch rejection; and failed body/CAS proof remaining protected.
Record automated evidence separately from connected-client acceptance.

## Starting points

- `ms14/player_body_control/lifecycle/server/LifecycleCharacterSessionControl.java`
- `LifecycleFirstJoinHandler`, `LifecycleExistingBodyReconnect`
- `LifecycleServerContext.disconnectCleanly`, `PlayerLifecycleRegistry`
- `docs/player-character-lifecycle-sprint/current-handoff.md`
- `docs/player-character-lifecycle-sprint/audits/2026-09-28-initial-connected-ready-timeout.md`
