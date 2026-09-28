# Player Body Control Sprint Closure — 2026-09-27

**Authoritative disposition:** **closed by owner direction as bounded experimental ghost/character-Mind control delivered; target single-Mind active-play architecture and gameplay/lifecycle acceptance remain unfulfilled**. This records the owner's requested closure of the bounded experiment; it does not accept the sprint's original target architecture, certify full connected acceptance, or authorize follow-up implementation. The dated milestones and audits linked below are historical evidence of work and limits, not full acceptance.

## Delivered bounded experiment

- `BodyControlRegistry` supplies the Mind/Mob Harness registry and exclusive bindings. The shared experimental path remains behind the COMMON, server-startup-sampled, default-off `experimentalMindGhostControl` gate and operator commands; this is not automatic join-time control.
- The bounded operator sequence supports ghost to configured Villager/Pig body and return to a fresh ghost, using an actor-targeted shared protocol, movement motor, and prediction/reconciliation path. Lease, slip handling, and possessed-Mob look/walk animation support are part of the delivered experiment. `host_entity_types` is retained for manual testing.
- The bounded active-carrier policy skips the bounded status, alerts, reagents, fire, hunger, and thirst activities on the active carrier; the active body continues to tick on its own existing scheduler. This policy does not route or forward ticks to the active body. A temporary possessed-body HUD projects health and eligible existing status/nutrition alerts.
- The owner reported connected Villager and Pig control/return and possessed-body-loss testing, saying “This is done. I did this,” and reported smooth possessed-Mob look/walk. These are owner reports only: no captured epoch/protocol, teleport, or AI logs were supplied. The HUD implementation has **no owner retest**. These reports do not establish broader connected acceptance.

See the [sprint instructions and historical milestone record](Instructions.md), [active-harness carrier policy audit](audits/2026-09-27-active-harness-carrier-policy.md), [operator possession handoff audit](audits/2026-09-27-operator-possession-handoff.md), [committed-character loss recovery audit](audits/2026-09-27-committed-character-loss-recovery.md), [possessed-character HUD handoff](audits/2026-09-27-character-hud-source-handoff.md), and the [single-Mind/harness architecture decision](architecture/single-mind-harness-movement-route.md).

## Explicit implementation and acceptance boundaries

- The carrier retains persisted `HUMAN` identity. This is the owner's explicit decision until a custom-created character Mob Harness exists. Partial carrier-policy filtering is delivered, but removal of stored `HUMAN` identity and complete carrier identity separation are not.
- `host_entity_types` remains for manual testing. Legacy `experimentalVerticalSliceMovement` remains default-off and retained until replacement proof; it is not retired or deleted. Effective friction 20 is unchanged.
- The hotbar remains hidden pending body-owned hands. Vanilla hearts are deferred; the temporary HUD is not a full body UI. Thermal/atmosphere work remains with its separate owner and is untouched. SS14 prototype YAML importing/parity is deferred.
- There is no accepted target single-Mind active-play lifecycle or complete gameplay identity/action contract. Implementation presence, focused tests, and bounded owner reports do not prove login, respawn, reconnect, or lobby lifecycle; no lobby is invented here.

## Validation evidence and owner report

Worker-reported focused player-body-control JUnit checks and `compileGametestJava` passed after the HUD source change. No post-HUD dedicated-server GameTest or connected-client visual acceptance is claimed. The isolated dedicated-server retest at `build/gametest-active-body-routing-retest/logs/latest.log` completed 132 GameTests and had exactly two concurrent atmosphere failures: `coveredexteriorsealingandbreachrespectownership` and `fullwallopeningdrainsmorethanoneblockopening` (lines 277–280). This is **not** a full GameTest pass and is not evidence of connected acceptance. The test-only timing adjustment is described in the [active-harness carrier policy audit](audits/2026-09-27-active-harness-carrier-policy.md); it did not establish broader gameplay behavior.

The bounded owner reports are preserved as reports, not converted into authenticated handoff, epoch, target/AI, teleport, visibility, latency, or lifecycle evidence. In particular, the source-level HUD change lacks owner visual retest. See the [HUD handoff](audits/2026-09-27-character-hud-source-handoff.md) for what was implemented and what remains unverified.

## Explicit carry-forward; not accepted by closure

- Account/profile/body lifecycle across login, respawn, reconnect, and lobby (if/when a server-owned lobby exists); genuine body-owned status, health, actions, and hands; direct effects; and a generic actor-capability contract.
- Connected failure/mode/teleport/latency/visibility/second-client/HUD testing, including connected HUD retest and evidence of ownership epochs, cleanup, and transfer behavior.
- Passive-carrier thermal behavior as a separate atmospherics-owner concern; lease-failure handling and ghost `noPhysics` collision parity.
- Replacement proof and residual-reference audit before retiring the legacy route or removing its default-off gate.

The older open milestones and acceptance wording in the [sprint instructions](Instructions.md), dated audits, and architecture records remain historical evidence/specification, not proof those gates passed and not an instruction to reopen work under this closure. This disposition closes the bounded sprint by owner direction while preserving the unfulfilled architecture and acceptance work for future authorization.
