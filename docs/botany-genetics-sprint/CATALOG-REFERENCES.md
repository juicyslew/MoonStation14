# Botany catalog source-reference index

`CATALOG-REFERENCES.json` is a source-reference inventory, **not** runtime data, an item/prototype registry, or a composition/parity claim. Rows preserve source YAML order and its `id`, packet prototype, ordered product prototypes, ordered mutation targets, RSI path, and growth-stage count (including the upstream default of 6). Source pin and path are recorded in the JSON; the checkout was verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Inventory

| Source measure | Verified count / note |
|---|---:|
| Seed IDs / rows | 71 |
| Distinct packet prototype IDs | 70 — `pea` and `worldPea` both refer to `PeaSeeds` |
| Product references / distinct products | 71 / 71; one product per row |
| RSI families | 70 — `fakeCapfruit` and `realCapfruit` share `capfruit.rsi` |
| Mutation edges / distinct target IDs | 34 / 31; every target resolves to one of the 71 seed rows |

These are references from `Resources/Prototypes/Hydroponics/seeds.yml`, not resolved packet or product entity compositions. `Content.Server/Botany/SeedPrototype.cs` defaults `PacketPrototype` to `SeedBase`, product and mutation lists to empty, and `GrowthStages` to 6; the catalog records per-seed values/defaults, not the resolved inherited entity composition. `bloonion` explicitly references `FoodBloonion` despite its unusual indentation in the source; it is included.

## Runtime and asset boundaries

The current typed runtime catalog remains the basic five registered families: wheat, tomato, potato, poppy, and aloe. The documented growth request is 65 new visual families; 65 grown requests do not audit all 20 new ordinary item-family assets. The separate item asset request names ten targets, while supply art/source assets are not fully audited by this reference inventory. See [PLANT-MODEL-BRIEF.md](PLANT-MODEL-BRIEF.md), [ITEM-ASSET-BRIEF.md](ITEM-ASSET-BRIEF.md), and [PARITY.md](PARITY.md) for their distinct scopes.

Product identity is not a generic-food classification. Source audit examples include `OatBushel`'s ruminant composition; banana/mimana peel and corn trash outputs; and mandatory special consumers or outputs for items such as killer tomato, gatfruit/capfruit, nettles, material-producing plants, bloonion, melons, and flower outputs. Do not label this whole catalog generic food or infer behavior from prototype ID/reference alone. This index does not establish full product/packet parent chains, gameplay composition parity, runtime registration/bindings, or asset licenses. Full product/packet composition and asset-license audit remains incomplete.

No item asset request is duplicated or submitted by this index; the named request files remain the references above. This document and JSON make no sprint-completion, readiness, or owner-acceptance claim.
