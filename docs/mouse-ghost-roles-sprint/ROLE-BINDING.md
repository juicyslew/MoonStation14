# Mapped role-body lifecycle binding

`PlayerCharacterHarnessBinder.bind(Mob, accountId, profileKey, mindId, resolvedCharacterId)` binds an already-loaded server Mob only. The body must be live in its current level, and its type must resolve to the requested character prototype in the current host mapping (the legacy custom human harness retains its explicit HUMAN-only exception).

`PlayerCharacterHarnessBinder.binding(Entity)` is the common read API. Custom human harnesses continue to use their existing persisted `PlayerCharacterBinding`; mapped ordinary Mobs store the same immutable record in `MOB_LIFECYCLE_BINDING`, a persisted server-only attachment. The attachment is not synced, copied on death, or treated as proof of ownership. Consumers must independently validate the resolved character identity and consult `BodyControlRegistry` / `PlayerLifecycleRegistry` for authority. Binding does not grant role availability, movement, or hands.

The shared binding codec rejects malformed UUID/profile values on decode; absent attachments remain null. Existing custom-human serialized data and binding getter are unchanged.

## Prepared ghost-role control seam

The server catalog/request endpoint authenticates with `LifecycleGhostSessionControl.canonicalGhost(ServerPlayer)` and prepares a handoff with `offerRoleTarget(ServerPlayer, Mob)`. The endpoint must send success only when the offer call returns `true`; the server binds the target UUID and CHARACTER kind into the existing Offer/OfferReady exchange. The ghost remains the committed camera/controller during preparation.

Offer preparation enrolls only the resolved mapped character identity, registers an unclaimed CHARACTER harness in the shared `BodyControlRegistry`, and acquires the existing `GroundedHarnessLease`. OfferReady revalidates the exact ghost epoch, target registration, role component, body liveness and lease before `takeOverGhostRoleDurably`. Failure, expiry or logout closes the lease and releases only the unclaimed registration. Success persists the non-human `prototype` metadata, binds the shared `PlayerCharacterBinding`, and adopts the already-offered target with the new epoch; the client receives the existing Begin/Commit sequence without a second readiness wait. No ghost-session teardown path runs against the committed target.
