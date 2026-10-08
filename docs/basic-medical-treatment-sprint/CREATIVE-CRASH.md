# Creative-tab stack-count crash

The 2026-10-07 client crash occurred while NeoForge assembled the Items tab: a resolved medical prototype with initial quantity 10 was passed directly to creative-tab output, whose display-stack contract requires count 1. `ModMedicalItems.createCreativeStack` now adapts only the display copy to count 1, retaining its item components (including syringe mode/dose). `createStack` and prototype initial quantities remain unchanged for gameplay. Creative display is not a promise that selecting an entry gives the prototype's gameplay quantity; no custom tab override is used to bypass vanilla behavior.

This change targets the reported failure. A connected client-screen opening check remains advisable; native tab construction/output validation is the relevant automated regression, not a general guarantee against unrelated client crashes.

## Prevention contract

- Keep creative display templates separate from gameplay quantities: nonempty,
  detached display stacks must have count 1 and retain their components.
- Run the `creative-menu` native fixture when changing item registration,
  prototype-backed creative creation or tab callbacks. Factory-only assertions
  do not cover this platform boundary.
- Preserve separate assertions for gameplay quantities and syringe metadata;
  do not make the UI pass by changing prototypes or removing entries.
- Report callback coverage separately from a connected creative-screen smoke
  check. Do not claim a client screen was opened unless it actually was.

The earlier creative-factory assertion expected 10 and encoded the wrong
contract. Passing that test never established that NeoForge would accept the
entry; the new native fixture covers the missing boundary.

`CreativeMenuGameTests.itemTabBuildHonorsCreativeOutputContract` exercises the native Items tab `buildContents` path under NeoForge 21.1.224 / Minecraft 1.21.1, checks every emitted stack is nonempty/count 1, verifies medical entries and syringe mode/dose, and separately checks gameplay factory counts (10 for normal ointment/brutepack/gauze and 1 for their single variants). Focused tag run: 1/643 selected, 1 passed. Full unit/build chain: 2,569 tests, 0 failures/errors/skips; GameTest compilation and build succeeded. Logs: `build/validation-logs/creative-menu-fix-gametest.log` and `build/validation-logs/creative-menu-fix-unit.log`. This is native callback-path coverage, not evidence that a connected GUI was opened.
