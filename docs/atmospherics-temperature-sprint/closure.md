# Atmospherics and Temperature Sprint Closure — 2026-09-28

**Authoritative disposition:** The owner has declared this bounded atmospherics experiment **complete and closed now**. The sprint delivered a default-off, opt-in Minecraft world-atmosphere foundation, not blanket proof that its historical M0–M12 acceptance gates passed. This closure is an owner decision to stop this sprint at its bounded delivered scope; it is not full SS14 acceptance, a claim that every reported behavior is fixed, or authorization for further implementation. The older milestone labels and audits are retained as historical scope/evidence. No code change is made or requested by this closure.

## Delivered bounded experiment

- Opt-in world atmosphere is controlled by `enableAtmospherics`, a COMMON setting defaulting to `false` and sampled at server start. The simulated unit is a fixed 1 m³ cell. Service due cadence is 10 Hz at 20 TPS; that is a service cadence, not a guarantee that each region is solved ten times per second.
- Gas mixtures track species and thermal energy. The delivered source/sink/heater/cooler devices and nine single-gas generators coexist with the analyzer; five gases have client overlay visuals. The implementation includes local exchange, LINDA species mixing, bounded normal Monstermos equalization, distinct space flow, and an immutable-boundary fallback.
- Normal-room Monstermos currently routes the selected bounded patch's full donor surplus toward average total moles, with species and energy carried along packets. LINDA remains the species mixer. Space flow is distinct. This is SS14-inspired behavior, not a full SS14 solver/parity claim.
- Exterior ownership uses indexed exterior proof and topology-aware door handling, while chunk lifecycle work is scoped to affected ownership searches/claim plans. The implementation has finite-cell claims and bounded service work; this does not establish accepted mapping policy or persistent-world lifecycle/restart behavior.
- Before the final ownership change, the owner reported that the tritium line works. This is an owner report, not a post-final-ownership-change door smoke or broad runtime acceptance.

## Validation evidence

The coordinator reported a full unit-test rerun and build as successful after the ownership chunk-lifecycle scoping change and atmosphere fixture isolation:

```powershell
.\gradlew.bat test --rerun-tasks --no-daemon
.\gradlew.bat build --no-daemon
```

Both returned `BUILD SUCCESSFUL`; `compileGametestJava` executed. The isolated dedicated GameTest server command was:

```powershell
.\gradlew.bat runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-atmos-fixture-check6
```

It returned `BUILD SUCCESSFUL`. `build/gametest-atmos-fixture-check6/logs/latest.log` records 154 tests started at line 68, `154 GAME TESTS COMPLETE IN 5.899 s` at line 775, and `All 154 required tests passed :)` at line 776. These are 154 required server GameTests across the batch, **not 154 atmospherics tests**. The selected atmosphere fixtures include the 124-cell tritium hallway, room/breach and door open/close/reopen cases, opening-size comparison, and equal-pressure mixing/high-to-low pressure scenarios. See [M16 evidence](audits/m16-server-gametest-and-acceptance-evidence.md) for scope. This documentation change did not rerun commands.

## Explicit boundaries and deferred scope

Closure is deliberately bounded; the following are not established as passed:

- No blanket M0–M12 acceptance, full SS14 parity, connected-client acceptance, comprehensive transactionality, or post-proof owner door smoke is claimed.
- A limited prior owner report says the tritium vent plume line appeared to work; it predates the final proof change. No post-fix owner visual acceptance or full connected-client integration smoke is evidenced. No 20-player measurement or broader performance benchmark exists, including an 800-write-per-tick CPU benchmark.
- No real persistent-world chunk unload/reload or restart proof exists. Indexed exterior proof has not been accepted as a migration/policy decision; mapped exterior migration, topology-edit mass displacement/conservation policy, and persistent boundary-ledger/accounting policy remain open for any future work.
- Respiration, gas-composition chemistry/physiology, fire/reactions, space wind, and physical pressure effects are not delivered. Temperature behavior remains bounded and does not activate general gas-composition effects.
- The intermittent owner-reported FINITE cells at exactly zero moles in two of six rows remain **explicitly deferred at the owner's request**. The hallway GameTest neither reproduces, diagnoses, nor fixes that observation; do not describe it as fixed.
- The owner-reported door-gap UNKNOWN/exterior-classification issue predates the final ownership change and has no post-proof owner smoke. The automated breach tests are not confirmation that this specific owner observation is fixed.

These items do not reopen or block this owner-directed bounded closure. They are follow-up only if the owner chooses to return to the topic. The cached connected-component **average-and-nudge** alternative was considered but not selected; retain current normal Monstermos. **Space wind** is also future-only, not current behavior. Any extension requires separate owner authorization and its own evidence. See [Instructions](Instructions.md) for the preserved historical milestone record, [current handoff](current-handoff.md) for concise current status, and the [M15 audit](audits/m15-local-exterior-proof-and-large-room-work.md) for the deferred finite-zero report.
