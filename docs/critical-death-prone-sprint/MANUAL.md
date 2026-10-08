# Connected-client checks

Sprint closed by owner acceptance on 2026-10-07; retained regression checklist.
See `HANDOFF.md` for current state and remaining automated gaps. Fixtures alone
do not establish connected-client rendering or Ready transport.
Use the normal lifecycle/mind-control setup, without changing configuration.

**Ghost regression check:** Restart server and clients for a changed handoff.
Die, release movement, then press W again: leave the corpse and fly toward the
camera heading. Check mouse look and Space/Shift flight. Pulling, other medical
behavior, and removal of the dev head are already owner-confirmed; do not repeat
the whole checklist for this ghost-entry repair.

1. **Prone:** C lies face down; C again gets up. K rolls you over. Starting to
   crawl turns you face down; stopping does not roll you back. Crawl through a
   one-block gap and try standing under its ceiling. Empty/one occupied/two
   occupied hands should give 40%/20%/0% crawl speed. Forward crawling must lead
   with your head; K changes stomach/back without exchanging head and feet.
    Looking around must not turn the prone body or head. Crawl sideways/backward:
    the head must lead movement, then retain that facing when stopped. Idle limbs
     must be neutral. Compare grounded voluntary, Critical, and Dead bodies.
     Turning must pivot around the entity center, not sweep the body in an arc.
     Slip/knockdown should start face down, without repeatedly flipping while moving.
2. **Body visibility:** Use a second client to view a prone body near the edge of
   the screen, from both ends and both sides. It must not disappear while its head
   or feet should still be visible. Check both lying positions and F5.
3. **Critical:** Take damage and watch the health display, initially 100/100.
   Critical must occur before death, with a clear CRITICAL label, dropped hand
   items, retained worn gear, blocked movement/actions/speech, and tightening fog.
    The temporary debug row must show damage remaining until death. Fog must
     start wider and close to about 1.5 blocks near death, completely blacking out
     distant terrain, entities, sky, and clouds. F5 remains
    usable. Ordinary damage must not automatically shove the body.
    Hurt sound is off by default. Chemical damage must not shake the camera,
    play hurt noise, flash the character red, or interrupt
   an otherwise valid action. Listen for periodic gasps after Critical breathing
   lowers saturation; gasps must stop on death.
4. **Death:** Hold a direction while dying. Remain viewing your face-up corpse
    until you release and press a direction again. The ghost then moves separately;
     the corpse stays behind. Also die above the floor: the corpse must fall and
     settle, not hang in mid-air. Check ghost movement and mouse look for several
     seconds. Living players must not see the ghost.
     Turn yaw through a full circle and back: the camera must respond immediately,
     and W must always move toward the camera heading. Test pitch separately.
     Flight should respond locally without waiting for server position updates.
     From the second client, check that no Minecraft spectator head remains above
     the corpse or follows the owner; the retained body must stay visible.
     Enter Critical in the air too: both Critical and Dead must fall and remain movable.
     Lie face down before Critical, then turn the Critical body over externally;
     neither medical transition should reset its current stomach/back orientation.
5. **Bodies:** Ctrl + left-click toggles dragging; P must not control dragging.
   Drag around a wall and check collisions and the owner's camera. Dragging must
   not roll the body. With a free hand, aim at a downed body and press Shift+K to
     turn it over; its medical state must not change. Aim at visible head, torso,
     and feet; test both Critical and Dead targets. Run while pulling a jug/body:
     modest elastic lag is allowed, but it must keep following without detaching
     or snapping. Jump while pulling, including against a blocked target: gravity
     must bring you back down, never leave you hovering. Pull from above a block
     to check upward hauling without a toss action; try jumping or a higher pulling
     position too, rather than expecting effortless stair climbing. A stuck target must increasingly
      restrain the puller; check the dragged player's camera for jitter too.
      The pulling-hand icon must update promptly on start, explicit stop, and
      release caused by the conscious target moving. Compare a full vertical jump
      with and without an attached target; early slack must not hold you down.
      Repeat player dragging while the target is conscious, Critical, and Dead,
      including after the dead owner ghosts. Check both clients for stutter.
6. **Debug revival:** As an operator, run `/ms14 revive` while ghosted. Your
   original linked body should recover, with a return notification. You remain a
   ghost until R returns you to that same body with its gear. Repeat before
    leaving corpse view: control should resume automatically, without pressing R.
    Check that revival leaves no persistent red death tint.
7. **Chat:** Open chat with a message longer than the visible panel. PageUp and
   PageDown must let you read every wrapped line and return to the newest lines.

There are no gameplay medical supplies yet. Record the first failed step and what
happened instead; do not disable lifecycle or mind control to make a check pass.
