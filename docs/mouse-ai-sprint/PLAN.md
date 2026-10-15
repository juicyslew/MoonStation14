# Mouse AI sprint — active

Owner testing found zero autonomous movement after two builds on an ordinary
superflat grass plane. The literal command/NBT activation defect is now corrected
and movement verified on a normal main-only dedicated superflat server. Subsequent
owner testing exposed correlated wandering, failed/repeated eating, stale food
routes, stalls and increased player movement jitter. Corrections passed final
automated validation; owner subsequently confirmed random wandering, occasional
squeaks and drinking after forcing Parched, with much less jitter. Connected
correction behavior has not been instrumented. The active follow-up is indexed
in `INTERACTIONS.md`. This is not a sprint-closure handoff.

## Current corrections

1. Replace hash/cardinal wandering with independent per-entity random accessible
   destinations and waits; retain each task until completion or invalidation.
2. Pin an active ingestion transaction, neutralize locomotion during eating,
   acquire target-facing before ray admission, and avoid retry loops for rejected
   or capped sources. Keep the existing shared ingestion owner and player rules.
3. Replan moving targets by meaningful displacement, not unchanged UUID alone.
   Bound and stagger search work, reuse computed routes, distinguish deferral from
   unreachable results, and eliminate per-tick empty-path search retries.
4. Stabilize arrival/braking and verify ordinary dropped-food pursuit. Profile
   many mice/food targets on a normal grass world; do not attribute owner jitter
   to prediction without evidence distinguishing tick stalls from corrections.
5. Add occasional randomized, server-authoritative ambient squeaks through a
   resolved policy and the existing speech sound asset. This is an explicitly
   requested Minecraft adaptation, not an established SS14 ambient schedule.

The subsequent owner-requested interaction follow-up selects player squeak emotes
and brief idle attention. Living carrying/inventory escape still require a new
ownership foundation and are deferred; see `INTERACTIONS.md`.

## Target

Implement reusable, resolved-prototype NPC behavior matching pinned SS14 mouse
AI, with shared player/NPC passage rules. Preserve the accepted mouse appearance,
physiology, ingestion, role takeover, prediction and Creative-return behavior.
Minecraft navigation is an implementation candidate, not a parity requirement.
No commits, merges or sprint closure without authorization.

## Ordered work

1. **Fixture composition and passage:** map supported actor/object collision
   masks and layers; use the actual cross-intersection predicate. Physics-only
   passage must apply to server movement and local prediction. Airlock occupancy,
   bump and crush checks must agree. Keep interaction rays and air sealing intact.
2. **Reusable NPC locomotion and behavior:** prove a bounded 3D planner adapter;
   reuse shared grounded movement rather than add a second motor. Resolved NPC
   composition selects nutrition and idle tasks. AI yields to player authority,
   sleeps when incapacitated, and uses existing ingestion transactions.
3. **Acceptance:** inheritance/default and live-consumer tests; food/drink seeking,
   idle timing, stairs/jumps/pit safety, path invalidation, collision consistency,
   lifecycle takeover/release and regression checks. Run relevant readiness
   selectors and the full native suite in fresh isolated fixture worlds. Supply
   a concise connected checklist; automated checks are not visual acceptance.

## Established SS14 rules

Pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `MouseCompound` tries `FoodCompound`, then `IdleCompound`.
- **Expand compounds recursively:** `FoodCompound` tries food with
  `HungryPrecondition(Peckish)`, then drink with `ThirstyPrecondition(Parched)`.
  Both query nearby Edible sources, move within `MeleeRange`, alt-interact and
  optionally wait on an existing `IdleTime` key.
- Blackboard defaults: vision 10, melee range 1, idle range 7, idle wait 2–7 s.
  Base puddles carry `BadDrink` and are excluded from NPC drink selection;
  player-controlled puddle drinking remains unchanged. `NPCSteeringSystem`
  writes sprint movement, so autonomous locomotion uses the resolved sprint
  speed rather than vanilla movement speed. HTN plan cooldown is 0.45 s.
- Fixture collision checks **mask against the other fixture's layer**, never
  mask against mask. Closed unwelded airlocks permit mice, including bolted or
  unpowered ones. Welding changes the layer to WallLayer and blocks mice.
  Humans remain blocked by closed doors; physical walls block mice.
- Table/firelock/windoor/assembly passage follows audited fixture categories.
  Only map objects the local runtime actually implements; do not guess vanilla
  block classifications or add unsupported components to imitate YAML.

References: upstream `NPCs/{mob,nutrition,idle,utility_queries}.yml`,
`NPCBlackboard.cs`, NPC operator/precondition/utility systems,
`CollisionGroup.cs`, `SharedPhysicsSystem.Contacts.ShouldCollide`, airlock
prototype parents and `WeldableSystem.OnWeldChanged`.

## Architectural invariants

- Brain state is separate from player movement ownership; do not acquire a
  player lease or fabricate hands to operate an NPC.
- Native route search, if used, must honor the same live collision policy and
  cannot also execute vanilla movement alongside the shared character motor.
- Movement passage must not make closed-door interaction LOS or atmosphere
  passable. Avoid changing the block's general collision shape for this reason.
- Runtime scans/path work stay bounded and loaded-only. 3D jump/drop/pit rules
  are explicit Minecraft adaptations, not claimed SS14 behavior.
- Local airlock welding is not implemented. Verify WallLayer collision in the
  policy matrix, but do not add unused welded-state data or claim live welding.
- Parked owner-held OFFLINE bodies must not wander away from their persisted
  return chunk: no periodic location recorder exists. Determine eligibility
  from existing lifecycle authority, not ordinary-mob binding alone.
- Reuse `BodyTimedIngestionService`, resolved food/drink profiles and authority
  validation. Keep existing hunger, hydration and digestion values unchanged.

## Baseline

After main merge: HEAD `8935beb`; 3,280 unit tests, 115 selected readiness tests,
180/810 focused native tests and 810/810 full native tests passed. Reused native
worlds can retain ACTIVE lifecycle profiles and falsely fail fixture enrollment;
use fresh isolated worlds while preserving failed artifacts where useful.
