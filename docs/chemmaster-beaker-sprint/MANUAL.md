# ChemMaster client check

Focused automated validation passed; client presentation acceptance is pending.

Use a powered ChemMaster, two beakers, and a known reagent mixture.

For an open-air playable fixture, a permission-2 operator runs `/ms14 chemmaster_fixture build [origin]`;
without an origin a player-relative platform corner is chosen four blocks east, while console must
provide coordinates. Origin is the platform's lowest corner. The clear footprint is 8x10 with a
four-level preflight envelope (platform plus three levels above); it must be already loaded and
unobstructed. No walls or roof are built.
Run `/ms14 chemmaster_fixture status <origin>` after normal power-solve ticks to inspect the actual
250 W receiver meter; no power grant is forced. Seventeen ordinary loose-item supplies are staged
on the platform (two empty beakers of each capacity plus 50u water20+sugar10 and 100u water40+sugar20,
and ten 200u single-reagent jugs: water, sugar, sodium, chlorine, oxygen, iron, ethanol, ammonia,
carbon, and silicon; plus one 100u beaker with 10u each of ale, milk, coffee, cola, beer,
and absinthe). Pick them up through the normal controlled-body loose-item route; there is
no vanilla chest. Water and sugar jugs are at `(1,1,8)` and `(5,1,9)` and start within twice the
  ordinary pickup reach. The other jugs are at `(1,1,2)`, `(2,1,2)`, `(6,1,2)`,
`(7,1,2)`, `(1,1,3)`, `(2,1,3)`, `(6,1,3)`, `(7,1,3)`; approach them normally before use.
All supply positions are within the open 8x10x4 envelope and clear the posts and beaker row.

For the presentation retest, build a fresh fixture:
- Check Sort on the left, Transfer/Discard on the right, and right-side tabs
  beside the title with no redundant divider. Insert a beaker: its name and
  volume/capacity must show. Reagent amounts end in `u`; dose buttons do not.
- Insert the six-reagent sample; check five visible input rows and scrolling.
  Bring the distant jugs near the machine before dumping them; dump nine distinct
  reagents to check eight visible buffer rows and scrolling.
- Drag the window to the center and bottom-right. All rows and dose buttons must
  remain visible and clickable; names must read normally, not `reagent-name-*`.
- Check empty, partially filled, and full beakers in hand and on the ground;
  liquid level and color should follow their contents.
- Dump sodium and chlorine into the buffer: they must stay separate. Return 25u
  of each to an empty large beaker: expect 50u table salt.
- Hold a filled beaker/jug and use it on another ground container: expect 5u
  poured from the held container, capped by remaining capacity. Try both sizes
  and jug/beaker pairs. Use **Z** (Use in hand) on a water-filled beaker to drink;
  expect 5u consumed after the normal ingestion delay.
- Check reaction audio for salt formation, but not for water-only transfers or
  inert buffer dumping. Shift short-click should examine; Shift dragging should
  dump without opening examine or drinking.

The aim-and-release control defaults to Shift + left mouse and can be rebound in
Controls. Keyboard Alt + E retains ordinary alternate world use.

If dumping reports `no active dump capture`, reproduce once and preserve client
and server `logs/debug.log` (if enabled). Dump traces share a short gesture token
across send/ACK/cancel and capture-removal events. This failure's cause is still
under investigation; diagnostics are not a claim that it is fixed.

1. Aim at a loose filled jug, hold Shift + left mouse, aim at the ChemMaster, then
   release left mouse. Confirm all contents entered the buffer, the empty jug
   remains, and the input slot was not changed. Repeat without machine power.
   Try invalid release, Escape cancellation, a blocked path, and moving out of
   reach: none should drain the jug. Check camera movement and both cursor modes.
2. Use a filled beaker on the machine to insert it; use an empty hand to open it.
   Confirm its name, capacity, quantities, per-reagent amount buttons, and Eject.
3. Transfer one reagent to the buffer; eject and insert the other beaker. Confirm
   the buffer stayed intact. Return some reagent, then use All against a nearly
   full beaker: excess must remain in the buffer.
4. Check Transfer/Discard on both lists, sorting, scrolling, and the Output
   placeholder. Discard must affect only the selected reagent. With the active
   hand occupied, Eject should use another free hand; with both full, it should
   drop the beaker without duplicating or losing it.
5. Open storage/analyzer windows alongside ChemMaster. Move, look away, switch
   mouse lock/cursor mode, and change focus. ChemMaster must stay open. Close one
   window without closing the others; check controls at your usual GUI scale.
6. With two players viewing one machine, change its contents. Both must converge
   on the same quantities; neither may duplicate or restore old contents.
7. Test power loss, walking out of range, and Critical/death. Each must close the
   invalid interaction. While unpowered, Shift + machine interaction with an empty
   hand must still eject its beaker. Restore validity and reopen normally.
8. Save/reload and break/re-place a loaded machine. Confirm the saved beaker and
   buffer are preserved, and the old interaction cannot control its replacement.
