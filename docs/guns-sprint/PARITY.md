# Guns: pinned parity/composition map

Reference checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
The composition/source-value sections below are retained historical research notes,
not a statement that local implementation remains unfinished. Subsequent implementation
is recorded in `CURRENT.md` and `ROOT-BINDINGS.md`. Historical audit instructions below
apply only to unverified areas; do not infer behavior from gun descriptions.

## Selected inheritance and consumers

Contraband parents are retained in the source chain but do not by themselves imply
a supported local contraband gameplay system.

| Leaf | Relevant resolved composition |
|---|---|
| `WeaponPistolMk58` | `[BaseWeaponPistol, BaseSecurityContraband]`; pistol base → `BaseItem`. Chamber/magazine provider, distinct magazine/chamber item slots, ammo counter. Leaf rate 5 and SemiAuto-only modes replace broader pistol-base defaults. |
| `WeaponRifleLecter` | `[BaseWeaponRifle, BaseSecurityContraband]`; rifle base includes `BaseGunWieldable` and `BaseItem`. Inherits rate 5, initially FullAuto, modes FullAuto + SemiAuto. Magazine/chamber tags specifically `MagazineRifle` / `CartridgeRifle`, starting `MagazineRifle`, five-step magazine visuals. |
| `WeaponRevolverInspector` | `[BaseWeaponRevolver, BaseSecurityContraband]`; revolver base → `BaseItem`. SemiAuto, rate 1.5, use delay 0.66; six-chamber provider, Magnum cartridge/speedloader compatibility. Resolve initial chamber flags and spawning/fill semantics from the provider, not the leaf's null slots alone. |
| `WeaponShotgunKammerer` | `[BaseWeaponShotgun, BaseGunWieldable, BaseSecurityContraband]`; shotgun base → `BaseItem`. Cycleable ballistic provider, capacity 7, `ShellShotgun`; leaf requires wield, rate 1, spread modifier 0.6. |
| `WeaponSniperMosin` | `[BaseWeaponSniper, BaseGunWieldable, BaseMajorContraband]`; sniper base → `BaseItem`. SemiAuto rate 0.75, ballistic provider capacity 10 / `CartridgeLightRifle`; wield-required firing plus leaf melee, wielded-melee and `AltFireMelee` behavior. |
| `WeaponLaserGun` | `[BaseWeaponBatterySmall, BaseMajorContraband]`; battery-small → battery → `BaseItem`. Integral battery 1000 max/start; base rate 2, SemiAuto. Leaf `BatteryAmmoProvider` uses `RedMediumLaser`, cost 62.5, five-step indicator. **No power-cell slot in this chain.** |
| `WeaponEnergyShotgun` | `[BaseWeaponBattery, BaseGunWieldable, BaseSecurityContraband]`; battery base → `BaseItem`. Integral battery 480 max/start, required wield. Battery fire modes select lethal `BulletLaserSpreadNarrow` / cost 80 or nonlethal `BulletDisablerSmgSpread` / cost 48; nonlethal mode allows pacifism. |
| `WeaponLaserSvalinn` | `[BaseWeaponPowerCellSmall, BaseSecurityContraband]`; power-cell-small → power-cell → `BaseItem`. Real `cell_slot`, starting `PowerCellSmall`, whitelist `PowerCell` / `PowerCellSmall`, `PowerCellSlot` and matching container; five-step charge appearance. Do not give it integral-battery semantics. |

`BaseGunWieldable` supplies `Wieldable.unwieldOnUse: false`, `GunWieldBonus` and
angle modifiers. It does **not** make every inheritor wield-required. Preserve
`GunRequiresWield` separately from this bonus.

## Provider and shot distinctions

- `ChamberMagazineAmmoProviderComponent` defaults `AutoCycle=true`, `CanRack=true`.
  Magazine presence, chamber contents, cycling and round consumption are separate.
- `BallisticAmmoProviderComponent.Cycleable` defaults true. Shared use-in-hand and
  access/interact-gated verbs expose manual cycling. The server ejects an actual
  loaded entity or materializes/ejects a configured unspawned proto round. This is
  the Kammerer/Mosin manual-cycle behavior; a dedicated pump/bolt component is not required.
  **It is not a per-shot chamber gate.** `OnBallisticTakeAmmo` directly removes
  `Entities[^1]` or materializes one `UnspawnedCount` round. The provider has no
  chamber/spent slot to invent locally. Manual cycle removes a live round and
  raises the cycle event; ordinary firing can consume the next available round.
- Chamber-magazine racking is different: if open, close and feed; otherwise eject
  the chambered round and feed a replacement in one rack operation. Auto-cycle
  firing consumes the chamber and refills it, opening the bolt when the magazine
  has no replacement. Pistol starting slots specify a magazine, not an extra
  chambered cartridge; do not conjure a free round during initialization.
- `RevolverAmmoProvider` is indexed chamber state, not an unordered magazine count.
  Preserve live/spent/empty state, compatible speedloaders and actual cylinder verbs.
- Integral `BatteryAmmoProvider` and cell-backed providers must debit the actual
  appropriate owner. `BatteryWeaponFireModes` changes ammo proto/cost separately
  from ordinary SemiAuto/FullAuto selection.
- `CartridgePistol → BaseCartridgePistol → [BaseCartridge, BaseSecurityContraband]`
  supplies `CartridgeAmmo.proto: BulletPistol`; `MagazinePistol` contains those
  cartridges. Analogous standard Rifle, Magnum, LightRifle and shotgun families
  must resolve through their actual prototypes/tags, not inferred naming patterns.

## Fan-out: total count, not extra count

`GunSystem.CreateAndFireProjectiles` fires the original entity at `angles[0]`, then
spawns indices 1 through Count−1. The following counts include that original:

| Ammo | Total projectiles | Spread | Per impact | Resource debit |
|---|---:|---:|---|---|
| `ShellShotgun → PelletShotgunSpread → PelletShotgun` | 6 | 15° | Piercing 10 + Structural 15 | one shell |
| `BulletLaserSpreadNarrow → BulletLaser` | 4 | 10° | Heat 13 | one battery shot, cost 80 in the selected shotgun mode |
| `BulletDisablerSmgSpread → BulletDisablerSmg` | 3 | 9° | stamina 15, Heat 0 | one battery shot, cost 48 in the selected shotgun mode |

Charge scales with provider shot count, **not child projectile count**. These are
source values, not a proposal to flatten a shotgun blast into one combined hit.

## Authority and prediction

Shared `OnShootRequest` requires an attached user, combat mode and the actual active
held gun (or gun entity itself). Request gun identity must match. Request/stop
semantics and continuous fire counters matter; SemiAuto caps an attempt at one
shot while FullAuto can process due shots. Firing checks attack permission,
cancellation, cooldown and ammo before shot events/effects. Damage is downstream
on projectile/hitscan impact. Local `GunFireSystem`'s combat-mode gate therefore
has an upstream basis and should not be removed merely for convenience.

Complete the exact client-predicted versus authoritative split before extending
the existing request/feedback policy. This initial audit is not proof of native
client timing, recoil alignment or multiplayer behavior.

## Source anchors

Under `Resources/Prototypes/Entities/Objects/Weapons/Guns/`:

- `Pistols/pistols.yml`, `Rifles/rifles.yml`, `Revolvers/revolvers.yml`,
  `Shotguns/shotguns.yml`, `Snipers/snipers.yml`, `Battery/battery_guns.yml`.
- `Ammunition/Cartridges/{pistol,shotgun}.yml`, relevant magazines/cartridges,
  `Ammunition/Projectiles/shotgun.yml`, `Projectiles/{projectiles,hitscan}.yml`.

Code:

- `Content.Shared/Weapons/Ranged/Components/{GunComponent,BallisticAmmoProviderComponent,ChamberMagazineAmmoProviderComponent,BatteryWeaponFireModesComponent}.cs`.
- `Content.Shared/Weapons/Ranged/Systems/SharedGunSystem{,.ChamberMagazine,.Ballistic,.Battery}.cs`
  and `BatteryWeaponFireModesSystem.cs`.
- `Content.Server/Weapons/Ranged/Systems/GunSystem{,.Ballistic,.Revolver}.cs`;
  `CreateAndFireProjectiles` establishes total-count fan-out.
- `Content.Shared/Projectiles/ProjectileSpreadComponent.cs` and actual impact
  consumers for each selected projectile/hitscan/stamina component.

## Historical audit checklist (remaining applicable gaps only)

Record precise reload/cycle/eject/spin/slot input/verb entry paths and timings;
effective spread/recoil/wield modifiers; starting loaded/chamber states; Svalinn's
resolved shot/cost; cell empty/recharge policy; hitscan range/blocking/reflect rules;
projectile speeds, shooter exclusion and effect defaults. Verify full nested ammo
parent chains and impact consumers, including stamina and loose-object damage.
Do not silently invent these from the sample's title or this preliminary table.

## Local reuse boundary

### Hand-button loading

Pinned `Content.Client/Hands/Systems/HandsSystem.cs:UIHandClick` and
`Content.Client/UserInterface/Systems/Hands/HandsUIController.cs:HandPressed`
establish that clicking another occupied hand uses the active item on it rather
than selecting it. An incompatible use does not implicitly switch hands. Clicking
an empty hand selects it; clicking an occupied other hand with an empty active hand
moves that item into the active hand. The server uses
`SharedHandsSystem.Interactions.cs:TryInteractHandWithActiveHand` → `InteractUsing`.
Magazine insertion then uses the ordinary item-slot owner, not a gun-specific key.

Local hand clicks follow this precedence through the shared gesture dispatcher.
Explicit dragging nominates its captured source hand independently of selection,
as existing storage drags do; both source and target tokens/revisions are validated
and updated in one commit. Fresh standalone magazines materialize their resolved
starting contents once before insertion; initialized depleted magazines stay empty.

### Persistent spent casing outputs

At the pinned SS14 revision, `SharedGunSystem`/`GunSystem.EjectCartridge` publishes
the spent cartridge entity into the world. `BaseCartridge` supplies `SpaceGarbage`,
not an ordinary floor despawn timer. The round-three correction supersedes the
port's finite cosmetic adaptation: spent outputs now use persistent loose items,
retain their spent flag, and can be collected/disposed through normal item owners.
The publication boundary remains post-commit and does not also emit a casing particle.
Live racked/cycled rounds remain recoverable and unspent. Cylinder/chamber cases
remain inside their provider until actually ejected. See `IMPLEMENTATION.md`.

### Hitscan and charging correspondence

Pinned `HitscanBasicRaycastSystem.FireEffects` places the muzzle center half a tile
forward, travel between offsets `1` and `distance - .5`, and impact at the hit.
The local resolved effect data supplies those offsets, source-axis rotation and
world alignment; client camera clearance affects visuals only. Empty/inverted short
travel spans are omitted rather than reproducing negative scale. Directed source
caps and moving projectile sprites share the world-travel orientation helper.

`WeaponCapacitorRecharger` supplies 20 J/s while anchored/powered, with 1 W passive
draw. `PowerCellSystem.TryGetBatteryFromEntityOrSlot` resolves integral batteries
before installed cells. The port uses the shared APC processing meter and machine
slot ledger; see `charging.md` for the supported composition and source references.

Local typed damage maps use the existing lowercase `DamageKeys` vocabulary
(for example upstream `Piercing` maps to local `piercing`). Preserve the source
type and numeric value while authoring valid local prototype data; do not relax
the ledger's strict validation or introduce a second casing/mapping owner.

**Owner-agreed departure (2026-11-05):** general structural health is deferred.
The source pellet's Structural 15 is documented above, but it is not living damage.
Local `DamageKeys` intentionally excludes it; do not add it to health, rename it
Blunt, or introduce an unsupported decorative Structural component. The sprint
retains supported body/armor/stamina and existing damageable-item effects. General
wall/structure damage needs its own shared owner in a later sprint.

Historical note (superseded): the kernel was once wired without bundled gun profiles.
Current profiles and implementation status are summarized in `CURRENT.md`. Existing
`TypedProjectileEntity → DamageSystem → EquippedArmor → EquipmentArmorSystem`
already provides typed health/armor modification, with `ignoreResistances` respected.
Nonlethal projectile stamina and new hitscan/pattern behavior still require explicit
integration; they are not automatically supplied by that health call. Do not add a
parallel armor system or substitute melee damage for ranged projectile definitions.

Mosin solution appearance parity verified: upstream `SolutionContainerVisualsComponent`
defaults `ChangeColor` to true; `SharedSolutionContainerSystem.UpdateAppearance` writes
fill fraction and solution color, with `UpdateChemicals` refreshing appearance. The
local native model mask tint is bound to the reagent-stack solution property as recorded
in `ROOT-BINDINGS.md`; this mapping is verified source parity, not GPU/visual acceptance.
