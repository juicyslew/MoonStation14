# Melee replacement: implementation and evidence

**Sprint closed by owner acceptance, November 1, 2026.** `HANDOFF.md` supersedes
the candidate-status descriptions below. No further validation submission is
authorized; historical PASS, FAIL and ERROR verdicts remain unchanged.

## Kitchen-knife follow-up (replacement validation pending)

`run_9b9d8304b17fb38f668a4032` failed after unit/readiness checks passed:
49/50 selected native tests passed (1,379 total). The knife throw was admitted;
its fixture then compared the released stack against a pre-pickup baseline.
`BodyHandItemTransfer` legitimately stages `MELEE_COOLDOWN` through
`MeleeSelectionCooldown.stageSelectedInsertion` on knife pickup. The fixture now
checks the original item/count/unique marker and that cooldown staging, then
strictly compares every released component with the authoritative post-pickup
`LiveHands` stack. Duplicate detection uses the independent unique marker.
Production throw behavior is unchanged by this fixture correction. Fresh native
validation is still required; no technical PASS is claimed for the replacement.

Owner confirmed observer swings and combat-mode pickup work after
`run_d5448be4fab640a96a101cd8` PASS. Preserve those fixes and accepted item flight.
Monkey kitchen-knife throwing still produced server DENIED. Diagnostic
`run_2b2c1a83f366fc92fe149f4a` reproduced it: 2 selected / 1,379 total, cobblestone
passed and kitchen knife failed at `precommit-position-revalidation`.

Initial hand placement subtracted two floats before promotion; revalidation used
double arithmetic. For `.9f` body height and `.08f` item height, the resulting
origins differ by approximately 1.49e-8 blocks even without movement. Initial
placement and both revalidations now consume the same shared `handOrigin` helper.
Equality/authority checks and flight rules are unchanged. The regression uses the
old expression at zero and the diagnostic's large coordinates. Native rerun is
pending; do not treat the failed diagnostic as validation of this repair.

Separate requested side task: ghost-role window pointer capture repair, documented
in `docs/ghost-role-ui-followup.md`; it is not an expansion of melee gameplay.

## November 1 follow-up (validation pending)

The preceding complete gate `run_3c77cab287a8c469557e97d0` passed, including
48 selected native tests of 1,377. Owner feedback reopens presentation/input work;
that PASS does not validate these newer edits or establish owner acceptance.

- Observer weapons now retain rigid profile scale and captured-tip anchoring;
  melee-only quad clipping hides geometry behind the captured body-front plane.
  The plane follows the current captured lunge, not future-lunge padding.
  First-person constrained/near-plane fitting remains unchanged.
- Combat-mode ordinary ground-item clicks reuse pickup dispatch when the active
  hand is confirmed empty. Held items and pulling occupants retain attack routing;
  living portable mobs cancel pickup, while eligible dead bodies may allow it.
- A native controlled-monkey pickup/throw regression exercises the existing hand
  authority and release path. A monkey-specific rejection has not been reproduced;
  do not describe a species throw fix or claim OS-input coverage.
- Item flight is owner-confirmed working and its implementation is unchanged.

New focused checks and the complete replacement gate remain pending. Preserve all
preceding requirements and package/provenance floors. No interactive client launch
or visual acceptance is claimed. Earlier sections below record historical work.

`run_31729330fbfde112e1d45bf2` FAILED and is acknowledged: unit/readiness passed;
native selected 47/48 of 1,377. Its sole failure was the handless observer-arc
empty-hand contract. The source repair is now complete: body-relative UNARMED
payloads permit `activeHand == ""` only with no copied weapon; null/whitespace/
overlong values and empty-hand-plus-weapon remain invalid. Producer, codec, client
receipt/render path, roundtrip tests and native publication assertion were audited.
The body remains authority-bound to resolved UNARMED selection; no fake Hands.
Fresh union preflight passed 11 classes / 42 tests (zero failures/errors/skips),
three source compile tasks, five canonical JSON parses, and diff check. See
`build/melee-owner-followup/handless-presentation-repair-preflight.log` and its
summary. The prior native gate selected 48 of 1,377 and had one empty-arc guard
failure; this repair is complete but native rerun remains pending. Do not claim a
new native/gate PASS or owner acceptance. Preserve all 12 criteria, 101 selectors,
15 tags, prior crash fixes and full package/provenance floors. No client was
launched or visually/audio reviewed.

Current candidate: shared `supported_animal_ancestor` composition adds Hands,
ComplexInteraction, disarm combat, Stamina and Stunnable to Kobold, Monkey and
Scurret. Controlled-unarmed and NPC paths share the resolved profile; target
disarm uses resolved hands, and handless animals use the clock sentinel. Kobold
and Monkey native eye/speech fields feed eye refresh and speech anchors. Shared
ordinary/portable palm transforms correct held-item placement. Default profile
animation 1 / stamina 0.5 / reset true restores legacy tuning parity; NPC accuracy
and health damage stay unchanged, with blunt NPC stamina correctly using 0.5.

Eye dimensions now refresh in `onAddedToLevel` via shared
`refreshPresentationDimensions` (with late-catalog tick fallback). The ghost-entry
fixture uses canonical `DevelopmentPossessionGameTests` control flow and real
typed damage/epoch checks, not fabricated death events; production authority is
unchanged. Three creature native fixture classes require fresh execution.

Pending handless cooldown repair uses validated `Selection.handRevision` for body
UNARMED consumption (zero when legitimately handless, actual revision when hands
exist), without fake Hands or inventory bootstrap. Its strengthened fixture checks
immediate COOLDOWN and unchanged damage while the source has no Hands.

## Current four-point review

1. Remove rejected miniature near-wall camera weapon; use the same copied weapon,
   captured tip, scale 2, rotation and timeline. First-person fit at physical gap
   <=0.2 uses view near-plane intersection; normal near clipping can remain.
   World whole-AABB/lunge fit, reach, fan and chest origin remain unchanged. The
   pre-existing generic legacy projection path outside normal strikes is unchanged.
2. Preserve world orange arrow. HUD arrow now uses committed-velocity projections
   on actual 3D camera RIGHT/UP/forward axes and `atan2(right, up)`; vertical or
   behind-camera near-zero depth falls back to projected up. Lifetime/role sizes
   unchanged.
3. Target movement, not NPC movement, controls the existing rule. New native
   fixture uses real controlled-human input, motor ticks and published velocity
   against a fresh stationary NPC; no synthetic velocity or movement-code change.
   No new NPC balance rule; manual/runtime outcome must be verified in gate.
4. Ghost-facing visually confirmed by owner; unchanged this revision and remains
   a distinct `ghost-facing` criterion.

Manual GPU/audio/two-client visibility (including tiny wall/eye-gap occlusion)
remains unverified. No client was launched. Keep all earlier nine-point feature
coverage, crash fixes and package/provenance floors in the new complete gate.

## Current settled-gate findings and repair

`run_f72722149206de50b037242b` passed unit/readiness; native selected 41/1,371
with 39 pass / 2 fail. Package/provenance are not claimed fresh. The published
trusted NPC feedback event now permits null victim on a nominated miss but keeps
attacker required; actual-hit victim remains non-null. Consumers were audited,
with contract coverage in `TrustedNpcFeedbackPublishedEventTest`. Fixture
expectations now distinguish stationary target (no miss roll, HIT) from moving
target at chance 1 (MISS, swing, null victim, no damage), while chance 0 moving
target remains HIT. No balance, movement, disarm, or ghost behavior changed.

Fresh focused repair log `build/melee-owner-followup/npc-miss-repair-preflight.log`
plus three selected JUnit XML reports records 14 tests / three classes with zero
failures/errors/skips. This remains separate from 272-test preflight and
inheritance validation. A fresh all-pack replacement gate is pending.

## Current nine-point review

| Owner finding | Implementation / evidence boundary |
|---|---|
| Active pulling hand | Pinned SS14 deliberately permits unarmed fallback for virtual pulling items; retain admission, no offhand-switch rule. Source references in `PROTOTYPES.md`. |
| Wide weapon starts inside back/head | Capture source body bounds; fit actual model support outside the whole-AABB/lunge support plane without changing the accepted fan. Shared pose/neutral-geometry tests. |
| Light weapon starts behind body | Same bounded support fit; axially compress short presentation paths or suppress no-fit frames rather than extend the tip/reach. |
| Knife/crowbar pitch shifts | One shared world-axis transform for measured and drawn support; captured bounds roundtrip in observer payload. Tests include steep pitch, fan bearings and both swing directions. |
| Disarm flight invisible | Existing committed-release cue enlarged: 650 ms transient, 300 ms full visibility, longer clipped colored streak, larger outlined participant bearing and localized caption. No physics/network replacement or ground marker. |
| Silent Kobold retaliation | Resolved `MeleeAttack` audio/arc; shared damage/outcome/sound and observer publication. Pinned claw sprite and AlienClaw audio retained with attribution. Native fixture source exercises retaliation admission; execution evidence pending. |
| Other station structures sound like misses | Shared typed block-contact resolver adds explicit floor/airlock mappings, preserving construction policies and rejecting ambiguity. Authenticated nomination/range/state checks remain. |
| No crashes | Preserve both prior fixes: semantic BLOCK validation and `CollisionContext.empty()` disarm clipping. Owner reported no crashes in the preceding test. |
| Separate victim hit particle? | Not in pinned generic melee. Victim flash and attacker-origin moving fist/weapon are distinct; no invented victim particle. |

Actual GPU appearance, two-client cue noticeability and audio playback remain
manual observations, not conclusions from these source/tests. Native fixtures
compile but were not run by the author; the serialized gate must run required
headless checks. No interactive client launches are authorized without explicit
desktop readiness.

## Preserved preceding eight-point review

| Requirement | Current implementation / focused evidence |
|---|---|
| Simpler disarm visual | Original flat-white palm, CC0 metadata retained; `DisarmSimpleGlyphResourceTest` and prototype activation tests. |
| Initial flight direction, not ground marker | Actual post-publication release origin/velocity; canonical actor/victim controller delivery; brief clipped world arrow and participant bearing cue. No persistent loose-item beacon. `DisarmFlightNetworkingTest`, renderer/HUD/visual-state tests, and committed-release throw fixtures. |
| Consistent knife light origin | Frozen emission source and stable weapon frame, no alternating side/35° roll. `CapturedAttackFramePoseTest` and neutral geometry tests. |
| Consistent wide height | Shared captured chest origin and central head-local up; scaled model-support anchor agrees with the drawn tip. Pose and geometry tests. |
| Wider sweep | Resolved human/base melee angle 120° total; knife/crowbar inherit it. `MeleeRosterCompositionTest` and weapon/schema tests. |
| Faster non-linear wide motion | Existing timeline consumes pinned SS14's fast sweep/settle keyframes, without altering cadence. `MeleeStrikeTimelineTest` and live endpoint sampling tests. |
| Pitch-invariant coverage | Shared head-local fan, resolved origin-height ratio 0.6, nominal range, independent per-ray clipping; client nominations and capture use the same math. `MeleeArcGeometryTest`, standing/ray tests, and runtime consumer tests. |
| Crowbar-wall crash | Semantic BLOCK admission before strict nomination; MISS remains MISS even if its endpoint cell is structural. `ClientBodyEntityRayTest.structuralNominationRejectsMissEvenWhenItsPositionIsStructural` reproduces the report's failure family. |

The later **two-client disarm crash** was a different defect: null Entity passed
to `ClipContext`. `DisarmFlightRenderer` now uses `CollisionContext.empty()`;
`DisarmFlightRendererTest.terrainClipContextUsesEntityIndependentCollisionContext`
constructs the real context that previously threw. Both actual reports are
`crash-2026-10-29_01.52.33-client.txt`, under `run/crash-reports` and
`run/client2/crash-reports`.

## Preserved original review scope

- Shared public character names in disarm messages; no raw harness IDs.
- Plastic jug provenance/yield preserved, not substituted glass.
- Committed stamina damage, existing resistance/status rules, normal held-input
  cadence and release/menu cancellation; no second stamina or attack pipeline.
- Actual prediction audio receipt prevents duplicate initiator swings; resolved
  wall/plastic/girder contact cues and normal damage/impact routing remain.
- Shared item damage feedback flashes the real jug red and restores its tint.
- World-syringe examination consumes the registered solution provider and shared
  formatter. No unsupported generic integrity display or held-stack entry path
  is claimed.
- Armed effects replace fist particles and use the captured item; no species,
  gear, hand or character animation rig was introduced.
- Thrown-knife damage and guns remain outside scope.

The new **stamina row** reuses the existing health/hunger/thirst HUD, localized
labels, resolved capability, and synchronized attachment/neutral EMPTY state.
`BodyStaminaHudPolicyTest` and `BodyHudSourcePolicyTest` cover projection and
source selection. It does not predict regeneration or add physiology networking.

## Preceding candidate evidence (not fresh follow-up validation)

- `build/melee-strike-checks/owner-ready-final-preflight.log`: all three source
  sets compile; 163 suites / 609 focused tests pass, zero failures/errors/skips.
- `build/melee-strike-checks/owner-ready-required-boundaries.log`: a separate
  follow-up invocation passes all 57 tests in the three remaining prior-required
  fixture/catalog, construction-prototype, and body-hand policy classes. These
  are not combined into a fictitious single-run total.
- PowerShell parsing and all 14 pure capture-proof checks pass. These are
  validator tests, not game images.
- Independent source reviews cover live release/network/render/HUD paths,
  localized public names, weapon poses, and the original wall-crash correction.
- Historical selected UI readiness and native passes are recorded in
  `FEEDBACK.md`; the replacement gate must run its own typed checks.
- No complete current 20-scene/60-image capture, actual two-client GPU retest,
  subjective appearance/audio approval, or owner acceptance is claimed.

The retained capture fixtures stop on focus loss and never grab the mouse or
close screens to recover control. No client is to be launched while the owner
is using the desktop without explicit readiness. Supported OWNER_READY packs
run headless unit/readiness/native checks plus fresh package/provenance checks;
they are not a workaround for the paused interactive capture.
