# Verified thrown-item composition

Reference checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Values below are upstream facts; local resolved composition and live wiring are documented and covered by focused tests. Serialized gate validation remains pending.

| Entity / chain | Relevant resolved behavior |
|---|---|
| `KitchenKnife â†’ BaseKnife â†’ BaseItem` | Slash 10 melee; no `DamageOtherOnHit`. Do not invent thrown damage from melee stats. |
| `ThrowingKnife â†’ [BaseKnife, BaseSyndicateContraband]` | Slash 5 melee, attack rate 2; thrown Slash 10 + Piercing 15, ignore resistances true; embeds on throw, removal 1.5 s, offset (-.15,0), throwing angle 225Â°. |
| `Dart â†’ BaseItem` | Piercing 2 melee; thrown Piercing 4, default resistance bypass false; embedding removal .5 s, angle 315Â°; `melee` solution capacity 2u, embed transfer 2u, blocks occupied OUTERCLOTHING. |
| `HypoDart â†’ Dart` | Inherits hit/embedding behavior; solution capacity and embed transfer 10u; blocked slots NONE. It is not a gun projectile or a renamed syringe. |
| `Bola â†’ [BaseItem, BaseSecurityContraband]` | Ensnaring, max 1; walk/sprint .5; free-other 2 s, self-breakout 3.5 s; zero stamina damage; throwing triggers; movement does not cancel breakout. No added stun. |

`DamageOtherOnHitSystem` consumes thrown-hit events and calls typed damage with
thrower attribution. Default universal thrown modifier is 1. Repeated qualifying
collision entries may hit again while thrown; there is no universal once-per-flight
damage rule. The existing port's accepted fixed flight remains an adaptation.

Local presentation maps upstream knife throwing angle 225° and dart angle 315°
through resolved item metadata; upstream knife embedding offset (-.15, 0) is
adapted to the local inward contact-point offset (-.15), not upstream entity
parenting, which the Minecraft renderer does not model directly.

## Manual-feedback revision: resources, cues, and controls

- The owner's client log showed missing textures for all six thrown items. Their
  PNG files are valid and packaged; the active `assets/minecraft/atlases/blocks.json5`
  now includes each sprite (HypoDart reuses Dart). Focused CPU tests validate
  atlas membership, image decoding, and provenance; this is not GPU visual review.
- Resolved composition inherits supported `base_item_sounds`: WeakHit has minimum
  speed 3, cooldown .2, gain -10 to 2, and ramp 10; land/drop is 2 dB lower.
  The port reuses the existing weak-hit cue and adds only the missing native
  drop cue, with unknown provenance logged. Land playback is one-shot after an
  armed throw, not every frame of sliding. Bola inherits throw sound and its
  explicit snap override; CPU/native packet assertions are not human listening
  or audio-mix acceptance.
- Dart and HypoDart have the owner-requested 1.5 blocks/tick and 16-block query
  ceiling. Generic knife and bola retain .75 blocks/tick and 8. The 16-block
  value is a loaded-query ceiling, not a guaranteed landing range. These are
  deliberate Minecraft adaptations, not SS14 inverse-mass scaling.
- Dart use-help is the existing in-hand `Z` hint, not selectable syringe modes.
  It reports current/capacity, embed maximum 10, and jab 1; manual bottle `E`
  transfer may require multiple pours. Conditional ensnared Break Free is `K`;
  attached-item removal is Alt+E.

`EmbeddableProjectile` defaults: EmbedOnThrow true, removal 3 s, offset zero,
DeleteOnRemove false. There is no embed-chance field. MinimumSpeed exists in the
component (Dart authors 3, default 5), but the inspected shared thrown-embed consumer
does not use it; do not turn it into a new gameplay threshold. Actual attachment
is static entity parenting, followed by `EmbedEvent`; target termination detaches
tracked objects. Removal is a DoAfter followed by detach and pickup-or-world return.

Injection is triggered by `EmbedEvent`, not an arbitrary collision. Base event
injector defaults are transfer 1u, efficiency 1, PierceArmor **true**, blocked slots
NONE. Dart's explicit occupied-outer-clothing filter and a false PierceArmor's
hardsuit-tag check are separate gates. HypoDart inherits true PierceArmor and clears
the slot filter. Extract the actual source dose before bloodstream insertion;
failed insertion does not refund a consumed dose. No bloodstream/blocked/empty
targets do not inject. Hand-melee injection is a separate inherited capability.

Ensnaring requires a resolved target `Ensnareable` capability, not every living
entity. `BaseSpeciesMob` supplies it in upstream species composition. Bola removal
requires a hand; damage and dropping do not cancel it, and movement is allowed.
Preserve shared timer/admission owners rather than substituting a timed stun.

Armor already exists locally. Upstream `ClothingOuterArmorBasic` inherits Blunt,
Slash, Piercing .70 and Heat .80; `ClothingHeadHelmetBasic` inherits .90 for those
four types. Modifiers stack multiplicatively. SS14 relays armor damage modification
to non-pocket inventory slots, without locational damage. ThrowingKnife bypasses
ordinary resistance/armor modifiers; Dart does not. Local unworn suit-storage
exclusion is an existing adaptation, not new body-zone armor behavior.

## Source anchors / assets

- `Resources/Prototypes/Entities/Objects/Weapons/Melee/knife.yml:1â€“65,330â€“364`
- `Resources/Prototypes/Entities/Objects/Fun/darts.yml:1â€“130`
- `Resources/Prototypes/Entities/Objects/Weapons/Throwable/bola.yml:1â€“61`
- `Content.Shared/Projectiles/{EmbeddableProjectileComponent,SharedProjectileSystem}.cs`
- `Content.Server/Chemistry/{Components/BaseSolutionInjectOnEventComponent,EntitySystems/SolutionInjectOnEventSystem}.cs`
- `Content.Shared/Ensnaring/SharedEnsnareableSystem.cs` and target component
- `Content.Server/Damage/Systems/DamageOtherOnHitSystem.cs`; shared Damageable API
- `Clothing/OuterClothing/armor.yml`, `Clothing/Head/helmets.yml`, base clothing chains

Sprites: throwing_knife.rsi (Ubaser), dart_red.rsi (TheShuEd), bola.rsi (Citadel/TG
credits in meta.json), all CC-BY-SA-3.0. HypoDart inherits the red dart sprite.
Use canonical resource locations and preserve metadata. Impact/throw/land sounds
include star_hit.ogg, pierce.ogg, bolathrow.ogg and snap.ogg; inspect attribution
before import and record genuinely unknown provenance in the shared audio log.



## Owner-feedback revision notes

- Preserve SS14 dart/hypodart transfer limits and solution semantics: no user
  selectable syringe modes; local `CanChangeTransferAmount=false` is deliberate.
  No new Free action is granted on every target; local self/other escape durations
  remain the requested timed adaptation.
- Ensnare presentation reuses loaded public attachment queries and passive status
  rendering, not a new clickable icon/menu. Timed removal reuses existing action
  routing and server geometry checks; normal empty-hand click is translated to the
  timed pickup path, while explicit Alt+E remains. Self/back-source choice is a
  known UX limit, not an assertion of full SS14 parity.
- Aim endpoint correction shares existing ray math; query caps are upper bounds,
  not promised throw distances. Dart/hypodart and knife/bola speeds are deliberate
  Minecraft tuning, not an unsupported SS14 mass conversion.
- Attachment persistence writes canonical NBT and resolves the UUID on load without
  replaying attachment or injection. Flight state sync, root alignment, opaque tip
  UV projection, and camera-plane presentation are Minecraft rendering adaptations;
  CPU tests/review cannot establish GPU appearance across views.
- Native tests exercise timed escape, close-floor aim, and real sprite/persistence
  behavior, but the added cases await the replacement gate. Do not claim native PASS
  or owner acceptance from the earlier run.


## Current owner-approved adaptations and open artwork request

- Controlled-character removal is a conditional capability action, not SS14's
  innate Break Free action on every entity. It selects ensnared escape first,
  otherwise the newest self-attachment, and pins the existing timed interaction.
  Own attachments are excluded from generic world targets; other-target click/Alt+E
  routes remain. Self/back-source chooser UI is intentionally deferred.
- HypoDart differs from Dart in visual sprite scale only; physical dimensions,
  profile, and tip metadata remain unchanged. The shared projection uses the existing
  sprite-tip mapping; automated tests are not GPU acceptance.
- `ARMOR_MODEL_REQUEST.md` is a local draft marked NOT YET DELIVERED. No modeler
  channel/receipt or approval exists. No placeholder/new wearable mesh was imported;
  current inventory icons and worn visuals are preserved. External authored models
  and approval are separate from this code revision.
- Fresh focused validation is 33 source-verified classes / 196 passing tests. New
  native cases remain unexecuted; the preceding owner-approved run is historical,
  not parity/runtime proof for this revision.


## Capability hotkeys and wearable-request boundary

- SS14 source-pinned chord convention is direct 1-9/0 then Shift+1-9/0 for
  actions 1-20. This port uses actual rebindable keyboard mappings/modifiers; it
  does not invent Ctrl/Alt banks or reserve capability keys.
- Local capability bindings are stable action/provider identities from accepted
  body snapshot frames, not permanent prototype-priority ranks. Shared HUD, menu,
  and pointer behavior consumes one projected slot list; painted hit-testing uses
  the same immutable entries/rectangles. This is a client adaptation, not a new
  authority/network protocol.
- Self-removal remains a conditional capability with ensnared escape first and
  no dedicated self-removal shortcut. Specific-source chooser UI stays deferred.
- Wearable models are not present in this code revision. The local request draft
  points to corrected source paths/prototype names, but its frozen submitted context
  is unchanged. Request r1 is CLAIMED only; do not infer delivery, approval, or
  import from that status.
