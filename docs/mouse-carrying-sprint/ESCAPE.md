# Portable body escape — source preparation

`PortableBodyEscapeService` is a source-ready timed owner, not yet runtime-integrated.
It delegates duration to the caller's resolved capability and custody/release to an
exact-token `Custody` adapter; it does not remove references or spawn/recreate bodies.
It uses the shared `TimedInteractionSystem` (damage threshold 1, break-on-move 0.3)
and deliberately treats repeated input as already-started, never as duplicate cancellation.
The validity callback continuously rechecks custody/removability/accessibility, actor
displacement >0.3, and change in actor-to-target separation >0.3. Independently, current
actor-target range must remain <=1.5 (including start). This follows pinned
`CanEscapeInventory`'s separate `InRangeAndAccessible` gate and do-after movement
snapshots; the custody owner supplies accessibility using canonical interaction
geometry, avoiding a second LOS engine or self-storage-collider rejection.

SS14 pin `c9df5ef5d675b0d1d226828bddf6b78c28502d91`: `CanEscapeInventory` defaults
to five seconds; upstream escape is directional, requires no hands, interrupts on
movement and one committed damage, and has no chance/strength contest. Local resolved
capability mapping and live dispatch remain unverified. No arbitrary-container escape
or scheduler/input wiring is included.

The local typed `CanEscapeInventoryComponent` now represents `baseResistTime` in
seconds, default 5. Upstream audit: `Resources/Prototypes/Entities/Mobs/NPCs/animals.yml`
`MobMouse` at lines 1721-24 inherits `SimpleMobBase` and `BaseMobAnimal`, whose component
composition is already summarized in `FOUNDATION.md`; its explicit `CanEscapeInventory`
at line 1882 uses the component default. Upstream
`Content.Server/Resist/CanEscapeInventoryComponent.cs` sets `BaseResistTime = 5f`; the
consumer `EscapeInventorySystem.cs` multiplies it by the attempt multiplier for the
DoAfter duration (lines 59-70). Checkout revision verified at the pin above.
The service production entry is `PortableBodyEscapeService.request(LivingEntity,
ServerPlayer, long originSequence, Custody)`: it resolves through `ActiveCharacterPolicy`,
denies missing capability, and converts seconds through `StatusEffectSystem.secondsToTicks`.
Coordinator's concurrent registration/composition changes are now present in the working
tree; this service consumes that resolved component rather than a hardcoded duration.
Package-private duration/component seams exist only for focused scheduler fixtures.

Autonomous eligibility should reuse `NpcBehaviorSystem.eligible`: resolved NPC policy,
alive/live/non-passenger, not `MindControlledMob` movement-owned, not contained, and
not the exact offline player-owned body per `LifecycleStartupRuntime`. It is private
today; expose/reuse it when the autonomous scheduler is integrated. Player lifecycle
and mind control must never be treated as autonomous.

Focused synthetic `portable-escape` GameTests cover production resolution of the mouse
default (5 seconds/100 ticks), absent-capability denial, an explicit resolved-component
override (3 seconds/60 ticks), scheduler timing/exactly-once release,
repeated input, separate actor/relative-target movement, 1.5 range admission boundary,
accessibility, stale custody, damage threshold, and release failure using the shared
production scheduler. Damage already flows through
`AppliedDamageEvent` → `ModEventHooks` → `TimedInteractionSystem.damage`; no duplicate
listener was added. Fixtures are synthetic scheduler/admission evidence only and are
unrun. Include stationary actor-target separation probes at .5 and 1.5 as diagnostic boundaries;
capability direction/no-hands and canonical removal remain unverified integration responsibilities.

Movement source now dispatches controlled directional input to STATIONARY owners and
retains UNIT's directional-or-jump behavior; TRANSIT remains non-escaping and every
contained attempt returns pinned movement state. `LifecycleCharacterSessionControl` passes
the committed `intent.sequence()` into the sequence-aware `GroundedHarnessWorldStep.step`
overload. `ContainedBodyOwner.tryEscape(body, sequence)` defaults to legacy dispatch;
the portable carrier owner must override it and forward the sequence into
`PortableBodyEscapeService.request`. Legacy/NPC callers intentionally supply zero.
The controlled-step fixture verifies sequence forwarding into a shared timed snapshot on
the exact escaping body; it is not a connected client intent/authority test. NPC tick checks the same composed NPC,
movement-ownership and exact OFFLINE-owner gates as ordinary `eligible`, before its
containment rejection, and only dispatches for resolved escape capability. No planner,
motor, ingestion, or voluntary contained movement is enabled by that path.

After canonical release, owner completion may call
`NpcBehaviorSystem.requestPostEscapeFlee(Mob, Vec3 formerRootPosition)`. It sets a bounded
100-tick adaptation through the existing supported-goal route planner and motor; it has
no shared threat registry and is not upstream SS14 behavior. Canonical custody adapter
must invoke it only after successful release. Flee keeps a supported away-goal and retries
DEFERRED routes only at the NPC plan cadence; NO_PATH advances through at most four
supported directional alternatives per cadence, never using direct movement through
obstacles. It cancels an owned NPC ingestion operation on accepted post-release request.
The 100-tick flee window is a deliberate Minecraft adaptation, distinct from five-second
escape duration.

Native NPC fixtures cover composed mouse owner dispatch, synthetic containment, player
movement-ownership denial, away-point planning, shared-budget deferral/retry, finite
phase, enclosed no-route safety, and movement-lease takeover clearing. Deferred routing
retries at the bounded NPC plan cadence; no per-frame A* or unsupported direct steering.
`realPortableCarrierContainmentUpdatesOfflineMouseLocator` reuses the canonical
RegisteredPlayerReadyDriver CHARACTER → death/CORPSE → bound GHOST ready → mouse role
OfferReady chain and `/ms14dev creative` production park, then checks the exact
`LifecycleStartupRuntime.isExactOfflineOwnerBody` predicate while the original mouse has
real portable-key custody. Its 120-tick autonomy window repeatedly drives the existing NPC
tick consumer and checks that the exact body/Mind/custody persist with no escape operation,
flee phase, or movement. This is a native registered-player GameTest fixture, not connected
authenticated-client evidence; central native verification remains pending.
Human harness movement fixture's
stationary-owner input is synthetic and does not prove live inventory removal. Runtime
canonical mouse release remains pending the carry owner adapter.
