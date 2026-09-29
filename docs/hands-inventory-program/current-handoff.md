# Hands/inventory sprint: current handoff

**Status: incomplete; activation is not approved.** This note records the current implementation and verification boundary. Pure models, geometry, authority seams, and passing tests do not constitute a playable inventory.

## Owner-approved policy

- Any controlled `CHARACTER` body admitted through lifecycle authority or the experimental harness may be eligible only when its bound prototype explicitly declares hand capability. Capability is prototype data, not inferred from carrier/entity type.
- Human declares left and right hands. Pig declares no hands and gains none implicitly.
- In actual Creative, preserve the account-owned vanilla contents and restore those exact contents only in Creative, including after death and disconnect/reconnect. Do not copy them into body inventory or drop/lose them on body death.
- There is no body-intrinsic storage. Hands and wearable slots belong to the body; grid/container contents belong to their storage item.

## Implemented foundation (not live inventory)

Implemented are pure hand and grid geometry kernels; body-hands attachment metadata tokens; prototype hand capability; a read-only lifecycle accessor and unified actor resolver; and a pure quarantine model plus token-location ledger.

**None of this provides playable `ItemStack` transfer, vanilla inventory isolation, or inventory UI.** In particular, the pure quarantine model is not a durable account-owned quarantine/recovery mechanism, and the ledger is not proof of conserved live item ownership across real transfers.

## Verification evidence

Coordinator-reported commands both completed with `BUILD SUCCESSFUL`:

```text
.\gradlew.bat test compileGametestJava --no-daemon
.\gradlew.bat runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-hands-inventory-validated
```

Verified `build/gametest-hands-inventory-validated/logs/latest.log`: at 2026-09-28 20:04:10, it reports `161 GAME TESTS COMPLETE` and `All 161 required tests passed`. This run followed the H8 changes and included the later `HandActorAuthority` and quarantine changes. An earlier isolated 161-test run had an unrelated timed-stun test failure; its retry passed.

No manual client test or 20-player test was performed. There is no genuine connected-client authority test. Passing unit/GameTests do not establish connected acceptance.

## Next gate — do not enable partial inventory

Before activation, implement and validate durable Creative quarantine, including persistence ownership and crash recovery, plus server packet and direct-API coverage. Coverage must include spectator `handlePickItem`, lifecycle handoff, and actual item-bearing transfers with ownership conservation. Keep the inventory gate disabled until those safety/authority paths and the relevant acceptance criteria pass; do not ship a partial gate.

The ghost-playability/system-hardening work is a future follow-up **after this inventory sprint is complete**, as recorded in [the deferred ghost playability handoff](future-ghost-playability-handoff.md). It is not part of this sprint.

## Worktree note

The worktree contains an unrelated external `build.gradle` change that was not authored by this sprint. Do not edit or attribute it to this handoff.
