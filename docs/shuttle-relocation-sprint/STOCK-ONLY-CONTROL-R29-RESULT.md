# R29: mass admission verified; initial-settling guard failed

## Bound evidence

- Candidate `shuttle-feedback08-stock-only-20261016-r29`, snapshot
  `9b8e591de2791dc8bea94b1e5fb40279328186a739ae807629bc385cf4d11d61`.
- Receipt `shuttle-feedback08-stock-only-20261016-r29-gate-validation.json`,
  SHA-256 `6accd95885305db1c5adf008aaf1a5190c9676da5e77339ec0236932b2bb5dc4`.
- Raw `feedback08-stock-only-r29-native.log`, 133,754 bytes, SHA-256
  `920ab51aee7a569a6a7acd43008e24eda05fd8d130f4c44b15896a5a1e0a7fa0`.
  Receipt/raw are in `build/agent-workflow/shuttle-relocation-sprint/reports/`.

## Established setup result

Lead-owned root `compileGameTestJava` passed. Native full **control catalogue**
selected/completed 2/2, passed 0, failed 2 during setup (raw 115–125). Neither
case reached launch, grounded admission or trajectory assertions. No high-speed
collision conclusion follows.

The corrected guard executed before `prepareGameTestServerRun` (9–11). Runtime
exclusion, exact versions and official loaded Sable digest again passed (81–85).
This is not proof of the owner's installed runtime identity. The empty mixin
query is not proof that Sable's own mixins were absent.

Both pre/post property captures resolve 451 stone blocks at mass 2 each and one
glass block at mass 1: **903**, equal to the actual tracker (109–121). Both use
friction 1, restitution 0, volume 1 and null optional inertia. State/shape
fingerprint remains `c6cdd7a22cf9c4098132d63c9e03fca1ef79d50e9022426c238f7ea44d9c4bdd`.
The false R28 one-mass-unit assumption is now explained by actual runtime data;
this still differs from R16's MS14 composition.

The current harness places the source one block above the intended pad surface
and applies the 1e−6 near-identity guard to **every initial settling sample**.
Both failures show identical small rotation (quaternion vector norm about
0.000367), finite moving state, coverage/hull unchanged and approximately
0.03342 m penetration during initial support contact (115/122). They did not
fail the shared >0.10 m/two-sample flight criterion. Initial placement, support
authoring order and the timing of the orientation guard require comparison with
R16 before changing another premise. Do not reset/freeze rotation or silently
relax the final admission guard.

## Shutdown evidence

The two cases completed at 01:59:43 in 2.549 s. Matching server stopping began
at 02:05:54, followed by `Saving worlds` (129–132). Bounded capture armed and
produced thread stacks; there is no clean save/exit proof. Receipt exit 124,
owned-tree cleanup `terminated`, overall **FAIL**.

Both server-thread samples (134–148 and 392–406), taken 15 and 30 seconds after
stopping, are **RUNNABLE**, in `DynamicGraphMinFixedPoint.checkNeighbor/runUpdates`, reached
through `ChunkTracker`, `DistanceManager$ChunkTicketTracker.runDistanceUpdates`,
`ServerChunkCache.tick` and `MinecraftServer.stopServer`. This identifies sampled
work, not a lock deadlock or an engine defect. Investigate the harness's vanilla
force-ticket use for virtual Sable storage coordinates before assigning cause.
No identity with R26's uncaptured hang has been established.

Source investigation confirms a local ownership mismatch in the harness:
Sable's `SubLevelContainer` routes plot coordinates through `LevelPlot.toLocal`
to plot-owned chunks; public assembly creates these itself. The harness instead
used ordinary vanilla forced tickets and world ENTITY-TICKING checks for those
virtual storage coordinates. Replace that readiness path with exact public
container/plot/chunk ownership checks, while retaining vanilla tickets and
FULL/ENTITY-TICKING checks for physical-world coverage only. This is a justified
local correction, **not yet proof** that those tickets caused the sampled stall.
Do not alter older fixtures or production based only on the hypothesis.

Preserve R29's world, raw log, stacks, receipt and frozen plan. No automatic
rerun, owner-world/configuration change, Sable modification, ten-step adoption,
readiness or M1 acceptance is authorized by this result.
