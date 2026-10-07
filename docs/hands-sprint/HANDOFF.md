# Hands foundations and superseding delivery

## STATUS

Reconstructed 2026-10-03. H-series records describe capability/token-ledger foundations. **Later hands/inventory delivery is owner-closed on 2026-10-03**; `../hands-inventory-program/HANDOFF.md` is current authority for that scope.

## ARCHITECTURE

Current LiveHands persists real ItemStacks on the controlled body, with hand/equipment capability and server-authoritative intents. Storage grids belong to items. HandActorAuthority binds actor/body/epoch. Historical pure ItemLocation ledger is not the live inventory owner.

## IMPORTANT FILES

`audits/h0-authority-and-storage-ownership.md`, `h4-prototype-capability.md`, `h8-item-location-contract.md`; current program handoff. Runtime under `src/main/java/com/juicyslew/moonstation14/ms14/hands/`: `live/LiveHands.java`, `BodyHandTransfer.java`, `BodyEquipmentTransfer.java`, `BodyEquippedStorageTransfer.java`, and `network/BodyHandRequestService.java`.

## SS14 CORRESPONDENCE

Human left/right hand capability and handless pig were audited. Later program records deliberate either-hand drag nomination, direct child transfers without intermediate hands, and unsupported nesting. Historical ledger proposals are not upstream behavior implemented by fiat.

## DECISIONS / WHY

Vanilla Creative/account inventory stays separate from body hands. Tokens, revisions, revalidation and conservation govern transfers. Retired pouch has no playable item/UI; its legacy internal name does not indicate a feature.

## KNOWN GAPS

Cross-owner publication is staged/compensated, not crash-durable atomicity. Closure does not prove every authenticated route/mouse sequence or universal direct-API isolation. Old H0/H8 missing-live-inventory claims are superseded.

## VALIDATION

Later closure: 1,821 unit tests and all 401 GameTests passed; final renderer-only changes were unit-tested, not server-rerun. These are program-level historical results, not a dedicated H-series retest. No new suites run.

## NEXT WORK

Use the owner handoff and `future-extensions.md`; extensions remain proposals. Do not reopen delivered inventory scope based on older foundation gaps.
