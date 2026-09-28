# M4d power integration GameTests

## Added coverage

`PowerDeviceGameTests` uses registered in-world blocks and the GameTest server. In addition to checking actual registered lamp light emission (0 off, 14 on) and cable/floor persistence, it places cable records on station-floor hosts before toggling the same hosts' tile finish between exposed and concealed states; this checks property-state changes preserve host identity. It also builds the complete live source → HV → substation → MV → APC → APC → lamp path on full-sturdy wall hosts. It asserts the actual lamp `LIT` blockstate after delayed ticks for chunk graph refresh and runtime solves, then tests loss of HV with the precharged APC battery, breaker-open isolation, empty-battery/source-off darkness, and wrong-tier MV isolation. The cables are placed on their exact specified wall faces, with tiers ending/starting at the device ports. These tests complement the pure `PowerLiveLoop`/graph tests; they do not replace them.

The lamp block now declares state-dependent light emission. Previously `PowerRuntime` could synchronize `LIT` while the block emitted no light.

## Not yet covered / execution

The live chain and stated failure scenarios are covered. A chunk-seam integration fixture remains follow-up coverage. Existing pure service tests exercise additional solver portions, but are not substitutes for the live fixture.

## Automated server run

The coordinator initially ran ` .\gradlew.bat runGameTestServer -Pms14GameTestDir=build/power-gametest-run --no-daemon`; three power GameTests failed: the spool-refund fixture, source-to-lamp game-time scheduling, and a stale host-battery fixture. Those fixtures were fixed and the server run was repeated. The latest `build/power-gametest-run/logs/latest.log` reports `153 GAME TESTS COMPLETE` and `6 required tests failed`. The six failures are exclusively atmospherics: `openingtransfersgasdirectionallyfromhightolowpressureroom`, `closeddoortoggleautomaticallyopensandclosesexteriorroute`, `coveredexteriorsealingandbreachrespectownership`, `equalpressureadjacentroomsexchangespeciesthroughnewopening`, `fullwallopeningdrainsmorethanoneblockopening`, and `longhallwayservicediffusestritiumacrossall124cells`. No power failures appear in the latest log; the power GameTests pass in this automated run.

This was an existing GameTest world, not a fresh-world placement check. Manual client behavior and the new-world station-generation/placement path remain unverified; passing power GameTests does not establish fresh-world station placement acceptance.

No manual client or in-game testing was performed; the reported server execution is automated GameTest coverage only.
