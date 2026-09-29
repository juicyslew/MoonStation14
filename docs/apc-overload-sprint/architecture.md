# APC timed overload trip — architecture and invariants

## Ownership map

```text
Known loaded power graph + current solve
  └─ pure accounting: actual MV input, APC-tier delivery, storage and losses
       └─ authoritative server PowerRuntime sample
            └─ pure breaker-protection reducer/timer
                 └─ PowerDeviceBlockEntity owns breaker/trip persistence
```

The solver owns electrical accounting, not world lifecycle. The runtime owns bounded, server-only sampling and applies the pure result to the APC owner. `PowerDeviceBlockEntity` remains authoritative for persisted battery and breaker state. A typed overload flag/timer snapshot may be added to that owner only if necessary; no second durable owner is allowed. UI/menu code may display a server snapshot only if separately authorized, and is not part of this sprint.

`MS14Provider` and `MS14Bridges` connect holder traits/attachments and data components. They do not own APC BlockEntity state; do not put breaker, battery, or trip state there. Do not introduce a station-wide/global overload manager when the per-APC runtime/BE boundary suffices.

## Solver contract

Input is the actual known MV-side power made available to this APC through its real connected path, after source and upstream substation caps/losses. Output is actual APC-tier delivery to loads, not demand. Battery state is an explicit energy store in joules; rates are watts. The solver must return a fully explainable result: input accepted, input passed toward output, storage charge/discharge, actual delivered output, unmet demand, curtailed power, and all conversion losses as applicable.

Required invariants:

- Unknown graph/input produces no claimed delivery and no claimed charging/discharging. It invalidates/reset the overload observation; it does not preserve a timer across uncertainty.
- Demand is a request/upper bound, never evidence that power was delivered. `demand > 20,000 W` with actual delivered output at or below threshold cannot trip.
- Passthrough comes from the actual known input, not a synthetic APC source. It may be supplemented by a bounded battery discharge when input cannot meet actual demand and the breaker is closed.
- Charge uses actual surplus, charge-rate/room limits and charge efficiency. Storage-energy delta is consistent with accepted charge/discharge watts and elapsed seconds.
- There is no double count: if an input-derived amount already contributes to output, do not add that input a second time as another source or again through `CurrentReceiving * Efficiency`.
- Conservation equation and sign convention are explicit. At minimum, reconcile accepted input + battery energy withdrawn against delivered output + battery energy stored + conversion losses + curtailed input, with elapsed time applied to convert watts to joules. Numerical residual is bounded by a declared solver epsilon and covered by tests.
- Breaker open means zero APC-tier output and zero discharge, even if the MV input remains known. Charging may remain enabled from that known input as an independent allowed path, subject to rate/efficiency/room limits.
- No graph traversal, chunk lookup, or protection sampling may force-load a chunk.

## Protection state machine

Represent protection as a pure reducer/state transition so boundary and reset behavior can be tested without a world. Inputs should include current server tick/time, breaker state, sample validity/currentness, and actual delivered output watts. State needs only the pending overload start/elapsed observation and trip flag unless local existing state requires a similarly small representation.

| Observation | Timer behavior | Trip flag / breaker behavior |
|---|---|---|
| Valid sample, breaker closed, output `> 20,000 W` | Accumulate continuous observed duration | Trip only after duration is strictly `> 3 s`; open breaker and mark tripped |
| Valid sample, output `<= 20,000 W` | Reset immediately | No new trip |
| Breaker open | Reset immediately | Do not output or discharge; manual reclose clears trip flag and starts with no inherited timer |
| Unknown, stale, or missing sample | Reset immediately | Do not claim overload continuity or output |
| APC unloaded | Reset/invalidate ephemeral timer; persist only the intended trip/breaker state | No force-load or off-screen sampling |

The configured 3-second duration and strict threshold are the gameplay contract. Because the power solver updates every 20 ticks, tests and owner-facing notes must define the actual sampling/tick-time interpretation and maximum detection delay. A missed or unknown interval breaks continuity; do not interpolate through it. If per-tick protection polling is used between solver updates, it may only consume the last valid metered output when the sample's freshness policy allows it, and must remain bounded. Prefer bounded per-tick iteration/indexing over an unbounded scan; measure the upper bound and stale-sample behavior.

The trip mutation must be server-owned and persistence/update behavior must follow existing BlockEntity conventions. A manual reclose is an explicit operator action and clears the latched trip flag. A thermal/event station incident is not implied by this breaker state.

## Runtime lifecycle and performance

Power solve cadence remains `PowerRuntime.TICKS_PER_SOLVE = 20` ticks unless a separately approved design changes it. APC inputs, outputs, and trip samples use loaded-known graph results only. Bound APC work each tick; document the maximum work budget and what happens to queued APCs when the budget is exhausted. Work deferred by the budget must not manufacture extra elapsed overload time. Device removal/unload invalidates live sample/timer state safely; save/reload behavior for a previously tripped/open breaker must be explicitly tested.

Keep server-only code free of client UI references. Do not add station scans or unbounded world queries to achieve overload checks. Any UI display is outside this sprint and cannot weaken or own protection authority.

## Configuration and scope boundary

Threshold `20_000 W`, strict comparison, and `>3 s` duration are explicit sprint defaults. Existing prototype supply, charging, load, source, bridge, and storage limits may make the condition unreachable; reachability must be evaluated on the complete local network before configuring or claiming it. A typed prototype-oriented settings record is acceptable only if existing typed prototype patterns support it and the minimum implementation needs data-driven tuning. It must not replace the BE's breaker ownership or allow arbitrary per-device unvalidated state.

No flicker/audio response, event hooks, ghost-haunt flow, station event, generic overload framework, or UI changes are architectural requirements here.
