# Accepted Power R6 model bindings

Acceptance is current: `terminal_support_r6/user_acceptance.json` records
`USER_ACCEPTED_AS_IS_current_Power_R6` on 2026-10-11 (56 appearances / 86
editable projects). The acceptance explicitly does not claim loader/runtime
validation and does not authorize rebuilding or correcting art.

## Source and coordinate contract

The accepted exports in
`../Asset Building Workspace/assets/Structures/Power/candidates/terminal_support_r6/export`
are the sole art source. PA model IDs are `generation_pa_{control_box,end_cap,
fuel_chamber,power_box,emitter_port,emitter_fore,emitter_starboard}`; singularity
IDs are `generation_singularity_{emitter,containment,generator}`. Models use
Minecraft -Z as front, +Y up and 16 model units per block. Their X/Z +8 centering
has already been applied exactly once. Native adapters must not recenter.

## PA presentation mapping

The seven PA roles remain the resolved identities/offsets/orientations in
`PA-COMPOSITION.md`; no art mapping changes that composition. Control state is
Unpowered exactly when the APC receiver is unpowered; otherwise Standby selects
Powered and Level0–3 select unlitp0–3. Other assembled parts select Unpowered
when disabled and their selected strength when enabled. Missing/lost assembled
parts present unpowered. End cap has only its accepted static completed art.
This is a presentation projection, not a change to APC/MV demands, control
authority, strength or firing.

## Emitter and field presentation mapping

Emitter runtime power selects the accepted `beam` surface state as the local On
presentation; requested-on without runtime power selects the accepted animated
`underpowered` state; not requested selects accepted `default`. Here `beam` is
the emitter model's accepted surface snapshot only: no projectile/beam effect is
imported or altered. Its native facing must preserve the
accepted muzzle direction for all six directions. Lock artwork is independent;
the local presentation is the accepted unlocked appearance and adds no lock
gameplay or unsupported state.

Containment generator is projected as three independent accepted indicators:
power tier hidden at zero charge, high at max charge (25 in current resolved
defaults), low below resolved connection threshold, otherwise medium; field tier
actual connections >1, ==1, enabled-with-none, or hidden; and connected light
only for actual graph connections. Charge and field tiers coexist; connections
come from `FieldContainmentRuntime`, not charge eligibility. `FieldGeometry.center`
continues to own all geometry at index + 0.5; model projection must not move its
anchors or import baked field/singularity effects. Startup generator uses only
the accepted fixed icon/model; no charge/spent variants are inferred.

## Resolved behavior and departures

Upstream parity references are the pinned revision
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. PA behavior is documented in
`PA-COMPOSITION.md` (notably `ParticleAcceleratorSystem.ControlBox.cs:289-362`
and the client part visualizer). Emitter is the
`SmallConstructibleMachine -> ConstructibleMachine -> BaseStructure` visual
state gate. Containment derives from `BaseStructure` and the resolved local
`FieldGeneratorProfile` / `FieldGeneratorState`; see `FIELD-GENERATOR-DOMAIN.md`.
Local APC and MV are separate network adaptations. None of these visual bindings
changes enabled state, power draw, charge/strength, graph identity or geometry.

The native containment adapter partitions the same nine physical source elements:
base `field_base`; each default/a1/a2/a3 field tier `field_column`,
`column_rear_rib`, both lobe/cap pairs; power tier `power_lens`; connected tier
`activation_lens`. All layers preserve source geometry, UV and face data and
compose each element exactly once; tier zero retains dark physical geometry.
Authored shade is preserved for the other eight elements. The existing adapter's
`activation_lens` is intentionally unshaded in both connected/off overlays (the
R6 source element itself has `shade=true`); this established LED-readability
adaptation is retained. It does not make the R8 cyan column bar selectively
emissive.
Approved texture-only R8 uses revision-specific
`textures/block/reactor_r8/generation_singularity_containment/` resources; R6
source resources remain immutable. The default model and item art use full R8
default geometry. Hashes, attribution, and explicit owner approval dated
2026-10-12 are recorded under
`assets/moonstation14/provenance/reactor_r8/generation_singularity_containment/`;
external saved acceptance remains false and is not represented as accepted.
R8 GPU acceptance is pending. Cyan fill is texture color only: selective
emissivity is not supported here, and no column/metal fullbright was added.

Field projection still consumes resolved `FieldGeneratorProfile` and live
`FieldGeneratorState`: disabled/zero-charge -> field tier 0, enabled at zero
charge/no edges -> tier 1 (R8 a1), one actual edge -> tier 2 (a2), multiple ->
tier 3 (a3), and actual edges alone select the connected lamp. Power tier stays
independent. `FieldContainmentRuntimeGameTests` covers fresh disabled/enabled
zero-charge projection, one genuine edge, and the existing multiple-edge case;
run focused `reactor-models,containment` tags when the lead validator authorizes
GameTests. `PowerR6MinecraftModelBakeTest` covers all 4x4x2 layer combinations,
source element equality, R8 atlas progression and independent lens regions;
`PowerR6MachineBindingsTest` verifies live resource pointers, item default and
all eight imported hashes. Final current headless validation is recorded in
`VALIDATION.md`: **3,374/3,374 units** (664 XML suites, no failures/errors/skips),
focused native **40/40 selected**, and full native **858/858 passed**. This does
not establish GPU acceptance; the earlier failed MV-outage wave-restart run
remains unclassified historical evidence in that validation record.

Runtime projections use same-block state changes with client update flags only
(2), retaining BE/placement identity and avoiding neighbor/topology notifications.
The PA controller resolves the loaded assembly before projecting its own state;
controller state is then mirrored to every validated non-control role while the
MV demand notification remains exclusive to the POWER role. Tagged server
coverage exercises the registered PA with real APC/MV power, live emitter-meter
transitions, and connected/disconnected/decaying field generators. CPU native
model tests deserialize and bake accepted meshes for the six emitter orientations.
