# Disposal parity baseline

Reference checkout verified at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. This records established behavior;
remaining integration questions are not implementation claims.

## Resolved core composition

- `DisposalUnit → DisposalUnitBase → BaseMachinePowered → BaseMachine →
  BaseStructure`. Relevant inheritance includes static machine physics,
  anchoring, no-rotation transform, damageability, APC receiver **1000 W**,
  extension receiver and power switch. The disposal base adds its container,
  disposal-grid placement, climbability without vaulting, UI and destruction
  behavior. The concrete unit adds disposal logic and throw insertion with
  probability **0.67**. Mailing is a separate restricted sibling, not the
  ordinary unit's default eligibility.
- `DisposalPipeBase` defines static noncolliding anchored subfloor pipe
  behavior, rotation/pulling, metallic damage and tube/container composition.
  Concrete transit, bend, trunk, junction, tagger and router components provide
  different routing behavior.
- `DisposalHolder` declares holder/container behavior, not a Physics/Item
  prototype. Runtime creates and moves it. Any Minecraft entity/containment
  representation is an explicit adapter, not invented inherited SS14 physics.

Sources: `Resources/Prototypes/Entities/Structures/Piping/Disposal/units.yml`,
`pipes.yml`, and `Structures/Machines/base_structuremachines.yml`.

## Units, entry and flushing

| Behavior | Verified default |
| --- | --- |
| Self-entry | Contextual climb-inside verb; 0.5 s |
| Other-entity insertion | 2 s, movement/damage-interruptible |
| Manual flush scheduling | 2 s |
| Flushed visual window | 3 s |
| Automatic engage | Enabled; 30 s after insertion |
| Pressure readiness | Timer abstraction, 0.05 per second / 20 s recharge |
| Movement escape | Movement permitted, at least one hand; unit-wide 0.5 s cooldown between exit attempts |
| Unit air volume | 2500 L |

Insertion requires an anchored, insertable unit and an Item or MobState
entity; non-items require colliding physics. No item-count/volume capacity
is declared by the audited ordinary-unit prototype. Do not invent a gameplay
limit solely to bound a UI packet.

Flush requires anchoring, power, a ready timer and a same-tile entry trunk.
Missing entry cancels engagement without ejecting contents; the system has
already advanced `NextFlush` by the automatic-engage interval, and retains
that deadline. Power loss clears scheduled flushing; power restoration
recalculates an engaged flush. EMAG
disables the timer requirement, not gas sampling or power; implementing an
EMAG item is not part of this slice.

The timer is **not** a reservoir-pressure measurement. At successful flush,
`HandleAir` replaces unit air with gas removed from the environment; flushing
moves all contents into one holder, merges the sampled air into it and clears
unit air. It does not refill
the reservoir continuously during the charging timer. The intake calculation
uses 0.1 times the deficit toward `0.25 × 1 atm × 1.01`, unit volume, external
temperature and the gas constant. Exact removal behavior is delegated to the
gas-mixture consumer; preserve finite source conservation locally.

Unanchoring/destruction eject contents; explosion includes contained entities
in explosion contents. The source self-entry is a verb, not a special hotkey
or automatic capture of every nearby jumping character.

Before flush, an occupant uses surrounding tile/room air: the unit has no
airtight/tank air boundary, and its reservoir is not an exposure source.
Only the holder relationship redirects inhale/exhale/exposure to carried air.
Verified through `BeingDisposedSystem` and server atmosphere
`GetContainingMixture`, not inferred from the unit's `Air` field.

Sources: `SharedDisposalUnitSystem.cs`, `DisposalUnitComponent.cs`, server
`DisposalUnitSystem.cs`, and `Atmospherics.CellVolume`.

## Transport and routing

- One holder per flush; no audited packet merging or tube capacity limit.
- Holder fallback air volume is **70 L**. Carried air remains finite.
  Gas merge retains destination volume rather than adding source volumes.
- Each segment takes **0.1 s**, with interpolated position. It is not a
  pressure/velocity simulation and does not consume gas at each segment.
- Straight transit reverses if entering from its forward side. Bends and
  junctions use their rotated direction sets. Ordinary junctions randomly
  select a side when entered from forward/invalid direction; otherwise go
  forward. Do not substitute round-robin routing.
- Connections require adjacent anchored tubes with reciprocal directions.
- Taggers append tags; routers select the configured side for matching tags,
  otherwise forward. Regex validation is server-owned.
- Trunks provide entry. Arrival back through the trunk's facing direction
  exits. There is no verified separate outlet prototype; outlet construction
  references are commented out.
- Invalid direction, missing/disconnected tube or absent next tube exits the
  holder. At a disposal-unit tile, contents pass through the unit and eject;
  otherwise world ejection calls `TryThrow` with a direction vector of magnitude
  **3** and base throw speed **10**. Verified in pinned
  `Content.Server/Disposal/Unit/DisposableSystem.cs:121-127`; the exact overload
  is `TryThrow(EntityUid uid, Vector2 direction, float baseThrowSpeed = 10.0f,
  EntityUid? user = null, ...)` (`Content.Shared/Throwing/ThrowingSystem.cs:87-98`),
  and its documentation identifies `baseThrowSpeed` as throw velocity
  (`:79-82`). The Minecraft adaptation uses initial horizontal velocity **0.5
  blocks/tick** (10 blocks/second); Minecraft gravity and flight dynamics mean
  the resulting travel distance is not guaranteed to match SS14's generic throw
  system. This is initial-velocity parity, not full throw/flight parity.
  Direction changes play clang at volume **−5**;
  default blunt turn damage is **0**.
- Exit merges holder air into its containing environment when available,
  clears that gas and deletes the holder. Do not silently discard carried gas
  in a known finite room. Unavailable containing mixtures need an explicit
  local outcome rather than an invented exterior sink.
- `BeingDisposed` routes atmospheric exposure, inhale and exhale events to
  holder air. Bodies/items are payloads with collision disabled in transit.
  Local breathing and timed-item integration must be traced rather than
  assumed or silently suspended.

Sources: `DisposalTubeSystem.cs`, `DisposalHolderComponent.cs`,
`DisposableSystem.cs`, `BeingDisposedSystem.cs`, `DisposalTubeComponent.cs`.

## Editing and construction

Core pieces: straight, bend, trunk, three-way/flipped junction, Y and X
junction, plus tagger/router for tag-directed routing. Signal variants and
mailing are separate scope.

Pipe construction uses **2 steel, 1 s**. Junction shape conversions use a
screwdriver, **1 s** per directed edge: normal → Y → flipped → X → normal.
These are not arbitrary bidirectional conversions. Welder deconstruction takes **1 s**, returning
two steel; broken-pipe recovery returns one steel. Signal variants also use
cable but are not silently treated as ordinary pipes.

Machine construction stages use **1 steel / 1 s**, **2 cable / 1 s**,
**2 plastic / 1 s**, screwdriver **0.25 s**, then **1 steel / 1 s** only when
anchored. Graph reversals and exact tool requirements remain the authority;
they must not be replaced with generic Minecraft axe mining.

Sources: construction graphs `utilities/disposal_pipes.yml` and
`disposal_machines.yml`. Local pipe/tool consumers are implemented; machine
construction remains the explicit BlockItem adaptation in `architecture.md`.

Pipe sprites: `Structures/Piping/disposal.rsi`, 32×32, directional and
anchored/free states; metadata credits CEV Eris and named contributors under
CC-BY-SA-3.0. Imported unit/tool sprites and audio are documented in
`assets-and-attribution.md`; unresolved per-file audio terms remain recorded in
`docs/audio-unknown-provenance.md`. Connected presentation checks are pending.

## Local integration constraints

### October 4 spatial replacement audit

### Interim disposal-family construction controls

The pinned checkout was reverified for the authorized family-mode follow-up.
`Content.Client/Construction/ConstructionSystem.cs` handles OpenCraftingMenu,
Use on a ghost and EditorFlipObject; an ordinary ghost rotation/cancel default
was not established. Local construction/change, exit and rotate bindings are
explicit UX adaptations, not upstream keybind parity claims.

Pipe parent composition and the two-steel/one-second initial graph step remain
verified in `pipes.yml` and `Graphs/utilities/disposal_pipes.yml`. The supported
local inherited construction-step component supplies material/count/time to
live placement and readiness presentation. Connection-aware frame candidates
preserve reciprocal ports and directional junction/router semantics rather
than treating all equal-looking meshes as equivalent. No new signal/mail
variants or the full crafting browser are included in this follow-up.

Upstream screwdriver steps cycle junction types through directed graph edges.
The owner's intended orientation-only screwdriver adjustment is a deliberate
departure, not an upstream rule; implementation/validation must distinguish it
from the separate initial-placement rotation control.

The pinned checkout was reverified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`Resources/Prototypes/Entities/Structures/Piping/Disposal/pipes.yml` resolves
`DisposalPipeBase` into anchored Transform, static **noncolliding** Physics,
SubfloorMask fixture, Anchorable, Rotatable, Pullable and tube/container data.
Concrete straight/bend/trunk/junction/tagger/router nodes supply their respective
routing components and containers. `DisposalTubeSystem` startup/anchor events
make connectivity active only while anchored and require reciprocal ports.

The authorized own-block, colliding thick 3D tube model, six-face routing and
sealed steel-wall crossings are explicit Minecraft adaptations, not upstream
Physics or native vertical-route parity. Local geometry and orientation are
supported typed data consumed by block shape, placement, routing and rendering.
Material/time/anchor/routing-role/container rules remain the parity target.

The controlled body is not the spectator carrier. Existing harness movement,
camera/session eligibility and hand/action authority reject vanilla passenger
bodies. Do not broaden mounted-body permissions or assume `startRiding`
preserves authority unchanged. Containment needs exact owner/body relations,
preserved mind/epoch/camera, restricted external actions, and a dedicated
containing-unit interaction path.

Held grenade fuse ticking currently depends on hand authority; new contained
owners must not freeze armed items or lose contents in the explosion primitive.
The legacy respiration bridge is a no-op, but `TickHooks` separately calls
live `LungSystem.tickIfDue` and barotrauma processing. Lung exchange currently
uses eye-position atmosphere sampling with validation before finite-room
mutation. Holder air must replace that source while contained; it must also
supply the correct volume to pressure/breath-demand calculations. The global
atmosphere enable switch still gates ordinary physiology operation.

Disposal routing is independent of finite gas-pipe lanes. Subfloor placement,
3D orientation, loaded-chunk boundaries and canonical-input UI adaptations
must be recorded explicitly once architecture is selected.
