# Airlock model mini-sprint handoff

## STATUS

Closed by owner acceptance on 2026-10-08. The owner accepted the work and has
chosen a different airlock design for a forthcoming implementation. That redesign
has **not** begun. Preserve the shared contact and posture corrections rather
than treating this sprint as disposable model content.

Current jar: `build/libs/moonstation14-0.0.1.jar`, 7,224,139 bytes;
SHA-256 `106A3EEE97C004B774B38C4F3E84FD88943F265F689EC3BD4C646BEAC0392600`.
Client and server both require this build. No assistant staging, commit or push
accompanied closure. Owner acceptance is not an automated connected-client test.

## ARCHITECTURE

- The lower/root controller renders exported Minecraft Java component models,
  not `.bbmodel` files or the old procedural mesh. Minecraft bakes registered
  component/state models; decoded quad geometry is cached with reload-safe model
  identity. The item references the exported closed model and display transforms.
- Export coordinates face -Z; a local 180-degree Y adaptation preserves existing
  facing semantics. Leaves travel 6.5/16 blocks using shared openness and resolved
  `AirlockData` phase durations. Maintenance pieces follow the right leaf.
- All translated component quads are clipped to `[0,1] × [0,2] × [0,1]` before
  facing transforms, interpolating UVs without stretching or synthetic caps.
  Boundary intersections are deduplicated and degenerate polygons discarded.
  Minecraft 1.21.1 sprite `getU/V` take **normalized 0–1 coordinates**, not 0–16.
- `AirlockGeometry` loads immutable boxes from the same packaged component JSON
  for matching clipped examination hits/outlines. Open apertures remain selectable
  through their centers. Mechanical collision remains the existing full block
  volume, not the thinner exported visual mesh.
- Stationary contact is distinct from movement intent and crush eligibility.
  Scheduled transitions to Closed check exact doorway contacts, including side
  tangency with a small numerical tolerance, and quietly attempt normal bump
  opening for resolved `DoorBumpOpener` bodies. This is not proximity sensing or a
  global per-tick closed-door scan. Loaded medical corpses use the existing body
  validity predicate; contained/incorporeal/spectator bodies are excluded.
- Solidification attempts one shared electrical BUMP contact per contacting body
  after crush effects. Closed-state recovery does not shock again. Reopening and
  another solidification creates a new episode. Safe obstruction aborts do not
  create that electrical contact. Active crush-release waits bypass ordinary
  closed recovery so their two-second deadline is not shortened.
- Safety obstructs **closing**, not opening. Existing power/bolt rules remain
  authoritative; current doors are all-access. Dead bodies remain relevant to
  physical obstruction/crush queries, with effects delegated to shared systems.
- Knockdown now drives authoritative posture through resolved
  `knockdown_posture` status behavior and `DefaultStatusEffectLifecycle`.
  `PostureSystem` supplies physical dimensions and the existing synchronized
  renderer state. Already-down orientation/manual stand policy are preserved;
  active knockdown blocks get-up. Recovery retains timing, medical, consciousness
  and headroom checks. Slip no longer duplicates the posture projection.
- The redundant `airlock_screwdriver` item/catalog aliases are removed. Ordinary
  `screwdriver` serves airlock quality-based and disposal consumers, with shared
  loose-item metadata. Its retained texture filename does not denote another item.

## IMPORTANT FILES

Java paths below are under `src/main/java/com/juicyslew/moonstation14/`:

- `MoonStation14Client.java`: additional-model registration.
- `ms14/doors/client/{AirlockBlockEntityRenderer,AirlockFramePlanner,AirlockQuadClipper}.java`:
  baked rendering, presentation policy and clipping.
- `ms14/doors/{AirlockGeometry,AirlockBumpSystem,AirlockBlockEntity,AirlockState}.java`:
  shared geometry, contact/opening, solidification and controller state.
- `ms14/examine/ExamineWorldSelector.java`: authored piecewise selection.
- `ms14/status_effect/DefaultStatusEffectLifecycle.java`,
  `component/codec/json/StatusEffectBehavior.java`, `ms14/standing/PostureSystem.java`,
  `ms14/character/CharacterControlSystem.java`, `ms14/slip/SlipSystem.java`:
  common knockdown-to-posture projection.
- `item/{ModItems,ModCreativeModeTabs}.java`, `ms14/tools/ModToolProfiles.java`:
  ordinary screwdriver registration/quality reuse.
- Resources: `assets/moonstation14/{models/block, textures/block}/airlock/default/`,
  `models/item/airlock.json`, and `data/moonstation14/moonstation14/status_effect/knockdown.json`.
- `IMPLEMENTATION.md`: detailed decisions, artifact history and validation logs.
  `MANUAL.md`: concise regression checks. `../KNOCKDOWN.md`: shared posture behavior.
  `../airlocks-sprint/HANDOFF.md`: controller/wiring baseline and subsystem index.

## SS14 CORRESPONDENCE

Verified reference revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`SharedDoorSystem.CheckDoorBump`/`Update` check contacting tagged entities after
scheduled closing; the inherited human tag persists through death. Collider
re-enabling can generate stationary `StartCollideEvent` electrocution independently
of opening. `Crush` has no alive-only filter. `SharedStunSystem` initializes
knockdown through `StandingStateSystem.Down`, rather than just displaying a marker.

Local solidification contact executes synchronously instead of relying on a later
physics event. The retained local crush controller reaches Closed before its
separate release deadline; upstream redirects its crushing state to opening
before ordinary Closed recovery. The skip guard preserves the accepted local wait.
Door motion/3D geometry remain Minecraft adaptations, not SS14 sprite parity.

## DECISIONS / WHY

Reuse exported Java assets, the existing bake pipeline, controller authority,
body validity and shared posture infrastructure. Do not introduce a Blockbench
interpreter, parallel collision scanner, airlock-only pose flag or new packet.
Timer-wire behavior is unchanged: an already-armed countdown is retained;
subsequent arming uses the modifier. Cut gives 0.05 seconds after full Open.

The 44 export resources retain attribution in
`../airlocks-sprint/NEW-MODEL-ASSETS.md` and packaged model-directory
`ATTRIBUTION.md` (CC-BY-SA-3.0). External authoring/export source is indexed there.
Unsupported welded/emergency/effect assets do not imply implemented gameplay.

## KNOWN GAPS

Departmental access, welding and other deferred controller features remain absent.
Dead-body damage/paralysis follow shared effect policy without special overrides.
Exact upstream physics-step timing was not reproduced. Native fixtures do not
establish authenticated-player ghost-handoff or actual client visual behavior;
source tracing confirms retained corpse composition, and closure is owner acceptance.
No client was launched or owner configuration changed during these follow-up fixes.

## VALIDATION

Latest `test compileGameTestJava` and `assemble` passed: **2,890 JUnit tests**, zero
failures/errors/skips. Final filtered native knockdown/contact/crush tags selected
**11/716**, completed/passed **11/11**. Includes player-harness physical dimensions,
posture recovery, stationary Critical/dead contacts and electrical contact.
Independent source reviews passed.

A broader posture-tag run selected **36/716**, passed **35**, failed **1**:
`jumpingTautPullerFallsAndLandsAfterInputStops`, also failing before this correction;
cause uninvestigated. Earlier full baseline: **696/708 passed, 12 failed**. Filtered
passes do not make that suite green. Logs are in `build/validation-logs/`; useful
full-run failures remain in `build/gametest-airlock-model-fixture`. Final passing
disposable fixtures were removed after retaining logs.

## NEXT WORK

Start the new airlock design from this handoff and its delivery/binding contract.
Reassess exported geometry, component hierarchy, UV layout, orientation and travel;
reuse clipping, shared geometry, model registration, contact recovery and posture
services. Do not replace the model while accidentally reverting these fixes.
