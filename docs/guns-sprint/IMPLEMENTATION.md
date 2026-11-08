# Guns implementation coordination

Implementation is authorized. This document is an integration index, not a readiness
claim. The eight-gun scope and acceptance criteria remain in `PLAN.md`.

## Shared ownership and invariants

- `GunProfile` / merge describe resolved component composition. Ammo-provider
  families are mutually exclusive; battery storage is a separate profile keyed by
  the actual gun/cell item. Wieldable behavior, numeric wield bonuses and hard wield
  requirements are distinct. Magazine capacity belongs to the magazine, not a second
  copy in the gun profile. Battery fire-mode lists are separate from ordinary gun modes.
- `AmmoContainerProfile` and `BatteryProfile` use the existing `PrototypeRuntime`
  server/client catalogs. Do not create fallback static catalogs in consumers.
- Actual nested magazine, chamber, round and cell stacks are item-owned immutable
  component state. Pure kernels return changed copies and explicit outputs; existing
  hand/world commit services publish them. No ejection transition may silently delete
  the previous item. No read-only query refills a depleted item.
- Reuse `RevolverProviderKernel` for indexed chambers and its single-round insertion
  transitions. There is no separate existing speedloader family/service: use
  `AmmoContainerProfile/State` for the actual loader and transfer through that kernel.
  Manual ballistic providers instead take their last loaded round directly or
  materialize one logical unspawned round. A manual cycle ejects a round; it does
  not create a pump-required chamber gate. See source audit correction in `PARITY.md`.
- `LiveWieldSystem` / `WieldHandPolicy` extend hand reservations, not a duplicate
  physical gun in the other hand. Exact source token and reservation proof matter;
  invalidation must release only that wield lease, never a pull/carry reservation.
- `ProjectileProfile` is being extended as the existing ranged-shot composition
  family for moving shots, explicit hitscan, pattern and stamina data. Hitscan must
  never instantiate a fake moving projectile. Moving and ray impacts share health/
  armor/stamina owners; the owner explicitly deferred general structural health.
- Firing must keep the current stage/commit/publish boundary. Publication failure
  currently consumes committed ammo/cadence; do not silently promise a new recovery
  or refund framework. Validate shot profiles before committing ammunition.

## Initial implementation boundaries

| Unit | Owned integration surfaces |
|---|---|
| Profile contracts | `GunProfile`, its merge and profile tests |
| Ammo transitions | `AmmoContainerProfile/State`, magazine/chamber and ballistic states/kernels, common provider dispatch |
| Battery transitions | `BatteryProfile/State`, owned-cell state and battery kernel |
| Registration | `PrototypeRuntime`, new family accessors/merge classes, state `ModDataComponents` registrations |
| Wield reservations | `LiveHands`, wield policy/state/service, `ModDataAttachments`, gateway reconciliation |
| Ranged backends | `ProjectileProfile`/merge, `TypedProjectileEntity`, shared impacts and shot emitter/hitscan presentation |
| Content | native item registrations, creative entries, bundled data and factory initialization |
| Live firing/control | existing `GunFireSystem`, use/transfer services, request/client feedback/input; starts after kernel APIs stabilize |

One worker owns a shared file at a time. Pure definitions or synthetic fixtures
alone are not completion: live consumers, real bundled content and focused tests
must be wired before acceptance.

## Source access

Implementers restricted to the worktree receive a bounded, verbatim pinned source
bundle under `docs/guns-sprint/reference/`, with per-file hashes and original license/
headers. It includes ranged, hitscan, wield/virtual-hand and relevant damage sources.
Workers may extract private working copies under ignored `build/guns-sprint/`.
This is source evidence, not production assets. Remove the temporary source archive
before final submission; preserve curated parity findings/source pin rather than
shipping a second upstream codebase.

## Asset state

The owner lifted the earlier hold with “The modelers are done with your gun models,
so use those now.” Latest magazine-fed R4, energy R3 and manual-feed R1 deliveries
were independently reviewed and imported under exact-digest authorization. All 260
runtime destinations matched their frozen hashes immediately after import.
`ARTIST-MODELS.md` records the digests and retained original attribution;
`ROOT-BINDINGS.md` records subsequent derived native state-selection metadata.
Artist geometry, UVs, textures and display poses remain unchanged. Native/GPU
presentation and owner acceptance are separate from import verification.

## Current integration evidence and limits

Foundation compile tasks passed, and a focused run covered 22 exact JVM classes /
84 tests with no failures. A separate actual-bundled gun resolution test passed.
These are historical bounded checks, not validation of later edits or readiness.

Use-in-hand now reaches `GunUseSystem`. World empty/spin and recognized cartridge
use now reach `GunWorldUseSystem` through the existing nominated target. The shared
insertion planner is being extended across all providers; held-to-held interaction
uses a distinct `USE_ON_HAND` request rather than reinterpreting analyzer self-use.
Both hand changes must be staged from one snapshot and published together through
the existing hand commit gateway.

`BodyHeldItemInteraction.stage` now composes existing detached hand transforms and
the live service commits their final state once. `HandEquipmentGesture` and
`BodyHandClient` nominate both hand/token proofs for `USE_ON_HAND`; ordinary held
analyzer self-use remains separate. World insertion now uses the planner's source
remainder, preserving partial speedloaders instead of clearing the whole source.

`GunTriggerPolicy` gates starts, full-auto continuations and matching stops around
the existing per-shot firing service. Revisions remain per-shot proofs, not a fixed
trigger binding. Client hold state is in `GunTriggerClientPolicy`, with press,
reconcile and release hooks in `GunShotClient`; a stop does not create a second
server firing loop. Selected battery mode now participates in shared pacifism.

A later focused compile/test batch covered 10 exact classes / 40 tests, zero
failures. This is bounded JVM evidence only; focus/pointer admission and native
network timing remain unverified. Later control/audio/resource edits need fresh
checks. Complete presentation/resources, accessible held controls and native
registered-gun evidence remain integration work.

## Later integration corrections

- Held controls now use the shared pointer action-list window, with a separate
  proof-bound held context rather than a fabricated world UUID. Right-clicking
  the active gun hand in inventory/popups opens applicable controls. World-target
  menus retain their own aim/UUID proof. Menu tests and visual review remain needed.
- `GunUseSystem` must distinguish **pre-commit** hand proof from the returned
  `LiveOwnedItemAccess.Committed` state used to guard output publication. Checking
  the old snapshot after a successful commit loses ejected rounds/magazines/cells.
  Provider-use admission must also include ballistic cycling and battery modes;
  an old revolver/magazine-only early return made those branches unreachable.
- Wield reconciliation must not nest a hand commit inside another commit callback.
  That published stale state and could stop drop publication after removing the
  source. Invalid leases are queued and reconciled through the existing level-post
  tick, including trusted actorless cleanup. Transfer identity guards stay intact.
- Confirmed hand snapshots now carry validated wield evidence for client prediction.
  It is body/hand/token/reservation context, never a persistent gun-stack flag.
- Shared `RangedDirection` handles recoil and pellet spread at vertical aim. Y-axis
  rotation alone removes all spread when looking straight up/down. This is an
  explicit 3D Minecraft adaptation of SS14's 2D directions.
- Hitscan eligibility reuses `CommonMeleeTargetEligibility`, excluding carriers and
  spectators. Supported opaque dynamic targets are living bodies; ordinary loose
  items do not acquire opaque collision merely because physical bullets can damage
  them. Beams stop at actual entity intersections, not the wall behind a target.
- Abstract gun parents are excluded from live catalogs. `BaseGunWieldable` supplies
  spread 21°/32° and bonus −20°/−30°, not invented firing-mode defaults. Energy
  Shotgun inherits this composition and separately requires a battery ammo provider
  as well as its mode list. Tests must assert one provider per concrete gun.
- Reuse the existing `cartridge_magnum` native item; the briefly added duplicate
  `cartridge_magnum_standard` identity was removed. Seventeen selected item
  identities do not mean seventeen new registrations.

Source carry audit: Small L-shaped Mk58/Inspector/retro laser/Svalinn use cells
`(0,0),(1,0),(0,1)`; Lecter is Huge with explicit 4×3 cells; Kammerer is Large
with explicit 5×1 cells; Mosin and Energy Shotgun are Large with default 4×2
cells. Four accessories are Small 1×2; five cartridges/shells are Tiny 1×1.
These are supported exact footprint data, not guessed rectangular size templates.
All guns have `quickEquip: false`; Svalinn allows Belt only, the other sidearms
Belt/suitStorage, and long guns Back/suitStorage. Human suit storage already uses
the outer-clothing allowance; GasTank-only restrictions belong to specific animal
templates and must not be widened for guns.

Mosin's bayonet is **not** unsupported chemistry: existing `ItemFoodSolutionItem`
is a named-solution adapter without ingestion behavior, also used by hypo darts.
It now composes an empty 2u `melee` solution, refill/transfer cap 2u and the existing
1u-per-eligible-target, armor-piercing melee injector. `ChemicalInjectionDose` and
the shared transfer owners remain authoritative; no separate gun solution engine.

Historical focused checks reached 70 passing tests after fixture corrections.
Subsequent edits need fresh checks; registered Mk58/Lecter fixture sources have
been added, but native execution has **not** been performed. Do not infer native,
GPU, package readiness or owner acceptance from these counts.

## Consumer and lifecycle invariants

- Hand reconciliation must remain lightweight. `ClientGunWeaponResolver` reads a
  confirmed hand snapshot, which itself reconciles hands. Calling it from
  `GunShotClient.reconcile` creates infinite recursion. Held-input polling belongs
  once per client tick **after** hand reconciliation returns, not in HUD/query
  accessors. Stop sends are skipped after disconnect; local state still clears.
- Rejected shot replies cancel only their admitted trigger generation. A later
  press may already exist while an earlier request awaits its acknowledgement;
  matching an old request sequence does not authorize cancelling the new gesture.
- Native body inventory retains hand proof and uses the existing approved pointer
  overlay host. Keeping proof does not enable world firing or movement with a
  modal screen open. Opening a held menu cancels pending world-menu requests;
  delayed world replies must not replace that newer held context.
- `GunSoundDefaults` is independent of `GunProfile`: nested descriptor defaults
  calling an outer-class factory cause codec initialization cycles when a nested
  type is initialized first. Cue IDs are lowercase native resource IDs. Lecter's
  leaf gunshot is `ltrifle.ogg`, not its rifle parent's `batrifle.ogg`.
- Manual chamber-magazine racking always finishes **closed**, even with no supply.
  Closing an open bolt preserves an already chambered round. Automatic firing opens
  the bolt only when a **present magazine item** cannot feed another round; firing
  without a magazine leaves the bolt closed. Empty magazine contents are not an
  empty magazine `ItemStack`. See pinned `SharedGunSystem.ChamberMagazine`.
- A physical spent cartridge is a dry attempt, distinct from no ammunition. Internal
  `DRY_FIRE` uses normal cadence and no projectile/muzzle/camera/gunshot effect;
  its provider state transition and outputs still commit. Revolvers retain cases,
  direct ballistic providers eject them, and auto-cycling magazine guns eject/feed.
  Only genuine no-ammo `EMPTY` uses empty-safety cadence. Never discard a ballistic
  casing by reporting it as retained after removing it from the supply.
- **Persistent spent outputs (round-three owner correction):** the former cosmetic
  adaptation is superseded. `CartridgeOutputPublisher` publishes both spent and
  live stacks through the existing post-commit world-output batch. Spent cartridges
  retain `CARTRIDGE_SPENT` on persistent `LooseItemEntity` objects; they must never
  become live rounds or gain a second particle representation. Shared tiny loose
  profiles supply their world size. Floor audio reuses the existing collision policy
  and resolved cartridge landing cue. Ordinary pickup/disposal provides cleanup.
  Spent chamber/cylinder state remains authoritative until actually ejected; live
  racked rounds remain recoverable, with their existing item components preserved.
- Preserve recoil's source timing convention: `LastFire` is set to `NextFire`, not
  the current tick (`Content.Server/Weapons/Ranged/Systems/GunSystem.cs:236–246`).
- Detachable magazines/speedloaders are also reload targets. Cartridge-to-container
  refills use the same staged insertion planner and exact hand/world target proofs;
  fresh absence and a present depleted state remain different. Bolt/chamber access
  is separate from magazine insertion, with a closed bolt locking direct loading.
