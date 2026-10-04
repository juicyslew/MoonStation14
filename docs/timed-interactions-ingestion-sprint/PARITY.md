# Verified ingestion and DoAfter mapping

Reference checkout revision verified: SS14
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. These findings use Content systems;
no additional Robust internals were needed. Direct source verification corrected
two preliminary research errors: puddles take **three**, not four, seconds, and
held self-ingestion **does** require the original item to remain held.

## Resolved composition

| Source | Relevant resolved upstream composition | Local supported mapping |
|---|---|---|
| Jug | Parents `DrinkBaseMaterialStrongPlastic`, `DrinkBase`, `DrinkVisualsAll`. Material parent inherits `DrinkBaseMaterialPlastic`; visual parent inherits openable/fill visual bases, but does not itself add `Openable`. `DrinkBase` supplies `Edible` (Drink, solution `drink`, destroy=false, utensil=None) and solution transfer/storage capabilities. Jug overrides capacity to 200 and visual tiers to 6/5. | Typed inherited Edible: 500 cents, 20 ticks, destroy=false. Existing local capacity 150, glass-like impacts and six shared appearance tiers are retained departures; ingestion does not redesign them. |
| Chemistry bottle | `BaseChemistryEmptyBottle` inherits `BaseItem`, not `DrinkBase`. Explicit Edible selects Drink/`drink`, destroy=false, utensil=None; solution capacity 30. | Separate default-derived bottle profile, explicit destroy=false, inherited 500-cent/20-tick defaults. Existing 30-unit capacity retained. |
| Puddle | `Puddle` composes Edible (Drink, `puddle`, delay=3, transfer=1, utensil=Spoon) and puddle solution/behavior. Edible defaults destroy=true and utensil-required=false. | Edible overrides 100 cents/60 ticks; empty cleanup only after confirmed ingestion. No required spoon is invented. |

`EdibleComponent` defaults transfer=5 units, delay=1 second, destroy=true.
Recursive closure: `DrinkBase` and `DrinkVisualsOpenable` both inherit `BaseItem`,
which has no further prototype parent; `DrinkVisualsFill` has no parent.
`BaseItem` supplies Small Item, Clickable, physics/fixtures, Sprite, Pullable and
general item behavior. Jug overrides Item size to Normal; chemistry bottle
overrides it to Tiny. Puddle is standalone with no parent: its own composition
includes anchored transform, clickable/static physics, a 1000-unit solution and
solution/edge visuals. These item/physics baselines are already mapped by existing
runtime subsystems, not additional ingestion-profile fields.
`EdibleEvent.Time` starts at zero; `OnEdible` adds the component's resolved delay
once. A prototype override does not add another default second.

The local `ingestion_profile` family has a required typed `edible` composition.
Abstract defaults, a drink-base mapping and concrete jug/bottle/puddle entries
resolve through the existing prototype catalog. Dose, duration and puddle empty
policy must be consumed by the live timed completion path. Unsupported upstream
material, utensils, openable visuals and multi-solution components are not dummy
local fields. Existing source capacity and local physiology remain their current
runtime contracts, not newly certified upstream parity.

## Timing, interruption and repetition

- `GetEdibleDoAfterArgs` sets self-use movement interruption false, damage
  interruption true, active-hand-change interruption false, and `NeedHand` when
  force-feeding or when the source began held. `BreakOnDropItem` remains true:
  the original item may remain in either hand, but dropping/storing it interrupts.
- The default damage threshold is one typed damage unit. Local accepted-damage
  events use the existing five typed units per Minecraft health conversion;
  zero, rejected damage and healing must not interrupt. Damage then healing in
  one tick must still interrupt.
- Generic duplicate blocking/cancellation cancels the matching active actor action
  and blocks the new start. The same initiating gesture is the explicit stop
  control; no universal upstream cancel key was found in `Resources/keybinds.yml`.
- `OnEatingDoAfter` rechecks consumption, finds stomach admission and clamps the
  transfer. Voluntary `OnEdibleIngested` requests repeat while source remains;
  repetition stops when desired transfer meets/exceeds available stomach volume,
  the source empties/is destroyed, completion fails or the action is canceled.
  Repeat restarts a full duration on the same DoAfter identity.
- Active actions belong to the actor's networked DoAfter component; callbacks
  consume authoritatively. Local timed attachment data is transient, with separate
  runtime delegates and presentation snapshots. Independent source keys may run
  independently. Completed/canceled presentation lingers approximately 0.5 s.

## Presentation and adaptations

SS14 draws a 24×7 progress bar above actor sprite bounds plus 0.05 world units,
stacking multiple bars. Fill spans x=2…22 and y=3…4; progress color uses HSV
hue `(5/18) * progress`, saturation 1, value .75; completion is green. Canceled
bars freeze progress; their red fill flashes on/off every .125 s while the frame
remains visible. Local actor bars are unshaded,
remote actor bars are shaded. The port maps 32 pixels to one Minecraft block.
Minecraft adapts the locally owned active CHARACTER body's bar to a camera-relative world-space billboard in first person (0.25 forward, anchored at 70% of the projection-derived vertical half-extent below center; projection `m11` with a 70-degree-FOV-derived invalid-scale fallback), placing it in the lower view above the hotbar while retaining world-space depth. `ownedCharacterForHud()` only returns the exact committed body while the authenticated active body-view binding is valid; the body view is supplied by the camera mixins, while Minecraft's native camera entity remains the player/ghost (it is reset to the player when the binding clears). Thus native camera-entity identity is not the active-view test. Third-person, remote, and non-owned actors retain the interpolated actor-above-AABB anchor, now with 0.30 world-unit clearance so the frame sits outside the head, and 32 pixels per world unit. First-person scale is 1/12 of normal 1/32, or 1/384 world units per pixel, keeping the 24×7 bar about 0.0625×0.01823 world units. Billboard orientation uses camera rotation without an additional half-turn: camera-local X consequently projects screen-left-to-right, so fill grows naturally from the left. This presentation-only projection never affects authority, and its first-person placement is not claimed to match an SS14 overlay position.
The imported progress-bar PNG retains its adjacent CC-BY-SA-3.0 attribution.

Client prediction is presentation-only. Server snapshots correct progress;
reagent writes and repeat completion remain server-authoritative. Own first-start
progress can be provisional; concurrent starts and duplicate stop wait for
authoritative presentation. The upstream color-blind palette and container-hidden
bar rules are not implemented in this slice (sources are held or loaded world roots).

Generic progress does not refresh hand state. Successful ingestion sends an
owner-only `IngestionCommitted` hint (body UUID and control epoch); the hands
client verifies its current binding and requests a normal authoritative snapshot.
Denied completion, cancellation and unrelated timed actions send no such hint.

Before adding another timed-action consumer, audit its upstream prototype
composition **and the system constructing its `DoAfterArgs`**. Do not assume the
current subset is sufficient: the shared port does not yet expose SS14's full
per-operation thresholds, target/tool distance checks, hand interruption options,
duplicate policies, attempt-event frequencies or typed completion event dispatch.
Implement the capabilities actually used by that consumer before claiming parity;
do not substitute ingestion's rules or invent a generic prototype-policy family.

Existing adaptations remain: body Mouth/stomach/BodySystem eligibility, one local
stomach admission contract, 5-block loaded/unobstructed world access (upstream
`MaxFeedDistance` is 1), and initially empty/unreserved hands for world/puddle
gesture admission. Ongoing world access does not require continued aiming.
No force-feeding, utensils, food/trash spawning, clothing blockers or new thirst
stopping rules are added. `Drink` sound plays only after successful ingestion;
the existing asset's unresolved individual provenance remains recorded in
`docs/audio-unknown-provenance.md`.

## Source index

- `Resources/Prototypes/Entities/Objects/Specific/Chemistry/{chemical-containers,chemistry-bottles}.yml`
- `Resources/Prototypes/Entities/Objects/Consumable/Drinks/{drinks_base,drinks_base_materials}.yml`
- `Resources/Prototypes/Entities/Effects/puddle.yml`; `Resources/Prototypes/Nutrition/edible.yml`
- `Content.Shared/Nutrition/Components/EdibleComponent.cs`; `Nutrition/IngestionEvents.cs`
- `Content.Shared/Nutrition/EntitySystems/IngestionSystem.cs:302–410,421–435`; `.Blockers.cs:70–92`; `.API.cs:MaxFeedDistance`
- `Content.Shared/DoAfter/{DoAfterArgs,DoAfterComponent,SharedDoAfterSystem}.cs`; `SharedDoAfterSystem.Update.cs:TryComplete,ShouldCancel`
- `Content.Client/DoAfter/DoAfterOverlay.cs`; `Content.Client/UserInterface/Systems/ProgressColorSystem.cs`
- `Resources/Textures/Interface/Misc/progress_bar.rsi/{meta.json,icon.png}`
