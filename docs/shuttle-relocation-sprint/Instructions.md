# Sable feasibility, physical ships, and FTL — implementation sprint

## Status, priority, and delegation

**Revised 2026-10-10; planning only. No ship integration or performance validation delivered.** This supersedes the original discrete region-relocation plan, retained in [discrete-relocation-reference.md](discrete-relocation-reference.md) only as a historical alternative. The owner requested execution by **another agent in a worktree**; the authoring session must not finish this implementation.

Read [HANDOFF.md](HANDOFF.md), [licensing-review.md](licensing-review.md), then this plan. The assignment is **first to prove Sable works seamlessly and efficiently with MoonStation's actual bodies and systems**, then, if the licensing/technical gates pass, deliver a bounded playable continuous-flight and FTL slice. Merely installing Aeronautics or flying an empty vanilla cabin is not the feasibility result requested.

Follow M0–M6 in order. Work autonomously within these boundaries; escalate material licensing questions, backend changes, demonstrated infeasibility, destructive actions, or unresolved gameplay choices. Do not silently revert to region copying, maintain two competing movement backends, or treat source inspection as a measured performance guarantee.

### Worktree bootstrap

1. Confirm branch/base revision, `AGENTS.md`, `gradle.properties`, and worktree status; preserve unrelated changes. Current project target is Minecraft **1.21.1**, NeoForge **21.1.224**, Java **21**. Recheck current source/configuration, not old handoff assumptions.
2. Copy this **whole documentation directory** into the worktree if it is uncommitted: a worktree created from HEAD does not inherit working-tree documents. No staging/commit/push is requested here.
3. Read the subsystem handoff indexes in section 4 before broad exploration. Successors/current source override historical descriptions. Do not restore retired atmosphere/power gates or legacy movement protocols.
4. Use the worktree for code and validation. Reuse existing language services; never launch duplicate LSP backends. Run resource-intensive server/client validation sequentially on the owner's one computer, not competing worker processes.
5. Establish current unit/GameTest baseline and retain relevant existing failures. Neither assume the full suite is green nor declare later failures unrelated without comparison evidence.

## 1. Current owner intention and supported scope

### Gameplay direction

- **Continuous physical flight around the moon map:** moving, interactive, breachable ships; ordinary approach, landing, collision and docking. Antagonists physically relocate their ships, and observers may see them. That visibility is a deliberate gameplay consequence, not something to conceal with distance-based invisibility.
- **FTL primarily for travel between dimensions**, eventually moon/deep space/CentComm. Prove same-dimension jumps first as a stepping stone, then actual cross-dimension ship/body/state transfer. Deep-space locations, incentives, salvage content and CentComm/round-ending gameplay remain deferred. A tiny test destination dimension is a transport fixture, not delivery of those features.
- **Terrain-based concealment:** recessed/hangar/crater-side berths, berms, appropriate vessel/site sizes, and prepared antagonist geographic knowledge. No ship-specific distance cloak in this sprint. Caves remain contiguous lunar terrain, not ship interiors or linked dimensions.
- Distinguish **flight**, **landing**, **docking**, and **FTL**. Flight does not become FTL because the old design used discrete transport. Normal collisions must not trigger a broad FTL materialization-shock effect.

### Required bounded slice after the gates

An authored small ship, two suitable moon berths, a normal body-authorized pilot/controller interaction, supported machinery and atmosphere aboard, real loose cargo and characters/corpses, physical departure/flight/docking, arbitrary breaches/repair, normal save/restart, and a separately proven FTL path. Operator fixture commands provide repeatability and diagnosis, not the only player interface.

Start with a small authored fleet and measured hull/content/fragment limits. The station remains static; loose items remain ordinary mod-owned entities, not separate Sable sublevels. Do not introduce arbitrary player-built assemblies or an unrestricted population of physics bodies as an initial acceptance assumption.

### Invariants

1. One authoritative live craft state. No remote duplicate cabin, cosmetic shell, breached-cell portal workaround, pristine reconstruction, or recurring disassemble/reassemble-to-travel pipeline. Initialization is explicit and cannot reseed damaged/revisited ships.
2. Internal blocks, machinery and simulation records remain at stable ship-plot coordinates during same-dimension flight. Adapt existing owner/query boundaries rather than rekeying every cell/cable each frame or creating another solver.
3. Characters, Minds, hands, equipment, storage tokens and item ownership remain authoritative through flight and FTL. Same-dimension motion keeps the existing entity instances/UUIDs. Cross-dimension transfer must use an established engine/platform path, preserve logical identities/contents, and explicitly rebind any runtime references the platform changes. Never manually clone a body into a fresh character to fake success.
4. Breaches remain functional in the real scene. Do not require an airtight hull to fly/FTL, fabricate oxygen, hide asphyxiation with immunity, or make the hull invisibly invulnerable. Controller eligibility is separate from passenger eligibility.
5. Retain current movement/status/action authority and loaded-only simulation responsibilities. The spectator carrier is not a physical passenger or inventory owner. No new possession handshake, competing movement motor, duplicated UI framework, or direct edits bypassing shared hand/containment/pull services.
6. Server authority owns pilot admission, forces, route/phase state, arrival hazards and docking links. Existing Sable movement interpolation is not permission to predict arbitrary ownership, damage or successful docking on the client.
7. Normal losses from deliberate collisions/gibbing/destruction are accounted gameplay events, distinct from unexplained transfer loss. Never erase inventories or other registered craft state with raw placement/deletion to resolve an overlap.

### Non-goals

No custom physics/worldspace engine, universal other-mod compatibility framework, Create survival progression/economy, arbitrary assembly/construction catalog, general radar/navigation game, full aircraft aerodynamics or CFD, unrestricted space/EVA/dogfighting feature suite, production evacuation ending, pods/antagonist/mobile-core content, ATS purchasing/selling, automatic cargo sale, save migrations, broad anti-cheat, or crash-atomic transaction framework.

Continuous flight and cross-dimension FTL are now intended work; the older document's exclusions no longer apply. This does **not** mean all newly possible flight/space gameplay is automatically part of this sprint.

## 2. Licensing and dependency gate

**Sable is a candidate dependency, not already approved for adoption/distribution.** Read [licensing-review.md](licensing-review.md). Its PolyForm Shield noncompete clause is the principal unresolved issue. It is not automatically safe because MoonStation is free, MIT, an addon, or a different genre; nor has this research established that MoonStation is prohibited.

M0 must record a basis for permitted use of the intended prototype and later product. Do not assume private evaluation is a blanket exemption. Because independently authored ship gameplay could overlap covered vehicle products, obtain project-specific written clarification/permission before committing to dependent public gameplay/distribution; this review does not establish competition or legal affiliation. Do not contact anyone, agree to terms for the owner, or publish artifacts without owner authority. Until resolved, source/artifact/license inventory and dependency-free baselines can proceed; flag any blocked runtime evaluation explicitly.

Prefer **Sable alone** for the first cabin prototype. Evaluate Create/Aeronautics only where their actual components reduce current work; taking the engine does not require importing Create progression or aircraft recipes. Sable Companion's MIT license does not relicense Sable. Avoid copying internal Sable implementation or restricted Aeronautics/Create assets. Use separately installed pinned dependencies by default; no shading, fork, bundled installer or copied asset package without an exact-artifact compliance review.

Record versions, release IDs, SHA-256, source revision/tag correspondence, minimum loader requirements, native platforms, public APIs used, actual embedded jars/native libraries, licenses and notices. Source build targets are not necessarily minimum runtime requirements. A required small NeoForge patch upgrade must be justified and baseline/regression-tested; do not silently change Minecraft versions or broad dependencies.

## 3. Verified evidence and what remains uncertain

### Sable / Aeronautics research anchors

- **Sable NeoForge 2.0.6, Minecraft 1.21.1**, published 2026-10-03, Modrinth release `fg9dTRz9`: [release](https://modrinth.com/mod/sable/version/fg9dTRz9). Client and server required. The inspected source revision was **`d501fb3103c3247e20cb2151a99182ad5dd61adb`**, declaring version 2.0.6 and NeoForge 21.1.228. Exact artifact/source identity and compatibility with project 21.1.224 were **not** established.
- **Create Aeronautics 1.3.2**, Minecraft 1.21.1 NeoForge, published 2026-08-29, release `44pLdPGg`: [release](https://modrinth.com/mod/create-aeronautics/version/44pLdPGg). Requires Create and Sable. Optional in this plan, not an already integrated dependency.
- Sable plots contain normal chunks/BEs at stable storage coordinates under a parent `Level`, presented through sublevel poses. `ServerSubLevel` implements `PhysicsPipelineBody`. [Source root](https://github.com/ryanhcode/sable/tree/d501fb3103c3247e20cb2151a99182ad5dd61adb).

Source paths below are relative to that Sable revision's `common/src/main/java/dev/ryanhcode/sable/`:

| Verified source observation | Implication / limit |
| --- | --- |
| `sublevel/ServerSubLevel`, `sublevel/SubLevel`, `network/packets/ClientboundSableSnapshotDualPacket` represent pose/velocity per sublevel; `network/client/SubLevelSnapshotInterpolator` interpolates poses. | Favors efficient persistent ships, not per-block movement copying. This packet path does not prove total traffic or chunk-update costs. |
| `mixin/entity/entity_sublevel_collision/EntityMixin` redirects `Entity.move` collision for general entities; `LivingEntityMixin` applies inherited living motion at `travel` return. | Our native move calls can benefit, but our canceled vanilla travel/custom motor needs explicit integration; do not rely on mixin order. |
| `mixin/clip_overwrite/BlockGetterMixin.clip` projects rays only for `Level` getters, otherwise uses ordinary clipping. | Our custom loaded-only `BodyItemTargetProbe.LoadedGetter` bypasses projected clipping. This is an identified incompatibility, not a hypothetical concern. |
| `sublevel/system/SubLevelPhysicsSystem` performs configured substeps; `SableServerConfig` defaults to two per game tick, 40/sec at 20 TPS. Loops visit loaded sublevels/actors. | Synchronous call path; native parallelism and parked-ship savings unmeasured. Do not call physics free/background-only. |
| `SubLevelPhysicsSystem.queryIntersecting` currently selects `SubLevelContainer.queryIntersecting`, a filter over loaded sublevels. Block changes notify mass/geometry/wake-up paths. | Query costs can grow with loaded ships/fragments; hook existence is not a complete geometry-rebuild performance audit. |
| `api/physics/handle/RigidBodyHandle.teleport` delegates to the current pipeline. | Same-pipeline pose capability, **not** proof of cross-dimension ship/body transfer. |
| `api/sublevel/SubLevelContainer` and plot/storage systems maintain UUID/storage; splitting is enabled by default in inspected config. | Assembly, splits, unload and dimensions may relocate records/change pieces. Stable identity of one sublevel does not automatically define one craft across fragments. |

The [entity wiki](https://github.com/ryanhcode/sable/wiki/Working-with-Entities) documents tracking versus plot-contained entities and default entity "kicking" into world space. Audit actual release behavior/tags for our bodies/items; do not blanket-retain all entities in plot space to suppress problems. Some helpers bearing `api` in their package are marked internal: do not mistake a package name for a stable public contract.

No combined runtime test, reproducible ship benchmark, seamlessness proof or 20–100-client capacity evidence was produced by this research.

### SS14 parity anchor

Verify `C:\Users\William\Documents\SS14-dev\space-station-14` at **`c9df5ef5d675b0d1d226828bddf6b78c28502d91`**. This is the **SS14 pin**, not the Sable pin above. If unavailable, mark relevant mapping unverified.

- SS14 uses persistent movable grids, pilot/thruster systems, docking between separate grids, and FTL grid relocation. Inspect actual movement/impact consumers before designing flight thrust, fuel/power, control and damage rules.
- Console ancestry: `ComputerShuttle → BaseComputerShuttle → BaseComputer → BaseStructureComputer → BaseStructure`. Sources: `Resources/Prototypes/Entities/Structures/Machines/Computers/{computers,base_structurecomputers,frame}.yml`, `Entities/Structures/base_structure.yml`.
- Port ancestry: `AirlockShuttle → AirlockRCDResistant → Airlock → BaseStructure`. Sources: `Entities/Structures/Doors/Airlocks/{shuttle,base_structureairlocks}.yml`. Ordinary local airlocks do not already provide docking composition.
- Consumers: `Content.Server/Shuttles/Systems/{ShuttleSystem.cs,ShuttleSystem.Impact.cs,ShuttleSystem.FasterThanLight.cs,ShuttleConsoleSystem.FTL.cs,DockingSystem.cs,DockingSystem.Shuttle.cs,EmergencyShuttleSystem.cs}`, pilot/shared shuttle consumers and `Content.Shared/CCVar/CCVars.Shuttle.cs`. `Content.Server/Doors/Systems/FirelockSystem.cs` treats docking separately from door sealing. Finish exact pilot/thruster prototype chains and their relevant inherited defaults in M0.
- Ordinary FTL config defaults are startup **5.5 s**, travel **20 s**, arrival **5 s**, cooldown **60 s**. Arrivals-specific cooldown **10 s** is not universal; mass limit **300** is physics mass, not Minecraft block count. Hyperspace knockdown **5 s** needs its actual eligibility/exemption audit. These are server CCVars, not invented entity-prototype defaults.
- Console FTL can target coordinates/beacons; it is not inherently restricted to evacuation connector docking. Local authored FTL destinations are a bounded MVP adaptation.
- `LeaveNoFTLBehind` selects `NoFTL` entities by `TransformComponent.GridUid` through Robust `EntityLookupSystem.ComponentQueries.GetGridEntities`, not AABB proximity. Establish local flight/FTL membership explicitly.
- `UpdateFTLArriving` supplies a timed destination visualizer (`Content.Client/Shuttles/FtlArrivalOverlay.cs`, `Resources/Prototypes/Entities/Effects/shuttle.yml`). `Smimsh` checks 0.2-expanded hard-fixture bounds, gibs external bodies and queues other affected entities for deletion, with exclusions. Body gibbing can produce/fling configured giblets; normal moving-grid collision uses a separate impact system. **Do not claim every FTL obstruction becomes flung salvage.**
- Emergency arrival is connector-first with proximity/map fallback, not guaranteed docking at a destroyed connector. A destroyed physical port must not erase authored destination knowledge. Strict forced placement at one blocked coordinate would need an explicit local policy, not a parity claim.

Relevant ancestry/behavior was researched, not an exhaustive supported-component mapping. M0 must resolve full chains, inherited defaults and actual consumers before defining typed local flight/controller/port data. Represent only supported behavior; JSON or unsupported component names alone do not count.

## 4. Local reuse map and known integration work

Java paths are below `src/main/java/com/juicyslew/moonstation14/`. Verify exact current symbols before assigning changes.

| Owner / handoff to read first | Reuse / extension target |
| --- | --- |
| Composition | `ms14/prototype/`: `PrototypeRuntime`, `PrototypeManager`, `PrototypeResolver`; typed inherited airlock/disposal/ventilation catalogs and existing sync/tests. |
| Bodies, camera and movement | Newer `docs/disposals-pneumatic-sprint/HANDOFF.md`, `docs/airlocks-sprint/HANDOFF.md`, then body-control/lifecycle handoffs. `ms14/player_body_control/`, `BodyControlRegistry`, `character/GroundedHarnessWorldStep`, `movement/ExternalBodyFlight`, `client/GhostControlClient`, and current authoritative snapshots. |
| Native-travel suppression | `mixin/LivingEntityMindHarnessTravelMixin`: owned bodies/corpses cancel vanilla travel. Extend the current owner for inherited ship motion; do not revive old experimental movement or copy possession handshakes. |
| Hands / targeting / items | Hands/inventory and loose-world-foundation handoffs; `ms14/hands/live/{BodyItemTargetProbe,BodyInteractionAccess,HandActorAuthority}`, `ms14/hands/network/BodyHandRequestService`, `entities/{LooseItemEntity,LooseClientPositionInterpolation}` and shared storage/token publication. |
| Loose support / pulling / actions | `LooseItemEntity.tick` uses native `Entity.move` but settled early returns and `hasFloorSupport` directly inspect world blocks. `ms14/loose_objects/pulling/BodyPullService`, `TetherMotion`; timed-ingestion handoff and `ms14/timed_interaction/`. |
| Exposure / finite atmosphere | Atmos-temperature handoff as index, then pressure/incident/combustion successors. `ms14/atmos/world/{AtmosphereService,AtmosphereChunkData,AtmosphereTopology,AtmosphereEventHooks}`, `exposure/{ExposureAirRouting,BarotraumaSystem,BodyTemperatureSystem}` and breathing consumers. Body-eye world sampling is not currently ship-aware. |
| Power / pipes / records | Power/APC and powered-ventilation handoffs; `ms14/power/{graph/PowerGraphService,runtime/PowerRuntime,cable/}`, `ms14/atmos/piping/`, `PipeChunkTopology`, `PipeGasInventorySavedData`, lane UUIDs, concealed-host incarnations and device links. |
| Containment / disposal | Disposal handoff; `ms14/containment/BodyContainmentService` and disposal holder/transport owners. They own position/air/camera policies; a moving cabin is **not** disposal containment. |
| Doors / menus / visuals | Airlock and machine-visual-kit handoffs; `ms14/doors/`, existing machine menus/snapshots, `CharacterControlSystem`, shared `StoragePopupScreen`. Cable/gas/device client caches and renderers need frame-aware presentation and correct tracking. |
| Registration / fixtures | Service `register` hooks in `MoonStation14.java`, existing disposal/ventilation builders, real-world GameTests and `docs/gametest-tags.md`. No parallel fixture infrastructure. |

Each worker must check for additional reusable paths, report reused/extended symbols and justify substantial overlap. Independent review rejects duplicated solvers, motors, authority checks, inventory transfers, UI frameworks, or unnecessary universal abstractions.

## 5. Architecture and gameplay contracts

### Frames, identity and supported data

Separate **world-space** physics/body/camera/reach positions from **plot-space** blocks/BEs/gas/cables. Retain stable craft identity and resolved supported prototype/config data separately from engine runtime references and phase/pilot state. Use a narrow shared engine/query boundary for current consumers, not ad hoc transform math copied into every feature. Reuse engine coordinate/collision APIs where permitted and supported.

Prefer initial authored ship creation/assembly through supported engine APIs, followed by initialization of real owners at their correct plot positions. Test assembly's treatment of existing records if supported; never assume BE NBT moves all gas/cables/SavedData. Do not repeatedly reassemble during ordinary flight or FTL.

Internal graphs and gas kernels operate in stable plot coordinates. World queries resolve the relevant frame and convert at the boundary; avoid scanning/transforming every ship cell on every body query. Physical dock interfaces deliberately join frames. Do not infer connected power/air merely because bounding boxes touch.

Audit plot chunk load/save hooks and NeoForge attachments, bounds/world-border checks, clock semantics, scheduled ticks, block replacement and owner indexing. SavedData keyed by dimension/position/lane UUID and long-lived client caches are separate obligations. Internal identity links remain stable; exterior links cannot silently bind replacement devices.

Hull damage, removal and splitting must update affected owner's state/topology once. Establish craft↔sublevel/piece membership and normal destruction behavior. If splitting is constrained for the first slice through a verified supported option, record that as a deliberate limitation for owner review; do not leave it enabled with silent gas/record loss or call a no-split lab proof full destructive-flight acceptance.

### Movement, prediction, cargo and interaction

- Current native `Entity.move` remains a useful collision seam. Integrate inherited platform motion explicitly with our motor/snapshots; distinguish voluntary movement from ship-carried movement for friction, animation, posture/slip, falling, collision and damage. No duplicate motion application or camera drifting behind the body.
- Prove standing still, walking, sprinting, jumping, prone/critical/dead bodies, acceleration/turning, deck exit and moving hatch crossings. Do not rely on `LivingEntity.travel` mixin order to supply canceled-path behavior.
- Preserve loose-item identity and settled optimization. Make support/contact velocity and loaded guards frame-aware; carried ship movement must not falsely trigger restitution, jug breakage, an unloaded freeze, or a permanent active-object storm. Keep container children in their existing owner, not new world roots.
- Make loaded-only ray/reach/LOS probes, outlines and body-eye machine access frame-aware through shared services. A converted hit includes enough identity/frame context for server revalidation; do not trust client coordinates or bypass the loaded-only policy by replacing everything with an unrestricted vanilla raycast.
- Pulls use current release/tether/hand reservations. Test inside one ship, ship-to-dock boundaries and deck departure. Unsupported discontinuities cancel through the shared owner, not direct hand edits.
- Timed actions and menus follow exact identity/body authority and frame-correct reach. Preserve held self-actions where valid; reject/close stale moved-world sessions with feedback without disabling HUD/chat/storage indiscriminately.
- Contained bodies follow their actual owner's transformed position/air. Do not force all ship occupants into third-person disposal containment or offset a holder and body independently.

### Atmosphere, power and vacuum flight

Keep the accepted float-backed gas, pressure transport, species/thermal conservation and 15 Hz admissions. Extend existing exposure routing/sampling for world-to-plot reads and writes; oxygen consumption/exhalation/thermal transfer must affect the correct owned cell, not just a display sample. Claimed vacuum stays claimed vacuum. Unknown is not invented oxygen/vacuum.

A breach needs a real exterior boundary at the ship's physical environment; unloaded plot neighbors are not a valid oxygen source or an excuse to prevent venting. Docked air connections need explicit frame-aware boundary exchange and clean disconnection. Test sealed, vacuum, breached and open dock states separately.

Preserve typed power delivery, grants/paid intervals, battery/fuel inventory and pipe gas. Begin with self-powered ships to avoid speculative dock utilities. Required air connection/boarding must still work; ship-to-station power/pipe transfer can be explicitly deferred if not needed by the fixture. No FE-only substitution for current HV/APC gameplay.

Use a pinned SS14 pilot/thruster composition audit for the spacecraft controls/thrust/power/fuel behavior actually implemented. Moon vacuum must not silently use Earth balloon/propeller lift or fabricated aerodynamic pressure. Configure engine dimension physics consciously and document 3D/unit adaptations; engine physics config is not permission to change existing character gravity globally.

### Flight, landing, docking and stealth

Pilot admission uses the real controlled body's authority/status, controller identity, supported power/controls and current craft. Reuse configured input routing/UI; map verified SS14 pilot controls, documenting necessary 3D ascend/descend adaptations. Movement input ownership while piloting must not simultaneously walk the body or conflict with spectator-carrier movement. A destroyed console/power loss stops invalid control, not the existence of the moving ship or its contents.

Player ships physically approach outposts; no same-map FTL shortcut is the default travel loop. Use supported engine thrust/constraints and collisions rather than client pose teleports disguised as flight. A small authored fleet and measured speed/attitude envelope are initial limits, not a ban on physics or a second kinematic engine.

Connector alignment alone does not prove fit. Docking/autoland must check the craft's shape/required clearance against terrain and notable structures. A larger ship may be incompatible with an outpost. Manual flight can still crash into geometry: docking rejection is not an invisible collision shield.

Loose objects, corpses and living entities are **not structural landing/FTL vetoes**; apply supported physical impact/destruction rules instead. Ordinary collisions use relative physical motion and the shared damage/destruction owners, not the broad FTL shock policy. Real approaching motion/sound provides warning; do not invent an FTL countdown for every ordinary ground contact.

Docking links separate station/ship frames and existing door owners; never merge station blocks into ship ownership. Destroying the connector removes that sealed docking capability, not persistent destination knowledge. Author sites sized and positioned for both fit and covert approaches; flight within sight is intentionally observable. No selective cloak/render-range workaround or globally revealing arrival markers.

### FTL, membership, warnings and failure

FTL is a separate server phase transition using the existing live ship, not reassembly, a pristine template, or the old region-copy mover. Use verified startup/travel/arrival/cooldown config as baseline, with tested explicit local overrides. Preserve gameplay during travel except the minimum justified transition barrier. Stale movement/action intents cannot replay from the old frame after arrival.

Resolve passengers through engine tracking plus a tested policy for airborne bodies, roof riders, plot-retained entities, holders, containment and explicit nontravelling exceptions. Membership is not simply proximity or pilot eligibility. Spectator carriers follow their body authority without becoming a second physical passenger. Do not blindly apply the old cuboid feet-membership rule to this backend.

FTL arrival warns locally in **three dimensions**: ground footprint plus readable airborne/roof/vertical hazard volume. The warning covers the actual lethal/impact envelope and gives time to leave it with reaction allowance. Five seconds is SS14's arrival-phase default; approximately ten seconds was suggested during discussion, **not owner-approved as a final warning duration**. Size-dependent safe escape and local visibility are acceptance criteria, not a silent configuration guess.

The owner wants FTL gib danger beyond direct hull intersection, with roughly **three metres** discussed as an arrival-shock margin. Establish the resolved volume/margin, cover/boundary rules and duration together in M0; test them and label the widened 3D hazard a Minecraft adaptation, not SS14's 0.2 expansion or simulated wind. Incoming passengers are excluded; destination bodies are not assumed passengers. Actual gibbing must use proper body/destruction ownership and configured remains/possessions. Do not assume upstream queues deletion imply universal salvage/impulses for every obstruction.

FTL destination eligibility includes authored structure fit and supported source/destination state, not a veto on loose/living objects. Preflight revalidates loaded readiness, transform, blockers and phase authority before mutation. An obstructing registered ship must be handled through its owner/physics/destruction or a valid fallback; raw overwrite leaving its registry intact is forbidden.

Start with connector-first and eligible authored proximity/map fallback semantics. A destroyed port is not a reason to delete passengers or permanently cancel evacuation arrival. Strict guaranteed placement at one coordinate despite any structure/ship would need a separate resolved arrival-priority policy; do not claim upstream already guarantees it. Production evacuation/round ending remains deferred.

Prove same-dimension engine jumps before cross-dimension FTL. Cross-dimension work must audit actual engine transfer support, plot/chunk attachment migration, dimension SavedData and record keys, device links, passenger/entity transition, camera/tracking, lifecycle claims and source teardown. Same-pipeline `teleport` alone proves none of this. If blocked, report moon-flight acceptance separately and stop FTL claims; do not disguise remote same-world sites as proven dimensions.

Before transition, rejection leaves authoritative state unchanged. If a target becomes unavailable after departure, retain the live craft at a valid transit/holding state with visible retry/fallback—not a fresh copy at either end. Normal save/restart preserves craft/body/content identities, state and documented phase/deadline semantics. Define controlled stop during transition, not a broad crash-recovery framework.

## 6. Ordered milestones and evidence

### M0 — License, exact artifact, composition and baseline

Resolve the licensing basis for the intended work; complete exact artifact/dependency/native/API inventory and version compatibility. Finish pinned SS14 full ancestry/default/consumer mapping for controller, pilot/thrust and docking/FTL behavior; map only supported local components. Record the actual current movement owner, frame/state-owner inventory, membership/hazard policy, pilot/3D adaptations, assembly/split/dimension assumptions and small fixture bounds.

Build the stationary baseline with existing helpers: supported hull/window/two-cell airlock, real powered machine/cables, finite non-default room/pipe gas, controlled body/corpse, filled container and representative settled cargo. Define repeatable performance workloads/budgets/headroom and a time-box for the feasibility gate. Read existing health/lifecycle failures before new death/dimension tests.

**Exit:** `PARITY.md`, short frame/owner architecture, pinned artifact/license inventory and stationary baseline evidence. No unresolved legal assumption disguised as permission, no version advertising substituted for compatibility, no runtime/global-gate changes merely to make a fixture easy.

### M1 — Engine/body/query/cargo proof, not empty-ship demo

Create/assemble one ship through supported engine APIs; preserve current owners and identify assembly-time remaps. Integrate native-body inherited motion, authoritative snapshots/camera, loaded-only projected targeting and loose support/carried motion through their current services. Exercise moving/turning/accelerating cabin and hatch crossing, two chunk seams, ground versus airborne support, corpse and container identity, ray obstruction and ordinary actions aboard.

**Exit:** a connected actual controlled body can stand still, walk/jump/use aboard and cross the boundary without drift, source snapback or incorrect hits; native fixtures prove identities and frame-aware loaded behavior. A vanilla player on a vanilla floor alone does not pass.

### M2 — Existing simulation owners and persistence aboard

Wire atmosphere/exposure/breathing, power/cables, pipes/device links, visuals and required containment/actions to the ship frame/storage lifecycle. Verify stable records during flight, finite gas transfer and paid machine work, breach leaks, correct local inhalation/exhalation and temperature, normal save/load, edits and the supported split policy. Support the minimum fixture; explicitly reject/defer other machinery rather than drop state.

**Exit:** live moving-cabin conservation/control/visual fixtures pass, including vacuum claims, gas/energy and inventories, pipe lane/device IDs, no stale paid grants, corpse/contained policy, hatch boundary and damage/repair. Sealed conservation tests and open/leak tests have appropriate separate exchange accounting. No duplicate solver or second source of inventory truth.

### M3 — Decisive seamlessness/performance adoption gate

Measure the integrated stationary-versus-moving fixture, representative settled cargo, active throwing/pulling, larger within-limit hull, a second ship and dock approach, parked state, and destructive fragmentation. Inspect physics, actor, loaded-query, network and render costs—not just empty-hull FPS. Report maxima/over-budget ticks, connected control/frame-time continuity, memory and supported fleet/content/fragment limits.

Independent review checks actual source/identity/service boundaries and evidence. Identify whether failures come from our adapter, engine/API, lifecycle/persistence, or intrinsic cost. If requirements/budgets fail, optimize the demonstrated bottleneck within scope or return concrete alternatives to the owner. Do not automatically implement the historical copying backend or two parallel backends.

**Exit:** owner-reviewed go/no-go for Sable-based gameplay with explicit measured bounds and license gate satisfied. Passing means this **combined** fixture, not proof of 20–100 authentic clients. Do not begin the broader pilot/FTL gameplay on unsupported assumptions.

### M4 — Playable piloting, physical moon flight and docking

After adoption, implement resolved flight/controller data, real pilot input/authority and feedback, engine thrust/physics, power/fuel where supported, and normal approach/docking at the two authored berths. Distinguish pilot and passenger status. Test larger incompatible craft/site fit, destroyed connector, loss of pilot/control/power, normal collision damage via shared owners, boarding from arbitrary hull breaches and continuity of HUD/actions while moving.

Preserve existing interaction/keybind conventions; document unavoidable 3D adaptations. Do not expose unrelated Create progression. Design covert berths/approach geometry rather than range cloaking. Engine capability is not acceptance of arbitrary assemblies or a full space-combat feature suite.

**Exit:** normal controller-driven departure → actual moon flight/turning → physical approach/docking → return, playable aboard with persistent damage/cargo, geometry-fit rejection and observed normal impacts. No same-map FTL shortcut masking unimplemented flight.

### M5 — FTL phase/warning/impact and actual dimension gate

Implement server FTL phase/config/route data and local volumetric warning; prove engine same-dimension jump/transit/arrival on the existing ship first. Exercise cooldown, stale/duplicate requests, airborne/roof/contained passengers, actual gib hazard and incoming-passenger exclusion, destroyed dock/fallback and blocked/unavailable targets. Controller loss must not delete an already departed craft.

Then prove a round trip through an **actual fixture destination dimension** using the supported engine/platform transfer route plus existing owner participation. Include controlled body/carrier/camera, corpse, items/storage, machinery, claimed vacuum, room/pipe gas, cables/grants, host/device identity links, damage and normal restart. Account for any engine-induced plot-coordinate changes and platform entity-instance changes without fresh character/Mind ownership or simultaneous duplicates.

**Exit:** phase/3D warning/destruction policy evidence and an actual dimension round trip preserving required state and control. If only same-world jumps work, record a partial result and blocker; cross-dimension FTL/deep-space/CentComm is not complete. Do not build those gameplay destinations to work around a failing transport gate.

### M6 — Integrated limits, regression review and implementation handoff

Repeat representative performance and persistence/failure checks with piloting/docking/FTL enabled. Cover normal unload/reload/restart, controller removal, destination loss after countdown, supported damage/splits, repeated trips and two conflicting admissions. Run the broad GameTest suite once at the integrated gate; review reuse, composition, SS14 adaptations, dependency notices, authority, runtime ownership and client rendering.

**Exit:** acceptance matrix links every delivered criterion to evidence; failed/partial gates remain explicit; no unsupported efficiency/capacity claim. Successful disposable worlds removed, useful failures retained, concise human check and updated handoff. Only owner closure permits calling the sprint complete.

## 7. Validation and measurement contract

Use existing fixture source sets and `@GameTestTags`: new `shuttle` and/or `sable` selectors, relevant cross-domain tags only where exercised. Follow explicit template namespace/registration rules in `docs/gametest-tags.md`. Use a separate designated fixture directory, never owner configuration:

```powershell
./gradlew test compileGameTestJava assemble
./gradlew runGameTestServer "-Pms14GameTestTags=shuttle,sable" -Pms14GameTestDir=build/gametest-shuttle-fixture
# At integrated gates: broad owner/mixin changes require full-suite reporting.
./gradlew runGameTestServer -Pms14GameTestDir=build/gametest-shuttle-fixture
```

Do not run an unknown/unregistered selector before adding it; the preflight catalog is authoritative. Tags use OR selection, not isolation/order. Reuse the fixture directory where safe, clean successful worlds between incompatible configurations, preserve useful failures/logs/XML. Report filtered/full, selected/total, pass/fail/incomplete counts and baseline comparisons. Do not weaken tests or absorb unrelated fixes to make a green report.

Record mod/source revision, exact engine/dependencies/natives, loader/JVM/hardware/settings, UDP/TCP mode, flight controls, hull/chunk/BE/actor counts, room cells and records, root/contained/settled/active items, ships/pieces/watchers, fixture generation, warm-up/duration and raw samples. Measure:

- Whole-server tick p50/p95/p99/**maximum**, counts above 50 ms, and relative cost against identical stationary worlds. Fifty milliseconds is the entire 20 TPS interval, not the ship budget.
- Physics/native calls, Java pre/post/substep/actor work, collision/projection queries, atmosphere/power and action adapter work separately where instrumentation permits. A native engine does not prove asynchronous/off-main-thread cost.
- Connected client frame times/stutter, camera/body relative drift and correction, boarding/interaction/visual continuity while moving, parked and after FTL.
- Pose and ordinary chunk/block/entity traffic, bytes/packet counts and watchers where measurable. A compact snapshot format is not total bandwidth evidence. Test supported TCP fallback as well as default UDP; do not require owner router changes to claim basic operation.
- Initial assembly/load, edits/breaches/splits, stationary/accelerating/turning ships, settled versus active cargo, concurrent ships/fragments, docking and FTL both same/cross dimension. Report allocation/heap estimates as estimates unless measured.

Choose explicit provisional time/memory/network/frame budgets and headroom in M0 from evidence; confirm/tune at M3 and M6. Publish hard supported caps and behavior beyond them. Do not lower accuracy until tunneling/interaction tests justify it, call sleeping bodies zero-cost, or hide hitches under a loading screen.

**One computer suffices for focused feasibility and single-client connected proof. No mandatory 20-player gathering.** Synthetic actors/watchers are labeled proxies. They do not establish genuine 20–100-client capacity; that remains an explicit risk unless measured.

### Acceptance matrix

| Criterion | Required evidence |
| --- | --- |
| Permission / distribution readiness | Exact-artifact license/dependency inventory and resolved permitted-purpose basis; project-specific clarification where needed, notices and asset boundaries. |
| Composition and parity | Full relevant pinned parent/default/consumer mapping; resolved local inheritance and live flight/controller/FTL consumer tests; configuration and adaptations distinguished. |
| Seamless physical body/cargo | Connected actual controlled-body/camera proof, relative carried motion/settled items, frame-aware reach/LOS, possession/token/corpse/containment continuity. |
| Live interior systems | Paid machinery, gas/pipe species/energy, vacuum claims, breathing/exhalation/temperature, breach/repair, edits/split policy, owner/cache persistence. |
| Actual flight / docking | Server-authoritative piloting and visible continuous travel, two physical berths, size/shape compatibility, arbitrary breach boarding, ordinary impact and missing port behavior. |
| FTL / dimensions | Server phases/cooldown, locally readable 3D hazard/warning/gib policy, passenger exclusions, fallback/retry and actual dimensional round trip with no duplicate owners. |
| Persistence / failure | Normal stop/restart/unload, stale/unsupported/unavailable refusals, live holding/fallback, no pristine reseeding or orphan source ownership; limitations documented. |
| Efficiency / regression | Reproducible combined fixture, stationary comparison, parked/moving/cargo/split limits, maxima and client evidence, focused plus full-suite reports and independent reuse review. |

## 8. Assets, reports and closure

Prioritize SS14 console/shuttle art and actual hyperspace sounds; Sable/Aeronautics resources are not an asset pool to extract into MoonStation. Startup/arrival references: `/Audio/Effects/Shuttle/hyperspace_begin.ogg`, `hyperspace_end.ogg`; upstream system volume `-5f` requires correct unit conversion, not a Minecraft literal gain. Pinned `Resources/Audio/Effects/Shuttle/attributions.yml` attributes both to /tg/station, **CC-BY-SA-3.0**, source directory at `71a79f1f75902d2ccab27cbffb971b12b7ab042d`. Verify actual files/metadata, preserve attribution/licenses, and record genuinely unclear audio in `docs/audio-unknown-provenance.md`.

Milestone reports identify changed/reused/extended paths, evidence, licensing state, parity verified/adapted/pending, measured limits and next gate. Keep any manual check very concise: enter powered cabin; stand/walk/use while flying/turning; inspect cargo and breach/repair; dock/return; FTL round trip and warning/failure check. Automated fixtures cover long edge-case lists.

On owner closure, update `HANDOFF.md` using `docs/HANDOFF_TEMPLATE.md`, roughly under 1,200 words: actual dependencies/license decision, APIs/frames/owners, prototype/config mapping, source paths, measurements/caps, connected evidence, failures and next work. This planning package is not implementation acceptance, and historical source research must not be rewritten as gameplay validation.
