# M13 — Restored Monstermos room routing and breach flow

**Status: SOURCE/PURE-TEST REVIEW; VALIDATION PASSED; ACCEPTANCE OPEN.** M13 documents the restoration of normal Monstermos donor-surplus routing and the current Monstermos space-flow route. The `MonstermosEqualization` and `MonstermosSpaceFlow` sources and focused tests were reviewed alongside `AtmosphereService` and targeted SS14 sources. The coordinator reports the following after the latest normal Monstermos full-surplus reversion, propagated space-flow routing, and scenario-test correction:

- `.\gradlew.bat test --rerun-tasks --no-daemon` returned `BUILD SUCCESSFUL`; `compileGametestJava` executed, but NO server GameTests were run.
- `.\gradlew.bat build --no-daemon` returned `BUILD SUCCESSFUL` and was up-to-date.

This documentation task ran no tests/build. No manual server/client launch, 20-player metrics, or full SS14 parity acceptance is claimed.

## 1. Normal finite-room Monstermos

- A selected finite patch is processed as one connected graph with at most 800 applied cells and 8,000 discovery candidates. The service advances only one Monstermos discovery/equalization job per server level at a time, and discovery is capped at 384 probes per due service step. Other ownership, LINDA, retry, and scheduler work share due-step budgets.
- The normal kernel calculates the total-moles average over the selected patch, then routes the donor surplus needed by selected takers along deterministic face-connected paths. There is no 0.15 donor-surplus cap. It does not artificially retain half a packet in intermediate cells: species mixture and enthalpy travel with the packet through each intermediate tile. The routes are bounded by graph-search/transfer-operation ceilings; a bounded invocation can return partial, conservative progress and continue later.
- Closed finite-route transfers conserve each species and thermal energy. Equal-total-moles pure O2/N2 mixtures are not mixed by this pressure/total-moles equalizer; LINDA remains the species mixer. This is not exact SS14 behavior: the Minecraft kernel uses selected-patch graph paths and bounded work, rather than SS14's tile-level giver/taker equalization algorithm and map/grid model.
- This supersedes the earlier “Gradual room filling” behavior: no per-invocation 15% donor-surplus clamp and no 50% packet retention at the first intermediate cell. It does not imply instant full-room availability outside the selected patch or unbounded solver work.

## 2. Verified-opening Monstermos space flow

- Space flow uses a breadth-first search over the finite face-connected graph, seeded by finite boundary cells adjacent to explicitly supplied verified exterior openings. Missing/unknown adjacency is closed, never inferred to be space. The service chooses the space-flow kernel only when it has verified exterior faces.
- Routing proceeds from interior cells toward their selected nearer boundary parent. Each finite tile requests `0.15 × ORIGINAL tile inventory + accumulated child demand`; the accumulated packet is carried toward the opening, and the 0.15 fraction is **not** applied again to the same child packet at each hop. A boundary cell requests its own `0.15 × ORIGINAL boundary inventory + inbound child demand`.
- Actual boundary export is released from the boundary cell's **current** gas mixture and current pressure. Above the low-pressure spacing-decay regime, the release amount is capped by the request and the moles corresponding to half the current boundary-to-exterior pressure difference, with a 500 kPa maximum pressure cap. Below the 10 kPa release threshold, the local spacing-decay rule permits the pressure-derived budget to rise to the boundary's current pressure instead. The local calculation is an approximation, not SS14 pressure-wind semantics. There is no exact SS14 directional pressure wind, force/impulse, or minimum-two-moles inventory cleanup floor.
- Species and thermal energy exported are derived from the actual before/after finite boundary mixture and added to the transient per-level export ledger. Pure tests exercise conservation, deterministic routes, multiple openings, low inventory, and budget rejection.

## 3. Distinct immutable-boundary fallback

If a Monstermos job is unavailable, the immutable finite-to-vacuum fallback does **not** run the above BFS or include inbound child demand. It uses only the boundary cell's current pressure: above the fallback threshold it removes enough inventory for half the pressure difference; at/below the threshold it removes half the boundary cell's gas per step, snapping only at the 1e-6 mol residual epsilon. It retains no SS14 two-mol cleanup floor. Do not describe this fallback as the Monstermos room-route release calculation.

## 4. Cadence, retained excited-group deviation, and unrelated current rules

`AtmosphereService.TICK_CADENCE` is two game ticks (10 Hz when the server is at 20 TPS). This is the service due rate only; it does not guarantee every region or patch receives a solve 10 times per second. One Monstermos discovery/equalization job per level advances at once; its 384-probe discovery budget, 800-cell patch bound, 8,000-candidate ceiling, ownership work, LINDA work, retries, and scheduler work affect actual per-region progress. These are work caps, not performance metrics. No 20-player measurements exist.

The excited-group near-uniform gas/temperature check remains intentionally. SS14's excited-group self-breakdown unconditionally averages the group on the fifth counted cycle; local M11 only commits that averaging when the tracked cells are already sufficiently uniform. This guard was retained after the earlier complaint about instant equal-pressure mixing, and is an explicit deviation. M13 restores the normal-room donor route; it does not remove this guard or claim unconditional excited-group parity.

The unrelated source/device and visual settings remain unchanged: producers/sinks are still capped at 2 mol per service step (20 mol/s nominal at 20 TPS), the heater remains 40,000 J per two-tick step (400,000 J/s nominal), and the M12 opacity reversion remains current. M13 makes no changes to them.

## 5. Source/test reference and acceptance limits

Reviewed Minecraft sources:

- `MonstermosEqualization.java` and `MonstermosEqualizationTest.java`.
- `MonstermosSpaceFlow.java` and `MonstermosSpaceFlowTest.java`.
- `AtmosphereService.java` for discovery/commit limits, kernel selection, cadence, and fallback distinction.

Reviewed SS14 checkout revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`:

- `Content.Server/Atmos/EntitySystems/AtmosphereSystem.Monstermos.cs` for finite total-moles equalization and space progression.
- `Content.Server/Atmos/EntitySystems/AtmosphereSystem.ExcitedGroup.cs` for unconditional combined-mixture group averaging.
- `Content.Server/Atmos/EntitySystems/AtmosphereSystem.Processing.cs` for staged solver scheduling.

This was a targeted source review, not a full SS14 audit. Focused pure tests were updated for the restored route behavior and the coordinator-reported full test rerun passed. Runtime topology/ownership/ledger integration, actual in-game breach behavior, measured patch tick cost, 20-player performance, and owner acceptance remain outstanding. Do not claim full SS14 parity or acceptance.
