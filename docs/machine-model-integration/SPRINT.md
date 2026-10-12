# Machine model integration

**Closed by the owner on 2026-10-10.** Final APC ambient light is level 5;
see `HANDOFF.md` for the final build, acceptance evidence and remaining gaps.

## Scope

Import supplied ChemMaster/mixer, SMES/HV source, HV→MV substation and APC
Minecraft models, textures and native animation schedules. Bind supported
appearance states to authoritative gameplay. Convert APC to a real wall-mounted
block and preserve working station and fixture power connections.

Source workspace: `C:/Users/William/Documents/Minecraft Mod Dev Files/Asset Building Workspace/assets/Structures/`.
Canonical mixer export: `Machines/collection/export/resourcepack/`.
Power exports: `Power/{SMES,Substation,APC}/export/resourcepack/`.
Preserve source/modeler attribution and licenses. Do not modify source artwork
or import unrelated collection assets. User requested these imports despite
some export approval records still describing first-pass artwork.

## Ordered work units

1. ChemMaster assets and authoritative powered/occupied projection.
2. Power-device assets, resolved appearance data and wall-mounted APC behavior.
3. Station plans and every independent APC fixture placement/support audit.
4. Independent review, integrated build/unit/resource tests and full native suite.

Source work may proceed independently; use one integrated validator after joins.
Client GPU appearance and animation acceptance remains a manual check.

## Invariants and reuse

- Keep registered block IDs and existing power topology/port definitions.
  Graphics cannot grant power, reset block entities or alter allocation.
- Derive visuals from existing authoritative power meters, freshness, battery,
  breaker and owned-container state; never from UI visibility.
- Resolve and consume relevant typed prototype defaults. Extend existing
  catalogs where reasonable; do not create parallel power mechanics.
- ChemMaster uses its resolved receiver demand and existing full-fresh power
  predicate. Powered screen and compatible-container occupancy are independent.
- APC facing denotes its outward direction. Support and model/shape backing
  must agree; placement, support loss, collision and all four rotations matter.
- Explicit fixture/station support placement must not silently overwrite
  another device or cable host. No automatic support creation during gameplay.
- Native animated PNG schedules are preferred; no new animation framework for
  already-exported block models and texture cycles.

## SS14 correspondence

Pinned reference: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Relevant prototypes: `Resources/Prototypes/Entities/Structures/Machines/chem_master.yml`
and `Resources/Prototypes/Entities/Structures/Power/{smes,substation,apc}.yml`.
Resolve their parent chains and consuming appearance systems before extending
local data. Mixer has powered screens and a loaded layer, not a working timer.
APC is wall-mounted with battery/power indicators.

Deliberate existing adaptations: local HV source is fixed generation, not
SS14 SMES storage; local substation is conversion, not SS14 battery storage.
Do not invent charge/discharge mechanics or misleading indicators merely to
activate every supplied art variant. Unsupported maintenance/damage/remote
states remain unbound unless existing gameplay supplies those states.

Verified APC display semantics (`ApcSystem.CalcChargeState`): charge fraction
strictly greater than 0.9 selects Full; otherwise net receiving greater than
supply selects Charging; otherwise Lack. Lack does not mean only zero charge.
The upstream visualizer does not directly map breaker-open to an unpowered
sprite; breaker effects must flow through actual power/battery state. Visual
updates are limited to once per second upstream. Relevant consumers are
`Content.Server/Power/EntitySystems/ApcSystem.cs` and
`Content.Client/Power/APC/ApcVisualizerSystem.cs`.

## APC lighting follow-up

Owner requests a glowing screen and minor nearby light when the APC has power.
Keep screen fullbright rendering separate from block-light emission. Verified
upstream uses an unshaded ChargeState layer and a separately inherited PointLight
(radius 1.5, energy 3, color green, shadows off); its visualizer updates color,
not enable state. Minecraft's small monochrome block light and explicit
power-availability gate are adaptations. Preserve charge-display semantics and
do not directly tie screen state to breaker position.

Final owner brightness adjustment: resolved APC block-light level **5** (raised
from 3). Keep screen fullbright, actual power predicates and lamp level 14
unchanged. Native emission must remain state-owned for chunk source discovery.

## Acceptance checks

Resource dependencies, facing variants and animation metadata resolve; runtime
appearance follows supported states without viewers; prototype inheritance and
defaults are tested through live consumers. Wall-mounted APCs have correct
outline/collision, attachment and support-loss behavior, retain electrical
connections, and work in starter station and all fixtures. Report filtered/full
test selection and actual pass/failure counts, retaining useful failure artifacts.
Do not claim full-suite green from focused results or GPU correctness from
headless tests.
