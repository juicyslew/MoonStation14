# Airlock electrification parity and implementation boundary

Reference checkout: `C:\Users\William\Documents\SS14-dev\space-station-14`,
verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Resolved composition

The airlock prototype is `Airlock -> BaseStructure` (no further parent).
`base_structureairlocks.yml` composes `Electrified` and `ApcPowerReceiver`;
`base_structure.yml` contributes the structure base composition. The airlock's
Electrified defaults are `enabled: false`, `usesApcPower: true`,
`requirePower: true`, `shockDamage: 7.5`, `electrocuteTime: 5`,
`probability: 1`, `siemensCoefficient: 1`, and touch/bump/using/attack enabled.
The activated visual's server lifetime is one second; power-wire pulse durations
are 30/15 seconds and come from wire action data, not Electrified fields.
The full authored composition and receiver demand are in
`Resources/Prototypes/Entities/Structures/Doors/Airlocks/base_structureairlocks.yml`;
the component defaults are in
`Content.Shared/Electrocution/Components/ElectrifiedComponent.cs`.

## Wire ordering and live consumers

`Content.Server/Power/PowerWireAction.cs` calls `TrySetElectrocution` before
changing the shared power-wire cut count. Thus the first cut shocks before the
count moves to one and remains powered; the second cut can shock before the
count moves to two and power is removed. Mend also attempts the shock before
decrementing; electrification clears on wire update only when all wires are
mended. This means mending one of two cut wires restores power while remaining
electrified. Pulse enables/attempts shock before `PowerPulse`; the pulse timers
are 30/15 seconds. Preserve upstream's pulse/mend timer-cancellation latch
quirk rather than synthesizing an expiry.

`PowerWireAction.Update` separately calls `UpdateElectrocution`: it schedules
the 30-second `ElectrifiedCancel` timeout only when the pulse is active, power
is present, and not all power wires are cut. Therefore the state transition for
`Pulse` alone has no cancel deadline; the post-action update computes one. Mend
cancels this timer before changing cut count; a subsequent Update may schedule
it again if those same powered-pulse conditions hold. `AwaitElectrifiedCancel`
only disables electrification when all power wires are mended.

`Content.Server/Electrocution/ElectrocutionSystem.cs` consumes the component:
power is checked against fresh APC state, update expiry controls the activated
state, and contact/attack/hand/using routes attempt shocks. Collision uses
start-contact, not per-tick damage. Root halves in Minecraft must canonicalize
to one root contact. `DoCommonElectrocution` scales integer damage by the
Siemens coefficient (truncating), and coefficients above .5 apply the stun
path. A 7.5 configured source damage therefore must remain a float until
coefficient arithmetic to produce 7 at coefficient 1. Insulation attempts are
separate from resistance bypass.

## Local adaptation and runtime integration

Local `AirlockData` resolves a typed `ElectrifiedData` component by inheritance;
the root BE consumes its resolved record (including defaults). The resolved eight
wire layout carries power behavior in its existing two power actions, not a
separate fabricated electrification wire. Fresh fully paid 20 W APC supply
is required for shock; unknown, disabled, or partial supply is quiet. Server
admission precedes any shock; rejected/stale actor commands do not shock. The
actor is the authenticated controlled body, not a held-item carrier. Only
distinct eligible contacts trigger, without per-tick damage. `applyWireActor`
is the admitted source-aware path; actorless fixture mutations deliberately
update state without inventing a shock actor. The wire menu supplies the
authenticated controlled body after its normal session/reach/tool/revision
admission. E interaction, tool use, and movement collision edge paths use that
body; the two vertical block hits canonicalize to the root and repeated
movement contact is memoized until departure. An admitted contact's successful
shock is an effect, not a veto: E activation and eligible bump still attempt
their normal door activation, and qualified tool/menu use continues through
normal validation. Menu admission precedes its contact effect; a rejected menu
open falls through without duplicating the shock. Requests rejected at initial
body-action admission (including already-stunned requests) still have no
contact effects. This matches pinned upstream `ElectrocutionSystem`'s
`OnStartCollide` (line 155) and `OnHandInteract` (line 172), which do not cancel
those events, and `SharedInteractionSystem` (lines 517, 529), which dispatches
the hand event before interaction activation.
The current body interaction API has no admitted melee-attack request, so the
upstream `OnAttacked` route is not wired to carrier-side vanilla block attacks;
those cannot be treated as controlled-body actions safely. The supported
touch, bump, using, and accepted wire-action routes are server-body attributed.

The local electrocution effect currently intentionally rejects a
`bypassInsulation=false` chemical effect when it cannot resolve insulation.
Airlock shocks must not misuse the bypass flag. Their source path needs a
separate float-damage/coefficient API that applies resolved supported
insulation before truncation while retaining chemical policy. Local equipment
protection composition is incomplete (in particular gloves); absence of an
implemented insulation component resolves to coefficient 1. This is an
explicit parity gap, not evidence that gloves protect locally. Do not invent a
glove slot or armor protection for this feature.

The source-shock API in the shared
electrocution boundary: it accepts float damage and a resolved coefficient,
truncates only after multiplication, and delegates coefficient-above-.5
paralyze/knockdown projection to `CharacterControlSystem.applyParalyze`.
Chemical-effect admission and insulation policy remain unchanged.

Enable/disable OGG feedback reuses upstream `airlock_electrify_on/off.ogg`
from `Resources/Audio/Machines/attributions.yml` (CC0-1.0, ScarKy0,
`https://github.com/space-wizards/space-station-14/pull/32012`). Successful
nonzero shocks emit the pinned SS14 sparks collection and a short server-side
`ELECTRIC_SPARK` burst at the airlock root; no carrier-position or per-tick
particles are used. Sparks audio provenance is unresolved and recorded in
`docs/audio-unknown-provenance.md`; this is not a license claim.

Required focused coverage: resolved inheritance/defaults and live consumer;
two power cuts, mend, pulse/timer latch, fresh-power gate; source damage and
coefficients 0/.5/1; and unique contact/no-repeat behavior.
The crush-close safety path keeps its existing pre-solidification victim
capture, one-crush damage/paralyze, and 2.4-second paralyze projection. It
finishes the normal second close transition before the separate two-second
from-crush reopen deadline. If power is unknown or unavailable at that release
deadline, the door stays sealed, the victim exemption persists, and a bounded
20-tick retry is scheduled. This avoids a past-due transition tick loop and
never invents power or drops the victim into a newly solid door.
