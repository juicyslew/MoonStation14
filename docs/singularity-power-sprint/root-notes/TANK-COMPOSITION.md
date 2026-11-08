# Portable plasma tank composition

## Verified upstream composition

Reference checkout `C:/Users/William/Documents/SS14-dev/space-station-14` is pinned to `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

`PlasmaTankFilled` in `Resources/Prototypes/Catalog/Fills/Items/gas_tanks.yml` inherits `PlasmaTank` (`Resources/Prototypes/Entities/Objects/Tools/gas_tanks.yml`), which inherits `GasTankBase`. `GasTankBase` inherits `BaseItem` (`Resources/Prototypes/Entities/Objects/base_item.yml`). Upstream `BaseItem` declares Item size `Small`; `GasTankBase` overrides Item size to `Normal`. The portable tank also inherits base physical/collision/sprite/pullable components, tank use/UI, atmosphere device, gas-tank, explosive, melee and composition/price behavior. PlasmaTank overrides its sprite/item/clothing sprites and release pressure to 101.3 kPa; the base release pressure is 21.3 kPa. Filled child overrides air with 5 L, 293.15 K and 2.051379050 mol plasma. The 5 L volume is 0.005 m³ locally.

The local supported composition is intentionally narrower: `gas_tank_base` → `plasma_tank` → `plasma_tank_filled` resolve finite volume, temperature and initial gas species. The one registered Minecraft item is `moonstation14:plasma_tank`; the profile `moonstation14:plasma_tank` yields the empty stack and `moonstation14:plasma_tank_filled` yields the filled stack. `GasTankItem` consumes the resolved server/client catalog in stack factories; default instance uses the empty profile. Contents are immutable `GasMixture` snapshots held by the persistent/network-synchronized `GAS_TANK_CONTENTS` data component once materialized. A vanilla `new ItemStack(PLASMA_TANK)` with no stored component gets a read-only projection of resolved empty-profile contents through `GasTankItem.contents`; reads never write components. The first copy-on-write mutation copies metadata and materializes that projected profile plus changed gas only on the returned stack. Stored contents with an unknown profile or profile-inconsistent capacity fail closed. Tank capacity is stable across writes, stack count is one, and non-tank items do not advertise gas capability.

## Deliberate departures and limits

The vanilla item has no local wearable/back-slot mapping. The local family does not claim upstream breathable internals, pressure release valve/UI, pressure damage, low-pressure warning, explosion, melee damage, atmosphere-device join behavior, material/price, or gas-tank clothing behavior; no inert fields are added for these unsupported systems. There is no refill consumer, gas transfer, collector workflow, radiation, or broader reactor integration yet. The item has a flat RSI-derived inventory/held icon only; no new 3D held model or GPU review is claimed.

Local item-footprint metadata uses the inherited `Normal` item size and a four-cell 2x2 footprint. This is a storage-adaptation profile, not a claim that SS14 has the same Minecraft inventory grid.

## Validation targets

`GasTankPrototypeTest` exercises production resource inheritance and resolved empty/filled factory contents (base defaults plus filled-child species); `GasTankContentsCodecTest` covers JSON persistence/network stream data and immutable capacity. The tagged native stack persistence check `gas-tank` in `GasTankPersistenceGameTests` exercises raw vanilla stack projection, live profile-default overrides, non-mutating reads, first copy-on-write materialization, metadata preservation, fail-closed unknown profiles, and vanilla ItemStack NBT round-trips.

## Asset provenance

See [`assets/moonstation14/provenance/plasma_tank/ATTRIBUTION.md`](src/main/resources/assets/moonstation14/provenance/plasma_tank/ATTRIBUTION.md). The flat icon comes from pinned SS14's `Objects/Tanks/plasma.rsi` icon state; its source metadata's CC-BY-SA-3.0 license and TGStation commit/Princess-Cheeseballs credit are recorded there.
