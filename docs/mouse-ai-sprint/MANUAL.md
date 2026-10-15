# Connected mouse AI check

Use a disposable test area, not an owner world with important bodies.

1. Replace the old JAR and restart. Spawn several unclaimed mice
   (`/summon moonstation14:mouse`) on grass. Confirm varied wandering and pauses,
   rather than matching directional patterns; listen for occasional squeaks.
2. Give a hungry/parched mouse accessible cheese/pie and a supported drink
   container. Confirm it approaches and consumes them; puddles are not AI drink
   targets. Existing physiology controls when it becomes hungry or parched.
   Move cheese while approached; try another reachable slice if the first becomes
   blocked. Check pursuit recovers, eating stops movement, and your own movement
   does not develop jitter with several mice nearby.
3. Test a route over slabs/stairs and a one-block rise. Place a deep pit nearby:
   the mouse should avoid walking off its edge.
4. Put a closed airlock in the route. The mouse should pass underneath without
   opening it, including when bolted/unpowered. A human remains blocked. A
   closed door still obstructs interaction rays and seals atmosphere.
5. Take over the mouse: AI must stop immediately. Check player movement,
   ingestion, appearance and animations. Check `/ms14dev creative` followed by
   `/ms14dev return`, including after its chunk unloads.

## Emote/attention follow-up

- Control a mouse and run `/emote squeak`. Check one squeak and an emote line;
  immediate repeated requests should be rate-limited. A human/ghost must not
  gain the mouse emote.
- Stand near an idle mouse. It should sometimes briefly face you without walking
  toward you. Food/drink pursuit and takeover must override attention.

These checks require a connected client. Automated tests do not establish
visual acceptance. Airlock welding is not implemented locally.
