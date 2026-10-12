# Power-machine native appearance projection

## Local resolved profile and consumer

`moonstation14/power_appearance` is a display-only typed family. The abstract `base_power_appearance` provides inherited display defaults and model-space geometry; concrete `hv_source`, `hv_mv_substation`, and `apc` prototypes override only their appearance identifiers and APC enclosure bounds. `PowerRuntime.projectPowerDeviceAppearances` consumes the resolved catalog after the existing 20-tick `PowerLiveLoop.solve`; a state mutation is made only when the native `DISPLAY` value differs. No topology, facing, battery, or BE identity field is changed by that projection. The APC shape is also consumed from its resolved profile.

SMES (`SMESBasicEmpty -> BaseSMES -> [BaseMachine, ConstructibleMachine]`) is upstream an HV/HV 8 MJ battery with 150 kW supply. Local `HV_SOURCE` remains the existing fixed 25 kW source, so only the fixed-on/off art is adapted; upstream battery, charging, discharging, UI and monitoring components are not claimed or added. Its charging/discharging assets are deliberately not bound.

Standard substation (`SubstationBasicEmpty -> SubstationBasic -> BaseSubstation -> [CoreSubstation, BaseMachine, ConstructibleMachine]`) inherits a 2.5 MJ battery; the separate wall composition derives through `BaseSubstationWall` and has 2 MJ. Local HV-to-MV conversion is stateless and has no charge store, so `full`/`dead` here means live conversion supply present/absent, not battery fullness. The authored charging animation is not bound to invented storage.

`APCBasic -> BaseAPC -> BaseWallmount -> BaseStructure` carries the upstream 50 kJ battery, MV input, APC output and `ApcVisuals`. The local existing APC remains authoritative for its Minecraft-only 1 MJ battery and breaker. Existing solve energy deltas drive the source's three display states: `FULL` only above 90%, otherwise `CHARGING` while stored energy increases and `LACK` otherwise. The native APC system derives charge state from battery fullness and current supply/receiving; local delivery and energy deltas are its available authoritative equivalents. An open breaker does not force an unpowered sprite: displayed charge reflects the resulting battery state. Screen changes remain at solve cadence (20 ticks), not the upstream one-second visual throttle. Unknown readings conservatively map to `LACK`, matching the upstream visualizer's missing-charge-state default rather than claiming a disconnected state.

No upstream lock/channel, emag, remote, panel-open, damage or electrical-charge mechanics are represented by this appearance profile. Their exported overlays remain unbound. The APC's thin enclosure collision/outline is a Minecraft wallmount adaptation, not imported upstream physics. Native PNG schedules (`.mcmeta`) are retained and animate the selected APC full/charging/lack and substation base states through vanilla block models.

The APC's Minecraft-only powered glow is separate from SS14 charge-state selection: the native screen remains `full`/`charging`/`lack` according to the existing visual-state projection, while a derived `GLOW` state is true when resolved battery energy is positive or actual solved APC input is positive (including an open breaker with stored charge). The APC profile supplies minor block light level 5; inherited profiles default to zero. Powered variants add a separate, minimally offset screen-only overlay to each of the three native animated-state atlases. The overlay's front face uses NeoForge 21.1 face `neoforge_data` (`block_light:15`, `sky_light:15`, `ambient_occlusion:false`) and is unshaded; case geometry and its lighting remain unchanged. This is a Minecraft rendering adaptation, not a claim that SS14's unshaded sprite layer emits point light. The original native models/textures remain unchanged; the overlay models and multipart state bindings are local derivatives.

## Reference

Verified SS14 checkout: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `Resources/Prototypes/Entities/Structures/Power/{smes,substation,apc}.yml` (prototype parents/components/defaults)
- `Content.Server/Power/EntitySystems/ApcSystem.cs` (`CalcChargeState`, `VisualsChangeDelay`)
- `Content.Client/Power/APC/ApcVisualizerSystem.cs` (charge-state screen layer and colors)
- Local source of truth: `PowerDeviceRules`, `PowerRuntime`, `PowerLiveLoop`, and `ApcVisualState`.

Asset authorship/provenance for each imported family is retained under `assets/moonstation14/power-provenance/` alongside the original attributed export bundle.
