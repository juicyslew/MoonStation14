# Airlock domain slice

Verified source pin: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`AirlockData` models upstream `Airlock.OpenPanelVisible`, `WiresPanel`
(`openingToolQuality` and toggle delay), and `WiresPanelSecurity.WiresAccessible`
as distinct resolved composition. The default opener quality is `SCREWING`;
the live timed-interaction admission and completion both consume that prototype
quality rather than an item-name/constant check.
Upstream `WiresPanelSecurity.WiresAccessible` controls wire-UI availability
(upstream closes the UI when set false); it does not gate physical panel opening
or the panel sprite. The supported local layout defines two power wires plus
bolt, bolt-light, timing, and safety functions. `AirlockBlockEntity` owns
persisted panel-open state; panel visibility additionally resolves the upstream
`OpenPanelVisible` behavior. `AirlockToolInteraction` routes generic resolved
opener quality through its prototype delay and actor/hand-token/root-identity/
revision/reach/LOS-validated timed operation. Cutting/pulsing qualities are
recognized only on a visible panel and remain a seam for the UI transport owner.
The conditional server mutation is not a client/actor authorization API.
Crowbar-pry and panel-tool revalidation share a bounded ray test that accepts
either exact half only when it resolves to the same live root controller.
Pry toggles an eligible unpowered/unbolted door open or closed. Closing retains
the `.4s` passable phase, rechecks the two-block collision bounds at partial
close, then crushes only actors still intersecting; a safe late obstruction
returns the door open and retries after the upstream `.2s` open-time-two delay.
Blocked or power-denied scheduled auto-close retries after one second, and
wire deadlines are advanced before retry logic so this timer cannot starve wire
expiry callbacks.
See pinned `Content.Shared/Doors/Systems/SharedDoorSystem.cs` (`OnAfterPry`,
`CanClose`, `OnPartialClose`) and `SharedAirlockSystem.cs` (`OnBeforeDoorClosed`).
The local door composition has no weld state or welding interaction, so there is
no fabricated welded component/gate; bolt and actual-power eligibility remain
enforced by the local pry service/state.

Wire state and transitions remain in `AirlockState.applyWire` and the existing
revision/root-identity guarded BE API. Layout assignment/color caching and the
wire menu are intentionally deferred to the UI transport owner. AI
electrification/logging, access electronics, and other unsupported SS14 wire
actions are not represented.

Resolved wire parameters feed the live kernel (power pulse lengths, timing
modifiers and safety pulse duration). Actual power loss cancels timing/safety
expiry deadlines while retaining the current modifier, matching source timer
cancellation behavior. Safety-wire mend enables safety even when the resolved
initial value was false; pulse restores it only while its timer remains active.
Effective power loss, including receiver-disable wires, cancels an open door's
pending auto-close; eligible power return starts a fresh configured/modifier delay.
Screwdriver and multitool are registered as one-cell
loose items with footprint entries and use prepared SS14 art.

The renderer-facing BE update DTO explicitly contains root identity/revision,
phase/timing/power/bolt/light/panel appearance state. It never serializes
functional wire-state fields to clients; a later authenticated menu snapshot is
separate.

No registered connected-GameTest helper presently demonstrates an authenticated
player's body-hand route through this new timed interaction. Do not substitute a
production FakePlayer/authentication bypass: the live route composes existing
`HandActorAuthority` and `LiveHands` validation, but connected-player acceptance
remains a manual integration check until lifecycle-owned test admission exists.

Focused validation details are kept in the implementation handoff; connected
acceptance and authenticated player tool admission are separate from these
domain-only checks.
