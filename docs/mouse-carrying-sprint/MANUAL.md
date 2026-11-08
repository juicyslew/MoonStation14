# Candidate manual checks

Use the candidate JAR in a disposable test world.

1. With an empty active hand, aim at a nearby mouse and left-click to pick it up.
   Move it between hands, a bag,
   worn storage and a world bag; drop the bag and retrieve the mouse. Check the
   coat/state icon, held model and storage pointer behavior.
2. Keep an AI mouse held or stored: after its five-second escape, it should flee
   immediately. Adopt a mouse role, have another player carry it, and press a
   movement direction: verify progress, interruption and release of your body.
3. Throw a held mouse, then insert one into a disposal unit using held-item use
   and flush it. Verify the same player mouse stays controlled through the outlet.
4. Park a player mouse in Creative and save/restart. Carry its body across a chunk
   boundary after restart, unload that chunk, then return to its body. Check that
   no duplicate or replacement mouse appears.
5. Give a mouse supported reagent doses; check reaction products and continued
   digestion while carried. Nearby hungry mice should spread food-pile choices.
6. Remove airlock power: its header lamps should be dark.

Report visual issues and any lost body/control. Native GPU presentation and the
complete unloaded/restarted return sequence still need these checks.
