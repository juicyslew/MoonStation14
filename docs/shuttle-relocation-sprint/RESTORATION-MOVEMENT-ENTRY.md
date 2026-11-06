# Reconstructed living bodies: movement-entry phase

## Source contract (2026-10-20)

Pinned public Sable source:
`https://github.com/ryanhcode/sable/tree/ad8b4d377741fbd923f4d863b2cf9e462daff9a6`.
This establishes source behavior, not installed source/binary equivalence.

Paths below are relative to `common/src/main/java/dev/ryanhcode/sable/`:

- `mixin/plot/ServerLevelMixin.java`: `sable$tickPlotContainer` injects at
  `ServerLevel.tick(BooleanSupplier)` HEAD and calls the container when ticks run
  normally. This is **not** `mixin/plot/container/ServerLevelMixin.java`, which
  constructs/exposes the container and does not contain the ticking call site.
- `Sable.java`: `defaultSubLevelContainerInitializer` registers the physics
  observer. `api/sublevel/SubLevelContainer.java` dispatches observer ticks.
- `sublevel/system/SubLevelPhysicsSystem.java`: copies logical to last pose
  before stepping the pipeline; updates logical pose from subsequent physics
  steps. The native interval therefore exists before normal entity ticking.
- `sublevel/entity_collision/SubLevelEntityCollision.java`: interpolates last
  to logical pose during entity collision. Ordinary tracked-entity carrier
  handling belongs to that interval; hydration must not independently replay it.

Before this correction, living-body initialization preceded `super.tick`, while
passive owned physics ran in `MoonStation14.onServerTickPost`. A placement at
the latest pose followed by ordinary native interval consumption can start a
newly restored actor at the wrong phase. R47's numerical witness is indexed in
`RESTORED-BODY-COLLISION-PHASE.md`; it is not proof that all later drift has the
same cause.

## Correction boundaries

The correction places a pending living actor **once**, at
its first admitted ordinary owned movement entry, with the exact frame's native
interval-entry basis. Initialization cannot consume its anchor earlier and hope
that movement occurs later. Current-position entity-ticking admission, movement
ownership, prototype policy, and the per-body physical-attempt budget still
apply. Missing dependencies must leave the actor pending without physics or
velocity/gravity mutations. Incapacitated-body ownership must not deadlock behind
the entity-tick barrier. Pending bodies remain behind that barrier; the normal
grounded consumer initializes after admission and before constructing motor state.
The existing corpse consumer does the same, with fallback dispatch only bridging
pending incapacitated bodies whose normal entity-tick callback cannot yet run.
Already-initialized corpses retain their existing owner. Both use the existing
per-body attempt budget, not another motor or a second native move.

Do not apply an unconditional previous-pose substitution to loose cargo: its
same-tick support/loaded-sweep movement path differs and has passing bounded
reconstruction evidence. Reuse the existing codec, initialization state,
loaded-only frame readiness and native movement consumers. Gameplay ray queries
retain their strict current-pose semantics. No recovery placement, engine change,
carrier reset, additional physics attempt, or new collision tolerance is allowed.

## Continuity observation

Both OFFLINE baseline and reconnect live-point observations must use the existing
bounded LOWEST-priority server-post fixture sampler, after fallback physics.
Require physical-step admission for the sampled server tick, alongside the full
existing identity/payload/current-owner/grounded/ACTIVE checks. Admission is not
proof of successful native movement. Keep the live-point `.12` limit unchanged;
do not replace it with a remembered committed point.

R47's saved committed localY129 and first completed post-hydration localY129.153900
already differ by more than `.12`. Its failure must not be erased by correcting
the observation phase. Original OFFLINE current-query baseline, committed SAVE
point, hydrated placement and final current point remain separate witnesses.

## Validation status

No new native result or readiness submission is established by this document.
The owner retired legacy in-flight jobs/holds on 2026-10-20; R48 still has no
technical verdict. New intake requires completed owner-ready work, not a logging
checkpoint. M1 remains unaccepted and M2–M6 remain paused.
