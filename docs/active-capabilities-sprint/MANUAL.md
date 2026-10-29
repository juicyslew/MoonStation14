# Capability checks

Candidate checks after technical validation; not an acceptance receipt.

1. Join as a normal character. Granted actions should run down the top-left edge;
   no empty debug-cap button should be present.
   Press **1** to toggle combat mode and **2** to scream; also click their icons
   using the normal pointer mode. Check toggle feedback and the circular
   ten-second cooldown overlay/countdown. Scream chat must use your character's
   saved personal name (compare ordinary speech), never `Human` or
   `entity.moonstation14.player_character_harness`.
   Melee is not added. K is only the supplementary abilities menu.
2. Open the K menu. Drag its title bar, close with its top-right **X**, then reopen
   and click back into cursor-locked play. Movement and other HUD/windows should
   remain available; closing this menu must not close other windows.
   Check scrolling and layout at a large GUI scale.
3. Open chat, type `123`, and click scream/combat while keeping the draft.
   Actions must stay visible/clickable; typing alone must not fire them, including
   after closing chat. Check scream sound quality by ear.
4. With an empty hand, run `/ms14 capabilities debug_cap` as an operator. Select
   the cap and press **Z** to quick-equip, or use the existing equipment controls.
   **Debug pulse** should appear in slot **3** only while worn; activate it with
   **3** or a click for visible particles.
5. Remove the cap through the equipment window. Its button must disappear. Switch
   bodies/ghost roles: old abilities must not remain usable. Bat should have combat
   mode but no human scream action.

Defaults: action slots **1–3**, menu **K**, direct disposal climb **Ctrl+K**.
Check Controls if you have saved custom bindings. Typing numbers in chat must not
fire abilities after closing chat; action keys must not also change vanilla
hotbar selection while controlling a character. Do not edit the parked inventory.
