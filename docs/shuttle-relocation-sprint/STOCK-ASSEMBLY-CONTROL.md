# Hull setup control — diagnostic only

## Boundary

Owner instruction: do not modify Sable internals, including source, binaries,
native code or new physics hooks. Keep stock Sable 2.0.6; live/default configuration
remains two substeps while isolated comparisons explicitly test supported settings.
Investigate our integration first. If no supported correction exists, consider
stock-setting mitigations as bandages, not continuous-collision fixes. No owner
configuration, installation, client launch or risky manual impact test is requested.

## Question and implementation

The existing unoccupied impact reproduction has no body, cargo or controller,
and no MoonStation in-flight hull pose writer. It is not a Sable-only runtime:
the mod and its existing compatibility mixins remain loaded.

The added 80 m/s case changes creation from direct plot authoring to existing
public `SubLevelAssemblyHelper.assembleBlocks`, pinned Sable source
`ad8b4d377741fbd923f4d863b2cf9e462daff9a6`,
`common/src/main/java/dev/ryanhcode/sable/api/SubLevelAssemblyHelper.java`.
The engine's canonical block transfer and mass/pivot adjustment remain untouched.
It builds the same production cabin via `ShuttleCabinFixture.build` in leased
physical-world space and assembles only the explicit cabin selection before
authoring the support pad. No corrective post-assembly teleport is added.

Changed code:
- `HullTerrainImpactGameTests`: new tagged case; shared original impact runner.
- `HullTerrainImpactProbe`: bounded pre/post assembly physical capture.
- `HullPhysicalSnapshot`: immutable relative-cell state/shape comparison, canonical
  box ordering, unique coordinates and 728-cell cap; five focused JVM tests.
- `SableFixturePlot`: wrap existing returned owner without pose/pivot changes;
  reuse footprint ownership and public removal for cleanup on wrapper failure.

Baseline 20/40/80 cases retain their original criteria. Control setup mismatch is
inconclusive, never evidence against Sable. Atmosphere/power external records are
not migrated; this is physical-only, not a gameplay assembly feature. Baseline
powered readiness remains; control uses native registration and natural rest.
Compare cross-case physical state/shape fingerprints, mass/COM, pivot, projected
bounds, orientation, rest and admitted velocity before interpreting trajectories.
Unproven equivalence means inconclusive. Matching failures do not establish engine
responsibility; a matched divergence implicates the creation/placement seam.

## Evidence and execution

Independent source review resolved wrapper-failure cleanup and snapshot test gaps,
then verified the unique-coordinate guard. Source review is not compilation or a
runtime verdict. The exact focused plan is
`GATE-PLAN-08-SHELL-SUBSTEPS10-R17.json`: preparation tests/fresh fixture setup, then
two shell cases out of an expected 891 discovered native cases. Complete the
started batch; preserve every genuine failure. No full-suite repeat is planned.
Normal workflow submission binds all current source paths; `scope-r11` is historical,
not current identity. Author detail: `build/shuttle-feedback-08-author-stock-assembly-control-r1.log`.

R12 stopped at fixture compilation: the new enclosing `plot` variable collided
with a lambda parameter. The parameter/references are renamed in R13 without
behavior changes. No R12 JVM tests or native cases ran; its failed receipt is retained.

R13 compiled all source sets; focused JVM results were 20 passed/1 failed of 21.
The translated-origin test accidentally constructed duplicate relative cells in
one snapshot. R14 corrects that test data; the runtime duplicate guard remains.
No R13 native cases ran. Receipts are retained, with no engine conclusion drawn.

### R14 result

Focused JVM **21/21 passed**. Native selected **4/890**, completed all four:
**one pass, three failures**, followed by normal world/sublevel saves and shutdown.
The 20 m/s direct case passed; direct 40/80 reproduced trajectory penetration.
Stock-assembly 80 failed before flight at `SETUP_MISMATCH`: relative pipe cell
`(5,3,8)` changed `up=true` to `up=false`, and its collision box changed from
full height to half height. No stock trajectory or comparable mass/COM/pivot/rest
evidence was obtained. The control is **inconclusive**, not evidence against Sable.

Raw native evidence: `build/agent-workflow/shuttle-relocation-sprint/reports/feedback08-stock-assembly-r14-native.log`,
SHA-256 `9d8904271d06f62200c84ac497495dc06f89337ddf45929017cd58709ad99cff`.
Selection/completion/failures: lines 1147–1156, 1194, 1244, 1368–1377;
save/shutdown: 1373–1404. Receipt is the sibling
`shuttle-feedback08-stock-assembly-20261015-r14-gate-validation.json`.
The logged integer fingerprint is only a convenience hash, not proof of equality;
the full state/shape comparison correctly rejected this transfer.

### Why the furnished stock control is abandoned

The neighbor at `(5,4,8)` is a steel-wall concealed-pipe host, **not** another
visual pipe (`ShuttleCabinFixture.SUPPLY_CONCEALED`, `installPowerMachine`).
`VisualPipeBlockEntity.refreshConcealedJoins` requires active exposed gas ownership,
a live concealed-wall segment/generation and active concealed gas ownership to
restore this upward arm. Stock assembly transfers block state/BE data, not those
MoonStation coordinate-keyed external records. A normal adjacency refresh alone
cannot establish the missing destination ownership. Exact destination records
were not captured, but this transfer does not implement their migration.

Do not add ownership migration merely for this physics diagnostic, force-copy
source states, ignore mismatches or repair pose to manufacture a match. Sable's
assembly already sends destination neighbor notifications; the failure is not
evidence that it never notifies neighbors. The earlier suggested neighbor-refresh
probe was rejected after correcting the fixture-composition reading.

Next diagnostic uses `ShuttleCabinFixture.buildHull` for **both** an 80 m/s direct
shell and public-stock-assembled shell, sharing the existing impact probe/runner.
This omits powered machinery and concealed gas ownership, and is not the same
477-cell furnished cabin. It can test placement dependence only if the direct
shell reproduces penetration and the pair establishes matching physical inputs.
Original furnished 20/40/80 cases and evidence remain intact. Replace the
permanently unmatchable furnished assembly test with the shell pair; preserve R14
receipts. Select only the new pair, not another full native suite.

R15 shell cases use direct plot x=4200 and stock public assembly x=4400, z=3600,
at the existing support height and 80 m/s upward launch. Both skip powered
readiness because neither authors machines. Both share the original strict
state/shape observation and trajectory failure latch. Runtime canonical snapshots
now log deterministic SHA-256 fingerprints rather than integer hashes; compare
these alongside actual mass and projected initial geometry/rest before inference.
New shell code and extended fingerprint tests require fresh validation.

### R15 setup guard correction

R15 compiled and passed JVM21/21. Native2/891 both completed with setup failures,
not impact failures, then saved/stopped normally. Both rejected the residual
quaternion `(2.638e-8,5.705e-9,-3.438e-7,1)` against a new test-only1e-8
component guard. Neither flew. Stock pre/post transfer matched452cells,
SHA-256 `934122934f05162dfe6052f4eda2b94c547b68787443e3f5f02f9d6d37382405`,
mass452; the direct placement log was behind the guard, so cross-case equivalence
was not established. Raw log `feedback08-shell-placement-r15-native.log`,
SHA-256 `02c162ad27c982665029f940e225e03fb753639197b071c50fab87c0b0e26d1a`,
lines1147–1187, in the existing reports directory.

R16 corrects only that setup guard: finite quaternion vector norm<=1e-6 and
`|abs(w)-1|<=1e-6`, both identity signs accepted. This is about2e-6rad or
<0.000032block point displacement at radius16; original impact/support/state
criteria are unchanged. No rotation reset/freeze or other physical repair.
Shared placement evidence now logs before the guard. One pure JVM test covers
observed residual, identity signs, material rotation and nonfinite inputs
(expected22 focused tests). Independent source review passed; fresh runtime
comparison is still needed. Preserve R15 failed receipts without inferring collision.

### R16 matched trajectory result / R17 bandage evaluation

R16 compiled and passed JVM22/22. Both native shell cases completed genuine
trajectory failures (2 selected/891 discovered; 0 passed/2 failed), then saved and
stopped normally. Both452cell/452part/mass452 shells matched the canonical SHA
above, orientation and rest/admitted80m/s velocities. Their4block horizontal
offset was intentional over a uniform64block pad; normalized y bounds matched.
Both first penetrated at247, confirmed248, maximum about0.990153m; final268,
zero settled samples. Later both escaped the slab, not persistent below-pad burial.
State/coverage remained valid. Raw `feedback08-shell-placement-r16-native.log`,
SHA-256 `d2cff16aa6c5a64ab28a9825f4a8bda9ff8246913a2ed99176d3bdba8b86ee24`,
lines1152–1161,1201–1243,1268–1309, in the existing reports directory.

**Direct custom placement is not necessary for this shell symptom.** No local
placement discrepancy was demonstrated. This does not prove Sable fault, native
CCD invocation, native cache equivalence, absence of every MS14 contribution, or
the furnished cabin's precise cause. Engine internals remain prohibited regardless.

R17 evaluates supported10substeps as an explicit bandage in one fresh disposable
fixture. It only extends the existing preparation helper's exact allowlist plus
tests; Java and impact assertions remain R16-identical. Do not duplicate valid
R16 JVM checks. Run12Python preparation tests, existing helper to write the fresh
fixture's single stock setting, then the same2native cases with effective10
required. No live/owner configuration is changed, and production is not adopted.
Higher sampling does not implement continuous collision or guarantee high-speed
safety. Existing furnished10substep regression/cost evidence is indexed in
`OWNER-FEEDBACK-08.md`; the prior fixed-order single-cabin cost run is not capacity
certification. Unknown-power pry cancellation and reported cargo loss remain
separate open issues; M1 is unaccepted.

### R17 result and architectural boundary

R17 preparation tests12/12 passed and created only the new fixture's minimal
stock Sable TOML. Native selected2/891, completed2, passed2/failed0; both actual
launches confirmed10substeps and80m/s,452cells/parts and mass452. Direct maximum
penetration0.02986m, stock0.03008m; both settled for10samples and the server saved
and stopped normally. Independent read-only evidence review confirmed these
results. Native log `feedback08-shell-substeps10-r17-native.log`, SHA-256
`b222b4f53b213fe8cf6eef3921aa2147b9c6cc21907e5c54973b9b40a67d5aba`,
and bound `shuttle-feedback08-shell-substeps10-20261015-r17-gate-validation.json`
remain in the reports directory. R17 snapshot:
`6a0e5242af98917c57ca2053c0d10b0accb558f07c5cf9df24007f814334c9e7`.

**Ten substeps is a supported candidate bandage for the tested80m/s case.** The
matched two-substep failure does not require direct plot authoring, and the
ten-substep mitigation works through both creation paths without engine edits.
Do not infer responsibility for the underlying failure, continuous collision,
safety at400m/s or arbitrary impacts, multiplayer capacity, or full furnished
ship lifecycle correctness. Prior furnished10substep regression/cost evidence
remains relevant but historical; the cost comparison was one cabin, fixed order,
not a throughput guarantee. No production adoption or live configuration edit
has occurred. Unknown-power pry cancellation and reported cargo-loss causality
remain unresolved; this diagnostic pass is not readiness or owner acceptance.

R11 actually completed 889 native cases: 886 passed, three failed (40/80 hull
penetration and premature unknown-power pry cancellation), followed by normal
save/shutdown. Its parser's missing all-passed marker is not an admission timeout.
Retain those failed receipts unchanged. M1 is unaccepted; M2–M6 remain paused.

## Stock-only runtime control (owner requested 2026-10-16)

The isolated comparison removes the MS14 mod, its mixins, systems and root-build
access transformer entirely. R16/R17 loaded them; public stock assembly alone
was not runtime isolation. A separate diagnostic Gradle project now exists
under `tooling/stock-sable-control`, without changing normal owner launches.

The original shell requires MS14 steel-wall and airlock blocks. The isolated
fixture therefore uses vanilla blocks at the same boundary/partition coordinates
and records its own actual shapes, fingerprint and mass. It is **not** the R16
state/shape fingerprint. Stock-only 20/80 m/s results must retain that limitation;
if an integration comparison is needed, run this same vanilla fixture with MS14
present, rather than comparing unlike hulls and assigning blame.

Version-pinned official documentation inspected:
- [Sable 2.0.6 release metadata](https://api.modrinth.com/v2/version/fg9dTRz9).
- [Pinned README](https://github.com/ryanhcode/Sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/README.md): describes intrusive integration and compatibility risks, with Rapier as the default pipeline.
- [Pinned dimension physics documentation](https://github.com/ryanhcode/Sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/wiki/Dimension%20Physics%20Data.md): configurable gravity/drag/pressure, not a documented collision-speed guarantee.

No version-pinned promise of tunneling prevention, unconditional terrain CCD or
an 80 m/s safety guarantee was found in those sources. This does not prove that
no such guarantee exists elsewhere, that 80 m/s is unsupported, or that an
observed penetration is an engine defect. Rapier's presence is not itself a
guarantee for Sable's terrain path. Keep stock Sable untouched and default two
substeps unchanged while establishing what actually happens without MS14.

Interpret the isolated analogue narrowly:
- A valid stock-only penetration shows that MS14 is not necessary for that
  vanilla-hull symptom. It does not diagnose the original furnished hull or
  distinguish an engine defect from an operating limit.
- A stock-only pass does not exonerate or indict MS14. It calls for a matched
  run of this same vanilla fixture with MS14 present before attributing a
  difference to integration.
- A setup/isolation/coverage failure is inconclusive, not collision evidence.
- The lower-speed case is a reference, not a certified speed limit. Preserve
  ordinary failing GameTest outcomes and complete bounded failure observations.

**R30 reached flight:** stock-only 80 m/s reproduced repeated approximately
0.989 m penetration and whole-slab escape at two substeps. The 20 m/s case passed
the strict settling/latch criterion, but had a single-sample 0.155 m maximum;
do not call it zero penetration or a safe-speed guarantee. Full control catalogue
2/2 completed, 1 pass/1 fail, with all dimensions saved and orderly shutdown;
exit1 is the genuine assertion failure. See
`STOCK-ONLY-CONTROL-R30-RESULT.md` for the bound evidence and interpretation.
R28/R29 failed setup before flight and remain preserved. MS14 is unnecessary for
this vanilla-shell symptom; this does not establish an engine defect or exclude
additional local contributions to the original MS14 hull.
