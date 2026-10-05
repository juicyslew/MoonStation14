# Powered ventilation and air alarms

## STATUS

**CLOSED — 2026-10-03, delivered ventilation slice.** Functional pipes, powered finite ventilation, alarm controls and the visible-gas fixture are delivered. The owner confirmed the CO₂ fixture works in-client. Closure does not certify full SS14 parity, Plasma appearance, restart or a green full suite; remaining work is explicitly deferred below. Changes are uncommitted; preserve concurrent edits.

## ARCHITECTURE

- Installation uses the exact active body hand, epoch/revision/token, replay sequence and body-eye hit (`BodyHandRequestService`), never carrier inventory. B is temporary held-use; generic click work is separate.
- `FunctionalExposedPipePlacement` installs independent primary/secondary/tertiary gas lanes, or a segment in precisely the clicked eligible steel wall. Wall hosts remain unchanged and sealed; `LevelChunkSealedWallHostMixin` invalidates replaced host generations. Cosmetic Blocks-tab pipe items are not functional segments.
- Gas inventory is finite, per-lane UUID-owned dimension SavedData. Loaded component discovery/equalization is bounded to 384 nodes. Pipe volume is 0.2 m³/node; room cells are locally 1 m³. `FiniteVentilationTransfer` conserves species/enthalpy between pipe inventories and proven finite atmosphere endpoints. A scrubber is not a deletion sink.
- `PowerRuntime` indexes machines, selects the nearest known APC cable within prototype reach, and meters actual delivery. Vents/scrubbers request 5 W and receive single-use gas-work authorization for completed paid intervals. Air alarms request 100 W and require full, fresh delivery for edits. Breaker, topology, identity, and replacement checks invalidate stale grants.
- Air alarms persist an explicit DeviceList of at most 32 dimension/position/kind/device-UUID links. Candidate discovery uses already-loaded indexed devices in a local 16-block sphere, visits at most 4,096 indexed entries, and never pairs automatically or loads chunks. Replaced devices cannot inherit old links. Offline links remain removable.
- Alarm opening requires strict body-eye authority and power. Persistent `HandActorAuthority.revalidateInMenu` permits only the exact slotless menu, retaining carrier emptiness, identities, epoch, reach/loaded LOS and power. Normal actions stay inventory-menu-only. Controls: enable, scrubber filter/siphon/gases, vent 0–4500 kPa and scrubber 0–0.2 m³/s. Direct-device UIs are removed; departmental access is absent.
- Vents/scrubbers are thin, noncolliding fixtures. Resolved `fixtureGeometry` drives 0.75×0.75×0.125 outlines and empty collision. Devices and alarms support all six mounting faces through inherited, live-consumed `allowVerticalMounts`; facing points outward and the pipe remains immediately behind, including concealed steel-wall lanes. UP/DOWN model rotations are 270°/90°. Vertical mounting is a Minecraft adaptation; see `ventilation-fixture-geometry.md`.
- Alarm UI reuses the machine toolkit. Devices → select → Configure opens a named device detail with Back to Devices; Controls and scrubber-only Filters are subordinate pages, not peers of the device list. `AirAlarmLayout` retains the 304×224 footprint, undersized notice, per-device drafts and correlated timeout/rejection handling.
- Exposed-pipe removal retires exact lane UUIDs. Player destruction first attempts a conserved spill into a proven finite room; otherwise nonempty gas persists in an inactive UUID archive. Live and retired records share the 65,536-record bound. New installation uses fresh vacuum ownership. Loaded-AIR orphan repair retires all three stale lanes at that position in one revision, fixing previously broken positions. Chunk unload and same-block updates do not retire ownership. See `pipe-removal-gas.md`.
- Successful concealed installation sends a lane-specific chat receipt after hand consumption; it does not reveal hidden pipes persistently or change the wall.
- Exposed arms now join validated same-lane concealed ports. An 18-bit server-derived BE update-tag mask carries only adjacent join geometry, not hidden wall layouts/gas authority. Refresh covers installation, identity changes, neighbor updates, load and host invalidation; client queries require a loaded Steel Wall. Primary blockstate and lane collision arms agree, including body-overlap preflight for concealed installation.
- `/ms14 ventilation_fixture build|initialize|status X Y Z`: two sealed 18-cell rooms, 11 Primary pipes, real HV→substation→APC power and a paired alarm. Source initialization uses 8 mol O₂, 30 mol N₂ and **2 mol Plasma/cell at 300 K**; the same plan-selected gas drives filtering/readouts. Proven finite cells and a persisted token prevent reseeding. Existing CO₂ fixtures are not converted; station generation is unchanged.

## IMPORTANT FILES

- `ms14/atmos/piping/prototype/`, `alarm/prototype/`: typed definitions and validation; registered in `ms14/prototype/PrototypeRuntime.java`.
- `data/moonstation14/moonstation14/ventilation_device/`, `air_alarm/`: inherited local resource prototypes consumed by live BEs and power runtime.
- `ms14/atmos/piping/alarm/`: block/BE, body-held placement, persistent links and bounded candidates.
- `ms14/atmos/piping/alarm/ui/`: alarm-only request/snapshot/menu service and client screen. Shared desired-state reducers remain under `piping/ui/`.
- `VentilationDeviceConfig`, `VentilationDeviceBlockEntity`, `FiniteVentilationTransfer`, `LoadedPipeGasOwner`, `PipeGasInventorySavedData`: configuration and finite gas ownership/flow.
- `ms14/power/runtime/{PowerRuntime,PowerLiveLoop,VentilationPaidInterval}` and `power/graph/PowerGraphService`: allocation, payment and receiver selection.
- `BodyBlockOutlineRenderer` and `BodyPlacementCollision`: body-camera selection feedback and pre-mutation collision checks, including newly introduced neighboring pipe arms.
- `ms14/atmos/piping/fixture/`: plan, operator commands, initialization guard and telemetry. Sprite attribution: repository-root `powered-ventilation-sprint/asset-attribution.md`.

All Java paths above are below `src/main/java/com/juicyslew/moonstation14/`; data paths are below `src/main/resources/`.

## SS14 CORRESPONDENCE

Pinned reference: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Unary devices inherit `[GasUnaryBase, AirSensorBase]` (APC/extension receivers, DeviceNetwork, no direct BUI). `AirAlarm → BaseWallmountMachine → BaseWallmountMetallic → BaseWallmount` supplies 100 W and noncolliding wallmounts. Alarm controls use addressed DeviceList messages; `WiredNetworkSystem` checks grid identity, not separate data cabling.

Local prototypes model only supported behavior. UUID pairing/16-block discovery replace upstream addressing and Network Configurator Set/Add setup. Empty upstream allow-lists cancel broadcasts; activation does not populate them. Frequency dispatch, access, sensor alarms, automatic presets, wires, and complete vent direction/pressure checks remain absent. SS14 unary devices are floor/subfloor tile-centered entities, not wallmounts. Their inherited `CollideOnAnchor.Enable=false` disables collision while anchored; the static noncolliding local wall fixture matches that state, not their placement. Source mapping is in `air-alarm-prototype-slice.md`, the geometry note and parity matrix.

## DECISIONS / WHY

- Prototype/composition review is repository-wide (`AGENTS.md`), not ventilation-only. JSON alone is insufficient: resolution/defaults and live consumers need tests.
- Three gas lanes are independent; color does not specify chemistry or connectivity.
- Sealed-wall eligibility is a Minecraft host policy, not SS14 `Airtight`.
- Endpoint enclosure has a separate 1,024-probe synchronous budget: seven probes/cell means a sealed 144-cell room fits. Ownership-search per-tick budget remains 384; do not confuse probes with supported cell count.
- Generated-station sealing/ventilation is authorized for **new worlds only**, not retrofit. Its current open entrance is not a finite-room endpoint. Station doors/airlocks and ventilation placement are not completed here.

## KNOWN GAPS

**Deferred, not completed by closure:** full alarm/sensor/automatic-mode parity, generated-station sealing/airlocks/initial gas, handheld scanner activation/reveal, multiplayer budgets, genuine restart and Plasma client-appearance validation. The owner confirmed the earlier fixture works, not every body-hand/menu/breaker scenario. Strict carrier-menu self-invalidation was fixed by exact-menu revalidation. Atmospherics defaults off; new pipes start vacuum. Manual APC recreation remains unresolved; use the fixture or station supply. `reported-cable-removal.md` records covered-wire loss without diagnosis. Archived gas has no reclaim workflow. Join recovery remains separately owned.

## VALIDATION

Latest build: `build/ventilation-visible-gas-final-build-20261003.log` passed with GameTest compilation and **1,872 JUnit tests**, zero failures/skips. Coverage includes authority, gas archives, model resources, fixture geometry/persistence/telemetry and positive Plasma overlay eligibility. Scoped source reviews passed.

`build/ventilation-visible-gas-fixture-20261003.log`: **415 GameTests completed, two failed** (cable-face validation, paid disabled-device fixture). Normal fixture initialization, paid capture, release and conservation of **36 mol Plasma** passed. Earlier `ventilation-join-demo-fixture-flow-20261003.log` proves exposed/concealed equalization and six-direction arm/tag rejection. Remote fixture tests require forced tickets; production commands never force chunks. No green full-suite or Plasma client-appearance claim. Disposable runs were removed; logs remain.

Earlier authority: `build/air-alarm-authority-fix-20261003.log`. Six-face mounting/removal/orphan repair: `build/ventilation-feedback-fixture-mounted-20261003.log`. Trusted fixture construction/CAS and FakePlayer destruction do not prove connected body-hand transport or human UI/render acceptance.

## NEXT WORK

No active work remains in this sprint. Future tasks should start with `ventilation-demonstration-fixture.md` and the parity matrix: confirm Plasma appearance/restart, triage the two failing fixtures and reported wire loss, then scope alarm parity, new-world station sealing or scanner/budget work separately. Preserve prototype audits and existing worlds.
