# M15: Multitier wire visuals and corner lane correction

## Change

The procedural tier-colored geometry now assigns each coexisting tier an
offset lane on a host face. Same-face geometry and coplanar quads preserve those
lanes at both positive and negative directions. For perpendicular face turns,
`CableVisualGeometry.spokeCorners` takes the absolute components of the ordered
face-normal cross product. This selects the canonical positive world axis along
the shared cube edge, so reciprocal spokes project to the same HV/MV/APC lane
coordinate rather than reversing lane displacement. This supplies corner
projection/wrapped-turn geometry; it is not finished authored or mitered art.
Topology and cable storage are unchanged by this presentation milestone.

## Validation

`CableVisualSyncTest` exhaustively checks all 24 ordered perpendicular face
turns for reciprocal world-space edge midpoints at all three tiers. It checks
transverse rendered positions within 0.004 (accounting for the renderer's
0.502 face-normal lift) and verifies that coexisting tier lanes remain distinct.
The test includes floor/wall, wall/wall, and ceiling/wall orientations. Existing
visibility and topology behavior is unchanged; spokes are still drawn from
visible nodes only and terminate at the shared cube boundary when the neighbor
is hidden. A hidden top-face cable concealed by a floor tile suppresses all
three tiers at that host face, while a visible neighbor's stub still points to
the covered tile boundary; no cable is drawn beneath the cover.

No bevel connector was added: the bounded 0.004 transverse tolerance covers
the small normal-lift difference while a connector would risk drawing across
concealed endpoints. No Minecraft game was launched.
