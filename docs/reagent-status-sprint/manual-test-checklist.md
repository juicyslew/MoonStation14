
# Manual in-game smoke checklist (Minecraft 1.21.1)

These are short client-world checks for the documented bounded behavior, not
full-fidelity acceptance tests. Use a singleplayer/dev world with cheats (or
operator permission). Enter commands in **in-game chat**, not a server
console, so `@s` selects your player. The command strings below were checked by
a parser-only GameTest; they were not run as an in-game manual session.

## 1. Bottle sip routes into stomach

1. Enter:

   ```text
   /gamemode survival
   /give @s moonstation14:bottle[moonstation14:reagent={"moonstation14:water":30.0f}]
   ```

2. Hold use on the bottle for at least one second, then inspect the selected
   item before and after a sip:

   ```text
   /data get entity @s SelectedItem
   ```

   Expect the bottle's water amount to decrease by 5 for one completed sip.
   There should be no vanilla `FoodData` projection. Stomach contents may not be
   visible through a convenient command. Allow a few seconds for scheduled
   digestion; do not expect an immediate alert.

## 2. Puddle fill and empty-hand drinking

1. Stand still on flat, solid ground and get a filled water bottle:

   ```text
   /give @s moonstation14:bottle[moonstation14:reagent={"moonstation14:water":30.0f}]
   ```

2. **Sneak-right-click the top face of a solid block** with the bottle to spill
   five units and create a filled puddle. Then right-click the puddle once with
   the bottle to transfer another 5 units into it.
   Switch to an empty hand and right-click the puddle once to drink 5 units.
   The bounded route is into stomach, not vanilla food/water. Optionally inspect
   the selected bottle with `/data get entity @s SelectedItem` before/after.
    Empty/full sources should not lose extra quantity, but no guaranteed
    attachment `/data` display is promised. Ingestion admits and splits doses
    in integer hundredths; requests or free stomach capacity below one cent are
    no-ops. Any normally accepted dose conserves each reagent in cents and
    keeps stomach volume at or below 50.

## 3. Held and placed jug drinking route to stomach

1. Enter:

   ```text
   /give @s moonstation14:jug[moonstation14:reagent={"moonstation14:water":20.0f}]
   ```

2. **Test held-jug drinking before placing it:** keep the filled jug in hand and
   sneak-right-click (hold use) to drink. After one completed sip, expect its
   water to decrease by 5 and the dose to enter custom STOMACH, not shared body
   `REAGENT` or vanilla food/water. This is the HELD `JugItem.onUseTick` path;
   it is distinct from the placed-block path below. The automated registered
   held-jug GameTest verifies the callback boundary, source/stomach deltas,
   unchanged body sugar, full-stomach retention, and non-sneaking throw.

3. First place the filled jug in survival, break it, and pick up exactly one
   jug. Select that jug and inspect it:

   ```text
   /data get entity @s SelectedItem
   ```

   Verify its reagent component/contents persist across placement and pickup.
   If `/data` does not expose a readable amount due to NeoForge serialization,
   inspect the held item's component through available UI; do not infer a
   specific serialization display format.

4. With the picked-up filled jug, empty your hand and right-click the placed
   jug once. Expect five units to enter custom stomach, not shared body
   `REAGENT` or vanilla food/water. This is the PLACED `JugBlock` empty-hand
   path. Check the jug before/after if `/data` exposes contents; stomach
    attachment display is not guaranteed.

## Optional damage control: Peaceful natural regeneration

In a normal client world after restart, and only if you are comfortable
changing its settings, first save the current difficulty and
`naturalRegeneration` gamerule values. Then in chat enter:

```text
/gamemode survival
/difficulty peaceful
/gamerule naturalRegeneration true
/damage @s 5 minecraft:fall
```

If your player is eligible for the character hunger route, health should not
regenerate over several seconds in Peaceful. This is a manual-only check: the
command parser GameTest does not cover `/damage` or the gamerule command, and no
command execution is claimed. The fix is entity-scoped and does not change the
Peaceful rule globally. Difficulty and gamerule changes can affect the world;
save their old values before testing. For a simple normal-difficulty control,
run `/difficulty normal` after observing the result; restore the saved prior
difficulty and gamerule values when finished.

## 4. Puddle empty lifecycle and inert acid contact

1. With a water bottle (or a fresh test player if your stomach is full), stand
   on flat, solid ground and use the parser-tested command:

   ```text
   /give @s moonstation14:bottle[moonstation14:reagent={"moonstation14:water":30.0f}]
   ```

   Sneak-right-click the top face of a solid block once to spill 5 units into a
   puddle. Empty your hand and click the puddle once to drink 5 units; assume
   your stomach has room for the dose. At exactly zero, verify the puddle block
   disappears. There is no mop or reverse-transfer cleanup route; do not try
   to empty it by drinking if your stomach is full.

2. On a separate fresh clear spot with a solid block top, get a filled acid
   bottle with this parser-tested command:

   ```text
   /give @s moonstation14:bottle[moonstation14:reagent={"moonstation14:polytrinicacid":30.0f}]
   ```

   Sneak-right-click the block top once to create a nonempty puddle. Observe it
   for a positive tint, then step across it once. Contact is disabled: the
   puddle should remain and stepping across should not transfer chemicals to
   you or hurt you. Tint is a client-visible manual check; if you cannot
   distinguish it in the client, report it as not visually verified. Custom
   `/data` output may not expose puddle contents, so this visual check does not
   prove the exact amount. There is no cleaning system to remove the acid
   puddle; **do not drink acid** or claim it disappears without cleaning it.
