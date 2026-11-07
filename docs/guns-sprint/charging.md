# Energy item charging correspondence

The pinned SS14 source is revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`WeaponCapacitorRecharger` inherits `BaseItemRecharger`, then `BaseRecharger`.
The resolved source composition has grid anchoring/SnapgridCenter, APC passive demand 1 W,
active charger demand/rate 20 W (20 J/s), one `charger_slot` item slot, and
`ejectOnInteract`. Its supported whitelist includes BatteryAmmoProvider/Stunbaton/
PowerCell; unsupported PotatoBattery is intentionally absent. This local typed
projection supports battery ammo providers and power cells only; there is no local
Stunbaton battery profile. It adds no fake component fields.

Local charger data is abstract `base_recharger` → abstract `base_item_recharger` →
concrete `weapon_capacitor_recharger`; manager-resolved inheritance supplies defaults,
including the one-item `charger_slot`. The component whitelist is represented by
typed `battery_ammo_provider` and `power_cell` capabilities, resolved against actual
gun/battery profiles rather than fabricated item tags. Unsupported source Stunbaton
component behavior has no local implementation and is not emulated.
Anchored and SnapgridCenter are represented by the local placed-block structure:
the only local charger is a stationary Minecraft block, placed at a block-grid
position. These source structural behaviors are not independent profile fields or
runtime-tunable components. The block has no detachable/portable machine lifecycle.
The block item inherits local `Normal` item size and the standard 2×2 Normal cells,
matching the upstream recharger's `Item` size rather than relying on generic fallback.
Local battery charge is represented as joules and advances at 20 J/s while fully
powered/admitted. Energy Shotgun owns a 480 J integral battery; Retro Laser owns
1000 J. PowerCellSmall has 360 J. Svalinn has no integral battery profile; charging
resolves and updates the actual nested cell stack. There is no offline catch-up or
unpowered elapsed-time accumulation. The live block uses the shared APC processing
meter, machine-slot ledger, and ordinary controlled-body block-use owner. Native
power, ownership and persistence fixtures are provided; their execution and GPU
appearance remain separate validation boundaries.

## Manual charging route (implementation candidate)

Place a Weapon Capacitor Recharger beside the APC power lane and connect a source
through HV cable → HV/MV substation → MV cable → APC → APC cable, leaving the APC
breaker closed. Unwield with **Ctrl+Z** if needed. Hold a depleted Energy Shotgun,
Laser Gun, small power cell, or Svalinn with a cell installed; aim at the charger
and press **E**. Wait for its charging/charged light, then press **E** with an empty
active hand to retrieve the same item. Standalone cells show their charge when held
or examined. Charging an empty 480 J Energy Shotgun takes about 24 powered seconds.
Opening the APC breaker must stop charging. This is a candidate manual procedure,
not owner-acceptance evidence.
