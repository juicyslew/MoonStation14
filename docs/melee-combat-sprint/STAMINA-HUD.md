# Stamina HUD follow-up

Owner-requested local presentation: a stamina text row beneath the existing
health/hunger/thirst rows. This is separate from the reopened melee visual and
crash fixes; it is not acceptance of either.

Reuse `MoonStation14Client.renderAlerts`, `BodyHudSourcePolicy`, and
`TopRightHudLayout`. Read the selected body's resolved `StaminaPrototypeComponent`
and existing synchronized `STAMINA` attachment. Missing neutral state is
`StaminaState.EMPTY`, without client-side attachment creation. Display remaining
capacity, not accumulated damage; use the threshold actually consumed by the
local stamina reducer. Do not invent client-side regeneration or another packet.

Pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91` inherits human stamina
through base mob composition. `StaminaComponent` and `SharedStaminaSystem` use
damage increasing toward the critical threshold; upstream HUD uses a seven-level
stamina alert, not this text row. The additional row deliberately follows the
port's existing physiology display. No new sprite attribution is required.

Manual check: with HUD visible, confirm a full stamina row beneath thirst, then
take stamina damage and watch remaining capacity recover with server updates.
Ghost/no-body sessions must not show the carrier's stamina.

All three source sets compile. The focused batch, including projection and
shared HUD-source tests, passes 480 tests; source review passes. Connected visual
validation remains pending. No owner acceptance is claimed.

The UI readiness catalog includes the new projection and shared body-source
tests. Disarm flight's real collision-context constructor and participant HUD
tests are also selected, so the readiness run exercises the changed boundaries.
Selected UI readiness passed in `run_656334ba8e37aa22c27109db`: 31 suites,
190 tests, zero failures/errors/skips. That run's capture failed before the new
disarm scenes and may have been affected by desktop focus interference; it is
not connected visual acceptance of the stamina row.
