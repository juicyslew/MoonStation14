# Player body-control experiment

## STATUS

Reconstructed 2026-10-03. Closed by owner direction as a bounded ghost/character-mind control experiment, not full target acceptance. Closure does not authorize resumption.

## ARCHITECTURE

BodyControlRegistry owns exclusive mind/harness bindings. Operator ghost/Mob control remains behind startup-sampled, default-off experimentalMindGhostControl. Vanilla player is a HUMAN carrier, not the separate ghost Mob. Target single-mind active play, automatic join possession and production lobby are not established by this slice.

## IMPORTANT FILES

`closure.md`, `architecture/single-mind-harness-movement-route.md`, M1/M2 and operator-possession audits; `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/BodyControlRegistry.java`, `server/GhostMobHarnessControl.java`. See also `../player-character-lifecycle-sprint/HANDOFF.md`.

## SS14 CORRESPONDENCE

Harness/motor are Minecraft adaptations. Human/Pig prototype parity was deferred. SS14 body composition and SharedMindSystem separate entity anatomy from mind ownership; a carrier/harness binding is not full correspondence.

## DECISIONS / WHY

Keep legacy routes/default-off gates until replacement proof. Carrier-policy exclusions are bounded; ticks are not forwarded wholesale to the body. No universal Mob eligibility or automatic lifecycle is implied.

## KNOWN GAPS

Full connected transition/latency/second-client evidence, collision parity, lease recovery, carrier identity separation, and historical HUD retest remain unproven. Later hands/HUD acceptance is scoped separately, not retroactive body-control protocol acceptance.

## VALIDATION

Historical focused tests/compile; 132-GameTest run included two atmosphere failures, not full pass. Owner Villager/Pig observations are bounded reports, not complete epoch/protocol proof. No fresh tests run.

## NEXT WORK

Any new control work needs authorization and the closure carry-forward audit. Reconcile later lifecycle/hands consumers before changing registry or carrier policy.
