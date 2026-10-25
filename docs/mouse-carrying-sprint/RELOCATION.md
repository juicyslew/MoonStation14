# Offline body relocation

The lifecycle profile remains the recovery locator, not a second ownership store.
Trusted server code may call `LifecycleServerContext.relocateOfflineBody(accountId,
mindId, bodyId, expectedGeneration, loadedBody, dimension, location, ownerProof)`
after proving physical custody of the exact loaded body on the server thread.
`ownerProof` receives the exact durable row and must verify the captured loaded
entity is still the original exact level entity, the persisted
`PlayerCharacterHarnessBinder.binding(entity)` matches account/profile/Mind/body,
and custody still holds. Recheck that proof at the caller around world sampling;
never trust a client packet or a role component alone. The operation requires one
OFFLINE account/Mind/body/generation row and a matching registered `MobHarness`;
it rechecks caller-supplied owner proof before committing. It changes only the
dimension/location and row revision through the current-primary CAS. Lifecycle
state, connection epoch/generation, appearance, and offline timestamp are kept.
Same dimension/chunk (floor X/Z divided by 16) updates are no-ops. ACTIVE, ghost, ambiguous, stale, and unclaimed
bodies are not enrolled or written. Callers must avoid per-tick calls and invoke
only when the recorded chunk/dimension changes; cold lookup continues to use the
loaded body's live pose rather than the locator.

The public gameplay facade for containment/flight callers is
`LifecycleStartupRuntime.recordOfflineBodyRelocation(Entity body,
Predicate<Entity> physicalOwnerProof)`. It derives generation and the expected
row from the current OFFLINE durable account row; callers do not guess a
generation or scan storage. The explicit-generation overload remains available
for focused tests/internal use. It is server-thread only, accepts an
actual loaded `Mob`, samples its level/dimension/position, derives the lifecycle
binding, and requires the pre-existing registered character harness. The proof
must confirm current physical custody; callers should invoke on dimension/chunk
transitions, not every frame. The current stored APIs are
`LifecycleServerContext.relocateOfflineBody(...)`
for the trusted gameplay adapter and `PlayerLifecycleRegistry.relocateOfflineDurably(
CurrentPrimary, savedOffline, dimension, location)` for the core CAS transition.
`MobHarnessKind.CHARACTER` and the generic lookup
`PlayerCharacterHarnessBinder.binding(Entity)` cover mapped role-owned ordinary
mobs as well as custom harnesses: the mouse character definition maps the
`moonstation14:mouse` host and resolves a `GhostRole` component, while the binder
stores the same generic account/profile/Mind binding for mapped mobs. Role adoption additionally checks exact
account/profile/Mind/body and resolved `GhostRoleComponent` in
`LifecycleCharacterSessionControl.reconnectRoleAdoption`.

`OfflinePortableMouseRelocationGameTests` now covers the active-owner lifecycle
path: it creates an account through the canonical ready/death/ghost/role-offer
chain, parks the exact mouse through the production Creative command, holds that
same mouse with a separate ready carrier's committed portable key, moves the
carrier across a chunk boundary, and lets `BodyContainmentService.tick()` pin
the mouse and perform the production relocation call. The fixture reads the
current durable profile through the package-local server context and asserts
that only location/revision change while OFFLINE state, account/profile/Mind/
body UUID, connection epoch/generation, and offline timestamp are preserved.
It does not claim cold-reload return or authenticated-peer input. It is source
only until the carry worker runs the focused native selector.
