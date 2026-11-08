# Species-independent strike presentation

## November 1 observer correction (validation pending)

Observer weapons no longer use axial compression. `MeleeArcClient` anchors the
same uniformly scaled model at its captured animated tip, then scopes a
`MeleeQuadBufferSource` to that weapon draw. `MeleeQuadClipper` crops complete
attribute-rich quads behind the captured AABB support plane plus the current
arc's visual lunge projected on the sampled stroke direction. Camera-relative
vertices use the correspondingly translated plane. Padded emission origins must
not push that plane forward. The accepted first-person constrained/near-plane
branch and ordinary world-item/flight rendering are unchanged.

CPU geometry/stream checks do not prove GPU appearance. Earlier axial-fit
descriptions below are historical for observers; they still describe the
preserved first-person fit where applicable.

PASS head `run_69c651d34abe4008cace3223` was reopened; it is not acceptance. This
revision removes the rejected miniature camera-corner weapon: the actual copied
weapon uses the captured animated tip, scale, rotation and timeline. Near-wall
first-person fit uses the view near-plane intersection; the world retains AABB /
lunge fit. This does not remove an older generic `renderProjection` adaptation
outside the normal strike branch. HUD flight arrow uses actual 3D camera axes;
approved world arrow is unchanged. NPC miss condition is target movement (not NPC
movement); no new balance change. Ghost-facing remains a distinct criterion but
was owner visually confirmed and is untouched. GPU/audio/two-client review remain
unverified.

## Current armed-effect requirement

Owner review of the live capture rejected placing a weapon over the fist effect.
For armed attacks, **the weapon replaces the fist crescents and their guides**.
The blade end, not merely the sprite-plane normal, must point predominantly
toward the attack. A light knife attack advances blade-first and withdraws with
its handle trailing; a wide attack sweeps the actual weapon along the captured
fan. Do not make an upright weapon float forward like an offered item.

The copied stack, resolved profile, shared item renderer, frozen strike frame,
and existing timeline remain the owners. `weapon_pose` describes the local
generated-icon forward axis and a small oblique presentation angle; it does not
change targeting. Actual transformed baked-model support anchors the leading
vertex at the bounded attack tip. The shared renderer supplies actual submitted
vertex counts rather than retaining invisible punch geometry for diagnostics.
Unarmed effects and ordinary held-item rendering remain independent of this path.

### Current body-clearance correction

Leading-tip anchoring alone allowed the model's rear support to start behind the
source. Strikes now capture the physical source AABB and transmit it with observer
geometry. Each sampled direction derives its rear-clearance plane from the
captured AABB's furthest projected support and existing lunge envelope, not merely
the ray's exit point (which can leave a corner/head intersecting a wide model).
Actual transformed support is fitted between
that plane and the animated tip. Full fitting weapons retain scale 2; shorter
paths compress only along the world-space attack axis, and no-fit frames suppress
the weapon instead of moving it through the body or beyond the endpoint.
`CapturedAttackFramePose.axialTransform` is shared by measuring and drawing.
Gameplay reach, the accepted 120° fan and 0.6 chest origin remain unchanged.
These presentation adaptations still need real GPU/owner review.

NPC claw feedback uses resolved profile-selected textured strike presentation,
not a rig or a generic victim-impact particle. Target-directed capture does not
rotate the real NPC or alter player crosshair-directed capture. Existing fists
retain their procedural trail and held weapons retain weapon-only presentation.
The source claw art is a four-frame 2×2 atlas, not a single full-sheet sprite;
typed glyph frame/column data selects one frame over its 0.399-second lifetime.

### Follow-up readability corrections (GPU validation pending)

The first weapon-only capture produced 33 images before the jug readiness
fixture stalled. Wide-frame pairs show the copied knife changing sides without
fist particles, but close-wall fist fill, the close observer's source cue, and
the dark disarm hand did not meet readability review.

The revised unarmed short-contact path retains the outer crescent radius while
reducing filled band area and redundant overlapping strokes. Its source guide
uses the existing normal-depth emissive material. The disarm hand uses that same
material behavior, normalized fade opacity, and the existing extension/withdrawal
sample. These are presentation-only corrections; captured reach and gameplay
authority are unchanged. Fresh images are required before visual acceptance.

The complete follow-up capture still failed visual review. The next candidate
supersedes the full-radius short-contact rule: radius follows captured available
length between a visible `1.5 × trailWidth` floor and the ordinary
`3 × trailWidth` maximum, rather than becoming a point-sized mark. Weapon copies
use resolved uniform effect scale 2, applied identically to measured support and
drawing. Disarm is one view-facing quad at its frozen world-space trajectory
position, not overlapping planes. Facing the art toward the view does not change
source, endpoint, or gameplay direction. These Minecraft adaptations require new
GPU review.

## Agreed scope

Owner-approved follow-up for **issue (1) only**: replace the floating primary
swing effect with a clear, independent 3D slash/thrust and a short,
accurately bounded trail. The other reported issues remain parked; this work
does not close the sixteen-item feedback list or introduce a new hit marker.

**Owner clarification:** the actual character must not require new limb, clothing,
species, borg, creature, or prone animation rigs. “Connected” means the effect's
source is visibly near the attacker, not that a character limb must be animated.
This supersedes the earlier real-arm implementation choice.

## Acceptance

- Light strikes visibly begin near the attacker and move outward as an
  independent effect. Close observers can identify its source and direction.
- Observer motion preserves the attack's full pitch, including steep upward and
  downward aim. Model orientation is attack-relative, not camera-facing.
- Capture the effect's source, attack direction, and clipped geometry once.
  The emitted effect stays in that attack space; it does not chase a moving
  character or require updates when the character turns or changes posture.
- Wide strikes sweep through the existing fan, with a brief curved trail.
  Gameplay reach, fan angles, targeting, cadence, and damage remain unchanged.
- Trails communicate nominal reach and stop at blocking contact. They are
  presentation, not hit admission or an additional damage window.
- First-person framing may differ for visibility, but must communicate the same
  direction and bounded reach. Ordinary loose-item poses remain unchanged.
- Do not pose or replace character arms, clothing, or held-item layers. Use generic
  attack-space geometry and the existing effect renderer/profile owners; origins
  use captured body/eye data, never species-specific bones or player pivots.
- Keep ordinary character and held-item rendering unchanged. No new character
  model asset is required for the independent effect.
- Demonstrate outward, fully extended/swept, and fading frames of the same attack in real
  client frames, including close observers, pitched observers, wide attacks,
  a held weapon, and blocking contact. Pure geometry tests are not visual proof.
  The existing diagnostic's `_return` filename denotes its final fade sample;
  no character retraction animation is required.

## Architecture and parity

### Empty-hand body-relative observer arcs

Valid controlled bodies may use UNARMED melee without a `Hands` component; an
empty active-hand identifier is therefore legitimate for body-relative fist
presentation. Its `MeleeArcPayload` has no copied weapon, while an empty identifier
paired with a weapon remains invalid. Null, whitespace-only, and overlong hand
identifiers remain rejected. Do not add synthetic hands or inventory bootstrap to
make this visual payload nonempty: request authority remains bound to the resolved
body and UNARMED selection. Body-owned cooldown uses the revalidated selection's
hand revision (zero for handless bodies, real revision when hands exist), without
dereferencing absent live hands. `MeleeArcPayloadTest` covers roundtrip and negative
guards; `NativeCreatureCombatGameTests` asserts actual body publication, no
duplicate publication, no Hands, and cooldown behavior. This preserves the valid
empty-hand contract rather than weakening authority.

Reuse the melee arc queue, prediction/rejection/context rules, observer publisher,
typed arc profiles, and existing mesh submission. Keep body/gear rendering out of
the new animation path. Add
supported 3D timing/style data to resolved arc profiles rather than hardcoding
human/weapon-specific animation parameters. Use one shared full-pitch geometry
capture for prediction and observer snapshots, and the existing physical reach
and block-ray kernels for presentation bounds.

The existing species-neutral SS14-style whole-body lunge remains baseline
behavior. The new effect neither drives that transform nor attaches itself to it.
Its primary stroke travels outward/sweeps, then dissipates without retracting
through the character; a faint bounded guide is secondary to the short stroke.
When space permits, the captured emission point is just beyond the physical
body bounds and existing lunge envelope along the attack ray, never past blocking contact. This avoids
starting the visible stroke inside a head or torso without inspecting a model.
Near contact, source advancement is limited to 60% of the eye-to-contact path
to preserve visible travel; this does not extend the captured endpoint or reach.
Those source-advancement rules apply to light attacks. Wide attacks instead
capture a chest-centered emission origin from the resolved weapon's
`wide_origin_height_ratio` (0.6), retaining separate eye/look aiming. The client
nomination fan and all wide weapon/trail geometry share that origin, configured
120° total angle, head-local frame, nominal range, and per-ray clipping.
Faint crescent ghosts and an open crossed-quad guide replace capped trail boxes.
The crescent retains its transverse profile during travel; only its axial depth
is constrained between the frozen source and advancing tip. Short contact paths
still cap the profile, and zero travel emits no ribbon.
Primary crescents use the engine's unlit translucent effect material with normal
depth testing; they do not create world light or draw through walls.

The independent 3D stroke/trail is an explicit Minecraft adaptation. Pinned SS14
`c9df5ef5d675b0d1d226828bddf6b78c28502d91` uses a static `None` fist mark,
copied weapon sprites, thrust/slash animations, and a separate body lunge.
See `PROTOTYPES.md` for resolved source compositions. Relevant consumers are
`MeleeWeaponSystem.Effects.cs:21-129,209-231` and
`SharedMeleeWeaponSystem.cs:413-477,966-990`.

Preserve the existing nomination trust boundary: the server checks ownership,
cadence, range, LOS, map, and eligibility, not an independent fan-angle check.
Keep the five-target cap and existing wide reach tolerance. Do not change audio,
stamina, disarm admission/tracking, names, examine/chemistry, jug behavior,
thrown-weapon damage, or wide-angle values in this follow-up.

## Owner manual review

Candidate visual review passed for all nine outward/full/fade image trios from
diagnostic `run_b357ba4f38999be18d2db570` (27 PNG hashes verified). Captures:
`build/reports/melee-visual-capture/746857038b6f44b08f561e8efa5d4c54/manifest.json`.
This is screenshot review, not owner acceptance or the owner-ready gate.

After the owner-ready gate passes:

1. Enable combat mode. Try light and wide attacks with empty hands and a knife,
   in first and third person; check source, direction, reach, sweep, and fade.
2. Have another player attack beside you, then aim up and down. Check the slash
   stays readable near the attacker without depending on character or gear poses.
3. Attack toward a nearby wall and while moving/turning. Confirm the emitted slash
   stays in its captured attack space and does not draw through the wall.

Report unclear views. Screenshots do not establish OS input, audio audibility,
crowded multiplayer readability, or owner acceptance. Other feedback is parked.
