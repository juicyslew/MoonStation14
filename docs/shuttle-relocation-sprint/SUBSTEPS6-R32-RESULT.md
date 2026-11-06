# Six-substep regressions — R32 interrupted by fixture failure/crash

## Actual outcome

Diagnostic **FAIL**, not readiness or production adoption. Focused compilation
and JVM checks passed **8/8**. Native preflight selected **51/893**, but the
batch did **not** complete: progress recorded **25 passes / 1 failure / 25
unfinished** before a server crash. These are partial results, not 51 completed
tests. The cost fixture preparation and native measurement were **not run**.

Raw `feedback08-substeps6-r32-regression-native.log` records:

- Selection at line 17.
- `registeredcargopreservesnativepointvelocitywhenleavingframe` failed with
  “owned native frame floor is removed without destroying the frame” at
  lines 1375–1376. This does not identify six substeps as the cause.
- Cargo-drop readiness confirmed actual SDK substeps **6** at 1532/1536.
- An `ItemStack.get(...)` null dereference in a callback at
  `SableOwnedBodyCarryGameTests.java:1200` crashed the server (1552–1587).
  A crash report was saved at 1600. No completed aggregate, dimension-save
  sequence or orderly shutdown was evidenced. Exit code was 1; the receipt's
  `tree_cleanup=not_needed` is not a clean-shutdown claim.
- Accepted sustained exterior jump cases were shown starting, but their
  outcomes were not evidenced; this run does not establish their six-step pass.

Independent evidence review verified the bound native artifact. Source-cause
investigation is separate; do not assume the floor-removal assertion and null
access are either caused by six or evidence of a runtime hull-collision defect.
Preserve the complete failed fixture, raw log, crash report and receipt.

## Source classification and follow-up

The floor assertion only required `SableFixturePlot.setStorageBlock` to return
true for each of four selected cells; that method returns the parent world's
`setBlock` result. Its message did not actually verify frame survival. R32 did
not record the failed coordinate or before/after states, so an already-air
selection, failed write and frame-lifecycle issue remain distinguishable unknowns.
The selected Y is derived from the settled actor point, not directly from the
known authored deck Y; do not infer which cell failed without a witness.

The jug event observer dereferenced a missing reagent component. Fixture setup
had authored water, but when/why the component disappeared is unestablished.
Follow-up makes affected cargo observers capture nullable content evidence and
retain normal test assertions rather than throwing from the global tick event.
It also records bounded floor-write operands without changing selected cells,
stimulus or the write-success requirement. These are fixture safety/diagnostic
changes, not a runtime fix or a claim that the underlying failures are repaired.
Classification requires a fresh fixture; the R32 world must not be reused.

## Bound evidence and limits

- Candidate: `shuttle-feedback08-substeps6-followup-20261016-r32`.
- Snapshot: `ecc0c896eaf830511e6d28181a6607a17657746d5d5658e492c3c88e5705b88a`.
- Frozen plan: `GATE-PLAN-08-SUBSTEPS6-R32.json`.
- Reports root: `build/agent-workflow/shuttle-relocation-sprint/reports/`.
- Receipt: `shuttle-feedback08-substeps6-followup-20261016-r32-gate-validation.json`,
  SHA-256 `50a9b25e911365c0f6ad549ad458af65e974618720ceb767a65c202b1468bf1f`.
- Native log: 377,057 bytes,
  SHA-256 `021be1810b8b0e29fd01ad5d662a1fc6004a8638dd1725838c31eb28dcd50c4a`.

R31's five six-step impact passes remain separate bounded evidence, not proof
of these regressions or server cost. Production/owner configuration is unchanged.
Six-step performance remains unmeasured. M1 remains unaccepted and M2–M6 paused.
Do not automatically rerun this used fixture or treat its failures as passed.
