# Guns sprint plan

## Status and objective

Implementation authorized by the owner on 2026-11-05; modelers are running in
parallel. Deliver a **resource-backed, playable SS14-style gun system**
using eight mechanically distinct weapons, with approved **3D held/loose models**.
Hostile NPC AI is a separate future sprint; NPC shooting is not a prerequisite.

Reference: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91` (checkout verified).
`PARITY.md` records verified composition/consumer findings and remaining audit work.

## Representative content

| Gun | Primary coverage |
|---|---|
| Mk58 (`WeaponPistolMk58`) | Semi-auto; detachable pistol magazine plus independent chamber, racking and tactical reload state |
| Lecter (`WeaponRifleLecter`) | Rifle magazines; inherited FullAuto/SemiAuto modes, held-trigger cadence and wield accuracy bonus |
| Inspector (`WeaponRevolverInspector`) | Six indexed chambers, cartridge and speedloader handling, spent/live rounds, cylinder operations |
| Kammerer (`WeaponShotgunKammerer`) | Internal shell supply, manual cycling, wield-required firing, pellet fan-out and spread |
| Mosin (`WeaponSniperMosin`) | Individually loaded rifle ammunition, manual cycle/ejection, wield gate and its actual alternate melee composition |
| Retro Laser Gun (`WeaponLaserGun`) | Integral battery, per-shot charge consumption, hitscan firing and charge appearance |
| Energy Shotgun (`WeaponEnergyShotgun`) | Battery shot patterns, selectable lethal/nonlethal ammo modes, distinct charge costs and stamina impacts |
| Svalinn (`WeaponLaserSvalinn`) | Actual removable power-cell slot and cell-backed ammunition, distinct from integral batteries |

Eight is intentional: the two ordinary shotguns exercise different ammunition
families, while the three energy guns cover hitscan, pattern/mode effects and
replaceable cells. Do not infer a dedicated pump/bolt component from weapon names:
Kammerer and Mosin use the inherited cycleable ballistic provider.
That provider takes ammunition directly when firing. Its manual cycle action
ejects a round; it does not establish a mandatory pump/bolt chamber gate between
shots. Do not invent one from the weapon's name.

Associated content: their standard cartridges/shells, two magazines, magnum
speedloader, small power cell, required projectile/hitscan prototypes, and spent
states. Freeze exact compatible tags and starting contents from upstream before
implementing factories. The early art requests additionally cover the two magazines,
speedloader and small cell; loose ammunition/projectile art can reuse appropriate
SS14 resources and is not a new gun-model commission.

## Existing foundation: extend, do not replace

There is already a **wired but incomplete** gun kernel. `CombatModeNetworking`
registers shot requests; hands input reaches `GunShotRequestService`; the server
uses `GunWeaponResolver` and `GunFireSystem`. `GunProfile` currently supports only
SemiAuto and a revolver provider. No bundled gun-profile content was found, so the
existing `weapon_revolver_python` item is not proof of a functioning firearm.
Do not overwrite that unrelated identity or treat it as an approved Inspector model.

Reuse these owners first:

- `ms14/combat/`: `GunProfile`, merge/catalog/runtime owners, `GunFireSystem`,
  `GunFirePreparation`, `GunUseSystem`, `GunInputRoute`, `GunWeaponResolver`,
  `GunShotRequestService`, client shot policy/feedback, recoil/muzzle services and
  `RevolverProviderKernel`.
- `entities/TypedProjectileEntity.java`: frozen projectile profile, loaded-only
  travel/collision and existing typed impact routing. It already reaches armor via
  `DamageSystem → EquippedArmor → EquipmentArmorSystem`; **no gun-specific armor engine**.
- `LiveHands`, `LiveOwnedItemAccess`, `LiveHandsCommitGateway`, item-slot/transfer
  services and shared timed interactions for ownership, slot mutations and delays.
- Existing stamina/damage/effect services for nonlethal impacts; do not substitute
  generic health damage for disabler stamina effects.
- `WorldItemPresentationRenderer` / `LooseObjectProfile`, native ItemRenderer and
  NeoForge OBJ baked models for world/held art; `ItemFootprintProfile` remains storage geometry.
- Existing gameplay input/pointer/readiness owners and unified capability allocator.
  No new fixed capability slots or competing combat input dispatcher.

Before adding any provider, slot or visual logic, check for further shared support.
Extract shared behavior only for current callers; explain unavoidable overlap.

## Ordered work units

### 0. Early assets — submitted

Three independent model requests are open; see `ASSET-REQUESTS.md`. The owner has
been notified and can launch three modelers now. Coding need not wait for art, but
the sprint is not complete until approved complete groups are imported and consumed.
Do not add placeholder/code-generated gun models to production.

### 1. Lock composition, controls and state contracts

Finish the effective prototype/consumer matrix, including every relevant parent,
defaults, ammo compatibility, starting contents, action timings and effect flags.
Use the verified findings in `PARITY.md`; resolve its remaining details before
designing new fields. Separate upstream behavior from necessary Minecraft adaptations.

Map existing gun, cartridge, projectile, battery and item-slot families to supported
local typed composition. Extend existing codecs/resolution rather than hardcoding
eight gun IDs or adding unsupported YAML-shaped components.

Freeze a control/admission table against upstream and existing bindings: shoot/stop,
use-in-hand, provider verbs, magazine/cell transfer, cycle/eject, mode selection and
wielding. Do not casually assign reload to an already-used key. Combat-mode and
active-held-gun admission are verified upstream requirements. Keep the accepted
Actions Ctrl+K / turnover K+Shift+K controls and current throw interactions intact.

### 2. Ammunition ownership and wielding

Extend the existing provider kernel with chamber/magazine, cycleable ballistic,
integral battery and power-cell-backed consumption. Preserve revolver indexing,
speedloader compatibility, spent cartridges and ejection semantics.

Gun contents live on their actual owned item state. Magazine contents and charge
must survive hand/storage/drop/transfer without copying or resetting. All live
mutations use existing ownership/commit services; stage publication/ejection through
their current responsibilities, not a second inventory or transaction framework.
Reloading/cycling must not refill ammo on query, duplicate casings, or retarget a
replacement stack. Use shared timing where upstream has a timed action; do not
invent delays for instant interactions.

Establish reusable wield/support-hand state using existing hand reservation support
where suitable. A wield accuracy bonus is not the same as a firing requirement:
Lecter must not acquire Kammerer's/Mosin's mandatory-wield rule by convenience.

### 3. Firing control and predicted feedback

Extend `GunFireSystem` and existing request/client-state owners for held-trigger
FullAuto and mode switching, cadence, spread/recoil growth and decay, empty use and
shot acknowledgement. Keep battery ammo-mode selection separate from SemiAuto/
FullAuto selection. Bind a continuous intent to the exact gun token/body epoch;
hand changes, lost authority, UI capture and invalid wield state must stop it.

Use current prediction/feedback services for responsive recoil, muzzle and sound,
with authoritative ammo/charge/collision results and reconciliation. Avoid duplicate
effects on acknowledgement and a new per-gun polling/networking pipeline.

### 4. Shot backends and impacts

Extend existing projectile flight and introduce the smallest shared hitscan/pattern
support needed by the samples. Reuse existing collision/loaded-world boundaries;
do not build a separate ray engine or use client hits as authority.

Preserve **one ammo/charge debit per shot**, then the resolved number of impact
entities: standard shell 6 total pellets; energy shotgun 4 lethal or 3 nonlethal
projectiles. `Count` includes the original projectile, not Count plus one. Apply
typed damage/stamina per impact through the shared owners. Verify hitscan blocking,
range, shooter exclusion, loose-object impacts and any inherited reflection/effect
rules. Do not automatically copy thrown weapons' owner-requested self-hit adaptation
onto gun projectiles; inspect their actual upstream shooter rules.

Units 2–4 may have independent workers after unit 1 fixes interfaces. Assign one
owner for shared `GunProfile`/`GunFireSystem` edits to avoid competing state models.

### 5. Real content, presentation and assets

Register all eight item IDs and resolved profiles with actual ammunition factories,
not fixture-only profile injection. Preserve Mosin's verified melee/alternate-use
composition through the existing melee service, not a gun-only melee implementation.

Integrate approved native OBJ groups through the normal baked item path, using
source-visible magazine/cell/charge states and shared HUD/tooltips. Model bindings,
grip/support/muzzle alignment and loose floor offset need live consumers. The dropped
NONE context is not GROUND; test both held hands, wielding and dropped orientation.
Do not invent unsupported animated parts or custom loaders merely because an editable
model has named groups. Preserve SS14 firing/empty/cycle/load sounds and attribution;
reuse existing sound resources and record genuinely unknown provenance.

### 6. Integration, readiness and owner acceptance

Run focused author JVM/compile checks, independent implementation/parity/reuse review,
then the authorized serialized integration gate. Select affected UI/interaction/
prediction checks and source-verified native tags; broaden coverage if shared changes
require it. No independent heavy server/client validation by coding workers.

Native tests must use the **registered guns and resolved bundled prototypes**, real
body/hand ownership, actual projectile/hitscan impacts and finite ammo/charge. Reuse
`RegisteredGunFireGameTests` admission fixtures, but temporary synthetic gun profiles
alone cannot establish content readiness. Register narrow gun-family tags as tests
are added; do not claim selectors exist before checking their declarations.

Art requires independent inspection of exact frozen exports, native bake/dependency
coverage, owner approval and separate exact-digest import authorization. Final manual
checks should be a concise per-mechanism checklist, not a new gameplay scenario feature.
Automated PASS is readiness for manual testing, not acceptance.

## Acceptance matrix

| Criterion | Required evidence |
|---|---|
| G1 Composition | Resolved inheritance/defaults and all eight live registry/profile paths; compatibility and starting contents tested |
| G2 Ammo/charge | Magazine/chamber, revolver/speedloader, ballistic cycle and both battery forms; wrong ammo/empty/partial/eject/drop-transfer tests; no duplication |
| G3 Fire control | Semi/full auto, independent battery modes, cadence, spread/recoil, empty handling, trigger-stop/authority and wield bonus versus wield requirement |
| G4 Impacts | Real bullets, hitscan, total pellet counts, per-shot costs, armor/ignore flags and genuine stamina effects; loaded terrain/target filtering |
| G5 Input/prediction | Shared entry owners, current/rebound chords, UI/F3/context boundaries, immediate feedback and authoritative reconciliation |
| G6 3D/resources | Approved held/loose gun models, required state variants and accessories consumed through real model loading; SS14 audio/provenance retained |
| G7 Regression/conservation | Existing melee, thrown hits/self-return, medical injection, hand/storage/pickup and item identity remain correct |
| G8 Delivery | Complete implementation/assets, focused/native/readiness/package evidence, concise manual checks and explicit owner acceptance |

## Explicit boundaries

No Hostile NPC AI, gun fabrication/vending/lockers, stationary charging machinery,
full weapon catalog, scopes/attachment ecosystem, grenade/syringe/flame launchers,
new airborne-catch restrictions, general lag compensation or save migrations.
The owner explicitly deferred general wall/structure health after implementation
research found no shared structural-hit owner. Preserve supported living damage,
armor/stamina and existing item-damage routes; do not invent gun-only wall HP or
map SS14 Structural damage into the living-character ledger.
Use existing recharge services if available; otherwise keep finite/depleted states
honest and provide charged setup supplies without adding a charger machine here.
No bespoke animation framework, speculative NPC interface layer or unrelated cleanup.

## Planning evidence

Read-only port/upstream investigations established the baseline and selected sample.
Three public model requests were submitted and verified **open/unclaimed**. No mod
code changed, no game/build/test was run, and no coding-gate submission was created.
The owner approved implementation after planning. This paragraph describes the
planning checkpoint, not current implementation readiness; no gate PASS is implied.
