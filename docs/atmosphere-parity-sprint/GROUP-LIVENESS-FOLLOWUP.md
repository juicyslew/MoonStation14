# Group pacing and classification liveness

## Confirmed causes

### Classification

The ownership search supports 131,072 candidates, but exterior witness validation and total retained proof references were capped at 4,096. A valid loaded sky route with more than 4,096 visited open cells was repeatedly rejected and requeued, even with unchanged topology. Validation and retained-reference bounds now match the existing search bound. All witness cells still require live loaded/passable checks and a sky connection; full dependency/column/chunk invalidation remains intact.

A separate closed-openable fallback used **column dependency** as evidence that a closed door/trapdoor/gate was inside a passable witness. Unchanged closed blocks below witness air could repeatedly invalidate valid exterior proofs. That also invoked topology cleanup, discarding transport/group work. The fallback now tests exact witness-cell membership. Actual state transitions and block changes retain broad column invalidation.

These fix reachable liveness bugs, not every reason for UNKNOWN. Unloaded boundaries and searches reaching the 131,072-candidate limit remain unresolved until their safety conditions change. The command now distinguishes queued/search/claim/unloaded/saturated/rejected states.

### Excited groups

The old implementation computed the full average, exchange and snapshot up front, then counted dummy per-cell progress before publishing. The service rebuilt/validated tracked-cell snapshots on resumptions and restarted them after writes. This could defer already-computed results while blocking subsequent atmosphere stages.

Pinned SS14 instead snapshots a group queue once, ticks groups once per cycle, and performs each eligible whole-group average/copy as one operation. Budget checks are between groups. Breakdown is on the fifth processed cycle (`>4`); dismantling is `>16`, with breakdown taking precedence.

The port now follows that structure: queue group identities once per cycle; read gas only for an eligible breakdown; capture the current group, average and publish within one server callback; resume between groups, not between fake cell counts. No across-admission gas snapshot/replay is needed. Generator writes between callbacks therefore cannot perpetually invalidate a suspended average. Boundary exchange remains success-only. Zero-budget calls neither initialize/advance the queue nor tick cooldowns.

**Whole-group instantaneous mixing exists in SS14, but ongoing hotspot processing postpones it.** Follow-up upstream research found that `ProcessHotspot` resets both group cooldowns after its first-process skip and before burn/fizzle checks. The port omitted this reset. Consequently a burning group could reach its averaging deadline when SS14 would keep postponing it. The hotspot-driven reset and progressive visual delivery now pass focused runtime validation; see `REACTIVE-FRONT-FOLLOWUP.md` for evidence and remaining live acceptance. The earlier explanation that a room-wide sweep during persistent fire was simply unavoidable source behavior was incomplete.

## Other reported symptoms

- Whole-air fire reactions can create hotspots without grenades when local fuel, oxygen and temperature satisfy reaction gates. Automatic creation requires a real fire event, not merely a carried Fire result. Prior heat is a possible explanation, not a confirmed live cause. Diagnostics retain recent creation details plus a separate first-ignition-per-cycle history so a large wave does not immediately erase its root evidence.
- The sickness string belongs to uncooked-animal-protein bloodstream metabolism. It is local to its player target; nearby NPCs cannot broadcast it. Naturally circulating blood is not converted by digestion during bloodstream processing. Stomach blood digestion or previously present reagents can supply the protein, but the player's actual history is unknown. A rare, bounded provenance log records applied sickness popups with reagent/stage/profile/quantity/creative status. Gameplay, popup wording and creative protections are unchanged.

## Measurement and acceptance

A local headless 5,000-cell eligible group measured 19.550 ms total, 14.243 ms averaging, 1.460 ms capture and 0.752 ms map publication in the latest gate. Timings varied between runs. This is not live-world capture/publication or a server-TPS result. A large atomic operation can exceed the soft 3 ms checkpoint; those milliseconds alone do not establish the reported multi-second pauses.

Independent review passed the final queue and capture structure. Tests establish 5,000 cumulative member reads across the first five cycles (zero on cooldown-only cycles), one complete mean, current data after intervening writes, zero-budget safety, and resumption only between groups. Fresh gates passed **1,883 JUnit tests across 277 suites** with zero failures/errors/skips, **73 focused GameTests**, default `compileGametestJava`, and `assemble`. Evidence: `build/atmosphere-parity-validation/group-parity-liveness-gate-01/`; artifact: `build/libs/moonstation14-0.0.1.jar`.

`/ms14atmoscheck` exposes actual operation/read counts and separate capture/average/publication timings, ownership reasons and ignition roots. Use fixed-position counter differences to verify live impact. Live station TPS, client delay and gameplay responsiveness remain unaccepted; the existing intermittent fire-drying failure and whole-namespace validation boundary remain as documented in `VALIDATION.md`.
