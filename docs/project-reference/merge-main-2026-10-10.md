# Main integration — 2026-10-10

Resolved the 19 conflicted paths while preserving both sides:

- Ours: beakers/ChemMaster, shared pouring and timed drinking, dump capture/ACK
  handling, nonmodal workspace, machine models, wall-mounted APC with resolved
  level-5 native emission, and readiness-timeout OFFLINE parking.
- Main: equipment/wearables/stripping, device networking/configurator/signaller/
  signal controls, updated airlock models, canonical ordinary interaction reach,
  deferred power-topology validation and existing regression repairs.

Inputs: HEAD `bad1bbfd15b31b95e6e5d520622eba221128d740` and MERGE_HEAD
`ea91ca2b5b9127c577c433c76db93509846329d7`.
Registration/prototype/resource conflicts were merged as unions, not replaced
with an entire branch. Main's intentional screwdriver rename remains; obsolete
airlock-screwdriver aliases were not restored. Dump keeps its explicit 5-block
policy without overriding main's ordinary reach service.

Integration corrections retain existing services:

- Bag/belt quick-equip dispatches once through the shared input policy.
- Beaker/configurator property callbacks, creative registry syntax, prototype
  test composition and authenticated block-item tag-update arguments were repaired.
- Two machine-slot fixture helpers accept INITIALIZED or PRESERVED only with
  matching resolved hand IDs and compatible live hands. Main may initialize
  hands before the fixture does; exact ownership/rollback/drop assertions remain.
- Power tests use bounded known-topology/powered-state readiness instead of
  assuming completion after one solve cadence. No power is granted by fixtures.

## Validation

- Clean unit/build/GameTest compilation: **3,158 tests**, no failures/errors/skips.
- Filtered native: **292/783 selected, 292 passed**.
- Full native: **783/783 completed, 783 passed**.
- Conflict-marker scan: none. Independent source preservation review passed.
- Logs: `build/validation-logs/main-merge-preserve-{unit-build,native-focused,native-full}.log`.
- JAR: `build/libs/moonstation14-0.0.1.jar`; verified SHA-256:
  `B6BD9C0F02F149543DB92405C9612B00490EF9F5E1C8BD01AC5ED8210B5E6971`.

Conflicts and the two fixture adaptations were staged as resolved; no merge
commit or push was performed. Headless validation does not prove every
authenticated client/GPU interaction; previously documented manual-only gaps
remain distinct from this fully passing native snapshot.
