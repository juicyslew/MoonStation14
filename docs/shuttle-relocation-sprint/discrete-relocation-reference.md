# Discrete region relocation — superseded reference

**Superseded 2026-10-10. Historical alternative, not the active assignment.** The owner now wants Sable feasibility evaluated first, physical flight around the moon map, and a gated FTL path primarily for dimension travel. Read [Instructions.md](Instructions.md) and [HANDOFF.md](HANDOFF.md). Do not implement this older region-copy architecture, its entity-clearance veto, or its no-flight/no-cross-dimension scope by following this reference. Returning to it requires an explicit owner decision and a revised plan. Its research links remain useful, not implementation acceptance.

## Status and delegation

**2026-10-09: implementation plan, not delivered gameplay.** The owner requested this package for a different agent working in a worktree. The authoring session is documentation-only and must not finish the implementation. Start with [HANDOFF.md](HANDOFF.md), then execute this plan when assigned by the owner. Do not mistake research or historical test results for implementation acceptance.

The assignment is a **playable, reusable shuttle vertical slice**, not an evacuation/antagonist/cargo-content sprint. Follow the ordered gates below; continue autonomously within the specified architecture. Escalate only material departures, infeasibility, destructive changes, or an unresolved gameplay choice that local/upstream evidence cannot settle.

### Worktree bootstrap

1. Confirm the worktree's branch/base revision, `AGENTS.md`, `gradle.properties`, and worktree status. Preserve unrelated edits. The researched target is Minecraft **1.21.1**, NeoForge **21.1.224**, Java **21**; recheck rather than silently upgrading.
2. Ensure this directory exists in the worktree. **An ordinary worktree created from HEAD does not inherit uncommitted documentation.** Copy these two documents into it, or include them through the owner's normal documentation-commit workflow. No staging/commit/push is requested by this package.
3. Read the handoff indexes below, then their referenced source only as needed. Successor handoffs/source override historical descriptions. In particular, retired atmosphere/power gates and legacy movement protocols must not be restored.
4. Use the worktree as the implementation/validation working directory. Reuse existing language services; do not launch duplicate LSP backends. Do not run competing server/client validation processes against this machine.
5. Record a current test baseline before broad edits. Existing handoffs contain unresolved full-suite failures; neither assume a green baseline nor label new failures unrelated without evidence.

## 1. Goal, scope, and invariants

**One real, destructible ship at a berth → that live ship at an offsite transit location → that live ship at another berth.** All locations are initially in the same dimension, with the same orientation. The ship consists of ordinary world blocks, block entities, real loose entities, and existing character bodies. During travel phases, gameplay continues except for a bounded relocation commit window.

Required delivered slice:

- An authored small shuttle, two prepared berths, and one prepared transit site; safe fixture construction and repeatable departure/arrival/return commands.
- A normal body-authorized controller interaction for selecting an eligible authored destination and seeing phase/status/rejection feedback. Operator commands are diagnostics, not the sole playable interface.
- Resolved typed craft/controller/route data, stable craft identity/location, server-owned phase transitions, normal save/restart continuity, and one reusable relocation path used at every leg.
- Arbitrary hull damage/repair and entry through windows, walls, floor, roof, or the normal hatch while docked. No special breached-cell portals or second authoritative interior.
- Preservation of the supported live machinery, gas, cables/pipes, items/storage, bodies/corpses, and control state listed below. Unknown unsupported state is rejected explicitly before departure rather than silently lost.

Hard invariants:

1. Transfer **current state**, including missing hull blocks; never respawn a pristine ship template at arrival. Fixture initialization is explicit and cannot reseed a damaged/revisited craft.
2. **A breached or non-airtight ship may depart.** Pressure loss and injury remain real gameplay. Do not replace vacuum with fresh oxygen or introduce invisible invulnerable hulls.
3. Same-dimension entity transfer preserves entity UUIDs, character/Mind identity, possessions, and existing inventory ownership. The carrier is not the physical passenger. Never serialize/recreate bodies or loose items to simplify relocation.
4. No live duplicate machinery/items/gas owners at source and destination. Serialized staging data is not another gameplay owner. Any staged world objects must be demonstrably inactive/non-owning until publication.
5. Source and destination update through existing owning services. No second atmosphere, power, character movement, hands, timed-interaction, or device-UI implementation.
6. Destination readiness and live source membership are revalidated at the mutation cutoff, not only when the trip was requested. Requests are bounded and server-authoritative; client craft IDs, positions, and phase predictions are not authority.

### Explicit non-goals

No continuous flight, steering physics, ramming, arbitrary landing, rotation, arbitrary player-built ships/enlargement, cross-dimension control transfer, generic Create/contraption engine, portal rendering, mod compatibility framework, save migrations, extensive anti-cheat, or crash-atomic distributed transaction framework. No production evacuation round-ending, escape-pod catalog, cargo purchasing/selling, automatic sale on arrival, antagonist roles, mobile-core rules, terrain generation, or moving caves. Caves remain physically contiguous lunar terrain.

Do not add AE2, WarpDrive, or Valkyrien Skies as a dependency by default. Study/reuse suitable code only within its license; direct copying requires verified distribution obligations and attribution. An architectural reference does not justify copying substantial code or introducing another owning service.

## 2. Established upstream behavior and external precedents

### SS14 parity anchor

Verify `C:\Users\William\Documents\SS14-dev\space-station-14` at **`c9df5ef5d675b0d1d226828bddf6b78c28502d91`** before relying on it. If unavailable, mark mapping unverified and do not claim parity.

- SS14 moves the existing shuttle **grid entity** between maps; it does not copy a block region or rebuild its contents. `ShuttleSystem.OnGridInit` adds shuttle composition to initialized grids. `LeaveNoFTLBehind` retains explicit `NoFTL` entities at the old transform. Robust's component lookup filters `TransformComponent.GridUid`, not AABB overlap.
- Ordinary FTL has Starting → Travelling → Arriving → Cooldown phases. An intact hull is not an eligibility requirement. Docking joins separate grids/ports; it does not merge station and ship tiles. Pressure behavior is a separate door/atmosphere concern.
- Console chain: `ComputerShuttle → BaseComputerShuttle → BaseComputer → BaseStructureComputer → BaseStructure`. Sources: `Resources/Prototypes/Entities/Structures/Machines/Computers/{computers,base_structurecomputers,frame}.yml` and `Entities/Structures/base_structure.yml`.
- Port chain: `AirlockShuttle → AirlockRCDResistant → Airlock → BaseStructure`. Sources: `Resources/Prototypes/Entities/Structures/Doors/Airlocks/{shuttle,base_structureairlocks}.yml`. Docking, signal control, fixtures, directional airtightness, and door fields override inherited airlock behavior. Do not assume an ordinary local airlock already implements docking.
- Consumers: `Content.Server/Shuttles/Systems/{ShuttleSystem.cs,ShuttleSystem.FasterThanLight.cs,ShuttleConsoleSystem.FTL.cs,DockingSystem.cs,DockingSystem.Shuttle.cs}`, `Content.Server/Doors/Systems/FirelockSystem.cs`, and `RobustToolbox/Robust.Shared/GameObjects/Systems/EntityLookupSystem.ComponentQueries.cs`.

Verified **server configuration defaults**, not entity-prototype defaults, in `Content.Shared/CCVar/CCVars.Shuttle.cs`:

| Setting | Pinned default |
| --- | --- |
| Startup | 5.5 seconds |
| Travel | 20 seconds |
| Arrival | 5 seconds |
| Cooldown | 60 seconds |
| Arrivals-specific cooldown | 10 seconds; do not apply to all ships |
| Mass limit | 300 physics mass units; not a Minecraft block-count limit |
| Hyperspace knockdown | 5 seconds; audit application/eligibility before porting |

Use ordinary FTL phase timing as the initial local server-config baseline; retain tested route overrides where needed, never test-only shortened production defaults. Map the actual configuration consumers. Audit existing knockdown/status capabilities and upstream exemptions before deciding the supported slice; no speculative buckling framework or invented conversion from physics mass to blocks.

The chains and relevant behavior above were researched, **not an exhaustive inherited-component/default/consumer audit**. M0 must finish the repository-required resolved-composition mapping for implemented controller/port behavior. Record unsupported inherited behavior explicitly instead of adding nonfunctional components.

### Relevant mods: evidence, not performance certification

1. **AE2 Spatial IO, Minecraft 1.21.1**, audited branch head `db17504a86128fdf3dae31f5fb7a112a646e0b93`: [guide](https://guide.appliedenergistics.org/1.21.1/ae2-mechanics/spatial-io), [SpatialStorageHelper](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db17504a86128fdf3dae31f5fb7a112a646e0b93/src/main/java/appeng/spatial/SpatialStorageHelper.java), [CachedPlane](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db17504a86128fdf3dae31f5fb7a112a646e0b93/src/main/java/appeng/spatial/CachedPlane.java). Actual bounded region swap, block-entity movement strategies, entities/players, scheduled **block** ticks, POIs, lighting and chunk packets. Bulk edits use per-cell `LevelChunkSection` operations, **not constant-time whole-chunk swapping**. Unsupported/blacklisted blocks and custom chunk/SavedData ownership need separate handling. Source identifies LGPL-3.0-or-later; it is not project-MIT code.
2. **WarpDrive, Minecraft 1.12.2**, commit `7a3f93859f3a9dfa46d638aadfef012a09898b43`: [JumpSequencer](https://github.com/LemADEC/WarpDrive/blob/7a3f93859f3a9dfa46d638aadfef012a09898b43/src/main/java/cr0s/warpdrive/event/JumpSequencer.java), [JumpBlock](https://github.com/LemADEC/WarpDrive/blob/7a3f93859f3a9dfa46d638aadfef012a09898b43/src/main/java/cr0s/warpdrive/data/JumpBlock.java). Staged source/target preparation, capture, transformed placement, external records, entities and source cleanup. Configurable blocks-per-tick bounds deployment/removal passes; some other work is not similarly bounded. Old Forge chunk-ticket/tile-entity APIs are not 1.21.1 recipes. License was **not verified**: do not copy code until resolved.
3. **Valkyrien Skies** [shipyard architecture](https://docs.valkyrienskies.org/the-shipyard): persistent ship chunks projected through world transforms, with interaction/collision infrastructure. This is not a hollow shell plus hatch teleporter. It requires coordinate-query integration and a much larger framework. Official repository describes 1.20.1; unofficial 1.21.1 NeoForge listings are not a verified artifact for this project. Framework adoption requires an owner-approved architectural change.
4. [JumpDrive](https://github.com/mt-mods/jumpdrive), head `7799ffa62d6ad93aa666ad35d4eb76f51f6d2a01`, is **Luanti/Minetest, not Minecraft**. Its separate map/metadata/timer/object/player transfer concerns are a useful checklist, not compatible implementation evidence.

No reproducible benchmark for MoonStation's workload was established by this research. Timing instrumentation, batching, mod existence, and advertised ship sizes do not certify smooth 20–100-player operation.

## 3. Reuse map and required orientation

Java paths below are relative to `src/main/java/com/juicyslew/moonstation14/`. These are starting points, not an assertion that each has a ready-made relocation API.

| Owner / handoff to read first | Reuse or extend; audit before adding logic |
| --- | --- |
| Prototype composition | `ms14/prototype/{PrototypeRuntime,PrototypeManager,PrototypeResolver}`; existing typed airlock, disposal and ventilation catalogs, inherited definitions, validation and sync. |
| Bodies / current movement | `docs/disposals-pneumatic-sprint/HANDOFF.md`, `docs/airlocks-sprint/HANDOFF.md`, then lifecycle/body-control handoffs. `ms14/player_body_control/`, `BodyControlRegistry`, `character/GroundedHarnessWorldStep`, `client/GhostControlClient`, `movement/ExternalBodyFlight`; verify the **current** owner rather than reviving old experimental movement. |
| Containment | `ms14/containment/BodyContainmentService`; disposal holder transport preserves existing body identity and supplies camera-transition precedent, not whole-craft transfer. |
| Loose items / hands / storage | `docs/loose-world-objects-foundation-sprint/HANDOFF.md`, `docs/hands-inventory-program/HANDOFF.md`; `entities/LooseItemEntity`, `entities/LooseClientPositionInterpolation`, `ms14/hands/live/{LiveHands,HandActorAuthority,LiveHandsCommitGateway}`, body-hand/world transfer and storage ownership validation. Verify exact class locations. |
| Pulling / active operations | `ms14/loose_objects/pulling/BodyPullService`; `docs/timed-interactions-ingestion-sprint/HANDOFF.md`, `ms14/timed_interaction/`. Reuse existing release/cancellation and completion validation, never clear reservations by editing hand data directly. |
| Atmosphere / combustion / visuals | `docs/atmospherics-temperature-sprint/HANDOFF.md` as index, then `docs/pressure-transport-sprint/HANDOFF.md`, incident/combustion successors. `ms14/atmos/world/{AtmosphereService,AtmosphereChunkData,AtmosphereTopology,AtmosphereEventHooks}`, `ms14/atmos/visual/network/AtmosphereVisualServerHooks`. Preserve accepted float semantics, pressure transport, 15 Hz admissions and conservation. |
| Cables / power / sync | `docs/power-sprint/HANDOFF.md`, APC successors; `ms14/power/cable/{CableChunkData,CableStorage}`, `graph/PowerGraphService`, `runtime/PowerRuntime`, `cable/network/{CableVisualServerHooks,CableVisualClientCache}`. |
| Pipes / gas owners / linked devices | `docs/powered-ventilation-sprint/HANDOFF.md`; `ms14/atmos/piping/`, `PipeGasInventorySavedData`, `LoadedPipeGasOwner`, `VisualPipeBlockEntity`, alarm links and `mixin/LevelChunkSealedWallHostMixin`. Gas lanes have separate UUID-owned SavedData, not just BE NBT. |
| Doors / console UI / action authority | `docs/airlocks-sprint/HANDOFF.md`, `docs/machine-ui-visual-kit-sprint/HANDOFF.md`; `ms14/doors/`, existing machine menus/snapshots, `BodyHandRequestService`, shared `StoragePopupScreen`, `CharacterControlSystem`. Retain E/world-use conventions and body-eye admission. |
| Fixtures / test registration | Disposal and ventilation fixture builders, existing real-world GameTests, `docs/gametest-tags.md`, mod/service `register` hooks in `MoonStation14.java`. Extend proven helpers rather than new test infrastructure. |

Each implementation report must name reused/extended paths and justify remaining substantial overlap. Review must reject avoidable duplicate solvers, authority gates, world movers, inventory transfer implementations, UI frameworks, and parallel prototype catalogs for the same behavior.

**Current movement clarification:** `GroundedHarnessWorldStep.step` uses native `Entity.move` collision and observer paths, alongside containment and `ExternalBodyFlight`. Lifecycle's historical default-off effects are not a statement that every body-movement route is disabled. No general same-body teleport/prediction/camera reset API was found. `GhostPredictionHistory.reset` is session-oriented, and containment's pinning teleport is not that API. Extend the current character/control owner for this discontinuity; do not copy offer/ready/commit possession or reactivate a retired movement protocol. Rebase or cleanly cancel transient flight bookkeeping without applying old-site positions after the move, while explicitly accounting for legitimate carried velocity.

## 4. Architecture and transport contract

### Data and runtime responsibilities

Use existing resolved typed prototypes for supported controller/craft behavior and authored route/berth metadata. Keep static definition data separate from runtime craft identity, current origin, phase, chosen route, deadlines and revision. World-coordinate placement belongs to authored site/instance data, not hardcoded controller logic. Do not impersonate SS14 grid components the local runtime cannot support.

Prefer a small feature-specific relocation coordinator with explicit participation from existing state owners. A shared participant seam is justified by atmosphere, cables and pipe ownership; it is not permission for a universal plugin/compatibility framework. Owners retain their serialization, identity, topology, mutation and synchronization responsibilities. Departure, arrival, return and retry all use the same path.

### Authored boundaries and occupants

- A fixed **block capture volume** contains ship blocks and interior air, not terminal, landing pad, terrain or dock bridge blocks. Ordinary block additions inside it become craft state; expansion outside it is unsupported and must be visible/documented. Define a hard measured size limit and validate dimensions/coordinate arithmetic.
- Give the authored craft a separate explicit **entity boarding volume**, including a limited roof/exterior fringe for stowaways. Default membership is each eligible world root's authoritative feet/origin inside a half-open volume at commit. This is a deliberate Minecraft adaptation, **not SS14 grid membership**. Never use vague proximity or airtight-enclosure membership. Show/test the boundary in fixtures.
- Entities partly across the boundary follow that root-membership rule; avoid silently displacing or collecting nearby dock users. Root outside stays; root inside travels. Test exact-edge ties, tall/prone bodies, roof riders, wall/floor holes, negative coordinates and chunk seams. Final destination must not overlap unrelated bodies/objects in required clearance.
- Select physical roots only. Contained child inventories travel with their owner and are not enumerated as new loose objects. Linked carriers, contained bodies/holders and passengers need owner-driven handling, not independently applied offsets.
- Explicit nontravelling eligibility is analogous to `NoFTL`; do not exclude dead/unconscious characters merely because they cannot pilot. Implement only supported entity categories. Fixtures must cover a corpse with inventory.
- Keep a pull when both endpoints travel and the current service can prove continuity; otherwise stop it through the shared service. Cross-boundary pulls must release cleanly. Do not move the target twice, duplicate reserved-hand state, or leave a remote tether behind.

### Preparation, cutoff, and publication

Preparation must validate site bounds, chunk availability, overlap, destination clearance, supported blocks/BEs/entities/owners, and runtime authority without consuming state. Prepared sites are generated beforehand; never generate terrain during the commit. Existing services remain loaded-only: any explicit temporary loading policy belongs to this transport boundary, is bounded/released, and requires evidence rather than global force-loading changes.

Allow ordinary damage, boarding and sabotage during countdown. At relocation cutoff, briefly quiesce only the affected craft/site work: body movement inputs, local mutation/action commits, machine/gas transfers and cross-boundary graph work. Capture the **then-current** state. Revalidate optimistic preparation, publish one location/ownership transition, reset affected caches/prediction, then resume. No whole-server gameplay freeze, permanent security lockdown, or invisible hull invulnerability.

Start with the smallest measured commit implementation. Batch preparation or expensive phases only where identities and isolation remain correct. If relocation spans ticks, prove complete quiescence and absence of live duplicated source/destination owners; a blocks-per-tick counter alone is insufficient. Avoid broad raw chunk edits until their lifecycle, heightmap/lighting/tick/update responsibilities and service bypasses are established.

### Failure and persistence boundaries

Before mutation, invalid requests/blocked or unavailable sites leave the live craft and inventories unchanged. If destination becomes unavailable after departure, hold the live craft at transit with explicit status and a bounded retry path; never regenerate at either berth or discard passengers. Return/fallback may use an eligible clear site through the same service, not a second mover.

Handle tested normal runtime rejection/failure through preflight and small justified compensation. Report irrecoverable cases explicitly with retained diagnostics; do not claim crash atomicity. Normal save/restart must preserve stable craft identity/location, hull/device/owner state, and phase/deadlines outside the commit window. Define controlled shutdown during commit; broad power-loss recovery is not required. Suppress fixture reseeding and automatic duplicate craft creation after reload.

## 5. State preservation checklist

| State | Required treatment and evidence |
| --- | --- |
| Blocks and BEs | Current blockstates, orientation, missing hull cells, inventories, machine settings, identities, timers and NBT/components; correctly unregister/re-register tickers and owners. Test two-cell airlock controller/upper structure, damaged state and powered machine. |
| Scheduled/world updates | Audit pending block **and fluid** ticks, lighting, heightmaps, POIs and neighbor/redstone updates. AE2's verified block-tick handling does not establish fluid-tick coverage. No orphan callbacks at the old position. |
| Finite room atmosphere | Move owned cell claims, species, thermal energy/temperature and relevant reaction/hotspot state through the atmosphere owner; invalidate queued identities/topology at both sites. A claimed vacuum cell remains claimed vacuum. Missing records are not fresh air. Boundary mixing/leaks resume through ordinary simulation. |
| Cables and power | Translate only region-owned sparse records; preserve cable faces/tiers, machine storage and identity, sever dock-spanning connections, invalidate/rebuild loaded graphs and paid-interval grants. No invisible old-site network, free power or transfer-time double payment. |
| Pipe gas / concealed hosts | Preserve lane UUID/gas, host incarnation and joins through their SavedData owners. Do not use player-destruction spill/archive paths as relocation. Internal device links remap with stable device IDs; external links follow existing identity validation, not nearest replacement devices. |
| Disposal and containment | Include supported unit air, holders, transport SavedData and contained bodies through their owners. If mid-route disposal/containment cannot safely move, preflight refuses that state with actionable feedback; do not teleport the body while leaving its holder behind. No requirement to create a new disposal feature. |
| Loose items / containers | Same entities, stack components, storage tokens/content, relative position, velocity and gameplay timers; no pickup/drop/merge/reseed to perform travel. No duplicated container children. Client correction must reset old/current interpolation for distant moves. |
| Bodies / control | Same body/Mind/lease, health/posture/corpse state, hands/equipment/storage, relative position, yaw and velocity. Preserve legitimate motion while avoiding artificial fall damage, impulse or interpolation from the jump distance. Reconcile controller/carrier/camera/chunk tracking and stale inputs through the current owner. |
| Actions and UI | World-targeted operations/menus must revalidate moved identities/reach; cancel unsupported crossing operations through shared services. Held self-actions may continue only if existing validators support continuity. Close/requery stale machine sessions with feedback; do not forcibly close unrelated HUD/chat/storage. |
| Client caches | Clear old-site cable/gas/device presentation and publish new-site state; test a viewer remaining at source and a viewer arriving later. Geometry, inventory and phase snapshots remain bounded and identity/revision-qualified. |

M0 must inventory every position-keyed owner present in the authored fixture, including chemistry/explosion timers if carried by selected entities/machines. Classify each as supported, deliberately absent, or explicitly rejected. An unknown record must not be silently dropped. Support the listed minimal fixture; do not implement arbitrary third-party machinery or all future block types.

## 6. Ordered milestones and acceptance evidence

### M0 — Audit, contract, baseline, feasibility gate

Finish the pinned full parent-chain/component/default/consumer audit and local typed mapping; distinguish config defaults, supported inherited behavior, and adaptations. Verify current native body-control ownership and actual lifecycle/config gates in source. Complete the state-owner inventory and reuse map. Choose authored bounds, supported state set, cutoff/isolation strategy, persisted phase policy, clearance/boarding rules and provisional performance budgets.

Create a repeatable small fixture plan using existing builders: two berths plus offsite vacuum transit, hull/window/hatch, roof boarding fringe, real self-powered machine/cable network, finite non-default room and pipe gas, a filled container with children, loose items, controlled body, and corpse. Use current live power/atmosphere setup, not retired opt-in switches. Baseline it before shuttle edits.

**Exit:** written `PARITY.md` and short architecture/state matrix; prototype consumption and current control owner established; measured baseline and all unsupported states visible. If identity-preserving body/camera relocation or required owner publication cannot be made safe within this architecture, stop with concrete evidence/options before building more gameplay.

### M1 — Resolved definitions and server travel model

Add typed inherited definitions through existing loaders, plus stable authored craft/site/route identities and server travel state. Use SS14-derived ordinary timing config and tested overrides. Validate bad/unready prototypes, abstract/inherited defaults, missing routes, dimensions, overlaps, duplicate craft admission, stale requests, repeated requests, cooldown, and target eligibility. Persist stable state/deadlines through normal reload without placing a fresh hull.

**Exit:** pure/model tests plus a live consumer test prove inherited fields affect actual admission/timing/bounds. No hardcoded-only behavior or client-owned phase transitions.

### M2 — Bounded world relocation and BE lifecycle

Implement the single block/BE relocation path between prepared, unrotated sites. Preserve current holes/additions and machine state; address scheduled ticks, lighting, heightmaps, POIs, neighbor updates, chunk readiness and site clearance. Exercise failed preflight and measured commit/isolation. Do not activate unsupported region contents.

**Exit:** real-world fixtures move intact and breached hulls across positive/negative coordinates and chunk seams, move a damaged/configured BE and two-cell airlock, clear the source, preserve relative coordinates, and refuse blocked/unready targets without loss/duplication. Return repeats the same path, not a template restore.

### M3 — Existing state-owner participation

Extend atmosphere, cable/power, pipe gas and required device/link/containment owners for source removal, destination publication and cache/topology refresh. Preserve inventories, vacuum claims and finite gas/energy. Invalidate stale queued work, old grants/graphs and position-based references. Physical dock power/pipe/air connections disconnect/reconnect only through actual new topology.

**Exit:** closed transfer fixtures assert species/thermal conservation within existing numerical tolerances, lane/cable identity continuity, no old-site ghosts, no free or duplicate power, no stale grants, and continued paid machine work. Separate breached/open-dock fixtures account for real exchanges and prove ordinary pressure/leak behavior rather than demanding sealed-room conservation during exposure.

### M4 — Existing entities, controlled bodies and interactions

Move existing world roots and owner-linked passengers once; integrate current movement/control services for the discontinuity barrier and authoritative client reconciliation. Cover the contained-state policy, corpses, loose/storage tokens, internal/cross-boundary pulls, timers, menus/actions and observer views. Keep legitimate body authority; no fresh possess/reconnect or new Mind just to travel.

**Exit:** GameTests preserve all supported identities/inventories/state through repeated legs, boundary cases and cancellation. A connected single-client check demonstrates continuous possession/camera, no source snapback/stale-input replay, normal movement/actions after arrival, and proper tracking/visual resets. Headless body tests alone cannot close the connected gate.

### M5 — Playable route, controller, docking and SS14 presentation

Wire ordinary body-authorized E interaction and existing machine UI primitives to authored route selection, departure/status/cooldown and clear failure/retry feedback. Validate console identity/revision, actual body reach/LOS/status, power where supported composition requires it, and destination eligibility. A non-pilot may still ride. Controller destruction or loss of its power must not delete an already departed craft; resolve upstream behavior before adding cancellation rules.

Implement one physically contiguous berth opening using the existing airlock owner; reserve station/dock geometry outside ship capture. Arbitrary breaches remain ordinary world holes throughout. During transit the live ship remains damageable and playable; a breach exposes it to the site's real vacuum/environment. Verify boarding cutoff, roof rider and arrival clearance with the full state model.

Prioritize SS14 console/shuttle art and the actual hyperspace sounds over invented substitutes. Startup/arrival references are `/Audio/Effects/Shuttle/hyperspace_begin.ogg` and `hyperspace_end.ogg`, system volume `-5f`; do not treat this as a Minecraft gain value without conversion. Pinned `Resources/Audio/Effects/Shuttle/attributions.yml` attributes these to /tg/station, **CC-BY-SA-3.0**, source hyperspace directory at `71a79f1f75902d2ccab27cbffb971b12b7ab042d`. Verify files/metadata and retain adjacent attribution/license. Record genuinely unclear assets in `docs/audio-unknown-provenance.md`. Do not invent new directional cues for sounds already directional.

**Exit:** ordinary controller-driven berth → transit → destination → return works with damage and loose contents; transit remains playable; imported assets have provenance; no command-only feature, fake second interior, full radar/piloting rewrite, or unrelated HUD/input regressions.

### M6 — Performance, failure/regression review and implementation handoff

Repeat representative measurements and test normal stop/reload, destination loss after countdown/departure, duplicate requests, controller loss, chunk unavailability and bounded site retry. Independent review checks parity/adaptations, live prototype consumers, state ownership, mandatory reuse, networking/control and accidental service bypasses. Run broad regression validation once at the integrated gate; do not make each worker run the full suite.

**Exit:** acceptance matrix below links each criterion to evidence; full-suite status/failures are honestly reported, performance scope is explicit, disposable successful worlds removed, concise manual check provided, and this planning handoff updated to actual implementation state. Only owner closure permits calling the sprint complete.

## 7. Validation and measurement contract

Add `@GameTestTags` with a new `shuttle` selector; keep relevant cross-domain tags on the tests actually exercising those domains. Follow explicit template namespaces and registration rules in `docs/gametest-tags.md`. Suggested commands, from the worktree:

```powershell
./gradlew test compileGameTestJava assemble
./gradlew runGameTestServer "-Pms14GameTestTags=shuttle" -Pms14GameTestDir=build/gametest-shuttle-fixture
# Integrated gate: broad service ownership changes require the full suite.
./gradlew runGameTestServer -Pms14GameTestDir=build/gametest-shuttle-fixture
```

Tags select tests, not isolation/order; use a separate directory, never owner configuration. Reuse the designated fixture directory where safe, cleaning successful disposable worlds between incompatible runs. Preserve useful failures and logs/XML. Report filtered/full, selected/total, pass/fail/incomplete counts and comparison with the baseline. Add service-focused selections as appropriate; do not weaken existing tests or absorb unrelated failures to make a green report.

Performance evidence must include revision/hardware/JVM/settings, fixture bounds and touched chunk columns/sections, non-air and BE counts, supported external-record counts, loose/root/contained entity counts, warm-up/sample method, and raw results. Measure:

- Whole-server tick p50/p95/p99/**maximum**, ticks exceeding 50 ms, plus separate preparation, capture, ownership removal/publication, block/BE, topology, entity/control and synchronization timings. A 50 ms tick is the entire 20 TPS interval, not the shuttle's budget.
- Warm prepared-site trips separately from chunk preparation and normal reload. Record allocation/heap estimates as estimates unless actually measured.
- Connected client frame-time/stutter and movement/camera continuity through both relocation legs. Record packet/chunk update counts/bytes and watcher count where measurable; do not infer them from block-loop timing.
- Representative small ship plus an enlarged within-limit stress fixture; repeated trips, one simultaneous competing-trip refusal, and occupied/damaged variants. Publish the supported size cap and behavior when limits are exceeded.

Set explicit provisional subsystem/headroom budgets from M0 evidence, not mod advertising; report misses and tune/defer before claiming acceptance. If bounded copying cannot meet the desired smoothness, compare smaller supported bounds, bulk lifecycle-safe operations and isolated staging before proposing a different worldspace engine. Never hide hitches under a loading screen as evidence of efficiency.

**One computer is sufficient for focused automated and single-client feasibility validation.** No mandatory 20-player gathering. Synthetic actors/watchers are labeled proxies and do not certify genuine 20–100-client capacity; real-client scale remains an explicit unresolved risk unless measured.

### Acceptance matrix

| Criterion | Minimum evidence |
| --- | --- |
| Real breachable ship and playable route | Connected hatch plus arbitrary breach entry, countdown/board cutoff, live transit, damaged arrival/return. |
| Composition/config consumption | Full relevant pinned mapping; inheritance/default validation and live consumer tests; ordinary timing/config trace. |
| World/machinery continuity | Loaded-world block/BE/tick/airlock tests, current hull state and source cleanup, repeat legs and reload. |
| External state continuity | Atmosphere/pipe species+energy checks, vacuum claim preservation, cables/power paid work, topology and linked-identity checks at both sites. |
| Identity/ownership continuity | Same body/Mind/item/container identities and contents; corpse/contained policy; cross-boundary pulls and actions clean up without duplication. |
| Network/control continuity | Connected owner camera/movement/HUD, stale intent barrier, source/later watcher caches and bounded authoritative controller requests. |
| Safety and lifecycle | Refusal before mutation, occupied/unavailable target, post-departure hold/retry, normal restart without pristine reseed/duplicate hull; limits documented. |
| Performance/regressions | Reproducible full-path measurements with budgets/caps and limitations; focused plus full suite reports and independent reuse/parity review. |

## 8. Required execution reports and final handoff

At each coherent milestone report changed paths, reused/extended services, tests and evidence, parity verified/adaptations/pending, remaining risks and next gate. Keep review/failure artifacts useful but compact. Do not announce later milestones complete because a fixture constructed blocks or one entity teleported.

Keep any manual document very short: build fixture, board/breach and depart, interact/damage during transit, arrive/return and inspect continuity, exercise blocked destination/retry. Let automated fixtures cover long edge-case lists.

On owner closure, update `HANDOFF.md` yourself using `docs/HANDOFF_TEMPLATE.md` (roughly under 1,200 words): actual architecture and APIs, resolved prototype/config correspondence, owner boundaries, paths, evidence, measured size/performance limits, known failures and future starting points. Preserve this plan's status/history; do not rewrite research as historical gameplay validation.
