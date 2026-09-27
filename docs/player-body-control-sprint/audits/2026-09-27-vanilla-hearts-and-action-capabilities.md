# 2026-09-27 vanilla hearts and future action capabilities

## Vanilla hearts decision: defer

The request was to use vanilla-like hearts for a possessed Mob, including hearts that reflect its actual maximum health, only if Minecraft/NeoForge provides a simple route. Source checked against the pinned Minecraft 1.21.1 / NeoForge 21.1.224 merged `Gui.java` does not provide one: `getCameraPlayer()` returns a `Player` only when the camera entity is a `Player`; `renderHealthLevel` reads `Player` health and `MAX_HEALTH` and applies the spectator gate; and the private `renderHearts` renderer accepts a `Player`, not a generic `LivingEntity`. NeoForge GUI-layer replacement and `PlayerHeartTypeEvent` do not make that player-specific renderer accept a Mob.

Setting a Mob's supported `MAX_HEALTH` therefore does not make vanilla hearts display for a Mob camera. A custom heart layer or invasive mixin is not a simple/safe extension. Per the owner's bounded decision, **do not implement hearts now**: leave the existing owned-Mob BODY HEALTH text projection in `MoonStation14Client` unchanged. It reads the controlled Mob's health and max health; do not fake health onto the spectator carrier or add new health synchronization for hearts.

The current text is explicitly temporary and is not vanilla hearts. The owner also chose to keep the hotbar hidden until genuine body-owned hands exist; do not expose the spectator carrier's hotbar or imply action authority. See the [possessed-character HUD source handoff](2026-09-27-character-hud-source-handoff.md), which still requires connected owner retest.

## Future action-capability boundary: design note only

Genericize mob-harness abilities/actions behind a server-owned Mind → active MobHarness relationship. A future design should use typed, prototype-derived optional capabilities and default-deny when a capability is absent or unsupported. It must have one shared actor-targeted action dispatch for players and AI, with server validation of target, range, status, inventory, and ownership epoch. For example, placing a block requires supported hands/inventory, while drinking liquid from the floor may not. Capability data must be consumed and enforced by shared systems; do not add species-specific motor/action code or a JSON boolean that has no consumer.

This is a future boundary, not authorization to implement actions, hands, or inventory in this HUD task. Stop before those systems; schedule them as a separate sprint per the [next-systems roadmap](../../next-systems-roadmap.md). No source, config, prototype/JSON, or atmosphere changes are part of this decision.
