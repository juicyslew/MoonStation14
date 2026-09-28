# M1 — pure lifecycle model status

**Status: bounded pure-model implementation; runtime/persistence gates remain closed.**

`BodyControlRegistry` is the single in-memory Mind/Mob Harness ownership authority. It now supports
stable caller-supplied Mind IDs on registered eligible `CHARACTER` harnesses, nondestructive
disconnect/rebind, connected actual-death transfer, and offline death-claim reconnect to a registered
eligible ghost. `PlayerLifecycleRegistry` delegates Mind and body ownership transitions to that
registry, rather than maintaining a second ownership map. A lifecycle instance must be constructed
with the shared `BodyControlRegistry` used by other ownership paths to enforce cross-path exclusivity.

Actual world death is not knowable to this pure model. Death transitions require a caller-supplied
`Predicate<MobHarness>` evidence check; the world/runtime caller must establish actual death (and must
not report voluntary alive ghosting as death). The offline-death form records a claim while offline
and keeps the corpse bound; it does not create or possess a ghost until reconnect supplies a fresh
registered eligible ghost. Connected transfer also leaves the corpse entity registered and unowned.

The offline `DEAD_CLAIM` state is only the awaiting-ghost state. Once a connected death or offline
recovery attaches the fresh ghost, the profile enters `GHOST`; its historical `deadClaim` bit remains
set but does not veto authorization. Authorization still requires the current generation, registered
body kind, shared Mind ownership, and supplied eligibility. A disconnected ghost enters
`GHOST_OFFLINE` and reconnect currently fails closed: ghost persistence/recovery policy is not defined,
so reconnect cannot silently create or replace another ghost. Repeated death transitions from a ghost
are also rejected by lifecycle state, even if the caller's supplied death predicate says true.

Focused validation covers initial claim rejection/atomicity, shared registry conflicts including a
debug-created Mind, stable Mind disconnect/reconnect and stale generations, connected death transfer,
offline death claim and reconnect, and existing startup-gate behavior. This remains in-memory only:
it does not prove durable account/profile records, server-thread or authenticated-login integration,
world death evidence correctness, restart recovery, entity persistence, or runtime gate wiring. No
handler/runtime effect or configuration change is part of M1. Any future lifecycle runtime effect
must use the existing `EXPERIMENTAL_MIND_GHOST_CONTROL` startup latch (`MindGhostStartupGate`); this
pure model does not bypass or itself implement that gate.

Lifecycle profile authorization now also checks the shared Mind, current harness ownership, epoch, and
caller-provided eligibility. Lifecycle transitions hold the shared registry monitor for the combined
profile/ownership update; lock order is lifecycle registry then shared registry, and shared registry
operations never call back into the lifecycle registry. Eligibility and death-evidence predicates
are caller supplied and therefore must be pure/non-reentrant. This model trusts the caller's actual
world-death predicate, authenticated account identity, and server-thread discipline. Offline death
claims are mirrored in shared Mind state and can only recover onto a newly supplied eligible ghost;
debug attach/transfer/release/logout/authorization cannot mutate lifecycle-owned Minds, and harness
unregister remains the intentional world-body-loss path that makes profile authorization fail closed.
The shared registry binds one opaque lifecycle capability to its sole `PlayerLifecycleRegistry`
facade; direct lifecycle mutation APIs without that identity are rejected before any state or
generation change, and a second facade cannot bind the same registry. Restart generation reuse remains
an M2 persistence gate blocker (not an M1 in-memory property); persistence and M4 startup-gate wiring
remain outstanding.
