# M2b cable visual sync and floor concealment

> **Historical baseline — superseded by M15/M16.** The original M2b limitation that perpendicular edge-turns lacked corner geometry no longer describes the current implementation. Retain this audit as historical evidence; see [M15](m15-multitier-wire-visuals.md) for tier-lane corner projection and [M16](m16-first-spawn-wire-visuals.md) for bounded early snapshot staging/retries. The manual-client-verification caveat remains current.

## Implemented

The chunk-watch visual channel sends server-owned, bounded replacement snapshots of sparse cable host-face records. Snapshot revisions reject stale data; mutation observation is deliberately a narrow `CableStorage` hook and does not transfer storage ownership. A fair per-recipient send queue is capped at 16,384 jobs, with global per-tick ceilings of 64 packets, 8,192 record-work units, and 256 KiB estimated encoded payload (a single bounded snapshot can exceed a remaining budget to guarantee progress). Client retention is capped at 256 chunks and 32,768 aggregate records; presentation is capped at 2,048 nearby records per frame. Empty snapshots replace old data, chunk unloads remove retained state, and login/logout clears revision history. The texture-free tier-colored placeholder computes local neighbors with `PowerTopology` from those synchronized, radius/budget-capped records. Rendered joins now require a visible endpoint as well as a topology edge: a steel-tile-concealed `UP` host cannot emit a branch beneath its cover or receive a join from an exposed cable, while graph/storage records remain connected. The coplanar strip endpoint stops at the neighboring host-tile boundary. Host visibility is checked only for already-loaded client chunks; unloaded endpoints are treated as not visible without loading chunks. Steel finish on `StationFloorBlock` masks only the `UP` cable face; all other faces remain visible.

## Asset ledger (future imports; none imported in M2b)

SS14 art use is approved, but this implementation intentionally ships no notable artwork and uses procedural geometry only. When binary sprites can be imported, provide one 16x16 RGBA PNG per file:

| Future file | Intended use |
|---|---|
| `assets/moonstation14/textures/block/cable_hv.png` | High-voltage red wire strip, transparent background |
| `assets/moonstation14/textures/block/cable_mv.png` | Medium-voltage yellow wire strip, transparent background |
| `assets/moonstation14/textures/block/cable_apc.png` | APC cyan wire strip, transparent background |

These files are not currently present or referenced by block models. No SS14 binary sprite was copied or generated.

## Limitations / verification

The renderer remains an untextured placeholder, not an SS14 sprite match. `CableVisualAdjacency` exposes a deterministic 16-bit neighborhood mask derived from true `PowerTopology` contacts, including perpendicular face contacts; same-face contacts get branch geometry only when both endpoints are visible. Perpendicular edge-turn mask contacts are recognized but do not yet have dedicated miter/corner geometry, so a continuous wrapped corner is still a visual limitation. There are no links through solid gaps: only actual topology neighbors in the synchronized local set connect. Neighbor gathering is limited to the already radius-filtered and capped visible-record collection; it does not scan the world per cable. The server sends complete bounded chunk snapshots on mutation rather than record-level deltas; this avoids client/server divergence on removals and limits complexity while remaining chunk-watch scoped. A stale/out-of-order snapshot is ignored; the next chunk-watch or mutation replacement snapshot restores current state. The byte budget is conservative estimated encoded size, not a transport-level byte meter. No manual client visual test was performed.
