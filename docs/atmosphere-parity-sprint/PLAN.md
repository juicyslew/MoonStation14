# Atmospherics foundation parity plan

## Status and target

Implementation approved by the user on 2026-10-03: proceed autonomously, resolve uncertain decisions locally, and surface them afterward for judgement. The user committed the preceding work and is willing to revert this parity overhaul if necessary. Starting commit: `9c32799744627347cc1dac034a506518ae2f685e`.

Target SS14 revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, available at `C:\Users\William\Documents\SS14-dev\space-station-14`.

Prioritize room-gas and vacuum/space parity first. Return to hotspot/fire behavior afterward. Do not generalize explosive decompression to nonzero exterior atmospheres now. Test-fixture design and development protection mode are deferred by the user.

Orientation: `docs/atmosphere-incident-sprint/HANDOFF.md` and `GAS_PARITY_AUDIT.md`. The prior 1,821 JUnit / 73 focused GameTest gate proves local regression safety, **not broad SS14 parity**.

## Agreed decisions

- Keep canonical SS14 numbers in data/configuration; centralize their Minecraft unit conversions. One Minecraft cell is 1,000 L versus the upstream reference tile's 2,500 L.
- Reference-tile mole/energy quantities scale by 0.4: e.g. 0.01 mol becomes 0.004 mol; 143,000 J becomes 57,200 J. Temperature, pressure, ratios, per-mole quantities, numerical tolerances, and computational bounds require their own explicit treatment—not blanket multiplication.
- Use upstream temperature clamping and operation-specific microscopic-species cleanup. Prefer floats for simulated gas state/calculations; exact representation design remains part of the implementation plan.
- Adopt upstream cycle archives, heat sharing, group behavior, routing, boundary mechanics, reaction semantics, and staged resumable processing rather than maintaining locally invented substitutes.
- Retain necessary Minecraft adaptations: 3D six-face geometry, loaded-only world access, server-thread mutations, and explicit finite/exterior/unknown ownership. These adapters must not silently change gas equations.
- Wind/entity pressure movement is deferred. Pipes, pumps, scrubbers, and optional superconductivity are separate missing features, not prerequisites for this numerical foundation pass. Superconductivity is disabled by default upstream.

## Ordered work units

### 1. Canonical units and mixture contract

- Introduce explicit conversions for reference-tile inventory/energy, physical volume, pressure, per-mole heat capacity/heat, ratios, and numerical tolerances. Apply conversions consistently to constants and reaction prototype requirements.
- Retain the nine upstream gases and their molar heat capacities. Prefer prototype/configuration-backed values where practical rather than duplicating source numbers across kernels.
- Reconcile upstream `AtmosHeatScale` normalization with local energy APIs and external writers. Its default factor is 8 and normally cancels between gas heat capacity and reaction heat; it is not an automatic eightfold temperature error.
- Represent mixture volume/removal semantics sufficiently to reproduce upstream operations. Do not substitute a more physically intuitive detached-volume model without comparing upstream behavior.
- Move simulation numerics toward float parity. Reproduce the 2.7–262,144 K temperature clamp, `RemoveRatio` cleanup around 5e-8 mol, merge temperature/capacity gates, zero-gas behavior, and immutable-reservoir semantics.
- Conservation checks must account for explicit upstream clamping/cleanup rather than reject those rules as conservation bugs. Preserve safe, loaded-only, server-authoritative publication.

### 2. Atmosphere cycles and resumable stages

- Replace the two-Minecraft-tick/10 Hz cadence with upstream's nominal **15 Hz stage admissions/resumptions** on Minecraft's 20 TPS server. Direct source verification corrected the earlier assumption that 15 Hz meant completed cycles: default processing has nine stages (all except disabled superconductivity), so nominal completed cycles occur every 0.6 seconds.
- Preserve per-level processing state, stage queue/cursor, archived/processed cycle identity, and completed-cycle counter across yields.
- Use an elapsed-time soft budget corresponding to upstream's default 3 ms, with bounded checks. Resume unfinished work on subsequent server processing opportunities, not only when a new simulation step becomes due.
- Do not start overlapping cycles or advance the completed-cycle counter before the stage chain completes. Preserve timer debt across yields, admit at most one stage invocation per server callback, and spend the soft budget on resumable work within that stage. Retain default timing slots for absent pressure, pipe, and full-device stages as explicit no-ops rather than collapsing the cycle clock.
- Replace arbitrary ten-source/eight-reaction cadence assumptions with stage progression. Keep reasonable work safety bounds without altering upstream results through new partial-publication policies.
- Define how block changes, chunk lifecycle events, and external gas/energy writes invalidate or join in-progress work. Do not recreate blanket same-cycle sharing starvation.

### 3. LINDA and excited groups

- Archive each participating cell once per cycle. Preserve archives across resumptions; do not resnapshot modified inputs as if they were the same cycle's original state.
- Match live species flux, receiver-neighbor divider, transfer order, archived-temperature carried heat, archived-capacity conduction, and upstream conduction eligibility gates. A shared coefficient of 0.4 alone is not parity.
- Keep the correctly volume-scaled comparison thresholds distinct from the 5e-8 per-transfer cutoff.
- Match group formation/merging and sharing-driven cooldown resets: >10% of standard-cell moles resets both cooldowns; >0.1% resets dismantling only.
- Remove the local near-uniformity prerequisite for breakdown. At breakdown cooldown >4, homogenize the group using upstream merge/distribution semantics. At dismantle cooldown >16, retire group tracking/active cells without deleting gas or adding a final averaging operation.
- Remove the separate 12-Minecraft-tick group clock. Advance group cooldowns within atmosphere-cycle semantics. These are pass counters, not independent wall-clock timers. The old 0.6-second timer happened to match the source's nominal cycle duration; the earlier ninefold-slower claim was incorrect. Quiet breakdown nominally takes about three seconds, not one-third of a second.
- Preserve breakdown/dismantle precedence and upstream configuration defaults: excited groups enabled; space-all-consuming group averaging disabled.

### 4. Normal Monstermos routing

- Match neighbor-based activation rather than the local patch min/max trigger.
- Restore upstream breadth-first discovery: up to 2,000 discovered tiles, with the first 200 included in normal averaging/transfer. Extra discovery can identify space and select depressurization.
- Implement the conditional sorted fast redistribution pass, then giver-rooted or taker-rooted BFS according to which side has fewer members.
- Record edge transfers and finalize them in upstream order, including reciprocal bookkeeping and insufficient-inventory handling. Replace always-donor BFS/immediate packet routing where it changes behavior.
- Replace the local 800-cell mean and independent 8,000-expansion/8,000-transfer partial-publication policy with upstream bounds and stage semantics. Both systems use BFS; BFS itself was never the mismatch.
- Preserve normal total-mole equalization, not a newly invented pressure-driven finite-room fluid solver. Transfer route/order affects species and heat even when final total-mole averages agree.

### 5. World adapters and exterior/vacuum boundaries

- Keep finite/exterior ownership, reservoir gas profile, and `Space` classification conceptually separate. Empty finite gas is not automatically space; external finite gas is not automatically vacuum.
- Retain Minecraft's loaded-only enclosure/revalidation adapter rather than copying SS14's 2D floor identity literally. Specify airtight adjacency and gas-state transitions on opening/closing/building blocks, proof invalidation, and newly classified cells.
- Ordinary non-space exterior air participates in normal Monstermos averaging/routing as an immutable endpoint, and in LINDA species/heat sharing. This corrects the earlier incomplete LINDA-only description. Remove custom pressure-equilibrium bisection, half-relaxation, packet caps, and the separate 0.125 boundary policy where they replace upstream mechanics.
- Vacuum/space depressurization must reproduce upstream discovery/progression, reverse transfer order, `ReleaseGasTo` pressure-target behavior, depletion, and artificial cooling.
- Remove custom 15%-inventory packet routing, transit retention, residue clearing, and flow rules where they differ from upstream. Some source constants have unit quirks: a mole-derived quantity becomes a pressure target. Document and translate behavior deliberately; do not silently repair it or magnify it through inconsistent cell-volume conversion.
- Keep diagnostic export accounting where it observes rather than changes simulation. Wind/entity effects are deferred.
- Do not promise instantaneous decompression from high pressure. The explosive branch is selected by connection to `Space`, not an arbitrary finite-room pressure gradient. Nonzero-exterior explosive discharge is a future explicit extension, not this pass.

### 6. Chemistry foundation, then fire

- Restore one shared priority-ordered reaction pass instead of whole-mixture non-fire chemistry followed by a separate fire pass. Match upstream start-of-pass temperature/energy gates and effect ordering; remove local tie rules where they cause different outcomes.
- Match ammonia/frezon-production temperature handling: upstream changes species while retaining temperature, whereas local constant-energy recomputation adds cooling. N₂O's current capacity balance means that same representation choice does not necessarily change its temperature.
- Match coolant effect ordering and temperature/capacity handling. The local negative-energy safeguard is normally redundant under stock frezon constants; it is not an established ordinary-gameplay defect. The ammonia oxygen clamp is likewise redundant for valid mixtures.
- Convert fixed inventory/energy gates consistently while preserving per-mole reaction energy and stoichiometry. Tritium consumes 0.5 mol O₂ per mole burned.
- **On the reviewed foundation**, restore hotspot exposure metadata, ×25 seed-volume behavior, assigned reaction-result growth, first-processing/bypass semantics, and upstream reaction partition/merge behavior. Fire uses a persistent last-invoked-effect result slot, not a sum of successful reaction events. Whole-air LINDA reactions precede the hotspot stage; non-bypass portions run the full shared catalog again, while bypass reuses the whole-air result without another evaluation. Remove invented 10% seeds, additive growth, source heat-affordability restrictions, and uniform-composition projections where they replace source behavior.
- Preserve grenade integration, deferred scheduling, and visual/server bridges through source-faithful lifecycle semantics rather than keeping an incompatible fractional model solely because its local tests pass.

## Acceptance and deferred work

Each unit must be compared with the pinned upstream behavior and documented volume conversion, not merely pass existing locally authored expectations. Detailed reference fixtures and validation scheduling will be planned later as requested. Do not label a unit complete without evidence, and keep numerical, Minecraft integration, and live-station acceptance distinct.

Deferred: generalized ambient explosive decompression; wind/entity movement; test fixtures/dev protection planning; pipe networks and full SS14 devices; optional superconductivity. The 20 mol/s / 202.65 kPa producer remains a test device, not a universal gas-input limit or a claimed SS14 miner port. Hotspot implementation is now part of the active validation scope; it is not implicitly accepted by standalone foundation review.

## Resume instructions

Read this plan and `GAS_PARITY_AUDIT.md` before further investigation. Implementation is approved; use the current session/worker reports for active assignments and avoid overlapping edits. The preceding fixes are committed at the starting commit. Preserve their unrelated explosion/chat/lung changes. User requests autonomous completion and a report of uncertain decisions afterward.
