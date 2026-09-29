# APC overload runtime integration (M4)

## Runtime path

`PowerRuntime.solve` stores each APC's `PowerLiveLoop.Result.apcOutputWatts` meter with the solve's server-event tick. The cached validity bit is true only when **both** the APC's MV input and APC-tier output topology are known. The meter is delivered output, not demand or input power; absent map entries become invalid zero observations.

On each enabled server tick, the runtime iterates only cached APC samples, checks `hasChunkAt` before obtaining the block entity, and calls `observeActualApcOutput` once. This adds O(loaded APCs with a cached sample) block-entity lookups per tick; there is no per-tick topology traversal or solver call. Each sample carries the read-only per-level `topologyGeneration`; a mismatch or age over 20 ticks resets protection, and a mismatch requests one early solve when the graph is ready. Normal full indexed-device topology resolution and solving remain on the existing 20-tick cadence. No fixed upper bound on the device index was found, so this is an APC-count bound rather than a hard numeric budget. Unloaded chunks are removed from the cache/index and never force-loaded.

When the BE trips, its authoritative breaker and trip latch change immediately. The runtime resets the cached output meter and schedules one bounded re-solve for the next server tick. That solve recomputes indexed lamp projections from the resolved APC-tier components, so lamps on the tripped circuit go dark on that next tick—not in the trip tick—while unrelated circuits retain their computed supply. Removal and chunk unload discard cached samples; level/server shutdown clears the cache; disabling simulation resets loaded APC observations and clears runtime state. Existing startup enable/disable behavior is unchanged.

## Reachability and cadence


The fixed local debug-source offer is now 25,000 W (up from 10,000 W). The known path is capped by the 30,000 W rated source, 30,000 W substation transfer, 30,000 W APC input port, and 90% substation efficiency. Thus the source path can deliver up to 22,500 W before APC-tier demand, with any battery contribution separately accounted. Connected demand is still required to consume that power and cross the strict `>20,000 W` threshold. No prototype YAML importer or prototype changes were added. This debug-source balance adjustment enables the test fixture; it is not an assertion of upstream prototype parity or ordinary starter-station reachability.

Protection observes the latest valid solve meter each loaded tick, but does not interpolate between solve outputs. The reducer only advances once for each consecutive server tick and trips on the 61st qualifying tick (`>3 s`, strict). With 20-tick solve sampling, overload beginning just after a solve can take up to roughly 80 ticks (4 seconds) to trip: the next solve must first record a high meter, then 61 tick observations must accrue. Any unknown/stale interval resets continuity.

## Evidence and limitations

- `ApcOverloadGameTests.connectedCablePathLampLoadTripsApcAndManualRecloseRestoresIt` is the real wire-connected overload GameTest. It connects a source, substation, APC, cable grid, and 298 actual 100 W lamp block entities (29.8 kW demand), then verifies the runtime trip/latch, manual reclose, and lamp projection returning after the scheduled re-solve. This proves that fixture, not general station-scale performance or every wiring topology.
- `PowerDeviceGameTests.liveSourceSubstationApcBatteryAndLampChain` separately verifies a connected passthrough path and breaker-open darkness for a manual open. Synthetic solver/protection tests cover accounting and boundary/reset cases not established by that fixture.
- `ApcOverloadGameTests.connectedCablePathLampLoadTripsApcAndManualRecloseRestoresIt` also removes an MV cable after overload observations have accrued and verifies the APC remains closed/unlatched through 85 ticks with the zero-battery lamp dark. This exercises topology freshness/disconnected-before-trip behavior; see [topology-freshness.md](topology-freshness.md).
- Per-tick work is limited to cached APCs; no 20-player station benchmark or hard device-population ceiling was measured. The one-time trip blackout walks the already-indexed device set to darken lamps. No performance claim beyond these bounds is implied.

## Validation

- `./gradlew.bat compileJava compileGameTestJava test --no-daemon` — passed.
- `./gradlew.bat runGameTestServer -Pms14GameTestDir=build/apc-overload-final-server --no-daemon` — passed, 165 required GameTests. Server output also contained pre-existing legacy over-capacity puddle warnings; they did not fail tests.
- `git diff --check` — passed. No 20-player load/performance benchmark was run. Owner acceptance and the full M4 performance/review gate remain separate.
