# Exact abandoned-ACTIVE reconnect boundary

`LoadedBodyResolver.resolve` intentionally remains OFFLINE-only. Startup
classifies a valid primary containing only ACTIVE/OFFLINE/DEAD_CLAIM rows as
DEFERRED and reserves accounts; it hydrates no Mind and grants no login
authority. PREPARING, GHOST, RECOVERY_REQUIRED, duplicate ownership and corrupt
store/path evidence remain startup RECOVERY_REQUIRED. The separate
`resolveAbandonedActive` / `MinecraftLoadedBodyAdapter.observeAbandonedActive`
entry points provide loaded-only identity evidence for normal-login recovery.
ACTIVE records have no trustworthy historical fault reason; admission uses
exact identity/ownership proof, not a reason.

Admission must additionally prove the unique current-primary account row,
epoch/generation agreement, valid saved binding, finite actual body pose and
known current frame where applicable, absence of competing sessions/owners, and
either an exact disconnected `RECOVERY_REQUIRED` record retaining the same
Mind/body, or absent lifecycle memory with clear account/Mind/body ownership
indexes and a preflighted registered-character restoration. Both paths CAS the
exact current-primary ACTIVE row to OFFLINE at the observed live pose; the fresh
path then uses `restoreOfflineAfterDurableCas`, followed by the ordinary
`reconnectOffline` generation bump and Begin/Ready flow. Conflicting indexes,
stale rows, duplicate/missing body evidence, or invalid bindings remain
fail-closed. Never use the saved ACTIVE pose to move the body or load a chunk as
proof. This is a loaded-body Minecraft recovery adaptation, not a claim of
SS14 reason-coded recovery parity or broad crash reconciliation.

## Failed saved-character admission

An authenticated account with a unique OFFLINE/ACTIVE saved living claim is not disconnected merely because its body cannot be loaded or exact proof fails. The already-connected vanilla carrier is put in SPECTATOR and tracked only for that connection; no carrier inventory writes, body/Mind reassignment, or saved pose teleport occur. `/ms14_return` is the player-accessible retry and calls the same exact reconnect handler. Logout and server stop clear waiting ownership. A timeout ends one automatic wait, not the durable claim.

The adaptation follows the admission fallback shape in pinned SS14 `Content.Server/GameTicking/GameTicker.Player.cs`: `PlayerStatusChanged` falls back to observer for missing/deleted bodies or a failed attach, while lobby admission without a Mind is distinct. Here the existing Minecraft vanilla carrier in SPECTATOR is the non-character waiting state; this does not create an SS14 ghost or replace/detach the stable Mind. For abandoned ACTIVE, loading the one exact persisted chunk is availability only: the loaded entity must still pass live identity, ownership and frame proof, and only its observed pose can be recorded by the existing CAS recovery.

## Moving-cabin headless coverage

`LifecycleMovingShuttleReconnectGameTests` passes in feedback-04 motion-6. One
4 m/s native pulse establishes motion before logout/rejoin. The normal Ready flow
preserves the same body/Mind and issued active-hand item/token/stack, leaves the
Spectator carrier inert, and emits a real frame/storage snapshot. Independent
review found the old post-Ready assertion compared against the pre-logout pose,
so it could pass after motion stopped. The corrected fixture uses a Ready-time
baseline and supplies normal neutral intents on each of eight subsequent ticks,
as a connected client does. Reconnect-15 passes **1/858 selected, 1 passed** with
actual additional body/frame motion and the existing snapshot producer. It does
not prove connected camera or interpolation.
The owner still needs a connected moving-cabin rejoin check after the M1 retest hold
is lifted; stationary connected rejoin was already reported working.
