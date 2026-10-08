# Airlock power GameTest chunk coverage

The airlock power fixtures force and synchronously load the complete rectangle containing both the resolved receiver's `localReachBlocks()` footprint and the connected fixture cable network, with a one-chunk margin. `LoadedPowerGraph.nodesNear` deliberately returns unknown if any chunk intersecting the receiver footprint is absent from its loaded snapshot; this fixture coverage prevents randomized GameTest origins from putting that conservative boundary across an unloaded chunk. Production graph lookups remain loaded-only and fail closed.

Each fixture tracks only chunks it newly forced (pre-existing forced chunks are preserved), and releases those chunks from its batch `@AfterBatch` hook, including the prototype-override batch.

Physical coverage alone is not index coverage. A diagnostic found three forced,
FULL-cache footprint chunks missing from the graph's loaded-space set, causing
`nodesNear` to return null with clean topology and no meter. Receiver queries
now queue those already-loaded missing chunks through the ordinary bounded
snapshot/rebuild path. They remain unknown until indexing completes; no
production chunk is force-loaded and no meter is fabricated. Both airlock and
cached APC-extension lookup paths share this reconciliation.

Contact debounce includes fresh full-power readiness as well as root identity
and revision, so an actor still pressing into a door can retry after indexing
recovers without requiring an unrelated controller-state change.
