# Connected airlock checks

Sprint closed by owner acceptance on 2026-10-07. These remain optional regression
checks. Use a controlled character and **body hands**, not the
carrier inventory. Have an airlock on a working APC circuit, screwdriver,
wirecutter, multitool and crowbar available.

1. View a powered door from the front, back and side: it should have a solid 3D
   frame and leaves occupying most of the block depth. Front/back should each
   show one complete door; other faces should be plain grey. Use E and walk into it; leaves should slide horizontally
   open/closed, independently on two doors. Check five-second auto-close.
   No PDA/ID is required; walking nearby alone must not open it.
2. Open the APC breaker. Left-click the door with the crowbar held; after five seconds it should
   toggle the unbolted door in either direction. The full door artwork must
   remain visible without power; E and walking contact still require power.
   E on an unpowered door must not play access-denial audio.
   Restore power.
3. With the door closed, left-click using the screwdriver. After one second,
   check the panel changes visibly and makes a sound; repeat to close it.
   Check the hatch is low enough and visible from both sides.
   Verify the crowbar/wirecutter icons and clearly thicker targeting outline.
 4. Open the panel, then left-click using a wirecutter or multitool. Check a small
     circuit window with eight colored wires, seven status lights, no function
      labels and no Cut/Pulse buttons. AIA and Log continuity lamps start On,
      turn Off when cut, and return On when mended. Log pulse turns its lamp Off
      for 30 seconds (a repeat does not extend the timer); the local build does
      not record access-log events. AIA pulse intentionally has no effect.
5. Tap L-Alt to resume ordinary mouse-look/gameplay while the window stays
   visible. Tap it again to interact with the window; neither toggle should
   close the session. Move, change hands/tools, drop an item and use E. Check
   Alt/Control-click still perform their normal actions outside the window.
    Click wires with the cutter to cut/mend; switch to the multitool without
    closing the window and click to pulse. Check cut/mend sounds, sharp UI art,
     red-bright/white-dim core and large status lights, and dim small lights when
     their circuits are cut. While
     stunned, hand/world/storage mutations must be blocked; wire actions should
     show authoritative stun feedback while passive queries keep the window and
     feedback visible. Speaking, cursor use
    and chat review remain available. Check
   bolts block operation/prying, both power wires are needed to disable power,
   and mending one restores it. The menu must remain usable without power.
6. Hide/close the panel or move out of reach: the window should close. With safety
    enabled, enter a closing doorway; it should reopen rather than trap you.
 7. Disable safety and let the powered door close on you: expect one 15-Blunt
    hit, knockdown/paralysis for 2.4 seconds, no upward jump or repeated in-wall
    damage. With timing set fast, confirm the crush release visibly traverses
    normal reopening and reaches fully OPEN before beginning its next close.
 8. On a powered door, cut the first power wire: expect a shock and subsequent
    touch shocks. Bump the still-powered, electrified door: it must start opening
    and shock the contacting actor. Cut both: power and touch shocks stop. Mend
    one: power returns while electrification remains; mend the last:
    electrification clears.

Do not use a mend during an active power pulse as a recovery check: pinned SS14
cancels that timer without clearing its pulse flag, leaving power latched off.
