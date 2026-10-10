# Full-suite failure repair

Owner reports the vertical model works well and requests repair of the failing
tests, not acceptance of a non-green suite. Final target is all 730 native tests
passing, including the real powered vertical-door fixture.

## Final acceptance — 2026-10-10

**Passed:** full native suite **730/730 selected, 730 passed, zero failed**,
unfiltered, with no skipped fixtures or server crash.
Log: `build/full-suite-repair-native-final-round12.log`.
Combined `test compileGametestJava assemble`: **2,989 JUnit tests**, zero
failures/errors/skips; `build/full-suite-repair-build-final-round12.log`.
Independent final evidence review passed. Artifact hash is in `VALIDATION.md`.
Earlier pending statements below describe intermediate runs, not current status.

Runtime repairs reuse existing graph/meter, quiet activation and scheduling paths,
loaded-only chunk access, and float-aware gas conservation. Fixture repairs reuse
owned region tickets, native sequence scheduling, resolved prototype values and
the live atmosphere singleton. No temporary producer/closed-edge tracing remains.
The accepted visual assets, gameplay payment gates and owner configuration are
unchanged. Successful disposable final/focused worlds and temporary audit tooling
are removed; useful failure logs/worlds remain. No commit, staging or push occurred.

## Work units

1. Diagnose the shared power topology/readiness failures using bounded graph
   progress and invalidation evidence. Fix the demonstrated lifecycle/rebuild
   cause, preserving loaded-only topology and fresh fully paid delivery gates.
2. Diagnose the remaining slip event and dead-body pull admission failures at
   their actual gates. Fix runtime defects or invalid fixture setup as evidence
   warrants; retain dead-body pullability and sliding Touch-dose semantics.
3. Review reuse and regressions; run focused checks followed by full JUnit,
   assembly and the full native suite sequentially in isolated fixture directories.

No skipped tests, forced successful admission/meter readings, weakened assertions,
blind timeout/budget increases, or owner launch/configuration changes. Temporary
diagnostic instrumentation must be removed or justified as useful existing-service
diagnostics. Preserve failure evidence and document the established causes, fixes
and final counts. Visual implementation and accepted asset bytes remain unchanged.

## Verified parity constraints

Upstream checkout `c9df5ef5d675b0d1d226828bddf6b78c28502d91` confirms:
- `BaseMob` supplies `Pullable` to Human; death does not remove it. Critical/Dead
  pullers stop their outgoing pull, without making dead targets inadmissible
  (`Shared/Movement/Pulling/Systems/PullingSystem.cs`, mob-state action gates).
- Super-slippery contact still raises `SlipEvent` for an already knocked-down or
  sliding target. Puddle Touch rejects already-sliding targets before chance or
  solution splitting (`Shared/Slippery/SlipperySystem.cs:98–149`,
  `Server/Fluids/EntitySystems/PuddleSystem.cs:246–268`).

Diagnostics enrich fixture failure output and expose bounded graph progress; they
do not authorize gameplay or prove a root cause by themselves. Empty-chunk graph
no-op optimization remains pending full-run evidence; its boundary tests preserve
loaded-empty removal/reload and fail-closed UNKNOWN frontiers.

## Diagnostic evidence and repair direction

`build/power-readiness-diagnostic-full.log`: 726 selected, 683 passed, 43 failed.
At the vertical fixture deadline, only 361 cable nodes were indexed, but graph
revision had reached 14,229 and connectivity work kept restarting. Unrelated
loaded-space churn must not restart cable connectivity; relevant boundary changes
must still update UNKNOWN knowledge and invalidate sample freshness. Early chunk
load events are not proof of FULL-cache readiness.

Pull diagnostic `rayPoint=null` was caused by aiming above prone interaction
bounds. Its conditional `selectionChunksLoaded=false` did not itself prove a
missing chunk. Native physics fixtures nevertheless need explicit bounded
entity-ticking residency, without borrowing or releasing another owner's tickets.
Use exact engine ticket ownership and cleanup on test completion/failure/timeout.

The ventilation fixture independently treated finite-room readiness as power
readiness and used absolute follow-up times after an asynchronous wait. It now
awaits actual paid meters within the original deadline and schedules later phases
relative to successful admission. Final combined native validation remains pending.

## Validation progression

- Completed meter-diagnostic run: **727 selected, 712 passed, 15 failed**,
  `build/repair-meter-diagnostic-full.log`; vertical-door fixture passed.
- Round 2 selected 727 but crashed in the native scheduled-task iterator:
  **593 passed, 7 failed, 11 running, 116 unstarted** at the final status bar.
  Evidence: `build/full-suite-repair-native-round2.log` and retained fixture crash
  report. This is not a completed suite result.
- Shared GameTest-only polling now uses native sequences and a next-tick task
  bridge, avoiding insertion into the task map during its active iteration.
  Focused stress test: **1/728 selected, 1 passed, zero failed**,
  `build/repair-polling-focused.log`.
- Latest combined build (`build/full-suite-repair-build-round3.log`) passed;
  prior combined JUnit count is **2,987 passed, zero failures**. Full native
  acceptance is still pending. No gameplay gates or accepted asset bytes changed
  to obtain these passes.
- Round 3 completed: **728/728 selected, 719 passed, 9 failed**,
  `build/full-suite-repair-native-round3.log`. No scheduling crash; vertical-door
  and body/slip fixtures passed. Some elevated power fixtures wrote beyond their
  declared template bounds; these now use `power_receiver_tall_clear` (16×13×16).
  `power_overload_clear` is now 16×13×18; `apc_debug_fixture` is 12×4×12.
  These are x/y/z dimensions decoded with Minecraft NBT, not raw byte offsets.
  Declared runner reservations must cover actual fixture writes; disappearing
  nodes in concurrent runs must not be repaired by bypassing power gates.
- Round 4 completed: **728/728 selected, 714 passed, 14 failed**,
  `build/full-suite-repair-native-round4.log`. Exact disposal guard diagnostics
  show renewed topology invalidation, not an entity/prototype identity mismatch.
  A producer-tracing focused run passed **3/729 selected, 3 passed, zero failed**;
  the trace therefore needs the full shared-world run to establish its cause.
  Temporary producer tracing will be removed after diagnosis.
- Producer full run: **729/729 selected, 721 passed, 8 failed**,
  `build/invalidation-producer-full.log`. The rolling trace established repeated
  unload/load/onLoad/setRemoved cycles for orphan airlock chunks. These advanced
  the world topology generation four times per tick, invalidating previously paid
  disposal samples and preventing consecutive paid vent intervals. The fix must
  stop unintended chunk loading, not suppress legitimate freshness invalidation.

The concrete lifecycle hazard is `AirlockBlockEntity.setRemoved`: its endpoint
retirement check reads `ServerLevel.getBlockState` after a chunk has left the FULL
cache. That lookup can reload the chunk being removed. Classification must use
already-loaded chunk state, treating an absent FULL chunk as unloaded, while still
retiring an actually broken/replaced airlock in a loaded chunk.

Airlock and air-alarm callbacks now follow that loaded-only classification; review
passed both teardown paths. Power fixtures also hold uniquely owned region-ticket
leases for their whole circuit, released on test pass/failure, rather than relying
on the runner's root-chunk ticket. The temporary global-stability diagnostic test
was removed because unrelated legitimate fixture setup could invalidate its
assertion; the actual unload regression replaces that diagnostic role.

The lifecycle full run completed **729/729 selected, 728 passed, one failed**
(`build/full-suite-repair-native-lifecycle.log`). Unload/reload identity and fresh
power recovery passed; all original airlock, physics and power tests passed.
The last failure is the ventilation demo's release phase. The alarm-preset
hypothesis was rejected by direct source verification: local FILTER is a scrubber
mode, and membership sync does not reapply a preset. Actual paid-work, pipe-port
and transfer preflight diagnostics must establish the remaining cause. Producer
tracing has been removed from source; clean final build and full native acceptance
remain pending.

The vent endpoint diagnostic found an empty outlet node despite 0.4 mol Plasma
stored elsewhere in the same 11-node component. `PipeGasLedger.split` uses float
shares, but batch commit still used double-level conservation precision and
rejected the equalization. Batch validation now reuses `PipeGasTransfer.close`'s
existing bounded float-aware comparison. Pinned SS14 gas arrays and arithmetic
are float-backed; eight ULPs is a local tolerance, not an upstream exact value.
Ownership/version checks, atomic commit and rejection of material gas or energy
creation must remain covered by regressions.

The focused demo passed **1/729 selected, one passed, zero failed** after the
float-aware batch fix. The clean build passed **2,989 JUnit tests**, zero failures
or skips. The subsequent full run crashed in the native task-map iterator without
a reported test failure (`build/full-suite-repair-native-final.log`); it is not a
completed pass. Remaining legacy recursive/nested fixture callbacks are being
migrated through the existing safe scheduling bridge. Production tracing remains
removed; no engine patch or gameplay workaround is used.

A disposable Javac AST audit parsed 287 fixture files and identified 86 remaining
task-phase registrations plus 27 manually confirmed unsafe callback sites.
These are being migrated by exact invocation prefix through the shared bridge;
main/prequeued and native-sequence registrations remain native. The source audit
also checks indirect helper callbacks rather than treating a focused stress pass
as proof of complete migration. Expanded stress coverage passed **2/730 selected,
two passed, zero failed** (`build/polling-exhaustive-focused.log`).

The audited full run completed **730/730 selected, 718 passed, 12 failed**
(`build/full-suite-repair-native-audited-final.log`), without an iterator crash.
Remaining fixture corrections cover native floor placement, positive slip-contact
overlap, status expiry timing and post-contact observation. Further focused work
found `onEachTick` registrations inside readiness callbacks also mutate the native
task map; those now use the shared sequence-phase registration helper. The finite
room endpoint fixtures now have an isolated batch that restores their global
vacuum configuration instead of leaking it into subsequent tests. Native
validation of these last changes remains pending.

Focused callback/unload union: **4/730 selected, three passed, one failed**
(`build/final-callback-focused.log`). Blood ingestion and both scheduling stress
tests passed without a crash. The unload test proved actual cache eviction and
identity preservation, then lost residency during its explicit reload's power
solve. It now acquires a separate owned lease for that manual-reload phase;
the first lease remains released throughout the actual unload proof.

The next full run completed **730/730 selected, 719 passed, 11 failed**
(`build/full-suite-repair-native-final-round2.log`), without a scheduler crash;
blood ingestion, vent flow and prior slip fixes passed. Remaining setup fixes
include settling native actors before movement ownership pauses their physics,
straining a tether beyond its resolved slack without breaking reach, waiting for
all four signed boundary cells to become entity-ticking, and measuring clicked
sealed-record deltas rather than the entire shared chunk ledger. The new unload
proof flushes normal queued saves once in its isolated fixture batch before
observing eviction; production lifecycle code remains loaded-only.

Closed-edge diagnostics established a real missed request: qualifying corpse and
Critical actors remained tangent, but quiet bump admission saw a power generation
mismatch. The request is now deferred within one existing paid-solve interval,
using normal contact, phase, bolt and fresh-payment gates; it does not replay
electrical contact. This bounded delay is a Minecraft meter adaptation, not an
SS14 cadence claim. Focused airlock union improved to **9/730 selected, eight
passed, one failed**. The remaining crush hold assertion used raw feet Y across
native posture height changes; the collision-box center is the appropriate
post-crush no-ejection reference, with health, damage and collision checks retained.

The next full run completed **730/730 selected, 724 passed, six failed**
(`build/full-suite-repair-native-final-round3.log`). Corpse/Critical quiet recovery
and crush hold/reopen passed. Follow-up corrections target native actor residency
and tick progress, the unmarked movement control's incorrectly disabled vanilla
AI/travel, native log-deadline observation, and unload proof using actual FULL
status plus detached controller state rather than null lookup alone.

The next full run completed **730/730 selected, 726 passed, four failed**
(`build/full-suite-repair-native-final-round4.log`). The jug scene now reserves
and clears its actual native movement corridor; Critical contact closing waits
for current paid admission. Door-gas diagnostics verify inventoried finite cells
and report operation-derived conservation bounds. The unload regression still
reports an actually FULL, non-entity-ticking remote chunk after ticket release;
this is being traced rather than treating inactive ticks as proof of unloading.

Focused connected-airlock checks passed **12/730 selected, all 12 passed**
(`build/tangent-gas-fix-focused.log`); native face placement now corrects bounded
ULP overlap without confusing safety obstruction with tangent contact. Closed
portal cells are not required to have fabricated live finite claims before opening.
The remote unload/reload proof passed **1/730 selected, one passed**
(`build/nonforcing-power-focused.log`) after periodic power-device solving and APC
observation reused the existing non-forcing FULL-chunk lookup/direct block-entity
access. Level-level getters could keep idle indexed power chunks resident.
Latest combined build passed **2,989 JUnit tests**, zero failures or skips
(`build/full-suite-repair-build-final-round5.log`); final full native acceptance
remains pending.

The next full run completed **730/730 selected, 723 passed, seven failed**
(`build/full-suite-repair-native-final-round5.log`). The door-gas diagnostic showed
42 mol initial nitrogen becoming 107.6825 mol plus 17.4599 mol oxygen: exactly two
ambient portal-cell inventories, not rounding. That fixture now uses the live
atmosphere singleton in its own vacuum-configured batch, with restoration, instead
of a private graph competing over the same chunk gas store. Paid-ventilation
fixtures likewise explicitly own their batch configuration and residency, rather
than depending on configuration leaked by earlier tests. Native status expiry is
observed in actual living-entity ticks. Full acceptance remains pending.

The subsequent run was interrupted by a mistyped large-template resource ID,
not a completed suite result (`build/full-suite-repair-native-final-round6.log`).
Both affected annotations now reference the existing `power_receiver_tall_clear`
resource; compilation passed. No duplicate structure was introduced.

The next full run completed **730/730 selected, 728 passed, two failed**
(`build/full-suite-repair-native-final-round7.log`), both elastic-pull fixtures.
Their async tick observer now uses the shared safe registration; the corpse-pull
scene reserves and clears its full owned corridor and stops directional input
before leaving its support. Both passed the worker's focused run. That broader
17-test selection reported one blast-planning failure, still requiring full-run
verification rather than being dismissed as unrelated.

Another full run completed **730/730 selected, 728 passed, two failed**
(`build/full-suite-repair-native-final-round8.log`). All elastic-pull and blast
tests passed. The lamp fixture now waits for complete known source connectivity
before observation, since APC battery payment alone did not establish its HV
source path. The drying sequence's not-ready callbacks now throw retry assertions
instead of returning and advancing with an unset timestamp; its existing two
20-tick bounds remain unchanged. Focused drying/residency checks passed.

The next full run completed **730/730 selected, 729 passed, one failed**
(`build/full-suite-repair-native-final-round9.log`). The remaining heavy-overload
fixture timed protection from one output sample despite freshness interruptions.
It now observes the existing native protection counter and actual trip in an
isolated circuit batch; intentional zero output after trip no longer resets its
measurement. The read-only immutable protection getter introduces no new state
or protection rules. Trip, manual reclose, restoration, cable cut and stale-sample
rejection remain asserted. Focused heavy-circuit validation passed; full acceptance
is pending.

The next full run completed **730/730 selected, 726 passed, four failed**
(`build/full-suite-repair-native-final-round10.log`). The heavy overload passed.
Three paid-ventilation setups sampled ambient gas before deferred finite ownership
had committed; readiness must check both actual finite claims. The animation
fixture now derives stationary settling from native walk-animation decay rather
than assuming a fixed callback delay is enough. These follow-ups remain under
validation; no gameplay assertion is waived.

Paid-ventilation setup is now callback-based: both actual finite claims must
commit before gas seeding and test observation. The synthetic future-clock loop
was removed; the live singleton progresses its normal work queue. Focused checks
passed **3/730 selected, three passed**. Final full-suite validation is pending.

The next full run completed **730/730 selected, 729 passed, one failed**
(`build/full-suite-repair-native-final-round11.log`), only stationary owned walk
animation. Owned animation advances through accepted harness steps, not cancelled
vanilla travel. Its stationary phase now supplies real zero-input harness steps
and checks their zero accepted displacement while the existing animation update
decays naturally. Paid wait budgets are capped to the existing overall deadline,
reserving breaker-observation time; finite-ownership setup remains bounded at 40.
