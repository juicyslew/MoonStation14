# APC timed overload trip — sprint instructions

This is a new, documentation-only sprint handoff for an SS14-inspired APC overload breaker trip and truthful upstream-power passthrough. It is separate from the existing [APC machine UI sprint](../apc-machine-ui-sprint/Instructions.md): this document does not reopen, modify, or change the acceptance of that UI sprint. This handoff itself did not authorize implementation, prototype edits, UI changes, assets, sounds, station events, or ghost-haunt behavior; the owner subsequently authorized the separate server-side breaker-switch sound described in [audio-milestone.md](audio-milestone.md).

## Goal and scope

Implement a server-owned APC breaker protection state. Trip only when the APC's **actual metered delivered output** is strictly greater than **20,000 W continuously for more than 3 seconds**. The threshold is not a requested-load estimate, configured capacity, input power, solver demand, or a sum of input and output. A manual reclose clears the trip flag. Overload timing must reset when the breaker is open, the relevant graph/sample is unknown, the APC is unloaded, or output is at/below threshold.

Preserve upstream passthrough semantics in local bounded form: a known MV input can serve APC-tier output and charge storage; storage supplements actual demand; all source, conversion, storage, loss, and delivery accounting conserves energy exactly within the documented numeric precision. An open APC breaker may accept input for charging but must never supply APC-tier output or discharge storage. Demand alone must never cause a trip. Do not force-load chunks to find a source, APC, or load.

The existing local power solver cadence is 20 ticks. Keep protection sampling bounded per tick, and account for the fact that a 20-tick solver cadence cannot establish continuous overload at sub-second resolution. Choose/document a bounded sample policy that measures output observations rather than interpolating fictitious load; tests must cover sample timing, reset/unknown behavior, and the resulting trip-time tolerance. Do not turn a sampled interval with missing/unknown data into continuous overload.

### Explicit exclusions

- Station event scheduling, event prototypes, event announcements, or station-wide overload consequences.
- Ghost roles, ghost haunting, ghost interaction, or any haunt-related UX.
- Low-power flicker or low-power audio. Neither is authenticated here as a core upstream effect; defer both pending separate design. This exclusion does not apply to the separately authorized server-side APC breaker-switch sound on accepted manual changes and overload trips: SS14 `machine_switch.ogg` has been imported and registered as described in [audio-milestone.md](audio-milestone.md). Its file-specific license remains unresolved; follow the [media-use and attribution policy](../media-use-and-attribution.md) and [adjacent notice](../../src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg.license.txt).
- APC UI sprint changes or treating UI milestone acceptance as a dependency or acceptance proxy.
- General power graph rewrites, force-loading, unrelated cleanup, or unapproved architecture replacement.

## Required behavior

1. Observe only authoritative, actual APC output delivered by the power solve and only from a known/current graph sample.
2. Accumulate overload duration only while `actualOutputWatts > 20_000`, the breaker is closed, and the sample is valid and current. Trip after the required continuous duration exceeds 3 seconds; document cadence quantization and boundary semantics.
3. Clear the pending overload timer whenever output is `<= 20_000 W`, the breaker is open, a graph/sample is unknown or stale, or the APC is unloaded. Missing observations do not preserve elapsed continuity.
4. On trip, open the APC breaker and set the trip flag in the APC's authoritative server state. Reclosing manually clears the trip flag; define whether overload timing starts fresh after reclose (it must not inherit a prior partial interval).
5. Unknown/unloaded inputs fail closed for output and trip measurement, but must not erase stored energy or falsely mark the device as receiving/supplying.
6. Use the actual resolved known MV input, APC-tier demand, and storage state. Permit input passthrough toward output plus storage supplement, bounded by the real input, bridge efficiency/cap, storage state/rates, and load demand. Do not count passthrough input twice when calculating delivered output.
7. An open breaker may charge from a known input when the existing power contract permits; it provides no APC-tier delivery and no battery discharge.
8. Keep watt and joule units explicit. For every solve, source input must reconcile to delivered output, stored-energy change, and conversion/loss/curtailment terms under the stated efficiency convention; test exact conservation at the solver's defined precision.

## Architecture boundaries

Keep pure arithmetic and protection timing in testable power-domain logic; keep world queries, tick scheduling, device lifecycle, and breaker mutation in the owning server power runtime/BlockEntity path. Preserve `PowerDeviceBlockEntity` as the single persistent owner of APC battery and breaker state. `MS14Provider` and `MS14Bridges` are not APC BlockEntity state owners and must not be used to store this trip state. Do not add parallel persistence in UI/menu state or an unrelated attachment.

Use a prototype-oriented configuration only if configuration is actually needed; if introduced, use a typed immutable record and existing prototype/catalog validation patterns rather than raw maps, magic ID branches, or an untyped config bag. Defaults must be explicit and invalid thresholds/rates rejected. Do not imply the existing SS14 prototype value is locally reachable before validating the complete local path and caps (see [upstream parity](upstream-parity.md)).

## Reference and platform pin

- Minecraft 1.21.1; NeoForge 21.1.224.
- Upstream reference checkout: `C:\Users\William\Documents\SS14-dev\space-station-14`.
- Upstream reference commit: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
- Relevant upstream evidence and the limits of the parity claim are recorded in [upstream-parity.md](upstream-parity.md).

## Completion and handoff

Implementation evidence through M5 is recorded in [milestones.md](milestones.md), with the final status, exact validation commands, limitations, risks, and bounded owner manual checklist in [current-handoff.md](current-handoff.md). The handoff records coordinator-reported automated validation; this documentation update does not rerun those Gradle commands or substitute for owner acceptance. No manual client run has been performed. Preserve unrelated working-tree changes. This task is documentation-only: make no code changes, unrelated cleanup, commit, reset, or manual Minecraft launch; validate with `git diff --check` and `git status --short`.
