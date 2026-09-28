# Power Distribution and Floor-Substrate Sprint

This is a project-local implementation guide for a bounded, server-authoritative power distribution foundation and the floor substrate/tile layer required to route visible floor cables. It is a design handoff, not evidence that any listed Minecraft/NeoForge API has been verified. Implement only after checking the exact pinned platform APIs and current call sites. Keep atmospherics and player-character work with their concurrent owners; do not edit their code or docs as part of this sprint.

## 1. Goal, scope, and decisions

### Owner-approved design decisions

- Pin implementation to Minecraft **1.21.1** and NeoForge **21.1.224** (the repository's `gradle.properties`). Do not infer APIs from another release.
- A cable is an attachment to a host block **face**. It does not replace the host block, consume its block position, or make a solid host conductive through its volume. One host may independently carry cable on more than one face.
- For an eligible station floor/substrate, a cable attached to its top face is initially exposed. Installing the separate removable floor tile normally hides that same cable; removing the tile reveals that same cable in place. Tile presentation changes must not delete, move, or silently recreate cable data. Tile placement may invalidate/recompute visuals only. Cable on wall and ceiling faces remains exposed, regardless of floor-tile state. Removing/replacing the cable's host is different: it can invalidate/remove the attachment and change graph topology under an explicit lifecycle policy.
- Cable-to-device connectivity is based on an explicitly defined adjacent cable/device face/contact rule. Do not require or render a device plug. Visual device plugs are not part of this sprint.
- Cables turn around genuine block edges when compatible face attachments meet under the defined adjacency rule. There is no implicit connection through a solid block, diagonally, through a closed boundary, or between unrelated vertical floors. Cross-floor continuity requires an explicit multi-floor riser made of real cable-face segments.
- High voltage (HV), medium voltage (MV), and APC/low-voltage are distinct network classes. A junction or adjacency does not convert voltage. The bounded system includes an explicit HV→MV substation bridge and an MV→APC battery-backed APC; these are typed devices/ports with explicit behavior, not implicit graph merges. Other cross-tier conversion is excluded unless separately approved.
- SS14 cable sprites may be used only after file-by-file license/provenance review. The reviewed source directories are `Resources/Textures/Structures/Power/Cables/{lv,mv,hv}_cable.rsi`: local `lv`/`mv` assets are CC-BY-SA-3.0 from tgstation commit `fcf375d7d9ce6ceed5c7face899725e5655ab640`; `hv` is CC-BY-SA-4.0 from PJB3005. Images have not yet been imported. A per-file actual-import ledger with exact file/frame, attribution, modification, and notice details is still due before import; the mod's MIT license does not relicense assets.
- ProjectRed for Minecraft 1.21.1 is an architectural reference only (MIT). Its multipart/dependency model is not required, and no ProjectRed runtime dependency is approved by this guide.
- Target station-scale efficiency without one ticking BlockEntity per cable and without forcing chunk loads. Server state is authoritative. A UI is deferred until the power core exists and local UI/menu APIs and lifecycle have been audited.
- `MS14Provider` is appropriate for ItemStack/Entity/BlockEntity attachment data where that holder model fits. Its abstraction does not imply that an ItemStack attachment is a world cable graph, nor does it provide graph indexing, chunk ownership, persistence policy, or network simulation.

### In scope

- Inventory of local registration, block-entity, provider, persistence, rendering, and game-test patterns; exact-version platform API audit before implementation.
- A distinct eligible station substrate/floor block and removable tile item/layer, with accessible placement and cutting/removal interactions (including a bounded cable-cutting tool interaction) and the cable-visibility semantics above.
- Face-oriented cable data and block/edge rendering, persistence/synchronization, explicit adjacent network discovery, and minimal server-authoritative device interaction/mutation.
- Distinct HV/MV/APC network identities; explicit riser segments, HV→MV substation and MV→APC battery-backed APC bridges; bounded generation/distribution/storage/consumer behavior, lifecycle handling, and focused automated tests/benchmarks.

### Out of scope

- Atmosphere/gas/temperature ownership or changes; character/player identity/control changes; UI, menus, screens, cable plugs, broad electrical simulation, realistic AC, arbitrary/untyped transformers or batteries, explosion/short/fire gameplay, automatic placement of hidden cable, and a required multipart library. Full fuel handling and elaborate generator systems are optional follow-up after platform audit, not prerequisites for the bounded station power loop.
- Replacing hosts with cable blocks, storing one ticking BE per cable, force-loading chunks to complete a network, or claiming SS14/ProjectRed parity.
- Any change outside the new `docs/power-sprint/` guide during this documentation-only task.

## 2. Local baseline and architecture constraints

- `gradle.properties`: Minecraft and Parchment target 1.21.1; NeoForge is 21.1.224. `build.gradle` uses Java 21, ModDevGradle 2.0.141, a dedicated `gametest` source set and server `gameTestServer` run. Future implementation should add focused tests there and run narrow checks first.
- `ModBlocks` uses deferred block registration and helper registration of `BlockItem`s; `ModBlockEntities` registers BEs per supported block type. Keep registration consistent, but do not interpret this pattern as a need for a BE on every cable.
- `MS14Provider` reads detached defaults without materializing absent data, has holder-specific updates, and exposes snapshots/change-only update helpers. Reuse it when adding attachment-like data to a legitimate holder, preserve change-only synchronization, and inspect existing `SystemLink`/attachment codecs before adding another store.
- An ItemStack is an item holder, not a world topology record. Cable networks must be indexed/resolved from authoritative loaded world faces (or an explicitly designed world/chunk-owned index); never pretend attaching data to an item creates or persists a world graph.
- Existing atmosphere work is separately owned and already has chunk attachments, bounded searches, queues, and specific unknown/unloaded policies. Do not borrow its gas model or edit/integrate its implementation without its owner. Character/player lifecycle work is also separately owned.

## 3. Implementation sequence and independent milestones

Each milestone has a bounded deliverable and can be reviewed/tested in isolation. A later milestone consumes accepted earlier contracts; it can be started in a separate branch/session without requiring UI or unrelated atmos/player work. Stop at an API or owner-decision gate instead of guessing.

### M0 — local evidence and exact-version API audit

Inspect the actual Minecraft/NeoForge 1.21.1/21.1.224 APIs for block-face attachment representation, block-state changes, BE lifecycle if any, chunk load/save/sync, model/render-layer or block-entity-renderer options, neighbor updates, and GameTest support. Inventory eligible floor blocks, block placement/removal hooks, chunk unload behavior, current attachment codecs, and registration. Verify how client rendering obtains authoritative face state without introducing per-tick BEs. Record API classes/signatures from local mapped sources/docs and tests; these are presently **unverified**. Check available legacy build/test conventions, rather than assuming an interface name from this design document.

**Gate:** write down concrete version-pinned storage, sync, render, and lifecycle seams and identify any ambiguous owner policy before implementation. No speculative API claim is accepted as proof.

### M1 — substrate, tile items, and accessible interactions

Add only the eligible station substrate and separate tile item/layer with accessible placement and cutting/removal interactions. Demonstrate that tile placement/removal changes presentation and may invalidate visuals only, while leaving host identity and cable data intact. Define eligible substrate via a typed tag/property/configuration contract, not a broad “any solid floor” guess. Preserve the substrate on tile removal. Specify server-authoritative validation for placement/removal, permissions, and drops; test save/reload and no accidental host replacement. This milestone does not require power generation or graph traversal.

### M2 — face cable storage and rendering

Add cable data addressed by `(dimension, host position, face, voltage class)` or an equivalent typed key. Store only placed cable state; empty reads do not materialize persistent data. Define one canonical host-face attachment and edge geometry so adjoining face segments draw a continuous bend over a genuine edge without replacing host blocks. Top-face cable on the eligible substrate is exposed with no cosmetic tile and hidden with the tile installed; wall/ceiling cable stays exposed; removing a tile reveals unchanged cable. Include rotations, chunk boundary visuals, server-to-client updates, and save/reload tests. No network simulation or cable BE tick loop is required here.

### M3 — bounded topology and voltage separation

Build an authoritative, loaded-world adjacency resolver over face cable records. Specify exact compatible face pairs for straight continuation, edge turns, device contact, and risers. Adjacency cannot tunnel through a solid block or infer an absent face segment. Unloaded neighboring chunks are unknown boundaries: defer/retry bounded discovery; never force-load and never report a disconnected/complete result as proven when a frontier is unknown. Keep graph traversal bounded, cycle-safe, deterministic, and invalidated by cable/host/device/chunk changes; tile changes may invalidate visual state only and must not mutate graph membership. Keep HV, MV, and APC identities separate except at the explicit typed HV→MV substation and MV→APC APC-device ports, whose per-port graph boundaries must be tested.

### M4 — bounded station power loop, storage, and APC

Only after topology passes, add bounded source/load accounting for generation→distribution→storage/APC→consumer: a minimal source, ordinary MV distribution/load, the explicit HV→MV substation bridge, and an MV-fed battery-backed APC on the APC tier. Define typed ports and bridge/storage behavior; server decides allocation, charge/discharge, and energized state, while clients only present synchronized results. Include the accessible basic device interactions needed to place/configure/remove these devices, with all mutations validated server-side. This milestone delivers the complete bounded station loop, not just a tiny source-to-load proof. Avoid a per-cable tick BE: event-driven dirty component rebuilds and/or bounded scheduled work are preferred, with an explicitly capped queue and fair continuation. Do not force chunk loads. Record cadence, capacity/units, allocation policy, storage limits, and unknown-frontier guarantees before implementation; exact fuel simulation or elaborate generator controls are optional after platform audit. No UI is required; UI remains optional after the core and a separate local UI/API audit, unless the owner separately approves it.

### M5 — station-scale benchmark, reliability, and handoff

Exercise complete placement → cable → adjacency → source/distribution → substation → storage/APC → consumer and removal/reload flows, both explicit cross-tier bridges, tier mismatch outside bridges, tile conceal/reveal, cutting/placement interactions, genuine edge turns, explicit riser continuity, solid/diagonal nonconnections, chunk boundaries/unload/reload, and malicious client attempts. Run automated checks and a separate benchmark/reliability pass over representative station-scale layouts: report measured work/memory/update bounds, sustained-edit progress, persistence/recovery, and behavior at unloaded boundaries. Produce the actual-import asset ledger and support/deviation matrix. Manual in-game smoke remains an owner task and must not be reported as performed by the assistant.

### Forward note — M14–M16 follow-up

Current implementation has moved beyond the original M2 contract: schema v2 allows all three tiers on one host face (reading v1 as a migration source), procedural visuals use tier-offset lanes through same-face/quads and corner projections, and first-spawn resync has bounded staging plus 30→60→120→200-tick retries. The M14–M16 audits document these changes and their remaining async/session-identity limits. Keep manual appearance/first-spawn acceptance and any future SS14 art import as separate gates; do not infer them from automated tests.

## 4. Test and performance acceptance

### Focused automated coverage

- Substrate/tag eligibility and separate tile-item behavior; accessible placement/cutting is server-authoritative; tile changes preserve underlying host and exact cable-face data (host removal follows its separate graph invalidation policy).
- Cable face persistence/sync and rendering state for floor (visible → hidden → same cable visible), walls, and ceilings. Host blocks remain unchanged.
- All six face orientations, rotations and true-edge corners; valid adjacent device contact works without a rendered plug; no same-cell/diagonal/through-solid accidental link.
- Explicit riser connects only with each intentional segment; no implied Y connection between floors.
- HV, MV, and APC components remain distinct except at the explicit HV→MV substation and MV→APC battery-backed APC; verify bridge ports, direction/behavior, storage, and that unrelated tier mismatches cannot energize/load across boundaries.
- Graph cycle safety, change invalidation, deterministic component membership, chunk-edge connectivity, unknown unloaded frontier behavior, restart persistence, and bounded continuation without forced loads.
- Server-authoritative source/load/storage state and device interactions; client inputs cannot fabricate cable, change tier/bridge/storage state, or claim power. Dedicated-server classloading remains safe.

### Scale and budgets

Use generated station-scale systems, including sources, HV/MV substations, MV runs, APC batteries/loads, long lines, dense junctions, cycles, repeated edits, and disconnected/unloaded chunk seams. Record cable/device counts, traversal/rebuild and storage work per server tick, queue high-water mark, memory/index footprint, save payload, recovery behavior, and client update volume. Set and test explicit per-tick work and queue limits before claiming performance. Require progress/fairness under sustained edits, coalescing of duplicate dirty work, no per-cable ticking, and zero forced chunk loads. This is a distinct benchmark/reliability milestone, not merely a tiny source-to-load proof. No numeric CPU/player target is claimed before measurement; owner acceptance of an eventual target belongs in the implementation report.

No build, server launch, GameTest execution, or manual game testing is part of this documentation-only task.

## 5. Validation and risks

For the future implementation: run focused unit/GameTests first, then `./gradlew.bat test --rerun-tasks --no-daemon`, `./gradlew.bat build`, and the configured GameTestServer with an isolated game directory. A successful compile alone does not establish world lifecycle, chunk seams, client rendering, or scale. Preserve logs/counts; manual client smoke is owner-run.

Risks requiring explicit treatment:

- **Host state and migration:** block replacement, vanilla flooring, state codecs, old worlds, tile removal, and modded host changes can orphan/corrupt face cable state. Define migration or explicitly no compatibility, and fail safely.
- **Chunk ownership:** graph state must not duplicate inconsistently across chunk boundaries. Unloaded/unknown edges cannot trigger forced loads or false “off”/“on” claims; persistence and retries must be bounded.
- **Topology invalidation/security:** stale caches, client-spoofed edits/tiers, maliciously large networks, queued work, and duplicate edge visits can cause incorrect power or server denial of service. Validate all mutation server-side and bound work.
- **Visuals:** tile overlay ordering, neighbor occlusion, resource reload, rotations, chunk edges, and face transforms may differ from available 1.21.1 APIs; validate in engine before claiming fit/finish.
- **Asset licensing:** SS14 art is not automatically available merely because SS14 is referenced. Audit each RSI and selected frame/file, exact upstream URL/revision/path, author/attribution, license, modification status and required notices; omit unclear assets until resolved.
- **Unverified APIs/semantics:** this guide intentionally does not name presumed NeoForge storage/render callbacks or prescribe concrete transfer/current rules. The M0 audit must settle these against local 1.21.1/21.1.224 sources.
- **Cross-owner work:** atmosphere and character/player changes have separate concurrent owners. Reconcile interfaces only through explicit review; do not alter their files to make power tests pass.
