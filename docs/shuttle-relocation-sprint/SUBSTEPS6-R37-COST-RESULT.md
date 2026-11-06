# Matched two-versus-six single-cabin cost — R37

## Validity

Both fresh serialized runs passed: **1/894 selected,1 completed/passed,
0 failed/unfinished each**. Compilation and minimal-config preparation passed.
Actual/expected SDK settings were2/2 then6/6. Both logged **players0,477 hull
pieces**, the same powered furnished cabin/support workload and overworld
dimension. The existing known100W, authored-hull, native support/natural-rest,
launch-admission and timing-pair guards passed. No player-count invalidation
applies.

Each run used200 warmup ticks,300 parked paired samples at actual ticks245–544,
then600 flight pairs at548–1147. Launches were0 parked and6 flight, using the
existing six additive upward20m/s nudges with natural rest before each. The
sample bounds are inclusive and match the paired counts. Frame UUIDs differ
normally between fresh worlds. Both tests passed at raw1223, stopped at1225 and
saved all dimensions at1238; no crash or abnormal stop was found. Independent
read-only review verified receipt/raw hashes, matched workload and distributions.

## Whole-server tick-envelope distributions

All values are milliseconds; raw setup/results are at1154/1182/1216 in each log.

| Substeps | Phase | Min | Mean | P50 | P95 | P99 | Max |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 2 | Parked | 0.4899 | 1.79335 | 1.1217 | 4.9502 | 6.4066 | 20.1647 |
| 6 | Parked | 0.6583 | 1.9434256666666667 | 1.2379 | 4.9016 | 6.1802 | 24.6808 |
| 2 | Flight | 0.4127 | 1.9715085 | 1.3503 | 5.0284 | 8.0812 | 28.2808 |
| 6 | Flight | 0.6524 | 2.660422 | 1.7169 | 6.3101 | 8.9161 | 13.1803 |

Observed six-minus-two mean differences: parked **+0.1500756667ms (+8.37%)**,
flight **+0.6889135ms (+34.94%)**. Flight P95 increased **1.2817ms (+25.49%)**;
parked P95 was0.0486ms lower. Peaks do not move uniformly: the six-step flight
maximum was lower in this pair, which is not proof of better worst-case stability.

## Interpretation and limits

This answers the bounded current-code single-cabin cost question; R7's older
two/ten pair is historical context only. Fixed two-then-six order, independent
process/JIT/GC/environment effects and fixture/server activity remain confounded.
The percentage is an **observed whole-server envelope difference**, not a causal
substep cost multiplier or isolated Sable CPU measurement. The unoccupied cabin
does not measure passenger correction/prediction overhead or representative
multi-ship/server population. No profiler, counterbalanced repeats or additional
runs were requested.

Measured P95/maxima were below50ms in this workload, but that is not a20TPS,
capacity or operating-envelope guarantee. There is no new performance acceptance
threshold. Ordinary impact stress at launch40/80 was not repeated: R31's six-step
trajectories mitigated whole-slab escape but still had transient overlap up to
about14.4cm. Cost success cannot turn those into zero-penetration/safe-speed proof.

The six-step trial now has separate bounded collision, repaired scoped/combined
regression and cost evidence. **Production/default remains2**, owner configuration
is untouched, and neither six-step adoption nor M1 acceptance follows. Continue
M1's flight-plus-actual-unload/reconstruction and owner continuity work; connected
render/audio/prediction, process restart, Creative/uncontrolled combinations and
owner-reported cargo loss remain unestablished. M2–M6 remain paused. Preserve all
used/abandoned fixtures and genuine failure artifacts; R32 cost was never prepared.

## Bound evidence

- Candidate `shuttle-feedback08-substeps6-cost-20261016-r37`.
- Snapshot `60a3de3148d6c7456eba2bdcf3e63483028c97445759c1371b1d6510b332726d`.
- Frozen `GATE-PLAN-08-SUBSTEPS6-R37.json`.
- Reports root `build/agent-workflow/shuttle-relocation-sprint/reports/`.
- Receipt `shuttle-feedback08-substeps6-cost-20261016-r37-gate-validation.json`,
  SHA-256 `c859fa2a0f162c783bfb465b54248110e622908b429e77e2f07eefe980fbfae9`.
- Raw2 `feedback08-substeps6-r37-cost2-native.log`,248,861 bytes,
  SHA-256 `d92642ad87542144900db00fd93cb546aba72956eb4a17d1bcbd9326484a2ad2`.
- Raw6 `feedback08-substeps6-r37-cost6-native.log`,248,851 bytes,
  SHA-256 `66869f0b39cf74de525feb605849ea95059ee4c3b7594c0fcb39b2314eee323c`.
