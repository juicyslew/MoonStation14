# References and open decisions

**Status: future proposal; not authorized for implementation.** The paths below were verified to exist in the provided `C:\Users\William\Documents\SS14-dev\space-station-14` checkout. Their content/API and the checkout's pinned upstream revision have not yet been audited for implementation. Do not copy implementation or media.

## Targeted SS14 paths

| Area | Reference path |
|---|---|
| Atmos pipe prototypes | `Resources/Prototypes/Entities/Structures/Piping/Atmospherics/pipes.yml` |
| Unary atmos prototypes | `Resources/Prototypes/Entities/Structures/Piping/Atmospherics/unary.yml` |
| Disposal pipe prototypes | `Resources/Prototypes/Entities/Structures/Piping/Disposal/pipes.yml` |
| Disposal units | `Resources/Prototypes/Entities/Structures/Piping/Disposal/units.yml` |
| Unary gas vent behavior | `Content.Server/Atmos/Piping/Unary/EntitySystems/GasVentPumpSystem.cs` |
| Gas manifold behavior | `Content.Server/Atmos/Piping/EntitySystems/GasPipeManifoldSystem.cs` |
| Disposal tube behavior | `Content.Server/Disposal/Tube/DisposalTubeSystem.cs` |
| Atmos construction graph | `Resources/Prototypes/Recipes/Construction/Graphs/utilities/atmos_pipes.yml` |
| Disposal construction graph | `Resources/Prototypes/Recipes/Construction/Graphs/utilities/disposal_pipes.yml` |

Review for concepts and provenance only. SS14 is not a Minecraft API specification and its implementation is not a literal port. Confirm license and attribution obligations for any actual use of code or media; do not copy assets or code under this proposal.

## Settled conceptual decisions

- This is clearly slated future work and approved conceptual direction only, not authority to implement or a claim that systems exist.
- Pipes/tubes are exposed real 3D runs on walls, ceilings, maintenance areas, and exterior surfaces; no default 2D wire treatment or mandatory subfloor/plenum/tunnel/access-panel/tile-only scheme. Maintenance floors are optional.
- Any player holding the corresponding segment item sneak-uses a reachable solid atmos-sealing wall block to install one straight sealed segment in only the clicked block. No additional installation tool, automatic traversal, suggested continuation, remote placement, or far-side placement.
- Gas and disposal ports/capacities are distinct. Disposal may have larger radius/lane occupancy and entity transport; it is not constrained to gas’s one-block capacity or generic item-pipe equality.
- Incomplete segments are capped; a sealed pass-through is a network edge, never an open-air route. Host remains unchanged full-collision/sealing. No intermediate AIR even on retrofit.
- Ordinary suitable station/vanilla/modded full-solid host support does not require an invented `pipeage` property, but depends on verified API and protection; fail closed if safe support is uncertain.
- Thick walls require physically removing earlier wall blocks to expose deeper positions, then rebuilding and installing/filling their segments from farthest to nearest. Do not recommend instant remote dismantling of the whole wall. Removal method/input or tool, side/access, cost, and granularity remain open; staged removal (such as one block/segment per action) is only a candidate, not a settled interaction. Wall destruction is separate; only actually removing the last seal can breach the room.
- Intentional gas room transfer requires a distinct room-facing vent/scrubber and atmos-owner API to one room, never an implicit open path. Straight-axis concealed crossing only; elbows on exposed runs; no far-side forced loads.
- Preserve full collision and the existing `AtmosphereTopology` passability check. Network gas and room gas are separate. Sparse per-host persistence, host replacement, unloaded frontiers, atomic authoritative edits, save/sync, bounded scale for 20 players, and privacy need verified contracts.
- Future T-ray scanner work is a separate dependency/ownership question, not part of pipe implementation.
- **No extra installation tool is needed or authorized by the settled installation decision; the same corresponding pipe segment item is used to install.** Removal tool/input or interaction, side/access, and cost are not decided. Do not infer a sneak-use action that removes an entire run or invent a pry/drill tool.

## Open decisions — resolve before implementation

1. **Supported full seals:** What exact server-verifiable property/API defines a suitable full-collision atmos seal across station, vanilla, and modded blocks? Which hosts must be rejected if airtightness cannot be proven? How will this preserve the actual `AtmosphereTopology` check?
2. **Gas/disposal width and overlap:** What dimensions, lane occupancy, mounting, clearance, and overlap rules apply, especially where larger disposal geometry shares a block/face with gas or other infrastructure?
3. **Removal method/input and cost:** What interaction or tool, if any, is acceptable for removal, and what cost or granularity should it have? Staged removal is a candidate, not a settled one-segment/action rule. No instant remote whole-wall dismantling is recommended, and no removal tool/input is decided by the installation decision, which uses the corresponding pipe segment item without an extra installation tool.
4. **Access/view side:** From which side can a player expose, inspect, install, and remove segments? How does the chosen side interact with reach, line of sight, block occlusion, multiplayer, and thick-wall farthest-to-nearest construction?
5. **Protection versus material:** Are protected walls always immune to pipe actions, or can material/type rules additionally disallow otherwise permitted blocks? Permission must be explicit and server-enforced.
6. **Host replacement:** How should records behave when the same block type is removed/replaced in place, when a different type replaces it, or when a chunk reloads? Define identity/version checks, invalidation, and recovery so stale pipes never silently reconnect.
7. **Limits:** What are safe dimensions, port counts, capacities, run/network sizes, edit rates, transfer rates, disposal lane limits, and persistence/sync budgets?
8. **Disposal contents:** Which entity/content types may travel, how are insertion/extraction, blockage, direction, ownership, damage, and recovery handled? This is distinct from gas mixture transfer.
9. **Vent controls:** What are vent/scrubber transfer direction, rates, capacity, UI/configuration, power/control requirements, and failure modes? Confirm with atmos owner; no device behavior is implied yet.
10. **Visual privacy and future T-ray:** Who owns normal concealed-pipe synchronization and observer authorization? What data may ordinary clients receive? Who owns a future scanner, and what filtering/query contract is needed? Resolve privacy before distributing hidden pipe coordinates.
11. **Persistence/atomicity/API:** Which exact local APIs own per-host sparse state, dirty/save lifecycle, client synchronization, atomic multi-record edits, transaction safety, and loaded-only topology frontiers?
12. **Owner boundaries:** Who owns atmos room transfer, disposal transport, construction interaction, rendering, protection, and acceptance? Secure explicit consent before each subsystem crosses an ownership boundary.

## Evidence required at M0

Record the upstream repository/revision and relevant exact source paths, exact local Minecraft/NeoForge API source evidence, atmos-owner confirmation for room topology/transfer, host eligibility test evidence, protection contract, save/sync/lifecycle behavior, privacy decision, resolved geometry/limits, and license/provenance. If any safety-relevant behavior remains unverified, fail closed and defer rather than treating this conceptual document as proof.
