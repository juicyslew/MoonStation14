# Visual correspondence and deliberate adaptations

Reference checkout: Space Station 14
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`, verified during upstream research.
The existing resolved local `AirlockData` and authoritative two-tile controller
remain the operational composition; no new unsupported upstream components or
door gameplay variants were introduced. See the prior airlocks handoff for the
door's inherited power, wire, phase, safety and collision mapping.

The live renderer consumes resolved door phase durations and synchronized
presentation. Shared `AirlockGeometry` loads the same accepted component exports
for moving examination bounds. Upper leaf, infill, reader housing, cover and wires
share upper travel; the lower leaf moves down. Gameplay collision is unchanged.

## Minecraft visual adaptations

- Use the accepted `all_variants_revision2/standard_basic` cuboid exports, not the
  unaccepted standalone `vertical_models` iteration. Only the currently supported
  standard door is bound; the package's 49 profiles are not 49 implemented doors.
- Per owner choice, omit external pockets and clip visible geometry to the
  two-block root volume. The complete authored installation is four blocks tall.
- Reach the authored upper/lower endpoint (+21.5/-9.5 model units) during the first
  resolved opening phase, then hold through settling. The ordinary controller
  retains 0.4/0.2-second phases instead of adopting linear 0.6-second model travel.
- Native overlays use static frame 00. Full native overlay and panel-transition
  animation is not implemented. Base paint is ambient-lit; selected native
  overlays are power-gated fullbright. No glass profiles are bound.

## Painting, not included here

Upstream `SharedSprayPainterSystem.OnPainterDoAfter` sets
`PaintableVisuals.Prototype`; the client door appearance system uses that prototype
as a visual source without replacing the target's identity or components.
`Resources/Prototypes/Paintables/airlock_groups.yml` defines standard/glass groups,
department styles, three-second application and three-charge cost. Relevant base
composition is in `Entities/Structures/Doors/Airlocks/base_structureairlocks.yml`.

The port does not yet implement this painter workflow. Department styles can
share geometry where their exported geometry/UVs match, but the accepted package
also contains genuinely different constructions; one mesh cannot represent every
profile through a universal tint or texture swap.

Minecraft pixel appearance, lighting, all-facing readability and connected-client
animation still require owner visual retesting; headless tests are not visual
acceptance. Source attribution and frozen import hashes are indexed in `ASSETS.md`.
