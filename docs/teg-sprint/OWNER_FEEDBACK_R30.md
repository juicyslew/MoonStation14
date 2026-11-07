# R30 corrections — paused, not complete

**Owner-directed pause for the codebase ECS overhaul.** No further fixes, scheduler work or submissions are authorized by this document. Start future consolidation/resumption at [HANDOFF.md](HANDOFF.md). This is not sprint completion or owner acceptance.

The current normal feature head remains **`run_a41a839f14eff258af724c60`, generation 2, FAIL**, acknowledged with confirmed cleanup. It completed 248 native tests with 23 required failures. No replacement has been submitted; neither the earlier timeout's cause nor native readiness is established.

## Source-backed corrections

- **Named-port admission:** `LoadedDevicePortGasOwner.admit` now invalidates topology after successful admission, following the exposed-lane owner. Pre-admission invalidation omitted the last newly admitted named port. Reconciliation could preserve its singleton as untouched, then block the complete discovered manifold because it overlapped that singleton. The existing manifold fixtures now check ordinary lookup resolves all six ports without an extra invalidation.
- **Relocation:** `PipeGasInventory.relocateBatch` preserves exact unrelated network objects, identities, generations, gas revisions and mixtures instead of recreating every network in the dimension.
- **Conservation observations:** continuous and finite-fueled audits count unique network gas-revision changes, not node metadata versions. Complete-network measurements use authoritative mixtures. The existing operation-derived allowance remains eight float ULPs per actual network write; zero writes still require exact conservation. Identity, membership and topology changes invalidate the comparison.
- **Fixtures:** canonical-volume finite contributions and untouched vacuum outputs replace obsolete per-port writes; valve assertions observe instantaneous topology merging and unchanged gas commits. Projection assertions use stored-float quantities. Retirement checks distinguish exact archived shares from subsequently split live networks. Exchanger gas baselines follow topology capture. Disposal geometry precedes ownership proof. Analyzer use spans real ticks rather than exceeding the unchanged eight-request gate. Miner readiness explicitly requests unknown production-target ownership before requiring the strict endpoint.
- **Unknown frontier:** fixture placement suppresses Minecraft neighbor/shape notifications that otherwise read—and load—the adjacent chunk. The fixture asserts the real frontier is non-FULL after setup and after discovery; recovery still requires the ordinary chunk-load callback.

These are implemented candidates, not native pass results. No runtime resources changed relative to R29. Approved `PowerRuntime` (`4e346e…`), `TegContinuousOperatingPlan` (`98f5fb…`) and world-atmosphere scheduling remain unchanged.

## Outstanding thermal causes

- **Foundation chamber:** fuel loss was 0.2107 mol, room/pipe energy gain approximately 513,514 J, APC consumption approximately 620,360 J, and the sampled exchanger pipe delta was negative. The strict chemical-excess and positive heat-transfer predicates were not established. Pinned SS14 research confirms old-energy/new-capacity plasma-fire accounting and fixed thermomachine power demand; neither supports changing chemistry or billing only useful heat.
- **Planetary comparison:** the final power frame was current and ready, but input was 769.64 W against 1,725 W output at the original 1,000-tick warmup deadline. Mean burner temperature was 566.99 K; the approximately 509 K samples were individual hot-contact cells, not the room mean. Total owned CO₂ was effectively unchanged across several 100-tick intervals despite detached evaluation finding all 27 burner cells eligible. Detached evaluation is not committed reaction evidence.

The world reaction scheduler holds its HOTSPOTS pass while background chunk-load scans remain pending and defers already-reacted cells until pass completion. Investigation established that dependency, **not proof that its backlog caused either failure**. LINDA also performs whole-air chemistry. The owner chose to leave the scheduler alone; no scheduler/cadence change was implemented. No coefficient adjustment, timeout increase or coverage removal is authorized or claimed here. The 2 J / 1,000 J / 1,200-tick guards remain intact.

## Validation boundary

The final current-byte recheck, including the admission-ordering correction, passed all three compile tasks and six JVM classes / 30 tests with zero failures, errors or skips. Source hashes were stable during validation; protected power, operating-plan and world-atmosphere hashes remained unchanged. The retained 175-class floor, native coverage, readiness categories and package/provenance requirements are not replaced by these focused checks. No owner testing, acceptance, merge or sprint closure is claimed.
