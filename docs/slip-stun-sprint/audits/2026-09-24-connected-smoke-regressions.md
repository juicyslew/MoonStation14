# 2026-09-24 Connected Smoke Regression Audit

**Outcome: owner connected-world smoke failed; original sprint acceptance remains OPEN.** The observations below are the owner's earlier report. The code-path fixes described here have since been implemented and received automated server-side validation, but there has been no full player/client manual rerun. No connected-client runtime validation is claimed.

## Owner-reported observations

The project owner reported the following from a connected-world smoke:

- The player did not slip alone. A Villager sometimes slipped; the player appeared stunned only when colliding with a Villager or after acid drinking/damage knockback.
- Sword attacks and mining still appeared to be attempted during the short stun. Snowball count predicted down, then snapped back up after server denial.
- Isolated Space Lube felt nearly stationary.
- Disconnect/rejoin failed with `NoSuchElementException` at `EntityActivitySystem.reconcile:80`, reached from `ModEventHooks.onEntityJoinLevel:50`.

These are owner observations, not a reproduced test result. In particular, the occasional Villager response is a positive control observation, not proof that player puddle contact works. The action animations/prediction and later correction are also not proof of successful server actions.

## Traced causes and implemented fixes

The following causes were traced and the corresponding fixes implemented. Automated validation does not replace retesting the owner's connected world.

1. **Rejoin crash — missing stomach activity binding.** `MS14Bridges.STOMACH` lacked an `ActivityBinding`. When a persisted stomach was present, `EntityActivitySystem.reconcile:80` called `optional.orElseThrow()` and crashed. The binding predicate now accepts a nonempty stomach activity, and a FakePlayer join/reconcile GameTest covers the server join path. This fixture does not cover an authenticated reconnect or disk-world lifecycle.
2. **Player-only slip admission — stale movement input and fast crossings.** Packet-driven player movement could leave `getDeltaMovement` stale for server slip-speed evaluation. The accepted-movement path now uses bounded swept-path sampling at 0.125-block intervals for fast crossings (up to 1.5 blocks of movement), and an exit latch prevents the same exit packet from retriggering contact. FakePlayer accepted-movement seam tests exercise this server path without a real network connection. This is not connected-player proof.
3. **Visible action attempts during stun — client prediction and server correction.** Synchronized status is queried client-only and gates attack/continue-mining/use/drop prediction; client-side `isImmobile` is also applied. Server authorization remains authoritative, with denied use/drop inventory menu resynchronization retained for predicted inventory changes. These paths have not been exercised in a connected-client run, so client behavior is not verified.
4. **Space Lube feels nearly stationary — contact friction is not post-contact glide.** Source friction is `F=.05` only while overlapping qualifying puddles; off contact, vanilla stone retention is approximately `0.546`. SS14 sliding is contact-driven, while the generic Minecraft baseline friction differs. No post-contact glide change is implemented. The owner's single-puddle long-distance glide expectation remains **OPEN**; do not invent a post-contact timer or infer a long slide from the source friction value. Compare one puddle with a row of adjacent puddles during connected-client retest.
5. **Villager/FakePlayer movement diagnostic — fixture limitation.** In a stable natural-tick fixture, the unstunned Villager moved about `0.766` and the stunned Villager moved about `1.027`; this does not demonstrate a Villager stun navigation pause. FakePlayer did not tick/move in the dedicated GameTest, either unstunned or stunned. Its zero displacement therefore does **not** prove real-player glide is stuck or stun physics are frozen. Do not use that fixture as a movement-control conclusion.

## Latest postfix validation (2026-09-24)

The coordinator's latest final validation after the postfix changes reported:

- `test --rerun-tasks --no-daemon` — `BUILD SUCCESSFUL`, 11 tasks executed.
- `build --no-daemon` — `BUILD SUCCESSFUL`.
- Isolated `runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run` — `BUILD SUCCESSFUL`; latest log at **2026-09-24 06:57:55–06:57:58** recorded **106 started / 106 required passed**.
- Python syntax parsing succeeded for **603 JSON files**.

These are coordinator-reported automated results, not rerun for this documentation update. GameTests cover the bounded accepted-movement seam, the stomach rejoin/activity binding path, and other server-side cases; they do not replace a real connected-client test. Client-only attack/mining/use/drop gates, client status projection, real inventory correction, authenticated reconnect, and client/server synchronization remain runtime-unverified.

For the later repeated-Touch damage accounting, including the sliding eligibility guard and the real Villager GameTest evidence, see the [repeated Touch damage follow-up](2026-09-24-repeated-touch-damage.md). Its 109/109 required GameTest result is worker-reported server-side evidence only; connected-player caustic behavior is still unverified. The root sprint slide/stun concern remains open pending the owner's input-authoritative movement foundation decision.

See the dated [M0–M5 milestone reports](#milestone-context) for prior bounded evidence and limitations. This audit supplements, rather than overwrites, those dated results.

## Milestone context

- [M0 hook and parity audit](m0-hook-and-parity-audit.md)
- [M1 character prototype and binding](m1-character-prototype-and-binding.md)
- [M2 timed stun and action gates](m2-timed-stun-and-action-gates.md)
- [M3 puddle slip-trigger audit](m3-puddle-slip-trigger.md)
- [M4 slip-triggered Touch audit](m4-slip-triggered-touch.md)
- [M5 validation and open gates](m5-validation-and-open-gates.md)
- [Latest owner retest checklist](../manual-smoke-checklist.md)

## Retest boundary

Before rejoin, restart using matching client/server builds and back up the world/player data. The owner checklist first rechecks rejoin, then isolates player slip from Villager collisions, retains the Villager as a positive control, explicitly tests a fast crossing, checks denied actions and repeats rejoin. Compare one puddle with a row of adjacent qualifying puddles to characterize contact-dependent behavior; the single-puddle long-distance glide concern remains unresolved, not fixed by a timer. No owner retest after these fixes is claimed here; all connected-client retest remains **PENDING**.
