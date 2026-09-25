# M1 Kernel and Open Gates

**Status: historical M1 kernel/validation snapshot; experimental runtime now exists, connected acceptance remains open.** The implementation inventory below records the isolated pure-kernel milestone, not the current complete source tree. A default-off experimental input/snapshot and motor path has since been implemented, but no authenticated connected-player run proves it operational. See the [experimental connected-smoke checklist](experimental-connected-smoke.md).

## Delivered

- `src/main/java/com/juicyslew/moonstation14/ms14/movement/CharacterMovementState.java` is immutable position, velocity, and grounded state; it has no actor/controller identity.
- `CharacterMovementCommand.java` validates finite axes in `[-1, 1]`, normalizes diagonal wish, and suppresses voluntary wish/jump when stunned.
- `CharacterMovementPolicy.java` provides the same pure `moonstation14:human` profile lookup for any host, with SS14-derived acceleration 20, ground friction 2.5 per second both with and without input, minimum friction speed 0.005, walk 2.5/s, and sprint 4.5/s. Strictly below the minimum friction speed, the motor skips friction damping; at equality, friction applies. It does not zero the small horizontal velocity.
- `CharacterMovementEnvironment.java`, `MovementVector.java`, and `MovementCollisionResolver.java` define validated tick/environment values and an injected collision contract. `CharacterMovementMotor.java` deterministically applies external impulse, grounded friction, voluntary acceleration, optional jump, gravity, requested displacement, and bounded resolver output; blocked axes lose velocity into the obstruction. Stun suppresses voluntary wish and jump, so effective wish is zero and grounded friction selects the no-input value. A step-up is accepted only when both requested and actual horizontal displacement are nonzero, in addition to the other bounds.
- `src/test/java/com/juicyslew/moonstation14/ms14/movement/CharacterMovementMotorTest.java` covers shared policy for player/Villager host examples, speed/acceleration, normalized wish, friction/gravity/impulse, stun, collision bounds, determinism/replay, invalid values, and jump/step cases. These are pure unit fixtures, not Minecraft actor or world tests; they provide no numeric three-puddle fixture.

The kernel accepts environment inputs and an abstract collision resolver; it does not supply Minecraft constants or establish parity with Minecraft travel. The player/Villager comparison proves profile/kernel equivalence in a unit fixture only, not runtime binding.

## Latest validation evidence

The following validation reflects the final M1 code and test-fixture state:

- `& ".\gradlew.bat" test --rerun-tasks --no-daemon` — `BUILD SUCCESSFUL` (36s), after the M1 change that preserves sub-0.005 horizontal momentum and the bounded GameTest fixture change. This is the full JUnit suite, not only the focused movement test. Full JUnit coverage and data-loaded isolated GameTest startup cover prototype/reagent reference semantics; no separate standalone semantic-reference audit was run.
- `& ".\gradlew.bat" build --no-daemon` — `BUILD SUCCESSFUL` (6s).
- During validation at 12:04:24, a 109-test isolated GameTest run had one failure in the existing `stunnedvillagerretainsinstalledimpulseonnaturalserverticks` test: the Villager remained at floor height with nonzero impulse while transiently reporting `onGround=false`. The existing fixture at `src/gametest/java/com/juicyslew/moonstation14/gametest/PuddleSlipGameTests.java:226-351` was narrowly stabilized to require feet to stay near the stone floor top on each of three observed ticks, remain inside the lane, and avoid a discontinuous jump. Stun, status, and velocity assertions were preserved; FakePlayer remains diagnostic-only. This fixture-only change made no runtime/motor behavior change. Record this intermediate failure rather than treating it as a passing run.
- After that stabilization, two `& ".\gradlew.bat" runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run` runs each passed 109/109. The latest `build/gametest-run/logs/latest.log` records 109 tests started at `2026-09-24 12:09:28` (line 35), `109 GAME TESTS COMPLETE` (line 224), and `All 109 required tests passed` at `12:09:31` (line 225). This is dedicated-server GameTest evidence, not a connected-client or client-prediction test; two consecutive passes are not proof of full determinism.
- A Python JSON syntax parse covered 603 files under `src/main/resources`.
- Semantic references were covered through JUnit and GameTest data-load; no separate standalone semantic-reference audit was run. Documentation link targets and whitespace were checked; `git diff --check` passed (with existing CRLF warnings).

These checks validate the repository test/build/data-loading state at the time they were run, not live movement authority or prediction/reconciliation. No authenticated connected player was tested. Do not infer connected movement success, safe mode handoff, a numeric bound, or client prediction success from the server GameTests or these builds.

## M1 snapshot limitations / current open gates

- The M1 kernel report itself did not establish actor adapters or a Minecraft collision adapter; later experimental runtime hooks now exist. A Villager AI adapter is still missing.
- Custom protocol, snapshot, and handoff code now exist, but their live connected-client behavior is unproved. Pending input replay is not implemented.
- Runtime mode ordering, vanilla fallback, teleport handoff, and movement behavior have not been authenticated-client tested. The vanilla LocalPlayer continues to send position packets during committed custom ownership, so lag-related correction/rubberbanding remains a risk.
- The frozen three-puddle fixture remains absent: there is no numeric lane traversal tick bound, exact lane dimensions, or completed frozen starting/input/slip/knockdown record. Do not infer or invent a bound from this kernel.
- The [single-owner handoff gates](../architecture/single-owner-handoff-gates.md) remain acceptance obligations; candidate code does not prove packet arrival/ordering or client mismatch behavior. No connected-client proof exists. See also the [M0 investigation](m0-authority-and-protocol-proof.md) as a historical source audit.
