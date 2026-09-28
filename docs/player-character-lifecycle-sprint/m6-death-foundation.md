# M6 durable death-claim foundation

**Status: connected/offline durable death claims and connected corpse-to-ghost handoff are wired; restart and world/entity-save agreement remain unproven.**

On ordinary startup, the lifecycle probe accepts only a validated current-primary batch whose records are all
OFFLINE and/or DEAD_CLAIM as DEFERRED reserved claims. The exact persisted account IDs remain excluded from
debug Mind starts, but startup does not hydrate a living-body owner, prove that a corpse is loaded, or spawn a
ghost. Empty initialized stores remain EMPTY and absent stores remain UNINITIALIZED. PREPARING, ACTIVE, GHOST,
GHOST_OFFLINE, RECOVERY_REQUIRED, unsupported/corrupt evidence, and batches mixing any such state with an
offline/death claim remain RECOVERY_REQUIRED.

`PlayerLifecycleRegistry.markOfflineDeathDurably` accepts only a restored account whose exact saved profile
is an OFFLINE member of an active `CurrentPrimary` lease. `markActiveDeathDurably` accepts the matching
connected durable ACTIVE claim. Both validate store schema/epoch/generation, exact account/profile/Mind/body
identity and ownership, and the registered CHARACTER harness. A caller-provided world-death predicate must
affirm that exact corpse before any CAS/write. False or throwing evidence, stale identity/epoch,
absent/unregistered body, or an invalid/expired lease leaves durable and in-memory state unchanged. The pure
facade accepts evidence as input and does not itself establish that the caller is trusted. Neither method
infers death from a missing or unloaded body.

The complete profile batch is CAS-replaced with DEAD_CLAIM, revision and connection generation incremented,
while preserving the offline timestamp for OFFLINE claims, or recording the supplied current timestamp for
connected ACTIVE claims; dimension/location/appearance come from the complete caller-supplied body snapshot.
Only after successful CAS does the facade use a capability-protected preview/commit to mark the stable Mind
deadClaim and disconnected. Its claim on the corpse remains; the corpse remains registered; no ghost is
spawned, claimed, or authorized. Durable profiles cannot use the in-memory-only death or reconnect route.
Pure non-durable M1 profiles retain their existing in-memory death behavior. If the post-CAS ownership commit
unexpectedly fails, the facade suspends authorization and marks memory RECOVERY_REQUIRED rather than reporting
success.

Focused lifecycle tests exercise connected and offline claims, true/false/throwing evidence, stale account/epoch,
store CAS failure, duplicate death, corpse retention/no ghost, and schema/store serialization. These prove only the pure policy and file-store
boundary exercised by the tests, not that arbitrary supplied evidence is trustworthy. The pure harness does
not contain a dimension. `MinecraftCharacterDeathEvidence.verify` now supplies a narrow, read-only server-thread
adapter: after both startup gates it checks only the saved dimension and exact loaded body UUID, and requires the
registered custom character harness, saved account/profile/Mind binding, HUMAN prototype, and
`isDeadOrDying()` or non-positive health. It never force-loads chunks and never treats missing, unloaded,
removed, wrongly typed, ghost, or ordinary Mob entities as death evidence. Its focused test seam covers gate-off
without lookup, alive and mismatched evidence, and a positive dead-body result.

The custom entity calls `LifecycleCharacterDeathHandler` only after `super.die` changed `dead` from false to
true on the server. The handler checks both startup gates, the exact server thread and loaded entity UUID,
custom entity type, confirmed death, binding and HUMAN identity. For a saved OFFLINE profile it rechecks the
exact current-primary row under its lock, restores corpse-only dormant ownership if needed, and CASes the
profile to DEAD_CLAIM using the existing durable transition. The corpse is retained and no ghost is created.
Repeated hooks are harmless because the saved state is no longer OFFLINE. Failed or ambiguous persistence is
logged and does not declare a ghost. For a connected ACTIVE death, the handler additionally requires the exact
current lifecycle session, owner, body/profile/Mind binding, and ACTIVE in-memory epoch. Under the current-primary
lease it rechecks the exact ACTIVE row and calls `markActiveDeathDurably` with trusted evidence for the confirmed,
dead, still-loaded exact custom body. Only after the DEAD_CLAIM CAS does it drop that exact session, clear the
movement-owner marker, and send Stop for the old epoch without invoking generic fail/RECOVERY_REQUIRED handling
or disconnecting the carrier. The old character handler is removed before any ghost is staged, so stale old-epoch
intent has no owner. It then requires the exact retained corpse to remain loaded in the saved dimension, stages a
fresh `.noSave` ghost at that corpse's server-observed position, and durably activates the same Mind as GHOST
before calling `LifecycleGhostSessionControl.beginPrepared`. Begin/Ready/Commit uses the new generation; no
character and ghost session overlap. Staging or handshake failures retain DEAD_CLAIM and corpse identity, clean up
a safely revocable transient ghost, and disconnect with reconnect/recovery guidance. Rejected or failed durable
claims retain the existing fail-closed suspension path; vanilla active play is not resumed.

`stageDeadClaimGhost` accepts only an exact DEAD_CLAIM member of a live current-primary lease and a registered,
eligible, unowned GHOST harness distinct from the durable corpse UUID. It restores the same stable Mind directly
onto that ghost in a disconnected state, without a harnessless active interval. This works with an empty
post-restart ownership registry and also exclusively transfers an existing offline corpse claim after validating
its owner. The durable row remains DEAD_CLAIM with the corpse UUID; the ghost is runtime-only. Staging alone
cannot authorize. `activateDeadClaimGhostDurably` requires authentication and the same exact row/ghost binding,
increments profile revision and generation while retaining corpse identity and offline timestamp, CASes that
DEAD_CLAIM row first, then enables the Mind. Invalid targets, stale rows, unauthenticated requests, or store CAS
failure do not grant ghost authority. The runtime stager uses these transitions only after validating the
authenticated carrier and exact saved claim.

The capability-protected `returnGhostToDeadClaim` registry transition now supports pure logout of that active
durable ghost session. It requires the exact account/Mind/ghost/corpse/current epoch and retained corpse binding,
then atomically revokes connection authority and transfers the same Mind's ownership back to its registered
CHARACTER corpse. The profile returns to inactive DEAD_CLAIM while the durable row remains unchanged: same corpse
UUID, state, and generation. Logout allocates no generation; the disconnected Mind cannot authorize its former
ghost epoch, and a later staged ghost receives a newer generation. Missing ghosts or ownership inconsistencies
fail closed into RECOVERY_REQUIRED rather than creating a second claim. This is a pure registry capability;
runtime callers remain responsible for safely unregistering/discarding their transient ghost entity.

The lifecycle networking slot is now owned by `LifecycleSessionPacketRouter`: character sessions install their
exact-session handler through the router, which reserves an optional ghost-handler slot for the future lifecycle
ghost controller. Packets route only when exactly one controller's supplied owner predicate accepts the connected
server player; overlap and unknown ownership are dropped, with no payload body or epoch used for owner selection.
Clearing the last character session removes only its router handler and preserves an installed ghost owner. The
debug networking slot remains independent. `LifecycleGhostSessionControl` provides a prepared runtime
handshake boundary: its package-private entry accepts an already-active durable GHOST Mind, exact authenticated
connected non-fake listed spectator, and the already-registered transient `.noSave` ghost. It installs the shared
ghost router handler while sessions exist, sends Begin with the exact epoch/entity id/GHOST kind, and accepts Ready
only for the matching uncommitted session before 100 ticks after rechecking the loaded ghost, same dimension,
spectator mode, startup gate, movement conflict gate, and current Mind ownership. Commit changes the camera only.
Committed sessions accept only exact-epoch intents for the authorized account/player/Mind and current ghost camera;
at most eight packets are admitted per tick, while the shared monotonic `GhostIntentGate` allows only one strictly
increasing sequence to be applied per tick. On `PlayerTickEvent.Post`, the shared `GhostMovementMotor` applies
quantized wishes using server-side `Entity.move`; client position/body selection is absent. The carrier remains a
spectator, and each tick emits a bounded authoritative GHOST snapshot with the current entity ID, epoch, and
applied-sequence acknowledgement. Invalid motion, out-of-world state, authority loss, or ghost removal returns
the exact Mind to its retained corpse claim, unregisters/discards the transient ghost, and disconnects the carrier;
revocation failure fails closed for explicit recovery. Server shutdown clears transient sessions without deleting
the corpse claim. Both connected-death and saved-claim login routes invoke this controller only after durable
activation, and neither route intentionally leaves a player with a half-activated ghost.

`LifecycleDeadClaimGhostStager.stage` is called by the exact authenticated listed real-player join route for a unique
current-primary DEAD_CLAIM. The connected-death route calls its stricter loaded-corpse entry after the claim CAS. The join reads one unique account
row: OFFLINE uses the existing living reconnect, DEAD_CLAIM uses this ghost route, while missing/ambiguous rows and
all PREPARING/ACTIVE/GHOST/RECOVERY_REQUIRED states fail closed. After changing the carrier to spectator, staging
rejects debug/duplicate lifecycle sessions and conflicting/ACTIVE memory, and revalidates the exact supplied claim
under the current-primary lock. It uses the saved corpse location only when its dimension/chunk is already loaded;
otherwise it spawns at the currently loaded login carrier location, never force-loading a corpse chunk. A loaded
saved corpse must be the exact bound custom character, and its current position is preferred. It creates a fresh
registered `.noSave` ghost, verifies the exact live entity and harness, stages the stable disconnected Mind,
advances the durable claim generation/revision while retaining the corpse UUID, and returns a prepared result only
after GHOST authority is active. The login route then calls `beginPrepared` for the full GHOST protocol and
authoritative movement. A clean ghost logout returns that same Mind to DEAD_CLAIM while retaining the corpse UUID.
Pre-stage failures unregister/discard only the new ghost. If staging has transferred the Mind but durable activation
fails, the ghost remains registered and disconnected as an explicit recovery reservation. If prepared handshake
startup fails after activation, the caller returns the exact Mind to DEAD_CLAIM or suspends recovery and discards
only the transient ghost, then disconnects. When corpse chunks are unloaded, the ghost appears at the login carrier
until a future spawn-near-corpse policy for reconnects; connected-death handoff instead requires a loaded exact
corpse and refuses to fall back to a carrier-selected location.

This staging boundary has no dedicated mocked-world JUnit/GameTest: pure registry tests cover stable Mind transfer,
new ghost identity, durable corpse-row retention, stale/unauthenticated rejection, and wrong harness kind. Those
tests establish policy only; they do not demonstrate a live authenticated caller or production handoff.

This still does not prove durable world/entity-save agreement. A dead loaded body is valid evidence until the
world/entity cleanup event removes it, but asynchronous call timing may miss that short-lived loaded-body window;
missing after cleanup deliberately fails closed rather than being inferred as death. No event integration or
record mutation is performed by this adapter.

The custom `PlayerCharacterHarnessEntity` now overrides only its own `tickDeath`: it keeps the actual dead
entity in-world and saturates vanilla `deathTime` at 20 instead of allowing the default removal. It does not
restore health, change invulnerability, or interfere with vanilla `die` processing (including the cancellable
NeoForge death hook and one-time drops). After `super.die` returns, the entity exposes a one-shot
`hasConfirmedDeath` signal only when a server-side call changed `dead` from false to true. This is a safe future
adapter signal only for its own entity type and does not affect other entity types. The
character remains no-AI, and no chunks are force-loaded. A GameTest checks zero-health death and same-UUID corpse
retention beyond 25 ticks; it does not mutate the global cancellable-death event to test cancellation behavior.

Connected confirmed death now follows durable corpse claim with same-Mind fresh-ghost handoff; offline death persists
`DEAD_CLAIM` and the authenticated reconnect route stages a fresh ghost. If the corpse is unloaded at offline-death
login, the ghost can start at the already-loaded carrier location. Use actual damage/death, not entity removal, when
testing. This wiring and the automated tests are not connected-owner acceptance. Full build and full JUnit recently
passed after the movement fix; the isolated
`runGameTestServer -Pms14GameTestDir=build/gametest-lifecycle-movement-final --no-daemon` run reported 158 GameTests,
all required tests passed (`build/gametest-lifecycle-movement-final/logs/latest.log`). Those runs precede connected
owner testing. M6 and M2–M7 remain unaccepted pending the [connected acceptance checklist](connected-acceptance-checklist.md).
Crash-time world/profile save disagreement remains fail-closed future work; there is no operator recovery tool. No
shared events, debug systems, atmosphere, or power behavior is changed here.
