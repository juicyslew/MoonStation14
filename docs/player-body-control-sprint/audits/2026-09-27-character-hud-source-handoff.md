# 2026-09-27 possessed-character HUD source handoff

**Status: bounded client-side HUD projection implemented; connected owner retest pending.** The owner reported that while possessing a Mob they saw no MS14 slipped, knocked-down, Hunger, or Thirst alerts, and no health, hotbar, or other HUD beyond noise messages. The source-level handoff below documents the implemented correction and its limits; it does not claim that the owner has retested the change in a connected session.

Related records: [sprint instructions](../../../instructions-player-body-control-sprint.md), [active harness carrier policy](2026-09-27-active-harness-carrier-policy.md), [committed CHARACTER loss recovery](2026-09-27-committed-character-loss-recovery.md), and the [vanilla hearts and future action-capabilities decision](2026-09-27-vanilla-hearts-and-action-capabilities.md).

## Source cause and owner decision

The custom `RenderGuiEvent.Post` overlay read `LocalPlayer` for alerts, sliding, and status. During possession that player is the spectator carrier, not the controlled Mob. Separately, vanilla spectator presentation hides the health and hotbar HUD. Thus the custom overlay lacked the possessed body's state while vanilla did not provide the expected character HUD.

The owner explicitly chose to **hide the hotbar until real body-owned hands exist**. Do not show the spectator carrier's inventory and do not imply action authority. XP was explicitly out of scope/ignored.

## Implemented source behavior

- `GhostControlClient.ownedCharacterForHud` accepts only the exact committed, locally owned `Mob` and camera association, while excluding the local mind carrier through pending handoff state. It is not a generic camera-target lookup.
- `MoonStation14Client.renderAlerts` reads existing synchronized Mob alert, status, and sliding attachments for the possessed body. While ghost/pending, it suppresses spectator `LocalPlayer` character labels; outside a session the normal `LocalPlayer` overlay is restored.
- For an owned possessed Mob, the custom top-left projection displays health from that Mob's health/max-health and optional hunger/thirst only where existing attachments and eligibility permit. Gas visuals remain independently rendered.
- F1 Hide GUI is respected. No hotbar, inventory, hands, XP, or action-authority UI is added.
- No new payloads, prototypes, or thermal changes were part of this HUD handoff.

This is temporary text projection, not Minecraft vanilla hearts/hotbar and not a full SS14 UI. Do not infer absent data as a default status/value. In particular, `thirst_eligible.json` covers only player/Villager, while the current Pig prototype disables stun/slip. There is no sourced Pig Hunger/Thirst eligibility or Pig slip behavior to display or claim; do not fabricate Pig Hunger/Thirst or Pig slip.

## Validation and remaining evidence

Focused `player_body_control` and `ServerClassloadingTest` JUnit checks and `compileGametestJava` passed; `git diff --check` passed. The latest dedicated 132-GameTest run had two concurrent atmosphere failures. If citing that run, its failure evidence is in `build/gametest-active-body-routing-retest/logs/latest.log`, lines 277–280; it predates this HUD change and provides no evidence about client visuals.

Still required: a connected owner retest of placement, synchronized body state, F1 Hide GUI, and transitions into/out of possession, including no-session and ghost/pending behavior. Until then, source implementation and automated compilation/tests are not connected HUD acceptance. Preserve concurrent atmosphere work; no atmosphere behavior is changed or validated by this record.
