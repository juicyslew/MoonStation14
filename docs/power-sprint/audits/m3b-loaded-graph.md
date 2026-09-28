# M3b: loaded-world cable topology cache

`PowerGraphService` owns one topology cache per live `ServerLevel`. It only considers
records read from already-loaded `FULL` chunks (`getChunk(..., false)`); it never
requests a chunk. Chunk load/unload and the `CableStorage` mutation boundary notify
the service. Repeated edits coalesce in a set of dirty chunk positions. Chunk reads
are limited to four per level per server tick, and topology expansion to 256 cable
nodes per level per tick. `CableChunkData` already caps each chunk at 4096 records.
Before a persisted record becomes a graph node, refresh checks its saved host block
registry ID against the currently loaded block and rechecks that the recorded face is
still fully eligible. Invalid records are pruned through `CableStorage` and mark the
chunk dirty; this queues a later bounded refresh rather than restarting refresh inline.
This identity check detects a host replaced by a different block type, but cannot
detect replacement by another instance of the same block type (or a same-type remove
and re-place between observations): the persisted registry ID contains no instance or
placement generation. Such same-type replacement remains a known limitation.

Entity-place, break, and neighbor-notify events are registered as chunk-coalesced
mutation hints only when the changed position is a cable host with an attachment record.
The event hook looks up the existing attachment in an already-loaded `FULL` chunk; it
does not create attachments, force-load chunks, or queue unrelated edits. This includes
ordinary neighbor notifications and property-only edits such as station-floor tile
finish changes when no cable node changes. Mutation hints make service answers
`UNKNOWN` while their chunk is pending, but do not discard the cached graph. A bounded
refresh compares validated cable nodes to the indexed snapshot and invalidates topology
only if those nodes actually differ. Indexed chunks that still contain cable nodes are
also lazily revalidated every server tick, with at most four full chunks refreshed per
level per tick and at most 4096 cable records in each chunk snapshot. Refresh validates
saved host registry ID and current face eligibility before retaining a node, pruning
invalid records. Thus direct `setBlock(..., 3)` host replacement is eventually observed
even when it emits no neighbor notification. There is no loaded-world block scan.

An actual cable-node change or chunk load/unload invalidates answers immediately. Queries
then return `UNKNOWN` until cached loaded chunk snapshots have been refreshed and all
known nodes have been traversed. Pending mutation snapshot refreshes also keep service
queries `UNKNOWN`, but a benign refresh with identical nodes preserves the completed
graph and component IDs. Device/source port resolution is read from current device
ports on each solve, so facing changes do not invalidate unrelated cable topology.
Each completed component records its loaded chunks and its geometrically possible
frontier into unloaded chunks; such a frontier keeps that component `UNKNOWN` until
the missing chunk loads. A loaded neighboring chunk with no compatible cable closes
that frontier and does not make the component unknown. Unloaded chunks are removed
from the graph, so a component cannot remain known across an unavailable seam. Tier
is part of every node and `PowerTopology` edge predicate, so distinct voltage tiers
never merge. Neighbor enumeration uses stable topology ordering and bounded-degree
local candidates; numeric component IDs are cache-local and may be reassigned on
rebuild, while membership is deterministic. The BFS tracks queued nodes in a hash
set, avoiding linear frontier membership checks. No cable
block entity or per-cable tick is involved.

This cache represents connectivity only, not a voltage source or an energized
simulation. Consumers must treat `UNKNOWN` as unavailable and must not infer power
from a known component alone. Source/device indexing and live port reads remain separate
from cable topology; removing a source is reflected in the next solve and yields zero
downstream lamp output when no source remains.
