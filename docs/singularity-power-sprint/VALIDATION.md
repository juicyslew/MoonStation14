# Validation checkpoints — 2026-10-10

## Combined singularity JUnit gate selection — 2026-10-14

- `test` now supports opt-in `-Pms14IncludeNativeShaderCompile`: it runs both
  ordinary tests and the native shader compile/link tag in one JUnit task. The
  existing `-Pms14NativeShaderCompile` remains native-tag-only; default behavior
  still excludes that tag. This prevents repeated `test` task invocations from
  replacing the XML evidence path before finalization.
- The r2 gate passed focused tests, native shader compile/link, and assemble,
  but two invocations of `test` overwrote the earlier JUnit XML; the evidence
  reader therefore rejected the first check. Its report was rewritten after a
  ready report had been bound, making that review stale. Old r2 records and logs
  are preserved; no freshness checks were bypassed.
- The parent-owned r3 plan combines the test populations into one JUnit check
  and retains the separate assemble check:
  `build/validation-logs/singularity-depth-policy-20261014-r3-gate-plan.json`.
  No author tests or builds were run for this selection-only Gradle change;
  integration lead 05 owns fresh gate execution.

## Documentation-only gate correction — 2026-10-14

- Removed the surviving superseded `.972353` cap/logistic/coarse-grid no-fold
  derivation. Corrected camera-Z ramp wording to state it is not invariant to
  camera rotation and replaced implicit-root wording with actual silhouette,
  fold, and foreground-sampling risks.
- No tests were rerun for this documentation-only correction. The existing
  27-test focused evidence remains at
  `build/validation-logs/singularity-depth-policy-focused-20261014-r11.log`.
- Copied the unchanged integration plan to
  `build/validation-logs/singularity-depth-policy-20261014-r2-gate-plan.json`
  for the parent's new candidate snapshot.

## Post-review API-default and formatting correction — 2026-10-14

- Canonical six-argument `radialDisplacement` now defaults strength to 1; the
  obsolete viewport `dMax` helper is removed. Projection helpers and fragment
  shader were formatted without changing their lens formulas.
- Focused same four JVM suites: **27 tests passed, 0 failures/errors/skips**.
  Log: `build/validation-logs/singularity-depth-policy-focused-20261014-r11.log`.
  Regression explicitly compares omitted strength with explicit 1 and rejects
  the retired .7 default.
- No native GLSL compile/link, assemble, GPU draw/readback, or full suite was
  run; those remain assigned to integration lead 05.

## Uncapped SS14-response / full-resolution depth-policy candidate — 2026-10-13

- Focused JVM command selected 4 existing suites: `SingularityVisualProfileTest`,
  `SingularityWarpFieldTest`, `SingularityDepthShaderContractTest`, and
  `SingularityCameraProjectionTest`: **27 tests passed, 0 failures/errors/skips**.
  Exact successful output: `build/validation-logs/singularity-depth-policy-focused-20261013-r9.log`.
- Coverage includes resolved level-scaled ramp defaults, 20-world-unit support,
  uncapped strength, SS14 threshold transition/center guard, full-screen pixel
  depth response, one-pixel foreground fixture, flat foreground identity,
  background full response, and offscreen admission. This is CPU/shader-contract
  evidence, not GPU rendering.
- Earlier focused attempts failed while expectations still encoded sprite-limited
  support/source averaging; another compile attempt exposed and corrected the
  profile constructor change. A finite-difference seam assertion in r8 was too
  strict for the narrow transition; r9 uses a closer endpoint sample and passes.
  Evidence remains at `singularity-depth-policy-focused-20261013-r1.log` through
  `-r8.log`; no historical artifacts were overwritten. No native shader compile/link, assemble, GPU draw,
  readback, GameTest, full build, or client run was performed by this worker.
- The structured parent-owned gate plan is
  `build/validation-logs/singularity-depth-policy-20261014-r1-gate-plan.json`.
  Actual shader compilation/packaging and connected rendering remain unverified
  pending the integration lead and owner. Foreground color may be pulled from
  the composited scene; original SS14 inner folds, silhouette branch behavior,
  transparent mixed-depth layers, and viewport-edge clamping remain explicit
  risks. No global no-fold guarantee is made.

## Singularity visual-localization, source-tick and operator checkpoint — 2026-10-13

- Focused singularity unit selection: **100 tests, 0 failures/errors/skips**;
  27 XML suites archived at `build/validation-logs/singularity-corrections-20261013-focused-unit-xml/`.
  The quantitative CPU reference at FOV 70°, source depth 12, 1920×1080,
  ideal visibility and background depth 24 measured maximum displacement outside
  each actual resolved square sprite footprint as L1 12.99 px, L2 30.68,
  L3 38.31, L4 41.35, L5 42.89 and L6 42.92. These are CPU/reference results,
  not rendered pixels. L1–L4 exceed their retained previous-reference values
  4.56/11.58/16.27/18.76 px under this test's common setup. The separate narrow
  foreground regression proves a sample-grid miss is possible: a query inside a
  thin foreground patch between the four depth nodes receives the same result as
  background. The field is a coarse depth approximation, not complete object
  occlusion.
- Focused unit first attempt (`singularity-corrections-20261013-focused-unit.log`)
  exposed an incorrect 1×1 viewport identity assertion: the actual edge fade is
  full-strength there and output remained finite/in-bounds at x=0.47064. The test
  now checks the stated bounded contract. Preserved first-attempt XML at
  `singularity-corrections-20261013-focused-unit-failed-xml/`; passing rerun log:
  `singularity-corrections-20261013-focused-unit-r2.log`.
- Native shader compiler negative-control test: **1/1 passed** using hidden GLFW
  OpenGL 3.2; the deliberately reserved `smooth` identifier fails, and the current
  GLSL 1.50 vertex/fragment pair compiles and links. The initial native attempt
  failed because its old negative-control replacement no longer matched the
  shader; corrected the test stimulus only. Failure log/XML retained as
  `singularity-corrections-20261013-native-gl.log` and
  `singularity-corrections-20261013-native-failed-xml/`; passing log/XML are
  `singularity-corrections-20261013-native-gl-r2.log` and
  `singularity-corrections-20261013-native-xml/`. This is compiler validation,
  not framebuffer rendering/readback. The earlier raw-GL draw/readback crash is
  still unclassified; it was not rerun.
- Full `cleanTest test assemble compileGameTestJava prepareClientRun` passed:
  **3,381 tests / 664 suites, 0 failures/errors/skips**. XML archived at
  `build/validation-logs/singularity-corrections-20261013-unit-xml/`; log at
  `singularity-corrections-20261013-full-build.log`. Packaged
  `build/libs/moonstation14-0.0.1.jar` SHA-256:
  `F5DE3C94803A00202FBE1E51C0C16D35BDA6025A7608D1DFFE761EEDD5C17B3E`.
  Shader vertex, fragment and JSON resources match source byte-for-byte (3/3);
  all 56 animated frame PNGs match; packaged language contains the Singularity
  display-name key and exactly matches its source.
- Focused GameTest OR selector requested `singularity-gravity`,
  `singularity-horizon`, `dormant-gravity-pulse`, `singularity-display-name`,
  `startup-source-visibility`, `singularity-visual-catalog`, and
  `reactor-bootstrap`: **17/864 selected/available, 17 passed**. Log:
  `singularity-corrections-20261013-focused-gametest.log`. Full GameTest:
  **864 available/executed, 864 passed**; log:
  `singularity-corrections-20261013-full-gametest.log`. Dedicated successful
  worlds were removed; previous failed worlds/logs were not touched. These full
  results are current-run evidence, not a claim that prior intermittent failures
  were repaired by this run.
- Historical worker compiler/fixture attempts remain preserved and are not
  counted as current failures; the final compile and complete current suites
  above pass. The separate pig native source-clock run passed 13/864 focused
  tests (`singularity-pig-native-20261013-r12.log`); its source-local tick cache
  fixes repeated elapsed sampling without changing gravity equations, radii,
  pulse schedule, guards or death semantics. Native quiet-source steps were
  within 0.239 blocks versus the prior 11.269-block jump. See the existing
  `GRAVITY-PARITY.md` for the worker's detailed parity evidence.
- **Still unverified:** raw GPU pixel readback, connected-client/live visual
  acceptance, and appearance across Fabulous/Fast/Fancy. No screenshot or client
  run was performed.

### Current owner world-lens visual recheck (no screenshot required)

1. Keep camera position fixed and turn your head; verify no unchanged foreground
   copy remains beside a distorted duplicate. Orbit the camera; source art should
   retain its unchanged camera-facing, world-up billboard basis.
2. Check strong continuous response through viewport edges with no fullscreen
   block/edge clamp. Check offscreen affected world geometry within resolved
   support and opaque core occlusion. CPU tests do not prove finite-mesh appearance
   or GPU performance; this remains pending owner review.
3. Hold Shift and look at the entity; its name should read “Singularity.”
4. Optionally verify a quiet source advances its gravity pulse and eligible
   nearby bodies move/contact naturally. Prior accepted PA/R8 checks need not be
   repeated.

## Startup-generator self-occlusion correction — 2026-10-12

- Root cause candidate confirmed in local render composition: startup entities
  spawn at generator block center and the block stays in place. The accepted
  startup model is partial machinery, while its ordinary opaque default state
  supplied a full-cube VISUAL occluder. The source visibility routine correctly
  hit that cube on all nine rays. Applied `.noOcclusion()` only to registered
  startup-generator properties; collision/outline and gameplay/spawn behavior
  are untouched. No generic opaque-block exception or source-warp tuning.
- Focused visibility/shader/projection/warp unit selectors passed (24 tests,
  0 failures/errors). Visibility regression checks source-containing full-cube
  stone as 0 and transparent glass as 1 at six cardinal/pitched views; existing
  full-wall and partial-pillar tests remain.
- Focused GameTest selector `singularity-generator,reactor-bootstrap,
  reactor-models`: **21 selected / 858 available, 21 passed**. The real startup
  fixture asserts registered generator state, unchanged generator-center spawn,
  and source visibility using the resolved level-1 profile: the full-cube
  original-equivalent state yields 0/9 probes while the actual registered
  startup state yields 9/9 at four cardinal and two pitched views. Power/spawn
  integration tests remain in this selection.
- Follow-up isolated unique tag `startup-source-visibility` confirmed actual
  fixture inclusion/execution: preflight **1/859 selected / available**, one
  required test passed. Captured fixture INFO line reports six views, each
  original full-cube **0/9** and actual registered generator **9/9**. Log:
  `build/validation-logs/reactor-warp-startup-visibility-confirm.log`.
- Current `cleanTest test assemble compileGameTestJava prepareClientRun` passed.
  Archived post-run XML evidence at
  `build/validation-logs/reactor-warp-startup-visibility-unit-xml/`: **664
  suites, 3,375 tests, 0 failures/errors/skips**. Full build log:
  `build/validation-logs/reactor-warp-startup-visibility-build.log`.
- Existing opt-in native compiler test: **1 passed** with explicit ErrorFile
  setting (no crash report emitted); log:
  `build/validation-logs/reactor-warp-occlusion-native-compile-r2.log`.
  JAR SHA-256 `B44AEE2F63F509D83B22795D10A2FC62D9B42CDEA4CE4206C1A4D7B74721AD31`.
  Packaged vertex, fragment and shader JSON SHA-256s match source (3/3).
- The raw-GL draw/readback attempt remains a blocker/unverified boundary. Its
  captured foreground output records executor exit `-1073741819` before a
  testcase summary; no matching newly generated `hs_err_pid` file was located.
  We did not infer a buffer-size cause. Removed the unproven crashing readback
  attempt from the test; existing opt-in native compiler negative control passed
  separately. No pixel readback or connected-client GPU acceptance is claimed.


## Singularity GLSL reserved-identifier startup fix — 2026-10-12

- Corrected the GLSL reserved identifier `smooth` to `depthWeight` in the
  depth-weight interpolation only; shader math, fixed-grid bilinear mask,
  single-scene lookup and the .7378 bound are unchanged. Preserved the owner
  startup compiler failure at `build/validation-logs/reactor-shader-compile-owner-failure.log`.
- `SingularityDepthShaderContractTest` now guards against a reserved `smooth`
  declaration. The opt-in `@Tag("native-shader-compile")` test uses the existing
  Gradle `test` task (`-Pms14NativeShaderCompile`) and a hidden GLFW OpenGL 3.2
  context. On NVIDIA GeForce RTX 3060 Ti, driver 610.47, its actual GLSL 1.50
  negative control reproduced compiler error C7537 for `smooth`; fixed vertex and
  fragment shaders compiled and linked successfully. This validates compilation,
  not visual rendering or live-warp acceptance. Log:
  `build/validation-logs/reactor-shader-native-gl.log`.
- Focused shader/projection/warp/visibility unit selection passed. Full
  `cleanTest test assemble prepareClientRun`: **3,374 tests, 664 XML suites,
  0 failures/errors/skips**. Log:
  `build/validation-logs/reactor-shader-full-build.log`. The packaged JAR's three
  shader resources match source byte-for-byte. JAR SHA-256:
  `24AFF3AB24A3C2ACFF8F32B5852124BDBA0C76BAFA1460EC457687FD3CE23A22`.
- The previous full native result **858/858** is historical and was not rerun
  for this lexical shader fix. Connected live-warp visual acceptance remains
  pending.

## R8 multipart/shade regression hardening — final current checkpoint — 2026-10-12

- Tightened `PowerR6MinecraftModelBakeTest`: the 32 power/field/active
  compositions now come from the production `field_generator.json` multipart
  predicates and resolved model resources rather than manually chosen adapter
  filenames. Each state asserts its selected R8 materials, nine unique cuboids,
  and exact source geometry/UV/metadata. All authored cuboids retain source
  shade except the existing activation-lens adapter, which is explicitly
  asserted `shade:false` for both inactive and active lens bindings; the other
  eight elements, including column/rib/shell, retain exact authored shade.
  Existing native R8 yellow/cyan pixel progression checks remain. The test
  reuses the PA test's model/resource resolver. No gameplay, source model, or
  imported texture values changed.
- Focused unit union: **32/32 passed**; full clean unit/build/packaging:
  **3,374 tests, 664 XML suites, 0 failures/errors/skips**. Logs:
  `build/validation-logs/reactor-r8-focused-units.log` and
  `build/validation-logs/reactor-r8-build.log`.
- Focused native `reactor-models,containment,emitter-firing-adapter`:
  **40/40 selected / 858 available passed**. Full native: **858/858 passed**.
  Logs: `build/validation-logs/reactor-r8-focused.log` and
  `build/validation-logs/reactor-r8-full.log`. The successful focused world
  `build/gametest-reactor-r8-focused` was removed after its 40/40 pass. The
  successful final full world remains at `build/gametest-reactor-r8-full`; do
  not remove it because this exact path was also recorded for the earlier failed
  run and no separate preserved failure backup was found.
- The immediately preceding R8 full run had one `mvoutagerestartsfullresolvedchargeinterval`
  failure (power observed at 5000 W and eligible, zero particles; exact state
  was reported in the session output). That run and the successful final rerun
  used the same `build/validation-logs/reactor-r8-full.log` and
  `build/gametest-reactor-r8-full` paths. The first log was overwritten and the
  full world was reused; the current filesystem has only the final successful
  log/world. I searched the managed shell-output directory for the distinctive
  failure diagnostic; no captured stdout file or separate failed-world backup
  was found. Thus the old result is retained as reported historical evidence,
  not as a preserved artifact. Do not infer it harmless from the later pass.
- Final packaged closure remains **289/289** R6 originals, **8/8** R8 approved
  textures, **56/56** singularity frames, **5/5** impact frames, **7/7** PA
  `uvlock:false` blockstates, **3/3** shaders. Current JAR
  `build/libs/moonstation14-0.0.1.jar` SHA-256:
  `C78148040B7DD02B211726E15FEDCD550D14959130BD6ADF06D61EF90DE7653C`.
- R8 owner approval and integration are covered; this is not GPU acceptance.
  The existing activation lens shading adaptation is preserved, not generalized
  into fullbright. Monitor/side-connector and warp/orientation client review
  remain pending.

This checkpoint supersedes the preceding R8 checkpoint for final test source
and native counts. It supersedes the R6-only checkpoint below; all historical
power failures remain recorded rather than presumed unrelated.

## Owner-approved R8 field-generator artwork checkpoint — 2026-10-12

- Targeted model/binding/PA-resource and warp/visibility unit union: **32/32
  passed**. R8 checks verify all eight owner-approved atlas hashes/pointers,
  authored cyan fill progression, independent power/connected lens pixels, and
  the composed nine-cuboid default with one shared authored geometry across all
  32 combinations. The check preserves R6 source pixels and original import
  manifest. Log: `build/validation-logs/reactor-r8-focused-units.log`.
- Full `cleanTest test assemble compileGameTestJava prepareClientRun` passed:
  **3,374 tests, 664 XML suites, 0 failures/errors/skips**. Log:
  `build/validation-logs/reactor-r8-build.log`.
- Focused native selectors `reactor-models,containment,emitter-firing-adapter`:
  **40/40 selected / 858 available passed**. Includes resolved zero-charge
  enabled field tier, one-real-edge R8 tier projection, PA adapter and
  containment checks. Log/world:
  `build/validation-logs/reactor-r8-focused.log` and
  `build/gametest-reactor-r8-focused` (retain pending lead approval).
- One full native run: **858 complete, 857 passed, 1 failed**:
  `mvoutagerestartsfullresolvedchargeinterval`. At failure, power was restored
  at tick 15981, first observed epoch 15984, schedule eligible since 16097 /
  next wave 16217, MV observation 5000 W and demand 5000 W, eligibility true,
  but particles remained 0. Cause remains unclassified, not baseline/harmless.
  The original failure report named `build/validation-logs/reactor-r8-full.log`
  and `build/gametest-reactor-r8-full`, but those same paths now hold the final
  successful rerun. No separate failure backup was found; retain the failure as
  unclassified history without claiming its artifacts are preserved.
- Packaged resource closure: **289/289** R6 manifest originals,
  **8/8** R8 approved textures (matching the frozen R8 import manifest and
  owner approval record), **56/56** singularity animation frames, **5/5** impact
  frames, **7/7** PA `uvlock:false` blockstates, and **3/3** shaders match.
  PA yaw/side-adapter and all-four-facing native baked geometry assertions pass.
- JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `C78148040B7DD02B211726E15FEDCD550D14959130BD6ADF06D61EF90DE7653C`.
  R8 preserves authored shade rather than forcing all elements fullbright;
  pixel-level fullbright is not implemented. GPU acceptance remains pending.
  R8 is integrated/tested, but no claim of accepted connected-client generator
  visibility is made.

This checkpoint supersedes the R6-only continuity checkpoint immediately below
for source/resource counts and JAR hash. That prior checkpoint's two full-suite
failures remain retained history and are not classified as harmless. The
historical PA see-through report below has since been superseded by owner
acceptance after the UV correction.

## Source-visibility and PA-facing continuity checkpoint — 2026-10-12

- Focused units: **30/30 passed** across the requested PA model/alpha/binding,
  warp-field, camera-projection, depth-shader, source-visibility and warp-pass
  protocol tests. Log: `build/validation-logs/reactor-continuity-focused-units.log`.
  Coverage includes native baked muzzle/connector geometry for
  all four root orientations; offscreen projected centers whose influence still
  intersects the viewport; positive Jacobians across synthetic depth steps,
  thin-wire barriers and map-grid seams; and empty/glass, full-wall, partial-
  pillar, unloaded-cell, visibility-cache and paired source-anchor checks.
  The inverse-projection tolerance is derived from adjacent encoded float depth
  values, reflecting depth-buffer quantization rather than fixed projection
  assumptions. Focused unit command completed successfully.
- Full `cleanTest test assemble compileGameTestJava prepareClientRun` completed
  the requested build tasks; **3,372 tests, 664 XML suites, zero failures,
  errors or skips**. Log: `build/validation-logs/reactor-continuity-build.log`.
- Focused native tag union (`emitter-targeting,singularity-containment,
  particle-accelerator-firing,singularity-horizon,reactor-containment`):
  **14/14 selected passed / 856 available**. Log and isolated world:
  `build/validation-logs/reactor-continuity-focused.log` and
  `build/gametest-reactor-continuity-focused` (retained pending lead approval).
- One full native run: **856 complete, 854 passed, 2 failed**. Failures are
  `bootstrapsanddeliversfixturepower` (loaded power fixture did not become
  authoritative by 600 ticks; control observation 250 W, power observation
  0 W) and `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`
  (29,800 W observed, overloadTicks 22, breaker still closed/unlatched).
  Causes remain unclassified; neither failure is treated as harmless or
  baseline. Full log/world retained at
  `build/validation-logs/reactor-continuity-full.log` and
  `build/gametest-reactor-continuity-full`.
- Packaged integrity: **289/289** R6 manifest files, **56/56** singularity
  animation frames, **5/5** impact frames, **7/7** PA `uvlock:false` blockstates,
  and **3/3** singularity shader files match packaged/source bytes. Focused
  native-bake assertions validate the PA screen yaw, opposite side-adapter art,
  and native emitter yaw/connector/muzzle geometry for all four orientations.
- Current JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `5C9936E43ACF6D607CF399A8C2063C41918E35186DDB674EDD4D2334CE7901B0`.
  Owner's latest review says the PA otherwise looks correct after the UV fix;
  monitor and side-connector presentation still need GPU recheck, as do warp
  and orientation. No GPU presentation acceptance is claimed. R8 is owner-
  approved but its import is not complete and is not present in this artifact.

The source visibility traversal now treats native final misses as clear rays,
preserves loaded-chunk admission and VISUAL shapes, and uses the same paired
anchor/projection tuple after null/invalid projection filtering. Gameplay layout,
facing and cadence are unchanged. This checkpoint supersedes the immediately
preceding stair-clearance checkpoint's counts and artifact hash, but its two
unclassified failures remain historical evidence.

## Stair-clearance release checkpoint — 2026-10-12 (current source)

- Focused required union: **14/14 selected passed**, **856 available**.
  Log: `build/validation-logs/reactor-feedback-stairs-focused.log`. The
  successful isolated world `build/gametest-reactor-feedback-stairs-focused`
  was removed after the pass; the failed full world remains retained.
- Full native: **856 complete, 854 passed, 2 failed**:
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` and
  `retaineddeadtangentbodyquietlyreopenseachnormalclose`. The first reported
  29,800 W delivered, overload counter 58, but no trip; breaker remained closed
  and unlatched. The second reported a corpse still at the static face when the
  next close began. Causes remain unclassified; no cleanup or assertion changes
  were made. The previously failing receiver-reconciliation case passed this
  run. Full log/world: `build/validation-logs/reactor-feedback-stairs-full.log`
  and `build/gametest-reactor-feedback-stairs-full` (retained).
- Fresh post-fixture-source `cleanTest test assemble compileGameTestJava
  prepareClientRun`: **3,364 tests**, **662 XML suites**, 0 failures/errors/skips;
  successful. Log: `build/validation-logs/reactor-feedback-stairs-build.log`.
- Root cause for the previously reproducible compact-default reactor failure:
  kit stairs at origin-relative `(4,7,7)` and `(10,7,7)` obstructed the two
  upper-front emitter lanes. Kit clearance now keeps the stair landing below
  those lanes while retaining operator access. The compact startup test uses the
  production service fixture and verifies all eight corners begin uncharged and
  each real swept emitter projectile first contacts its expected generator
  before startup. It does not inject charge or duplicate collision logic.
  Focused/full reactor tests now pass. Default remains compact with the normal
  finite startup sequence (initial Level 0, Level 2 until target, then Level 0).
  Explicit `inactive_wide` remains guarded against automatic start; this does not
  claim automatic wide commissioning.
- Current JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `954269983D6385F7E1B243DE6ED684A9C267F547D4F64757662383C63E2BF3FD`.
  Packaged integrity: **289/289** R6 manifest assets match, **56/56**
  singularity frames, **5/5** impact frames, **7/7** PA `uvlock:false`
  blockstates, and **3/3** singularity shader files are byte-identical to source.
- PA UV-false native adapter evidence passes; this does not establish owner GPU
  presentation. PA/lens/impact client presentation remains pending owner recheck.

This checkpoint supersedes the failed compact-lane checkpoints and prior JAR
hashes below. Full native is not green because of the two failures listed here.

## Release validation — 2026-10-12 (focused blocker; no current full run)

- Full `cleanTest test assemble compileGameTestJava prepareClientRun` passed:
  **3,364 tests, 662 XML suites, 0 failures/errors/skips**. Log:
  `build/validation-logs/reactor-feedback-release-build.log`.
- Focused requested union plus `wide-fixture-command`: **15 selected / 856
  available; 14 passed, 1 failed**. Failure:
  `naturalstartupgeneratessustainedexportpower`. Log/world:
  `build/validation-logs/reactor-feedback-release-focused.log` and
  `build/gametest-reactor-feedback-release-focused`.
- The failed run exercised the **compact default**. Startup ended `FAILED` at
  the field/power/closure deadline before creating a singularity; generator was
  not inert, commissioning target remained 0, fuel remained at baseline, APC
  output/offers were 0, only 4 owned faces/11 edges existed. Two field corners
  in the ordered compact placement list were `0/false` (uncharged/ineligible):
  origin-relative `(5,7,7)` and `(9,7,7)`; the other six were `25/true`.
  An isolated **1/1** `reactor-generation` rerun reproduced the same two corner
  failures, ruling out the focused union as their necessary cause. Fixture
  geometry/cause is not yet established; no geometry, charge, radius or
  production behavior was changed. Isolation log/world:
  `build/validation-logs/reactor-feedback-release-generation-isolation.log` and
  `build/gametest-reactor-feedback-release-generation-isolation`.
- No full native run was started because the required focused union failed.
  The prior full result (**851/855**) belongs to an earlier server-fixture
  revision and is not current-release evidence; its failures remain
  `receiverreconcilesalreadyloadedmissingfootprint`,
  `naturalstartupgeneratessustainedexportpower`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`, and
  `resolvedassemblyprojectseverypartacrossnaturalpowerandstrengthtransitions`.
  The two unrelated power failures remain unclassified, not baseline/harmless.
- The tagged inactive-wide command guard was included in the focused selector;
  this validates its explicit inactive-only scope, not automatic wide startup.
  The PA-focused cases in the union passed, including current adapter UV-false
  native evidence. Direct native UV evidence does not establish connected GPU
  PA/lens/impact presentation; owner recheck remains pending.
- JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `1CFCB809606A6213DEABDD3CAF24F6E9AB51402788D9BDE01D6B0D3794BDE19B`.
  Packaged resource verification: **289/289** R6 manifest asset hashes match;
  **56/56** singularity animation frames, **5/5** impact frames, and **7/7**
  PA `uvlock:false` blockstate files are byte-identical to source. The
  singularity vertex/fragment shaders and shader JSON also match source.

This checkpoint supersedes older wide-default experiments and artifact hashes
for release sign-off. Release validation remains incomplete pending resolution
of the reproducible compact-corner charging failure and a current full native
run.

## Test-only PA epoch follow-up — 2026-10-12

- Closed the final standby-epoch gap in
  `ParticleAcceleratorFiringGameTests`: the callback now asserts captured MV
  demand, enabled state, strength, meter and visuals, and verifies APC/control
  plus power-node identity and BE references remain unchanged. It makes no
  callback-time power-freshness claim. Future MV demand-retirement, topology,
  cadence and startup assertions were preserved.
- `compileGameTestJava` passed; the focused
  `particle-accelerator-firing` selector passed **5/5 selected / 855 available**.
  Logs: `build/validation-logs/reactor-feedback-accepted-pa-compile.log` and
  `reactor-feedback-accepted-pa-focused.log`; successful disposable world is
  retained at `build/gametest-reactor-feedback-accepted-pa-epoch` pending owner
  cleanup approval.
- This is a **GameTest-source-only revision**. No main artifact changed and no
  full native rerun followed it; prior full-suite results remain tied to the
  preceding test source. Direct native UV evidence does not establish connected
  GPU presentation, which remains pending owner review. Last main artifact:
  `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `1063BE17A158CBEDDEE2681BE6A4D49DCFCAD104249A44EB6B19FF135F16752E`.

## Sole-validator final rerun — 2026-10-12 (failed; supersedes prior checkpoint)

- `cleanTest test assemble compileGameTestJava prepareClientRun` was run
  sequentially. The build failed at `test`: **3,363 tests, 2 failures, 0
  errors, 0 skipped** (662 XML suites). Both failures are camera-projection
  expectation mismatches in `SingularityCameraProjectionTest`:
  `reportsOffscreenProjectedCenterForProductionDepthAdmission` and
  `footprintPixelPolicyHandlesOffscreenTinyAndDegenerateViewports`. The later
  artifact-only `assemble compileGameTestJava prepareClientRun` completed
  successfully; this does not turn the unit run green.
- Focused native union: **14 selected / 855 available, 13 passed, 1 failed**;
  `naturalstartupgeneratessustainedexportpower`. At tick 11,209 the generated
  singularity was no longer present, while containment remained closed, all
  emitters powered, the APC reported 12,000 W, lamp lit and collection continuing.
  This is consistent with singularity depletion/dissipation, not an electric
  export failure; the required sustained-generation condition remains unmet.
- Full native: **855 complete, 851 passed, 4 failed**:
  `receiverreconcilesalreadyloadedmissingfootprint`,
  `naturalstartupgeneratessustainedexportpower`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`, and
  `resolvedassemblyprojectseverypartacrossnaturalpowerandstrengthtransitions`.
  The last failure reported APC-on control displaying powered standby while the
  controller was switched off. These failures are unresolved; no assertion was
  loosened and no full-green claim is made.
- Focused/full logs:
  `build/validation-logs/reactor-feedback-final-{focused,full}.log`; native
  failed worlds retained in `build/gametest-reactor-feedback-final-{focused,full}`.
  Unit/build log: `build/validation-logs/reactor-feedback-final-build.log`;
  final artifact preparation log:
  `build/validation-logs/reactor-feedback-final-artifacts.log`.
- Produced JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `9C79C27420F4EC0F8DA9DF8E3F65884205540F6201ABA4349FEA52CBD6D9A5C3`.
  This artifact includes the latest compiled source, but the unit suite failed.

This checkpoint supersedes earlier apparently successful validation counts and
all prior JAR hashes for this source tree. Impact presentation remains
unverified in a connected client; headless tests do not establish PA/lens/GPU
presentation.

## Emitter impact feedback + current sole-validator checkpoint — 2026-10-12

- Final `assemble compileGameTestJava prepareClientRun` passed after particle
  per-impact frame selection and resource-atlas description. JAR:
  `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `004173FE92022E70E8963D1F3B163FF8FEA1E01B8254DB14E2B4392674C25AB4`.
- Full clean unit/build command did not pass: Gradle reports **3,023 tests,
  3 failures, 1 skipped**; XML contains 573 suites, 3 assertion failures plus
  one failed test-worker process record (4 failure entries), 0 errors, 1 skip.
  The assertions are three `SingularityCameraProjectionTest` cases; the test
  worker also terminated with an OpenJDK access violation. Logs:
  `build/validation-logs/reactor-feedback-build.log`.
- Focused native tags (`singularity-entity,emitter-firing-adapter,
  singularity-horizon,reactor-containment,particle-accelerator-firing`):
  **16 selected / 855 available; 14 passed, 2 failed**. Failures:
  `stagesthreeandfourmoveinsideandstagefiveescapesfieldbox` and
  `resolvedassemblyprojectseverypartacrossnaturalpowerandstrengthtransitions`.
- Full native: **855 complete, 854 passed, 1 failed**; failure:
  `stagesthreeandfourmoveinsideandstagefiveescapesfieldbox`. Failed worlds/logs
  retained under `build/gametest-reactor-feedback-{focused,full}` and
  `build/validation-logs/reactor-feedback-{focused,full}.log`.
- Focused emitter resource/profile/options tests passed (4/4); final resource
  includes per-impact five-frame atlas JSON and pixel-checked crops. GPU/client
  impact, PA and lensing presentation remain unaccepted.

## Depth, contact and weak-pull follow-up — packaged; manual acceptance blocked

Historical owner report at this checkpoint said PA nearest faces and ground
underneath appeared see-through. That observation was superseded by the later
owner recheck after the UV correction: the PA otherwise looks correct. The
current remaining client questions are monitor and side-connector appearance;
both require GPU recheck, not a claim that nearest-face/ground rendering remains
broken. The independent outward-face test uses native block-center rotation and
passes for the current PA source models; it remains source/bake evidence, not
proof of GPU presentation. The native UV-false evidence is recorded in the
current continuity checkpoint above.

Scope: depth-aware lensing, generator in-world state art, bolt contact geometry,
weak-pull handling and clear operating instructions. Owner explicitly deferred
smoothing/quality changes. No new terrain/item destruction is included.

- Depth-aware lensing captures depth before translucent blocks and final scene
  color afterward, compares projected
  source depth against source/destination/sample depths, suppresses offscreen
  sources and rechecks the final lookup against every contributing source.
  The live sprite is depth-tested but color-write-only to avoid self-occlusion.
  CPU/shader tolerance now shares the production `DepthTolerance` uniform.
  Native stage ordering is verified from the cached engine sources;
  Fabulous/GPU presentation remains unverified (`DEPTH-LENSING.md`).
- The first human audit omitted a consumed global default. Base friction is
  **2.5**, but `CCVars.Physics.TileFrictionModifier` defaults to **8**, and
  `MovementSpeedModifierSystem` multiplies them: effective friction is **20**.
  The attempted 2.5 local change was reverted, not accepted as
  parity. At 20 Hz, pre-movement linear damping can still erase horizontal
  gravity impulses. Robust defaults to 30 Hz server ticks with 60 Hz physics
  substeps; the port's grounded horizontal motor now composes three friction/
  wish steps inside its 20 Hz tick and accumulates displacement. It still invokes
  native collision once, retaining vertical/step/flight rules. Impulse ordering
  remains pre-locomotion; the exact upstream well-to-controller phase is not
  established. Attraction strength is unchanged. Pure motor tests and owned-body
  fixtures pass; the full native suite is not green (see below).
- Accepted R6 `a1/a2/a3` pixels are identical to `default`. Enabled-at-zero-charge
  feedback is **blocked on updated, accepted art**, not solved by text or by
  borrowing the connected lamp. See `GENERATOR-MODELER-REQUEST.md`.
- Routine successful-toggle actionbar notifications are removed. Generator
  collision uses metal-foot/column bounds instead of the full cell; selection
  keeps a conservative usable outline. Live X/Z bolt contact, charge-once and
  consumption fixtures pass; an upper-cell native clip miss remains documented
  (`GENERATOR-FEEDBACK-GEOMETRY.md`).
- `docs/singularity-operator-instructions.md` now separates startup, upkeep,
  reserve-vs-watts, plasma refueling, deliberate growth and developer checks.

Current packaged checkpoint:
- **3,352/3,352 unit tests passed**, 659 suites, zero failures/errors/skips.
  Compile, GameTest compilation, packaging and client preparation passed.
- Latest PA-focused native run: **5/5 selected passed**, separately **854
  available** (`particle-accelerator-firing`). The earlier PA/contact/gravity/
  movement union passed **27/27 selected**, from 854 available. Earlier broader movement/horizon union:
  **90/90 selected passed**, from 853 before the added contact fixture.
- Full native checkpoint before the latest PA fixture corrections: **851/854
  passed, 3 failed**:
  `realpoweredcontrolemitsthreeparticlesafterresolveddelay`,
  `mvoutagerestartsfullresolvedchargeinterval`, and
  `twowireddebuglampstripandgodarkthenreclose`.
  This run predates the latest PA fixture corrections; a focused pass does not
  make the full-run failures harmless. Failed worlds are retained.
- A separate 31-selected movement-focused run failed
  `fixtureticketskeepbothsidesofsignedchunkedgesentityticking`: actors did not
  naturally tick within 60 ticks despite loaded/entity-ticking chunks. This is
  unresolved, not classified unrelated.
- Final packaged-resource hash verification: **289/289 imported machinery files**,
  **56/56 animation frames**, and **6/6 visual profiles byte-identical to the JAR**.
- JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `4F909BCAE6167E961B93D60046226A454227324C295BB60878B415721B21F048`.

PA fixture corrections retain the changed-demand observation-retirement guard,
15 particles, exact-one startup and real power. The strength-transition test now
establishes a known standby baseline. The five-wave wait permits one scheduled
final wave only after four waves and completed startup with sufficient real
power; profile-speed travel bounds and the original GameTest hard timeout cap
it. It does not chase successive future waves. The restored-power test checks
cadence against the authoritative epoch captured by the actual wave, requiring
that epoch to follow restored power and the first observed eligible epoch,
rather than assuming eligibility remained uninterrupted. The strength-transition
fixture verifies the known 500 W to 5000 W demand change on the same power-node
identity without a topology change; it retries if the standby sample expires
between the readiness predicate and its next-tick callback.

Evidence: `build/validation-logs/reactor-depth-physics-final-pa-firing.log`,
`reactor-depth-physics-final-pa.log`, `reactor-depth-physics-final-emitter-contact.log`,
`reactor-depth-physics-final-pa-movement.log`; full failure artifacts under
`build/gametest-reactor-final-full`, movement failure artifacts under
`build/gametest-reactor-final-focused-r2`. The first 3,346-test/5-failure
checkpoint and earlier runs below are historical, not current acceptance.

Independent source review passes the pre-translucent-block depth capture,
state restoration, source/sample gates, shared motor integration and collision
response, bounded active-resource diagnostics, and final PA fixture authority/
cadence assertions. GPU/Fabulous checks, PA nearest-face rendering and enabled generator
art remain **unaccepted**. Use `PA-RENDER-EVIDENCE.md` and the existing visual
status command for actual-client evidence; no screenshot is required.

## Connected feedback corrections — current automated checkpoint

Owner confirmed manual field/PA/collector activation, singularity creation and
actual power collection. This establishes the working power loop, **not** model,
prediction, distortion or danger acceptance. The owner reported incomplete/
inside-out machinery, invisible collector/canister models, delayed E interaction,
missing initial fields, unclear generator state, PA strength-click closure,
internal equipment names and a static, nonlethal singularity.

Corrections and evidence:
- Owner client log confirms `models/models/block/...` lookups: registration and
  rendering IDs now omit the extra `models/` prefix. End-cap variants and held
  equipment translations are corrected (`MODEL-LOADING-FIX.md`).
- Field off states omitted six physical mesh elements; all 32 indicator
  combinations now retain nine accepted parts exactly once
  (`MACHINERY-GEOMETRY-FIX.md`). Reversed PA faces remain a connected rendering
  question until native winding and GPU checks establish the cause.
- Global unchanged-face suppression prevented initial/relevance snapshots.
  Delivery is now per recipient, includes empty departure snapshots and uses
  shared controlled-body observation. Authoritative toggle feedback distinguishes
  enabled/disabled/connected (`FIELD-SYNC-FIX.md`).
- Dynamic PA/emitter demand changes no longer invalidate physical topology.
  Fixed APC demand stays observable; changed MV demand still rejects stale
  allocation until the existing solver updates it (`DYNAMIC-DEMAND-FIX.md`).
- A verified-hand target miss no longer incurs an unnecessary hand-refresh RTT.
  Real proof/authority failures still invalidate proof. Initial movement/use
  pose ordering remains unresolved (`BODY-USE-TIMING.md`).
- Imported 56 original SS14 animation frames. Live/preview consumers share
  resolved frame timing. Lensing now uses homogeneous projection, the actual
  custom shader, source-calibrated 32-pixels/meter intensity and mirrored sampling
  (`ANIMATED-VISUALS.md`, `ANIMATION-ASSETS.md`). GPU/Fabulous remains unverified.
- Living-body horizon contact now invokes accepted native death, independent of
  radiation PPE. Corpses are retained; terrain, items and recursive container
  consumption remain deferred. Confirmed character death is not yet evidence of
  connected durable DEAD_CLAIM/ghost staging (`HORIZON-CONTACT.md`).
- Reopened pinned ordinary-emitter scheduling: four actual shots, ten-second
  initial delay, two-second shot intervals and random four-to-ten-second burst
  pauses. Previous three-shot/random-initial-delay claims were incorrect
  (`EMITTER-CADENCE.md`). PA timing is separate and unchanged.

Existing gravity consumers remain; source profiles are now resolved before a
mutable pulse sample. Connected pull/reconciliation and the visually short bolt
endpoint remain open. No force boost, fabricated beam continuation, global
double-sided model workaround or terrain destruction was added.

Endpoint research must distinguish target prototypes: pinned `containment.yml`
gives the **containment field generator** a 0.4-radius circle; `generator.yml`'s
0.45-radius fixture belongs to the **singularity startup generator**. Local bolts
currently collide with the native block collider and immediately disappear on
authoritative contact. The inherited upstream `BulletImpactEffect` is a separate
0.25-second unshaded effect (`Projectiles/impacts.yml`); it has not been wired as
an emitter impact presentation here. Neither the generator center nor continuing
the damaging bolt beyond its hit is an appropriate substitute.

Final automated evidence:
- **3,341/3,341 unit tests passed**, 656 suites, zero failures/errors/skips.
  Compilation, packaging and client preparation passed.
- Focused native: **30/30 selected passed**, separately **851 available**.
- Full native: **849/851 passed, 2 failed**:
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` and
  `actualpowerchainprojectsdisplayswithoutchangingtopologyorbattery`.
- Narrow power isolation: **5/5 selected passed**, separately **851 available**;
  both failed full-run cases are included. This does not establish them benign.
- **289/289 original R6 import hashes match**, **56/56 animation frame hashes
  match**, and all **six current visual profile JSONs match the packaged JAR**.

Logs under `build/validation-logs/`:
`reactor-manual-corrections-final-unit-build.log`,
`reactor-manual-corrections-corrected-focused.log`,
`reactor-manual-corrections-full.log`,
`reactor-manual-corrections-power-isolation.log` and
`reactor-manual-corrections-final-package.log`. Successful disposable fixture
worlds were removed; failure artifacts remain. Final JAR SHA-256:
`BB0FEE3D767F291EEDCE145D6BA41A95C5A039A45D86DA7B578360FF81FE93A1`.

Independent source reviews passed for field/demand/hand admission fixes and the
finite native-death path. Animated source/projection/pass integration was
reviewed, but GPU/Fabulous composition remains unverified. Baked normal checks
do not independently prove outward-facing winding or resolve the reported PA
inside-out appearance. Initial movement/use ordering, short visual bolt
endpoints, connected pull and durable corpse-to-ghost handoff remain open.
The full run's failures were an overload counter of 59 without a trip and
missing nearby APC block light; no unrelated power/light changes, assertion
relaxation or deadline extension were made.

The final unit/package run followed a visual-only frame-boundary correction;
the successful native runs were not repeated for that pure timing change.
Earlier counts below are historical checkpoints, not current acceptance.

After Mixin repair, the first executing checkpoint ran **3,340 unit tests** with
**6 failures** and **30 selected native fixtures** with **25 passed / 5 failed**.
Logs: `build/validation-logs/reactor-manual-corrections-{unit-build,focused,package}.log`.
Four horizon fixtures exposed a real integration bug: native `kill()` supplies
`Float.MAX_VALUE` damage, overflowing the finite typed-damage boundary. Contact
must use the accepted native death/lifecycle boundary without fabricated infinite
damage. Other regressions concern the refactored admission guard location,
corrected model IDs, six-face fixture setup, disabled-profile setup, animation
cycle rounding and new inactive mesh coverage. World-global topology generation
cannot be assumed constant across concurrently running fixtures. These were
corrected without relaxing real power, six-face, death or frame-wrap assertions.
Returning to an unchanged field dimension required recipient state invalidation
because the client clears geometry on level unload.

These regressions are corrected: the existing accessor remains accessor-only;
horizon contact sets health to zero and invokes accepted native `die()` rather
than feeding infinite damage into the ledger; dimension changes reset delivery
state. Cumulative animation boundaries normalize representation-scale rounding
and are tested at actual 20-Hz ticks/partial ticks. Invalid per-frame wrap
expectations were replaced by an actual cycle-wrap assertion, not dropped.

## Accepted machinery resource integration — prior automated checkpoint

Current Power R6 acceptance was verified against the asset-workspace handoff,
`user_acceptance.json` and accepted hash inventory. Imported **289 runtime files**
for **11 existing machinery identities**. Independent resource review confirmed
scope, byte equality, provenance and imported inventory; no mixed revisions or
unlisted model/texture files were found. Existing canister R2 and suit R3 imports
were not changed. Live state bindings, native baking and connected presentation
are separate acceptance steps; resource import alone does not close them.

Live bindings and independent source review now pass: all seven PA parts,
standalone emitter, containment generator, startup generator and collector use
the accepted machinery art. Server-derived native blockstates drive PA/emitter/
field indicators; the collector uses a minimal derived update packet, independent
tank/pressure materials and one 0.8-second shell/panel animation. Gameplay power,
fuel, charge, topology and machine identity remain authoritative in their existing
systems. See `MODEL-BINDINGS.md` and `COLLECTOR-MODEL.md`.

Final validation:
- **3,318/3,318 unit tests passed**, 651 suites, zero failures/errors/skips;
  compilation, GameTest compilation, packaging and client preparation passed.
- Focused native: **11/11 selected passed**, separately **847 available**.
- Narrow power isolation: **5/5 selected passed**, separately **847 available**,
  including both failed full-run cases below. This does not prove them benign.
- Full native remains **845/847 passed, 2 failed**; no blind full rerun was made
  after the final collector resource-only correction. That correction passed
  fresh source-pixel, raw geometry/UV, clip-matrix, bounds and native bake checks.
- **289/289 imported hashes match**; the final JAR retains exactly six canonical
  singularity visual profiles, byte-identical to source.

Logs: `build/validation-logs/reactor-model-integration-final-{unit-build,focused}.log`,
`reactor-model-integration-power-isolation.log` and
`reactor-model-integration-full.log` in the same directory. Successful disposable
fixture worlds were removed; failure artifacts remain. Final JAR SHA-256:
`4D9A7AB169F60842A8CD9B2EE751B27E690C0FB04E5365A2FD77A640013B6B3D`.
Connected visual/audio/UI acceptance remains pending; no GPU verification or
full-suite-green claim is made.

First live-binding build passed **3,314 unit tests**, 650 suites, zero
failures/errors/skips. Focused native selected **11/847**, with **8 passed and
3 failed**; see `build/validation-logs/reactor-model-integration-{unit-build,focused}.log`.
The failed fixture assertions used 80 ticks instead of resolved field decay,
tested PA firing immediately after explicitly disabling the controller, and
waited for emitter standby allocation while its intent was already on. Corrected
fixtures retain the zero-charge, real-power and cadence assertions. This failed
checkpoint is not acceptance; corrected checks and the full native run are pending.

Corrected checkpoint: **3,315 unit tests**, 651 suites, zero failures/errors/skips;
focused native **11/11 selected passed**, separately **847 available**. Full
native completed **847/847**, with **845 passed and 2 failed**:
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit` and
`indexedruntimenodesupplierfeedsrealmvnetworkandidentityboundmeter`.
These differ from the earlier two full-run failures; do not assume they are
unrelated or baseline. Logs: `build/validation-logs/reactor-model-integration-corrected-{unit-build,focused}.log`
and `reactor-model-integration-full.log` in the same directory. Full validation
is not green. Failed artifacts are retained; assertions were not weakened.

Collector resource review also caught a partly pre-posed glass pane in the
integration adapter. Both panel materials now use the accepted raw unposed
geometry/UVs and one host clip transform; source PNG pixels remain unchanged.
Swept render bounds include the controls, pressure overlay and all panel poses.
The final checks above cover these corrections, not connected/GPU appearance.

The full-run power failures remain unexplained: the APC reported 29,800 W load
and overload counter 60 without a trip; the indexed receiver got a 5,000 W
callback while its meter and four ports were unknown. Both pass the isolated
selectors `machine-model-regression,power-runtime-node-meter`. No unrelated
power-runtime changes, assertion relaxation or deadline extension were made.

## Second correction batch — current automated evidence

- Fresh build, GameTest compilation, packaging and client argument preparation
  passed: **3,299 units**, 645 suites, zero failures/errors/skips.
- Focused native: **18/18 selected passed**, separately **844 available**.
- Full native: **844 complete, 842 passed, 2 failed**; the terminal summary lists
  `receiverreconcilesalreadyloadedmissingfootprint` and
  `mvoutagerestartsfullresolvedchargeinterval`. Full validation is not green.
- Logs: `capturebuild/validation-logs/reactor-second-client-corrections-final-{build,focused,full}.log`.

The focused run includes actual server visual-catalog loading and projectile
spawn-packet motion restoration. A separate failure-isolation run passed
**18/18 selected**, from **844 available**, including both failed cases' tags;
see `reactor-second-client-corrections-failure-isolation.log`. This suggests a
full-suite interaction but does not prove the failures benign. Assertions and
deadlines were not relaxed. The failed full-run world remains available; the
successful isolated world was removed. Connected UI delivery, field appearance
and live sprite/warp rendering still need recheck.

Second correction batch JAR SHA-256 (before the pointer-window fix below):
`C2BEE27C206E148B2B6AA4E033290A60F66073E751B29B98665A6C83313EF5F2`.
Exactly six visual profile JSONs are at the canonical path in source, processed
resources and JAR; no stale duplicate directory remains. Client runtime arguments
were prepared without changing owner configuration.

### Separate pointer-window crash

`run/crash-reports/crash-2026-10-11_18.45.40-client.txt` identifies a render-thread
`ConcurrentModificationException` in `PointerOverlayRegistry.key`, called by
`StoragePopupScreen.keyPressed`. A key handler can unregister/reorder a window
while the registry streams its live list. This is a reentrant input-dispatch bug,
not evidence of concurrent reactor simulation. Its correction is scoped to shared
window-list dispatch; reactor startup, power values and UI wattage labels are not
being changed as part of this fix. Owner client logs are in `run/logs`.
Dispatch snapshots preserve the event's initial window order and consumption
short-circuit; registration/reordering changes take effect on the next event.

Independent source review passed. Focused checks passed **25/25 selected**;
the full unit build passed **3,303/3,303**, 645 XML suites, zero failures/errors/skips.
Compilation, packaging and `prepareClientRun` passed. Logs:
`build/validation-logs/pointer-window-cme-fix-{focused,full-unit-build}.log`.
Regression coverage includes consumed/unconsumed self-removal, deferred
registration and actual reordering; test isolation restores prior production
registry membership, order and foreground state. No native suite rerun or
connected-client reproduction is claimed; the two full native failures above
remain unresolved.

Pointer-fix JAR SHA-256:
`21EC43E71D28D7D61C314825E1832056265575D68BA0DBA1D74383379FDFB455`.

## Second connected-player recheck — corrections reopened

Owner verified audible sounds and emitter damage, but reported these failures:
PA controls disappear on mouse lock and cannot reliably reopen; dragging to a
screen edge restores an earlier window position; filled fields have an unwanted
color and connect generator block corners rather than predictable centers;
emitter shots last one visible frame and have incorrect orientation; the live
singularity remains invisible. Repeated startup-like audio needs comparison with
the upstream loop and local playback lifecycle.

The automated checkpoint below does not accept those client behaviors. Standing
on the startup generator without being swallowed is consistent with the port's
explicitly deferred matter-consumption scope. Pinned SS14 consumes eligible player
entities inside the live event horizon (level 1 radius 0.5); that is separate from
radiation damage and is not prevented by a radiation suit. No destructive
gameplay change was silently added in this correction batch.

Implemented correction scope: PA replacement-close/open correlation and shared
edge-safe window geometry; one generator-center conversion for field rendering,
collision and failsafe checks; untinted authored field overlay with a softer
violet substrate; emitter flight-aligned art and client trajectory advancement.
The new visual-catalog lifecycle regression exposed six profiles packaged one
directory too deep. Login cleared the initial client bootstrap, and authoritative
sync then supplied an empty visual catalog. The canonical resource-path fix and
a live server-catalog assertion are now in combined validation. The recurring
`singularity_form.ogg` loop is an upstream default, not automatically a bug.

The first full recheck had two failures: a containment fixture still constructed
trajectories from the old integer planes, and the wired-debug-lamp fixture timed
out with an unloaded neighbor chunk and a missing lamp. The former expectations
now use the shared block-center geometry; collision radii were not increased.
Power failure logs are retained. Its intended ticket halo covered the reported
chunk, so no production power change or deadline relaxation is justified by that
failure alone. Both cases are included in the next focused/full recheck.

## Latest automated checkpoint — client corrections and audio, 2026-10-11

- Fresh `cleanTest test assemble compileGameTestJava prepareClientRun`:
  **3,287 units**, 641 XML suites, zero failures/errors/skips; build successful.
- Filtered `reactor-generation,reactor-inactive,pa-client-sync,collector_interaction`:
  **5/5 selected passed**, separately **841 available**.
- Full unfiltered native: **841/841 passed**.
- Logs: `capturebuild/validation-logs/reactor-audio-complete-final-{build,focused,full}.log`.
- JAR SHA-256: `2D9972907A6CE1640D385F0A1A4717C7957E173C5C6666D51E55829943CA3E77`.
  Packaged six singularity images and four fly-by clips match source resources;
  see `reactor-audio-complete-artifact-closure.txt` in the same log directory.
  Exact top-left singularity crops also passed **6/6** pinned-upstream pixel
  comparisons (`reactor-client-corrections-sourcepixelcompare.txt`).

Coverage includes real PA identity update tags/open correlation, final glass
cable-host retention, resolved audio defaults/overrides, committed slot cues,
qualified projectile impacts, local swept fly-by policy, sprite-frame sampling,
filled field geometry and opaque substrate closure. Client launcher arguments
were regenerated. Source reviews passed; connected playback/UI and GPU
compositing still require the concise operator walkthrough.

## Connected-player findings — 2026-10-11, manual recheck pending

Owner observation supersedes the earlier unverified presentation assumptions:
creative names were untranslated, the default fixture was one block too high,
APC breaker labels contained corrupted glyphs, PA controls did not open, rear
cable walls blocked the view, emitter bolts were barely visible, field surfaces
appeared as outlines behind clouds, PA textures showed whole sheets, and the live
singularity was invisible. Applicable machine/projectile audio was also absent.

Corrections are implemented and passed the checkpoint above. A
successful compile or headless run cannot prove connected UI delivery, sound
audibility/lifecycle, or cloud/Fabulous/GPU compositing. Destructive matter
consumption remains intentionally deferred; retaining the inert startup generator
is expected behavior, not evidence that the singularity failed to spawn.

## Pre-correction automated checkpoint — playable fixtures, 2026-10-11

- Fresh `cleanTest test assemble compileGameTestJava`, caches disabled:
  **3,259 units**, 630 XML suites, zero failures/errors/skips; exit 0.
  `capturebuild/validation-logs/reactor-final-build.log`.
- Focused `reactor-generation,reactor-inactive`: **2/2 selected passed**,
  **840 available**, exit 0. `reactor-final-focused.log` in the same directory.
- Full unfiltered native: **840/840 passed**, exit 0.
  `capturebuild/validation-logs/reactor-final-full.log`.
- JAR SHA-256:
  `B74EEFE94563A4CCCF265235AE069128908F58623F03A427935BCA268D2F39EB`.

Both modes share the complete playable builder (machinery, real wiring, access
decks/stairs, finite fuel, suit/separate hood, tools and canister). The inactive
case executes the permission-2 console command and verifies unstarted machinery.
The running case uses the production service job and natural world tick hook.

Measured running startup elapsed **4,726 ticks**; subsequent **6,000 natural ticks**
delivered **12,000 W throughout**. There were **42 positive energy increments**
totaling **581.69997**, plasma decrease **0.47705984 mol**, energy
**133.18127–296.79266**, levels **1–2**. The singularity remained alive; end energy
was **133.18127**. Collector offers varied **75,215.97–348,381.96 W**. This is a
five-minute operational proof, not a guarantee of indefinite energy equilibrium.
Operators retain normal strength and finite-fuel maintenance responsibilities.

Commissioning uses actual Level0 waves for creation, then ordinary Level2 until
the resolved reserve is reached (default 260 energy), then Level0 once. This is
visible fixture-local startup automation, not an upstream admin default or a
continuing governor. Auxiliary HV powers engine/startup controls only; collector
export is isolated and drives a real 12 kW load. No field charge, singularity,
food, allocation or fuel was injected by the acceptance path.

The two successful final fixture directories were removed after retaining
command/server logs. Failed earlier artifacts and owner configuration remain.
Connected-player controls, gravity reconciliation and GPU/model appearance are
still manual acceptance gaps; the sprint is not declared complete.

## Earlier playable reactor checkpoints — 2026-10-11

The user requires both a running power-producing installation and an inactive,
manually startable installation. New fixture sources are under
`ms14/singularity/fixture`; both modes share placement, wiring, and startup logic.

- Registered placement and rotated-emitter checks: **4/4 selected passed**,
  **837 available**; fresh units at that point: **3,259**, zero failures/errors/skips.
- Real eight-emitter containment survived 600 natural ticks. Later assertions
  explicitly scope twelve edges/six faces and verify PA standby.
- Real PA startup and collector export: **1/1 selected passed**, **839 available**,
  `capturebuild/validation-logs/reactor-dynamic-offer-20261011.log`.
  Startup elapsed 2,215 ticks; measured 1,200 ticks at **12,000 W** export;
  plasma decrease **0.03702425956726074 mol**. Energy **118.446..178.450**, level 1.
- That energy decline did **not** prove sustainable feeding. This earlier run
  predates the compact layout and complete-builder acceptance recorded above.

Runtime defects exposed by integration: emitters needed block-state orientation,
rear surface-cable ports (front hosts obstructed muzzles), and conversion of
40 blocks/second to 2 blocks/tick. Collector dynamic offers no longer invalidate
structural topology; normal 20-tick solves read current offers. Actual placement,
profile, and removal notifications retain structural freshness checks.

The earlier 835-test baseline below predates these fixtures. Failed reactor
worlds/logs are retained for diagnosis.

### Five-minute complete-builder checkpoint

The compact full-3D layout and actual service-managed running job passed
**1/1 selected, 839 available** in `reactor-generation`. Measurement: 6,000
natural ticks, 12,000 W throughout, 13 tick-to-tick replenishment events totaling
159.34998 energy, plasma decrease 0.20092154 mol, levels 1–2, energy
28.48639–201.94890. This exposed weak initial-stage maintenance rather than an
electrical failure. A fixed Level1 commissioning interval proved insufficient
in a later run and was superseded by the measured-reserve procedure above.

## Pre-fixture accepted automated checkpoint — 2026-10-11

This checkpoint supersedes the historical counts below, not the outstanding
connected-client/GPU acceptance requirements.

- Fresh full unit/build: **3,259 tests**, zero failures/errors/skips;
  `cleanTest test assemble compileGameTestJava` passed with both caches disabled.
- Focused native: **63/63 selected passed**, from **835 available**, exit zero.
  Log: `capturebuild/validation-logs/subagent-focused-20261011-030107.log`.
- Full unfiltered native: **835/835 passed**, exit zero.
  Log: `capturebuild/validation-logs/subagent-full-20261011-030154.log`.
- JAR SHA-256:
  `080C65F9D4CC9AE148BA28CA77780E4873BF129AA0474ABF491950BCF530099D`.

Accepted suit R3 resources reach the resolved equipment render plan; authored
rendering remains WIDE-only and the hood remains separate with its placeholder.
Accepted canister R2 components consume synchronized profile, pressure band and
actual tank occupancy; baked-model drawing reuses the airlock quad helper.
Plasma's locked appearance is visual-only: access/lock gameplay remains absent.
Asset/resource closure and source reviews pass; headless validation does not
establish model baking, GPU appearance or connected-client packet behavior.

The residency fixture now waits for all four actors' first natural tick in
addition to exact entity-ticking residency, and cleans up only owned actors.
Independent review passes. Production ticket and power admission rules were not
relaxed. Earlier intermittent station mismatch/removal causes are not all proven.
Connected-client PA/collector controls, gravity reconciliation, GPU rendering and
the fully powered emitter/reactor loop remain manual acceptance gaps.

## Historical integration status (failed checkpoint, superseded)

New source includes emitter firing/bolts, field faces and presentation, radiation,
collector fuel handling, singularity energy/movement, PA assembly adapters,
canister controls and radiation PPE. These additions do not establish an
integrated working reactor. PA particle firing/startup, gravity integration,
collector permanent slot release and client/GPU acceptance remain incomplete
or unverified.

Latest inspected integration run:
`build/validation-logs/singularity-integration-source-baseline-r7-20261010-181928-607.log`.
Java and GameTest compilation tasks were up to date; the focused unit run
executed **51 tests, 9 failed** (5 power, 2 armor, 2 field-generator tests).
This is a **failed checkpoint**, not current full-suite evidence. Its status
file's zero parsed-test count is inconsistent with the Gradle log; XML-based
selection/provenance must be reconciled on the next run. Subsequent edits also
require fresh compilation. No new runtime or GPU acceptance is claimed.

Fresh stabilization attempt:
`capturebuild/validation-logs/stabilization-current-20261010-184058.log`
failed at `compileJava` with four PA integration errors (chunk-status import,
boxed demand type and block codec override). Tests did not run; no fresh test
counts are available for this attempt. Corrections to the earlier armor,
field-generator and power tests are source-only until rerun.

After the PA API corrections, `compileJava compileGameTestJava` passed:
`capturebuild/validation-logs/stabilization-compile-r2-20261010-184500.log`.
This establishes compilation only, not passing unit/native tests or reactor
gameplay. The next focused run passed those compilation tasks but failed
`compileTestJava`: five invalid `ResourceLocation` factory calls in armor tests
and one PA test position/direction mismatch. No unit tests ran in that attempt;
test-source API repairs and a further rerun are pending.

The subsequent focused run executed **73 tests, 9 failed**, with Java, GameTest
and unit-test compilation successful:
`build/validation-logs/stabilization-focused-r4.log`. Failures involve armor,
power, field/singularity profile tests, canister codec setup and PA resource
fixtures. This supersedes the earlier no-tests stabilization attempts, but
remains a failed checkpoint. No native regression acceptance is established.

Fresh XML agrees with the latter log: **73 tests, 9 failures, 0 errors**.
Triage established two production power defects: shared APC receiver demand was
computed separately per MV bus, and APCs with unknown output connectivity did
not commit charging from their known MV input. Remaining failures concern test
setup (armor coefficient typo, unpopulated local prototype catalogs, missing
item registry access and working-directory-relative PA resource paths).
Corrections require a new run; these explanations are not passing evidence.

Additional runtime-discovery defect found during fixture repair: the singularity
gameplay resource was under `prototypes/singularity_gameplay`, outside its
declared `singularity_gameplay` resource directory. It has been moved to
`data/moonstation14/singularity_gameplay/singularity.json`. Prototype fixtures
now load bundled resources into local managers rather than assume populated
global server catalogs. The repaired focused/full unit runs are pending.

Latest completed focused result:
`build/validation-logs/stabilization-focused-r5-20261010-184804.log`.
Java/GameTest compilation passed; **74 tests ran, 71 passed, 3 failed,
0 errors, 0 skipped**. Two power assertions and one field-generator assertion
remain to be reconciled; the final cross-bus fixture edit may postdate this
run. Full unit/build and native validation were not started after this failure.

Subsequent reported full unit run: **3,221 tests, 5 failures, 0 errors,
0 skipped**. Failures concern GameTest namespace metadata, creative-tab machine
coverage, two prototype-runtime expectations and client/server classloading
boundaries. `assemble` did not run after the failing test task.
The worker instead used a native `suite-stabilization` filter, reporting
**24/808 selected, 24 passed**; its exact coverage/provenance is being checked.
It is not the requested 74-test focused unit rerun or a full native regression.

The native log is `build/validation-logs/stabilization-focused-r6-20261010-185105.log`,
using isolated `build/gametest-stabilization-r6`. Its `suite-stabilization`
selection covers pre-existing airlock, atmosphere, hands, pulling and power
regressions, not the new singularity/PA/emitter/collector/canister fixtures.
It therefore does not provide native acceptance for the reactor additions.

The five full-unit failure fixes are written, but verification is blocked at
GameTest compilation. Both
`capturebuild/validation-logs/class-boundary-stabilization-20261010-incident.log`
and `build/validation-logs/isolated-compile-20261010-195707.log` report broad
missing main-project symbols during `compileGametestJava`. The isolated attempt
restored `compileJava` from cache. Current main outputs and the configured
`sourceSets.main.output` dependency exist; the actual failing compile classpath
is being diagnosed. Overlapping validation was observed earlier, but is not a
proven explanation for the reproduced failure. No later full-unit/build pass
is claimed.

Forced fresh main and GameTest compilation with both configuration and build
caches disabled subsequently passed. The diagnostic GameTest classpath included
`build/classes/java/main` with **2,704 classes**. No build configuration change
was needed; the earlier missing-class failure did not reproduce under that
fresh compile. A full unit/build rerun is pending.

**Current full unit/build checkpoint:**
`capturebuild/validation-logs/full-regression-fresh-20261010-195943.log`
records successful `cleanTest test assemble compileGameTestJava` with both
caches disabled: **3,221 tests passed, 0 failures/errors/skips**. `assemble`
executed. This supersedes the earlier failed unit checkpoints, not the missing
native/GPU or integrated-reactor acceptance. The actual reactor-fixture tag
union is now queued separately from the unrelated `suite-stabilization` run.

That unit/build checkpoint JAR SHA-256 was
`12B7234C36B9666CCB696E2AAD06ABE2C4ECF263194B58A227FD4AE0B9548A6F`.
Later source fixes require rebuilding; this identifies only that snapshot.

Actual reactor-fixture union result:
`build/validation-logs/native-focused-20261010-200106.log`,
**30/808 selected**. The run exited 1 after four containment fixture failures,
one emitter-bolt fixture failure and one singularity-entity fixture failure,
and a server crash: `Missing resolved damage feedback profile
moonstation14:radiation`. It did not complete, so final pass/failure totals
are unavailable. Failure world/crash artifacts remain in
`build/gametest-tags-fixture-singularity-stabilization`. Full native regression
was not run; radiation feedback and containment failures are under correction.

Containment fixtures used global level-wide counts despite concurrent fixture
boxes. Corrections scope topology to fixture placements and preserve unaffected
faces during simulated chunk unload; cleanup must restore all still-loaded
owners in the simulated chunk. Bolt collision now identifies the hit block
directly, without a neighboring-generator fallback. The initial-energy fixture
now includes continuous first-tick drain. These changes await native rerun.
Pinned radiation damage uses ordinary resisted dose damage without immediate
radiation-specific effects and does not interrupt do-afters; its feedback
profile reuses quiet defaults. Metal-taste/vomit thresholds remain deferred.

The foundation and portable-fuel results below describe older snapshots only.

## Subsequent stabilization checkpoint

`capturebuild/validation-logs/full-regression-fresh-20261010-200930.log`
passed full unit/build validation again: **3,221 tests, 0 failures/errors/skips**.
Snapshot JAR SHA-256:
`4ED32C667D6BE41DE0D528F25CDBBBA74CF051E62139F74577EBB6B2F8CD5A24`.
The next **30/808** native selection failed before completion:
`capturebuild/validation-logs/native-stabilization-20261010-201012.log`.
Reported failures concern wall radiation attenuation, obstructed containment,
bolt charging and singularity drain. The crash was an uncaught fixture
`AssertionError` from the radiation test, not a repeat of the missing-feedback
profile exception. Final native totals are unavailable.

The original failed world is retained as
`build/gametest-tags-fixture-singularity-stabilization-failed-r1`; the second
failure remains at the designated fixture path. Fixture repairs now use proper
GameTest failure reporting and isolated radiation receivers; bolt placement
has been corrected from world-origin to fixture-relative coordinates.
These repairs require fresh validation. Integrated reactor and GPU acceptance
remain outstanding.

After repairing radiation fixture API compilation, full unit/build validation
passed again. The focused native run **completed: 30 selected, 26 passed,
4 failed**. Remaining failures concern initial shielded radiation dose,
collector plasma loss, containment placement replacement invalidation and
singularity walk timing/speed. Bolt charging now passes. The preceding failed
fixture is retained at
`build/gametest-tags-fixture-singularity-stabilization-failed-r2-20261010-201725`.
This is still a failed native checkpoint; full native regression and integrated
reactor acceptance remain pending.

Latest measured native run:
`capturebuild/validation-logs/measured-native-r2-20261010-202230.log`,
**30 selected, 27 passed, 3 failed**, completed with exit code 3.
Replacement and walking assertions now pass. Radiation actually reached the
receiver (typed ledger **9.517264**), but the wait deadline preceded readiness
evaluation at the one-second cadence boundary. Collector readiness incorrectly
accepted the preceding tank-name mutation (revision 1→2, plasma unchanged).
The remaining singularity failure attempted to add a restored Minecraft UUID
while the original entity was still registered. Fixture corrections are in
progress; these diagnoses do not establish a passing native run.

After explicit native armor/toughness removal from the attenuation receiver
fixture, full units passed again (**3,221, zero failures/errors/skips**):
`build-validation-cleanTest-20261010-202700-637.log`.
The exact union completed **30/808 selected, 29 passed, 1 failed**:
`build-validation-gametest-20261010-202746-773.log`.
Radiation now passed; the remaining failure was randomized emitter first-shot
timing (`first shot is randomized within resolved 4-10 second delay: 0`).
Its power-readiness/timing boundary is under investigation. This run is not a
passing checkpoint merely because previously failing fixtures now pass.

## Current green stabilization checkpoint

`build/validation-logs/emitter-scoped-full-20261010-203034.log`:
full unit/build **3,221 passed, 0 failures/errors/skips**.
`build/validation-logs/emitter-scoped-native-20261010-203133.log`:
focused native **30/808 selected, all 30 required tests passed**.
Emitter shot observation now filters by the existing source placement UUID,
uses one world-time clock and unregisters its listener on pass/failure/rerun.
Previous failure world retained at
`build/gametest-tags-fixture-singularity-stabilization-failed-r6-20261010-203133`.
Snapshot JAR SHA-256:
`D99969E22E15DF054667A7355175B1F50DD7EC3E28F1C6DAA345ABA6DB2684AA`.

Full native regression is pending. This green checkpoint validates the current
foundations, not a complete reactor: PA particle production, registered
generator startup, gravity completion, collector release/controls and client
art/GPU acceptance remain outstanding.

## Full native regression result (not accepted)

`build/validation-logs/singularity-stable-native-full-20261010-203221.log`:
**808/808 selected, 805 passed, 3 failed**. Failures were emitter first-shot
timing and the critical airlock fixtures
`stationarycriticalcrushcontactshocksoncepersolidification` and
`criticaltangentcontactreopenswithoutanothershock`.
The emitter recorded first=6388, first observed power=6050 (338 ticks);
the test now observes the authoritative schedule-start epoch after power resets
rather than assumes the first known allocation stayed uninterrupted. Runtime
power eligibility also now consumes the resolved `onWatts()` instead of a
hardcoded 600 W. Singularity fixture cleanup now covers early failure/rerun.
Airlock contact diagnostics are being added; their cause is not established.
These source edits require rerun, and the full native baseline is still failed.

## Accepted stabilization baseline

The subsequent frozen-source runs passed:

- `capturebuild/validation-logs/baseline-unit-20261010-203631.log`:
  **3,221 full unit tests passed, zero failures/errors/skips**, assemble and
  GameTest compilation successful.
- `capturebuild/validation-logs/focused-native-20261010-203711.log`:
  **37/37 selected native fixtures passed** (reactor-foundation tags plus
  stationary airlock contact regressions).
- `capturebuild/validation-logs/full-native-20261010-203737.log`:
  **808/808 full native fixtures passed**.

This supersedes the failed stabilization results above. The two airlock failures
were not independently diagnosed; added diagnostics and a fresh rerun passed,
which does not prove a specific airlock fix or eliminate run-to-run variability.
The accepted baseline includes source-scoped emitter observation, authoritative
powered-epoch timing and failure-safe singularity fixture cleanup.
Next work is registered generator startup followed by real PA particle waves;
the contained reactor loop and client GPU/art acceptance remain incomplete.

## Earlier generator extension review: rejected composition (superseded below)

The initial extension passed **10 focused unit tests** and **9/813 selected
native fixtures**, but is **not accepted**. It registered the startup generator
as a `FieldGeneratorBlock` using the shared `FIELD_GENERATOR` block-entity type
and added PA startup to `FieldGeneratorBlockEntity`. This incorrectly gives
ordinary containment generators singularity-startup behavior and gives the
startup generator containment behavior. Independent review confirmed the issue.
Separate block/entity types were required. The corrected types and negative
composition checks are recorded in the current acceptance checkpoint below.

## Registered generator startup checkpoint

### Earlier PA particle failures (superseded below)

The subsequent particle profile/entity/presentation extension passed **3,226
full unit tests, zero failures/errors/skips**, assemble and GameTest compilation.
Focused native selected **19/816**, with **18 passed and 1 failed** in ordinary
wall penetration/living radiation contact. The fixture did not make its zombie
explicitly unarmored and its delayed stop happened after leaving the target,
so its continuous-overlap assertion was not meaningful.
Source review additionally requires consistent resolved-half-extent broad and
narrow bounds, aligned overlap tracking and correctly ordered squared-distance
contacts. Those corrections and stronger fixture cleanup/re-entry checks are
in progress. The particle extension is not accepted; PA wave production remains
subsequent work. Audio attribution uncertainty is recorded separately.

Registered `singularity_generator` block/BlockEntity startup is implemented on
the focused current snapshot. `compileJava compileGameTestJava` passed, and the
focused generator/profile/startup/singularity/creative unit selection passed
**10/10 tests**. The tagged native union
`singularity-generator,field-generator-adapter-fixture` passed **9/9 selected**
from **813 discovered** in the isolated GameTest directory. It covers registered
startup, configured resolved singularity profile and finite initial energy,
partial-charge NBT reload with placement UUID, threshold-once inert behavior,
top/bottom face failure, actual 200-tick non-extending failsafe and late startup,
creative registration, and existing containment fixtures. Earlier failed run
artifacts remain in `build/gametest-tags-fixture-singularity-stabilization-failed-r1`,
`-failed-r2-20261010-201725`, `-failed-r4-20261010-202458`, and
`-failed-r6-20261010-203133`; the transient current startup attempt timeout was
followed by a passing rerun and is not counted as a green checkpoint.

The fixture reuses the shared eight-corner builder with field adapter tests.
Current containment is refreshed immediately before acceptance; the full 3D
volume must contain the generator center and keep each of its six face distances
within the resolved 16-unit failsafe. This replaces upstream's planar cardinal
rays as the approved 3D adaptation. Generator prototype defaults inherit through
the local typed `base` profile (upstream generator entity itself has no parent).
PA particle production, integrated PA collision delivery, and full native
regression are still pending.

The follow-up architecture correction is now in the snapshot: the registered
`SingularityGeneratorBlock` creates only `SingularityGeneratorBlockEntity` via
`ModBlockEntities.SINGULARITY_GENERATOR`; `FIELD_GENERATOR` and
`FieldGeneratorBlockEntity` are containment-only again. Native checks verify
that the startup entity exposes no emitter-bolt/connection API, the field entity
exposes no PA startup input, and the enclosed fixture retains its 12 edges from
the eight field-corner placements only. After separation, compileJava and
compileGameTestJava passed, the focused unit selection passed **10/10**, and the
tagged native union passed **9/9 selected from 813 discovered**. The startup
fixture uses the new singularity-only BlockEntity while the shared field-box
builder and existing field fixtures remain field-only.

## Accepted PA particle checkpoint; wave validation pending

The corrected particle extension passed **3,226 full unit tests**, assemble and
GameTest compilation. The focused union including damage admission passed
**73/819 selected native fixtures, zero failures**; native log:
`build/gametest-tags-fixture-singularity-stabilization-r2/logs/latest.log`.
Snapshot JAR SHA-256:
`6F2943A9803DB0EEC3436AEB90460331EBEFE7D88D1DC1319F929716AA5E120C`.
Independent source review passed corrected sweep/overlap/contact ordering and
canonical damage admission. `APPLIED` remains the legacy processing receipt;
`applyHealthChangeWithAdmission` uses the existing transaction commit marker.
Rejected positive hits spend particles without discarding them, preserving
special generator/food contacts. Native coverage includes re-entry, grazing,
exact food, nearest-contact ordering and spent behavior. Audio provenance
uncertainty and client GPU acceptance remain unresolved.

PA property ownership is consolidated: control profiles own wave interval,
launch speed and particle-profile reference; projectile profiles own lifetime,
extent, damage and presentation. Missing references fail closed at runtime;
cross-catalog candidate validation is not currently available.
Wave production is wired; atomic three-emitter preflight passes source review.
Shared real-power fixtures passed **3/822 selected native tests**: exact delayed
three-particle waves, MV outage recovery with a fresh charge interval, and no
partial wave after removing an emitter. Cable paths assert actual surface-edge
adjacency and opposing device ports. Observations retain source UUID isolation
and authoritative powered epochs: cadence is asserted within uninterrupted
epochs, not across unrelated level-wide topology invalidation.

The isolated PA-to-contained-generator startup milestone now passes independent
source review and **4/823 selected native fixtures**, all four passed:
`build/gametest-tags-fixture-failed-pa-startup-r6/logs/latest.log` (the retained
directory name is misleading; this log records the passing run).
Full unit/build passed **3,229 tests, zero failures/errors/skips**, assemble and
GameTest compilation: `build/validation-pa-startup-r6.log`.
JAR SHA-256:
`EE2ED5B86F87FF2EBDC2BCA15311EC5E8F28F3E16B3165DA6CCF7918172D08B5`.

The integration uses `pa_contained_clear`, a 32×10×16 air template, explicitly
translated power hardware and an unobstructed field box within its own bounds.
Real emitted particles start exactly one singularity; spawn-profile energy/level
are captured at join before continuous drain. Containment charge is externally
maintained by the fixture; this does not validate a fully powered emitter loop.
The preflight uses the resolved failsafe distance, not zero.
The subsequent full native run selected **823/823**, with **822 passed and one
required failure**: `buttonpressopenslinkedpoweredairlock` reported
`doorPhase=CLOSED cachedPower=true queued=0`. Log:
`build/validation-logs/reactor-extensions-native-full-20261010-1.log`.
The failed world remains at the designated fixture directory; the earlier
fixture was preserved as `-prior-1`. The linked-button failure is under targeted
triage, not established as unrelated or a known flake. Full regression acceptance
is pending. A complete usable reactor is still not accepted: operator controls,
gravity closure, collector lifecycle and client art/GPU checks remain.

### Measured full-suite delivery failures

`capturebuild/validation-logs/solebuilder-fullunit-20261010-231010-361.log`
passed **3,231 full unit tests**, zero failures/errors/skips, assemble and
GameTest compilation. The focused packet/button, APC-overload and PA union
passed **6/6**. Full native:
`capturebuild/validation-logs/solebuilder-fullnative-20261010-231156-189.log`,
**823 selected, 821 passed, 2 required failures**: linked-button delivery and
`poweredalarmqueuedsyncreceivesventstate`.

Button diagnostics establish an endpoint call, not a transport drop: at that
call cached receiver power was true but the fresh allocation was unknown/zero,
and the door remained closed. Deadline power subsequently returned to 20 W.
Delivery-time fresh admission and fixture batch/precondition semantics are under
investigation; no power or packet guard has been bypassed. Read-only graph
frontier and scoped packet-attempt diagnostics now have focused unit coverage.

### Current green reactor-foundation snapshot

`capturebuild/validation-logs/solebuilder-batchfix-native-full-20261010-231853-189.log`
records **823/823 required native tests passed**, unfiltered; completion and
`BUILD SUCCESSFUL` are explicit. Unit XML records **3,231 tests, zero
failures/errors/skips**; latest `test assemble` passed, with assemble up to date.
Current JAR SHA-256:
`419FD6A957A1CCA33D22E74109894DAB55E0B3F33F54AB5E9EA7E1037B7A6C35`.

The positive powered button/alarm workflows now have dedicated native batches.
Their stable-power preconditions and asynchronous delivery assertions remain;
no admission, power or packet guard was changed. The measured button call with
fresh allocation unknown is rejected by `AirlockBlockEntity.activate` as intended.
Batch isolation prevents unrelated topology mutations during these positive
workflow tests; it is not a production topology-recovery fix. Earlier intermittent
HV-frontier and ingestion observations remain incompletely diagnosed.

This accepts the current reactor foundations, **not the complete playable loop**.
Next is usable PA operator controls, then gravity/collector lifecycle completion
and approved art/client GPU validation. Integrated startup still uses externally
maintained containment charge rather than a full powered-emitter fixture.

### Operator and gravity checkpoint (2026-10-11)

Operator integration passed **3,241 full units** and **823/823 native fixtures**;
its connected-client opening/rendering remains unverified. Gravity integration
then passed **3,244 full units**, assemble and GameTest compilation, and the
gravity/PA union passed **7/826 selected, all seven passed**. The native travel
fixture enables AI only for its direct travel call: NoAI setup otherwise makes
the vanilla actor AI-ineffective and unable to integrate the tested displacement.
Real motor-lease exclusion and registered/settled versus detached item behavior
are covered. Configured gameplay profile ID and source phase are synchronized;
prediction uses deterministic nominal source slots, with authoritative late-pulse
differences left to movement reconciliation (connected-client check pending).

Current JAR SHA-256:
`12CF49480FDCD641F983FC1EF8F8608B2567045F4FC25C9580667D89D0140B82`.
Focused log: `build/gametest-tags-fixture-singularity-stabilization/logs/latest.log`.
Full native completed **826**, with **825 passed, one required failure**;
failed artifacts remain in `build/gametest-tags-fixture-singularity-full`.
The beaker timed-ingestion trace records a 5-unit stomach receipt followed by
4.5 units one tick later. Physiology consumption and a receipt-time assertion
are being investigated; do not treat the full suite as green or declare the
failure unrelated without that evidence. Its verbose failure also exceeds the
native writable-book limit and needs bounded reporting.

### Collector lifecycle and receipt checkpoint

Current unit verification: **3,245 tests, zero failures/errors/skips**,
assemble and GameTest compilation passed. The final unit log explicitly runs
`:cleanTest` and `:test` (compile/testClasses are legitimately up to date):
`capturebuild/validation-logs/collector-lifecycle-final-unit-20261011.log`.
Focused native **33/33** and full native **829/829 required passed**:
`build/validation-logs/collector-profile-phase-native-focused-20261011.log` and
`collector-profile-phase-native-full-20261011.log`.
JAR SHA-256:
`82B4F601C2E649F5903E66B21AC305159DB504D0C51B9F0CB63AB163466574D7`.

Collector suspension preserves serialized fuel; permanent owner removal disposes
its slot payload and claim. Survival drops use the standard self-drop loot table
and sanitized block data. Actual block-item re-placement preserves the configured
full-tank profile and initialized flag without recreating starter fuel. Source
review accepts this lifecycle. Player insert/swap/eject and toggle delay remain
unfinished. The stationary-critical airlock fixture now waits for the second
actual closed phase before exact shock/crush assertions, rather than assuming a
fixed delay during unavailable fresh power; production guards are unchanged.

Timed-ingestion receipts use actual committed cents, including puddle transactions
and partial transfers; rollback emits no receipt. Exact dose is checked at commit,
not after physiology. Direct puddle transaction receipt tests do not establish
end-to-end timed-service observer delivery; forwarding passes source review.
Connected-client PA opening, gravity reconciliation, art and GPU checks remain.

### Collector operator primitives (integration pending)

Resolved `use_delay_ticks` defaults to 20 and is consumed by the collector toggle;
its native fixture verifies immediate first use, unchanged deadline on rejected
uses, persisted cooldown and exact T+20 eligibility. Shared hand/slot swap uses
the existing ledger, hand gateway and `RECOVERY_REQUIRED` compensation contract,
with a narrow revision-checked two-claim exchange rather than a parallel inventory.
Source review passes. The helper is not yet collector-routed.

Fresh unit/build passed **3,245 tests, zero failures/errors/skips**:
`capturebuild/validation-logs/sole-validation-recovery-20261011-004804.log`.
Filtered native **219/830 selected, all 219 passed**:
`capturebuild/slot-swap-native.log`. The actual fixture tags are `machine-slots`
and `hands`; toggle-delay uses `collector_interaction` (underscore). This run
included both primitives despite earlier reports naming nonexistent alternatives.
Stale-slot, whitelist-rejection and commit-compensation coverage is being added
with admitted collector routing. No current full-830 acceptance is claimed.

Registration correction: later review found new swap and collector transfer
scenarios were plain helpers without registered `@GameTest` entry methods. The
219-test pass therefore does **not** establish execution of those new scenarios.
Existing helper classes use separate `GameTestEntry` wrappers; wrappers for the
new cases are being added. Toggle-delay is directly annotated and was selected.
Do not infer runtime coverage merely from source compilation or class tags.

The missing wrappers are now registered (total **834**). Their first execution
exposed fixture errors: the root collector profile starts with a tank (use
`NO_TANK_ID` for empty-slot insertion), an occupied hand must be emptied before
placing the whitelist-test apple, and ejection with an occupied active hand goes
to another free hand. Production transfer guards were not relaxed. Corrected
registered coverage passed **17/834 selected**, including swap success,
stale-slot/whitelist denial, rejected-commit compensation and the real collector
typed-tank insert/swap/eject path. Fresh units passed **3,245**, zero failures,
errors or skips. Logs: `validation-collector-transfer-20261011-01.log` and
`validation-collector-build-20261011-01.log`.
JAR: `4838A832C72422CFB13F08835FB263E72484044E6C6858295D6882C8FC4853DB`.
These native fixtures exercise trusted transfer cores, not authenticated connected
request routing. Right-click eject context-verb integration is underway; no full
834-test green claim or connected-client acceptance is made yet.

### Collector context integration checkpoint

Right-click query/eject payloads, canonical body admission, pending-correlation
state and shared-kit popup are implemented. Eject carries a fresh hand proof and
server snapshot placement/profile/slot/ledger expectations; action replies cannot
open a menu. Authority cancellation and owned-popup replacement pass source review.
Common networking stays client-free; the classloading guard excludes only the
actual `radiation/control/client` package. Connected-client interaction remains
manual and unverified.

Current full unit reports: **3,254 tests, zero failures/errors/skips**. Latest
filtered native: **52/52 selected passed**, from **834 available**. Latest full
native: **833/834 passed**; debug-lamp authoritative-power readiness failed after
600 ticks, under investigation. Do not claim a full-834 green baseline.
JAR SHA-256: `3DF66AA1C743CB33035522808F8B2404679DF4425C48AF6EC1269855F8E830BF`.
Failed artifacts are retained in `build/gametest-tags-fixture-current-collector-full`.

PA diagnostics established actor removal with its radiation attachment unchanged,
not ledger erasure. The fixture actor is now persistent; the precise prior removal
cause is not proven. Global Peaceful-difficulty tests run in a dedicated batch and
restore difficulty. Fixture ticket tests retain all signed-edge entity-ticking
checks with a 60-tick bounded startup budget. Both cases pass the latest focused
and full runs; this is snapshot evidence, not proof of permanent flake elimination.
Collector holdover tests wait for resolved toggle eligibility before asserting
unchanged output through update 40 and clearing on update 41. Gameplay values and
fresh-power admission guards were not relaxed. Station actual-state diagnostics
are retained; its earlier lamp mismatch has not been causally explained.

### Current green automated baseline

Fresh full unit/build: **3,254 tests, zero failures/errors/skips**. Focused native:
**53/53 selected passed**, from **834 available**. Full native: **834/834 required
passed**, Gradle exit zero. Logs:
`capturebuild/validation-logs/absoluteagent01-focused-native-022532.log` and
`absoluteagent01-full-native-022605.log`. JAR SHA-256 remains
`3DF66AA1C743CB33035522808F8B2404679DF4425C48AF6EC1269855F8E830BF`.

The debug overload fixture now leases a one-block halo: loaded-graph certainty
requires potential cable-neighbor hosts beyond the hardware footprint. Production
graph rules, allocation and unknown-power rejection are unchanged. PA actors are
persistent, and fixture residency waits remain bounded with exact entity-ticking
predicates. These changes establish the current green snapshot; prior intermittent
station mismatch/removal causes are not all conclusively established.
Successful fixture directories are
`build/gametest-tags-fixture-current-collector-full-agent01-022532` and
`build/gametest-tags-fixture-current-collector-full-designated`; failure artifacts
remain separate. Connected-client controls/popup, gravity reconciliation and GPU
acceptance still require manual validation. Accepted R2/R3 art binding is next.

## Historical foundation checkpoint

## Implemented at this checkpoint

- Six resolved visual profiles, static SS14 artwork, perspective projection,
  client-only visual preview and native scene-snapshot screen-warp shader.
- Rated supplier offers and shared MV consumer allocation in the existing solver.
- Loaded runtime-node discovery, typed ports and identity/demand/topology/freshness-
  bound MV observations; mixed supplier/receiver roles fail closed.
- Existing fixed source behavior and downstream transfer limits retained.

This is **not reactor gameplay**. Collector, portable tanks/canister refilling,
radiation delivery, PA, emitters, containment and singularity physics/energy are
not implemented. A visual anchor is not a server-side singularity entity.

## Automated evidence

- Clean unit/build/GameTest compilation: **3,166 tests**, zero failures/errors/skips.
- Final focused native: **4/784 selected, all 4 passed**.
- Latest full native: **784/784 passed** on a fresh disposable fixture world.
- Independent renderer source review and corrected power-source/MV runtime
  review passed. No client/GPU validation was performed.

Final logs under `capturebuild/validation-logs/`:

- `singularity-foundations-unit-build-final.log`
- `singularity-foundations-focused-final.log`
- `singularity-foundations-native-focused-final.log`
- `singularity-foundations-native-full-r2.log`

**Run-to-run variability remains unresolved.** The initial full run, recorded in
`singularity-foundations-native-full.log`, failed
`receiverreconcilesalreadyloadedmissingfootprint` and
`twowireddebuglampstripandgodarkthenreclose`. The fresh-world rerun passed without
source or fixture changes. This is not a diagnosed fix or evidence those failures
are unrelated to this sprint. Failed logs/fixture artifacts were retained;
successful disposable fixture directories were removed.

JAR: `build/libs/moonstation14-0.0.1.jar`, built after final production changes.
Verified SHA-256:
`2B43175210A15332DF352319BED434EC65A8238195C3945271B76A80F8BC7A1B`.

## Manual visual check

1. In a test world, use `/ms14_singularity_visual_preview 1`; repeat with levels
   2–6 as desired, up to five anchors. At a fixed camera position, turn the view:
   the billboard should stay world-position-facing rather than spin with head-look.
2. Move/orbit the camera position around the source. The sprite should face the
   viewer and keep its image top world-up, including near vertical poles.
3. Check localized lensing just outside the resolved sprite footprint and at
   several projected depths. The 20-world-unit profile cutoff is presentation
   input, not a 20-unit viewer-distance cull; actual support is compact and
   profile/sprite-local.
4. Inspect broad foreground attenuation, scene continuity and offscreen falloff.
   A narrow foreground object can fall between fixed depth-grid nodes, so do not
   interpret this approximation as complete object occlusion or physical optics.
5. Check the current Singularity identity/name. Exercise Shift+Singularity when
   that owner input is enabled; for the preview harness, use
   `/ms14_singularity_visual_reduced_motion`, confirm reduced motion/status, then
   clear anchors with `/ms14_singularity_visual_clear`.

GPU appearance, Fabulous compositing, close-camera behavior and resource reload
remain unverified until actually exercised. Preview commands are client-only.

## Portable fuel checkpoint

Subsequent implemented work:

- One registered plasma-tank item, with resolved empty and filled profiles,
  finite persistent/network-synchronized gas contents, and a flat SS14 icon
  placeholder. Inheritance and live factory consumers are tested.
- Ordinary constructor-created stacks project their resolved empty composition
  without mutating the stack during reads. First gas mutation materializes data
  on a metadata-preserving copy. Independent review of this correction passed.
- A pure pressure-release calculation reuses `GasMixture.splitRatio`,
  `mergedWith` and the existing ideal-gas constant/conversion. This is not yet a
  player-operated refill system.

Latest clean unit/build/GameTest compilation: **3,173 tests**, zero
failures/errors/skips. Unit counts were independently verified from
`build/test-results/test/TEST-*.xml`; the build worker reported successful
`cleanTest test build compileGameTestJava` completion.
Portable-tank native check: **1/785 selected, 1 passed**, in
`singularity-gas-tank-native-fix.log` under the same validation-log directory.
The pure release helper has unit coverage; no canister runtime was present for
this checkpoint. The full native suite was **not rerun** after portable-tank and
release-helper additions; the earlier 784/784 result is historical evidence.

Latest checkpoint JAR SHA-256:
`21738424183536D05AC48B08ADB115158CFECDDD2308128652C93578C2FF6188`.
Earlier artifact hashes above identify earlier snapshots, not this checkpoint.
Tank pressure UI/release, breathing, overpressure destruction, canister controls
and collector use remain deferred. Held-item/GPU presentation is unverified.
