# M5 Validation and Open Gates

**Historical report; superseded disposition:** This report was written before the owner closed the sprint on 2026-09-24. See the [authoritative closure disposition](../closure.md): bounded work delivered, not fully SS14-accepted; the owner decision does not mean the historical sprint Definition of Done passed. The report's open-gate wording below remains a dated account, not the current sprint status. The owner reported a failed connected-world smoke; see the [2026-09-24 connected smoke regression audit](2026-09-24-connected-smoke-regressions.md) for the implemented postfix fixes and limits. Historical 102-test evidence below is distinct from later results, including a later 109/109 repeat acid test documented in the closure. No full player/client manual rerun is claimed. M5 adds a bounded Minecraft grounded-sliding adapter; neither connected-client movement/synchronization nor the owner's long-slide expectation is established.

**Review date:** 2026-09-24
**Scope:** documentation-only validation and handoff. The coordinator's latest validation is reported below, not rerun for this documentation update; no client launch or manual connected-player retest is claimed.

## M5 implementation and canonical source

Owner-approved canonical reagent source is SS14-style JSON `slipData` (typed `SlipData`, with camelCase keys such as `requiredSlipSpeed`); top-level reagent `friction` is a distinct source field. The adapter reads current puddle solution data and does not copy friction onto floor blocks or redefine slip aggregation. See the [sliding-friction architecture](../architecture/sliding-friction.md), [character slip-control architecture](../architecture/character-slip-control.md), and implementation in `SlidingFrictionSystem`, `LivingEntitySlidingFrictionMixin`, and `MinecraftSlidingPhysics`.

For an already-sliding entity on the grounded vanilla movement branch, current qualifying puddle friction values are combined as an arithmetic mean `F`. With no qualifying source, use neutral `F = 1`. Minecraft-only conversion is:

```text
grounded acceleration = vanilla acceleration * F
ground horizontal retention = pow(vanilla retention, F)
```

This is an explicit Minecraft adapter approximation, not SS14 timestep/physics identity. The source scan is a bounded feet-band scan; non-ground travel branches are not changed. Sliding is a synchronized, transition-updated, nonpersistent runtime attachment. Current puddle contents determine friction. A non-slippery qualifying puddle contributes its neutral `F=1` to the source mean; leaving all qualifying puddles restores neutral friction, while slide state may persist until knockdown expiry/reconciliation. Join/clone clears the volatile state. Generic knockdown is not slip provenance, so the state is not reconstructed from knockdown after reload/dimension transfer.

`LivingEntity` mixin code also applies to `LocalPlayer`, so client prediction follows the common code path by inspection. This code fact is not a connected-client run, packet synchronization proof, or evidence of client/server agreement; those remain unverified pending the owner checklist.

## Validation evidence

### Directly inspected repository evidence

- The historical `build/gametest-run/logs/latest.log` capture timestamped **2026-09-24 04:51:13–04:51:26** recorded 102 tests starting, `102 GAME TESTS COMPLETE`, and **All 102 required tests passed** (then-current log lines 35, 94–96). This earlier 102-test result predates the postfix run below. It is dedicated-server GameTest evidence, not a client or authenticated-player session.
- The same latest log records sliding traces for Villager and FakePlayer: Villager baseline acceleration `0.05458494572740112` versus sliding `0.004849643926607124`; baseline retention `0.10920001268386842` versus sliding `0.19403926912370142` (lines 78–79). FakePlayer baseline acceleration `0.0546000071555377` versus sliding `0.0048509820714475235`; baseline retention `0.10920001268386842` versus sliding `0.19403926912370142` (lines 80–81). Both traces return to baseline away from the source and after attachment removal. A capture trace checks friction remains sampled for that travel frame when movement exits a puddle (line 86). These are server fixture measurements, not client measurements.
- The log contains expected negative-path test diagnostics (including a deliberately missing status, invalid reagent, and unresolved identity). The required GameTest completion summary still records all 102 required tests passed.
- Reviewed `SlipSystem`, `SlidingFrictionSystem`, `LivingEntitySlidingFrictionMixin`, `MinecraftSlidingPhysics`, and canonical `SlipData` source. The slip listener runs before new sliding/status response; sliding is a separate attachment. The mixin captures grounded state and friction for the travel frame, applies acceleration scaling and adjusted horizontal retention, and leaves other movement branches neutral.

### Postfix coordinator-reported checks (not rerun for this documentation update)

After the regression fixes, the independent coordinator reported:

```powershell
.\gradlew.bat test --rerun-tasks --no-daemon
.\gradlew.bat build --no-daemon
.\gradlew.bat runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run
```

The earlier postfix run reported 103 required GameTests passed. The latest final coordinator validation after subsequent changes reported:

- `test --rerun-tasks --no-daemon`: `BUILD SUCCESSFUL`, 11 tasks executed.
- `build --no-daemon`: `BUILD SUCCESSFUL`.
- Isolated `runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run`: `BUILD SUCCESSFUL`; latest log timestamp **2026-09-24 06:57:55–06:57:58**, with **106 started / 106 required passed**.
- Python JSON syntax parsing: **603 files parsed**.

This is coordinator-reported evidence, not independently rerun here, and does not establish client runtime, authenticated reconnect/disk-world lifecycle, or connected-client synchronization. The mixed-jug command syntax is covered by a parser-only GameTest and listed in the owner checklist; the command was **not executed** in a client. No full client/manual retest has occurred since the fixes.

## Support / partial / deferred matrix

| In-scope behavior | M5 handoff status | Evidence and boundary |
|---|---|---|
| Canonical source schema and puddle source friction | **Supported** | Owner-approved reagent JSON `slipData` plus separate top-level `friction`; source is read from current puddle solutions. Existing M3 weighted slip aggregation is unchanged. |
| Slip-triggered source sliding state | **Supported, bounded** | M3/M4 server tests and current log exercise slip/sliding state. State is synced and nonpersistent; no provenance recovery from generic knockdown. |
| Grounded Minecraft acceleration and retention conversion | **Supported, bounded** | Pure conversion is `F` and `pow(vanillaRetention,F)`; server Villager/FakePlayer traces cover movement paths and neutral return. This is not SS14 numerical/timestep parity. |
| Current puddle friction aggregation, source exit, neutral puddles | **Supported, bounded** | Source code uses current contents and arithmetic mean, including neutral friction from qualifying nonslippery puddles; server traces cover leaving puddle and capture across exit. |
| LocalPlayer prediction path | **Partial / code-supported only** | `LocalPlayer` inherits the common `LivingEntity` mixin by source inspection. No client launch, client movement observation, or runtime client mixin validation. |
| Sliding-state synchronization and client/server movement agreement | **Deferred owner gate** | Attachment is synchronized by code and M5 dedicated-server evidence exists, but no connected client/packet synchronization verification. Follow [owner smoke checklist](../manual-smoke-checklist.md). |
| Real connected-player caustic Touch damage and action denial | **Outstanding acceptance gate** | FakePlayer is damage-immune. Villager caustic trace does not substitute for a connected player. Owner smoke remains outstanding. |
| Villager AI navigation pause under stun | **Unproven** | Natural-tick control moved about `0.766`, and stunned Villager moved about `1.027` in the stable fixture; this does not demonstrate a pause. FakePlayer did not tick/move in the dedicated GameTest unstunned or stunned, so zero displacement is not evidence that a real player's glide or stun physics are frozen. |
| Physical prone/crawling | **Deferred / not implemented** | No full physical prone/crawl behavior is claimed; generic knockdown marker is not SS14 physical parity. |
| Sliding persistence through join/reload/dimension transfer | **Deferred / known gap** | Volatile sliding state is cleared on join/clone; no persistence/reconstruction from generic knockdown. |
| Complete character-family rollout and coverage deadline | **Deferred owner planning** | Future animal, hostile/zombie, humanoid-race, and nonliving sentient adapters remain incremental; coverage review date is pending owner assignment. |
| Per-group reactive method profiles and exact pinned fixed-point allocation parity | **Partial / known model gap** | Character reactive group/method lists encode a global cross-product. Shared cent split can differ from pinned per-reagent allocation by tiny amounts; M4 report documents tested conservation and boundaries. |

## Milestone report index

- [M0 — hook and parity audit](m0-hook-and-parity-audit.md)
- [M1 — character prototype and binding](m1-character-prototype-and-binding.md)
- [M2 — timed stun and action gates](m2-timed-stun-and-action-gates.md)
- [M3 — puddle slip trigger](m3-puddle-slip-trigger.md)
- [M4 — slip-triggered Touch](m4-slip-triggered-touch.md)
- [Sliding-friction architecture](../architecture/sliding-friction.md)
- [Character slip-control architecture](../architecture/character-slip-control.md)
- [Connected-client owner smoke checklist](../manual-smoke-checklist.md)
- [2026-09-24 connected smoke regression audit](2026-09-24-connected-smoke-regressions.md)

The M3/M4 reports retain their dated historical findings and test counts. Their earlier stage descriptions are not retroactively rewritten by this M5 report; read this report and the M5 architecture as the later sliding-friction update.

## Open owner gates and decision

1. Resolve/retest the reported regressions using the [connected smoke regression audit](2026-09-24-connected-smoke-regressions.md) and [owner checklist](../manual-smoke-checklist.md): restart matching client/server builds, back up the world/player data, rejoin first, then test a solo sprint puddle with no Villager, a fast crossing, denied attack/mining/use/drop and snowball count/duration, the Villager control, one versus a row of adjacent lube puddles, and rejoin again. The owner has not yet retested after the implemented fixes. Record ambiguous caustic response honestly; 0.30 typed damage is only 0.06 vanilla health, and do not claim `/data` displays status. A small caustic effect may be visually unmeasurable.
2. Perform the real connected-player smoke for caustic Touch damage; record what is observable and do not treat FakePlayer immunity or Villager results as a pass. This remains a sprint acceptance gate.
3. Perform connected-client grounded movement and synchronization characterization with the owner checklist, including ordinary floor, one lube puddle versus a row of adjacent puddles, an additional qualifying nonslippery puddle, leaving contact, reconnect, and dimension behavior. The single-puddle long-distance glide concern remains open; do not add or imply a post-contact timer. No actual client launch/packet sync is claimed here.
4. Keep Villager AI navigation pause unproven until a viable behavioral fixture or owner check demonstrates it. Keep full prone/crawl explicitly unsupported pending a scoped decision.
5. Owner to assign a dated future character-family coverage review/deadline. Sliding state remains volatile and does not reconstruct from generic knockdown.

**Decision:** M5 automated implementation/reporting is complete on the available dedicated-server evidence. Original sprint acceptance is **OPEN**, not complete, until the applicable owner/player gates above are recorded and resolved. No source change or client validation is part of this report.
