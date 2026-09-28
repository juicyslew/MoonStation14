# M2a: Ghost Mob Harness foundation audit

**Status: the registered ghost entity and its dedicated-server GameTest are verified; this is still only an integration gate, not M2 completion.** The [sprint instructions](../Instructions.md), [M1 audit](m1-pure-mind-mob-harness-registry.md), and [M0c contract](m0c-mind-and-mob-harness-contract.md) remain the architecture and scope references. No player input, connected control, NPC transfer, or command is implemented or claimed here.

## Added foundation

The recently added files are:

- `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/ghost/GhostMobHarnessEntity.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/ghost/GhostMobHarnessRegistration.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/ghost/client/GhostMobHarnessRenderer.java`
- `src/gametest/java/com/juicyslew/moonstation14/gametest/GhostMobHarnessGameTests.java`

The entity is a separate registered `Mob`, not a `ServerPlayer`. Its current movement policy sets no AI, no gravity, and `noPhysics`; the registered type is marked `noSave`. Registration uses a standalone NeoForge `RegisterEvent` listener, with attribute creation, and renderer registration is client-only. The GameTest verifies that the registered ghost and a Villager can be spawned on the dedicated server; it does not establish connected-player runtime behavior.

The renderer intentionally supplies no model: the inherited `EntityRenderer` draws no model, and there is no ghost texture asset. Consequently, the ghost is an invisible placeholder, not an established visible in-world ghost. It is registered and GameTest-spawnable, but is not remotely player-controlled. There is no input/session integration, NPC transfer, or command.

## Fidelity boundary

The pinned SS14 observer prototype has a visible sprite and collision limited by its `GhostImpassable` rule. Local Minecraft `noPhysics` bypasses all block and entity collisions, including walls; it is therefore an explicit mismatch, not SS14 parity. This foundation does not prove connected ghost movement, collision-resolved movement, input ownership, or an active Mind-to-ghost harness binding.

## Validation record and stop gate

Initial attempts encountered transient Java compilation errors in concurrent atmospherics edits to `ModBlocks.java`, referencing missing `AtmosphereTestDeviceBlock.Device` members. After those errors, the ghost GameTest source's incomparable-class check was corrected by widening the comparison to `Entity`. The dedicated GameTest server then completed **117/117 tests**; the run log records completion at 2026-09-25 01:47:10–01:47:13 (lines 36, 240, and 241).

An initial full JUnit run executed 500 tests and had one failure in `ServerClassloadingTest`, caused by the new client renderer import. That test was then updated to use an explicit single-file allowlist and assert the renderer's `Dist.CLIENT` annotation, without broadly skipping classloading checks. The focused `ServerClassloadingTest`, full JUnit suite, and build were rerun and all passed:

```powershell
& ".\gradlew.bat" test --tests "com.juicyslew.moonstation14.ms14.prototype.ServerClassloadingTest" --no-daemon
& ".\gradlew.bat" test --rerun-tasks --no-daemon
& ".\gradlew.bat" build --no-daemon
```

The GameTest result is the most recent run after the ghost entity change. It was not rerun after the final `ServerClassloadingTest`-only correction; the focused classloading test, full JUnit suite, and build all passed afterward. These results do not establish connected-client behavior or a working client render hook. Continue to stop at this gate until authenticated ghost input, server-authoritative collision movement, visibility, and ownership are proven in a connected-client test. In particular, `noPhysics` bypasses all block and entity collisions, unlike the SS14 ghost's `GhostImpassable` behavior. NPC transfer follows only after ghost control is proven. This is not M2 acceptance or sprint completion.

## Latest harness and networking update

Two additional `GhostMobHarnessGameTests` now check that the ghost retains its position for six real ticks with no input and that it passes through an ordinary stone wall. This is an explicit local limitation: SS14 `GhostImpassable` behavior is **not** supported. The latest dedicated GameTest run, performed after network registration, discovered and completed **119/119 tests** successfully. This updates the earlier 117/117 result above; the 117 result and its validation context are retained as historical evidence rather than overwritten. The current run output is at `build/gametest-run/logs/latest.log`: the log records that 119 tests were running at 2026-09-25 02:06:44.542 (line 35), followed by `119 GAME TESTS COMPLETE` and `All 119 required tests passed` at 02:06:47.673 (lines 250–251).

The latest networking foundation adds `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/network/GhostControlPayloads.java` with validated Begin, Ready, Commit, Intent, and Stop payloads. The schema carries no client position or body target. `GhostControlNetworking.java` registers the payloads as a standalone NeoForge network seam, dispatches on the MAIN thread, checks for a connected non-fake `ServerPlayer`, and currently installs no packet handlers. Focused codec and networking JUnit checks passed, as did `gradlew build --no-daemon`; the dedicated GameTest run then passed 119/119. A full JUnit `--rerun-tasks` run was **not** performed after network registration.

These payload registrations do not implement control: there is still no server/client ghost input handler, no executed camera handshake, no enabled gameplay command, no owner-connected ghost control, and no NPC transfer. The ghost remains an invisible placeholder, and its `noPhysics` setting remains an all-wall bypass. This is interim validation only, not full SS14 parity or M2 acceptance. The next gate is the server-owned Mind lifecycle/controller plus client input sampler and Ready acknowledgement, followed by a real connected player test behind the separately default-off opt-in gate. Coordinate before shared edits: concurrent atmospherics work overlaps `Config`, `MoonStation14`, and `ModDataAttachments`; do not touch those overlapping files for this gate.
