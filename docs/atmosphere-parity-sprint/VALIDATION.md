# Foundation parity validation

Target: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. See `PLAN.md`, `DECISIONS.md`, and `UNIT-AND-MIXTURE-CONTRACT.md` for the accepted scope and conversions.

**Latest production transport:** the separately authorized pressure-driven normal solver and pressure-compatible LINDA passed **1,928 JUnit tests / 76 selected GameTests**, default compilation and assembly. See `../pressure-transport-sprint/VALIDATION.md` for current evidence and artifact. This is an intentional normal-transport departure from the reference kernels described below; chemistry and the Space/explosive kernel remain unchanged by that departure. Live station acceptance remains open.

Foundation and follow-up evidence are distinguished below. Station-scale follow-up passed fresh automated gates, but still requires a live retest; see `LIVE-FOLLOWUP.md`.

The second live retest improved pressure balance but still reported classification flicker and long reaction episodes/lag. Further LINDA-locality and redundant-classification fixes passed static review and fresh consolidated gates below. Their live-station impact remains unmeasured.

Additional hot-path preparation/render-selection optimizations passed independent review and fresh gates below. Scope and remaining profiling candidates: `PERFORMANCE-AUDIT.md`.

The latest retest still reported pending classification and burst-like updates. Subsequent source-group scheduling and ownership liveness corrections passed **1,883 JUnit tests / 277 suites**, **73 focused GameTests**, default compilation and assembly. See `GROUP-LIVENESS-FOLLOWUP.md` for causes, measurements, evidence paths and remaining live acceptance boundaries. A further fire-cooldown and progressive-visual correction passed **1,888 JUnit tests** and **75 focused GameTests**; see `REACTIVE-FRONT-FOLLOWUP.md`. Prior performance gates alone are not evidence for these later changes. Live initial-front speed and the reported room-wide burst remain unaccepted.

## Acceptance layers

| Layer | Evidence / current status |
|---|---|
| Source correspondence | Independent static reviews cover mixtures, canonical reaction gates, archived LINDA, groups, Monstermos, immutable boundary classification, stage/deadline resumption, hotspot snapshots and TileFireEvent exposure. Source corrections are recorded in `DECISIONS.md`. This is not a bitwise cross-runtime guarantee. |
| Numerical/regression | **Passed:** latest performance audit has 1,870 JUnit tests across 275 suites, zero failures/errors/skips (`performance-audit-gate-01/native.log`, `junit-xml/`). Prior evidence remains in the earlier gate directories. |
| Fixture compilation | **Passed:** default `compileGametestJava` restored after the latest selected world gate; `assemble` successful. See `performance-audit-gate-01/default-compile-restore.log`. |
| Focused Minecraft integration | **Passed on rerun:** all 73 required tests in the existing 18-class gate after the performance audit (`performance-audit-gate-01/gametest.log`). Attempt 01 hit the previously intermittent negative-stack drying assertion; retained in `attempt-01/`. Root cause is unresolved; no production cadence or assertion was changed to obtain the passing run. |
| Whole namespace | Not rerun/accepted as green. An additional 382-test run reported one failure: `LungGameTests.lungEffectsScaleOnceAndStayInPersistedInventory`, an exact-decimal assertion incompatible with float-backed gas storage. The assertion now derives its expected inventory from stored float inputs; **all four LungGameTests passed** in `lung-effect-float-gate/`. The 382-test namespace was not repeated. This was an active-overhaul expectation, not an unrelated failure. |
| Connected client / live station | **Still unaccepted; another retest pending:** pressure balance improved, but classification flicker and long combustion episodes/lag persisted. Subsequent quadratic-work and redundant-proof fixes are implemented; see `LIVE-FOLLOWUP.md`. Automated gates do not establish live responsiveness or actual station TPS. |

## Important world-discovered corrections

- Monstermos admission must not consume the shared active queue before LINDA; pressure-equal cells can still need species/temperature exchange.
- Space-flow bookkeeping also does not suppress LINDA. Upstream reactivates Space-progression finite cells; a low-pressure Space kernel no-op must still permit later immutable-reservoir sharing.
- An activated loaded unknown seed must request ownership classification automatically. Diagnostic-only polling must not depend on manually sampling the seed to create work.
- HOTSPOTS snapshots actual source positions across yields. New spread receivers cannot displace previously admitted sources or receive a scheduler whole-air fallback reaction.
- Newly created receiver hotspots wait for a later pass even if their positions were already admitted as generic reactive work without hotspot metadata.
- An observer must distinguish 6,250 L spread-created metadata from 25,000 L whole-air reaction-created metadata. The latter can legitimately appear with chemistry during LINDA; it is not evidence of same-pass HOTSPOTS receiver processing.
- Existing fixtures must count stage callbacks correctly: 15 Hz admits stages/resumptions, nine default stages give nominal 0.6-second cycles. Larger observation windows do not waive finite ownership, loaded-only behavior, transaction safety or eventual transport assertions.
- Source microscopic cleanup and float arithmetic intentionally invalidate exact-double conservation assertions in specific cases; expectations must name the operation and float/cleanup effect, not merely loosen tolerances.
- Archived-capacity conduction can also change raw energy beyond floating-point error when live inventory differs. A source-equation numerical reference establishes that behavior; world continuous-writer energy residuals must not be labeled exact physical-conservation checks.
- Missing ledger entries can look like stalled transport. Realized immutable LINDA exchanges and immutable-member averaging now update observers after successful commits, excluding whole-air chemistry and avoiding boundary labels for pure finite-group drift.

## Commands

Native gate: `./gradlew.bat test compileGametestJava`.

Focused server: `runGameTestServer` with `-Pms14GameTestDir=build/atmosphere-parity-fixture-run` and the existing classes: `AtmosphereAmbientBreachGameTests,AtmosphereCraterOwnershipGameTests,AtmosphereFractionalChainGameTests,AtmosphereHotFrontGameTests,AtmosphereHotspotMutationGameTests,AtmosphereLoopRoomGameTests,AtmosphereM11GameTests,AtmosphereOwnershipTailRetryGameTests,AtmosphereReactionGameTests,AtmosphereRoomGameTests,ExplosionAtmosphereGameTests,ExplosionDownstreamGameTests,ExplosionGameTests,FireStackGameTests,LungExchangeGameTests,PressureGameTests,ThermalGameTests,ThrownGrenadeGameTests` through `-Pms14GameTestClasses`.

Run serially; archive logs/XML before reruns. Retain failure evidence separately from disposable server worlds. No new fixture design or development-protection mode is included in this sprint.

Disposable primary/failure server-run copies were removed after acceptance; logs, JUnit XML and summaries remain in the evidence directory. All changes remain uncommitted for review.
