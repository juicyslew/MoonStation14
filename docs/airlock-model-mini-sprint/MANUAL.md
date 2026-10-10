# Airlock model checks

Install the rebuilt jar on **both client and server**, then restart both.

1. Place powered airlocks facing each direction, with solid blocks immediately
   beside them. Check the new model and its inventory/held appearance.
2. Open and close them. Leaves should slide smoothly into the sides without any
   geometry showing through adjacent blocks or outside the doorway's block space.
3. Open the maintenance panel, then operate the door. Its parts should follow the
   leaf; check powered/unpowered and visible bolt indicators too.
4. Shift-examine the frame and leaves. The whole-door outline should fit the new
   model; a fully open doorway should let you select objects through its center.
5. Reload resources with F3+T and repeat an opening. No missing textures or stale
   model geometry should appear.
6. Confirm there is only one screwdriver in the tools tab. Use it on both an
   airlock panel and a disposal unit.
7. On a powered, electrified door, cut safety and timing. Let it close onto a
   stationary body (including a critical body). Each actual closing contact should
   shock once; reopening and closing again can shock again. A body merely nearby
   must not be shocked. Restore safety: an obstructed close must abort without
   producing the new closing-contact shock.
8. Leave a dragged corpse touching a powered, unbolted door's side, with its
   center outside the doorway. After an opening, watch it close and reopen while
   the corpse stays in contact. Move it away from the door's block-space boundary;
   the door should then remain closed. Repeat with an incapacitated living body.
9. From standing, take an airlock shock. Check third-person pose and collision
   height: the body should become prone, not just show status labels. After the
   shock ends, check normal get-up recovery. Shock an already-prone, rolled body
   too: its orientation should be preserved.

Welded, emergency and optional effect assets are included, but this rendering
change does not introduce the missing gameplay/state replication for those modes.
