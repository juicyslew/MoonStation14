# G1 — isolated pure geometry kernel

**Scope:** bounded, immutable geometry and occupancy only. This is not live storage integration, an item model, or a claim of parity with upstream Minecraft behavior.

## Contract implemented

- `GridGeometry.StorageMask` models a bounded rectangular domain plus an explicit approved-cell mask. It rejects invalid dimensions, null/duplicate cells, and cells outside the domain.
- `GridGeometry.Footprint` accepts explicit occupied cells, rejects empty/duplicate/oversized/out-of-bounds-span shapes, and normalizes the supplied shape to origin. All four cardinal rotations are retained in 0°, 90°, 180°, 270° order, including equivalent rotations.
- `Snapshot` is immutable and contains opaque caller tokens plus placements. Placement succeeds only if every translated footprint cell lies in the storage mask and no existing placement occupies it. Rejected mutation results return the exact input snapshot. Remove is likewise pure.
- First-fit visits every origin in the bounded rectangular domain row-major (y, then x), testing rotations in 0°/90°/180°/270° order at each origin. Origins need not themselves be approved storage cells: only translated footprint cells must be approved. A snapshot precomputes occupied cells in an immutable-by-encapsulation bit index, so overlap checks do not rescan placements.
- With at most 256 domain cells and 256 footprint cells, first-fit checks at most 256 × 4 candidates × 256 footprint cells (262,144 cell checks), plus bounded snapshot-index construction. Non-overlapping nonempty placements number at most 256; no unbounded coordinate scan or repeated scan over all placements occurs.
- Translation uses widened arithmetic and rejects out-of-domain translated coordinates before narrowing. Input footprint span subtraction also uses widened arithmetic.

## Provisional limits

Implementation safety limits are **provisional pending owner approval**: maximum domain/footprint bounding dimensions of 16×16, no more than 256 cells in either explicit shape, and no more than 256 approved domain cells. These values are guardrails for this isolated kernel, not accepted product limits or an upstream constraint.

## G0 relationship and remaining integration risks

G0 observed upstream support for item masks, storage masks, rotation-adjusted shapes, and occupied-cell fit checking. This kernel uses those observations as context only; rotation conventions, normalization, search order, limits, prototypes, and persisted representation have not been verified for parity. Tokens are generic caller values and must retain stable `equals`/`hashCode` behavior while present in a snapshot; the kernel does not enforce that requirement. Integration still needs owner approval of limits/semantics, a mapping from real items to stable opaque tokens and shapes, persistence/migration decisions, admission and item-count limits, interaction with live storage operations, and concurrency/authoritative-state rules. No ItemStacks, attachments, UI, player inventory gating, prototypes, or live storage mutations are introduced here.

## Final validation record

After the latest hands bounds and grid changes, the coordinator ran `.\gradlew.bat test compileGametestJava --no-daemon`; it reported `BUILD SUCCESSFUL`. The coordinator also ran `.\gradlew.bat runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-hands-proof-final`, which reported `BUILD SUCCESSFUL`. At 18:56:42, `build/gametest-hands-proof-final/logs/latest.log` contained `160 GAME TESTS COMPLETE` and `All 160 required tests passed :)`. The later invalid optional token-only test change in `HandComponentTest` came after this GameTest run; a worker subsequently ran focused `HandComponentTest` and `compileGametestJava` successfully. No 160/160 result after that test change is claimed. These validation results do not resolve the provisional limits or integration risks above.
