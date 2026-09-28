# Future gas and disposal piping milestones

**All milestones are proposed future work, independently reviewable, and not authorized by this document.** No milestone implies that gas piping or disposal tubes currently exist. Stop at any gate if owner consent, exact platform support, safety, or performance cannot be demonstrated.

## M0 — contracts, upstream, and host/API proof

- Obtain explicit project-owner authorization and consent from the atmos owner; identify separate disposal ownership as needed. Confirm protection/permissions and visual/privacy owners.
- Locate and pin the exact upstream SS14 revision for the prototype, unary gas, manifold, disposal tube, and construction graph reference paths in `architecture.md`; review relevant code and license/provenance without copying implementation or media.
- Verify exact local Minecraft/NeoForge APIs for clicked-block reach/face, sneak-use, block-state and collision/seal inspection, protection checks, world/chunk persistence, synchronization, transactions, render visibility, and unloaded frontiers.
- With the atmos owner, establish the actual `AtmosphereTopology` passability check and approved room-transfer API. Prove a supported full-solid wall remains collision- and atmosphere-sealing without adding a special property; fail closed for unprovable hosts. Resolve arbitrary vanilla/modded host policy.
- Agree geometry/overlap, capacities, transport semantics, and numeric bounds for separate gas and disposal systems. No production feature code or assets in this milestone.

**Gate:** written contracts, source evidence for every relevant API, testable host eligibility proof, owner consent, and settled open decisions. Otherwise defer.

## M1 — one-block gas sealed pass-through and explicit room transaction

- Implement only if authorized: one gas segment item installs one straight segment in the one clicked supported wall block under the exact sneak-use/reach/protection rules. Validate atomically server-side; incomplete ends are visibly/semantically capped.
- Represent a compatible completed connection as an explicit gas-network edge without changing the host, collision, atmosphere passability, or room topology. Add a separate room-facing vent/scrubber prototype only after atmos-owner agreement; use its approved API to transact gas to exactly one room.
- Bound persistence/sync to verified ownership and ensure no concealed segment data is sent to unauthorized clients. No automatic continuation, remote placement, scanner, or multi-block traversal.

**Gate:** focused tests show no vacuum, air exchange, room merge, host mutation, partial edit, unauthorized placement, or out-of-range placement; save/load and unloaded boundaries are safe.

## M2 — manual multi-block sequence, removal, and security

- Add only the explicit manual sequence for thick walls: physically remove earlier wall blocks to expose deeper positions, then rebuild and install/fill the wall-block segments from farthest to nearest.
- Resolve the removal method/input or tool, side/access, and cost through owner design. Do not recommend instant remote whole-wall dismantling. Staged removal (such as one block/segment per action) may be considered, but is not already decided; do not assume a removal interaction or invent a pry/drill tool. Destructive wall removal remains separate from pipe operations.
- Cover protection, host replacement/migration, chunk unload/reload, interrupted saves, stale records, incompatible endpoints, and breach only after actual final seal destruction.

**Gate:** multiplayer adversarial tests confirm no far-side placement, traversal, unauthorized edit, or implicit breach; recovery is deterministic.

## M3 — disposal independently owned flow and geometry

- Design and implement disposal separately with its own owner, segment identity, compatible ports, larger radius/lane occupancy, overlap rules, capacities, and transport (including whether/how entities are transported).
- Do not reuse gas mixture logic or assume generic item-pipe equivalence. Reuse only reviewed shared infrastructure primitives that preserve independent semantics and ownership.
- Test vertical runs, multi-floor risers, bends on exposed runs, occupied lanes, network boundaries, persistence, unload, and performance.

**Gate:** disposal owner accepts its transport/geometry model and gas/disposal interactions cannot cross types or capacities accidentally.

## M4 — multiplayer, scale, visuals, and owner acceptance

- Exercise multiple players (target budget: 20), dense networks, rapid placement/removal, host changes, loaded/unloaded frontiers, save/restart, and simultaneous edits. Profile bounded server, client, persistence, and sync work against numeric budgets.
- Verify ordinary concealed-infrastructure visibility and privacy separately from presentation. Coordinate future T-ray ownership, authorization, observer-specific data, and lifecycle as a dependency only; do not implement scanner here.
- Conduct owner manual acceptance for construction, ports/caps, vents, removal, protection, visible sabotage/capped ends, multiplayer interaction, and failure recovery.

**Gate:** recorded tests and measured budgets pass, relevant owners sign off, and remaining risks are explicitly accepted. Until then, do not claim system completion.

## Required focused regression coverage

- No accidental vacuum/air connection or atmosphere topology change on any wall crossing, including retrofit and failed/partial edits.
- No remote placement, far-side forced load, automatic traversal, suggested continuation, or multi-block action; exactly one clicked block changes.
- Suitable solid host eligibility and unsupported-host fail-closed behavior; wrong side, unreachable target, protection, and permission failures.
- Chunk unload/reload, sparse save/load, host replacement/migration, interrupted operation, endpoint mismatch, and stale state.
- Explicit separate wall removal and pipe removal; room seal remains unless player actually removes the final sealing block. A visible capped port communicates incomplete connection/sabotage risk.
- Gas-only transfer through an intentional room-facing device to one room; no implicit air route. Disposal lane/transport tests remain independent.
- Vertical/multi-floor runs, exposed bends, and no scanner or concealed-data leak to unauthorized viewers.
