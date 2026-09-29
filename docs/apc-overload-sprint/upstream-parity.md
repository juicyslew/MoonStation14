# APC timed overload trip — upstream parity notes

## Pinned reference

- Checkout: `C:\Users\William\Documents\SS14-dev\space-station-14`
- Commit: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`
- Relevant files: `Content.Server/Power/EntitySystems/ApcSystem.cs`, `Content.Server/Power/Pow3r/BatteryRampPegSolver.cs`, `Content.Shared/Power/SharedBattery.cs`, APC/battery component and prototype definitions, and associated power tests.
- Local target platform: Minecraft 1.21.1 / NeoForge 21.1.224.

These notes document source observations, not a claim that the local power model is equivalent to SS14. The upstream solver and prototype values have different units, topology, timing, ramp behavior, and device semantics. Confirm the checked-out pin before implementation and record any changed evidence.

## Observed upstream behavior

At the pinned `ApcSystem.cs` overload block (approximately lines 63–83), the system observes overload only while `MainBreakerEnabled` and tests `battery.CurrentSupply > apc.MaxLoad`. The first overloaded update records `TripStartTime`; a later update trips only when `curTime - TripStartTime > TripTime`. It then sets `TripFlag` and toggles the breaker off. The `else` branch clears `TripStartTime`, so a non-overloaded update resets continuity. `ApcToggleBreaker` clears `TripFlag` on a manual breaker toggle (approximately line 142). This is a strictly greater-than comparator for both limit and duration, not `>=`.

`CurrentSupply` is the battery network's actual supplied/delivered output meter; `CurrentReceiving` is receiving/input power. In `BatteryRampPegSolver.cs` (approximately lines 180–189), available battery supply is limited by stored-energy/ramp supply and `supplyCap + CurrentReceiving * Efficiency`. This term allows passthrough input, efficiency-adjusted, in addition to the bounded battery supply cap. `CurrentSupply` is later allocated as the battery's actual output (approximately lines 258–277). Do not treat the passthrough term as an independent second supply or add `CurrentReceiving * Efficiency` again to `CurrentSupply` when measuring trip output: that would double count input-derived output.

The local authorized behavioral target is intentionally specified as actual APC delivered output `>20,000 W` continuously for `>3 s`, reset by output at/below threshold, unknown/stale sample, breaker-off, or unload. Use a truthful local mapping for actual delivered output and elapsed time. Do not import upstream transient/ramp assumptions unless independently verified and approved.

## Local reachability is fixture-proven; upstream/base reachability remains uncertain

The repository power loop is solved on a 20-tick cadence (`PowerRuntime.TICKS_PER_SOLVE`). The local fixed debug-source offer and rated source/substation limits are 25,000 W; at 90% efficiency the connected path can deliver up to 22,500 W before APC-tier demand. The wired GameTest proves overload reachability with a connected 29.8 kW lamp load. This does not establish that ordinary starter-station or upstream/base-prototype configurations can reach the threshold. Local `BaseAPC` defaults include `maxSupply = 10,000 W` and `maxCharge = 5,000 W`; the 10 kW battery supplement is separately accounted and is not the debug source offer.

Reachability must be judged from the complete path and connected demand, not one field name. The 25 kW local debug-source balance adjustment is not a prototype importer or an upstream-parity claim. Do not treat supply/charge caps as actual output telemetry, sum demand, count input twice, or silently change the strict threshold. Where base-prototype reachability has not been demonstrated, keep that uncertainty explicit.

The existing local solver uses storage charging/discharging and bridge accounting different from upstream Power3r. Preserve local source/input/output conservation and exact known MV input semantics. The upstream expression `supplyCap + CurrentReceiving * Efficiency` is a parity clue for passthrough-plus-storage capability only; it is not a formula to transplant blindly. Count actual input once and trip on actual `CurrentSupply`/delivered-output equivalent only, never on input plus output.

## Cadence and timing caveat

Upstream `ApcSystem.Update` is an update-loop timer based on game time, while local power accounting currently solves every 20 ticks. The local implementation must not claim equivalent temporal precision. A 20-tick sample interval is one second at 20 TPS; over-3-second duration therefore needs a documented sampled-time policy and has cadence quantization. If elapsed time is accumulated between valid solve snapshots, every interval must be justified as continuously represented by a valid metered observation; unknown/stale intervals reset, and work-budget delay cannot silently extend continuity. State measured detection tolerance and tests.

## Parity statement

Target only the bounded semantics supported by the local model: true output-meter overload, strict threshold/time boundaries, continuous-valid-sample reset behavior, latched trip flag, breaker open, and manual reclose clearing the flag. Do not claim exact upstream power parity, exact ramp dynamics, identical station topology, or that default local prototypes can reach the condition until demonstrated by M0/M4 evidence. No upstream station-event or ghost-haunt behavior is part of this sprint. Low-power flicker and low-power audio are not authenticated here as core effects and remain deferred pending separate design. Separately, the owner authorized the server-side APC breaker-switch sound for accepted manual changes and overload trips; SS14 `machine_switch.ogg` has been imported and registered (see [audio-milestone.md](audio-milestone.md)). Its file-specific license remains unresolved; follow the [media-use and attribution policy](../media-use-and-attribution.md) and [adjacent notice](../../src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg.license.txt).
