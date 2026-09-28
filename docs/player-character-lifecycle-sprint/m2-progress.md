# M2a — durable record/schema foundation progress

**Status: bounded schema/codec foundation only; M2 is not complete and its gate has not passed.**

Implemented an immutable schema-v1 `SavedLifecycleProfile` containing account UUID, profile key,
stable Mind UUID, body UUID, dimension/location, immutable string appearance options, a connection
epoch token plus generation counter, lifecycle state, revision, and optional offline timestamp. The
randomly-issued epoch token is intended to make generations from a prior process/session distinct;
it does **not** prove safe monotonic advancement until durable epoch allocation and record replacement
are coordinated atomically. `GHOST` means the record's current body is a ghost; it does not mean a
disconnected ghost can be recovered. `GHOST_OFFLINE` is separately represented. No runtime behavior
uses these records yet.

The pure JSON codec validates schema/version and required field shape, rejects unknown fields, duplicate
JSON member names, malformed/truncated/trailing input, and batches with duplicate account/profile/Mind/body
ownership. Pure body reconciliation distinguishes a uniquely loaded/alive matching body, unloaded body
(defer), and missing/ambiguous/dead/owner-mismatched body (recovery required). It never loads chunks,
spawns a replacement, or converts uncertainty to a ghost.

Focused tests added: v1 record round-trip and immutability; active/ghost-offline state encoding; unsupported
schema, invalid fields, duplicate JSON keys, unknown fields and truncation rejection; batch uniqueness;
and each fail-closed reconciliation decision. Validation passed:
`.\gradlew.bat test --tests 'com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.*' --tests 'com.juicyslew.moonstation14.ms14.player_body_control.BodyControlRegistryTest' --no-daemon`
(29 tests; Gradle BUILD SUCCESSFUL). This focused result is not the M2 gate.

## Remaining M2 blockers

- M2b now provides the bounded atomic on-disk store foundation, CAS revision updates, and backup/last-known-good
  preservation described below. Still outstanding are explicit file/directory flush durability, proven
  crash/partial-write recovery, migration, and durable epoch allocation/restore integration.
- No hydration API or startup integration and no evidence binding records to world/entity saves. Restart
  monotonicity is therefore a design intent only, not guaranteed against replay or lost writes.
- No integrated loaded-body adapter proving the runtime entity lookup contract, duplicate UUID detection,
  chunk-unloaded retry/defer UX, or operator diagnostics.
- No world-save restart/crash/transition-boundary tests, no authenticated claim or shared-registry hydration,
  and no actual `experimentalMindGhostControl` startup-gate/legacy movement-conflict wiring. Any future runtime
  save/load/reconciliation must be behind the existing startup-sampled COMMON gate; no new flag is allowed.
- Ghost disconnected recovery is explicitly unresolved: the record can say `GHOST_OFFLINE`, but there is
  no policy/store/entity recovery to reclaim that ghost. Existing M1 reconnect fails closed for this case;
  this schema does not change that behavior. Offline death-to-fresh-ghost recovery is also not connected
  to durable records here.
- No automatic join, entity spawn, appearance application, packets, world-save hook, or lifecycle registry
  semantics were changed in this increment.

## M2b — atomic on-disk record-store foundation

Implemented a strict immutable store envelope (schema, CAS store revision, epoch token, generation
counter, complete validated profile list) and `LifecycleProfileStore`. CAS writers acquire a non-blocking
OS file lock on a stable, dedicated `<primary>.lock` sibling before reading the expected revision, and retain
it through backup and atomic primary replacement. The lock file is persistent coordination metadata, not an
orphan temp file. Busy/overlapping locks fail closed. Updates preserve the initialized epoch and nondecreasing
generation counter, require all profile generation tokens to match that epoch, and reject a lower generation
for any account/profile identity present in both old and new snapshots. The store stages same-directory
files and requires atomic rename; unsupported atomic moves fail closed. Before replacing a valid primary,
the previous bytes are atomically retained as the last-known-good backup. Invalid primary/backup metadata,
backup-only evidence, or orphan temporary files cause operator-recovery diagnostics; backup is never silently
selected. A failed primary replacement leaves the old primary intact and temp evidence untouched.

Focused JUnit coverage exercises initialization/reload, multiple accounts, CAS conflict across separately
constructed store instances, lock contention, epoch/profile-generation continuity, corrupt/unsupported data,
backup-only and orphan-temp rejection, and injected partial temp write, backup staging, backup atomic move,
and primary atomic move failures. Staged regular files are passed to `FileChannel.force(true)` before atomic
promotion. Each injected update failure keeps the original primary parseable and leaves temp/backup evidence
that makes subsequent reads fail closed rather than silently reset or promote a backup. This is evidence for
the tested process-crash boundaries only, not a claim of power-loss durability: no directory metadata is
forced, filesystem/device behavior is not proven, and no actual process-kill/restart crash harness is run.
CAS rejection is also tested to retain the winner and allow a later update from its current revision. This
remains a file-store foundation only: no runtime caller, startup gate integration, world-save/entity
reconciliation, restart/entity consistency guarantee, or M2 acceptance is provided. The supplied world-specific path is trusted caller input; this
store does not establish a security boundary against path substitution or symlink/reparse-point attacks. The existing shared
`experimentalMindGhostControl` startup-sampled gate remains a mandatory future runtime I/O boundary; the
independent legacy movement-conflict behavior is untouched.

The store also preserves account/profile continuity across CAS updates: an existing account record cannot be
removed, and its profile key and stable Mind ID cannot be replaced without a future explicit lifecycle
transition proof. Body ID changes remain permitted with nondecreasing generation, so a later death-to-fresh-
ghost transition is not precluded. New accounts may still be added. This is a store-level replay safeguard,
not a deletion/reset lifecycle implementation; MVP still has no profile deletion or character reset.

## M2h — callback-scoped primary CAS primitive (implemented; reconnect remains blocked)

`CurrentPrimary.compareAndSwap` now exposes a capability-scoped complete-snapshot CAS that reuses the OS
file lock held by `withCurrentPrimary`, rather than attempting an unsupported nested lock. It compares
against the exact leased primary revision and epoch and re-reads/revalidates primary data before writing;
expired/wrong-thread leases reject. Tests cover successful replacement while the lock is held, expired
lease rejection, simulated stale-primary revision conflict, and invalid-snapshot rejection without replacing
the current primary. This is deliberately only the store primitive: authenticated persistent reconnect has
not been implemented, and the existing pure living reconnect is still not safe to use for restored lifecycle
Minds. Do not treat this slice as authorization or durable session-generation integration.

The durable living reconnect transition was completed in the next bounded lifecycle slice below. Runtime
store/server integration remains absent, and no process-power-loss guarantee is claimed (including directory
metadata flush or proven filesystem/device behavior). Concurrent atmosphere/power work remains a separate
blocker; this lifecycle-only change does not touch those systems.

## M2c — pure loaded-body resolution/classification seam

Added `LoadedBodyResolver`, a pure injected read-only lookup/classification seam for an already loaded body.
Callers supply exact-UUID lookup evidence and an explicit custom-CHARACTER eligibility validator. A unique
candidate is available only for an `OFFLINE` profile, and only when its UUID, authoritative-bound Mind UUID,
recorded dimension, owner account/profile binding, alive status, non-null entity, and custom-character
validation all match. Other lifecycle states reject before lookup. Unloaded evidence defers; ambiguity,
missing evidence, mismatch, dead bodies, null lookup evidence, and invalid candidates fail closed. An ordinary
`Mob` is not presumed to be a custom character. The candidate's bare fields do not authenticate its Mind
binding or attachment; callers remain responsible for authenticating attachment and validating the custom
`CHARACTER` type. The `runtimeEnabled` boolean only proves no lookup when false and does not authenticate or
replace the integrated startup gate. The resolver does not query worlds, load chunks, mutate records/entities,
spawn, or fall back to a ghost.

No Minecraft server adapter is provided or wired. Runtime gate enforcement is therefore deferred: a future
adapter must check `MindGhostStartupGate.enabledForServer()` and reject when
`MovementStartupGate.enabledForServer()` is true **before** any lookup, and must use only loaded-entity and
non-loading FULL-chunk checks. The pure seam's `runtimeEnabled` argument supports proving no lookup when
disabled, but is not a substitute for an actual integrated server gate. No M3 custom character type or
validator exists yet. This bounded increment is not M2 acceptance and does not verify entity-save binding,
duplicate entity detection, restart behavior, or safe loaded-only adapter integration.

Focused tests cover gate-off/no lookup, unloaded defer, exact loaded owner success, UUID/dimension/owner/profile
mismatch, dead and ambiguous cases, missing/null evidence, and validator rejection. Validation is recorded
after running the requested focused Gradle test command; the remaining M2 gate above is still open.

## M2d — pure in-memory OFFLINE hydration seam

Added a capability-protected hydration API from an already-read `LifecycleStoreEnvelope` and its exact
member `SavedLifecycleProfile` into the shared lifecycle/ownership registries. The facade accepts only
schema-v1 `OFFLINE` records whose epoch matches the envelope, whose saved generation is no greater than
the store-wide generation counter, and which occur exactly in that envelope. It rejects every other
saved state, conflicting profile/account/Mind/body owners, unregistered/non-CHARACTER/ineligible bodies,
and generation overflow without publishing profile, Mind, body ownership, or counter mutations. The
caller-supplied eligibility predicate remains responsible for proving the already-loaded living custom
body and its authoritative Mind/account/profile binding before calling this pure API.

Successful hydration leaves the stable Mind disconnected and body-owned, and assigns a fresh in-memory
generation strictly above both the current registry generation and the envelope's durable counter. Tests
cover a store counter above the saved profile generation, old-generation denial, same-Mind/body
authenticated reconnect, rejected state/kind/eligibility/unregistered/duplicate/debug-owned/overflow
cases, and rejection without partial state. This is **not restart-safe generation persistence**: the
new in-memory generation must be durably persisted before a later integration accepts live sessions,
and durable metadata must be coordinated with the world/entity save. No disk I/O caller, runtime effect,
world hook, auto join, config change, body resolver adapter, or M2 acceptance/restart guarantee is added.

The initial pure hydration API accepted a supplied envelope; same-epoch backup envelope data could satisfy
its checks. That public arbitrary-envelope API has been removed in the bounded M2f provenance slice below.

**Approved limited prerequisite order:** owner authorization allows only the custom Mob entity/identity-binding
foundation (a subset of M3) to precede M2's final loaded-body proof. Once that foundation is done, return to M2;
this does not mark M2 or M3 complete and does not accept renderer/appearance behavior. Automatic join and all
runtime lifecycle activation remain blocked until M2 and full M3 both pass. Any eventual runtime effect must
remain behind the existing COMMON startup-sampled `experimentalMindGhostControl` gate (default `false`) and
existing legacy movement-conflict check.

## M2e — read-only Minecraft loaded-body adapter (implementation, gate still open)

Added `MinecraftLoadedBodyAdapter.observe(server, profile)`. It samples the existing master startup latch and
independent movement-conflict latch before any entity/world lookup; a closed master or active conflict yields
`DISABLED`. It refuses off-thread observation, then performs exact UUID lookup through each server level's
loaded-entity `getEntity(UUID)` only. Duplicate UUID hits are ambiguous. A UUID hit in the wrong dimension is
rejected by the pure resolver. If no UUID is loaded, the adapter checks only the recorded dimension and
location chunk via `ServerChunkCache.hasChunk`: an unloaded chunk defers, while a loaded chunk with no UUID,
missing dimension, or other absent evidence is recovery-required. It never calls `getChunk`, forces a load,
spawns, changes a lifecycle record, or authorizes a session based on `SAME_BODY_AVAILABLE`.

Positive evidence is restricted to the exact registered `PlayerCharacterHarnessEntity`, with a valid explicit
per-instance account/profile/Mind binding, live/not dying/not removed state, and a bound HUMAN prototype
attachment that resolves in the current catalog. Vanilla Mobs and ghost entities cannot pass this adapter's
validator. Focused injected unit coverage verifies gate/conflict short-circuit and off-thread rejection; the
existing pure resolver suite exercises missing/wrong bindings as candidate evidence, vanilla/validator
rejection, Mind and dimension mismatch, unloaded defer, and null/ambiguous fail-closed cases. Real
`ServerLevel` loaded-chunk behavior is not exercised in unit tests and remains a GameTest requirement.

At the time this M2e note was written, primary-store provenance/current-primary hydration was pending; that
prerequisite is now implemented in M2f below. This is **not M2 gate completion**. Durable session generation
allocation and persistence, cross-restart world/entity reconciliation and crash/restart evidence, full persistence
integration, and real server/GameTests remain pending. Full M3 appearance/rendering and connected acceptance
remain pending too. The adapter observes evidence only and does not mutate the registry or represent an
authenticated/authorized session.

## M2f — current-primary provenance prerequisite (implemented; gate still open)

`LifecycleProfileStore.withCurrentPrimary` now obtains the same non-blocking OS file lock used by CAS,
rejects orphan temporary evidence, and decodes the primary file directly. Before lending a store-created
opaque `CurrentPrimary` lease, it validates any present backup under the lock using the same fail-closed
rules as `read()`: corrupt/unsupported backup data, a different epoch, or a backup revision ahead of the
primary prevents the callback and therefore prevents a trusted lease/restore. A valid older backup is
permitted, but is never selected or promoted. The lease is callback-thread scoped and invalidated in
`finally` on normal return or callback failure. The facade restore entry point requires that lease, checks
it is currently active, and hydrates only an exact OFFLINE member of its primary envelope subject to the
existing pure loaded-body eligibility proof. Focused tests were added for same-epoch stale backup membership
against a newer primary, corrupt and higher-revision backup rejection before callback, valid older backup
acceptance without selection, expired lease rejection, lock contention, callback-exception invalidation, and
rejected hydration leaving account/Mind ownership untouched. These new tests have **not** been run after the
owner's hold.

This is only a provenance prerequisite, not M2 acceptance. There is still no production runtime caller or
hook. In particular, reconnect remains unsafe with respect to durable generation ordering: its in-memory
generation change is not yet represented by a persistence receipt. Durable generation allocation/advancement
must be persisted before reconnect/session acceptance. Runtime I/O integration, crash/restart and entity/world-save
consistency evidence, and real server/GameTests remain blockers. Startup-sampled `experimentalMindGhostControl`
remains default `false`; this slice adds no flag or automatic hook and does not alter unrelated shared systems.

## Validation hold

The focused lifecycle JUnit command passed in a previous run, before the newest M2f changes; it is historical
evidence and does not validate those changes. GameTest coverage is fixtures written, **not passed or executed**.

```powershell
.\gradlew.bat test --tests 'com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.*' --no-daemon
.\gradlew.bat compileGametestJava --no-daemon
```

Then run the isolated GameTest and connected visual checks. The previously blocked `AtmosphereRoomGameTests`
compilation is only a historical snapshot, not a claim about current concurrent work. For M2g, the focused JUnit
command and `compileGametestJava` were attempted, but both stop in `compileJava` at the unrelated current error
`StarterStationService.java:128: variable target might not have been initialized`; no tests executed. `git diff --check`
completed without whitespace errors (Git emitted existing LF-to-CRLF working-copy warnings for unrelated files).

## M2g — gated startup-only profile-store probe (implemented; gate still open)

Added a separate lifecycle `ServerStartedEvent`/`ServerStoppedEvent` subscriber. It samples the existing
startup-latched `MindGhostStartupGate` and the independent movement-conflict latch before invoking the
store seam. A closed master gate or active movement conflict produces a bounded `DISABLED`/`CONFLICT`
snapshot with no path calculation or store construction/read. With both gates clear, the probe uses the
fixed `moonstation14-lifecycle/profiles.json` child of `server.getWorldPath(LevelResource.ROOT)`, checks
normalized containment, rejects link/reparse-point or unexpected directory/file components where the
platform exposes them, and creates the dedicated child directory only for this enabled startup probe.
Missing primary and backup is `UNINITIALIZED` and does not create a profile file. An initialized empty
store has a distinct `EMPTY` status and does not authorize first enrollment. Only a batch containing known
`OFFLINE` living claims produces `DEFERRED`; any `PREPARING`, `RECOVERY_REQUIRED`, `ACTIVE`, `GHOST`,
`DEAD_CLAIM`, or `GHOST_OFFLINE` claim makes the whole startup probe `RECOVERY_REQUIRED` with a bounded,
state-specific operator diagnostic. Corrupt, backup-only, orphan-temp, unsupported-schema, or unsafe-path
evidence also becomes `RECOVERY_REQUIRED`. These outcomes do not hydrate registries or spawn characters.
Status is held by exact server instance and stop removes only that server's entry.

This integration does not hydrate profiles or registries, resolve loaded bodies, restore OFFLINE/ACTIVE/
DEAD/GHOST state, spawn/join/reconnect, change modes, install payload handlers, or otherwise activate
lifecycle behavior. Tests use an injected store-reader seam and no fake player. Filesystem containment and
link checks reduce accidental/untrusted-path risk but cannot prove resistance to concurrent path substitution;
Windows reparse-point identification also depends on provider/JDK file-attribute reporting. This is startup
plumbing only, not M2 acceptance; M2 persistence/entity reconciliation, durable session generations, loaded-body
proof, crash/restart evidence, and GameTest/connected validation remain outstanding.

### M2 startup fail-closed state classification

The startup probe now inspects every decoded profile state before reporting a valid-store result. A complete
all-`OFFLINE` batch is still only `DEFERRED` pending loaded-body proof. An initialized empty store is distinct
from a missing/uninitialized store and cannot be mistaken for permission to enroll. Every other known state,
including durable recovery and enrollment-intent claims, rejects the entire batch as `RECOVERY_REQUIRED`; no
startup hydration, spawn, or store write is performed. The gate-off and legacy-conflict paths still return
before invoking the reader. Focused coverage enumerates every schema state and mixed OFFLINE/non-OFFLINE
batches; this classification does not make those blocked states recoverable or complete the M2 gate.

## M2i — durable reconnect generation barrier (implemented; M2 remains open)

Restored OFFLINE records are marked in-memory as restored and cannot use the older in-memory-only living
reconnect API. Under facade/ownership synchronization and a live `CurrentPrimary` callback lease, the new
living reconnect path requires authenticated eligibility, exact membership of the saved OFFLINE profile in
the current primary, matching account/profile/Mind/body identity and the same dormant offline Mind/body owner.
The registry provides a capability-protected read-only reconnect preview and a guarded commit. The preview
allocates an epoch strictly above both the in-memory and durable generation counters without mutation and
rejects non-CHARACTER, ownership mismatch, dead claim, ineligible, and overflow cases. The facade constructs
an immutable ACTIVE profile with incremented profile revision and cleared offline timestamp, replaces only
that member in the entire profile list, and performs lease-scoped complete-snapshot CAS before committing
in-memory authorization. No ghost fallback exists in this method; the explicit death-claim/ghost route remains
separate. CAS/write failure leaves the restored Mind and body offline. A memory commit failure or RuntimeException
after successful durable CAS now uses capability-protected, callback-free suspension: the retained Mind is
disconnected without releasing its body or invoking eligibility again, and its in-memory profile becomes inactive
`RECOVERY_REQUIRED` at the persisted ACTIVE generation. The persisted ACTIVE record is not rolled back and cannot
be ordinarily reconnected; it requires recovery. A pre-CAS rejection/write failure leaves the saved record OFFLINE
and memory normally OFFLINE.

Focused temporary-directory tests cover same Mind/body and strictly newer durable/in-memory epoch, profile
state/revision/timestamp update, stale/invalid/unauthenticated/ineligible rejection, old reconnect bypass
rejection, expired lease, injected primary-write failure preserving offline memory, and separate death-to-ghost
behavior. A post-CAS eligibility exception test verifies persisted ACTIVE with a disconnected, inactive
`RECOVERY_REQUIRED` in-memory claim and no authorization. This does not implement automatic join, connected ACTIVE
crash restore, logout durability, first-join
persistence, server runtime integration, power-loss durability or filesystem directory flush, world/entity-save
coordination, crash/restart acceptance, GameTests, or M2 completion. The broader M2 gate remains open.

### M2 safety follow-up

Durable reconnect now fails closed when the restored Mind has lost its harness (including the null harness ID
left by owner-body unregister), before reading the harness ID or attempting the store CAS. Restored OFFLINE
profiles also reject the in-memory-only offline death claim and pure reconnect-to-ghost route: these routes
cannot atomically persist a durable death/ghost transition. In-memory-only M1 profiles retain their pure death
and ghost reconnect behavior. Regression tests verify no CAS or ghost authorization for a lost body and that
restored death/ghost attempts leave the durable record OFFLINE. A durable death-claim/ghost route remains an
unresolved M2 item; no ghost fallback is provided.

## M2 first-enrollment intent schema foundation (runtime saga still blocked)

Schema-v1 now includes explicit `PREPARING`, a durable first-enrollment reservation for an account/profile,
body UUID, and stable Mind UUID while initial body creation and Mind ownership are being staged. It is not
`ACTIVE` or `OFFLINE`, carries no `offlineSinceMillis`, and existing pure restore/resolution entry points
continue to accept only OFFLINE records; PREPARING and RECOVERY_REQUIRED therefore cannot restore or authorize
a session. Unknown state names remain rejected by strict enum decoding. Store CAS preserves every account claim
(including body identity for enrollment intents), does not allow deletion or identity reuse, rejects direct
PREPARING-to-ACTIVE/OFFLINE promotion without saga proof, and permits only PREPARING-to-RECOVERY_REQUIRED as
the failure terminal transition. RECOVERY_REQUIRED is irreversible in this foundation. Existing generation
monotonicity applies, including at the representable `Long.MAX_VALUE` boundary.

This is schema/store protection only: no runtime auto-join or staged body/store/Mind saga is implemented, and
there is deliberately no direct promotion helper. The running implementation must later persist the reservation
before effects and durably mark failed/ambiguous stages RECOVERY_REQUIRED; crash recovery and a trustworthy
proof/ordering for completing the intent are still missing. Until that saga, entity/world-save coordination,
crash/restart evidence, and the rest of the M2 gate exist, first enrollment remains blocked. No client/server
hook, world spawn, registry behavior, or feature flag changed in this increment.

## M2 first-enrollment disconnected staging (partial; durable promotion blocked)

Added a capability-protected staging entry point that accepts only an active `CurrentPrimary` lease and the
exact PREPARING profile member from that primary. Account, profile, Mind, and body identity are carried from
that reservation; the body must already be registered as an eligible CHARACTER and unowned in the shared
registry. The staged stable Mind is lifecycle-owned and retains exclusive body ownership, but starts
disconnected. Both lifecycle and shared ownership authorization remain false. The existing M1 `create` API
retains its prior immediate in-memory experimental semantics.

This is intentionally staging-only. No proof-scoped PREPARING-to-ACTIVE store transition has been added:
generic CAS still rejects promotion, and no operation activates a staged Mind. Staging failure leaves the
durable reservation PREPARING; there is no implicit deletion, retry-spawn, world access, packet, or join.
The trusted caller remains responsible for proving the registered harness corresponds to the intended staged
world body through the supplied eligibility predicate. Durable commit proof construction and its before/after
write failure/recovery behavior remain blocked and must precede any activation API. This does not complete M2,
M3, or runtime lifecycle integration.

## M2j — durable disconnect transition (pure facade only; gate remains open)

Added a capability-protected, read-only disconnect preview and exact-generation commit to the shared ownership
registry. The durable facade accepts only an exact current-primary ACTIVE record for the same restored profile
that was marked durable-active by a successful durable reconnect, with matching account/profile/Mind/body,
current connected Mind epoch and registered owned CHARACTER body. It constructs a complete replacement batch
with the same Mind/body, dimension, location and appearance, a strictly newer generation, revision+1, and the
nonnegative server timestamp as `offlineSinceMillis`. Store CAS occurs before in-memory disconnection; failed
CAS leaves the session and profile state unchanged. Successful commit makes the same Mind disconnected while
retaining its body ownership. The old in-memory-only disconnect rejects restored profiles, including ACTIVE
ones, while the pure M1-created path remains available. An unexpected post-CAS memory-commit failure revokes
the old Mind epoch where possible, leaves it unauthorized and returns no successful transition; durable
record/memory divergence then requires recovery.

Temporary-directory tests cover persistence and same-Mind/body offline retention, stale generation, duplicate
and expired lease no-op behavior, the pure bypass rejection, and injected primary-write failure. Following a
successful OFFLINE CAS, any in-memory commit rejection or runtime failure now uses a capability-protected,
callback-free suspension: it sets the same lifecycle Mind disconnected without allocating an epoch or releasing
its body claim, and marks the in-memory profile inactive/RECOVERY_REQUIRED at the persisted generation. The
persisted OFFLINE record is never rolled back. This includes commit-time eligibility exceptions or reentrant
mutation; a failed/pre-write CAS still leaves active memory and durable state unchanged. Recovery-required
records cannot take an ordinary reconnect/disconnect route. A focused regression test covers eligibility that
passes preview and throws during commit. This is a pure transition only: no player login/logout hook, indicator,
death route, world/entity adapter, or runtime caller was added. It does not claim M2 completion, M5 completion,
power-loss durability, world/entity-save consistency, or crash/restart safety.

## M2 first-enrollment durable promotion foundation (pure transition; not M2/M4 acceptance)

The staged disconnected first-enrollment Mind can now be promoted only through the lifecycle facade while
holding the exact active `CurrentPrimary` OS-lock lease and the facade/ownership monitors. It requires the
exact current-primary PREPARING member, matching account/profile/Mind/body claim, an existing inactive staged
Mind still owning that registered eligible CHARACTER body, and a successful read-only activation preview.
The facade constructs a complete ACTIVE replacement with unchanged profile/body/Mind identity, incremented
profile revision, and an epoch strictly above both memory and durable generation floors. A facade-minted,
unforgeable one-use proof binds that exact before/after profile, epoch token, store revision, and generation
floor. Only the proof-scoped lease CAS accepts PREPARING-to-ACTIVE; ordinary generic CAS remains strict.
Durable CAS precedes in-memory activation. A write failure leaves the reservation PREPARING and the staged
Mind disconnected. An unexpected post-CAS activation/predicate failure suspends the Mind and marks memory
RECOVERY_REQUIRED; durable ACTIVE then requires operator recovery and cannot authorize a session.

This is solely a pure durable transition foundation. Its caller remains responsible for real authenticated
player identity, loaded/alive world entity and authoritative binding proof, and server-thread execution. No
join hooks, packet handling, server active mode, automatic spawn, or runtime caller was added. No M2 or M4
acceptance, cross-world-save/restart consistency, power-loss guarantee, or player-facing recovery behavior is
claimed. Promotion no longer executes a caller-supplied eligibility callback while holding lifecycle,
checks the exact registered body ID and CHARACTER kind; the ownership registry independently rechecks exact
Mind/body ownership and epoch at preview and commit. A deprecated source-compatibility overload ignores its
callback argument and must not be treated as runtime entity proof. Any future runtime CALLER must prove a live,
alive, custom-character, owner-bound entity and main-server-thread execution before entry, and recheck that
evidence immediately before Begin/Commit when those hooks are wired. Do not accept an untrusted packet boolean
as proof. Tests cover same staged Mind/body promotion, rejection of wrong kind/owned body, mismatched reservation,
expired lease, and absent/forged promotion proof; primary-write failure remains covered by store tests.
There is no injectable guarded post-CAS memory-commit failure seam for this promotion, so deterministic
post-CAS recovery behavior is not covered by a test in this increment. The runtime/proof recheck gap remains.
This change does not claim that deadlock has been solved across legacy methods: create/restore/stage and other
lifecycle/ownership APIs still accept external callbacks while synchronized, and callback risks remain there.

## M2 — durable restored OFFLINE death-claim foundation (partial; M2 remains open)

Added a pure facade transition for an already-restored account whose current durable profile is exactly
OFFLINE. Under the live current-primary lease and facade/ownership monitors it checks exact account/profile/
Mind/body/generation identity, store epoch and full-batch membership, the disconnected Mind's matching body
ownership/epoch, and the registered CHARACTER corpse. Only a trusted caller-supplied world-death predicate
that returns true allows progress, and that check runs before any store write. The facade builds a complete
batch replacement to DEAD_CLAIM with incremented profile revision and generation above both durable and
memory floors, retaining dimension/location/appearance and the existing non-null `offlineSinceMillis`.
After CAS, a capability-protected exact preview commit marks the same Mind dead-claimed and disconnected;
the corpse remains registered and owned. No ghost is created or attached. In-memory-only death handling
continues to reject restored records.

Focused tests cover false/true evidence, stale account/generation and duplicate transitions, CAS failure
leaving store and memory OFFLINE, retained corpse ownership/no ghost authorization, and schema/store
round-trip of DEAD_CLAIM. This does not make a caller predicate into proof by itself: production must provide
a trusted, loaded-world predicate that verifies actual death and its dimension/world identity. The pure
model has no dimension-bearing harness field and cannot independently prove world/entity-save agreement.
There is no death runtime hook, durable ghost reconnect/ghost record transition, connected runtime event
integration, or crash/restart/world-save acceptance. M2 and M6 remain incomplete; this is only their pure
durable death-claim foundation.

## M2 runtime ownership boundary (bounded; not M2 acceptance)

The startup runtime now keeps a separate exact-server lifecycle context only after the existing master startup
latch and legacy movement-conflict latch permit startup and the world-scoped store read/path validation succeeds.
Each context constructs exactly one fresh `BodyControlRegistry` and its one bound `PlayerLifecycleRegistry`,
and holds the associated primary-store handle plus an immutable account-UUID claim index copied from the
validated primary envelope. It never adopts the debug runtime's registry or its sessions. Missing/uninitialized
and initialized-empty stores remain distinct; all saved account claims, including non-OFFLINE states, reserve
their account for future conflict checks. `hasReservedClaim` is package-private/read-only and consults only the
startup snapshot, never disk. Invalid/corrupt/backup-only evidence exposes no context. Server stop removes only
that server's lifecycle context (and startup status), without touching world entities or debug state.

This is strictly an ownership/store-context boundary: no lifecycle mutation, join/auto-spawn, possession,
network, or handler changes are included. It is not M2 acceptance. Risks remain that the debug controller uses
a separate registry and existing handler conflicts must be resolved before M4; startup ownership does not yet
provide loaded-body proof or restart/world-save consistency.

### M2/M4 debug Mind exclusion boundary

The operator-only `/ms14 mindghost start` path now consults the exact server instance's in-memory lifecycle
startup snapshot before creating its debug runtime state, ghost, mode change, or Mind. A validated saved
account claim excludes only that UUID. `RECOVERY_REQUIRED` excludes every account because persisted ownership
is ambiguous; absent/disabled/healthy-unclaimed startup state does not change the existing debug path. The
existing master gate and independent movement-conflict/operator checks remain in force, and the query performs
no disk or world I/O. Lifecycle server stop removes the snapshot and context, so the check retains no stale
server reference.

This is a narrow exclusion boundary, not automatic join or M2/M4 acceptance. The index starts from startup
evidence and can now admit a dynamic account only through the server-thread `rememberPreparingClaim` seam,
while the exact store's live `CurrentPrimary` lease is held after a successful complete-snapshot CAS. The
seam re-reads the locked primary using the existing lease (no nested OS lock), requires a newer primary
revision and an exact account/profile/Mind/body PREPARING record match, and is idempotent. Failed/no CAS,
expired/wrong-store leases, off-thread calls, stopped/unknown servers, and closed master/conflict gates do
not publish. The debug exclusion query remains an in-memory-only check; it does no disk I/O. No runtime
first-enrollment caller exists yet, so this seam alone does not make automatic join possible.

The eventual FIRST-JOIN coordinator must separately reject an already-active debug session before enrollment
effects; account exclusion alone does not prove that a debug session is absent. Recovery, loaded-body proof,
durable saga/restart consistency, and broader persistence/world-save blockers also remain.
