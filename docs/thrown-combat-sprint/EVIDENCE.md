# Thrown-combat submission evidence

## Current owner-feedback revision

Latest follow-up: the thrown-contact helper now detects an existing start overlap
instead of relying solely on an entry ray. A focused regression demonstrates the
old empty-clip case; a new native fixture moves a target at ordinary speed into a
normal-speed bola's starting bounds. Actions now defaults to Ctrl+K, turnover stays
on K/Shift+K, and direct disposal climb moves to Ctrl+J. Press-time roll intents
survive quick release, but not UI/world changes; the menu owns its configured chord
over an aliased roll/climb binding. Saved owner Controls are not migrated.

Fresh local checks for this follow-up: **70 classes / 410 tests**, zero failures,
errors or skips, and main/test/GameTest compilation PASS. Independent source
reviews of collision/fixture geometry and final input ownership are PASS. The
new native moving-bola fixture and the complete replacement gate still need execution.
Counts and gate results in the following revision history are not new proof.

Gate `run_c0833e62db0c88205f714ae0` passed 70/71 native tests. The new moving-bola
fixture stopped at its movement precondition: the target moved 0.0 blocks, so that
run did not establish the moving-hit outcome. The ordinary-speed movement assertion
is retained while correcting the fixture's native movement setup; neither the
assertion nor the projectile speed/hitbox may be relaxed to obtain a pass.
The fixture now leaves native travel enabled (NoAI disabled), removes villager
brain behaviors and stops navigation, then applies the ordinary horizontal
velocity after publication. This suppresses autonomous decisions without freezing
physics. Its strict displacement, overlap-precondition and ensnare assertions remain.
Gate `run_cae0ae41610e9523a0448fab` then passed movement/overlap preconditions but
failed ensnare. Source tracing found the fixture armed its bola before publication;
the production arming guard correctly refused it. The fixture now publishes first,
arms and asserts active combat plus thrower UUID, then uses the normal launch path.
Independent review checked this order against the actual guard and production caller.
Movement speed, geometry and ensnare assertions are unchanged; native execution of
the corrected fixture remains pending.

`FEEDBACK.md` defines the current scope and parity decisions; `ASSETS.md` records
the approved armor import and live resolved bindings. Labels now sit below the
capability icons, shared outer HUD margins are tightened, and menu chords are
filtered using event modifiers. Attached-item selection shares its bounded upper
tolerance between client and server. Returning throws use per-flight physical
clearance and the ordinary typed contact pipeline, without launch self-hits.

Independent source reviews of UI/input, armor material/catalog integration, and
throw/floor geometry are PASS after correcting their findings. Fresh native fixtures
cover actual prone attachment selection and an actual returning swept self-hit.
Their execution, current readiness/package checks and owner GPU testing are separate
boundaries; the historical results below do not certify this feedback revision.

Final focused feedback preflight: **68 exact classes / 400 tests**, zero
failures/errors/skips, with main, test and GameTest compilation passing. The
selection retains the merged 64-class regression set and adds affected clearance,
renderer and HUD-layout checks. All six imported armor files match the approved
delivery hashes. The current serialized gate, native execution and owner testing
remain separate from this local PASS.

The first feedback gate (`run_5b8cd434ed07a764e11dd7dd`) passed JVM and readiness
checks, but its 70 native tests had two failing fixture controls. The synthetic
floor attachment was placed at the observer's feet, so the wall was not across
its ray; the corrected fixture uses the other body's contact and explicitly
checks that ray hits the wall. The internals audio fixture counted unrelated
emitters on the shared level; it now counts only the controlled body's cues,
retaining the strict single-connect/no-loop/silent-disconnect checks. Independent
source review passed both corrections. No gameplay behavior was weakened to make
these assertions pass; replacement native validation remains required.

The next gate (`run_0ba0a21c4548d43e2c4208c2`) passed 69/70 native tests, including
internals audio, actual prone selection and returning self-hit. Its sole failure
was another wall-control error: the fixture built a multi-block wall but accepted
only its bottom-center block as the expected ray hit. It now validates membership
in the exact set of 12 placed wall blocks and clears that same set. Blocked selected
admission, clear-ray recovery and own-attachment exclusion remain asserted.
Independent source review passed this correction; a fresh gate is still required.

## EVA merge revision

The reconciled merge preserves the shared stateful 20-slot action/provider allocator,
direct and Shift-number bindings, cached painted action identity, and conditional
self-removal. Incoming source-specific mask/internals actions use the same allocator;
effect preparation carries both the validated provider grant and request sequence.
Existing EVA protection, finite breathing, examination, approved worn assets, and
entity-attached audio remain in scope for regression, not redesign.

Fresh merged-source checks: main/test/GameTest compilation passed; the exact union
of the prior 44-class regression set and incoming changed tests passed **64 classes,
376 tests, zero failures/errors/skips**. Independent source review found no semantic
merge issues. Language resolution retains all 1,493 distinct keys from both branches,
with no duplicate keys. All 12 Git conflicts are marked resolved; no merge commit
was created. These checks do not establish native/GPU behavior or owner acceptance.
The sections below describe prior revision evidence; they are not fresh merge proof.

Implementation evidence is the current source tree and focused test report; the
serialized integration gate remains authoritative for package, provenance,
readiness, native contact, and review verification.

| Criterion | Evidence covered | Boundary |
|---|---|---|
| C1 resolved composition | Resolved prototype composition/runtime consumer implementation; focused tests | Resolved behavior is implemented; gate validates source and package. |
| C2 throw damage and armor | Damage/armor implementation and focused tests | No interactive visual review claimed. |
| C3 embed, injection, fill | Embedding/injection/fill implementation and focused tests | No native physical-contact result claimed. |
| C4 bola timed restraint | Bola restraint and timed interaction implementation and focused tests | Manual timing/interaction review remains. |
| C5 attachment presentation | Attachment presentation implementation and focused tests | GPU rendering accuracy is unverified. |
| C6 conservation regression | Conservation/cleanup regression tests | Gate should verify applicable test coverage. |
| C7 resources and provenance | Resource/provenance source evidence | Fresh package artifact and provenance are to be produced by the required package gate. |

Final focused JVM validation: 44 exact source-verified classes, 240 tests,
zero failures/errors/skips; compileJava, compileTestJava, and compileGametestJava
passed. The manifest/summary are rebuilt from the current 44 XML suites. Current
capability-hotkey tests cover snapshot-identity slot assignment, real modifier
lookup/default chords, combat HUD compatibility, shared HUD/menu/pointer projection,
rebind policy, and stale painted-entry rejection. They do not claim native client
or visual acceptance. Independent review passed after cache/layout findings were
resolved; owner acceptance is pending.

C8-hotkeys now covers the shared identity-stable 1-20 allocator and the same
accepted slot projection across HUD, menu, and pointer. Default direct/Shift chord
semantics follow pinned SS14 sources; no extra banks or dedicated self key are
introduced. Self-removal remains conditional; ensnared escape priority and item
recovery are preserved. Other capabilities do not reserve slots, snapshot
reordering does not shuffle keys, and holes remain unassigned.

Wearable model request `thrown-combat-basic-armor-wearables-20261103-r1` is publicly
listed as CLAIMED by `gpt-6.1-sol-basic-armor-r1`. This is not evidence of modeler
progress, delivery, approval, or import. Local corrected upstream prototype/path
references are clarifications only; the submitted frozen context was not updated
and no duplicate request was created. Existing approved inventory sprites and
worn visuals are unchanged.

Fresh native, ui/interaction/prediction readiness, package/provenance, and owner
acceptance for this revision remain pending. Prior accepted gate history is not
current proof.

## Manual owner review

Follow `MANUAL.md`: test knife/dart armor behavior, dart and hypodart bottle
fill/injection and dose consumption, bola movement restraint and timed removal,
embedded-item contact-point/presentation/removal, and cleanup/conservation on
death and pickup/drop. Verify exact item IDs in the current registry. Native
physical-contact fixtures and interactive GPU rendering have not been verified.

