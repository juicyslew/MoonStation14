# Loose world objects sprint plan

**Status: documentation and planning only. No implementation is authorized by this document.** The milestones below are a proposed bounded sequence and owner-review artifact, not a commitment to deliver all stages or a change to roadmap ranking/owner priorities. Obtain explicit owner authorization and resolve the decisions below before code, assets, or tests are changed.

## Goal and contract

Design a distinctive, readable mod-owned 3D floor-item experience before the item catalog grows, without changing vanilla's global item renderer or unrelated/Creative behavior. The planning target is 20 typical concurrent players and a 100-player worst-case envelope around a compact, vertically stacked station with low-thousands loose objects. Cargo, Botany, and Chemistry (Chems) may produce dense piles; most objects should be settled. These are workload goals, not demonstrated capacity or performance guarantees.

For eligible controlled CHARACTER bodies, the proposed pickup UX is deliberate click-to-pickup, not walk-over pickup. This is an owner decision and new UX work, not current behavior: connected pickup is currently key-based (G), not an existing clickable interaction. A click should identify the exact intended member of an overlapping pile, subject to server authority and the existing ownership/transfer contract. Mod-owned loose items need distinct, legible floor presentation; vanilla items retain vanilla presentation. Small objects such as jugs collide with the world but do not block the body. A large unanchored vending machine collides with the world and blocks the body. Deliberate shove/pull behavior is a separate policy, not an accidental consequence of collision. Anchored fixtures are a separate category and must not be treated as loose items.

### Non-goals and guardrails

- No global vanilla `ItemEntity` renderer replacement, global auto-pickup, or blanket changes to vanilla/Creative interactions.
- No promise that all item entities stop ticking, that smaller simulation distance solves scale, or that client walls produce server interest culling.
- No unconditional loose-record-to-entity conversion on pull. The recommended baseline is mod-owned loose objects represented as entities throughout resting and pulling. A record/hybrid design is only an alternative after measured evidence demonstrates a material problem and the owner chooses it; any transitions then need explicit lifecycle/conservation tests.
- No assumed pulling representation swap. SS14's entity-distance-joint plus separate virtual hand item is a conceptual analogy, not an established Minecraft 1.21.1 implementation or proof that this project supports a separate virtual hand item. Upstream virtual hand-item creation is conditional on `NeedsHands`; verify the exact upstream revision and condition before using this analogy. M4 must audit and develop a suitable physics constraint/pull route, or explicitly reject pulling if unsupported. `LIVE_HANDS` currently stores item tokens and real `ItemStack`s for held items; **no pull-hand reservation is implemented locally**. Any proposed visual/hand reservation is distinct from SS14's virtual item: it would reserve actor hand state, not carry or duplicate the pulled object, and the body remains the actor; pulling must not transfer the floor item into `LIVE_HANDS`.
- No assertion of crash-atomic pickup/drop. The current slice has compensation for known failures, not a crash-atomic transaction guarantee.
- No implementation, art production, catalog expansion, or upstream code copying is authorized by this plan.

## Local baseline and architecture evidence

Current local behavior is a bounded accepted slice, not the complete future contract:

- `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyHandItemTransfer.java` transfers a **whole stack** between an `ItemEntity` and body-owned `LIVE_HANDS`; pickup revalidates server/body/item state and compensates some known failures. Its `RECOVERY_REQUIRED` outcomes and comments explicitly do not imply crash-atomicity.
- `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyItemTargetProbe.java` is a body-eye first-hit probe. It chooses the first hit by distance and, in the current source, uses UUID order for exact-distance ties; it only accepts a nomination matching that chosen first item. This is not client selection of an arbitrary intended pile member. It reads loaded chunks only for its own getter, but that does **not** guarantee no force-load: arbitrary collision-shape callbacks may independently access/load chunks. M0/M2 must audit callbacks and provide/accept a safe path or fail-closed alternative; no-force-load is a desired policy, not a property proven by the current probe.
- The local hands handoff, `docs/hands-inventory-program/current-handoff.md`, describes key-based G pickup / H drop for eligible bodies (not click-to-pickup) and explicitly says no two-client acceptance or 20-player performance has been proven. Click-to-pickup would require client/UI/action routing and owner approval.
- `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/GroundedHarnessWorldStep.java` is the owned grounded custom-body movement path. Collision acceptance, step-up, and possessed-body behavior must be tested on the actual `PlayerCharacterHarnessEntity`, not inferred from vanilla player movement or a generic mob.
- `src/main/java/com/juicyslew/moonstation14/block/custom/JugBlock.java` is a placed jug fixture/block behavior, not evidence that a future loose jug has the desired world/body collision. Test loose representations independently.
- `docs/character-capabilities-sprint/ground-items-decision.md` is **historical**: it documents a pending earlier pickup decision and older prerequisites. Do not treat it as the current accepted click/whole-stack slice or silently rewrite its history.
- `docs/power-sprint/current-handoff.md` documents a bounded records/snapshot pattern for power records. That is an alternative pattern to study, not evidence that loose objects should become records.

SS14/Robust evidence audited as conceptual references only (paths relative to an external SS14 checkout):

- `Resources/Prototypes/Entities/Objects/base_item.yml`: floor item prototypes remain entities and use physics-related configuration; inspect version-specific prototype and components rather than assuming Minecraft equivalence.
- `Content.Shared/Movement/Pulling/Systems/PullingSystem.cs`: pulling operates on an entity through a distance joint and a separate virtual hand item. It does not require replacing the pulled entity with another representation.
- `RobustToolbox/Robust.Shared/Physics/Systems/SharedPhysicsSystem.Island.cs`: physics islands and sleeping are distinct from destroying/unregistering entities. Sleeping does not establish zero server entity tick cost.
- `RobustToolbox/Robust.Server/GameStates/PvsSystem.Chunks.cs`: PVS chunk/client visibility selection is a networking/client-interest mechanism; it is not server simulation unloading. Inspect exact upstream revision before relying on details.

These paths support the architecture distinction, not copied code or performance predictions. Minecraft 1.21.1 NeoForge 21.1.224 entity ticking, persistence, collision, rendering, and tracking behavior must be measured in this project.

## Measurement contract (start in M0; repeat at gates)

Record environment, mod/build revision, server/client settings, hardware, fixture generation method, warm-up, sample duration, and raw outputs. Use a repeatable seeded layout and report distributions rather than a single best run. Measure separately:

- Server tick duration p50/p95/p99 and missed/over-budget ticks, with baseline empty/station and each workload; isolate entity update/physics, interaction, persistence, and network costs where instrumentation permits.
- Active, idle/settled, and total loose entities; per-tick callbacks/work; item count and stack/component-size distribution; pile sizes; and floor/room/vertical-level distribution. Do not label entities “sleeping” unless a suitable state is actually implemented and verified. Record entity counts and chunk columns (X/Z) separately from vertical stacking: a column is not a vertical simulation slice.
- Network bytes and packet/update counts per client and server, tracking/watch queues, queue high-water/age, entity spawn/remove/update rates, and behavior as watchers join, leave, or move across chunk seams.
- Client frame-time p50/p95/p99 and draw/instance counts for an ordinary view and a dense pile view; include 3D readability, overlap selection feedback, occlusion/walls, and viewport contents. Server interest policy cannot be inferred from what one client sees behind a wall.
- Save/load and restart duration, serialized size, loaded-chunk behavior, and ownership conservation. Track memory/heap with a stated method; estimates are not measured memory.

Use representative room/floor distributions, not only a flat entity line. Include cargo, botany, and chemistry pile clusters; compact vertical levels; settled populations; join bursts; and a separately labeled active mass-jostle case. Compare to a no-loose-object baseline. Do not invent pass thresholds: owner and implementers agree budgets and headroom after M0 measurements. If a 20 TPS objective is adopted, identify 50 ms as the total tick interval, not an object subsystem allowance, and explicitly reserve agreed headroom for the rest of the server.

## Ordered milestones

Milestones have stop/review points and are not freely reorderable: later work may depend on predecessor decisions or behavior. The short M0 benchmark baseline is reusable in later stages; repeat/extend it at the later gates as specified. Any proposed reordering requires review of those dependencies, and failure does not authorize silently broadening scope.

### M0 — Source audit, playable contract, baseline and fixture

1. Pin/audit the exact SS14 and Robust revisions for the paths above; verify the local current behavior and identify Minecraft-specific hooks for entity tick/idle behavior, persistence, tracking, collision, render selection, and pulling. Do not port code. Do not assume Minecraft has a built-in asleep `ItemEntity` state.
2. Write the owner-approved behavior table for eligible controlled body, unrelated vanilla player, Creative player, NPC, and spectator/carrier. Specify deliberate click pickup, whole-stack semantics for current slice versus any future partial transfer decision, drop behavior, no walk-over acquisition for eligible bodies, and unchanged unrelated routes.
3. Define a deterministic representative station fixture and baseline. Cover 20 typical and 100 worst-case player targets, low-thousands objects, compact vertically stacked floors, and dense Cargo/Botany/Chems piles. One machine with synthetic loads is only a reproducible proxy; clearly distinguish simulated watchers/actors from authentic connected clients and never describe it as a real 100-player test.
4. Establish the measurements in the contract, client visual capture conditions, ownership/conservation checks, and proposed budgets/headroom for owner decision. Audit all probe/collision-shape callback paths for chunk loads; demonstrate a safe no-force-load path or fail-closed alternative before accepting the policy, since the current loaded-only getter cannot constrain arbitrary callbacks. Record current known limitations, including the first-hit-only pile limitation (exact-distance ties are UUID-ordered) and non-crash-atomic transfer.

**Exit:** owner reviews the behavior/measurement contract and accepts or revises numeric budgets. No performance target is claimed until measured and agreed.

### M1 — Mod-owned ground presentation and entity lifecycle

Prototype distinct, authored/project-owned 3D visuals for mod-owned loose objects only; vanilla item rendering remains unchanged. Decide readability across small items, large objects, stacked/overlapping floor items, lighting, occlusion, and dense views. Define a bounded model/variant/transform policy and avoid a unique unbounded render path per stack. Verify client appearance in actual viewports; do not infer it from assets or screenshots alone.

Specify and test mod-owned loose-item retention and despawn policy explicitly (including boundaries around vanilla behavior); do not change global vanilla item despawn/retention behavior. Test sustained lifetime across chunk unload, save, and restart, in addition to stationary/settled, moving, merged, split, picked up, dropped, reloaded, and removed states. Determine whether a relevant idle/sleep state exists or needs implementing; do not assume Minecraft has a built-in asleep `ItemEntity` state. Establish item size/fill/readability policy and stack merging boundaries, including identity/components that must not merge. Distinguish server entity ticking from stationary visuals and from render/network update rate. Keep the baseline as entities; if evidence motivates an alternate representation, defer it to the explicit gate in M5.

**Exit:** representative items remain legible and preserve exact stack/component identity and the agreed mod-owned retention/despawn policy through sustained lifetime, chunk unload, save/restart, and client watch changes; no global renderer or vanilla lifetime regression. Owner visually accepts the mod-owned subset.

### M2 — Bounded pile picker and authoritative transfer

Design a bounded client nomination/selection contract for piles so a click can select the exact visible member rather than silently selecting an arbitrary first hit. Specify stable member identity, deterministic sort/tie policy, maximum candidate count and payload, selection feedback, stale list behavior, reach/occlusion, and behavior when the pile exceeds bounds. Client nomination is never authority. Server re-resolves the authenticated actor and exact controlled body/epoch, loaded target and current ownership; validates reach, visibility, pickup delay/eligibility, hand/revision and the chosen member immediately before transfer.

Keep the current whole-stack transfer unless the owner separately approves partial-stack semantics. Define simultaneous-click races and failure outcomes without implying crash-atomicity; preserve the current recovery-required/admin-recovery caveat. Audit/suppress only eligible body's bypass acquisition routes, including vanilla walk-over/playerTouch and other identified direct routes. Prove unrelated vanilla and Creative pickup remain unchanged. No forced chunk loads is the target policy, not a proven property: audit all collision-shape callbacks and gate acceptance on a demonstrated safe probe path or fail-closed alternative; stale/unloaded targets fail closed.

**Exit:** dedicated tests show exact nominated-member resolution, bounded work/payload, stale/ambiguous/occluded/unloaded denial, and two-actor contested-pile conservation (no duplicate/loss for observed outcomes). Connected two-client contest and unrelated/Creative checks are owner-run acceptance, not assistant-performed gameplay.

### M3 — Collision policy and body movement integration

Implement and document a category matrix independent of art: small loose item/jug (world collision, no body blocking); large unanchored vending-machine-sized object (world collision and body blocking); deliberate shove and pull; anchored fixture. Specify mass/shape, step-up, floor contact, stacking/jostle, projectiles, and what happens at station walls/doors. Deliberate shove/pull is a separately approved action policy; do not accidentally make every world collision shove a body or fixture.

Exercise fixtures under vanilla actors and the actual possessed `PlayerCharacterHarnessEntity` using its grounded world-step path. Cover collision resolution, stepping, blocked movement, projectile impact, stacked items, and anchored-versus-unanchored distinction. Verify this does not activate or change unsupported movement modes.

**Exit:** agreed matrix is demonstrated in server-world tests and owner-connected observation; no inference from generic collision tests to the controlled body.

### M4 — Pulling with stable entity ownership

Specify pull eligibility, actor authority, range/line-of-sight as applicable, constraint/action-state creation/removal, movement state, obstruction, target idle/waking, chunk seam/unload, logout/body lease or stun invalidation, and cleanup on entity/body removal. Keep the existing loose entity as the pulled object; do not swap its representation or place it into body `LIVE_HANDS`. SS14's distance joint and separate virtual hand item are conceptual references only; upstream virtual hand-item creation is conditional on `NeedsHands`, so qualify the analogy against the exact upstream revision. Audit Minecraft/project support and develop a suitable physics constraint or other pull route, or explicitly reject pulling if unsupported; do not claim an existing entity distance joint or separate virtual hand item is available. `LIVE_HANDS` currently stores item tokens and real `ItemStack`s for held items and has no local pull-hand reservation. If one is proposed, M4 must design and gate held-item conflicts, persistence, body epoch invalidation, hand occupancy, and virtual-item policy before use. It is not a carrier and must not duplicate the object. Specify visual status and how it differs from a held item. Pull is not pickup and does not bypass M2 acquisition rules.

Test conservation and cleanup through pull start/stop, denied/stale intents, stun/lease loss, reconnect, body removal, target unload, save/restart boundaries, and contested target actions. Define the unloaded-seam policy as bounded/fail-closed and no force-load.

**Exit:** if pulling is supported, entity identity/ownership remains stable through its lifecycle; the chosen constraint/action state cleans up, and no pull path duplicates or consumes the item. Unsupported pulling is explicitly rejected rather than asserted implemented.

### M5 — Scale verification and conditional representation gate

Repeat M0 measurements with the M1–M4 behavior. Run the 20-player typical and 100-player worst-case fixture on one machine with clearly labeled synthetic versus authentic watchers/actors; test authentic multi-client samples where available. Include dense Cargo/Botany/Chems piles, clustered active mass-jostle, mostly settled populations, vertical floors, join/watch bursts, chunk seam movement/unload/reload, and save/stop/restart. Measure server p95/p99 tick, network bytes/queues, client viewport frame times, memory and persistence costs as defined in M0. Include sustained mod-owned item lifetime, retention/despawn policy, chunk unload/save/restart tests without global vanilla lifetime changes, two-client contested pile, and owner-only connected visual/interaction checks. Reconfirm the audited safe no-force-load path or fail-closed alternative.

**Records/hybrid alternative gate (optional; requires owner decision):** only investigate a record/hybrid representation if repeatable measured evidence shows the all-entity baseline misses an owner-agreed budget and identifies which cost is responsible. Present entity tuning/merge/render/network alternatives and costs first. Owner must explicitly choose whether to prototype records. If chosen, scope transitions narrowly and test record↔entity spawn, pull/release, pickup/drop, chunk unload/reload, save/restart, crash window policy, identity/components, and concurrent ownership conservation. A record pattern elsewhere in this codebase is not sufficient rationale. Without measurement and owner approval, retain entities for resting and pulling.

**Exit:** publish measurements with workload, hardware, limitations, and agreed headroom; owner decides accept/revise/defer. No unsupported guarantee for 20/100 players. No representation switch absent the gate and transition evidence.

## Owner decisions before implementation authorization

- Confirm this plan's status and authorize a specific milestone; this document itself authorizes none.
- Confirm eligible actor categories and deliberate click-only pickup, exact-pile UX/bounds, current whole-stack transfer, and no walk-over pickup. Confirm bypass routes to suppress narrowly and unchanged vanilla/Creative behavior.
- Approve object category/collision table, deliberate shove policy, anchored fixture boundary, and whether large loose objects are actually item-like entities or a distinct object system.
- Choose visual direction and asset ownership/licensing process; no SS14 assets are copied by this plan.
- Set measurable server/client/network/persistence budgets and headroom after M0 evidence; say whether 20 TPS means a 50 ms total tick interval and what subsystem share is acceptable.
- Decide whether synthetic 100-player loads are sufficient for any particular gate, and what authentic connected-client evidence is required. Neither one is interchangeable with the other.
- Decide whether to open M5's record/hybrid gate only if measurements warrant it. Default recommendation is entities at rest and while pulled.
- Confirm failure/recovery language: known compensation is not crash-atomicity, and an ambiguous recovery-required result may require operator intervention.

## Acceptance priority and evidence boundaries

1. **Correct ownership and unchanged actor boundaries:** no duplicate/lost stack in tested success/failure/race cases; eligible controlled body only; unrelated and Creative behavior preserved.
2. **Playable usability:** exact intended overlapping member selectable with clear feedback, deliberate rather than walk-over pickup, readable mod-owned floor visuals, and understood body collision/shove/pull rules.
3. **Lifecycle:** save/restart, measured idle/active transitions (and sleep/wake only if implemented), chunk seam/unload/reload, pull cleanup, and no force-load behavior pass their declared tests.
4. **Measured scale:** report real repeatable results against owner-agreed budgets; synthetic simulation is labeled, and no player-count guarantee is inferred from it.
5. **Presentation quality:** 3D assets/viewport readability and unrelated vanilla/Creative rendering remain owner-connected checks.

Useful next references: [hands/inventory handoff](../hands-inventory-program/current-handoff.md), [historical ground-item decision](../character-capabilities-sprint/ground-items-decision.md), and [power sprint handoff (alternative records pattern)](../power-sprint/current-handoff.md). Those documents provide context, not authorization or a substitute for this plan's gates.
