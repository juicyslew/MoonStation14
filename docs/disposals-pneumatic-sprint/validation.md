# Validation status

Not yet accepted as a completed sprint. Connected checks are in
`client-checks.md`; deliberate scope limits are in `architecture.md`.

**October 4 replacement:** own-block, six-direction implementation and focused
automated validation are complete. Connected acceptance remains outstanding;
the full suite also has non-disposal failures reported below.

The construction-family follow-up compiled main/test/GameTest sources and
passed fresh unfiltered **2,153 JUnit tests** (no failures/errors/skips),
`disposals-family-junit-03.log`. The first full family regression run completed
**505/509**, with **30/30 disposal tests** passing. Failures were negative-stack
drying/cleanup and three paid-ventilation fixtures without known meters:
`disposals-family-gametest-01.log`. The source-frozen retry completed
**502/509**, again **30/30 disposal tests** passing:
`disposals-family-gametest-02.log`. Seven non-disposal failures were exterior
atmosphere preconditions, alert expiry, vent-meter breaker/replacement,
lamp-extension reach, stone-host cable replacement (both reach diagnostics
threw substring-range errors), post-lease vanilla movement, and paid ventilation
conservation. Causes remain unresolved; none is waived as proof of a green full
suite. `disposals-family-final-build.log` records a successful actual build;
`jar` executed, while test/compilation were up-to-date against the fresh green
results. Artifact: `build/libs/moonstation14-0.0.1.jar` (5,188,555 bytes).

G/H/R controls, non-container palette, material HUD, neighbour-aware rotation,
and orientation-only screwdriver workflow passed independent source reviews.
New tests cover the actual runtime state reducer, payloads, material/count/time
resolution, semantic orientation candidates, readiness and rotation confirmation
policies. Authenticated key/input and timed-placement/rotation still require a
connected client; no FakePlayer authority bypass was introduced merely to obtain
a headless test. The full regression run includes two unrelated test-fixture
worktree edits excluded from the planned disposal commit; production/JUnit
sources are the staged feature sources.

## Latest recorded evidence

- `disposals-spatial-junit-06.log`: fresh unfiltered **2,132 passed**, no
  failures/errors/skips; main, test and GameTest compilation succeeded.
- `disposals-spatial-gametest-02.log`: **508/509 passed**, including **30/30
  disposal tests**. Sole failure was a powered scrubber's unknown paid meter.
- `disposals-spatial-gametest-03.log`: source-frozen **505/509 passed**, again
  **30/30 disposal tests**. The scrubber passed; remaining failures were exterior
  atmosphere override preconditions, alert expiry, vanilla movement after lease
  close, and local extension receiver/cable-cut reach. These are non-disposal
  selectors; their causes are not established or waived by this sprint.
- `disposals-spatial-final-build.log`: actual `jar` and `build` succeeded;
  `test`/`check` and compilation were up-to-date against the fresh green results.
  Artifact: `build/libs/moonstation14-0.0.1.jar`. This does not clear the separate
  full-server failures.

Coverage includes live exposed block ownership, floor-host rejection, sealed
wall admission/replacement/same-state invalidation, inherited/custom geometry,
six-axis frames and routing, natural vertical/bend traversal, DOWN unit inlets,
broken-trunk return, safe ejection, exact body/camera pin position and persisted
holder identity mismatch. Source reviews passed placement/preview authority,
resolved geometry consumers, renderer/texture resources and host/ownership fixes.
The pipe block is intentionally itemless; construction does not produce a
portable BlockItem. The earlier floor-overlay suite is historical only.

Earlier failure logs remain under `build/disposals-*.log`. They exposed and
helped repair fixture bounds, NBT encoding, readiness scheduling and incorrect
gas accounting. The gas test now sums distinct withdrawal and release cells
plus holder inventory; it does not assume a cell endpoint is a whole-room sum.
Natural-tick traversal exercises resolved ports, actual bends and ejection.

## Outstanding acceptance

Connected-client checks remain unperformed. Independent source review passed
destruction gas ownership handoff and passive held-fuse menu authority, but
live spawn-rejected destruction/fallback and bound-owner held-explosion
collection still lack dedicated integration tests. SavedData round-trip and
expiry ordering tests are narrower evidence, not substitutes for those paths.
Normal unload/reload camera and original-flag behavior still needs connected
verification. Historical automated results are not replacement acceptance;
the current spatial regression gate and remaining connected evidence are still
required before the sprint can be accepted as complete.

## Execution invariants

The shared `empty.nbt` is only **3×1×1**. Disposal layouts extending to local
X/Z 6 cannot use that footprint: boards may overlap and preparation does not
declare their real loaded region. New disposal tests now use `disposal_clear`
(32×8×32). The real power fixture uses `disposal_power_clear` (48×8×48), with
its layout offset +16 X/Z so the receiver search stays inside its bounds.
These are fixture bounds, not permission to force chunks in gameplay.

Only one designated Gradle/server validator runs at a time. The configured
GameTestServer is headless and does not bind a TCP port (verified against the
cached Minecraft `GameTestServer.initServer` source); another development
server on 25565 is not a port conflict and is not stopped.

Use `-Pms14GameTestDir=build/gametest-disposal-focused-01`. No established
per-method Gradle filter exists, so server validation runs all registered tests.
Retry with only that disposable fixture world reset. Retain failure logs,
never reset another workspace's server/world or weaken loaded-world guards.

Logs are under `build/disposals-*.log`; prior server failure logs are preserved
separately from the reused run directory. Compilation and isolated tests do
not establish connected camera, presentation, or menu behavior.
