# Connected manual acceptance

Use the matching rebuilt JAR on client and server with two controlled human
characters close together (within 3.0 root blocks; shared flat +1.5 Minecraft
offset over the ordinary 1.5 SS14 interaction reach).

- Aim E at the other eligible body; confirm the compact panel opens, has at most
  three columns, exact clothing slot order/spacing, and the target's drag title.
- Walk and turn with the panel open; confirm movement works. Click an occupied
  target slot and verify timed removal.
- Hold a compatible item, click an empty target slot, and verify timed dressing,
  including a target hand.
- Check occupied pockets show the information icon without real item name/icon.
- Move during an operation and verify cancellation without locking the panel;
  move out of range and verify the stripping view closes and cannot be used.
- Remove from full actor hands and verify normal world drop. Remove a uniform and
  verify dependent pockets/ID drop while bag child contents remain preserved.
- Confirm a simple coat does not enable suit storage; verify F5/observer appearance.
- Close, retarget, change body, ghost, and disconnect; confirm the old view never
  revives.
- With the matching build, also verify E opens stripping on an ordinary spawned
  villager, the Ginormous Utility Case is denied in pockets, and worn clothing
  remains visible in F5. These client checks remain manual.
- Drag blank-ID gear from the actor to the target and verify it chooses the ID
  slot ahead of an empty pocket; verify an explicit manual pocket equip remains
  possible. Dragging an invalid/noncanonical MOVE_EQUIPMENT request must be
rejected rather than crashing. Move beyond the shared 3.0-block interaction
range while the view is open and verify it closes.
- On grounded humans, compare resolved walk/sprint speed against 3.125/5.625
  blocks/second (raw prototype values remain 2.5/4.5; common grounded conversion
  is 1.25x). Vanilla Creative flight, ghost flying, crawl/pull/status modifiers,
  acceleration and friction are unchanged.
- Ordinary E/block-use/APC/examine/manual caps remain separate local limits; this
  calibration does not globally widen those routes. F5 eye aim remains 5 blocks
  and medical analyzer scan/UI ranges remain 3.5/3.0 blocks.
