# Composition and parity baseline

AtmosMonitor registration follows pinned SS14 `Content.Server/Atmos/EntitySystems/AtmosMonitoringSystem.cs` (revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`): Register adds the sender address to transient registered devices, Deregister removes it, and neither command replies. Local ventilation entities keep this address-subscription state separate from `DeviceList`; only Sync requests a state reply. This does not implement automatic alarm thresholds/status broadcasts or claim full atmos-alarm parity.

Verified upstream: `c9df5ef5d675b0d1d226828bddf6b78c28502d91` in
`C:\Users\William\Documents\SS14-dev\space-station-14`.
This is the implementation baseline, not a claim that every behavior below has
already been delivered. Concrete field mappings and validation accompany implementation.

## Entity composition

- `NetworkConfigurator → BaseItem`: item/physics/click defaults, configurator
  component, in-hand ActivatableUI and List/Configure/Link interfaces. Link mode
  defaults true; List activation is cancelled in Link mode. AltUse changes mode.
  Its Sprite/Appearance/GenericVisualizer composition uses the animated `icon`
  plus an independent `ModeLight` layer (`mode-link` or `mode-list`), not a base
  icon swap. Pinned RSI metadata declares 32 frames at 0.1 seconds each (two
  Minecraft ticks); the earlier investigation's 45-frame count was incorrect.
  Sources: `tools.yml:187–231`, `network_configurator.rsi/meta.json`, and
  `NetworkConfiguratorSystem.cs:265–304,321–325,446–459`. Upstream examination
  reports mode, and switching sets appearance and sound pitch. Local Alt+E is
  the existing body alternative-action binding; no new keybind is introduced.
- `RemoteSignaller → BaseItem`: Signaller, SignalOnTrigger(Pressed), UseDelay,
  UseDelayOnTrigger, UseDelayTriggerCondition, DeviceLinkSource(Pressed), Wireless
  DeviceNetwork with RX BasicDevice and no configured TX frequency,
  WirelessNetworkConnection(range 15).
  `RemoteSignallerAdvanced` inherits it and overrides range to 50.
  Resolved default address prefix is empty; the local shared wireless network
  catalog does not invent a `remote-` prefix.

The pinned `SharedInteractionSystem.cs` (lines 1209–1235) checks UseDelay before
UseInHand and calls TryResetDelay when handled with ApplyDelay enabled. Thus the
one-second held-use cooldown is verified upstream behavior. The Minecraft remote
records that cooldown on every admitted non-delayed activation, even when delivery has no
linked receiver or fails (for example, an overloaded sink); TX remains unconfigured
(resolved frequency 0) while RX resolves to BasicDevice (1280). Queued linked
remote delivery remains valid through the existing explicit target-RX override.
An early repeat is silently handled, without another emission or extending its
deadline, matching the generic upstream interaction gate; it is not an authority
denial. Both held and owner-requested loose-world activation share that operation.
Ground E activation is a deliberate Minecraft adaptation: the pinned signaller
does not declare `TriggerOnActivate`. Exact nominated loose-target admission
remains separate from held-use proof, while stack identity/address, port policy,
wireless delivery and use delay remain shared. Configurator target use retains
priority over activation when that tool is held.
- `SignalButton`, `SignalSwitch → BaseWallmountMetallic → BaseWallmount`: anchored
  wallmount defaults; wireless BasicDevice/range 200 and DeviceLinkSource, no APC
  receiver. Button uses Pressed for both switch directions and no remembered
  binary status. Switch emits On/Off and remembered Status, initially Low. Both
  inherit an empty default address prefix; local `button`/`switch` prefixes were
  removed.
- `Airlock → BaseStructure`: Door/Airlock/DoorBolt, APC receiver load 20,
  electrical wires/electrification, Wireless BasicDevice/range 200, sinks
  Open/Close/Toggle/AutoClose/DoorBolt and source DoorStatus initially Low.
- `AirAlarm → BaseWallmountMachine → BaseWallmountMetallic → BaseWallmount`:
  100 W receiver, AtmosDevices/AtmosMonitor TX/RX, `AIR-` address prefix and broadcast
  attempt hook, wired and wireless(range 200), DeviceList and RequiresPower.
- Unary vent/scrubber inherit `[GasUnaryBase, AirSensorBase]`; GasUnaryBase inherits
  GasPipeBase. AirSensorBase supplies AtmosDevices/AtmosMonitor network, wired
  connection and broadcast hook. GasUnaryBase has no address prefix; concrete vent
  and scrubber use local `VNT-` and `SCR-` prefixes respectively. **Neither unary
  prototype declares RequiresPower**: network
  reception must not be conditioned on configured enablement or paid gas work.

Prototype sources: `Resources/Prototypes/Entities/Objects/Tools/tools.yml`,
`Objects/Devices/Electronics/signaller.yml`,
`Structures/Wallmounts/Switches/switch.yml`,
`Structures/Doors/Airlocks/base_structureairlocks.yml`,
`Structures/Wallmounts/WallmountMachines/air_alarm.yml`,
`Structures/Wallmounts/base_wallmount.yml`,
`Structures/Piping/Atmospherics/{pipes,unary}.yml` and
`Structures/Specific/Atmospherics/sensor.yml`.

## Runtime contracts

Link editor opening uses loaded source/sink components, not transport availability
or power, matching `NetworkConfiguratorSystem.TryLinkDevice/OpenDeviceLinkUi`.
The source's link range is enforced when creating links, not before showing ports.
Gameplay transport and airlock power/bolt/safety checks remain separate.

DeviceLinking is a directed port graph; DeviceNetwork transports queued packets;
DeviceList filters membership; electrical Wires remain independent. BasicDevice
frequency is 1280, AtmosMonitor 1621. Network IDs Wireless=2, AtmosDevices=4.
DeviceList defaults: limit 32, allow-list true, incoming filter false.

Link range 30 is checked at setup, wireless sender range during dispatch. High/Low
is remembered, Momentary is not; clearing memory is not Low. Overload sink counter
decays one each tick, checks `count > limit` before increment with default limit
10, then disconnects all sources to the overloaded sink.

Airlock Open/Close/Toggle ignore Low; power/bolt/safety gates remain authoritative.
AutoClose/Hold disables auto-close regardless of signal level until Closed resets
it. DoorBolt Momentary toggles, High lowers and Low raises. DoorStatus Closed is
Low; Open/Opening/Closing/Emagging is High upstream. Unsupported local phases are
not created just to complete the enum.

Atmos register/deregister/sync commands are `atmos_register_device`,
`atmos_deregister_device`, `atmos_sync_data`; set-state carries typed
`IAtmosDeviceData`. SyncData replies populate address-keyed alarm state; enqueue
is not an acknowledgement. Pump data includes enabled, pressure bounds/checks,
direction and override fields; scrubber includes enabled, filters, direction,
volume rate and wide-net/panic flags. This sprint consumes the port's supported
manual settings without inventing unsupported automatic alarm modes.

System sources: shared/server DeviceLinking and DeviceNetwork systems,
`DoorSignalControlSystem`, `SharedAirlockSystem`,
`SharedNetworkConfiguratorSystem.OnUiOpenAttempt`,
`Content.Server/Atmos/Monitor/Systems/AtmosDeviceNetwork.cs`, `AirAlarmSystem`,
shared unary `GasVentPumpData`/`GasVentScrubberData`.

## Deliberate Minecraft boundaries

Use existing server controller identities; portable identity travels in actual
stack data, not ownership/session tokens. Loaded endpoints only; unloading retains
links but makes endpoints offline. No packet delivery forces chunks.

One dimension adapts one static grid; 3D block distances adapt map-unit ranges.
Separate bases in one dimension therefore share that wired transport scope, unlike
independent SS14 grids. No cable-derived communications topology is invented.
Player interaction and retained-window authority reuse body/hands services;
gameplay machine signals do not impersonate a player's hands.

Asset attribution and live-validation results are separate acceptance evidence.

## Storage footprints

Footprint profiles are authored by item ID and reuse the existing `small` parent
(two cells) for `NetworkConfigurator` and `RemoteSignaller`; the advanced remote
inherits the latter profile. At pinned revision
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`, `tools.yml` explicitly sets
NetworkConfigurator `Item.size: Small`; `signaller.yml` inherits `BaseItem`, whose
`Item.size` default is `Small`, and its advanced child inherits that item. In
`Resources/Prototypes/item_size.yml`, Small's `defaultShape` is coordinates
`0,0,0,1` (a two-cell vertical shape), while Normal is `0,0,1,1` (2x2); the local
profiles preserve Small's actual shape rather than infer a rectangle. SS14
`SignalButton` and `SignalSwitch` inherit wallmount prototypes and have no Item
composition/default storage shape. Their one-cell local footprints are a deliberate
Minecraft block-item storage adaptation, not a claim about SS14 item size.

## Local wall-control mapping

At the pinned revision, `SignalButton` and `SignalSwitch` both inherit
`BaseWallmountMetallic → BaseWallmount`. The local `signal_control` family records
the anchored/non-colliding wallmount defaults in `base_wallmount`, inherits them
through `base_wallmount_metallic`, and resolves each concrete control's
`DeviceComponents` against the shared network/frequency/port catalogs. The button
declares `Pressed` and authored Momentary outputs for both transitions, with no
remembered status. The switch declares `On`, `Off`, and `Status`, Momentary On/Off
outputs, remembered initial Status Low, and High/Low Status transition outputs.
The shared block entity's persisted UUID is the graph/endpoint identity. Its separate
random `XXXX-XXXX` network address belongs to the dimension's `DeviceLinkSavedData`
and survives unload/reload. Default collisions regenerate the address, not identity.
Button/switch blocks share one wallmount implementation;
the resolved output policy, not a block-kind branch, determines signal behavior.

The local adaptation does not implement SS14's StationAiWhitelist, Rotatable,
construction/destruction systems, use delay, or wallmount arc-constrained snapping;
Minecraft's ordinary block placement/use mechanics remain in effect, with placement
against a sturdy face. Imported `switch.rsi` `dead`, `on`, and `off` frames are
from the pinned SS14 texture directory; provenance/licensing is recorded in the
adjacent texture metadata. Real switch actions consume the prototype's click sound,
using the pinned `lightswitch.ogg`, not the distinct APC switch clip. Airlocks
compose the pinned defibrillator zap on device-link overload; other shipping
devices do not gain an invented overload sound. Unresolved file-specific audio
attribution is retained in `docs/audio-unknown-provenance.md`.

## Player-facing network addresses and configurator editing

Pinned `DeviceNet.GenerateValidAddress/Add/RandomizeAddress` uses a random 32-bit
uppercase `XXXX-XXXX` suffix, optional localized prefix, retained automatic
addresses and same-network collision retry; custom duplicates refuse connection.
The port now uses that format/policy rather than UUID-derived addresses. Resolved
English prefixes are `AIR-`, `VNT-`, `SCR-`; controls, airlocks and signallers have
none. Local typed prefixes store the English literal rather than an SS14 locale
key. UUIDs remain internal graph/owner identities. Block default-address ownership
uses existing per-dimension network SavedData (one static transport grid); portable
ownership uses actual signaller-stack data. This is the persistence adaptation,
not a second address/collision registry. Unload retains addresses; destruction
forgets block defaults. Composition reads do not allocate random addresses.

`NetworkConfiguratorLinkMenu.xaml.cs` provides direct port-to-port lines, pending
selection toggling, and Clear Links separate from link creation. Source-port
prototypes define defaults: a button/airlock matches `Pressed → Toggle`; source
defaults are not an instruction to connect every port. Local Clear also removes
pending highlights, as requested. Viewport area is capped at one-sixth with
chat-scale body text; this is the owner's Minecraft presentation adaptation.
Remote Signaller has DeviceLinkSource(Pressed) and is linkable as a world entity;
the local held configurator's exact nominated loose-item `USE_TARGET` route now
uses the existing authenticated selector, rather than depending on a block behind it.

Switch `Status` is remembered High while on and Low while off; `On`/`Off` are
momentary events. Airlock Open/Close/Toggle ignore Low, so `Status → Toggle` is
not symmetric on/off door control. `On → Open` and `Off → Close` provide that
simple control; DoorBolt explicitly consumes High/Low as lower/raise.
