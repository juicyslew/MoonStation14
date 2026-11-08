# Combined merge — owner check

Use matching candidate builds on client and server; the snapshot wire format changed.

1. Repeat the accepted M1 test in your existing cargo bay: check the sea-lantern lighting, jug placement, outlines and visibility at rest.
2. Run `/ms14 shuttle_fixture flight <ship> 30`. Walk, turn, release movement, jump and land while aboard. Check for floor loss, unexpected wall pushes, airborne dragging, camera corrections, or cargo/light flicker. The diagnostic supplies no guaranteed collision bumps.
3. Recheck ordinary pickup/drop/throw, a plant harvest, and portable-body release/throw. No duplicated item/key should remain; produce appearance should retain its normal scaling.
4. Open a gameplay window while moving. Movement-capable windows should retain their behavior; keyboard-capturing windows must send neutral movement.

Report any difference from accepted M1 behavior before accepting the finer ground arithmetic. `/ms14 shuttle_fixture motion_stop <ship>` disables feedback, **not momentum or gravity**; use a safe test area.

Automated readiness is not owner acceptance. Full regression status must be reported separately.
