# Future gas and disposal piping — scope and instructions

**Status: clearly slated future work; approved conceptual direction only. This is not authorization to implement.** No gas-pipe or disposal-tube system is claimed to exist in MoonStation14. Do not begin code, assets, active atmospherics/player work, or edits to shared roadmap/handoff documents under this proposal.

## Scope when separately authorized

This future system is exposed, real 3D infrastructure: gas pipes and larger disposal tubes can run freely on walls, ceilings, maintenance areas, and exterior surfaces. They are not default 2D wires. Do not impose a mandatory subfloor plenum, tunnel, access-panel, or tile-only installation scheme. Maintenance floors remain optional and station-design dependent.

Concealed wall crossings are a narrow, explicitly manual exception for a sealed straight segment, not a general hidden network or a substitute for exposed runs. The intended installer is any player holding the corresponding gas-pipe or disposal-pipe **segment item**. Sneak-use on a reachable, solid, atmos-sealing wall block installs exactly one straight segment in exactly the clicked block. There is no additional pipe/drill installation tool, automatic traversal, suggested “continue through wall” behavior, remote placement, or placement beyond the adjacent clicked cell. Gas and disposal have distinct compatible port and capacity rules.

## Non-negotiable constraints

- A newly installed isolated segment has a capped end while incomplete. A sealed pass-through joining pipe ports is a network edge, never an open-air exchange.
- The host remains the same full-collision, atmosphere-sealing wall block. The single sneak-use pipe installation action on the clicked host never creates intermediate AIR, removes the host, or opens room gas, including during a retrofit. This constraint applies to the pipe action, not to separate wall demolition performed by the player. Ordinary suitable station, vanilla, or modded full-solid hosts may be eligible without a special `pipeage` property, subject to protection and verified platform APIs. Fail closed when safe host support cannot be established.
- For a thick multi-block wall, the player physically removes earlier wall blocks to expose deeper positions, then rebuilds and installs/fills the wall-block segments from farthest to nearest. Separately removing wall blocks may expose AIR and could breach vacuum if the player removes the final seal. Do not recommend instant remote dismantling of the whole wall. The removal method/input or tool, side/access, cost, and granularity are open for future owner decision; staged removal (for example, removing blocks individually) is only a candidate, not a settled interaction. Wall removal is a separate destructive gameplay risk; only actual removal of the last seal can breach a room. Pipe operations themselves never breach it.
- Intentional vent/scrubber room output is a distinct device/item on the room-facing side. It transfers pipe gas to one room using the atmos owner’s API; it creates no implicit open atmospheric route.
- Sealed wall crossing needs only a straight axis; elbows belong on exposed runs. A one-sided install modifies only the clicked block. Never force-load a far-side chunk or place on the far side remotely.
- Preserve full collision and the current `AtmosphereTopology` passability check. Model network-to-network links separately from room gas. Define sparse per-host persistence, host replacement invalidation, and unloaded frontiers before implementation.
- Server-authorized validation, atomicity, save/sync, bounded work and performance for 20 players are prerequisites. Concealed visual data and future T-ray scanning are a separate privacy/ownership dependency; do not implement a scanner here.
- Do not assume disposal has gas-mixture semantics or the same one-block capacity: its larger radius/lane occupancy and transport (potentially entities) need independent design and ownership.

## Work discipline

1. Treat [`architecture.md`](architecture.md) as the approved conceptual contract, not a platform/API specification.
2. Resolve every open decision in [`references-and-open-decisions.md`](references-and-open-decisions.md), secure explicit owner authorization, and complete the gates in [`milestones.md`](milestones.md) before implementation.
3. At M0, inspect the exact upstream revision and this project’s exact NeoForge/Minecraft APIs. Confirm with the atmos owner before interacting with `AtmosphereTopology` or room transfer. Do not copy SS14 implementation or media; review license and provenance first.
4. Keep gas and disposal ownership and compatibility explicit. Do not introduce active atmos/player edits, scanner implementation, generic item-pipe equivalence, speculative rendering, or unrelated documentation changes.
5. Test protection, unloaded boundaries, multiplayer authority, safe save/load and the no-vacuum/no-air-exchange invariants before presenting anything as usable.

## Acceptance language

Until separately authorized and accepted, refer to this as a **future proposal**. Never report that pipes, disposal tubes, pass-throughs, vents, or scanner support are implemented. A milestone may be independently rejected or deferred without implying later milestones are approved.
