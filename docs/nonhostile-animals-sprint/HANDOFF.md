# Non-hostile animals

## STATUS

Owner closed the sprint on 23 October 2026 after the final AI/controlled-flight
speed correction. Remaining model work is deferred. This records owner closure,
not a conversion of diagnostic results into full-suite or workflow feature
acceptance. No commit, merge, push or runtime installation is implied.

The implementation includes resolved animal compositions, shared native hosts and
rendering, selective ordinary ghost roles and operator development offers,
physiology/abilities through existing services, and a fresh-world showcase of 37
animals including the existing mouse. Historical rejection/pending language in
supporting documents describes earlier candidates; this handoff indexes closure.

## ARCHITECTURE

- Character prototypes resolve inheritance and typed components before gameplay
  consumption. Capabilities are not granted by species-name checks or model type.
  Shared carrying, medical, hands, containment, equipment, speech and control
  services remain authoritative.
- `NpcBehaviorSystem` owns autonomous intent and phase progression. Ground and air
  planners supply routes only; `GroundedHarnessWorldStep` remains the shared
  collision-resolved motor. Air planning uses vanilla `PathFinder` and
  `FlyNodeEvaluator`, not vanilla navigation/move-control ticking.
- Resolved flight defaults: grounded factor **0.25**, airborne factor **0.80**,
  recovery **4 ticks**, ground phase **160 ticks**, air phase **400 ticks**. Phase
  durations vary 0.75–1.25. Autonomous ascent/descent is distinct from landing.
- Natural airborne AI cruise uses the converted resolved sprint policy, matching
  controlled sprint's speed target. Intermediate air nodes receive full steering;
  final destinations retain braking. Walk/sprint controls remain distinct. The
  shared speed factor is applied once on authority and prediction paths.
- Only committed portable-body throws use `launchPortableBodyThrow`. Eligible
  airborne flyers recover after reaction, preserving velocity. One motor owns
  each tick; a fixed-boundary release marker prevents double movement. Fall
  distance clears only after successful recovery. NPCs discard interrupted phase
  and idle-wait state, then resume air travel anchored at the catch position.
- Animal presentation uses shared adapters/snapshots, grounded flight gaits,
  corpse-contact alignment, and species-specific motion where implemented.
  Preserve the approved snake appearance, human movement and crowded-Kobold feel.

## IMPORTANT FILES

Java paths below are relative to
`src/main/java/com/juicyslew/moonstation14/`:

- `entities/AnimalMob.java`, `AnimalMobRegistration.java`;
  `ms14/animals/AnimalCatalog.java`.
- `ms14/character/components/FlightComponent.java` and resolved definitions in
  `src/main/resources/data/moonstation14/moonstation14/character/`.
- `ms14/mobs/NpcBehaviorSystem.java`, `NpcFlightController.java`,
  `AirNpcRoutePlanner.java`, `GroundNpcRoutePlanner.java` and route-budget owners.
- `ms14/animals/flight/{FlightSystem,FlightGroundSpeedPolicy}.java`;
  `ms14/player_body_control/character/GroundedHarnessWorldStep.java`;
  `ms14/player_body_control/client/GhostControlClient.java`.
- `ms14/player_body_control/movement/ExternalBodyFlight.java`;
  `ms14/hands/live/{BodyLooseThrow,PortableBodyCarrying}.java`.
- `ms14/mobs/client/{AnimalMobRenderer,AnimalModelAdapters,AnimalPoseBounds}.java`;
  native rigs/animations in `ms14/mob/animals/client/`.
- `ms14/animals/diagnostics/`: showcase, population capture and paired-pose tools.

Start with `PROTOTYPES.md` for upstream composition/source references and
`MANUAL.md` for setup. `OWNER-FEEDBACK.md` preserves historical findings;
`VALIDATION.md` and `locomotion-adaptation.md` provide supporting indexes.

## SS14 CORRESPONDENCE

Reference revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Full ancestry/default audits are indexed in `PROTOTYPES.md`. Preserve authored
species walk/sprint values and supported component inheritance. Upstream NPC
steering seeks current sprint speed; player movement retains walk/sprint choice.
SS14's 2D `FlyingMobBase`/`CanMoveInAir` does not define Minecraft altitude or
automatic throw cancellation. Natural 3D routes, landable phases, explicit air and
ground factors, and thrown-flyer recovery are deliberate Minecraft adaptations.

## DECISIONS / WHY

- Do not increase shared speed to hide AI throttling. The previous per-node
  `distance * 2 / speed` wish made short AI route legs crawl. Fixing that steering
  and its authored/converted speed-unit mismatch permits comparable cruising.
  **1.20 is historical; 0.80 is current.**
- Keep planning bounded: shared 12 calls/7,000 charged expansions per tick, local
  700-expansion limits, 12-XZ/8-Y spatial bounds and separate 40-block travel cap.
  `DEFERRED` is legitimate budget admission, not an unreachable-path result.
- Preserve normal damage, crowding, gamerules and unrelated ballistic launches.
- Bat and parrot are not portable. Bee/butterfly support the real pickup-and-throw
  check. Direct-launch bat fixtures do not establish player pickup eligibility.
- Retain selective upstream ordinary roles; other takeover offers remain
  operator-only `[DEV]` entries. Do not fabricate bat pickup or ordinary ghost roles.

## KNOWN GAPS

Further model/appearance work is deferred. Cow remains quiet without a verified
SS14 moo asset. Preserve attribution and the unknown-audio provenance ledger.
Automated evidence does not establish authenticated possessed throw recovery,
pixel-visible flight quality, every species' relative feel, or sustained-load/FPS
acceptance. The reported FPS collapse was qualified as possibly another program;
it is not an established mod regression. Equipment admission does not imply fitted
animal clothing meshes or replacement first-person arms. Unported major upstream
subsystems remain outside scope.

## VALIDATION

Latest coordinator diagnostic `run_481e11552a12d743130a8eeb`: PASS, acknowledged,
cleanup confirmed. **54/54 selected native tests** passed, including real normal-AI
short-waypoint cruise sampling against the resolved controlled-sprint cap.
Focused JVM evidence: **8 classes / 34 tests**, zero failures/errors/skips.
Interaction/prediction selectors passed; their JVM evidence is not authenticated
possessed-control proof. No full-suite pass is claimed.

Paired capture: 1,288 rows, 16 matched UUIDs, zero rejected rows; build reference
`build/reports/nonhostile-animals/population-profiles/mixed-0b8afaf7285f44adac810b99dd58f42b`.
Use coordinator run artifacts if disposable build output is unavailable. Client
`actualCount=0` is an intentional handshake field, not visible population count.
Renderer calls/pairing do not prove camera-pixel visibility or FPS performance.

## NEXT WORK

Treat model refinements as a separate task. For later regressions, reuse
`/ms14 animal_fixture setup`, `status`, and `clear`; compare AI and possessed flyers
of the same species. Use bee/butterfly, not bat, for pickup/throw checks. Preserve
the existing shared services, motor ownership and resolved-prototype contracts.
