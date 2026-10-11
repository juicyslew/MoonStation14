# Connected boundary check

Owner confirmed these checks on 2026-10-11, including the six reported regression fixes. Keep this checklist for future changes; automated reports do not replace connected observation.

Use a disposable world after the automated checks pass. Do not change owner configuration.

1. Build `/ms14 chemmaster_fixture build`; use the powered ChemMaster with an empty hand. Click a control (no visible `Pending...`), drag its title, and close with X. Fill both hands and press the explicit activation key (default E) while aimed at the machine; it should open without inserting the held item.
2. Open an airlock panel with a screwdriver, then use a wire tool to open its UI. Check title dragging, wire clicks and X.
3. Keep both windows open. Click each in turn: the clicked window should come to the front. Drag each across the other and release outside both; the original dragged window should keep the drag. Toggle the rebindable pointer control (default L-Alt), move with the cursor locked, then unlock. Clicking to return to locked mode while looking at the airlock must not reopen/recenter wires. Subsequent locked-mode clicks must interact with the world even under visible window rectangles.
4. Resize/change GUI scale; controls must remain reachable. Open chat and type: text input must not activate gameplay actions. Close chat and recheck movement.
5. Compare one matching preview's title, text, X, sprites and clipping with the real render (`build/reports/feature-readiness/previews`). Record the state/scale and any mismatch; CPU images alone do not prove GPU fidelity.
6. Use a held drink (default Z). Check immediate progress, cancellation and one completed dose without duplicate consumption.
7. Place a filled jug in the world. Hold Shift, press left-click on the jug, aim at ChemMaster and release. Its solution should enter the buffer once; the jug should remain in the world, not enter the input slot. Cancel another drag and verify no transfer.
