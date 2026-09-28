# M16: first-spawn cable visuals

## Change

Cable visual snapshots received before a client level/player is ready are now retained in a
client-only staging store, rather than immediately discarded. The store is keyed by dimension
and chunk, keeps only the newest revision per key, and is limited to 16 chunks and 65,536 total
records. Once the player and matching level are available, staged snapshots for loaded chunks
are applied. A new login resets the old visual cache and request scheduler but preserves bounded
snapshots already received on that connection before the world is ready. Logout fully clears
staged state. On an observed A-to-B dimension transition, only staged B snapshots survive; the
old cache and scheduler are reset, then ready B chunks can flush those snapshots. A stale revision
remains subject to the normal cache revision fence. No client-side cable topology is invented:
displayed state continues to come only from server-authored snapshots.

Staging is intentionally bounded, so a first visual may still arrive asynchronously after the
world becomes usable. If an early snapshot is missed, resync starts with a 30-tick timeout and
then retries at 60, 120, and finally up to 200 ticks; this reduces the prior long initial wait but
does not guarantee immediate first-spawn appearance. Heavy load or delayed packet arrival can
therefore cause a brief visible delay.

The resync policy now retries an unanswered first request after 30 ticks and then uses bounded
exponential delays (60, 120, then at most 200 ticks). A matching snapshot, including an empty
snapshot, is its implicit acknowledgement. Requests are limited to two per client tick, are
restricted to loaded chunks in the player's nearby 3x3 window (plus loaded chunk-watch
candidates), and favor the center/nearer chunks. Existing server watcher validation and global
server queue/packet/byte/record budgets are unchanged.

## Transition identity limitation

`CableVisualPayload` v2 identifies the dimension/chunk/revision, but carries no connection or
world-session identifier. Logout clears staging, but a delayed packet from an old connection
arriving between `LoggingOut` and the next `LoggingIn` can be staged and cannot be proven stale;
the new-login reset intentionally preserves it along with valid early packets. Likewise, a
same-dimension packet crossing that transition cannot be distinguished from a packet belonging
to the new connection. Observed dimension transitions retain only packets tagged for the newly
active dimension and only loaded chunks are applied, but neither behavior supplies session
identity. Full protection requires a payload/protocol session identifier; this change preserves
the v2 wire format and does not claim to solve that unidentifiable case.

## Validation

Focused pure tests cover pre-world staging/application across login reset, retaining the target
dimension across a world transition, logout discard, dimension isolation, revision ordering,
short retry after a denied initial request, authoritative empty-snapshot acknowledgement,
unload forgetting, pending-store bounds, and a shared two-request-per-tick limit across a
20-candidate conceptual client workload.

No manual client observation of appearance or first-spawn timing has been performed. Automated
staging/retry tests do not establish perceived timing under real client load.
