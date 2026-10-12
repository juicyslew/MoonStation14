# Device signals and networking sprint plan

## Status and goal

Implementation authorized by the owner on 2026-10-08. Deliver all shipping scope
and automated validation before requesting owner hands-on testing. The owner
supplied a Network Configurator port-link UI reference. ChemMaster is owned by
another agent; refresh its landed changes and coordinate shared item/UI edits.
New devices must reuse composition, transport and linking services with small
domain adapters, not duplicate networking logic.

Implementation references: `CORE-CONTRACT.md`, `CORE-COMPONENTS.md`, `PARITY.md`
and `EXTENDING.md`. `MANUAL.md` is the concise owner checklist, to be used only
after automated acceptance. Implementation is not owner-accepted or closed.

Deliver usable device communication, not an isolated demo framework:

1. Configure a Remote Signaller or wall button to operate an existing airlock.
2. Configure switch On/Off outputs and use the airlock's other declared ports.
3. Use Network Configurator device lists to set up an air alarm, then control
   existing vents/scrubbers through real addressed, queued device messages.

The airlock's forthcoming visual redesign is separate. Bind to controller
identity and domain behavior, not this sprint's predecessor model geometry.

## Verified upstream baseline

Reference checkout: `C:\Users\William\Documents\SS14-dev\space-station-14`,
verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

Keep three systems distinct:

- **DeviceLinking:** directed source-port → sink-port relationships; invocations
  and remembered logic states. Linking is not electrical wire sabotage.
- **DeviceNetwork:** server-side gameplay packets routed by network ID,
  frequency and address. This is not Minecraft's multiplayer socket transport.
- **DeviceList:** explicit entity-reference membership and allow/deny filtering,
  used by networked machines such as air alarms.

### Confirmed composition and values

| Entity/system | Relevant resolved composition and behavior |
| --- | --- |
| NetworkConfigurator → BaseItem | Distinct tool, not a multitool alias. NetworkConfigurator component and List/Configure/Link BUIs; in-hand List activation. Default Link mode; alternative action switches mode. |
| RemoteSignaller → BaseItem | Signaller, Pressed source port, trigger/use-delay composition, Wireless/BasicDevice, wireless range **15**. **No frequency/code settings UI**. |
| RemoteSignallerAdvanced → RemoteSignaller | Wireless range override **50**; shared behavior, not another implementation. |
| SignalButton / SignalSwitch → BaseWallmountMetallic → BaseWallmount | Wireless/BasicDevice, range **200**, DeviceLinkSource; no APC power declaration. Button invokes Pressed on either toggle direction. Switch emits On/Off plus remembered Status High/Low, initially Low. |
| Airlock → BaseStructure | Existing Door/Airlock/Bolt/power/wires composition plus DeviceLink source/sinks, Wireless/BasicDevice and wireless range **200**. Preserve physical controller rules. |
| AirAlarm → BaseWallmountMachine → BaseWallmountMetallic → BaseWallmount | **100 W** receiver; AtmosDevices/AtmosMonitor TX/RX; DeviceList, wired connection and wireless range **200**. Full automatic alarm/threshold behavior is not implied by this networking slice. |
| GasVentPump / GasVentScrubber → [GasUnaryBase, AirSensorBase]; GasUnaryBase → GasPipeBase | Inherited AtmosDevices/AtmosMonitor network, broadcast hook and wired connection; concrete vent/scrubber supply their own power receivers and address prefixes. |
| DeviceList | Capacity **32**, allow-list true, incoming filtering false. Empty allow-list cancels broadcasts when the owner enables the broadcast-attempt hook. |

Network IDs: Private=0, Wired=1, Wireless=2, Apc=3, AtmosDevices=4.
Frequency prototypes: BasicDevice=**1280**, AtmosMonitor=**1621**. Connection
components and network IDs are different concepts; do not infer one from the other.

Source link range defaults to **30** and applies when creating/saving links,
not on every invocation. Wireless delivery uses the sender's configured range
and same-map restrictions. A standard remote can therefore be linked within 30
but must be within 15 to send successfully; advanced range is 50 after setup.

### Signals and airlock semantics

- Invocations are Momentary; maintained High/Low signals are remembered and replayed
  to new links. Clearing remembered state is not sending Low.
- Open/Close/Toggle accept High or Momentary and ignore Low. Open/Close are
  state-appropriate operations, not aliases for blindly toggling.
- The UI label **Hold** refers to sink ID **AutoClose**: any received signal
  disables auto-close until the door reaches Closed, which resets it. It is not
  a maintained boolean hold/release command.
- DoorBolt: Momentary toggles, High lowers bolts, Low raises them.
- DoorStatus sends Low on Closed and High for upstream Open/Opening/Closing/
  Emagging states. Map supported local phases explicitly; do not fabricate Emagging.
- Normal power, bolt and closing-safety rules still apply. Do not simulate a
  player hand request or pulse an electrical wire to implement a signal input.
- Sink invocation count decays by one per game tick. Default limit is 10, with
  upstream's pre-increment `count > limit` check: the eleventh same-tick invocation
  is accepted; the next overloads. Overload resets the count, emits the sink event,
  and removes **all source links to that sink**, not just one port pair.

When both linked endpoints have DeviceNetwork, DeviceLink uses addressed wireless
packets; otherwise it dispatches a direct signal event. Preserve this distinction.
Packets are delivered in a later network update pass, not synchronously on enqueue.

## Shipping scope / exclusions

**Include:** shared port definitions and resolved source/sink/network/list data;
persistent link graph; network addressing, frequencies and queues; standard and
advanced Remote Signaller; Network Configurator's three windows; wall SignalButton
and SignalSwitch; existing airlock input/output bindings; air-alarm and unary-device
manual-control packet integration.

**Do not include automatically:** timers, logic gates/latches, pressure pads,
programmable controllers, departmental access, full sensor alarm thresholds and
automatic atmos presets, chat/security/PDA networking, lamp tube replacement or
new lighting internals, redstone bridging, separate data cables, or new airlock art.
Lamp On/Off/Toggle is an upstream-supported future consumer, but its full local
power/tube composition has not been audited for this sprint.

There is no verified Signaller-specific wire-attachment workflow at this pin.
Do not invent one or import SS13-style frequency/code radio controls.

## Architecture decisions and mandatory reuse

### One endpoint identity, one graph/list authority

Use existing persistent controller UUIDs for block endpoints; resolve current
network addresses independently of physical identity. Address/frequency changes
must not turn a link into a positional reference to a replacement machine.

Portable signallers need persisted item-device identity/address data: hand tokens
are **not** stable identities. `BodyHandItemTransfer` mints a new token on pickup
and does not preserve a hand token on drop. Persist device metadata in the actual
stack and mutate through existing ownership gateways. Preserve graph associations
through drop/pickup, transfer and restart; do not create another held inventory.

Promote/extend existing `AirAlarmDeviceLinks` into the shared DeviceList ownership
model, retaining its actual controller membership as the sole authority. Do not
keep direct UUID pairing and new addressed membership as competing lists.

Chunk unload means offline, not destruction: retain links/list membership while
unloaded, never force-load to deliver, and clean up retired/replaced identities.
This is a Minecraft lifecycle adaptation, not a save-migration/recovery project.

### Transport and domain behavior

Use a shared queued, server-owned transport with addressed/broadcast routing,
frequency/network membership, receive-all behavior and endpoint/filter hooks.
Payloads should be typed command families, not an arbitrary scripting framework.
Retain direct event delivery where upstream lacks one endpoint's network component.

SS14 "wired" currently means same grid, **not power-cable traversal**. The port has
no verified movable-grid identity service. Proposed local adapter: a Minecraft
dimension is one static transport grid, with 3D block distances for wireless/link
range. Record the departure: separate bases in one dimension are not separate
SS14 grids. Keep the scope adapter explicit; do not derive it from `LoadedPowerGraph`.

Network receive-power eligibility and operational enablement are distinct. A
disabled but powered vent must remain remotely re-enableable; paid gas-work
authorization remains separate and still prevents unpaid/disabled gas movement.
Audit current power-demand/enable coupling before wiring network eligibility.

Air alarms use register/deregister/sync/set-state and returned device state.
Membership changes reset stale caches and register/sync the new set. Reuse existing
ventilation config reducers, identity/revision validation and finite paid-work
consumers. Do not add a fictitious generic packet acknowledgement: existing UI
correlation/drafts must reconcile asynchronous observed state as a port adaptation.

### Handheld authority and UI

Reuse `HealthAnalyzerServer`, `BodyHeldItemMutation`, `LiveHandsCommitGateway`,
`BodyHandRequestService`, `HandActorAuthority`, body-eye targeting and the retained
`StoragePopupScreen` workspace. Analyzer/wire sessions are precedents, not a generic
implemented device framework. Revalidate actual body/item ownership and current
target identity; a read-only probe or open menu is not mutation authority.

Selecting a dropped remote needs a narrow held-tool-on-world-item operation that
reuses target probing and both owners' mutation paths; no generic route currently
exists. Support the complete drop → configure → pick up → activate workflow.

Validate physical reach on each endpoint-selection interaction. Link mutation
uses source/sink link range and selected identities; do not incorrectly require
the actor to remain in hand reach of both endpoints after walking between them.
DeviceList Configure has its own target-range lifetime rule. Stun/critical action
admission and passive/read-only UI lifetime remain separate.

Reuse existing item use-in-hand and contextual-action routes for activation and
mode switching. Do not invent a new keybind or overload multitool PULSING.

## UI deliverables

1. **Link window — owner image reference.** Source left, sink right, localized
   port names/descriptions, connection lines, selection feedback, actual endpoint
   addresses (blank when absent), Net#Link ™ footer, Clear / Link defaults / Done.
   Picking opposite-side ports toggles that pair. Clear and defaults commit
   immediately; Done closes and resets active endpoint selection, not saves drafts.
2. **List window.** Configurator's staged address/name entries; remove/clear.
   Do not cap this buffer at 32 merely because target lists have capacity 32.
3. **Configure window.** Target DeviceList entries and Set/Add/Clear/Copy/Show,
   individual removal and membership visualization. Set replaces; Add merges;
   Copy stages target membership; Show is the upstream visualization workflow.
4. **Existing AirAlarm UI integration.** Preserve working device/detail drafts,
   controls and filters while changing their authoritative backend and setup flow.

Use the delivered machine visual kit (`MachineSearchList`, draft models, buttons,
tooltips and existing fonts). Add only the missing feature-specific port-link
canvas; do not build a general graph-editor/UI framework. Mode switching is a
tool action, not an invented toolbar on the supplied Link screenshot. Remote
Signaller has no separate settings window at this revision.

## Ordered work units

### 1. Resolved composition and endpoint lifecycle

Write the verified upstream→local component/default mapping before adding fields.
Define shared port/network/list data and frequency prototypes; extend existing
machine families rather than duplicate their data. Establish controller and stack
identity, loaded indexing, address lifecycle, scope adapter and body-held targeting.
Confirm current item/UI APIs against ChemMaster changes before assigning shared files.

**Gate:** inheritance/default tests and real block/portable endpoint lifecycle tests;
no JSON-only feature or duplicated identity source.

### 2. Transport, signal graph and device consumers

Implement queued packet routing, filters and shared DeviceList service; persistent
links, state replay, disconnect/overload semantics; wall button/switch sources and
all supported airlock input/output ports. Reuse existing state machines and power
services; keep signal commands separate from electrical wire actions.

**Gate:** real button/switch→airlock operation; wrong network/frequency/range rejects;
empty allow-list broadcasts cancel; direct/queued paths differ correctly; loop/
overload behavior is bounded and matches the pinned counter semantics.

### 3. Configurator and portable remote workflows

Deliver the three configurator windows and mode/target interaction flow. Standard
and advanced remotes invoke Pressed through existing held-use admission. Preserve
source identity/address/links through dropped-world and held ownership transitions.

**Gate:** pictured immediate-action UI behavior; defaults configure Pressed→Toggle;
remote range 15/50 tests after setup within 30; drop/pickup/new owner/restart;
stale tool/body/endpoint rejection; no invented radio settings or redundant multitool.

### 4. Air-alarm networking integration

Replace direct control/pairing with explicit configurator membership and queued
AtmosDevices/1621 register/sync/set-state handling for existing vents/scrubbers.
Extend existing DeviceList persistence and reducers, remove competing setup paths,
preserve current desired fields and finite gas/power authority.

**Gate:** select vents/scrubbers → Set/Add → alarm receives state → configure →
queued device change → returned state updates UI. Empty list controls nothing;
replacement/offline devices cannot inherit authority; operationally disabled but
powered devices can be re-enabled; gas work remains conserved and paid.

### 5. Integration, assets and acceptance

Import pinned SS14 sprites/sounds only after their provenance audit; retain metadata
and attribution, recording unclear audio in `docs/audio-unknown-provenance.md`.
Run full unit/build/GameTest compilation, focused tagged integration fixtures and
the full native suite for this broad change. Serialize heavy validation; isolate
fixture directories, preserve useful failures and remove passing disposable runs.
Report selected/discovered and pass/failure counts; do not silently classify old
failures as unrelated or claim a green suite from filtered tests.

Owner manual acceptance covers GUI scale/resize, port lines/selection, immediate
clear/defaults, tool movement/drop, two-client edits, remote range and alarm controls.
No automated GUI input, QuickPlay or owner configuration changes. Do not start a
duplicate client while the owner's normal client is running.

Units 1–2 own overlapping core contracts and should be sequenced. Parallel endpoint
or UI work is safe only after those contracts stabilize and file ownership is clear.

## Starting paths

Port paths are under `src/main/java/com/juicyslew/moonstation14/`:

- `ms14/doors/`, `ms14/doors/ui/AirlockWireMenuService.java`;
  `ms14/atmos/piping/alarm/{AirAlarmDeviceLinks,AirAlarmCandidates}.java` and `ui/`.
- `ms14/medical/{HealthAnalyzerServer,HealthAnalyzerStackData}.java`.
- `ms14/hands/network/BodyHandRequestService.java`,
  `ms14/hands/live/{BodyHeldItemMutation,LiveHandsCommitGateway,BodyHandItemTransfer,BodyItemTargetProbe,BodyLiquidInteraction}.java`.
- `ms14/ui/{client,model}/`, `ms14/hands/client/StoragePopupScreen.java`.
- `ms14/power/runtime/PowerRuntime.java` (lifecycle/index pattern, not data topology),
  `ms14/prototype/PrototypeRuntime.java`, item registrations and resolved profiles.

Upstream:

- `Content.Shared/DeviceLinking/`, `Content.Server/DeviceLinking/Systems/`.
- `Content.Shared/DeviceNetwork/Components/`, `Content.Server/DeviceNetwork/Systems/`.
- `Content.Client/NetworkConfigurator/`, shared configurator BUI messages.
- `Content.Server/Atmos/Monitor/Systems/{AirAlarmSystem,AtmosDeviceNetwork}.cs`.
- `Resources/Prototypes/Device/devicenet_frequencies.yml`, `DeviceLinking/` port YAML,
  `Entities/Objects/Tools/tools.yml`, `Objects/Devices/Electronics/signaller.yml`,
  wall switches, airlock, air-alarm, unary and sensor prototypes.

Relevant handoffs: `powered-ventilation-sprint`, `airlocks-sprint`,
`airlock-model-mini-sprint`, `machine-ui-visual-kit-sprint`, `apc-machine-ui-sprint`,
and `hands-inventory-program`. The `device-ui-platform-sprint` handoff is a deferred
proposal, **not** an existing implemented platform.

## Remaining implementation-start audits

Complete local component-field mappings and sprite/audio provenance before code.
Trace each supported vent/scrubber set-state field and reply variant, current
receiver-power coupling, and exact contextual-mode/input plumbing. These are
bounded first-unit audits, not unanswered product choices or permission to expand
into full atmosphere alarms, lamp internals or the entire logic ecosystem.
