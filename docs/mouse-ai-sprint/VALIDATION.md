# Mouse AI validation

**Automated correction validation passed; owner confirmed the core behaviors.**
Owner observed random wandering, occasional squeaks, working jug drinking after
forcing Parched, and much less jitter. This does not establish a measured network
correction cause or every terrain scenario. Emote/idle-attention follow-up
automated validation has also passed; connected owner review remains pending.

## Current emote/attention artifact

- `/emote squeak` reuses committed-body admission and resolved emote permissions.
  A canonical mouse role takeover fixture verifies one spatial sound, one chat
  line and immediate-repeat cooldown suppression; a committed human is denied.
  Focused native: **2/838 selected, 2/2 required passed**;
  `build/mouse-emote-final-final.log`.
- Production idle attention turns toward a nearby player entity and yields to
  food. Its observer is a synthetic FakePlayer; this is native pose/behavior
  evidence, not connected-peer or visual acceptance.
- Latest full native: **838/838 required passed**;
  `build/mouse-emote-final-full.log`. JVM: **3,290 passed**;
  `build/mouse-emote-junit-jar.log`. Interaction/prediction readiness:
  **51 passed**; `build/mouse-emote-readiness.log` and the readiness report.
- Scoped source review passed. An earlier full run failed two airlock fixtures;
  focused reproduction **8/8 passed** and the later full suite passed. The earlier
  cause remains unestablished; do not claim the fixture cleanup diagnosed it.
- Current JAR: `build/libs/moonstation14-0.0.1.jar`.
  SHA-256: `1464EA81634988A273E13552BE4AD052F205BECF46F8C24BDA42D2AD2C2E1E58`.

See `EMOTES-ATTENTION.md` for mapping and failure history. Carrying/inventory
escape remain deferred, not implemented. The correction artifact below is now a
historical baseline. No commits or pushes were made.

Owner
testing after the activation fix exposed correlated wandering, failed eating,
stale food pursuit, stalls and increased movement jitter. Earlier passing results
and artifact hashes below are historical baselines, not validation of the current
working source. Final correction evidence is below.

## Final correction evidence

- Focused native checks: **56/56 required passed**;
  `build/validation-logs/mouse-ai-root02-mouse-ai-final3.log`.
- Full native suite: **835/835 required passed**;
  `build/validation-logs/mouse-ai-root02-full-gametest.log`. The final log reports
  all required tests passed and `BUILD SUCCESSFUL`; earlier failed logs below
  are diagnostic history, not this final result.
- JVM suite: **3,288 passed**, zero failures/errors/skips;
  `build/validation-logs/mouse-ai-root02-final-unit.log`.
- Interaction/prediction readiness: **50 passed**;
  `build/reports/feature-readiness/readiness.md`. This is scheduler/dispatch and
  composition evidence, not proof of connected-player corrections or jitter.
- Build passed: `build/validation-logs/mouse-ai-root02-final-build.log`.
  JAR: `build/libs/moonstation14-0.0.1.jar`.
  SHA-256: `1514E9EBDDE252D67E70DE5A477038648DF161990C4ECFD40DCB1ACBC4CF7189`.
- Independent final source/evidence review: **PASS**. Fixtures retain production
  traversal, deferred-budget retries without resetting budgets, and grounded
  idle arrival; shared admission and continuation contracts were also reviewed.
- Normal main-only server: one ordinary mouse consumed a normally initialized
  registered loose cheese slice. The item disappeared by the third sample and
  stomach nutriment was `1.5d`;
  `build/validation-logs/mouse-ai-root02-single-final-rcon.log`.
- Normal 16-mouse/16-cheese profile: cheese queries returned no rows by the first
  10-second sample and a sampled mouse had stomach nutriment. This does not map
  each food to an individual mouse or establish a moving-food workload.
  `build/validation-logs/mouse-ai-root02-food-final16-rcon.log`.
  JFR server ticks: mean **1.515 ms**, p99 **3.764 ms**, max **5.396 ms**;
  `build/validation-logs/mouse-ai-root02-food-final16-server-jfr.json` and the
  corresponding `-server.jfr`.

The temporary main-only Gradle binding was restored and the final JAR excludes
GameTest fixtures. Passing disposable run directories were removed; logs/JFR and
useful failure artifacts were retained. No owner-world/configuration changes,
commits or pushes were authorized or performed.

**Remaining boundaries:** connected jitter, audible/visual owner acceptance and
normal-server moving-food workload remain unverified. The earlier normal-world
failure's exact rejection cause was not captured; successful current consumption
does not establish that retrospective diagnosis. Sixteen tracked root `net/`
reference-source deletions have unknown provenance and were left untouched; they
are outside configured project source sets and are not attributed to this work.

## Correction failure history (superseded by final evidence)

- Initial focused corrections passed 53 required native tests, but later runs
  exposed movement regressions. Step/drop traversal remains unresolved; slab and
  idle-arrival checks must also be green before acceptance.
- A normal main-only server with 16 mice and 16 registered loose cheese slices
  consumed no cheese after 90 seconds; a sampled mouse's hunger reached zero.
  Evidence: `build/validation-logs/mouse-ai-root02-final-food3-rcon.log`.
- That unsuccessful workload yielded 83 JFR tick samples: mean 1.72 ms, p95
  2.98 ms, max 7.31 ms. It does **not** validate successful food decisions or
  explain/fix connected-player jitter.
- Review identified and implementation addressed rejected-source cache growth
  and route budgets resetting across dimensions. Focused NPC-physics coverage
  initially passed 7 tests including the cross-dimension budget case; subsequent
  movement failures prevent accepting the overall physics changes.
- Near-floor ray clipping regression passed. Admission and continuation retain
  distinct contracts: selecting food requires the body-eye ray; an active bite
  revalidates the nominated source with gaze-independent loaded reach/collider
  LOS. Seven handless-ingestion checks passed after restoring that distinction.
- The unreachable pinned-source alternate-food regression passed. Normal-world
  timed food completion and final moving-food workload measurements are still
  required; operation starts alone do not establish consumption.
- Interaction/prediction readiness passed (50 checks in the reported run).
  Full JVM/native validation and a final build remain pending.
- Ambient squeaks use the existing asset and resolved policy. Source review
  passed and focused sound-event tests ran with the initial correction batch;
  hearing/visual review on a connected client remains unverified.

The old `BE110AFC…` JAR is the activation-fix baseline, not these corrections.
Do not close the sprint or claim connected jitter fixed from headless evidence.

## Historical activation baseline

Four mice summoned in a normal superflat grass
world remained stationary after two builds. Earlier passing fixtures did not
establish the literal command/NBT-load path; their results below are historical
evidence, not proof that normal summoned mice work.

The concrete mismatch is Minecraft `SummonCommand.createEntity` loading an
empty NBT tag through `EntityType.loadEntityRecursive`. `Mob.readAdditionalSaveData`
sets `NoAI` from that tag, clearing the constructor's `true` value when absent.
The local NPC eligibility gate rejected that entity. Direct `EntityType.create`
plus `addFreshEntity` tests never exercised the NBT reset and must not be called
equivalent to the summon command. The generic resolved-NPC activation correction
now owns native-AI suppression before eligibility; non-NPC policies are untouched.
The literal-command regression and normal main-only reproduction below establish
this path without treating prepared fixtures as equivalent to owner gameplay.

## Current acceptance evidence

- Full JVM suite: **3,286 passed**, no failures/errors/skips.
- Focused native suite: **90/824 passed**, covering NPC, motion, doors,
  ingestion and ChemMaster regression boundaries.
- Full native suite: **824/824 passed** in a fresh isolated fixture world.
- `featureReadiness -Pms14Readiness=interaction,prediction`: **50 passed**.
- Independent source review: **PASS** after correcting idle-task retention,
  partial-height support, puddle exclusion and player/NPC ingestion ownership.
- Final build (including the remote NPC gait fix): **PASS**;
  `build/libs/moonstation14-0.0.1.jar`.
  SHA-256: `7BA281A6DF603470EC5755DCAB6ACF7691C2F7342475D42BD5A97B18525368D4`.
  Log: `build/validation-logs/mouse-ai-build-final-with-gait.log`.

The latest passing full-native evidence is
`build/validation-logs/final-native-20261012d.log` (824/824). Supporting runs
are under `build/validation-logs/`. In particular,
`mouse-ai-full-native-final.log` is an **older failed baseline**, not the final
passing run. Earlier failed runs are diagnostic history, not current acceptance
evidence. Preserve useful failure
artifacts; passing disposable fixture worlds are removed when identified safely.

## Exercised behavior

Resolved NPC composition and defaults drive the production tick consumer.
Tests exercise food/drink priority and shared timed consumption, rejected/full
sources, player takeover, puddle exclusion, retained idle destinations and
grounded arrival before waiting, slabs, one-block rises/drops, deep-pit avoidance,
and actor-specific physical collision/support. Closed airlocks permit mice
without opening; walls and human collision remain blocking. Collider interaction
rays are unchanged. Welding is not implemented locally.

NPC motion reuses the grounded motor and authoritative body world step. It has
its own synchronized physics marker, not a player claim, and suppresses duplicate
vanilla travel. Owner-held parked OFFLINE bodies stay asleep to preserve exact
Creative return/reconnect locations. Route search is a bounded custom surface
A* Minecraft adaptation, not native Minecraft or SS14 pathfinding reuse.
Remote NPC gait reuses the existing client displacement-based animation updater;
local predicted player animation retains its existing exclusion.

## Blocking regression diagnosis

The full suite exposed explicit-ingestion fixtures that did not model player
movement ownership and an NPC cleanup path that could cancel player operations.
Fixtures now use the existing ownership lease; NPC cleanup targets only its own
transaction identity. Player puddle ingestion remains supported.

ChemMaster sessions can be invalidated by an unrelated global power-topology
generation change. The existing fresh-power guard remains unchanged. Its fixture
uses typed same-tick evidence to retry an interrupted complete sequence within
the original deadline; success still requires every original stun, retention,
action-denial, recovery and range-close assertion. An interrupted attempt is
never counted as success. This is not a power-system behavior change.

## Unverified boundaries

Automated tests and source review do not establish connected-client/GPU visuals,
live network latency behavior or owner acceptance. Perform `MANUAL.md` before
closing the sprint. Existing mouse models, sounds and attribution are retained;
no new audio assets were introduced.

## Ordinary-summon inactivity correction

Owner reported four summoned mice remaining stationary. The route planner's
0.65-block start-node radius rejected valid corner positions, whose distance
from a cell center is about 0.707 blocks. Start-node resolution now accepts that
offset and checks the loaded, collision-valid connector; supported graph nodes
and the existing movement pit guards remain in use.

New tests exercise normal fresh-entity join and production ticks at integer,
near-corner `.01`/`.99`, and centered coordinates, without manual enrollment or
AI-state edits. Focused native checks: **15/15 passed**; log:
`build/validation-logs/mouse-ai-summon-fix-native.log`. Focused planner/prototype
unit checks and `build` passed. This narrow fix was not followed by another full
native suite; the earlier 824/824 result is baseline server evidence.

Updated JAR: `build/libs/moonstation14-0.0.1.jar`.
SHA-256: `1AC4855DE8214268E4FB8717B44C64C93776042FC452C3AA07F3D69E47D6362B`.
Connected retest is pending: the owner's original positions/terrain were not
captured, so this is a confirmed planner defect, not a proven diagnosis of that
specific world.

## Verified command/NBT activation correction

The uniquely tagged literal-command regression failed before the correction:
all four mice had `NoAI=false`, `driven=false`, displacement `0.0`. The same test
passed afterward (1/1); the full native suite passed **826/826**, with JVM and
interaction/prediction readiness checks also passing.

An isolated **normal dedicated server**, temporarily bound to the main source set
only, verified grass at y=-61 and summoned four mice with default vanilla commands
and no NBT overrides. Over eight seconds all four moved: **7.36, 7.56, 4.94 and
12.98 blocks**. No GameTest registrar, catalog or test batches appeared in that
run. The temporary Gradle binding change was restored; owner worlds/configuration
were untouched. EULA acceptance was explicitly authorized for this disposable
local server by the owner.

Evidence: `build/validation-logs/mouse-ai-normal-main-only-proof.log` and
`build/validation-mouse-ai-normal-main-only-console.log`.
Final build passed; the JAR contains NPC runtime classes and no GameTest fixtures.
Current artifact: `build/libs/moonstation14-0.0.1.jar`.
SHA-256: `BE110AFC476BA9E22D88DD687E84F205E7CA9A7425C45231AA7D6DFB0BE7F751`.
Connected-client visuals and owner retest remain unverified; do not close the
sprint from these automated/headless observations alone.
