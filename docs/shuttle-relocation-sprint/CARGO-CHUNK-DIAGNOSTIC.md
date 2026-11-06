# Ordinary shuttle cargo: chunk persistence diagnostic

## Scope

One cargo-only fixture, `ordinaryCargoSurvivesNormalWorldChunkUnloadReload` in
`SableCargoChunkPersistenceGameTests`. Expected filtered selection1/892 with
`shuttle-cargo-chunk-persistence`. R24 native **1/892 selected, 1 completed/passed,
0 failed**, exit0. Focused fixture compilation and independent source/evidence
review passed. No production code changes.

This is **not** logout, body/Mind, flight, connected-client or process-restart
continuity, a cargo-loss fix, M1 acceptance or readiness. R3's resident reconnect
evidence and the owner's unexplained loss remain separate.

## Required observations

1. An owned empty remote scene supports the canonical bare hull naturally.
   Three normal jug roots hold5/6/7 units of water; settled state, exact live
   frame/tracking and normal token/world-entity ledger ownership are admitted.
   Snapshot item/count/components, UUID/token, storage/world points and chunks
   before release. Effective native substeps must be2.
2. Release only the scene's exact fixture lease and use normal pending chunk
   save. Observe every hull-world/cargo-world chunk below FULL without loading
   it, all old cargo removed as `UNLOADED_TO_CHUNK` and absent by UUID, and the
   old sublevel removed/absent. Global plot-storage chunks are separate metadata.
3. Recheck those conditions throughout a quiet interval, then explicitly reload
   with a new owned lease. Allow asynchronous entity loading; require distinct
   reconstructed Java objects with the original sublevel and cargo UUIDs.
4. Validate the replacement plot's storage origin/footprint before registering
   destructive cleanup. Require settled cargo, unchanged item/count/components
   and ledger ownership, exact replacement-frame tracking and storage-position
   continuity within0.12 blocks. Frame restoration is tested, not assumed.

`RESIDENT_UNLOAD_NOT_OBSERVED owner=unknown` is an inconclusive premise, never a
pass or cargo-loss finding. Partial unload, interrupted quiet interval,
destructive removal and failed reacquisition retain distinct failure evidence.
Do not force eviction, release unknown tickets, recreate/rebind a ship/entity,
repair ledgers or use plot teardown as the unload action.

## Reuse and boundaries

Reuse `SableFixtureScene`, `FixtureChunkTickets`, `BoundedGameTestWait`,
`SableFixturePlot.allocateAndAuthor`, canonical `ShuttleCabinFixture.buildHull`,
existing registered cargo/placement helpers, `LoadedFrameRayQuery` and normal
item ledgers. The existing nonmutating `wrapAssembled` implementation can wrap
the validated replacement owner despite its historical assembly-specific name;
`trackPlot` then owns terminal cleanup. The old wrapper's exact-reference guard
prevents it from deleting the replacement. Cargo cleanup also uses exact known
references. Unverified replacement ownership is not adopted/deleted.

No Sable source/binary/native changes or new hooks; no gravity, rotation, speed,
teleport-recovery or global residency changes. Default2 remains unchanged;
ten substeps is still only the separate tested candidate bandage.

Plan: `GATE-PLAN-08-CARGO-UNLOAD-R24.json`. Author compilation evidence:
`build/shuttle-feedback-08-author-cargo-unload-r24.log`.

## R24 verified observations

- Cargo ready at test tick21, effective substeps2. The separate global
  plot-storage chunk was `[1280064,1280064]`; both world witness chunks were
  absent at confirmed unload tick22. All three old cargo objects had
  `UNLOADED_TO_CHUNK` and null UUID lookups; the old plot was removed/absent.
- Quiet checks ran at ticks22–41: **20 observations spanning19 intervals**, not
  20 elapsed ticks. Explicit reload requested41; same plot UUID on a new object
  acquired42; final cargo verification44. All three original cargo UUIDs were
  new objects with matching storage points, replacement-frame tracking and
  normal world-entity token-ledger ownership.
- Passing source-guarded assertions compare full pre-unload `ItemStack.copy()`
  snapshots with `ItemStack.matches`, including count/components;
  `ReagentComponent.equals` compares its exact cents map. This proves admitted
  snapshot conservation. Initial5/6/7-unit amounts are source-derived setup:
  neither initial numeric cents nor final numeric cents were separately printed
  or explicitly numerically asserted. Do not present500/600/700 cents as raw
  observed values.
- Cleanup skipped the removed original owner and closed the validated current
  replacement. Test passed, worlds saved and server stopped normally. No
  process-restart, body/logout, flight or owner-loss result follows.

Snapshot `78fa00b750bc402cd169a69ba8f65a2e060defd5f6766c685e2049e90f89c647`.
Receipt `shuttle-feedback08-cargo-unload-20261015-r24-gate-validation.json`;
raw `feedback08-cargo-unload-r24-native.log`, SHA-256
`dc23cc87a73877485f81ac106462b3cb6fe8efe93978e71509612d08d8dd02a5`,
under `build/agent-workflow/shuttle-relocation-sprint/reports/`.
Retain the bound evidence; no logging-only rerun is needed.
