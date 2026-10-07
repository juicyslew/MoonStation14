# Visible Timed Interactions + Ingestion

**Owner-closed October 4, 2026.** Read [HANDOFF.md](HANDOFF.md) for the accepted
state, architectural invariants, latest validation and remaining gaps. The state
and run history below are historical; no commit or push is implied by closure.

Latest connected follow-up: [CONNECTED-02.md](CONNECTED-02.md) records Alt+E input
routing and blood-digestion damage evidence. [CONNECTED-01.md](CONNECTED-01.md)
records client loose-item interpolation, progress placement/direction and oral
bicaridine evidence.

## Goal and scope

Deliver a reusable, visible timed-interaction foundation and use it for held
drinking and puddle sipping. Resolve pinned SS14 ingestion timing, repetition,
progress presentation and cancellation before choosing runtime fields.

The loose-object foundation and hands/inventory handoffs are prerequisites.
Its temporary immediate puddle sip must not become an invisible delay: timing
and progress are introduced together. Current input routes, whole-stack
ownership, storage windows and reagent conservation remain intact.

Construction, clothing, generic notification redesign, active-object performance
and unrelated power/ventilation gameplay repairs are outside this sprint.

## Ordered work

1. Audit upstream prototype inheritance and timed/ingestion systems; map local
   authority, transaction, lifecycle, presentation and fixture seams.
2. Establish the shared timed-operation contract, typed resolved ingestion data
   and server lifecycle with focused unit tests.
3. Integrate authoritative ingestion completion/repetition and visible client
   progress/cancellation through the existing controlled-body input boundary.
4. Verify live consumers, concurrency, interruption and conservation; run tagged
   fixtures, the unit suite and a full server regression run sequentially.

Baseline follow-up evaluates the recorded power/ventilation fixture failures
separately. Do not claim those are preexisting without matching evidence, or
make unrelated runtime changes merely to obtain a green suite.

## Acceptance

- Relevant pinned SS14 full parent chains, component defaults, consuming systems,
  repeat rules and progress assets are verified and cited; departures are explicit.
- Live gameplay consumes resolved local prototype data, including inheritance.
- A shared timed operation has visible progress and tested cancellation semantics;
  client presentation never grants mutation authority.
- Held and puddle ingestion preserve exact ownership, eligibility, source/stomach
  conservation, compensation and commit-dependent cleanup throughout interruption.
- Repetition follows established upstream rules, not an assumed universal
  "drink until stopped" behavior.
- Changing control/body/source/target and two actors sharing a source are tested
  according to the verified contract; no duplicate completion or consumption.
- Fixtures use `@GameTestTags`, explicit template namespace and isolated run
  directories. Report selected/total and pass/failure counts; tags do not isolate.
- SS14 sprites/sounds retain attribution; uncertain sound provenance is recorded
  in `docs/audio-unknown-provenance.md`.

## State

Research and implementation are underway; [PARITY.md](PARITY.md) records the
verified mapping. Owner committed the previous alignment as `5d55c01`; this
sprint started from a clean worktree. Connected control validation is not recorded.

Fresh pre-change `loose-objects,ventilation,air-alarm` baseline: **73/502 selected,
all 73 passed**, zero untagged (`build/timed-interaction-baseline.log`). The earlier
four full-suite failures remain historical evidence, not failures reproduced by
this filtered run. No power/ventilation runtime repair was needed for the baseline.

Current implementation includes the resolved ingestion catalog, actor-owned timed
state and generic callback runtime, replicated/predicted world-space progress,
damage interruption and timed live input dispatch. Independent server and client
reviews passed. Full unit validation: **2,122 passed**; focused server validation:
**12/514 selected, all 12 passed**, zero untagged. Full regression validation:
**514/514 selected, 508 passed, 6 failed**, zero untagged. All new timed fixtures
passed in the full run. A subsequent terminal-prune delivery correction ensures nontracking
owners receive the final empty snapshot before presentation metadata is released.

Connected rendering/controls remain unverified; use [MANUAL.md](MANUAL.md).
This is implementation status, not owner closure. No commit or push is implied.

Follow-up: removed generic timed-progress hand-refresh coupling. Ingestion sends
an owner-only body/epoch-validated notification after each committed dose; generic
prediction outcomes now accept sequence/acceptance rather than hand result types.
Independent review passed. Revalidation: **2,122 unit tests passed**, focused
**12/514 selected, all 12 passed**, zero untagged (`build/ingestion-refresh-unit.log`,
`build/ingestion-refresh-fixture.log`). Full suite was not rerun for this narrow
follow-up; its unresolved failures below remain the latest full-suite evidence.
Successful disposable follow-up world removed. Restart client and server before
connected testing because a new ingestion notification payload is registered.

Full-run failures (`build/timed-interaction-full.log`; failed world retained):

- `worldhandworldhandconservesfilledpouch` — spawned-body world pickup denied.
- `alarmusesinheritedhundredwattsandradiusthree` — fresh solve did not restore alarm.
- `farplayercannotdespawnboundunboundorcorpse` — another fixture's `soap-slip`
  fake player was nearer than the expected distant fixture player.
- `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
  `onlycompletedpaidsolvemovesconservedgas` — paid ventilation solve timeout.

These prevent claiming a green full suite. Some names match earlier failures,
but their cause and baseline status are not established by name alone. A fresh
`hands,carrier,air-alarm,ventilation` regression comparison selected **200/514**,
and **all 200 passed** with zero untagged (`build/timed-interaction-regression-focus.log`).
Tags are OR selectors, not fixture isolation; this does not make the full suite
green or establish causes for the full-run failures. No unrelated gameplay fix
is included in this sprint. Successful disposable baseline/focused worlds were
removed; full/initial failure artifacts remain.
