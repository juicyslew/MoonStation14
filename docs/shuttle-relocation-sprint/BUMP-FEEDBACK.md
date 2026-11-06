# Native-frame airlock bump feedback

## Feedback-04 collision-time correction

The native collision-time path passes focused headless checks, but **M1 and
connected behavior remain unaccepted**. Pinned SS14
`Content.Shared/Doors/Systems/SharedDoorSystem.cs:609-621` opens a tagged actor's
door from `StartCollideEvent`; lines 717-727 recheck actual contacts on closure.
Neither requires final post-solver overlap or voluntary movement into the door.
Normal open/access/bolt/power admission remains mandatory.

The corrected native trigger must prove a current selected door fixture collision,
or a genuine coplanar collision-time fixture witness—not a neighborhood nominee
or proximity. Consumer-side live selected state, frame ownership, canonical root
and captured controller identity are still revalidated. Current selected plane
classification must not come from Sable's first-plane-per-frame map: that map can
retain a floor plane before a later door collision in the same solve.

Capture-only validation was 12/854 selected, four passed and eight failed.
Feedback-04 bump-10 completed **12/858 selected, four passed and eight failed**.
The real floor-first fixture now consumes that same fresh result through
`afterNativeMove`. Bump-30 completes **13/859 selected, all 13 passed**, including
powered idle-Human moving-door contact, real inside/outside travel, stale seam/root
refusal, ordinary-world occlusion and shock/recontact memo episodes. Source review
passes. Full-31 also passes **859/859**; connected results remain a separate gate. Earlier
failures and fixture-premise corrections are recorded in `EXECUTION.md`.

## Current implementation boundary

`AirlockBumpSystem.beforeMoveContacts` retains the existing bounded ordinary-world
swept-AABB ordering but only nominates its nearest world-door plane. Effects are
deferred to `afterWorldMove`, where the actual post-collision body bounds must still
touch the nominated world leaf. This prevents a farther ordinary-world door from
opening when Sable's earlier sublevel collision clips the body against a nearer
frame wall. World and frame contacts have separate actor-local root/revision/readiness
memos; each route clears only its own contact episode, while both routes reuse the
same `activateContactSet` power/phase/electrification gates.

The native observer retains the selected blocker plus a bounded same-plane
neighborhood of candidate airlock pieces, each with canonical root and controller
UUID obtained through `LoadedFrameRayQuery`'s known-state/controller getters. The
collision-result-owned selection memo avoids repeated neighborhood scans; nominee
storage is deduplicated but not truncated by a copied SDK iteration constant.
`afterNativeMove(Entity, AABB, Vec3, CollisionInfo)` consumes only a fresh native
result supplied by its movement owner after native carry. It validates each
selected blocker and each exact nominated door/root/controller identity against
live FULL storage, so a replacement seam controller cannot be discovered and
substituted from old collision evidence. Direct selected airlock pieces are native
witnesses; other nominees require uninflated collision-time SAT of the real piece,
coherent with the current selected horizontal normal. All witness math copies
geometry into private SDK scratch so it cannot mutate the actual solver.
Non-airlock selected hull pieces may nominate a genuinely colliding coplanar door;
the bounded neighborhood itself is never evidence. No final-body SAT, own-input
or request-opposition gate remains in the native trigger.

Ordinary-world and native endpoint ordering reuse one loaded first-blocking-plane
sweep. A nearer world wall or closed ordinary door must mask a farther frame door.
The exception is an independently witnessed original pre-move body already touching
the candidate at collision-time frame pose: a moving door can contact an idle actor
even if its push is blocked. `preMoveDoorWitness` must use the original native AABB,
not the current solver OBB; conflating them previously bypassed every world blocker.
Vertical world clipping must precede the horizontal ordering sweep so a downward
support probe cannot make the floor a false horizontal occluder; this correction
passes focused validation. Selection claims are per native substep/iteration so
an earlier contact cannot suppress a later door reached with the same blocker and
normal. No new SAT kernel, proximity opener or prototype component
is introduced. `FirstCollisionInfo.localLocation` is not block-position data.

The existing generic server `LivingEntity.travel` RETURN observer dispatches after
Sable's priority-1000 carry for non-movement-owned bodies. The owned-body bridge
uses the same method once after native carry, comparing the exact result reference
against that move's prior collision info. Signature:
`afterNativeMove(body, preMoveBounds, requestedOwnVec, freshCollisionInfo)`.
It must not run from client prediction/replay or apply a second move.

## Parity and verification status

The pinned SS14 source is documented in `docs/airlocks-sprint/BUMP.md`: the
`Airlock -> BaseStructure` entity chain composes a `Door` component;
`Door.BumpOpen` defaults true, and shared
door collision routes through existing open/access/bolts gates. The local human
profile's `DoorBumpOpener` marker is resolved from `human.json`; no generic living
entity fallback is added. Local bump activation reuses the current resolved door
prototype, paid power, phase, bolt and electrification behavior.

`NativeAirlockContactGameTests` includes source fixtures for native metadata; paid
20-W real-travel opening; both frame/world wall occlusion directions; rotated
face/near-jamb behavior; seam roots; continuous-contact memo partition; unpowered
leave/paid re-entry; and refusal after controller replacement or frame removal.
The final focused selector is green. The native-before-ordinary ordering case
retains the entire canonical cabin, starts its actor on the inside deck and uses
an outside powered ordinary door. Actual ordinary nomination and positive ordered
TOIs are prerequisites; whole-assembly zero-SAT hull clearance is retained.
Unknown-storage coverage must not be inferred from loaded-state source gates;
connected bump acceptance remains open.
