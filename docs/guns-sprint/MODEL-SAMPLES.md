# Verified model sample references

Reference revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91` (verified).
RSI paths below are relative to `Resources/Textures/` in the pinned SS14 checkout.
Local IDs are the intended new item/model bindings, not a claim they are implemented.

| Local asset ID | Upstream entity | RSI | Group |
|---|---|---|---|
| `weapon_pistol_mk58` | `WeaponPistolMk58` | `Objects/Weapons/Guns/Pistols/mk58.rsi` | magazine-fed |
| `weapon_rifle_lecter` | `WeaponRifleLecter` | `Objects/Weapons/Guns/Rifles/lecter.rsi` | magazine-fed |
| `weapon_revolver_inspector` | `WeaponRevolverInspector` | `Objects/Weapons/Guns/Revolvers/inspector.rsi` | manual-feed |
| `weapon_shotgun_kammerer` | `WeaponShotgunKammerer` | `Objects/Weapons/Guns/Shotguns/pump.rsi` | manual-feed |
| `weapon_sniper_mosin` | `WeaponSniperMosin` | `Objects/Weapons/Guns/Snipers/bolt_gun_wood.rsi` | manual-feed |
| `weapon_laser_gun` | `WeaponLaserGun` | `Objects/Weapons/Guns/Battery/laser_retro.rsi` | energy |
| `weapon_energy_shotgun` | `WeaponEnergyShotgun` | `Objects/Weapons/Guns/Battery/energy_shotgun.rsi` | energy |
| `weapon_laser_svalinn` | `WeaponLaserSvalinn` | `Objects/Weapons/Guns/Battery/svalinn.rsi` | energy |

Resolve `MagazinePistol`, `MagazineRifle`, `SpeedLoaderMagnum` and `PowerCellSmall`
by entity ID in the same checkout for their actual sprites and metadata. Their local
asset IDs are `magazine_pistol`, `magazine_rifle`, `speedloader_magnum`, `power_cell_small`.

Prototype source directory:
`Resources/Prototypes/Entities/Objects/Weapons/Guns/`. Gun definitions are in
`Pistols/pistols.yml`, `Rifles/rifles.yml`, `Revolvers/revolvers.yml`,
`Shotguns/shotguns.yml`, `Snipers/snipers.yml`, and `Battery/battery_guns.yml`.
Follow full parents and referenced ammo/item entities; do not infer behavior from names.

## Mechanical distinctions relevant to art

- Mk58: SemiAuto only; magazine and chamber are distinct. Lecter actually inherits
  FullAuto + SemiAuto. A physical selector's appearance must come from source art,
  not that mode list alone.
- Inspector: six-chamber revolver with cartridge/speedloader handling.
- Kammerer: seven-shell cycleable ballistic provider and required wielding.
  Mosin: ten light-rifle cartridges in a cycleable provider; it also has melee use.
  Manual cycling exists through inherited provider behavior, not a pump/bolt component.
- LaserGun: integral 1000-charge battery; `RedMediumLaser`, five-step indicator.
- EnergyShotgun: integral 480-charge battery; selectable lethal laser spread and
  nonlethal disabler spread with different per-shot costs; required wielding.
- Svalinn: `BaseWeaponPowerCellSmall → BaseWeaponPowerCell`, actual removable
  `PowerCellSmall` slot. It is deliberately distinct from the integral-battery guns.

Kammerer additionally references `Objects/Weapons/Guns/Shotguns/pump_inhands_64x.rsi`;
EnergyShotgun uses `heldPrefix: energy` with Battery `inhands_64x.rsi`. Inspect any
other inherited in-hand references rather than assuming the inventory view is complete.

## Existing local content

`weapon_revolver_python` is an existing unrelated identity/model candidate, with
no bundled gun profile found during planning. It is not the Inspector and must not
be overwritten or used as proof of an approved 3D Inspector model.
