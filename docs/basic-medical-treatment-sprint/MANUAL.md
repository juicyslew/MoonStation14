# Connected checks

- Open the actual Items creative tab. Supply stacks must cap at **10**; supplies
  and analyzer occupy two storage cells, syringe one.
- Hold a supply: **Z** treats yourself; primary-click a nearby injured body to
  treat it. Check progress, movement cancellation, correct healing, stack use and
  SS14 start/completion sounds. Counts must update immediately. Gauze must reduce
  bleeding and Slash/Piercing damage, not Blunt. Healthy targets consume nothing.
- Scan a nearby body with the analyzer; **Shift+Z** scans your controlled body.
  **Z** toggles its window; opening an unlinked analyzer must fail. Activating it
  again on the same scanned target closes the window. Check the compact neutral,
  upright portrait, no patient-name line, stats/species left of the portrait,
  small text, fixed summary/portrait above a clipped damage-only list, proportional
  full-track scrolling and scan sound. Confirm blood, temperature and total-damage
  labels/readouts align in a compact column and no damage row draws over the summary.
- Drag the window. Recapture the mouse with the existing pointer-mode toggle:
  the window must stay visible and retain its position. Reopen pointer mode and
  close it explicitly. It must not reopen from ordinary updates. Closing the last
  analyzer window must recapture the mouse, including after manual L-Alt
  unlock, if no storage window remains. Another managed window keeps the cursor
  unlocked. Z must also work while unlocked, including supply and syringe controls.
- Scan a patient 3.25 blocks away; move beyond 3.5 blocks and back to verify
  readings pause and resume. Drop/store the scanner: scanning stops, but the window
  remains open nearby and closes when its root is over three blocks away.
- Overlap storage and analyzer windows; clicking either raises its entire content.
  Covered storage items must not draw or receive clicks above the analyzer.
- Check that storage windows, Ctrl-click pulling, posture and ghost controls
  still work. Treatment of retained Dead bodies must not revive them.
