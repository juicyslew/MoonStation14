# First containment-field domain slice

## Current live geometry

The historical slice below predates the live reactor. Current runtime topology
still uses integer generator block indices, but `FieldGeometry.center` converts
them to block-center world coordinates (`index + 0.5`). Rendering, face-contact
collision, failsafe distances and network relevance share that conversion.
Faces attach to generator centers, not block corners; renderer-only offsets
would violate this invariant. Current material policy and manual GPU limits are
documented in `docs/field-surface-rendering.md`.

Upstream checkout `C:/Users/William/Documents/SS14-dev/space-station-14` is pinned at verified `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. `Resources/Prototypes/Entities/Structures/Power/Generation/Singularity/containment.yml` composes `ContainmentFieldGenerator` over `BaseStructure`; the component defaults consumed by `Content.Shared/Singularity/Components/ContainmentFieldGeneratorComponent.cs` are charge 25, bolt charge 3, threshold 6, decay 2 every 20 seconds and max connection length 8. Server behavior is in `Content.Server/Singularity/EntitySystems/ContainmentFieldGeneratorSystem.cs`: a valid emitter bolt resets decay elapsed time; endpoints retain a connection while either remains above threshold, while removal/unanchor invalidate it. Enabling requires anchoring and a connection; enabled generators cannot be unanchored. These defaults resolve through a local abstract typed base into the concrete field-generator profile and are consumed by pure state transitions.

`FieldGeneratorState` models charge, decay progress, anchor and enabled admission, but is not wired to a world block. `ClosedFieldVolume` is the deliberate 3D adaptation: exactly eight distinct axis-aligned box corners, twelve valid endpoint edges, each span at most eight blocks, and all corner positions already loaded. It does not load chunks or create a collision barrier. A connected arbitrary graph does not prove six continuous faces; this check proves only the box's twelve corner-to-corner boundary edges as input to later face construction. No barrier/guaranteed safety, projectile collision, renderer, registration, power or emitter exists in this slice.

This source-only domain deliberately adapts SS14's planar visual fields to continuous 3D faces. Machine art remains an unconfirmed inventory gap and needs an authored placeholder/model before registration. This is not live containment or reactor behavior.
