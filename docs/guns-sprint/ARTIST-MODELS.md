# Imported artist gun models

Owner authorized the accepted imports below; the temporary-asset hold is lifted for these model groups. The source runtime packages were imported byte-for-byte and their official completed import receipts recorded 110/80/70 destination hashes matching at import (260 runtime files total). That statement applies to original imports only: subsequent ten ROOT-only gameplay-binding metadata edits are documented in [ROOT-BINDINGS.md](ROOT-BINDINGS.md), so current ROOT files are not asserted byte-identical to the original imports. No geometry, UV, or pose edits were made here.

| Group | Request / revision | Snapshot | Delivery digest | Runtime files |
|---|---|---|---|---:|
| Magazine-fed | `guns-cartridge-handle-20261105-r4` / `cartridge_handle_r4` | `761e364c9d9c663c4e623d1bdc3f647ef4b504f976e21cc9ff77f42b4c03f0f7` | `9c25de37928a776921d4c7db7dd7cbab6c5d090b6a4df68ad79a8bbd6fc2398` | 110 |
| Energy / Svalinn / barrel texture | `guns-energy-svalinn-barrel-texture-20261105-r3` / `svalinn-source-barrel-texture-r3` | `27d111bd17c985da265bab0813891a4fb18d4685aecbb7689ac51aec868d3d9c` | `40208e261448b508e430753e2121b2c34a82168363827f6139a7164111a4c0ff` | 80 |
| Manual feed | `guns-manual-feed-20261105-r1` / `manual_feed_source_family_r1_colocated_layout` | `27bc4c00f786204a88c90a2e893d12a9adcb2dfeda2bd6eb02b4db745b929e9c` | `6dd730d21b45465e7c7327eb9579c97eb80e40bc0af8f6acc2cc3d4e336054a5` | 70 |

Receipts in the three respective `build/guns-sprint/artist-import/<request>/owner-receipt.json` files record the owner's import authorization for matching digests; they are not themselves installation verification. Verified manifests and import plans remain in those staging directories, and the official workflow retains completed import receipts. CC-BY-NC material remains restricted to noncommercial use; no broader license is implied.

Original attribution texts were copied byte-for-byte after verification against their `verified-metadata.json` sidecar hashes. Documentation copies: `artist-models/magazine/magazine-source-attribution.txt`, `artist-models/energy/energy-source-attribution.txt`, `artist-models/manual/manual-source-attribution.txt`, and `artist-models/manual/manual-export-attribution.txt`. Packaged copies: `assets/moonstation14/models/item/guns/artist-magazine-attribution.md`, `artist-energy-attribution.md`, `artist-manual-attribution.md`, and `artist-manual-export-attribution.md`. Source README statements that attribution was historically not imported predate the subsequent owner-authorized import and these copies; original texts remain unchanged. No relicensing is implied.

The 12 replaced temporary visuals are the eight listed weapon icons plus four accessory models (`magazine_pistol`, `magazine_rifle`, `speedloader_magnum`, `power_cell_small`). The five original ammo/casing icons and their spent states remain temporary source/UI assets, not artist imports.
