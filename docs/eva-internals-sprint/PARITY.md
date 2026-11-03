# EVA/internals source contract

Reference checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Paths below are relative to that checkout. This is a source/design contract, not a claim that the complete local feature is validated.

## Composition

- `ClothingMaskBreath -> ClothingMaskPullableBase -> ClothingMaskBase -> Clothing -> BaseItem`: simple mask, BreathMask, ingestion blocker and pull/lower action. Use this rather than implying unimplemented gas-filter functionality.
- `ClothingHeadHelmetEVA -> ClothingHeadEVAHelmetBase -> ClothingHeadBase -> Clothing -> BaseItem`: HEAD clothing, BreathMask, pressure protection high multiplier 0.6/low multiplier 1000 and thermal heating/cooling coefficients 0.2. Equip/unequip delay is two seconds.
- `ClothingOuterHardsuitEVA -> ClothingOuterEVASuitBase -> [ClothingOuterBase, AllowSuitStorageClothing] -> Clothing/BaseItem`: the basic EVA suit despite the leaf name; high/low pressure multipliers 0.6/1000, thermal coefficients 0.01/0.01, outer clothing and suit storage.
- `OxygenTank -> GasTankRoundBase -> GasTankBase -> BaseItem`; `EmergencyOxygenTank -> GasTankRoundBase`. Filled catalog leaves inherit their corresponding physical tank. Regular volume is 5 L; emergency volume is 0.66 L. At 293.15 K the filled oxygen amounts are respectively 2.051379050 and 0.270782035 mol. Empty profiles do not acquire these fill quantities on read.

Sources: `Resources/Prototypes/Entities/Clothing/{Masks,Head,OuterClothing}/` base/leaf definitions, `Entities/Objects/Tools/gas_tanks.yml`, and `Resources/Prototypes/Catalog/Fills/Items/gas_tanks.yml`. Local physical item IDs/factory mapping and articulated 3D meshes are explicit Minecraft adaptations.

## Breathing, selection and ownership

`BreathToolComponent` is registered under YAML `BreathMask`; allowed slots default to MASK and HEAD. Equipped functional tools connect to Internals. Lowering/removing a tool disconnects it; losing the last tool disconnects tank supply. Basic EVA helmets can supply the breath-tool function without a separate mask. No stable helmet-over-mask selection priority should be inferred from the upstream HashSet.

Tank selection checks Back, suit storage, then hands/inventory, preferring a non-jetpack tank; it does not guarantee gas species suitability. Exact selected identity must survive normal carried-slot moves but not removal from the body. Item-provided `ActionToggleInternals` and `ActionToggleMask` are the control precedent; no dedicated upstream internals keybind was established.

`RemoveAirOutput -> RemoveVolumeAtPressure` requests `pressure × volume / (R × temperature)` moles, bounded by available contents. Default outlet is 21.3 kPa; default breath volume is 0.5 L. Returned gas volume is normalized to the breath volume. Low remaining tank pressure does not change that requested output formula; exhaustion returns less gas, then empty gas. Zero outlet yields an empty draw. Empty tanks can stay connected and respiration handles the failed oxygen supply; do not silently supply ambient air because the tank is empty.

Respirator updates default to two seconds, alternating inhaling/exhaling: one inhale per four seconds absent overrides. Preserve the local resolved cadence rather than adding a separate drain timer. Low-pressure alert compares internal tank pressure to 30 kPa, not outlet pressure. Exhalation is routed to ambient/containing air, never back to the supply tank.

Sources: `Content.Server/Body/Systems/{RespiratorSystem,InternalsSystem}.cs`, `Content.Shared/Body/Systems/SharedInternalsSystem.cs`, `Content.Shared/Atmos/{Components/BreathToolComponent,EntitySystems/SharedAtmosphereSystem.Gases,EntitySystems/SharedGasTankSystem}.cs`, and `Content.Server/Atmos/EntitySystems/GasTankSystem.cs`.

The initial local loop is sealed-tank breathing. Open-valve venting, adjustable regulator controls, rupture and other tank subsystems remain outside this scope; their upstream presence must not be confused with delivered local support.

## Pressure

`BarotraumaComponent.ProtectionSlots` defaults to `head` and `outerClothing`. All required slots need PressureProtection; missing coverage resets clothing resistance to neutral. The weakest part governs: maximum high-pressure multipliers/modifiers and minimum low-pressure multipliers/modifiers. The helmet and suit do not multiply their pressure resistances together.

Crucially, upstream `BarotraumaSystem.Update` clamps a **known** ambient pressure to at least 1 kPa before applying protection. Low felt pressure is `min((pressure + modifier) × multiplier, 101.325)`; high felt pressure uses `max(..., 101.325)`. Apply the appropriate branch at the upstream warning thresholds, then existing hazard damage. Without the 1 kPa floor, a literal zero multiplied by 1000 would wrongly make a complete EVA set fail in vacuum. This clamp must not turn unknown samples into physical air or change tank/temperature gas data.

Source: `Content.Server/Atmos/EntitySystems/BarotraumaSystem.cs:85-168,224-256`; `Content.Server/Atmos/Components/{BarotraumaComponent,PressureProtectionComponent}.cs`.

## Temperature

`TemperatureSystem.ChangeHeat` relays `ModifyChangedTemperatureEvent` to equipped, non-pocket clothing, not held items. Each applicable protection multiplies the heat delta by its heating/cooling coefficient before heat-capacity conversion. Apply this to external exchange and preserve finite-air energy accounting; it is not an armor/damage reduction. Stored body temperature independently controls thermal damage.

Intrinsic regulation must retain its own source semantics. In particular, `AdjustTemperatureEntityEffectSystem` calls `ChangeHeat(..., ignoreHeatResistance: true)`: direct reagent heat bypasses clothing insulation. Do not infer otherwise merely because the local effect schema has no ignore flag.

Sources: `Content.Server/Temperature/Systems/TemperatureSystem.cs:73-105,121-130`, `Content.Shared/Temperature/TemperatureEvents.cs`, and `Content.Shared/EntityEffects/Effects/AdjustTemperatureEntityEffectsSystem.cs:15-20`.

## Asset and validation boundaries

Use source RSI icons and internals audio with their own metadata/attribution. Worn models are new Minecraft adaptations using existing articulated wearable owners, not upstream geometry. Preserve provenance and require actual runtime references; icons or procedural fallback geometry are not finished worn assets. GPU appearance/audio and real-player usability remain owner observations even after automated checks pass.
