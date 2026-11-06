# Six-substep trial — R31

## Result

Diagnostic only; not readiness, owner acceptance or production adoption.
Filtered native selection **5/893**, completed **5 passed / 0 failed**.
All five launches confirmed actual SDK substeps **6**, expected **6**, before
native velocity admission. Each passed ascent, descent, contact and ten settled
samples with unchanged hull-conservation and geometry assertions.

| Fixture | Upward launch nudge | Maximum penetration (m) | Raw result / named completion lines |
| --- | ---: | ---: | --- |
| Furnished cabin | 20 m/s | 0.0034149488874462013 | 1204 / 1205 |
| Furnished cabin | 40 m/s | 0.07300552011449923 | 1226 / 1227 |
| Furnished cabin | 80 m/s | 0.1435592474402796 | 1263 / 1264 |
| Direct shell | 80 m/s | 0.1436226572687147 | 1269 / 1270 |
| Stock-assembled shell | 80 m/s | 0.14362265726678913 | 1255 / 1256 |

These are launch settings, not measured contact-speed certifications. At 80,
all three fixtures briefly overlapped terrain by about **14.4 cm** at sample
247; sample 248 was about **5 cm**, then they settled. The unchanged oracle
fails penetration over 0.10 m on **two consecutive** samples, or immediate
hull/coverage loss or whole-slab escape. Thus PASS does **not** mean zero
penetration. No permanent failure latch or whole-slab escape occurred.

Actual-setting/admission evidence is at raw lines 1159–1182. Furnished cabins
had 477 cells/parts and mass 478; both shells had 452 cells/parts and mass 452,
with matching fingerprint
`934122934f05162dfe6052f4eda2b94c547b68787443e3f5f02f9d6d37382405`.
These MS14-present shells are not R30's vanilla mass-903 stock-only comparator.

Batch completion is at lines 1271/1276. Normal stopping and dimension saves
appear at 1278–1291; build success at 1303. Receipt exit code is 0 and
`tree_cleanup=not_needed`. This is not a diagnosis of previous shutdown hangs.

## Scope and remaining limits

Only the existing Python preparer's exact fresh R31 name/count approval and
its tests changed (14 focused Python tests passed; independent review passed).
Existing Java fixtures, physical thresholds, engine and production defaults
did not change. The fresh isolated run used Sable's supported server setting;
owner configuration/worlds/processes/installation were untouched.

Six mitigated these tested trajectories, but minimum sufficient count,
arbitrary orientations/speeds, connected body/cargo behavior and performance
at six remain unestablished. No cost comparison or full suite ran. M1 remains
unaccepted; M2–M6 remain paused. Preserve this used fixture and all previous
failures; do not automatically rerun it.

## Bound evidence

- Candidate: `shuttle-feedback08-substeps6-20261016-r31`.
- Snapshot: `be6525e6bfdd655ab15e72e49cf9b142877ca83715f7bd4a980bcd7909787d75`.
- Frozen plan: `GATE-PLAN-08-SUBSTEPS6-R31.json`.
- Reports root: `build/agent-workflow/shuttle-relocation-sprint/reports/`.
- Receipt: `shuttle-feedback08-substeps6-20261016-r31-gate-validation.json`,
  SHA-256 `88a22f2f964bf650a8728922ef6776c3f6933207c2cede9ee12f1ee427ed7347`.
- Raw: `feedback08-substeps6-r31-native.log`, 274,487 bytes,
  SHA-256 `08670305959bc71b3aff1f2d3947f8d7ead0ba6427d1f72c469b6aba9baf051e`.
- Parent independently checked receipt/raw hashes and named completion mapping.
