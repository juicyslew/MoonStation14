# Airlocks

## Scope

Owner-authorized powered airlocks, bolts, and a functional maintenance wire UI.
Implement in order: door/controller and atmosphere/power integration; tool-gated
panel and wires; SS14 presentation and connected validation. This is not closure.

## Current checkpoint

**Latest evidence and unresolved acceptance are in `VALIDATION.md`.** The dated
interim results below explain earlier gates and do not supersede that summary.

Core review corrections are implemented: exact two-cell obstruction/crush bounds,
redundant power-wire behavior, and fully prototype-driven receiver demand.
`build/airlocks-validation/core-review-fix-final/` records 28/28 focused JUnit
checks and 7/540 selected GameTests, all seven passing. Those fixtures establish
real APC supply and scheduled transitions, but not timed body-hand prying,
unsafe-close damage/stun or gas-kernel conservation across transitions.

Tool profiles and attributed source assets are prepared and independently
reviewed. Maintenance domain/tools, renderer/synchronization and connected
fixtures are in progress; the menu/networking slice follows the domain APIs.
No human rendering/UI acceptance or completed airlock sprint is claimed.

Later combined unit/compile gate passed 2,341 JUnit tests. The connected
fixtures initially failed because their setup left template barriers in intended
cells and did not check cable placement. Explicit fixture preparation and checked
placement now pass 4/545 selected `airlock_connected` tests. Full airlock and
unfiltered server gates remain pending. A shared-prototype override fixture also
needs batch-end cleanup independent of its delayed callback before final test
acceptance; a successful callback alone is not isolation evidence.

Latest release gate (`build/airlocks-validation/release-gate/`) passes 2,342
JUnit tests and all 12 selected airlock GameTests. Unfiltered registration
selected 545/545; 542 started, but an airlock core fixture's raw AssertionError
aborted the run, so full-suite totals are unavailable. That fixture is being
changed to normal GameTest failure reporting and bounded power readiness.
Prototype cleanup now covers synchronous failure, delayed completion and an
AfterBatch fallback, with selected-batch hook tests. Production assembly
succeeded; connected human UI/rendering acceptance is still pending.

Subsequent full run now completes: 545 selected, 542 executed, 533 passed and
9 failed (`build/airlocks-validation/full-suite-final-gate.log`). However, the
latest focused airlock rerun also has two failures after earlier 12/12 passes;
power admission/activation is still intermittent. **Do not treat the earlier
green focused run as final acceptance.** Investigation is active; neither the
full-suite failures nor the discovery/execution difference are resolved.

## Architecture and invariants

- One authoritative controller owns a two-block-tall, one-block-wide Minecraft
  doorway. Both halves share state and invalidate atmosphere topology. Upper-half
  interactions resolve to the same controller; replacement must not inherit old
  action/menu authority. The footprint is a Minecraft adaptation.
- Typed, inherited prototypes represent supported door, airlock, bolt, receiver,
  panel and wire composition. Live consumers use resolved defaults. Do not declare
  unsupported upstream components merely to reproduce YAML.
- Reuse APC allocation and loaded receiver indexing. Standard receiver demand is
  20 W; unknown/unavailable power is not a free-powered door. Preserve ventilation
  paid-work semantics and existing pressure/fire rules and simulation cadence.
- Reuse controlled-body interaction admission, exact hand/tool identity, timed
  operations and the machine UI toolkit. Client presentation is not authority.
- Collision and airtight state change together according to verified door
  transition behavior. Test both halves and gas ownership/conservation through
  opening/closing; do not silently discard gas to obtain a sealed doorway.
- Bolt state, bolt indicator lights, safety and timing are distinct behavior.
  Panel UI actions must affect the same controller used by normal operation.

## Verified SS14 reference

Checkout `SS14-dev/space-station-14`, pin
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`, verified during research.
Inspect the full `Airlock -> BaseStructure` parent chain and consumers before
designing fields; the list below is an index, not the complete resolved mapping.

- `Resources/Prototypes/Entities/Structures/Doors/Airlocks/base_structureairlocks.yml`
  and `Resources/Prototypes/Entities/Structures/base_structure.yml`.
- `Content.Shared/Doors/Components/AirlockComponent.cs`,
  `Content.Shared/Doors/Systems/SharedAirlockSystem.cs`,
  `SharedDoorSystem.Bolts.cs`, and server `DoorSystem.cs`.
- `Content.Shared/Wires/SharedWiresSystem.cs`, `WiresPanelComponent.cs`,
  `Content.Server/Wires/WiresSystem.cs`, and
  `Resources/Prototypes/Wires/layouts.yml`.
- Server door wire actions, `Content.Server/Power/PowerWireAction.cs`, and
  tool entity prototypes under `Resources/Prototypes/Entities/Objects/Tools/`.

Established behavior: normal powered opening/closing requires raised bolts;
bolts also block ordinary prying. Normal bolt changes require power. A cut bolt
wire can lower bolts on power restoration. Default auto-close is five seconds
and safety is enabled. Panel toggle requires `Screwing` and one second; wire
cut/mend requires `Cutting`, pulse requires `Pulsing`, and cut wires cannot pulse.
Wire colors/functions are assigned through a shared layout cache, not a fixed
color/function table or independently shuffled function mapping for every door.

Door transitions change passability/collision after 0.4 seconds and finish after
another 0.2 seconds. Ordinary airlock pry time is five seconds divided by the
tool speed modifier; powered/bolted modifiers are 9 and 3, but those modifiers
do not bypass the ordinary pry eligibility checks. Default crowbar speed is 1.
Unsafe closing uses the door's crush behavior: 15 Blunt damage and two-second
stun. Safety is therefore functional behavior, not just an indicator.

Power-wire semantics were corrected after inspecting the consumer: both wires
share an action-type cut count. One cut leaves the receiver enabled; both cuts
disable it; mending either restores it unless pulsed. Receiver-disabled state is
distinct from available APC supply. The two power pulses last 30 and 15 seconds.
Pinned upstream mend cancels an active pulse timer without clearing its pulsed
flag (`OnTimedWire` suppresses cancelled callbacks). Preserve and test that
source quirk explicitly rather than silently inventing a restoration callback.

Tool composition: Screwdriver, Wirecutter and Multitool inherit BaseItem and
provide Screwing, Cutting and Pulsing qualities. Crowbar inherits
BaseCrowbar -> BaseItem, with Tool/Prying composition. Checks consume resolved
qualities, not item names. The local wirecutter can provide Cutting without
changing its existing cable-removal behavior.

## Boundaries

Initial functional wire scope: two power wires, bolts, bolt lights, timing and
safety. Access electronics/departments, remote door networks, access logging,
station AI and electrification need their respective owners and are deferred,
not decorative wires or claims of completed parity. No round system exists to
assume a round-reset hook; explicitly document the local layout-cache lifetime.
Do not turn temporary scope gaps into permanent design-departure entries.

Use original SS14 sprites and sounds with attribution. Record unclear audio
provenance in `docs/audio-unknown-provenance.md`. Transition durations, unsafe
closing/crush behavior and detailed power-wire timer semantics were researched
from consumers; implementers must verify their exact event ordering there, not
infer it from labels.

## Acceptance

1. Prototype inheritance/default validation and live consumer coverage.
2. Actual APC-powered door operation, power loss/restoration, bolt gates,
   auto-close, obstruction handling, ordinary prying and panel visibility.
3. Two-half atmosphere sealing/opening and conserved gas through topology changes.
4. Tool-gated panel toggle and server-authoritative cut/mend/pulse, including
   stale controller/menu/tool rejection and shared layout mapping.
5. SS14 visual/audio assets with attribution and concise connected manual steps.
6. Focused tagged fixtures in isolated run directories, followed by full JUnit
   and full GameTest reporting. Tags select tests; they do not isolate fixtures.
   Retain useful failure evidence and distinguish unrelated failures honestly.

The preceding merged tree passed 2,306 JUnit tests. Its last full GameTest run
selected 533 methods, ran 530, and had ten failures; later focused checks ran
138 and passed 136. These are historical baselines, not airlock acceptance.
