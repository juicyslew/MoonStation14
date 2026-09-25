# M4 Slip-Triggered Touch Audit

**Milestone result: M4 implementation and server GameTest stage are in progress; sprint acceptance is not complete.** The latest required server GameTest suite passes 99/99, including the bounded reactive Touch cases described here. The required actual connected-player caustic-damage proof remains open; FakePlayer immunity means that test fixture cannot establish it. Do not waive player damage or report full acceptance until the owner completes the manual player test (or an equivalent real-player harness proves it).

**Evidence reviewed:** local `ReactiveTouchSystem`, `ReagentAttachment.splitUnits` / shared `ReagentUnits.split`, `ReagentSchemaAudit`, `PuddleReactiveTouchGameTests`, `PuddleSlipGameTests`, pinned SS14 `Content.Server/Fluids/EntitySystems/PuddleSystem.cs:245-268`, `ReactiveSystem.cs`, and `Solution.SplitSolution`; latest `build/gametest-run/logs/latest.log` (2026-09-24 03:04:24). No build, test, client launch, or code changes were performed for this documentation audit.

## Pinned behavior and local safety boundary

- Pinned `PuddleSystem.cs:245-268` checks that the target has Reactive and is not already sliding at the `SlipEvent` observation point, then rolls the 50% chance, resolves the source solution, and splits 15% before iterating per-reagent Touch effects. The split happens even when no reagent's reactive group/method matches or no effect is ultimately dispatched. `ReactiveSystem.cs` gates each reagent's effects by the target's reactive group and the reagent's method; a method/group mismatch is therefore **not** a zero-dose gate in pinned behavior. Do not claim otherwise in root instructions or treat this compatibility point as an acceptance waiver.
- The local implementation follows that division: target-level Touch eligibility, not-sliding, chance, and source validity precede the split; group/method matching gates which per-reagent effects are dispatched. Local hardening additionally preflights invalid/unknown reagent prototypes and unsupported effect payloads before mutation, returning with no dose consumed. This is an intentional local safety policy, not pinned behavior for ordinary group/method mismatch.
- Both upstream and local execution split before effect callbacks. The operation is not atomic across solution mutation and callbacks: a callback failure after split does not restore dose, and must not trigger dose replay. Local dispatch reports/catches effect failures after mutation; it does not claim rollback.
- `ReagentAttachment.splitUnits` reuses the generic `ReagentUnits.split` implementation used by other reagent operations. It is not a Touch-specific splitter. The request uses `floor(totalCents * 0.15 + 0.00001f)`, matching the pinned `FixedPoint2` signed epsilon before truncation for this positive request. Representative tested portions conserve cents; this audit does not assert bit-for-bit per-reagent identity across every possible pool/order.
- Production chance uses the server level RNG by default. Deterministic GameTests scope a test RNG to one level/source/target dispatch and remove it during cleanup; this does not replace or alter the production RNG.

## Implemented and tested behavior

- `ReactiveTouchSystem` listens to the admitted `SlipEvent`, rejects events already sliding at that point, checks live server target/source state and target reactive Touch capability, applies the default 50% chance, snapshots and validates the current solution, computes the 15% request, preflights supported payloads, splits via the shared helper, mutates/synchronizes via `MS14Provider`, then dispatches matched Touch effects scaled to each actual reagent portion. Same-source/target/tick duplicate dispatch is bounded.
- Real movement through puddle blocks is exercised for a `FakePlayer` fixture and a real Minecraft Villager, both bound to the same `moonstation14:human` policy, against independent mixed sources containing 16 Space Lube + 4 polytrinic acid (20 total). Deterministic accepted and rejected 50% branches are exercised for both fixture types.
- Accepted branch removes 3.00 total: 2.40 Space Lube and 0.60 polytrinic acid, retaining 17.00 and conserving all 20.00 units across source remainder and consumed split. The Villager receives the Touch HealthChange at actual scale and records 0.30 caustic. Rejected chance leaves all source units unchanged.
- A second actual slip event while already sliding does not consume the second source or apply another caustic reaction. Repeated movement in the same contact does not duplicate slip/Touch. Unsupported flagged `ModifyKnockdown` payload is rejected in preflight with the source pool preserved.
- `ReagentSchemaAudit` validates the canonical lowercase methods `touch`, `injection`, and `ingestion`, supports future reactive groups, and rejects unknown/case-drifted, empty, and duplicate method lists with reagent/path diagnostics. Reactive group names are not globally frozen by this audit.
- Untouched/future group reference behavior remains intentionally tolerant: a valid group with no target match simply has no dispatched effect after the upstream split. Invalid prototype/preflight unsupported payload behavior is stricter and retains the pool before split.

## Player-damage limitation and open acceptance gate

- NeoForge `FakePlayer` is immune to damage by override; this was independently verified from bytecode and is also asserted by the direct typed-effect control GameTest. The puddle test proves its split occurs, but cannot prove connected-player caustic damage. It would be inaccurate to report that the player fixture received damage.
- The real Villager proves the scaled caustic effect, but does not substitute for the player acceptance requirement. An attempted ordinary `Player` GameTest did not produce an actual slip and was removed; it is not evidence for or against player behavior.
- Owner agreed to a manual real-player test if the harness proves hard to build. That test remains outstanding. Preserve the acceptance requirement: demonstrate a real connected player actually slips and receives the scaled caustic Touch damage, or add an equivalent validated real-player harness. Do not declare the sprint fully accepted until then.
- M2 limitations remain: live packet action denial and actual Villager AI navigation pause have not been proven. M3 sliding is a volatile runtime projection, not durable across reload. Physical prone/crawl is not implemented or claimed. Future character-family coverage review date remains pending owner assignment.

## Validation evidence and decision

The latest observed server log reports **99 tests started**, `99 GAME TESTS COMPLETE`, and **All 99 required tests passed**. This is required server GameTest evidence only, not a runtime client test or manual player session. Worker-reported validation commands (not rerun for this documentation task):

```powershell
.\gradlew.bat test ... build --no-daemon
.\gradlew.bat runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run
```

The worker also reported Python validation of 603 JSON files; this audit did not rerun that check. The approved sprint is in progress: M4 is implemented and passes the available server suite, but connected-player damage, remaining M2/M5 handoff evidence, physical prone/crawl decision, and dated future character coverage review remain open. No runtime client launch or live-player result is claimed.
