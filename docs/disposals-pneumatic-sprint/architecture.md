# Disposal architecture decisions

## Spatial model

### Replacement target — owner decision, October 4, 2026

The floor-overlay model has been replaced in code; replacement acceptance is
still pending. Disposal pipes are substantial 3D pipes capable
of carrying characters, spatially comparable to gas piping rather than wiring:

- Exposed segments occupy world space, with thick geometry and collision;
  they are not thin sprites hidden beneath station-floor tile finishes.
- Every exposed segment occupies its own block cell. No horizontal disposal
  run can be hidden by floor coverings, and no pipe exists on a floor face.
- Sealed structural crossings retain the solid host and its atmosphere seal,
  following gas piping's exposed-segment / sealed-wall-crossing distinction.
- Placement orientation comes from the targeted face, not a Facing button.
  Routing must support all six directions, including vertical runs.
- Disposal topology, holder/item/body ownership and finite carried air stay
  independent of gas lanes and gas-flow simulation.
- Structure construction remains in-place recipe selection and placement
  preview; this change does not authorize a generic crafting-system expansion.

Storage/host, placement, topology, rendering, collision, unit-entry and ejection
have been updated together. Earlier automated passes cover the old floor
overlay, not the replacement; fresh validation is required.

The owner authorized implementation on October 4. Exposed runs use pipe blocks
in air; ordinary straight crossings may occupy eligible sealed steel-wall cells
without replacing their hosts. These are separate spatial representations,
not floor-cover layers. Six-direction orientation uses an orthogonal frame so
bends/junctions preserve their directed SS14 port roles in any world plane.
The requested thick collision geometry is a Minecraft departure: upstream
`DisposalPipeBase` is anchored/static but noncolliding and uses `SubfloorMask`.
Resolved local tube geometry must drive collision, selection and previews.

### Implementation contracts

- `DisposalTubeRecord` identifies an exposed pipe block or a sealed wall-host
  incarnation, with a perpendicular forward/up frame and revision. World
  lookups validate the actual host; no station-floor record is live piping.
- Inherited `tubeDiameter` defaults to 14/16 block. Resolved geometry drives
  collision, outline, mesh and preview. Collision is intentionally non-full,
  so exposed pipes do not become airtight wall substitutes.
- Frame mapping preserves directed routing roles in all six world directions.
  A trunk has one logical routing port and a separate physical world-UP unit
  interface. A valid inlet below a unit may point DOWN or sideways, not UP into
  that unit. Terminal return remains valid when the exit trunk breaks.
- Initial placement uses a construction-family plan bound to the exact body/mind
  epoch. Resolved construction data supplies starting material, count and time
  (ordinary pipes: two steel, one second). Steel alone does not select a shape
  or open a construction picker.

### Authorized construction-family follow-up

The old steel-triggered shape picker is superseded. The construction-browser
key will enter Disposal Pipes directly until the shared browser sprint exists.
Construction intent consumes no hand slot. A compact family palette chooses
the shape; HUD text shows its starting material/count, Change/Exit bindings,
and wrong-material/insufficient/ready states separately from placement validity.
Only use of the relevant starting material at eligible placement space changes;
ordinary tools, doors, movement and inventory remain usable. Exit clears the
plan and preview, and holding unrelated items hides the ghost but not status.

Provisional project-local bindings are **G** construction/change, **H** exit,
and **R** rotate. Hold the construction key and scroll through the compact
family palette; release it to keep the chosen shape. These are rebindable
Controls entries and the HUD displays the configured bindings. The palette
is an overlay, not a container screen, and does not stop movement.

Placement prefers compatible neighbour ports, then face/full-look orientation;
Rotate offers distinct alternatives, including disconnected new-run choices.
Screwdriver adjustment is orientation-only rather than junction-type cycling.
These controls are explicit Minecraft adaptations. The shared browser, queued
item crafting and other construction families remain the next sprint.

Unanchor an installed tube, use a screwdriver to open its existing configuration
editor, preview an orientation and explicitly confirm. Confirmation starts a
one-second Screwing action (scaled by resolved tool speed), not an instant
rotation. Movement, damage, menu closure, tool changes and record/host changes
cancel it. Rotation preserves tube ID/type, routing configuration and host UUID;
the editor warns about lost existing reciprocal connections without forbidding
intentional rerouting. E configuration remains separate from family selection.

- Straight sealed-wall installation leaves an eligible steel wall unchanged;
  shared host-generation identity prevents replaced walls reviving stale pipes.
  Disposal routing and payload ownership never use gas-lane identities.
- The existing sealed-host contract invalidates a registered incarnation before
  every section write, including a same-state/same-type replacement. Disposal
  uses that contract unchanged; it does not weaken gas-pipe invalidation.
- Visual and build protocols are version 2. Client records are presentation
  state; server lookups, geometry, placement and ownership remain authoritative.

Routing uses only loaded cells. Unloaded is distinguishable from missing;
routine transport does not force chunks. Terminal handling must preserve
payloads at a known loaded location. No separate outlet prototype is invented.

## Ownership and containment

Items use whole-stack disposal owner locations in the existing ledger, with
exact-source transitions, stable tokens and preserved contained storage data.
Units and transit holders have separate owner identities. Transfer staging
and compensation are ordinary runtime safeguards, not crash-durable promises.

Characters remain the exact loaded body entity. Disposal containment is not
vanilla riding, spectator inventory, entity removal or mind/camera transfer.
One level-owned containment service indexes exact body-to-owner membership;
units and holders implement its narrow owner contract. Containment suppresses
physical movement/collision and presentation while preserving prior flags for
exit. Owned movement becomes unit escape intent or transit suppression.
External actions are restricted independently of control-session identity.

Character snapshots explicitly carry GROUNDED/UNIT/TRANSIT movement mode.
Contained clients continue sending intents but suppress grounded prediction
and replay; authoritative exit resumes it. A successful unit escape must return
the actual safe exit position, never repin to a cached former owner.
An explicit synchronized presentation marker hides contained bodies even from
spectator viewers; it is never used as server authority.

The containing-unit interaction path must validate that exact owner/body
relation; it must not authorize general mounted bodies or all nearby units.

## Time, power and air

Unit eligibility is SS14's charging timer, not a measured gas-pressure gate.
Resolved APC demand is 1000 W, projected into the existing loaded power
runtime with fresh full-delivery validation. Holder traversal is 0.1 s per
segment, independent of a gas-flow solver.

Unit sampling uses the upstream 2500 L intake calculation at flush. Gas merge
retains the holder's 70 L destination volume. Finite carried moles and energy
feed validated lung exchanges and relevant environmental exposure consumers.
The existing world's 1 m³ cell default remains unchanged; holder calculations
must explicitly use holder volume. Known containment with unavailable air is
unknown, never a fallback to surrounding room air. Known empty air is vacuum.
Exit returns carried air to its known containing environment where supported.
UNIT owners deliberately expose a world-room source before flush, matching
upstream's non-airtight unit. This is distinct from falling back to room air
when a TRANSIT owner cannot supply its finite holder source.

Persistent action/fuse/fuel deadlines use the level game-time clock, not server
uptime tick counts that reset on restart. Power meter freshness may continue
using its existing transient solve sequence.

Timed items and lit welders require owner-specific ticking across containment;
moving stacks must not reset deadlines or burn twice. Welder fuel is a finite
stack adaptation of upstream solution storage. Refilling is not yet implemented
and must remain an explicit limitation until an appropriate consumer exists.

Held-fuse progression uses passive exact-body authority against the captured
current menu, separate from strict hand-mutation authority. Opening a unit
panel must not pause a connected character's held fuse. Disposal-owned payloads
tick through their owning store; armed world ejection uses the existing grenade
projectile with its original deadline, not an ordinary unticked item drop.

Unit destruction hands any retained reservoir gas to a resolved, gas-only
pending-release holder before clearing the reservoir. Rejected carrier spawn
uses dimension SavedData keyed by the removed unit's UUID; loaded-only retries
transfer that record to a carrier exactly once. Compare-and-replace failures
undo the staged carrier/record. Chunk unload instead persists the unit normally.
`DisposalPendingAirSavedData` is a narrow normal-removal fallback, not a recovery
journal. Its live spawn-rejection handoff still needs dedicated integration proof.

## Integration acceptance

Prototype/domain tests prove foundations only. Acceptance additionally requires
player construction, live power/flush/traversal, controlled self-entry and
self-flushing, destruction/ejection, authority/prediction checks and attributed
presentation. Isolated owner adapters or an unbound flush callback do not meet
those gates.

## Explicit limitations

- Unit placement currently uses a canonical body-owned BlockItem rather than
  SS14's multi-stage steel/cable/plastic construction graph. Pipe construction
  and tool editing are the implemented player construction slice.
- Self-entry is implemented; dragged insertion of other characters and
  individual payload rummaging are not established by the current UI.
- Held-item insertion uses canonical primary Use. Automatic capture of generic
  thrown projectiles is not established; generic throwing is a separate owner.
- Pipe structural damage/broken-node recovery and welder refilling remain
  unimplemented. Ordinary welder deconstruction is distinct from broken recovery.
- Native Minecraft ejection matches the verified initial launch speed but not
  SS14's full three-unit destination/flight-time throw simulation. Generic
  throwing belongs to another workstream.
- If a destination unit accepts a returned payload but no safe spatial exit
  exists, it retains that payload under valid unit ownership. This is a
  Minecraft collision adaptation, not a failed ownership transfer.
- Unknown gas-release endpoints retain owned holder gas for retry; routine
  routing does not force chunks or invent an exterior gas sink.

- Connected-client camera, menu, sprite and audio checks remain separate from
  automated server validation. Unclear audio licenses are recorded in the
  repository's unknown-provenance register.
