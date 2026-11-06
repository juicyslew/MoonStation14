# Moving-airlock pry: classify unknown power before fixing behavior

R11 completed889 native cases with886 passes/3 failures and normal save/shutdown.
The own-walk pry failed because validation rejected unknown power at tick9590,
one tick after admission9589, before the actor moved. Local delta0 did not prove
the required0.3block own-motion cancellation; LOS, revision2/2, door phase and
body identity remained valid. Evidence:
`build/agent-workflow/shuttle-relocation-sprint/reports/feedback08-cargo-orientation-r11-full-native.log`,
lines26531–26552. The run lacked the existing power-meter acceptance diagnostic.

`PowerRuntime.airlockPowerDiagnostic` delegates to the same `airlockMeterCheck`
used by gameplay. It identifies topology readiness, absent/identity-mismatched,
generation/future/stale samples, and APC identity/breaker/chunk validity. The raw
R11 observation does not select among these reasons. No production power fix is
justified yet.

Next bounded instrumentation reuses `AirlockPryService`'s existing rejection log
at the exact unknown-power decision, plus `MovingAirlockPryGameTests` admission
and immutable terminal witnesses. Do not change readiness timing, retry an
invalidated action, force a power state or weaken any rejection. Keep stable
reason tags and use the existing diagnostic, not a second validation predicate.
Select the three existing pry cases with `shuttle-pry-power-diagnostic` through
the serialized lead; a pass without reproduction is not a cause diagnosis or fix.

The instrumentation is source-complete and independently reviewed, uncompiled
and unrun before submission. Exact plan:
`GATE-PLAN-08-PRY-POWER-DIAGNOSTIC-R18.json` (focused airlock JVM preparation,
then3/891 existing native cases in a fresh default-stock fixture). Author scope:
`build/shuttle-feedback-08-author-pry-power-r18.log`. No new GameTests or power
checker were introduced; the new class tag only selects existing cases.

Preserve unknown/full-power rejection, authority, LOS, root/revision/frame/tool
checks, normal duration/completion and the0.3block own-motion threshold. Native
carrier motion alone must not cancel a valid pry. No Sable changes, stock-setting
adoption or live owner configuration edits belong to this task. Ten substeps
passed the isolated shell bandage comparison; that is unrelated to accepting
unknown power. M1 remains unaccepted; no owner testing is requested yet.

## R18 result

Compilation/focused JVM21/21 passed. Filtered native selected3/891 and passed3/3,
exit0. This is **non-reproduction, not a fix or root-cause diagnosis**; only
diagnostic logging/fixture witnesses changed. Receipt snapshot:
`2d3d51e837b61a40784d2b00eafa74805336bed5f06644ec6d7151f037e40a5a`.
Evidence in `build/agent-workflow/shuttle-relocation-sprint/reports/`:
`shuttle-feedback08-pry-power-20261015-r18-gate-validation.json` and
`feedback08-pry-power-r18-native.log` (SHA-256
`9c2ec71ec2967dafc1ee01b919f326ad0dcb12f2f8a9c8228b0e327caa54fbca`).

Inspect the new admission/terminal evidence and R11's immediate preceding batch
plus power scheduling before choosing a contextual reproduction. Do not infer a
production power fix, weaken safe rejection, or repeat the full suite merely
because these focused checks passed.

R18 admissions all had known zero/accepted samples and ready topology with
matching generations: carry tick67/sample61/gen99; walk377/372/225;
freefall222/217/162. Walk cancelled normally at0.31239423202143585block versus0.3,
terminal power still known zero/accepted, LOStrue and closed door. This is valid
own-movement proof, not a diagnosis of R11.

## R19 bounded context selection

R11 immediately preceded the failing walk with
`registeredWireSessionQueriesAndActsDuringNativeFrameCarry`: wire completed and
owner closed at9526; walk started9526/admitted9589/rejected9590 (raw lines26516–26532).
R19 adds one shared method tag to those **two existing cases**, changing no
behavior or batches. Exact plan `GATE-PLAN-08-PRY-CONTEXT-R19.json` selects2/891 in
a fresh default-stock fixture. R18 JVM checks need not be duplicated for a
selection-only annotation change. Normal native launch compiles the changed tags.

Batch order is not guaranteed: verify actual wire completion/close before pry
start/admission. Reverse, simultaneous or unproven order is inconclusive for this
context. Do not invent a new ordering framework, force power state, or rerun a
full suite without further rationale. Even a passing correctly ordered pair
would be non-reproduction, not proof of a fix or harmless broader context.

R19 completed2/891, both passed and saved/stopped normally. Wire passed/owner
closed at67 before pry admission120; power knownzero/accepted, sample114,
ready topology/gen164. Walk cancelled for own motion0.312394 at122, terminal
knownzero/accepted, LOStrue and closed door. Correct order was observed, but R11
did not reproduce. Raw `feedback08-pry-context-r19-native.log`, SHA-256
`027166c583a072e8cbd6bad03c81d2df3c326a919d3e08566c395a0d957eba59`;
snapshot `3a64bf87ac3250247c827e2c1a263f4e0cc6ebe8db98ccdd78a8de1aae12cc17`.

## R20: one instrumented broader-context check

The only demonstrated failure was in full context. R18 isolated tests and R19
correctly ordered immediate predecessor do not reproduce it. This justifies one
instrumented current full-context native check, not repeated broad validation:
`GATE-PLAN-08-PRY-FULL-CONTEXT-R20.json`, fresh default-two-substep fixture,
expected891 current cases (R11 had889 before the two shell controls). Current
ordering/context is not an exact historical reconstruction. No source changes
or duplicate JVM checks accompany this plan.

Capture the exact meter rejection if it recurs; a pass still leaves R11
unexplained. Preserve known hull failures and every required-case failure,
complete the batch and verify the raw aggregate/save/stop. The parser's missing
all-passed marker is not a timeout. No automatic rerun, speculative runtime fix,
power-safety weakening, readiness claim or owner testing follows this diagnostic.

### R20 result: generation mismatch established

All891 tests completed:884 passed/7 failed, exit1, with normal saves and shutdown.
Pry reproduced: admission9102 knownzero/accepted, sample9097/gen3878, topology
ready/current3878. Validation9104 rejected
`generation=mismatch(sample=3878,current=3879)`; sample door/prototype identities
matched and sample APC was absent. Operation cancelled9104 for operation-invalid;
terminal own-local delta0.312394234969897>0.3, LOStrue, revision2/2, closed phase
and same body. This is safe generation rejection, not a failed movement threshold.
It differs from R11's earlier-before-movement witness but classifies R20's power
UNKNOWN precisely. The event advancing the generation is not logged/attributed.

Raw `feedback08-pry-full-context-r20-native.log`, SHA-256
`8260dd059b03777fe2674a9d878ab6a2a3eedc7b9f59234e68085f867529a18a`,
lines25390–25395 (pry) and25703–25728 (aggregate/save/stop). Snapshot:
`7a371bcceef4e3516639a92f74e0b244aedb4577d84c74dcbe403faf57b4f22f`.

Other failures: four original/new-shell hull impacts;
`boundhumandeparturepreservesrotatingframepointvelocity` (genuine, unresolved);
`singlecabincostenvelope` (explicit expected-substeps property omitted by this
diagnostic plan; setup failure, not a measured cost regression). Preserve all
failures. Future cost-inclusive plans must supply the existing expected-setting
property rather than changing the fixture's requirement. No automatic full rerun.

Next source-only architectural check: the meter rejects generation only **after**
the topology-ready check. Determine whether `PowerRuntime.onTick`'s publication
phase leaves a ready new generation with old samples until the next tick:
`observeApcOutputs` currently schedules topology re-solve after the solve decision.
Do not fix by accepting/restamping old samples; verify existing scheduling,
elapsed-energy accounting, publication-side mutations and event ordering before
changing runtime behavior. Attribution of the generation increment remains unknown.

## R21 fixture premise, not production power changes

Source review confirmed the ready-generation publication interval is a safe
transitional UNKNOWN allowed by existing fail-closed contracts, not a proved
production defect. `observeApcOutputs` can schedule the ready topology's solve for
the next tick. No cadence, billing, freshness or solver change is made merely to
green a movement fixture, and no event advancing3878→3879 is attributed.

The own-walk fixture now establishes an actually disconnected receiver by cutting
POWER_ONE and POWER_TWO through the existing guarded root-UUID/current-revision
`AirlockBlockEntity.applyWire` API, as existing domain GameTests do. This consumes
resolved wire behavior and yields legitimate knownzero `receiver=disabled` power,
not a forced meter or field injection. Both cut flags, disabled state and knownzero
must hold before pry and at terminal. Original movement/duration/authority/tool/
frame/root/revision/LOS assertions remain; other carry/freefall cases retain APC
breaker-zero setup. This changes the test premise and does **not** prove the old
APC-only own-walk scenario or claim to exercise actor wire-UI requests.

Add the existing core35W meter invalidation/refresh test to the diagnostic tag;
select4/891 using `GATE-PLAN-08-PRY-PREMISE-R21.json` in a fresh default-stock fixture.
No new GameTest, production changes or duplicate JVM checks. This core case proves
meter invalidation/refresh, not dedicated active-pry UNKNOWN cancellation. R20's
actual safe cancellation remains recorded. Source review passed; native validation
is still required before claiming this fixture's premise/cancellation proof works.

### R21 focused result

Native4/891 selected,4 completed/passed,0 failed, with fresh fixture setting2,
normal server stop and all dimensions saved. Own-walk admission413, cancellation416:
`reason=movement(distance=0.31239423202143257,threshold=0.3)`, not operation-invalid.
Both wires remained cut; terminal meter knownzero,
`powerDiagnostic=receiver=disabled sample=absent`, door closed. Other carry/freefall
and existing35W meter invalidation/refresh cases passed. Independent read-only
review verified cancellation reason, terminal power, config and shutdown.

Raw `feedback08-pry-premise-r21-native.log`, SHA-256
`f506cff5481dd1e201a866702a410adc707c6b4594a929d73a0a149a14ee64c4`,
lines1316–1322 and1331–1344; snapshot
`e266305ec5d28e715b8d59921a728e872a1146fec1e7ad0796ec9565da41b9de`.
Fixture correction is focused-validated, not full-sprint readiness or owner
acceptance. Original APC-only own-walk scenario and the event advancing R20's
generation remain unproved/unattributed. Production power behavior is unchanged.
R20's rotating-frame departure failure remains a separate active M1 issue.
