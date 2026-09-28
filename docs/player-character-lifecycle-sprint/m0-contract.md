# M0 — lifecycle contract and verified local source map

**Status:** M0 documentation deliverable, 2026-09-27. This records source observations and authorized planning defaults; it does not implement lifecycle behavior or establish stage acceptance. Source/config were read only. All lifecycle runtime effects remain subject to the [shared master-gate contract](proposal.md#runtime-opt-in-boundary-applies-to-every-new-effect).

## Verified local hooks and boundaries

| Concern | Verified source and observation | M1+ implication |
| --- | --- | --- |
| Authenticated login hook | [`ModEventHooks`](../../src/main/java/com/juicyslew/moonstation14/eventhooks/ModEventHooks.java) currently registers entity join/leave, player clone, and logout listeners. There is no login listener there. The lifecycle entry seam must be NeoForge's authenticated `PlayerEvent.PlayerLoggedInEvent` (or equivalent authenticated login lifecycle event), not `EntityJoinLevelEvent`. | Join-level entity callbacks also run for ordinary spawned/loaded entities and provide no authenticated account-claim boundary. Use login identity for profile lookup/claim; keep body entity load/reconciliation separate. Hook and thread assumptions need focused M1 tests. |
| Entity join / clone | `ModEventHooks.onEntityJoinLevel` reconciles unrelated status/activity and character identity enrollment for server living entities. Clone policy copies a character identity key; neither is account/Mind session ownership. Logout currently only notifies movement disconnection. | Do not retrofit these generic hooks as an implicit login/claim path. Clone/death signals require lifecycle policy and actual-death validation. |
| Mind and harness registry | [`BodyControlRegistry`](../../src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/BodyControlRegistry.java) is a synchronized in-memory ownership model. `createMind` only accepts a registered, eligible `GHOST` and creates a random Mind attached to it. `logout` destructively deletes Mind/session/ownership. Generations currently represent registry operation epochs, not durable login generations. | It cannot yet create a Mind on a character body or model durable account profile, dormant disconnect, stable Mind reconnect, or authenticated generation claim. Do not funnel initial join through a temporary ghost or the debug command. M1 needs a distinct pure lifecycle transition model/API. |
| Existing debug path and master gate | [`Config`](../../src/main/java/com/juicyslew/moonstation14/Config.java) defines COMMON `EXPERIMENTAL_MIND_GHOST_CONTROL` as `experimentalMindGhostControl`, default `false`. [`MindGhostStartupGate`](../../src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/server/MindGhostStartupGate.java) latches startup config and clears on stop. [`GhostMobHarnessControl`](../../src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/server/GhostMobHarnessControl.java) checks that gate and independently rejects start when `MovementStartupGate` (`experimentalVerticalSliceMovement`) is enabled; its start also changes the operator carrier to spectator. | Existing `/ms14 mindghost` remains op-only and unchanged. New automatic join path is separate, permissionless only for an eligible authenticated first join when the shared master gate is enabled; no automatic path may call debug start or change mode via it. The legacy conflict remains independent. M4 must update the config comment currently describing the flag as operator-only so it also describes gated lifecycle behavior. |
| Ghost persistence and presentation | [`GhostMobHarnessRegistration`](../../src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/ghost/GhostMobHarnessRegistration.java) registers a `.noSave()` ghost entity. [`GhostMobHarnessEntity`](../../src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/ghost/GhostMobHarnessEntity.java) is a debug ghost with no AI/gravity and `noPhysics`. [`GhostMobHarnessRenderer`](../../src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/ghost/client/GhostMobHarnessRenderer.java) renders a vanilla slime model/texture. | This is not a persistent character body, suitable character appearance renderer, or lifecycle presentation. A dedicated custom character entity/prototype, per-instance identity binder, renderer/model and synchronized visible appearance need design and tests; model/texture availability is not assumed. |
| Existing data synchronization | [`MobMindHarnessOwnerMixin`](../../src/main/java/com/juicyslew/moonstation14/mixin/MobMindHarnessOwnerMixin.java) declares a synced movement-owned boolean. The debug ghost renderer exposes no appearance sync/render policy. | No existing custom character appearance/indicator sync mechanism was found in this source map. M3 must define and test authoritative server validation, persisted options, entity data or payload synchronization, and observer-visible rendering. |
| Lease and logout hazard | [`GroundedHarnessLease`](../../src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/GroundedHarnessLease.java) captures `priorNoAi`; `close()` clears movement ownership and restores that prior AI state. | M5 must not close this lease on logout in a way that wakes AI. Design a durable offline-owned lease/stop gate that blocks voluntary input and ordinary AI while leaving normal physics/status/damage active; reconnect resumes only after generation authorization. |
| Persistence / entity lookup | No account/profile/Mind lifecycle persistence, profile reconciliation, SavedData implementation, or body UUID recovery store was found in the source inspected. The registry is process-memory-only. Minecraft entity UUID resolution is therefore a future implementation seam, not currently a verified lifecycle API. | M2 must choose a versioned server-owned durable record and world-save coordination. Lookup must distinguish present, known-but-unloaded, missing, duplicate/ambiguous, and invalid entities. Boundedly resolve a known body; never create a replacement or silently fall back to a ghost on lookup failure. |
| Server startup / stop | [`MoonStation14`](../../src/main/java/com/juicyslew/moonstation14/MoonStation14.java) samples the independent movement gate at `ServerStartingEvent`; `GhostMobHarnessControl` samples the mind-ghost config at server start and resets at `ServerStoppedEvent`. | Extend/reuse only `MindGhostStartupGate` for the master opt-in. No new config switch and no hot toggle. All new runtime side effects must check the same gate, including async/network handlers and recovery. |

## Contract decisions and record sketch

Accepted defaults for planning (not previously specified details):

- Persist the selected character's body UUID and location across server restart. A missing, unloaded-but-unresolvable, duplicate, or ambiguous body fails closed with actionable server diagnostics; never spawn a duplicate as fallback.
- Reject a second simultaneous login claim while the recorded connection generation remains valid. Revoke/expire explicitly before retry. Use a monotonically increasing authenticated connection generation and reject stale input/transitions.
- Appearance is restricted to server-validated visible options, applies live to the current body, and persists with the profile. Final option set awaits M3 asset/source proof; do not claim current renderer supports it.
- Provisional indicator default: configurable 60-second offline grace, then a small synced visual badge on the body. This is proposed local policy, not a user-specified duration/design or a universal SS14 behavior.
- Keep the same stable player Mind attached to a living offline body, dormant and disconnected; no AI takeover. Actual offline death becomes a death claim and reconnect transfers that same Mind to a fresh ghost. No automatic respawn/body grant.

Versioned record sketch (schema proposal, not an implementation/API commitment):

```text
LifecycleRecord {
  schemaVersion: integer
  accountUuid: UUID
  profileId: stable profile key
  stableMindId: UUID
  character: { prototypeId, appearance: validated option IDs }
  body: { entityUuid, dimensionId, lastKnownPosition }
  state: FIRST_JOIN | ACTIVE | OFFLINE | DEAD_CLAIM | GHOST | RECOVERY_REQUIRED
  connection: { generation: unsigned integer, active: boolean }
  offlineSince: optional server timestamp/tick policy value
  revision: monotonically increasing record revision
}
```

Persistence must use an atomic/versioned write strategy with backup or preserved last-known-good record. Account/profile uniqueness, body ownership uniqueness, and record/entity agreement are invariants. Exact storage location, migration policy, backup retention, and durable generation encoding remain M2 design/acceptance work. Never repair ambiguity by guessing ownership.

## Fail-closed transition and recovery table

| Trigger / condition | Allowed transition | Failure / recovery behavior |
| --- | --- | --- |
| Gate off; any login/recovery/entity event | No new lifecycle effect. Existing independent baseline behavior only. | No auto claim, persistence/reconciliation, character spawn, or mode change. No cached decision may bypass startup gate. |
| Gate on; authenticated first eligible login; no existing record | Reserve profile and generation, create custom character body, register eligible CHARACTER Harness, bind profile, create stable Mind already attached, then commit acknowledged handoff. | Any failed authorization, persistence, spawn, registration, or handoff rolls back partial claim/body/session; no active unattached Mind, ghost carrier, or mode fallback. Explicit retry/error. |
| Existing valid generation; concurrent login | Reject new claim; existing owner unchanged. | Tell claimant retry is required after old generation revocation. Never duplicate-spawn or implicitly steal. |
| Reconnect; body UUID resolves uniquely and is alive | Reattach new authorized generation to the same body and same stable Mind. | Stale generation rejected. Do not replace body or Mind. |
| Body is known but chunk/entity is unloaded | Boundedly resolve/load only the recorded body under server-owned policy. | If bounded lookup cannot resolve uniquely, keep claim reserved and fail closed with actionable operator recovery. Never spawn a duplicate or ghost fallback. |
| Body missing, duplicate UUID/record, owner mismatch, corrupt/unsupported schema, or ambiguous save | `RECOVERY_REQUIRED`; preserve evidence and prior record. | No ownership guess, claim transfer, spawn, or reconciliation mutation. Diagnostic/backup/operator recovery; migration rejects unknown versions. |
| Logout while living | Same stable Mind/body binding and profile claim retained; session detached, new generation invalidated; body dormant. | Do not call lease close if it restores prior AI. Stop player input and ordinary AI only; physics/status/damage continue. Badge only after grace. |
| Reconnect races with stale packet/old generation | Only current authorized generation may control. | Reject packet/transition and leave current ownership unchanged; audit stale generation. |
| Actual death while connected or offline | Record death claim; retain corpse; transfer same stable Mind to fresh ghost on connected death or eligible reconnect. | Voluntary alive ghosting is not death eligibility. No corpse repossession, automatic new living body, role grant, or implicit respawn. Failure stays claimed/recovery-required. |
| Persist/update/commit failure at any boundary | Do not publish partial active state. | Roll back staged entity and claim when safe; otherwise preserve recoverable intent and mark recovery required. Never report committed success with uncertain durable state. |

## M1 acceptance criteria and test seams

M1 may begin only after this M0 record is reviewed against owner-approved scope. It is a pure model/test milestone (no world spawn/persistence integration): stable Mind identity distinct from authenticated session; explicit session generation and reject-second-live-generation rules; exclusive body ownership; disconnect is nondestructive and keeps same Mind/body association; destructive logout is not used for normal disconnect; stale-generation rejection; initial creation requires eligible registered CHARACTER Harness already attached atomically; actual-death eligibility is distinct from voluntary alive ghosting; invalid/duplicate body and authorization failure leave no side effects; transitions represent no duplicate bodies or two-Minds-per-body state. Unit tests should cover every legal/rejected table transition and property/invariant checks. M1 does not claim renderer, durable restart recovery, automatic join integration, or connected acceptance.
