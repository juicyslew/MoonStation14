# Examination metadata provider mapping

This provider metadata is stored at the local prototype root, separately from
behavior components. Empty metadata means consumers retain their native-name
fallback; a blank description contributes nothing.

The pinned source is SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`Airlock` inherits `BaseStructure` metadata: name `airlock` and description
“It opens, it closes, and maybe crushes you.” Local `base_airlock` records those
resolved defaults. `DisposalUnit` inherits `DisposalUnitBase`, which resolves
through `BaseMachinePowered`, `BaseMachine`, and `BaseStructure`; its inherited
description is “A pneumatic waste disposal unit.” Local `disposal_unit_base`
records that description. `Screwdriver` derives from `BaseItem`; its source
description is “Industrial grade torque in a small screwdriving package.” The
local tool profile is only a supported behavior subset of that item, so this
metadata does not imply the complete source item composition.

Character-root metadata uses the same inherited root record. `MobObserverBase`
source metadata is name `observer`, description `Boo!`; local `observer_base`
records both, inherited by `observer`. Local human metadata has generic display
name `Human` as a Minecraft adaptation, not an SS14 identity claim. Public
custom character identity takes precedence over generic prototype metadata; the
source human name is not an appropriate replacement for player identity. No generic jug contents lore,
job identity, unsupported wearable metadata, or fabricated window metadata is
included.

Runtime contributors read only the already-resolved Airlock, Disposal Unit,
Character, and ToolProfile metadata. Airlock/Disposal Unit receiver status is
reported from a fresh known runtime meter; unknown meter state is explicitly
unavailable rather than guessed off. APC battery charge is the local supported
APC block-entity battery quantity against its declared capacity, shown only in
authorized detail rows. The local APC lacks a typed entity-prototype host, so
this is a narrow runtime-supported battery adapter, not a claim of full SS14
APC prototype composition. No local APC input-receiver status, panel state,
electrification, or wire-map claim is emitted.

## Puddle solution details

The pinned ordinary `Puddle` prototype has no entity parent. Its supported local
root is `moonstation14:puddle`; the existing `PuddleBlockEntity` capacity and
examine provider both consume its resolved profile. `SolutionContainerManager`
provides the `puddle` solution with default maximum volume 1000, and
`ExaminableSolution` uses that solution with `exactVolume: false`. This is only
the locally supported metadata/solution/examinable subset; temporary-puddle,
slip, and evaporation behavior are not asserted by this provider.

The close-detail provider reads current `ReagentAttachment` values through the
existing `MS14Bridges.REAGENT` bridge, never block fill state or tint. It delegates
formatting to `SolutionExamineFormatter`, reusable for another supported solution
consumer through caller-supplied `Templates` (with generic default keys), without
duplicating storage or chemistry. The resolved caller supplies its capacity and
exact-volume mode; the formatter does not depend on `PuddleData`. It uses the existing
`ReagentComponent.getBlendedColor` API for mixture color and resolved `ReagentData`
for descriptions, names, and individual name colors. It reports the greatest-quantity
reagent's physical description (generic localized fallback if that reagent cannot be
resolved), and names only resolved reagents explicitly marked recognizable, ordered
by descending amount. Missing
reagent profiles are omitted rather than guessed. The existing typed reagent
descriptor already carries `physicalDesc` and optional `recognizable`; absent
recognizable values are treated as false, matching the source default. No
reagent resource was edited in this step. Exercised local `water` maps to
upstream `Water` (`Resources/Prototypes/Reagents/Consumable/Drink/drinks.yml`),
inheriting `BaseDrink` and explicitly declaring translucent physical description
and recognizable true. Local `blood` maps to the parentless upstream `Blood`
entry (`Resources/Prototypes/Reagents/biological.yml`), explicitly declaring
ferrous physical description and recognizable true. All 98 distinct physical
description tokens currently authored by local reagent prototypes are translated
with localized `a`/`an` articles. Values are ported from pinned SS14
`Resources/Locale/en-US/reagents/meta/physical-desc.ftl`; no Fluent markup is copied.
The additional `.article` labels are local English grammatical templates, not SS14
source fields. A coverage test scans the resolved reagent catalog against both
translation keys. Any future token absent from SS14's pinned FTL remains a local
content gap and requires an explicit safe fallback, not a fabricated parity claim.
All 406 resolved reagent-name tokens are localized in `en_us.json`; values are
ported from the pinned checkout's `Resources/Locale/en-US/reagents/**/*.ftl`
`reagent-name-*` entries (including inherited parent definitions). SpaceLube's
verified upstream value is `space lube` from `reagents/meta/cleaning.ftl`.
The name-coverage test checks the resolved typed catalog, requiring a nonblank
translation distinct from its key and rejecting unprocessed Fluent placeholders.
No unmatched reagent-name tokens were found; no chemical recognizability flags
were changed as part of localization.
The formatter
uses a generic physical-description fallback when the greatest-quantity reagent
has no resolved profile, rather than borrowing a secondary reagent's description.
Reagent-name ties use stable prototype IDs on the dedicated server because there
is no safe server-side localized-name resolver. Upstream
`SharedSolutionContainerSystem.OnExamineSolution` gates this material to the
detail range; local equal-amount ordering uses stable prototype IDs because the
dedicated server cannot safely resolve localized names. Puddle volume phrasing and
physical-description singular/plural/article forms use separate localized templates;
only the physical description and each recognizable reagent name carry color.
SS14's middle-volume category depends on examiner identity; local examination keeps
a deterministic half-full adaptation. Source exact-volume mode is supported, with
the Puddle template deliberately showing current volume only. Full chemical-analysis verbs and
unsupported puddle slip/evaporation observations are omitted.

## Jug solution composition

Pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
`Resources/Prototypes/Entities/Objects/Specific/Chemistry/chemical-containers.yml`
defines Jug with parents `DrinkBaseMaterialStrongPlastic`, `DrinkBase`, and
`DrinkVisualsAll`. The resolved material chain is
`DrinkBaseMaterialStrongPlastic` → `DrinkBaseMaterialPlastic`; `DrinkBase` →
`BaseItem`; `DrinkVisualsAll` → `DrinkVisualsOpenable` + `DrinkVisualsFill`,
and `DrinkVisualsOpenable` → `DrinkVisuals`. Drink base supplies the logical
`drink` solution; Jug overrides its maximum volume to 200 and sets
`ExaminableSolution.exactVolume` true. Resolved examine defaults are not held-only,
examinable while closed, and three details. Jug's source metadata is name `jug`
and its large-amount/chugging warning description. Local `JugData` models only
metadata, logical solution capacity, and supported examine settings. It does not
claim the unsupported local runtime implements plastic destruction, openable
behavior, or source drink behavior. `JugItem` capacity reads the resolved
`ModJugs` catalog (separate server/client catalogs); 200 is only the bootstrap
fallback before catalogs load. Both runtime getters project profile capacity
through `JugItem.capacityFromResolved`; prototype tests pass an inherited child
with a nondefault capacity through this exact helper. This verifies the resolved
child-to-runtime-capacity projection without constructing an unregistered `Item`
or mutating global catalogs. The native dropped-item fixture uses registered
`ModItems.JUG` and verifies the root's resolved 200-unit capacity in provider
output; it does not claim native child-override coverage. A bound-data constructor
remains available for explicit isolated compositions. The source default corrects
the former local 150-unit capacity to 200; this is deliberate SS14 parity, not an
unchanged-capacity claim.

The dropped-item provider uses the jug's existing `REAGENT` trait handle and
detached `MS14Provider` snapshot, delegating all content formatting to
`SolutionExamineFormatter.Templates.GENERIC`. It uses the capacity and examine
flags from the same resolved composition. Pinned `SharedSolutionContainerSystem.cs`
`OnExamineSolution` lines 859–876 gates details and emits the generic empty fill
level before exact quantity when there is no primary reagent; the shared
formatter now preserves that empty behavior for exact solutions too. It does
not initialize or mutate the stack attachment. The local provider follows the
existing details-range policy, does not invent an openable/closed gate for Jug,
and shows only recognized reagent names plus the primary physical description
and mixture color.
