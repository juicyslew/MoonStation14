# Validation status

## Sprint closure snapshot — 2026-10-10

Owner closed the sprint. Final build/unit/GameTest compilation passed **2,979
unit tests**, zero failures/errors/skips. Final APC/model tag union selected
**10/737**, all passed. This is focused evidence, not a full ChemMaster transport
or GPU acceptance claim. Latest full native evidence remains **734/737** with
three unresolved failures; see `../machine-model-integration/VALIDATION.md`.

Final JAR: `build/libs/moonstation14-0.0.1.jar`; SHA-256:
`0295543CB6B3D15AF07CD0456A0D755B7F67002CB4AED77E8601F05ED5C55622`.
This supersedes the historical artifact hashes below. Durable state and deferred
work are indexed in `HANDOFF.md`. Changes remain uncommitted; no commit/push was
requested or performed.

Implementation remains uncommitted. This is not end-to-end client acceptance.

- Final unit/build check: `cleanTest test build compileGameTestJava
  --max-workers=4 --no-build-cache` passed **2,919 tests / 541 suites**, with
  zero failures, errors, or skips.
- First focused native check: `chemmaster,machine-slots` selected **9/717**;
  **6 passed, 3 failed**. Two fixtures requested amounts absent from the UI's
  preset enum; the third attempted ordinary pickup during the canonical drop's
  pickup delay. These do not establish a power failure.
- Corrected filtered native check (`chemmaster,machine-slots,beaker`):
  **10/718 selected, 10 passed, zero failed**, including world-beaker examination.
- Full native check: **720/720 completed, 714 passed, 6 failed**. Failures:
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `livesourcesubstationapcbatteryandlampchain`,
  `obstructionusesexactlytwodoorwaycellsandsafetydeniescloseactor`,
  `liveapcmeterdrivesscheduledtwophasedoorandautoclose`,
  `logpulsedisableslampandrestoresatresolveddeadline`, and
  `crushreleasecompletesnormalopenbeforefastautoclose`.
  The doorway obstruction assertion is confirmed in the preceding merge log.
  The other five failures remain unclassified; diagnostics alone do not establish
  a ChemMaster regression or prove they are unrelated.
- Final snapshot filtered native check: **12/720 selected and completed,
  12 passed, zero failed**. This includes both UI hand/world ejection fallback
  fixtures and world-beaker examination. Examination component defaults now
  retain upstream `exactVolume=false`; BaseBeaker explicitly inherits true.
- Core sprint JAR at that validation point: `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `5C5F726387529F319D0BDF9E935AEEF2816EEFDFD5AE6484FD83079928D1592D`.
  The full native run above preceded the final snapshot validation; it is not
  a clean full-suite result.
- Regression probe (`power,airlock_power_cycle,airlock_log_pulse,
  airlock_crush_cycle`): **53/720 selected, 53 completed and passed**, covering
  all five unclassified full-run failures. They did not reproduce in this focused
  run; that does not prove they are baseline failures or unrelated to the sprint.
  No broad suite repair was performed.
- Spawnable open-air fixture addition: full unit/build/GameTest compilation
  passed **2,919 tests**; focused `chemmaster-fixture` native check selected
  **2/722**, both passed. Tests cover real fresh 250 W delivery, six ordinary
  loose beaker supplies, declared bounds, repeat-build refusal, and pristine-site
  occupied preflight without partial placement. Independent source review passed.
  Latest JAR SHA-256:
  `60690887A1179702DEFB0FE0B609BEA2726342FB8F15A214C978419B96DE14CC`.
  This fixture-only addition did not rerun the full native suite.
- Connected-client controls, window coexistence/mouse lock, authenticated
  successful requests, and multiplayer convergence remain **unverified**.
  See `MANUAL.md`.

## Aim-and-release addition

- Clean unit/build/GameTest compilation passed **2,937 tests**, all passed with
  zero skips. Resolved policy projection tests reuse the live Jug getter's
  projection without constructing items after registry freeze. A disabled child
  retains inherited `unlimited`; an explicit false override is tested separately.
- Initial filtered native check selected **18/727**; **15 passed, 3 failed**.
  Failures cover whole-mixture success, commit revalidation, and unloaded target
  nomination. These failures were corrected and rerun as described below.
- Investigation identified target self-collision in the dump visibility ray and
  incorrect absolute/relative actor fixture coordinates. Fixes preserve the
  **1.5-block** actor-position reach to both entities and independent visibility
  checks, without adding power or server-gaze requirements. Visibility now starts
  at the actor's eye, targets the source's bounding-box center, and accepts the
  intended machine collider only. Machine lookup rejects unloaded chunks before
  reading a block entity.
- Final clean unit/build/GameTest compilation passed **2,937/2,937** unit tests.
  Final filtered native check selected **18/727** (tag union), **18 passed**.
  Full native check completed **727/727**, **726 passed, 1 failed**:
  `livesourcesubstationapcbatteryandlampchain`. This also failed in the earlier
  full run and passed the earlier focused probe; its cause remains unclassified.
  No unrelated suite repairs were made.
- Final JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `46D47BA7AEB03266245CF08598061A2FDE1A36D6B90A493B14484C2DCE4929CC`.
- Final logs: `capturebuild/validation-logs/aim-release-unit-build-final.log`,
  `aim-release-native-focused-final.log`, and `aim-release-native-full-final.log`.
- Initial addition logs are in `capturebuild/validation-logs/`; failed fixture
  artifacts are retained in `build/gametest-tags-fixture` for failure diagnosis.
- Connected-client aim/release, cancellation, rebindings, cursor modes, and
  authenticated multiplayer transfers remain unverified. See `MANUAL.md`.

## Owner presentation feedback follow-up

The owner observed position-dependent clipping, literal reagent-name keys,
oversized flat presentation, unclear controls, and missing beaker fill visuals.
The apparent missing sugar was visible after sorting; client data loss was not
established. Source changes now use toolkit panels/buttons, inline doses, a
half-scale 170×133 GUI-pixel window with five input/eight buffer rows, shared
localized display/sort names, and explicit render/input coordinate conversion.

Beakers now have separate six-tier standard/large fill layers and mixture tint.
The upstream tier mapping is empty=0, nonfull=`ceil(fraction × 5)`, full=6;
existing item property, color, and loose-item renderer paths are reused. Fixture
stock is seven beakers and ten jugs, including a six-species input sample and
sodium/chlorine for a real salt reaction demo. Independent source reviews passed.
Initial clean unit/build/GameTest compilation passed **2,943 tests**, zero
failures/errors/skips. The first focused native run selected **20/728**, with
**19 passed, 1 failed**: fixture preflight correctly rejected the abstract
`basejuice` reagent used in the six-species sample. Stock was corrected to
concrete `milk`; abstract-reagent resolution and preflight were not relaxed.
Final clean unit/build/GameTest compilation passed **2,943 tests**, zero
failures/errors/skips. Final filtered native check selected **20/728** (tag
union), **20 passed**. Full native check completed **728/728**, **727 passed,
1 failed**: `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`
reported unknown APC output/spine node state (`indexedDevices=327`). This failure
also occurred in the earlier full core-sprint run and passed its focused probe;
the current failure remains unclassified. No unrelated power-suite repair was
performed. Client appearance and moved-window controls must be retested; examine
targeting remains explicitly deferred.

Final JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`1EA30F584B45A3427E9675850ACF982B49F7C3579FFD70D63EEF4394653ED532`.
Logs under `capturebuild/validation-logs/`:
`chemmaster-presentation-unit-build-final.log`,
`chemmaster-presentation-native-focused-final.log`, and
`chemmaster-presentation-native-full-final.log`.

Source consumer checks: loose beakers use `LooseItemRenderer`'s current-stack
`ItemRenderer.renderStatic` path. Controlled-hand icons use `ItemUiRenderer`,
which falls back to `GuiGraphics.renderItem` for beakers; `ItemPresentation`
retains the reagent component. These establish wiring, not observed GPU output.

## Second connected-client correction pass

The owner reported remaining clipping at moved origins, empty white liquid,
awkward/hazardous Alt dumping, insufficient dump reach, missing reaction sound,
and unavailable container pouring/drinking. Source fixes now use absolute GUI
scissors for both lists, small tabs/controls, Container labels and overflow bars;
empty models omit liquid layers. The actual scissor contract was verified in
`build/moddev/artifacts/neoforge-21.1.224-sources.jar`:
`GuiGraphics.enableScissor` (line 201) ignores the pose; nested clips intersect
and framebuffer conversion occurs separately.

Shift drag now captures before examine, with safe nominated examination on a
short click. Dump access uses ordinary pickup reach/nearest bounds at both
endpoints, an owner-requested departure from upstream's 1.5-unit limit. Resolved
drink/refill/transfer policies feed shared pouring and timed ingestion; reaction
effects use recipe-specific metadata and emit only after accepted commits.
Semantic review identified a missing reaction step in paired pours; the staged
recipient now reacts before publication and retains its effects until both
commits succeed. A native actual-pour fixture verifies 5u chlorine combines with
5u sodium into 10u table salt. Final targeted source review passed.

- Clean unit/build/GameTest compilation: **2,954 tests passed**, zero failures,
  errors, or skips.
- Filtered native union: **62/731 selected and completed, 62 passed**. Includes
  chemistry, removable slots, container pairs, and timed ingestion.
- Full native: **731/731 completed, 727 passed, 4 failed**:
  `liveSourceSubstationApcBatteryAndLampChain`,
  `obstructionUsesExactlyTwoDoorwayCellsAndSafetyDeniesCloseActor`,
  `liveApcMeterDrivesScheduledTwoPhaseDoorAndAutoClose`, and
  `logPulseDisablesLampAndRestoresAtResolvedDeadline`. Only the obstruction
  failure is established as pre-existing; the other three remain unclassified.
  No unrelated suite repair was made; failed world artifacts are retained.
- Final JAR SHA-256:
  `69B5E9F42D89B932F5FC3BBBE83A160793B669402E5567FECBCCBCC3D000371A`.
- Actual captured logs/results under `capturebuild/validation-logs/`:
  `chemmaster-client-corrections-unit-build-final.log`,
  `chemmaster-client-corrections-native-focused-final.log`,
  `chemmaster-client-corrections-native-full-final.log`, and
  `chemmaster-client-corrections-results.txt`.

No client visual/sound acceptance or authenticated connected-client transfer
acceptance is claimed. Retest the current build using `MANUAL.md`.

Reported Mojang hostname DNS exceptions lack retained full stack traces; no
authentication/network settings were changed. Deferred examine target-priority
work remains untouched.

## Third connected-client correction pass

The owner observed fast-gesture capture rejection, title/tab overlap, insufficient
margins, oversized/misaligned control text, hover in mouse-lock mode, padded
amounts, and buffer rows/wheel interaction encroaching on the border.

Source fixes reserve the compact title's actual GUI height in logical geometry,
use shared scaled-label rendering, suppress pointer interaction when grabbed,
and use panel-interior rectangles consistently for rows, clipping and scrolling.
Exact concise amount formatting is shared through `ReagentUnits.formatCents`
and `formatUnits` across ChemMaster and solution examination.

Fast releases now wait locally for matching Begin acceptance before dispatch,
without a hold-duration requirement. Rejected Begin reasons are retained rather
than masked by a later missing-capture rejection. Pending cancellation/context
and server capture-lifecycle regressions are covered by unit tests. The original
fast-input trigger remains unverified without an incident trace; authenticated
client transport and visual approval remain pending.

Validation caught a real size-budget error: the input panel overlapped the buffer
heading, and the buffer could not fit eight full rows. Final geometry is 338×272
logical pixels (169×136 GUI pixels), with five/eight full interior rows, separate
panel rims and reserved footer space. Independent final source review passed.

- Focused UI/gesture unit tests: **22 passed**.
- Clean unit/build/GameTest compilation: **2,964 passed**, zero failures,
  errors, or skips.
- Filtered native union: **139/732 selected and completed, 139 passed**.
- Full native: **732/732 completed, 729 passed, 3 failed**:
  `onewireddebuglampstaysbelowtripthreshold`,
  `logpulsedisableslampandrestoresatresolveddeadline`, and
  `liveapcmeterdrivesscheduledtwophasedoorandautoclose`. These power failures
  remain unclassified; no power behavior was modified in this correction pass.
- Final JAR SHA-256:
  `6794072E128E1096A4E75377344AB1F7E9CE4ED24FCCBE25FC7D0E9B19E5DA04`.
- Captured logs under `capturebuild/validation-logs/`:
  `chemmaster-third-feedback-unit-build-final.log`,
  `chemmaster-third-feedback-native-focused-final.log`, and
  `chemmaster-third-feedback-native-full-final.log`.

## Joined tabs, alignment and primary-pour dispatch

### Latest header/caption correction snapshot

**Superseded by the Buffer-control snapshot below.**

- Sort is left; Transfer/Discard are grouped right. Right-aligned tabs share the
  title row, using an opt-in shared frame without the redundant title divider.
- Reagent controls/amounts have tighter right spacing. Quantity labels use `u`;
  dose buttons do not. Caption rendering uses an explicit positive-width region
  and the existing inserted-item snapshot, fixing the deterministic negative-width
  truncation. Empty slots still omit volume/capacity.
- Layout tests: **19 passed**. Clean unit/build/GameTest compilation:
  **2,967 passed, zero failures/errors/skips**.
- Filtered native: **23/732 selected, 23 passed, zero failed**. Full native was
  not rerun for these presentation-only changes; the prior 729/732 result and
  its unclassified failures below remain the latest full-suite evidence.
- JAR: `build/libs/moonstation14-0.0.1.jar`; SHA-256:
  `CFB77612A18368CB561BC29E30B859117392E454A12097325483354EE9F2A968`.
- Logs: `capturebuild/validation-logs/chemmaster-header-caption-unit-build.log`
  and `chemmaster-header-caption-native-focused.log` in the same directory.
- Client visual approval remains pending. This does not resolve the missing dump
  capture investigation or establish authenticated connected-client pouring.
- Independent source review passed after correcting long quantity clipping:
  measured text is right-aligned within a dedicated quantity cell, and dose
  buttons stop before it. The counts and hash above are the rebuilt snapshot.

### Previous snapshot

### Latest Buffer-control snapshot

- Sort follows the Buffer heading with a clear gap. Discard ends at the Buffer
  panel's right edge. Dose buttons use the space between their existing left
  anchor and each row's measured amount label, with a small gap; the fixed
  worst-case quantity reservation has been removed.
- Rendering and hit-testing consume the same per-row width/layout APIs.
  Independent source review passed; client visual acceptance remains pending.
- Layout tests: **20 passed**. Clean unit/build/GameTest compilation:
  **2,968 unit tests passed, zero failures**.
- Filtered native: **23 selected, 23 passed**. Full native was not rerun for
  these presentation-only changes; the previous 729/732 result remains below.
- JAR: `build/libs/moonstation14-0.0.1.jar`; SHA-256:
  `4F29CEFB83DF0BDCA91761289799C57161210BB292984A290B9748D7270547AD`.
- Logs in `capturebuild/validation-logs/`:
  `chemmaster-buffer-controls-unit-build.log` and
  `chemmaster-buffer-controls-native-focused.log`.

### Previous joined-tab/pour snapshot

Transfer/Discard now form a left-aligned group; Sort alone uses its normal larger
font. Tabs share a joined pane border/surface with an opening beneath the selected
tab. Visual approval remains pending.

The primary-click request already carried the target UUID. The confirmed pour
blocker was `USE_TARGET`'s strict medical lookup: an unlisted jug/beaker caused
an exception and early DENIED before generic liquid transfer. Optional lookup
now permits the existing generic fill dispatch for nonmedical items; syringe
precedence and genuine lookup-error denial remain intact. A production-dispatch
regression test covers the nonmedical fallback. Connected-client pouring is not
yet verified.

Missing dump capture remains unresolved. Short gesture-token diagnostics now
cover client sends/ACKs/queued flushes/cancellations and server acceptance,
replacement and removal, including specific tick-removal reasons. No capture
authority checks were weakened; diagnosis requires an incident trace.

- Clean unit/build/GameTest compilation: **2,966 unit tests passed**.
- Filtered native union: **138/732 selected and completed, 138 passed**.
- Full native: **732/732 completed, 729 passed, 3 failed**:
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `livesourcesubstationapcbatteryandlampchain`, and
  `crushreleasecompletesnormalopenbeforefastautoclose`. All three remain
  unclassified; no full-suite-green or baseline claim is made.
- JAR SHA-256:
  `CC5FE5B54E7BFB9CE47CEB6AC81A9D7843716461F8B23EC4AABEB2A313314F78`.
- Logs in `capturebuild/validation-logs/`:
  `chemmaster-tab-pour-unit-build-final.log`,
  `chemmaster-tab-pour-native-focused-final.log`, and
  `chemmaster-tab-pour-native-full-final.log`.

Logs: `build/validation-logs/chemmaster-unit-build-final.log`,
`build/validation-logs/chemmaster-final-snapshot-build.log`,
`build/validation-logs/chemmaster-final-snapshot-focused.log`,
`build/validation-logs/chemmaster-native-regression-probe.log`,
`build/validation-logs/chemmaster-native-focused*.log`, and
`build/validation-logs/chemmaster-native-full.log`. Failed native artifacts
were preserved with the full-run logs; the successful focused fixture world was
removed. Prior merge evidence of 707/708 native
passes is historical, not a result for the current implementation.
