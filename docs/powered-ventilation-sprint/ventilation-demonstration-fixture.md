# Finite ventilation demonstration

## Run it

1. Start a disposable/test world with `enableAtmospherics=true`. With operator permissions, choose a clear, loaded **20×12×5** area and run `/ms14 ventilation_fixture build X Y Z`. The origin is the platform's northwest bottom corner; the command refuses occupied space. Relative coordinates work, but use the **absolute origin printed by Build** for subsequent commands.
2. Stay nearby, wait a few seconds, then run `/ms14 ventilation_fixture initialize X Y Z`. If ownership is still pending, wait and retry. Initialization is once-only: it fills the source room with finite air (8 mol O₂, 30 mol N₂, 2 mol plasma per cell at 300 K), leaves the recipient room vacuum, enables the plasma scrubber, and leaves the vent disabled.
3. Run `/ms14 ventilation_fixture status X Y Z` twice, several seconds apart. Expect **source plasma down, pipe plasma up, recipient plasma zero**. The pipe should report **11/11 expected connected nodes**. Delivery should reach **5 W** for each device and **100 W** for the alarm; a known meter alone does not mean full power.
4. The pre-paired alarm is at origin + **(6,1,6)**, accessible in the outside passage. Open it from the controlled body with an empty hand and **B**. Select the scrubber → Configure → disable it; Back → select the vent → Configure → enable it. Repeat Status: expect **pipe plasma down and recipient plasma up**. Do not break the room seals.

Source room: west glass window; recipient: east glass window. The visible pipe run is on the north side. APC/breaker: origin + **(15,1,7)**. No separate power construction or gas generator is needed.

Plasma is intentionally used because the existing gas overlay policy renders it at 2 mol in a 1 m³ cell. It is toxic and flammable; the initial 300 K mixture is below the 373.15 K fire threshold, but do not treat plasma as harmless or introduce ignition sources. Keep rooms sealed. Build a new fixture explicitly for this mixture; old initialized fixtures are never reseeded.

## What connects to what?

- A scrubber moves selected **room gas into finite pipe storage**. A connected pipe can terminate in open space: its bare end is capped, not an open-air exhaust. Another room is not required for capture.
- A vent moves stored **pipe gas into its sealed room**. Connecting a vent in a separate low-pressure room demonstrates release. Empty pipes cannot supply gas.
- Creative atmosphere producers inject gas into the **room cell in front of their facing**, not into pipe inventory. Point them into room air, not at a pipe. They have no pipe connector; electrical generators likewise do not supply pipe gas. The fixture replaces this setup with a one-time finite initial mixture.
- Concealed wall segments connect only along the clicked face/opposite-face axis, on the same lane. Exposed arms now show those validated joins; the wall itself remains concealed and solid.

## Implementation / limits

`ms14/atmos/piping/fixture/` contains the fixed authored plan, operator commands, placement service, bounded SavedData marker and measured telemetry. Existing resolved device/alarm prototypes, real power allocation, owned vacuum pipes, explicit alarm links and normal finite-room writes drive the fixture. Initialization requires existing loaded, proven finite ownership; it neither fabricates claims nor enables atmospherics itself. The persisted fixture token is not an alarm UUID. Partial initialization keeps its guard and requires a new fixture; crash-atomic setup is not promised.

SS14 correspondence: pinned `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, `unary.yml` (`GasUnaryBase`, `GasVentPump`, `GasVentScrubber`), `sensor.yml` (`AirSensorBase`), wallmount bases and `air_alarm.yml`; consumers `GasVentScrubberSystem` and `GasVentPumpSystem`. Plasma's overlay/fuel fields come from `Atmospherics/gases.yml`; `GasPrototype.cs` supplies inherited visibility defaults. This change reuses the existing local overlay policy without changing its thresholds or assets. Fixed Minecraft rooms, surface mounting, 1 m³ cells, operator construction, initial mixtures and pre-pairing are authored test-map adaptations, not new generators/tanks or automatic pairing behavior.

Server evidence: `build/ventilation-visible-gas-fixture-20261003.log` proves normal-loop initialization, paid capture, vent release and conservation of **36 mol plasma** across both rooms and all pipes. The owner confirmed the previous CO₂ fixture works in-client; Plasma's actual appearance and genuine restart acceptance remain pending. The full suite is not green.
