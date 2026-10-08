# Girder and wall construction parity slice

Verified upstream revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

References: `Resources/Prototypes/Recipes/Construction/Graphs/structures/girder.yml`
(Girder graph); `Resources/Prototypes/Recipes/Construction/structures.yml`
(starting recipe); `Resources/Prototypes/Entities/Structures/Walls/walls.yml`
(WallSolid and WallPlastic); `Resources/Prototypes/Entities/Structures/Walls/girders.yml`
(Girder). Graph consumers are `Content.Server/Construction/ConstructionSystem.Initial.cs`
(material placement), `ConstructionSystem.Interactions.cs` (step and edge checks),
`ConstructionSystem.Guided.cs`, `ConstructionSystem.Pathfinding.cs`, and
`ConstructionSystem.Graph.cs`. `EntityAnchored` conditions gate edges;
`SnapToGrid`, `SpawnPrototype` and `GivePrototype` execute graph completions.

The relevant graph path is start --2 Steel/1s--> girder; girder --2 Steel/2s,
requires anchored--> ordinary wall; wall dismantles with Welding/10s and returns
2 SheetSteel1. The plastic branch is girder --2 Plastic/2s, requires anchored-->
plastic wall; dismantling requires Prying/10s and returns 2 SheetPlastic1.
The girder itself unanchors to start with Screwing/2s and returns two steel sheets.
The reverse-wall return action does not encode extra tool effects beyond its
listed ordered steps. Real Prying is represented as a graph tool quality.

Girder `BaseStructureDynamic` composition includes Anchorable; Girder overrides
Transform to anchored and Physics to static. Pinned `AnchorableComponent` defaults
to Anchorable+Unanchorable flags, `Snap: true`, and `Delay: 1s`; `AnchorableSystem`
uses the held tool's Anchoring quality and `ToolSystem.UseTool` at the component
delay. Local `construction_entity` prototypes resolve the block mapping, default
anchored state, Anchorable/Unanchorable flags, one-second anchor delay, and
airtight material policy; live construction and atmosphere consumers read these
resolved values. Girder's missing upstream Airtight composition maps to an
explicit gas-passable policy despite its unchanged full Minecraft collision cube;
steel/plastic walls map to airtight. The Minecraft girder keeps its fixed grid
cell when toggled unanchored (local adaptation: no physics/pull system or
cardinal snapping/rotation); its persisted graph record nevertheless gates the
anchored wall edges and unanchored screwdriver dismantle edge. No unsupported
Anchorable component or generic physics component is introduced.

Verified entity composition from `Resources/Prototypes/Entities/Structures/Walls/girders.yml`
and `walls.yml`: Girder -> BaseStructureDynamic -> BaseStructure; WallSolid and
WallPlastic -> BaseWall -> BaseStructureWall -> BaseStructure. BaseStructure
defaults include anchored Transform, Clickable, GravityAffected, PhysicsStatic,
fixtures, Pullable, and Structure tag; Dynamic overrides to unanchored/dynamic
physics and adds Anchorable. Girder overrides anchored/static physics and its
fixture density/layer values. Root details are in `Entities/Structures/base_structure.yml`.
These are upstream entity facts only; no local typed physics/fixture components
are claimed or added.

Root/item facts: `Entities/Objects/base_item.yml` composes Item(Small),
Animateable, Clickable, interaction outline, MovedByPressure, collision/land
emitters, DamageOnHighSpeedImpact, Sprite, Pullable, Damageable, Examinable and
dynamic fixed-rotation physics/fixtures. `Entities/Objects/Specific/Stacks/Sheets/metal.yml`,
`other.yml`, `Stacks/other.yml`, and `Stacks/base_stacks.yml` define Steel,
Plastic and MetalRod sheet stacks atop BaseSheetStack (max/default count 30;
single-sheet prototypes count 1). Material values are Steel/Plastic/MetalRod
as declared. These engine components remain Minecraft adaptations, not local
construction schema.

Step source: `Content.Shared/Construction/Steps/ConstructionGraphStep.cs`,
`MaterialConstructionGraphStep.cs`, `ToolConstructionGraphStep.cs`; server
consumers listed above. Base DoAfter is 0, material amount defaults 1, tool fuel
defaults 10. Initial construction accumulates step DoAfter and separates
material consumption; interaction starts timed work only when positive. The
local graph schema retains explicit amount/timing in this slice (omitted timings
must be resolved to upstream zero in future graph data). Entity anchoring is a
graph condition, not an invented physics field. Placement snapping/physics and
SpawnPrototype/GivePrototype entity actions remain deliberately unsupported.
Goal guidance is advisory and does not restrict branch eligibility.

## Concurrent work and preview follow-up

The same pinned checkout was reverified for the owner-reported placement issues.
`ConstructionSystem.Initial.cs` reserves/splits materials into a temporary
container **before** initial work, sums the first edge's step durations, and
spawns its target entity only after completion. Initial DoAfter flags are
NeedHand=false, BreakOnMove=true, BreakOnDamage=true, CancelDuplicate=false and
BlockDuplicate=false. Cancellation returns the reserved materials. Different
initial jobs may use the same source stack; later source count/hand changes must
not invalidate already-reserved placement work.

`ConstructionSystem.Interactions.cs` consumes later material steps on successful
completion. These use NeedHand=true, BreakOnMove=true and BreakOnDamage=false.
`DoAfterArgs.cs`, `SharedDoAfterSystem.cs` and `DoAfterEvent.cs` establish that
default duplicate conditions use **all-match** target/used-entity/event matching:
different construction targets can work concurrently; same-target duplicates
retain cancellation/blocking behavior. Count changes in the same surviving held
stack entity are not themselves cancellation conditions; deletion/drop/hand
replacement can cancel hand-dependent later work.

The nine supported disposal shapes inherit `DisposalPipeBase` in
`Entities/Structures/Piping/Disposal/pipes.yml`. Its composed defaults include
anchored transform, Disposal snap placement, hidden/subfloor sprite, static
noncolliding physics, Anchorable, Rotatable, containers and topology-specific
fixtures/components. Shape subclasses resolve sprite/visualizer and topology
overrides. All nine supported first edges in
`Recipes/Construction/Graphs/utilities/disposal_pipes.yml` consume **2 Steel over
1 second**. Cable/signaller variants are outside this local catalogue and are
not added. The existing local graph already carries these costs/times; the
previous live legacy placement adapter bypassed the duration. Keep typed local
disposal topology/geometry and construction entity block policies rather than
introducing unsupported engine component types.

`ConstructionCatalog.initialStage` is the shared resolved-data entry point for
the first physical node/entity, steps and duration. Previews and live placement
must consume it rather than hardcoding a girder or showing the finished wall.
The pinned client `ConstructionSystem.cs` constructs green/translucent aim ghosts
and clears them on successful construction acknowledgement; this does not
establish a server-authored per-job world marker. Requested pending-work spot
markers are an explicit Minecraft adaptation, carried by existing timed-work
presentation and removed on completion/cancellation—not persistent ghost plans.

The corresponding runtime repairs and acceptance are in progress; this audit
records verified upstream behavior, not a claim that the live fixes have passed
validation.

## Owner placement-priority and guidance follow-up

Pinned client `ConstructionSystem.HandleUse` / `TryStartConstruction` consume
use on a construction ghost and send a distinct structure placement request.
Server initial construction handles that request separately from ordinary
`ConstructionSystem.Interactions.HandleEvent` / `HandleNode` material use on an
existing construction entity. This does not establish a blanket upstream rule
that an active recipe overrides an existing entity interaction.

The owner now explicitly wants active-plan placement to take precedence when
holding that plan's initial material: clicking a girder or player-built wall
must attempt adjacent foundation placement, not advance/deny against the hit
host's graph. Blocked placement must not fall through into progression. Other
materials/tools retain ordinary interactions; exiting construction mode permits
normal steel progression. This is an intentional Minecraft input adaptation.

The owner also requests a goal-name guidance header with only the next actionable
material/tool requirement (anchoring first when needed), replacing verbose
stage/next/path prose. It remains a Minecraft dwell-HUD adaptation. Requirements
come from resolved graph progress; no new upstream-like component flags are
introduced, and remembered goals do not restrict alternate branches.

### Smaller-browser, icon and progress follow-up

The owner subsequently reduced advisory dwell to **0.5 seconds**. This changes
only the local aim advisory, not construction work duration. Compact portable
instructions show input quantities and output count without timing/numbering;
MetalRod remains **1 Steel → 2 Metal Rods**, zero-duration work.
Structure browser requirements now follow the complete graph-start→goal route,
including foundation costs, with one resource/tool line per step. Multiple steps
use `1)`, `2)`; a single step is unnumbered. Node/output headings, “Add”/“Step”
labels and timing are presentation omissions only; resolved costs and durations
remain unchanged. This is owner-requested Minecraft browser formatting.

Pinned sources: `Recipes/Construction/materials.yml` and
`Graphs/materials/metal_rod.yml`; output `PartRodMetal` inherits `PartBase` in
`Entities/Objects/Materials/parts.yml` (sprite `Objects/Materials/parts.rsi`,
state `rods_5`). Its stack UI icon is separately declared in
`Prototypes/Stacks/other.yml` (`rods`). `ConstructionGuide.cs` makes icon and
entry number optional. The local browser reuses existing resolved recipe/entity
block/item mappings and attributed assets, not a fabricated upstream mandatory
icon component. Family representatives are derived from base/output composition.
Disposal recipe thumbnails retain the resolved tube kind/configuration rather
than collapsing all shapes to the generic physical host item. Existing disposal
geometry and material rendering are reused for this Minecraft presentation; no
bespoke models or new sprite imports are introduced.

Timed starts already publish authoritative full-operation snapshots immediately.
World-target markers had replaced the owner's camera-anchored bar entirely;
retaining the existing local first-person bars alongside markers is a Minecraft
visibility correction. It does not change work timing, authority or interruption
rules, nor establish the cause of the reported late connected-client display.
Upstream DoAfter correspondence remains indexed by
`docs/timed-interactions-ingestion-sprint/PARITY.md`.
