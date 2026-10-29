# Active capabilities — current evidence

**Sprint closed by owner on 25 October 2026.** After the final manual checklist,
the owner reported no recurring idle lag and that everything appeared to work,
then explicitly requested closure. See `HANDOFF.md` for the durable state and
remaining automated-validation boundary. No measured performance cause is claimed.

This document is an **as-of-submission author snapshot**. Planned/pending checks
describe preparation, not the eventual execution status of a new gate. The gate's
verified current run/generation/input check receipt supplies authoritative final
execution facts and supersedes pre-submit pending prose. Owner observations remain
separate from technical readiness.

## Latest workflow result

Owner feedback after the PASS below requests half-width/half-height buttons and
menu, and reports severe idle client lag. Size changes and performance diagnosis
are in progress. The prior PASS does not validate these subsequent edits; no
performance improvement or owner acceptance is claimed.

Subsequent owner testing: the half-size **menu looks good**. Keep its sizing and
window behavior. Open defects are repeat-use scream cooldown display, excessive
icon padding, F3+1–3 being captured by action bindings, and unresolved idle lag.
The attempted F3+L capture opened advancements; no performance capture is available.

The compact sizing, repeat-cooldown clock, larger button artwork and F3+1–3
queue guards are now implemented and independently source-reviewed. Main and
GameTest compilation plus **118 focused JVM tests across 24 fresh suites** passed,
zero failures/errors/skips. Event ordering for the F3 guard was checked against
the mapped NeoForge client bytecode; physical debug-chord behavior remains a live
check. No fresh workflow/native readiness is claimed for these later edits.
Idle lag remains an unresolved owner-reported defect awaiting an actual capture;
it is not reclassified as a nonblocking subjective follow-up.

`run_cd9e0a6eb722cce655c3452c` generation 1: **PASS**, acknowledged. Technically
ready for owner testing, not owner acceptance. Follow-ups are live layout/input,
menu close/drag/cursor lock and movement, chat clicks versus typed numeric input,
cooldown visuals, personal-name attribution/audio, body switching and debug-cap
grant/revocation. Use `MANUAL.md`; no sprint closure or merge is implied.

### Historical evidence-review incident

`run_9f7202a8e56fa6db6c66bdf2` generation 1 remains **FAIL** after coverage
review; acknowledged. Its current CHECK result is **PASS**: unit, readiness,
**17/17 focused native tests**, and packaging all completed successfully. The
native log explicitly reports `All 17 required tests passed :)`, including the
corrected human negative-emote fixture and both personal-name regressions.

The coverage finding says fresh native evidence is absent, contradicting that
same run's completed check evidence. The earlier pending-native notes below were
pre-submission/history, not a description of this completed check. No technical
gate PASS or owner acceptance is inferred from that historical run. The pipeline
was subsequently repaired; the fresh PASS above is the current readiness result.
Current native log reference:
`artifact_task_9f7202a8e56fa6db6c66bdf2_log_e93dd95d637a17b1e5ae`.

## Status

Further owner-feedback revision: top-left vertical grant-only bar,
clear circular cooldowns, chat clicks, public emote names and standard menu chrome.
Previous FEATURE run `run_f00434d55b38ae603fe5ae37` generation 1 passed checks
and reviews, but does not validate this subsequent UI/input revision. Independent
source review passed for the earlier numbered-bar revision, not these further
changes. Independent source review passed for the six-fix revision; fresh workflow
validation is pending. Personal-name resolution now uses the existing speech
identity ledger; a prototype-name assertion alone is insufficient.
No owner acceptance.
Melee remains out of scope.

Coordinator diagnostic `run_2b42b7bcf49a90655edbda1f` passed on 24 October 2026;
result acknowledged and cleanup confirmed. Earlier FAIL runs remain historical.

## Criterion evidence

| Criterion | Implementation and evidence | Remaining boundary |
| --- | --- | --- |
| `character-composition-and-discovery` | Typed resolved action family; inherited body grants; real production resource/default and export/import tests; registered human native discovery | Not every species/variant was manually possessed |
| `combat-vocal-action-admission` | Body-owned combat state; mental action versus conscious scream; tick cooldowns and stale request rejection; native toggle, scream and critical-body cases passed | No melee; physical controls and audio quality need owner checking |
| `equipment-action-provider-lifecycle` | Debug cap supplied through body-give and equipped/removed through existing equipment owner; native worn grant, harmless pulse and old-provider rejection passed | UI equip/remove and observer-visible pulse need owner checking |
| `ability-ui-and-input` | Revised snapshot-driven numbered/clickable action bar; K remains supplementary. Input/geometry checks and independent key/pointer/lifecycle review passed; older readiness evidence below predates the revision | Native pixels, physical 1/2/click/movement and GUI-scale appearance not visually reviewed |

## Numbered-bar revision evidence

### Subsequent six-fix revision — 25 October 2026

- Main and GameTest compilation passed; **66 JVM tests across 21 fresh suites**,
  zero failures/errors/skips. After personal-name fixture and catalog integration
  corrections, the final expanded selection passed **111 tests across 24 fresh
  suites**, zero failures/errors/skips, including creative-tab registration,
  Human prototype composition and server/client source boundaries. This supersedes
  earlier local counts below; it is not a full-suite PASS.
- Source review passed for vertical top-left/grant-only button geometry, circular
  cooldown/seconds feedback, chat paint/input ownership and persistent menu chrome.
  The menu reuses shared geometry, close controls and pointer-workspace lifecycle;
  closing it does not replace or close other window models.
- Chat uses one shared workspace painter and ordered click/drag/release owner.
  Production-used admission tests cover the bar's chat path, visibility, grabbed
  cursor and unrelated hosts; shared routing tests cover misses and capture.
- Shared emotes now reuse `CharacterPublicName.component`. Compiled native
  regressions initially checked prototype metadata. The revised human fixture
  instead retrieves its durable account/profile personal name and requires that
  the scream plan use it, not `Human`, the Minecraft account name or native harness
  name. The mouse-composition delivery fixture now also retrieves its bound saved
  personal name, requires it to differ from account/species names and checks that
  exact delivered attribution. `Mouse` is not the expected emote attribution for
  that bound character.
  Selected tags include `mouse-emote` as well as
  `active_capabilities,equipment-quick-equip`.
- Physical cursor/chat draft behavior, native pixels, GUI-scale usability and
  subjective scream audio remain owner follow-ups, not claimed automated proof.

Workflow `run_1f72b5d05b43f1af178c7bc5` remains FAIL: JVM checks and readiness
passed (**201 readiness tests, 37 suites**, zero failures/errors/skips), while
native selection passed **16/17**. Only the new mouse test's invalid distinct-name
precondition failed; the human-harness naming regression passed. That fixture
precondition has been corrected without production changes and independently
reviewed. Fresh validation remains pending; packaging did not run after the native
failure. The correction retains metadata and delivered-name assertions.

The subsequent personal-name fix extends the shared public-name policy with a
read-only lookup of the body's bound account/profile in `ChatIdentitySavedData`,
the same saved name used by ordinary speech. No name is allocated during display;
custom-name precedence and unbound/NPC prototype/native fallback remain. This
also names inactive/dead bound bodies without copying speech-admission checks.
Client-wide entity-name projection has not been added; the fix covers authoritative
server emote output and existing server public-name consumers.

Workflow `run_29515e6f1300077cb431137a` remains FAIL: unit and readiness checks
passed, native selection passed **16/17**, including the personal-name human
scream regression. The mouse-composition fixture emitted its saved personal name
(`Felix Dawes` in that run) but still expected `Mouse`; its stale assertion is now
corrected and independently reviewed. Production naming behavior is unchanged
by this fixture correction. Fresh validation and packaging remain pending.

Additional targeted unit triage reproduced three sprint-related omissions:
creative-tab registration of the debug cap, Human's exact composition expectation
(37 versus 39 after CombatMode/Vocal), and the client-package allowlist for
`ms14/actions/client`. These are corrected and independently reviewed; expanded
focused validation includes their three test classes. The classloading fixture
also explicitly recognizes the existing `Dist.CLIENT` diagnostics sampler and
checks its annotation, without changing animal source or admitting its entire
diagnostics package.
The fourth reproduced failure is outside this sprint: committed
`PrototypeCatalogSyncTest` expects Flight cruise height 1.5 while committed
`FlightComponent.DEFAULT_NPC_CRUISE_HEIGHT` is 4.0. Git HEAD and unchanged working
paths confirm the mismatch predates this revision. No full-suite PASS is claimed;
the closed animals sprint is not reopened here.

Workflow `run_05b4d0ba206b437fe699c3c5` remains FAIL: unit/readiness passed and
native selection passed **16/17**, including both saved personal-name regressions.
The human negative-emote test failed; its sound listener observed the entire
shared level, including other fixtures' squeaks. The fixture is now scoped to its
own body/carrier and command window, retaining explicit dispatcher rejection and
no-squeak assertions. Independent source review passed; main/GameTest compilation
and **111 focused JVM tests across 24 suites** passed with zero failures/errors/
skips. No production admission or sound behavior is changed.
Fresh native validation is required; no all-native-pass claim is made.

### Earlier numbered-bar checks (historical)

- Main and GameTest compilation passed; focused JVM selection: **58 tests across
  18 suites**, zero failures/errors/skips.
- `BodyActionSlotsTest` covers stable slots, missing/revoked providers, current
  body/epoch admission and duplicate bindings. Layout tests cover shared pointer
  host admission and slot geometry. These are not live physical-input evidence.
- Independent source review passed for pre-vanilla key routing, shared pointer
  host click routing, painting and pointer lifecycle. The vanilla hotbar loop
  consumes queued clicks; the hook drains only conflicting hotbar clicks in the
  owned-action context. Invalid action input is discarded rather than delayed.
- Numeric keys and pointer clicks reuse `BodyActionClient.activate`; server
  admission, grants, cooldowns and provider ownership remain unchanged.

## Prior-candidate validation (before numbered-bar revision)

- Filtered native selection: `active_capabilities,equipment-quick-equip`;
  **15 selected, 15 passed, zero failures**. Five are new registered-body action
  cases. The total available native suite was not collected here; no full-suite
  claim is made.
- `featureReadiness` categories `ui,interaction,prediction`: **190 tests across
  34 fresh suites**, zero failures/errors/skips; all three categories PASS.
  These include action layout/admission/discovery/client-state/codec tests, not
  just unrelated legacy coverage. Preview category was not selected.
- Ordinary connected-client join/startup: PASS. This proves startup/connection,
  not physical menu activation, gameplay pixels or sound perception.
- Author-focused JVM selection: **53 tests across 17 suites**, zero
  failures/errors/skips; main and GameTest compilation passed.

The coordinator retains bound run artifacts. Disposable local references are
`build/reports/feature-readiness/readiness.md` and
`build/reports/nonhostile-animals/connected-join-proof.log` (the latter is the
existing shared runner's path; this check did not spawn an animal population).

## Deliberate limits

See `PROTOTYPES.md` for pinned SS14 composition and adaptations. Human voice uses
an explicit unsexed fallback because no local sex source exists. Existing generic
emote-command/reagent behavior and chunk-tracked emote chat are retained; complete
upstream muting/acoustic behavior is not claimed. Reptilian recording licensing
is unverified and recorded in `docs/audio-unknown-provenance.md`.

The revised bar supports numeric slot bindings and clicks; K is supplementary.
Full SS14 drag/reorder/search and user reassignment of actions between slots are
not included. Direct disposal
climb defaults to Ctrl+K to preserve it alongside abilities K; saved owner controls
are not overwritten. Existing animal commands are not migrated into this catalog.

Use the short `MANUAL.md` checks after owner-readiness validation. No sprint
closure, installation, commit or merge is implied by this document.
