# Syringe and analyzer animation checks

- Obtain an empty syringe and a filled reagent bottle/jug; place the container
  in the world. The syringe starts in **draw**, with a **5-unit** dose.
- **Z** switches draw/inject. **Alt+Z** cycles the resolved dose choices
  (normally 5/10/15). Check the mode/fill art and reagent tint.
- Primary-click the container: draw into the syringe or inject back into the
  container according to mode. Verify volume conservation and capacity limits.
- With sufficient source/free capacity, verify each selected 5/10/15-unit
  container draw and inject completes without waiting, then immediately repeat
  against the updated hand state. Smaller availability transfers only that
  amount. A positive configured container-draw delay should cancel on movement,
  damage, or hand change.
- Primary-click a nearby body to draw blood/inject; **Shift+Z** targets your
  controlled body. Check progress, movement/damage interruption, one dose only,
  no transfer on cancellation, and no oral/stomach route.
- Draw/inject on a downed or retained Dead patient. Drawing removes the actual
  blood mixture; ordinary injection must not force default Dead-body revival.
- Inject an existing medicine and check its normal bloodstream metabolism.
  Basic syringes have no configured SS14 draw/inject sound.
- View the analyzer in the hand HUD, storage and world. Its overlay should loop
  continuously: eleven 0.1-second frames, then a 0.5-second pause. **Z** opens
  its UI without stopping an existing scan.
