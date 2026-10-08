# Examination foundation: verified SS14 mapping

## Pin and source

Verified checkout `C:/Users/William/Documents/SS14-dev/space-station-14` at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. This records the **examiner
foundation only**. Target-provider audits and implemented metadata/contributors
are recorded separately in `CONTENT.md`; this is not a claim that all examined
entities have been audited.

## Resolved actor composition

- `MobHuman` composes `AppearanceHuman` and `BaseSpeciesMobOrganic`.
- `AppearanceHuman` derives `BaseSpeciesAppearance`, which composes
  `InventoryBase` and `BaseSpeciesLayers`.
- `BaseSpeciesMobOrganic` composes `MobBloodstream`, `MobRespirator`,
  `MobAtmosStandard` (which derives `MobAtmosExposed`), `MobFlammable`, and
  `BaseSpeciesMob`.
- `BaseSpeciesMob` composes `BaseMob`, `MobCombat`, `MobDamageable`,
  `MobPolymorphable`, `StripableInventoryBase`, and `BaseSpeciesAppearance`;
  `BaseMob` derives `BaseControllable`. `BaseControllable` adds `Examiner` with
  SS14 defaults `skipChecks: false`, `checkInRangeUnOccluded: true`.
- Observer: `MobObserver` derives `MobObserverBase`; that parent composes
  `Incorporeal` and `BaseMob`. `MobObserverBase` overrides examiner
  `skipChecks: true` and adds `Ghost`.

Source locations: `Resources/Prototypes/Entities/Mobs/base.yml`
(`BaseControllable`, `BaseMob`, `MobAtmosExposed`, `MobAtmosStandard`),
`Resources/Prototypes/Entities/Mobs/Player/observer.yml` (`MobObserverBase`,
`MobObserver`),
`Resources/Prototypes/Body/Species/human.yml`,
`Resources/Prototypes/Body/species_base.yml`,
`Resources/Prototypes/Body/species_appearance.yml`, and
`Resources/Prototypes/InventoryTemplates/inventorybase.yml`. `MobHuman` is a
prototype composition, not evidence that local display metadata or identity/job
services exist.

## Rules and implementation mapping

`Content.Shared/Examine/ExaminerComponent.cs` establishes examiner defaults.
`Content.Shared/Examine/ExamineSystemShared.cs` is the rule consumer:

- base range 16; details range 3;
- critical or blind range 1.3; dead range .75; blurry vision uses
  `clamp(16 - magnitude * 2.5, 2, 16)`;
- ordinary checks respect examiner skip/range flags, examination-attempt
  cancellation, world/map identity and unoccluded reach;
- details require non-incapacitated state and range/visibility, while ghosts
  bypass detail checks. A stun is not itself an examiner rule.

Local typed `ExaminerComponent` reproduces only the two examiner flags, including
the codec inherited defaults. The local human profile selects their defaults;
the observer base profile overrides `skip_checks: true`. These are resolved in
character inheritance, not guessed from camera kind. Local shared
`ExaminationPolicy` expresses the values and policy boundaries.
The server must supply authoritative resolved examiner profile, actor state,
loaded-world geometry, LOS and attempt veto; callers must not replace those with
client camera claims or hand-action admission. `16` and `3` are **system
constants**, not examiner prototype fields. Local range currently uses 3D
distance from the controlled body's physical eye to a closest visible target
point (Minecraft adaptation of upstream map-space distance).

For actor state, an existing `MOB_STATE` attachment is authoritative; when it is
absent, the read-only examine adapter uses the shared `MobStateData.DEFAULT`
(ALIVE) without creating an attachment. This handles freshly enrolled bodies
whose runtime reconciliation has not materialized a non-default state, while
preserving explicit CRITICAL/DEAD state and avoiding mutation during inspection.

The policy supports upstream blindness/blurriness limits, but the live authority
adapter currently supplies `blind=false` and `blurMagnitude=0`. Live integration
of those effects is therefore not implemented or claimed as parity. Do not
invent missing local effect components. Ghost behavior requires committed observer session
authority as well as its resolved `skipChecks` profile.

CORPSE observer mode uses the validated corpse's resolved examiner profile and
remains subject to dead-body range/details rules. Only GHOST mode uses the
resolved observer examiner override and ghost detail bypass.

## Deliberate adaptations and scope boundary

### Sun/Earth scene profiles (Minecraft-only adaptation)

There is no corresponding SS14 celestial prototype or examination target here;
this mapping is intentionally unverified and makes no SS14 parity claim. The
local `celestial_scene` prototypes represent the existing Minecraft sky renderer
composition: Overworld, Sun at local +Y / Earth at local -Y, 80 sky units,
32-segment disc radii 9 / 7, and the renderer's existing colors. Resolved profile
metadata supplies the public labels and blurbs. `CelestialSceneGeometry` shares
the renderer's time transform and rendered-disc intersection for later selection;
these virtual sky units are not physical range coordinates.
The Earth's small continent/ice triangles remain fixed renderer artwork with
their existing colors and normalized offsets; they are not prototype metadata.

### Recognizable reagent audit

All 59 locally authored `recognizable: true` profiles were matched to the pinned
upstream reagent prototypes and their resolved parent chains; all resolve true.
No disclosure-flag correction was required. SpaceLube is explicitly recognizable
(`Resources/Prototypes/Reagents/cleaning.yml`); Bicaridine omits the flag and uses
the false default (`medicine.yml`). Its physical description is not chemical
identification. Missing display-name translations must not be mistaken for an
incorrect recognizable flag.

The audit covers `Consumable/Drink/{alcohol,drinks,soda,juice}.yml`,
`Consumable/Food/{condiments,ingredients}.yml`, `biological.yml`, `toxins.yml`,
`gases.yml`, `cleaning.yml`, and `pyrotechnic.yml`. Relevant inherited bases include
BaseAlcohol, BaseDrink, BaseSoda, BaseJuice, Blood, and BasePyrotechnic. This verifies
recognizable flags only, not every chemical property or reaction.

Minecraft server checks loaded chunks only, uses collision geometry for
occlusion (not physical-use interaction), and explicitly tags allowed
transparent blocks. Never treat all `noOcclusion` blocks as transparent. A
transparent pane may be selected as a target while objects behind it remain
visible/selectable; opaque intervening geometry still denies the query.
Airlock upper/lower block coordinates resolve to the lower block entity root
after verifying both loaded halves. This is a local composite-target adaptation,
not a claim about SS14 mouse-ray behavior. Ordinary blocks currently use their
block-state type and position as identity; same-type replacement at the same
coordinate cannot be distinguished without a future root-incarnation field.

Block-entity targets carry an optional `controllerIncarnation` UUID appended to
the request and response records. The server assigns it through a weak
identity-keyed cache for the live block-entity object: state changes on that
object retain the stamp; replacing the block entity creates a new stamp. An
initial request may omit it and receives the current stamp; a stamped refresh
fails closed when the current resolved root no longer matches. This identifier
is transient, server-authored protocol identity—not a persistent save guarantee
or an authorization token. The request path reads existing chunk block-entity
instances only and never creates/writes persistent controller data. For
non-block-entity blocks, same-type replacement at one position remains
indistinguishable under the current position-only target contract.

See `CONTENT.md` for airlock/APC/disposal/tool/character metadata and contributor
coverage, source correspondence, and unsupported information.
