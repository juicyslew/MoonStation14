# Singularity power generation

## Target

A playable, server-authoritative power loop: assemble a particle accelerator,
charge a singularity generator, maintain containment with emitters, and harvest
radiation using plasma-fueled collectors connected to the existing HV network.
The singularity must have the SS14 visual effect, not merely placeholder particles.

Upstream reference: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Start from the power, machine-model, device-networking, chemistry/machine-slot,
equipment and atmosphere handoffs for existing services. Audit full upstream
prototype composition before each new capability; retain only supported local
components and test resolved inheritance and live consumers.

## Explicit scope decisions

- The user selected **full 3D movement and closed containment**, including top
  and bottom protection. This deliberately departs from SS14's planar world.
- Initial containment design: eight corner generators defining an axis-aligned
  box and six continuous field faces. Each face depends on valid charged corner
  and edge connections. A connected graph alone does not prove enclosure.
  Geometry must be loaded and valid; no forced chunk loads. Exact placement,
  clearance and collision rules remain implementation/audit work.
- Preserve upstream energy growth/drain, selected PA strength, collector fuel
  consumption and actual supplied watts. Do not invent a safe-stage governor,
  infinite fuel, constant collector output or permanently frozen singularity.
- Station/entity matter consumption and destructive singuloosing are deferred.
  This phase is not acceptance of complete unsafe-reactor behavior. Record that
  missing behavior explicitly rather than disguising it with a safety clamp.
- When new items or machinery need authored 3D models, notify the user and proceed
  with clearly identified placeholders. Modeling does not block implementation.
- Startup requires a verified closed volume, replacing the planar generator
  failsafe with a deliberate 3D adaptation. Loss of containment after startup
  must not silently teleport or immobilize the singularity.

## Ordered work units

### Connected-client corrections (2026-10-11, in progress)

- Correct reactor creative-tab names and APC breaker labels; lower the default
  fixture origin one block without changing explicit coordinates.
- Preserve real cable support while making the obstructing fixture rear walls
  transparent. Validate final placement after installing operator supplies.
- Synchronize PA placement identity/profile to clients so the existing authenticated
  open-reply correlation can succeed; do not weaken session checks.
- Give live singularities an entity sprite renderer, fix PA frame sampling and
  emitter visibility, and replace field outline art with continuous, fullbright
  3D surfaces. Continuous surfaces are a requested Minecraft visual adaptation;
  their geometry must still come exclusively from authoritative active faces.
- Include all applicable pinned SS14 audio: emitter discharge, singularity
  lifecycle/ambient, projectile impacts and supported generic slot/valve actions.
  Reuse controlled-camera sound dispatch. Do not invent machinery hums where
  upstream has none. Record unresolved sample attribution in
  `docs/audio-unknown-provenance.md`.
- Obtain fresh automated evidence, then repeat connected-player sound/UI and
  GPU acceptance. Earlier green server runs do not establish these corrections.

### Required playable reactor fixtures (2026-10-11)

Readiness now requires two world fixtures backed by one shared placement recipe:

1. **Inactive:** assembled PA, zero-charge startup generator, eight corner field
   generators and eight real emitters; machines initially off, finite collector
   fuel and ordinary operator supplies. A player must be able to create the
   singularity through existing controls and actual gameplay ticks.
2. **Running:** start that same installation through normal scoped control
   transitions. Real powered emitter bolts must maintain all six containment
   faces; PA particles must create the singularity. Actual radiation must consume
   collector fuel and deliver measurable power to a real export load.

Auxiliary HV startup/control power, if used, must be clearly labeled and isolated
from collector export. Current APC batteries cannot bootstrap MV machinery; no
finite HV bootstrap capability is established. Do not count auxiliary delivery
as singularity output or claim autonomous operation without measurement.
Start at PA Level0 and measure energy/capture/output rather than assuming a stable
stage. Do not inject field charge, directly spawn the running singularity, freeze
it, override allocation, or invent a hidden safety governor.

Implementation sequence: canonical placement and refusal tests; wiring/supplies;
world commands and diagnostics; scoped running startup; sustained integrated
validation and concise human startup instructions. Neither fixture is complete
merely because placement compiles. Connected-client operation and GPU validation
remain separate acceptance requirements.

Maintenance acceptance additionally requires at least five minutes after real
startup, with observed PA energy gains and continuous collector-only load power.
The first 60-second run delivered 12 kW but lost about 60 energy; that demonstrated
generation, not sustainable feeding. The current experiment uses a compact
four-block-edge cube, still six-faced and fully 3D, with unchanged PA strength
and gameplay profiles. Do not substitute frozen motion, forced energy, or an
automatic stage governor for adequate physical beam coverage.

Running-mode commissioning is an explicit fixture-local operator sequence:
create the singularity with Level0, use Level2 until actual particle feeding
establishes a measured reserve, then return to Level0 once. The reserve is the
resolved Level2 threshold plus two Level0 full volleys (default 260 energy).
This is not an SS14 default admin procedure. It uses normal controls/allocation,
reports the target visibly, and never changes strength again after commissioning.
It is bounded startup automation, not a continuing energy/safety governor. The
manual setup exposes the same procedure. A fixed 30-second boost was insufficient
in validation and is superseded; maintenance is measured after commissioning.

### Remaining integration sequence after stabilization

The current foundation baseline passed 3,221 full unit tests and 808 full native
fixtures (see `VALIDATION.md` for snapshot logs). It is not a complete reactor.

1. Register the generator startup actor around the existing resolved profile,
   accumulator and closed-volume proof; correct cooldown ordering and validate
   persistent one-spawn/inert behavior.
2. Add real PA particles and three-beam waves using fresh APC/MV allocations,
   six-second cadence and resolved strength values. Keep generator charge
   separate from singularity food ingestion; reuse loaded projectile sweeps.
3. Close gravity authority/prediction/native-target paths and collector canonical
   permanent release/player tank controls using existing motor and slot services.
4. Bind approved canister/suit art, run integrated contained-reactor checks and
   full regression, then obtain client GPU/presentation acceptance. Power-machine
   model candidates awaiting review are not approved assets.

1. **Rendering spike:** import attributed level artwork and translate the actual
   screen-warp shader through native NeoForge rendering. Use an explicitly named
   visual-preview fixture, not a fake working reactor. GPU acceptance is separate
   from compilation/unit checks. Resolve perspective scaling and framebuffer
   lifecycle before treating the renderer as production-ready.
2. **Radiation, fuel and generation:** typed finite gas-tank storage, collector
   item-slot ownership, periodic radiation delivery and damage/shielding; extend
  existing HV allocation to accept actual variable supplier output. Preserve
  existing fixed-source behavior for existing devices. Establish player-facing
  tank filling/insertion/removal, not only an admin-filled fixture.
   Upstream canisters fill an inserted tank through their release valve;
   reuse that behavior rather than inventing an unrelated fuel-exchange mechanic.
3. **Emitters and containment:** power-gated emitter bolts, anchored generator
   charge buffers/decay, derived six-face enclosure, full 3D swept collision and
   client field presentation. Prototype-backed construction/tools and real power.
4. **PA and singularity lifecycle:** seven-entity assembly, rotated layout,
   MV/APC power, controls, projectile waves, generator charge and spawn; energy,
   movement/gravity, radiation and level appearance. No duplicate electrical or
   machine-window service.
5. **Integrated fixture and acceptance:** exercise normal startup, sustained
   fuel-powered output, power/field interruption, repair and tank replacement.
   Test prototype consumers, ownership, network/session authority and persistence;
   focused tagged checks followed by the full native suite for broad integration.

## Verified parity anchors

- Seven PA entities: control box plus end cap, fuel chamber, power box and three
  emitters. Control box: 250 W APC receiver. Enabled PA MV demand: 500 W Standby,
  2,000/3,500/5,000/6,500 W for Level0/1/2/3; minimum received fraction 99.9%.
- Three particles per six-second wave. Generator charge per particle: 1/2/4/8;
  singularity food energy: 10/20/30/60. Generator threshold 16; initial energy 180.
- Levels 2–6 start at energy 200/500/1,000/2,000/5,000. Drain for levels 1–6 is
  1/5/10/20/0/0 per second; level 5 can breach. Minimum PA input averages 5 energy/s;
  that average assumes all three particles are captured. This is not proof of a
  steady stage with discrete six-second waves, movement and gravity. Particles
  have a large collision fixture (half-width 0.48), so neither three hits per
  wave nor only the center lane hitting is established by centerline arithmetic.
  Startup timing and sustained capture must be measured in the actual fixture;
  do not advertise a guaranteed six-wave startup or level-2 equilibrium.
- Field generators: maximum charge 25, connection minimum 6, valid bolt adds 3,
  decay 2 per 20 seconds without a fresh hit, maximum connection length 8.
- Radiation normally updates every second, with linear distance falloff and
  blockers. Collector output depends on received dose, plasma moles and gas
  temperature; fuel depletes. Ordinary collector starts with an empty PlasmaTank;
   a prefilled tank is a distinct prototype. Output holdover is two seconds:
   upstream sets `PowerTicksLeft = TickRate * 2`, decrements once per server
   power update, and clears supply on the update after the counter reaches zero.
   Disabling does not immediately clear previously generated supply.
- Collector supply has no upstream blanket 30 kW source clamp. The current local
  solver has a 30 kW source ceiling; its source-rating contract must be extended
  so collector offers are not silently clipped. Existing downstream transfer
  limits are separate from the collector's actual generation.
- PA/emitter integration also requires correcting unconditional rated APC demand:
  the current local MV solve requests 30 kW per APC regardless of battery space
  or receiver demand. That can starve a fully affordable PA. Upstream battery
  input is dynamic, capacity/time-bounded and includes downstream demand;
  ordinary consumers and charging share proportional allocation. Preserve
  existing local capacity/rate/transfer adaptations, but do not invent PA-first
  priority or treat a maximum rating as permanent consumption.
- Singularity artwork is static, unshaded, level-specific. The separate overlay
  sequentially warps screen coordinates for up to five sources, with a reduced-
  motion gate and no world-occlusion test. Do not substitute animated swirl art
  or depth-mask the distortion and call it exact parity.

Sources: upstream `Singularity/*.yml`, `PA/*.yml`, `gas_tanks.yml`, singularity/
PA/radiation systems, `SingularityOverlay.cs` and `Textures/Shaders/singularity.swsl`.
Detailed source-to-component mappings belong in `COMPOSITION.md` as implemented.

## Open evidence

Perspective/render-target behavior needs actual client/GPU checks. Three-particle
collision coverage, discrete energy transitions, full 3D shielding/field geometry,
player-facing fuel tooling and precise power-tick holdover require consumer tests.
Asset provenance must be established before import; do not infer authorship from
file names. No functioning reactor or verified GPU effect exists at plan creation.

## Fuel-canister architecture

Use a movable world canister, extending existing loose-object movement/pulling
where practical, rather than treating it as an anchored block or a normal carried
gas tank. Its inserted tank uses `MachineItemSlot` and the existing whole-stack
transfer service, with the canister entity UUID as persisted slot owner identity.
Entity lifecycle must restore/suspend ownership claims correctly; this is not
already provided by the movement base. Ordinary hand pickup of the structure is
denied through a supported admission policy.

This avoids broadening storage nesting or generalizing bag/belt grids merely to
implement a machine slot. Gas and slot contents belong to the canister entity;
an item used for its visual representation is not a second fuel inventory.
Refill commits must preserve both finite source gas and the exact inserted stack.
Pipe coupling and overpressure destruction remain separately identified gaps.
