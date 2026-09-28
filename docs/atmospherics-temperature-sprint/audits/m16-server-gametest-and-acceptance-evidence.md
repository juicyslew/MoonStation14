# M16 — server GameTest and acceptance evidence

## Status and evidence

The coordinator reports that, after the ownership chunk-lifecycle scoping change and isolation of atmosphere GameTest fixtures, these commands completed successfully:

```powershell
.\gradlew.bat test --rerun-tasks --no-daemon
.\gradlew.bat build --no-daemon
.\gradlew.bat runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-atmos-fixture-check6
```

Both the full test rerun and build returned `BUILD SUCCESSFUL`; `compileGametestJava` executed during the test rerun. The authorized dedicated server GameTest run returned `BUILD SUCCESSFUL`. Independent inspection of `build/gametest-atmos-fixture-check6/logs/latest.log` confirms 154 tests started (line 68), `154 GAME TESTS COMPLETE IN 5.899 s` (line 775), and `All 154 required tests passed :)` (line 776). This is actual server GameTest execution, not merely GameTest compilation. It is not a connected authenticated client or manual visual smoke run. This documentation-only update did not rerun these commands.

The selected isolated atmosphere fixtures ran in the valid empty 36x5x8 GameTest template and include the 124-cell TRITIUM hallway, normal sealed room, covered breach, door open/close/reopen without analyzer rescue, a wall with a six-block opening compared to a one-block opening, and M11 equal-pressure gas mixing plus high-to-low pressure transfers. The whole server batch had 154 passing required tests; that total includes tests outside atmospherics and must not be represented as 154 atmosphere tests.

## Scope and non-claims

- This evidence verifies only the assertions in the tests actually included in that isolated batch. It does not establish all M1–M12 gates, full world integration acceptance, exact SS14 behavior/parity, or sprint completion.
- It is not a connected-client or owner manual/visual smoke, a real saved-world/restart test, or proof of disabled-world startup/restart semantics. Default-off M0 behavior has only its existing pure checks; the real disabled-world/restart scenario was not executed.
- The run is not a 20-player benchmark, a profile of 800-cell tick cost, or any performance guarantee.
- Real persistent-world chunk unload/reload behavior remains unverified. The production ownership change scopes cancellation/requeue to search/claim plans whose probed chunks overlap the chunk load/unload; unrelated chunk lifecycle events do not restart those plans. Focused world unit tests passed, but this isolated run does not prove persistence across an actual unload/reload.
- Topology variants/custom chunks, restart material behavior, and the full remaining M2 matrix are unresolved. M1 remains partial/open; do not blanket-close M0–M12.
- An automatic server breach comparison passed, but the owner's last report of a door losing exterior classification predates the exterior-proof change and has not been retested afterward. This automated pass is not confirmation of that owner-reported issue being fixed.
- The intermittent finite-zero hallway row observation remains explicitly deferred at the user's request. The 124-cell hallway GameTest pass neither reproduces, diagnoses, nor fixes that report. No production fix is claimed.
- Normal Monstermos remains the SS14-inspired kernel the user chose to retain. Cached average-and-nudge remains deferred; no full SS14 parity claim is made.
- Owner checks remain outstanding for M3 device effects, M6 MoonSky appearance, M8 visuals beyond the owner's reported tritium line, and M9 in-world temperature effects. Respiration and gas-composition harm are not implemented by this evidence.
- M4 still requires both the 20-player benchmark and 800-cell tick profiler. Other unresolved risks include large connected regions over the current patch limit, exterior witness limits, topology-edit matter behavior, mapped exterior policy, and boundary-ledger accounting/persistence.

See [M15](m15-local-exterior-proof-and-large-room-work.md) for user observations and the deferred hallway report, and [Instructions](../Instructions.md) plus [current handoff](../current-handoff.md) for current acceptance gates. Earlier statements in M15 that its service GameTest had not run are historical and superseded only as to execution status by this audit.
