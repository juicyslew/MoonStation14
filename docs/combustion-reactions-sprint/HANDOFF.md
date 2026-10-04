# Gas reactions and partial combustion

## STATUS

Reconstructed 2026-10-03. Owner accepted closeout/merge planning with M5 proof **accepted/deferred**, not passed/verified. Historical uncommitted-branch language is not current checkout status. Delivered partial combustion is not full fire/parity acceptance.

## ARCHITECTURE

Typed gasReaction prototypes feed deterministic pure evaluation; bounded finite-cell queues schedule reactions. Hotspot kernels/spread and exposure integrate fire stacks/body temperature. Only owned finite cells react. Runtime bounds and thermal ownership remain atmosphere-service responsibilities.

## IMPORTANT FILES

`plan.md`; under `src/main/java/com/juicyslew/moonstation14/ms14/`: `atmos/reaction/{GasReactionEvaluator,GasReactionStep,HotspotKernel,HotspotSpread}.java`, `atmos/world/AtmosphereService.java`, `fire/FireStackSystem.java`.

## SS14 CORRESPONDENCE

Plan records pinned upstream inspection and six reaction mappings. Stable-ID tie-break for equal priorities deliberately differs from upstream tie ordering. Source references in plan distinguish reaction chemistry from partial hotspot/fire integration.

## DECISIONS / WHY

Heater admission rejects near-zero gas/heat capacity to bound runaway, not as a general gas clamp. Owner-observed flames/restart are observational evidence, not instrumented damage/persistence proof. Do not equate reaction prototypes with complete fire behavior.

## KNOWN GAPS

No recorded post-later-change server rerun. In-world damage ledger, timed persistence/recovery, profiling and earlier near-zero ambient issue remain unresolved in closeout evidence. Prior unrelated cable/slip failures prevent a full-green historical claim.

## VALIDATION

Historical 1,398-unit-test XML snapshot had zero failures; GameTest compilation passed. Earlier 226-test run corrected a pig fixture; later 235-test run passed nine reaction tests but had two unrelated failures. Owner saw flames in a 3×3×3 room and after restart. No fresh suites run.

## NEXT WORK

If separately scoped, validate current server integration, actual damage ledger, scheduling/persistence and performance. Preserve accepted/deferred versus verified distinction when reporting M5.
