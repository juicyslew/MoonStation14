# Field-generator feedback and contact geometry

Upstream containment prototype verified at
`Resources/Prototypes/Entities/Structures/Power/Generation/Singularity/containment.yml`
on pinned HEAD `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. It inherits
`BaseStructure`, overrides the fixture with a circle radius 0.4, and exposes
independent power, field, and connected layers. The corresponding system
projects a1 when enabled (including zero charge); on means connected only.
No toggle audio was verified.
The upstream point light is enabled only when connection count is positive
(`UpdateConnectionLights`); it is not an enabled-at-zero-charge substitute.

**R6 source limitation remains true:** its a1/a2/a3 pixels are identical to
default; p1/p3/p6 change the power lens and on changes the activation lens. The
owner-approved R8 texture-only revision is imported and wired through the
existing native adapters. R8 provides dark default, increasing one-third/two-
thirds/full cyan vertical fill in a1/a2/a3, and independent power/connected
indicators. The original 289 accepted R6 power-collection resources remain
unchanged; R8 import and explicit owner approval dated 2026-10-12 are recorded in
`assets/moonstation14/provenance/reactor_r8/generation_singularity_containment/`.
External candidate acceptance remains unmodified/not claimed. Current final
headless validation is **3,374/3,374 units** (664 XML suites, no failures/errors/
skips), focused native **40/40 selected**, full native **858/858 passed**; see
`VALIDATION.md`. GPU visual acceptance remains pending. Earlier failed full-run
power/continuity cases remain unclassified historical evidence, not harmlessness
claims.

The accepted model's metal foot is x/z 3..13, y 0..2 units; its column is x/z
6..10, y 2..20. The native Minecraft shape uses those bounds rather than a full
cell cube. It extends to y=1.25 blocks because the accepted column does; this is
an intentional mesh-based adaptation, not the upstream circle fixture. The
generator's placement anchor remains `FieldGeometry.center`; bolt radius and
sweep extent are unchanged.

Selection deliberately remains a conservative full-width, 1.25-block-high
outline so visible lobes remain usable with the shared native hand-target probe.
The narrower metal shape is the collision authority; selection is not substituted
for projectile contact. `EmitterFiringGameTests` now exercises registered live
bolts in X/Z lanes against the column, requiring exactly one resolved charge
increment and bolt consumption. The focused adapter/contact run passed **4/4
selected**, from **854 available**; the combined native run passed **25/25
selected**, from 854. These are server-contact checks, not proof of client beam
terminal presentation.

Known limitation: a horizontal native clip wholly above the generator's owning
cell can miss its overhanging column, because native block-ray traversal does
not visit the cell below. The upper-cell diagnostic records this miss; it does
not establish full 3D fixture parity. Standard emitter lanes at generator-center
height are covered. No broader multiblock collider or projectile-extent sweep
was introduced, and no bolt is kept damaging after authoritative contact.

The R8 adapter repartitions the same nine physical source elements: base has
`field_base`; each field tier has `field_column`, `column_rear_rib` and the four
lobe/cap elements; power and connected overlays each retain their one lens.
Geometry, UV and face data, collision bounds, orientation and placement anchor
are unchanged. Authored shade is preserved on the other eight elements; the
existing activation-lens adapter remains unshaded in both states, unlike its
`shade=true` source element. This established connected-LED readability
adaptation does not make the R8 column bar selectively emissive. Cyan art is
ordinary texture color:
emissivity is not implemented, and neither column nor metal is fullbright.
For manual comparison use a fresh inactive fixture without an emitter, then E
on one isolated generator to compare dark/off against enabled at zero charge;
a connected running generator retains the existing cannot-toggle-off rule.
