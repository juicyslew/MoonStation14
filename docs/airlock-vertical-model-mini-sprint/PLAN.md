# Accepted vertical airlock model

## Scope and decisions

Replace the current standard two-block airlock visual with the frozen accepted
`all_variants_revision2/standard_basic` Java exports from Asset Building Workspace.
The newer standalone `vertical_models` package is not accepted. One-high doors,
49-profile gameplay rollout and a new spray-painter tool are outside this scope.

Owner decisions:
- Keep all visible geometry within the two-block doorway; omit external pockets
  and retain existing root-volume clipping. Do not reserve neighboring blocks.
- Preserve SS14-derived 0.4/0.2-second gameplay phases. Reach visual clearance by
  the first-phase boundary and hold the endpoint during settling, rather than
  use the package's proposed linear 0.6-second travel.

Use authored upper/lower travel (+21.5/-9.5 model units). Preserve controller
identity, power/payment, collision, atmosphere sealing, wires, bump/contact,
crush/reopening and shared posture behavior. Color/style is visual data, not
replacement of the door's operational prototype or identity.

## Ordered work

1. Import only accepted standard-profile dependencies, preserving source bytes,
   hashes and attribution. Extend the existing baked renderer and shared geometry
   with one canonical vertical-part/motion binding; use resolved phase durations.
2. Validate actual resources, timing/clearance, clipping/UVs, all facings, panel,
   power/bolt presentation, closed item model and late-join endpoints. Reuse existing
   native fixtures and tag new focused coverage; run full JUnit and native checks.
3. Independently review reuse, source acceptance, two-block bounds and unchanged
   gameplay. Supply the rebuilt jar and concise visual retest steps. Owner visual
   acceptance is still required; write the closure handoff only when requested.

## Known baseline

Previous device sprint is owner-closed. Latest full native baseline was 725/725
selected, 685 passed and 40 failed, with power/topology prerequisite failures among
them. See its handoff and retained logs; do not treat overlap as proof of unrelated
failures or pull broad power fixes into this model work.

## Renderer binding

The renderer uses each imported baked quad's own sprite ID and normalized UVs;
it never remaps those UVs through the former default-airlock state sprites. Static
native overlays bind `closed_unlit_00_header`, `opening_unlit_00_header`,
`bolted_unlit_00_header`, and `deny_unlit_00_header`; the source supplies reader
overlays only for `opening_unlit_00_reader` and `deny_unlit_00_reader`. Selected
overlay quads are fullbright only while powered; base components, including the
reader pigment, remain ambient-lit. Frame-00 assets are intentionally static:
this does not add a native blink or panel-transition scheduler. Native `OPEN`
uses the persistent opening art; local phase timing reaches full visual clearance
at the existing first passability boundary.
