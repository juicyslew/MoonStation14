# Disposals and pneumatic transport

## Authorization and target

Owner authorized this sprint on October 3, 2026. Target SS14 behavior at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`, including:

- Powered disposal units accepting items and controlled characters.
- Player-built, editable pneumatic pipe networks.
- Characters entering units and flushing themselves through the network.

This is not the previously proposed air-alarm sprint.

## Current phase

Owner revised the spatial target on October 4: substantial 3D disposal pipes,
gas-style exposed runs and sealed structural crossings, six-direction routing,
and face-derived orientation without the custom Facing selector. The replacement
is implemented in code and undergoing fresh validation. See the contracts at
the top of `architecture.md`; previous floor-overlay test passes do not validate
this replacement.

Implementation is now authorized. Ordered replacement units:

1. Six-face orientation/ports, typed resolved geometry, own-block/ sealed-host
   persistence and lifecycle invalidation.
2. Face-derived construction and repeatable recipe selection/preview, thick
   rendering/collision and sync, plus unit/holder spatial integration.
3. Live horizontal/vertical/bend/wall-crossing, editing, host-removal and
   transport conservation regression coverage; independent source review.
4. One designated unfiltered validator and concise connected-client checks.

This is not a generic crafting-system rewrite or an old-save migration.

Owner-authorized follow-up: replace the steel-triggered construction picker
with a non-hand Disposal Pipes family mode. The future browser key enters this
family directly for now; a non-container quick palette, persistent readiness
HUD, Exit control and connection-aware Rotate preview supply the agreed UX.
Screwdriver editing changes orientation, not pipe type. The shared browser and
item crafting queue are deferred to `../construction-crafting-sprint/plan.md`.

This follow-up is implemented and source-reviewed. Fresh automated evidence:
2,153 JUnit tests passed, 30/30 disposal GameTests passed in both full-suite
family runs, and the final build succeeded. The full server suite is not green;
connected authenticated placement/rotation and HUD checks remain outstanding.
See `validation.md` for exact failures and evidence boundaries.

Core implementation and upstream composition mapping are in place. Final
acceptance is pending live regression repairs and connected-client checks.
The first live run passed 489/499 tests; its failures are being investigated
and repaired, not waived. See `architecture.md` for integration decisions and
`client-checks.md` for the remaining human checks. No completed parity claim
is warranted from prototype or isolated foundation tests alone.

## Ordered work units

1. **Composition and ownership design.** Resolve upstream entity parents,
   component defaults and actual consumers. Map only supported local
   components. Establish unit contents, transit-holder ownership, finite air,
   and controlled-character containment/control boundaries.
2. **Editable pipe network.** Implement resolved pipe definitions,
   construction/rotation/removal, connectivity and routing. Preserve distinct
   segment, bend, junction and trunk/entry behavior, including terminal
   ejection; establish tagger/router support from the source audit. Reuse body-hand
   interaction and relevant geometry services, not gas-inventory lane identity.
3. **Units and transport.** Implement audited insertion, charging, engagement,
   flushing, traversal and ejection, with item/air conservation and appropriate
   destruction handling.
4. **Character transport and presentation.** Implement self-entry and exit,
   self-flushing, camera/control continuity and containment-specific action
   restrictions. Integrate relevant breathing and timed-item behavior rather
   than silently suspending them. Import SS14 presentation with attribution.
5. **Live integration validation.** Build a small operator fixture using real
   power, gas and editable pipes. Exercise items and controlled bodies through
   routing, broken paths, power loss and ejection. Validate prototype
   inheritance/defaults and live consumers, not only disconnected logic.

Work may be regrouped where files/context overlap; independent presentation
work can run alongside established runtime work. Only one heavy validator
should run at a time.

## Invariants

- Existing canonical interaction intents; no disposal-specific hotkeys or
  spectator inventory proxy.
- Preserve controlled body identity, mind/epoch ownership, hands/equipment,
  item metadata and legitimate contained storage contents.
- Disposal routing and moving holders are not gas-pipeline equalization.
- No gas, items or bodies vanish or duplicate during handled transitions.
- No unproven exterior-gas fallback or forced chunk loading for routine routing.
- Record Minecraft-specific spatial, containment and input adaptations against
  the corresponding SS14 behavior.
- Use prototypes/inheritance and composed runtime consumers; no unsupported
  YAML components added merely to resemble upstream.
- Retain SS14 sprite/sound attribution. Record unclear audio provenance in
  `docs/audio-unknown-provenance.md`.
- No mod-compatibility framework, save migrations, extensive anti-cheat or
  crash-durable transaction machinery unless a current requirement demands it.

## Acceptance gates

- A player can build, redirect and remove a working pneumatic route through
  the controlled body's inventory and audited tool/construction interactions.
- Units follow verified power, pressure/charging and flush timing rules.
- Items arrive with their identity/contents intact; routes behave as audited.
- A controlled character can enter, operate/self-flush, travel and exit while
  retaining control ownership and inventory; enclosure prevents inappropriate
  external actions.
- Missing entry, disconnected pipes, destruction and unavailable chunks have
  defined, validated outcomes; adaptations are explicit rather than parity claims.
- Relevant presentation and authority/network paths are validated separately
  from core logic. Connected checks remain explicit where automation cannot
  prove them.

## Outside this slice

There is no verified separate outlet prototype at the pinned revision;
terminal behavior is trunk/unit return or open-end ejection, not an invented
outlet block.

Station generation, air-alarm monitoring, generic throwing implementation
(owned by another agent), and unrelated physiology/container features.
Mailing and signal-controlled disposal variants require separate scope if
not necessary for the audited core network; they are not silently claimed
as implemented.
