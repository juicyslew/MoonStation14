# Ground-item pickup decision — CHARACTER actions

**Plan and pending owner decision; not implemented.** Recommendation: explicit click-to-pick-up for eligible controlled CHARACTER bodies, matching inspected SS14 active-hand pickup intent. This is not yet approved as final. Vanilla Minecraft walk-over pickup is the alternative considered and must be recorded as an SS14-fidelity divergence if selected; it also requires proof of server authority, no duplicate acquisition path, and collision/body ownership behavior. Record the owner's choice before M4; server pickup proof is independent of the later, nonblocking visual decision. Do not enable global auto-pickup or change unrelated contexts.

## Preconditions and behavior

1. **Ownership prerequisite:** do not enable item pickup, drop, grant, item consumption, placement or held-item/tool actions until the hands/inventory integration has real `ItemStack` transport, durable canonical ownership, durable Creative preservation/isolation, and a tested atomic transfer/rollback contract. Current hands metadata is token-only and grid/ledger work is not item transport. This does not block M0/M1 or audited item-independent block use and empty-hand breaking.
2. A client click is only an intent. On the server resolve the exact authenticated session, current capable body and epoch; verify pickup capability, usable/not-stunned state, loaded chunk, target still present, target visibility and server ray/reach, hand availability/whitelist and quantity. If walk-over is explicitly approved instead, its collision/trigger and body ownership checks must likewise be server-authoritative.
3. Commit the `ItemEntity` quantity/identity to body-owned hand storage atomically. Revalidate immediately before commit. A race, stale epoch, removed target, occupied/ineligible hand or failed transfer rejects without consuming or duplicating either side. Drop uses the inverse ownership transaction and an authoritative spawn/result.
4. Before choosing/implementing pickup, audit every acquisition entry point: vanilla `ItemEntity.playerTouch`, mob-loot pickup, and direct/API pickup pathways in addition to the proposed click route. For eligible sessions, suppress only the route(s) that would bypass the chosen interaction model. Scope narrowly: unrelated players and Creative behavior remain unchanged. Do not use a global cancellation or alter another sprint's mode gate. If precise scoping is not proven, stop rather than risk regression.
5. Apply rate/count/work bounds. Log or return bounded rejection/results; clients cannot select arbitrary quantities beyond server-owned stack limits or assert target ownership.

## Visual decision remains separate and nonblocking

Recommend a later client stage with a flat, non-bobbing visual for SS14 mod-owned items only; leave vanilla item rendering unchanged. This is not the final visual decision and does not block server pickup proof. A placeholder is acceptable until the owner decides. Do not change the global vanilla `ItemEntity` renderer or generate assets in this plan. Server pickup validation must work identically regardless of rendering and must never trust client visibility claims.

## Required proof before enablement

- Hands owner signs off on real item identity/quantity, persistence, isolation and atomic hand↔world transfer API.
- Before M4, owner records click versus walk-over decision; walk-over selection explicitly records the fidelity divergence.
- Exact-version audit identifies `playerTouch`, mob-loot and direct/API routes and proves chosen-route enforcement/suppression without affecting unrelated players/Creative.
- Dedicated GameTests cover empty/occupied hand, whitelist, full and partial quantities, stale/removed target, occlusion/range/loaded-chunk rejection, two-player race, and injected transaction failure. If explicit click is approved, test that the eligible body cannot pick up by walk-over. If walk-over is approved, test collision/body ownership, server authority and absence of a duplicate acquisition path; do not enable global auto-pickup for unrelated contexts.
- Owner-only connected test verifies the owner's selected pickup behavior and unrelated/Creative behavior in a disposable world; assistant does not perform manual gameplay.
- Two-location conservation evidence proves failed and successful operations leave exactly one owner, including body/session invalidation boundaries.

**Decision status:** click pickup recommended but owner decision pending before M4; visual finalization is separate and nonblocking, with flat mod-item-only visuals recommended for a later stage. Server implementation remains blocked on actual hands/item ownership, durable Creative isolation and route audit. This does not authorize grants, inventory activation, global renderer changes, or edits to other sprint gates.
