# Connected examination checks

Use a controlled character. Have an APC, airlock, disposal unit, item, and a
glass wall with a visible target behind it.

1. Hold Shift: check the highlighted target and the single-line name-only row
   centered above the cursor with the increased gap. Shift-click to examine; holding a
   tool must not also use it. Release Shift and look away: the result should stay.
2. Aim through glass: the nearest eligible target behind it should be selected by
   default, including when it becomes available after glass. Shift-scroll to the
   glass; manual choices remain stable while anchor and target remain eligible.
   A solid wall must block hidden targets.
3. Examine machines and items. Check useful names/descriptions, power indication
   where supported, and APC charge only within close-detail range (3 blocks).
   Beyond ordinary range (16), authoritative information must disappear.
4. Repeat in third person and with the L-Alt pointer outside storage windows.
   Targeting follows the view/pointer; eligibility follows the character.
5. Move, chat, and toggle L-Alt with the popup visible. HUD, hands, backpack, and
   storage should remain usable. Inventory shift-click and G-scroll construction
   controls must retain their normal behavior. Stun alone must not block examine.
6. Switch targets quickly; remove/replace an examined machine or change bodies.
    Old replies/details must not reappear. Active and preview outlines can coexist;
     entity outlines follow movement smoothly, including between ticks. The active
     target outline stays latched after looking away. Details expand to fit their
     contents from 56 to 112 GUI pixels, then wrap; details stay against the right
     border below seven reserved status rows
     and must not drift.
     Its preview-name row fits its name independently. Hover the recognizable
     thumbtack: a nonempty Pin/Unpin tooltip should appear. Click the
    thumbtack to pin across outside clicks. Unpinned timeout is ten real seconds,
    unaffected by rendering/refresh; pin suspends timeout and unpin starts ten
    fresh seconds. The adjacent × closes it.
     Otherwise an outside click dismisses the popup and is consumed. Click × or
     press Escape while gameplay has focus to dismiss without opening Pause.
     Focused chat and machine windows retain their own Escape behavior. Test Pin
    through a glass wall: it does not extend range or survive target/body changes.
    With the popup open, click inside and outside while Pause, Options, inventory,
    and chat are open: examine must not pin, close, dismiss, or consume those
    clicks. Repeat after resuming; ordinary outside-dismiss, Pin, and × work.
    Storage popup interactive slots keep their normal routing while its open
    world-host area continues to admit examination.
  7. The details panel begins below seven reserved status-row backgrounds at the
     shared right edge. At 320x240 and larger GUI sizes, long readouts clip within
     the remaining space below row seven rather than overlapping statuses. Use
     non-Shift wheel over the panel to reach all wrapped rows; Shift-wheel still
     cycles candidates. Pause/menu wheel and storage-slot scrolling retain priority.
 8. Repeat world-target checks while healthy, critical, and dead: previews and requests should
   disappear beyond the shared 16, 1.3, and 0.75 block rules respectively; use
   body position rather than a displaced third-person camera to judge range.
   Looking at a steel wall must not expose a hidden target or show a denial popup.
    Focused wire/storage Escape continues its own action before any examine popup.
 9. Examine water, Space Lube, and a mixed puddle within 3 blocks. Check complete volume
   and physical-description sentences, mixture-colored physical descriptions,
   and individually colored recognizable names. No raw keys/placeholders should
   appear; unrecognizable chemical names must remain hidden. Beyond 3 blocks,
    liquid details must be withheld.
 10. Drop a filled jug and examine it within 3 blocks. Check exact current/max
   volume (default capacity 200), supported physical description, and recognized
   names only. Repeat empty and beyond 3 blocks; inspection must not change contents.
 11. Outdoors, Shift-hover/click the Sun by day and Earth by night. Check names and
    the requested blurbs. These virtual targets have no world-space outline.
    Repeat toward ordinary blocks/glass and under a roof: world selection wins,
    and opaque geometry must prevent sky fallback. A pinned sky result must close
    if its scene becomes ineligible; looking away alone must not close it.

These checks require a connected client; headless tests do not establish visual
acceptance. GUI automation/client launches are not part of this manual procedure.
