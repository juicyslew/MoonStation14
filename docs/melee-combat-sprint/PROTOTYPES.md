# Melee prototype mapping

Reference: local SS14 checkout `C:/Users/William/Documents/SS14-dev/space-station-14`, verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## November 1 interaction/presentation correction

Exact-item follow-up: generic/cobblestone native success did not cover the knife.
The knife's resolved `.08f` physical height exposed mixed-precision arithmetic in
the shared throw origin. `BodyLooseThrow.handOrigin` now owns initial placement
and both unchanged-equality revalidations. This is a numeric bugfix, not a new
prototype capability, species policy, balance change or launch-physics redesign.

Pinned `SharedInteractionSystem.CombatModeCanHandInteract` (lines 349–369)
requires Hands, an empty active hand, an Item target and no target cancellation.
`Content.Client/Weapons/Melee/MeleeWeaponSystem.cs:209–225` suppresses light
attack for that case. `MobStateSystem.Subscribers.cs:196–201` cancels for non-dead
mobs. `SharedItemSystem.cs:108–114` handles the permitted hand interaction through
the shared Hands `TryPickup`. Local routing reuses nominated pickup and confirmed hand proof;
wearable gloves are not held occupants, whereas a pulling virtual occupant is.

Monkey's audited ancestor supplies Hands and ComplexInteraction. The local
`BodyLooseThrow`/`HandActorAuthority` path is shared and has no species gate.
No speculative Thrower component or new throw physics is introduced. Native
release and desktop-input behavior require separate evidence.

Observer weapon clipping is a Minecraft presentation adaptation: keep the model
rigid at its resolved scale, anchor its leading support to the captured tip, and
crop behind captured body support plus the current lunge projected on the sampled
stroke direction. Do not reuse future-lunge-padded emission as the crop plane.
First-person fitting and the resolved combat prototypes remain unchanged. This
supersedes older observer axial-compression descriptions below.

## Current owner-authorized follow-up

No new NPC balance behavior is authorized in this revision. Existing 30% shared
miss rule is conditioned on the **target's** published movement, not NPC
movement; do not describe it as a rule limited to moving NPCs or add stationary
player behavior. New native coverage connects controlled-human accepted input,
motor ticks and published velocity to a fresh stationary NPC through the actual
trusted melee path. Synthetic positive velocity is not used. Existing NPC path
and attribution references remain `NPCCombatSystem.cs:107–115`,
`SharedMeleeWeaponSystem.cs:344–347,489–522`, and `Blackboard.cs:28–29` / operator
startup. Kobold default data is owner-verified 5 slash / 4 piercing
(`animals.yml:1599–1600`).

The approved orange world flight arrow is unchanged. HUD direction now projects
committed velocity against the actual 3D camera RIGHT, UP, and forward quaternion
axes; `atan2(right, up)` gives compass orientation, with near-zero projected depth
fallback for vertical/behind-camera cases. This is a Minecraft view adaptation,
not changed release physics or actor-role/lifetime policy.

The rejected miniature first-person near-wall weapon was removed. Normal strikes
continue to draw the same copied weapon at the captured animated tip/rotation and
scale 2/timeline. First-person close contact uses view near-plane intersection
clearance at physical gap <=0.2; world-space model fit remains based on body AABB
and lunge. Ordinary GPU near clipping may clip part of the weapon. This does not
claim removal of the pre-existing generic legacy `renderProjection` adaptation
outside the normal strike branch, and does not change reach, fan, or chest origin.

Ghost-facing is separately tracked. The production ghost sprite direction helper
must keep front/back stable for tangential facing across the existing 180° offset
and RsiDirection mapping (South 0, North 1, East 2, West 3), with focused helper
coverage. No asset or control changes are part of this correction.

## Reopened-review presentation mapping

The owner requested these deliberate Minecraft adaptations; validation of the
reopened revision is still pending:

- Optional `MeleeArcProfile.disarm_texture` and `disarm_frames` inherit from the
  local abstract arc parent and are consumed by predicted/observer presentation.
  The current candidate replaces the rejected complex UI-art derivative with a
  simple project-original white palm silhouette (CC0-1.0), because no suitable
  simpler upstream single-hand asset was established. Runtime
  `textures/effects/melee/source-meta.json` records this provenance; other SS14
  assets retain their existing attribution. It is not a new character rig. Upstream
  disarm selects the ordinary light animation; this distinct action cue is an
  owner-requested departure, not an upstream disarm-animation claim.
- Armed effects replace fist crescents with the captured item and shared
  world-item renderer. `weapon_pose.forward_degrees` maps the generated icon's
  grip-to-blade axis (45° for this roster); `oblique_degrees` is a small 20°
  Minecraft readability adaptation. Blade-forward projection remains positive
  and dominant. Wide appearance banking consumes the existing degree-valued
  `wide_animation_rotation`, separately from fan travel. Actual transformed
  model support bounds the leading tip. `weapon_pose.scale` defaults to 1 and
  the base arc configures 2 for the disembodied effect copy only; the same uniform
  scale is applied to measured support and shared drawing. Ordinary held/loose
  item scale is unchanged. This is a Minecraft readability adaptation, not an
  upstream component field. Shared counted rendering supplies real
  submissions. Light motion reuses the existing extension/withdrawal sample;
  wide motion follows the captured fan. No model stretching, character rig,
  gameplay angle/reach change, or fist-particle overlay is intended.
- Transverse crescent size comes from resolved strike style, independently of
  contact distance. Axial geometry remains source-to-tip bounded. Standard depth
  testing still applies; this is not proof that every transverse vertex has a
  separately sampled terrain ray.
- The shared lunge distance is now 0.22 rather than upstream 0.15, retaining its
  0.04-second peak and 0.10-second end. Captured emission clearance consumes the
  same constant. This is explicitly requested visual tuning, not SS14 values.
- The former 60-tick ground beacon is rejected by owner review and removed in
  the current candidate. Its replacement is brief authoritative release-direction
  feedback, based on the committed item's actual origin and initial velocity,
  delivered to both participants and observers. This is transient presentation,
  not a second item owner, through-wall glow, or altered throw physics.
  A quarter-second participant-only bearing cue also covers initially off-screen
  flight; it uses the same committed velocity and expires rather than tracking a
  grounded item. Recipient lookup uses canonical controlled-body bindings, not
  just the parked Minecraft player's position or current input/menu admission.

### New eight-point review: candidate adaptations (validation pending)

- The configured wide fan is increased to 120° total (±60°) for human unarmed and
  inherited base melee profiles, at owner request. Pinned SS14's human 30° and
  component fallback 60° are full widths; the port's 120° is deliberate tuning,
  not a corrected interpretation of upstream half angles.
- Following live-test feedback that the 90° eye-centered sweep appeared too
  narrow and high, `MeleeWeaponData.wide_origin_height_ratio` authors the wide
  origin at 0.6 of actual body-bounds height (about 1.08 m for a standing human).
  This new local typed field has no upstream 2D counterpart. Both captured wide
  geometry and client fan nominations must consume the resolved value through
  the same body-height helper; eye position remains separate for aiming and
  light attacks. No character rig or first-person-only visual offset is used.
- A shared head-local orthonormal frame preserves nominal lateral fan width at
  vertical aim. Display and client nominations use the same fan rays and clip
  terrain per ray rather than truncating every ray at the center crosshair hit.
  The nominal wide radius is the configured range; existing server physical
  range/LOS checks and wide tolerance remain authoritative. No independent
  server fan-angle enforcement is added, consistent with upstream nomination
  validation. This 3D head-axis geometry is a Minecraft adaptation.
- Pinned `MeleeWeaponSystem.Effects.cs:GetSlashAnimation` reaches half the angle
  at `0.10 × length`, all of it at `0.15 × length`, settles to 90% at 0.20, then
  eases to 80% at 0.60 with OutQuart; `length = 0.6 / attackRate` seconds. Reuse
  that fast sweep/settle in the existing timeline, separately from attack cadence.
- Repeated weapon light attacks use a stable captured source/frame; alternating
  swing side must not inject a ±35° weapon roll or vertical offset. Wide sweep
  poses share the central head-local up axis throughout the fan.

No thrown-weapon damage is introduced. See `FEEDBACK.md` for the complete list.

## Owner-feedback reference audit

These are upstream findings, not claims that the follow-up implementation is complete.

- `MobPig` and `MobChicken` inherit `SimpleMobBase` through `SimpleSpaceMobBase`
  and `BaseSimpleMob`. `MobDamageable` provides stamina, but `SimpleMobBase`'s
  explicit status-effect allowlist omits `KnockedDown`; neither animal has hands.
  The disarm admission at `SharedMeleeWeaponSystem.cs:871-907` therefore rejects
  them before chance resolution. Sources: `Entities/Mobs/NPCs/simplemob.yml`,
  `animals.yml`, and `Entities/Mobs/base.yml` under upstream prototypes.
  Local resolved pig/chicken profiles also deny disarm admission, but currently
  lack stamina composition rather than inheriting all upstream animal defaults.
  This revision preserves admission; it does not establish full animal stamina parity.
- `Jug` inherits `[DrinkBaseMaterialStrongPlastic, DrinkBase, DrinkVisualsAll]`.
  Strong plastic inherits `DrinkBaseMaterialPlastic`'s `Damageable(Inorganic)`;
  its own ordered destruction thresholds are 200 (destruction only) and 20
  (`MetalCrunch`, volume -4, spill, random 0–1 `SheetPlastic1`, destruction).
  Source: `Entities/Objects/Consumable/Drinks/drinks_base_materials.yml:81-154`.
  A missing direct `Damageable` declaration on Jug is not evidence of immunity.
  Local melee mapping uses `plastic_drink_container` →
  `strong_plastic_drink_container` → `jug` loose-object profiles. A 0.5 normal
  destruction-output probability for one registered plastic item represents the
  upstream uniform 0–1 yield. Existing jug throw/break-on-impact behavior is not
  changed to reproduce all upstream material collision defaults in this revision.
- `WallSolid` and `WallPlastic` inherit `BaseWall` → `BaseStructureWall` →
  `BaseStructure`; `Girder` inherits `BaseStructureDynamic`. All three use
  `StructuralInorganic` and `StructuralMetallic`. The latter subtracts 10 from
  blunt/slash before clamping positive damage to zero. Fists 5, crowbar 8 and
  knife 10 therefore contact-hit but cause no structural damage. Sources:
  `Entities/Structures/Walls/{walls,girders}.yml`, `Damage/modifier_sets.yml:28-39`,
  and `DamageSpecifier.cs:126-155`. Upstream light attacks require Damageable
  (`SharedMeleeWeaponSystem.cs:496-501`); hit audio and positive-damage flash are
  distinct (`:577-581`). No general terrain-damage implementation follows from this.
  Local mapping must omit the upstream `Structural` damage-type entries: that type
  is not supported by the local closed damage vocabulary. This does not change
  this roster's blunt/slash result; it is not full structural-damage parity.
- `control.hold_to_attack_melee` is an archived client preference, default false;
  weapon `AutoAttack` independently enables held retries. The client resamples
  targets when ready. Sources: `CCVars.Interactions.cs:74-78` and client
  `Weapons/Melee/MeleeWeaponSystem.cs:54-153`.
  The second owner-feedback revision deliberately defaults the local option on
  for new configurations. Explicit saved false values are not overwritten.
- Arc states are static 32×32 art, not generic hit particles. Upstream animates
  copied weapon sprites or fist art for `0.6 / attackRate` seconds, fading from
  half that duration to duration + 0.15 seconds. Positive damage flashes a target
  red and restores its prior tint over 0.30 seconds. Sources: client
  `MeleeWeaponSystem.Effects.cs`, `ColorFlashEffectSystem.cs`, `weapon_arc.yml`,
  and `Effects/arcs.rsi/meta.json`. Camera-relative first-person framing is an
  explicit Minecraft adaptation; CPU geometry checks do not establish GPU visibility.
  The local first-person branch uses the owned character/camera identity, not the
  detached player carrier. It reuses the arc queue and timeline. Copied flat tool
  sprites use the shared item renderer's camera-facing pose rather than its
  floor-laying rotation. World attack sprites now also face the camera; ordinary
  loose-world items retain their floor pose.

## Second owner-feedback corrections

Normal humanoids inherit `CombatMode.canDisarm: true` from pinned
`Resources/Prototypes/Body/species_base.yml:93-94`. The port's human component
omitted this field, suppressing secondary disarm before any request was sent.
Represent that inherited field as `can_disarm: true` in the local human composition.
Do not change the nullable component default: upstream
`CombatModeComponent.cs:21-26` and `SharedMeleeWeaponSystem.cs:865-868` distinguish
null/impossible and false/disabled from true. Client gesture and server admission
must continue consuming the resolved component; animal target admission is separate.

The arc catalog descriptor searches `moonstation14/melee_arc_profile`, but the
four resources were under `data/moonstation14/prototypes/melee_arc_profile`.
They now belong under `data/moonstation14/moonstation14/melee_arc_profile`.
The mismatch left production catalogs empty: both local enqueue and observer
receive skipped rendering. Direct JSON/image tests had not tested resource
discovery. Reload and encoded client-publication coverage guards this boundary:
four raw definitions resolve to three concrete profiles; the static parent is abstract.
World attack sprites also need camera-facing planes in Minecraft, rather than
the loose-item renderer's floor pose. These are presentation adaptations, not
new models or changes to attack authority.

Pinned `MeleeSoundSystem.cs:34-105` chooses target no-damage audio when the supplied
damage-type key is null. The light path derives that key from pre-resistance
modified damage, while the wide path uses applied damage. For clearer ineffective
contact feedback, the local revision deliberately uses committed positive damage
to choose normal versus configured `no_damage` audio in both modes. Explicit
event sound overrides retain precedence. This is not exact parity with the pinned
light-path asymmetry.

The pinned melee effects path supplies animated/copied arc sprites, a static fist
mark, body lunge, and positive-damage tint. A separate melee star/burst particle
effect has not been established; projectile impact events are not melee effects.
The missing arc mark must be made visible and demonstrated before inventing an
additional effect. A plastic jug still has plastic, not glass, destruction outputs.

## Resolved source compositions

Human resolves `MobHuman` (`Resources/Prototypes/Body/Species/human.yml`) through `AppearanceHuman` and `BaseSpeciesMobOrganic` (`Body/species_base.yml`), which inherits `BaseSpeciesMob`; that composes `MobCombat`, `MobDamageable`, and body/inventory bases (`Entities/Mobs/base.yml`). `MobHuman` inherits the organic melee composition. The composed `MeleeWeapon` defaults are blunt 5, attack rate 1, range 1.5, and `angle: 30`. This is the wide-fan angle, not a light-attack cone: light attacks follow the aimed ray. The component's wide-angle fallback is 60 degrees when no angle is set. Local `human.json` composes supported `MeleeWeapon` directly, using `weapon_arc_fist` for light and `weapon_arc_slash` for wide attacks.

`Crowbar` resolves through `BaseCrowbar` (`Resources/Prototypes/Entities/Objects/Tools/crowbars.yml`) to `BaseItem`; there is no `BaseItemTools` parent. Its `MeleeWeapon` supplies blunt 8 and `soundHit: MetalThud`. `KitchenKnife` resolves `KitchenKnife` → `BaseKnife` → `BaseItem` (`Resources/Prototypes/Entities/Objects/Weapons/Melee/knife.yml`); its `MeleeWeapon` supplies slash 10 and blade-slice `soundHit`. Sounds are fields on `MeleeWeapon`, not arbitrary separate item-level sound components. Local registered profile IDs `moonstation14:crowbar` and `moonstation14:kitchen_knife` extend the abstract `moonstation14:base_melee`; profile resolution merges nested cues but replaces the whole damage map, so each child supplies its complete damage map.

The local base captures common attack defaults (rate 1, range 1.5, thrust light arc, slash wide arc, blunt stamina factor 0.5, hand-selection reset, alternate disarm). Its configured wide angle is now 120 with origin-height ratio 0.6 at owner request; the component codec's absent-angle fallback remains 60. Children override damage and wide animation rotation. Local audio remains encoded at `weapon.audio` (`swing`, `hit`, `no_damage`) as consumed by `MeleeWeaponData`; this is the local schema adaptation of upstream `MeleeWeapon.soundHit`. Child profile audio cues merge with inherited cues. Local arcs are existing typed resources: `weapon_arc_fist`, `weapon_arc_thrust`, and `weapon_arc_slash`.

## Sound and runtime boundaries

### Reopened owner follow-up (implementation under validation)

The latest owner review accepts the 120° sweep but rejects weapon/body
intersection and feedback readability. Weapon fitting now captures the source
body AABB with the strike and carries it in observer snapshots. The intended
presentation constraint fits actual transformed model support between a per-direction
whole-AABB/lunge support plane and the animated tip; short paths may compress the
weapon axially, never extend gameplay reach. This is a Minecraft presentation
adaptation, not an SS14 model-pose rule. GPU appearance remains unverified.

The additional `block_structural_contact` catalog explicitly maps station floor
and airlock blocks to contact-only typed damage modifiers. Existing construction
contact data retains precedence. Shared client/server resolution must reject
ambiguous mappings and still require a genuine, revalidated blocking contact.
These definitions grant neither construction placement nor structural damage.
Their <=10 physical-damage absorption boundary is a local audio adaptation;
SS14 floors are map tiles and Airlocks are entities, not this substrate model.
This is not a claim that SS14 airlocks are invulnerable. Non-colliding devices
and natural terrain are not implicitly opted in.

Pinned `animals.yml:1348–1425,1551–1600,1660–1665` resolves `MobKobold` through
`MobBaseKobold` and `MobBaseAncestor`, whose ancestry includes `BaseMobAnimal`,
`SimpleMobBase` and `StripableInventoryBase`. Its inherited melee definition is
Slash 5 + Piercing 4, `AlienClaw` hit audio and `WeaponArcClaw`; component defaults
supply rate 1/sec and range 1.5. Local `MeleeAttack` owns NPC damage,
rate and range and now resolves typed `audio`/`arc` fields without
introducing a second damage source. Shared damage outcome, sound routing and
observer presentation remain the runtime owners; NPC intent/admission remains
with retaliation. The Kobold maps to `weapon_arc_claw` and registered
`moonstation14:ss14/effects/alien_claw`; the pinned sprite and three audio samples
are retained in canonical effect/audio resources with attribution. Adding claw
raises the arc catalog to five raw definitions / four concrete profiles. Final
asset/consumer evidence remains subject to the current checks.
Other unmapped NPC attacks use supported default fist feedback; this correction
does not claim a complete species-specific bite/claw/audio roster.

Pinned pulling deliberately resolves an active pulling virtual item to gloves
or unarmed melee (`SharedMeleeWeaponSystem.cs:207–242,309–334`), with ordinary
physical non-melee held items still blocking fallback. No hand-switch restriction
is added. Server/client `DoDamageEffect` produces victim color flash; the separate
attacker-origin animated fist/weapon moves outward. No generic victim/contact-
position melee particle exists in the verified pinned path; none is added here.

Sound events are existing assets in `assets/moonstation14/sounds.json`: swing `moonstation14:ss14/weapons/punch_miss`; fists hit `moonstation14:ss14/weapons/punch`; knife hit `moonstation14:ss14/weapons/blade_slice`; no-damage `moonstation14:ss14/effects/weak_hit`; crowbar hit `moonstation14:ss14/effects/metal_thud` (provided by the separate audio asset change). Do not add sound groups here.

Unarmed resolution uses the human's composed `MeleeWeapon` component; held catalog items use `MeleeWeaponResolver` and the registered/merged weapon profiles. Light attacks use eye/look crosshair aiming and a conservative eye allowance with physical AABB filtering. Wide attacks use the resolved chest origin, full look direction and yaw-stable head-local lateral axis at nominal range, with independent terrain clipping, limited to five targets. As in pinned SS14's `Content.Server/Weapons/Melee/MeleeWeaponSystem.cs:28-54`, the server validates nominations for range, LOS and map validity, with +0.1 wide range tolerance; it does **not** independently enforce fan angle. Cadence uses 20 Hz ceiling ticks rather than SS14 timing. These are deliberate 3D adaptations; no unsupported upstream components are introduced. Automated results belong to the bound workflow run; native input, GPU presentation and actual playback remain separate manual boundaries.
## Wide melee feedback adaptation

Pinned SS14 `SharedMeleeWeaponSystem.cs:604-627` plays a swing when the initial
wide target set is empty; `:635-668` removes arc/ray failures before collecting
targets; `:695-745` positions hit audio at the first surviving target, not the
original nomination. Local wide-hit audio likewise uses the first still-present
server-validated contact from `hits`, through `MeleeSoundPolicy.hit` and
`shouldPlayHit`/`PositionalSoundRouter`; armor-blocked damage does not suppress
contact audio. For a non-empty initial nomination list whose members all fail
server validation, pinned SS14 is silent. Local play intentionally adapts this
case to a miss swing for feedback/reconciliation; it is not a claim that SS14
always swings after filtering. The server excludes the initiator only when the
initial nominations were empty (the client predicted that swing); filtered
non-empty misses include the initiator because no client swing was predicted.

## Animal model presentation heights

Kobold and Monkey `AnimalAppearance` records author optional `eye_height` and
`speech_height` from their local 3D model geometry; these values are Minecraft
presentation adaptations, not SS14 eye-height parity. For this model convention,
world Y is `1.501 - globalModelY / 16`. Kobold eye global Y 4.92024611 gives
1.193484618 m (authored 1.19); the highest horn top at model Y 1.516636097 gives
1.406210244 m, so speech height 1.42 leaves clearance. Monkey eye height is
authored 1.06 m and crown height 1.218494373 m supports speech height 1.23 m.
Speech height describes model top; the shared bubble's existing +0.22 m offset
remains. Source geometry is the local Kobold/Monkey model geometry; component
field names are `AnimalAppearanceComponent.java:8,14-15`.

## Latest creature combat mapping

Pinned `animals.yml:1348–1425,1551–1600,1660–1665` resolves `MobKobold` →
`MobBaseKobold` → `MobBaseAncestor` → (`BaseMobAnimal`, `SimpleMobBase`,
`StripableInventoryBase`); `MobMonkey` resolves through `MobBaseAncestor`.
`MobBaseAncestor` explicitly sets `CombatMode.canDisarm: true`, one left hand,
and `ComplexInteraction`. `MobBase` supplies stamina defaults: 100 stamina,
critical decay 3, decay cooldown 3 seconds, and stun 6 seconds
(`Entities/Mobs/base.yml`; stamina definitions/defaults in the pinned mob
prototypes). Monkey bite is blunt 3 + piercing 3; Kobold claw is slash 5 +
piercing 4 (`animals.yml`). Local Kobold/Monkey compositions reuse the typed
`combat_mode` and `stamina` fields. No global animal permission default changes.
Both also compose the supported local `Stunnable` capability: local
`StaminaSystem.takeStaminaDamage` requests paralysis through
`CharacterControlSystem.extendParalyze`, which requires resolved
`StunnableComponent`. Without it critical stamina disarm cannot paralyze these
creatures. This maps upstream's direct `SharedStaminaSystem.cs:389–408`
paralyze update to the supported local runtime; no unsupported status-effect
allowlist or global animal stun permission is introduced.

Resolved `MeleeAttack` is consumed by the shared `UnarmedMeleeProfileResolver`
for NPC and controlled-body conversion. `AltDisarm` defaults true from upstream
`MeleeWeaponComponent` (correcting the earlier critical-false mapping); actual
permission remains explicit `CombatMode.canDisarm`. Held-item and glove
precedence is unchanged. A true handless resolved body uses the existing
`UNARMED` binding (empty hand, revision 0, no token) and
`GhostControlClient`'s confirmed session clock: it does not fabricate hands.
Canonical body/epoch/request authorization remains authoritative. Native hand
bootstrap runs only at join/readiness. Disarm target checks use resolved actual
hand capability, not equipment-slot count or attachment existence; the existing
empty-target-stamina response is retained. NPC miss and close-wall behavior are
unchanged.

The shared conversion also now restores upstream common-melee defaults for
blunt stamina damage (.5), hand-selection reset, animation offset (1), and
click damage modifier (1); this intentionally corrects NPC blunt stamina as
well as controlled-body behavior. Its accepted Minecraft wide-arc adaptation
remains 120 degrees with a .6 origin-height ratio. Health damage, NPC accuracy,
and Kobold slash/piercing damage are unaffected.

Held-item PALM versus HUMANOID_ARMPIVOT is handled by the shared renderer, not a
duplicate pose path. Minecraft's typed composition, hand binding and renderer
are deliberate conversions; unsupported upstream components are not invented.
Native-input and visual validation remain pending, so this mapping does not
claim those checks or the complete integration gate passed.

## Fixture isolation

`ScopedPrototypeCatalogFixture` restores nested overrides in reverse registration
order through one lifecycle listener per test. FIFO cleanup previously restored
an intermediate, damage-only Human profile after the original catalog, removing
the production punch cue in mixed melee runs. Production-roster fixtures assert
that resolved cue before exercising attacks. Distinct batches remain necessary;
LIFO cleanup is not isolation for concurrent global catalog mutations.
