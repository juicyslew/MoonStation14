# Ghost-role backend contract (work 2)

Status: agreed target for work 3 UI; backend is being implemented. UI must remain presentation/request-only.

## Server authority

The server publishes role entries only for loaded, alive, unclaimed entities in the ghost's current level whose resolved prototype explicitly opts into the ghost-role component. Player-character harnesses are not ordinary role hosts; villagers still use the Human composition. A persisted ordinary-mob binding alone is provenance, not an active claim. Listing, teleport and takeover are same-level in this initial scope; cross-dimension roles are not advertised. Each entry has a stable target entity UUID, role/prototype id, display name, and capabilities (`teleport`, `takeover`). This is a snapshot, not authority: stale, unknown, unloaded, dead, already-minded/claimed, or ineligible targets are denied at every request stage.

Requests are authenticated against the sender's current canonical ghost session and its control epoch, and carry the target UUID (never a client-selected player/mind). Teleport moves that same ghost/view to the target location without mind transfer, body claim, or ending the ghost session. Cross-dimension teleport is not promised in this initial scope. Request-role starts the existing Begin/Ready/Commit lifecycle; target lease/preparation happens while the requester remains the ghost. Ready and commit revalidate target availability and ghost credentials. Only successful atomic commit transfers control; failed/cancelled preparation leaves the old ghost and target role availability intact. Competing claims have one winner.

## Composition and control

Role eligibility is an inherited, typed resolved prototype capability, never inferred from `Mob` or living status. Villager/Pig opt-in is an explicit adaptation; the Mouse worker must add its eligibility metadata to the Mouse composition. A role body may have no hands. Role takeover grants ordinary body control but does not grant hand/inventory authority. Suicide acts on the exact committed body through normal medical lethal damage/death handling, never on a ghost carrier.

Successful takeover forfeits the prior body claim (no return-to-old-body path), invalidates its control epoch and pending actions, and adopts the target as the profile's one current body through existing profile CAS and shared `BodyControlRegistry` / `PlayerLifecycleRegistry`. Do not add an owner map, parallel lifecycle, or production use of experimental ghost possession. Existing default-off lifecycle gates stay off.

## UI-facing operations

* Request available-role snapshots; revalidate indexed candidates' current eligibility/claim/death/load state at snapshot time, not by a per-tick all-mob scan. The open menu refreshes at most once per second; closed menus do not request updates. This bounded pull refresh is a local adaptation rather than SS14's event-pushed UI updates.
* `Teleport(targetEntityUuid, ghostSessionIdentity/epoch)` is view-only and returns success/denial. Its camera adaptation places the ghost two blocks behind the target and one block above its feet, aiming the ghost's eye toward the target's eye. A successful result carries bounded authoritative yaw/pitch; only a matching pending correlation/current ghost context may reset client look and prediction.
* `RequestRole(targetEntityUuid, ghostSessionIdentity/epoch)` requests takeover; UI waits for authoritative result and does not optimistically switch the controlled body.
* UI must expose separate Teleport and Request Role actions and must tolerate entries disappearing between display and click.

### Client protocol/API implemented

`GhostRolePayloads` in `ms14.ghost_roles.network` defines `Snapshot(Context, entries)`, `Request(Context, correlation, target, action)` and `Result(Context, correlation, success, message)`. Context binds the canonical ghost entity UUID and positive control epoch; the request never contains a player/mind selector. Snapshot entry count is capped at 256 and role/display strings at 128 characters. `TELEPORT` and `REQUEST_ROLE` are distinct actions. A result must echo both the context and correlation; the menu ignores either mismatch.

`GhostRoleMenuWindow.open(context)` and `GhostRoleNetworking.installClientHandler` / `installSnapshotRequester` / `send` are client integration seams for the shared bootstrap/backend hookup. The window uses `GhostControlClient.committedGhostContext()` (committed GHOST harness only, not vanilla spectator state); it closes when that exact context is no longer authoritative. It waits for the matching result before clearing pending state and never changes control locally. Server payload type/codec registration, snapshot request encoding and endpoint handler remain backend/bootstrap work; these client seams alone do not provide end-to-end functionality.

## Deliberate SS14 departures

Pinned SS14 uses first-come-first-served ghost-role takeover and unregisters critical/dead or minded role bodies. This implementation uses explicit prototype eligibility, loaded/alive/unclaimed filtering, and lifecycle CAS/lease authority. Stable local mind transfer with forfeiture of the previous body is the semantic counterpart of upstream transfer that prevents return. Teleport is requested instead of upstream follow. Testing suicide is simplified to a command applying normal lethal medical damage; SS14 `/suicide` ghosts first and dispatches item/environment overrides, which are outside this contract.
