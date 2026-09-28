# OWNER connected lifecycle smoke

**Partial connected observations exist; full smoke not run and no acceptance claimed.** This checklist is for an OWNER
on a disposable world only. Use a matching rebuilt client and server. Do not use a production world or enable the feature
on production. Ghost movement after disconnect/rejoin failed twice with the dead claim retained; testing was suspended.
See [current handoff](current-handoff.md) and [sprint closure](closure.md). Leave every unchecked item unchecked.

The initial Ready-timeout attempt and later Creative-mode kick are documented in the
[initial incident audit](audits/2026-09-28-initial-connected-ready-timeout.md) and [Creative carrier-escape audit](audits/2026-09-28-creative-carrier-escape.md).
Future lobby architecture is described in the [future lobby handoff](future-lobby-handoff.md); it is not part of this
smoke or current implementation.

## Prepare

1. Use a disposable test world. Stop the server before deleting/recreating it between tests. No backup preservation is
   required; never edit `moonstation14-lifecycle/profiles.json` or other profile data by hand.
2. Edit **only** `config/moonstation14-common.toml` in the installation that will actually run the server. Set
   `experimentalMindGhostControl=true` and keep `experimentalVerticalSliceMovement=false` throughout the smoke. When
   using a development run, that active installation may be the repository's gitignored `run/config`; do not mistake a
   repository-local config for a committed template, and do not edit someone else's installation. The lifecycle gate is
   default-off and startup-sampled: normal behavior remains unchanged unless explicitly enabled for this smoke, and
   config edits do not hot-toggle it. The owner/operator debug command is not required and must not be called for this
   checklist.
3. Start the matching rebuilt server and client after the edit. Record build/source revision, world copy, client/server
   versions, and startup log. Keep a second client/account available as a remote observer. Record each result, stable
   body UUID and Mind identity where visible, and relevant log excerpts. Read
   `moonstation14-lifecycle/profiles.json` only read-only and after shutdown if needed to verify persisted profile
   state/generation; never edit it. Keep `experimentalMindGhostControl` default-off outside this disposable test and
   restart after changing it; retain `experimentalVerticalSliceMovement=false`.

## Smoke sequence

- [ ] Fresh account first join: exactly one custom player character is directly possessed through the complete
  Begin/Ready/Commit flow. Confirm no transient ghost/camera-only state and no failed or incomplete handshake.
- [ ] Exercise move, jump and look; test slip if available. Confirm normal grounded custom-character movement (the
  custom type is explicitly WorldStep-eligible, not a host-entity lease addition).
- [ ] Temporary development Creative escape (disposable world only; `/gamemode creative` was observed working, but this
  full checklist item remains unaccepted): while actively possessing the character as an operator, run
  `/gamemode creative` once. Confirm there
  is no recovery kick; vanilla Creative proceeds only after the exact body is durably parked OFFLINE at its current
  location/appearance with an advanced generation, and the character session stops with the carrier camera restored.
  Do not force a persistence/CAS failure or manipulate the profile to test failure handling. If a park error is naturally
  reported, confirm the mode change is canceled and possession/session remain intact.
- [ ] While parked in Creative, verify Survival and Adventure requests are refused and do not reclaim/control the
  character. Run `/ms14dev return` in the same operator connection. Confirm it reclaims the same already-loaded body and
  Mind, has no transient ghost, and completes Begin/Ready/Commit. If return cannot proceed, confirm the operator stays in
  Creative and receives retry/logout-rejoin guidance; do not move, remove, or edit the body/profile to manufacture this
  state. A normal logout while parked should leave the profile OFFLINE; after relogin, verify ordinary gated reconnect
  returns the same body. This is a bounded developer exception, not production gameplay or a lobby return path.
- [ ] On the owner account run `/ms14char skin default`, `/ms14char skin alex`, `/ms14char shape wide`, and
  `/ms14char shape slim`. Have the second client observe each change; verify synchronized texture and geometry and
  that a remote observer does not retain a stale appearance.
- [ ] Log out cleanly. Verify the same body remains in-world, NoAI, and subject to normal world damage/physics. Leave it
  offline at least 60 seconds and verify the synchronized `Offline` nameplate from the observer.
- [ ] Reconnect the same account. Verify the **same body UUID** and stable Mind, with a new connection generation;
  there is no replacement body. If needed, after shutdown inspect the saved profile read-only or use server logs.
- [ ] Log out, stop the server cleanly, restart the same installation/world, then reconnect. Verify the same body UUID
  and stable Mind again. This is ordinary healthy restart proof, not crash-recovery proof.
- [ ] While that account is active, attempt a second simultaneous login for the same account. Confirm rejection and no
  duplicate body, Mind, or session; the active owner remains valid.
- [ ] Cause confirmed death by **actual damage** to the connected character (not `/kill`, command removal, or other
  entity removal). Verify durable corpse retention and a fresh ghost controlling the same Mind; verify the old body
  cannot control movement and no character/ghost sessions overlap.
- [ ] For a separate offline-death test, use a different test account or create another fresh disposable world, then log
  out cleanly, apply actual damage to the offline body, and reconnect. The MVP supports one profile
  per account: do not expect or grant a new profile for the same account. Verify durable `DEAD_CLAIM` handling and a
  fresh ghost. If the corpse chunk is unloaded, the ghost may spawn at the already loaded login carrier location; this
  is allowed and must not force-load/search for the corpse.

## Abort and evidence

Abort immediately on camera-only possession; failed/incomplete Begin, Ready, or Commit; duplicate character/body/Mind;
owner or account mismatch; unexpected camera/authority transfer; save/store errors; or any ambiguity in body ownership.
Do not retry by editing saved data/profile files, forcing entity removal, or improvising recovery. Stop the server before
deleting/recreating the disposable world; record useful log evidence if available. Do not claim the affected stage passed.

Record PASS/FAIL/NOT RUN for every item, the exact observed UUID/generation and observer results, and relevant log
evidence. A failed or partial smoke is not acceptance. Healthy ordinary restart evidence does not resolve future
crash-time world/profile save disagreement. A real server-owned lobby, including an explicit readiness/character-selection
flow, remains future work; Creative escape is only a temporary operator development path.
