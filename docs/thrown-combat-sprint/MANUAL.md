# Owner manual check

Use a creative world with a second player/target and the registered thrown items,
armor, and the approved existing worn visuals.

1. Equip armor and compare knife/dart typed damage and armor behavior. Fill dart /
   hypodart from a solution bottle with `E`; check consumed dose, transfer, and
   blocked/empty cases. No selectable syringe modes or Z help are intended.
2. Throw a bola; check movement reduction, passive ensnared status, escape, and
   catch snap. Test other-actor timed release and self-breakout, including full hands.
3. Embed an item in your own body. Use its currently displayed body-action hotkey
   to remove it; the action is conditional and ensnared escape has priority. Repeated
   presses must not restart its pinned timer; verify same-item hand/world recovery.
4. On another target, test normal empty-hand click and Alt+E timed removal. Held
   tools retain their actions. Own attachments are not world-targetable.
5. Inspect default/rebound capability keys: direct 1-9/0 then Shift+1-9/0. Ensure
   slot numbers and holes remain stable while actions appear/disappear/cool down.
   Compare menu/HUD/pointer hit targets after resize and body changes.
6. Check HypoDart's larger sprite against Dart; physical dimensions and profile
   remain unchanged. Review atlas appearance and audio cues manually.
7. Confirm pickup/drop, target termination, and attachment persistence conserve
    the original item and payload. Self/back-source chooser UI remains deferred.
8. Run `/ms14 eva_fixture build`. Use the displayed numbered mask/internals actions;
   verify mask icons, tank connection, and tank examination. Add/remove a mask or
   tank while self-removal is available: surviving actions must keep their keys,
   and new providers must fill the lowest free slot. Recheck scream and wear audio.

## Feedback revision checks

- Inspect the imported basic helmet and armor vest while turning and changing pose;
  check visor transparency, fit and lighting. The approved models are now imported.
- Check the cyan removal arrow, labels below icons, and tighter capability-strip
  margins. Resize the window; hover/click the same visible action after slots change.
- Ctrl+K opens Actions; K turns yourself over and Shift+K turns the targeted body.
  Try quick press/releases. Direct disposal climb now defaults to Ctrl+J. The old
  saved plain-K Actions binding is automatically repaired and saved on client start;
  disposal-climb bindings and other custom settings are not migrated.
- Remove a floor-clipped hypodart from a prone target. A wall must still block access.
- Throw an object away and arrange for it to fall/bounce back: no immediate spawn
  hit, but a returning hit after separation should damage the thrower normally.
- Test bola hits while approaching, retreating and crossing at normal walking/running
  speeds. Compare with a stationary target; a second bola must not add another ensnare.

Movement during embedded-item removal remains allowed, as in pinned SS14.
Fresh gate validation and owner acceptance are still required for this revision.
