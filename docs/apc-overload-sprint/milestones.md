# APC timed overload trip — milestones and acceptance gates

This is the current evidence status, not owner acceptance. Implementation claims are bounded by the evidence listed below and by [current-handoff.md](current-handoff.md). Upstream parity is partial: a breaker-switch clip is implemented for accepted manual toggles and timed trips, but no station event, ghost-haunt, or flicker behavior is included.

## M0 — source, caps, and reachability audit — recorded

The pinned upstream audit records an APC default `maxLoad` of 20,000 and a strict overload comparison (`CurrentSupply > MaxLoad`) held for strictly more than 3 seconds. The upstream system checks actual supplied output, not receiving power. The local `BaseAPC` defaults include `maxSupply = 10,000 W` and `maxCharge = 5,000 W`; those alone do not support the target overload.

For the implemented local fixture, the rated source and substation limits were raised to 25,000 W. The actual source/substation 90%-efficiency path can deliver 22,500 W before APC-tier load demand, and a separate 10,000 W battery supplement is accounted for. A deterministic per-APC delivered-output meter, not requested load, is used for protection. The 20-tick solve cadence remains distinct from the per-server-tick protection observations. This is a local reachability adjustment, not a prototype importer or a claim of exact SS14 parity; the source change also affects the existing power fixture.

**Status:** audit and reachable local path recorded. Upstream ramp/peg behavior and a full upstream prototype importer are not implemented.

## M1 — pure solver accounting and conservation — implemented/tested

Pure passthrough/accounting supports known MV input, APC output, battery supplementation and charging, caps/losses, unknown input, and breaker-open behavior. It meters actual delivered output deterministically per APC. An open breaker cannot deliver APC-tier output or discharge storage, but can charge from its own known MV input. These are local bounded semantics, not the upstream ramp/peg solver.

**Evidence:** synthetic pure solver tests cover accounting/protection inputs and the required synthetic cases. They are not a substitute for the wired GameTest. See [current-handoff.md](current-handoff.md) for the validation commands and evidence distinction.

## M2 — BE-owned protection state — implemented/tested

The APC BlockEntity owns the persistent trip latch and breaker state. Protection uses actual metered output strictly greater than 20,000 W for strictly more than 3 seconds of consecutive qualifying observations; threshold equality does not count. Invalid/unknown/stale observations, output at or below threshold, breaker-off, and unload reset the transient timer. A trip opens the breaker and persists the latch; manual reclose clears the latch and starts timing fresh.

**Caveat:** the timer is transient and resets on unload; the trip latch is persistent. Do not describe timer continuity across unload/reload as implemented.

## M3 — runtime integration and loaded lifecycle — implemented/wired-tested

The loaded server runtime consumes the deterministic actual-output meter; it does not infer output from demand. Protection observations are bounded to cached APC samples per tick, while topology solving remains on the existing 20-tick cadence. A trip opens the APC breaker and isolates its output. The trip is local to that APC/circuit: other circuits are not blacked out.

**Evidence:** synthetic direct-meter tests cover reducer behavior; separately, the dedicated wired GameTest connects a real source/substation/APC/cable/lamp overload fixture and exercises the runtime trip and manual reclose. These proof types must not be conflated. No hard numeric APC-population performance bound or station-scale benchmark is claimed.

## M4 — runtime output observations and UI trip label — implemented/tested

Actual delivered-output observations feed protection; no demand-only trip path or fictitious between-solve output interpolation is used. The local implementation does not reproduce SS14's ramp/peg behavior. The UI displays a distinct **OVERLOAD — TRIPPED** label based on the authoritative trip latch; a manually opened breaker is not mislabeled as tripped. The breaker can be manually reclosed, clearing the latch.

Automated validation recorded by the coordinator:

- `.\gradlew.bat compileJava compileGameTestJava test --no-daemon` — passed.
- `.\gradlew.bat runGameTestServer -Pms14GameTestDir=build/apc-overload-final-server --no-daemon` — passed, **165/165 required GameTests before M6**; not evidence of a full-suite pass with the debug fixture.

The exact commands without typographic spacing are repeated in [current-handoff.md](current-handoff.md). No manual client was run. Automated results do not constitute owner acceptance.

## M5 — wired dedicated GameTest and handoff — implementation evidence recorded; owner review pending

The dedicated wired GameTest uses an actual loaded source → substation → APC → APC-tier cable/lamp path and proves overload trip/latch and manual reclose. Synthetic pure tests remain separate coverage for solver arithmetic, protection boundaries, and cases not represented by that fixture. The wired test is not a manual client test and does not establish station-scale performance or other-circuit blackout behavior.

**Gate:** implementation/test evidence is recorded, but explicit owner acceptance and the bounded manual checklist in [current-handoff.md](current-handoff.md) remain pending. Do not report the sprint as owner-accepted until that review occurs. Low-power flicker and any related low-power effect remain deferred; the separate breaker-switch sound is implemented with unresolved file-specific license and no manual listening confirmation (see [audio-milestone.md](audio-milestone.md)). Station events and ghost behavior were explicitly excluded by the owner.

## M6 — compact creative debug load fixture — implemented; full-suite and owner gates pending

Creative-only `high_load_test_lamp` requests 12 kW **whenever connected**, not only while lit. It shares the ordinary lamp model and is visually indistinguishable except for its label; there is no additional art or recipe, and the ordinary lamp stays at 100 W. Two connected debug lamps request 24 kW against the known 22.5 kW source path plus 1.5 kW battery supply, exceeding the strict 20 kW APC output threshold. One connected lamp remains below it. See [debug-load-fixture.md](debug-load-fixture.md).

**Evidence and limit:** compile and unit tests passed; the connected wired one- and two-lamp debug tests each passed individually within earlier full server runs. Earlier full **167-required-test** server runs failed varying unrelated atmosphere/cable whole-chunk assertions across three isolated fresh runs. Shared-chunk/test-order interaction is suspected, not established as root cause. The 165/165 run predates M6. A later coordinator-reported isolated audio-era run passed **168/168 required GameTests**, following `compileJava compileGameTestJava test`; see the exact commands in [current-handoff.md](current-handoff.md). This does not establish deterministic suite success: prior runs also had unrelated cable/atmosphere/wired failures. No owner test, manual client run, or sound-listening confirmation has occurred.

**Owner gate:** with the same ready source and battery, place two debug lamps near the APC/LV floor wire, wait more than three seconds, check the UI trips, then remove one lamp and manually reclose; it should stay closed. No 298-lamp placement is requested.
