# Reactive-front follow-up

## Goal and acceptance boundary

The live retest still reported slow initial tritium fire followed by abrupt water/fire updates. Removing dummy progress did not satisfy gameplay acceptance. This follow-up separates authoritative propagation, excited-group equalization and visual delivery. Live acceptance remains open until a retest demonstrates useful reaction time.

## Missed upstream rule

Pinned SS14 `AtmosphereSystem.Hotspot.cs:ProcessHotspot` removes invalid hotspots, adds active tiles, performs its first-process skip, then resets both excited-group cooldowns before temperature/volume/air/ignitability checks. A persistent hotspot therefore postpones group-wide averaging; even a valid hotspot which fizzles on that processed pass refreshes the timers.

The port lacked that reset. Its burning groups could periodically average fuel, products and heat across all members. The earlier claim that such a sweep during ongoing fire was simply inevitable SS14 behavior was incomplete.

The correction uses existing cell-to-group membership for an O(1) reset of only the affected group. A null hotspot or first-process skip does not reset; a committed fizzle does. Resets publish after a committed hotspot pass, not during speculative evaluation. Rejected preflight/writes leave cooldowns unchanged. The existing stamped-commit failure path preserves committed metadata. Nonreactive group averaging rules and all reaction constants remain unchanged.

Independent review passed this commit ordering and predicate. Core and transaction tests cover ongoing resets, missing membership, first skip, committed fizzle and rejection.

## Visual delivery barrier

Server mutations flush every seven game ticks. Large dirty sets can become paginated chunk snapshots; the old client retained the entire transfer in staging until its final packet. That is a concrete mechanism for a chunk-sized visual jump even when individual server cells changed earlier. It does not establish that every reported jump was visual.

The client now displays received authoritative cells promptly while retaining full staging/revision reconciliation. Unvisited old cells remain until final reconciliation; newer deltas immediately update the display and win over older snapshot fragments. Final reconciliation still removes cells absent from the completed snapshot. This is not interpolation or a fabricated advancing flame front; server gas/fire simulation remains authoritative.

Independent review passed per-cell delta precedence, explicit clears, staged-delta memory bounds and final replay against the snapshot's base revision. View refresh is batched per packet/replay, not repeated per replayed cell. Tests include a newer clear resisting a later older fragment and separate revision-6/revision-7 changes both surviving final reconciliation. Large-snapshot coverage shows cells before final transfer completion.

## Measurement

`AtmosphereReactiveFrontGameTests` drives an isolated sealed room through real service callbacks and records cell gas/temperature, current hotspot state, commit stamps and stage/cycle changes. It is a diagnostic/acceptance fixture, not a measured reproduction of the user's station. Source and room have equal total moles, avoiding an initial mole-inventory imbalance; their temperatures and pressures are not equal. Runtime telemetry exposes group-average counts for stage attribution.

The final 75-test gate recorded **22 committed active-hotspot cycles** in each reactive fixture. First water above the tiny physical threshold at Manhattan distance >=8 appeared after **127–128 game ticks**; the first group average followed at **483–485 game ticks**. This establishes that distant physical water in this stimulus was not first transported by averaging. It does not establish visible-water onset or live-wall-time latency.

The inhibition assertion checks the next cycle's GROUPS stage against the preceding cycle's committed hotspot pass, provided the source is still active. Checking the same cycle would be vacuous because GROUPS precedes HOTSPOTS. A pass is detected by a changed commit stamp, not a gas change: bypassing hotspots can commit metadata while LINDA has already committed their whole-air gas reaction. First-process skips and non-admitted callbacks do not count as active processed passes.

## Unresolved source correspondence

- Port admissions are 15 per 20 game ticks, not one per 20 ticks. The healthy nine-stage chain is nominally 0.6 seconds at 20 server TPS. SS14 also dispatches one state per atmosphere time gate; no evidence supports blaming a different nominal stage cadence for the initial slow front.
- SS14 exposes new hotspots with `SkippedFirstProcess = tile.CurrentCycle > UpdateCounter`. LINDA stamps `CurrentCycle = UpdateCounter`, so same-cycle processing does not imply that flag is true. The port's existing exposure/receiver mapping uses LINDA membership as an already-processed flag. This separate pre-existing parity gap is not evidence of an extra slow-front delay: in the inspected same-cycle case it can make the port process earlier than upstream. Do not blindly reverse it as a speed fix.
- The six-face topology is an intentional 3D extension. Quarter-cell exposure is 250 L for the port's 1,000 L cell; accepted initial volume grows by 25 to 6,250 L. Neither changes the canonical chemistry gates.

## Validation and next acceptance

Final validation passed **1,888 JUnit tests** with zero failures/errors/skips, **75/75 focused GameTests** (the previous 18-class/73-test selector plus the two new fixtures), default `compileGametestJava`, `assemble`, and `git diff --check`. Both physics reset and visual reconciliation passed independent static review. Evidence: `build/atmosphere-parity-validation/reactive-front-gate-01/`; rebuilt artifact: `build/libs/moonstation14-0.0.1.jar`. Successful disposable fixture worlds were removed after retaining logs/XML.

No global diffusion increase, tritium-specific exception, group-size barrier or cosmetic flame interpolation is introduced. Initial slow propagation and the actual live room-wide jump are not established as resolved. Retest the rebuilt client/server artifact; if a burst persists, retain `/ms14atmoscheck` captures from the affected position around it to distinguish real group averages, reaction-triggered creation, stage load and visual delivery. The sprint remains active, with live/gameplay acceptance open.
