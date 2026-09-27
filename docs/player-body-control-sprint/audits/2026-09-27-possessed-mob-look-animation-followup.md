# 2026-09-27 possessed Mob look and walk-animation follow-up

**Status: bounded connected owner visual acceptance covers both the possessed Mob WALK animation and the previously notable mouse-turn delay on the owner's tested setup.** After initial connected Mob control, the owner reported notable delay in mouse turning and no walk animation on controlled mobs. Following the look and generic-animation changes, the owner confirmed “Both.” This records acceptance of those two symptoms on that connected test setup only; the tested Mob species/model was not named. It does not establish separate Villager and Pig proof, second-client animation, broader possession acceptance, or full GameTest success. See the [operator possession handoff](2026-09-27-operator-possession-handoff.md), [render-frame ghost look notes](2026-09-27-render-frame-ghost-look-and-transfer-notes.md), and [sprint instructions](../../../instructions-player-body-control-sprint.md).

## Look projection

The character look path now reuses `setOwnedBodyLook(LivingEntity)`, the same render-frame projection used by the ghost. It projects the current local player's look onto the owned body on the render frame, including yaw/pitch history and body/head rotation fields that were previously omitted for `CHARACTER`. This is intended to avoid waiting for a later client tick to see mouse turns. It is a client visual projection, not a transfer of movement authority. The owner subsequently confirmed the previously notable mouse-turn delay looks/work right on their connected test setup; this is bounded owner observation, not separate species/model or broader possession proof.

## Walk animation

Generic `OwnedHarnessWalkAnimation` now derives animation from accepted harness displacement. The server updates the world-step animation and the owning client predicts it once per local step rather than replaying it during reconciliation. On remote clients, a client-only Mob tick mixin samples the tracked interpolation and excludes the exact local owner to avoid double-applying animation. The prior approach of invoking `calculateEntityAnimation` while canceling `travel` was removed: `Entity.baseTick` resets `xo`/`zo` before a later natural tick could use those position deltas.

The `leasedPigAndVillagerKeepVanillaWalkAnimation` GameTest checks immediate and next-natural-tick animation without manually invoking travel. It passes. This is fixture-level verification; it does not establish owner-visible animation, remote two-client interpolation, or connected possession behavior.

## Owner observation, validation, and remaining acceptance

- The owner connected to a possessed Mob and confirmed “Both” symptoms—the WALK animation and mouse-turn response—look/work right on the tested setup. This is bounded owner feedback; the specific species/model was not identified. Do not infer separate Pig and Villager results or second-client animation.
- Ghost minor turning-while-moving jitter remains separately deferred; this observation does not establish broader possession lifecycle acceptance or prototype parity.

- Focused `player_body_control` tests passed.
- `ServerClassloadingTest` passed.
- `compileGametestJava` passed.
- The dedicated `runGameTestServer` run at `build/gametest-controlled-animation-poststep/logs/latest.log` completed 132 GameTests but had **two required concurrent atmosphere failures**: `coveredexteriorsealingandbreachrespectownership` and `fullwallopeningdrainsmorethanoneblockopening` (lines 263–266). This is not a green full GameTest run.
- Bounded connected owner visual acceptance is recorded for these two symptoms only. Separately identified Pig/Villager coverage, second-client animation, broader possession lifecycle, and prototype parity remain unproven. The separate minor ghost turning-while-moving jitter remains deferred and is not part of this character fix.

Describe only the possessed Mob WALK animation and mouse-turn response as accepted on the owner's tested connected setup; do not generalize this to all species/models or clients. This follow-up does not establish broader possession lifecycle or connected transfer acceptance, prototype parity, or unrelated atmosphere behavior.
