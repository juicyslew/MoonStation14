# M2 validation status

Focused candidate gate plan: [chemical-mutation-gate-plan.json](chemical-mutation-gate-plan.json).

## Current M2 implementation evidence (not a serialized gate result)

- Fixedpoint correction: 22 focused JVM tests across five classes, 0 failures. Coverage includes wheat 650/1650 and tomato 441/1841, signed-epsilon/cent additions, and values above the former 10000 codec cap.
- Command/native fixture compilation passed. Strengthened fixture causal, table and status-ID assertions pass independently.
- Final core independent review is underway; serialized integration gate remains pending. M2 live core and fixtures are implemented, but are not technically gated or accepted.
- Earlier 19-test/five-class snapshot is superseded historical evidence. Preserve foundation r7 and its evidence as historical; do not imply that it validates M2.

## Serialized gate status

M2 gate r1 returned `needs_changes`. The only confirmed r1 result recorded here is readiness `99/101`; both failures are in `PrototypeRuntimeTest`, whose expected registered/exported sets omit the new `weighted_random_fill_solution` owner even though production registration/export includes it. Evidence: `build/test-results/featureReadiness/xml/TEST-com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntimeTest.xml`.

M2 gate r2 returned `needs_changes`: the command fixture's initial one-unit Vestine assertion was delayed, and authenticated chemical roundtrip timed out at 3200 ticks. This timing failure was corrected for r3 with 4000-tick headroom and exact-soil observation at ready feedback. Preserve r2 failure world and evidence under `build/gametest-botany-chemical-mutation`, including `logs/latest.log` and `logs/debug.log`.

M2 candidate `botany-chemical-mutation-20261014-r3` reached `ready_for_owner`; report: `build/agent-workflow/botany-genetics-sprint/reports/...r3-gate-validation.json`. Confirmed r3 results: build/jar passed with task rerun despite identical hash; focused JVM 45 passed; interaction/prediction 101/101 passed (filtered dispatch/reconciliation regressions, not native proof); native 28/28 selected of 851 passed with `botany,botany-genetics,botany-chemical-mutation`. Live Vestine tray poll, authenticated harvest/extract/replant, and setup-ready snapshot were verified. Successful disposable native worlds are lead-owned cleanup after evidence capture; retain useful failures. No full-suite/GPU/connected-client validation or owner acceptance is claimed. The M2 result is a historical snapshot and does not establish whole-sprint readiness after M3 changes; the full sprint remains open.

## Pending central checks

For any later M2 rerun, the integration lead must use a fresh disposable owned world at the M2-r3-specific `build/gametest-botany-chemical-mutation-r3`; fixtures on an ACTIVE lifecycle world can enter `RECOVERY_REQUIRED` before gameplay. Preserve failed artifacts; remove successful disposable runs only after evidence capture. Generic interaction/prediction readiness selectors are dispatch/reconciliation regression coverage, not proof of live growth or native admission; registered native tests cover changed behavior. Central gate owns reruns. No full suite is requested for narrow M2.

Manual spot-check (not automated evidence): `/ms14 botany_fixture`, wait about 3 seconds, inspect `/ms14 botany_fixture status` for the chemical-mutation label and acquired IDs, then harvest, extract, and replant. This is not a claim about an ordinary chemical scanner or asset delivery.

## Boundary

M2 is not complete 27-mutation runtime, the full 71-entry catalog, pollen, full-suite validation, visual/connected-client acceptance, or owner acceptance. Fixture labels pending/mutated real source chemistry; examine is read-only effective-profile presentation. M3A changes are underway and mean r3 is a historical M2 snapshot, not whole-sprint readiness.
