# 2026-09-24 Repeated Touch damage follow-up

**Scope:** bounded explanation of reactive Touch caustic amounts and why repeated-contact testing is difficult. This documents the pinned behavior; it is not a change request or a connected-player test result. The owner considers the damage amount acceptable. Do not increase acid damage, bypass the Touch chance, or add post-contact glide as part of this follow-up.

## Pinned behavior

`PuddleSystem.OnPuddleSlip` skips targets already marked Sliding. For a slip admitted by that guard, Touch then has a 50% acceptance chance and removes 15% of the *current total* solution, split proportionally among its reagents. The acid's Touch reaction applies `HealthChange` caustic damage at `0.5` per actual polytrinic-acid dose unit. This is typed caustic damage, not the same number of vanilla health points. The reaction does not persistently transfer the puddle's reagents into the target's body.

Consequently, a second admitted super-slippery contact while the target is still Sliding can still produce a slip/stun response but causes **no Touch dose and no caustic damage**. Sliding is the relevant gate, not the vanilla damage cooldown.

## Dose examples

For a fresh 50-unit source containing 25 Space Lube and 25 polytrinic acid:

- One accepted Touch split removes 7.5 units total: 3.75 Space Lube and 3.75 acid. That acid dose produces `3.75 × 0.5 = 1.875` typed caustic damage, equivalent to 0.375 vanilla health if unmitigated.
- After that split, the runtime pool has 42.50 units total: 21.25 acid and 21.25 Space Lube. Once the actor has legitimately been knocked down, the status has cleared, and sliding reconciliation makes the actor eligible again, a later accepted Touch requests `floor(4250 × 0.15f + epsilon) = 637` cents (6.37 units) from this current pool. The local shared `ReagentUnits.split` allocates 319 cents (3.19 units) to polytrinic acid and 318 cents (3.18 units) to Space Lube: both initial shares floor to 318 cents, and the remainder cent goes to the earlier sorted reagent key, `polytrinicacid`. The acid dose applies 1.595 units of typed caustic damage before mitigation. This is the local cent-backed runtime result; pinned upstream `Solution.SplitSolution` can differ slightly in per-reagent remainder rounding. It can occur within the vanilla damage cooldown because reagent `HealthChange` damage bypasses the vanilla damage cooldown tags.
- If five Touch reactions are accepted sequentially against that same depletion pool, the continuous-model calculation is about 6.954 typed caustic damage. Across ten independent eligible 50% rolls, the expected total is about 6.768 typed caustic damage. These are approximations from the continuous model, not a prediction of any particular player's sequence. About five accumulated typed caustic damage could, for example, result from about three accepted reactions (about 4.824 typed damage) plus other accepted/rejected attempts, sliding skips, and depletion effects. The owner's sequence cannot be inferred from the amount alone; the earlier 5.56 estimate is incorrect and should not be reused.

Space Lube also depletes with each accepted proportional split: after four accepted Touch reactions on this source, it is below the `>15` slip-activation threshold. A separate newly prepared source has its own contents and threshold.

## Test and project boundary

A dedicated **real Villager GameTest** proves the later eligible Touch and caustic path described above. The latest worker run reported **109/109 required GameTests passed**. This is server-side automated evidence, not a connected-player test; player caustic response remains unverified in the owner's client. Sliding skips, the 50% roll, depletion, and the small typed amount make visual manual counting particularly unreliable. Record connected-player results only when actually observed; do not claim that the player's response was verified from the Villager test.

The root sprint slide/stun concern is still unfinished, pending the input-authoritative movement foundation, per the owner's decision. It is outside this follow-up: do not work around that boundary, add post-contact glide, or treat this damage clarification as sprint acceptance. See the [connected smoke regression audit](2026-09-24-connected-smoke-regressions.md) and [manual smoke checklist](../manual-smoke-checklist.md) for the broader open retest gates.
