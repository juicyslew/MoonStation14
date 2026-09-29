# APC overload sprint — current handoff

## Status

Implementation and automated evidence through M6 are recorded in [milestones.md](milestones.md). This is not owner acceptance: a manual client run remains outstanding. This handoff update is documentation-only and makes no source or prototype changes.

## Recorded behavior and parity limits

- **M0 reachability:** Upstream's APC default `maxLoad` is 20,000 and its trip rule is actual supplied output strictly above that limit for strictly more than 3 seconds. Local `BaseAPC` defaults include `maxSupply = 10,000 W` and `maxCharge = 5,000 W`. The local fixed debug-source offer and rated source/substation limits are 25,000 W; the actual 90%-efficiency source/substation path can deliver up to 22,500 W before APC-tier demand. Connected demand is required to consume it; a separately accounted 10,000 W battery supplement can also support output. The local meter is deterministic per APC and records delivered output, not demand or input. This debug-source adjustment establishes fixture reachability, not ordinary starter-station reachability or upstream prototype parity.
- **Local-vs-upstream boundary:** This does not implement SS14 ramp/peg behavior or a full upstream prototype importer. The source change also impacts the existing power fixture. Do not claim exact upstream parity.
- **Protection/lifecycle:** Actual output must be strictly greater than 20,000 W for strictly more than 3 seconds. Timing is based on local observations and the existing 20-tick solver cadence; no fictitious between-solve ramp/output is inferred. A read-only per-level `topologyGeneration` token is saved with each APC sample; topology changes invalidate the sample, reset timer continuity, and request an early solve once the graph is ready. The pending timer is transient and resets on unload. The trip latch is persistent. Manual reclose clears the latch and starts timing fresh.
- **Breaker and scope:** An open breaker may charge from its own known MV input but does not provide APC-tier output or discharge its battery. A trip isolates the affected APC/output circuit; other circuits are not blacked out. This is not station-wide overload behavior.
- **UI/effects boundary:** The UI has a distinct trip label. The server plays the same source-verified SS14 `machine_switch.ogg` clip once at each accepted manual breaker toggle and once at a timed overload trip, matching the upstream switch-sound precedent; the opening button does not play a duplicate client-side sound. This is a breaker-switch effect, not low-power flicker or a general low-power sound. Flicker/aged-bulb/ghost behavior and station events remain excluded. The clip's file-specific license remains unresolved: see [audio-milestone.md](audio-milestone.md), its [adjacent notice](../../src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg.license.txt), and the [running unresolved-audio register](../audio-unknown-provenance.md). Do not infer MIT audio rights. The sound has not been manually heard in a client.

## Test and validation evidence

Pre-M6 coordinator-reported automated validation:

```powershell
.\gradlew.bat compileJava compileGameTestJava test --no-daemon
.\gradlew.bat runGameTestServer -Pms14GameTestDir=build/apc-overload-final-server --no-daemon
```

Both commands passed **before** the compact debug fixture; the dedicated server run passed **165/165 required GameTests**, including `ApcOverloadGameTests.connectedCablePathLampLoadTripsApcAndManualRecloseRestoresIt`. Later full **167-required-test** runs with the fixture failed varying unrelated atmosphere/cable whole-chunk assertions across three isolated fresh runs; a shared-chunk or test-order interaction is suspected, **not proven**. Separately, the coordinator reported these audio-era validation commands passed:

```powershell
.\gradlew.bat compileJava compileGameTestJava test --no-daemon
.\gradlew.bat runGameTestServer -Pms14GameTestDir=build/apc-audio-verified-world --no-daemon
```

The isolated server run passed **168/168 required GameTests**. Earlier varying unrelated cable/atmosphere/wired GameTest failures mean this is evidence of one passing run, **not** a claim that the suite passes deterministically. No manual client run or owner test has occurred. For evidence interpretation:

- Synthetic pure tests exercise solver/accounting and direct protection observations, including boundaries and reset behavior; they are distinct from the wired integration proof.
- The wired dedicated GameTest uses a loaded source/substation/APC/cable grid and 298 actual 100 W lamps (29.8 kW demand), and verifies runtime overload trip/latch and manual reclose. Lamp projection updates on the scheduled next-tick re-solve, not in the trip tick. The same fixture disconnects an MV cable after overload observations accrue, then verifies the APC remains closed/unlatched and the zero-battery lamp remains dark for 85 ticks. It proves these fixture cases only, not general station-scale performance, every topology, or other-circuit behavior. See [wired-proof.md](wired-proof.md) and [topology-freshness.md](topology-freshness.md).
- The M6 compact creative fixture uses two connected 12 kW debug lamps for 24 kW requested output (22.5 kW known source plus 1.5 kW battery); one connected lamp remains below the 20 kW threshold. Demand is 12 kW per lamp whenever connected, not only while lit. The ordinary 100 W lamp is unchanged. See [debug-load-fixture.md](debug-load-fixture.md).
- Per-tick processing is bounded to cached APC samples, but no fixed numeric APC population ceiling or station-scale performance benchmark is claimed.

## Owner manual checklist — bounded

Use a starter-station setup with a single APC and normal use; placing 298 lamps is **not requested**. For the short overload check, use the ready source and battery with two creative High-Load Test Lamps near the APC/LV floor wire: wait more than three seconds, confirm the UI shows a trip, then remove one lamp and manually reclose; it should stay closed. The one-/two-lamp wired debug tests passed individually, but owner visual acceptance is still pending. Check:

1. Normal single-APC use and the UI's distinct tripped status.
2. Manual reclose after a trip, then retrip behavior under the optional stress harness if desired.
3. Save/reload behavior: the trip latch persists; a pending overload timer does not persist across unload.
4. Separate circuits remain unaffected by this APC's trip.
5. No phantom charging/output accounting; with the breaker open, charging can come only from that APC's own known MV input, with no output or battery discharge.
6. Power-off gate behavior.

Owner visual and audible acceptance and any findings remain pending: automated GameTests prove server behavior, not the in-client presentation or that the sound was heard. Source-verified breaker audio is implemented, but flicker is not; upstream base-prototype reachability remains uncertain, and the 25 kW source is a local debug balance change rather than an upstream-parity claim.
