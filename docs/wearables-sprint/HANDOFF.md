# Wearables and equipped storage plan

## STATUS

Reconstructed 2026-10-03. Original plan was not implementation approval; no independent wearables closure found. **Later hands/inventory owner closure delivers equipment slots and wearable storage for its scope.** Old unaccepted-prerequisite/absent-equipment wording is superseded there.

## ARCHITECTURE

Body LiveHands owns hands/equipment slots. Bags/belts own GridContents; equipping exposes access, not body ownership of contents. Authenticated server intents route equip/unequip and equipped-grid transfers.

## IMPORTANT FILES

`Instructions.md`, `../hands-inventory-program/HANDOFF.md`; under `src/main/java/com/juicyslew/moonstation14/ms14/`: `hands/live/{BodyEquipmentTransfer,BodyEquippedStorageTransfer}.java`, `storage/grid/GridContents.java`, `hands/network/BodyHandRequestService.java`.

## SS14 CORRESPONDENCE

Plan observes human pocket1/2 dependence on jumpsuit and suitstorage on outer clothing/access, plus belt/back slots. This does not establish local universal garment-pocket parity or that upstream pocket contents physically belong to garments.

## DECISIONS / WHY

Keep storage item-owned and transfers server-validated. No nested storage. Retired pouch has no playable item/UI. Program's explicit interaction adaptations govern delivered behavior.

## KNOWN GAPS

No standalone wearables-specific acceptance or universal clothing/pocket/protection implementation inferred. Program closure does not certify every authenticated mouse sequence. New garments and dependency rules require their own resolved-prototype/runtime mapping.

## VALIDATION

Later program records 1,821 unit tests and all 401 GameTests passed; final renderer-only changes were not server-rerun. These are overall delivery results, not a dedicated wearables run. Reconstruction runs no tests.

## NEXT WORK

Use the owner-closed hands/inventory baseline. Additional clothing/pockets/parity work needs explicit scope; this historical plan does not authorize it.
