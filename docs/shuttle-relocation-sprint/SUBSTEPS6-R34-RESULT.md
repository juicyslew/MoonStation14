# Focused six-substep follow-up — R34

## Outcome

Compilation passed. Filtered **6/894 selected, 6 completed: 5 passed / 1 failed /
0 unfinished**. Actual SDK substeps6/expected6 was logged before the separate
geometry diagnostic (raw1229). Server stopping and all dimension saves were
normal (1290–1302); exit1 is the failed diagnostic, not a crash or readiness.

| Case | Result and evidence |
| --- | --- |
| Cargo departure after removing authored deck support | PASS; four known stone→air writes at authoredY128, true write results, `registeredBefore=true`, `registeredAfter=true`, `plotRemoved=false` (1209–1212). Source binds each registration flag to exact container instance equality. |
| Cargo point velocity and following world tick | PASS (1215–1217); departure horizontal error `4.440892098500626E-16`m/s; subsequent airborne error `1.6940658945086007E-21`; exact root/token/reagent assertions retained. |
| Moving jug own-travel/carry separation | PASS (1222/1225); ownX0.75, totalX0.8431397853, tiny positive ownY remains. Same live root, reagent present, water500 cents, removed=false. |
| Real wall and floor jug contacts | Both PASS (1192–1193), including existing spill-conservation assertions. |
| Fast airborne jug without contact | PASS (1214), preserving contents. |
| Geometry-observed negative-X strong impulse | FAIL at tick57. Captured geometry accompanies the same approximately `(-0.003628,+0.002662,-0.0000016)` accepted-own response; underlying movement admissibility remains unresolved. |

The two cargo rows describe the same selected departure fixture, so this table
is six selected tests, not seven. The five passes establish scoped behavior
after the support-coordinate and own-impact eligibility corrections, not a
complete regression or persistence pass.

## Invariants and remaining work

The jug repair requires clipping of a nonzero requested **own** travel axis
before break eligibility. Extra position pushout alone cannot qualify. Resolved
profile values, total own-speed threshold0.5, genuine floor impact including
horizontal speed, response friction/restitution, spill and identity rules remain.
The extracted production predicate passed focused JVM2/2 before submission.

The diagnostic reuses existing selected-contact geometry capture and shared
negative-X setup in a separate batch. Its complete geometry includes a selected
steel-wall contact and ten entity-collision subdivisions, distinct from six
world physics steps. The original default-path negative-X case remains unchanged
and unresolved. Do not relax `1e-6` movement bounds, admit arbitrary pushout,
or claim default-path repair from observation-enabled results. Analyze the
independent geometry/accounting before any acceptance-path implementation.

Cost testing remains paused. Preserve this failed fixture and all older evidence;
do not rerun it automatically. Production/owner configuration remains unchanged,
M1 unaccepted and M2–M6 paused.

## Bound evidence

- Candidate `shuttle-feedback08-substeps6-focused-20261016-r34`.
- Snapshot `7439f6806fb896929d432e72ec673fae4597c26263bb8c96120cb85bd4d83ba6`.
- Frozen plan `GATE-PLAN-08-SUBSTEPS6-R34.json`.
- Reports root `build/agent-workflow/shuttle-relocation-sprint/reports/`.
- Receipt `shuttle-feedback08-substeps6-focused-20261016-r34-gate-validation.json`,
  SHA-256 `ff5df263d482a3751ef013748929897930182df22c7277e18aab406c43da939d`.
- Raw `feedback08-substeps6-r34-native.log`, 358,677 bytes,
  SHA-256 `4d69e31bcb8df9885b45289bfb92bd42b963cac61ca354764e854195742546d0`.
- Independent raw evidence review verified artifact hash/counts. Parent resolved
  its registration-evidence concern using raw1209–1212 and the source equality
  checks; `registeredRoot` alone is not container-liveness evidence.
