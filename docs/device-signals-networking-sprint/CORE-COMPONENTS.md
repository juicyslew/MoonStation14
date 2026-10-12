# Reusable device composition codec

Entity family codecs embed `DeviceComponents.CODEC` under `deviceComponents`.
PrototypeManager resolves ordinary entity-family inheritance/merging before this
codec is applied; do not resolve raw parent JSON separately. Example:

```json
"deviceComponents": {
  "deviceNetwork": { "network": "moonstation14:atmos_devices",
    "transmitFrequency": "moonstation14:atmos_monitor",
    "receiveFrequency": "moonstation14:atmos_monitor", "addressPrefix": "VNT-",
    "autoConnect": true, "savableAddress": true, "broadcastHook": true },
  "deviceConnection": { "wired": true, "wireless": true, "wirelessRange": 200 },
  "deviceLinkSource": { "ports": ["moonstation14:pressed"] },
  "deviceLinkSink": { "ports": ["moonstation14:toggle"] },
  "deviceList": { "capacity": 32, "allowList": true, "filterIncoming": false }
}
```

Network fields default transmit/receive true, autoConnect/savableAddress true,
other flags false and strings empty; frequencies are optional, and absent frequencies resolve to numeric
zero (no configured frequency). `deviceConnection` is independent of the network
interface and alone declares wired/wireless/range. Source range defaults to 30
and lastSignals defaults empty; lastSignals maps source port prototype IDs to
High/Low only, and is applied once by an endpoint owner without replacing persisted
graph memory. Sink limit
defaults to 10; list defaults to 32/allow-list/incoming-filter-off. Port references
must resolve in the registered port catalog and have the appropriate SOURCE or
SINK direction. Network/frequency references likewise resolve only through the
existing ModDeviceNetworks catalogs; unknown IDs fail resolution. Port definitions
retain their default-link metadata in that shared catalog. No live DeviceList is
stored here: `DeviceComposition.deviceList()` is immutable Config and each endpoint
owns its own mutable membership instance.

Resolve with `components.resolve(prototypeManager)`. The returned runtime record
contains the port definitions, numeric network/frequency values, independent
connection capabilities, source range/initial signals, sink limit and list config.
The resolved network address starts blank; the transport allocates and persists the
endpoint's short address before publication, then binds it with
`composition.withAddress(address)`. The prefix is a generation hint, never the
runtime address. Local prefixes are literal ASCII strings (`AIR-`, `VNT-`, `SCR-`);
this adapts SS14's localized prefix presentation to the backend's address format.
This Minecraft adapter intentionally maps wired connectivity separately from
Wireless network membership; wired transport's same-dimension grid and 3D ranges
are documented departures in `PARITY.md`/`CORE-CONTRACT.md`.
