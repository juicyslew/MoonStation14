# Melee combat sprint

**Closed by owner acceptance, November 1, 2026.** See `HANDOFF.md` for final state,
architecture and validation limits. No further validation submission was requested.
The candidate/reopened-review sections below are historical.

## Goal and status

Deliver a coherent, usable melee experience through ordinary gameplay—not merely
exercise the incoming combat classes. **Owner-feedback revision in progress; not accepted.**
Existing code is a candidate for reuse, not evidence of accepted combat gameplay.
The merge's 1,343-case diagnostic PASS is a regression baseline, not acceptance
of this sprint or proof that its controls and presentation work for a player.

### Reopened complete review

After `run_cd54bb5b0afb29c9740eb571` passed technical readiness, owner testing
reopened the complete sixteen-item review and added distinct disarm, recognizable
weapon, true sweep, close-contact sizing, and larger-lunge requirements.
`FEEDBACK.md` is the item-by-item scope index. Thrown-weapon damage remains
explicitly deferred. The preceding strike-only gate is not acceptance or proof
that this broader revision is ready.

Current work order:
1. Complete shared attack/disarm presentation and deterministic captured geometry.
2. Fix identity, held-disarm dispatch, sound ownership, and actual-item location cues.
3. Reconcile jug/wall contact, stamina, hit/examine parity, and syringe contents.
4. Compile and run focused coverage, resolve independent review, then obtain fresh
   serialized real-client evidence and a complete owner-ready replacement gate.

The first feature gate (`run_4846ac55815c79c81e08c9c1`) passed, but owner testing
found insufficient first-person feedback and missing target/input behavior. That
PASS does not validate this follow-up revision.

### Owner-feedback follow-up

Second owner test: `run_49a94c4f01b86bb7b5d2015b` passed its technical gate,
but the owner still saw no swings, reported silent player disarm and awkward
hit admission, and requested clearer ineffective-contact feedback. Acceptance
remains pending. The next revision must exercise real client rendering and
production input, not infer visibility from CPU geometry or server-only tests.

Current corrections/verification:
- Connected diagnostic `run_5658f53694969cbe1cba023c` failed during registered
  peer setup (`startup=UNINITIALIZED`, no CHARACTER Begin). Its actual GPU
  captures showed the fist sprite in first person and own front-third-person.
  Disarm, knife, and remote observer captures did not complete; this is partial
  visual evidence, not readiness or acceptance.
  The diagnostic must await the real lifecycle post-tick CHARACTER Begin before
  driving peer Ready. Its launcher requires tracked/eligible body control and
  authenticated melee outcomes, not an unrelated movement-intent marker.
- Follow-up diagnostic `run_ad98177c4ae04b2e8abe01b6` reached a committed peer
  but stalled before a disarm request. Source tracing established that local human
  composition omitted the inherited SS14 `canDisarm: true` field. Restore it on
  the human prototype, preserving null/false defaults and target eligibility.
- `run_637042f0a310d6a8811ff90a` completed all five captures and an accepted
  `DISARM_FAILED` roll, but its workflow verdict was ERROR: success cleanup deleted
  the fixture before copying the promised client/server log artifacts. Preserve
  those current-run logs before deletion. Observer capture also needs to wait for
  the peer's client-side pose and the previous local animation to settle.
- `run_d5e3de89de23836400ae8a4f` passed the five-case connected diagnostic.
  Current PNGs and hashes were reviewed: first-/third-person fists, first-person
  knife, real peer-body disarm (`DISARM_HANDLED`), and a visible remote body/fist
  on the world rendering branch. This proves event-injected gameplay/network/GPU
  paths, not OS input, sound listening, item-throw success, or owner acceptance.
  Near/edge human light hits and wall contact remain the next focused captures.
- `run_4cde276ebb4531f7b366d0f4` passed the expanded eight-case diagnostic.
  Reviewed near/edge human LIGHT-hit PNGs show both red damage tint and the fist
  effect; wall contact shows the fist effect. The edge scene uses two-block
  center spacing (1.4 physical AABB gap) with the unchanged 1.5 configured reach.
  Full feature readiness and owner acceptance remain separate pending steps.
- Owner-ready gate `run_fb165887d003a301c6df514d` passed JVM/readiness steps but
  failed one of 29 selected native fixtures: the new knife-wall case attacked
  immediately after pickup, before the source's hand-selection cooldown expired.
  The fixture must wait for that real deadline; gameplay cooldowns remain unchanged.
- Activate arc resources through the registered production resource directory and
  reload/catalog sync; test that all three concrete profiles resolve from the four
  raw definitions (the static parent is abstract).
- Make world-view attack sprites readable billboards as well as first-person
  presentation. Reuse the SS14 fist/weapon effects; no new model is currently needed.
- Use the configured no-damage cue for fully resisted contacts, consistently in
  light and wide attacks; record the pinned upstream light/wide asymmetry.
- Exercise disarm between two registered player-controlled bodies and capture the
  actual client nomination/reply path before changing eligibility or reach.
- Default hold-to-attack **on** for new client settings, preserving explicit saved
  settings. This is an owner-requested Minecraft usability departure.
- Keep critical hits and stamina-cost jumping deferred, along with harm-mode HUD
  coloration/status icons and gun feature work.

- Make fist and weapon attempts, including misses, readable in first person using
  the existing SS14 arc presentation. Preserve observer presentation. Confirmed
  positive damage should produce target feedback, not a predicted phantom impact.
- Preserve pinned animal eligibility: pigs and chickens have stamina but no hands,
  and their inherited allowed effects exclude `KnockedDown`; they are not valid
  disarm/shove targets. Do not broaden eligibility to all living entities.
- Resolve jug damageability through strong-plastic inheritance and use the existing
  loose-item typed damage/destruction owner, including normal versus overkill rules.
- Add contact nomination for the existing steel wall, plastic wall and girder.
  Their inherited `StructuralMetallic` reduction absorbs this roster's damage:
  contact feedback is appropriate, but damage flash or destruction is not. General
  block integrity, arbitrary terrain damage, and new windows/grilles are not implied.
- Expose SS14's default-off client hold-to-attack preference; independently honor
  resolved weapon `autoAttack`. Retain the existing cooldown and input owners.
- **Deferred by owner:** harm-mode HUD coloration and a top-of-screen status-icon
  row. Neither belongs to this revision. Guns remain deferred.

## Owner decisions

- **Melee only. Guns are the next sprint**, with no gun feature work here.
- Full core melee: combat mode, unarmed and weapon attacks, light/wide attacks,
  disarm and the applicable stamina/knockdown outcomes.
- Representative weapon set, not every melee-capable item.
- **Confirmed controls:** left-click light attack; right-click wide attack, or
  disarm when unarmed and eligible. The owner selected these after direct client
  and `Resources/keybinds.yml` verification. The earlier right-plus-left chord
  proposal was incorrectly described as SS14 behavior and is superseded.
- Normal gameplay aims with the existing center-screen crosshair. No additional
  free-pointer combat mode is proposed. The earlier off-center-pointer question
  introduced an unnecessary distinction.

Shoving is not a separate button or guaranteed spatial knockback. At the pinned
April 20, 2026 SS14 revision, successful disarm first offers the target's active
held item to the hand-system throw outcome. If handled there, it does not also apply the
fallback shove/stamina outcome. Stamina handling can otherwise produce a stun.

## Scope

Starting roster: **human fists, crowbar, kitchen knife**. The latter two are
already registered locally. Resolve their full upstream/local composition before
finalizing values; add another representative only if these cannot exercise a
required core behavior. Do not silently promise every imported weapon works.

Include:
- Capability-gated combat toggle, discoverable HUD action and configurable binding.
- Unarmed/held-weapon resolution, accepted-attempt cooldowns, hits and misses.
- Single-target light attacks and genuinely multi-target wide attacks.
- Eligible disarm with authoritative chance, active-hand ownership and fallback
  stamina/knockdown effects.
- Existing typed damage, armor and status-system integration; relevant blunt
  stamina effects. No parallel damage or inventory ownership implementation.
- Immediate, reconciled local feedback and correct observer sound/visual delivery.
- Player and NPC targets through their existing damageable body composition.

Exclude guns/ammunition/projectiles, grenades, a complete arsenal, new NPC combat
AI, parrying/blocking combos, a new armor system, and new limb-targeting mechanics.
Do not delete or finish unrelated incoming gun code. Shared input routing may be
adjusted only as needed to give melee its correct, non-conflicting gesture path.

## Parity baseline

Configured upstream checkout directly verified clean at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91` (April 20, 2026), at
`C:/Users/William/Documents/SS14-dev/space-station-14`. It contains `DoDisarm`,
`AltDisarm` and the hand/stamina disarm subscribers. The owner confirmed this is
the intended checkout. Direct inspection additionally verified human inheritance
of `CombatMode.canDisarm: true`, live client dispatch and the server handlers;
these are not merely unused component names. No checkout files were modified.
Primary references in `SS14-dev/space-station-14`:
- `Resources/Prototypes/Body/Species/human.yml` and `Body/species_base.yml`:
  representative human combat composition is inherited, including unarmed melee.
- `Resources/Prototypes/Entities/Objects/Weapons/Melee/knife.yml`: KitchenKnife
  inherits BaseKnife and BaseItem; knife damage/sounds are composed data.
- `Content.Shared/Weapons/Melee/{MeleeWeaponComponent,SharedMeleeWeaponSystem}.cs`
  and `Content.Client/Weapons/Melee/MeleeWeaponSystem.cs`: defaults, selection,
  attack admission, cadence, light/wide resolution and prediction.
- `Content.Shared/CombatMode/`, server `Hands/Systems/HandsSystem.cs`, and
  `Content.Shared/Damage/Systems/SharedStaminaSystem.cs`: combat mode and ordered
  disarm outcomes.

Confirmed reference values include human unarmed blunt damage 5, attack rate
1/sec, component-default range 1.5 and wide arc 60 degrees (the human base overrides
its angle to 30 degrees), maximum five wide targets, and
default disarm failure chance 0.75. BaseKnife supplies slash damage 10. These
are source references, not permission to hardcode an implementation. Complete
the full relevant parent chains/default mapping—including crowbar—before changes.

SS14's 2D geometry requires a deliberate Minecraft adaptation. Reuse controlled-
body aiming and native collision/LOS services; specify vertical reach and wide
geometry explicitly. No infinite-height sweep, wall penetration, or targeting
from a detached spectator carrier. Preserve SS14 values where practical; record
any necessary departures with rationale rather than silently making them vanilla.

### Wide-attack authority boundary

Preserve the pinned SS14 client/server split: the client samples the resolved
weapon fan; the server validates nominated targets for range, obstruction, map,
target eligibility, source ownership and cadence, with the upstream +0.1 wide
range tolerance and five-target cap. **Independent server-facing/fan-angle
enforcement is not an acceptance requirement for this sprint.**

At the pin, `Content.Server/Weapons/Melee/MeleeWeaponSystem.cs:37-54` explains
prediction divergence and explicitly leaves arc validation as a TODO; its active
check is unobstructed range. The shared handler and heavy path
(`SharedMeleeWeaponSystem.cs:221-232,587-712`) do not add another orientation gate.
The port's finite 3D reach and client pitch-preserving fan do not introduce one.
Rejecting nominations against current server facing would be a separately scoped
hardening/prediction change, not a missing SS14 parity feature. Ordinary-client
fan selection and authoritative range/LOS rejection must still be tested.

## Ordered work units

### 1. Composition and live-path audit

Verify current source rather than stale completion notes. Map resolved human and
roster-item parent chains, components, defaults and actual runtime consumers.
Establish capability availability, keybinding defaults and client/server profile
sync. Inventory gaps without treating class names or fixture profiles as delivery.

Reuse candidates: `combat/CombatModeSystem`, `CombatModeRequestService`,
`CombatModeClient`, `combat.client.CombatModeHudAction`, `actions/BodyActionService`,
`MeleeWeaponResolver`, `ClientMeleeWeaponResolver`, `MeleeWeaponProfile`,
`MeleeWeaponData`, their merge strategy, and `PrototypeRuntime`.

### 2. Ordinary controls and authoritative attack loop

Extend `BodyHandClient`, existing pointer/gesture admission, `MeleeAttackClient`,
`CombatMeleeTargetProbe`, `MeleeAttackRequestService`, `LiveMeleeAttackService`
and `MeleeCooldownSystem`; do not create another input or attack dispatcher.
Use the confirmed direct secondary control: right-click performs wide attack or
eligible unarmed disarm. Resolve pointer/context admission through shared owners
so one gesture does not also activate menus, item use or unrelated ranged
requests. Preserve noncombat interactions and normal UI input capture.

Validate light/wide targeting, misses, cooldown, body/session/hand revisions,
range, LOS and multi-target selection on the server. Document 20 Hz timing and
3D geometry adaptations. Client nominations never establish successful hits.

### 3. Outcomes and disarm

Consume resolved roster composition through existing melee events and
`DamageSystem`, armor/status/stamina owners. Complete `DisarmSystem`/`DisarmPolicy`
against pinned eligibility and event ordering. Reuse canonical hand transfer and
throw owners: exactly one item owner and no duplicated inventory or outcomes.

### 4. Feedback and reconciliation

Complete existing `MeleeArcClient`, `MeleeArcObserverPublisher` and attack-client
presentation. Retain SS14 sounds/sprites and attribution; unclear audio provenance
goes in `docs/audio-unknown-provenance.md`. Show accepted swings/misses promptly,
reconcile denied/stale requests, and prevent predicted/authoritative duplication.
Visual review and live sound playback remain separate from resource tests.

### 5. Integration and owner demonstration

Test the actual registered-player entry path with resolved production profiles,
not only scoped fictional composition. Cover fists and both weapons, light/wide
hits and misses, obstruction/vertical reach, cooldown, armor/stamina, disarm
outcomes, ownership, stale requests and observer delivery. Preserve valid negative
tests and prototype/schema validation. Use owned fixture bounds and cleanup.

Run focused JVM/prototype checks and applicable `ui`, `interaction`, `prediction`
readiness selectors; extend coverage where changed behavior is not exercised.
At implementation start, follow the shared author workflow for serialized heavy
validation. A diagnostic PASS is never owner acceptance. Review reuse and parity
independently before offering a concise playable demonstration.

## Acceptance

- Ordinary controls expose the agreed melee loop without dev-only request injection.
- Every roster behavior consumes resolved prototypes, including inherited defaults.
- Damage/disarm are authoritative; prediction is responsive without phantom results.
- Hits, misses, cooldown and disarm communicate clearly to attacker and observers.
- Combat-off interactions, UI capture and hand ownership remain correct.
- No gun features or unrelated systems are pulled into this sprint.
- Report precise automated coverage and unverified client/visual boundaries.
- Owner acceptance is explicit; no broad manual regression checklist is required.

Minimal demonstration: toggle combat, punch, use crowbar/knife with light and wide
attacks, try disarming a held item and an empty-handed target, then toggle combat
off and use an ordinary item. Keep iteration focused on these new interactions.
