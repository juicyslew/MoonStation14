# Airlock composition and boundaries

Reference checkout: `C:\Users\William\Documents\SS14-dev\space-station-14`,
verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
This is a mapping of the implemented slice, not full-airlock acceptance.

## Resolved upstream entity

`Airlock -> BaseStructure`; BaseStructure has no further parent. It contributes
anchored Transform, Clickable, GravityAffected, static Physics, Fixtures,
Pullable and the Structure tag. Airlock overrides its fixture and sprite and
adds Door, Airlock, DoorBolt, ApcPowerReceiver, ExtensionCableReceiver,
WiresPanel, WiresPanelSecurity, Wires and Airtight, alongside access/electronics,
electrification, device/network/link, damage and construction behavior.

Entity sources: `Resources/Prototypes/Entities/Structures/base_structure.yml`
and `Structures/Doors/Airlocks/base_structureairlocks.yml`.

## Supported local composition

Local `standard_airlock -> base_airlock` definitions live under
`data/moonstation14/moonstation14/airlock/`. `ModAirlocks.TYPE` is registered in
`PrototypeRuntime`; the controller resolves its prototype at runtime.

| Local composition | Resolved defaults and consuming behavior |
| --- | --- |
| Door | 0.4 s collision/passability transition, then 0.2 s final transition; 0.45 s denial; crush 15 Blunt and two-second stun. Controller/state consume timings; damage/control systems consume injury and stun. |
| Airlock | Safety and auto-close enabled, five-second auto-close, pry modifiers 9 powered / 3 bolted, OpenPanelVisible false. |
| DoorBolt | Bolt state gates normal opening, closing and prying; bolt lights are separate and require power, lowered bolts and enabled lights. |
| ApcPowerReceiver / ExtensionCableReceiver | 20 W APC demand with inherited overrides consumed by APC allocation and fresh-meter admission. Independently inherited ExtensionCableReceiver reach defaults to 3 locally; it is a Minecraft power-index adaptation, not an asserted upstream default. |
| WiresPanel | Screwing opening quality, one-second toggle delay and independently resolved open/close sounds. Timed tool completion revalidates the body, source tool and target controller before toggling and playing its sound. |
| WiresPanelSecurity | WiresAccessible true; no fabricated department credential check. |
| Wires | Two power actions, bolt, bolt-light, timing and safety. Resolved pulse durations/modifiers feed the state consumer. |

The block/controller implement supported collision and airtight behavior for both
halves. Anchoring is hosted by Minecraft block ownership; unsupported upstream
components are not added as inert YAML-shaped metadata.

At pinned upstream revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
`Airlock -> BaseStructure` (no further parent) declares separate
`ApcPowerReceiver` and `ExtensionCableReceiver` components in
`Resources/Prototypes/Entities/Structures/Doors/Airlocks/base_structureairlocks.yml`.
`Content.Server/Power/Components/ExtensionCableReceiverComponent.cs` defaults
`ReceptionRange` to 3. Locally, each is a separate typed record/codec and
prototype component; runtime cable-node lookup reads the resolved extension
receiver while APC watt demand remains on the APC receiver.

Receiver lookup reconciles missing graph coverage only for chunks already
available in the server's FULL cache. It queues ordinary bounded chunk refresh
and topology rebuild, returning unknown until that work completes. It never
force-loads a chunk or grants power based solely on physical chunk presence.
Airlocks, lamps and ventilation receivers share this bookkeeping path.

Tools use a separate resolved `tool_profile` family. Screwdriver, Wirecutter and
Multitool inherit BaseItem upstream; Crowbar inherits BaseCrowbar -> BaseItem.
Tool qualities are Screwing, Cutting, Pulsing and Prying; crowbar pry speed
defaults to one. The local wirecutter supplies Cutting while retaining its
existing cable behavior. See `TOOLS.md` for sources and consumer paths.

Ordinary crowbar prying toggles an unpowered, unbolted door open or closed;
completion during another transition does not reverse that transition. Closing
rechecks occupancy when the door becomes solid. A safe late obstruction leaves
it open; unsafe crush damage/stun occurs at that partial-close step, not when
closing starts. Ordinary dropped BaseItem tools/bags are not obstructions in
pinned SS14: their ItemMask lacks the LowImpassable bit required by GetColliding.
The local living-body obstruction policy matches that ordinary-item case.

### Crush behavior

At unsafe partial close, living occupants are captured before the two cells
become solid, take one resolved 15 Blunt damage application, and receive the
shared paralyze projection for `DoorStunTime + OpenTimeOne` (2.4 seconds with
the resolved defaults). The root controller maintains a transient victim UUID
set; only those entities receive an empty collision shape from this two-cell
door. The exemption is consumed by real entity collision-context queries,
clears when a victim exits or the door opens, is removed with the root, and is
included in the existing BE update tag for matching client collision. A one-time
position guard restores only an observed upward displacement during
solidification; it does not teleport victims on later ticks. This addresses
vanilla block suffocation locally through AirlockBlock's `isSuffocating=false`;
other blocks retain normal suffocation behavior. After two seconds the root
rechecks fresh power/bolt admission before reopening. If admission is
unavailable, the door remains closed and the ordinary gates are not bypassed.
The controlled body movement route calls `body.move(MoverType.SELF, ...)` in
`GroundedHarnessWorldStep`; the root-bound entity collision exemption is consumed
by that vanilla collision query rather than bypassing the motor or disabling
all actor physics.
This is a scoped Minecraft adaptation, not SS14's in-place entity-transform
guarantee, and does not claim physical prone/crawl parity.

## Wire and timer invariants

- Power actions share a cut count: one cut leaves the receiver enabled, both
  cuts disable it, mending one restores it unless pulsed. Receiver disablement
  is not the same thing as actual APC supply.
- Power pulses last 30 / 15 seconds; an already active pulse is not extended.
  Pinned upstream mend cancels the pulse timer without clearing its pulsed flag;
  cancelled callbacks are suppressed. The resulting latch is preserved, not
  silently repaired.
- Bolt cut lowers bolts when powered and on power restoration. Bolt pulse
  toggles when powered, but can only lower bolts while unpowered.
- Bolt-light cut/mend disables/enables lights; pulse toggles lights.
- Timing cut sets modifier .01 and cancels its timer; mend restores 1; pulse
  sets .5 for 30 seconds. Safety cut disables checks; mend enables; pulse
  disables for 30 seconds. Power loss cancels timing/safety expiry callbacks
  without inventing a restoration callback.
- Cut/mend require Cutting, pulse requires Pulsing, and cut wires cannot pulse.
  These actions have zero additional tool-action delay, unlike panel toggling.

Consumer references: `Content.Shared/Doors/{Components,Systems}/`,
`Content.Shared/Prying/`, `Content.Shared/Wires/`,
`Content.Server/Wires/WiresSystem.cs`, `Content.Server/Power/PowerWireAction.cs`,
`Content.Server/Doors/WireActions/`, and `Resources/Prototypes/Wires/layouts.yml`.

## Minecraft adaptations and scope gaps

- One SS14 tile becomes a one-block-wide, two-block-tall Minecraft doorway with
  one lower controller. Upper/lower hits resolve to that controller; both cells
   change atmosphere topology. Original sprite art is adapted to a simple 3D
   frame and horizontally sliding leaves. Vertical opening is deferred. See
   `ASSETS.md` and `RENDERING.md`.
- Layout assignment has server lifetime because there is no accepted round
  owner. Colors/letters and function assignment are shared per layout; per-door
  seed affects cosmetic wire shape only. UI wire rows must not disclose function
   identities. See `WIRE-UI.md`.
- Access electronics/departments, welding/construction, remote networks, actual
  access logging, and AI services are deferred. Log circuit-control indication
  and AIA continuity are supported, without claiming those services exist.
  Electrification is implemented within the boundaries in `ELECTRIFICATION.md`.
- Automatic bump-opening consumes the resolved Door.bumpOpen default and the
  actor's inherited DoorBumpOpener character component. A server Entity.move
  observer detects actual blocking contact, not proximity. It also observes the
  controlled-body motor's explicit move calls. Normal power/bolt gates remain;
  the current local door policy is all-access with no PDA/ID requirement. See
  `BUMP.md` for composition and contact handling.
- Render-only BE replication deliberately excludes functional wire maps. The
   wire window uses its own authenticated snapshots and server-side selectors.
   Its dedicated body-bound session leaves the normal inventory menu active;
   ordinary authority/reach/root checks apply to every query and command.
   Movement and gameplay actions use the shared gameplay-window input contract.
   Clicking a wire lets the server choose cut/mend/pulse from the current held
    tool, rather than upstream's wire-body/contact hit distinction. AIA and Log
     lamps show circuit status. Log pulse disables its circuit indicator for the
     inherited 30-second timeout; Cut cancels and latches disablement, power loss
     cancels the timeout without reenabling, and Mend reenables. Actual access-log
     entries remain unsupported: no local AccessReader or event-log service is
     fabricated. AIA pulse remains an intentional no-op, matching the pinned
      `Content.Server/Access/LogWireAction.cs` pulse timeout; upstream
      `AIInteract.Pulse` is empty. Stun blocks new command effects but does not
      close a valid wire UI or deny its state queries; the authoritative rejection
      remains visible until a later accepted user action.

Blocked auto-close attempts retry after one second, matching SharedDoorSystem's
NextState rule. Power loss cancels scheduled auto-close; power restoration
reschedules the configured delay for an eligible open, unbolted door. Wire-timer
processing must remain independent of failed close attempts.

## Evidence boundary

For current results, use `VALIDATION.md`; the interim gates below are historical.

Core correction gate: 28 focused JUnit checks passed and 7/540 selected
GameTests passed. Later domain checks passed 14/14 JUnit; their first 9/542
GameTest run exposed two connected-fixture failures. Fixture and rendering-test
corrections require a combined rerun before acceptance. See `SPRINT.md`,
`CONNECTED-TESTS.md` and retained logs under `build/airlocks-validation/`.

No existing headless helper establishes a registered, authenticated player/body
session for new tool/menu admission. Trusted NPC/domain tests do not substitute
for that boundary; connected player tool use and UI/rendering need manual
acceptance. No production authentication bypass was introduced.
