# Thrown-object combat

Current follow-up: `FEEDBACK.md` defines the active owner-feedback revision.
Approved armor is imported and wired (`ASSETS.md`); the older development-state
counts and claim-only artwork notes below are historical, not current gate proof.

## Development state (implementation/reviews complete; gate and artwork approval pending)

Current focused validation covers 44 exact source-verified JVM classes and 240
tests, with zero failures/errors/skips; all three compile tasks passed. The
capability-hotkey revision allocates the lowest free direct slot (1-20) once per
action/provider identity from accepted body/epoch/revision snapshots. Survivors
keep their bindings; removal frees slots, while cooldown/temporary disable does
not. Ordering is snapshot discovery/grant order, not prototype priority. Combat,
scream, debug, and self actions consume no reserved slots. Direct 1-9/0 and
Shift+1-9/0 follow the pinned SS14 chords; no Ctrl/Alt banks or dedicated self key
were added. Rebindable mappings use real keyboard modifiers and existing vanilla
hotbar/screen admission.

HUD, menus, pointer targeting, and hotkeys share the same accepted slot projection.
Holes remain holes; narrow layouts retain all bindings, and too-small viewports
clip rather than inventing scrolling. Pointer clicks use the last painted immutable
entry/rect projection, avoiding stale-slot activation before repaint. Review found
and resolved projection/cache findings; automated state/layout tests do not
establish native client visual acceptance.

Self-removal and HypoDart sprite visibility remain as documented above. Armor
wearable request `thrown-combat-basic-armor-wearables-20261103-r1` is claimed by
`gpt-6.1-sol-basic-armor-r1`, but only CLAIMED is verified; delivery, approval, and
import are not. Its frozen submitted context may retain source reference typos;
this local correction is not a duplicate request or a sent clarification. Existing
approved inventory sprites/worn visuals remain unchanged.

Fresh JVM evidence is 44 classes / 240 passing tests; review is PASS. Fresh
readiness, native, package/provenance, and owner acceptance remain pending for
this source revision. The prior accepted run is history, not current proof.

## Agreed scope

- Purpose-built throwing knife, ordinary dart, hypodart and bola; preserve existing
  kitchen-knife behavior rather than deriving thrown damage from melee damage.
- Typed target-impact damage through the existing damage/armor owners.
- Hypodart payload injection on successful embedding, with actual solution loss.
- **Owner selected SS14 bola ensnaring**, not an added on-hit stun.
- Suitable items remain visibly attached at their captured body contact point,
  follow the target's visual pose, and can be removed through timed interaction.
- Reuse the already-live armor system. Add representative armor data/content only
  where needed to demonstrate the scoped damage-type protection, not a new system.

## Ordered work units

1. Typed, inherited thrown-impact composition and representative item/resources;
   map verified upstream components to supported local data. Add typed target
   ensnareability only to resolved character compositions that actually support it.
2. Extend canonical loose-item release/contact handling: damage, stable thrower
   attribution, attachment ownership and cleanup. The same loose entity/stack
   remains the sole owner; no second projectile or visual item copy.
3. Reuse shared chemistry and timed transactions for payload injection, item
   retrieval and bola freeing/breakout. Reuse movement-modifier/prediction owners
   for ensnaring; no parallel stun or movement pipeline.
4. Render body attachments through existing body poses and shared item geometry,
   preserving interpolation, posture and lunge without a new character rig.
5. Focused JVM/compile checks and independent review, then a complete OWNER_READY
   gate with required native/shared-boundary coverage, package and provenance.

## Reuse targets and invariants

`BodyLooseThrow`, `LooseItemEntity`, `TypedProjectileEntity` collision/damage seams,
`DamageSystem`, `EquipmentArmorSystem`, `ResolvedWornEquipment`,
`MeleeChemicalInjectorSystem`/its owner transaction, `BloodstreamSolutionAccess`,
`BodyLiquidInteraction`, `TimedInteractionSystem`, `LiveHandsCommitGateway`,
`BodyHandItemTransfer`, `WorldItemPresentationRenderer` and body appearance layers.

Keep accepted flight, terrain collision, disarm release cues, melee presentation,
current-body identity and hand/world conservation intact. Land/self damage is not
target-impact damage. Merely dropping or picking up an object must not arm combat.
Generate contact-entry events, not repeated damage/injection on every overlap tick.
Re-entry while still thrown may generate another collision as upstream permits.
Embedding/ensnaring end ordinary free-flight contact handling.

New item impact composition should be separate from movement physics and melee
damage: reuse the prototype registry/sync and a resolved per-item combat profile,
not item-ID branches or another throwing engine. Empty capabilities do nothing.

## Explicit limits

No guns/launchers, round-history features, save migrations, general surgery,
body-zone damage or character animation rigs. A 3D contact-point visual is a
Minecraft adaptation of SS14 entity parenting, not proof of upstream limb/bone
placement. Do not add a speed/chance gate from an unconsumed upstream field.
No interactive clients without explicit desktop readiness. No staging or commits.

