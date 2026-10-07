# Airlock connected acceptance fixtures

`AirlockConnectedGameTests` adds server-side checks for two remaining core behaviors:

* `unsafeCloseCrushesOnlyLivingBodyInsideExactDoorway` enrolls actual human-prototype bodies, opens an APC-powered door through the controller, cuts the safety wire through the stable root-BE API, then begins a real close. It checks the typed blunt ledger delta (15), immediate active stun, and that an entity whose bounds begin above y+2 is not crushed.
* `scheduledDoorOpeningTransportsAndConservesFiniteGas` seeds unequal finite nitrogen inventories in sealed adjacent cells, opens and closes the actual APC-powered airlock through scheduled transitions, and checks directional transport plus conservation including the portal cells.

The first focused run reached the final assertions for both fixtures: crush damage, stun, and above-door exclusion passed, while the close assertion ran at the transition boundary. The atmosphere fixture observed no directional transfer. Inspection found the room/portal envelope left lower/upper portal faces exposed; the fixture now closes all portal faces except the lower north/south chamber connections, reports both block-state topology changes to its local atmosphere service, and inventories finite-cell chunk storage (including portal cells after closing) instead of omitting cells whose active sample is blocked. Gas and thermal-energy tolerances derive from the committed face/LINDA operation count and actual per-cell float ULPs. Scheduled checks now allow 14 ticks per full transition. These corrections have not yet been rerun.

Authenticated crowbar-pry admission, panel/wire UI, client synchronization/rendering, and equalization persistence remain separate follow-up coverage. The tests intentionally make no claim about those paths.
