# Cabin ventilation installation plan

Status: authored installation now in source; runtime validation pending. This
replaces the lone pipe/unlinked-alarm setup, which was not ventilation. M2 eye
exposure still samples world cells and is a separate unresolved adaptation.

## Composition and reuse

Verified SS14 checkout: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`GasVentPump` / `GasVentScrubber` inherit `GasUnaryBase` and `AirSensorBase`;
`AirAlarm` inherits `BaseWallmountMachine -> BaseWallmountMetallic -> BaseWallmount`.
Existing local resolved `gas_vent`, `gas_scrubber`, `gas_unary_base` and air-alarm
prototypes supply the supported components. No new prototype is needed.
Defaults: devices enabled, 5 W each; alarm 100 W; receiver reach 3; vent external
bound 101.325 kPa; scrubber 0.2 m³/s with the full inherited filter list. Preserve
those settings; select a separate return lane through ordinary configuration.
References: `GasVentPumpComponent.cs`, `GasVentScrubberComponent.cs`,
`SharedVentScrubberComponent.cs`, and the powered-ventilation handoff/source map.

Reuse `FiniteVentilationTransfer`, existing paid intervals and alarm UUID links.
Trusted fixture placement/admission is already implemented in
`VentilationFixtureService`, `LoadedPipeGasOwner`, `LoadedSealedWallSegment` and
`LoadedSealedWallGasOwner`. Share placement pieces used by both current builders
rather than copying the demonstration fixture or creating another gas solver.
UUIDs identify **individual lane-node incarnations**; network equalization does
not merge these identities. Preserve the original pipe's live owner.

## Feasible local geometry

Important correction: concealed segments are straight, with only the clicked
face and its opposite as ports (`LoadedSealedWallSegment.prepareOne:81-84`). A
north/south concealed wall run cannot turn west into a device. Do not add an
unsupported concealed junction or silently assume six-way ports.

Use the already-supported vertical fixture mounting adaptation:

- Vent `(5,2,3)`, facing DOWN; room endpoint `(5,1,3)`.
- Scrubber `(5,2,5)`, facing DOWN; room endpoint `(5,1,5)`.
- Vertical concealed supply `(5,3,3)` and `(5,4,3)`, with UP/DOWN ports.
- Exposed Primary supply line `(5,5,z=3..8)` in the maintenance space.
- Vertical concealed drop `(5,4,8)`, then exposed `(5,y=3..1,8)` connects to
  the existing pipe `(6,1,8)`.
- Separate Secondary return: vertical concealed `(5,3,5)` and `(5,4,5)`, ending
  at a Secondary lane on the exposed host `(5,5,5)`. Primary and Secondary on
  the same host remain independent inventories.

The power support hosts `(5,4,z=3..8)` currently use Stone. Author eligible Steel
Wall supports **before** installing cables/pipes, retaining the same cable faces
and power graph. Room ceiling and outer hull remain sealed; devices replace room
AIR, not airtight wall blocks. Existing ceiling APC extensions serve both devices.
The current alarm `(5,4,11)` explicitly links both live device UUIDs.

## Finite charge and validation

The original 2 mol pipe charge is only about 25 kPa, below the room's 79 kPa.
Retain that node/identity, and seed a finite breathable supply once through the
six admitted exposed upper nodes using the existing trusted fixture gas API.
A total supply near 1,000 mol (including the original 2) has enough headroom to
raise the 72-cell room toward the prototype bound and leave a finite reserve.
Distribute it rather than placing the whole charge in one 0.2 m³ node. The return
starts vacuum; it is not a deletion sink. Never reseed during movement or operation.

Required checks: resolved inherited defaults consumed live; both devices receive
5 W and alarm 100 W; exact two links; supply/return component separation and node
UUID stability; real paid vent release and CO₂ capture while moving; conserved
species/enthalpy across the room and all owned pipe nodes; no new room leaks.
Actual pressure/capacity results, restart and connected presentation remain unproved.

## Current implementation and evidence boundary

`ShuttleCabinFixture` places the devices before collecting and initializing finite
room cells. `Built.supplyPipeNodes()` and `returnPipeNodes()` describe the authored
13-node Primary and three-node Secondary networks. The return ends at its exposed
upper node; it intentionally does not continue to the old Primary pipe. The supply
includes the concealed `(5,4,8)` drop. `FixturePipeAdmission` reuses existing
admission owners and is shared with `VentilationFixtureService`; adding Secondary
to the exposed host preserves its Primary identity.

Existing transfer-level tests establish species/enthalpy conservation at the
transfer boundary. Live room/pipe species aggregation can validate finite ownership,
but gas-only energy does not include gas/wall heat exchange. A whole-cabin gas-plus-
wall thermal ledger remains unverified; rounding allowances must not disguise it.

Focused source checks now reuse `CabinGasInventorySnapshot` in both stationary
and moving fixtures. Immediate authored gas values are retained as setup checks;
after paid operation, the invariant is whole finite species inventory and node
incarnation stability, not an unchanged individual pipe mixture. The moving test
adds CO₂ to current mixtures once, then requires actual paid transfer and frame
pose change in the same sampled interval. Tags: `shuttle-cabin-ventilation`.
Compilation passed with **3,221/3,221 JUnit**, but integration-20 and -22 failed
before live ventilation proof. The latter established a concrete lifecycle fault:
valid new sealed segments encountered old active gas owners at recycled plot
coordinates. Host eligibility, collision shape and generation checks were valid;
weakening those checks would not fix the ownership problem.

`SableFixturePlot` now removes its owned footprint normally and reuses the existing
proven-AIR retirement service before releasing storage. Nonempty gas/heat remains
in the UUID archive; unrelated positions are untouched. A new lifecycle case checks
all supply/return lanes, archive contents and fresh-owner rebuild on recycled storage.
Integration-24 passed **26/28 selected of 868**, clearing the earlier admissions.
Its two new cases failed an active-owner lookup and an unproved immediate allocator-
reuse premise. The lifecycle premise is corrected without weakening archive or
fresh-owner assertions. Ventilation-26 passed the other three cases, including the
full-cabin archive/fresh-build proof; its moving case found a removed frame and void
storage while saved owners remained active with matching UUIDs. The fixture's
32-block support/ticket scene did not cover its 120-tick, 20 m/s pulse window.

The moving fixture now derives a 160-block corridor from pulse duration/speed and
hull/margins, adds two normal air cells above the hull, and checks strict whole-hull
containment in the scene voxel volume and entity-ticking tickets. No collision
tolerance, physics flag, recurring velocity pulse or gas-owner state was changed.
Ventilation-29/-32 failed scene-bound guards, not a proved gameplay gas fault.
Ventilation-35 passed **3/4 selected of 868**. The frame stayed in its scene and
real finite transfer occurred (10.7801 mol O₂ supply loss/room gain; 0.2806 mol CO₂
return gain), but neither same-interval paid/moving flag was established. End meters
were 5 W/5 W; end-frame displacement was zero. Those end values do not prove power
or gas transfer coincided with earlier motion. Ventilation-38 will capture bounded
per-sample pose, paid meter, gas delta/budget and solve history. No timing workaround
or assertion relaxation has been made. Integration-36/full-37 were not run; live
moving transfer remains unaccepted.

Ventilation-38's interval history established actual concurrent paid flow/motion:
at tick 61 both devices delivered 5 W and the frame translated 0.670654 blocks;
8.795555 mol O₂+N₂ left supply, 8.795610 entered the room, and 0.05 mol CO₂
entered return. The test had reversed room-gain, return-gain and room-loss signs
when calling its `after - before` helper. Those measurement directions are now
corrected; no production transfer or scheduling change was needed. The moving
predicate additionally requires positive X displacement over 0.01 blocks, excluding
vertical f32 settling noise. Existing budgets, finite-owner identity and conservation
assertions remain. Ventilation-39 observed three qualifying paid/moving transfer
intervals (61/81/101), but a redundant completion predicate incorrectly required
cumulative flow to exceed the whole-cabin, all-operations conservation allowance.
That allowance bounds possible rounding loss; it is not a minimum flow quantity.
Completion now uses the unchanged quantitative per-interval flow proofs, with
whole-inventory species/UUID conservation checked on every sample and again at
the full 120-tick deadline. No early success, conservation-budget widening or
production timing change was introduced. Ventilation-42 passed **4/4 selected of
868**; integration-43 passed **28/28 selected of 868**, both including live paid
ventilation while the frame moves, stationary finite inventory, and exact archive/
fresh-build lifecycle checks. Full-44 completed **864/868**, failing three exterior
wall cases and strong cargo, not ventilation. The new lifecycle test's terminal
resource-cleanup additions are not yet runtime-validated. Integration-40/full-41
were not run. Whole gas-plus-wall heat accounting, sustained capacity, restart and
connected presentation remain unverified; M1 is not accepted.

The tightened lifecycle resource hygiene passed in geometry-46 alongside the
other three ventilation cases (**4/4; combined filter 9/868, 6 pass / 3 fail**).
Its foreign position now acquires and awaits its own entity-ticking ticket before
the loaded/AIR/no-owner admission guard; cleanup still removes only the exact
test-owned pipe through normal hooks and verifies its archive. The three combined
failures are exterior wall probes, not ventilation. This evidence remains scoped
and does not replace a full-suite or connected gate.
