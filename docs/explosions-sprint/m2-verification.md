# Explosions sprint — M2 verification

## Result and scope

M2 has focused test-only GameTest evidence. This is not a pass of the entire GameTest suite and is not gameplay acceptance. The coordinator reports that `./gradlew.bat compileJava compileGameTestJava test --no-daemon` completed with `BUILD SUCCESSFUL` in 37 seconds. This confirms those Gradle tasks completed; it does not establish a whole GameTest pass. A JUnit test count is not reported here.

Two fresh isolated GameTest runs were performed:

| Run directory | Result |
|---|---|
| `build/gametest-explosion-final-20260930-01` | 359 tests completed; one required failure: `farplayercannotdespawnboundunboundorcorpse`; nearest FakePlayer `swept-parallel`. None of the six new explosion GameTests failed. |
| `build/gametest-explosion-final-20260930-02` | Same: 359 completed, the same single required failure and nearest FakePlayer, and none of the six new explosion tests failed. |

The six new tests are three in `ExplosionGameTests`, two in `ExplosionDownstreamGameTests`, and one in `ExplosionAtmosphereGameTests`. Thus the evidence supports those focused fixtures passing in both runs, not an all-suite result. The unrelated failure remains unresolved and must not be hidden by describing either run as successful overall.

Earlier iteration history is relevant: an initial shared run completed 356 tests with 15 failures, including the zombie fixture, `newworldmarkercompletesandbuildstiledstationinoverworld`, and 13 atmosphere-related failures. The failure messages do not establish stale global configuration as their cause. Subsequent isolated/iterative fixture runs also exposed fixture failures, which were corrected before the final two fresh isolated runs. Those failures are part of the test history, not evidence to discard; the final outcome above is limited to the two fresh isolated runs.

## Behaviors exercised by focused fixtures

- Structural floor, wall, and ceiling mutation follows the shared fixture breach rule.
- Cable host cleanup is exercised on destruction.
- Typed entity damage is applied once across adjacent reached blast cells.
- Vanilla/non-mod fixture targets, an unknown prototype ID, and an out-of-height source fail closed in GameTest. Unloaded-source boundary behavior has pure/unit test coverage, not real GameTest proof.
- Puddle support loss exercises relocation to prepared landing support; breached wall changes the subsequent speech-occlusion predicate/outcome.
- With the explicitly enabled global atmosphere fixture, breach notifies atmosphere topology and verifies gas drain/restore behavior. This is not evidence for a production default-on setting or permission policy.

## Explicit limits and open gates

These are trusted local test fixtures, not connected-client tests or approval of any production trigger. They do not establish client/network behavior, claims or world-protection integration, a permanent immunity policy, gameplay balance/calibration or SS14 parity, production trigger safety, or performance/worst-tick behavior. Fixture-only block classification is not permanent immunity or ownership policy. The limited known-mod-station fixture whitelist and values documented in [M0](m0-reference-and-gates.md) remain provisional. Vanilla remains deferred. Owner decisions and other gates in M0 and the [draft](draft.md) remain unresolved, including protection/admission hooks, numeric 3D tuning, damage balance/eligibility, atmosphere permission, and representative performance review.

No commits were made as part of recording this verification.
