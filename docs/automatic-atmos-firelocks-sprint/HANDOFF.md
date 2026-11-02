# Automatic atmosphere alarms and firelocks

## STATUS

Owner-approved on 2026-11-01 after manual testing and the final pry-interaction corrections. Technical readiness passed serialized run `run_b409974c0e33f842442ff6a0`, generation 1; its result was acknowledged. This records owner acceptance separately from that automated PASS.

Delivered artifact: `build/libs/moonstation14-0.0.1.jar`, 36,093,363 bytes, SHA-256 `e2ad4df77f8900bb8ab8a610daf84c932cdd7ec7217ab255a1a28e754880c027`. Changes remain uncommitted/unpushed; acceptance is not a merge.

## ARCHITECTURE

- Resolved typed prototypes supply monitor thresholds, alarm composition, ventilation presets, door defaults, power demand and networking. Existing ventilation devices provide sensing; the air alarm is an aggregator, not an invented local sensor.
- Real finite atmosphere samples drive source-keyed reports through the existing dimension-scoped device transport and explicit DeviceList membership. Danger selects WideFiltering; Normal/Warning select Filtering when automatic mode is enabled. Unknown samples or unloaded sources do not imply safe air.
- Standalone firelocks and independent covers installed on ordinary airlocks use the same `FirelockController`. Base and cover retain separate identities, power, endpoints and state. Either nonpassable door seals both doorway cells through the shared atmosphere topology owner.
- Alarm Danger and Space-connected explosive-depressurization closure are separate triggers. Local differential warnings/locking are another separate mechanism; uniform Danger does not itself lock a door.
- Fresh paid-power observations authorize network delivery, ordinary opening and alarm actuation. Graph invalidation can make those observations unknown between allocation solves. Loaded alarm/firelock receivers reconcile freshness before and after transport draining, retain cached hazards during uncertainty and synchronize through existing messages when fresh power returns. Confirmed receiver power-down still clears reports. Enqueue acceptance clears sync intent; this is not guaranteed-delivery/ACK machinery.
- Hand and tool prying use the shared timed-interaction runtime. `DoorPrySupport` owns shared duration arithmetic, admitted-hand snapshot proofs and stable-phase checks. Actor/body authority, hand revision, target identity/revision, supply and reach are revalidated; movement/damage interrupt progress.

## IMPORTANT FILES

- `ms14/atmos/monitor/`: threshold evaluation, sampling and `AtmosAlarmReceiverState` aggregation/sync intent.
- `ms14/atmos/piping/alarm/`: alarm receiver, explicit membership, preset control, snapshots and UI.
- `ms14/devicenet/DeviceNetworkRuntime.java` and `ms14/power/runtime/PowerRuntime.java`: transport lifecycle and loaded-only freshness reconciliation. Meter getters remain the authorization source.
- `ms14/doors/`: `FirelockController`, targets/installation, `FirelockPryService`, `AirlockPryService`, `DoorPrySupport`, interaction dispatch and shared topology/collision integration.
- `ms14/doors/prototype/`: firelock composition and shared `AirlockData.Door.pryTimeSeconds`; ordinary airlocks also resolve `PryUnpowered` data. Definitions live under `data/moonstation14/moonstation14/{firelock,airlock}`.
- `ms14/doors/client/`: approved R4 rendering, frame planning and authored selection/collision presentation.
- `ms14/atmos/piping/fixture/`: coordinate-free fixture commands, real finite-gas scenarios, telemetry and constant roof lighting. Shared `FixtureSiteContext` also serves disposal fixtures.
- Native fixtures: `AutomaticAtmosFirelockGameTests`, `FirelockDifferentialWarningGameTests`, `AirlockPryDomainGameTests`, and Space preflight fixtures. The airlock domain fixture does not prove authenticated hand/tool dispatch.
- Local indexes: `PARITY.md`, `ASSETS.md`, `FIXTURE.md`, `MANUAL.md`. Earlier subsystem indexes: airlocks, powered ventilation and device-signals networking handoffs.

## SS14 CORRESPONDENCE

Reference checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; full composition and consuming-system references are in `PARITY.md`.

- Closed-door pressure/temperature locks use strict side-to-side differences above 20 kPa / 330 K, including while unpowered. Ordinary opening is denied while environmentally locked. Closed warning overlays remain emissive without power; both channels use 16-tick then 8-tick frames.
- Door pry base time is 1.5 seconds. Duration divides by hand/tool speed, not multiplies. Firelock hand speed is 0.5; locked target multiplier is 1.5. Thus hand/crowbar times are 3/1.5 seconds unlocked and 4.5/2.25 seconds locked.
- Ordinary unpowered, unbolted airlocks use hand speed 0.1: 15 seconds by hand, 1.5 seconds with a default crowbar. Powered ordinary airlocks reject normal crowbar prying. Hands require confirmed unpowered supply on either door type; uncertainty denies admission/completion.
- Firelock strong-tool pry derives from resolved `Prying` composition and permits crowbar prying while powered. Successful firelock prying grants the existing two-second emergency-close cooldown.

## DECISIONS / WHY

Preserve one transport, power authority, finite-gas solver and timed-interaction owner. No room-wide synthetic alarm broadcast or parallel gas blocker was added. The attached-controller composition adapts SS14's independent entities to Minecraft's one-block-entity-per-cell constraint.

All 52 approved large-indicators R4 runtime resources remain byte-identical to the canonical manifest; attribution and provenance stay with the runtime assets. `ASSETS.md` identifies the delivery/import receipts. No artwork substitution was authorized by these runtime fixes.

## KNOWN GAPS

Ordinary bolted airlocks remain non-pryable locally; upstream instead applies a bolted pry multiplier. Firelock welding/bolting, access expansion and glass/directional variants remain unsupported. Native GPU/audio fidelity is not established by machine receipts alone; owner approval is not an assertion that every presentation combination was separately exercised.

The reported water-jump disconnect is separate and unfixed. Archived server logs contain `WORLD_STEP_REJECTED`; the movement adapter rejects water, but those logs do not establish the precise rejected condition. A later critical-state/medical-camera session is distinct. Start future triage at `GroundedHarnessWorldStep` and `LifecycleCharacterSessionControl`, with the input-authoritative movement handoff—not the firelock subsystem.

## VALIDATION

Final mandatory gate passed the 70-selector JVM pack, applicable UI/interaction/prediction readiness, five filtered native tags, packaging/provenance and independent reviews. Tags were `automatic_atmos_firelocks`, `firelock`, `space_firelock_preflight`, `firelock_fixture`, and `airlock_pry_domain`. This was not a full GameTest-suite run. Final additions had no optional local execution at the owner's request; authoritative execution came from the serialized gate.

Coverage includes real alarm-to-firelock behavior, differential warnings/locking, power restoration and a deterministic queued-Danger stale-power rejection followed by recovery without another hazard write. Source/prototype tests cover pry defaults, inheritance, overrides and resolved duration consumers. Gate artifacts are coordinator-managed; ignored preparation references are under `build/automatic-atmos-firelocks/pry-interaction-owner-ready-r9/`.

## NEXT WORK

No further sprint feature work is requested. Follow the integration workflow for any future merge/regression. Use `FIXTURE.md` for repeatable manual checks; build a new fixture to obtain roof lighting because reset does not rebuild old rooms.
