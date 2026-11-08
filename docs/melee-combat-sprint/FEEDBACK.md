# Owner feedback reconciliation

**Accepted and closed by the owner, November 1, 2026.** See `HANDOFF.md` for final
state and validation limits; the pending candidate notes below are historical.

## Exact kitchen-knife rejection

After the origin repair, `run_9b9d8304b17fb38f668a4032` passed unit/readiness but
failed one of 50 selected native tests (1,379 total). The throw was now admitted;
the test's original stack lacked the legitimate selected-hand melee cooldown.
The fixture now verifies that staging and compares release against the actual
held stack, retaining strict components/count, unique-marker, cleared-hand,
velocity and ownership assertions. This is a test-baseline correction, not a
second throw-policy change. Fresh complete validation remains pending.

Owner confirmed the observer swing and combat pickup corrections. The remaining
Monkey report is an authoritative DENIED, not a missing input binding. The exact
registered knife fixture reproduced it while the cobblestone baseline passed
(`run_2b2c1a83f366fc92fe149f4a`, 1 pass / 1 fail of 2 selected, 1,379 total).
The shared launch/revalidation origin used inconsistent float/double subtraction;
both now reuse one double-precision helper. No species gate or throw-physics change
is introduced. Fresh complete replacement validation remains pending.

## November 1 reopened feedback

Previous complete gate `run_3c77cab287a8c469557e97d0` passed (48/48 selected native
tests; 1,377 total). The following newer edits still require validation:

1. Observer crowbars must emerge rigidly from the attacker, not flatten in front.
   Observer rendering now clips the rigid weapon at captured body support plus
   current lunge; accepted first-person fitting is preserved.
2. Item flight is owner-confirmed working; preserve the shared release/flight path.
3. Pinned SS14 uses pickup for an ordinary ground item with an empty active hand
   even in combat mode. Shared pickup dispatch now has that precedence, with
   confirmed proof and living-portable-mob cancellation.
4. Monkeys should throw held objects. No species restriction was found in the
   canonical route; a controlled-monkey native regression is being validated.
   Current ordinary throw binding is Ctrl+Q (the existing grenade-labelled key).

No new gate PASS, native result, visual acceptance, or monkey root cause is claimed
for this follow-up yet. Sections below retain preceding evidence/history.

## Latest candidate status after `run_31729330fbfde112e1d45bf2`

The acknowledged run FAILED: unit/readiness passed; native selected 47/48 of 1,377.
Its sole failure was the handless observer arc. Source repair is complete and a
fresh union preflight/native gate is pending; no new native PASS, gate PASS, or
owner acceptance is claimed. Expected preflight evidence is
`build/melee-owner-followup/handless-presentation-repair-preflight.log` and summary.
Expected union coverage is 11 classes / 42 tests, but terminal structured XML
counts have not yet arrived; record counts only from that evidence. Fresh full gate
must retain all prior floors; package/provenance for the failed gate were not fresh.

The producer, codec, client receipt, and render path have been audited for the
body-relative empty-hand contract. `activeHand == ""` is permitted only when no
copied weapon is present; null, whitespace-only, overlong, or empty-hand plus
copied-weapon payloads remain invalid. A valid handless UNARMED body binds to its
resolved body/selection and hand revision zero; it has no token or fake Hands.
`MeleeArcPayloadTest` covers real roundtrip and invalid guards, while
`NativeCreatureCombatGameTests` checks real body publication, no duplicate, no
Hands, and cooldown behavior. Body cooldown uses revalidated `Selection.handRevision`
(zero if handless, actual revision when hand-bearing), never dereferencing absent
live hands. Request authority is unchanged. See `STRIKE-PRESENTATION.md` for the
durable contract.

Previous PASS run `run_c3fc47697147a6e8e3e517d9` was reopened, not accepted. Owner
accepted close-wall hits and NPC-miss-chance behavior; ghost-facing remains a
separate previously accepted criterion.

Shared `supported_animal_ancestor` composition now grants Hands,
ComplexInteraction, disarm combat, Stamina and Stunnable to Kobold, Monkey and
Scurret. Generic controlled-unarmed combat shares `UnarmedMeleeProfileResolver`
with NPCs, including handless clock handling; disarm targets use resolved hands.
Kobold/Monkey have native eye and speech height fields and refreshed eye
dimensions/tracked speech anchors. Shared ordinary and portable animal palm
transforms now avoid the extra 0.625-down human-arm translation for all animal
held items. New default profile animation 1 / stamina 0.5 / reset true restores
legacy tuning parity; NPC accuracy and health damage are unchanged, while blunt
NPC stamina now correctly uses 0.5.

`NativeCreatureCombatGameTests`, `NativeCreatureDisarmGameTests`, and
`AnimalPresentationAnchorGameTests` are among the native coverage to rerun. No
client was launched; visual/audio acceptance is not claimed. Preserve all 12
criteria, 101 selectors, 15 tags, prior crash fixes and full package/provenance
floors in the fresh gate. Human actions in `MANUAL.md` are unchanged.

## Prior owner feedback and evidence

### Current four-point owner review

1. **Close weapon near walls:** remove rejected miniature camera-corner weapons.
   Render the same copied weapon/profile at the captured animated tip, scale 2,
   rotation and timeline. For first-person close contact (physical gap <=0.2),
   fit against the view near-plane intersection instead of the world body plane;
   ordinary GPU near clipping may still clip the model. World observers retain
   whole-AABB/lunge fitting, accepted reach, 120° fan and 0.6 chest origin.
   A pre-existing generic legacy renderProjection adaptation outside the normal
   strike branch is unchanged; no claim that all legacy camera adaptations were
   removed.
2. **HUD flight arrow:** preserve the approved world orange arrow. Correct HUD
   arrow orientation using committed-velocity dot products against actual 3D
   camera RIGHT/UP/forward quaternion axes and `atan2(right, up)`, with near-zero
   projected-depth fallback for vertical/behind-camera cases. Lifetime and
   actor-role sizes are unchanged.
3. **NPC movement test:** target movement, not NPC movement, controls miss chance.
   No new NPC balance change is authorized. A real registered controlled-human
   accepted-input/motor-ticks/published-velocity path to a freshly spawned
   stationary NPC proves the movement condition; no synthetic positive velocity.
   No stationary-player balance test/change, movement code, or additional NPC
   balance rule. The native fixture compiles but author did not run it.
4. **Ghost-facing:** owner visually confirmed existing tangential front/back
   orientation works; unchanged in this revision and remains a separately tracked
   `ghost-facing` criterion. No repeated ghost manual test, asset or control change.

Actual GPU/audio/two-client visibility remains manual/unverified, especially
whether very small eye-to-wall gaps partially occlude the near-plane projection.

### Current repair since `run_f72722149206de50b037242b`

The gate passed unit/readiness; native selected 41/1,371 and reported 39 pass / 2
fail. Package/provenance are not claimed fresh. Both findings are repaired:
`TrustedNpcFeedbackPublishedEvent` permits a null victim for a correctly
nominated miss while retaining a required attacker; actual-hit paths retain a
non-null victim. All event consumers were audited and
`TrustedNpcFeedbackPublishedEventTest` covers the contract. The stationary-target
chance-1 fixture now expects HIT while its attacker moves; moving-target
chance-1 expects MISS/swing/null victim/no damage, and chance-0 moving-target
still expects HIT. No balance, movement, disarm, or ghost behavior changed.

Fresh focused repair evidence: `build/melee-owner-followup/npc-miss-repair-preflight.log`
and its three XML reports under `build/test-results/test`: three classes, 14
tests, zero failures/errors/skips. This is separate from the earlier 272-test
preflight and inheritance check; do not combine totals. The fresh replacement
must rerun all required packs. GPU/audio/two-client review remains unverified.

### Preserved nine-point feature scope

1. **Pulling hand:** pinned SS14 permits unarmed melee while the active hand
   holds the pulling virtual item; do not impose an offhand-switch requirement.
   `PullingSystem.cs:111–139`, `SharedMeleeWeaponSystem.cs:207–242,309–334`,
   and `ActionBlockerSystem.cs:188–219` establish creation, resolution and admission.
   Physical non-melee held items still block unarmed fallback.
2. **Wide weapon/body intersection:** preserve the accepted 120° arc and 0.6
   chest origin; correct transformed rear support, including upward aim.
3. **Light weapon/body intersection:** constrain the complete rendered weapon,
   not only its leading tip, without increasing gameplay reach.
4. **Knife/crowbar pitch consistency:** use the same captured bounds and actual
   measured/drawn transform in both attack modes; no camera-only relocation.
5. **Invisible disarm direction:** strengthen the existing committed-velocity
   world streak and participant HUD. The new transient policy is 650 ms with
   300 ms full visibility, not a ground marker or another throw implementation.
6. **Silent Kobold retaliation:** integrate the NPC damage path with shared
   authoritative melee outcome, sound and observer presentation owners.
7. **Station contact audio:** expand explicit resolved solid-structure contact
   beyond the existing construction roster; do not infer admission from arbitrary
   solidity or grant structural damage/destruction.
8. **No crashes in owner retest:** preserve semantic BLOCK validation and the
   entity-independent disarm clipping context.
9. **Victim impact particle:** pinned SS14 has no separate generic melee particle
   at the victim/contact point. It has a victim color flash plus an attacker-origin
   fist/weapon animation that moves outward. Server/client `DoDamageEffect` and
   client `MeleeWeaponSystem.Effects.cs` establish these distinct effects. No
   additional victim particle is implemented or claimed as parity.

Movement remains read-only. Interactive clients remain paused until explicit
desktop readiness. Fresh focused checks have completed; the complete OWNER_READY
replacement is still required. No GPU, audio, or two-client manual result is
claimed, and earlier PASS/FAIL results are not evidence for a fresh replacement.

Settled run `run_22907e55e0bfff935048fccb` passed unit/readiness; its native pack
selected 37/1,367 and had 31 passes / 6 failures. Package/provenance were not
claimed fresh. Root causes were isolated without weakening admission: the
construction-family `StructuralContactResolver` map overload skipped shared
damage-modifier application, creating a positive weapon-map miss; the modifier is
now applied exactly once consistently across families and overloads. The floor /
airlock fixture reused a valid target before cooldown expiry, testing cooldown
rejection instead of an admitted MISS; phases now wait through the actual
deadline. Resolver live-fixture coverage checks construction/contact thresholds
and overload parity. A zero-damage NPC fixture used a hit-only profile but
dereferenced nullable no-damage data; it now asserts resolved
`MeleeAttackSoundPolicy.outcome` fallback and has a focused policy regression.
No gameplay admission, range, LOS, or trust boundary was weakened; no new damage
permission was added.

Verified repair validation is recorded in
`build/melee-owner-followup/native-repair-verified.log` and
`native-repair-verified-summary.json`: all three source sets compiled and all
five selected classes passed 15 tests with zero failures/errors/skips. The five
XML suite reports are present and counted individually. This verified invocation
replaces the inconsistent earlier `native-repair-preflight.log` report. The
earlier 6-test and 307-test batches remain separate invocations and are not
combined. Native/GameTest readiness, package/provenance, GPU/audio/two-client
observations remain pending/unverified; independent source review passed. The
replacement gate must freshly run all five packs.

### Preceding candidate evidence

Implementation was complete for the preceding melee corrections, both reported
crashes, the 120° chest-level sweep, and the separate stamina HUD addition.
The latest local check compiled all three source sets and passed 609 tests in
163 suites, with zero failures/errors/skips; PowerShell proof fixtures also pass.
`OWNER-READY.md` maps requirements to current implementations and tests.
The replacement OWNER_READY gate has not passed yet; automated technical
readiness and owner/manual acceptance are separate. Older validation notes below
describe earlier candidates, not current aggregate evidence.

The original crowbar-wall report was retrieved at
`run/crash-reports/crash-2026-10-28_21.58.13-client.txt`: a raycast MISS was
mistaken for a block contact because Minecraft uses `BlockHitResult` for both.
`ClientBodyEntityRay` now checks semantic hit type before either light contact
or structural fan nomination. The strict nomination factory remains unchanged;
the structural-position MISS regression and source review pass.

No more routine DIAGNOSTIC prevalidation is planned. The next automated feature
validation is the complete OWNER_READY replacement, using the supported
headless check packs with packaging/provenance and applicable readiness/native
coverage. Interactive capture remains opt-in and paused while the owner tests.

## Additional manual-test findings

- **Critical:** both real clients crashed on an actual held-item disarm. The
  author retrieved matching reports at `run/crash-reports/` and
  `run/client2/crash-reports/`, both `crash-2026-10-29_01.52.33-client.txt`.
  They identify `DisarmFlightRenderer.render` constructing `ClipContext` with a
  null Entity, which crashes inside `EntityCollisionContext`. Fix with an
  entity-independent collision context and test the actual constructor path,
  not merely arrow geometry. Accessible automated captures have not completed
  genuine flight rendering; the earlier static review missed this API hazard.
  Do not equate this with the older crowbar-wall crash.
  The renderer now uses `CollisionContext.empty()` through its actual clipping
  helper. The actual-constructor regression and combined 481-test batch pass;
  independent review confirms the null-entity path is removed without disabling
  clipping or feedback. A fresh live two-client/GPU retest is still pending.
- Movement appeared offset while moving and snapped back on release. No
  movement changes are authorized here: other agents own that subsystem. The
  melee lunge is attack-only (0.1 s); ordinary walking involvement and active
  movement ownership still need confirmation before attribution. The owner
  explicitly requests root-cause investigation only, with no fix yet.
  Manual server/client logs confirm committed CHARACTER control for both users.
  Source review does not establish prediction lead alone as a cause of reversal
  on release; that remains an unconfirmed hypothesis, not a diagnosed defect.
- **Separate HUD follow-up:** expose remaining stamina beneath the existing
  health/hunger/thirst text rows using the selected body's resolved capability
  and synchronized stamina attachment. This must reuse existing HUD placement,
  not add a parallel HUD, guessed regeneration, or new physiology networking.
  The row is now wired; focused projection/shared-source tests and source review
  pass (480 tests in the combined batch). Connected HUD review remains pending.

The first-scene combat-mode stall is not diagnosed yet. The capture fixture now
records bounded actual combat-policy/context transitions and the return value of
the real toggle attempt; its prior last-request fields described melee only and
could not establish whether a combat toggle was sent. No gameplay combat-mode
or movement behavior was changed to hide the stall.

Diagnostic `run_656334ba8e37aa22c27109db` passed UI readiness, including the new
stamina/source and disarm collision-context/HUD tests. Capture progressed to
`motion_wall_clip` before its real input was not admitted. The owner reports
that the test reclaimed their cursor while they were working, so this run may
have been affected by desktop input/focus interference. Do not classify that
failure as a proven gameplay defect. Further interactive launches are paused
until the owner explicitly indicates readiness; non-interactive work may proceed.
The cursor-reclamation cause is confirmed in both melee capture fixtures: their
admission preparation repeatedly closed screens and grabbed the mouse without
checking window focus. Those calls are removed. Shared focus admission now
requires an active game window, no screen, and an already-captured mouse before
setup commands, attacks, observer swings, and screenshots. Losing control stops
the capture terminally with explicit focus evidence rather than reclaiming it.
All three source sets compile, focused policy/protocol/admission tests pass, and
independent source review passes. No interactive retest was performed; opening a
new client may still obtain OS focus, so explicit owner readiness remains required.

## Current owner review — reopened after `run_efd9547200ce7e0a21a845b3`

The technical PASS was not acceptance. This revision must address all eight:

Additional live-test feedback: the 90° wide sweep remains too narrow, and its
eye/lower-head origin places the knife/weapon too high in view. Increase the
configured total width to 120° and move the actual wide sweep to chest height,
sharing the resolved origin between display and client nominations. This is a
deliberate Minecraft adaptation, not a camera-only offset or a character rig.

1. Replace the complex disarm icon with a simpler visual.
2. Remove the disarmed-item ground marker. At the interaction moment, both
   participants must clearly see the actual item's initial flight direction.
3. Repeated knife light strikes in the same orientation must originate consistently.
4. Knife wide strikes must maintain consistent sweep height.
5. Make the wide sweep noticeably wider.
6. Make wide motion faster and forceful, with an appropriate non-linear sweep.
   Pinned SS14 concentrates its main rotation in the first 15% of its authored
   length; do not mislabel a generic ease-in/out as exact upstream timing.
7. Use a chest/lower-head-centered fan in a head-local frame. Looking up/down
   must retain lateral world-space coverage instead of collapsing with pitch.
   Display and actual nomination coverage must agree; preserve reach and LOS.
8. Diagnose and fix the client crash from wide crowbar attacks against a wall.
   The actual report and semantic-MISS correction are recorded above.

Sequence: diagnose the crash; stabilize the shared sweep basis/weapon frame and
timing; replace marker feedback with committed-release direction and simplify
the disarm cue; then run focused regressions and the complete OWNER_READY gate.
Do not add character rigs or thrown damage.

Current candidate: all eight corrections are wired, with a simple white palm, committed
release-direction streak/participant bearing cue, stable weapon frames, a 120°
head-local chest-centered fan, and the fast SS14 sweep/settle sampler. Wide display and client
nominations share nominal range and per-ray clipping; the direction streak also
respects its actual terrain-clipped length. Compilation and the expanded focused
JVM batch pass (163 suites / 609 tests); the PowerShell proof fixtures pass 14
checks. Source review passes. These do not establish visual acceptance.

The expanded connected diagnostic now requires 20 scenes / 60 images, including
repeat knife, ±60°/±90° wide aim, real wide-crowbar wall HIT/block nomination,
and genuine committed disarm release from actor and victim perspectives. Its
private deterministic RNG seed selects a real production chance roll; it does
not override chance, fabricate release events, or alter normal gameplay.
Complete new connected coverage is still pending; no complete 60-image result
is claimed. The retained capture tool's strict guards have not been weakened.

### Historical diagnostic results

Diagnostic `run_0a9373df7a462500cd5377e6` passed all 10 selected native fixtures
(10/1,362, tags `melee-disarm,melee-presentation-setup`). Its connected capture
failed at `motion_observer_down` before the new crowbar-wall case. Failure
artifacts show a missed outward window and a zero-pitch scene snapshot. Source
inspection confirms that generic observer setup overwrites the scene-specific
pose. Reply correlation also lacks an explicit expected peer swing sequence;
the primary client's policy counter is not the peer's request sequence. Fixing
the fixture must retain measured windows, normal cooldowns, and all 60 images.
This is neither evidence of the reported crowbar crash nor complete visual
validation. The retry is capture-only: the selected native pass above remains
historical evidence, not a fresh native run. Fixture changes preserve specialized
flight setup, correlate replies to the setup's expected peer sequence, and reuse
safe presentation warmups with normal cooldown admission. Irreversible flight
and damage scenes must not be warmed up.

Capture-retry preflight: all three source sets compile; 129 focused JVM suites /
465 tests pass with zero failures, errors, or skips, including observer pose,
reply correlation, warmup/cooldown boundaries, and expired-flight evidence.
The 14 PowerShell proof fixtures and independent source review pass. The retry
has not yet established runtime capture success or a crowbar-crash diagnosis.

Capture-only `run_4aefbb36ac2cc7a15dbffe09` reached 18 scenes / 54 images,
including the explicit crowbar-wide wall HIT path, then failed on missing peer
identity metadata in `motion_disarm_flight`. This does not reproduce or diagnose
the reported crash. Both flight-role scene schemas need producer/consumer
regressions before another capture; the complete 60-image proof is still absent.

The chest-height revision now compiles and passes 472 focused JVM tests. The
resolved ratio is 0.6 of body height; custom inherited ratios are tested, and
the strict character schema continues rejecting unknown/invalid fields. Actual
weapon support/anchor and shared head-local up are tested with float tolerances.
Both flight scene roles now serialize peer and victim identities through the
same producer and validate them before input, with roundtrip regressions. A
fresh native/capture diagnostic is still required for this gameplay revision.

Diagnostic `run_974deec16890977be464a230` passed all 10 selected native tests
(10/1,362) on the chest-height revision. Connected capture timed out before its
first attack at `motion_fist_first`, with combat-mode admission still blocked;
it produced no new PNGs. The missing-image finding is secondary to that stall,
not proof of a screenshot collector defect. Complete visual coverage and the
original crash diagnosis remain outstanding.

Scope reopened after owner testing of `run_cd54bb5b0afb29c9740eb571`.
That technical PASS is not acceptance. The preceding revision addressed only
strike presentation; it did not resolve the complete review below.

## Original review

| # | Requirement/question | Current disposition |
|---|---|---|
| 1 | Noticeable 3D strikes, clear source/direction/reach, observer pitch | Improve with new weapon/sweep feedback below. |
| 2 | Disarm messages leak `entity.moonstation14.player_character_harness` | Fix using canonical character display identity. |
| 3 | Should jugs yield broken glass rather than plastic? | Pinned Jug inherits strong plastic; preserve 0–1 plastic-sheet yield, not glass. |
| 4 | Close/crowded observers must see strikes emerge near attacker | Preserve captured source; revalidate close views. |
| 5 | Is stamina implemented? | Existing stamina system is live: blunt committed damage feeds `MeleeStaminaPolicy`/`StaminaSystem`, and disarm has stamina outcomes. No second system. |
| 6 | Swing sound sometimes plays twice | Trace prediction and authoritative delivery; fix duplicate ownership. |
| 7 | Disarmed weapon is difficult to follow | Add a simple readable Minecraft adaptation using existing presentation owners. |
| 8 | Wide arc seems small; is it SS14 accurate? | Pinned human resolves 30°; component fallback is 60°. Preserve resolved angles; fix fan-following presentation. |
| 9 | Missing SS14-style contact/hit animation | Establish upstream effect before selecting feedback; distinguish damage flash, arc, and debris. |
| 10 | Correct punch/contact audio for all eligible walls | Consume resolved prototypes, not steel-specific branches. |
| 11 | Jug hits lack feedback | Audit real contact/audio/damage-feedback delivery and fix gaps. |
| 12 | Jug/wall integrity absent from examine | No generic health line established for this roster. Do not confuse `DamageExaminable` (offensive-damage verb) with opt-in `ExaminableDamage` (health-severity text). |
| 13 | Syringe analysis omits contained liquid | Trace resolved solutions and existing analysis service. |
| 14 | Thrown knife should damage | **Explicitly deferred: separate thrown-weapons sprint.** |
| 15 | Fist/weapon orientation inconsistent | Use deterministic captured attack frames and recognizable weapon presentation. |
| 16 | Cannot hold disarm | Verify upstream repeat semantics and extend existing held-input owner if applicable. |

## Latest review additions

- Give disarm an identifiable action visual, not merely an attack or outcome popup.
- Do not shrink a close-wall punch into an inconspicuous mark. Separate visible
  transverse size from forward contact distance without extending gameplay reach.
- Modestly exaggerate the existing whole-body lurch; keep its shared emission
  clearance consistent. No new limb, clothing, species, or posture rigs.
- Incorporate the captured weapon into the attack effect itself, so recognizing
  it communicates danger without inspecting the held-item layer.
- Weapon secondary attacks must visibly sweep the actual captured fan rather
  than read as an outward thrust.

## Candidate implementation and remaining validation

The owner rejected the weapon-over-punch overlay during live capture. Armed
attacks now render the copied weapon **instead of** fist crescents/guides, using
a configured blade axis and bounded model-tip placement. Light attacks reuse
the authored extension/withdrawal sample; wide attacks sweep the captured fan.
The candidate also includes profile-sized unarmed ribbons, the larger shared
lunge, and a profile-inherited hand-only disarm glyph.

Disarm popups use `CharacterPublicName`; held disarm reuses the held-input owner.
`MeleeAttackRequest.swingPredicted` records whether local swing audio actually
played. Server swing routing excludes the initiating player only on that receipt;
impact audio and gameplay admission do not consume it. The temporary loose-item
cue is attached to the actual committed forced-disarm item.

Syringe solution examination now joins the existing world-item examine registry,
with resolved-container gating, detached contents, and the shared formatter.
Held-stack examine remains unsupported by that entry path. Structural-contact
profiles cover the currently eligible solid wall, plastic wall, and girder roster;
this does not make arbitrary terrain damageable.

All three source sets compile. The latest exact focused JVM batch passed 114
suites / 403 tests with no failures, errors, or skips; scoped implementation
reviews passed. The new weapon-first direction is not yet GPU-validated.
The expanded connected diagnostic requires twelve attack trios (36 PNGs),
including real DISARM input, an actual crowbar identity, and a real jug HIT whose
outward capture requires client red-flash state on the nominated entity.
Focused native tests additionally cover disarm-item ownership/expiry and the
registered syringe examine provider. The corrected native selection passed all
18 tests (18/1,361 selected) in `run_8aaa30556c3b3c6db3a8941b`. The subsequent
client capture stopped at a stale pre-pickup hand snapshot in its fixture;
strict item identity is retained while that fixture is corrected. Full visual
capture is still pending. Marker readability, audio audibility, OS input, crowded views,
and owner acceptance remain unverified. A fresh serialized owner-ready gate is
still required; do not treat source review or earlier passes as completion.

The first reopened native diagnostic selected 18/1,361 fixtures and failed
before client capture. Its new expiry fixture incorrectly required continued
motion after landing, and its syringe fixture confused a default empty reagent
component with absence. Both fixture preconditions/assertions are corrected,
compile/focused checks and source reviews pass, and the subsequent 18-test native
selection passed as recorded above.

Capture setup has a dedicated `melee-presentation-setup` GameTest calling the
same weapon setup helper as the client fixture: empty → knife → idempotent knife
→ crowbar. `LiveHands` snapshots must be re-read after a committed transfer;
token coherence compares `Optional<String>` values, not a string to an optional.
That preflight passed (1/1,362 selected) in `run_7484f84e595f05734175fb41`.
Its client run captured the first 33 images, then exposed that primary jug aim
must originate from the local-player look owner, not a server-only body rotation.
The fixture now uses that existing look path and waits for real target/ray state;
the wall scene explicitly tests an unarmed punch rather than the retained knife.

The weapon-first capture diagnostic `run_b92b6b9f4392deb8fece36e3` failed before
its first input (`wait_ready`, `motion_fist_first`); no fresh PNGs were produced.
Server scene readiness and render stabilization were present, but no attack
request was sent. This is not visual evidence for or against the new rendering.
The fixture now prepares screen/mouse capture before its actual combat-mode
admission guard. Jug readiness uses the shared nearest damageable entity ray,
then checks the registered target UUID. Failure JSON records the blocked stage.
All three source sets compile; the focused admission-order and body-ray selection
passed eight tests. Independent source review passed. These checks do not prove
connected input or GPU presentation; all capture windows and identity requirements
remain in force pending a fresh capture.

The next diagnostic, `run_ab7fdb66d1e8496bba9cb777`, progressed past the initial
scene but failed at final `motion_jug_hit` readiness before sending that attack.
Its earlier images are fresh partial weapon-first evidence, not a complete
36-image pass. Jug readiness and those partial visuals require separate review;
this run does not establish jug damage feedback or owner readiness.
Source investigation found a contradictory jug guard: `presentationStack` is
empty for `UNARMED`, but the fixture required it to be present. Readiness now
requires a copied presentation stack only for held/glove selections. The earlier
correlated MISS belongs to an earlier scene, not a jug attack. Partial visual
review confirms weapon-only presentation and right-to-left wide pose changes;
continuous timing and complete visual acceptance remain unverified.

Readability review of the partial capture rejected the filled close-wall effect,
faint close-observer source guide, and dark disarm glyph. The corrective candidate
preserves the outer short-contact crescent but reduces its band fill and removes
redundant cross/ghost layers; the source guide shares the normal-depth emissive
material. Disarm uses normalized opacity and extension/withdrawal. All three source
sets compile and the seven-class focused selection passes 33 tests with no
failures, errors, or skips. Regression coverage checks unchanged outer vertices,
reduced nonzero fill, clipped bounds, and source-guide endpoints. Source review
passed; fresh GPU validation and owner acceptance remain outstanding.

Diagnostic `run_b522d39990f56c927b487e8f` subsequently passed and produced all
12 trios / 36 distinct, hash-matching PNGs under
`build/reports/melee-visual-capture/558e8c1df7f14c1b957fd79dc201c0d1`.
Jug metadata records the registered target UUID, HIT, and outward live-client
`targetRedFlashProgress: 0.8333333`. This automated pass is
not visual acceptance: review of all trios still rejected dominant close-wall
fill, an overlapping-shape disarm cue, and inconsistently readable weapon/source
presentation. The candidate is **not owner-ready**. Reassess presentation scale,
orientation, and source placement before another gate; do not add character-hand
attachment requirements or rigs to solve a disembodied-effect problem. Continuous
motion, visible jug red flash, audio, OS input, and marker readability remain
manual/unverified boundaries.

The next candidate uses resolved `weapon_pose.scale: 2` in both actual support
measurement and shared drawing, one camera-facing disarm quad at the frozen
world-space tip, and a short-contact radius bounded between `1.5 × trailWidth`
and `3 × trailWidth`. The floor keeps a substantial crescent rather than a tiny
mark. All three source sets compile; eight focused suites pass 36 tests across
two selections, with no failures/errors/skips. Source review passes. Prior images
do not validate these new changes; another complete capture is required.

Diagnostic `run_b42099661d615960d551ae65` passed with another complete,
hash-verified 36-image set at
`build/reports/melee-visual-capture/61e6fd97cec6447db4c211bb567208e6`.
Lead review of labeled trios confirms a blade-leading first-person knife and a
smaller, substantial wall crescent. Reviewer claims that the first-person knife
image contains Steve, that effects must attach to Steve's hands, or that an
unarmed observer control must show a weapon are not supported criteria/evidence.
Do not use those claims to add character rigs or revert the weapon-only branch.
Crowbar foreshortening and glyph styling still warrant owner visual judgment.
The jug's outward metadata records HIT and red-flash progress `0.8333333`, but
the rendered jug looks white/gray; its actual tint consumer is under investigation.
No new owner-ready claim is made.

The jug's opaque branch was bypassing the shared tint consumer. It now uses the
same production presentation-buffer selector as other items; reagent base color
remains separate and damage tint is applied once. Regression coverage exercises
the jug-used selector's opaque red tint, actual vertex count, white fast path,
and single alpha/tint application. All three compiles and the full focused
reopened selection pass: 115 suites / 409 tests, no failures/errors/skips. Source
review passes. GPU confirmation of this tint fix remains required; the earlier
36-image capture predates it.

Confirmation diagnostic `run_38a927b9848e379ef13a6550` passed all twelve trios.
At `build/reports/melee-visual-capture/5cba75a61c5a445781c4fb6f36d11fdf`, all
36 PNG hashes match; the registered jug target receives HIT, is visibly red in
outward/extended captures, and returns to white in the return capture. Both lead
inspection and independent review confirm the visible tint. The remaining effect
size/style judgments, live audio, crowded readability, OS input, and disarmed-item
marker readability are owner manual-test boundaries, not automated acceptance.
The complete reopened revision is being submitted to the serialized owner-ready
gate; diagnostic PASS is not that gate's verdict or sprint acceptance.

The complete owner-ready replacement `run_8bcd23c5a15ecee033cb11c5` failed
before Gradle started: Windows reported “The command line is too long” for the
expanded unit-selector launch. Stale/missing JUnit XML is the resulting coverage
finding, not evidence that tests ran and failed. All 131 selected classes and
trusted readiness/native/package/provenance floors must be retained while the
launch path is corrected. The owner-ready gate has not passed.
The shared SDK expands unit selectors into `--tests <FQN>` pairs in
`tooling/agent_workflow/workflow_check_packs.py`; its
`workflow_native_fixtures.py` unit authorization/adapter expects that same form.
The pipeline owner must repair both paths with bound Windows-safe selector
transport and an end-to-end public SDK regression before a fresh complete gate.
Do not bypass this by reducing selectors or accepting old XML.
The owner reports the launch issue repaired, and authenticated status now reports
a new installation receipt. A fresh complete replacement will retain the same
131 selectors and validate the repaired runtime path; old FAIL history and old
JUnit XML remain invalid as evidence of that rerun.

## Verified parity distinctions

Reference remains `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`Content.Shared/Damage/Systems/DamageExamineSystem.cs:21–38` raises a detailed
damage-verb event; its consumers include melee weapon damage, not generic health.
`Content.Server/Damage/Systems/ExaminableDamageSystem.cs` separately requires
`ExaminableDamage`. Neither an inherited `Damageable` nor BaseItem's
`DamageExaminable` alone establishes a structural-integrity line.

Pinned melee feedback establishes arc/copied-weapon effects, lunge, and a
positive-damage target tint. A separate universal melee hit-burst particle has
not been established; verify the existing tint/contact path rather than invent
an effect and call it SS14 parity. Jug positive-damage feedback still needs live
verification in this revision.
