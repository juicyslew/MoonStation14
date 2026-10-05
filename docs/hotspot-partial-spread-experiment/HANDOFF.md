# Partial-hotspot propagation — closed

The owner live-tested the change, reported faster fire spread, accepted it, and requested sprint closure. This is retained behavior, not a pending experiment. The accepted SS14 departure is indexed in `../SS14-DESIGN-DEPARTURES.md`.

## ARCHITECTURE

An already-first-processed **partial hotspot** can expose face-adjacent neighbors after a positive local combustion pass. Its local post-process flame temperature must exceed 423.15 K; cold source bulk gas no longer blocks that exposure. Full-cell/bypassing hotspots retain their existing bulk-temperature behavior.

The kernel reports spread eligibility, the planner selects the appropriate temperature and creates exposure offers, and the service publishes receiver exposure only after a successful source transaction. Both the production reaction path and the shared hotspot commit helper use the same partial-versus-full temperature distinction.

## IMPORTANT FILES

- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/reaction/HotspotKernel.java`: positive-combustion partial-spread eligibility; partial/full processing unchanged.
- Same package, `HotspotSpread.java`: local flame temperature for partial sources, bulk temperature for full sources.
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/AtmosphereService.java`: post-commit exposure and commit-helper gating.
- Focused tests: `HotspotKernelTest.java`, `HotspotSpreadTest.java`, `AtmosphereHotspotSpreadTest.java`, and hotspot/reaction commit coverage.
- `EXPERIMENT.md`: scope, evidence and targeted reversal guidance.
- `../pressure-transport-sprint/HANDOFF.md`: underlying pressure transport, composition diffusion and cycle architecture.

## SS14 CORRESPONDENCE

Compared with pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, this intentionally removes full-tile-only neighbor-spread eligibility for actively burning partial hotspots and uses their local flame temperature.

Tritium and other reaction chemistry, fuel/oxygen requirements, chemical ignition thresholds and reaction heat are unchanged. So are the 95% partial/whole-air processing threshold, 250 L neighbor exposure, 0.85 temperature attenuation, receiver volume amplification, first-process skipping, deferred receiver processing, loaded/open topology checks and computational budgets. No water-vapor trigger or whole-cloud ignition is introduced.

## DECISIONS / WHY

A flame occupying part of a cubic metre should influence neighboring combustible gas before the source cell becomes almost entirely burning or its bulk gas becomes sufficiently hot. Earlier exposure is the narrow adaptation; exposure strength was deliberately not weakened or retuned at the same time.

The service commit helper previously retained an independent bulk-temperature guard. That guard was corrected too; changing only the kernel/planner would not deliver the intended behavior. Fizzle, first-skip and rejected-write cases must not publish partial spread.

## KNOWN GAPS

Receiver exposure creates hotspot metadata, not direct bulk-gas heating. A 250 L offer becomes 6,250 L metadata through the existing ×25 rule; cold receiver whole-air/bypass processing can still limit actual combustion. This behavior was left unchanged intentionally. The live result improved despite that limitation.

No controlled before/after flame-speed benchmark or new station TPS measurement was captured. The earlier independent hotspot first-process parity question remains outside this change. Entity fire-stack chemistry/damage/drying behavior was not changed by this follow-up.

## VALIDATION

Focused kernel/planner and service commit tests passed. **14/14 selected GameTests** passed across reaction, fractional-chain and reactive-front fixtures. Independent review passed the corrected service gates, success-only publication and cold-bulk/hot-flame tests. Unfiltered `compileGametestJava assemble` and diff check passed. The previous full 1,928-test/76-GameTest pressure gate was not rerun for this narrow change.

Evidence: `build/hotspot-partial-spread-experiment/final-focused-junit/` and `final-gametest-latest.log`. Successful disposable worlds were removed after logs were retained. Reactive-front far-water observations are not a controlled speedup measurement.

Rebuilt `build/libs/moonstation14-0.0.1.jar`: 3,924,084 bytes, 2026-10-05 06:35:20 UTC. SHA-256: `2BCF2D37F0A6FFDCE7F634BE03CE39B49EDCD193E09F06502D1D57B67F432231`.

## NEXT WORK

No further propagation change is required for this sprint. Preserve the accepted behavior and departure ledger. If a future issue warrants reversal, revert only the partial-spread eligibility/temperature selections and associated expectations—not entire files containing earlier uncommitted pressure work. No commit or merge was performed as part of this closeout.
