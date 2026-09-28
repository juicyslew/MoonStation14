# M11 wire geometry audit

## Change

The client placeholder renderer now draws a centered face-local square cap: both tangent coordinates span `-0.075` to `+0.075` for every one of the six faces, with no preferred compass direction or unconditional compass/wall stubs. The cap and spokes use the same `.502` face-normal offset, retaining the small separation from the host face to avoid z-fighting. Spokes remain backed by a compatible edge in `PowerTopology`. Coplanar spokes terminate at the full shared half-block boundary; perpendicular `EDGE_TURN` spokes terminate at the shared cube edge. Each visible cable remains gated by the loaded, eligible host and its tile finish; a hidden neighbor is not drawn merely to show a connection, while the visible branch reaches its tile boundary.

Topology lookup is independent of the visible-record list and its render budget. The bounded client snapshot cache maintains a node index across synchronized chunks, including chunk seams, and removes indexed entries when snapshots are replaced, unloaded, emptied, or evicted. Missing snapshots do not produce inferred connections. Tier and face remain part of the lookup identity, and this is presentation-only: electrical graph behavior is unchanged. This rendering rule does not establish that hidden cable details are absent from network payloads; packet privacy remains open.

## Validation focus

`CableVisualSyncTest` covers square-cap tangent bounds on all six faces, cardinal floor contacts, vertical wall chains, coplanar boundary and edge-turn endpoint geometry, cross-chunk and budget-independent topology, tier isolation, unload, and authoritative empty snapshots. The coordinator's broader `compileJava compileGameTestJava test` command passed, as did the 157-test server GameTest run recorded in the current handoff. No manual client pixel/depth verification is included in this audit.
