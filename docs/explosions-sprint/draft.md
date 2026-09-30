# Explosions sprint — design draft

## Status and intent

**Status: DRAFT.** This document proposes a bounded, server-authoritative explosion system translated from SS14's 2D behavior into the project's 3D voxel world. It is a planning artifact only: it does not authorize implementation. Owner-approved direction is recorded below; remaining decisions block implementation scope/acceptance.

The goal is a system-heavy event that uses existing world, atmosphere, power, damage, puddle, and speech behavior where the contracts permit. It is not an item, combat, or player-equipment feature. No player interaction is needed for the first vertical slice.

## Evidence and source translation

The SS14 source baseline was inspected in pinned checkout `C:\Users\William\Documents\SS14-dev\space-station-14` at `c9df5ef5d6` (full commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`). Relevant source:

- `Content.Shared/Explosion/ExplosionPrototype.cs`: `damagePerIntensity`, `TileBreakChance(intensity)` block-break probability/intensity curves, `TileBreakRerollReduction`, and optional fire-stack/temperature effects. There is no universal tile-specific material-resistance scalar.
- `Content.Shared/Explosion/Components/ExplosiveComponent.cs`: source total intensity, slope, per-tile intensity cap, maximum breaks, and vacuum permission.
- `Content.Server/Explosion/EntitySystems/ExplosionSystem.TileFill.cs`, `ExplosionSystem.Processing.cs`, `ExplosionSystem.Airtight.cs`, and `ExplosionGridTileFlood.cs`: bounded intensity propagation through the 2D grid and space, accounting for airtight blockers and event budget. Floor tiles are handled separately: non-indestructible floors get a probability roll from effective intensity, successful peels can expose underlying tile history/layers up to `MaxTileBreak` while reducing intensity, and vacuum creation can be forbidden. Airtight wall/structure entities instead derive destruction tolerance from remaining Destructible/Damageable health and prototype damage types/modifiers; tolerance affects propagation through blocked directions. Entity damage resistance is another distinct mechanism (`GetExplosionResistanceEvent` / `ExplosionResistanceComponent`).
- `Resources/Prototypes/explosion.yml` and bounded SS14 CCVars are reference material for shape and tuning, not license to cargo-cult values.
- `Resources/Prototypes/Tiles/plating.yml` marks `FloorHullReinforced` indestructible, and `Resources/Prototypes/Tiles/planet.yml` marks planetary base/ice tiles indestructible. `ExplosionSystem.Processing.cs` checks `Indestructible` for floor tiles. Its vacuum-creation restriction is a distinct rule; these examples do not mean all SS14 entities are immune.

SS14 is a **2D** algorithm. This proposal does not claim parity: Minecraft cells have six face neighbors, vertical shafts/floors/ceilings, 3D distance and a different block/topology model. Preserve SS14's distinct target-class semantics where feasible rather than inventing one material-resistance value for all targets. Define and test a deterministic six-face adaptation; do not copy 2D flood ordering or reinterpret it as 3D without an explicit rule. Determinism here means seeded rolls and reproducible tests, not identical random-generator behavior to SS14.

Local anchors include `ms14/damage/DamageSystem.java` (`applyHealthChange`, typed blunt/heat), `ms14/atmos/world/AtmosphereService.java` (`topologyChanged`, default-off configuration and closure rules), `ms14/power/cable/CableStorage.java` (`removeHost`) and `ms14/power/graph/PowerGraphService.java` (`noteChanged`). Direct `setBlock` is not a reliable substitute for block lifecycle notifications. Puddle support flow uses `PuddleBlock.neighborChanged`; speech occlusion is evaluated at delivery by `ms14/chat/server/LocalSpeechOcclusion.java` and has no cache. The merged combustion plan, `docs/combustion-reactions-sprint/plan.md:31`, explicitly excludes explosions and block destruction; this proposal is a separate future scope, not an amendment to that plan.

## Proposed boundary

### In scope for a first sprint, subject to owner decisions

- A server-only, explicitly invoked explosion event with validated source parameters and a bounded 3D propagation plan.
- Typed damage to eligible server entities once per event, through `DamageSystem`; no player-specific handling.
- Destruction of eligible blocks using an explicit target-class policy informed by SS14's separate floor-tile and airtight-structure behavior, while preserving exceptional explicitly blast-immune unbreakables and world-protected blocks. Modded blocks are the first implementation focus; vanilla blocks are welcome for tests and future station use if feasible.
- Explicit follow-on notifications for atmosphere topology (only when enabled) and power graph host removal/invalidation.
- Integration evidence for puddle support loss and subsequent speech audibility, without implying that raw block mutation automatically triggers either behavior.

### Out of scope

No player/equipment/inventory behavior; no shrapnel, physics impulse, chain reactions, gas-fire ignition, fire-stack/temp effect, public gameplay trigger, admin public command, or new explosion VFX/audio. Do not copy SS14 sprites/audio; licensing/provenance requires a separate review. A visual effect, if desired, needs separate approval and its own design/asset gate. No claim that a functional blast outcome is itself a visual effect.

## Proposed contracts (not implementation instructions)

### Trigger and prototype

The first trigger should be a trusted, server-only GameTest fixture, unavailable to player clients. It must be possible to construct a source in a GameTest without adding a public command, usable item, or live gameplay explosive. Identify event source and coordinate in server-owned context; reject client-supplied authority, invalid coordinates, unloaded source chunks, or unknown prototype IDs. Public gameplay triggers remain excluded.

Represent tuning as immutable typed prototype data loaded by existing prototype infrastructure. Candidate fields, all requiring schema and range validation: stable `id`; total intensity/budget; distance falloff slope; maximum intensity per voxel; maximum affected voxels/work; damage per intensity and typed damage kind; floor-tile break probability/intensity curve and bounded reroll reduction. Do not imply these fields define a universal block material resistance. Optional fire-stack/temperature knobs in SS14 are deliberately excluded from this first contract. Avoid arbitrary executable behavior in data. Unknown IDs/fields, nonfinite values, negative budgets, inverted curves, and unsupported damage types fail closed at catalog resolution; do not substitute a dangerous default.

Translate source total intensity, falloff, cap and floor-break curve into explicit local units and a 3D rule. Define probability deterministically from event seed plus stable voxel coordinates (and a specified roll index), not thread order or ambient randomness. This is reproducible local sampling, not a claim to duplicate SS14's random generator. Repeated rerolls, if retained, must have a fixed capped count and the pinned reduction rule. One event must not obtain extra work or damage by being re-entered.

### Propagation and plan boundary

Use a pure deterministic kernel over an injected world sampler. It returns an immutable side-effect plan; it does not access Minecraft state, load chunks, mutate blocks, damage entities, or notify services. Propagate only through six face neighbors in a fixed documented order. At each candidate compute intensity from the pinned rule, apply the per-voxel cap, and stop when the budget/range rule says so. Prevent revisits from multiplying energy. Distinguish eligible/open paths, floor-tile break outcomes, airtight structure tolerance/blocking, vacuum/space policy, and unknown samples explicitly; their Minecraft mapping is a design decision, not a universal resistance scalar.

Never load chunks as a side effect of a blast or scan a radius without a hard work bound. Unloaded boundary cells stop propagation and are not queued for later phantom damage. Set hard per-event and per-tick inspection/plan caps before implementation; validate the complete bounded plan before applying any destructive effect. A cap/queue overflow must have a documented safe outcome chosen by the owner (preferred proposal: reject the event plan before side effects, report saturation, and do not silently truncate a potentially misleading partial blast). If caps are split across ticks, define immutable event ownership, ordering, expiry/cancel semantics and ensure no partial destruction occurs before validation. This is not a claim of atomicity across Minecraft world, entity, atmosphere and graph services.

### World application, ownership, and integrations

Explosions should be broadly destructive—nearly anything may be destroyed—with exceptional explicitly immune/unbreakable and world-protected/admission-denied targets. Player placement or provenance is not a reason for immunity. Do not model all outcomes as a single material-resistance scalar: SS14 floor tiles use intensity-based probabilistic peeling (except indestructible tiles), while airtight structures use health/damage-type-derived tolerance for propagation; the exact 3D Minecraft mapping remains to be decided. Vanilla blocks are welcome for tests and future station use; if supporting vanilla alongside modded blocks complicates the first implementation, focus first on modded blocks and defer vanilla. For modded station floors/walls, M0 must define whether each maps to layered floor-tile or structure/airtight semantics. Do not blindly equate vanilla Minecraft blast-resistance numbers with SS14 behavior; defer the exact vanilla adapter if parity is infeasible. No generic block-break API is established by current evidence: implementation must use a narrow mutation/admission hook that respects loaded chunks and world protection, and revalidate current state immediately before writes.

### Owner-approved direction

The blast target policy is broadly destructive, not restricted by mod ownership or placement provenance. It may destroy eligible modded blocks once the design gate is approved; it is not required to prove block ownership first. Outcomes use distinct target-class rules rather than a universal material-resistance scalar. There is no arbitrary universal admin bypass: preserve only the precise exceptional immune/unbreakable and world-protected/admission-denied cases agreed at M0. This draft still does not authorize implementation.

If approved targets are changed, use a narrow server-side mutation adapter that revalidates loaded state and target eligibility immediately before each write. Record actual old/new states and issue required downstream notifications; a previously prepared plan can become stale. Do not describe cross-system application as transactional or all-or-nothing unless the code can truly provide that guarantee. No writes occur for rejected/unloaded/protected targets.

Prioritize entity damage and power/atmosphere integration. Query entities only within bounded affected loaded cells; apply one typed damage outcome per eligible entity per event using the agreed intensity rule and `DamageSystem`, not an untyped direct health edit. Atmosphere block/topology changes must call the existing topology integration only when atmosphere is enabled; respect its ownership/closure rules and never assume direct `setBlock` emits an event. Power cable removal must clean the host through `CableStorage.removeHost` and invalidate via `PowerGraphService.noteChanged` as required, not mutate the cable block alone.

Two additional acceptance checks demonstrate downstream world behavior rather than claiming generic hooks: loss of a supported puddle block should exercise the existing neighbor support-flow behavior; speech delivered after a changed occluding wall should reflect `LocalSpeechOcclusion`'s delivery-time check. No cache invalidation claim is needed for that uncached check. These integrations are not permission to destruct arbitrary supports/walls; they require an approved target fixture.

Damage, mutation, power and atmos updates have distinct failure surfaces. Define whether a stale target is skipped and how that outcome is reported. Protect against duplicate entity application and duplicate notifications when an event is retried. Atmosphere is default-off: tests must cover both disabled and an explicitly enabled, permitted fixture. Any production acceptance requiring atmosphere must also settle permission to alter closure/topology state.

## Owner decisions required before implementation

1. Which exact blocks are exceptionally blast-immune, and how do Minecraft targets map to SS14-like floor peeling versus airtight structure tolerance? After M0 feasibility, decide whether an exact vanilla block adapter fits the first implementation or is deferred; do not assume vanilla blast-resistance numbers provide SS14 parity. Ownership/provenance is not an immunity criterion.
2. Which precise world-protection/admission hooks must mutation respect, and what exact destructive trigger (if any) is permitted beyond the trusted server-only GameTest fixture? Public gameplay triggers remain out of scope.
3. Approve 3D intensity/radius/falloff units, energy and work budgets, tick caps, deterministic break rule, and queue overflow behavior; require benchmark targets before tuning values.
4. Approve entity eligibility, typed damage kind/intensity curve, and whether damage is applied once per event independent of path/revisits.
5. May the test fixture enable atmosphere and alter its closure/topology? Confirm power-host cleanup and graph invalidation obligations.
6. Is puddle/speech integration acceptance required for first release? Are visual effects explicitly a later separate sprint?

Until resolved, choose the safe default: no public gameplay trigger, no implementation before the design gate, bounded fixture-only planning, no fire effects, and no assertion of SS14 parity. After approval, the default proof may destroy eligible modded blocks subject to agreed immunity and world-protection rules; do not impose a blanket no-destruction/provenance gate.

## Milestones and gates

**M0 — pin choices and safety.** Answer decisions above; record units, typed prototype schema, validation/fail-closed behavior, exceptional immune set, floor-tile versus airtight-structure mapping (including modded station floors/walls), world-protection/admission hooks, vanilla adapter feasibility, 3D propagation and deterministic sampling, caps/overflow, entity policy, and exact integration notifications. Map prototypes to existing infrastructure. No implementation before this design gate.

**M1 — pure geometry.** Implement/test only after approval: deterministic six-face propagation against a fake sampler; no world access. Cover symmetry/order, floor/ceiling/shaft/corner, thin versus thick barriers, distinct floor-peel probability and airtight-structure tolerance semantics, intensity caps/falloff, vacuum policy, unloaded boundaries, duplicate visits, finite energy/work budgets, cap saturation and seeded reproducible rolls. Demonstrate that the pure plan has no destructive effects.

**M2 — safe server application and damage.** Trusted server-only GameTest trigger, immutable validated plan, revalidation, eligible typed entity damage once, target-class destruction rules and immunity/protection enforcement, and no chunk loads. Exercise failed/unknown prototypes and stale/protected/unloaded targets. After M0 approval, prove destruction of eligible modded blocks; add vanilla only if feasibility permits. Preserve exceptional unbreakables and protected blocks.

**M3 — integrations in real loaded-world GameTests.** For eligible fixture blocks, prove atmosphere topology notification when enabled and no atmosphere writes when disabled; prove power cable host removal and graph invalidation; prove a supported puddle reacts to actual support loss and speech audibility changes on the next delivery after an eligible occluder change. Tests must observe actual world/service outcomes, not merely mock a callback or assert that `setBlock` caused implicit hooks.

**M4 — owner-connected acceptance and performance.** Review safe visual/outcome expectations, workload counters and worst-tick measurements in a representative loaded world before any production trigger. If no VFX is approved, state plainly that the blast outcome has no explosion visual presentation. Revisit saturation, fairness and event cancellation with owner before release.

## Minimum 3D verification matrix

| Case | Required observation |
|---|---|
| Flat floor and ceiling | Six-face propagation is spatially symmetric; vertical shielding is real, not a 2D projection. |
| Vertical shaft | Open loaded shaft transmits only according to the pinned 3D intensity rule. |
| Corner and thin/thick barriers | Fixed neighbor order and barrier costs produce repeatable, distinguishable outcomes. |
| Loaded chunk seam / unloaded chunk edge | Loaded seam agrees with uninterrupted geometry; unloaded boundary stops without loading or deferred phantom effects. |
| Duplicate entity / multiple reached voxels | Exactly one typed damage application per eligible entity and event. |
| Energy/work budget and queue saturation | No over-budget plan or partial destructive side effect; outcome follows owner-approved overflow rule. |
| Disabled atmosphere / enabled permitted level | Disabled service remains untouched; enabled fixture receives required topology update and expected real closure outcome. |
| Eligible vanilla block (if included) / protected or immune block | Eligible target follows its approved class rule (floor peel or structure tolerance); protected and explicitly immune blocks remain unchanged. |
| Eligible cable / puddle support / speech occluder | Real power cleanup, puddle support reaction, and subsequent delivery-time speech occlusion respectively; placement provenance is irrelevant. |

Tests should use real GameTest world outcomes for integration and focused pure tests for arithmetic/order. A compiled test fixture is not evidence that it ran in a server. Benchmark event inspection counts, mutation counts and worst-tick time against an idle baseline; assert no full-world scan, chunk loading, starvation or unbounded deferred work.

## Validation plan (commands only; not run for this draft)

After implementation, run the narrow relevant unit tests and GameTest compilation first, then the project checks:

```powershell
./gradlew.bat compileJava compileGameTestJava test --no-daemon
```

Run `runGameTestServer` separately with a unique isolated build directory so logs/results cannot be confused with concurrent runs. Inspect actual server logs and fixture outcomes, record failures without claiming success from compilation. Run `git diff --check` and inspect the complete diff, including untracked files.

**Draft limitation:** all algorithm parameters, target policy, resource limits and damage outcomes remain proposals pending owner decisions. This file is not an implementation plan authorization, parity claim, benchmark result, or test report.
