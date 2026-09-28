# M5 station-scale power audit

## Scope and method

This is a deterministic standalone JUnit probe of `LoadedPowerGraph`; it does not start a game/server and makes no player/atmosphere claims. Run with ` .\gradlew.bat test --tests '*PowerScale*' --no-daemon`. The test reports elapsed nanoseconds, graph work/tick, convergence ticks, queue high-water, and a deliberately rough retained-memory allowance. It asserts only the graph's per-call node budget, not timing.

Environment: Windows, Gradle 9.2.1, JUnit 5.10.2, Eclipse Temurin OpenJDK 21.0.10; benchmark host `DESKTOP-4F0ER3K`. Wall-clock values are a single local run and are indicative only, not a reproducible service-level guarantee. The memory estimate is 256 bytes per cable-face-node, not measured heap/RSS. Each node workload is split across 16-block chunks; line tests span 500 and 1,250 chunks respectively. Junction workload packs six cable-face orientations per host position. Edits invalidate/rebuild the same 8k graph three times; unload/reload removes then restores one chunk.

## Recorded run

| Workload | Nodes | elapsed ns | processed nodes/tick (mean) | max/tick | convergence ticks | queue high-water | rough bytes |
|---|---:|---:|---:|---:|---:|---:|---:|
| line-8k | 8,000 | 208,714,800 | 253 | 256 | 63 | 1 | 2,048,000 |
| line-20k | 20,000 | 280,455,800 | 254 | 256 | 157 | 1 | 5,120,000 |
| junction-8k | 8,000 | 392,884,500 | 253 | 256 | 63 | 1 | 2,048,000 |
| burst-edit-8k (three chunk edits) | 8,000 | 105,787,000 | 253 | 256 | 63 | 1 | 2,048,000 |
| unload-8k | 7,984 | 85,643,300 | 253 | 256 | 63 | 1 | 2,043,904 |
| reload-8k | 8,000 | 106,613,600 | 253 | 256 | 63 | 2 | 2,048,000 |

The unload removes the first 16-node chunk; the following reload restores it. These values are a harness report, not a strict comparison; repeat runs may vary substantially due to JIT and host load.

## Findings / bottlenecks

- `PowerGraphService.noteChanged` coalesces pending refresh by chunk but calls `graph.invalidate()` for every edit. `LoadedPowerGraph.invalidate()` clears component/index/frontier state. A burst of cable changes therefore discards all station graph progress, rather than refreshing only affected components. The benchmark demonstrates deterministic full-graph reprocessing but does not include Minecraft chunk attachment validation/loading overhead.
- Graph work is hard-capped at 256 nodes per server tick. At that configured budget the 20k line requires 157 processing ticks to converge. Graph knowledge deliberately remains UNKNOWN while pending/rebuild; this is safe fail-closed behavior but can leave a large edited station without known power for multiple seconds.
- Current queue high-water for these line and packed-orientation workloads was 1-2. That does not predict branching topology/heap peaks in a real station. Memory figures are estimates only.
- The runtime's silent 512-device insertion cap has been removed. Loaded registered devices now remain indexed and `PowerRuntime.indexedDeviceCount(level)` exposes the current count for diagnostics/audits; device solve work is still synchronous every 20 ticks and has no adaptive chunk slicing. Thousands of devices are no longer silently omitted, but production-scale device solve latency has not been benchmarked here.

## Acceptance status and remaining risk

This is not an acceptance claim for a 20-player live station. The synthetic 20k graph workload converges under the current fixed budget, but its 157 ticks, full invalidation behavior, lack of actual heap profiling, missing Minecraft chunk-validation cost, and unbenchmarked device solve prevent claiming station-scale performance acceptance. Incremental affected-component invalidation and production-like dense/burst workloads remain follow-up work; changing graph invalidation safely needs split/merge correctness and chunk-boundary review beyond this bounded task. No manual game/server was run.
