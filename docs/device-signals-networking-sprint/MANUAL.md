# Owner testing (after automated validation)

## Retest the reported blockers first

1. Use the rebuilt jar on client and server. In ordinary controlled-body gameplay,
   drop a button and switch: both should be visible on the floor as single sprites.
2. Pick them up and place each against a solid wall's side with the active hand
   and left-click. No creative mode should be needed. Placed sprites should show
   one frame; activating the switch should change its on/off appearance.
3. Hold a fresh configurator: its single icon should animate and show a mode light.
   **Alt+E** should change Link/List mode and report the new mode.
4. In Link mode, left-click the button, then a powered airlock. Each click should
   report selection, editor opening, or rejection. Check source
   and sink ports in the linking window; choose **Link defaults** → **Done**.
   Press the button.
5. Check a loose item's single white target outline, without highlighting the
   block behind it too. Then continue the broader checklist below.

## Broader workflow checks

Retest the usability corrections: addresses should be short uppercase `XXXX-XXXX`
(alarms/vents/scrubbers add `AIR-`/`VNT-`/`SCR-`), labels should resolve, and each
pane should occupy at most one-sixth of the viewport with chat-sized body text.
Select a port twice to deselect it; Clear links should also remove pending
highlights. Hover Defaults for its meaning and check direct connection lines.
Defaults should also clear pending highlights. Door visibility can end before the
advanced remote's 50-block signal range; disappearance is not proof of lost range.

Use the same final jar on client and server. Keep your normal launch configuration.

1. Obtain `network_configurator`, `remote_signaller`, `remote_signaller_advanced`,
   `signal_button`, and `signal_switch` with `/give @s moonstation14:<item>`.
   Grants go to the controlled body's hands, or drop nearby when hands are full.
2. Place a button/switch on a supported face beside a powered airlock. With the
   configurator active in **Link** mode, use it on the source, then the airlock.
   Check port names, lines and tooltips; use **Link defaults** and **Done**.
   Activate the button; check normal door power/bolt/safety gates still apply.
3. Reopen linking. Toggle an individual pair, Clear and Link defaults: each must
   take effect immediately. Done only closes. Drag the window and resize the GUI.
4. Drop a remote. With the configurator held in Link mode, left-click the remote,
   then the airlock; configure `Pressed → Toggle`, pick it up, and activate with **Z**.
   Keep it in the active hand, within 15 blocks (advanced: 50). Test Z once with
   the workspace closed and again while it is open, allowing one second between
   uses. E is world interaction, not remote in-hand activation.
   Drop the linked remote and aim at it: E should activate it without pickup
   (also test with an ordinary item held). Configurator-held targeting should
   still configure it. Rapid repeat presses within one second should silently
   do nothing; moving the receiver beyond signal range should not deny the press.
   Check standard range 15, advanced range 50; link setup range is 30.
5. Use **Alt+E** with the configurator active to switch to **List** mode. Use it on
   vents/scrubbers to stage them; **Z** opens the staged list. Use it on an air
   alarm to Configure its list. Check Set/Add/Clear/Copy, individual removal and Show.
6. Open the powered alarm. Check enabled state, pressure, flow and filters update
   after network responses. A disabled vent should remain remotely re-enableable.
7. With two clients, change links/list membership and device settings. Confirm
   refreshed state agrees; moving/dropping the tool or replacing a target must
   not permit stale edits. Reconnect/restart and check identities and links persist.

Report the failing step, visible result, and any configurator selection/rejection
message. Z and Alt+E refer to current default
bindings; use your rebound equivalents. No frequency/code settings are expected.
