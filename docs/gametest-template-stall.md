# Power GameTest template stall

## Confirmed cause

Two structures encoded root `size` as `TAG_INT_ARRAY` (11), rather than the
Minecraft structure format's `TAG_LIST` (9) containing three `TAG_INT` (3)
values:

| Resource under `src/gametest/resources/data/moonstation14/structure/` | Intended dimensions |
| --- | --- |
| `power_live_chain_clear.nbt` | 9 × 4 × 10 |
| `power_overload_clear.nbt` | 13 × 4 × 16 |

Minecraft 1.21.1 `StructureTemplate.load` reads `tag.getList("size", 3)` and
constructs its size from that list. An integer array is not accepted as a
list; the dimensions become zero. This is a test-resource encoding bug, not
evidence of a gameplay power bug or a test-owned chunk-cleanup leak.

## Why only some runs hang

The GameTest server selects a random origin. Zero-size structures expose a
boundary mismatch: `StructureUtils.getStructureBoundingBox(pos, size,
rotation)` moves the box's minimum X/Z to the supplied origin when preparing
and forcing chunks. Its structure-block overload does not perform that move.
For a zero size, preparation covers origin through origin + 1, while the
subsequent readiness check covers origin - 1 through origin.

At a chunk boundary, readiness therefore requires an additional chunk that
was never forced. `GameTestInfo.tick` waits for all structure chunks to be
entity-ticking **before** starting execution and processing the per-test
timeout. Waiting here can persist indefinitely.

The replay at origin `(4142203, -59, 1754552)` identified
`VentilationPowerGameTests.paidWorkCallbackIsSingleUseAndInvalidated` as a
blocked test. Its structure block was current and not removed, but reported
size `(0,0,0)`. Its bounds included chunks `(258891,109689)` and
`(258891,109690)`; the former was loaded but not forced/entity-ticking, while
the latter was ready. No force call for the missing chunk was recorded.

Other users of these resources include the ventilation power-prototype and
meter tests, `PowerDeviceGameTests.liveSourceSubstationApcBatteryAndLampChain`,
and the overload fixture in `ApcOverloadGameTests`. The blocked test is an
affected consumer; its callback never started and did not cause the stall.

## Evidence and repair acceptance

- Exact framework source: `build/moddev/artifacts/neoforge-21.1.224-sources.jar`:
  `StructureTemplate.java:706–710`, `StructureUtils.java:74–77,126,189–195`,
  and `GameTestInfo.java:95–107,120 onward`.
- Reproduced live state: `build/gametest-stall-be-trace-01.log`.
- Replay order: `build/gametest-stall-replay-order.txt`.
- Observed normal completed runs: 479 tests in roughly 18 seconds, roughly
  43–46 seconds including Gradle/startup. Test failures are distinct from a
  harness stall.

Repair must preserve the intended dimensions and correct their NBT types,
not add force-ticket fallbacks, enlarge timeouts, or weaken test assertions.
Regression checks must inspect the actual resource schema and the list
Minecraft consumes. Both templates have been corrected, and
`GameTestStructureTemplateEncodingTest` guards the actual fixture tag types
and intended positive dimensions.

The formerly stalled origin/batch-order replay now **completes all 479
tests**; it still reports a `poweredscrubbercapturesonlyselectedgas`
ventilation-solve assertion failure. This is not a pre-start stall and is
retained as a separate ordering/origin-sensitive validation issue. The
ordinary clean-world run passed all 479 tests. Evidence:
`build/gametest-template-size-fixed-replay.log` and
`build/gametest-template-size-fixed-standard.log`.

Temporary diagnostic/replay mixins and their Gradle options were removed.
The final ordinary run without them passed all 479 GameTests in 16.98
seconds (40 seconds including Gradle/startup):
`build/gametest-template-final-clean.log`. The final build and GameTest
compilation passed with 2,060 JUnit tests, no failures/errors/skips:
`build/item-controls-and-template-final-build.log`.
