# Extending device networking (developer guide)

This guide describes the landed extension seams. The sprint is owner-closed;
see `HANDOFF.md` for state and limitations. Core APIs live in
`src/main/java/com/juicyslew/moonstation14/ms14/devicenet/`.

## Add a block device

Add its family prototype data codec with `DeviceComponents.CODEC` under
`deviceComponents` (for example `VentilationDeviceData` or `AirlockData`).
`DeviceComposition` is built from the **resolved** prototype via
`DeviceComponents.resolve(PrototypeManager)`; preserve inheritance/defaults and
do not parse raw parent JSON independently. Reuse `ModDeviceNetworks`' registered
network, numeric frequency, and port catalogs; define a new catalog prototype
only when needed. Payloads implement typed `DevicePayload` and domain receivers
interpret them—do not add a central device-kind switch.

Implement `DeviceEndpoint` as an owner adapter. Register on load and call
`DeviceNetworkRuntime.forLevel(level).unloaded(identity)` on unload; reserve
`retired(identity)` for actual destruction/replacement. Override
`acceptsBlockTarget` for multi-block aliases. Device-list membership remains the
owner's mutable store, changed through `mutateDeviceListMembers`; do not put a
shared mutable list in prototype data. Configurator staging is a temporary
stack buffer, not a second canonical membership store. Keep actual UUID and
address ownership/policy with the endpoint owner and `DeviceAddressPolicy`.
Block default addresses live in dimension network `DeviceLinkSavedData`. Resolved
composition reads its retained value; transport performs collision-aware allocation
before endpoint publication, not during a composition read.

## Add a portable signaller variant

Use the `NETWORK_DEVICE_ITEM_PROFILE` data component (registered in
`ModDataComponents`) to select a prototype, and add an inherited JSON definition
under `data/moonstation14/moonstation14/network_device_item/`, for example with
`"parent": "standardwireless15"`. `ModNetworkDeviceItems.isSignaller(stack)` identifies
variants from resolved prototype composition; do not gate behavior on a
hard-coded item ID. Keep stack metadata and identity mutation in the ownership
gateway (`RemoteSignallerServer`), not in client/UI code. Override
`DeviceEndpoint.ownsAddressOutsideWorldSavedData()` for stack-owned endpoints:
prepare and persist the short address on the actual stack before registration,
using the shared transport allocator to check collisions. Never put portable
addresses in the block-default SavedData map or use hand tokens as identity.
Held and loose activation must reuse `RemoteSignallerServer`'s shared invocation/
cooldown operation after owner-specific admission. Do not copy packet creation or
introduce a second cooldown. Ground E capability is resolved from the client
catalog; server proof uses the server catalog. Early cooldown presses are handled
without emission or extending the deadline. Empty-hand world activation uses the
existing channel's exact-UUID `ACTIVATE_TARGET` action, not block-hit fallback.

## Checks and parity boundaries

Cover inherited prototype defaults and the live resolved-prototype consumer
(see `DeviceComponentsTest`, `ResolvedDeviceCatalogCompositionTest`, and
`NetworkDeviceItemPrototypeTest`). For runtime workflows also cover queued
delivery on the next update, power and gas authority paths using focused
GameTests (device network/workflow fixtures).

The SS14 reference is pinned at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`;
see `CORE-CONTRACT.md` and `PARITY.md` for verified behavior. Minecraft's
dimension-as-static-grid test and block address persistence layout are deliberate adapters,
not SS14 movable-grid or identity semantics.
