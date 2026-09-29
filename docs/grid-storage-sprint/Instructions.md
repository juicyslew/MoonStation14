# Grid Storage Sprint — Instructions

**Status: future plan only; not implementation approval.** This is the pure grid-core sprint after the [hands contract](../hands-sprint/Instructions.md). Its main result is deterministic storage geometry and admission logic. It must not implement wearables, end-to-end interactions, a user menu, or claim upstream parity without a source audit.

## Boundary and evidence

Own a pure, bounded grid-storage model and its tests. A grid is a storage capability of an actual item/container (for example a bag or belt), never a body-owned inventory grid. Consume the accepted hands sprint's canonical item identity/location and transaction interface; the ledger records the item's location but does not own the storage contents separately. Do not edit existing unrelated sprint docs, lifecycle/body-control code, assets or the separately owned deferred device-UI proposal. No assets expected.

Local platform is Java 21 / Minecraft 1.21.1 / NeoForge 21.1.224 (`gradle.properties`). The local `MS14Provider`/`MS14Bridges` are component/attachment data seams, not storage or a transaction engine. See `../hands-inventory-program/Instructions.md` for local code evidence and invariants.

## Geometry and admission contract

- Represent storage as a bounded rectangular cell domain plus a bounded mask of approved/usable cells; never assume every cell in a storage bounding box is usable. Represent item footprints as bounded masks of occupied cells. Support regular rectangles first and explicit irregular masks only if owner approves; do not infer arbitrary upstream prototype compatibility from this choice.
- Support the four cardinal orientations (0°, 90°, 180°, 270°). Transform masks exactly and normalize coordinates deterministically. For regular rectangles, deduplicate rotations that produce equivalent footprints; retain orientation when it has semantic value to placement/state. Do not silently rotate on insertion unless the operation explicitly requests it. The pure contract is bounded cardinal geometry, not a claim to copy upstream's exact search algorithm or prototype parity.
- Placement is legal only when every occupied cell is in bounds, unoccupied, and allowed by storage/item rules. Reject empty/malformed masks, duplicate coordinates, dimensions/cell counts beyond configured maxima, overlaps and overflow. Define anchor/origin consistently. Geometry may be computed by a pure kernel, but the storage and its contents remain associated with the actual container item, not the wearer/body.
- Admission is server-authoritative: verify actor/body lease and access to the source/destination through the hands contract, then validate current capacity, item whitelist/blacklist/tag/size rules and requested orientation. Client geometry is advisory at most. Recheck at commit to prevent TOCTOU/stale admission.
- State exact limits for grid dimensions, footprint cells, total item count, nesting depth and request work before implementation. Reject container self-insertion and ancestor cycles; disallow or cap nested storage pending explicit decision. A grid search must be finite and deterministic.
- Rejected or failed placement leaves the item in exactly its prior location. Successful placement and removal use the shared atomic move/rollback contract; persistence stores canonical item identity, location and orientation without duplicating ItemStack snapshots.

## Milestones and pass/fail gates

### G0 — upstream and limits audit

Inspect the targeted upstream sources listed in [the G0 geometry audit](audits/g0-upstream-geometry.md). Record source revision and distinguish observed upstream behavior from this proposal; do not claim algorithmic copying or prototype parity. Owner must decide irregular masks/orientation scope and quantitative bounds. **Pass:** documented limits, source revision, behavior comparison, and accepted hands contract. **Fail:** ambiguous bounds or no source-of-truth item location.

### G1 — pure grid kernel

Implement/test only after owner authorization: immutable or safely encapsulated grid snapshots; regular footprints; optional explicitly approved irregular masks; four cardinal transforms (with equivalent regular-rectangle rotations deduplicated); occupancy, place/remove, deterministic fit query, and bounded admission results. Storage's usable-cell mask may describe any approved subset of its bounded domain; bounding boxes are not implicitly filled. No Minecraft UI or world mutation in the kernel. **Pass:** deterministic outputs, all invalid geometry rejected, no overlap/out-of-bounds/placement on forbidden cells, bounded complexity proven for maximum grid/item dimensions. **Fail:** nondeterministic fit, unbounded search, or ambiguous mask/orientation semantics.

### G2 — server storage adapter and transactions

Integrate with canonical item locations and hands API. Validate authority, whitelist/admission, revisions and capacity on server; commit atomically with rollback; persist/load validated state; reject stale, malformed, repeated and unauthorized requests. Do not add a generic menu. Define failure recovery for corrupt saves and removed/invalid prototypes. **Pass:** dedicated-server tests show conservation through hands ↔ item-owned grid/world, failed insertion/removal, partial/invalid persistence and load; other holder paths cannot duplicate contents. **Fail:** item is stranded, duplicated or lost on any rejected/rolled-back operation.

### G3 — handoff to wearables

Publish the bounded storage interface and invariant suite for belt/bag holders. **Pass:** owner accepts adapter contract and the wearable sprint can add holders without forking geometry/admission or location semantics. End-to-end hand/equip/store user flows remain for wearables plus the separate UI/connected gate.

## Test matrix (implementation stage)

| Category | Minimum cases |
|---|---|
| Geometry | regular 1×1 and multi-cell footprints, edge fit, all boundaries, 0°/90°/180°/270° transforms, non-square rotations, equivalent-rotation deduplication for regular rectangles, irregular disconnected/concave item masks and non-rectangular approved storage regions if approved |
| Occupancy | empty/full and sparse storage masks, adjacent cells, overlap, holes in storage/item masks, duplicate mask cells, empty/negative/oversized dimensions, overflow and deterministic fit ordering |
| Admission/ownership | allowed/denied item classes or whitelist, invalid/oversize items, capacity boundary, nesting/depth/cycle/self-insertion, stale revision and access denial; container identity remains the storage owner, not the body |
| Transactions | insert/remove/move from hand/world; destination race; injected failure/rollback; save/load and malformed saved grid; item quantity and unique-location conservation |
| Bounded work | maximum legal grid/item case, rejected extreme payload, request flood/rate limit; representative 20-player target profile |

Pure kernel unit tests are necessary but not sufficient for server adapter gates. Future implementation commands: `gradlew.bat test --no-daemon`, `gradlew.bat compileGametestJava --no-daemon`, and targeted dedicated-server GameTests. No manual gameplay testing for the documentation-only plan. UI/connected two-client acceptance must be separately authorized and gated.

## Risks and open decisions

SS14 upstream details may not map directly to Minecraft stacks and persistence. Irregular masks and four-way rotation increase state/validation complexity; nested storage creates cycles, recursive cost and save-size risks. Quantitative bounds, partial stack admission, merge policy, prototype migration and corrupt-save recovery require owner approval. Keep the pure core reusable by item-owned containers; do not attach an implicit grid to a body or infer that upstream dependent equipment slots physically contain pocket contents. The geometry audit records observed behavior only; implementation fidelity and prototype parity are not established. Deviations must be explicit and justified, not silently represented as upstream parity.

## References

Upstream paths listed in G0 are targeted references, not local evidence. Hands contract: [first sprint](../hands-sprint/Instructions.md). Program-wide ownership, security and platform context: [umbrella](../hands-inventory-program/Instructions.md).
