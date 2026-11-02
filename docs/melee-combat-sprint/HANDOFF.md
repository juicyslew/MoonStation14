# Melee combat and controlled-creature follow-ups

## STATUS

**Closed by owner acceptance on November 1, 2026.** The owner explicitly requested
no further validation submission. Acceptance includes the melee work and requested
ghost-role expansion and Kobold emote-name corrections. Changes remain uncommitted;
closure does not authorize staging, committing, merging, or further validation.
The worktree contains unrelated changes; preserve them, including the pre-existing
staged `docs/merge-main-capabilities/VALIDATION.md` changes.

## ARCHITECTURE

- Combat authority belongs to the authenticated controlled body and session epoch,
  not its parked player carrier. Controlled and NPC melee consume resolved typed
  profiles/components. Legitimate handless unarmed attacks use body-owned cooldowns
  and the unarmed `("", 0, null)` binding, without inventing Hands. Observer payloads
  permit an empty active-hand ID only when no copied weapon is present.
- Armed effects replace fist effects. Captured source bounds, endpoints, direction,
  rotation and timeline keep strikes independent of subsequent actor movement.
  Observer weapons retain uniform profile scale and leading-tip anchoring;
  melee-scoped quad clipping hides geometry behind captured body support plus the
  current visual lunge projected onto the sampled stroke direction. Do not use the
  future-lunge-padded emission origin as that clipping plane.
- First-person weapons retain constrained fitting and close-contact camera
  near-plane clearance. Ordinary world-item rendering and accepted item flight
  bypass the observer clipping adapter.
- Combat-mode empty-hand ground-item clicks reuse existing pickup nomination and
  dispatch. Admission requires confirmed current body/epoch/hand proof and no held
  or virtual pulling occupant. Living portable mobs remain attack targets; eligible
  dead portable bodies may be picked up. Gun/modifier/UI precedence remains intact.
- Kobold, Monkey and Scurret inherit supported interaction/combat composition from
  `supported_animal_ancestor`. Eye/speech presentation heights are Kobold 1.19/1.42
  and Monkey 1.06/1.23; native eye dimensions refresh on entry/catalog changes.
  Animal held-item PALM transforms avoid the humanoid arm-to-hand translation.
- Voluntary throws still use `BodyLooseThrow`. Initial placement and both strict
  revalidations now share `handOrigin`: mixed float/double subtraction previously
  rejected Monkey kitchen-knife throws without movement. Equality, authority,
  whole-stack transfer and flight rules were not relaxed.
- Native lifecycle bindings identify control ownership, not the previous human's
  personal name. `CharacterPublicName` uses saved account/profile personal names
  only for profile-harness bodies, including corpses. Native bodies use their own
  resolved/native names; explicit custom names remain highest priority. Emotes,
  examine and other shared public-name consumers reuse that resolver.
  **Subsequent correction:** this closure-time harness-class workaround was rejected
  in the separately authorized character-identity mini-sprint. Its current ownership
  repair is indexed by `docs/character-identity-mini-sprint/HANDOFF.md`.
- Separate UI fix: `GhostRoleMenuWindow` now advertises actual pointer capture, so
  registry release ends title drags and shared relayout can resize expanded rows.

## IMPORTANT FILES

- `docs/melee-combat-sprint/{PROTOTYPES,STRIKE-PRESENTATION,MANUAL}.md`: parity,
  presentation contracts and manual actions. `FEEDBACK.md` and `OWNER-READY.md`
  retain historical candidate evidence; pending statements there are historical.
- `ms14/combat/client/{MeleeArcClient,CapturedAttackFramePose,NeutralMeleeStrikeEffectGeometry,MeleeQuadBufferSource,MeleeQuadClipper}.java`
  beneath `src/main/java/com/juicyslew/moonstation14/`: captured rendering owners.
- `ms14/hands/client/{BodyHandClient,PrimaryWorldGesture}.java` and
  `ms14/hands/live/BodyLooseThrow.java`: input routing and canonical release.
- `ms14/character/CharacterPublicName.java`, `ms14/effect/EmoteEffect.java` and
  `ms14/actions/BodyActionEffects.java`: public identity and scream delivery.
- `ms14/ghost_roles/client/{GhostRoleMenuWindow,GhostRoleMenuPresentation}.java`;
  `docs/ghost-role-ui-followup.md` and `docs/emote-identity-fix.md`: side-fix boundaries.
- `NativeCreature{Combat,Disarm,Throw}GameTests`,
  `AnimalPresentationAnchorGameTests`, and existing registered body-action fixtures.
  `CharacterPublicNameTest` covers the saved-human/native-name regression.
- Start shared subsystem work from `docs/hands-inventory-program/HANDOFF.md`,
  `docs/nonhostile-animals-sprint/HANDOFF.md`, and `docs/chat/HANDOFF.md`.

## SS14 CORRESPONDENCE

Pinned reference: `SS14-dev/space-station-14` at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`; verify before new parity work.
`PROTOTYPES.md` records resolved ancestry, defaults, assets and deliberate Minecraft
adaptations. Pinned `CombatModeCanHandInteract`, client light-attack interception,
`SharedItemSystem.OnHandInteract`, and MobState cancellation establish combat-mode
pickup precedence. Gloves do not occupy the active hand; pulling virtual items do.
Do not infer SS14 behavior from SS13 harm intent.

Resolved melee tuning, sounds and sprites remain data-driven. The 120-degree fan,
0.6 body-height wide origin and copied-weapon presentation retain the accepted
project adaptations. No speculative Thrower component or species throw pipeline
was added. Retain existing asset attribution.

## DECISIONS / WHY

- Crop rigid observer geometry rather than squash it, extend gameplay reach, or
  introduce character animation rigs. The adapter preserves complete quad
  attributes, handles retained base/glint consumers and counts emitted vertices.
- Preserve accepted close-wall first-person rendering, NPC moving-target miss
  behavior, ghost-facing and release cues. NPC motion is not the miss condition.
- Pickup stages a melee selection cooldown on knives. Throw fixtures must compare
  output strictly against the authoritative post-pickup held stack, not the
  pre-pickup fixture stack. Keep independent marker/count/no-duplication checks.

## KNOWN GAPS

Ordinary local-speech identity payloads use a separate ledger path; the emote-name
fix does not redesign that path. Automated CPU tests do not establish GPU/audio,
OS-input or crowd behavior. No interactive client was launched by this work.
Thrown-knife damage, guns and movement overhauls were not added.

## VALIDATION

- `run_d5448be4fab640a96a101cd8` passed the preceding complete gate; the owner then
  confirmed observer swings and combat pickup.
- Exact knife diagnostic selected 2/1,379: cobblestone passed; knife reproduced the
  origin-revalidation denial. The shared arithmetic fix addresses that failure.
- Later `run_9b9d8304b17fb38f668a4032` passed unit/readiness but only 49/50 selected
  native tests. Its remaining failure was the knife fixture's pre-pickup baseline;
  that assertion was corrected without changing production release behavior.
- Interrupted submission `run_5daea9f42640c326ef0c0bec` recorded 50/50 selected
  native passes of 1,379, but its terminal verdict is **ERROR**, because source
  changed from its admitted snapshot. It is not a complete technical PASS.
- Final fixture-focused preflight: 75 tests across six classes and three compiles
  passed. Emote correction: 11 tests across five suites and three compiles passed;
  independent source review passed. Evidence is in ignored build output under
  `build/melee-owner-followup/` and `build/emote-identity-fix/`.
- Owner manual acceptance closes the sprint; it does not convert the ERROR or
  unrun integration boundaries into automated passes.

## NEXT WORK

No further work or submission is authorized for this closed sprint. For future
authorized validation, resolve the earlier ERROR through the existing recovery
route; never replay the interrupted request or claim its native receipts as a
whole-gate PASS. Begin new subsystem work from the indexed handoffs and preserve
the accepted shared owners above.
