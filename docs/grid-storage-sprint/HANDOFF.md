# Grid storage foundations

## STATUS

Reconstructed 2026-10-03. Original Instructions are a future plan with G0/G1 kernel audits, not standalone implementation approval/closure. **Live grids were subsequently delivered and owner-accepted within hands/inventory**; use that program handoff for current state.

## ARCHITECTURE

GridContents belongs to storage ItemStacks, not bodies. Live body/hand/world routes validate owner, token, revision and grid geometry server-side. Pure GridGeometry is distinct from ownership/transactions. Current bounded content supports 8×8/64 cells and 32 entries; unknown footprints default to 1×1.

## IMPORTANT FILES

`Instructions.md`, `audits/g0-upstream-geometry.md`, `g1-pure-geometry.md`; `../hands-inventory-program/HANDOFF.md`. Runtime: `src/main/java/com/juicyslew/moonstation14/ms14/storage/grid/GridContents.java`, `hands/network/BodyWorldGridService.java`, `hands/live/BodyEquippedStorageTransfer.java`.

## SS14 CORRESPONDENCE

G0 records upstream geometry; G1 did not certify rotation/search-order, persistence, limits or prototypes. Later UI/transfer adaptations are explicit in the program handoff; do not infer SS14 algorithm equivalence from working grids.

## DECISIONS / WHY

Storage follows its item host across held/worn/world locations. Nested storage remains unsupported. Prototype/footprint and authority changes need live-consumer tests, not only pure geometry tests.

## KNOWN GAPS

Old absent-adapter/isolated-kernel gaps do not describe current delivery. Broad footprint mappings, reload semantics and exact upstream search parity lack separate certification. Program closure is not every mouse-route proof or crash atomicity.

## VALIDATION

Historical G1: successful build and 160/160 tests. Later program: 1,821 unit tests, 401/401 GameTests; final renderer-only changes not server-rerun. Distinct evidence, not standalone grid-sprint acceptance. No fresh tests run.

## NEXT WORK

Start with accepted program behavior and its extension proposals. Additional geometry/parity/catalog scope needs separate authorization, not completion of stale proposal gaps.
