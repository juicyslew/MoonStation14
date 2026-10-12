# ChemMaster composition and Minecraft adaptation

Reference checkout was verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
The entity chain is `ChemMaster -> [BaseMachinePowered, ConstructibleMachine]`;
`BaseMachinePowered -> BaseMachine -> BaseStructure`. The machine YAML overrides
the inherited APC receiver load (1000 W) with 250 W and leaves
`ExtensionCableReceiver` at its component default `receptionRange: 3`. The
extension receiver system further requires static physics for a connectable
receiver. Relevant sources: `Resources/Prototypes/Entities/Structures/Machines/
chem_master.yml`, `base_structuremachines.yml`, `Resources/Prototypes/Entities/
Structures/base_structure.yml`, `Content.Server/Power/Components/
ApcPowerReceiverComponent.cs`, `ExtensionCableReceiverComponent.cs`, and
`Content.Server/Power/EntitySystems/ExtensionCableSystem.cs`.

`RobustToolbox/Robust.Shared/GameObjects/Components/UserInterface/
UserInterfaceComponent.cs:115-142` defines each interface's
`InteractionRange` default as 2f; `chem_master.yml` does not override it. The local
typed `uiRange` therefore resolves to 2.0 blocks by default and supports normal
prototype inheritance/child overrides. This models the supported ActivatableUI
interaction-range policy; it is not a separate Minecraft-specific distance knob.

Local typed `chemmaster` prototypes model the resolved APC load, cable reach, UI range, and
input-slot `FitsInDispenser` policy. `ChemMasterBlockEntity` reads these resolved
values; its slot accepts `DispenserCompatibleItem` policies, not a beaker prototype
name. The persistent inert buffer uses the existing `ChemMasterBuffer` codec.
No output slot, machine-board graph, or unsupported SS14 component was invented.
The block uses native Minecraft mixer models and animated screen textures with
independent powered/loaded state projection; see
`../machine-model-integration/HANDOFF.md`. `PowerRuntime` indexes loaded machines and feeds
their resolved demand into the existing APC-tier `PowerLiveLoop` allocation.
UI authority requires full, fresh, identity-bound delivery; unknown topology or
partial delivery cannot authorize machine processing.

Upstream machine-frame conversion is adapted to Minecraft block-item break/re-place:
the ordinary singleton drop carries standard block-entity data (input slot and
buffer included). Persistent slot owner survives that transition; placement creates
a new machine identity, while chunk save/unload retains the current identity. Full
machine-frame construction and upstream destruction thresholds remain out of
scope. The opt-in item ledger tracks active slot ownership only: successful
hand/world releases retire claims, and slot suspension retires the claim while
retaining its serializable payload. A fresh restore cannot adopt an active claim.

Independent server subscriptions use controlled-body authority, not menu focus.
Empty-hand interaction opens the powered window; Shift + empty-hand interaction
ejects through the strict hand adapter even without power. Each session is scoped
to a placement identity and closes on explicit dismissal or actual invalidation,
not looking away, idle time, sibling focus, or cursor mode changes. The shared
pointer workspace is a deliberate Minecraft UI adaptation.

Click audio comes from `ChemMasterComponent.cs:27–28` and
`ChemMasterSystem.cs:366–369`: `machine_switch.ogg` at −2 dB. Slot insert/eject
use the inherited ItemSlots magazine sounds. Server-accepted actions dispatch
resolved sounds through the existing controlled-body sound service; attribution
and unresolved provenance remain recorded in the asset notices and
`docs/audio-unknown-provenance.md`.

Input reagent rows retain container order. The four presentation sort modes apply
only to the buffer, matching `Content.Client/Chemistry/UI/ChemMasterWindow.xaml.cs`
lines 246 and 249–325; localized alphabetical order does not reorder stored chemistry.
Each reagent row exposes its own amount buttons, including All.

World beaker examination consumes resolved `ExaminableSolution` policy and the
existing detached solution formatter, including exact capacity/volume and only
recognizable reagent names. The pre-existing examination target model does not
support held-stack targets; this sprint does not introduce a separate held-item
inspection protocol or expose private machine buffer contents through examination.

## Aim-and-release solution policies

Whole-world-source dumps use the existing ordinary body-item pickup reach (`BodyItemTargetProbe.MAX_REACH`),
not a new dump range or the ChemMaster UI range. This is an explicit owner-approved parity departure:
the pinned SS14 drag/drop contract uses 1.5 interaction units, while this Minecraft adaptation permits
the ordinary 5-block pickup reach at both source and machine. Reach is body-eye to the nearest point
of each target bounds; loaded chunks and unobstructed actor-to-source and actor-to-target paths are
still independently rechecked at commit. The source need not remain the release ray target. No global
pickup range is changed and no range field is added to item/machine prototype data.

The same pinned checkout (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`) resolves
Jug's multiple parents are `[DrinkBaseMaterialStrongPlastic, DrinkBase,
DrinkVisualsAll]` in
`Resources/Prototypes/Entities/Objects/Specific/Chemistry/chemical-containers.yml`.
The strong-plastic material inherits `DrinkBaseMaterialPlastic`; `DrinkBase` and
`DrinkVisualsAll` independently inherit `BaseItem` (see
`Objects/Consumable/Drinks/drinks_base.yml` and `drinks_base_materials.yml`).
`DrainableSolution` is inherited from DrinkBase with solution `drink`; the
component's own default is `default`. Jug explicitly sets its solution container
to `drink`, 200u; the local runtime stores that logical solution as `drink`.
`Beaker -> BaseBeaker -> BaseItem` is in
`Resources/Prototypes/Entities/Objects/Specific/Chemistry/chemistry.yml` and
`Objects/base_item.yml`; BaseBeaker supplies `beaker`, 50u and drainable behavior,
and LargeBeaker overrides capacity to 100u. The local `drainableSolution` typed
policy resolves by field-aware inheritance, denies absent/disabled policy, and
the `IReagentTrait.drainableSolution(stack, level)` getter is the only item
eligibility authority (capacity alone does not admit an item).

Dump RELEASE treats the client block position as a nomination only. The server resolves
the currently loaded ChemMaster block entity at that exact position without chunk loading,
then delegates authority to `ChemMasterWorldDump.transfer`, which revalidates controlled-body
authority, exact source identity/stack, machine placement identity, shared ordinary pickup reach
to both source and machine, and unobstructed LOS to both. RELEASE deliberately
does not require a server body-look ray to match the nominated cursor target: pinned
`SharedDragDropSystem` validates interaction access/range for both entities, not cursor-ray
agreement, and Minecraft free-cursor projection/press-release pose timing make an added gaze
gate a non-parity restriction. Consequently this adaptation does **not** claim server gaze-ray
validation; client nomination is not itself permission to transfer.

ChemMaster's `DumpableSolution` points to `buffer`; its component
default for `unlimited` is false while the ChemMaster prototype explicitly sets
true. Local `ChemMasterData.dumpableSolution()` preserves that distinction; its
buffer has no gameplay capacity cap beyond supported numeric reagent units.
At the pinned revision `Content.Server/Chemistry/EntitySystems/ChemMasterSystem.cs`
lines 53-55 handles the drag/drop UI-refresh event only; the actual transfer is
the generic `Content.Shared/Fluids/EntitySystems/SolutionDumpingSystem.cs`
lines 73-108. No ChemMaster-specific drained-target hook is claimed. These
containers have no supported Openable/lid state, so Minecraft adaptation does
not invent one or gate dumping on a fictitious closed lid.

Beaker visuals map the pinned `BaseBeaker -> BaseItem` composition from
`Resources/Prototypes/Entities/Objects/Specific/Chemistry/chemistry.yml`:
BaseBeaker's Sprite states are `beaker`/`beaker1`, fill is initially hidden,
and SolutionContainerVisuals defaults to `maxFillLevels: 6`,
`fillBaseName: beaker`, `changeColor: true`; its solution is `beaker` at
50u. LargeBeaker inherits this and overrides Sprite/Item RSI to
`beaker_large.rsi`, states `beakerlarge`/`beakerlarge1`, capacity 100u, and
fill base `beakerlarge`. `Content.Shared/Chemistry/EntitySystems/
SolutionContainerVisualsSystem.cs` computes the fill index using
`ContentHelpers.RoundToLevels(fraction, 1, maxFillLevels + 1)` calls
`Content.Shared/Rounding/ContentHelpers.cs:20-38`, which returns
`ceil(fraction * 5)` for `0 < fraction < 1` and tier 6 only when full. It is
not `(int)(solution.Volume / maxVolume * maxFillLevels) + 1`; the mapped
fill-layer color is the solution's blended color.
`maxFillLevels` (non-empty states 1..6; empty remains hidden), and applies
the solution's blended color to the mapped fill layer only.

The typed `solutionVisuals` policy inherits these supported state names and
settings. Minecraft item models use genuine transparent upstream `beaker1..6`
and `beakerlarge1..6` overlays above untinted base layer0; fill selection and
color reuse `ReagentComponent.getBlendedColor`
through existing `ModSpecialProperties` and item color handling. The existing
Minecraft generated-item renderer deliberately adapts by sharing six tiers
between hand and world item models rather than inventing SS14 hand layers.
Beaker tiers use exact cent-unit ceil(fraction * 5), distinct from Jug tiering:
standard thresholds step by 10u (50u full), large by 20u (100u full), with
tier 6 only at exact capacity. SS14 hand visuals separately use 3 standard / 4
large tiers (`InHandsMaxFillLevels`); sharing world six tiers is the deliberate
Minecraft hand adaptation.
