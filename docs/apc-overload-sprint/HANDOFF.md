# APC overload and breaker trips

## STATUS

Reconstructed 2026-10-03. Implementation/evidence through M6 recorded; manual owner visual/audio acceptance remains open in the sprint records.

## ARCHITECTURE

APC trips when actual delivered output is strictly above 20 kW for strictly more than three seconds, observed at 20-tick solver cadence. Topology-generation changes invalidate samples/reset continuity. Timer is transient; trip latch persists. Manual reclose clears latch and restarts timing; trips remain circuit-local.

## IMPORTANT FILES

`current-handoff.md`, `milestones.md`, `wired-proof.md`, `topology-freshness.md`, `debug-load-fixture.md`, `audio-milestone.md`; runtime under `src/main/java/com/juicyslew/moonstation14/ms14/power/`.

## SS14 CORRESPONDENCE

Docs audit APC maxLoad/threshold/timing. Local 25 kW debug source makes overload reachable; it is not ordinary starter-station balance or SS14 solver ramp/peg parity.

## DECISIONS / WHY

Open breaker can charge from its known MV input without output/discharge. Server emits source-verified `machine_switch.ogg` on accepted manual toggles and timed trips; no duplicate client sound. Preserve unresolved per-file license information.

## KNOWN GAPS

Later visual-kit closure records a visible overload trip and working single server breaker sound with two real clients; it does not establish the complete timed-trip/reclose/persistence checklist or representative scale. Flicker, aged bulbs, ghost behavior and station events excluded. No APC-population/performance ceiling demonstrated.

## VALIDATION

Historical isolated 168/168 passed after fixture changes; 165/165 predates the fixture. Other full runs had varying unrelated failures of unproven cause. No new suites or listening test performed.

## NEXT WORK

Follow `current-handoff.md`: normal UI, timed trip/reclose, persistence, independent circuits, open-breaker accounting, and disabled-power gate. Keep debug balance distinct from production tuning.
