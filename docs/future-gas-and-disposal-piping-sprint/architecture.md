# Future gas and disposal piping architecture

**Status: approved conceptual direction for future work only. No implementation is authorized or implied.** This document describes desired behavior and safety boundaries. It does not verify a Minecraft/NeoForge API or assert that MoonStation14 currently has gas piping, disposal tubes, or wall pass-throughs.

## World model and presentation

Gas pipes and disposal tubes are exposed, genuinely three-dimensional infrastructure, constructible as freeform runs on walls and ceilings as well as in maintenance spaces and outdoors. They are not a default 2D wire abstraction. No mandatory subfloor plenum, tunnel, access panel, tile-only scheme, or station-wide maintenance floor is assumed. Maintenance floors are optional station design.

Exposed runs may turn with elbows and include vertical runs and multi-floor risers. Gas and disposal are distinct systems, with separate port compatibility, capacity, occupancy, and transport semantics. A disposal tube may occupy a larger radius/lane and transport entities; it must not be forced into gas mixture rules, a one-block capacity, or generic item-pipe equality. Define actual geometry and overlap rules before implementation.

## Manual sealed wall crossing

An eligible player holding the corresponding gas-pipe or disposal-pipe **segment item** sneak-uses a reachable, solid, atmos-sealing wall block. The operation places one straight sealed segment in only the clicked block. The item itself is the installer; no extra pipe tool, drill, automatic traversal, “continue through wall” suggestion, or remote placement past the adjacent cell exists. The requested action and server validation must identify the clicked host and exact single segment. Gas and disposal segment items accept only their own compatible ports/capacity rules.

An incomplete segment has a capped end. A completed sealed pass-through is an explicit network edge connecting compatible pipe ports, not a hole into room air. It need only cross on a straight axis; use exposed elbows for direction changes. A one-sided action installs only the clicked host-block segment and never searches, loads, or places in a far-side block/chunk.

The host retains its exact full-collision, atmosphere-sealing block state. The single sneak-use pipe installation action on the clicked host never creates intermediate AIR, removes the host, opens room gas, causes a temporary breach, substitutes a host, uses a tile trick, or changes passability, including when retrofitting existing walls. This restriction is on the pipe action, not on separate demolition a player performs to expose a deeper wall block. Suitable ordinary station, vanilla, or modded full-solid hosts may be eligible without requiring a special `pipeage` property, but only once the exact API proves a safe eligibility test. Unknown or unsafe host behavior fails closed. Existing protection rules and server permissions apply.

For thick walls, the player physically removes earlier wall blocks to expose deeper positions, then rebuilds and installs/fills the wall-block segments from farthest to nearest. This separate, player-performed demolition may expose AIR and could breach vacuum if the player removes the final seal. Do not recommend instant remote dismantling of the whole wall. The removal method/input or tool, side/access, cost, and granularity remain open for future owner decision; staged removal (for example, removing blocks individually) is only a candidate, not a settled interaction. Wall destruction is a distinct gameplay risk from pipe operations: a room can be breached only if the player actually removes the final seal. A pipe action never destroys the host or opens the room.

## Room gas versus pipe network

Network edges and room atmosphere are separate domains. A sealed wall edge transports only through its compatible pipe network ports and never makes the host passable to `AtmosphereTopology`. An intentional gas output is a distinct vent/scrubber device/item placed on the room-facing side. It transfers pipe gas into one room only through the atmos owner’s approved API and validated transaction; no implicit atmospheric route or open connection is created by a pipe crossing. Device inputs, rates, controls, directionality, and capacities are not decided here.

Preserve full collision and the current atmosphere passability check. Any future integration must preserve the existing `AtmosphereTopology` passability behavior and be reviewed with the atmos owner; pipe state cannot cause a solid wall to become a gap or a false room merge. Gas mixture transfer is not disposal entity transport.

## Ownership, persistence, and lifecycle

Before coding, establish server-owned authoritative segment/port records and their exact holder/lifecycle. Persistence should be sparse and per host/block (or another verified owner-approved representation), not a per-segment ticking entity by default. Host replacement must invalidate/reconcile old segments deterministically; a replacement must not inherit a connection merely because it occupies the same coordinates. Save/load and synchronization must preserve explicit segment identity, type, orientation, cap/port state, and compatibility without exposing concealed data to unauthorized clients.

Network discovery and edits must be bounded and mutation-driven. Unloaded frontiers are unknown/incomplete; never force-load a chunk to place, connect, traverse, render, or remove a run. A completed edge is valid only after both endpoints and the necessary host records have been verified. Server validation/atomicity must reject stale targets, incompatible type/ports, protected blocks, invalid reach, or unsupported hosts without partial writes. Define recovery for save interruption, host replacement, chunk unload/reload, and failed synchronization.

For concealed infrastructure, visual synchronization is a privacy boundary decision, not just renderer occlusion. Determine who may receive segment positions, how ordinary snapshots are filtered, and the future T-ray scanner owner/authorization contract before exposing concealed pipe data. The deferred scanner proposal does not authorize scanner work as part of this system.

## Scale and operational constraints

Set numeric bounds for run/network size, searches, pending work, sync payloads, visible segments, and edit cadence before implementation. Budget for 20 players, dense station infrastructure, rapid edits, overlapping networks, and load/unload churn. Rendering, graph resolution, persistence, and network traffic must be bounded and recoverable; no whole-world scans or per-pipe-per-tick work. Gas and disposal can use separate indexes and update budgets.

## SS14 reference boundary

These targeted **SS14 reference paths** were verified to exist in the provided `C:\Users\William\Documents\SS14-dev\space-station-14` checkout. Their content/API and the checkout's pinned upstream revision have not yet been audited for implementation. They are not code to copy or proof of behavior in this Minecraft project:

- `Resources/Prototypes/Entities/Structures/Piping/Atmospherics/pipes.yml`
- `Resources/Prototypes/Entities/Structures/Piping/Atmospherics/unary.yml`
- `Resources/Prototypes/Entities/Structures/Piping/Disposal/pipes.yml`
- `Resources/Prototypes/Entities/Structures/Piping/Disposal/units.yml`
- `Content.Server/Atmos/Piping/Unary/EntitySystems/GasVentPumpSystem.cs`
- `Content.Server/Atmos/Piping/EntitySystems/GasPipeManifoldSystem.cs`
- `Content.Server/Disposal/Tube/DisposalTubeSystem.cs`
- `Resources/Prototypes/Recipes/Construction/Graphs/utilities/atmos_pipes.yml`
- `Resources/Prototypes/Recipes/Construction/Graphs/utilities/disposal_pipes.yml`

At future M0, pin the actual upstream repository revision and audit the exact relevant implementations, prototypes, construction graphs, and APIs for suitability; confirm license/provenance before any use. Do not port SS14 architecture literally, copy code, or import media. Record license and attribution for any material actually used; prefer project-owned assets.

## Invariants for review

1. The single sneak-use pipe install action changes only the clicked block and never creates air, removes the host, opens room gas, changes collision, weakens sealing, force-loads chunks, or reaches remotely. Separate player-performed demolition to expose a deeper position may expose AIR; removing the final seal can breach vacuum.
2. A capped segment is incomplete; a sealed compatible pass-through is a pipe-network edge, never a room-air opening.
3. Gas network flow, explicit room transfer, and disposal transport are separate ownership/semantics.
4. Unsupported hosts and unresolved frontiers fail closed; replacement hosts do not silently inherit old connections.
5. Walls can be opened only by separate actual block destruction, not by a pipe operation.
6. All player edits and device transfers are server-authorized, atomic, bounded, and protection-aware.
7. No system is described as implemented until owner acceptance and tested platform behavior establish it.
