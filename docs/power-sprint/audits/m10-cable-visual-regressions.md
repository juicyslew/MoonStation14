# M10 — cable visual regression fixes and owner verification

## Reported regressions and implementation status

The reported issues were floor cables staying hidden after prying a tile, wall cables appearing only after a power-network update, an APC/LV cable color that looked light blue, and occasional cables showing through walls. The following targeted fixes are present. They concern presentation and visual synchronization; they do not change cable records or graph topology.

- **Floor tile reveal:** after a successful `setBlock` finish transition, `StationFloorTileItem.useOn` and `StationFloorTilePryItem.useOn` call `CableVisualServerHooks.noteChanged` for the affected chunk. The hook queues a visual snapshot for current watchers. Tile state changes do not mutate cable storage or the power graph. The floor renderer keeps UP-face cable concealed for either tile finish and reveals it only when the finish is `NONE`.
- **Wall cable first appearance / missed initial snapshot:** client initial snapshots can be missed during login. `MoonStation14Client.requestCableVisualResync` periodically considers only loaded chunks in the player's nearby 3x3 chunk window, and `CableVisualResyncScheduler` rotates through chunks with no usable snapshot. It sends at most **2 requests per 40 ticks**, with a **200-tick per-chunk cooldown**. On the server, `CableVisualServerHooks.requestResync` validates the requester is still watching the chunk, that it is loaded at `ChunkStatus.FULL`, and that the requester has not exceeded the per-tick request limit. It sends a visual snapshot only to that requester (not to every watcher); the snapshot is read from cable storage and does not mutate network state. Normal chunk-watch snapshots remain the primary sync path.
- **Dense nearby-window cache churn:** `CableVisualClientCache` retains up to **65,536 records** and still caps retained chunk entries at **256**. The record cap accommodates **16 maximum-size chunks**, so all nine full chunks in a nearby 3x3 window remain usable while providing headroom; the existing bounded LRU eviction still applies when the aggregate cap is exceeded. Periodic resync therefore does not repeatedly request chunks merely because a dense 3x3 window exceeded the former 32,768-record budget.
- **APC/LV color:** `CableVisualPresentation` uses normalized `(0, 128/255, 0)`, matching SS14's APC `Color.Green` / `CableApcExtension` prototype and Robust `Color.Green`; this replaces the reported light-blue appearance.
- **Ordinary wall occlusion / see-through:** `CableVisualRenderer` establishes depth testing with `GL_LEQUAL` and disables depth writes while drawing the thin cable overlay, then restores depth writes and the expected depth/cull state in `finally`. It skips upload when filtering leaves no geometry. This is ordinary world occlusion behavior, not an x-ray or concealed-infrastructure reveal.

## Validation boundary and follow-up

These are code-path/status notes, not evidence of a successful graphical client run. **Graphical world behavior remains NOT MANUALLY VERIFIED**, including fancy/fabulous graphics modes, actual wall occlusion, tile transitions, and first-load/reconnect behavior. Existing focused pure tests and the earlier M10 render audit are evidence for palette, concealment, and face-local geometry only; they do not substitute for the owner checks below. SS14 sprite resources have **not** been copied/imported, so final SS14-textured fidelity is deferred; current visuals remain procedural placeholders.

The ordinary M10 occlusion work is distinct from the proposed handheld T-ray scanner. See [future T-ray scanner](../future-t-ray-scanner.md): it is **only a planned future milestone**, not an implemented feature or part of this fix. Ordinary cables must continue to respect block occlusion. The gas-pipe data owner/query is not yet established; do not imply that gas-pipe detection or scanner behavior exists.

## Owner validation checklist (not performed)

- [ ] In a graphical client, test both steel and white tile finishes: place each over a floor cable, then pry it; confirm the same cable hides/reappears without cable or power-network edits.
- [ ] Load a wall-cable chunk with an initially connected client and confirm the cable appears without editing power or waiting for a network update. Repeat on login/reconnect and after walking into an already-loaded nearby chunk.
- [ ] In fancy and fabulous graphics modes, inspect cables against solid walls and other occluders from multiple angles; confirm no through-wall visibility, z-fighting, or depth-state leakage after rendering.
- [ ] Exercise chunk unload/reload and reconnect with two clients watching the same area; verify snapshots converge and resync is requester-scoped, including a watcher leaving/unwatching during recovery.
- [ ] Check both integrated singleplayer and a multiplayer dedicated-server/client setup, including startup/login and ordinary floor, wall, and ceiling attachments.
- [ ] Record client/graphics settings and any remaining reproduction steps. Do not mark visual acceptance complete until the owner has observed these cases in-game.
