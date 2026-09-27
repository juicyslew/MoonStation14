# M10 breach and overlay regression audit

**Status: code and reported build/test validation complete; M10 acceptance is OPEN.** After the latest breach and visual fixes, the coordinator reports all three Gradle validations listed below as successful, including a full rerun that executed `compileGametestJava`. The breach-opening comparison exists in `AtmosphereRoomGameTests` source and was compiled but has NOT been executed in a server world. No game/client was launched, no visual smoke was performed, and no 20-player measurement was run. M10 does not close M0–M9.

## Reported regressions and current code

### 1. Large vacuum breach drained too slowly

The previous finite-to-exterior path used the general `0.125` transfer fraction at the four-tick atmosphere cadence. Because finite cells could sit in a FIFO work backlog, a large breach could drain slowly even beyond the per-cell relaxation rate.

Current source adds the vacuum-specific `ImmutableAtmosphereBoundary` behavior and opening-adjacent urgent work:

- For an immutable vacuum exterior, when finite-cell pressure exceeds exterior pressure by **more than 10 kPa**, the finite mixture loses enough moles to relax **half of the pressure difference** in that exchange. Species proportions and source temperature are preserved for the gas-removal calculation; thermal exchange with the space model is separate. This is a scalar cell-pressure approximation, not a directional flow calculation.
- The vacuum-boundary cleanup uses a **2 mol minimum-gas threshold** and a **10 kPa low-pressure-difference threshold**: it clears a nonempty finite mixture at/below the minimum inventory or once the pressure difference is at/below the low-pressure boundary. Thus it is an eventual-drain cleanup path rather than a strict rule that preserves all sub-threshold-pressure gas. Exterior ambient is immutable; exported species and energy are reported to the transient per-server-level boundary ledger.
- Cells still above 10 kPa after exchange are offered to an urgent lane, capped at **256 distinct positions**. Scheduler polling services up to **8 consecutive urgent positions** before yielding to ordinary FIFO work when FIFO work is available; ordinary queued work remains the fallback rather than being starved by urgent openings. Existing caps and deferred scheduling remain; this does not imply unlimited throughput or a bounded total scheduler backlog.

The source was informed by SS14 Monstermos depressurization reference parameters (`MonstermosDepressurization` enabled, `SpacingEscapeRatio = 0.15`, `MinGas = 2`, `MaxWind = 500`) and its half-pressure-bound `ReleaseGasTo` behavior. Those settings are comparison context only. This Minecraft code does **not** implement the directional Monstermos depressurization/wind behavior or reproduce its pressure-wind result; do not describe it as exact SS14 behavior or parity.

### 2. Breach size had no regression comparison

`AtmosphereRoomGameTests.fullWallOpeningDrainsMoreThanOneBlockOpening` builds two finite test rooms with a full-wall opening versus a one-block opening, injects the same initial breathable gas, ticks the isolated service, and asserts the larger opening leaves less gas. This is an opening-count comparison, not a detailed flow-rate or performance test. `coveredExteriorSealingAndBreachRespectOwnership` separately checks that a breach does not instantly erase a finite claim or gas, then checks drainage and species-ledger accounting.

Both are test-source evidence only: neither GameTest was run. They require execution in an actual GameTest server world before they can serve as acceptance evidence. The room fixture, topology behavior, cadence, conservation, and boundary ledger remain subject to that execution.

### 3. Gas quads bled through walls and opacity compounded

The crossed overlay-quad radius is now capped at **0.48 blocks**, inset from the half-block cell face; the earlier maximum exceeded 0.5 and could bleed across walls. The five SS14-referenced overlay gases and per-tile opacity policy remain: plasma, tritium, water vapor, ammonia and frezon are rendered, while nitrous oxide is not. Per-tile amounts use the SS14 2.5 m³ thresholds/maxima scaled to a 1 m³ Minecraft cell: plasma/tritium/water vapor use 0.25–5 mol, ammonia 2–7 mol, and frezon 0.6–12 mol. Opacity is quantized to 20 levels.

For multi-plane Minecraft rendering, the resulting alpha is converted to per-plane alpha for **three planes** across a **four-block column**, limiting accumulated column opacity. Candidate selection is nearest-first, with tritium favored only when candidates are at equal distance; the batch is limited to 2,000 cells within 48 blocks. A limited-visual HUD warning reports cache eviction/incomplete view. This is still a procedural, UI-color-tint approximation and not SS14's white-alpha sprites or animation. Runtime wall occlusion, appearance, opacity, and visual quality remain unverified.

### 4. Client overlay flicker / cache resets

The regression root cause was that non-reset delta packetization reused resettable snapshot semantics, clearing previously cached gas visuals whenever a delta was applied. Current code separates `packetizeDelta` from resettable snapshot `packetize`; delta packets carry `resetSnapshot = false`. On the client, deltas newer than a snapshot being staged are queued and replayed after the replacement snapshot publishes, rather than being lost during the snapshot transition. These changes address cache clearing/races at the source level, not a proven in-game flicker result.

Focused client visual tests are reported passing after a stale selection expectation was fixed. No launched client/server test or visual verification was performed; network ordering, chunk watch/unwatch, resync, and appearance still need runtime/integration verification. Cache truncation can still omit visuals, and the limited-visual HUD warning indicates an incomplete view rather than complete coverage.

## Validation status and acceptance gates

The coordinator reports the following commands each completed with `BUILD SUCCESSFUL` after the latest breach and visual fixes:

```powershell
.\gradlew.bat test --tests 'com.juicyslew.moonstation14.ms14.atmos.core.*' --tests 'com.juicyslew.moonstation14.ms14.atmos.world.*' --tests 'com.juicyslew.moonstation14.ms14.atmos.visual.*' --no-daemon -x compileGametestJava
.\gradlew.bat test --rerun-tasks --no-daemon
.\gradlew.bat build --no-daemon
```

The full rerun executed `compileGametestJava`. The earlier unrelated blocker in the concurrently edited `GhostMobHarnessControl.java` did not recur; this audit does not claim to have fixed that file. These results are coordinator-reported, not commands run as part of this documentation update. Focused tests cover relevant pure boundary/visual behavior, but do not establish in-game appearance or world integration. The full-wall-versus-one-block opening comparison in `AtmosphereRoomGameTests` was compiled, NOT EXECUTED. No game/client or server was launched; no manual visual inspection, owner smoke, or 20-player benchmark has occurred. No performance claim is made for the 256-position urgent lane or larger openings.

Before accepting M10, execute the atmosphere GameTests in a server world, including both breach tests and the full-opening versus one-block comparison; verify ledger results and behavior under a range of openings/initial pressures; exercise chunk watcher snapshots, deltas and resync; inspect wall bleed, column opacity and flicker in-game; and measure workload/latency at representative scale. Continue to preserve all earlier milestone gates as open until separately accepted. Do not launch a game/server as part of this documentation update.
