# Generic armor capability

Equipment items may compose optional `armor` data, keyed by canonical local damage
type. Each modifier has `flat_reduction` (default `0`) and `coefficient` (default
`1`). For positive damage, each equipped slot applies
`max(0, damage - flat_reduction) * coefficient`; slots run in resolved inventory
order. Unspecified types and negative healing deltas are unchanged. Resistance
bypass skips armor. Only equipped slots are inspected; resolved inventory-template
`POCKET` and `SUITSTORAGE` flags exclude pockets and suit storage, and item-owned
bag contents are never traversed. Local item registry IDs are passed unchanged to
`ModEquipmentItems.require`; they are not namespace-rewritten. Missing optional
item prototypes are skipped.

The shared clothing composition continues to be represented by the existing
resolved `EquipmentItemData` family; armor is an optional addition, not a new
prototype type or a concrete suit registration. Slot order follows the resolved
Minecraft inventory template as an adaptation (SS14 relays worn inventory slots
in inventory order). No radiation special case is introduced.

Pinned upstream reference `c9df5ef5d675b0d1d226828bddf6b78c28502d91`:
`Content.Shared/Armor/SharedArmorSystem.cs` implements sequential flat-then-
coefficient modifiers; `Content.Shared/Damage/DamageSpecifier.cs` and
`DamageableSystem.API/Events` establish the damage-modify event point;
`Content.Shared/Inventory/InventorySystem.Relay.cs` limits relay scope to worn
slots (excluding pocket slots). This implementation does not yet register or
author concrete radiation-protective clothing.
