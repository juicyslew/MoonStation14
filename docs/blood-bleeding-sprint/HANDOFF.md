# Blood, lungs and atmosphere physiology

## STATUS

Reconstructed 2026-10-03. Implemented local physiology with acceptance gaps; no full parity/owner closure recorded in `current-status.md`. Later atmosphere work does not alone certify scheduled physiology.

## ARCHITECTURE

Resolved character policy governs optional blood, lungs, thermal and barotrauma behavior. BLOODSTREAM owns circulating reagents; BLOOD stores bleed metadata. Pending spill is a persisted temporary mixture. Lungs own gas/saturation independently. Exposure requires authoritative atmosphere samples and fails closed where unavailable.

## IMPORTANT FILES

`current-status.md`, `ss14-parity-revision.md`; under `src/main/java/com/juicyslew/moonstation14/ms14/`: `blood/{BloodSystem,BloodReducer,BloodAttachment,BloodstreamStorage}.java`, lung system, and `atmos/exposure/{BodyTemperatureSystem,BarotraumaSystem}.java`.

## SS14 CORRESPONDENCE

Pinned SharedBloodstreamSystem.TryBleedOut, RespiratorSystem.Update and ThermalRegulatorSystem are recorded references. Local persisted stores, alternating lung phase, body-only vacuum cooling, typed-damage scaling and toxic-gas policy are adaptations, not complete organ physiology.

## DECISIONS / WHY

Above-threshold spill receives one ordinary placement attempt, then clears the whole pending batch even on failure/insufficient capacity: intentionally lossy, no retries/backpressure. Dead initialized actors may bleed without refresh/bloodloss damage. Unknown atmosphere must not become vacuum.

## KNOWN GAPS

Latest recorded physiology checkpoint does not prove globally enabled scheduled integration, manual gameplay, persistence/crash semantics, equipment protection or alerts. Sequential solution writes are not crash atomicity. Overall historical suite was not green.

## VALIDATION

Latest recorded isolated run: 193 tests with one unrelated villager-travel failure; earlier 176/190/191 snapshots are separate evidence. Focused physiology routes are covered, not every globally scheduled owner path. No fresh tests run.

## NEXT WORK

Use parity revision/current status to scope scheduled-owner and actual-body integration tests; establish manual/persistence evidence and classify the unrelated fixture failure before claiming full acceptance.
