# M12 gas visibility range revert

**Status: source/tests reviewed; visual acceptance OPEN.** The user rejected M12's widened 40 mol-per-block saturation and requested the pre-M12 range. The restored defaults are 2/2.8/4.8 mol for maximum alpha, with the three-plane/four-block column opacity cap at 0.85. Current source and focused tests confirm the restoration. No code or art was changed for this documentation update. After restoring the SS14 opacity values, the coordinator reported ` .\gradlew.bat test --rerun-tasks --no-daemon` as `BUILD SUCCESSFUL` (`compileGametestJava` executed; GameTests were NOT RUN) and ` .\gradlew.bat build --no-daemon` as `BUILD SUCCESSFUL` and up-to-date. No client/game was launched, no in-game gas visual/manual check was performed, and no 20-player test was performed. M0–M12 acceptance remains OPEN.

## Current policy

`GasVisibility` uses five overlays only: plasma, tritium, water vapor, ammonia and frezon. Nitrous oxide and other gases have no overlay. The SS14 reference amount scale is 2.5 m³; thresholds and maxima are volume-scaled to Minecraft's 1 m³ cell:

| Overlay gases | Threshold | Maximum-alpha amount |
| --- | ---: | ---: |
| Plasma, tritium, water vapor | 0.1 mol | 2 mol |
| Ammonia | 0.8 mol | 2.8 mol |
| Frezon | 0.24 mol | 4.8 mol |

The original rounded 20-level quantization is in effect. Amounts at/below threshold return alpha 0, and a just-over-threshold amount may also quantize to alpha 0; consequently an overlay is emitted only when quantized alpha is nonzero. Three crossed planes are composed within a four-block column with a maximum column opacity of 0.85. The maximum quad radius is 0.48 block, inset from cell boundaries. Restoring the pre-M12 opacity values does not change the wall or non-snapshot fixes; they remain unaffected.

Visual selection remains nearest-first, with tritium preference only among equal-distance candidates. The visual sync protocol remains unchanged: non-reset deltas do not clear state; staged reset snapshots publish with newer deltas replayed, and bounded change-tracking overflow triggers resynchronization. Renderer/network/runtime behavior has not been validated in a launched game. No SS14 PNG or sprite was copied; the existing procedural quad and UI-color tint approximation remains, with no art change requested or made.

This visibility policy is not a chemistry, harm, safety, or ignition indicator. Tritium visibility currently causes no chemical harm, and fire/reactions remain unimplemented.

## Owner visual follow-up

Compare the prior widened-range appearance with the restored range specifically for **tritium cloud readability**, then check that a thicker visible gas cloud is still readable and does not become opaque. This is a visual review only; do not make an art change as part of that check. No result is claimed until an owner performs it in-game.

## Evidence

Reviewed `src/main/java/com/juicyslew/moonstation14/ms14/atmos/visual/GasVisibility.java` and `src/test/java/com/juicyslew/moonstation14/ms14/atmos/visual/GasVisibilityTest.java`. The tests cover the reference-scaled thresholds/maxima, threshold-adjacent zero-alpha quantization, 20 levels, plane composition, inset radius and five-gas set. Coordinator validation reported `BUILD SUCCESSFUL` for `test --rerun-tasks --no-daemon` (`compileGametestJava` executed; GameTests NOT RUN) and `build --no-daemon` (successful/up-to-date); this docs-only task did not run those commands. For cadence, plume, vacuum and thermal findings preserved from M12, see [M12 cadence/plumes/opacity/thermal audit](m12-cadence-plumes-opacity-and-thermal.md); that audit's 40 mol visual decision is historical and superseded.
