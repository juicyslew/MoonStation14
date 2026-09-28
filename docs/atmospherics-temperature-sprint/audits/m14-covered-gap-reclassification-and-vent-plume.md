# M14 — covered-gap reclassification and vent plume

> **Superseded for current exterior-reclassification status by [M15](m15-local-exterior-proof-and-large-room-work.md).** In particular, the bounded retry of at most 256 recent active boundaries described below failed the user's subsequent smoke and must not be treated as a working fix. This audit remains historical evidence for the earlier report and implementation attempt.

This audit records the reported manual smoke and the code-level causes and limits relevant to covered exterior gaps and gas escaping through them. It is not a claim of successful game-smoke validation, unconditional gap classification, or SS14 parity.

## Reported manual smoke

The user reported the following observations:

- Initial producer-room filling worked, and an initial door breach vented gas.
- During continued emission, gas appeared only on the producer tile, with no visible intermediate plume.
- A later covered gap was UNKNOWN and the room refilled. Replacing the producer invalidated exterior ownership; a subsequent analyzer inspection queued the gap and restored venting.

These are user-reported observations, not a separately reproduced or validated game run.

## Code-level causes and intended behavior

- Block events globally clear transient exterior ownership. Before this change, invalidation did not requeue nearby gas-active exterior boundaries, so a covered gap could lose its transient exterior ownership and stay UNKNOWN until a later inspection or other ownership seed reached it.
- The M14 implementation attempt requeued at most 256 recent gas-active exterior boundary seeds on invalidation, ahead of ownership seeds, while clearing cached exterior state fail-closed. A due tick processed at most 384 ownership probes and one ownership job. The user later reported that a door still lost exterior classification and only tester intervention restored it; therefore this 256-boundary retry is superseded and not accepted as a fix. The newer candidate and its limitations are documented in M15.
- Space-flow routing now retains at most 0.6 mol of inbound gas at an underfilled interior cell. This per-cell cap is a total inbound-retention bound, not a 15% retention applied at every hop; it allows a routed plume to continue through intermediate cells rather than consuming the entire packet at the first cell. It can delay flow through a newly empty corridor while cells fill to their caps. Normal room equalization remains unchanged: it routes the selected patch's full donor surplus.

## Plume visibility limits

A routed plume is not guaranteed to be visible. With default local gas of 0.1 mol, quantization and overlay thresholds may require more gas to appear. The visible gas overlays cover only plasma, tritium, water vapor, ammonia, and frezon; oxygen and nitrogen have no overlay. The latest focused pure-model test injects 2 mol of plasma per step for 20 steps into a 10-cell line and verifies `alphaByte >= 40` at x=1..9, positive export, bounded source backlog, conservation, and source-off convergence to epsilon. This verifies sustained upstream plasma transport and overlay-level values in the pure model only; it is not in-game visual acceptance and says nothing about other gases or actual rendering/runtime setup.

## Validation and remaining gates

- After the 0.6 mol per-cell cap change, the coordinator reran `.\gradlew.bat test --rerun-tasks --no-daemon` and `.\gradlew.bat build --no-daemon`; both reported `BUILD SUCCESSFUL`. The test task compiled GameTest sources (`compileGametestJava`), but no server GameTests were executed.
- The user-reported smoke remains an unresolved owner/runtime gate; this documentation task did not run the game or independently verify the report. Build evidence reflects the coordinator's run and may be invalidated by concurrent changes from other agents.
- Non-active or evicted boundaries can remain UNKNOWN until sampled. Reclassification is bounded and best-effort, not an immediate or universal guarantee. Do not claim that gaps are never unclassified, that the game smoke passed, that exact SS14 behavior was achieved, or that performance was validated.
