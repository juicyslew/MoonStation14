# Melee demonstration

After the complete post-fix gate passes, use a controlled human. Visual quality
and sound playback still need your check:

1. Toggle combat with the existing combat action.
2. With an empty active hand, punch a target and empty space. Right-click a human holding an item, then an empty-handed human, to attempt disarm. It can fail normally; pigs/chickens are not eligible.
3. Hold a crowbar, then a kitchen knife: left-click for light attacks; right-click with several nearby targets in front for wide attacks. Check that the weapon replaces fist particles, the knife leads blade-first, and wide attacks sweep. Aim with the normal crosshair.
4. Check readable fist/tool swings on misses and red feedback on damaging hits. Provoke a Kobold: retaliation should have a visible claw attack and sound. Hit a loose jug with a held weapon; hit steel/plastic walls, girder, floor and a closed airlock for ineffective-contact sound.
5. Hold left-click to repeat at the normal cooldown (fists: one second). This defaults on for new settings; if previously saved off, enable **Mods → MoonStation14 → Config → Client Controls → Hold left mouse button to repeat melee attacks** once. Release/open a menu to stop. Turn combat off and confirm ordinary item interaction still works.

After the replacement gate passes, briefly verify the new owner feedback: repeat
the same weapon swing near a wall and confirm normal animated geometry (not a
miniature corner weapon); change pitch and confirm the HUD arrow follows flight
direction while the world arrow stays fixed to it. Test a real moving player
target against a stationary NPC; NPC motion is not the condition. Ghost-facing
was previously accepted and needs no repeat. Use two desktop clients only when
the owner explicitly confirms readiness.

For the latest reopened findings, control Kobold, Monkey and Scurret: test
controlled empty-hand melee and disarm against held-item and empty-handed targets;
test a handless animal attacking. Check Kobold/Monkey viewpoint and speech bubbles,
and held-item placement while idle and walking (including portable items). Briefly
regress accepted close-wall hits, NPC-miss-chance behavior and ghost-facing.

Disarm first attempts an active-item throw. Its stamina fallback can stun; it is not guaranteed spatial knockback. Guns are outside this sprint.

The jug is plastic: normal breakage spills its contents and may yield plastic, not glass shards. Fully resisted wall hits should use the ineffective-contact cue.

Also check the simple disarm cue and canonical names. On an actual item release,
both participants should immediately understand its flight direction, including
off-screen throws; there should be no ground marker. Repeat knife strikes with
unchanged aim, sweep level/up/down, and wide-swing a crowbar against a wall.
When looking level, the wide sweep should cross the chest, not the top of the
head; its configured total width is now 120°. With knife and crowbar, check
light/wide attacks level and steeply up/down: no weapon should start behind or
pass through the attacker. Observer weapons should remain rigid rather than flatten.
Unarmed attacks with the active pulling hand remain allowed, matching pinned SS14.
Check stamina beneath thirst drops and recovers from server updates.
Check solution text on a world syringe. Audio, crowds, and effect styling still
require your in-game review; automated captures do not accept them.

Latest follow-up, after its complete gate passes:
- Watch another player light/wide-swing a crowbar level and pitched: it should
  emerge from the attacker without flattening. Recheck accepted first-person fit.
- In combat mode, empty-hand click a ground item: pick it up. Holding an item or
  using a pulling-occupied hand should retain attacks; a living mouse remains a target.
- As a monkey, pick up an ordinary item and press **Ctrl+Q** toward clear space.
  If it fails, note the item and configured throw binding (currently grenade-labelled).
