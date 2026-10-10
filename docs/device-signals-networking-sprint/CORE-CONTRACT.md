# Device network core contract (unit 1)

Package: `com.juicyslew.moonstation14.ms14.devicenet`.

* `DeviceIdentity` is a stable UUID value. Block adapters must use the existing
  controller UUID; portable adapters persist their own UUID in stack data. It is
  never an actor, hand, or session token.
* `DeviceEndpoint` is the transport boundary: identity, current dimension and
  position, resolved `DeviceComposition`, independent `available()` and
  `powered()` eligibility, and typed receive callback. `requiresPower` is
  enforced centrally; unary vents can receive unpowered, alarms opt in.
  Adapters register only while loaded. Unregistering a
  loaded endpoint makes it offline; `retire` removes its graph identity.
* `DeviceComposition` is a reusable resolved component bundle (source ports,
  sink ports, optional network interface and optional DeviceList). Family
  prototypes compose this record; do not create a competing endpoint JSON
  definition for an entity whose family prototype already resolves its data.
* `DeviceLinkGraph` stores directed `(source identity, source port, sink
  identity, sink port)` edges and remembered High/Low state. Momentary is never
  remembered; clearing memory is distinct from Low. Persistence belongs in
  per-dimension SavedData; unloaded identities remain linked.
* `DeviceNetworkTransport` queues typed payload envelopes. Enqueue never invokes
  receivers inline; a later server update drains the queue. DeviceLink packets
  override the packet route to Wireless network ID 2 and the sink RX frequency,
  while retaining the sender's configured TX network/frequency/address snapshot
  for stale-enqueue rejection. The sender address must match at enqueue; an
  explicit positive frequency override remains valid even without a default TX
  frequency. Direct packets use their declared route. Each update drains a
  snapshot, so callback-enqueued packets wait until the next update.
  Recipients first require packet network/frequency membership. Broadcast
  reaches all listeners; addressed packets reach the destination plus ReceiveAll
  listeners. Wired and wireless eligibility/range are separate from packet
  selection; endpoint `beforePacketReceived` supplies an optional pre-delivery
  gate.
  Sender exclusion, target incoming lists, and sender broadcast-recipient-list
  hooks remain distinct; list membership is stable identity, not mutable address.
* Numeric frequencies resolve through registered frequency prototypes. Network
  interfaces retain both wired and wireless capability, independent TX/RX
  frequency, and address/auto-connect/savable/custom/broadcast-attempt settings.
  Wired is an explicit Minecraft adapter: same dimension is the static-grid
  predicate (not cable traversal or `LoadedPowerGraph`), with 3D range checks
  where required. Wireless range is evaluated from the transmitting endpoint.
* Loaded endpoint presence and network membership are separate. Registration
  binds an address and auto-connects only when configured; manual
  `transport.connect(identity)` / `disconnect(identity)` control membership.
  Packets enqueue/deliver only through connected network members. Default
  addresses are the resolved prefix plus random uppercase `XXXX-XXXX`, matching
  SS14's 32-bit address format. UUIDs remain internal identities. Default addresses
  are retained while unused; automatic collisions regenerate against the same
  network's existing membership map. Custom collisions fail connection unchanged.
  Block defaults belong to per-dimension `DeviceLinkSavedData`; portable defaults
  belong to the actual signaller stack. Composition reads never generate addresses.
  A changed address/network invalidates current membership and queued packets;
  call `connect(identity)` to rebind the endpoint to its current address.
* Configurator request IDs are a monotonic per-session operation sequence shared
  by endpoint-selection publications and UI commands. Initial selection publishes
  ID 0; clients issue commands above the latest accepted publication. Snapshot
  consumers reject duplicate/stale IDs without replacing the accepted command
  route. List-session target/staging changes likewise advance publication order.
* DeviceList defaults: capacity 32, allow-list enabled, incoming filtering
  disabled. Frequency defaults are BasicDevice/Wireless2=1280 and
  AtmosMonitor/AtmosDevices4=1621. Link setup default range is 30; it is not an
  invocation-time range. Wireless defaults: auto-connect and savable address
  true, custom address and broadcast-attempt false.
* Payloads are extensible typed records implementing `DevicePayload` (initially device signal; Atmos payloads
  can extend the family) rather than arbitrary dictionaries or executable
  scripts. Domain adapters own interpretation and never switch on device kind
  in transport.

Pinned SS14 source verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
SS14 DeviceLinking and DeviceNetwork are separate; when both link endpoints
have DeviceNetwork, link invocations use addressed wireless packets, otherwise
the direct signal callback is used. The dimension-as-static-grid rule is a
documented Minecraft departure because this runtime has no movable-grid ID.
More precise routing parity was verified in `Content.Server/DeviceLinking/Systems/DeviceLinkSystem.cs`
(`InvokeDirect`, `OnNewLink`) and `Content.Server/DeviceNetwork/Systems/DeviceNetworkSystem.cs`
(`QueuePacket`, `SendPacket`): DeviceLink forces Wireless network 2 and uses
the sink receive frequency; explicit queue overrides do not replace the sender
configuration snapshot. Recipients must match the packet network and frequency;
broadcast reaches ordinary listeners, while addressed sends also reach
ReceiveAll listeners. Wired and wireless components are independent receiver-side
veto hooks: wired rejects a dimension/grid mismatch; wireless checks same-map
and sender range only when both endpoints have wireless. Failed wireless checks
cannot be bypassed through wired, and absent components add no veto.

Prototype APIs: `ModDeviceNetworks.FREQUENCY_TYPE`, `NETWORK_TYPE`, and
`PORT_TYPE`; frequency values are numeric (`DeviceFrequency.frequency()`).
Port definitions include Open, Close, Toggle, AutoClose (localized UI label
Hold), DoorBolt, DoorStatus, Pressed, On, Off and Status, including pinned
default-link metadata.
Runtime APIs: `DeviceComposition`, `DeviceEndpoint`, `DeviceNetworkTransport`,
`DeviceLinkGraph`, `DeviceLinkSavedData`, and `DeviceLinkService`. Endpoint
owners obtain the runtime with `DeviceNetworkRuntime.forLevel(serverLevel)`;
`runtime.transport().endpoints()` and `lookupAt(BlockPos)` expose loaded-only
snapshots, while `lookup(DeviceIdentity)` resolves a selected identity.
register on load and unregister on chunk unload; retirement is only for actual
destruction/replacement. Source link range and target invocation limit resolve
from endpoint `DeviceComposition.SourceConfig` / `SinkConfig` (defaults 30/10).
Overload calls `DeviceEndpoint.onOverload()` and unlinks all incoming edges to
that sink. `DeviceNetworkLifecycle` drains every instantiated
dimension and decays invocation counters once at server post-tick, and clears
runtimes on unload/stop. `DeviceLinkSavedData.forMutation` returns per-dimension persistent
graph state on the server thread. Composition uses immutable `DeviceList.Config`;
adapters own a stable mutable DeviceList instance and override `deviceList()`.
Every persisted graph mutation triggers the SavedData dirty callback directly,
including mutations through the exposed graph reference; reads and no-op writes
do not mark the save dirty.

## Configurator observation versus input

Configurator snapshots may arrive before the corresponding hand/block action
acknowledgement. Client reception uses `BodyHandClientPolicy.confirmedSnapshot()`
through `networkConfiguratorProof()`, not `inventoryView()` input eligibility.
Pending requests keep normal action input barred but do not revoke a confirmed
read-only observation; suspended/lost authority still revokes it. Every editor
command is independently revalidated by the server. Link/List window visibility
also counts toward the shared pointer workspace's automatic lifetime.

## In-hand activation routing

Z (or its rebound equivalent) sends `USE_IN_HAND` from gameplay and the retained
workspace through the same guarded hand request gateway; medical self-use/mode
gestures retain precedence. Server configurator/signaller dispatch is independent
of the medical catalog: a required medical lookup must not gate nonmedical tools.
Signallers reuse `RemoteSignallerServer.activate` with active-hand token/revision
and body authority checks. E remains world interaction, not activation of the
held remote. The owner-requested ground-signaller world-activation adaptation
uses an exact nominated target and the existing hand-action channel, sharing
signal invocation, stack-owned identity/address and cooldown with held use.
An admitted cooldown press is silently handled without another signal or a
deadline extension; invalid authority, target or hand proof still rejects use.
