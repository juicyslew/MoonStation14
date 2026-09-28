# M13 cable visuals after chunk return

## Change

Client cable snapshots now retain their revision fence when a chunk unloads, and an unload is applied only when its dimension still matches the active cache. This prevents a delayed unload from an old dimension from tombstoning a newer dimension's snapshot. An unload only clears that chunk's retry cooldown; it does not reset global retry timing or cursor progress, including during unrelated chunk unload churn.

Client chunk loads enqueue a priority resync candidate. The queue is capped at 256 entries, and each client tick inspects at most 64 entries from a rotating cursor. Unloaded entries are pruned without using the shared two-request budget; unload forgets scheduler state even when no cache snapshot ever arrived, but only in the unloading dimension. Each client tick sends at most two cable snapshot requests total, prioritizing newly loaded chunks before the periodic bounded nearby-window repair pass. Per-chunk request cooldown remains 200 client ticks. Requests are issued only for client-loaded chunks, and authoritative empty snapshots count as complete. The periodic pass remains a fallback for snapshots missed during login or before server watcher registration is ready; retries then naturally repeat after cooldown. No client or server chunk is force-loaded.

No server payload, validation, graph, rendering, atmosphere hook, or chunk-loading behavior changed. Automated cache/scheduler tests cover same-dimension unload and fresh revision, delayed cross-dimension unload, unload before first snapshot, stale-priority pruning, queue bounds, unrelated unload churn, priority request bounds, retry cooldown, and empty snapshots. The coordinator verified compilation, unit tests, and a successful 157-test server GameTest run. No manual graphical/client testing was performed.

Validated server resync attempts consume the per-player per-tick request budget before queue-capacity rejection. When the send queue reaches its 16,384-job cap, requests are rejected before scanning the queue to replace a duplicate requester-only job; the client retries after its cooldown. This bounds repeated queue-full attempts without evicting queued jobs or exceeding the queue cap. With available capacity, a matching requester-only job is still replaced with a fresh snapshot and monotonic revision.

## Remaining race

There remains a short client/server lifecycle race: a priority request sent immediately after a client chunk-load event can reach the server before the player is registered as a FULL chunk watcher. The server rejects it safely; the 200-tick cooldown and periodic loaded-window retry provide eventual repair. The client never forces a chunk load.
