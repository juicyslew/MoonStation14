# Open validation findings

The first full native run completed 698 tests: 678 passed, 20 failed. The later
correction run completed 700: 676 passed, 24 failed, before the final fixture
repairs. See `VALIDATION.md` and the logs for exact names; matching a prior
failure name alone does not establish an inherited cause.

- Confirmed equipment fixture defects included passing the old two-slot list to
  a resolved 15-slot body transition, and reading array index zero as the belt
  when it now represents shoes. Repairs derive declarations from the actual
  body and select occupants by slot ID; standalone two-slot pure tests and
  legacy-absence save/load checks retain their original meanings.
- The quick-replacement fixture passed item tokens (`small-med`, `card`) as
  destination slot IDs. It now uses `pocket1` and `id`, with a resolved Small,
  max-10 Gauze stack. Runtime conservation assertions remain intact. The fresh
  storage-host fixture now checks named back/belt hosts rather than treating all
  15 equipment slots as storage hosts. The completed post-repair focused run
  selected 195/701 fixtures: 193 passed, two failed. The remaining failures were
  pulling physics and the new world-equipment round-trip fixture.
- The world-equipment fixture's reach denial was a blocked fixture ray. Raising
  the fixture body one block clears the path; bounded missing chunks are loaded
  during setup. The final isolated run selected 1/701 and passed 1/1, covering
  full-hand world/equipment round-trip and failed-publication compensation. No
  production reach guard was changed. The earlier focused run's pulling-physics
  failure remains unresolved; no full-suite-green claim is made.
- `staggeredretryinitializesabsentneedswithoutrerollingsavedstate` fails the
  missing-needs initialization assertion after 22 ticks. Actor/prototype
  eligibility versus timing is unresolved; no equipment cause is established.
- `farplayercannotdespawnboundunboundorcorpse` stops at its nearest-player setup
  assertion: another fixture's `soap-slip` FakePlayer is nearer than the intended
  distant player. It does not reach the actual despawn-protection assertions.
  This interference has prior documented appearances in
  `../timed-interactions-ingestion-sprint/SPRINT.md`; it is not a demonstrated
  equipment regression. No unrelated fixture-isolation repair is included here.

The sprint was closed with owner approval; final connected visual review was not
performed by the assistant. First-person arm/item rendering is removed, not a
remaining geometry-fix target. Latest toggle/click behavior and authored
F5/observer appearance still have the manual validation boundary recorded in
`HANDOFF.md` and `MANUAL.md`.
