# Power Distribution Architecture Notes

This document records approved topology/visual boundaries for the power sprint and separates them from platform questions that remain unverified. It is a design aid, not a claim that a specific NeoForge callback, renderer, or storage API has been validated.

## Approved model: face-mounted cable, independent floor finish

### Host and cable identity

Treat a cable as a typed segment attached to a host block face, not as a replacement block. Identify a segment by dimension, host `BlockPos`, `Direction` face, and voltage class. The current schema-v2 chunk records permit HV, MV, and APC segments to coexist independently on the same face; the complete host-face-tier key must be unique. Schema-v1 records migrate on read to the v2 representation. This does not make a solid block's volume an electrical conduit.

Cable appears on the face plane and turns only over an actual shared edge where compatible segments meet. A drawn bend is not itself a network edge: topology is determined by declared endpoint/face adjacency, not by pixels, same coordinates, a nearby cable texture, or visual overlap. Define a truth table for face-pair connections before implementation. Do not infer a connection diagonally, across an unoccupied segment, through a host's solid collision volume, or merely because two devices occupy neighboring positions.

### Station substrate and cosmetic tile

An eligible host is any sturdy full-collision block face, including eligible wall and ceiling faces; it is not limited to the dedicated station floor and vanilla stone is not prohibited by default. The cosmetic tile is a separate placeable item which applies a visible finish to the station-floor substrate; it does not replace the substrate and must not become the cable's host. M1's bounded exception to the general “do not put cable/tile occupancy into host blockstates” rule is a single typed station-floor block with a tiny `tile_finish=none|steel|white` enum property. That property is part of the floor's own persistent, vanilla-synchronized BlockState and presentation—not a second tile block or a generic property added to arbitrary Minecraft blocks. Cables remain separately owned sparse chunk attachment records keyed by host face, with the host block identity observed at placement retained to reject stale records after replacement.

Visibility truth table:

| Cable attachment | Floor tile | Presentation |
|---|---|---|
| Eligible substrate, top face | absent | Exposed cable |
| Eligible substrate, top face | installed | Cable hidden by tile; attachment retained |
| Eligible substrate, top face | removed again | The same attachment is exposed again |
| Any wall face | either | Exposed cable |
| Any ceiling face | either | Exposed cable |

The tile is normally cosmetic for power: installation/removal does not connect, disconnect, relocate, or rewrite cable records. The visible finish is the floor block's enum property; future top-face cable rendering reads it and suppresses a top cable when the finish is not `none`, without deleting that cable. Wall/ceiling cables are not hidden by a floor finish. The same registered substrate block remains at the same position on install and pry. Host removal/replacement is a separate lifecycle transition that can remove/invalidate attached cable and thereby affect graph topology; do not conflate it with tile removal.

### Device adjacency and risers

Devices connect by the approved adjacency/contact rule between device connection faces and cable faces. No rendered plug is necessary or permitted as a topology prerequisite. Device orientation and supported tier must still be validated server-side. Keep rendering independent from network membership so a missing optional asset cannot break power.

Floor-to-floor continuity is never automatic. A riser is an explicit assembly of cable-face segments across actual host faces / intervening positions according to the final topology truth table. A riser test must fail if any required segment is absent. No arbitrary long-range vertical search, same-X/Z shortcut, or connection through intervening solids.

## Network identity and evaluation

HV, MV, and APC/low-voltage are distinct typed network classes. Matching class is required for ordinary connectivity; the resolver must not combine, average, or silently convert across classes. The approved bounded bridges are an explicit HV→MV substation and an MV-fed, battery-backed APC connecting its MV input to its separately modeled APC output/network. Treat each as a device with separately owned, typed ports and explicit transfer/storage behavior—not as cable-tier merging or an unrestricted converter. No other cross-tier conversion is included without separate approval.

Use a server-owned authoritative graph derived from cable face records and device ports. The logical graph can be reconstructed from placed world state; any cache/index is an acceleration layer and must be invalidatable/rebuildable. The representation must not require every cable to be an active ticking BlockEntity. Prefer mutation-driven dirty marking and bounded recomputation/work continuation over scans or ticks proportional to every cable every tick. A large component that exceeds one work budget remains pending/unknown, not partially certified as complete.

When a traversal reaches an unloaded chunk boundary, do not force-load it. Model that frontier as unknown/incomplete until that chunk is ordinarily loaded and bounded continuation can inspect it. Do not let stale cached membership report connected state through an unverified frontier. Persistence should save actual cable/tile/device state with clear ownership; derived graph data should be reconstructible or versioned with explicit invalidation.

The bounded system is intended to cover a usable station loop: generation/source, MV/HV distribution through the explicit substation bridge, APC battery storage and APC-tier consumers. Before implementation, record a minimal unit/capacity convention, source limits, substation transfer behavior, APC charge/discharge and reserve rules, load priority/fairness, update cadence, and what an unknown frontier means for allocation. Include minimal accessible device placement/configuration/removal interactions with server-authoritative validation. Full fuel handling and elaborate generator simulation remain optional after the platform audit. Server is the only authority for supply, storage, and energized state. Client state is a synchronized projection, never a submitted claim.

## Existing MoonStation14 seams

`MS14Provider` supports reads/updates of `ItemStack` components and Entity/BlockEntity attachments through `SystemLink`; it also supplies detached reads and change-only updates. Where cable state legitimately resides on a holder covered by this pattern, use that established seam and preserve absent-read non-materialization and synchronization discipline. Inspect actual attachment definitions/codec behavior before choosing a holder.

An item attachment describes an item. It is not a world-position or chunk graph store and must not be used as an alternate name for cable attachment. World topology is identified by dimension/position/face and must follow Minecraft world/chunk save, load, update, and client synchronization ownership after M0 proves the right local API. Do not bolt a second generic provider onto world state without evidence that existing holder patterns apply. M1's floor finish is deliberately a narrow exception: ordinary BlockState on the dedicated floor block is authoritative for the bounded finish enum, so there is no separate tile attachment bit, custom tile packet, or custom tile renderer.

Existing `ModBlocks` and `ModBlockEntities` use deferred registrations for blocks and specific device BEs. Follow their registration style for the substrate/tile/device where appropriate; do not register a BlockEntity per wire simply because devices currently have BEs. Atmosphere's sparse chunk ownership/search is a separate subsystem, not a power graph API. Player-character/UI efforts are also separate ownership domains.

## Reference boundary and assets

- **SS14:** the repository's targeted reference areas are [`Content.Shared/Power`](https://github.com/space-wizards/space-station-14/tree/master/Content.Shared/Power), [`Content.Server/Power`](https://github.com/space-wizards/space-station-14/tree/master/Content.Server/Power), and [`Resources/Prototypes/Entities/Structures/Power`](https://github.com/space-wizards/space-station-14/tree/master/Resources/Prototypes/Entities/Structures/Power). Do not use the previously cited Piping texture path as a cable asset source. The reviewed cable asset directories are `Resources/Textures/Structures/Power/Cables/lv_cable.rsi`, `mv_cable.rsi`, and `hv_cable.rsi`. Local `lv` and `mv` assets are CC-BY-SA-3.0, tgstation commit `fcf375d7d9ce6ceed5c7face899725e5655ab640`; `hv` is CC-BY-SA-4.0, PJB3005. The images have not been imported. Record exact copied files/frames, attribution, modifications, and notices in the required actual-import ledger before import. The reference is not a literal ECS port, a Minecraft API specification, or blanket asset permission.
- **ProjectRed:** the 1.21.1 ProjectRed project is an architectural reference for multipart electrical construction/topology only; its license is MIT. It does not mandate its implementation, API, multipart dependency, power simulation, or binary/runtime dependency. Verify the exact referenced version/commit and license at implementation time.
- **Per-file asset ledger (required before importing art):** create one entry for each actual imported image/file (including RSI metadata where applicable): source repository and pinned revision, exact source path/direct URL, filename/frame, creator/attribution, license, modifications, required notices/credit, and review status. Current reviewed directories/licenses above do not mean images have been imported; that actual-file ledger remains due. If provenance/license is unclear, use a project-owned placeholder or omit the asset. The code's MIT license does not alter third-party asset terms.

## Verified decisions vs. unverified platform API

### Current implementation update (M14–M16)

The former one-tier-per-face storage assumption and the M2b limitation that edge turns had no corner geometry are historical, not current constraints. M14 adds three independent tiers per face with v1 read migration; M15 draws tier-offset lanes through same-face/coplanar quads in either direction and projects consistent lanes around perpendicular corners. When a top-face cable is hidden by a floor tile, all three tiers are hidden at that host face; visible neighbor stubs still terminate toward the covered tile. M16 stages bounded pre-world snapshots and retries resync at 30, 60, 120, then at most 200 ticks. Arrival remains asynchronous and can briefly lag under heavy load. The same-dimension packet arriving in the logout/login gap remains indistinguishable from a new-session packet because the protocol has no session ID.

### Decisions already settled for design

Face-occupying non-replacement cable; normally tile-hidden top-floor wire that persists and reappears on tile removal (tile changes may invalidate visuals only; host removal can affect topology); exposed walls/ceilings; adjacency-based device connection without plug rendering; genuine-edge turns; explicit risers; no through-solid or accidental floor connections; tier isolation (HV/MV/APC) except explicit HV→MV substation and MV→APC battery-backed APC bridges; server-authoritative accessible device interactions; no per-cable ticking BE; no forced chunk loads; UI optional only after core and local API audit unless separately approved; ProjectRed as optional architecture reference only; SS14 asset use subject to per-file actual-import ledger.

### Not established by this document

The actual 1.21.1/NeoForge 21.1.224 API to persist face cable data on a host, observe topology/neighbor/chunk changes, synchronize cable state, render arbitrary connected faces/edge turns, register menu APIs, or execute GameTests. M1 establishes one registered floor block's bounded finish property and item interactions; it does not establish cable attachment or cable rendering behavior. Also unverified: practical maximum graph size, transfer/storage policy, power units/cadence, loaded/unloaded behavior of individual relevant APIs, implementation details for SS14 asset attribution/import, and whether existing local types suffice. The M0 milestone must verify each required call against local mapped source/docs and a focused test before code assumes it.

UI is optional and gated: only after the core yields a stable server-owned power state API may a separate local audit verify the pinned menu/session and client classloading contract. Do not treat UI as an M0–M5 prerequisite; owner approval is required for UI scope. Any display must be read-only/presentation unless separately designed; never let a client report authoritative network power.

## Review invariants

1. Replacing/removing a host and placing/removing the cosmetic tile are distinct transitions; a replacement host must never inherit or energize the previous host's segments.
2. Cable face data is authoritative and survives tile conceal/reveal unchanged.
3. Network membership follows explicit edges in the resolved topology, not texture adjacency.
4. Solid blocks and unloaded chunks are never searched through or force-loaded to complete power.
5. Voltage tiers cannot merge except at the approved, explicitly modeled HV→MV substation and MV→APC battery-backed APC ports; no other conversion is implied.
6. Every unbounded or incremental operation has a work cap, continuation policy, and observable pending/unknown state.
7. Any platform API claim is accompanied by exact-version source evidence/test; each actually imported asset is accounted for in the per-file ledger.
