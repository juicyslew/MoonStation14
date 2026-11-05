# EVA / internals manual check

Use a disposable test character for unprotected hazard comparisons.

1. In a clear loaded area, run **`/ms14 eva_fixture build`**. You start inside a lit **vacuum** room, wearing EVA gear with finite oxygen connected. No setup commands or manual equipping are required.
2. Press **K** for Actions. Turn internals off to compare breathing, then back on. Remove the helmet or suit to test pressure protection. `/ms14 eva_fixture status` shows actual room pressure, supply and body state.
3. To test the mask alone, remove the helmet first. Lowering the mask then disconnects breathing; with the helmet worn, the helmet still supplies the breathing connection. Raising the mask does not automatically reconnect a tank.
4. **`/ms14 eva_fixture reset`** restores breathable room air for a control comparison. It does not heal you or refill the tank. Optional `scenario vacuum|cold|hot|contaminated` changes only the room gas.

The fixture equips and connects gear only during `build`; subsequent changes use ordinary gameplay. It never forces health, saturation or recovery. The obsolete `initialize`, `enter` and `kit` commands are removed. After a server restart, build a new fixture.

## Examination and presentation follow-up

- Shift-left-click an oxygen tank in a hand, clothing/pocket slot, storage grid, and on the floor. Inspect pressure and connection/closed-valve text. Re-examine after using oxygen; the pressure must reflect current contents.
- Lower the mask: its SS14 blocked icon should appear without the extra green active background. Hover actions in the HUD and Actions window; tooltips should appear beside the pointer, including near screen edges.
- Scream and move while the sound plays. It should follow the character; check with a nearby listener if available.
- Connect/disconnect internals: only connecting should play the one-shot cue; leaving it on should not produce a flow loop. Its action icon stays on even at low pressure while still connected.
- Equip/remove the EVA helmet: the sound belongs at completion of each two-second operation, not its start. Grey jumpsuit equip has a sound; removal, EVA suit operations, and mask equip/removal/raise/lower are silent in the pinned SS14 definitions.
