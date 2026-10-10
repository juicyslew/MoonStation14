# Device signals and networking

## STATUS

**Closed by owner acceptance on October 9, 2026.** Delivered reusable device
linking/networking, configurator, standard/advanced signallers, wall controls,
airlock signals and queued supported atmos controls, including owner usability
corrections. Acceptance is not a claim of universal connected-client test coverage
or a green full GameTest suite. Changes remain uncommitted; no staging, commit,
merge or push is implied. ChemMaster and airlock visual redesign were separate work.

## ARCHITECTURE

Family prototypes compose typed `DeviceComponents`; gameplay consumes resolved
inheritance through `DeviceComposition`. Shared network/frequency/port catalogs
provide routing values and default-link metadata. DeviceLinking, DeviceNetwork,
DeviceList and electrical Wires remain distinct responsibilities.

`DeviceEndpoint` adapts domain owners to a dimension-scoped `DeviceNetworkRuntime`.
Transport tracks loaded endpoints and connected membership separately, queues
typed packets, and drains a snapshot at a later server update. Callback-enqueued
packets wait for another update. Domain adapters interpret payloads; transport
does not switch on device kind. Power availability and paid finite-gas work remain
separate gates. No redstone bridge or competing power/network topology was added.

Internal identities are stable UUIDs. Player-facing addresses are separate random
uppercase `XXXX-XXXX` values with resolved prefixes. Block defaults belong to
existing per-dimension `DeviceLinkSavedData`; portable defaults belong exclusively
to actual signaller-stack components. `ownsAddressOutsideWorldSavedData()` prevents
portable addresses entering the block store. Transport's existing membership map
is the collision authority. Unused defaults persist; automatic collisions retry;
custom duplicates refuse connection. Composition reads never allocate addresses.
Live configuration edits invalidate stale membership/queued packets.

SavedData also owns directed port links and remembered High/Low state. Unload
retains identity, address and links; retirement removes destroyed block ownership.
DeviceList membership stays in each endpoint's canonical owner store; configurator
staging is temporary tool-stack data, not another membership authority.

Configurator panes live in the retained hands workspace. Server sessions revalidate
commands. `ConfiguratorOperationSequence` orders both selection publications and
commands. Read-only observations use confirmed snapshots independently of pending
input barriers; suspension/authority loss still revokes proof. Fresh tool mutation
refreshes committed hand proof before selection publication.

## IMPORTANT FILES

- `src/main/java/com/juicyslew/moonstation14/ms14/devicenet/`: catalogs,
  composition, address policy, endpoint/transport, graph/SavedData/link service,
  lifecycle and wall controls.
- `devicenet/item/`: `RemoteSignallerServer`, stack data, configurator server/list
  server, requests/snapshots; `item/client/` contains retained Link/List windows,
  renderers and shared fitted layout.
- `hands/client/BodyHandClient`, `BodyInputGesturePolicy`, `BodyHandClientPolicy`,
  `StoragePopupScreen`; `hands/network/BodyHandActionRequest`,
  `BodyHandRequestService`, `BodyWorldItemActions`; `hands/live/BodyBlockItemPlacement`.
- Owner adapters: `doors/AirlockBlockEntity`, ventilation and air-alarm block entities.
- Prototype JSON: `data/moonstation14/moonstation14/{network_device_item,
  signal_control,airlock,air_alarm,ventilation_device}` and shared catalogs.
- `CORE-CONTRACT.md`, `CORE-COMPONENTS.md`, `EXTENDING.md`: API/ownership seams.
  `PARITY.md`: audited composition and departures. `VALIDATION.md`: historical
  artifacts/failures and exact current evidence. `MANUAL.md`: concise retest steps.
- Tests: `DeviceAddressPolicyTest`, `DeviceLinkSavedDataTest`,
  `NetworkConfiguratorLinkWindowTest`, `RemoteSignallerHandUseRoutingTest`,
  `DeviceOwnerVisualResourceTest`; native `LooseConfiguratorTargetGameTests` and
  `LooseRemoteSharedActivationGameTests` plus device workflow fixtures.

## SS14 CORRESPONDENCE

Reference checkout: `C:\Users\William\Documents\SS14-dev\space-station-14`,
verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Parent chains, inherited
components/defaults and consuming systems are indexed in `PARITY.md`.

Standard/advanced signallers emit `Pressed`, with wireless ranges 15/50 and a
one-second UseDelay. The generic upstream interaction wrapper gates held use
before the event; early repeats are silently handled, without emission or extending
the deadline. Both local held/loose adapters share that activation operation.
Ground **E** activation is an explicit Minecraft adaptation: upstream signaller
does not declare `TriggerOnActivate`. Held activation is **Z**.

Switch On/Off are momentary; Status remembers High when on and Low when off.
Airlock Open/Close/Toggle ignore Low; simple symmetric control uses On→Open and
Off→Close. Defaults matches authored source-port defaults, e.g. Pressed→Toggle,
not every possible port. Clear removes pair links; Clear and Defaults also clear
pending highlights. Clicking a highlighted port deselects it.

Link packets force Wireless network 2 and sink RX frequency while retaining sender
configuration metadata; remote TX=0 is valid through that explicit override.
BasicDevice RX is 1280; AtmosMonitor is 1621. Setup range is 30, distinct from
transmission range. Prefixes are AIR-/VNT-/SCR-; controls/airlocks/remotes have none.

## DECISIONS / WHY

- One dimension adapts one static transport grid; no movable-grid/cable-derived
  communications topology. English prefix literals adapt upstream localized keys.
- Ground activation uses exact nominated-item ray admission through the existing
  hand-action channel (`ACTIVATE_TARGET` is token-free for empty hands). Occupied
  target use preserves configurator priority. Never substitute a nearby item or
  depend on a block behind it.
- Nonmedical activation must not depend on required medical catalog lookups.
  Workspace Z shares the ordinary guarded hand-action gateway.
- Each configurator pane fits at most one-sixth of viewport area, with effective
  chat-scale body text, shared render/input mapping and direct clipped connection
  lines. Configurator artwork uses 32 extracted frames at two ticks each; wall
  controls use extracted directional frames and alpha-fitted front-face UVs.
- Imported SS14 sprites/sounds retain attribution; unresolved defibrillator-zap
  provenance remains in `docs/audio-unknown-provenance.md`.

## KNOWN GAPS

No timer/logic ecosystem, automatic atmos alarms, lighting internals, access system
or new airlock art. Full native suite remains non-green; failure overlap does not
prove unrelated/preexisting status. Authenticated E/Z dispatch, multiplayer and
restart scenarios are not exhaustively automated. Native portable fixtures test
real preparation/probing and trusted shared-core emission, not fabricated players.

Advanced range exceeds the owner's observed door visibility. No render distance
was changed; the effective cutoff is unmeasured. Airlock rendering uses Minecraft
block-entity eligibility, distinct from server-loaded endpoint availability.

## VALIDATION

Final `test compileGametestJava assemble`: **2,976 JUnit tests passed, zero failures**.
Filtered native tags `device-network-owner,device-network`: **9/725 selected,
9 passed, zero failed**. New fixture verifies cooldown ticks 0/1/19/20, queued TX=0
override routing, and standard/advanced delivery differences at 20 blocks.
Full native: **725/725 selected, 685 passed, 40 failed**; seven of nine focused
fixtures passed there, while powered-airlock/alarm workflows failed power-readiness
prerequisites. Retain full failure world/log under
`build/gametest-device-ground-activation-full-fixture` and corresponding build logs.
Successful disposable fixtures were removed. No GUI automation or owner config changes.

Current jar: `build/libs/moonstation14-0.0.1.jar`, 7,559,525 bytes; SHA-256
`AE1157774B9691AA4B19E49B22C26EAF81D2AF68AEAE944676300B9F4CEFF008`.

## NEXT WORK

Use this handoff and extension guide before adding endpoints. Investigate full-suite
power/topology prerequisites separately without weakening payment gates. Assess
distant-client visibility/culling before changing render distance. Preserve portable
stack ownership, shared activation, resolved prototypes and observation/input separation.
