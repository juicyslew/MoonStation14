# Connected visual check

Use two connected players controlling character bodies.

1. Obtain the starter equipment from the creative tab. Tap Left Alt for the
   cursor, then equip the nine clothing slots in the bottom-left panel. Its bag
   toggle should sit left of shoes and below jumpsuit, with opposite open/closed
   arrows. The nine clothing tiles match the wearable hotbar tile size. ID,
   backpack and belt stay left of the centered hands; pockets and then suit
   storage stay to their right with uniform 3px hotbar gaps.
2. Wear the jumpsuit, fill both pockets and the ID slot, then remove it. Check
   dependent items drop intact. Wear the simple coat and confirm suit storage
   stays disabled; trying to equip the case there must leave it in your hand.
3. Hold clothing and press Z to equip it; repeat over an occupied matching slot.
   Check the displaced garment and dependent contents are conserved. Drag clothing
   between either hand and its slot. With an empty active hand, click occupied
   clothing without moving the pointer: it should move into that hand. Repeat
   with a full active hand; nothing should move. Open the existing backpack/belt and check
   clothing drags in both directions, including a bag held in a hand and a bag on
   the floor. Try gloves into a pocket and between pockets. Repeat with both hands
   full; invalid/occupied destinations must leave everything unchanged. Front
   windows must intercept clicks over equipment.
4. In first person, assert that no character arms or held-item models are drawn.
   Press F5 and verify the controlled character's clothing and both held items
   remain visible; have the other player verify the same while walking, turning,
   going prone, and changing equipment.
5. Change controlled body or become a ghost. Old equipment must not remain in
   the view. Reconnect the observer and confirm currently worn/held items appear.

Models are starter assets; earpieces and the blank card provide no radio/access.

For authored worn-mesh visual review, repeat the F5/observer checks with each of
the eleven mesh-backed items equipped. Inspect texture orientation, UV seams,
articulation while walking/turning, and placement while crouching, swimming, and
prone, including coat leg coverage and lighting. Check WIDE and SLIM characters:
WIDE currently uses authored meshes; SLIM deliberately shows the procedural
fallback. Visual review remains necessary; parser tests and compilation do not
establish in-game appearance or asset approval.
